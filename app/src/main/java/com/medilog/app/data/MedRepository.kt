package com.medilog.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.medilog.app.R
import com.medilog.app.model.DoseEvent
import com.medilog.app.model.DoseLog
import com.medilog.app.model.Medication
import com.medilog.app.model.Schedule
import com.medilog.app.util.TimeUtils
import java.util.Locale

class MedRepository(ctx: Context) {    private val dbh = DbHelper(ctx.applicationContext)

    // ---------- 药物 ----------

    fun insertMedication(m: Medication, schedules: List<Schedule>): Long {
        val db = dbh.writableDatabase
        val cv = ContentValues()
        cv.put("name", m.name)
        cv.put("form", m.form)
        cv.put("strength", m.strength)
        cv.put("icon", m.icon)
        cv.put("color", m.color)
        cv.put("as_needed", if (m.asNeeded) 1 else 0)
        cv.put("start_date", m.startDate)
        cv.put("duration_days", m.durationDays)
        cv.put("remind", if (m.remind) 1 else 0)
        cv.put("notes", m.notes)
        cv.put("created_at", System.currentTimeMillis())
        val id = db.insert(DbHelper.T_MED, null, cv)
        replaceSchedules(id, schedules)
        return id
    }

    fun updateMedication(m: Medication, schedules: List<Schedule>) {
        val db = dbh.writableDatabase
        val cv = ContentValues()
        cv.put("name", m.name)
        cv.put("form", m.form)
        cv.put("strength", m.strength)
        cv.put("icon", m.icon)
        cv.put("color", m.color)
        cv.put("as_needed", if (m.asNeeded) 1 else 0)
        cv.put("start_date", m.startDate)
        cv.put("duration_days", m.durationDays)
        cv.put("remind", if (m.remind) 1 else 0)
        cv.put("notes", m.notes)
        db.update(DbHelper.T_MED, cv, "id=?", arrayOf(m.id.toString()))
        replaceSchedules(m.id, schedules)
    }

    private fun replaceSchedules(medId: Long, schedules: List<Schedule>) {
        val db = dbh.writableDatabase
        db.delete(DbHelper.T_SCHED, "med_id=?", arrayOf(medId.toString()))
        for (s in schedules) {
            val cv = ContentValues()
            cv.put("med_id", medId)
            cv.put("hour", s.hour)
            cv.put("minute", s.minute)
            cv.put("enabled", if (s.enabled) 1 else 0)
            db.insert(DbHelper.T_SCHED, null, cv)
        }
    }

    /**
     * 事务化删除单个药品及其全部关联数据（服药记录 → 提醒计划 → 药品本身）。
     *
     * 为什么必须是事务：删除分三步，任一步抛异常（磁盘满 / 数据库被锁 / 进程被杀）
     * 都会留下"药品没了但计划还在"或"计划没了但药品还在"的残行，
     * 残行会让详情页查不到数据、列表出现幽灵条目、统计口径错乱。
     * 放进一个事务后，三步要么全部生效要么全部回滚。
     *
     * @return true 表示确实删掉了一条药品（false = 该 id 本就不存在）
     */
    fun deleteMedication(medId: Long): Boolean {
        val db = dbh.writableDatabase
        var ok = false
        db.beginTransaction()
        try {
            db.delete(DbHelper.T_LOG, "med_id=?", arrayOf(medId.toString()))
            db.delete(DbHelper.T_SCHED, "med_id=?", arrayOf(medId.toString()))
            val rows = db.delete(DbHelper.T_MED, "id=?", arrayOf(medId.toString()))
            ok = rows > 0
            if (ok) db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ok
    }

    /** 事务化清空全部业务数据（设置页「清除数据」/「载入示例」用）。 */
    fun deleteAllMedications() {
        val db = dbh.writableDatabase
        db.beginTransaction()
        try {
            db.delete(DbHelper.T_LOG, null, null)
            db.delete(DbHelper.T_SCHED, null, null)
            db.delete(DbHelper.T_MED, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 取某药品的全部提醒计划 id。
     * 必须在删除**之前**调用：删除后行已不存在，AlarmManager 里对应的
     * PendingIntent 就成了永远无法取消的孤儿闹钟（药删了还会响）。
     */
    fun scheduleIdsOf(medId: Long): List<Long> {
        val out = ArrayList<Long>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_SCHED, arrayOf("id"), "med_id=?",
            arrayOf(medId.toString()), null, null, null
        ).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0))
        }
        return out
    }

