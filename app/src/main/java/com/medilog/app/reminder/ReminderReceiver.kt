package com.medilog.app.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.medilog.app.App
import com.medilog.app.R
import com.medilog.app.data.MedRepository
import com.medilog.app.ui.MainActivity
import com.medilog.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟触发入口：负责校验计划有效性 → 发通知（含全屏 Intent）→ 重排次日闹钟。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "onReceive action=$action")

        // ---- 每日自检：重排全部提醒并武装下一天（无通知、无声音，用户无感）----
        // 这是零配置可靠性的兜底：ROM 清理掉待触发闹钟后，最迟次日凌晨自愈。
        if (ReminderScheduler.ACTION_DAILY_CHECK == action) {
            val appCtx = ctx.applicationContext
            // 广播主线程只做轻量工作；重排含 DB + IPC，放后台
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ReminderScheduler.scheduleAll(appCtx)
                } catch (e: Exception) {
                    Log.e(TAG, "daily check reschedule failed", e)
                }
            }
            return
        }

        val medId = intent.getLongExtra(ReminderScheduler.EXTRA_MED_ID, -1L)
        val schedId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHED_ID, -1L)
        val medName = intent.getStringExtra(ReminderScheduler.EXTRA_MED_NAME)
        val strength = intent.getStringExtra(ReminderScheduler.EXTRA_STRENGTH)
        val planTime = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_TIME)
        var planDate = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_DATE)

        val today = TimeUtils.today()
        if (planDate == null || planDate != today) planDate = today

        // ---- 测试提醒：走完整链路但不落库、不重排 ----
        if (ReminderScheduler.ACTION_TEST == action || schedId == ReminderScheduler.TEST_SCHED_ID) {
            showNotification(ctx, -1L, ReminderScheduler.TEST_SCHED_ID, ctx.getString(R.string.alert_test_title), "", "", today)
            return
        }

        // ---- 守卫：药物已删除 / 已关闭提醒 / 计划已删除或停用 → 不再打扰 ----
        val repo = MedRepository(ctx)
        val m = if (medId > 0) repo.getMedication(medId) else null
        if (m == null) {
            Log.i(TAG, "med $medId deleted, drop stale alarm")
            return
        }
        if (m.asNeeded || !m.remind) {
            Log.i(TAG, "med $medId reminder off, drop alarm")
            return
        }
        val target = repo.schedulesOf(m.id).firstOrNull { it.id == schedId }
        if (target == null) {
            Log.i(TAG, "sched $schedId removed/disabled, drop stale alarm")
            return
        }

        // ---- 稍后提醒触发时，若今天该剂量已记录，则抑制通知 ----
        if (ReminderScheduler.ACTION_SNOOZE == action) {
            if (repo.hasLogFor(m.id, schedId, planDate)) {
                Log.i(TAG, "snooze suppressed: dose already recorded")
                return
            }
        }

        showNotification(ctx, medId, schedId, medName, strength, planTime, planDate)

        // 重新排下一次（次日）
        try {
            ReminderScheduler.scheduleOne(ctx, repo, m, target)
        } catch (e: Exception) {
            Log.e(TAG, "reschedule failed", e)
        }
    }

    /** 通知渠道与通知权限是否可用 */
    private fun notificationsUsable(ctx: Context): Boolean {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) {
            Log.w(TAG, "notifications disabled by user")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val m = ctx.getSystemService(NotificationManager::class.java)
            if (m != null) {
                val ch = m.getNotificationChannel(App.CHANNEL_REMINDER)
                if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) {
                    Log.w(TAG, "reminder channel blocked by user")
                    return false
                }
            }
        }
        return true
    }

    private fun showNotification(
        ctx: Context, medId: Long, schedId: Long, medName: String?,
        strength: String?, planTime: String?, planDate: String?
    ) {
        if (!notificationsUsable(ctx)) {
            fallbackAlert(ctx, medId, schedId, medName, strength, planTime, planDate)
            return
        }

        val isTest = schedId == ReminderScheduler.TEST_SCHED_ID
        val title = if (isTest) ctx.getString(R.string.alert_test_title) else ctx.getString(R.string.notif_title)
        val body = if (isTest) {
            ctx.getString(R.string.notif_test_body)
        } else {
            ctx.getString(
                R.string.notif_body,
                medName ?: "",
                strength ?: "",
                TimeUtils.friendlyTime(planTime ?: "")
            )
        }

        // 点击通知：直接打开**居中提醒卡片**（而不是主界面）。
        // 2026-11-02 真机（荣耀 MagicOS）实测：系统拒绝 FSI、并静默拦截后台 Activity 启动，
        // 锁屏/后台时无法自动弹窗；此时"点一下通知就回到提醒卡片"是唯一可靠的落地方式。
        // 测试提醒保持打开主界面（无剂量可记录）。
        val contentIntent = if (isTest) {
            Intent(ctx, MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            ReminderScheduler.alertIntent(ctx, medId, schedId, medName, strength, planTime, planDate)
        }
        val contentPi = PendingIntent.getActivity(
            ctx, schedId.toInt(), contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 全屏 Intent：锁屏/熄屏时由系统直接拉起全屏提醒页
        val alert = ReminderScheduler.alertIntent(ctx, medId, schedId, medName, strength, planTime, planDate)
        val fsPi = PendingIntent.getActivity(
            ctx, ReminderScheduler.alertReqFor(schedId), alert,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = NotificationCompat.Builder(ctx, App.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .setFullScreenIntent(fsPi, true)

        if (!isTest) {
            val taken = Intent(ctx, ActionReceiver::class.java)
            taken.action = ActionReceiver.ACTION_TAKEN
            taken.putExtra(ReminderScheduler.EXTRA_MED_ID, medId)
            taken.putExtra(ReminderScheduler.EXTRA_SCHED_ID, schedId)
            taken.putExtra(ReminderScheduler.EXTRA_PLAN_DATE, planDate)
            taken.putExtra(ReminderScheduler.EXTRA_PLAN_TIME, planTime)
            val takenPi = PendingIntent.getBroadcast(
                ctx, (schedId + 500000L).toInt(), taken,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snooze = Intent(ctx, ActionReceiver::class.java)
            snooze.action = ActionReceiver.ACTION_SNOOZE
            snooze.putExtra(ReminderScheduler.EXTRA_MED_ID, medId)
            snooze.putExtra(ReminderScheduler.EXTRA_SCHED_ID, schedId)
            snooze.putExtra(ReminderScheduler.EXTRA_MED_NAME, medName)
            snooze.putExtra(ReminderScheduler.EXTRA_STRENGTH, strength)
            snooze.putExtra(ReminderScheduler.EXTRA_PLAN_TIME, planTime)
            snooze.putExtra(ReminderScheduler.EXTRA_PLAN_DATE, planDate)
            val snoozePi = PendingIntent.getBroadcast(
                ctx, (schedId + 600000L).toInt(), snooze,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 划掉通知（swipe）等同于「关闭」：同步收起弹窗，但不写服药记录
            val dismiss = Intent(ctx, ActionReceiver::class.java).apply {
                action = ActionReceiver.ACTION_DISMISS
                putExtra(ReminderScheduler.EXTRA_MED_ID, medId)
                putExtra(ReminderScheduler.EXTRA_SCHED_ID, schedId)
            }
            val dismissPi = PendingIntent.getBroadcast(
                ctx, (schedId + 700000L).toInt(), dismiss,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.setDeleteIntent(dismissPi)
            b.addAction(0, ctx.getString(R.string.notif_action_taken), takenPi)
                .addAction(0, ctx.getString(R.string.notif_action_snooze), snoozePi)
        }

        try {
            NotificationManagerCompat.from(ctx).notify(ReminderScheduler.notifyIdFor(schedId), b.build())
            Log.i(TAG, "notification posted id=" + ReminderScheduler.notifyIdFor(schedId))
        } catch (e: SecurityException) {
            Log.w(TAG, "notify SecurityException (POST_NOTIFICATIONS?)", e)
            return
        }

        // 弹窗优先（2026-11-02 需求）：
        //   - 解锁状态 → 立即弹；
        //   - 锁屏状态 → 服务内部挂起，等 ACTION_USER_PRESENT 解锁后再弹；
        //   - 不依赖 Activity 启动能力，因此不受 BAL 限制，后台/进程被杀都能弹。
        val overlayShown = ReminderOverlayService.show(
            ctx, medId, schedId, medName, strength, planTime, planDate, isTest
        )

        // 覆盖锁屏：悬浮窗（TYPE_APPLICATION_OVERLAY）层级低于 keyguard，锁屏时会被盖住；
        // 真正能盖住锁屏的是带 showWhenLocked 的 Activity。因此锁屏时再尝试拉起提醒页
        // （若 ROM 允许后台启动 Activity，即为"覆盖锁屏弹窗"；不允许则只记日志，
        //  悬浮窗依然点亮屏幕，并保留在解锁后立即呈现）。
        val locked = ReminderAlertActivity.lockedOrScreenOff(ctx)
        if (locked) {
            try {
                ctx.startActivity(alert)
                Log.i(TAG, "locked: try alert activity over keyguard")
            } catch (e: Exception) {
                Log.w(TAG, "locked: alert activity blocked (BAL restriction)", e)
            }
        } else if (!overlayShown) {
            // 解锁且无悬浮窗权限时的降级
            try {
                ctx.startActivity(alert)
                Log.i(TAG, "no overlay, launched alert activity directly (unlocked)")
            } catch (e: Exception) {
                Log.w(TAG, "direct launch blocked by system (BAL restriction)", e)
            }
        }
    }

    /** 是否拥有全屏 Intent 权限（canUseFullScreenIntent 自 API 34 才存在） */
    private fun canUseFullScreenIntent(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            return nm != null && nm.canUseFullScreenIntent()
        }
        return true
    }

    /**
     * 通知不可用时的兜底（零配置可靠性，2026-10-26 补）。
     *
     * 通知权限被拒是唯一会让提醒「彻底无声无息」的配置：通知和全屏提醒页都经由
     * 通知渠道投递，权限一拒等于整条提醒链静默 —— 违反「不遗漏任何一条提醒」。
     * 这里用**不依赖通知权限**的通道做最后提醒：
     *  1. 直接驱动震动马达（VIBRATE 权限独立于 POST_NOTIFICATIONS）；
     *  2. 尝试由本进程直接拉起全屏提醒页（Android 10+ 后台启动 Activity 可能被
     *     系统拦截，拦截只损失视觉提示，震动仍然送达，且不抛出到用户层）。
     */
    private fun fallbackAlert(
        ctx: Context, medId: Long, schedId: Long, medName: String?,
        strength: String?, planTime: String?, planDate: String?
    ) {
        Log.w(TAG, "notifications unavailable, fallback alert for sched=$schedId")
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vib?.hasVibrator() == true) {
                // 三记长震，节奏区别于通知渠道的默认震动，触觉上足以唤起注意
                vib.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 700, 350, 700, 350, 700), -1)
                )
                Log.i(TAG, "fallback vibration delivered")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fallback vibration failed", e)
        }
        try {
            ctx.startActivity(
                ReminderScheduler.alertIntent(
                    ctx, medId, schedId, medName, strength, planTime, planDate
                )
            )
            Log.i(TAG, "fallback: alert activity launched directly")
        } catch (e: Exception) {
            Log.w(TAG, "fallback: direct activity launch blocked by system", e)
        }
    }

    companion object {
        private const val TAG = "MediLog-Alarm"
    }
}
