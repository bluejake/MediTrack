package com.medilog.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material Design 3 设计令牌（单一取值来源）。
 *
 * 所有数值均取自 MD3 规范（m3.material.io 的 baseline scheme / shape / typescale / motion / state 令牌），
 * 不掺杂本项目自定义值。MATERIAL 主题模式下的每个自绘组件只从这里取色、取形、取字、取动效。
 *
 * 说明：
 * - 字体族沿用 [SfPro]（项目约定的品牌字体，MD3 允许替换品牌字形），但**度量全部采用 MD3 typescale**
 *   （字号 / 行高 / 字重 / 字距），不沿用 iOS 的自定义字号体系。
 * - 颜色为 MD3 baseline 配色（Material You 紫），非动态取色；设备支持时可由系统壁纸派生，
 *   但为避免运行时依赖，本工程固定 baseline 值（浅色/深色两套，均已校验对比度）。
 */
object Md3 {

    // ── 形状令牌（ShapeTokens）────────────────────────────────────────────
    /** extraSmall 4dp：输入框、小卡片 */
    val ShapeExtraSmall: Dp = 4.dp

    /** small 8dp：芯片、小浮层 */
    val ShapeSmall: Dp = 8.dp

    /** medium 12dp：卡片、分段按钮内部格 */
    val ShapeMedium: Dp = 12.dp

    /** large 16dp：FAB、大卡片 */
    val ShapeLarge: Dp = 16.dp

    /** extraLarge 28dp：对话框、全屏模态 */
    val ShapeExtraLarge: Dp = 28.dp

    /** full = 50%：按钮、开关、导航指示器（胶囊） */
    val ShapeFull = RoundedCornerShape(percent = 50)

    /**
     * 把调用方传入的圆角吸附到最近的 MD3 形状令牌。
     * 自定义圆角数值（18/20/22/26dp 等）一律收敛到 {4, 8, 12, 16, 28}，
     * 这是「形状统一采用 MD3 规范值」的落地方式：保留调用方意图，去掉非标圆角。
     */
    fun shapeFor(radius: Dp): Dp {
        val tokens = listOf(ShapeExtraSmall, ShapeSmall, ShapeMedium, ShapeLarge, ShapeExtraLarge)
        return tokens.minByOrNull { kotlin.math.abs(it.value - radius.value) } ?: ShapeMedium
    }

    // ── 状态层不透明度（State layers）─────────────────────────────────────
    /** hover 8% */
    const val StateHover = 0.08f

    /** focus 10% */
    const val StateFocus = 0.10f

    /** pressed 10% */
    const val StatePressed = 0.10f

    /** dragged 16% */
    const val StateDragged = 0.16f

    /** 禁用态：内容 38% */
    const val DisabledContent = 0.38f

    /** 禁用态：容器 12% */
    const val DisabledContainer = 0.12f

    // ── 动效令牌（Motion tokens）──────────────────────────────────────────
    /** Standard：cubic-bezier(0.2, 0, 0, 1) */
    val EasingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard decelerate：cubic-bezier(0, 0, 0, 1) */
    val EasingStandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Standard accelerate：cubic-bezier(0.3, 0, 1, 1) */
    val EasingStandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Emphasized decelerate：cubic-bezier(0.05, 0.7, 0.1, 1)（入场/落定） */
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Emphasized accelerate：cubic-bezier(0.3, 0, 0.8, 0.15)（退场） */
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** DurationShort1 50ms */
    const val DurationShort1 = 50

    /** DurationShort2 100ms */
    const val DurationShort2 = 100

    /** DurationShort3 150ms */
    const val DurationShort3 = 150

    /** DurationShort4 200ms */
    const val DurationShort4 = 200

    /** DurationMedium1 250ms */
    const val DurationMedium1 = 250

    /** DurationMedium2 300ms */
    const val DurationMedium2 = 300

    /** DurationMedium3 350ms */
    const val DurationMedium3 = 350

    /** DurationMedium4 400ms */
    const val DurationMedium4 = 400

    /** DurationLong1 450ms */
    const val DurationLong1 = 450

