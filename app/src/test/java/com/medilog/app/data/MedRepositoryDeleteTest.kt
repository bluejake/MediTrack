package com.medilog.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.medilog.app.model.Medication
import com.medilog.app.model.Schedule

/**
 * 删除链路的**数据层**回归测试（建议 2：补 Robolectric + DB 单测）。
 *
 * 背景：此前 `deleteMedication` 只能在真机/模拟器上验证，而模拟器上
 * 既没有 `sqlite3`、也无法 root，**根本查不了库**——"删干净了没有"只能靠 UI 间接推断。
 * 有了 Robolectric，药品 / 提醒计划 / 服药记录三张表可以直接在 JVM 上数行。
 *
 * 重点覆盖：
 *  1. 删除后三张表都**无残行**（这是原实现最大的隐患：3 条独立 delete，无事务）；
 *  2. 事务回滚：药品不存在时，不能把它的孤儿记录/计划"顺手删掉"再返回 false；
 *  3. `scheduleIdsOf` 必须在删除**前**才拿得到（闹钟清理依赖它）；
 *  4. `deleteAllMedications` 清空全部。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MedRepositoryDeleteTest {

    private lateinit var ctx: Context
    private lateinit var repo: MedRepository
    private lateinit var dbh: DbHelper

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        repo = MedRepository(ctx)
        dbh = DbHelper(ctx)      // 同一个 medilog.db，直接数行用
        // 每个用例从空库开始
        dbh.writableDatabase.delete(DbHelper.T_LOG, null, null)
        dbh.writableDatabase.delete(DbHelper.T_SCHED, null, null)
        dbh.writableDatabase.delete(DbHelper.T_MED, null, null)
    }

    // ── 工具 ─────────────────────────────────────────────────────────────

    private fun newMed(name: String): Medication = Medication().apply {
        this.name = name
        form = "Tablet"
        strength = "10 mg"
        startDate = "2026-01-01"
        remind = true
    }

    private fun scheds(vararg hhmm: Pair<Int, Int>) =
        hhmm.map { (h, mi) -> Schedule().apply { hour = h; minute = mi; enabled = true } }

    private fun count(table: String, where: String? = null, args: Array<String>? = null): Int {
        dbh.readableDatabase.query(table, arrayOf("COUNT(*)"), where, args, null, null, null)
            .use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    private fun rawInsertLog(medId: Long, scheduleId: Long, date: String, status: String) {
        val cv = android.content.ContentValues().apply {
            put("med_id", medId)
            put("schedule_id", scheduleId)
            put("plan_date", date)
            put("plan_time", "08:00")
            put("actual_at", 0L)
            put("status", status)
        }
        dbh.writableDatabase.insert(DbHelper.T_LOG, null, cv)
    }

    // ── 用例 ─────────────────────────────────────────────────────────────

    @Test
    fun deleteMedication_removesMedSchedulesAndLogs_noResidualRows() {
        val id = repo.insertMedication(newMed("A"), scheds(8 to 0, 20 to 30))
        val sid = repo.schedulesOf(id).first().id
        rawInsertLog(id, sid, "2026-01-02", "TAKEN")
        rawInsertLog(id, sid, "2026-01-03", "SKIPPED")

        assertEquals(1, count(DbHelper.T_MED))
        assertEquals(2, count(DbHelper.T_SCHED))
        assertEquals(2, count(DbHelper.T_LOG))

        assertTrue(repo.deleteMedication(id))

        // 核心断言：三张表都不留残行
        assertEquals(0, count(DbHelper.T_MED))
        assertEquals(0, count(DbHelper.T_SCHED, "med_id=?", arrayOf(id.toString())))
        assertEquals(0, count(DbHelper.T_LOG, "med_id=?", arrayOf(id.toString())))
        assertEquals(0, count(DbHelper.T_LOG))   // 全局也不该有孤儿
    }

    @Test
    fun deleteMedication_onlyTouchesTargetMedication() {
        val a = repo.insertMedication(newMed("A"), scheds(8 to 0))
        val b = repo.insertMedication(newMed("B"), scheds(9 to 0, 21 to 0))

        assertTrue(repo.deleteMedication(a))

        assertEquals(1, count(DbHelper.T_MED))
        assertEquals(listOf("B"), repo.listMedications().map { it.name })
        // B 的两条计划完好无损
        assertEquals(2, repo.schedulesOf(b).size)
    }

    @Test
    fun deleteMedication_missingMed_returnsFalse_andRollsBackOrphans() {
        // 构造"孤儿"：有 med_id=999 的记录与计划，但药品本身不存在
        val cv = android.content.ContentValues().apply {
            put("med_id", 999L); put("hour", 7); put("minute", 15); put("enabled", 1)
        }
        dbh.writableDatabase.insert(DbHelper.T_SCHED, null, cv)
        rawInsertLog(999L, 0L, "2026-01-02", "TAKEN")

        assertFalse(repo.deleteMedication(999L))

        // 事务语义：既然没删掉任何药品，就不该偷偷把孤儿记录/计划清掉
        // （旧实现的 3 条独立 delete 会先清 log、再清 sched，最后 med 删 0 行 —— 留不下证据但也改了数据）
        assertEquals(1, count(DbHelper.T_LOG, "med_id=?", arrayOf("999")))
        assertEquals(1, count(DbHelper.T_SCHED, "med_id=?", arrayOf("999")))
    }

    @Test
    fun deleteMedication_isIdempotent() {
        val id = repo.insertMedication(newMed("A"), scheds(8 to 0))
        assertTrue(repo.deleteMedication(id))
        assertFalse(repo.deleteMedication(id))   // 第二次：药品已经没了
        assertEquals(0, count(DbHelper.T_MED))
    }

    @Test
    fun scheduleIdsOf_availableBeforeDelete_emptyAfterDelete() {
        val id = repo.insertMedication(newMed("A"), scheds(8 to 0, 12 to 0, 20 to 0))
        val before = repo.scheduleIdsOf(id)
        assertEquals(3, before.size)

        repo.deleteMedication(id)

        // 删除后拿不到 id —— 这正是"必须先 cancelForMed 再删库"的原因
        assertTrue(repo.scheduleIdsOf(id).isEmpty())
    }

    @Test
    fun deleteAllMedications_clearsEveryTable() {
        val a = repo.insertMedication(newMed("A"), scheds(8 to 0))
        val b = repo.insertMedication(newMed("B"), scheds(9 to 0))
        rawInsertLog(a, 0L, "2026-01-02", "TAKEN")
        rawInsertLog(b, 0L, "2026-01-02", "SKIPPED")

        repo.deleteAllMedications()

        assertEquals(0, count(DbHelper.T_MED))
        assertEquals(0, count(DbHelper.T_SCHED))
        assertEquals(0, count(DbHelper.T_LOG))
        assertFalse(repo.hasAnyData())
    }
}
