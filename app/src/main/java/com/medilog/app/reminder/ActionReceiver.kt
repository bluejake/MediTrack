package com.medilog.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.medilog.app.data.DataBus
import com.medilog.app.data.MedRepository
import com.medilog.app.model.DoseEvent
import com.medilog.app.model.Schedule
import com.medilog.app.util.Prefs
import com.medilog.app.util.TimeUtils

/** 处理通知栏上的「已服用 / 稍后」动作 */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action
        val medId = intent.getLongExtra(ReminderScheduler.EXTRA_MED_ID, -1L)
        val schedId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHED_ID, -1L)
        val medName = intent.getStringExtra(ReminderScheduler.EXTRA_MED_NAME)
        val strength = intent.getStringExtra(ReminderScheduler.EXTRA_STRENGTH)
        val planTime = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_TIME)
        var planDate = intent.getStringExtra(ReminderScheduler.EXTRA_PLAN_DATE)
        if (planDate == null) planDate = TimeUtils.today()

        try {
            NotificationManagerCompat.from(ctx).cancel(ReminderScheduler.notifyIdFor(schedId))
        } catch (ignore: Exception) {
            Log.w(TAG, "取消通知失败（可能已被系统清理）", ignore)
        }

        if (ACTION_TAKEN == action) {
            val repo = MedRepository(ctx)
            val m = repo.getMedication(medId)
            if (m != null) {
                val e = DoseEvent()
                e.med = m
                e.planDate = planDate
                e.planTime = planTime
                e.schedule = repo.schedulesOf(m.id).firstOrNull { it.id == schedId }
                if (e.schedule == null) {
                    e.schedule = Schedule().also {
                        it.id = schedId
                        it.hour = 0
                        it.minute = 0
                    }
                }
                repo.takeScheduled(e, System.currentTimeMillis())
                // 通知栏记录完成后广播数据版本：应用内界面（含停留在"今天"页）立即刷新，
                // 无需切换页面。见 DataBus 说明。
                DataBus.bump()
                Log.i(TAG, "taken from notification: med=$medId sched=$schedId, data version bumped")
            }
        } else if (ACTION_SNOOZE == action) {
            val prefs = Prefs(ctx)
            ReminderScheduler.scheduleSnooze(
                ctx, schedId, medId, medName ?: "", strength ?: "",
                planTime, prefs.snoozeMinutes
            )
            DataBus.bump()
        } else if (ACTION_DISMISS == action) {
            // 划掉通知等同于「关闭」：仅通知弹窗收起，不写服药记录。
            // 通知本身已在方法开头 cancel，这里只需把收起指令转给弹窗服务。
            try {
                ctx.sendBroadcast(
                    Intent(ReminderOverlayService.ACTION_DISMISS_OVERLAY)
                        .setPackage(ctx.packageName)
                )
                Log.i(TAG, "notification dismissed: med=$medId sched=$schedId")
            } catch (e: Exception) {
                Log.w(TAG, "broadcast overlay dismiss failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "ActionReceiver"

        const val ACTION_TAKEN = "com.medilog.app.action.TAKE"
        const val ACTION_SNOOZE = "com.medilog.app.action.SNOOZE_USER"

        /** 通知被划掉（setDeleteIntent）：只收起弹窗，不写服药记录 */
        const val ACTION_DISMISS = "com.medilog.app.action.DISMISS"
    }
}
