package com.medilog.app.util

import android.content.Context

/** 应用偏好设置 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var isNotifyEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIFY, true)
        set(v) { sp.edit().putBoolean(KEY_NOTIFY, v).apply() }

    /** 提前提醒分钟数 */
    var leadMinutes: Int
        get() = sp.getInt(KEY_LEAD, 0)
        set(v) { sp.edit().putInt(KEY_LEAD, v).apply() }

    var snoozeMinutes: Int
        get() = sp.getInt(KEY_SNOOZE, 10)
        set(v) { sp.edit().putInt(KEY_SNOOZE, v).apply() }

    var isDemoLoaded: Boolean
        get() = sp.getBoolean(KEY_DEMO_LOADED, false)
        set(v) { sp.edit().putBoolean(KEY_DEMO_LOADED, v).apply() }

    /**
     * 是否已经就「精确闹钟权限」提示过用户（零配置：终身只提示一次）。
     * 正常情况下 USE_EXACT_ALARM 安装即授予，根本不会走到提示；
     * 只有个别 ROM 收回该能力时才提示一次，之后静默走降级通道。
     */
    var exactAlarmAsked: Boolean
        get() = sp.getBoolean(KEY_EXACT_ASKED, false)
        set(v) { sp.edit().putBoolean(KEY_EXACT_ASKED, v).apply() }

    /**
     * 应用内语言（仅 API<33 使用；API 33+ 由系统 LocaleManager 接管并持久化）。
     * "" = 跟随系统，"en" / "zh-CN" = 强制语言。
     */
    var appLocale: String
        get() = sp.getString(KEY_APP_LOCALE, "").orEmpty()
        set(v) { sp.edit().putString(KEY_APP_LOCALE, v).apply() }

    companion object {
        private const val NAME = "medilog_prefs"
        private const val KEY_NOTIFY = "notify_enabled"
        private const val KEY_LEAD = "lead_minutes"
        private const val KEY_SNOOZE = "snooze_minutes"
        private const val KEY_DEMO_LOADED = "demo_loaded"
        private const val KEY_EXACT_ASKED = "exact_alarm_asked"
        private const val KEY_APP_LOCALE = "app_locale"
    }
}
