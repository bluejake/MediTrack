package com.medilog.app.ui.glass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import com.medilog.app.ui.TestTags
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.ui.theme.LocalAppThemeMode
import com.medilog.app.ui.theme.LocalMd3Colors
import com.medilog.app.ui.theme.Md3
import com.medilog.app.ui.theme.Md3Colors
import com.medilog.app.ui.motion.AnimatedModalHost
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors

/** 全局共享的 Backdrop（由根布局提供） */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * MD3 状态层（state layer）。
 *
 * MD3 的交互反馈模型：在容器之上叠一层「on 色」薄层，hover 8% / focus 10% / pressed 10%，
 * 淡入淡出用 DurationShort4(200ms) + Standard 缓动。
 * 本项目沿用该模型（而非默认 ripple）：既符合 MD3，又避免早前审计发现的
 * 「按压色=内容色=灰 → 先变灰再变主色」异常中间态。
 * 供 glass 包与 ui/components 包的自绘 MD3 组件共用。
 */
@Composable
internal fun md3StateLayer(interactionSource: MutableInteractionSource, onColor: Color): Color {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val target = when {
        pressed -> Md3.StatePressed
        hovered -> Md3.StateHover
        focused -> Md3.StateFocus
        else -> 0f
    }
    val alpha by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = Md3.DurationShort4, easing = Md3.EasingStandard),
        label = "md3StateLayer"
    )
    return onColor.copy(alpha = alpha)
}

