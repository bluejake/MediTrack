package com.medilog.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.medilog.app.util.CrashHandler

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        createChannels()
    }

    private fun createChannels() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        var existing = nm.getNotificationChannel(CHANNEL_REMINDER)
        // 渠道一旦创建，重要性无法通过 update 提升；若历史版本建成了低优先级
        // （无声音/不弹窗），删除后重建，保证升级用户也能正常收到响铃提醒。
        if (existing != null && existing.importance < NotificationManager.IMPORTANCE_HIGH) {
            Log.i(TAG, "reminder channel importance too low (${existing.importance}), recreate")
            nm.deleteNotificationChannel(CHANNEL_REMINDER)
            existing = null
        }
        if (existing == null) {
            val c = NotificationChannel(
                CHANNEL_REMINDER,
                getString(R.string.channel_reminder),
                NotificationManager.IMPORTANCE_HIGH
            )
            c.description = getString(R.string.channel_desc)
            c.enableVibration(true)
            c.enableLights(true)
            nm.createNotificationChannel(c)
            Log.i(TAG, "reminder channel created (IMPORTANCE_HIGH)")
        }

        // 前台服务常驻通知的静默渠道：IMPORTANCE_MIN、无声、无角标。
        // 供 ReminderOverlayService（弹窗前台服务）使用，目的只是满足 FGS 的通知要求，
        // 同时把应用标记为「活跃」以降低被系统管家强停的概率，绝不能打扰用户。
        // 渠道名复用 channel_reminder（与真机版一致，不额外提供渠道文案）。
        if (nm.getNotificationChannel(CHANNEL_SERVICE) == null) {
            val s = NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.channel_reminder),
                NotificationManager.IMPORTANCE_MIN
            )
            s.enableVibration(false)
            s.enableLights(false)
            s.setShowBadge(false)
            nm.createNotificationChannel(s)
            Log.i(TAG, "service channel created (IMPORTANCE_MIN)")
        }
    }

    companion object {
        private const val TAG = "MediLog-App"
        const val CHANNEL_REMINDER = "medilog_reminder"

        /** 前台服务静默渠道（提醒弹窗前台通知） */
        const val CHANNEL_SERVICE = "medilog_reminder_service"
    }
}
