package com.medilog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 主题模式：Material Design（JetBrains Material3） / MIUIX（真·top.yukonga.miuix.kmp 组件库） */
enum class AppThemeMode { MATERIAL, MIUIX }

val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.MATERIAL }

/**
 * Material Design 3 完整配色（baseline scheme，角色无缺项）。
 *
 * 旧版只设了 primary/secondary/tertiary/background/surface 五个角色（其余走 MD2 默认值），
 * 导致 onPrimary / surfaceContainer* / outline / error 等角色缺失 → 组件只能自己猜颜色。
 * 现补齐 MD3 全部角色，[LocalMd3Colors] 与 MaterialTheme.colorScheme 同源。
 */
private val M3Light = lightColorScheme(
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
    surfaceBright = Color(0xFFFFFBFE),
    surfaceDim = Color(0xFFDED8E1),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)
private val M3Dark = darkColorScheme(
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
    surfaceBright = Color(0xFF36343B),
    surfaceDim = Color(0xFF141218),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerLowest = Color(0xFF0F0D13)
)

/** MD3 形状令牌（extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge 28） */
private val M3Shapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Md3.ShapeExtraSmall),
    small = androidx.compose.foundation.shape.RoundedCornerShape(Md3.ShapeSmall),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(Md3.ShapeMedium),
    large = androidx.compose.foundation.shape.RoundedCornerShape(Md3.ShapeLarge),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Md3.ShapeExtraLarge)
)

/**
 * MD3 排版：度量全部来自 [Md3] typescale 令牌（字号/行高/字重/字距），
 * 字族为品牌字体 SfPro（MD3 允许替换品牌字形）。
 */
private val M3Typography = Typography(
    displayLarge = Md3.DisplayLarge,
    displayMedium = Md3.DisplayMedium,
    displaySmall = Md3.DisplaySmall,
    headlineLarge = Md3.HeadlineLarge,
    headlineMedium = Md3.HeadlineMedium,
    headlineSmall = Md3.HeadlineSmall,
    titleLarge = Md3.TitleLarge,
    titleMedium = Md3.TitleMedium,
    titleSmall = Md3.TitleSmall,
    bodyLarge = Md3.BodyLarge,
    bodyMedium = Md3.BodyMedium,
    bodySmall = Md3.BodySmall,
    labelLarge = Md3.LabelLarge,
    labelMedium = Md3.LabelMedium,
    labelSmall = Md3.LabelSmall
)

/**
 * 按 (主题模式, 深浅) 产出自绘体系的 MediColors：
 * MATERIAL = Material You 紫 + M3 中性面（字段取值 = MD3 角色，见 [Md3Colors]）；
 * MIUIX = 小米橙 + MIUI 黑白灰面（保持原有视觉，不改动）。
 *
 * 这样做的目的：绝大多数界面文字/分隔线仍直接读 `colors.*`，
 * MATERIAL 模式下这些字段本身就是 MD3 角色值 → 全 App 自动获得 MD3 配色，
 * 无需逐屏改代码；MIUIX 模式取值不变。
 */
