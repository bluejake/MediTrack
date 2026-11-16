package com.medilog.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medilog.app.R
import com.medilog.app.data.DeleteUndo
import com.medilog.app.data.MedRepository
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.ui.theme.LocalAppThemeMode
import com.medilog.app.ui.theme.LocalMd3Colors
import com.medilog.app.ui.theme.Md3
import com.medilog.app.ui.theme.SfPro
import com.medilog.app.ui.theme.colors
import com.medilog.app.ui.theme.mediTextStyle

/**
 * 删除撤销条（建议 5）。
 *
 * 单独抽成可复用组件而不是写在 MainActivity 里，有两个原因：
 *  1. 撤销是**全局行为**（任何 Tab、任何页面下删除都要能撤销），
 *     位置固定在底部导航栏之上、不随页面转场移动；
 *  2. Compose UI 测试的导航壳（`androidTest` 的 CrudHarness）也需要渲染它，
 *     否则「删除 → 撤销」这条链路无法被自动化覆盖。
 *
 * - MATERIAL = MD3 Snackbar：容器 inverseSurface、圆角 4dp（ShapeExtraSmall）、
 *   内容 inverseOnSurface（bodyMedium）、操作文字 inversePrimary（labelLarge）、
 *   最小高度 48dp、水平内边距 16dp。
 * - MIUIX = 沿用项目既有的玻璃卡片语言（不改动 MIUIX 既有视觉规范）。
 */
@Composable
fun UndoDeleteBar(
    repo: MedRepository,
    modifier: Modifier = Modifier,
    onRestored: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val pending by DeleteUndo.pending.collectAsState()

    AnimatedVisibility(
        visible = pending != null,
        modifier = modifier.testTag(TestTags.UNDO_BAR),
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        label = "undoBar"
    ) {
        val p = pending
        if (p != null) {
            UndoBarContent(
                text = stringResource(R.string.undo_deleted_msg, p.name),
                actionText = stringResource(R.string.undo),
                onUndo = {
                    if (DeleteUndo.undo(ctx, repo)) onRestored?.invoke()
                }
            )
        }
    }
}

@Composable
private fun UndoBarContent(
    text: String,
    actionText: String,
    onUndo: () -> Unit
) {
    val c = colors
    val isMaterial = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    if (isMaterial) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(md3.inverseSurface, RoundedCornerShape(Md3.ShapeExtraSmall))
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                style = Md3.BodyMedium.copy(color = md3.inverseOnSurface)
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                actionText,
                modifier = Modifier
                    .testTag(TestTags.UNDO_ACTION)
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = Md3.LabelLarge.copy(color = md3.inversePrimary)
            )
        }
    } else {
        GlassCard(Modifier.fillMaxWidth(), radius = 16.dp, tintAlpha = 0.24f) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    style = mediTextStyle(
                        TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 14.sp)
                    )
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    actionText,
                    modifier = Modifier
                        .testTag(TestTags.UNDO_ACTION)
                        .clickable(onClick = onUndo)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = mediTextStyle(
                        TextStyle(
                            fontFamily = SfPro, color = c.primary, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                )
            }
        }
    }
}
