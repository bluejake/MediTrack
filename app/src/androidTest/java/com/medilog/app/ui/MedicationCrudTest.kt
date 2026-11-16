package com.medilog.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medilog.app.data.DataBus
import com.medilog.app.data.DeleteUndo
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.data.MedRepository
import com.medilog.app.model.Medication
import com.medilog.app.model.Schedule
import com.medilog.app.ui.screens.AddEditScreen
import com.medilog.app.ui.screens.DetailScreen
import com.medilog.app.ui.screens.MedsScreen
import com.medilog.app.ui.theme.AppTheme
import com.medilog.app.ui.theme.AppThemeMode
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 药品 增 / 查 / 改 / 删 的 **Compose UI 自动化测试**（建议 1）。
 *
 * 为什么这样搭（本环境踩过的坑都记在这里）：
 *  - JetBrains CMP **没有发布 `ui-test-manifest`**，标准的
 *    `createAndroidComposeRule<MainActivity>()` 在本机 API 37 模拟器上会抛
 *    "No compose hierarchies found in the app"
 *    → 改为在 `app/src/debug/AndroidManifest.xml` 声明一个空宿主 Activity，
 *      用 `createComposeRule()` + `setContent` 承载**真实的业务页面**。
 *  - 定位全部走 [TestTags]（**语言无关**，改文案 / 加语言都弄不坏测试 —— 建议 3）；
 *  - 输入走 `performTextInput`（**不弹软键盘**，绕开建议 7 的软键盘遮挡）；
 *  - 滚动走 `performScrollTo`（**不依赖坐标**，绕开建议 6 的硬编码坐标）。
 *
 * [CrudHarness] 刻意复刻 MainActivity 的结构（`AnimatedContent` + 覆盖层栈 +
 * `LocalDataVersion`）：删除药品后 `DataBus.bump()` 会让**正在退出动画中**的详情页
 * 再次重组 —— 这正是历史上 `IndexOutOfBoundsException: index: -1, size: 0` 的触发条件。
 * 因此下面每个删除用例同时都是那条崩溃的回归测试。
 */
