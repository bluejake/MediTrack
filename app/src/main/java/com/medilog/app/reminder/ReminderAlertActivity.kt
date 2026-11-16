package com.medilog.app.reminder

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import com.medilog.app.ui.components.rememberDrawablePainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.medilog.app.R
import com.medilog.app.ui.glass.GlassButton
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.GlassSurface
import com.medilog.app.ui.glass.LocalBackdrop
import com.medilog.app.ui.theme.AppTheme
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.ui.theme.ThemePrefs
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import com.medilog.app.util.TimeUtils

/**
 * 锁屏全屏服药提醒页。
 *
 * 由通知的 FullScreenIntent（或用户在锁屏/通知栏点击）启动：
 *   - setShowWhenLocked / setTurnScreenOn：显示在锁屏之上并点亮屏幕（API 27+，
 *     API 26 走窗口 flag）；
 *   - 120 秒无操作自动退出，避免忘熄屏。
 */
class ReminderAlertActivity : ComponentActivity() {

    // 审计 U4：应用内语言（API<33）同步作用于提醒弹窗
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.medilog.app.util.LocaleHelper.wrap(newBase))
    }

    private val handler = Handler(Looper.getMainLooper())
    private val autoDismiss = Runnable { finish() }

    private var medId = -1L
    private var schedId = -1L
    private var medName: String? = null
    private var strength: String? = null
    private var planTime: String? = null
    private var planDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        // 主题模式持久化：提醒页与主进程一致（决定 MD3 / MIUIX 分支，避免玻璃态误入）
        ThemePrefs.init(this)

        bind(intent)

        setContent {
            // 与主界面同一套主题分发（提供 LocalAppThemeMode / LocalMd3Colors）：
            // 此前用 MediTheme 不提供主题模式 → 组件默认走 MATERIAL 分支，
            // MIUIX 用户的全屏提醒会拿到 MD3 实心面，与主界面液态玻璃不一致。
            AppTheme(mode = ThemePrefs.mode.value) {
                AlertContent(
                    isTest = schedId == ReminderScheduler.TEST_SCHED_ID,
                    medName = medName,
                    meta = buildMeta(),
                    onTaken = {
                        sendAction(ActionReceiver.ACTION_TAKEN)
                        finish()
                    },
                    onSnooze = {
                        sendAction(ActionReceiver.ACTION_SNOOZE)
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }

        handler.postDelayed(autoDismiss, AUTO_DISMISS_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
        handler.removeCallbacks(autoDismiss)
        handler.postDelayed(autoDismiss, AUTO_DISMISS_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismiss)
        super.onDestroy()
    }

    /** 锁屏之上显示 + 点亮屏幕 + 保持常亮 */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun bind(intent: Intent?) {
        if (intent == null) return
        medId = intent.getLongExtra(ReminderScheduler.EXTRA_MED_ID, -1L)
        schedId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHED_ID, -1L)
        medName = intent.getStringExtra(ReminderScheduler.EXTRA_MED_NAME)
        strength = intent.getStringExtra(ReminderScheduler.EXTRA_STRENGTH)
        planTime = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_TIME)
        planDate = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_DATE)
        if (planDate == null) planDate = TimeUtils.today()
    }

    private fun buildMeta(): String {
        val sb = StringBuilder()
        if (!strength.isNullOrEmpty()) sb.append(strength)
        if (!planTime.isNullOrEmpty()) {
            if (sb.isNotEmpty()) sb.append(" · ")
            // 审计 P1-2：经资源取值（本类是 Activity，可直接 getString）
            sb.append(getString(R.string.alert_plan_prefix, TimeUtils.friendlyTime(planTime)))
        }
        return sb.toString()
    }

    private fun sendAction(action: String) {
        val i = Intent(this, ActionReceiver::class.java)
        i.action = action
        i.putExtra(ReminderScheduler.EXTRA_MED_ID, medId)
        i.putExtra(ReminderScheduler.EXTRA_SCHED_ID, schedId)
        i.putExtra(ReminderScheduler.EXTRA_MED_NAME, medName)
        i.putExtra(ReminderScheduler.EXTRA_STRENGTH, strength)
        i.putExtra(ReminderScheduler.EXTRA_PLAN_TIME, planTime)
        i.putExtra(ReminderScheduler.EXTRA_PLAN_DATE, planDate)
        sendBroadcast(i)
    }

    companion object {
        private const val AUTO_DISMISS_MS = 120_000L

        /** 供 ReminderReceiver 判断当前是否处于锁屏/熄屏状态 */
        @JvmStatic
        fun lockedOrScreenOff(ctx: Context): Boolean {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val screenOff = pm != null && !pm.isInteractive
            val locked = km != null && km.isKeyguardLocked
            return screenOff || locked
        }
    }
}

@Composable
private fun AlertContent(
    isTest: Boolean,
    medName: String?,
    meta: String,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onClose: () -> Unit
) {
    val c = colors
    val backdrop = rememberLayerBackdrop()
    // MIUIX 保留液态玻璃环境光；MATERIAL 按 MD3 收敛为干净表面（无 iOS 底光）
    val isMaterial = ThemePrefs.mode.value == AppThemeMode.MATERIAL

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        if (!isMaterial) {
            Image(
            // 审计 P1-1 回归发现的隐藏 bug：bg_ambient 是渐变 layer-list，
            // painterResource 只支持 Vector/位图，会直接抛 IllegalArgumentException。
            // 改用项目自带的 DrawablePainter（与 MainActivity 同场景一致）。
            painter = rememberDrawablePainter(R.drawable.bg_ambient),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            contentScale = ContentScale.Crop
        )
        }

        androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                GlassCard(
                    Modifier.fillMaxWidth(),
                    radius = 26.dp,
                    tintAlpha = 0.24f
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlassSurface(
                            Modifier.size(72.dp),
                            circle = true,
                            blurRadius = 1.dp,
                            refractionHeight = 8.dp,
                            refractionAmount = 16.dp,
                            tint = c.primary,
                            tintAlpha = 0.3f
                        ) {
                            Image(
                                painterResource(R.drawable.ic_bell),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(34.dp),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        BasicText(
                            stringResource(if (isTest) R.string.alert_test_title else R.string.notif_title),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                                color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                            ))
                        )
                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            if (isTest) stringResource(R.string.alert_test_body)
                            else (medName ?: ""),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                                color = c.textPrimary,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            ))
                        )
                        if (meta.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            BasicText(
                                meta,
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp))
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassButton(
                                stringResource(R.string.notif_action_taken),
                                onClick = onTaken,
                                modifier = Modifier.weight(1f),
                                tint = c.green,
                                textColor = Color.White
                            )
                            GlassButton(
                                stringResource(R.string.notif_action_snooze),
                                onClick = onSnooze,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        BasicText(
                            stringResource(R.string.alert_close),
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable { onClose() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 15.sp))
                        )
                    }
                }
            }
        }
    }
}
