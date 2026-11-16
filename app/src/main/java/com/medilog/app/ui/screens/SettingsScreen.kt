package com.medilog.app.ui.screens

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medilog.app.App
import com.medilog.app.R
import com.medilog.app.ui.components.ResponsivePage
import com.medilog.app.ui.components.contentWidth
import com.medilog.app.ui.TestTags
import com.medilog.app.data.MedDeletion
import com.medilog.app.data.MedRepository
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.reminder.ReminderScheduler
import com.medilog.app.reminder.rescheduleAsync
import com.medilog.app.ui.components.SettingRow
import com.medilog.app.ui.glass.GlassButton
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.GlassChoiceDialog
import com.medilog.app.ui.glass.GlassDialogBox
import com.medilog.app.ui.glass.GlassSwitch
import com.medilog.app.ui.glass.SectionHeader
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import com.medilog.app.ui.theme.ThemePrefs
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.util.CsvFileUtil
import com.medilog.app.util.DemoData
import com.medilog.app.util.LocaleHelper
import com.medilog.app.util.NavUtil
import com.medilog.app.util.Prefs
import com.medilog.app.util.TimeUtils
import java.util.Locale

private val LEAD_OPTIONS = intArrayOf(0, 5, 15, 30)
private val SNOOZE_OPTIONS = intArrayOf(5, 10, 15, 20)