/**
 * 通用表面容器。
 * MATERIAL 模式 = MD3 surface（不透明、无玻璃折射、无半透明染色，层级由 elevation 角色承担）；
 * MIUIX 模式 = 液态玻璃（保留原有视觉）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    capsule: Boolean = false,
    circle: Boolean = false,
    blurRadius: Dp = 20.dp,
    refractionHeight: Dp = 18.dp,
    refractionAmount: Dp = 36.dp,
    lensDepth: Boolean = true,
    lensChromatic: Boolean = false,
    tint: Color? = null,
    tintAlpha: Float = 0.5f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalBackdrop.current
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "glassPress")
    // 按压高光/阴影增强（上游 LiquidButton 同款果冻感反馈，仅可点击表面生效）
    val pressGlow by animateFloatAsState(
        if (pressed && onClick != null) 1f else 0f, label = "glassGlow"
    )

    val shapeProvider: () -> Shape = {
        when {
            circle -> CircleShape
            capsule -> Capsule()
            else -> RoundedRectangle(radius)
        }
    }
    val shape = shapeProvider()

    if (isM3) {
        // 严格 MD3：不透明 surface + elevation 色调角色；无玻璃折射、无整面半透明染色。
        // 形状吸附到 MD3 形状令牌（4/8/12/16/28），胶囊/圆形对应 MD3 full 形状。
        val m3Shape: Shape = when {
            circle -> CircleShape
            capsule -> Md3.ShapeFull
            else -> RoundedCornerShape(Md3.shapeFor(radius))
        }
        val container = (tint ?: md3.surface).copy(alpha = 1f)
        val contentColor = if (tint != null) Md3.onColorFor(container) else md3.onSurface
        val stateLayer = md3StateLayer(interaction, contentColor)
        Box(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick
                        )
                    } else Modifier
                )
                .background(container, m3Shape)
                .background(stateLayer, m3Shape)
                .then(
                    if (!enabled) {
                        Modifier.background(md3.onSurface.copy(alpha = Md3.DisabledContainer), m3Shape)
                    } else Modifier
                ),
            content = content
        )
        return
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick
                    )
                } else Modifier
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            // 深色下 blur 减半：大模糊会把深底糊成灰黑（上游 AdaptiveLuminanceGlass 同规律）
                            blur(blurRadius.toPx())
                            lens(
                                refractionHeight.toPx(),
                                refractionAmount.toPx(),
                                depthEffect = lensDepth,
                                chromaticAberration = lensChromatic
                            )
                        },
                        highlight = {
                            if (onClick != null) {
                                Highlight.Default.copy(alpha = 0.12f + 0.3f * pressGlow)
                            } else Highlight.Ambient
                        },
                        shadow = { Shadow.Default.copy(alpha = 0.08f + 0.22f * pressGlow) },
                        innerShadow = { InnerShadow.Default },
                        onDrawSurface = {
                            val t = tint ?: c.glassFill
                            drawRect(t.copy(alpha = tintAlpha))
                        }
                    )
                } else {
                    Modifier.background(
                        (tint ?: c.glassFill).copy(alpha = tintAlpha),
                        shape
                    )
                }
            ),
        content = content
    )
}

/**
 * 卡片容器（MD3 三种卡片变体 / MIUIX 液态玻璃）。
 *
 * MATERIAL 模式：
 * - `filled=false` = **outlined card**：surface + 1dp outlineVariant 描边，elevation 0；
 * - `filled=true`  = **filled card（模态浮层）**：surfaceContainerHigh（elevation 3 的色调角色）
 *   + extraLarge(28dp) 圆角，无描边 —— 对话框/选择器等模态面的 MD3 规范形态。
 * MIUIX 模式：保持原有液态玻璃视觉（`filled=false` 半透明内容卡 / `filled=true` 玻璃浮层）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    tint: Color? = null,
    tintAlpha: Float = 0.5f,
    onClick: (() -> Unit)? = null,
    filled: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current

    if (isM3) {
        val shapeDp = if (filled) Md3.ShapeExtraLarge else Md3.shapeFor(radius)
        val shape = RoundedCornerShape(shapeDp)
        val container = (tint ?: if (filled) md3.surfaceContainerHigh else md3.surface).copy(alpha = 1f)
        val outlined = !filled
        val interaction = remember { MutableInteractionSource() }
        val stateLayer = md3StateLayer(interaction, md3.onSurface)
        Box(
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick
                        )
                    } else Modifier
                )
                .then(
                    if (outlined) {
                        Modifier.border(Md3.CardOutlineWidth, md3.outlineVariant, shape)
                    } else Modifier
                )
                .background(container, shape)
                .background(stateLayer, shape),
            content = content
        )
        return
    }

    if (filled) {
        GlassSurface(
            modifier = modifier,
            radius = radius,
            tint = tint,
            tintAlpha = tintAlpha,
            onClick = onClick
        ) {
            // 描边覆盖层必须用 matchParentSize 而不是 fillMaxSize：
            // fillMaxSize 会把「自适应高度」的父容器顶到可用高度上限，
            // 导致 MIUIX 对话框被拉成接近整屏高、内容下方留大片空白。
            Box(
                Modifier
                    .matchParentSize()
                    .border(0.5.dp, c.divider, RoundedCornerShape(radius))
            )
            content()
        }
    } else {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val shape = RoundedCornerShape(radius)
        // 审计 U3：内容卡此前完全透明（tintAlpha 被忽略），仅靠发丝边框分组，
        // 层级感弱。现应用真实半透明填充（下限 0.7），分组清晰且保留玻璃质感；
        // 传入的 tintAlpha 小于 0.7 时按 0.7 兜底，保证可读性。
        val cardFill = (tint ?: c.card).copy(alpha = tintAlpha.coerceAtLeast(0.7f))
        Box(
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick
                        )
                    } else Modifier
                )
                .clip(shape)
                .background(cardFill)
                // 按压反馈：在填充之上叠一层弱高亮，而不是替换填充（卡片不消失）
                .background(if (pressed) c.textPrimary.copy(alpha = 0.06f) else Color.Transparent)
                .border(0.5.dp, c.divider, shape),
            content = content
        )
    }
}

/** 玻璃主按钮（MIUIX） / MD3 filled button（MATERIAL） */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
    textColor: Color? = null,
    leadingIcon: Painter? = null,
    // 审计 P2-2：主图标旁有文字时图标为装饰性（默认 null）；
    // 若图标本身承载独立语义，调用方可传入无障碍描述
    leadingIconContentDescription: String? = null
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "btnPress")

    if (isM3) {
        // 严格 MD3 按钮三态：
        // ① 主操作（无 tint）= filled button：容器 primary / 内容 onPrimary；
        // ② 语义操作（tint = 语义色，如绿色"已服用"）= filled button + 自定义容器色，
        //    内容色按 WCAG AA 对比度自动选取（Md3.onColorFor）；
        // ③ 次级操作（tint = c.divider，即 MD3 outlineVariant）= outlined button：
        //    透明容器 + 1dp outline 描边 + onSurface 文字。
        // 通用规范：高度 40dp / full 圆角 / 标签 labelLarge(14sp,w500) /
        // 水平内边距 24dp / 前导图标 18dp + 间距 8dp / elevation 0；
        // 状态层 = 内容色 10%（pressed）；禁用 = 内容 38% + 容器 onSurface 12%。
        val isOutlined = tint != null && tint == c.divider
        val container = when {
            isOutlined -> Color.Transparent
            tint != null -> tint.copy(alpha = 1f)
            else -> md3.primary
        }
        val contentColor = when {
            isOutlined -> md3.onSurface
            textColor != null -> textColor
            tint != null -> Md3.onColorFor(container)
            else -> md3.onPrimary
        }
        val stateLayer = md3StateLayer(interaction, contentColor)
        val disabledContainer = md3.onSurface.copy(alpha = Md3.DisabledContainer)
        Box(
            modifier
                .height(Md3.ButtonHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 1f
                }
                .clip(Md3.ShapeFull)
                .then(
                    if (isOutlined) {
                        Modifier.border(
                            if (enabled) Md3.TextFieldBorderWidth else 0.dp,
                            if (enabled) md3.outline else Color.Transparent,
                            Md3.ShapeFull
                        )
                    } else Modifier
                )
                .background(
                    when {
                        !enabled -> disabledContainer
                        isOutlined -> Color.Transparent
                        else -> container
                    },
                    Md3.ShapeFull
                )
                .background(stateLayer, Md3.ShapeFull)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = Md3.ButtonHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Image(
                        painter = leadingIcon,
                        contentDescription = leadingIconContentDescription,
                        modifier = Modifier.size(Md3.ButtonIconSize),
                        colorFilter = ColorFilter.tint(
                            if (enabled) contentColor else contentColor.copy(alpha = Md3.DisabledContent)
                        )
                    )
                    Spacer(Modifier.width(Md3.ButtonIconGap))
                }
                BasicText(
                    text = text,
                    style = Md3.LabelLarge.copy(
                        color = if (enabled) contentColor else contentColor.copy(alpha = Md3.DisabledContent)
                    )
                )
            }
        }
        return
    }

    val fg = textColor ?: c.textPrimary

    GlassSurface(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            },
        capsule = true,
        blurRadius = 10.dp,
        refractionHeight = 14.dp,
        refractionAmount = 28.dp,
        lensDepth = false,
        lensChromatic = true,
        tint = tint,
        // 审计 U1：显式着色（绿/红/蓝等语义色）时用近实心填充 —— 50% 透明 + 白字
        // 会让浅色底透出，对比度 ≈1.5:1 几乎不可读；近实心后恢复 iOS 实心按钮观感。
        tintAlpha = if (tint != null) 0.9f else 0.5f,
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Image(
                    painter = leadingIcon,
                    contentDescription = leadingIconContentDescription,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(fg)
                )
                Spacer(Modifier.width(8.dp))
            }
            BasicText(
                text = text,
                // 着色按钮 = 主要操作，iOS 规范用半粗体提升可读性
                style = TextStyle(
                    fontFamily = SfPro, color = fg, fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (tint != null) FontWeight.SemiBold else FontWeight.Normal
                )
            )
        }
    }
}