    // ── 组件几何（Component specs）───────────────────────────────────────

    // 按钮（Filled button / Tonal button / Text button）
    /** 按钮高度 40dp */
    val ButtonHeight: Dp = 40.dp

    /** 按钮水平内边距 24dp */
    val ButtonHorizontalPadding: Dp = 24.dp

    /** 按钮前导图标 18dp */
    val ButtonIconSize: Dp = 18.dp

    /** 图标与文字间距 8dp */
    val ButtonIconGap: Dp = 8.dp

    /** 文字按钮最小高度 40dp（对话框中同为 40dp，命中区由 padding 补足） */
    val TextButtonHeight: Dp = 40.dp

    /** 文字按钮水平内边距 12dp */
    val TextButtonHorizontalPadding: Dp = 12.dp

    // 图标按钮 / FAB
    /** 标准图标按钮容器 40dp */
    val IconButtonSize: Dp = 40.dp

    /** FAB 容器 56dp */
    val FabSize: Dp = 56.dp

    /** FAB 图标 24dp */
    val FabIconSize: Dp = 24.dp

    // 卡片
    /** outlined 卡片描边宽度 1dp（outlineVariant） */
    val CardOutlineWidth: Dp = 1.dp

    /** 对话框容器内边距 24dp */
    val DialogPadding: Dp = 24.dp

    /** 对话框最小宽度 280dp */
    val DialogMinWidth: Dp = 280.dp

    /**
     * 对话框最大宽度 560dp。
     * 没有上限时，平板 / 横屏 / 折叠屏展开态下对话框会被拉成一条通栏横幅，
     * 阅读行长过长、按钮跑到视野边缘 —— 这是最典型的响应式破相点。
     */
    val DialogMaxWidth: Dp = 560.dp

    /** 对话框距屏幕边缘的最小留白 24dp */
    val DialogScreenMargin: Dp = 24.dp

    /** 对话框圆角 28dp（MD3 ShapeExtraLarge） */
    val DialogCornerRadius: Dp = 28.dp

    /** 对话框内列表项最小高度 48dp（MD3 可点击目标下限，且允许超长文案换行撑高） */
    val DialogListItemMinHeight: Dp = 48.dp

    /** 对话框 scrim：黑色 32% */
    const val DialogScrimAlpha = 0.32f

    /** 对话框头部图标 24dp */
    val DialogIconSize: Dp = 24.dp

    // 顶栏（Top app bar · small）
    /** 小型顶栏高度 64dp */
    val TopAppBarHeight: Dp = 64.dp

    /** 顶栏标题左右内边距 16dp（有前导图标时 4dp） */
    val TopAppBarTitlePadding: Dp = 16.dp

    /** 顶栏前导/尾部图标 24dp */
    val TopAppBarIconSize: Dp = 24.dp

    // 底部导航栏（Navigation bar）
    // 以下数值全部来自 Material3 令牌（已对照类路径 material3 构件的
    // NavigationBarTokens / NavigationBarVerticalItemTokens 逐一核实）：
    /** 导航栏高度 80dp（NavigationBarTokens.TallContainerHeight） */
    val NavigationBarHeight: Dp = 80.dp

    /**
     * 激活指示器宽度 **56dp**（NavigationBarVerticalItemTokens.ActiveIndicatorWidth）。
     * ⚠ 旧值 64dp 是 MD3 早期规范的数值；本工程依赖的 material3 构件里令牌实为 56dp。
     */
    val NavigationIndicatorWidth: Dp = 56.dp

    /** 激活指示器高度 32dp（ActiveIndicatorHeight） */
    val NavigationIndicatorHeight: Dp = 32.dp

    /** 激活指示器形状 = CornerFull（全圆角胶囊，RoundedCornerShape(50%)） */
    val NavigationIndicatorShape = ShapeFull

