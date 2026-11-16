package com.medilog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.Md3TextButton
import com.medilog.app.ui.motion.AnimatedModalHost
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.ui.theme.LocalAppThemeMode
import com.medilog.app.ui.theme.LocalMd3Colors
import com.medilog.app.ui.theme.Md3
import com.medilog.app.R
import java.util.Locale

/**
 * 弹窗遮罩（整屏覆盖，玻璃卡片居中）。
 * MATERIAL 模式：scrim 用 MD3 规范值（黑色 32%）；MIUIX 保留原有 35% 遮罩。
 * 转场：整体走 [AnimatedModalHost]（进入淡入+缩放、退出淡出），关闭路径统一 closeWith。
 */
@Composable
fun DialogOverlay(
    onDismiss: () -> Unit,
    content: @Composable (closeWith: (() -> Unit) -> Unit) -> Unit
) {
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    AnimatedModalHost(
        isMaterial = isM3,
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxSize()
    ) { closeWith ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (isM3) {
                        md3.scrim.copy(alpha = Md3.DialogScrimAlpha)
                    } else {
                        Color.Black.copy(alpha = 0.35f)
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { closeWith {} },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                content(closeWith)
            }
        }
    }
}

/** 简单时间选择器（滚轮列表，无 Material3） */
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onPick: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }

    DialogOverlay(onDismiss = onDismiss) { closeWith ->
        GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, tintAlpha = 0.96f, filled = true) {
            Column(Modifier.padding(Md3.DialogPadding)) {
                BasicText(
                    stringResource(R.string.add_time),
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = if (isM3) {
                        Md3.HeadlineSmall.copy(color = md3.onSurface)
                    } else {
                        mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 17.sp))
                    }
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberWheel(
                        values = (0..23).toList(),
                        selected = hour,
                        onSelect = { hour = it },
                        modifier = Modifier.weight(1f)
                    )
                    BasicText(
                        ":",
                        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 22.sp))
                    )
                    NumberWheel(
                        values = (0..59).toList(),
                        selected = minute,
                        onSelect = { minute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isM3) {
                        Md3TextButton(
                            text = stringResource(R.string.cancel),
                            color = md3.onSurfaceVariant,
                            onClick = { closeWith {} }
                        )
                        Spacer(Modifier.width(8.dp))
                        Md3TextButton(
                            text = stringResource(R.string.ok),
                            color = md3.primary,
                            onClick = { closeWith { onPick(hour, minute) } }
                        )
                    } else {
                        BasicText(
                            stringResource(R.string.cancel),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { closeWith {} }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.primary, fontSize = 15.sp))
                        )
                        BasicText(
                            stringResource(R.string.ok),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { closeWith { onPick(hour, minute) } }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.primary, fontSize = 15.sp))
                        )
                    }
                }
            }
        }
    }
}

/** 数字滚轮 */
@Composable
private fun NumberWheel(
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = colors
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        val idx = values.indexOf(selected)
        if (idx >= 0) listState.scrollToItem(idx)
    }
    Box(
        modifier
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.field)
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        // 审计 P1-3：滚轮值唯一，直接用值作 key，滚动/选中变更时避免重组错位
        items(values, key = { v -> v }) { v ->
            val sel = v == selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable { onSelect(v) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        String.format(Locale.US, "%02d", v),
                        style = mediTextStyle(TextStyle(fontFamily = SfPro, 
                            color = if (sel) c.primary else c.textPrimary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        ))
                    )
                }
            }
        }
    }
}

/** 日期选择器（年月日三列） */
@Composable
fun DatePickerDialog(
    initial: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    val parts = remember(initial) {
        val p = initial.split("-")
        intArrayOf(
            p[0].toIntOrNull() ?: 2026,
            p[1].toIntOrNull() ?: 1,
            p[2].toIntOrNull() ?: 1
        )
    }
    var year by remember { mutableIntStateOf(parts[0]) }
    var month by remember { mutableIntStateOf(parts[1]) }
    var day by remember { mutableIntStateOf(parts[2]) }

    DialogOverlay(onDismiss = onDismiss) { closeWith ->
        GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, tintAlpha = 0.96f, filled = true) {
            Column(Modifier.padding(Md3.DialogPadding)) {
                BasicText(
                    stringResource(R.string.start_date),
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = if (isM3) {
                        Md3.HeadlineSmall.copy(color = md3.onSurface)
                    } else {
                        mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 17.sp))
                    }
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberWheel((2020..2035).toList(), year, { year = it }, Modifier.weight(1.4f))
                    NumberWheel((1..12).toList(), month, { month = it }, Modifier.weight(1f))
                    NumberWheel((1..31).toList(), day, { day = it }, Modifier.weight(1f))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isM3) {
                        Md3TextButton(
                            text = stringResource(R.string.cancel),
                            color = md3.onSurfaceVariant,
                            onClick = { closeWith {} }
                        )
                        Spacer(Modifier.width(8.dp))
                        Md3TextButton(
                            text = stringResource(R.string.ok),
                            color = md3.primary,
                            onClick = {
                                closeWith { onPick(String.format(Locale.US, "%04d-%02d-%02d", year, month, day)) }
                            }
                        )
                    } else {
                        BasicText(
                            stringResource(R.string.cancel),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { closeWith {} }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.primary, fontSize = 15.sp))
                        )
                        BasicText(
                            stringResource(R.string.ok),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    closeWith { onPick(String.format(Locale.US, "%04d-%02d-%02d", year, month, day)) }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.primary, fontSize = 15.sp))
                        )
                    }
                }
            }
        }
    }
}
