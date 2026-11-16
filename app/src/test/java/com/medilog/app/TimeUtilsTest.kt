package com.medilog.app

import com.medilog.app.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TimeUtils 纯逻辑单测（java.time，无 Android 依赖）。
 * 审计 P2-4：锁定日期计算/解析/本地化格式的行为，防回归。
 */
class TimeUtilsTest {

    @Test
    fun shiftDay_crossesMonthAndYear() {
        assertEquals("2026-03-01", TimeUtils.shiftDay("2026-02-28", 1))
        assertEquals("2026-12-31", TimeUtils.shiftDay("2027-01-01", -1))
        assertEquals("2026-02-28", TimeUtils.shiftDay("2026-02-28", 0))
    }

    @Test
    fun nextDay_rollsOver() {
        assertEquals("2026-03-01", TimeUtils.nextDay("2026-02-28"))
        assertEquals("2027-01-01", TimeUtils.nextDay("2026-12-31"))
    }

    @Test
    fun monthHelpers() {
        assertEquals("2026-02", TimeUtils.monthOf("2026-02-28"))
        assertEquals("2026-02-01", TimeUtils.firstDayOfMonth("2026-02"))
        assertEquals(28, TimeUtils.daysInMonth("2026-02"))   // 2026 非闰年
        assertEquals(29, TimeUtils.daysInMonth("2024-02"))   // 2024 闰年
        assertEquals(31, TimeUtils.daysInMonth("2026-01"))
    }

    @Test
    fun weekdayIndex_mondayIsZero() {
        // 2026-06-01 是周一 → index 0；2026-02-01 是周日 → index 6
        assertEquals(0, TimeUtils.weekdayIndex("2026-06"))
        assertEquals(6, TimeUtils.weekdayIndex("2026-02"))
        // 2026-02-23 是周一
        assertEquals(0, TimeUtils.weekdayIndexOf("2026-02-23"))
        assertEquals(6, TimeUtils.weekdayIndexOf("2026-03-01")) // 周日
    }

    @Test
    fun millisAt_parsesDateAndTime() {
        val a = TimeUtils.millisAt("2026-02-23", "08:30")
        val b = TimeUtils.millisAt("2026-02-23", 8, 30)
        assertEquals(a, b)
        assertTrue(a > 0)
        // 同一天不同时刻：晚 > 早
        assertTrue(TimeUtils.millisAt("2026-02-23", "20:00") > a)
    }

    @Test
    fun millisAt_invalidInputReturnsZero() {
        assertEquals(0L, TimeUtils.millisAt("not-a-date", "08:00"))
    }

    @Test
    fun dateOf_timeOf_roundTrip() {
        val millis = TimeUtils.millisAt("2026-02-23", "09:05")
        assertEquals("2026-02-23", TimeUtils.dateOf(millis))
        assertEquals("09:05", TimeUtils.timeOf(millis))
    }

    @Test
    fun formatCnDate_fallsBackToRawOnBadInput() {
        assertEquals("garbage", TimeUtils.formatCnDate("garbage"))
    }

    @Test
    fun formatCnDate_validDateIsNotRaw() {
        val out = TimeUtils.formatCnDate("2026-02-23")
        assertFalse(out.isEmpty())
        assertFalse(out == "2026-02-23")
    }

    @Test
    fun friendlyTime_hasPeriodPrefix() {
        val morning = TimeUtils.friendlyTime("08:00")
        val evening = TimeUtils.friendlyTime("21:30")
        assertTrue(morning.isNotEmpty())
        assertTrue(evening.isNotEmpty())
        assertTrue(morning.contains("08:00"))
        assertTrue(evening.contains("21:30"))
        // 空输入
        assertEquals("", TimeUtils.friendlyTime(null))
        assertEquals("", TimeUtils.friendlyTime(""))
    }

    @Test
    fun greeting_neverEmpty() {
        for (h in 0..23) {
            assertTrue(TimeUtils.greeting(h).isNotEmpty())
        }
    }

    @Test
    fun passed_futureIsFalse() {
        assertFalse(TimeUtils.passed("2999-01-01", "12:00"))
        assertTrue(TimeUtils.passed("2020-01-01", "12:00"))
    }
}