    /**
     * 指示器顶部距导航栏顶 12dp。
     * 推导（与 Material3 NavigationBarItemLayout 一致）：
     * 内容垂直内边距 = (80 − (24 图标 + 4 指示器垂直 padding + 4 图标-标签间距 + 16 标签)) / 2 = 16dp；
     * 指示器顶部 = 图标顶部 16dp − 指示器垂直 padding (32−24)/2 = 4dp → 12dp。
     */
    val NavigationIndicatorTop: Dp = 12.dp

    /** 导航栏图标 24dp（NavigationBarVerticalItemTokens.IconSize） */
    val NavigationIconSize: Dp = 24.dp

    /** 指示器与标签间距 4dp（NavigationBarTokens.ItemActiveIndicatorIconLabelSpace） */
    val NavigationIndicatorToLabelPadding: Dp = 4.dp

    /** 指示器水平 padding 16dp = (ActiveIndicatorWidth 56 − IconSize 24) / 2 */
    val NavigationIndicatorHorizontalPadding: Dp = 16.dp

    /** 指示器垂直 padding 4dp = (ActiveIndicatorHeight 32 − IconSize 24) / 2 */
    val NavigationIndicatorVerticalPadding: Dp = 4.dp

    // 列表项（List item）
    /** 单行列表项高度 56dp */
    val ListItemOneLineHeight: Dp = 56.dp

    /** 两行列表项高度 72dp */
    val ListItemTwoLineHeight: Dp = 72.dp

    /** 三行列表项高度 88dp */
    val ListItemThreeLineHeight: Dp = 88.dp

    /** 列表项水平内边距 16dp */
    val ListItemHorizontalPadding: Dp = 16.dp

    /** 列表项前导图标 24dp */
    val ListItemLeadingIconSize: Dp = 24.dp

    /** 列表分组子标题（list subheader）高度 48dp */
    val ListSubheaderHeight: Dp = 48.dp

    // 响应式布局
    /**
     * 内容区最大宽度 640dp。
     * 手机竖屏（≤440dp）不生效；平板 / 横屏等大屏上把列表/表单限制在可读宽度并居中，
     * 避免行宽过长（MD3 可读性建议：单行文本不超过约 60–75 字符）。
     */
    val ContentMaxWidth: Dp = 640.dp

    // 输入框（Outlined text field）
    /** 输入框最小高度 56dp */
    val TextFieldHeight: Dp = 56.dp

    /** 输入框水平内边距 16dp */
    val TextFieldHorizontalPadding: Dp = 16.dp

    /** 未聚焦描边 1dp（outline） */
    val TextFieldBorderWidth: Dp = 1.dp

    /** 聚焦描边 2dp（primary） */
    val TextFieldFocusedBorderWidth: Dp = 2.dp

    // 分段按钮（Segmented button）
    /** 分段按钮高度 40dp */
    val SegmentedButtonHeight: Dp = 40.dp

    /** 分段按钮选中项复选图标 18dp */
    val SegmentedCheckIconSize: Dp = 18.dp

    // 开关（Switch）
    /** 轨道 52×32 */
    val SwitchTrackWidth: Dp = 52.dp
    val SwitchTrackHeight: Dp = 32.dp

    /** 选中滑块 24dp */
    val SwitchThumbSelected: Dp = 24.dp

    /** 未选中滑块 16dp */
    val SwitchThumbUnselected: Dp = 16.dp

    /** 滑块与轨道边缘间距 2dp */
    val SwitchThumbInset: Dp = 2.dp

    /** 单选按钮（Radio button）20dp */
    val RadioButtonSize: Dp = 20.dp

