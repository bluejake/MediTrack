package com.medilog.app.model

/**
 * 一次删除的**完整快照**（药品 + 提醒计划 + 服药记录，含原始 id）。
 *
 * 用于「删除后 5 秒内可撤销」（建议 5）。
 *
 * 为什么用「快照 + 原 id 还原」而不是「软删除（加 deleted_at 列）」：
 *  - 软删除要改表结构（DB_VER 1 → 2）并写迁移，**已装机用户的库有升级风险**；
 *  - 软删除要求**每一处查询都加上 `deleted_at = 0` 过滤**
 *    （listMedications / getMedication / buildTodayEvents / statsRange / logsForMed /
 *    loadAllSchedules / exportCsv …），漏一处就会出现"幽灵条目"，风险远高于收益；
 *  - 快照方案**零 schema 变更、零查询改动**，还原时按原 id 插回，
 *    药品/计划/记录的 id 完全不变，提醒闹钟也能原样重排。
 *
 * 代价：5 秒窗口内进程被杀则无法撤销（但数据已硬删，不留残行，状态是自洽的）。
 */
data class MedSnapshot(
    val med: Medication,
    val schedules: List<Schedule>,
    val logs: List<DoseLog>
)
