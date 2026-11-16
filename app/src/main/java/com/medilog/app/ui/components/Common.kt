package com.medilog.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.ui.theme.LocalAppThemeMode
import com.medilog.app.ui.theme.LocalMd3Colors
import com.medilog.app.ui.theme.Md3
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import com.medilog.app.ui.glass.md3StateLayer

/**
 * 响应式内容宽度：窄屏铺满，大屏（平板 / 横屏）限制为 [Md3.ContentMaxWidth] 并居中。
 *
 * 目的：手机上无感；大屏上避免列表/表单被拉成通栏（行长过长影响可读性），
 * 也避免横屏时内容贴边。仅影响布局容器，不改变任何交互与文案。
 */
fun Modifier.contentWidth(): Modifier =
    // 顺序：先 widthIn 夹住上界，再 fillMaxWidth 撑满「夹后」的宽度
    this.widthIn(max = Md3.ContentMaxWidth).fillMaxWidth()

/**
 * 页面级响应式容器：内容区居中、最大宽度 [Md3.ContentMaxWidth]。
 * 屏幕根节点为 LazyColumn 时用它包一层即可获得居中 + 限宽。
 */
@Composable
fun ResponsivePage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
        content = content
    )
}

/**
 * 应用文本输入框（MIUIX 灰底圆角框 / MATERIAL = MD3 outlined text field）
 *
 * MD3 规范：最小高度 56dp；extraSmall(4dp) 圆角；未聚焦 1dp outline 描边、聚焦 2dp primary 描边；
 * 容器 surface（描边型无填充）；标签/占位符 bodyLarge onSurfaceVariant；
 * 输入文字 bodyLarge onSurface；光标 primary。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    textColor: Color? = null,
    /**
     * 作用在**内部 BasicTextField** 上的 modifier。
     * 自动化测试需要把 testTag 打在真正的输入框上：外层 Box 只是装饰容器，
     * 语义里没有 `RequestFocus`，`performTextInput` 会直接失败。
     */
    fieldModifier: Modifier = Modifier
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current

    if (isM3) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(Md3.ShapeExtraSmall)
        val borderColor = if (focused) md3.primary else md3.outline
        val borderWidth =
            if (focused) Md3.TextFieldFocusedBorderWidth else Md3.TextFieldBorderWidth
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = Md3.TextFieldHeight)
                .clip(shape)
                .background(md3.surface)
                .border(borderWidth, borderColor, shape)
                .padding(
                    horizontal = Md3.TextFieldHorizontalPadding,
                    vertical = 14.dp
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                BasicText(
                    placeholder,
                    style = Md3.BodyLarge.copy(color = md3.onSurfaceVariant)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = Md3.BodyLarge.copy(color = textColor ?: md3.onSurface),
                cursorBrush = SolidColor(md3.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(fieldModifier)
            )
        }
        return
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.field)
            .border(0.5.dp, c.divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        if (value.isEmpty()) {
            BasicText(
                placeholder,
                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textTertiary, fontSize = 15.sp))
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = mediTextStyle(TextStyle(fontFamily = SfPro, color = textColor ?: c.textPrimary, fontSize = 15.sp)),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .then(fieldModifier)
        )
    }
}

/**
 * 顶栏（MIUIX 玻璃 / MATERIAL = MD3 small top app bar）
 *
 * MD3 规范：高度 64dp；标题 titleLarge(22sp) onSurface；前导/尾部图标 24dp，
 * 命中区 48dp（无底色图标按钮，onSurfaceVariant）；水平内边距 4dp（有前导图标）/ 16dp。
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backIcon: Painter? = null,
    actions: @Composable (() -> Unit)? = null
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current

    if (isM3) {
        Row(
            modifier
                .fillMaxWidth()
                .height(Md3.TopAppBarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null && backIcon != null) {
                val interaction = remember { MutableInteractionSource() }
                val stateLayer = md3StateLayer(interaction, md3.onSurfaceVariant)
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(stateLayer, CircleShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onBack
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        backIcon,
                        contentDescription = "back",
                        modifier = Modifier.size(Md3.TopAppBarIconSize),
                        colorFilter = ColorFilter.tint(md3.onSurfaceVariant)
                    )
                }
            }
            BasicText(
                title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) 4.dp else Md3.TopAppBarTitlePadding),
                style = Md3.TitleLarge.copy(color = md3.onSurface)
            )
            if (actions != null) actions()
        }
        return
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null && backIcon != null) {
            Image(
                backIcon,
                contentDescription = "back",
                modifier = Modifier
                    // MD3 触控目标 ≥48dp：外层 48dp 可点区，内层 24dp 保持原图标尺寸
                    .size(48.dp)
                    .clickable { onBack() }
                    .padding(12.dp)
                    .size(24.dp),
                colorFilter = ColorFilter.tint(c.primary)
            )
            Spacer(Modifier.width(12.dp))
        }
        BasicText(
            title,
            modifier = Modifier.weight(1f),
            style = mediTextStyle(TextStyle(fontFamily = SfPro,
                color = c.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            ))
        )
        if (actions != null) actions()
    }
}

/**
 * 分段选择器（MIUIX 药丸分段 / MATERIAL = MD3 single-choice segmented button）
 *
 * MD3 规范：高度 40dp；整行 full 圆角 + 1dp outline 描边，段间 1dp 分隔线；
 * 选中段 secondaryContainer 填充 + onSecondaryContainer 文字 + 18dp 复选图标；
 * 未选中段透明 + onSurface 文字；标签 labelLarge。
 */
