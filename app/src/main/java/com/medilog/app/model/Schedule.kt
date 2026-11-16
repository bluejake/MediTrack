package com.medilog.app.model

import java.util.Locale

/** 每日固定服药时间点 */
class Schedule {
    var id: Long = 0
    var medId: Long = 0
    var hour: Int = 0
    var minute: Int = 0
    var enabled: Boolean = true

    fun hhmm(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    fun minutesOfDay(): Int = hour * 60 + minute
}
