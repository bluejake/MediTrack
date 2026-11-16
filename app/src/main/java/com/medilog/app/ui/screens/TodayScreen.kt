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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.res.stringResource
import com.medilog.app.data.MedRepository
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.model.DoseEvent
import com.medilog.app.util.Palette
import com.medilog.app.util.TimeUtils
import com.medilog.app.ui.glass.GlassButton
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.MedIcon
import com.medilog.app.ui.glass.RingProgress
import com.medilog.app.ui.glass.SectionHeader
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import com.medilog.app.util.NavUtil

/** 「今天」页：待服用 / 已记录 / 按需 */
@Composable
fun TodayScreen(
    repo: MedRepository,
    onOpenMed: (Long) -> Unit,
    onChanged: () -> Unit
) {
    val c = colors
    val ctx = LocalContext.current
    // 进程级数据版本：通知栏「记录」/ 全屏提醒页 / onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    val today = TimeUtils.today()
    var refresh by remember { mutableStateOf(0) }

    // 通知开关状态（2026-10-25 实测：权限/渠道被关时闹钟照常触发、广播照常接收，
    // 但通知被静默丢弃，用户侧零提醒——必须在最显眼处给出可见的修复入口，
    // 不能把唯一的指引藏在设置页自检里）。onResume 会 bump DataBus，此处随之刷新。
    val notifyOk = remember(dataVersion) {
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    val all = remember(today, refresh, dataVersion) { repo.buildTodayEvents(today) }
    val pending = all.filter { it.status != DoseEvent.AS_NEEDED && it.status != DoseEvent.TAKEN && it.status != DoseEvent.SKIPPED }
        .sortedBy { it.planTime ?: "99:99" }
    val recorded = all.filter { it.status == DoseEvent.TAKEN || it.status == DoseEvent.SKIPPED }
        .sortedByDescending { it.actualAt }
    val asNeeded = all.filter { it.status == DoseEvent.AS_NEEDED }

    val totalPlan = all.count { it.status != DoseEvent.AS_NEEDED }
    val totalDone = all.count { it.status == DoseEvent.TAKEN }
    val progress = if (totalPlan == 0) 0f else totalDone.toFloat() / totalPlan
    val ringColor = if (totalPlan > 0 && totalDone == totalPlan) c.green else c.primary

    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }

    ResponsivePage {
        LazyColumn(
            Modifier
                .fillMaxHeight()

                .contentWidth()
                .testTag(TestTags.TODAY_ROOT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 通知被关闭时的常驻引导条（置于列表最顶部，任何滚动位置回到顶部均可见）
            if (!notifyOk) {
                item(key = "notify_banner") {
                    GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                        Column(Modifier.padding(16.dp)) {
                            BasicText(
                                stringResource(R.string.banner_notify_title),
                                style = mediTextStyle(
                                    TextStyle(
                                        fontFamily = SfPro, color = c.redText,
                                        fontSize = 15.sp, fontWeight = FontWeight.Medium
                                    )
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                stringResource(R.string.banner_notify_desc),
                                style = mediTextStyle(
                                    TextStyle(
                                        fontFamily = SfPro, color = c.textSecondary,
                                        fontSize = 13.sp, lineHeight = 18.sp
                                    )
                                )
                            )
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                stringResource(R.string.banner_notify_action),
                                onClick = { NavUtil.openNotificationSettings(ctx) },
                                modifier = Modifier.fillMaxWidth(),
                                tint = c.primary,
                                textColor = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(bottom = 2.dp)) {
                    BasicText(
                        TimeUtils.formatCnDate(today) + " " + TimeUtils.weekdayCn(today),
                        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp))
                    )
                    BasicText(
                        TimeUtils.greeting(hour),
                        style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                            color = c.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold
                        ))
                    )
                }
            }

            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, tintAlpha = 0.2f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RingProgress(
                            progress = progress,
                            modifier = Modifier.size(76.dp),
                            color = ringColor
                        ) {
                            BasicText(
                                "${(progress * 100).toInt()}%",
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                                    color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                                ))
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                stringResource(R.string.today_progress_hint),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                                    color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                                ))
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                if (totalPlan == 0) stringResource(R.string.today_no_plan)
                                else stringResource(R.string.today_progress_fmt, totalPlan, totalDone),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp))
                            )
                        }
                    }
                }
            }

            if (all.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BasicText(
                                stringResource(R.string.empty_today),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 15.sp))
                            )
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                stringResource(R.string.empty_today_hint),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textTertiary, fontSize = 13.sp, lineHeight = 18.sp))
                            )
                        }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_pending)) }
                items(pending, key = { "p${it.med?.id}_${it.schedule?.id}_${it.status}" }) { e ->
                    DoseCard(e, onTake = {
                        repo.takeScheduled(e, System.currentTimeMillis()); refresh++; onChanged()
                    }, onSkip = {
                        repo.skipScheduled(e, System.currentTimeMillis()); refresh++; onChanged()
                    }, onClick = { e.med?.id?.let(onOpenMed) })
                }
            }

            if (recorded.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_recorded)) }
                items(recorded, key = { "r${it.logId}_${it.med?.id}_${it.schedule?.id}" }) { e ->
                    DoseCard(e, onUndo = {
                        repo.undoLog(e.logId); refresh++; onChanged()
                    }, onClick = { e.med?.id?.let(onOpenMed) })
                }
            }

            if (asNeeded.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_as_needed)) }
                items(asNeeded, key = { "a${it.med?.id}" }) { e ->
                    DoseCard(e, onTake = {
                        val m = e.med
                        if (m != null) {
                            repo.takeAsNeeded(m, System.currentTimeMillis()); refresh++; onChanged()
                        }
                    }, onClick = { e.med?.id?.let(onOpenMed) })
                }
            }
        }
    }
}

