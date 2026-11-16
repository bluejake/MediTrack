package com.medilog.app.ui

/**
 * 覆盖层栈（详情页 / 编辑页）的出栈操作。
 *
 * **为什么单独抽成纯函数**：历史上这里写的是 `stack.removeAt(stack.lastIndex)`，
 * 删除药品时会被调用两次（第一次来自删除确认回调，第二次来自 dataVersion 变化后
 * 详情页在退出动画期间的重组兜底），第二次调用时栈已空 → `removeAt(-1)` →
 * `java.lang.IndexOutOfBoundsException: index: -1, size: 0` → 进程被杀。
 *
 * 抽成纯函数后有两点收益：
 *  1. 出栈**幂等**，重复调用不会崩；
 *  2. 不依赖 Android / Compose，可以直接写 JVM 单测锁死这个回归（见 NavStackTest）。
 *
 * @return true 表示确实弹出了一层；false 表示栈本来就是空的（静默忽略）
 */
fun <T> MutableList<T>.popOverlaySafe(): Boolean {
    if (isEmpty()) return false
    removeAt(lastIndex)
    return true
}
