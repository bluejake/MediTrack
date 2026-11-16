package com.medilog.app.data

import android.content.Context
import android.util.Log
import com.medilog.app.reminder.ReminderScheduler
import com.medilog.app.reminder.rescheduleAsync

/**
 * 删除药品的**唯一入口**（UI、设置页、后台清理都必须走这里）。
 *
 * 四条铁律（对应「删除药品必崩」事故的完整修复 + 后续两条加固）：
 *  1. **先取消闹钟与通知，再删库**。顺序反了的话，删除后 scheduleId 已不可查，
 *     那些 PendingIntent 就成了永远无法回收的孤儿闹钟 —— 药删了到点还在响。
 *  2. **删除在单事务内完成**（药品 + 提醒计划 + 服药记录），中途任何异常整体回滚，
 *     不会出现"药品没了计划还在"这类残行。
 *  3. **返回是否真的删掉了**，调用方据此决定提示文案与是否刷新，避免"删了个不存在的 id
 *     也弹已删除"。
 *  4. **删完异步全量重排一次提醒**（建议 4），兜住任何残留闹钟。
 */
object MedDeletion {
    private const val TAG = "MediLog-Delete"

    /**
     * 删除单个药品。
     *
     * @param undoable true = 先留快照并交给 [DeleteUndo] 提供 5 秒撤销窗口（建议 5）。
     *                 清空全部数据这类批量操作传 false。
     * @return true 表示确实删掉了一条。
     */
    fun deleteOne(
        ctx: Context,
        repo: MedRepository,
        medId: Long,
        undoable: Boolean = true
    ): Boolean {
        val appCtx = ctx.applicationContext
        // ① 删除前：拿到计划 id 与完整快照（删除后两者都取不到）
        val schedIds = repo.scheduleIdsOf(medId)
        val snapshot = if (undoable) repo.snapshotOf(medId) else null

        // ② 先摘掉该药品的闹钟与通知
        ReminderScheduler.cancelForMed(appCtx, schedIds)

        // ③ 再事务化删库（药品 + 计划 + 记录）
        val ok = repo.deleteMedication(medId)

        // ④ 兜底再清一次通知：覆盖"删除瞬间才弹出来"的极端时序
        if (ok) {
            ReminderScheduler.cancelForMed(appCtx, schedIds)
            rescheduleAsync(appCtx)                                     // 建议 4：全量重排兜底
            if (snapshot != null) {
                DeleteUndo.offer(snapshot.med.name.orEmpty(), snapshot) // 建议 5：5 秒可撤销
            }
        }
        Log.i(TAG, "deleteOne medId=$medId ok=$ok scheds=${schedIds.size} undoable=$undoable")
        return ok
    }

    /** 清空全部药品数据，并摘掉所有提醒闹钟与通知（批量操作不提供撤销）。 */
    fun deleteAll(ctx: Context, repo: MedRepository) {
        val appCtx = ctx.applicationContext
        DeleteUndo.discard()                 // 旧快照对应的数据已经没了，必须失效
        ReminderScheduler.cancelAll(appCtx)
        repo.deleteAllMedications()
        rescheduleAsync(appCtx)
        Log.i(TAG, "deleteAll done")
    }
}
