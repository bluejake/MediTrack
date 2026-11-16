package com.medilog.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import android.util.Log

object TimeUtils {

    private const val TAG = "TimeUtils"
    private val D: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val T: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
    private val CN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    fun today(): String = LocalDate.now().format(D)

    fun dateOf(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(D)

    fun timeOf(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(T)

    fun dateTimeOf(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DT)

    /** 审计 P1-2：按系统语言选择日期格式（中文 yyyy年M月d日 / 其他 MMM d, yyyy） */
    fun formatCnDate(date: String): String =
        try {
            val d = LocalDate.parse(date, D)
            if (isZh()) d.format(CN_DATE)
            else d.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        } catch (e: Exception) {
            Log.w(TAG, "formatCnDate 解析失败，原样返回: $date", e)
            date
        }

    private fun isZh(): Boolean = Locale.getDefault().language == "zh"

    fun millisAt(date: String, hhmm: String): Long {
        return try {
            val d = LocalDate.parse(date, D)
            val p = hhmm.split(":")
            d.atTime(Integer.parseInt(p[0]), Integer.parseInt(p[1]))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "millisAt 解析失败，返回 0: $date $hhmm", e)
            0L
        }
    }

    fun millisAt(date: String, hour: Int, minute: Int): Long {
        return try {
            val d = LocalDate.parse(date, D)
            d.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "millisAt 解析失败，返回 0: $date $hour:$minute", e)
            0L
        }
    }

    /** 当前是否已超过某天的某时刻 */
    fun passed(date: String, hhmm: String): Boolean =
        System.currentTimeMillis() > millisAt(date, hhmm)

    /** 审计 P1-2：locale 感知星期（中文 周X / 其他 Mon…） */
    fun weekdayCn(date: String): String {
        return try {
            val d = LocalDate.parse(date, D)
            if (isZh()) {
                val w = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                w[d.dayOfWeek.value - 1]
            } else {
                d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        } catch (e: Exception) {
            Log.w(TAG, "weekdayCn 解析失败，返回空: $date", e)
            ""
        }
    }

    /** 星期索引（0=周一 … 6=周日），供本地化星期数组使用 */
    fun weekdayIndexOf(date: String): Int {
        return try {
            LocalDate.parse(date, D).dayOfWeek.value - 1
        } catch (e: Exception) {
            Log.w(TAG, "weekdayIndexOf 解析失败，返回 -1: $date", e)
            -1
        }
    }

    /** 审计 P1-2：locale 感知问候语 */
    fun greeting(hour: Int): String = if (isZh()) when {
        hour < 5 -> "夜深了"
        hour < 12 -> "早上好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    } else when {
        hour < 5 -> "It's late"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    fun nextDay(date: String): String =
        LocalDate.parse(date, D).plusDays(1).format(D)

    fun shiftDay(date: String, days: Long): String =
        LocalDate.parse(date, D).plusDays(days).format(D)

    fun monthOf(date: String): String = date.substring(0, 7)

    fun firstDayOfMonth(ym: String): String = "$ym-01"

    fun daysInMonth(ym: String): Int = LocalDate.parse("$ym-01", D).lengthOfMonth()

    fun weekdayIndex(ym: String): Int =
        LocalDate.parse("$ym-01", D).dayOfWeek.value - 1  // 0=周一

    /** 审计 P1-2：locale 感知时段前缀（中文 凌晨/早晨/上午… / 其他 Late night/Morning…） */
    fun friendlyTime(hhmm: String?): String {
        if (hhmm.isNullOrEmpty()) return ""
        val p = hhmm.split(":")
        val h = Integer.parseInt(p[0])
        val m = Integer.parseInt(p[1])
        return if (isZh()) when {
            h < 6 -> String.format(Locale.CHINA, "凌晨 %02d:%02d", h, m)
            h < 9 -> String.format(Locale.CHINA, "早晨 %02d:%02d", h, m)
            h < 12 -> String.format(Locale.CHINA, "上午 %02d:%02d", h, m)
            h < 14 -> String.format(Locale.CHINA, "中午 %02d:%02d", h, m)
            h < 18 -> String.format(Locale.CHINA, "下午 %02d:%02d", h, m)
            else -> String.format(Locale.CHINA, "晚上 %02d:%02d", h, m)
        } else when {
            h < 6 -> String.format(Locale.US, "Late night %02d:%02d", h, m)
            h < 12 -> String.format(Locale.US, "Morning %02d:%02d", h, m)
            h < 14 -> String.format(Locale.US, "Noon %02d:%02d", h, m)
            h < 18 -> String.format(Locale.US, "Afternoon %02d:%02d", h, m)
            else -> String.format(Locale.US, "Evening %02d:%02d", h, m)
        }
    }
}
