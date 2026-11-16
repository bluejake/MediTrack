package com.medilog.app.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.medilog.app.ui.theme.SfPro
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import com.medilog.app.R
import com.medilog.app.ui.TestTags
import com.medilog.app.data.DataBus
import com.medilog.app.data.DeleteUndo
import com.medilog.app.data.LocalDataVersion
import com.medilog.app.data.MedRepository
import com.medilog.app.reminder.ReminderScheduler
import com.medilog.app.reminder.rescheduleAsync
import com.medilog.app.ui.components.rememberDrawablePainter
import com.medilog.app.ui.glass.GlassDialogBox
import com.medilog.app.ui.glass.GlassCard
import com.medilog.app.ui.glass.GlassSurface
import com.medilog.app.ui.glass.LocalBackdrop
import com.medilog.app.ui.screens.AddEditScreen
import com.medilog.app.ui.screens.DetailScreen
import com.medilog.app.ui.screens.HistoryScreen
import com.medilog.app.ui.screens.MedsScreen
import com.medilog.app.ui.screens.SettingsScreen
import com.medilog.app.ui.screens.TodayScreen
import com.medilog.app.ui.theme.AppTheme
import com.medilog.app.ui.theme.AppThemeMode
import com.medilog.app.ui.theme.LocalAppThemeMode
import com.medilog.app.ui.theme.LocalMd3Colors
import com.medilog.app.ui.theme.Md3
import com.medilog.app.ui.theme.ThemePrefs
import com.medilog.app.ui.theme.mediTextStyle
import com.medilog.app.ui.theme.colors
import com.medilog.app.ui.glass.md3StateLayer
import com.medilog.app.ui.motion.PageTransitions
import com.medilog.app.util.DemoData
import com.medilog.app.util.LocaleHelper
import com.medilog.app.util.NavUtil
import com.medilog.app.util.Prefs

class MainActivity : ComponentActivity() {

    // 审计 U4 + 黑屏修复：全 API 以 Prefs 持久化语言标签，由 attachBaseContext 包装上下文；
    // 切换语言走进程内 LocalContext 重排（见 MediAppRoot），不再 recreate。
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.medilog.app.util.LocaleHelper.wrap(newBase))
    }

    private val notifyPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.permission_notify_hint, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemePrefs.init(this)

        requestNotifyPermissionIfNeeded()
        maybeLoadDemoData()

        setContent {
            MediAppRoot()
        }
    }

    // 审计 P0-4：回到前台的闹钟重排移到后台线程 + 3 秒防抖。
    // scheduleAll 含 DB 查询与 AlarmManager IPC，主线程执行有 ANR 风险；
    // 防抖避免"编辑保存(已排)→立刻 onResume(再排)"的重复劳动与并发互踩。
    private var lastScheduleUptime = 0L

    override fun onResume() {
        super.onResume()
        // 零配置刷新保障：应用内数据可能被「通知栏记录 / 全屏提醒页 / 其他入口」改动过，
        // 每次回到前台都广播一次数据版本，界面重读数据库 → 无需切页即最新。
        // DataBus 是 StateFlow，重复 bump 只会触发一次重组，成本可忽略。
        DataBus.bump()
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastScheduleUptime < 3_000L) return
        lastScheduleUptime = now
        lifecycleScope.launch(Dispatchers.IO + NonCancellable) {
            ReminderScheduler.scheduleAll(this@MainActivity)
        }
    }

    private fun requestNotifyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun maybeLoadDemoData() {
        val repo = MedRepository(this)
        if (repo.hasAnyData()) return
        val prefs = Prefs(this)
        if (prefs.isDemoLoaded) return
        DemoData.load(this)
        prefs.isDemoLoaded = true
    }
}

/** 页面路由（内存内栈，无需 navigation-compose） */
private sealed interface Route {
    data object Today : Route
    data object Meds : Route
    data object History : Route
    data object Settings : Route
    data class Detail(val medId: Long) : Route
    data class AddEdit(val medId: Long) : Route
}

/**
 * 页面状态（[AnimatedContent] 的 targetState）。
 *
 * 同时携带当前 Tab 与覆盖层，并给出层级深度：
 * Tab = 0、Detail = 1、AddEdit = 2。深度差即导航方向 ——
 * 目标更深 = 前向（进入），更浅 = 后向（返回），相同 = 平级切换（Tab）。
 */
private data class PageState(
    val tab: Int,
    val overlay: Route?
) {
    /** 层级深度：用于判定前向/后向 */
    val depth: Int
        get() = when (overlay) {
            is Route.Detail -> 1
            is Route.AddEdit -> 2
            else -> 0
        }

    /** 内容键：同一键复用组合（Tab 各自稳定，覆盖层按路由身份） */
    val key: Any
        get() = overlay ?: "tab-$tab"
}

// 审计 P1-2：Tab 标题改为 composable 内经 stringResource 取值（顶级 val 无法读资源）。
// 顺序与 TAB_ROUTES / TAB_ICONS 一一对应：今天 / 用药 / 历史 / 设置。
private val TAB_TITLE_RES = listOf(
    R.string.tab_today, R.string.tab_meds, R.string.tab_history, R.string.tab_settings
)

