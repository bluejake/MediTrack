package com.medilog.app.ui.motion

import androidx.compose.animation.AnimatedVisibility
import com.medilog.app.ui.theme.Md3
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 页面过渡动画（双主题）。
 *
 * - **MATERIAL**：严格按 Material Design 3 Motion 规范取值 —— 时长/缓动全部取自
 *   [Md3]（MD3 官方 MotionTokens 规范值，单一来源；官方 API 在本工程依赖的
 *   JetBrains material3 产物里为 internal，故按同值定义于 Md3.kt，可随时替换为官方 API），
 *   映射到 MD3 的两个页面转场模式：
 *   - 顶层目的地切换 = **Fade through**（无强空间关系的同级内容）：进入淡入 + 92%→100% 缩放，
 *     退出纯淡出；
 *   - 层级推入/返回 = **Shared axis Y**（前/后向导航）：进出双方沿 Y 轴整屏同向移动形成连续性，
 *     进入用 EmphasizedDecelerate、退出用 EmphasizedAccelerate（MD3 铁律：进入减速、退出加速，
 *     两侧绝不同曲线）。
 * - **MIUIX**：按 miuix-ui 官方导航转场（`NavTransitions.MiuixDefault` + `NavMotion`）复现同等级体验 ——
 *   进入页自后缘（右）整宽滑入、被覆盖页向前缘（左）视差 1/4 屏宽并衰减 alpha 至 0.9，
 *   程序化推入/弹出统一 500ms + [MiuixNavCurve]（欠阻尼弹簧阶跃响应：中段快、尾段长而缓、不回弹）。
 *
 * 两个主题的转场"语言"不同（MD3 = 纵向共享轴；MIUIX = 横向卡片栈），但时长同一量级、
 * 节奏一致，切换主题时不会出现"一个拖沓一个生硬"的割裂感。
 */
object PageTransitions {

    /** 页面导航事件：用于选择转场模式 */
    enum class NavEvent {
        /** 顶层目的地切换（底部导航）→ Fade through / MIUIX 淡入缩放 */
        TabSwitch,

        /** 进入下级页（列表 → 详情 / 详情 → 编辑）→ 前向 */
        Forward,

        /** 返回上级页 → 后向 */
        Backward
    }

    /** MD3 fade-through 进入起始缩放（MD3 规范值 92%） */
    const val FADE_THROUGH_ENTER_SCALE = 0.92f

    /** MD3 模态（对话框）退出时长 —— 与 [modalExit] 一致，供 dismiss 延迟使用 */
    const val MD3_MODAL_EXIT_MS = Md3.DurationShort4 // 200ms

    /** MIUIX 模态退出时长 —— 与 [modalExit] 一致 */
    const val MIUIX_MODAL_EXIT_MS = 200

    // ── MD3（MotionTokens 规范值，见 Md3.kt）──────────────────────────────────────

