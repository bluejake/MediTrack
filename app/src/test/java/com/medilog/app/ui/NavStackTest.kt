package com.medilog.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 出栈幂等性的回归测试 —— 直接锁死「删除药品必崩」这个事故的根因。
 *
 * 事故回放：删除药品 → onConfirm 出栈一次 → DataBus.bump() → 详情页在退出动画期间
 * 再次重组 → 药品已不存在 → null 兜底第二次回调 onDeleted() → 旧实现
 * `stack.removeAt(stack.lastIndex)` 在空栈上取 lastIndex == -1 → IndexOutOfBoundsException。
 */
class NavStackTest {

    @Test
    fun popOnEmptyStack_isNoOp_notException() {
        val stack = mutableListOf<String>()
        // 崩溃版本在这里会抛 IndexOutOfBoundsException: index: -1, size: 0
        assertFalse(stack.popOverlaySafe())
        assertEquals(0, stack.size)
    }

    @Test
    fun popRepeatedly_isIdempotent() {
        val stack = mutableListOf("Detail")
        assertTrue(stack.popOverlaySafe())
        assertFalse(stack.popOverlaySafe())   // 第二次 = 删除流程里的重复回调
        assertFalse(stack.popOverlaySafe())
        assertEquals(0, stack.size)
    }

    @Test
    fun popRemovesOnlyLastItem() {
        val stack = mutableListOf("Today", "Detail", "AddEdit")
        assertTrue(stack.popOverlaySafe())
        assertEquals(listOf("Today", "Detail"), stack)
        assertTrue(stack.popOverlaySafe())
        assertEquals(listOf("Today"), stack)
    }

    @Test
    fun popFullStackThenMore_neverThrows() {
        val stack = mutableListOf("a", "b", "c")
        repeat(10) { stack.popOverlaySafe() }
        assertEquals(0, stack.size)
    }
}