/**
 * 圆形图标按钮（MIUIX） / MD3 icon button + small FAB（MATERIAL）
 *
 * MATERIAL 模式：
 * - 无 tint = 标准图标按钮：40dp 圆形容器（无底色）、图标 24dp onSurfaceVariant、状态层 onSurface 10%；
 * - 有 tint = FAB：40dp（small FAB）容器着色、large(16dp) 圆角、图标取 onColorFor(容器)（AA 对比度）。
 */
@Composable
fun GlassIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    tint: Color? = null
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    val interaction = remember { MutableInteractionSource() }

    if (isM3) {
        val container = tint?.copy(alpha = 1f)
        val iconTint = if (container != null) Md3.onColorFor(container) else md3.onSurfaceVariant
        val stateLayer = md3StateLayer(interaction, iconTint)
        val shape: Shape = if (container != null) {
            RoundedCornerShape(Md3.ShapeLarge)
        } else {
            CircleShape
        }
        Box(
            modifier
                .size(Md3.IconButtonSize)
                .then(
                    if (container != null) Modifier.background(container, shape) else Modifier
                )
                .background(stateLayer, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(Md3.TopAppBarIconSize),
                colorFilter = ColorFilter.tint(iconTint)
            )
        }
        return
    }

    // ── MIUIX：液态玻璃圆形图标按钮（保持原有视觉与交互）──────────────────
    GlassSurface(
        modifier = modifier.size(40.dp),
        circle = true,
        blurRadius = 8.dp,
        refractionHeight = 12.dp,
        refractionAmount = 24.dp,
        lensDepth = false,
        lensChromatic = true,
        tint = tint,
        // 审计 U1：同 GlassButton，着色时近实心（FAB 等）
        tintAlpha = if (tint != null) 0.9f else 0.5f,
        onClick = onClick
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .align(Alignment.Center)
                .size(iconSize),
            // 审计 U1 修复：着色底上的图标应为白色（原先与底同色，实心后不可见）
            colorFilter = ColorFilter.tint(if (tint != null) Color.White else c.textPrimary)
        )
    }
}

