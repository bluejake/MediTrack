package com.medilog.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medilog.app.R
import com.medilog.app.ui.components.ResponsivePage
import com.medilog.app.ui.components.contentWidth
import com.medilog.app.ui.TestTags
import com.medilog.app.data.MedDeletion
import com.medilog.app.data.MedRepository
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.model.Medication
import com.medilog.app.model.Schedule
import com.medilog.app.reminder.ReminderScheduler
import com.medilog.app.ui.components.AppTextField
import com.medilog.app.ui.components.ColorDot
import com.medilog.app.ui.glass.GlassButton
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.GlassChoiceDialog
import com.medilog.app.ui.glass.GlassDialogBox
import com.medilog.app.ui.glass.GlassSwitch
import com.medilog.app.ui.glass.MedIcon
import com.medilog.app.ui.glass.SectionHeader
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import com.medilog.app.util.Palette
import com.medilog.app.util.TimeUtils
import java.util.Locale

/**
 * 添加 / 编辑药物。
 * @param editId >0 表示编辑已有药物
 */
@Composable
fun AddEditScreen(
    repo: MedRepository,
    editId: Long,
    onDone: () -> Unit,
    onToast: (String) -> Unit
) {
    val c = colors
    // 进程级数据版本：保存/删除后回到本页也能拿到最新数据
    val dataVersion = LocalDataVersion.current
    val ctx = LocalContext.current
    val existing = remember(editId, dataVersion) { if (editId > 0) repo.getMedication(editId) else null }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var strength by remember { mutableStateOf(existing?.strength ?: "") }
    // 审计 P1-2：默认剂型跟随 locale（原硬编码 "片剂"）
    var form by remember { mutableStateOf(existing?.form ?: Palette.forms(ctx)[0]) }
    var iconIndex by remember { mutableIntStateOf(existing?.icon ?: 0) }
    var colorIndex by remember { mutableIntStateOf(existing?.color ?: 0) }
    var asNeeded by remember { mutableStateOf(existing?.asNeeded ?: false) }
    var remind by remember { mutableStateOf(existing?.remind ?: true) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var durationText by remember { mutableStateOf((existing?.durationDays ?: 0).toString()) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: TimeUtils.today()) }

    val times = remember(editId, dataVersion) {
        mutableStateListOf<IntArray>().apply {
            if (existing != null && !existing.asNeeded) {
                repo.schedulesOf(existing.id).forEach { add(intArrayOf(it.hour, it.minute)) }
            }
            if (isEmpty()) add(intArrayOf(8, 0))
        }
    }

    var showForm by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var editingTimeIndex by remember { mutableStateOf(-1) }
    var showTimePicker by remember { mutableStateOf(false) }

    ResponsivePage {
        LazyColumn(
            Modifier
                .fillMaxHeight()

                .contentWidth()
                .testTag(TestTags.EDIT_ROOT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 60.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 预览
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, tintAlpha = 0.2f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MedIcon(
                            painter = painterResource(Palette.iconRes(iconIndex)),
                            color = Color(Palette.COLORS[colorIndex % Palette.COLORS.size].toLong() or 0xFF000000L),
                            size = 52.dp
                        )
                        Spacer(Modifier.width(14.dp))
                            BasicText(
                                "$form · ${Palette.iconName(ctx, iconIndex)} · ${Palette.colorName(ctx, colorIndex)}",
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium))
                            )
                    }
                }
            }

            // 基本信息
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column(Modifier.padding(16.dp)) {
                        AppTextField(
                            name, { name = it }, stringResource(R.string.hint_name),
                            fieldModifier = Modifier.testTag(TestTags.EDIT_NAME)
                        )
                        Spacer(Modifier.height(12.dp))
                        AppTextField(
                            strength, { strength = it }, stringResource(R.string.hint_strength),
                            fieldModifier = Modifier.testTag(TestTags.EDIT_STRENGTH)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showForm = true }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(stringResource(R.string.form), style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 15.sp)))
                            Spacer(Modifier.weight(1f))
                            BasicText(form, style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 15.sp)))
                        }
                    }
                }
            }

            // 形状
            item { SectionHeader(stringResource(R.string.shape)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in 0 until 6) {
                            val sel = i == iconIndex
                            val color = if (sel) Color(Palette.COLORS[colorIndex % Palette.COLORS.size].toLong() or 0xFF000000L)
                            else c.divider
                            Box(
                                Modifier
                                    // MD3 触控目标 ≥48dp（原 44dp 图标格）
                                    .size(48.dp)
                                    .clickable { iconIndex = i },
                                contentAlignment = Alignment.Center
                            ) {
                                MedIcon(
                                    painter = painterResource(Palette.iconRes(i)),
                                    color = color,
                                    size = 44.dp
                                )
                            }
                        }
                    }
                }
            }

            // 颜色
            item { SectionHeader(stringResource(R.string.color)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in Palette.COLORS.indices) {
                            ColorDot(
                                color = Color(Palette.COLORS[i].toLong() or 0xFF000000L),
                                selected = i == colorIndex,
                                onClick = { colorIndex = i }
                            )
                        }
                    }
                }
            }

            // 按需服用
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            BasicText(stringResource(R.string.as_needed), style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp)))
                            Spacer(Modifier.height(4.dp))
                            BasicText(stringResource(R.string.as_needed_desc), style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)))
                        }
                        GlassSwitch(checked = asNeeded, onCheckedChange = { asNeeded = it })
                    }
                }
            }

            // 服用计划
            if (!asNeeded) {
                item { SectionHeader(stringResource(R.string.schedule_title)) }
                item {
                    GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                        Column(Modifier.padding(16.dp)) {
                            times.forEachIndexed { idx, t ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText(
                                        TimeUtils.friendlyTime(
                                            String.format(Locale.US, "%02d:%02d", t[0], t[1])
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                editingTimeIndex = idx
                                                showTimePicker = true
                                            },
                                        style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp))
                                    )
                                    Image(
                                        painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.delete),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clickable { times.removeAt(idx) }
                                            .padding(15.dp)
                                            .size(18.dp),
                                        colorFilter = ColorFilter.tint(c.textSecondary)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            GlassButton(
                                stringResource(R.string.add_time),
                                onClick = {
                                    editingTimeIndex = -1
                                    showTimePicker = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 其他
            item { SectionHeader(stringResource(R.string.section_other)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tintAlpha = 0.18f) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showDate = true }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(stringResource(R.string.start_date), style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 15.sp)))
                            Spacer(Modifier.weight(1f))
                            BasicText(
                                TimeUtils.formatCnDate(startDate),
                                style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textSecondary, fontSize = 15.sp))
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        AppTextField(
                            durationText, { durationText = it.filter { ch -> ch.isDigit() } },
                            stringResource(R.string.duration),
                            keyboardType = KeyboardType.Number
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(stringResource(R.string.remind_switch), style = mediTextStyle(TextStyle(fontFamily = SfPro, color = c.textPrimary, fontSize = 16.sp)))
                            Spacer(Modifier.weight(1f))
                            GlassSwitch(checked = remind, onCheckedChange = { remind = it })
                        }
                        Spacer(Modifier.height(8.dp))
                        AppTextField(notes, { notes = it }, stringResource(R.string.notes), singleLine = false)
                    }
                }
            }

            item {
                // 审计 P1-2：onClick 是普通 lambda（非 composable 上下文），文案需先在 composable 作用域取好
                val savedMsg = stringResource(R.string.saved)
                val nameRequiredMsg = stringResource(R.string.name_required)
                val timeRequiredMsg = stringResource(R.string.time_required)
                val passedHintMsg = stringResource(R.string.save_passed_hint)
                GlassButton(
                    stringResource(R.string.save),
                    onClick = {
                        val n = name.trim()
                        if (n.isEmpty()) { onToast(nameRequiredMsg); return@GlassButton }
                        if (!asNeeded && times.isEmpty()) { onToast(timeRequiredMsg); return@GlassButton }
                        val m = Medication()
                        m.name = n
                        m.strength = strength.trim()
                        m.form = form
                        m.icon = iconIndex
                        m.color = colorIndex
                        m.asNeeded = asNeeded
                        m.startDate = startDate
                        m.durationDays = durationText.trim().toIntOrNull() ?: 0
                        m.remind = remind
                        m.notes = notes.trim()
                        val schedules = if (!asNeeded) {
                            times.map { t ->
                                Schedule().also {
                                    it.hour = t[0]; it.minute = t[1]; it.enabled = true
                                }
                            }
                        } else emptyList()
                        if (editId > 0) {
                            m.id = editId
                            // 缺陷 B 修复（2026-10-26）：**先摘旧闹钟，再改库**。
                            // updateMedication → replaceSchedules 是「先 delete 该药全部计划行、
                            // 再逐条 insert」，新行会拿到**新的自增 id**；而闹钟 requestCode
                            // = 100000 + scheduleId（ReminderScheduler.reqFor），cancelAll 只能
                            // 按"当前数据库里还有哪些计划"反查取消 —— 旧 id 的闹钟永远取消不掉，
                            // 成了孤儿（实测：基线 4 个 REMIND，原样保存后变 6 个，同一时刻两个）。
                            // 与 MedDeletion 删除流程同一范式：删行之前先把这批 id 的闹钟摘掉。
                            ReminderScheduler.cancelForMed(
                                ctx.applicationContext,
                                repo.scheduleIdsOf(editId)
                            )
                            repo.updateMedication(m, schedules)
                        } else {
                            repo.insertMedication(m, schedules)
                        }
                        onToast(savedMsg)
                        // 零配置提示（2026-10-25 补）：所选时间今天已全部过去时，
                        // nextTrigger 会顺延到次日——老年用户看不到任何反馈会误以为
                        // "没设上"，是「自建药品不触发」投诉的高发点，这里明确告知。
                        if (!asNeeded && times.isNotEmpty()) {
                            val todayStr = TimeUtils.today()
                            val allPassed = times.all { t ->
                                TimeUtils.passed(
                                    todayStr,
                                    String.format(Locale.US, "%02d:%02d", t[0], t[1])
                                )
                            }
                            if (allPassed) onToast(passedHintMsg)
                        }
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.EDIT_SAVE),
                    tint = c.primary,
                    textColor = Color.White
                )
            }

            if (editId > 0) {
                item {
                    GlassButton(
                        stringResource(R.string.confirm_delete_title),
                        onClick = { showDelete = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.EDIT_DELETE),
                        tint = c.red,
                        textColor = Color.White
                    )
                }
            }
        }
    }

    if (showForm) {
        val forms = Palette.forms(ctx)
        GlassChoiceDialog(
            title = stringResource(R.string.form),
            options = forms.toList(),
            selectedIndex = forms.indexOf(form).coerceAtLeast(0),
            onPick = {
                form = forms[it]
                iconIndex = minOf(it, 5)
                showForm = false
            },
            onDismiss = { showForm = false }
        )
    }

    if (showDate) {
        DatePickerDialog(
            initial = startDate,
            onPick = {
                startDate = it
                showDate = false
            },
            onDismiss = { showDate = false }
        )
    }

    if (showTimePicker) {
        val cur = if (editingTimeIndex >= 0 && editingTimeIndex < times.size) times[editingTimeIndex]
        else (times.lastOrNull() ?: intArrayOf(8, 0))
        TimePickerDialog(
            initialHour = cur[0],
            initialMinute = cur[1],
            onPick = { h, m ->
                if (editingTimeIndex >= 0 && editingTimeIndex < times.size) {
                    times[editingTimeIndex] = intArrayOf(h, m)
                } else {
                    times.add(intArrayOf(h, m))
                }
                times.sortWith { a, b ->
                    (a[0] * 60 + a[1]).compareTo(b[0] * 60 + b[1])
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showDelete) {
        val deletedMsg = stringResource(R.string.deleted)
        GlassDialogBox(
            title = stringResource(R.string.confirm_delete_title),
            message = stringResource(R.string.confirm_delete_msg, name),
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showDelete = false
                // 与详情页同一条完整删除链路（先摘闹钟 → 事务删库 → 刷新）
                val ok = MedDeletion.deleteOne(ctx, repo, editId)
                if (ok) onToast(deletedMsg)
                onDone()   // 内含 DataBus.bump() + 后台重排闹钟 + 出栈（幂等）
            },
            onDismiss = { showDelete = false },
            destructive = true
        )
    }
}
