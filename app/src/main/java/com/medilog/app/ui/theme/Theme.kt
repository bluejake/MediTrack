package com.medilog.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 正文/标题字体族别名。
 *
 * 原内部版本使用子集化 SF Pro（Apple San Francisco），该字体受 Apple OS 软件许可协议约束，
 * 不允许随第三方开源项目再分发。公开版本改为系统默认无衬线字体：
 *  - Android 上由系统提供 Roboto/Noto Sans，按 [FontWeight] 自动选择真实字重；
 *  - 中文仍由系统字体渲染（与原版行为一致 —— SF Pro 本身不含 CJK 字形）。
 *
 * 保留 `SfPro` 这个别名是为了不触碰散落各处的 127 处调用点；名称仅为历史兼容，
 * 不再表示 Apple 字体。视觉上会有细微差异，但不影响功能与交互。
 */
val SfPro: FontFamily = FontFamily.SansSerif

/**
 * 轻量主题：不依赖 Material3。
 * 颜色取自 res/values/colors.xml 的 iOS 体系（深浅两套在此内联，避免资源读取开销）。
 */
@Immutable
data class MediColors(
    val bg: Color,
    val card: Color,
    val cardSecondary: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val onPrimary: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val blue: Color,
    val purple: Color,
    val gray: Color,
    val glassFill: Color,
    val glassRim: Color,
    val glassTab: Color,
    val glassDialog: Color,
    val field: Color,
    /** 状态色文字变体：仅用于「作为文字」的场景，保证 WCAG AA 对比度；图标底色/进度条仍用 green/red */
    val greenText: Color,
    val redText: Color,
    /** 当前是否深色模式（玻璃参数据此自适应：深色 blur 减半，避免糊成灰黑） */
    val dark: Boolean
) {
    companion object {
        val Light = MediColors(
            bg = Color(0xFFF2F2F7),
            card = Color(0xFFFFFFFF),
            cardSecondary = Color(0xFFFAFAFC),
            divider = Color(0xFFE5E5EA),
            textPrimary = Color(0xFF1C1C1E),
            textSecondary = Color(0xFF6C6C70),
            textTertiary = Color(0xFF8E8E93),
            primary = Color(0xFF007AFF),
            onPrimary = Color(0xFFFFFFFF),
            green = Color(0xFF34C759),
            red = Color(0xFFFF3B30),
            orange = Color(0xFFFF9500),
            yellow = Color(0xFFFFCC00),
            blue = Color(0xFF007AFF),
            purple = Color(0xFFAF52DE),
            gray = Color(0xFF8E8E93),
            glassFill = Color(0xC4FFFFFF),
            glassRim = Color(0xFFDBDBDB),
            glassTab = Color(0xD9FFFFFF),
            glassDialog = Color(0xE6FFFFFF),
            field = Color(0xFFF2F2F7),
            greenText = Color(0xFF1B7F3B),
            redText = Color(0xFFD93025),
            dark = false
        )
        val Dark = MediColors(
            bg = Color(0xFF000000),
            card = Color(0xFF1C1C1E),
            cardSecondary = Color(0xFF2C2C2E),
            divider = Color(0xFF38383A),
            textPrimary = Color(0xFFF2F2F7),
            textSecondary = Color(0xFF98989F),
            textTertiary = Color(0xFF85858B),
            primary = Color(0xFF0A84FF),
            onPrimary = Color(0xFFFFFFFF),
            green = Color(0xFF30D158),
            red = Color(0xFFFF453A),
            orange = Color(0xFFFF9F0A),
            yellow = Color(0xFFFFD60A),
            blue = Color(0xFF0A84FF),
            purple = Color(0xFFBF5AF2),
            gray = Color(0xFF98989F),
            glassFill = Color(0xE61C1C1E),
            glassRim = Color(0xFFA6A6A6),
            glassTab = Color(0xE62C2C2E),
            glassDialog = Color(0xF22C2C2E),
            field = Color(0xFF1C1C1E),
            greenText = Color(0xFF30D158),
            redText = Color(0xFFFF453A),
            dark = true
        )
    }
}