/**
 * 开关（MIUIX = iOS 风格 / MATERIAL = MD3 Switch）
 *
 * MD3 规范几何：轨道 52×32；选中滑块 24dp（onPrimary）、未选中滑块 16dp（outline）；
 * 选中轨道 primary，未选中轨道 surfaceContainerHighest + 2dp outline 描边；
 * 滑块尺寸/位移用 DurationShort4(200ms) + Standard 缓动过渡。
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current

    if (isM3) {
        val spec = tween<Dp>(durationMillis = Md3.DurationShort4, easing = Md3.EasingStandard)
        val thumbSize by animateDpAsState(
            targetValue = if (checked) Md3.SwitchThumbSelected else Md3.SwitchThumbUnselected,
            animationSpec = spec,
            label = "m3SwitchThumbSize"
        )
        val thumbX by animateDpAsState(
            targetValue = if (checked) {
                Md3.SwitchTrackWidth - Md3.SwitchThumbSelected - Md3.SwitchThumbInset
            } else {
                Md3.SwitchThumbInset
            },
            animationSpec = spec,
            label = "m3SwitchThumbX"
        )
        val trackColor = if (checked) md3.primary else md3.surfaceContainerHighest
        val thumbColor = if (checked) md3.onPrimary else md3.outline
        Box(
            modifier
                .size(Md3.SwitchTrackWidth, Md3.SwitchTrackHeight)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .clip(Md3.ShapeFull)
                .background(trackColor)
                .then(
                    if (!checked) {
                        Modifier.border(2.dp, md3.outline, Md3.ShapeFull)
                    } else Modifier
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .offset(x = thumbX)
                    .size(thumbSize)
                    .background(thumbColor, CircleShape)
            )
        }
        return
    }

    val trackColor = if (checked) c.green else c.divider
    val knobOffset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switchKnob")

    // 审计 U5：用 toggleable 提供语义角色（Role.Switch + 开/关状态），读屏可正确播报
    Box(
        modifier
            .width(50.dp)
            .height(30.dp)
            .clip(Capsule())
            .background(trackColor)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Box(
            Modifier
                .offset(x = knobOffset, y = 2.dp)
                .size(26.dp)
                // 修复"白方块+灰圆重叠"：勿用 graphicsLayer{shadowElevation}——
                // 该 layer 轮廓取矩形边界，会把圆形滑块裁成白方块并叠出灰影环。
                // 改用 shadow(shape=CircleShape) 投射圆形阴影 + background(color,CircleShape) 直接画圆。
                .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                .background(Color.White, CircleShape)
        )
    }
}

/** 段落标题（MIUIX） / MD3 list subheader（MATERIAL） */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    if (isM3) {
        // MD3 列表子标题：titleSmall(14sp,w500,ls 0.1) + onSurfaceVariant，高度 48dp，起始内边距 16dp
        Box(
            modifier
                .height(Md3.ListSubheaderHeight)
                .padding(start = Md3.ListItemHorizontalPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicText(text, style = Md3.TitleSmall.copy(color = md3.onSurfaceVariant))
        }
        return
    }
    BasicText(
        text = text,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = colors.textSecondary, fontSize = 13.sp))
    )
}

