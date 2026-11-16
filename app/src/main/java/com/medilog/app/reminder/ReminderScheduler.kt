package com.medilog.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medilog.app.data.MedRepository
import com.medilog.app.model.Medication
import com.medilog.app.model.Schedule
import com.medilog.app.util.Prefs
import com.medilog.app.util.TimeUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 管理服药提醒的闹钟。
 *
 * 权限策略（Android 14+ 关键修复）：
 *   - 清单声明 USE_EXACT_ALARM（安装时授予，提醒类应用核心功能即精确到点提醒），
 *     同时保留 SCHEDULE_EXACT_ALARM 作为用户可授权通道。
 *   - Android 14 起新安装应用若只声明 SCHEDULE_EXACT_ALARM，该权限默认拒绝；
 *     USE_EXACT_ALARM 让 canScheduleExactAlarms() 恒为 true。
 */
object ReminderScheduler {
    private const val TAG = "MediLog-Alarm"

    const val ACTION_REMIND = "com.medilog.app.action.REMIND"
    const val ACTION_SNOOZE = "com.medilog.app.action.SNOOZE"
    const val ACTION_TEST = "com.medilog.app.action.TEST"

    /**
     * 每日自检（零配置可靠性的兜底）：
     * 国产 ROM 可能在杀进程/清理内存时丢掉 AlarmManager 里的待触发闹钟。
     * 每天 00:10 由系统唤起本应用一次，重排全部提醒后再武装下一天，
     * 保证"装了就不用管"——即使闹钟被清掉，最迟次日凌晨即可自愈。
     */
    const val ACTION_DAILY_CHECK = "com.medilog.app.action.DAILY_CHECK"

    /** 每日自检的请求码 */
    private const val REQ_DAILY_CHECK = 700001

    const val EXTRA_MED_ID = "med_id"
    const val EXTRA_SCHED_ID = "sched_id"
    const val EXTRA_MED_NAME = "med_name"
    const val EXTRA_STRENGTH = "strength"
    const val EXTRA_PLAN_TIME = "plan_time"
    const val EXTRA_PLAN_DATE = "plan_date"

    /** 测试提醒专用 scheduleId（不会与数据库自增 id 冲突） */
    const val TEST_SCHED_ID = -1L

    private fun reqFor(scheduleId: Long): Int = 100000 + scheduleId.toInt()
    private fun snoozeReqFor(scheduleId: Long): Int = 200000 + scheduleId.toInt()

    /** 锁屏全屏提醒页 / 闹钟 showIntent 的请求码 */
    fun alertReqFor(scheduleId: Long): Int = 400000 + scheduleId.toInt()

    fun notifyIdFor(scheduleId: Long): Int = scheduleId.toInt()

    /** 构造跳转到锁屏全屏提醒页的 Intent */
    fun alertIntent(
        ctx: Context, medId: Long, schedId: Long, medName: String?,
        strength: String?, planTime: String?, planDate: String?
    ): Intent {
        val i = Intent(ctx, ReminderAlertActivity::class.java)
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra(EXTRA_MED_ID, medId)
        i.putExtra(EXTRA_SCHED_ID, schedId)
        i.putExtra(EXTRA_MED_NAME, medName)
        i.putExtra(EXTRA_STRENGTH, strength ?: "")
        i.putExtra(EXTRA_PLAN_TIME, planTime ?: "")
        i.putExtra(EXTRA_PLAN_DATE, planDate ?: TimeUtils.today())
        return i
    }

