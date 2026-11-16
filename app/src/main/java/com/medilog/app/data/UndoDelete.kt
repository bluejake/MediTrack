package com.medilog.app.data

import android.content.Context
import com.medilog.app.model.MedSnapshot
import com.medilog.app.reminder.rescheduleAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「删除后 5 秒内可撤销」（建议 5）。
 *
 * 目标用户是不熟悉手机操作的老年人——**误删必须能救回来**。
 *
 * 模型：**先快照 → 立即硬删 → 窗口内可原样还原**。
 *  - 立即硬删 ⇒ 「药品/计划/记录同步消失、不留残行」这条硬约束完全不受撤销功能影响；
 *  - 还原按**原始 id**插回（见 [MedSnapshot] 说明），提醒闹钟随后全量重排；
 *  - 窗口到期 / 用户切走 / 再次删除 ⇒ 快照丢弃，状态自洽（数据已删干净）。
 *
 * 这里只管状态机与计时，UI 由 MainActivity 的撤销条订阅 [pending] 渲染。
 */
object DeleteUndo {

    /** 撤销窗口：5 秒（MD3 Snackbar 的长时档位）。 */
    const val WINDOW_MS = 5_000L

    /** 一次待撤销的删除。 */
    data class Pending(val name: String, val snapshot: MedSnapshot)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private var timer: Job? = null

    /** 删除成功后调用：登记快照并启动窗口倒计时。 */
    fun offer(name: String, snapshot: MedSnapshot) {
        timer?.cancel()
        _pending.value = Pending(name, snapshot)
        timer = scope.launch {
            delay(WINDOW_MS)
            discard()
        }
    }

    /** 丢弃快照（窗口到期 / 离开页面 / 已被新的删除取代）。 */
    fun discard() {
        timer?.cancel()
        timer = null
        _pending.value = null
    }

    /** 当前是否有可撤销的删除。 */
    fun hasPending(): Boolean = _pending.value != null

    /**
     * 撤销：按快照原样还原，然后刷新 UI 并重排提醒。
     * @return true 表示确实还原成功
     */
    fun undo(ctx: Context, repo: MedRepository): Boolean {
        val p = _pending.value ?: return false
        val ok = repo.restore(p.snapshot)
        discard()
        if (ok) {
            DataBus.bump()
            rescheduleAsync(ctx)
        }
        return ok
    }
}
