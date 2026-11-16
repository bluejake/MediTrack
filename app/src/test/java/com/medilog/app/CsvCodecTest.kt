package com.medilog.app

import com.medilog.app.data.CsvCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV 编解码往返单测。
 * 审计 P2-4：dataRow → parseLine 往返必须无损；表头/BOM/注释行跳过规则锁定。
 */
class CsvCodecTest {

    @Test
    fun roundTrip_plainFields() {
        val row = CsvCodec.dataRow(
            planDate = "2026-02-23",
            planTime = "08:00",
            medName = "二甲双胍片",
            form = "片剂",
            strength = "0.5 g",
            taken = true
        )
        val f = CsvCodec.parseLine(row)
        assertEquals(7, f.size)
        assertEquals("2026-02-23", f[0])
        assertEquals("08:00", f[1])
        assertEquals("二甲双胍片", f[2])
        assertEquals("片剂", f[3])
        assertEquals("0.5 g", f[4])
        assertEquals("08:00", f[5])
        assertEquals(CsvCodec.STATUS_TAKEN, f[6])
    }

    @Test
    fun roundTrip_specialCharsQuotedAndEscaped() {
        // 含逗号 + 双引号 + 换行：必须整体加引号且引号翻倍，往返后还原
        val row = CsvCodec.dataRow(
            planDate = "2026-02-23",
            planTime = "08:00",
            medName = "复方, \"甘草\" 片",
            form = null,
            strength = "10 mg\n第二行",
            taken = false
        )
        assertTrue(row.contains("\""))
        val f = CsvCodec.parseLine(row)
        assertEquals(7, f.size)
        assertEquals("复方, \"甘草\" 片", f[2])
        assertEquals("", f[3])                       // null → 空串
        assertEquals("10 mg\n第二行", f[4])
        assertEquals(CsvCodec.STATUS_SKIPPED, f[6])
    }

    @Test
    fun roundTrip_emptyPlanTime() {
        val row = CsvCodec.dataRow(null, null, "维生素C", "片剂", "100 mg", true)
        val f = CsvCodec.parseLine(row)
        assertEquals("", f[0])
        assertEquals("", f[1])
        assertEquals("", f[5])
    }

    @Test
    fun isDataLine_skipsMetaLines() {
        assertFalse(CsvCodec.isDataLine(""))
        assertFalse(CsvCodec.isDataLine("   "))
        assertFalse(CsvCodec.isDataLine("# MediTrack CSV v1"))
        assertFalse(CsvCodec.isDataLine("日期,时间,药物,剂型,剂量/规格,计划时间,状态"))
        assertTrue(CsvCodec.isDataLine("2026-02-23,08:00,药,片,1片,08:00,已服用"))
    }

    @Test
    fun stripBom_removesOnlyLeadingBom() {
        val withBom = "\uFEFF2026-02-23,08:00"
        assertEquals("2026-02-23,08:00", CsvCodec.stripBom(withBom))
        assertEquals("2026-02-23", CsvCodec.stripBom("2026-02-23"))
    }

    @Test
    fun isValidDate() {
        assertTrue(CsvCodec.isValidDate("2026-02-23"))
        assertTrue(CsvCodec.isValidDate("1999-12-31"))
        assertFalse(CsvCodec.isValidDate("2026-2-23"))
        assertFalse(CsvCodec.isValidDate("23-02-2026"))
        assertFalse(CsvCodec.isValidDate(""))
    }

    @Test
    fun headerMatchesExportedColumnOrder() {
        assertEquals(
            "日期,时间,药物,剂型,剂量/规格,计划时间,状态",
            CsvCodec.HEADER
        )
    }

    @Test
    fun quote_wrapsOnlyWhenNeeded() {
        assertEquals("plain", CsvCodec.quote("plain"))
        assertEquals("", CsvCodec.quote(null))
        assertEquals("\"a,b\"", CsvCodec.quote("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvCodec.quote("say \"hi\""))
    }
}