    /** 当前是否允许设置精确闹钟 */
    fun canScheduleExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return am.canScheduleExactAlarms()
        }
        return true
    }

    /**
     * 统一的闹钟设置入口。
     * - 主提醒（带 showIntent）使用 setAlarmClock：系统闹钟级优先级，
     *   抗 Doze 与国产 ROM 的后台冻结；
     * - 其余（稍后提醒/测试）使用 setExactAndAllowWhileIdle；
     * - 无精确闹钟权限时降级为 setAndAllowWhileIdle。
     */
    private fun setAlarm(
        ctx: Context, am: AlarmManager, kind: String,
        triggerAt: Long, pi: PendingIntent, showPi: PendingIntent?
    ) {
        try {
            if (canScheduleExact(ctx)) {
                if (showPi != null) {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), pi)
                    Log.i(TAG, "$kind alarm-clock set at $triggerAt")
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Log.i(TAG, "$kind exact alarm set at $triggerAt")
                }
            } else {
                // 无精确闹钟权限时的降级通道（可能被 Doze 限流延迟）
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.w(TAG, "$kind exact alarm denied, fallback to inexact (may be delayed by Doze)")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "$kind SecurityException, fallback to inexact", e)
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (ignore: Exception) {
                Log.w(TAG, "$kind inexact fallback also failed", ignore)
            }
        } catch (e: Exception) {
            Log.e(TAG, "$kind set alarm failed", e)
        }
    }

    /** 全量重排（增删改药物、开关、开机后调用）。
     *  审计 P0-4：时间点改为单次批量查询（原为循环内 N+1 次 schedulesOf）。
     *  ⚠️ 本方法含 DB 与 AlarmManager IPC，调用方应在后台线程执行（MainActivity.onResume / BootReceiver 均已异步化）。
     *
     *  单飞（2026-10-25 竞态修复）：onResume 的 3 秒防抖只能挡住主线程侧的重复触发，
     *  挡不住「保存 → rescheduleAsync」与「onResume → scheduleAll」两条后台链路并发
     *  （实测同 PID 313ms 内完整双跑）。scheduleAll 是「先 cancelAll 再逐个 set」的
     *  两段式操作，两轮交叉时后一轮的 cancelAll 会清掉前一轮刚设好的闹钟；若进程恰好
     *  在此窗口内被系统回收，就停在「旧的清了、新的没设」的半程状态 = 全部漏提醒。
     *  因此这里做合并式单飞：已有一轮在执行时只登记一次复跑请求，由执行中的轮次
     *  结束后补跑，保证最终一定有一轮读到最新数据库状态，且任意时刻只有一轮在跑。 */
    fun scheduleAll(ctx: Context) {
        if (!scheduling.compareAndSet(false, true)) {
            rerunRequested.set(true)
            Log.i(TAG, "scheduleAll already in flight, coalesced rerun")
            return
        }
        try {
            do {
                rerunRequested.set(false)
                scheduleAllOnce(ctx)
            } while (rerunRequested.get())
        } finally {
            scheduling.set(false)
        }
    }

    /** 单飞标志：scheduleAll 正在执行中 */
    private val scheduling = AtomicBoolean(false)

    /** 单飞期间到达的新重排请求（合并式：多台请求只补跑一轮） */
    private val rerunRequested = AtomicBoolean(false)

    private fun scheduleAllOnce(ctx: Context) {
        cancelAll(ctx)
        val repo = MedRepository(ctx)
        val prefs = Prefs(ctx)
        if (!prefs.isNotifyEnabled) {
            Log.i(TAG, "scheduleAll skipped: notify disabled in settings")
        } else {
            val schedMap = repo.loadAllSchedules()
            var count = 0
            for (m in repo.listMedications()) {
                if (m.asNeeded || !m.remind) continue
                for (s in schedMap[m.id].orEmpty()) {
                    if (s.enabled) {
                        scheduleOne(ctx, repo, m, s)
                        count++
                    }
                }
            }
            Log.i(TAG, "scheduleAll done: $count reminders, exact=${canScheduleExact(ctx)}")
        }
        // 无论提醒开关如何，都武装每日自检：开关状态随时可变，
        // 自检本身只做重排，不会打扰用户（无通知、无声音）。
        scheduleDailyCheck(ctx)
    }

    /**
     * 取消**单个药品**的全部提醒：待触发闹钟 + 「稍后提醒」闹钟 + 已发出的通知。
     *
     * ⚠️ 必须在数据库删除**之前**调用，并传入删除前查到的 scheduleId 列表：
     * [cancelAll] 是按"当前数据库里还有哪些计划"来反查 PendingIntent 的，
     * 一旦药品先被删掉，这些计划行消失，cancelAll 再也找不到对应闹钟 ——
     * 结果就是「药已经删了，到点还在弹通知」的残留事故。
     */
    fun cancelForMed(ctx: Context, scheduleIds: Collection<Long>) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        var cancelled = 0
        for (sid in scheduleIds) {
            if (am != null) {
                val ri = Intent(ctx, ReminderReceiver::class.java).apply { action = ACTION_REMIND }
                PendingIntent.getBroadcast(ctx, reqFor(sid), ri, flags)?.let {
                    am.cancel(it); it.cancel(); cancelled++
                }
                val si = Intent(ctx, ReminderReceiver::class.java).apply { action = ACTION_SNOOZE }
                PendingIntent.getBroadcast(ctx, snoozeReqFor(sid), si, flags)?.let {
                    am.cancel(it); it.cancel(); cancelled++
                }
            }
            nm?.cancel(notifyIdFor(sid))
        }
        Log.i(TAG, "cancelForMed: ${scheduleIds.size} schedules, $cancelled pending intents cancelled")
    }

    /**
     * 武装「每日自检」闹钟：次日 00:10 触发一次，触发后由 [ReminderReceiver] 负责重排 + 再武装。
     *
     * 选 00:10 而不是 00:00：避开跨日边界上可能存在的时区/日期换算竞争。
     * 用 setExactAndAllowWhileIdle（无精确权限时退化为 setAndAllowWhileIdle）：
     *  - 抗 Doze，不需要电池优化白名单；
     *  - 不弹状态栏闹钟图标（区别于 setAlarmClock），对用户无感。
     */
    fun scheduleDailyCheck(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.action = ACTION_DAILY_CHECK
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, REQ_DAILY_CHECK, i, flags)
        val next = TimeUtils.millisAt(TimeUtils.nextDay(TimeUtils.today()), 0, 10)
        val triggerAt = if (next <= System.currentTimeMillis()) {
            // 理论上不会发生（nextDay 已保证是未来）；兜底 +1 天
            TimeUtils.millisAt(TimeUtils.nextDay(TimeUtils.nextDay(TimeUtils.today())), 0, 10)
        } else {
            next
        }
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(TAG, "daily check armed at $triggerAt")
        } catch (e: Exception) {
            Log.w(TAG, "arm daily check failed", e)
        }
    }

    /** 计算并设置下一次提醒 */
    fun scheduleOne(ctx: Context, repo: MedRepository, m: Medication, s: Schedule) {
        val next = nextTrigger(ctx, repo, m, s)
        if (next <= 0) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val i = Intent(ctx, ReminderReceiver::class.java)
        i.action = ACTION_REMIND
        i.putExtra(EXTRA_MED_ID, m.id)
        i.putExtra(EXTRA_SCHED_ID, s.id)
        i.putExtra(EXTRA_MED_NAME, m.name)
        i.putExtra(EXTRA_STRENGTH, m.strength ?: "")
        i.putExtra(EXTRA_PLAN_TIME, s.hhmm())
        i.putExtra(EXTRA_PLAN_DATE, TimeUtils.today())

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, reqFor(s.id), i, flags)
        // showIntent：用户点击状态栏/锁屏的「即将响铃」时直接打开全屏提醒页
        val showPi = PendingIntent.getActivity(
            ctx, alertReqFor(s.id),
            alertIntent(ctx, m.id, s.id, m.name, m.strength, s.hhmm(), TimeUtils.today()),
            flags
        )
        setAlarm(ctx, am, "remind(med=${m.id},sched=${s.id})", next, pi, showPi)
    }

    /** 稍后提醒：snoozeMinutes 分钟后再次触发 */
    fun scheduleSnooze(
        ctx: Context, scheduleId: Long, medId: Long, medName: String?,
        strength: String?, planTime: String?, snoozeMinutes: Int
    ) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + Math.max(1, snoozeMinutes) * 60_000L
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.action = ACTION_SNOOZE
        i.putExtra(EXTRA_MED_ID, medId)
        i.putExtra(EXTRA_SCHED_ID, scheduleId)
        i.putExtra(EXTRA_MED_NAME, medName)
        i.putExtra(EXTRA_STRENGTH, strength)
        i.putExtra(EXTRA_PLAN_TIME, planTime)
        i.putExtra(EXTRA_PLAN_DATE, TimeUtils.today())
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, snoozeReqFor(scheduleId), i, flags)
        setAlarm(ctx, am, "snooze(sched=$scheduleId)", at, pi, null)
    }

    /** 测试提醒：10 秒后走完整提醒链路（闹钟 → 广播 → 通知+声音） */
    fun scheduleTest(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + 10_000L
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.action = ACTION_TEST
        i.putExtra(EXTRA_SCHED_ID, TEST_SCHED_ID)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, 300001, i, flags)
        setAlarm(ctx, am, "test", at, pi, null)
    }

    /** 下一次触发的时间戳；不在疗程内或已停用则 <=0 */
    fun nextTrigger(ctx: Context, repo: MedRepository, m: Medication, s: Schedule): Long {
        val prefs = Prefs(ctx)
        val lead = prefs.leadMinutes
        val today = TimeUtils.today()
        if (!m.isActiveOn(today)) {
            // 已过期：找疗程开始日当天（可能在未来开始）
            // 审计 P1-4：startDate 为 public var，取本地 val 替代 !!
            val sd = m.startDate
            if (sd != null && sd.compareTo(today) > 0) {
                return TimeUtils.millisAt(sd, s.hour, s.minute) - lead * 60_000L
            }
            return -1
        }
        var t = TimeUtils.millisAt(today, s.hour, s.minute) - lead * 60_000L
        if (t <= System.currentTimeMillis()) {
            // 提前量窗口已过，但**服药时刻本身还没到**时，不能整天顺延：
            // 例）现在 10:00，用户设 10:10、提前提醒 15 分钟 → 09:55 已过。
            // 旧逻辑直接跳到明天 09:55，今天的 10:10 这一剂被静默丢失
            // （表现为「设了提醒当天不响」，而临近时刻未启用提前量时却正常）。
            // 修正：剂量时刻仍在未来 → 放弃本次提前量，按剂量时刻准点提醒。
            val doseAt = TimeUtils.millisAt(today, s.hour, s.minute)
            t = if (doseAt > System.currentTimeMillis()) {
                doseAt
            } else {
                TimeUtils.millisAt(TimeUtils.nextDay(today), s.hour, s.minute) - lead * 60_000L
            }
        }
        if (t <= 0) return -1
        return t
    }

    /**
     * 取消全部待触发的提醒闹钟。
     * 注意：不再取消「稍后提醒」闹钟——scheduleAll 在每次 App 回到前台时都会执行，
     * 若一并取消会导致用户刚设的稍后提醒被清掉。
     */
    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val repo = MedRepository(ctx)
        // 审计 P0-4：单次批量查询替代 1+N 次 schedulesOf
        val schedMap = repo.loadAllSchedules()
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        for ((_, scheds) in schedMap) {
            for (s in scheds) {
                val i = Intent(ctx, ReminderReceiver::class.java)
                i.action = ACTION_REMIND
                val pi = PendingIntent.getBroadcast(ctx, reqFor(s.id), i, flags)
                if (pi != null) {
                    am.cancel(pi)
                    pi.cancel()
                }
            }
        }
        // 取消可能残留的测试提醒
        val t = Intent(ctx, ReminderReceiver::class.java)
        t.action = ACTION_TEST
        val tpi = PendingIntent.getBroadcast(ctx, 300001, t, flags)
        if (tpi != null) {
            am.cancel(tpi)
            tpi.cancel()
        }
        // 注意：不取消每日自检（scheduleAll 末尾会重新武装它）
    }
}