    /**
     * 取一次删除所需的完整快照（药品 + 计划 + 记录，含原始 id）。
     * 必须在删除**之前**调用。
     */
    fun snapshotOf(medId: Long): com.medilog.app.model.MedSnapshot? {
        val m = getMedication(medId) ?: return null
        return com.medilog.app.model.MedSnapshot(
            med = m,
            schedules = schedulesOf(medId),
            logs = rawLogsOf(medId)
        )
    }

    /** 药品的全部服药记录（原始行，不带 UI 装饰）。 */
    fun rawLogsOf(medId: Long): List<com.medilog.app.model.DoseLog> {
        val out = ArrayList<com.medilog.app.model.DoseLog>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_LOG, null, "med_id=?", arrayOf(medId.toString()),
            null, null, "id ASC"
        ).use { c ->
            while (c.moveToNext()) out.add(readLog(c))
        }
        return out
    }

    /**
     * 按快照**原样还原**（含原始 id），用于撤销删除。
     *
     * 整个过程在单个事务里完成：要么还原出一个完整可用的药品，要么一行都不写，
     * 绝不出现"药品回来了但计划/记录丢了"的半截状态。
     */
    fun restore(snapshot: com.medilog.app.model.MedSnapshot): Boolean {
        val db = dbh.writableDatabase
        var ok = false
        db.beginTransaction()
        try {
            val m = snapshot.med
            val cv = ContentValues()
            cv.put("id", m.id)
            cv.put("name", m.name)
            cv.put("form", m.form)
            cv.put("strength", m.strength)
            cv.put("icon", m.icon)
            cv.put("color", m.color)
            cv.put("as_needed", if (m.asNeeded) 1 else 0)
            cv.put("start_date", m.startDate)
            cv.put("duration_days", m.durationDays)
            cv.put("remind", if (m.remind) 1 else 0)
            cv.put("notes", m.notes)
            cv.put("created_at", m.createdAt)
            if (db.insert(DbHelper.T_MED, null, cv) <= 0) return false

            for (s in snapshot.schedules) {
                val scv = ContentValues()
                scv.put("id", s.id)
                scv.put("med_id", s.medId)
                scv.put("hour", s.hour)
                scv.put("minute", s.minute)
                scv.put("enabled", if (s.enabled) 1 else 0)
                db.insert(DbHelper.T_SCHED, null, scv)
            }
            for (l in snapshot.logs) {
                val lcv = ContentValues()
                lcv.put("id", l.id)
                lcv.put("med_id", l.medId)
                lcv.put("schedule_id", l.scheduleId)
                lcv.put("plan_date", l.planDate)
                lcv.put("plan_time", l.planTime)
                lcv.put("actual_at", l.actualAt)
                lcv.put("status", l.status)
                db.insert(DbHelper.T_LOG, null, lcv)
            }
            ok = true
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ok
    }

    fun listMedications(): List<Medication> {
        val out = ArrayList<Medication>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_MED, null, null, null, null, null,
            "CASE WHEN as_needed=1 THEN 1 ELSE 0 END, start_date ASC, id ASC"
        ).use { c ->
            while (c.moveToNext()) out.add(readMed(c))
        }
        return out
    }

    fun getMedication(id: Long): Medication? {
        val db = dbh.readableDatabase
        var m: Medication? = null
        db.query(DbHelper.T_MED, null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) m = readMed(c)
        }
        return m
    }

    private fun readMed(c: Cursor): Medication {
        val m = Medication()
        m.id = c.getLong(c.getColumnIndexOrThrow("id"))
        m.name = c.getString(c.getColumnIndexOrThrow("name"))
        m.form = c.getString(c.getColumnIndexOrThrow("form"))
        m.strength = c.getString(c.getColumnIndexOrThrow("strength"))
        m.icon = c.getInt(c.getColumnIndexOrThrow("icon"))
        m.color = c.getInt(c.getColumnIndexOrThrow("color"))
        m.asNeeded = c.getInt(c.getColumnIndexOrThrow("as_needed")) == 1
        m.startDate = c.getString(c.getColumnIndexOrThrow("start_date"))
        m.durationDays = c.getInt(c.getColumnIndexOrThrow("duration_days"))
        m.remind = c.getInt(c.getColumnIndexOrThrow("remind")) == 1
        m.notes = c.getString(c.getColumnIndexOrThrow("notes"))
        m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        return m
    }

    fun schedulesOf(medId: Long): List<Schedule> {
        val out = ArrayList<Schedule>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_SCHED, null, "med_id=?", arrayOf(medId.toString()),
            null, null, "hour*60+minute ASC"
        ).use { c ->
            while (c.moveToNext()) out.add(readSched(c))
        }
        return out
    }

    private fun readSched(c: Cursor): Schedule {
        val s = Schedule()
        s.id = c.getLong(c.getColumnIndexOrThrow("id"))
        s.medId = c.getLong(c.getColumnIndexOrThrow("med_id"))
        s.hour = c.getInt(c.getColumnIndexOrThrow("hour"))
        s.minute = c.getInt(c.getColumnIndexOrThrow("minute"))
        s.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1
        return s
    }

    // ---------- 今日列表 ----------

    fun buildTodayEvents(date: String): List<DoseEvent> {
        val meds = listMedications()
        val schedMap = loadAllSchedules()

        // 当日记录按 (medId:scheduleId) 建索引，取 id 最大的一条（与原 findLog 的 id DESC 一致）
        val logMap = HashMap<String, DoseLog>()
        val db = dbh.readableDatabase
        db.query(DbHelper.T_LOG, null, "plan_date=?", arrayOf(date), null, null, null).use { c ->
            while (c.moveToNext()) {
                val l = readLog(c)
                val key = "${l.medId}:${l.scheduleId}"
                val prev = logMap[key]
                if (prev == null || l.id > prev.id) logMap[key] = l
            }
        }

        val out = ArrayList<DoseEvent>()
        for (m in meds) {
            if (!m.isActiveOn(date)) continue
            if (m.asNeeded) {
                val e = DoseEvent()
                e.med = m
                e.schedule = null
                e.planTime = null
                e.status = DoseEvent.AS_NEEDED
                out.add(e)
                continue
            }
            val ss = schedMap[m.id] ?: continue
            for (s in ss) {
                if (!s.enabled) continue
                val e = DoseEvent()
                e.med = m
                e.schedule = s
                e.planDate = date
                e.planTime = s.hhmm()
                val log = logMap["${m.id}:${s.id}"]
                if (log != null) {
                    e.logId = log.id
                    e.actualAt = log.actualAt
                    e.status = if (log.isTaken()) DoseEvent.TAKEN else DoseEvent.SKIPPED
                } else if (TimeUtils.passed(date, s.hhmm())) {
                    e.status = DoseEvent.OVERDUE
                } else {
                    e.status = DoseEvent.PENDING
                }
                out.add(e)
            }
        }
        // 排序：按计划时间；按需放最后
        out.sortWith { a, b ->
            // 审计 P1-4：DoseEvent 的 planTime/med 是 public var（无法智能转型），
            // 取本地 val 后判空，替代 !! 强解包
            val at = a.planTime
            val bt = b.planTime
            val aMedId = a.med?.id ?: 0L
            val bMedId = b.med?.id ?: 0L
            if (at == null && bt == null) return@sortWith aMedId.compareTo(bMedId)
            if (at == null) return@sortWith 1
            if (bt == null) return@sortWith -1
            val cmp = at.compareTo(bt)
            if (cmp != 0) return@sortWith cmp
            aMedId.compareTo(bMedId)
        }
        return out
    }

    private fun readLog(c: Cursor): DoseLog {
        val l = DoseLog()
        l.id = c.getLong(c.getColumnIndexOrThrow("id"))
        l.medId = c.getLong(c.getColumnIndexOrThrow("med_id"))
        l.scheduleId = c.getLong(c.getColumnIndexOrThrow("schedule_id"))
        l.planDate = c.getString(c.getColumnIndexOrThrow("plan_date"))
        l.planTime = c.getString(c.getColumnIndexOrThrow("plan_time"))
        l.actualAt = c.getLong(c.getColumnIndexOrThrow("actual_at"))
        l.status = c.getString(c.getColumnIndexOrThrow("status"))
        return l
    }

    // ---------- 记录操作 ----------

    fun takeScheduled(e: DoseEvent, actualAt: Long) {
        // 审计 P1-4：字段缺失时打警告并返回，替代 !! 崩溃
        val med = e.med ?: run { Log.w(TAG, "takeScheduled: DoseEvent.med 为空，已忽略"); return }
        val sch = e.schedule ?: run { Log.w(TAG, "takeScheduled: DoseEvent.schedule 为空，已忽略"); return }
        val pd = e.planDate ?: run { Log.w(TAG, "takeScheduled: DoseEvent.planDate 为空，已忽略"); return }
        upsertLog(
            med.id, sch.id, sch.hhmm(), actualAt,
            DoseLog.TAKEN, pd
        )
    }

    fun skipScheduled(e: DoseEvent, actualAt: Long) {
        // 审计 P1-4：同上
        val med = e.med ?: run { Log.w(TAG, "skipScheduled: DoseEvent.med 为空，已忽略"); return }
        val sch = e.schedule ?: run { Log.w(TAG, "skipScheduled: DoseEvent.schedule 为空，已忽略"); return }
        val pd = e.planDate ?: run { Log.w(TAG, "skipScheduled: DoseEvent.planDate 为空，已忽略"); return }
        upsertLog(
            med.id, sch.id, sch.hhmm(), actualAt,
            DoseLog.SKIPPED, pd
        )
    }

    private fun upsertLog(
        medId: Long, scheduleId: Long, planTime: String?, actualAt: Long,
        status: String, planDate: String
    ) {
        val db = dbh.writableDatabase
        db.query(
            DbHelper.T_LOG, null,
            "med_id=? AND schedule_id=? AND plan_date=?",
            arrayOf(medId.toString(), scheduleId.toString(), planDate),
            null, null, null
        ).use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow("id"))
                val cv = ContentValues()
                cv.put("status", status)
                cv.put("actual_at", actualAt)
                db.update(DbHelper.T_LOG, cv, "id=?", arrayOf(id.toString()))
            } else {
                val cv = ContentValues()
                cv.put("med_id", medId)
                cv.put("schedule_id", scheduleId)
                cv.put("plan_date", planDate)
                cv.put("plan_time", planTime)
                cv.put("actual_at", actualAt)
                cv.put("status", status)
                db.insert(DbHelper.T_LOG, null, cv)
            }
        }
    }

    fun takeAsNeeded(m: Medication, actualAt: Long) {
        val db = dbh.writableDatabase
        val cv = ContentValues()
        cv.put("med_id", m.id)
        cv.put("schedule_id", 0L)
        cv.put("plan_date", TimeUtils.today())
        cv.put("plan_time", null as String?)
        cv.put("actual_at", actualAt)
        cv.put("status", DoseLog.TAKEN)
        db.insert(DbHelper.T_LOG, null, cv)
    }

    fun undoLog(logId: Long) {
        val db = dbh.writableDatabase
        db.delete(DbHelper.T_LOG, "id=?", arrayOf(logId.toString()))
    }

    /** 该剂量当日是否已记录（已服用/已跳过），用于稍后提醒的抑制判断（单次点查，非 N+1） */
    fun hasLogFor(medId: Long, scheduleId: Long, planDate: String): Boolean {
        val db = dbh.readableDatabase
        var found = false
        db.query(
            DbHelper.T_LOG, arrayOf("id"),
            "med_id=? AND schedule_id=? AND plan_date=?",
            arrayOf(medId.toString(), scheduleId.toString(), planDate),
            null, null, null, "1"
        ).use { c -> found = c.moveToFirst() }
        return found
    }

    // ---------- 历史 ----------

    class LogItem {
        var log: DoseLog = DoseLog()
        var medName: String? = null
        var form: String? = null
        var color: Int = 0
        var icon: Int = 0

        fun desc(ctx: Context): String {
            if (log.planTime.isNullOrEmpty()) {
                return ctx.getString(R.string.section_as_needed)
            }
            return ctx.getString(R.string.alert_plan, log.planTime)
        }
    }

    fun logsOfDate(date: String): List<LogItem> {
        val out = ArrayList<LogItem>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_LOG + " l LEFT JOIN " + DbHelper.T_MED + " m ON l.med_id=m.id",
            arrayOf("l.*", "m.name as med_name", "m.form as med_form", "m.color as med_color", "m.icon as med_icon"),
            "l.plan_date=?", arrayOf(date), null, null, "l.actual_at DESC, l.id DESC"
        ).use { c ->
            while (c.moveToNext()) {
                val it = LogItem()
                it.log = readLog(c)
                it.medName = c.getString(c.getColumnIndexOrThrow("med_name"))
                it.form = c.getString(c.getColumnIndexOrThrow("med_form"))
                it.color = c.getInt(c.getColumnIndexOrThrow("med_color"))
                it.icon = c.getInt(c.getColumnIndexOrThrow("med_icon"))
                out.add(it)
            }
        }
        return out
    }

    fun logsInMonth(ym: String): List<LogItem> {
        val out = ArrayList<LogItem>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_LOG + " l LEFT JOIN " + DbHelper.T_MED + " m ON l.med_id=m.id",
            arrayOf("l.*", "m.name as med_name", "m.form as med_form", "m.color as med_color", "m.icon as med_icon"),
            "l.plan_date LIKE ?", arrayOf("$ym%"), null, null, "l.plan_date DESC, l.actual_at DESC, l.id DESC"
        ).use { c ->
            while (c.moveToNext()) {
                val it = LogItem()
                it.log = readLog(c)
                it.medName = c.getString(c.getColumnIndexOrThrow("med_name"))
                it.form = c.getString(c.getColumnIndexOrThrow("med_form"))
                it.color = c.getInt(c.getColumnIndexOrThrow("med_color"))
                it.icon = c.getInt(c.getColumnIndexOrThrow("med_icon"))
                out.add(it)
            }
        }
        return out
    }

    fun logsForMed(medId: Long): List<LogItem> {
        val out = ArrayList<LogItem>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_LOG, null, "med_id=?", arrayOf(medId.toString()),
            null, null, "plan_date DESC, actual_at DESC"
        ).use { c ->
            val m = getMedication(medId)
            while (c.moveToNext()) {
                val it = LogItem()
                it.log = readLog(c)
                it.medName = if (m == null) "" else m.name
                it.form = if (m == null) "" else m.form
                it.color = if (m == null) 0 else m.color
                it.icon = if (m == null) 0 else m.icon
                out.add(it)
            }
        }
        return out
    }

    // ---------- 统计 ----------

    class DayStat {
        var date: String? = null
        var expected: Int = 0
        var taken: Int = 0
        var skipped: Int = 0

        fun missed(): Int = Math.max(0, expected - taken - skipped)

        /** 依从率百分比；expected<=0 时返回 -1 表示无计划 */
        fun rate(): Double = if (expected <= 0) -1.0 else taken * 100.0 / expected
    }

    /** 计算指定日期的统计 */
    fun statOf(date: String): DayStat? = computeStatsRange(date, date)[date]

    /** 月历数据：ym 中每一天的统计（一次性批量计算，避免逐日查询） */
    fun monthStats(ym: String): Map<String, DayStat> {
        val days = TimeUtils.daysInMonth(ym)
        val from = "$ym-01"
        val to = "$ym-" + String.format(Locale.US, "%02d", days)
        return computeStatsRange(from, to)
    }

    /** 区间统计（累加区间内每日统计，同样走批量计算） */
    fun statsRange(from: String, to: String): DayStat {
        val st = DayStat()
        st.date = "$from~$to"
        for (s in computeStatsRange(from, to).values) {
            st.expected += s.expected
            st.taken += s.taken
            st.skipped += s.skipped
        }
        return st
    }

    /**
     * **单味药**在 [from, to] 区间内的统计（药物详情页用）。
     *
     * 与 [statsRange] 的区别：那个是"全部药物"的横向汇总（首页进度环 / 月历用），
     * 详情页的「近 30 天依从率」必须只算这一味药 —— 否则按需服用药品（无计划、
     * 无记录）的详情页会显示别人的应服/已服数（真机实测曾出现 应服106/已服32）。
     *
     * 口径与全局一致：按启用计划数 × 区间内「有效日数」算应服，as_needed 与已停用
     * 计划不计入。
     */
    fun statsRangeForMed(medId: Long, from: String, to: String): DayStat {
        val st = DayStat()
        st.date = "$from~$to"
        val m = getMedication(medId) ?: return st
        if (m.asNeeded) return st
        val cnt = (loadAllSchedules()[medId] ?: return st).count { it.enabled }
        if (cnt == 0) return st
        var expected = 0
        var d = from
        while (d <= to) {
            if (m.isActiveOn(d)) expected += cnt
            d = TimeUtils.nextDay(d)
        }
        st.expected = expected
        dbh.readableDatabase.rawQuery(
            "SELECT plan_date, status, COUNT(*) FROM ${DbHelper.T_LOG}" +
                " WHERE med_id=? AND plan_date>=? AND plan_date<=? GROUP BY plan_date, status",
            arrayOf(medId.toString(), from, to)
        ).use { c ->
            while (c.moveToNext()) {
                val n = c.getInt(2)
                when (c.getString(1)) {
                    DoseLog.TAKEN -> st.taken += n
                    DoseLog.SKIPPED -> st.skipped += n
                }
            }
        }
        return st
    }

    /**
     * 批量计算 [from, to] 区间内每一天的统计。
     * 仅 3 次查询：全部药物 / 全部计划 / 区间内的全部记录；其余在内存聚合，
     * 取代原先「每天 × 每种药」的 N+1 查询。
     */
    private fun computeStatsRange(from: String, to: String): Map<String, DayStat> {
        val meds = listMedications()

        // 每种药启用的计划数（expected 的基数）
        val enabledSchedCount = HashMap<Long, Int>()
        for ((medId, scheds) in loadAllSchedules()) {
            var cnt = 0
            for (s in scheds) if (s.enabled) cnt++
            enabledSchedCount[medId] = cnt
        }

        // 区间内每日的已服/已跳计数（一次范围查询，走 idx_log_date）
        val logCounts = HashMap<String, LogCounts>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_LOG, arrayOf("plan_date", "status"),
            "plan_date >= ? AND plan_date <= ?", arrayOf(from, to), null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                val d = c.getString(0)
                val st = c.getString(1)
                val lc = logCounts.getOrPut(d) { LogCounts() }
                if (DoseLog.TAKEN == st) lc.taken++ else if (DoseLog.SKIPPED == st) lc.skipped++
            }
        }

        val result = LinkedHashMap<String, DayStat>()
        var d = from
        while (d <= to) {
            val st = DayStat()
            st.date = d
            var expected = 0
            for (m in meds) {
                if (m.asNeeded || !m.isActiveOn(d)) continue
                val cnt = enabledSchedCount[m.id] ?: continue
                expected += cnt
            }
            st.expected = expected
            logCounts[d]?.let {
                st.taken = it.taken
                st.skipped = it.skipped
            }
            result[d] = st
            d = TimeUtils.nextDay(d)
        }
        return result
    }

    /** 一次性加载全部计划，按 medId 分组（取代逐药 schedulesOf 的 N 次查询） */
    /** 全部时间点按 medId 分组（单次查询）。审计 P0-4：供 scheduleAll/cancelAll 消除 N+1 */
    fun loadAllSchedules(): Map<Long, List<Schedule>> {
        val map = HashMap<Long, MutableList<Schedule>>()
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_SCHED, null, null, null, null, null,
            "med_id ASC, hour*60+minute ASC"
        ).use { c ->
            while (c.moveToNext()) {
                val s = readSched(c)
                map.getOrPut(s.medId) { ArrayList() }.add(s)
            }
        }
        return map
    }

    /** 单日记录计数（用于统计聚合） */
    private class LogCounts {
        var taken = 0
        var skipped = 0
    }

    fun hasAnyData(): Boolean {
        // 审计 P0-5：EXISTS 短路（命中第一条即返回），替代原先 2 次全表 COUNT(*)。
        // 该方法在 MainActivity.onCreate 的启动路径上被调用，必须保持 O(1) 级开销。
        val db = dbh.readableDatabase
        db.rawQuery(
            "SELECT EXISTS(SELECT 1 FROM " + DbHelper.T_MED + " LIMIT 1)" +
                " OR EXISTS(SELECT 1 FROM " + DbHelper.T_LOG + " LIMIT 1)", null
        ).use { c -> return c.moveToFirst() && c.getInt(0) == 1 }
    }

    // ---------- CSV 导出 ----------

    companion object {
        private const val TAG = "MedRepository"

        /** CSV 格式版本（导出首行写入，导入时跳过以兼容旧文件/未来扩展） */
        const val CSV_VERSION = "v1"
    }

    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.append("\uFEFF").append(CsvCodec.VERSION_LINE_PREFIX).append(CSV_VERSION).append("\n")
        sb.append(CsvCodec.HEADER).append('\n')
        val db = dbh.readableDatabase
        db.query(
            DbHelper.T_LOG + " l LEFT JOIN " + DbHelper.T_MED + " m ON l.med_id=m.id",
            arrayOf("l.*", "m.name as med_name", "m.form as med_form", "m.strength as med_strength"),
            null, null, null, null, "l.plan_date ASC, l.actual_at ASC"
        ).use { c ->
            while (c.moveToNext()) {
                val log = readLog(c)
                val medName = c.getString(c.getColumnIndexOrThrow("med_name"))
                val form = c.getString(c.getColumnIndexOrThrow("med_form"))
                val strength = c.getString(c.getColumnIndexOrThrow("med_strength"))
                sb.append(
                    CsvCodec.dataRow(
                        planDate = log.planDate,
                        planTime = log.planTime,
                        medName = medName,
                        form = form,
                        strength = strength,
                        taken = log.isTaken()
                    )
                ).append('\n')
            }
        }
        return sb.toString()
    }

    // ---------- CSV 导入 ----------

    /** 导入结果统计 */
    class ImportResult {
        var logs: Int = 0       // 成功导入的服药记录数
        var meds: Int = 0       // 新建的药物数
        var skipped: Int = 0    // 跳过的无效行数
    }

    /**
     * 从导出格式的 CSV 内容导入（合并模式）：
     * 表头「日期,时间,药物,剂型,剂量/规格,计划时间,状态」；
     * 药物按名称匹配已有档案，匹配不到则自动创建；
     * 记录直接追加，不覆盖现有数据。
     */
    fun importCsv(content: String?): ImportResult {
        val r = ImportResult()
        if (content.isNullOrEmpty()) return r
        var text = content
        text = CsvCodec.stripBom(text)
        val lines = text.split("\n")
        // 一次性建立 名称→药物 索引，避免逐行全表扫描
        val byName = HashMap<String, Medication>()
        for (m in listMedications()) byName[m.name.orEmpty()] = m
        val db = dbh.writableDatabase
        db.beginTransaction()
        try {
            for (raw in lines) {
                val line = raw.trim()
                if (!CsvCodec.isDataLine(line)) continue
                val f = CsvCodec.parseLine(line)
                if (f.size < 7) { r.skipped++; continue }
                val date = f[0].trim()
                val time = f[1].trim()
                val name = f[2].trim()
                val form = f[3].trim()
                val strength = f[4].trim()
                val planTime = f[5].trim()
                val status = f[6].trim()
                if (date.isEmpty() || name.isEmpty() || !CsvCodec.isValidDate(date)) {
                    r.skipped++
                    continue
                }
                // 药物：按名称匹配，找不到则创建
                var med = byName[name]
                if (med == null) {
                    med = Medication()
                    med.name = name
                    med.form = if (form.isEmpty()) null else form
                    med.strength = if (strength.isEmpty()) null else strength
                    med.asNeeded = false
                    med.startDate = date
                    med.durationDays = 0
                    med.remind = false
                    med.createdAt = System.currentTimeMillis()
                    med.id = insertMedication(med, ArrayList())
                    byName[name] = med
                    r.meds++
                }
                // 记录
                val cv = ContentValues()
                cv.put("med_id", med.id)
                cv.put("schedule_id", 0L)
                cv.put("plan_date", date)
                cv.put("plan_time", if (planTime.isEmpty()) null else planTime)
                cv.put("actual_at", parseAt(date, if (time.isEmpty()) planTime else time))
                cv.put("status", if (CsvCodec.STATUS_SKIPPED == status) DoseLog.SKIPPED else DoseLog.TAKEN)
                db.insert(DbHelper.T_LOG, null, cv)
                r.logs++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return r
    }

    private fun parseAt(date: String, hhmm: String?): Long {
        try {
            if (hhmm != null && Regex("\\d{1,2}:\\d{2}").matches(hhmm)) {
                val dt = java.time.LocalDateTime.parse(
                    date + "T" + (if (hhmm.length == 4) "0" + hhmm else hhmm)
                )
                return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        } catch (ignore: Exception) {
            Log.w(TAG, "parseAt: 带时间解析失败，回退到当天 00:00", ignore)
        }
        try {
            return java.time.LocalDate.parse(date)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (ignore: Exception) {
            Log.w(TAG, "parseAt: 日期解析失败，回退到当前时间", ignore)
            return System.currentTimeMillis()
        }
    }
}