/** 圆环进度（MIUIX 仿 iOS 健康 / MATERIAL = MD3 determinate circular progress） */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = colors.primary,
    trackColor: Color = colors.divider,
    strokeWidth: Dp = 13.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val p = progress.coerceIn(0f, 1f)
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current
    // MD3 圆环进度指示器：active = primary，track = secondaryContainer，线宽 4dp
    val activeColor = if (isM3) md3.primary else color
    val m3TrackColor = if (isM3) md3.secondaryContainer else trackColor
    val m3Stroke = if (isM3) 4.dp else strokeWidth
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = m3Stroke.toPx()
            val pad = sw / 2f + 2.dp.toPx()
            val d = size.minDimension - pad * 2
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val rect = Size(d, d)
            drawArc(
                color = m3TrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = rect,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            if (p > 0f) {
                drawArc(
                    color = activeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = topLeft,
                    size = rect,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** 药物图标（彩色圆底 + 白色矢量；MATERIAL 模式底色上的图标按 AA 对比度取 on 色） */
@Composable
fun MedIcon(
    painter: Painter,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
            colorFilter = ColorFilter.tint(
                if (isM3) Md3.onColorFor(color) else Color.White
            )
        )
    }
}

/**
 * 简单弹窗容器（MIUIX 玻璃浮层 / MATERIAL = MD3 basic dialog）
 *
 * MD3 规范：scrim = 黑色 32%；容器 = surfaceContainerHigh（elevation 3 色调角色）+ extraLarge(28dp) 圆角；
 * 内边距 24dp；标题 headlineSmall(24sp)、正文 bodyMedium(14sp, onSurfaceVariant)；
 * 操作按钮为右对齐文字按钮（labelLarge，最小高度 40dp，水平内边距 12dp）。
 *
 * 转场：进入/退出统一走 [AnimatedModalHost]（MD3 = 300ms 淡入+92% 缩放 / 200ms 淡出；
 * MIUIX = 300ms 淡入+95% 缩放 / 200ms 淡出）。所有关闭路径（点遮罩、取消、确认）
 * 都先播退出动画再执行动作，调用方仍是 `if (show) { GlassDialogBox(...) }` 的写法。
 */
@Composable
fun GlassDialogBox(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 破坏性操作（删除药品 / 清空全部数据）。
     * MD3：确认按钮改用 error 角色色，退让按钮保持 onSurfaceVariant，
     * 保持「退让在前、确认在后」的顺序与 8dp 间距。仅影响颜色，不改变任何行为。
     */
    destructive: Boolean = false
) {
    val c = colors
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
            // MD3 响应式：先按屏幕可用尺寸夹出「24dp 留白 + 最大 560dp + 最大高度」，
            // 再让内容在超高时滚动 —— 横屏/平板/超长正文都不会溢出或被切成通栏。
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Md3.DialogScreenMargin, vertical = Md3.DialogScreenMargin)
                    .widthIn(max = Md3.DialogMaxWidth)
                    .then(modifier),
                contentAlignment = Alignment.Center
            ) {
            GlassCard(
                modifier = Modifier
                    .widthIn(min = minOf(Md3.DialogMinWidth, maxWidth))
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                radius = Md3.DialogCornerRadius,
                tintAlpha = 0.94f,
                filled = true
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                if (isM3) {
                    Md3DialogContent(
                        title = title,
                        message = message,
                        confirmText = confirmText,
                        dismissText = dismissText,
                        onConfirm = { closeWith { onConfirm() } },
                        onDismiss = { closeWith {} },
                        destructive = destructive
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(Md3.DialogPadding)
                    ) {
                        BasicText(title, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 17.sp)))
                        if (!message.isNullOrEmpty()) {
                            // 标题 → 正文 16dp（与 MD3 一致），强化「标题 / 说明文字」两级层级
                            Spacer(Modifier.height(16.dp))
                            BasicText(message, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 14.sp)))
                        }
                        // 正文 → 操作区 24dp
                        Spacer(Modifier.height(24.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissText != null) {
                                BasicText(
                                    dismissText,
                                    modifier = Modifier
                                        .testTag(TestTags.DIALOG_DISMISS)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { closeWith {} }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.primary, fontSize = 15.sp))
                                )
                            }
                            // MD3：两个文字按钮之间 8dp
                            Spacer(Modifier.width(8.dp))
                            BasicText(
                                confirmText,
                                modifier = Modifier
                                    .testTag(TestTags.DIALOG_CONFIRM)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { closeWith { onConfirm() } }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro,
                                    color = if (destructive) c.red else c.primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                ))
                            )
                        }
                    }
                }
                }   // 滚动容器
            }
            }   // BoxWithConstraints（宽度 / 高度夹取）
        }
    }
}

