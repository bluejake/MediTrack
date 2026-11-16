package com.medilog.app.ui.motion

import com.medilog.app.ui.theme.Md3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 双主题转场令牌核对（纯 JVM，不依赖 Android 运行时）。
 *
 * 目的：把「动画是否符合 MD3 / MIUIX 规范」从"肉眼看"变成"可执行断言"：
 * - MD3：时长/缓动常量逐一比对 MD3 MotionTokens 规范值；
 * - MIUIX：程序化曲线逐一比对 miuix-ui 官方 `NavProgrammaticEasing(response=0.8, damping=0.95)`
 *   的既有特征（1/5 时长推进 48%、末端 ≈0.9993、窗口内单调不回弹），以及
 *   `MiuixDefault` 预设的空间参数（整宽滑入、1/4 屏宽视差、alpha 0.9、500ms）。
 */
class MotionTokensTest {

    // ── MD3 MotionTokens 规范值 ──────────────────────────────────────────

    @Test
    fun md3_durationTokens_matchSpec() {
        assertEquals(50, Md3.DurationShort1)
        assertEquals(100, Md3.DurationShort2)
        assertEquals(150, Md3.DurationShort3)
        assertEquals(200, Md3.DurationShort4)
        assertEquals(250, Md3.DurationMedium1)
        assertEquals(300, Md3.DurationMedium2)
        assertEquals(350, Md3.DurationMedium3)
        assertEquals(400, Md3.DurationMedium4)
        assertEquals(450, Md3.DurationLong1)
    }

    @Test
    fun md3_easingCurves_matchSpec() {
        // cubic-bezier(x1, y1, x2, y2) 的端点与中点行为
        val decelerate = Md3.EasingEmphasizedDecelerate // (0.05, 0.7, 0.1, 1)
        val accelerate = Md3.EasingEmphasizedAccelerate  // (0.3, 0, 0.8, 0.15)
        val standard = Md3.EasingStandard                // (0.2, 0, 0, 1)
        val standardAcc = Md3.EasingStandardAccelerate   // (0.3, 0, 1, 1)

        for (e in listOf(decelerate, accelerate, standard, standardAcc)) {
            assertEquals(0f, e.transform(0f), 1e-4f)
            assertEquals(1f, e.transform(1f), 1e-4f)
        }
        // 进入曲线：前 20% 就走完一半以上（"快起缓落"）
        assertTrue("decelerate 应快速启动", decelerate.transform(0.2f) > 0.4f)
        // 退出曲线：前 20% 只推进一小段（"缓起快走"）
        assertTrue("accelerate 应慢启动", accelerate.transform(0.2f) < 0.2f)
        // 退出曲线后段更陡：末 20% 推进量大于进入曲线
        assertTrue(
            "accelerate 末段应更陡",
            accelerate.transform(1f) - accelerate.transform(0.8f) >
                decelerate.transform(1f) - decelerate.transform(0.8f)
        )
    }

    @Test
    fun md3_pageAndModalTokens_areWired() {
        // 页面级 = Medium4(400) 进入 / Medium2(300) 退出；模态 = Medium2(300) 进 / Short4(200) 出
        assertEquals(400, Md3.DurationMedium4)
        assertEquals(300, Md3.DurationMedium2)
        assertEquals(200, PageTransitions.MD3_MODAL_EXIT_MS)
        assertEquals(0.92f, PageTransitions.FADE_THROUGH_ENTER_SCALE, 1e-6f)
    }

    // ── MIUIX 官方导航曲线与预设参数 ─────────────────────────────────────

    @Test
    fun miuixNavCurve_matchesOfficialStepResponse() {
        val curve = PageTransitions.MiuixNavCurve
        // 官方注释：约 1/5 时长即推进 48%
        assertEquals(0.48f, curve.transform(0.2f), 0.02f)
        // 末端 ≈0.99933（tween 末帧收干净残差）
        assertEquals(0.99933f, curve.transform(1f), 0.005f)
        // 窗口内单调不回弹
        var prev = -1f
        for (i in 0..20) {
            val v = curve.transform(i / 20f)
            assertTrue("曲线必须单调递增", v >= prev - 1e-4f)
            assertTrue("曲线不得越过 1", v <= 1f + 1e-4f)
            prev = v
        }
        // 中段快、尾段缓：前 50% 的推进量大于后 50%
        assertTrue(
            "中段应更快",
            curve.transform(0.5f) - curve.transform(0f) >
                curve.transform(1f) - curve.transform(0.5f)
        )
    }

    @Test
    fun miuixPresetGeometry_matchesMiuixDefault() {
        // NavTransitions.MiuixDefault：整宽滑入 + 1/4 屏宽视差 + alpha falloff 0.1
        assertEquals(500, PageTransitions.MIUIX_NAV_DURATION)
        assertEquals(0.9f, PageTransitions.MIUIX_COVERED_ALPHA, 1e-6f)
        assertEquals(200, PageTransitions.MIUIX_ENTER_FADE)
        // 顶层切换比推入/弹出短
        assertTrue(PageTransitions.MIUIX_TAB_DURATION < PageTransitions.MIUIX_NAV_DURATION)
        assertTrue(PageTransitions.MIUIX_TAB_EXIT_DURATION < PageTransitions.MIUIX_TAB_DURATION)
        assertEquals(0.96f, PageTransitions.MIUIX_TAB_ENTER_SCALE, 1e-6f)
        assertEquals(200, PageTransitions.MIUIX_MODAL_EXIT_MS)
    }

    @Test
    fun miuixCurve_reproducesSpringFormula() {
        // 同一组 (response, damping) 必须给出同一条曲线（复现而非近似）
        val a = PageTransitions.MiuixNavCurve.transform(0.35f)
        val b = MiuixNavEasing(response = 0.8f, damping = 0.95f).transform(0.35f)
        assertEquals(a, b, 1e-6f)
        // 欠阻尼：曲线在窗口内首次到达 1 的时刻晚于窗口末端（官方注释）
        assertTrue(PageTransitions.MiuixNavCurve.transform(1f) < 1f)
        assertTrue(abs(PageTransitions.MiuixNavCurve.transform(1f) - 0.99933f) < 0.01f)
    }
}
