package com.medilog.app

import com.medilog.app.model.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Medication 疗程/激活期纯逻辑单测。审计 P2-4 */
class MedicationTest {

    private fun med(
        startDate: String?,
        durationDays: Int,
        asNeeded: Boolean = false
    ): Medication = Medication().apply {
        this.startDate = startDate
        this.durationDays = durationDays
        this.asNeeded = asNeeded
    }

    @Test
    fun daysBetween_countsCalendarDays() {
        assertEquals(0, Medication.daysBetween("2026-02-23", "2026-02-23"))
        assertEquals(1, Medication.daysBetween("2026-02-23", "2026-02-24"))
        assertEquals(5, Medication.daysBetween("2026-02-23", "2026-02-28"))
        assertEquals(31, Medication.daysBetween("2026-01-31", "2026-03-03"))
    }

    @Test
    fun isActiveOn_nullDateIsFalse() {
        assertFalse(med("2026-01-01", 0).isActiveOn(null))
    }

    @Test
    fun isActiveOn_noStartMeansAlwaysActive() {
        val m = med(null, 0)
        assertTrue(m.isActiveOn("2020-01-01"))
        assertTrue(m.isActiveOn("2999-12-31"))
    }

    @Test
    fun isActiveOn_emptyStartMeansAlwaysActive() {
        val m = med("", 0)
        assertTrue(m.isActiveOn("2020-01-01"))
    }

    @Test
    fun isActiveOn_beforeStartIsFalse() {
        val m = med("2026-02-10", 0)
        assertFalse(m.isActiveOn("2026-02-09"))
        assertTrue(m.isActiveOn("2026-02-10"))
        assertTrue(m.isActiveOn("2026-03-01"))
    }

    @Test
    fun isActiveOn_durationWindow() {
        // 从 02-10 起 5 天：02-10 .. 02-14 有效，02-15 起失效
        val m = med("2026-02-10", 5)
        assertTrue(m.isActiveOn("2026-02-10"))
        assertTrue(m.isActiveOn("2026-02-14"))
        assertFalse(m.isActiveOn("2026-02-15"))
        assertFalse(m.isActiveOn("2026-03-01"))
    }

    @Test
    fun isActiveOn_zeroDurationMeansLongTerm() {
        val m = med("2026-01-01", 0)
        assertTrue(m.isActiveOn("2030-01-01"))
    }
}