@Composable
fun SegmentedPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current

    if (isM3) {
        Row(
            modifier
                .fillMaxWidth()
                .height(Md3.SegmentedButtonHeight)
                .clip(Md3.ShapeFull)
                .border(1.dp, md3.outline, Md3.ShapeFull)
        ) {
            options.forEachIndexed { i, opt ->
                if (i > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(md3.outline)
                    )
                }
                val sel = i == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val stateLayer = md3StateLayer(interaction, md3.onSurface)
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (sel) md3.secondaryContainer else Color.Transparent,
                            Md3.ShapeFull
                        )
                        .background(stateLayer, Md3.ShapeFull)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(i) }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (sel) {
                        Md3CheckIcon(
                            tint = md3.onSecondaryContainer,
                            modifier = Modifier.size(Md3.SegmentedCheckIconSize)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    BasicText(
                        opt,
                        style = Md3.LabelLarge.copy(
                            color = if (sel) md3.onSecondaryContainer else md3.onSurface
                        )
                    )
                }
            }
        }
        return
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.field)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, opt ->
            val sel = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) c.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    opt,
                    style = mediTextStyle(TextStyle(fontFamily = SfPro,
                        color = if (sel) Color.White else c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal
                    ))
                )
            }
        }
    }
}

/** MD3 复选图标（18dp，路径勾），用于分段按钮选中态 */
@Composable
private fun Md3CheckIcon(tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = androidx.compose.ui.res.painterResource(com.medilog.app.R.drawable.ic_check),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

/**
 * 设置页的一行（标题 + 说明 + 右侧控件）
 * MATERIAL 模式 = MD3 两行列表项：最小高度 72dp（无说明 56dp）、
 *  headline bodyLarge onSurface、supporting bodyMedium onSurfaceVariant、
 *  水平内边距 16dp、可点击时叠加 onSurface 10% 状态层。
 */
@Composable
fun SettingRow(
    title: String,
    desc: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current

    if (isM3) {
        val interaction = remember { MutableInteractionSource() }
        val stateLayer = md3StateLayer(interaction, md3.onSurface)
        val minHeight =
            if (desc.isNullOrEmpty()) Md3.ListItemOneLineHeight else Md3.ListItemTwoLineHeight
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(
                    if (onClick != null) {
                        Modifier
                            .background(stateLayer)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onClick
                            )
                    } else Modifier
                )
                .padding(horizontal = Md3.ListItemHorizontalPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = Md3.BodyLarge.copy(color = md3.onSurface))
                if (!desc.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    BasicText(desc, style = Md3.BodyMedium.copy(color = md3.onSurfaceVariant))
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        return
    }

    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        onClick()
                    }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp)))
            if (!desc.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                BasicText(desc, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp)))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * 圆形颜色选择点（MIUIX 实心点 / MATERIAL 取 MD3 令牌）
 * 未选中 = 1dp outlineVariant 环；选中 = 2dp primary 环（MD3 选中态强调）。
 */
@Composable
fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isM3) {
                    if (selected) {
                        Modifier.border(2.dp, md3.primary, CircleShape)
                    } else {
                        Modifier.border(1.dp, md3.outlineVariant, CircleShape)
                    }
                } else {
                    if (selected) {
                        Modifier.border(3.dp, colors.textPrimary, CircleShape)
                    } else Modifier
                }
            )
            .clickable { onClick() }
    )
}

