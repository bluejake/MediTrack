package com.medilog.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 审计 U4 + 黑屏修复：应用内语言切换。
 *
 * 策略：语言标签持久化于 Prefs，并通过「包装了目标 Locale 的 Context」在进程内
 * 经 CompositionLocal(LocalContext) 提供。切换语言只驱动组合树重排（背景常驻），
 * 不再 recreate Activity、也不走系统 per-app locale 自动重建 —— 从根上消除黑屏闪烁。
 *
 * 标签约定：""（跟随系统）/ "en" / "zh-CN"。
 */
object LocaleHelper {
    const val SYSTEM = ""
    const val EN = "en"
    const val ZH = "zh-CN"

    /** 当前持久化语言标签（"" = 跟随系统）。全 API 统一走 Prefs。 */
    fun currentTag(ctx: Context): String = Prefs(ctx).appLocale

    /** 持久化新语言（不触发任何重建/系统 locale 变更；由调用方驱动进程内重排）。 */
    fun persist(ctx: Context, tag: String) {
        Prefs(ctx).appLocale = tag
    }

    /**
     * 用指定语言标签包装上下文（全 API 生效）。
     * SYSTEM → 回落系统语言（原样返回 ctx）；否则 createConfigurationContext 应用 locale。
     */
    fun wrap(ctx: Context, tag: String = Prefs(ctx).appLocale): Context {
        if (tag == SYSTEM) return ctx
        return try {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(ctx.resources.configuration)
            config.setLocale(locale)
            ctx.createConfigurationContext(config)
        } catch (e: Exception) {
            android.util.Log.w("LocaleHelper", "语言包装失败，回落系统语言", e)
            ctx
        }
    }
}