private val TAB_ROUTES = listOf(Route.Today, Route.Meds, Route.History, Route.Settings)

private val TAB_ICONS = listOf(
    R.drawable.ic_today,
    R.drawable.ic_meds,
    R.drawable.ic_history,
    R.drawable.ic_settings
)

/**
 * 语言切换根：以进程内 CompositionLocal 提供「仅覆盖资源（携带目标语言）的 Context」。
 * 切换语言仅驱动组合树重排（根背景常驻），彻底避免 Activity 重建导致的黑屏闪烁。
 */
@Composable
private fun MediAppRoot() {
    val realCtx = LocalContext.current
    val mode = ThemePrefs.mode.value
    var appLocaleTag by rememberSaveable { mutableStateOf(LocaleHelper.currentTag(realCtx)) }
    // 当前 Tab。**必须提升到 AppTheme 之外**（缺陷 A 修复 2026-10-26）：
    // rememberSaveable 以 currentCompositeKeyHash（槽位的组合位置）为注册键，而
    // AppTheme 的 when(mode) 分支会在 MaterialTheme 与 MiuixTheme 之间切换、MediApp 内
    // 的 bg_ambient Image 节点也会随主题增删 —— 组合结构一变，槽位键就失效，
    // 保存值永远读不回来，tab 回落默认值 0 = 用户被踢回「今天」页（实测 13/13 复现）。
    // 放在这里（主题切换点之上）后，槽位位置不再受主题结构变化影响。
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val localeCtx = remember(appLocaleTag) { LocaleHelper.wrap(realCtx, appLocaleTag) }
    val localeWrapped = remember(localeCtx) { LocaleResourcesContext(realCtx, localeCtx.resources) }
        CompositionLocalProvider(LocalContext provides localeWrapped) {
            AppTheme(mode = mode) {
                // 进程级数据版本：通知栏「记录」/ 全屏提醒页 / onResume 等任意入口写库后
                // 都会 bump，这里收集后下发给所有页面 → 界面立即刷新（见 DataBus）。
                val dataVersion by DataBus.version.collectAsState()
                CompositionLocalProvider(LocalDataVersion provides dataVersion) {
                    MediApp(
                        tab = tab,
                        onTabChange = { tab = it },
                        onLocaleChange = { tag ->
                            LocaleHelper.persist(realCtx, tag)
                            appLocaleTag = tag
                        }
                    )
                }
            }
        }
}

/**
 * 仅替换 Resources（携带目标语言 Locale）的 ContextWrapper；其余调用全部委派真实 Activity，
 * 避免整体替换 LocalContext 导致 LocalActivityResultRegistryOwner / LocalLifecycleOwner 等丢失。
 */
private class LocaleResourcesContext(
    host: Context,
    private val localeResources: Resources
) : ContextWrapper(host) {
    override fun getResources(): Resources = localeResources
}

