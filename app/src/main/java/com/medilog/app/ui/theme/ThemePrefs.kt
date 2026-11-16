package com.medilog.app.ui.theme

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** 主题模式持久化（SharedPreferences + Compose State，切换即时生效） */
object ThemePrefs {
    private const val PREFS = "medilog_prefs"
    private const val KEY = "app_theme_mode"

    val mode: MutableState<AppThemeMode> = mutableStateOf(AppThemeMode.MIUIX)

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getString(KEY, AppThemeMode.MIUIX.name)
        mode.value = runCatching { AppThemeMode.valueOf(saved ?: AppThemeMode.MIUIX.name) }
            .getOrDefault(AppThemeMode.MIUIX)
    }

    fun set(context: Context, newMode: AppThemeMode) {
        mode.value = newMode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, newMode.name)
            .apply()
    }
}
