package com.medilog.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.components.ResponsivePage
import com.medilog.app.ui.components.contentWidth
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medilog.app.R
import com.medilog.app.data.MedRepository
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.util.Palette
import com.medilog.app.util.TimeUtils
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.MedIcon
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import java.util.Locale

/** 「历史」页：月历 + 统计 + 日志 */
@Composable
fun HistoryScreen(repo: MedRepository) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    var ym by remember { mutableStateOf(TimeUtils.monthOf(TimeUtils.today())) }
    var selectedDate by remember { mutableStateOf(TimeUtils.today()) }
    var refresh by remember { mutableStateOf(0) }

    val stats = remember(ym, refresh, dataVersion) { repo.monthStats(ym) }
    val today = TimeUtils.today()

    ResponsivePage {
        LazyColumn(
            Modifier.fillMaxHeight()
.contentWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(R.drawable.ic_chevron),
                                contentDescription = stringResource(R.string.history_prev_month),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { ym = shiftMonth(ym, -1) }
                                    .padding(15.dp)
                                    .size(18.dp),
                                colorFilter = ColorFilter.tint(c.primary)
                            )
                            BasicText(
                                stringResource(
                                    R.string.history_ym_fmt,
                                    ym.substringBefore("-"), ym.substringAfter("-")
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                                    color = c.textPrimary, fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                                ))
                            )
                            Image(
                                painterResource(R.drawable.ic_chevron),
                                contentDescription = stringResource(R.string.history_next_month),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { ym = shiftMonth(ym, 1) }
                                    .padding(15.dp)
                                    .size(18.dp),
                                colorFilter = ColorFilter.tint(c.primary)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        CalendarGrid(
                            ym = ym,
                            stats = stats,
                            selected = selectedDate,
                            onSelect = { selectedDate = it }
                        )
                    }
                }
            }

            item {
                val days = TimeUtils.daysInMonth(ym)
                var expected = 0; var taken = 0; var skipped = 0
                for (d in 1..days) {
                    val date = String.format(Locale.US, "%s-%02d", ym, d)
                    if (date > today) continue
                    val s = stats[date] ?: continue
                    expected += s.expected; taken += s.taken; skipped += s.skipped
                }
                // 必须显式 Double：混合分支 (0 / Double) 会被推断为 Any，装箱 Integer 使 "%.0f" 崩溃
                val rate: Double = if (expected == 0) 0.0 else taken * 100.0 / expected
                val missed = maxOf(0, expected - taken - skipped)
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCell(stringResource(R.string.stats_rate), String.format(Locale.US, "%.0f%%", rate), c.primary)
                        StatCell(stringResource(R.string.stats_expected), expected.toString(), c.textPrimary)
                        StatCell(stringResource(R.string.stats_taken), taken.toString(), c.greenText)
                        StatCell(stringResource(R.string.stats_skipped), skipped.toString(), c.textSecondary)
                        StatCell(stringResource(R.string.stats_missed), missed.toString(), c.redText)
                    }
                }
            }

            item {
                val days = TimeUtils.daysInMonth(ym)
                val showDate = if (selectedDate.startsWith(ym)) selectedDate
                else if (today.startsWith(ym)) today else String.format(Locale.US, "%s-%02d", ym, days)
                val logs = remember(showDate, refresh, dataVersion) { repo.logsOfDate(showDate) }
                BasicText(
                    TimeUtils.formatCnDate(showDate) + " " + TimeUtils.weekdayCn(showDate),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp))
                )
                if (logs.isEmpty()) {
                    GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, tintAlpha = 0.16f) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BasicText(
                                stringResource(R.string.empty_history),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp))
                            )
                        }
                    }
                } else {
                    logs.forEach { it ->
                        LogRow(it) {
                            repo.undoLog(it.log.id); refresh++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(value, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = valueColor, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)))
        Spacer(Modifier.height(4.dp))
        BasicText(label, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)))
    }
}

@Composable
private fun CalendarGrid(
    ym: String,
    stats: Map<String, MedRepository.DayStat>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    val lead = TimeUtils.weekdayIndex(ym)
    val days = TimeUtils.daysInMonth(ym)
    val today = TimeUtils.today()
    val weekNames = List(7) { i ->
        stringArrayResource(R.array.history_weekdays)[i]
    }

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekNames.forEach { w ->
                BasicText(
                    w,
                    modifier = Modifier.weight(1f),
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                        color = c.textTertiary, fontSize = 12.sp, textAlign = TextAlign.Center
                    ))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        var cell = 0
        val totalCells = lead + days
        val rows = (totalCells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val idx = r * 7 + col
                    val dayNum = idx - lead + 1
                    if (dayNum < 1 || dayNum > days) {
                        Spacer(Modifier.weight(1f).height(36.dp))
                    } else {
                        val date = String.format(Locale.US, "%s-%02d", ym, dayNum)
                        val st = stats[date]
                        val rate = st?.rate() ?: -1.0
                        val isSel = date == selected
                        val isToday = date == today
                        val bg: Color
                        val fg: Color
                        when {
                            isSel -> { bg = c.primary; fg = Color.White }
                            rate < 0 -> { bg = Color.Transparent; fg = c.textTertiary }
                            rate >= 99.5 -> { bg = c.green; fg = Color.White }
                            rate >= 75 -> { bg = c.green.copy(alpha = 0.35f); fg = c.textPrimary }
                            rate >= 40 -> { bg = c.yellow.copy(alpha = 0.45f); fg = c.textPrimary }
                            rate > 0 -> { bg = c.red.copy(alpha = 0.28f); fg = c.textPrimary }
                            else -> { bg = c.divider; fg = c.textSecondary }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                dayNum.toString(),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                                    color = if (isToday && !isSel) c.primary else fg,
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                ))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(item: MedRepository.LogItem, onUndo: () -> Unit) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    val log = item.log
    val color = Color(Palette.COLORS[item.color % Palette.COLORS.size].toLong() or 0xFF000000L)
    GlassCard(Modifier.fillMaxWidth(), radius = 16.dp, tintAlpha = 0.16f) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedIcon(painter = painterResource(Palette.iconRes(item.icon)), color = color, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    item.medName ?: "",
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    item.desc(androidx.compose.ui.platform.LocalContext.current) + " · " +
                            stringResource(R.string.history_recorded_suffix, TimeUtils.timeOf(log.actualAt)),
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 12.sp))
                )
            }
            Image(
                if (log.isTaken()) painterResource(R.drawable.ic_check) else painterResource(R.drawable.ic_close),
                // 审计 P2-2：状态图标承载「已服用/已跳过」语义，相邻文本未包含，需补无障碍描述
                contentDescription = if (log.isTaken()) {
                    stringResource(R.string.stats_taken)
                } else {
                    stringResource(R.string.stats_skipped)
                },
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(if (log.isTaken()) c.green else c.textSecondary)
            )
            Spacer(Modifier.width(8.dp))
            Image(
                painterResource(R.drawable.ic_undo),
                contentDescription = stringResource(R.string.undo),
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onUndo() }
                    .padding(15.dp)
                    .size(18.dp),
                colorFilter = ColorFilter.tint(c.textSecondary)
            )
        }
    }
}

private fun shiftMonth(ym: String, delta: Int): String {
    val p = ym.split("-")
    var y = Integer.parseInt(p[0])
    var m = Integer.parseInt(p[1]) + delta
    if (m <= 0) { m = 12; y -= 1 } else if (m > 12) { m = 1; y += 1 }
    return String.format(Locale.US, "%04d-%02d", y, m)
}