@RunWith(AndroidJUnit4::class)
class MedicationCrudTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var repo: MedRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        repo = MedRepository(ctx)
        repo.deleteAllMedications()
        DeleteUndo.discard()
        rule.setContent { AppTheme(mode = AppThemeMode.MATERIAL) { CrudHarness(repo) } }
    }

    // ── 用例 ─────────────────────────────────────────────────────────────

    @Test
    fun createMedication_appearsInList() {
        val name = uniqueName("Create")
        addMedicationViaUi(name)
        waitText(name)
        rule.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun readMedication_opensDetail() {
        val name = uniqueName("Read")
        val id = seedMedication(name)
        waitText(name)
        rule.onNodeWithText(name).performClick()
        waitForTag(TestTags.DETAIL_ROOT)
        rule.onNodeWithText(name).assertIsDisplayed()
        assertTrue("药品应写库成功", id > 0)
    }

    @Test
    fun updateMedication_renamesAndPersists() {
        val name = uniqueName("Update")
        seedMedication(name)
        val renamed = name + "2"
        waitText(name)
        rule.onNodeWithText(name).performClick()
        waitForTag(TestTags.DETAIL_ROOT)
        scrollToAndClick(TestTags.DETAIL_ROOT, TestTags.DETAIL_EDIT)
        waitForTag(TestTags.EDIT_NAME)
        tag(TestTags.EDIT_NAME).performTextClearance()
        tag(TestTags.EDIT_NAME).performTextInput(renamed)
        scrollToAndClick(TestTags.EDIT_ROOT, TestTags.EDIT_SAVE)
        waitText(renamed)
        rule.onNodeWithText(renamed).assertIsDisplayed()
    }

    @Test
    fun deleteMedication_fromDetail_removesFromList_andShowsUndo() {
        val name = uniqueName("DelDetail")
        seedMedication(name)
        waitText(name)
        rule.onNodeWithText(name).performClick()
        waitForTag(TestTags.DETAIL_ROOT)

        scrollToAndClick(TestTags.DETAIL_ROOT, TestTags.DETAIL_DELETE)
        waitForTag(TestTags.DIALOG_CONFIRM)
        tag(TestTags.DIALOG_CONFIRM).performClick()

        waitGoneText(name)                 // ① 列表同步刷新
        waitForTag(TestTags.UNDO_BAR)      // ② 撤销条出现（建议 5）
        tag(TestTags.UNDO_BAR).assertIsDisplayed()
    }

    @Test
    fun deleteMedication_undo_restoresMedication() {
        val name = uniqueName("DelUndo")
        val id = seedMedication(name)
        waitText(name)
        rule.onNodeWithText(name).performClick()
        waitForTag(TestTags.DETAIL_ROOT)
        tag(TestTags.DETAIL_DELETE).performScrollTo().performClick()
        waitForTag(TestTags.DIALOG_CONFIRM)
        tag(TestTags.DIALOG_CONFIRM).performClick()
        waitGoneText(name)

        waitForTag(TestTags.UNDO_ACTION)
        tag(TestTags.UNDO_ACTION).performClick()

        waitText(name)
        rule.onNodeWithText(name).assertIsDisplayed()
        assertTrue("撤销后药品应按原 id 还原", repo.getMedication(id)?.name == name)
    }

    @Test
    fun deleteMedication_fromEditPage_removesFromList() {
        val name = uniqueName("DelEdit")
        seedMedication(name)
        waitText(name)
        rule.onNodeWithText(name).performClick()
        waitForTag(TestTags.DETAIL_ROOT)
        tag(TestTags.DETAIL_EDIT).performScrollTo().performClick()
        // 编辑页的删除按钮在列表末尾、初始未组合，直接滚动过去（不能先 waitForTag）
        scrollToAndClick(TestTags.EDIT_ROOT, TestTags.EDIT_DELETE)
        waitForTag(TestTags.DIALOG_CONFIRM)
        tag(TestTags.DIALOG_CONFIRM).performClick()
        waitGoneText(name)
    }

    @Test
    fun deleteMultipleMedicationsInARow_neverCrashes() {
        // 连删 3 条：历史上「一次删除两次出栈」的 IndexOutOfBoundsException 就在这里必现
        repeat(3) { i ->
            val name = uniqueName("DelRow$i")
            seedMedication(name)
            waitText(name)
            rule.onNodeWithText(name).performClick()
            waitForTag(TestTags.DETAIL_ROOT)
            tag(TestTags.DETAIL_DELETE).performScrollTo().performClick()
            waitForTag(TestTags.DIALOG_CONFIRM)
            tag(TestTags.DIALOG_CONFIRM).performClick()
            waitGoneText(name)
        }
        // 走到这里说明没有崩溃；列表页仍然可用
        waitForTag(TestTags.MEDS_ADD)
        tag(TestTags.MEDS_ADD).assertIsDisplayed()
    }

    // ── 工具 ─────────────────────────────────────────────────────────────

    private fun tag(tag: String) = rule.onNodeWithTag(tag)

    /**
     * 节点是否存在。必须同时吞掉两类异常：
     *  - `AssertionError`：层级已建立但没匹配到节点；
     *  - `IllegalStateException`：层级还没建起来（"No compose hierarchies found"），
     *    启动瞬间必然出现，等待循环要能重试。
     */
    private fun tagExists(t: String): Boolean = try {
        rule.onNodeWithTag(t).fetchSemanticsNode()
        true
    } catch (e: AssertionError) {
        false
    } catch (e: IllegalStateException) {
        false
    }

    private fun textExists(text: String): Boolean = try {
        rule.onNodeWithText(text).fetchSemanticsNode()
        true
    } catch (e: AssertionError) {
        false
    } catch (e: IllegalStateException) {
        false
    }

    private fun waitForTag(t: String, timeoutMs: Long = 8_000L) =
        rule.waitUntil(timeoutMs) { tagExists(t) }

    private fun waitText(text: String, timeoutMs: Long = 8_000L) =
        rule.waitUntil(timeoutMs) { textExists(text) }

    private fun waitGoneText(text: String, timeoutMs: Long = 8_000L) =
        rule.waitUntil(timeoutMs) { !textExists(text) }


    /**
     * 在 LazyColumn 里滚动到目标并点击。
     *
     * ⚠️ 不能直接对目标调 `performScrollTo()`：LazyColumn 里**尚未组合**的项
     * 根本不在语义树里，`performScrollTo` 会先找不到节点而失败。
     * 正确做法是对**列表容器**调 `performScrollToNode(matcher)`。
     */
    private fun scrollToAndClick(listTag: String, targetTag: String) {
        rule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(targetTag))
        tag(targetTag).performClick()
    }

    private fun uniqueName(prefix: String) = "ZZ$prefix${System.currentTimeMillis()}"

    /** 直接写库播种（跳过 UI，让用例聚焦在被测动作上）。 */
    private fun seedMedication(name: String): Long {
        val m = Medication().apply {
            this.name = name
            form = "Tablet"
            strength = "10 mg"
            startDate = "2026-01-01"
            remind = true
        }
        val id = repo.insertMedication(
            m, listOf(Schedule().apply { hour = 8; minute = 0; enabled = true })
        )
        // 播种发生在 setContent 之后，必须 bump 才会让已组合的列表页重读
        DataBus.bump()
        return id
    }

    private fun addMedicationViaUi(name: String) {
        waitForTag(TestTags.MEDS_ADD)
        tag(TestTags.MEDS_ADD).performClick()
        waitForTag(TestTags.EDIT_NAME)
        tag(TestTags.EDIT_NAME).performTextInput(name)
        scrollToAndClick(TestTags.EDIT_ROOT, TestTags.EDIT_SAVE)
        waitForTag(TestTags.MEDS_ADD)
    }
}