    // ── 排版（Type scale）────────────────────────────────────────────────
    // 度量全部为 MD3 typescale 规范值；字族为品牌字体 SfPro。
    val DisplayLarge: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp
    )
    val DisplayMedium: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp
    )
    val DisplaySmall: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp
    )
    val HeadlineLarge: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp
    )
    val HeadlineMedium: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    )
    val HeadlineSmall: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    )
    val TitleLarge: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    )
    val TitleMedium: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    )
    val TitleSmall: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    )
    val BodyLarge: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    )
    val BodyMedium: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    )
    val BodySmall: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    )
    val LabelLarge: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    )
    val LabelMedium: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
    val LabelSmall: TextStyle = TextStyle(
        fontFamily = SfPro, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )

    /**
     * 为「自定义容器色」选配对的前景色（on-color）。
     *
     * MD3 要求文字与容器对比度 ≥ 4.5:1（AA）。语义色按钮（绿=已服用、红=删除等）
     * 的容器色由调用方给定，on 色不能写死白色——按 WCAG 相对亮度取对比度更高的一方。
     */
    fun onColorFor(container: Color): Color {
        val lum = container.luminance()
        val white = Color.White
        val dark = Color(0xFF1D1B20)
        val cWhite = contrastRatio(white.luminance(), lum)
        val cDark = contrastRatio(dark.luminance(), lum)
        return if (cWhite >= cDark) white else dark
    }

    private fun contrastRatio(a: Float, b: Float): Float {
        val l1 = a.coerceAtLeast(b)
        val l2 = a.coerceAtMost(b)
        return (l1 + 0.05f) / (l2 + 0.05f)
    }
}

/**
 * MD3 完整颜色角色（baseline scheme）。
 * 与 [MediColors]（iOS/MIUIX 体系）解耦：MATERIAL 模式的组件只读这里的角色名，
 * 避免把 MD3 角色硬塞进 iOS 字段名造成语义混淆。
 */
@Immutable
data class Md3Colors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceTint: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    /** MD3 `inverse-primary`：Snackbar 操作文字色（撤销按钮用） */
    val inversePrimary: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val dark: Boolean
) {
    /**
     * elevation（0–5）→  tonal surface 角色（MD3 规范映射表）：
     * 0=surface, 1=surfaceContainerLow, 2=surfaceContainer,
     * 3=surfaceContainerHigh, 4/5=surfaceContainerHighest（5 额外带阴影）。
     */
    fun surfaceAt(elevation: Dp): Color = when (elevation.value) {
        0f -> surface
        1f -> surfaceContainerLow
        2f -> surfaceContainer
        3f -> surfaceContainerHigh
        else -> surfaceContainerHighest
    }

    companion object {
        /** MD3 baseline · 浅色 */
        val Light = Md3Colors(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
            tertiary = Color(0xFF7D5260),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD8E4),
            onTertiaryContainer = Color(0xFF31111D),
            error = Color(0xFFB3261E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF1D1B20),
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1D1B20),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            surfaceTint = Color(0xFF6750A4),
            inverseSurface = Color(0xFF322F35),
            inverseOnSurface = Color(0xFFF5EFF7),
            inversePrimary = Color(0xFFD0BCFF),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0),
            scrim = Color(0xFF000000),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F2FA),
            surfaceContainer = Color(0xFFF3EDF7),
            surfaceContainerHigh = Color(0xFFECE6F0),
            surfaceContainerHighest = Color(0xFFE6E0E9),
            dark = false
        )

        /** MD3 baseline · 深色 */
        val Dark = Md3Colors(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFEFB8C8),
            onTertiary = Color(0xFF492532),
            tertiaryContainer = Color(0xFF633B48),
            onTertiaryContainer = Color(0xFFFFD8E4),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            background = Color(0xFF1C1B1F),
            onBackground = Color(0xFFE6E0E9),
            surface = Color(0xFF1C1B1F),
            onSurface = Color(0xFFE6E0E9),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceTint = Color(0xFFD0BCFF),
            inverseSurface = Color(0xFFE6E0E9),
            inverseOnSurface = Color(0xFF322F35),
            inversePrimary = Color(0xFF6750A4),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            scrim = Color(0xFF000000),
            surfaceContainerLowest = Color(0xFF0F0D13),
            surfaceContainerLow = Color(0xFF1D1B20),
            surfaceContainer = Color(0xFF211F26),
            surfaceContainerHigh = Color(0xFF2B2930),
            surfaceContainerHighest = Color(0xFF36343B),
            dark = true
        )
    }
}

/** MATERIAL 模式的 MD3 颜色角色（两种模式下都提供，组件在 M3 分支无条件读取） */
val LocalMd3Colors = staticCompositionLocalOf { Md3Colors.Light }