@Immutable
data class MediTypography(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle
) {
    companion object {
        /**
         * 按主题模式产出一套语义化字型：
         * - MATERIAL：度量全部取 MD3 typescale 令牌（见 [Md3]），颜色取 MD3 角色
         *   （onSurface / onSurfaceVariant），**不再使用 iOS 自定义字号体系**；
         * - MIUIX：保留原有 iOS 字型（视觉保持不变）。
         *
         * 这样界面里所有 `typography.xxx` 的调用无需按模式分流，
         * MATERIAL 模式自动得到 MD3 排版，MIUIX 模式自动得到原视觉。
         */
        fun of(c: MediColors, mode: AppThemeMode = AppThemeMode.MATERIAL): MediTypography {
            if (mode == AppThemeMode.MIUIX) {
                return MediTypography(
                    largeTitle = TextStyle(
                        fontFamily = SfPro, fontWeight = FontWeight.Bold,
                        fontSize = 32.sp, lineHeight = 38.sp, color = c.textPrimary
                    ),
                    title = TextStyle(
                        fontFamily = SfPro, fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp, lineHeight = 26.sp, color = c.textPrimary
                    ),
                    headline = TextStyle(
                        fontFamily = SfPro, fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp, lineHeight = 22.sp, color = c.textPrimary
                    ),
                    body = TextStyle(
                        fontFamily = SfPro, fontWeight = FontWeight.Normal,
                        fontSize = 16.sp, lineHeight = 22.sp, color = c.textPrimary
                    ),
                    callout = TextStyle(
                        fontFamily = SfPro, fontWeight = FontWeight.Normal,
                        fontSize = 15.sp, lineHeight = 20.sp, color = c.textSecondary
                    ),
                    caption = TextStyle(
                        fontFamily = SfPro, fontWeight = FontWeight.Normal,
                        fontSize = 13.sp, lineHeight = 18.sp, color = c.textSecondary
                    ),
                    caption2 = TextStyle(
                        fontFamily = SfPro, fontWeight = FontWeight.Normal,
                        fontSize = 12.sp, lineHeight = 16.sp, color = c.textTertiary
                    )
                )
            }
            // ── MATERIAL：MD3 typescale ──
            val m = if (c.dark) Md3Colors.Dark else Md3Colors.Light
            return MediTypography(
                // largeTitle → headlineMedium 28/36 w400
                largeTitle = Md3.HeadlineMedium.copy(color = m.onSurface),
                // title → titleLarge 22/28 w400
                title = Md3.TitleLarge.copy(color = m.onSurface),
                // headline → titleMedium 16/24 w500（letterSpacing 0.15）
                headline = Md3.TitleMedium.copy(color = m.onSurface),
                // body → bodyLarge 16/24 w400（letterSpacing 0.5）
                body = Md3.BodyLarge.copy(color = m.onSurface),
                // callout → bodyMedium 14/20 w400（letterSpacing 0.25）
                callout = Md3.BodyMedium.copy(color = m.onSurfaceVariant),
                // caption → bodySmall 12/16 w400（letterSpacing 0.4）
                caption = Md3.BodySmall.copy(color = m.onSurfaceVariant),
                // caption2 → labelSmall 11/16 w500（letterSpacing 0.5）
                caption2 = Md3.LabelSmall.copy(color = m.onSurfaceVariant)
            )
        }
    }
}

val LocalMediColors = staticCompositionLocalOf { MediColors.Light }
val LocalMediTypography = staticCompositionLocalOf { MediTypography.of(MediColors.Light) }

/** 便捷取色 */
val colors: MediColors
    @Composable get() = LocalMediColors.current

val typography: MediTypography
    @Composable get() = LocalMediTypography.current

/**
 * 字型归一化：把调用方声明的 [TextStyle] 按主题模式收敛到 MD3 typescale 令牌。
 *
 * - MATERIAL：按字号映射到最近的 MD3 typescale 角色（≥24→headlineMedium、≥18→titleLarge、
 *   ≥15→bodyLarge、≥13→bodyMedium、≥11→bodySmall、其余→labelSmall），
 *   **字号/行高/字重/字距全部换成 MD3 规范值**，仅保留调用方的颜色与对齐方式；
 * - MIUIX：原样返回 [base]（iOS 字型视觉零变化）。
 *
 * 用途：界面里既有的内联 `TextStyle(fontFamily = SfPro, fontSize = X.sp, ...)` 全部包一层，
 * 即可以往代码零改动地让 MATERIAL 模式拿到 MD3 排版，而不影响 MIUIX 模式。
 */
@Composable
fun mediTextStyle(base: TextStyle): TextStyle {
    if (LocalAppThemeMode.current != AppThemeMode.MATERIAL) return base
    val sp = base.fontSize.value
    val role = when {
        sp >= 24f -> Md3.HeadlineMedium   // 24–36sp：大标题 → headlineMedium 28/36
        sp >= 18f -> Md3.TitleLarge       // 18–23sp：卡片/页面标题 → titleLarge 22/28
        sp >= 15f -> Md3.BodyLarge        // 15–17sp：正文强调 → bodyLarge 16/24
        sp >= 13f -> Md3.BodyMedium       // 13–14sp：次要正文 → bodyMedium 14/20
        sp >= 11f -> Md3.BodySmall        // 11–12sp：辅助文字 → bodySmall 12/16
        else -> Md3.LabelSmall            // ≤10sp：微标签 → labelSmall 11/16
    }
    return role.copy(
        color = base.color,
        textAlign = base.textAlign
    )
}
