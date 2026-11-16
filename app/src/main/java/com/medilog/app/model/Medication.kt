package com.medilog.app.model

/** 药物实体 */
class Medication {
    var id: Long = 0
    var name: String? = null
    var form: String? = null          // 剂型文本：片剂/胶囊/颗粒/口服液/注射剂/滴剂/吸入剂/外用
    var strength: String? = null      // 剂量/规格文本，如 1 片、10 mg
    var icon: Int = 0                 // 图标索引 0..5
    var color: Int = 0                // 颜色索引 0..7
    var asNeeded: Boolean = false     // 按需服用
    var startDate: String? = null     // yyyy-MM-dd
    var durationDays: Int = 0         // 0 表示长期
    var remind: Boolean = true        // 开启提醒
    var notes: String? = null
    var createdAt: Long = 0

    fun isActiveOn(date: String?): Boolean {
        if (date == null) return false
        // 审计 P1-4：startDate 是 public var（无法智能转型），取本地 val 后判空，替代 !!
        val sd = startDate
        if (sd.isNullOrEmpty()) return true
        if (date.compareTo(sd) < 0) return false
        if (durationDays > 0) {
            val idx = daysBetween(sd, date)
            if (idx >= durationDays) return false
        }
        return true
    }

    // 审计 P1-2：原 scheduleLabel（硬编码中文）为死代码（全库零引用），已删除。
    // UI 侧需要"按需服用"标签时用 stringResource(R.string.as_needed_short)。

    companion object {
        fun daysBetween(a: String, b: String): Int {
            return try {
                val da = java.time.LocalDate.parse(a)
                val db = java.time.LocalDate.parse(b)
                (db.toEpochDay() - da.toEpochDay()).toInt()
            } catch (e: Exception) {
                0
            }
        }
    }
}
