package com.medilog.app.reminder

import android.content.Context
import android.content.SharedPreferences

/**
 * 弹窗去重记录。
 *
 * 只做一件事：记住"这个提醒刚刚已经弹过"，避免进程重启 / 重复广播导致同一条提醒弹两次。
 * （早期为"锁屏挂起、解锁再弹"实现过 save/load/clear 的待弹队列，
 *   后来需求改为锁屏直接覆盖弹出，那套逻辑已废弃并删除。）
 */
object PendingPopup {
    private const val FILE = "medilog_pending_popup"
    private const val K_SHOWN_KEY = "shown_key"
    private const val K_SHOWN_AT = "shown_at"

    /** 去重有效期：同一个计划同一天只弹一次 */
    private const val SHOWN_TTL_MS = 12 * 60 * 60 * 1000L

    data class Payload(
        val medId: Long,
        val schedId: Long,
        val name: String,
        val strength: String,
        val planTime: String,
        val planDate: String?,
        val isTest: Boolean,
        val at: Long
    ) {
        val key: String get() = "$isTest:$medId:$schedId:$planDate:$planTime"
    }

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 记为「已弹过」 */
    fun markShown(ctx: Context, key: String) {
        sp(ctx).edit()
            .putString(K_SHOWN_KEY, key)
            .putLong(K_SHOWN_AT, System.currentTimeMillis())
            .apply()
    }

    /** 该 key 是否已在去重窗口内弹过 */
    fun alreadyShown(ctx: Context, key: String): Boolean {
        val p = sp(ctx)
        if (p.getString(K_SHOWN_KEY, null) != key) return false
        val at = p.getLong(K_SHOWN_AT, 0L)
        return System.currentTimeMillis() - at <= SHOWN_TTL_MS
    }
}
