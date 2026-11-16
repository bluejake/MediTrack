package com.medilog.app.data

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级「数据版本号」：任何一处写入数据库后调用 [bump]，
 * 界面侧通过 [LocalDataVersion] 读取并把它作为 `remember` 的 key，
 * 从而在**通知栏「记录」/ 全屏提醒页记录 / 应用内记录 / CSV 导入 / 撤销**之后立即刷新界面。
 *
 * 为什么需要它：
 * 原有界面刷新依赖每个页面自己的局部 `refresh` 计数与 MainActivity 的 `dataVersion`，
 * 二者都只被「应用内的点击回调」推动。通知栏动作由 ActionReceiver 直接写库，
 * 界面无感知 → 用户返回应用看到旧状态（必须切到别的页面再回来才刷新）。
 *
 * 生效范围：本应用全部组件同进程（崩溃页是独立 :crash 进程，不需要刷新）。
 * 零配置：不需要用户任何操作，写入即广播。
 */
object DataBus {

    private val _version = MutableStateFlow(0)

    /** 单调递增的数据版本；界面 collect 后用于驱动重读 */
    val version = _version.asStateFlow()

    /** 写入数据库后调用（幂等、线程安全） */
    fun bump() {
        _version.value += 1
    }
}

/**
 * 当前数据版本（由 [com.medilog.app.ui.MainActivity] 的 MediApp 提供）。
 * 页面把 `LocalDataVersion.current` 放进 `remember(...)` 的 key 即可自动跟随外部写入。
 */
val LocalDataVersion = staticCompositionLocalOf { 0 }
