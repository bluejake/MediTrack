package com.medilog.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.medilog.app.App

/** 系统设置页跳转工具（均做异常兜底，避免个别定制 ROM 无对应页面时崩溃） */
object NavUtil {
    private const val TAG = "MediLog-Nav"

    private fun safeStart(ctx: Context, intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "start settings failed: " + intent.action, e)
            openAppDetails(ctx)
        }
    }

    /** 应用详情页（最终兜底） */
    fun openAppDetails(ctx: Context) {
        safeStart(
            ctx,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", ctx.packageName, null)
            )
        )
    }

    /** 本应用的通知设置页 */
    fun openNotificationSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeStart(
                ctx,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            )
        } else {
            openAppDetails(ctx)
        }
    }

    /** 「服药提醒」渠道设置页 */
    fun openChannelSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeStart(
                ctx,
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, App.CHANNEL_REMINDER)
            )
        } else {
            openNotificationSettings(ctx)
        }
    }

    /** 精确闹钟授权页（Android 12+；正常情况 USE_EXACT_ALARM 安装即授，此为兜底） */
    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            safeStart(
                ctx,
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.fromParts("package", ctx.packageName, null)
                )
            )
        } else {
            openAppDetails(ctx)
        }
    }

    /** 电池优化设置列表 */
    fun openBatteryOptimizationSettings(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** 全屏 Intent 权限页（Android 14+ 特殊应用访问权限） */
    fun openFullScreenIntentSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 34) {
            safeStart(
                ctx,
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.fromParts("package", ctx.packageName, null)
                )
            )
        } else {
            openAppDetails(ctx)
        }
    }

    /** 直接申请加入电池优化白名单（系统弹窗确认） */
    fun requestIgnoreBatteryOptimizations(ctx: Context) {
        safeStart(
            ctx,
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + ctx.packageName)
            )
        )
    }
}