/** 「设置」页 */
@Composable
fun SettingsScreen(
    repo: MedRepository,
    prefs: Prefs,
    onToast: (String) -> Unit,
    onDataChanged: () -> Unit,
    onLocaleChange: (String) -> Unit
) {
    val c = colors
    val ctx = LocalContext.current
    // 进程级数据版本：通知栏「记录」/ onResume 后「接下来的提醒」等卡片立即刷新
    val dataVersion = LocalDataVersion.current

    var notifyEnabled by remember { mutableStateOf(prefs.isNotifyEnabled) }
    var leadMinutes by remember { mutableIntStateOf(prefs.leadMinutes) }
    var snoozeMinutes by remember { mutableIntStateOf(prefs.snoozeMinutes) }
    var refresh by remember { mutableIntStateOf(0) }

    var showLead by remember { mutableStateOf(false) }
    var showSnooze by remember { mutableStateOf(false) }
    var showBattery by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showDemo by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val themeMode = ThemePrefs.mode.value

    // 审计 U4：应用内语言。当前标签在 Activity 重建后由 remember 重新初始化，
    // 无需手动刷新状态。
    var langTag by remember { mutableStateOf(LocaleHelper.currentTag(ctx)) }

    // ---------- i18n：回调/非 Composable 作用域用模板，Composable 作用域用 stringResource ----------
    val exportFilePrefix = stringResource(R.string.export_file_prefix)
    val exportDoneTpl = stringResource(R.string.exported)
    val exportFailedTpl = stringResource(R.string.toast_export_failed)
    val storageDeniedMsg = stringResource(R.string.toast_storage_denied)
    val importOpenFailedMsg = stringResource(R.string.toast_import_open_failed)
    val importTooLargeMsg = stringResource(R.string.toast_import_too_large)
    val importDoneTpl = stringResource(R.string.toast_import_done)
    val importFailedTpl = stringResource(R.string.toast_import_failed)
    val clearedMsg = stringResource(R.string.toast_cleared)
    val demoLoadedMsg = stringResource(R.string.toast_demo_loaded)

    fun doExport(ctx: Context, repo: MedRepository, onToast: (String) -> Unit, fileName: String) {
        try {
            val csv = repo.exportCsv()
            val path = CsvFileUtil.exportToPublicDownload(ctx, fileName, csv)
            onToast(String.format(exportDoneTpl, path))
        } catch (e: Exception) {
            onToast(String.format(exportFailedTpl, e.message ?: ""))
        }
    }

    // API 28 及以下写公共目录需运行时授权
    var pendingExport by remember { mutableStateOf(false) }
    val storagePermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingExport) {
            doExport(ctx, repo, onToast, exportFilePrefix + "_" + TimeUtils.today() + ".csv")
        } else if (!granted) onToast(storageDeniedMsg)
        pendingExport = false
    }

    // CSV 导入：系统文件选择器
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val ins = ctx.contentResolver.openInputStream(uri)
                if (ins == null) {
                    onToast(importOpenFailedMsg)
                } else {
                    val bos = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var n = ins.read(buf)
                    while (n > 0) {
                        bos.write(buf, 0, n)
                        n = ins.read(buf)
                    }
                    ins.close()
                    if (bos.size() > 4 * 1024 * 1024) {
                        onToast(importTooLargeMsg)
                    } else {
                        val content = String(bos.toByteArray(), Charsets.UTF_8)
                        val r = MedRepository(ctx).importCsv(content)
                        rescheduleAsync(ctx)
                        onDataChanged()
                        onToast(
                            String.format(
                                Locale.US,
                                importDoneTpl,
                                r.logs, r.meds, r.skipped
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                onToast(String.format(importFailedTpl, e.message ?: ""))
            }
        }
    }

    // ---------- 弹窗与选项文本（Composable 作用域，可直接 stringResource） ----------
    val minutesFmt = stringResource(R.string.settings_minutes_fmt)
    val themeDialogTitle = stringResource(R.string.settings_theme_dialog_title)
    val themeOptions = listOf(
        stringResource(R.string.theme_name_material),
        stringResource(R.string.theme_name_miuix_short)
    )
    val leadDialogTitle = stringResource(R.string.settings_lead)
    val snoozeDialogTitle = stringResource(R.string.settings_snooze)
    val batteryDialogTitle = stringResource(R.string.settings_battery_dialog_title)
    val batteryDialogMsg = stringResource(R.string.settings_battery_dialog_msg)
    val goSettingsText = stringResource(R.string.go_settings)
    val clearDialogTitle = stringResource(R.string.settings_clear)
    val clearDialogMsg = stringResource(R.string.confirm_clear_msg)
    val demoDialogTitle = stringResource(R.string.settings_demo)
    val demoDialogMsg = stringResource(R.string.settings_demo_confirm)
    val importDialogTitle = stringResource(R.string.settings_import)
    val importDialogMsg = stringResource(R.string.settings_import_msg)
    val importPickText = stringResource(R.string.settings_import_pick)
    val testReminderText = stringResource(R.string.settings_test_reminder)
    val testSentMsg = stringResource(R.string.settings_test_sent)
    // 语言选择对话框文案（English/简体中文 按惯例固定用各自语言展示，不翻译）
    val langDialogTitle = stringResource(R.string.settings_language)
    val langTags = listOf(LocaleHelper.SYSTEM, LocaleHelper.EN, LocaleHelper.ZH)
    val langLabels = listOf(stringResource(R.string.language_system), "English", "简体中文")
    val langIndex = langTags.indexOf(langTag).coerceAtLeast(0)
    val checkTitle = stringResource(R.string.settings_check_title)
    val checkDesc = stringResource(R.string.settings_check_desc)
    val checkNotify = stringResource(R.string.settings_check_notify)
    val checkExact = stringResource(R.string.settings_check_exact)
    val checkChannel = stringResource(R.string.settings_check_channel)
    val checkBattery = stringResource(R.string.settings_check_battery)
    val checkFsi = stringResource(R.string.settings_check_fsi)
    val nextHeader = stringResource(R.string.settings_next_header)
    val nextNone = stringResource(R.string.settings_next_none)

    ResponsivePage {
        LazyColumn(
            Modifier
                .fillMaxHeight()

                .contentWidth()
                .testTag(TestTags.SETTINGS_ROOT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- 主题 ----------
            item { SectionHeader(stringResource(R.string.section_theme)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    SettingRow(
                        title = stringResource(R.string.settings_theme_style),
                        desc = stringResource(
                            R.string.settings_current_fmt,
                            if (themeMode == AppThemeMode.MATERIAL) {
                                stringResource(R.string.theme_name_material)
                            } else {
                                stringResource(R.string.theme_name_miuix)
                            }
                        ),
                        onClick = { showTheme = true }
                    )
                }
            }

            // ---------- 通用：语言（审计 U4：此前无任何应用内语言切换入口） ----------
            item { SectionHeader(stringResource(R.string.section_general)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    SettingRow(
                        title = stringResource(R.string.settings_language),
                        desc = stringResource(R.string.settings_current_fmt, langLabels[langIndex]),
                        onClick = { showLanguage = true }
                    )
                }
            }

            // ---------- 提醒 ----------
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column {
                        SettingRow(
                            title = stringResource(R.string.settings_notify),
                            desc = stringResource(R.string.settings_notify_desc),
                            onClick = {
                                notifyEnabled = !notifyEnabled
                                prefs.isNotifyEnabled = notifyEnabled
                                rescheduleAsync(ctx)
                                refresh++
                            }
                        ) {
                            GlassSwitch(checked = notifyEnabled, onCheckedChange = { v ->
                                notifyEnabled = v
                                prefs.isNotifyEnabled = v
                                rescheduleAsync(ctx)
                                refresh++
                            })
                        }
                        SettingRow(
                            title = stringResource(R.string.settings_lead),
                            desc = stringResource(R.string.settings_lead_current, leadMinutes),
                            onClick = { showLead = true }
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_snooze),
                            desc = stringResource(
                                R.string.settings_current_fmt,
                                stringResource(R.string.settings_minutes_fmt, snoozeMinutes)
                            ),
                            onClick = { showSnooze = true }
                        )
                    }
                }
            }

            // ---------- 提醒自检 ----------
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    checkTitle,
                                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                                )
                                Spacer(Modifier.height(4.dp))
                                BasicText(
                                    checkDesc,
                                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp))
                                )
                            }
                        }
                        CheckRow(
                            checkNotify,
                            notificationsEnabled(ctx),
                            onClick = {
                                if (notificationsEnabled(ctx)) NavUtil.openChannelSettings(ctx)
                                else NavUtil.openNotificationSettings(ctx)
                            }
                        )
                        CheckRow(
                            checkExact,
                            ReminderScheduler.canScheduleExact(ctx),
                            onClick = { if (!ReminderScheduler.canScheduleExact(ctx)) NavUtil.openExactAlarmSettings(ctx) }
                        )
                        CheckRow(
                            checkChannel,
                            channelHealthy(ctx),
                            onClick = { NavUtil.openChannelSettings(ctx) }
                        )
                        CheckRow(
                            checkBattery,
                            batteryWhitelisted(ctx),
                            onClick = { showBattery = true }
                        )
                        CheckRow(
                            checkFsi,
                            fullScreenIntentUsable(ctx),
                            onClick = { if (!fullScreenIntentUsable(ctx)) NavUtil.openFullScreenIntentSettings(ctx) }
                        )
                    }
                }
            }

            // ---------- 接下来的提醒 ----------
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column(Modifier.padding(16.dp)) {
                        BasicText(
                            nextHeader,
                            style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        )
                        Spacer(Modifier.height(8.dp))
                        // 跟随 dataVersion：记录状态变化后这里立即重算
                        val meds = remember(dataVersion) { repo.listMedications() }
                        val labels = ArrayList<Pair<Long, String>>()
                        for (m in meds) {
                            if (m.asNeeded || !m.remind) continue
                            for (s in repo.schedulesOf(m.id)) {
                                if (!s.enabled) continue
                                val next = ReminderScheduler.nextTrigger(ctx, repo, m, s)
                                if (next <= 0) continue
                                labels.add(
                                    Pair(
                                        next,
                                        stringResource(
                                            R.string.settings_next_item,
                                            m.name ?: "",
                                            s.hhmm(),
                                            formatNext(next)
                                        )
                                    )
                                )
                            }
                        }
                        if (labels.isEmpty()) {
                            BasicText(
                                nextNone,
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp))
                            )
                        } else {
                            labels.sortedBy { it.first }.take(5).forEachIndexed { i, p ->
                                if (i > 0) Spacer(Modifier.height(4.dp))
                                BasicText(
                                    "· ${p.second}",
                                    style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp))
                                )
                            }
                        }
                    }
                }
            }

            // ---------- 数据 ----------
            item { SectionHeader(stringResource(R.string.section_data)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column {
                        SettingRow(
                            title = stringResource(R.string.settings_import),
                            desc = stringResource(R.string.settings_import_card_desc),
                            onClick = { showImport = true }
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_export),
                            desc = stringResource(R.string.settings_export_desc),
                            onClick = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingExport = true
                                    storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    doExport(
                                        ctx, repo, onToast,
                                        exportFilePrefix + "_" + TimeUtils.today() + ".csv"
                                    )
                                }
                            }
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_demo),
                            desc = stringResource(R.string.settings_demo_card_desc),
                            modifier = Modifier.testTag(TestTags.SETTINGS_LOAD_DEMO),
                            onClick = { if (repo.hasAnyData()) showDemo = true else loadDemo(ctx, repo, onToast, onDataChanged, demoLoadedMsg) }
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_clear),
                            desc = stringResource(R.string.settings_clear_desc),
                            modifier = Modifier.testTag(TestTags.SETTINGS_CLEAR_ALL),
                            onClick = { showClear = true }
                        )
                    }
                }
            }

            // ---------- 关于 ----------
            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    SettingRow(
                        title = stringResource(R.string.settings_about_title),
                        desc = stringResource(R.string.settings_about_local)
                    )
                }
            }

            item {
                GlassButton(
                    testReminderText,
                    onClick = {
                        ReminderScheduler.scheduleTest(ctx)
                        onToast(testSentMsg)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // ---------- 弹窗 ----------
    if (showLead) {
        GlassChoiceDialog(
            title = leadDialogTitle,
            options = LEAD_OPTIONS.map { String.format(minutesFmt, it) },
            selectedIndex = LEAD_OPTIONS.indexOfFirst { it == leadMinutes }.coerceAtLeast(0),
            onPick = {
                leadMinutes = LEAD_OPTIONS[it]
                prefs.leadMinutes = leadMinutes
                rescheduleAsync(ctx)
                showLead = false
            },
            onDismiss = { showLead = false }
        )
    }
    if (showSnooze) {
        GlassChoiceDialog(
            title = snoozeDialogTitle,
            options = SNOOZE_OPTIONS.map { String.format(minutesFmt, it) },
            selectedIndex = SNOOZE_OPTIONS.indexOfFirst { it == snoozeMinutes }.coerceAtLeast(0),
            onPick = {
                snoozeMinutes = SNOOZE_OPTIONS[it]
                prefs.snoozeMinutes = snoozeMinutes
                showSnooze = false
            },
            onDismiss = { showSnooze = false }
        )
    }
    if (showBattery) {
        GlassDialogBox(
            title = batteryDialogTitle,
            message = batteryDialogMsg,
            confirmText = goSettingsText,
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showBattery = false
                if (batteryWhitelisted(ctx)) NavUtil.openBatteryOptimizationSettings(ctx)
                else NavUtil.requestIgnoreBatteryOptimizations(ctx)
            },
            onDismiss = { showBattery = false }
        )
    }
    if (showClear) {
        GlassDialogBox(
            title = clearDialogTitle,
            message = clearDialogMsg,
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showClear = false
                clearAll(ctx, repo)
                rescheduleAsync(ctx)
                onToast(clearedMsg)
                onDataChanged()
            },
            onDismiss = { showClear = false },
            destructive = true
        )
    }
    if (showDemo) {
        GlassDialogBox(
            title = demoDialogTitle,
            message = demoDialogMsg,
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showDemo = false
                clearAll(ctx, repo)
                loadDemo(ctx, repo, onToast, onDataChanged, demoLoadedMsg)
            },
            onDismiss = { showDemo = false }
        )
    }
    if (showImport) {
        GlassDialogBox(
            title = importDialogTitle,
            message = importDialogMsg,
            confirmText = importPickText,
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showImport = false
                importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
            },
            onDismiss = { showImport = false }
        )
    }
    if (showLanguage) {
        GlassChoiceDialog(
            title = langDialogTitle,
            options = langLabels,
            selectedIndex = langIndex,
            onPick = { i ->
                showLanguage = false
                val tag = langTags[i]
                if (tag != langTag) {
                    langTag = tag
                    onLocaleChange(tag)
                }
            },
            onDismiss = { showLanguage = false }
        )
    }
    if (showTheme) {
        GlassChoiceDialog(
            title = themeDialogTitle,
            options = themeOptions,
            selectedIndex = if (themeMode == AppThemeMode.MATERIAL) 0 else 1,
            onPick = { i ->
                ThemePrefs.set(ctx, if (i == 0) AppThemeMode.MATERIAL else AppThemeMode.MIUIX)
                showTheme = false
            },
            onDismiss = { showTheme = false }
        )
    }
}

