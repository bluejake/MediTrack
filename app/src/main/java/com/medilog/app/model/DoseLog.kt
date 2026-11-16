package com.medilog.app.model

/** 一条服药/跳过记录 */
class DoseLog {
    companion object {
        const val TAKEN = "TAKEN"
        const val SKIPPED = "SKIPPED"
    }

    var id: Long = 0
    var medId: Long = 0
    var scheduleId: Long = 0   // 0 表示按需服用的临时记录
    var planDate: String? = null   // yyyy-MM-dd
    var planTime: String? = null   // HH:mm，按需时为空
    var actualAt: Long = 0         // 记录时间戳
    var status: String? = null     // TAKEN / SKIPPED

    fun isTaken(): Boolean = TAKEN == status
}
