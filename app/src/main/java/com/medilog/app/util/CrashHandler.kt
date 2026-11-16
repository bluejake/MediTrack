package com.medilog.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medilog.app.ui.CrashActivity
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全局崩溃捕获：闪退时把堆栈写入文件并展示，便于定位兼容性问题 */
class CrashHandler(
    private val ctx: Context,
    private val systemHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"

        fun install(ctx: Context) {
            val def = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(ctx, def))
        }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stack = sw.toString()

            val sb = StringBuilder()
            sb.append("=== 服药记录 崩溃日志 ===\n")
            sb.append("时间: ")
                .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
            sb.append("设备: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            sb.append("系统: Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            sb.append("线程: ").append(t.name).append("\n\n")
            sb.append(stack)

            Log.e(TAG, sb.toString())

            // 写入文件
            var dir = ctx.getExternalFilesDir(null)
            if (dir == null) dir = ctx.filesDir
            val f = File(dir, "crash-" + System.currentTimeMillis() + ".txt")
            try {
                FileWriter(f).use { w -> w.write(sb.toString()) }
            } catch (ignore: Exception) {
                Log.e(TAG, "崩溃日志写文件失败", ignore)
            }

            // 展示崩溃信息
            val i = Intent(ctx, CrashActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            i.putExtra(CrashActivity.EXTRA_TEXT, sb.toString())
            i.putExtra(CrashActivity.EXTRA_PATH, f.absolutePath)
            ctx.startActivity(i)
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        } catch (ignore: Throwable) {
            // 崩溃处理器自身不能再抛：兜底交还系统默认处理器
            Log.e(TAG, "uncaughtException 处理器内部失败", ignore)
            systemHandler?.uncaughtException(t, e)
        }
    }
}