@Composable
private fun DoseCard(
    e: DoseEvent,
    onTake: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ 全屏提醒页 / onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    val med = e.med ?: return
    val color = Color(Palette.COLORS[med.color % Palette.COLORS.size].toLong() or 0xFF000000L)
    val recorded = e.status == DoseEvent.TAKEN || e.status == DoseEvent.SKIPPED
    val isAsNeeded = e.status == DoseEvent.AS_NEEDED
    val asNeededTag = stringResource(R.string.as_needed_tag)

    GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, tintAlpha = 0.18f, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedIcon(
                    painter = painterResource(Palette.iconRes(med.icon)),
                    color = color,
                    size = 40.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        med.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                    )
                    Spacer(Modifier.height(4.dp))
                    val sub = buildString {
                        if (!med.strength.isNullOrEmpty()) append(med.strength)
                        if (isAsNeeded) {
                            if (isNotEmpty()) append(" · ")
                            append(asNeededTag)
                        } else {
                            if (isNotEmpty()) append(" · ")
                            append(TimeUtils.friendlyTime(e.planTime))
                        }
                    }
                    BasicText(
                        sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp))
                    )
                }
            }

            if (recorded) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.status == DoseEvent.TAKEN) {
                        BasicText(
                            stringResource(R.string.action_taken),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        )
                    } else {
                        BasicText(
                            stringResource(R.string.action_skipped),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        stringResource(R.string.recorded_at, TimeUtils.timeOf(e.actualAt)),
                        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textTertiary, fontSize = 12.sp))
                    )
                    Spacer(Modifier.weight(1f))
                    if (onUndo != null) {
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
            } else if (e.status == DoseEvent.OVERDUE) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.status_overdue),
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.redText, fontSize = 13.sp))
                )
            }

            if (onTake != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        stringResource(R.string.action_take),
                        onClick = onTake,
                        modifier = Modifier.weight(1f),
                        tint = c.green,
                        textColor = Color.White
                    )
                    if (onSkip != null) {
                        GlassButton(
                            stringResource(R.string.action_skip),
                            onClick = onSkip,
                            modifier = Modifier.weight(1f),
                            tint = c.divider
                        )
                    }
                }
            }
        }
    }
}
