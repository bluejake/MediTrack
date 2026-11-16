package com.medilog.app.reminder

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 提醒重排的**唯一异步入口**（建议 4）。
 *
 * 为什么需要它：
 *   `scheduleAll()` 内含 DB 查询 + AlarmManager IPC，必须在后台线程跑；
 *   而且它做的是「先 cancelAll 再逐个 set」的**两段式**操作——
 *   如果被中途取消，就会停在"旧的清了、新的没设"的半程状态 = 漏提醒。
 *   因此这里用 `Dispatchers.IO + NonCancellable`，保证一旦开始就跑完。
 *
 * 之前只有「新增/编辑保存」和「设置页」走了这条路径；**删除没有** ——
 * 已删除药品的闹钟靠 `cancelForMed` 精准摘除，但缺少全量重排这层保险。
 * 现在删除 / 撤销两条路径都跑一次全量重排，任何残留闹钟都会被清掉。
 */
private val reschedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun rescheduleAsync(ctx: Context) {
    reschedScope.launch(NonCancellable) {
        runCatching { ReminderScheduler.scheduleAll(ctx.applicationContext) }
    }
}