private fun themeColors(mode: AppThemeMode, dark: Boolean): MediColors {
    val base = if (dark) MediColors.Dark else MediColors.Light
    return when (mode) {
        AppThemeMode.MATERIAL -> base.copy(
            // ── MD3 角色映射 ──
            bg = if (dark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE),                 // surface
            card = if (dark) Color(0xFF1D1B20) else Color(0xFFF7F2FA),               // surfaceContainerLow
            cardSecondary = if (dark) Color(0xFF211F26) else Color(0xFFF3EDF7),      // surfaceContainer
            divider = if (dark) Color(0xFF49454F) else Color(0xFFCAC4D0),            // outlineVariant
            textPrimary = if (dark) Color(0xFFE6E0E9) else Color(0xFF1D1B20),        // onSurface
            textSecondary = if (dark) Color(0xFFCAC4D0) else Color(0xFF49454F),      // onSurfaceVariant
            // 审计：MD3 的 outline 角色用于描边而非文字，浅色下 #79747E 对 surface 仅 4.44:1
            // （低于 WCAG AA 4.5:1）。三级文字改用同色系加深值 #5F5A66（≈6.5:1），
            // 保留与 onSurfaceVariant(#49454F) 的层级差，同时满足 AA。
            textTertiary = if (dark) Color(0xFF938F99) else Color(0xFF5F5A66),       // outline → AA 合规
            primary = if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4),            // primary
            onPrimary = if (dark) Color(0xFF381E72) else Color(0xFFFFFFFF),          // onPrimary
            blue = if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4),
            // 语义色：红色取 MD3 error 角色（破坏性操作）；绿/橙/黄在 MD3 baseline 中无对应角色，保留原值
            red = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E),
            redText = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E),
            green = if (dark) Color(0xFF7ED957) else Color(0xFF1B7F3B),
            greenText = if (dark) Color(0xFF7ED957) else Color(0xFF1B7F3B),
            orange = if (dark) Color(0xFFFFB77C) else Color(0xFF9A5B00),
            yellow = if (dark) Color(0xFFE3C64B) else Color(0xFF7A6300),
            gray = if (dark) Color(0xFF938F99) else Color(0xFF79747E),
            glassFill = if (dark) Color(0xFF211F26) else Color(0xFFF3EDF7),          // surfaceContainer
            glassRim = if (dark) Color(0xFF938F99) else Color(0xFF79747E),           // outline
            glassTab = if (dark) Color(0xE6211F26) else Color(0xE6F3EDF7),
            glassDialog = if (dark) Color(0xF2211F26) else Color(0xF2F3EDF7),
            field = if (dark) Color(0xFF36343B) else Color(0xFFE6E0E9)               // surfaceContainerHighest
        )
        AppThemeMode.MIUIX -> base.copy(
            bg = if (dark) Color(0xFF000000) else Color(0xFFFFFFFF),
            card = if (dark) Color(0xFF1A1A1A) else Color(0xFFFFFFFF),
            cardSecondary = if (dark) Color(0xFF242424) else Color(0xFFF7F7F7),
            divider = if (dark) Color(0xFF2E2E2E) else Color(0xFFE5E5E5),
            textPrimary = if (dark) Color(0xFFFFFFFF) else Color(0xFF1A1A1A),
            textSecondary = if (dark) Color(0xFF999999) else Color(0xFF858585),
            textTertiary = if (dark) Color(0xFF666666) else Color(0xFFABABAB),
            primary = if (dark) Color(0xFFFF8A3D) else Color(0xFFFF6A00),
            blue = if (dark) Color(0xFFFF8A3D) else Color(0xFFFF6A00),
            glassFill = if (dark) Color(0xFF1A1A1A) else Color(0xFFFFFFFF),
            glassRim = if (dark) Color(0xFF3A3A3A) else Color(0xFFD9D9D9),
            glassTab = if (dark) Color(0xE61A1A1A) else Color(0xE6FFFFFF),
            glassDialog = if (dark) Color(0xF21A1A1A) else Color(0xF2FFFFFF),
            field = if (dark) Color(0xFF242424) else Color(0xFFF7F7F7)
        )
    }
}

/**
 * 应用级主题：按 [mode] 把内容包进 MaterialTheme 或 MiuixTheme，
 * 同时提供本项目自绘体系（LocalMediColors / LocalMediTypography / 字体 / LocalAppThemeMode）。
 * MATERIAL 模式额外提供 [LocalMd3Colors]（MD3 完整角色）与 MaterialTheme 的 typography/shapes 令牌。
 */
@Composable
fun AppTheme(
    mode: AppThemeMode = AppThemeMode.MATERIAL,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val mediColors = themeColors(mode, dark)
    // MD3 角色与深浅模式绑定（两种主题模式下都提供，避免组件里判空）
    val md3Colors = if (dark) Md3Colors.Dark else Md3Colors.Light
    val inner: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalMediColors provides mediColors,
            LocalMediTypography provides MediTypography.of(mediColors, mode),
            LocalAppThemeMode provides mode,
            LocalMd3Colors provides md3Colors
        ) {
            content()
        }
    }
    when (mode) {
        AppThemeMode.MATERIAL -> MaterialTheme(
            colorScheme = if (dark) M3Dark else M3Light,
            typography = M3Typography,
            shapes = M3Shapes,
            content = inner
        )
        AppThemeMode.MIUIX -> MiuixTheme(
            // key = dark：系统深浅切换时重建 controller。
            // 虽然 MainActivity 未声明 configChanges(uiMode)、Activity 会重建，
            // 但这里显式依赖 dark 可防止「将来给 Activity 加 configChanges 后 MIUIX 主题不跟随」的隐患，
            // 同时保证同一次组合内 dark 变化立即生效（零配置：跟随系统，无需用户设置）。
            controller = remember(dark) {
                ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            },
            content = inner
        )
    }
}