@Composable
fun MediApp(
    tab: Int,
    onTabChange: (Int) -> Unit,
    onLocaleChange: (String) -> Unit
) {
    val ctx = LocalContext.current
    val repo = remember { MedRepository(ctx) }
    val prefs = remember { Prefs(ctx) }
    val c = colors
    // 当前主题模式：MIUIX 保留其标志性液态玻璃环境光（底部暖桃椭圆柔光）；
    // MATERIAL 模式按 MD3 准则收敛为干净表面，不再叠加 iOS 风格环境光（审计 U 结论：该底光非 MD3）。
    val mode = ThemePrefs.mode.value
    val backdrop = rememberLayerBackdrop()
    // 覆盖层栈：仅承载详情/编辑页；为空时展示 tab 对应的主页面
    val stack = remember { mutableStateListOf<Route>() }
    var showExactDialog by remember { mutableStateOf(false) }

    /**
     * 幂等出栈：栈空时直接忽略。
     *
     * 崩溃根因防护：删除药品后 DataBus.bump() 会让详情页在**退出动画期间**再次重组，
     * 此时药品已不存在，DetailScreen 的 null 兜底会第二次回调 onDeleted()。
     * 旧代码无条件执行 stack.removeAt(stack.lastIndex)，第二次调用时 lastIndex == -1，
     * 直接抛 java.lang.IndexOutOfBoundsException: index: -1, size: 0 并杀掉整个进程。
     */
    fun popOverlay() {
        stack.popOverlaySafe()
    }

    // 覆盖层栈非空时，返回键逐层退出
    BackHandler(enabled = stack.isNotEmpty()) {
        popOverlay()
    }

    // 精确闹钟权限兜底检查（Android 14+）
    //
    // 零配置原则：本应用清单同时声明 USE_EXACT_ALARM（安装时授予）与 SCHEDULE_EXACT_ALARM，
    // 正常情况下 canScheduleExact() 恒为 true，**这个弹窗永远不会出现**。
    // 仅当个别 ROM 收回了精确闹钟能力时才提示，且**终身只提示一次**（asked 标记）——
    // 之后静默使用降级通道（setAndAllowWhileIdle）+ 每日自检兜底，不反复打扰用户。
    LaunchedEffect(Unit) {
        val prefs = Prefs(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ReminderScheduler.canScheduleExact(ctx) &&
            !prefs.exactAlarmAsked
        ) {
            prefs.exactAlarmAsked = true
            showExactDialog = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        // 底层环境光（被玻璃折射）：仅 MIUIX 模式保留其液态玻璃环境光；
        // MATERIAL 模式按 MD3 收敛为干净表面，不叠加 iOS 风格底光。
        if (mode == AppThemeMode.MIUIX) {
            Image(
                painter = rememberDrawablePainter(R.drawable.bg_ambient),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                contentScale = ContentScale.Crop
            )
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(Modifier.fillMaxSize()) {
                // 标题栏：MATERIAL = MD3 small top app bar（64dp / titleLarge / onSurface）；
                // MIUIX 保留原有大标题视觉
                val isMaterial = mode == AppThemeMode.MATERIAL
                val md3 = LocalMd3Colors.current
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .then(
                            if (isMaterial) {
                                Modifier.height(Md3.TopAppBarHeight)
                            } else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = if (isMaterial) 0.dp else 10.dp)
                ) {
                    val overlay = stack.lastOrNull()
                    val tabTitles = TAB_TITLE_RES.map { stringResource(it) }
                    val titleText = when (overlay) {
                        is Route.Detail -> stringResource(R.string.detail_title)
                        is Route.AddEdit -> if (overlay.medId == 0L) stringResource(R.string.add_med) else stringResource(R.string.edit_med)
                        else -> tabTitles[tab]
                    }
                    // 标题随页面切换淡入淡出（只淡入淡出，不做位移/缩放，避免阅读时晃动）
                    AnimatedContent(
                        targetState = titleText,
                        modifier = Modifier.align(Alignment.CenterStart),
                        transitionSpec = { PageTransitions.titleTransform(isMaterial) },
                        contentAlignment = Alignment.CenterStart,
                        label = "titleTransition"
                    ) { text ->
                        BasicText(
                            text,
                            style = if (isMaterial) {
                                Md3.TitleLarge.copy(color = md3.onSurface)
                            } else {
                                mediTextStyle(TextStyle(fontFamily = SfPro,
                                    color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold
                                ))
                            }
                        )
                    }
                }

                // 内容区：按主题模式套页面转场
                // - MATERIAL = MD3（Tab = fade through；推入/返回 = 共享轴 Y 前/后向）
                // - MIUIX = 官方导航手感（横向卡片栈：整宽滑入 + 1/4 屏宽视差 + alpha 0.9）
                AnimatedContent(
                    targetState = PageState(tab, stack.lastOrNull()),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    transitionSpec = {
                        val event = when {
                            targetState.depth > initialState.depth -> PageTransitions.NavEvent.Forward
                            targetState.depth < initialState.depth -> PageTransitions.NavEvent.Backward
                            else -> PageTransitions.NavEvent.TabSwitch
                        }
                        PageTransitions.pageTransform(isMaterial, event)
                    },
                    contentKey = { it.key },
                    label = "pageTransition"
                ) { state ->
                    val overlay = state.overlay
                    if (overlay != null) {
                        when (overlay) {
                            is Route.Detail -> DetailScreen(
                                repo = repo,
                                medId = overlay.medId,
                                onEdit = { stack.add(Route.AddEdit(it)) },
                                onDeleted = {
                                    popOverlay()
                                    DataBus.bump()
                                },
                                onToast = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                            )
                            is Route.AddEdit -> AddEditScreen(
                                repo = repo,
                                editId = overlay.medId,
                                onDone = {
                                    popOverlay()
                                    DataBus.bump()
                                    // 审计 P0-4：与 onResume 同样移到后台线程
                                    rescheduleAsync(ctx)   // 建议 4：统一异步重排入口
                                },
                                onToast = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                            )
                            else -> Unit
                        }
                    } else {
                        when (TAB_ROUTES[state.tab]) {
                            is Route.Today -> TodayScreen(
                                repo = repo,
                                onOpenMed = { stack.add(Route.Detail(it)) },
                                onChanged = { DataBus.bump() }
                            )
                            is Route.Meds -> MedsScreen(
                                repo = repo,
                                onOpenMed = { stack.add(Route.Detail(it)) },
                                onAdd = { stack.add(Route.AddEdit(0)) }
                            )
                            is Route.History -> HistoryScreen(repo = repo)
                            is Route.Settings -> SettingsScreen(
                                repo = repo,
                                prefs = prefs,
                                onToast = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() },
                                onDataChanged = { DataBus.bump() },
                                onLocaleChange = onLocaleChange
                            )
                            else -> Unit
                        }
                    }
                }
            }

            // 底部滚动渐隐遮罩 + 导航栏：随覆盖层（详情/编辑页）进出做**同步**渐隐渐移。
            //
            // 残影根因（2026-10-25 录屏逐帧实测）：旧写法是 `if (stack.isEmpty())` 直接组合——
            // 退出详情时，遮罩与导航栏以**全透明度瞬间入组合**，而 AnimatedContent 仍在播放
            // 500ms 的详情出场动画：半透明玻璃栏（tintAlpha 0.5）把仍在滑出的页面透出来，
            // 表现为栏体区域单帧 MAD≈115 的突变 + 随后 0.45s 内 4~5 的持续透影。
            // 改为 AnimatedVisibility 后，栏体与页面转场同步渐显/渐隐，运动中的页面被
            // 渐进覆盖，不再有「瞬时弹出 + 透影」。进入详情时同理：栏体随页面推入渐沉。
            // （与 UndoDeleteBar 同一范式，保持全 App 浮层显隐语言一致。）
            val isMaterialBar = mode == AppThemeMode.MATERIAL
            val md3Bar = LocalMd3Colors.current
            val fadeColor = if (isMaterialBar) md3Bar.surfaceContainer else c.bg
            // 进入：详情退出时栏体渐显并轻升（MD3 = EmphasizedDecelerate 300ms；
            // MIUIX = 官方导航曲线 300ms，位移 1/4 栏高）
            val barEnter = if (isMaterialBar) {
                fadeIn(tween(Md3.DurationMedium2, easing = Md3.EasingEmphasizedDecelerate)) +
                    slideInVertically(tween(Md3.DurationMedium2, easing = Md3.EasingEmphasizedDecelerate)) { h -> h / 4 }
            } else {
                fadeIn(tween(300, easing = PageTransitions.MiuixNavCurve)) +
                    slideInVertically(tween(300, easing = PageTransitions.MiuixNavCurve)) { h -> h / 4 }
            }
            // 退出：详情推入时栏体渐隐并轻沉（退出更快：MD3 Short4 / MIUIX 150-200ms）
            val barExit = if (isMaterialBar) {
                fadeOut(tween(Md3.DurationShort4, easing = Md3.EasingEmphasizedAccelerate)) +
                    slideOutVertically(tween(Md3.DurationShort4, easing = Md3.EasingEmphasizedAccelerate)) { h -> h / 4 }
            } else {
                fadeOut(tween(150, easing = PageTransitions.MiuixNavCurve)) +
                    slideOutVertically(tween(200, easing = PageTransitions.MiuixNavCurve)) { h -> h / 4 }
            }
            AnimatedVisibility(
                visible = stack.isEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = barEnter,
                exit = barExit,
                label = "bottomBar"
            ) {
                // 遮罩与栏体同组同基准：两者仍各自 align BottomCenter（几何与旧实现一致），
                // 但作为一个整体做显隐动画，保证任何时刻形态一致、不错位。
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to fadeColor.copy(alpha = 0f),
                                    0.35f to fadeColor.copy(alpha = 0.9f),
                                    1f to fadeColor.copy(alpha = 0.97f)
                                )
                            )
                    )
                    BottomGlassBar(
                        selected = tab,
                        onSelect = onTabChange,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // 删除撤销条（建议 5）：误删 5 秒内可原样还原。
            // 位置固定在底部导航栏上方，不随页面转场移动，任何 Tab 下都能撤销。
            val restoredMsg = stringResource(R.string.toast_undone)
            UndoDeleteBar(
                repo = repo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp),
                onRestored = { Toast.makeText(ctx, restoredMsg, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    if (showExactDialog) {
        GlassDialogBox(
            title = stringResource(R.string.exact_alarm_dialog_title),
            message = stringResource(R.string.exact_alarm_dialog_msg),
            confirmText = stringResource(R.string.go_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                showExactDialog = false
                NavUtil.openExactAlarmSettings(ctx)
            },
            onDismiss = { showExactDialog = false }
        )
    }
}


// ---- Material 3 动效令牌（参考 WeKit 的 Compose M3 范式）----
// EmphasizedDecelerate：元素入场/落定（"减速停稳"）；Emphasized：中等时长位移
private val M3_STANDARD_EASING = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)        // M3 EasingStandardCubicBezier
private val M3_EMPHASIZED_DECELERATE = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) // M3 EasingEmphasizedDecelerateCubicBezier
private const val M3_DURATION_SHORT = 200   // DurationShort4
private const val M3_DURATION_MEDIUM = 300  // DurationMedium2
private const val M3_DURATION_LONG = 400    // DurationMedium4