/** MD3 对话框内容（标题 + 正文 + 右对齐文字按钮组） */
@Composable
private fun Md3DialogContent(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    val md3 = LocalMd3Colors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(Md3.DialogPadding)
    ) {
        // MD3 对话框排印：标题 headlineSmall(24/32, onSurface) → 16dp → 正文 bodyMedium(14/20, onSurfaceVariant)
        BasicText(title, style = Md3.HeadlineSmall.copy(color = md3.onSurface))
        if (!message.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            BasicText(message, style = Md3.BodyMedium.copy(color = md3.onSurfaceVariant))
        }
        // 正文 → 操作区 24dp；操作区右对齐，退让在前、确认在后，两者 8dp 间距
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dismissText != null) {
                Md3TextButton(
                    text = dismissText, color = md3.onSurfaceVariant, onClick = onDismiss,
                    modifier = Modifier.testTag(TestTags.DIALOG_DISMISS)
                )
                Spacer(Modifier.width(8.dp))
            }
            Md3TextButton(
                text = confirmText,
                color = if (destructive) md3.error else md3.primary,
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.DIALOG_CONFIRM)
            )
        }
    }
}

/**
 * MD3 文字按钮：labelLarge、最小高度 40dp、水平内边距 12dp、full 圆角、
 * 状态层 = 文字色 10%（pressed）。无涟漪（沿用项目既有决策，避免默认 ripple 的灰色中间态）。
 */
@Composable
internal fun Md3TextButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val stateLayer = md3StateLayer(interaction, color)
    Box(
        modifier
            .heightIn(min = Md3.TextButtonHeight)
            .clip(Md3.ShapeFull)
            .background(stateLayer, Md3.ShapeFull)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Md3.TextButtonHorizontalPadding, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = Md3.LabelLarge.copy(color = color))
    }
}

