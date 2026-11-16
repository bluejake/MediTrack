package com.medilog.app.model

/** 今日页面的一个条目（某药某计划的一次服药） */
class DoseEvent {
    companion object {
        const val PENDING = 0    // 待服用
        const val TAKEN = 1      // 已服用
        const val SKIPPED = 2    // 已跳过
        const val OVERDUE = 3    // 已逾期未记录
        const val AS_NEEDED = 4  // 按需条目
    }

    var med: Medication? = null
    var schedule: Schedule? = null   // 按需时为 null
    var planDate: String? = null     // yyyy-MM-dd
    var planTime: String? = null     // HH:mm
    var status: Int = PENDING
    var logId: Long = 0
    var actualAt: Long = 0

    fun isPendingLike(): Boolean = status == PENDING || status == OVERDUE
}