    /** MD3 前向：进入自底部整屏滑入 + 淡入；退出向上整屏移出 + 淡出 */
    private fun md3Forward(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium4, // 400ms 页面级
                    easing = Md3.EasingEmphasizedDecelerate
                )
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium4,
                    easing = Md3.EasingEmphasizedDecelerate
                ),
                initialOffsetY = { fullHeight -> fullHeight } // 自后缘（底部）整屏
            )
            ) togetherWith (
            fadeOut(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2, // 300ms，退出更快
                    easing = Md3.EasingEmphasizedAccelerate
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2,
                    easing = Md3.EasingEmphasizedAccelerate
                ),
                targetOffsetY = { fullHeight -> -fullHeight } // 向前缘（顶部）整屏
            )
            )

    /** MD3 后向：进入自上方整屏回落 + 淡入；退出向下整屏移出 + 淡出 */
    private fun md3Backward(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium4,
                    easing = Md3.EasingEmphasizedDecelerate
                )
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium4,
                    easing = Md3.EasingEmphasizedDecelerate
                ),
                initialOffsetY = { fullHeight -> -fullHeight }
            )
            ) togetherWith (
            fadeOut(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2,
                    easing = Md3.EasingEmphasizedAccelerate
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2,
                    easing = Md3.EasingEmphasizedAccelerate
                ),
                targetOffsetY = { fullHeight -> fullHeight }
            )
            )

    /** MD3 Fade through：进入淡入 + 92%→100% 缩放（300ms）；退出纯淡出（100ms） */
    private fun md3FadeThrough(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2,
                    easing = Md3.EasingEmphasizedDecelerate
                )
            ) + scaleIn(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2,
                    easing = Md3.EasingEmphasizedDecelerate
                ),
                initialScale = FADE_THROUGH_ENTER_SCALE
            )
            ) togetherWith fadeOut(
            animationSpec = tween(
                durationMillis = Md3.DurationShort2, // 100ms
                easing = Md3.EasingStandardAccelerate
            )
        )

    // ── MIUIX（对齐 miuix-ui 官方导航转场）───────────────────────────────

    /**
     * MIUIX 程序化推入/弹出时长：与官方 `NavDriverSpec.PROGRAMMATIC_DURATION_MILLIS` 一致（500ms）。
     */
    internal const val MIUIX_NAV_DURATION = 500

    /** MIUIX 顶层切换时长（比推入/弹出短，Tab 是平级切换） */
    internal const val MIUIX_TAB_DURATION = 350

    /** MIUIX 顶层切换退出时长 */
    internal const val MIUIX_TAB_EXIT_DURATION = 150

    /** MIUIX 顶层切换进入起始缩放 */
    internal const val MIUIX_TAB_ENTER_SCALE = 0.96f

    /** MIUIX 被覆盖页 alpha 下限（官方预设 alpha falloff = 0.1） */
    internal const val MIUIX_COVERED_ALPHA = 0.9f

    /** MIUIX 进入页淡入时长（滑入是主体，淡入只做柔和收边） */
    internal const val MIUIX_ENTER_FADE = 200

    /**
     * MIUIX 程序化曲线（等价实现，参数与官方 `NavProgrammaticEasing` 完全一致）：
     * `response = 0.8f`、`damping = 0.95f` 的欠阻尼弹簧阶跃响应，烘成固定时长 Easing。
     * 特征：约 1/5 时长即推进 48%（中段快）、尾段长而缓、窗口内单调不回弹
     * （`transform(1) ≈ 0.9993`，末帧由 tween 收干净）。
     *
     * 之所以复现而不是直接引用：工程依赖的 miuix-ui 0.9.4 尚未包含 nav 模块
     * （`NavProgrammaticEasing` 属新版 miuix-nav），此处按同参数复现以保证节奏一致。
     */
    internal val MiuixNavCurve: Easing = MiuixNavEasing(response = 0.8f, damping = 0.95f)

    /**
     * MIUIX 前向（推入）：进入页自后缘（右）整宽滑入；被覆盖页向前缘（左）视差 1/4 屏宽，
     * alpha 1 → 0.9（官方 `MiuixDefault` 预设的 covered 处理）。
     */
    private fun miuixForward(): ContentTransform =
        (
            slideInHorizontally(
                animationSpec = tween(durationMillis = MIUIX_NAV_DURATION, easing = MiuixNavCurve),
                initialOffsetX = { fullWidth -> fullWidth }
            ) + fadeIn(
                animationSpec = tween(durationMillis = MIUIX_ENTER_FADE, easing = MiuixNavCurve)
            )
            ) togetherWith (
            slideOutHorizontally(
                animationSpec = tween(durationMillis = MIUIX_NAV_DURATION, easing = MiuixNavCurve),
                targetOffsetX = { fullWidth -> -fullWidth / 4 } // 1/4 屏宽视差
            ) + fadeOut(
                animationSpec = tween(durationMillis = MIUIX_NAV_DURATION, easing = MiuixNavCurve),
                targetAlpha = MIUIX_COVERED_ALPHA
            )
            )

    /**
     * MIUIX 后向（返回）：被覆盖页自 -1/4 屏宽视差位回正、alpha 0.9 → 1；
     * 顶层页整体滑出到后缘（右）并淡出。
     */
    private fun miuixBackward(): ContentTransform =
        (
            slideInHorizontally(
                animationSpec = tween(durationMillis = MIUIX_NAV_DURATION, easing = MiuixNavCurve),
                initialOffsetX = { fullWidth -> -fullWidth / 4 }
            ) + fadeIn(
                animationSpec = tween(durationMillis = MIUIX_NAV_DURATION, easing = MiuixNavCurve),
                initialAlpha = MIUIX_COVERED_ALPHA
            )
            ) togetherWith (
            slideOutHorizontally(
                animationSpec = tween(durationMillis = MIUIX_NAV_DURATION, easing = MiuixNavCurve),
                targetOffsetX = { fullWidth -> fullWidth }
            ) + fadeOut(
                animationSpec = tween(durationMillis = MIUIX_NAV_DURATION, easing = MiuixNavCurve)
            )
            )

    /**
     * MIUIX 顶层切换（底部 Dock）：淡入 + 96%→100% 轻微缩放（350ms）；
     * 退出纯淡出（150ms）。比 MD3 fade-through 多一点"呼吸感"，与 MIUIX 液态玻璃语言一致。
     */
    private fun miuixFadeThrough(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(durationMillis = MIUIX_TAB_DURATION, easing = MiuixNavCurve)
            ) + scaleIn(
                animationSpec = tween(durationMillis = MIUIX_TAB_DURATION, easing = MiuixNavCurve),
                initialScale = MIUIX_TAB_ENTER_SCALE
            )
            ) togetherWith fadeOut(
            animationSpec = tween(durationMillis = MIUIX_TAB_EXIT_DURATION, easing = MiuixNavCurve)
        )

    // ── 公共取值 ─────────────────────────────────────────────────────────

    /**
     * 页面转场：按主题模式与导航事件取 ContentTransform。
     *
     * @param isMaterial true = MD3 规范值（MotionTokens）；false = MIUIX 官方导航手感
     */
    fun pageTransform(isMaterial: Boolean, event: NavEvent): ContentTransform = if (isMaterial) {
        when (event) {
            NavEvent.TabSwitch -> md3FadeThrough()
            NavEvent.Forward -> md3Forward()
            NavEvent.Backward -> md3Backward()
        }
    } else {
        when (event) {
            NavEvent.TabSwitch -> miuixFadeThrough()
            NavEvent.Forward -> miuixForward()
            NavEvent.Backward -> miuixBackward()
        }
    }

    /**
     * 顶栏标题转场：仅淡入淡出（标题是文字，不做位移/缩放，避免阅读时晃动）。
     * MD3：DurationShort3(150ms) 进入 / DurationShort2(100ms) 退出，Standard 曲线；
     * MIUIX：200ms / 150ms + 官方曲线。
     */
    fun titleTransform(isMaterial: Boolean): ContentTransform =
        if (isMaterial) {
            fadeIn(
                animationSpec = tween(
                    durationMillis = Md3.DurationShort3,
                    easing = Md3.EasingStandard
                )
            ) togetherWith fadeOut(
                animationSpec = tween(
                    durationMillis = Md3.DurationShort2,
                    easing = Md3.EasingStandard
                )
            )
        } else {
            fadeIn(
                animationSpec = tween(durationMillis = 200, easing = MiuixNavCurve)
            ) togetherWith fadeOut(
                animationSpec = tween(durationMillis = 150, easing = MiuixNavCurve)
            )
        }

    /**
     * 模态（对话框/选择器）进入：淡入 + 缩放。
     * MD3：300ms（DurationMedium2）+ EmphasizedDecelerate，92%→100%；
     * MIUIX：300ms + 官方曲线，95%→100%（更克制，配合玻璃浮层的轻盈感）。
     */
    fun modalEnter(isMaterial: Boolean): EnterTransition =
        if (isMaterial) {
            fadeIn(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2,
                    easing = Md3.EasingEmphasizedDecelerate
                )
            ) + scaleIn(
                animationSpec = tween(
                    durationMillis = Md3.DurationMedium2,
                    easing = Md3.EasingEmphasizedDecelerate
                ),
                initialScale = FADE_THROUGH_ENTER_SCALE
            )
        } else {
            fadeIn(
                animationSpec = tween(durationMillis = 300, easing = MiuixNavCurve)
            ) + scaleIn(
                animationSpec = tween(durationMillis = 300, easing = MiuixNavCurve),
                initialScale = 0.95f
            )
        }

    /**
     * 模态退出：纯淡出（模态是浮层，退出不做位移，避免遮挡下层内容时产生拉扯感）。
     * MD3：200ms（DurationShort4）+ EmphasizedAccelerate；MIUIX：200ms + 官方曲线。
     */
    fun modalExit(isMaterial: Boolean): ExitTransition =
        if (isMaterial) {
            fadeOut(
                animationSpec = tween(
                    durationMillis = Md3.DurationShort4,
                    easing = Md3.EasingEmphasizedAccelerate
                )
            )
        } else {
            fadeOut(animationSpec = tween(durationMillis = MIUIX_MODAL_EXIT_MS, easing = MiuixNavCurve))
        }

    /** 模态退出时长（供 dismiss 时延迟回调，保证退出动画播完再移除） */
    fun modalExitMillis(isMaterial: Boolean): Int =
        if (isMaterial) MD3_MODAL_EXIT_MS else MIUIX_MODAL_EXIT_MS
}

