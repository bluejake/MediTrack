package com.medilog.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机 / 时间变更 / 应用升级后重建全部闹钟。
 *
 * 零配置与健壮性要点：
 * - **异步执行**：scheduleAll 含 SQLite 查询 + AlarmManager IPC，在广播主线程同步跑有 ANR 风险
 *   （开机瞬间系统负载高，更容易触发）。用 goAsync() 保住广播生命周期，放到 IO 线程执行。
 * - **保底完成**：无论成功失败都 finish()，避免挂住广播被系统判 ANR。
 * - 覆盖 BOOT_COMPLETED（开机）、MY_PACKAGE_REPLACED（覆盖安装）、
 *   TIME_SET / TIMEZONE_CHANGED（用户改时间/时区 → 闹钟时间基准变了，必须重排）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        Log.i(TAG, "onReceive ${intent.action}, rescheduling alarms")
        val pending = goAsync()
        val appCtx = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.scheduleAll(appCtx)
            } catch (e: Exception) {
                Log.e(TAG, "reschedule failed", e)
            } finally {
                try {
                    pending.finish()
                } catch (ignore: Exception) {
                    Log.w(TAG, "finish pending result failed", ignore)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MediLog-Boot"
    }
}
