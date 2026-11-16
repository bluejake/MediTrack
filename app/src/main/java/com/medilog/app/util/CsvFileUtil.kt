package com.medilog.app.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.medilog.app.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * 数据备份文件工具：写入公共 Download 目录下的备份文件夹（名称跟随 locale）。
 *
 *  - API 29+：MediaStore.Downloads（无需存储权限，卸载应用后文件仍保留）
 *  - API 26–28：直接写 /storage/emulated/0/Download/<备份文件夹>/（需 WRITE_EXTERNAL_STORAGE）
 */
object CsvFileUtil {

    /** 公共下载目录下的备份文件夹名（locale 感知，替代原硬编码中文名） */
    fun backupDir(ctx: Context): String = ctx.getString(R.string.backup_dir_name)

    /**
     * 把文本内容写入 Download/<备份文件夹>/<fileName>。
     *
     * @return 面向用户展示的路径（如 Download/服药记录备份/服药记录_2026-10-21.csv）
     */
    @Throws(IOException::class)
    fun exportToPublicDownload(ctx: Context, fileName: String, content: String): String {
        val backupDir = backupDir(ctx)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues()
            cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            cv.put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            cv.put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + File.separator + backupDir
            )
            val r: ContentResolver = ctx.contentResolver
            val uri: Uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IOException("无法在下载目录创建文件")
            val os: OutputStream = r.openOutputStream(uri) ?: throw IOException("无法打开输出流")
            os.use { it.write(content.toByteArray(StandardCharsets.UTF_8)) }
            "${Environment.DIRECTORY_DOWNLOADS}/$backupDir/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                backupDir
            )
            if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建备份目录")
            val f = File(dir, fileName)
            OutputStreamWriter(FileOutputStream(f), StandardCharsets.UTF_8).use { w -> w.write(content) }
            f.absolutePath
        }
    }
}