/**
 * 模态浮层容器：进入/退出动画 + 「先播退出动画再执行动作」的关闭语义。
 *
 * 用法（调用点零改动，仍是 `if (show) { XxxDialog(...) }`）：
 * ```
 * AnimatedModalHost(isMaterial = isMaterial, onDismiss = { show = false }) { closeWith ->
 *     GlassCard(...) {
 *         // 关闭路径：closeWith { }           仅关闭
 *         //           closeWith { onConfirm() }  关闭并执行确认动作
 *     }
 * }
 * ```
 * - **首次进入**：内部先以 `shown = false` 组合、下一帧置 true，因此首次显示也会播进入动画
 *   （AnimatedVisibility 默认不会为"首次组合就可见"播进入动画，必须自己翻一次帧）；
 * - **关闭**：所有关闭路径统一走 `closeWith { 动作 }` —— 先播退出动画，动画结束后再执行动作
 *   （通常是调用方把 `show` 置 false 的那个 lambda），因此退出动画可见、动作只执行一次，
 *   且调用方仍是 `if (show)` 的写法，无需改成传 `visible`。
 */
@Composable
fun AnimatedModalHost(
    isMaterial: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (closeWith: (() -> Unit) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val exitMillis = PageTransitions.modalExitMillis(isMaterial)
    // 首次进入也要播进入动画：先 false，下一帧再置 true
    var shown by remember { mutableStateOf(false) }
    // 关闭握手：防止退出动画期间重复触发（确认类动作只能执行一次）
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val closeWith: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                shown = false
                delay(exitMillis.toLong())
                action()
                onDismiss()
            }
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = PageTransitions.modalEnter(isMaterial),
        exit = PageTransitions.modalExit(isMaterial),
        modifier = modifier
    ) {
        content(closeWith)
    }
}

/**
 * 把欠阻尼弹簧的阶跃响应烘成固定时长 Easing（与 miuix-ui 的 `NavSettleEasing` 同公式）。
 *
 * 阶跃响应：`x(t) = 1 - e^(r·t)·(cos(ω·t) - c₂·sin(ω·t))`，
 * 其中 `ω = √(4k - c²)/2`、`r = -c/2`、`c₂ = r/ω`、`k = (2π/response)²`、`c = damping·4π/response`。
 */
internal class MiuixNavEasing(response: Float, damping: Float) : Easing {
    private val r: Float
    private val w: Float
    private val c2: Float

    init {
        val omega = 2.0 * PI / response
        val k = omega * omega
        val c = damping * 4.0 * PI / response
        w = (sqrt(4.0 * k - c * c) / 2.0).toFloat()
        r = (-c / 2.0).toFloat()
        c2 = r / w
    }

    override fun transform(fraction: Float): Float {
        val t = fraction.toDouble()
        val decay = exp(r * t)
        return (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
    }
}
