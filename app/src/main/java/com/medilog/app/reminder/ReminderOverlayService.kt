package com.medilog.app.reminder

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.medilog.app.App
import com.medilog.app.R
import com.medilog.app.util.TimeUtils

/**
 * 服药提醒弹窗（悬浮窗形态）。
 *
 * 为什么不用 Activity：Android 10+ 的后台启动 Activity 限制（BAL）在国产 ROM 上会被
 * 进一步收紧 —— 真机实测（荣耀 MagicOS 11 / Android 17）：FSI 被拒，startActivity
 * 不报错但窗口不出现。悬浮窗走 WindowManager 直绘，不经过 Activity 启动闸门。
 *
 * 弹出时机（2026-11-02 需求）：
 *   - 已解锁 → 到点立即弹；
 *   - 锁屏   → 直接覆盖锁屏弹出，并点亮屏幕（SHOW_WHEN_LOCKED + TURN_SCREEN_ON）；
 *   - 应用在后台 / 进程被杀 → 由精确闹钟拉起本前台服务，同样生效。
 *
 * 生命周期：闹钟广播 → startForegroundService → 静默前台通知保活 → 加悬浮窗
 * → 用户操作（已服用/稍后/关闭）或 120s 超时 → 移除窗口并 stopSelf。
 * 交互复用 ActionReceiver，写库逻辑与通知栏按钮同源。
 */
class ReminderOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var root: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { dismissAndStop("timeout") }
    private var attached = false
    private var currentSchedId: Long = -1L

    /** 收到"通知侧已处理"的广播 → 立刻收起弹窗（双向同步） */
    private val dismissReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
            if (i?.action == ACTION_DISMISS_OVERLAY) {
                Log.i(TAG, "dismiss requested by notification action")
                dismissAndStop("notification_action")
            }
        }
    }
    private var dismissRegistered = false

    private fun registerDismiss() {
        if (dismissRegistered) return
        try {
            val f = android.content.IntentFilter(ACTION_DISMISS_OVERLAY)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dismissReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION") registerReceiver(dismissReceiver, f)
            }
            dismissRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "register dismiss receiver failed", e)
        }
    }

    private fun unregisterDismiss() {
        if (!dismissRegistered) return
        try { unregisterReceiver(dismissReceiver) } catch (ignore: Exception) {}
        dismissRegistered = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 被全屏提醒页接管时，主动撤掉悬浮窗，避免两层 UI 叠加
        if (intent.action == ACTION_DISMISS) {
            Log.i(TAG, "dismiss requested by alert activity")
            dismissAndStop("activity_takeover")
            return START_NOT_STICKY
        }
        startForegroundSafely(intent)

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "overlay permission revoked, skip popup")
            stopSelf()
            return START_NOT_STICKY
        }

        val p = PendingPopup.Payload(
            medId = intent.getLongExtra(ReminderScheduler.EXTRA_MED_ID, -1L),
            schedId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHED_ID, -1L),
            name = intent.getStringExtra(ReminderScheduler.EXTRA_MED_NAME).orEmpty(),
            strength = intent.getStringExtra(ReminderScheduler.EXTRA_STRENGTH).orEmpty(),
            planTime = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_TIME).orEmpty(),
            planDate = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_DATE),
            isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false),
            at = System.currentTimeMillis()
        )

        // 同一提醒去重（进程重启 / 重复广播不重复弹）
        if (PendingPopup.alreadyShown(this, p.key)) {
            Log.i(TAG, "already shown recently, skip: " + p.key)
            stopSelf()
            return START_NOT_STICKY
        }

        val locked = isLocked()
        Log.i(TAG, "show popup now (locked=" + locked + " sched=" + p.schedId + " time=" + p.planTime + ")")
        showWindow(p)
        return START_NOT_STICKY
    }

    private fun isLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return km.isKeyguardLocked
    }

    // ---------------- 窗口 ----------------

    private fun showWindow(p: PendingPopup.Payload) {
        if (attached) return
        val isTest = p.isTest
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val cardBg = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
        val textPrimary = if (dark) 0xFFF5F5F7.toInt() else 0xFF111114.toInt()
        val textSecondary = if (dark) 0xFF9A9AA2.toInt() else 0xFF6B6B73.toInt()
        val accent = 0xFF007AFF.toInt()
        val accentSoft = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()

        val pad = dp(20)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(cardBg)
            }
            elevation = dp(18).toFloat()
            isClickable = true
        }

        // 标题行：该服药了 + 关闭
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = if (isTest) getString(R.string.alert_test_title) else getString(R.string.notif_title)
            setTextColor(accentSoft)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val close = TextView(this).apply {
            text = "\u2715"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(4), dp(10))
            setOnClickListener { dismissAndStop("user_close") }
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
        card.addView(titleRow)

        // 药名
        val name = TextView(this).apply {
            text = if (isTest) getString(R.string.notif_test_body) else p.name
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTest) 16f else 22f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(name)

        // 剂量 + 计划时间
        if (!isTest) {
            val metaText = listOf(p.strength, getString(R.string.alert_plan, friendly(p.planTime)))
                .filter { it.isNotBlank() }
                .joinToString(" \u00b7 ")
            val meta = TextView(this).apply {
                text = metaText
                setTextColor(textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(8), 0, dp(4))
            }
            card.addView(meta)
        }

        // 按钮行
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        if (!isTest) {
            val taken = pillButton(getString(R.string.notif_action_taken), accent, Color.WHITE) {
                sendAction(ActionReceiver.ACTION_TAKEN, p)
                dismissAndStop("taken")
            }
            val snooze = pillButton(getString(R.string.notif_action_snooze), 0x00000000, accentSoft) {
                sendAction(ActionReceiver.ACTION_SNOOZE, p)
                dismissAndStop("snooze")
            }
            btnRow.addView(taken, LinearLayout.LayoutParams(0, dp(46), 1f))
            btnRow.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
            btnRow.addView(snooze, LinearLayout.LayoutParams(0, dp(46), 1f))
        } else {
            val ok = pillButton(getString(R.string.alert_close), accent, Color.WHITE) {
                dismissAndStop("user_close")
            }
            btnRow.addView(ok, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        card.addView(btnRow)

        // 外层容器：点空白关闭
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setOnClickListener { dismissAndStop("outside") }
        }
        container.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // SHOW_WHEN_LOCKED 覆盖锁屏 / TURN_SCREEN_ON 熄屏点亮 /
            // NOT_TOUCH_MODAL 卡片外触摸落到下层 / DIM_BEHIND 轻遮罩聚焦
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.42f
        }

        currentSchedId = p.schedId
        try {
            wm.addView(container, lp)
            root = container
            attached = true
            registerDismiss()
            PendingPopup.markShown(this, p.key)
            handler.removeCallbacks(autoDismiss)
            handler.postDelayed(autoDismiss, AUTO_DISMISS_MS)
            Log.i(TAG, "overlay shown test=" + p.isTest + " med=" + p.medId + " sched=" + p.schedId)
        } catch (e: Exception) {
            Log.w(TAG, "addView failed", e)
            stopSelf()
        }
    }

    private fun pillButton(label: String, bg: Int, fg: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(bg)
                if (bg == 0x00000000) setStroke(dp(1).coerceAtLeast(1), fg)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    // ---------------- 动作 ----------------

    private fun sendAction(action: String, p: PendingPopup.Payload) {
        val i = Intent(this, ActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderScheduler.EXTRA_MED_ID, p.medId)
            putExtra(ReminderScheduler.EXTRA_SCHED_ID, p.schedId)
            putExtra(ReminderScheduler.EXTRA_MED_NAME, p.name)
            putExtra(ReminderScheduler.EXTRA_STRENGTH, p.strength)
            putExtra(ReminderScheduler.EXTRA_PLAN_TIME, p.planTime)
            putExtra(ReminderScheduler.EXTRA_PLAN_DATE, p.planDate)
        }
        try {
            sendBroadcast(i)
            Log.i(TAG, "action sent: " + action)
        } catch (e: Exception) {
            Log.w(TAG, "send action failed", e)
        }
    }

    private fun dismissAndStop(reason: String) {
        handler.removeCallbacks(autoDismiss)
        // 「关闭」类操作要同时作用于通知（超时不处理，保留通知继续提醒）
        if (reason == "user_close" || reason == "outside") {
            try {
                if (currentSchedId != -1L) {
                    androidx.core.app.NotificationManagerCompat.from(this)
                        .cancel(ReminderScheduler.notifyIdFor(currentSchedId))
                }
            } catch (e: Exception) {
                Log.w(TAG, "cancel notification failed", e)
            }
        }
        unregisterDismiss()
        if (attached) {
            try {
                root?.let { wm.removeView(it) }
            } catch (e: Exception) {
                Log.w(TAG, "removeView failed", e)
            }
            attached = false
            root = null
            Log.i(TAG, "overlay dismissed: " + reason)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismiss)
        unregisterDismiss()
        if (attached) {
            try {
                root?.let { wm.removeView(it) }
            } catch (ignore: Exception) {
            }
            attached = false
        }
        super.onDestroy()
    }

    // ---------------- 前台通知（静默） ----------------

    private fun startForegroundSafely(intent: Intent) {
        val n = buildSilentNotification(intent)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FGS_ID, n, FOREGROUND_TYPE_SPECIAL_USE)
            } else {
                startForeground(FGS_ID, n)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
        }
    }

    private fun buildSilentNotification(intent: Intent): Notification {
        val open = Intent(this, com.medilog.app.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, 910001, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(intent.getStringExtra(ReminderScheduler.EXTRA_MED_NAME).orEmpty())
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun friendly(hhmm: String): String =
        if (hhmm.isBlank()) "" else TimeUtils.friendlyTime(hhmm)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "MediLog-Overlay"
        private const val FGS_ID = 910

        /** FOREGROUND_SERVICE_TYPE_SPECIAL_USE */
        private const val FOREGROUND_TYPE_SPECIAL_USE = 0x40000000
        private const val AUTO_DISMISS_MS = 120_000L
        const val EXTRA_IS_TEST = "overlay_is_test"

        /** 通知侧（已服用/稍后/划掉）广播此动作，让弹窗同步收起 */
        const val ACTION_DISMISS_OVERLAY = "com.medilog.app.action.OVERLAY_DISMISS"

        fun dismissShown(ctx: Context) {
            try {
                ctx.sendBroadcast(
                    Intent(ACTION_DISMISS_OVERLAY).setPackage(ctx.packageName)
                )
            } catch (e: Exception) {
                Log.w(TAG, "dismissShown broadcast failed", e)
            }
        }
        /** 全屏提醒页已显示 → 撤掉悬浮窗 */
        const val ACTION_DISMISS = "com.medilog.app.action.OVERLAY_DISMISS"

        /** 供提醒页调用：让正在显示的悬浮窗让位 */
        fun dismissNow(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, ReminderOverlayService::class.java).apply {
                    action = ACTION_DISMISS
                })
            } catch (e: Exception) {
                Log.w(TAG, "dismissNow failed", e)
            }
        }

        /** 是否具备弹窗条件（悬浮窗 / 后台弹出界面已授权） */
        fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        /**
         * 触发入口：闹钟广播 / 测试按钮。
         * 锁屏与解锁都由服务内部处理（锁屏时覆盖锁屏并点亮屏幕）。
         * @return true 表示服务已成功启动（不代表窗口已显示）
         */
        fun show(
            ctx: Context, medId: Long, schedId: Long, medName: String?,
            strength: String?, planTime: String?, planDate: String?, isTest: Boolean
        ): Boolean {
            if (!canShow(ctx)) {
                Log.i(TAG, "no overlay permission, fallback to notification only")
                return false
            }
            val i = Intent(ctx, ReminderOverlayService::class.java).apply {
                putExtra(ReminderScheduler.EXTRA_MED_ID, medId)
                putExtra(ReminderScheduler.EXTRA_SCHED_ID, schedId)
                putExtra(ReminderScheduler.EXTRA_MED_NAME, medName)
                putExtra(ReminderScheduler.EXTRA_STRENGTH, strength)
                putExtra(ReminderScheduler.EXTRA_PLAN_TIME, planTime)
                putExtra(ReminderScheduler.EXTRA_PLAN_DATE, planDate)
                putExtra(EXTRA_IS_TEST, isTest)
            }
            return try {
                ContextCompat.startForegroundService(ctx, i)
                Log.i(TAG, "overlay service started med=" + medId + " sched=" + schedId + " test=" + isTest)
                true
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService rejected", e)
                false
            }
        }
    }
}