private fun loadDemo(
    ctx: Context,
    repo: MedRepository,
    onToast: (String) -> Unit,
    onDataChanged: () -> Unit,
    loadedMsg: String
) {
    DemoData.load(ctx)
    rescheduleAsync(ctx)
    onToast(loadedMsg)
    onDataChanged()
}

private fun clearAll(ctx: Context, repo: MedRepository) {
    // 走统一入口：先摘掉全部闹钟与通知，再事务化清空（避免逐条删除时的半程状态）
    MedDeletion.deleteAll(ctx, repo)
}

@Composable
private fun CheckRow(title: String, ok: Boolean, onClick: () -> Unit) {
    val c = colors
    val okText = stringResource(R.string.settings_check_ok)
    val badText = stringResource(R.string.settings_check_bad)
    SettingRow(title = title, onClick = onClick) {
        BasicText(
            if (ok) okText else badText,
            style = mediTextStyle(TextStyle(fontFamily = SfPro,
                color = if (ok) c.green else c.red,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ))
        )
    }
}

private fun notificationsEnabled(ctx: Context): Boolean =
    androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()

private fun channelHealthy(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
    val ch = nm.getNotificationChannel(App.CHANNEL_REMINDER) ?: return false
    return ch.importance >= NotificationManager.IMPORTANCE_HIGH
}

private fun batteryWhitelisted(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

private fun fullScreenIntentUsable(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }
    return true
}

/** 下一次提醒的相对时间描述（locale 感知） */
@Composable
private fun formatNext(millis: Long): String {
    val dt = java.time.LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault()
    )
    val today = java.time.LocalDate.now()
    val time = String.format(Locale.US, "%02d:%02d", dt.hour, dt.minute)
    return when {
        dt.toLocalDate() == today -> stringResource(R.string.settings_next_today, time)
        dt.toLocalDate() == today.plusDays(1) -> stringResource(R.string.settings_next_tomorrow, time)
        else -> stringResource(R.string.settings_next_date, dt.monthValue, dt.dayOfMonth, time)
    }
}