/**
 * 感知均匀色彩插值（OKLab 空间）。
 * M3 色彩过渡建议：在 OKLab（而非 sRGB/线性 sRGB）插值可避免两色经中性灰的“发灰”中点，
 * 消除选中态切换时“先变灰再变橙”的异常中间灰态。
 */
private fun lerpColorOklab(a: Color, b: Color, t: Float): Color {
    val ca = a.convert(ColorSpaces.Oklab)
    val cb = b.convert(ColorSpaces.Oklab)
    return Color(
        lerp(ca.red, cb.red, t),
        lerp(ca.green, cb.green, t),
        lerp(ca.blue, cb.blue, t),
        1f,
        ColorSpaces.Oklab
    ).convert(ColorSpaces.Srgb)
}

/**
 * 底部导航栏。
 *
 * - MATERIAL = MD3 navigation bar：高度 80dp、容器 surfaceContainer、激活指示器 64×32
 *   secondaryContainer 胶囊（滑动，EmphasizedDecelerate 300ms）、图标 24dp、
 *   标签 labelMedium；仅点按切换（MD3 导航栏无拖拽换页交互）。
 * - MIUIX = 液态玻璃 Dock：保留玻璃折射 + 拖拽换页 + 触觉等既有交互（视觉与行为不变）。
 */
