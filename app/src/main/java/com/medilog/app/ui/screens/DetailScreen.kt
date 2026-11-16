package com.medilog.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medilog.app.R
import com.medilog.app.ui.components.ResponsivePage
import com.medilog.app.ui.components.contentWidth
import com.medilog.app.ui.TestTags
import com.medilog.app.data.DataBus
import com.medilog.app.data.MedDeletion
import com.medilog.app.data.MedRepository
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.util.Palette
import com.medilog.app.util.TimeUtils
import com.medilog.app.ui.glass.GlassButton
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.GlassDialogBox
import com.medilog.app.ui.glass.MedIcon
import com.medilog.app.ui.glass.RingProgress
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors

/** 药物详情 */
@Composable
fun DetailScreen(
    repo: MedRepository,
    medId: Long,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    onToast: (String) -> Unit
) {
    val c = colors
    val ctx = LocalContext.current
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    var refresh by remember { mutableIntStateOf(0) }
    val m = remember(medId, refresh, dataVersion) { repo.getMedication(medId) }
    var showDelete by remember { mutableStateOf(false) }

    if (m == null) {
        // ⚠️ 绝不能在组合体里直接调用 onDeleted()：
        // 删除药品后 DataBus.bump() 会让 dataVersion 变化 → 本页在退出动画期间再次重组
        // → m 变成 null → onDeleted() 被第二次调用 → 覆盖层栈已空，
        // stack.removeAt(stack.lastIndex) 即 removeAt(-1) → IndexOutOfBoundsException 崩溃。
        // 放进 LaunchedEffect 才是合法的副作用时机，且配合 MainActivity.popOverlay() 的幂等保护。
        LaunchedEffect(medId) { onDeleted() }
        return
    }

    val color = Color(Palette.COLORS[m.color % Palette.COLORS.size].toLong() or 0xFF000000L)
    val scheds = remember(medId, refresh, dataVersion) { repo.schedulesOf(m.id) }
    val st = remember(medId, refresh, dataVersion) {
        // 只统计这一味药（曾误用全局 statsRange：按需服用药品的详情页会显示
        // 全部药物的应服/已服数，真机实测出现 应服106/已服32 的错值）
        repo.statsRangeForMed(m.id, TimeUtils.shiftDay(TimeUtils.today(), -29), TimeUtils.today())
    }
    val logs = remember(medId, refresh, dataVersion) { repo.logsForMed(medId) }
    val rate = if (st.expected == 0) 0f else st.taken.toFloat() / st.expected
    val undoneMsg = stringResource(R.string.toast_undone)

    ResponsivePage {
        LazyColumn(
            Modifier
                .fillMaxHeight()

                .contentWidth()
                .testTag(TestTags.DETAIL_ROOT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 60.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, tintAlpha = 0.2f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MedIcon(painter = painterResource(Palette.iconRes(m.icon)), color = color, size = 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                m.name ?: "",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                (if (m.strength.isNullOrEmpty()) "" else m.strength) +
                                        (if (m.form.isNullOrEmpty()) "" else " · " + m.form),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp))
                            )
                        }
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column(Modifier.padding(16.dp)) {
                        val plan = buildString {
                            val asNeededText = stringResource(R.string.as_needed_short)
                            val timesFmt = stringResource(
                                R.string.detail_freq,
                                scheds.size,
                                scheds.joinToString(stringResource(R.string.list_separator)) { it.hhmm() }
                            )
                            val courseFmt = stringResource(R.string.detail_course, m.durationDays)
                            if (m.asNeeded) {
                                append(asNeededText)
                            } else if (scheds.isNotEmpty()) {
                                append(timesFmt)
                            }
                            if (m.durationDays > 0) {
                                if (isNotEmpty()) append(" · ")
                                append(courseFmt)
                            }
                            val sd = m.startDate
                            if (!sd.isNullOrEmpty()) {
                                if (isNotEmpty()) append(" · ")
                                append(stringResource(R.string.detail_since, TimeUtils.formatCnDate(sd)))
                            }
                        }
                        BasicText(plan, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 15.sp, lineHeight = 21.sp)))
                        if (!m.notes.isNullOrEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            BasicText(m.notes ?: "", style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)))
                        }
                    }
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RingProgress(
                            progress = rate,
                            modifier = Modifier.size(66.dp),
                            color = if (rate >= 0.99f && st.expected > 0) c.green else color
                        ) {
                            BasicText(
                                "${(rate * 100).toInt()}%",
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                stringResource(R.string.detail_adherence),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                MiniStat(stringResource(R.string.stat_short_expected), st.expected.toString(), c.textPrimary)
                                Spacer(Modifier.width(18.dp))
                                MiniStat(stringResource(R.string.stat_short_taken), st.taken.toString(), c.greenText)
                                Spacer(Modifier.width(18.dp))
                                MiniStat(stringResource(R.string.stat_short_skipped), st.skipped.toString(), c.textSecondary)
                            }
                        }
                    }
                }
            }

            item {
                BasicText(
                    stringResource(R.string.detail_logs),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp))
                )
            }

            if (logs.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, tintAlpha = 0.16f) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BasicText(
                                stringResource(R.string.no_logs),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp))
                            )
                        }
                    }
                }
            } else {
                // 审计 P1-3：补稳定 key（日志 id + 计划日期时间），撤销/刷新后重组时条目不错位
                items(
                    items = logs,
                    key = { item -> "${item.log.id}-${item.log.planDate}-${item.log.planTime}" }
                ) { it ->
                    val lc = Color(Palette.COLORS[it.color % Palette.COLORS.size].toLong() or 0xFF000000L)
                    GlassCard(Modifier.fillMaxWidth(), radius = 16.dp, tintAlpha = 0.16f) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MedIcon(painter = painterResource(Palette.iconRes(it.icon)), color = lc, size = 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    it.medName ?: "",
                                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                                )
                                Spacer(Modifier.height(4.dp))
                                BasicText(
                                    it.desc(LocalContext.current) + " · " +
                                        stringResource(R.string.recorded_at, TimeUtils.timeOf(it.log.actualAt)),
                                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 12.sp))
                                )
                            }
                            Image(
                                if (it.log.isTaken()) painterResource(R.drawable.ic_check) else painterResource(R.drawable.ic_close),
                                // 审计 P2-2：状态图标承载「已服用/已跳过」语义，相邻文本未包含，需补无障碍描述
                                contentDescription = if (it.log.isTaken()) {
                                    stringResource(R.string.stats_taken)
                                } else {
                                    stringResource(R.string.stats_skipped)
                                },
                                modifier = Modifier.size(18.dp),
                                colorFilter = ColorFilter.tint(if (it.log.isTaken()) c.green else c.textSecondary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Image(
                                painterResource(R.drawable.ic_undo),
                                contentDescription = stringResource(R.string.undo),
                                modifier = Modifier
                                    // MD3 触控目标 ≥48dp：外层 48dp 可点区，内层 18dp 保持原图标尺寸
                                    .size(48.dp)
                                    .clickable {
                                        repo.undoLog(it.log.id)
                                        refresh++
                                        onToast(undoneMsg)
                                    }
                                    .padding(15.dp)
                                    .size(18.dp),
                                colorFilter = ColorFilter.tint(c.textSecondary)
                            )
                        }
                    }
                }
            }

            item {
                GlassButton(
                    stringResource(R.string.edit_med),
                    onClick = { onEdit(m.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.DETAIL_EDIT),
                    tint = c.primary,
                    textColor = Color.White
                )
            }
            item {
                GlassButton(
                    stringResource(R.string.confirm_delete_title),
                    onClick = { showDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.DETAIL_DELETE),
                    tint = c.red,
                    textColor = Color.White
                )
            }
        }
    }

    if (showDelete) {
        val deletedMsg = stringResource(R.string.deleted)
        GlassDialogBox(
            title = stringResource(R.string.confirm_delete_title),
            message = stringResource(R.string.confirm_delete_msg, m.name ?: ""),
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showDelete = false
                // 完整删除链路：取消闹钟/通知 → 事务删库（药品+计划+记录）→ 全局刷新 → 出栈
                val ok = MedDeletion.deleteOne(ctx, repo, m.id)
                if (ok) {
                    DataBus.bump()   // 药品列表 / 今日待服 / 历史 / 统计 同步刷新
                    onToast(deletedMsg)
                }
                onDeleted()          // 退出详情页（幂等，栈空时不再 removeAt）
            },
            onDismiss = { showDelete = false },
            destructive = true
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, valueColor: Color) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    Column {
        BasicText(value, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)))
        BasicText(label, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)))
    }
}