// ── 测试用导航壳：复刻 MainActivity 的 AnimatedContent + 覆盖层栈结构 ──────

private sealed interface CrudRoute {
    data object List : CrudRoute
    data class Detail(val id: Long) : CrudRoute
    data class Edit(val id: Long) : CrudRoute
}

@Composable
private fun CrudHarness(repo: MedRepository) {
    var stack by remember { mutableStateOf(listOf<CrudRoute>(CrudRoute.List)) }
    // 与 MainActivity 一致：把 DataBus 版本下发为 LocalDataVersion，
    // 删除后 bump 才会让退出动画中的详情页再次重组（崩溃复现的必要条件）。
    val dataVersion by DataBus.version.collectAsState()

    CompositionLocalProvider(LocalDataVersion provides dataVersion) {
        Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "crudHarness"
        ) { route ->
            when (route) {
                CrudRoute.List -> MedsScreen(
                    repo = repo,
                    onOpenMed = { stack = stack + CrudRoute.Detail(it) },
                    onAdd = { stack = stack + CrudRoute.Edit(0) }
                )

                is CrudRoute.Detail -> DetailScreen(
                    repo = repo,
                    medId = route.id,
                    onEdit = { stack = stack + CrudRoute.Edit(it) },
                    onDeleted = { stack = popOverlaySafe(stack) },
                    onToast = {}
                )

                is CrudRoute.Edit -> AddEditScreen(
                    repo = repo,
                    editId = route.id,
                    onDone = { stack = popOverlaySafe(stack) },
                    onToast = {}
                )
            }
        }
        // 撤销条是全局组件，导航壳里同样要渲染，才能覆盖「删除 → 撤销」
        UndoDeleteBar(
            repo = repo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
        }
    }
}

private fun popOverlaySafe(stack: List<CrudRoute>): List<CrudRoute> =
    if (stack.size > 1) stack.dropLast(1) else stack