/**
 * 单选列表弹窗（MIUIX 玻璃浮层 / MATERIAL = MD3 single-choice dialog）
 *
 * MD3 规范：选项为单选列表项（高度 56dp、标签 bodyLarge、右侧 20dp radio）；
 * 选中项文字 onSurface + radio 选中态（primary 外环 + 10dp primary 实心点），
 * 未选中项文字 onSurfaceVariant + outline 外环。
 */
@Composable
fun GlassChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val c = colors
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
            // MD3 响应式：24dp 屏幕留白 + 最大宽度 560dp + 最大高度夹取，
            // 选项区超高时可滚动 —— 平板 / 横屏 / 选项很多时都不会溢出。
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Md3.DialogScreenMargin, vertical = Md3.DialogScreenMargin)
                    .widthIn(max = Md3.DialogMaxWidth),
                contentAlignment = Alignment.Center
            ) {
            GlassCard(
                Modifier
                    .widthIn(min = minOf(Md3.DialogMinWidth, maxWidth))
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                radius = Md3.DialogCornerRadius,
                tintAlpha = 0.94f,
                filled = true
            ) {
                Column {
                    BasicText(
                        title,
                        modifier = Modifier.padding(
                            start = Md3.DialogPadding, end = Md3.DialogPadding,
                            top = Md3.DialogPadding, bottom = 8.dp
                        ),
                        style = if (isM3) {
                            Md3.HeadlineSmall.copy(color = md3.onSurface)
                        } else {
                            mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 17.sp))
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    // 选项区独立滚动：选项多 / 文案超长换行时列表自己滚，标题保持固定
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = Md3.DialogPadding)
                    ) {
                    options.forEachIndexed { i, opt ->
                        val sel = i == selectedIndex
                        if (isM3) {
                            val interaction = remember { MutableInteractionSource() }
                            val stateLayer = md3StateLayer(interaction, md3.onSurface)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    // 最小 48dp（MD3 可点击目标下限），
                                    // 不再写死 56dp —— 超长文案换行时整项自动增高而不是被裁掉。
                                    .heightIn(min = Md3.DialogListItemMinHeight)
                                    .background(stateLayer)
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = null
                                    ) { closeWith { onPick(i) } }
                                    .padding(horizontal = Md3.DialogPadding, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    opt,
                                    modifier = Modifier.weight(1f),
                                    style = Md3.BodyLarge.copy(
                                        color = if (sel) md3.onSurface else md3.onSurfaceVariant
                                    )
                                )
                                Md3RadioButton(selected = sel)
                            }
                        } else {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Md3.DialogListItemMinHeight)
                                    .clickable { closeWith { onPick(i) } }
                                    .padding(horizontal = Md3.DialogPadding, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    opt,
                                    modifier = Modifier.weight(1f),
                                    style = mediTextStyle(TextStyle(fontFamily = SfPro,
                                        color = if (sel) c.primary else c.textPrimary,
                                        fontSize = 16.sp
                                    ))
                                )
                                if (sel) {
                                    BasicText(
                                        "✓",
                                        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.primary, fontSize = 16.sp))
                                    )
                                }
                    }   // 选项滚动区
                            }
            }   // BoxWithConstraints（宽度 / 高度夹取）
                        }
                    }
                }
            }
        }
    }
}

/** MD3 单选按钮：20dp；选中 = primary 2dp 外环 + 10dp primary 实心点，未选中 = outline 2dp 外环 */
@Composable
private fun Md3RadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val md3 = LocalMd3Colors.current
    Canvas(modifier.size(Md3.RadioButtonSize)) {
        val ringColor = if (selected) md3.primary else md3.outline
        val stroke = 2.dp.toPx()
        val r = size.minDimension / 2f - stroke / 2f
        drawCircle(
            color = ringColor,
            radius = r,
            style = Stroke(width = stroke)
        )
        if (selected) {
            drawCircle(
                color = md3.primary,
                radius = 5.dp.toPx()
            )
        }
    }
}
