package com.medilog.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [DataBus] 语义测试（纯 JVM，只用 junit + coroutines-core）。
 *
 * 锁死三条不变量，它们是「通知栏记录后界面立即刷新」的地基：
 * 1. bump 单调递增 —— 界面按版本号做 remember key，必须每次都不同才会重读；
 * 2. 并发 bump 不丢号 —— 通知动作与界面操作可能几乎同时发生；
 * 3. 收集者能收到最新值 —— StateFlow 热流、有初值（收集瞬间先拿到当前值）。
 */
class DataBusTest {

    @Test
    fun bump_isMonotonic() {
        val before = DataBus.version.value
        DataBus.bump()
        assertEquals(before + 1, DataBus.version.value)
    }

    @Test
    fun bump_isDistinctAcrossCalls() {
        val v1 = DataBus.version.value
        DataBus.bump()
        val v2 = DataBus.version.value
        DataBus.bump()
        val v3 = DataBus.version.value
        assertNotEquals(v1, v2)
        assertNotEquals(v2, v3)
    }

    @Test
    fun concurrentBumps_doNotLoseUpdates() = runBlocking {
        val start = DataBus.version.value
        val jobs = (1..50).map { launch { DataBus.bump() } }
        jobs.forEach { it.join() }
        assertEquals(start + 50, DataBus.version.value)
    }

    @Test
    fun collectorReceivesLatestValue() = runBlocking {
        val seen = mutableListOf<Int>()
        val job = launch { DataBus.version.collect { seen.add(it) } }
        delay(50)                       // 等收集者订阅完成（StateFlow 有初值，先收到当前值）
        val current = DataBus.version.value
        DataBus.bump()
        DataBus.bump()
        delay(50)                       // 等新值送达
        job.cancel()
        assertEquals(current, seen.first())
        assertEquals(current + 2, seen.last())
    }
}
