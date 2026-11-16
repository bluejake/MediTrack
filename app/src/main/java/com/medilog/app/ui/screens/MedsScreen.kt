package com.medilog.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.components.contentWidth
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medilog.app.R
import com.medilog.app.ui.TestTags
import com.medilog.app.data.MedRepository
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.model.Medication
import com.medilog.app.model.Schedule
import com.medilog.app.util.Palette
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.GlassIconButton
import com.medilog.app.ui.glass.MedIcon
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors

/** 「用药」页：药物清单 */
@Composable
fun MedsScreen(
    repo: MedRepository,
    onOpenMed: (Long) -> Unit,
    onAdd: () -> Unit
) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    var refresh by remember { mutableStateOf(0) }
    val meds = remember(refresh, dataVersion) { repo.listMedications() }
    val schedMap = remember(refresh, meds) {
        meds.associate { it.id to repo.schedulesOf(it.id) }
    }

    // 响应式：大屏（平板 / 横屏）内容限宽居中，窄屏铺满
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.fillMaxHeight().contentWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (meds.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                val emptyMedsText = stringResource(R.string.empty_meds)
                BasicText(
                    emptyMedsText,
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 15.sp))
                            )
                        }
                    }
                }
            } else {
                items(meds, key = { it.id }) { m ->
                    MedRow(m, schedMap[m.id] ?: emptyList()) { onOpenMed(m.id) }
                }
            }
        }

        GlassIconButton(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.add_med),
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .testTag(TestTags.MEDS_ADD)
                .padding(end = 20.dp, bottom = 100.dp),
            tint = c.primary
        )
    }
}

@Composable
private fun MedRow(m: Medication, scheds: List<Schedule>, onClick: () -> Unit) {
    val c = colors
    // 进程级数据版本：通知栏「记录」/ onResume 等外部写入后立即重读
    val dataVersion = LocalDataVersion.current
    val color = Color(Palette.COLORS[m.color % Palette.COLORS.size].toLong() or 0xFF000000L)

    GlassCard(
        Modifier
            .fillMaxWidth()
            .testTag(TestTags.medItem(m.id)),
        radius = 18.dp, tintAlpha = 0.18f, onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedIcon(painter = painterResource(Palette.iconRes(m.icon)), color = color, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    m.name ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                )
                Spacer(Modifier.height(4.dp))
                val asNeededText = stringResource(R.string.as_needed_short)
                val listSep = stringResource(R.string.list_separator)
                val timesFmt = stringResource(R.string.meds_times_fmt, scheds.size, scheds.joinToString(listSep) { it.hhmm() })
                val durationFmt = stringResource(R.string.meds_duration_days, m.durationDays)
                val sub = buildString {
                    if (!m.strength.isNullOrEmpty()) append(m.strength)
                    if (m.asNeeded) {
                        if (isNotEmpty()) append(" · ")
                        append(asNeededText)
                    } else if (scheds.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(timesFmt)
                    }
                    if (m.durationDays > 0) {
                        if (isNotEmpty()) append(" · ")
                        append(durationFmt)
                    }
                }
                BasicText(
                    sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp))
                )
            }
            Image(
                painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(c.textTertiary)
            )
        }
    }
}
