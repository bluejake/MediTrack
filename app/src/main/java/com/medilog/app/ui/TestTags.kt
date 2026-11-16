package com.medilog.app.ui

/**
 * UI 自动化测试标签（testTag）**唯一来源**。
 *
 * 为什么必须有这张表（android-testing-skills 第 1 条铁律）：
 *   「从生产代码打标，测试用标签定位」。
 *   此前 ADB / E2E 脚本全部靠**文案**匹配（`text` / `content-desc`），
 *   i18n 极脆弱——切到中文/德文、或者只改一个字，整套测试就全部失效；
 *   同时脚本里还硬编码了坐标（名称输入框 `[126,645][1154,789]`、Tab 栏 y≈2706），
 *   换分辨率 / 折叠屏立刻失配。
 *
 * 打标后的收益：
 *   1. Compose 会把 testTag 映射成无障碍节点的 `viewIdResourceName`，
 *      因此 **uiautomator 的 `resource-id` 也能拿到它** —— 同一套标签同时服务于
 *      Compose 测试（`onNodeWithTag`）和 ADB E2E（按 resource-id 查找），不再依赖文案；
 *   2. 标签是**语言无关的稳定契约**，改文案/加语言都不会弄坏测试。
 *
 * 命名约定：`<屏幕>.<元素>[.<标识>]`，全小写点分；列表项用工厂函数带上业务 id。
 */
object TestTags {
    // ── 底部导航（MATERIAL / MIUIX 两套都打同一个标签，测试与主题解耦）──────
    const val NAV = "nav.bar"
    fun navTab(index: Int): String = "nav.tab.$index"

    // ── 药品列表 ──────────────────────────────────────────────────────────
    const val MEDS_LIST = "meds.list"
    const val MEDS_ADD = "meds.add"
    const val MEDS_EMPTY = "meds.empty"
    fun medItem(medId: Long): String = "meds.item.$medId"

    // ── 药品详情 ─────────────────────────────────────────────────────────
    const val DETAIL_ROOT = "detail.root"
    const val DETAIL_EDIT = "detail.edit"
    const val DETAIL_DELETE = "detail.delete"

    // ── 新增 / 编辑 ──────────────────────────────────────────────────────
    const val EDIT_ROOT = "edit.root"
    const val EDIT_NAME = "edit.name"
    const val EDIT_STRENGTH = "edit.strength"
    const val EDIT_SAVE = "edit.save"
    const val EDIT_DELETE = "edit.delete"

    // ── 通用对话框（所有 GlassDialogBox 共用，同一时刻只会有一个对话框）────
    const val DIALOG_ROOT = "dialog.root"
    const val DIALOG_CONFIRM = "dialog.confirm"
    const val DIALOG_DISMISS = "dialog.dismiss"

    // ── 今日 ─────────────────────────────────────────────────────────────
    const val TODAY_ROOT = "today.root"

    // ── 设置 ─────────────────────────────────────────────────────────────
    const val SETTINGS_ROOT = "settings.root"
    const val SETTINGS_CLEAR_ALL = "settings.clear_all"
    const val SETTINGS_LOAD_DEMO = "settings.load_demo"

    // ── 删除撤销条（建议 5：误删可恢复）─────────────────────────────────
    const val UNDO_BAR = "undo.bar"
    const val UNDO_ACTION = "undo.action"
}
