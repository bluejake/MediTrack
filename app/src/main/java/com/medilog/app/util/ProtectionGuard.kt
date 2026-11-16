package com.medilog.app.util

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * 「提醒守护」状态判定。
 *
 * 背景（真机实测，荣耀 MagicOS 11 / Android 17）：系统管家（com.hihonor.systemmanager）
 * 会在后台清理时**强制停止**本应用；应用一旦进入 stopped 状态，系统会取消它的
 * 全部待触发闹钟并屏蔽广播（Android 15+ 行为），表现为"设了提醒却不响"，且用户无感。
 * 这是平台机制，应用无法自行阻止，只能：
 *   1. 引导用户把应用加入「应用启动管理 / 清理白名单」（一次性）；
 *   2. 沉默失效时能被察觉（下次打开应用时提示，并已自动重排）。
 */
object ProtectionGuard {
    private const val TAG = "MediLog-Guard"
    private const val SP = "medilog_guard"
    private const val KEY_DISMISS_AT = "dismiss_at"
    private const val DISMISS_TTL_MS = 3L * 24 * 3600 * 1000   // 忽略后 3 天内不再打扰
    private const val FORCE_STOP_WINDOW_MS = 7L * 24 * 3600 * 1000

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    /** 是否已在电池优化白名单（这个应用自己能申请，系统弹窗一次即可） */
    fun batteryWhitelisted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** 最近是否被"强制停止"过（stopped 状态会让全部闹钟失效） */
    fun recentlyForceStopped(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return false
            val last = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 3).firstOrNull() ?: return false
            val recent = System.currentTimeMillis() - last.timestamp < FORCE_STOP_WINDOW_MS
            val forced = last.reason == android.app.ApplicationExitInfo.REASON_USER_REQUESTED &&
                (last.description?.contains("FORCE STOP", true) == true ||
                    last.description?.contains("force-stop", true) == true)
            if (forced) Log.i(TAG, "force stop detected: " + last.description)
            recent && forced
        } catch (e: Exception) {
            Log.w(TAG, "exit-info read failed", e)
            false
        }
    }

    /** 需要引导用户做一次性授权吗 */
    fun needsAttention(ctx: Context): Boolean {
        if (dismissedRecently(ctx)) return false
        return !batteryWhitelisted(ctx) || recentlyForceStopped(ctx)
    }

    fun dismissedRecently(ctx: Context): Boolean {
        val at = sp(ctx).getLong(KEY_DISMISS_AT, 0L)
        return at > 0 && System.currentTimeMillis() - at < DISMISS_TTL_MS
    }

    fun dismiss(ctx: Context) {
        sp(ctx).edit().putLong(KEY_DISMISS_AT, System.currentTimeMillis()).apply()
    }
}