@Composable
private fun BottomGlassBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = colors
    val isM3 = LocalAppThemeMode.current == AppThemeMode.MATERIAL
    val md3 = LocalMd3Colors.current

    if (isM3) {
        Md3NavigationBar(
            selected = selected,
            onSelect = onSelect,
            modifier = modifier
        )
        return
    }

    // ── 以下为 MIUIX 液态玻璃 Dock（架构与交互保持不变）────────────────────
    // 架构（2026-10-21 重写，核心是「静态玻璃 + 动态交互层」分离）：
    // ```
    // Box(navigationBarsPadding)
    //  └─ BoxWithConstraints(58dp 胶囊几何)
    //      ├─ ① GlassSurface(空内容)   ← 玻璃面只画一次，拖拽全程不失效
    //      └─ ② Box(clip(Capsule()))   ← 交互/指示层，画在玻璃之上
    //           ├─ 透镜（graphicsLayer 位移/缩放，仅变换属性）
    //           └─ Row(图标列 + 手势）
    // ```
    // ⚠️ 残影根因（用户报"拖动产生残影"）：
    // 旧结构里透镜和图标都是 GlassSurface 的**子内容**——拖拽每帧改位置/颜色都会使
    // surface 的 draw 失效 → `drawBackdrop` 的 blur+lens 着色器**整帧重跑**。
    // 本机 SwiftShader 软渲染下单帧几十 ms → 掉帧 → 透镜滞后手指 1~3 帧 = 残影。
    // 修复：玻璃面抽成空内容的独立层（底层极光静态 → backdrop 录制层不变 → 着色器不重跑），
    // 透镜走 graphicsLayer 变换（不重录显示列表）、图标着色只重绘本层 —— 拖拽全程
    // **零 shader 重执行**，跟手无残影。
    // 交互：吸附 EmphasizedDecelerate 300ms；抓取缩放快弹簧；速度形变 150ms 回弹；
    // 触觉分级（抓取=LongPress，越格/触边/落定=TextHandleMove）；
    // 手势仅水平主导才接管；释放默认就近取整，快甩(>800px/s)且未过邻格中点才进一格。
    val tabCount = TAB_TITLE_RES.size
    // 标题一次性取出（CompositionLocal 读取），避免逐帧 stringResource 重复分配 → 消除卡顿
    val titles = TAB_TITLE_RES.map { stringResource(it) }
    // 手势内 Animatable 写入口（awaitEachGesture 受 @RestrictsSuspension 约束，不能直调挂起函数）
    val scope = rememberCoroutineScope()
    // 手势内读最新 selected / onSelect，杜绝 pointerInput 块闭包过期
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current

    // 吸附：M3 EmphasizedDecelerate，300ms 确定性收场（无过冲 → 边界零抖动）
    val snapSpec = tween<Float>(durationMillis = M3_DURATION_MEDIUM, easing = M3_STANDARD_EASING)
    val lensPos = remember { Animatable(selected.toFloat()) }

    // 抓取态：透镜放大 + 描边增强（快弹簧，干脆的"拿起/放下"）
    var dragging by remember { mutableStateOf(false) }
    val grabScale by animateFloatAsState(
        targetValue = if (dragging) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "lensGrab"
    )
    // 速度形变（液态挤压拉伸）：只在 graphicsLayer 里读 → 不走重组，只更新图层变换
    var lensStretch by remember { mutableFloatStateOf(0f) }

    // 唯一动画所有者：selected 变化 → 滑向目标格
    LaunchedEffect(selected) {
        lensPos.animateTo(selected.toFloat(), snapSpec)
    }

    Box(
        modifier.fillMaxWidth().navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            Modifier
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .height(58.dp)
        ) {
            val tabWidth = maxWidth / tabCount

            // ① 玻璃面：空内容 → 静态绘制。拖拽全程不失效，
            //    blur+lens 着色器不每帧重跑（残影根因的修复关键）
            GlassSurface(
                modifier = Modifier.fillMaxSize(),
                capsule = true,
                blurRadius = 20.dp,
                refractionHeight = 24.dp,
                refractionAmount = 24.dp,
                // 上游 LiquidBottomTabs 实证：底部栏不开 depthEffect（depth shader 在薄胶囊上产生梳齿状采样伪影）
                lensDepth = false,
                tintAlpha = 0.5f
            ) {}

            // ② 交互/指示层：玻璃的兄弟，绘制在其上；拖拽只重绘本层，不触发玻璃重绘
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(Capsule())
            ) {
                // 透镜（选中态指示器）
                val drawPos = lensPos.value.coerceIn(-0.02f, (tabCount - 1).toFloat() + 0.02f)
                Box(
                    Modifier
                        .offset(x = tabWidth * 0.06f, y = 5.dp)
                        .size(tabWidth - 12.dp, 48.dp)
                        .graphicsLayer {
                            // 仅变换属性：不重录显示列表、不使父层失效
                            translationX = drawPos * tabWidth.toPx()
                            val s = lensStretch
                            scaleX = grabScale * (1f + s)
                            scaleY = grabScale * (1f - s * 0.6f)
                        }
                        .background(c.primary.copy(alpha = if (dragging) 0.16f else 0.10f), Capsule())
                        .border(0.5.dp, c.primary.copy(alpha = if (dragging) 0.5f else 0.25f), Capsule())
                )

                // 图标列 + 拖拽手势
                Row(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tabCount) {
                            // PointerInputScope 自身即 Density，可直接 toPx
                            val tabWidthPx = tabWidth.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)

                                // 触摸阈值 + 方向判定：仅水平主导才接管（垂直滑动不误入拖拽）
                                var horizontal = false
                                val slopChange = awaitTouchSlopOrCancellation(down.id) { over, slop ->
                                    horizontal = kotlin.math.abs(slop.x) > kotlin.math.abs(slop.y)
                                    if (horizontal) over.consume()
                                }
                                if (slopChange == null || !horizontal) {
                                    // 点按 / 垂直滑动 / 阈值前取消：透镜偏离选中格则自愈回弹
                                    scope.launch {
                                        if (lensPos.value != currentSelected.toFloat()) {
                                            lensPos.animateTo(currentSelected.toFloat(), snapSpec)
                                        }
                                    }
                                    return@awaitEachGesture
                                }

                                // 抓取态 + 触觉
                                dragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                var lastNearest = kotlin.math.round(lensPos.value)
                                    .toInt().coerceIn(0, tabCount - 1)
                                var edgeBumped = false

                                // 绝对目标 = 锚点 + 累计位移 → 与 launch 顺序无关，零竞争
                                val anchor = lensPos.value
                                val slopDx = (slopChange.position.x - down.position.x) / tabWidthPx
                                tracker.addPosition(slopChange.uptimeMillis, slopChange.position)
                                scope.launch {
                                    // 首个 snapTo 借 Animatable 的 MutatorMutex 自动取消进行中的吸附动画。
                                    // 须经 scope.launch：drag/awaitEachGesture 是受限挂起作用域，不可直接调 snapTo。
                                    lensPos.snapTo((anchor + slopDx).coerceIn(0f, (tabCount - 1).toFloat()))
                                }

                                // 持续跟手：瞬时速度 → 透镜挤压拉伸（graphicsLayer 读取，不走重组）
                                var lastT = slopChange.uptimeMillis
                                var lastX = slopChange.position.x
                                drag(slopChange.id) { change ->
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                    val target = (anchor + (change.position.x - down.position.x) / tabWidthPx)
                                        .coerceIn(0f, (tabCount - 1).toFloat())
                                    // 经 scope.launch：受限挂起作用域内不可直接调 snapTo
                                    scope.launch { lensPos.snapTo(target) }

                                    val dt = (change.uptimeMillis - lastT).coerceAtLeast(1L)
                                    val vInst = (change.position.x - lastX) / dt * 1000f
                                    lastT = change.uptimeMillis
                                    lastX = change.position.x
                                    lensStretch = (kotlin.math.abs(vInst) / 8000f).coerceIn(0f, 0.10f)

                                    // 越格 tick + 触边 tick（每次进入只响一次）
                                    val nearest = kotlin.math.round(target).toInt().coerceIn(0, tabCount - 1)
                                    if (nearest != lastNearest) {
                                        lastNearest = nearest
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    if (!edgeBumped && (target <= 0f || target >= (tabCount - 1).toFloat())) {
                                        edgeBumped = true
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }

                                // 抬手或被取消都走到这里：任何退出路径都归位，不存在卡死
                                dragging = false
                                scope.launch {
                                    // 形变快速回弹
                                    animate(
                                        lensStretch, 0f, 0f,
                                        tween(M3_DURATION_SHORT, easing = M3_STANDARD_EASING)
                                    ) { v, _ -> lensStretch = v }
                                }
                                val vx = tracker.calculateVelocity().x
                                val cur = lensPos.value
                                // 定位规则（可预期、不跳格）：
                                //  - 默认就近取整；
                                //  - 甩动（|vx|>800px/s）且位置仍未过邻格中点时，朝甩动方向进一格
                                //    （救援"短程快滑"）；已拖过中点则信位置，绝不叠加跳格；
                                //  - 边界处方向溢出被 coerce 夹住，不会逃出首/末格。
                                val posTarget = kotlin.math.round(cur).toInt()
                                val target = if (kotlin.math.abs(vx) > 800f && posTarget == currentSelected) {
                                    (posTarget + kotlin.math.sign(vx).toInt()).coerceIn(0, tabCount - 1)
                                } else {
                                    posTarget.coerceIn(0, tabCount - 1)
                                }
                                if (target != currentSelected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // 选中格变化 → LaunchedEffect 接管吸附动画（唯一动画所有者）
                                    currentOnSelect(target)
                                } else {
                                    // 同格回弹：单一协程 animateTo，与 LaunchedEffect 不争抢
                                    scope.launch { lensPos.animateTo(target.toFloat(), snapSpec) }
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TAB_ICONS.forEachIndexed { i, icon ->
                        // 着色/缩放跟随透镜距离插值：正下方=主色+SemiBold+微放大（拖拽预览）。
                        // 色彩在 OKLab 感知均匀空间插值（M3 色彩过渡建议），消除 sRGB 中点发灰。
                        val proximity = (1f - kotlin.math.abs(i - lensPos.value)).coerceIn(0f, 1f)
                        val tint = lerpColorOklab(c.textSecondary, c.primary, proximity)
                        val scale = 0.92f + 0.08f * proximity
                        Column(
                            Modifier
                                .weight(1f)
                                .testTag(TestTags.navTab(i))
                                .clip(CircleShape)
                                // 审计：去除默认 ripple indication（其按压色=内容色=灰），它就是点击瞬间
                                // “先变灰再变橙”的异常中间灰态；以透镜形变/辉光作反馈，与全 App 无涟漪风格一致
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(i) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .size(24.dp),
                                colorFilter = ColorFilter.tint(tint)
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicText(
                                titles[i],
                                style = TextStyle(
                                    fontFamily = SfPro,
                                    color = tint,
                                    fontSize = 12.sp,
                                    fontWeight = if (proximity > 0.5f) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * MD3 navigation bar（仅 MATERIAL 模式）。
 *
 * 规范值全部来自 Material3 令牌（已对照类路径 material3 构件逐一核实）：
 * - 容器 surfaceContainer、高度 80dp（NavigationBarTokens.TallContainerHeight）
 * - 每项 = 24dp 图标 + 4dp 间距 + labelMedium 标签；内容垂直内边距 16dp
 * - 激活指示器 56×32（ActiveIndicatorWidth/Height）+ CornerFull 胶囊 + secondaryContainer，
 *   顶部距栏顶 12dp、水平居中于所在项
 * - 选中项图标/标签 = onSecondaryContainer / onSurface；未选中 = onSurfaceVariant
 *
 * **触摸反馈边界 = 激活指示器胶囊**（Material3 NavigationBarItem 的核心规则）：
 * 整格都是点击目标（≥48dp），但按压的水波纹只画在 56×32 胶囊内，绝不铺满整格 ——
 * 早前实现在整格 Column 上铺矩形状态层，真机上即表现为"方形异常"。
 *
 * 转场：指示器滑动用 EmphasizedDecelerate + DurationMedium2(300ms)；
 * 水波纹展开 DurationMedium2(300ms) / 收起 DurationShort4(200ms)（见 [PillRipple]）。
 */
@Composable
private fun Md3NavigationBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val md3 = LocalMd3Colors.current
    val titles = TAB_TITLE_RES.map { stringResource(it) }
    // 指示器滑动：唯一动画所有者 = selected 变化（MD3 EmphasizedDecelerate 300ms）
    val indicatorPos = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        indicatorPos.animateTo(
            targetValue = selected.toFloat(),
            animationSpec = tween(
                durationMillis = Md3.DurationMedium2,
                easing = Md3.EasingEmphasizedDecelerate
            )
        )
    }
    val pos by indicatorPos.asState()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(Md3.NavigationBarHeight)
            .background(md3.surfaceContainer)
    ) {
        val itemWidth = maxWidth / TAB_ICONS.size

        // ① 激活指示器：56×32 secondaryContainer 胶囊，水平居中于当前项、顶部距栏顶 12dp。
        //    作为兄弟节点画在图标之前，因此位于图标下层。
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = itemWidth * pos +
                        (itemWidth - Md3.NavigationIndicatorWidth) / 2f,
                    y = Md3.NavigationIndicatorTop
                )
                .size(Md3.NavigationIndicatorWidth, Md3.NavigationIndicatorHeight)
                .background(md3.secondaryContainer, Md3.NavigationIndicatorShape)
        )

        // ② 图标 + 标签：每项一个 Column，整格为点击目标（selectable 带 Role.Tab 语义）
        Row(Modifier.fillMaxSize()) {
            TAB_ICONS.forEachIndexed { i, icon ->
                val sel = i == selected
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag(TestTags.navTab(i))
                        .padding(top = Md3.NavigationIndicatorTop)
                        .selectable(
                            selected = sel,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(i) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ③ 触摸反馈层：与激活指示器同形同位的 56×32 胶囊，水波纹只在此范围内绘制
                    Box(
                        Modifier.size(
                            Md3.NavigationIndicatorWidth,
                            Md3.NavigationIndicatorHeight
                        )
                    ) {
                        PillRipple(
                            interactionSource = interaction,
                            color = if (sel) md3.onSecondaryContainer else md3.onSurfaceVariant,
                            originX = (itemWidth - Md3.NavigationIndicatorWidth) / 2f,
                            originY = Md3.NavigationIndicatorTop,
                            modifier = Modifier.matchParentSize()
                        )
                        Image(
                            painterResource(icon),
                            contentDescription = titles[i],
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(Md3.NavigationIconSize),
                            colorFilter = ColorFilter.tint(
                                if (sel) md3.onSecondaryContainer else md3.onSurfaceVariant
                            )
                        )
                    }
                    Spacer(Modifier.height(Md3.NavigationIndicatorToLabelPadding))
                    BasicText(
                        titles[i],
                        style = Md3.LabelMedium.copy(
                            color = if (sel) md3.onSurface else md3.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

/**
 * 导航栏项的水波纹反馈：**边界 = 激活指示器胶囊**（56×32，CornerFull）。
 *
 * 与 Material3 `NavigationBarItem` 同思路：整格可点击，但只有指示器胶囊显示反馈
 * （官方用 `MappedInteractionSource` 把整格交互重映射到指示器范围，再交给
 * `clip(ItemActiveIndicatorShape).indication(ripple())`）。此处用同等视觉的自绘实现，原因：
 * 1. 本工程 Compose 版本的 `rememberRipple` 已按 ERROR 级弃用，新的 `createRippleModifierNode`
 *    需要 material3 内部的 ripple alpha，不便直接复用；
 * 2. 项目既有决策（早期审计）：不用默认 ripple —— 默认 ripple 的按压色 = 内容色 = 灰，
 *    会产生"先变灰再变主色"的异常中间态；这里显式用 MD3 的 on 色。
 *
 * 动效（MD3 令牌）：展开 DurationMedium2(300ms) + Standard；收起 DurationShort4(200ms) +
 * StandardAccelerate；半径由 0 扩开、alpha 随扩散衰减（10% → 5%）。
 * 触点位置取自整格交互源、平移到胶囊坐标系后夹紧在胶囊内，因此按压整格任意位置都可见反馈。
 */
@Composable
private fun PillRipple(
    interactionSource: MutableInteractionSource,
    color: Color,
    originX: Dp,
    originY: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // 半径与透明度分开驱动：按下 = 扩散到全胶囊 + 淡入到 10%（MD3 状态层 pressed 不透明度）；
    // 松手 = 只淡出（200ms），扩散半径保持，避免"先亮一下再缩没"的倒退感。
    val radius = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    var pressInPill by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    // 整格坐标 → 胶囊局部坐标（减掉胶囊左上角在整格中的位置）
                    with(density) {
                        pressInPill =
                            interaction.pressPosition - Offset(originX.toPx(), originY.toPx())
                    }
                    radius.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = Md3.DurationMedium2,
                            easing = Md3.EasingStandard
                        )
                    )
                    alpha.animateTo(
                        targetValue = Md3.StatePressed,
                        animationSpec = tween(
                            durationMillis = Md3.DurationMedium2,
                            easing = Md3.EasingStandard
                        )
                    )
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    alpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = Md3.DurationShort4,
                            easing = Md3.EasingStandardAccelerate
                        )
                    )
                    radius.snapTo(0f)
                }
            }
        }
    }
    Canvas(
        // 必须裁到指示器胶囊：Canvas 默认不裁剪自身边界，扩散圆会溢出到胶囊外
        modifier.clip(Md3.NavigationIndicatorShape)
    ) {
        val r = radius.value
        val a = alpha.value
        if (r > 0f && a > 0f) {
            val center = pressInPill?.let { press ->
                // 夹紧到胶囊内：触点可能落在胶囊外（整格比胶囊大）
                Offset(press.x.coerceIn(0f, size.width), press.y.coerceIn(0f, size.height))
            } ?: Offset(size.width / 2f, size.height / 2f)
            // 半径扩到「触点至胶囊最远角」的距离，保证铺满整个胶囊
            val far = maxOf(
                kotlin.math.hypot(center.x.toDouble(), center.y.toDouble()),
                kotlin.math.hypot((size.width - center.x).toDouble(), center.y.toDouble()),
                kotlin.math.hypot(center.x.toDouble(), (size.height - center.y).toDouble()),
                kotlin.math.hypot(
                    (size.width - center.x).toDouble(),
                    (size.height - center.y).toDouble()
                )
            ).toFloat()
            drawCircle(
                color = color.copy(alpha = color.alpha * a),
                radius = far * r,
                center = center
            )
        }
    }
}
