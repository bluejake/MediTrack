package com.medilog.app.data

/**
 * CSV 编解码纯函数（无 Android / 数据库依赖，可直接 JVM 单元测试）。
 *
 * 审计 P2-4：从 MedRepository 抽出，导入/导出逻辑与原实现逐行等价，
 * 行为不变；MedRepository 只负责 DB 读写，本对象负责文本格式。
 */
object CsvCodec {

    /** 导出版本头前缀（后接版本号） */
    const val VERSION_LINE_PREFIX = "# MediTrack CSV "

    /** 表头行 */
    const val HEADER = "日期,时间,药物,剂型,剂量/规格,计划时间,状态"

    /** 状态列取值（数据契约：跨版本/跨语言导入匹配依赖这两个字面量） */
    const val STATUS_TAKEN = "已服用"
    const val STATUS_SKIPPED = "已跳过"

    private val DATE_RE = Regex("\\d{4}-\\d{2}-\\d{2}")

    /** 去掉 UTF-8 BOM */
    fun stripBom(text: String): String =
        if (text.startsWith("\uFEFF")) text.substring(1) else text

    /** CSV 字段转义：含逗号/引号/换行时整体加双引号，内部引号翻倍 */
    fun quote(s: String?): String {
        if (s == null) return ""
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }

    /**
     * 生成一条数据行（列序与 [HEADER] 一致）。
     * 注意：导出格式中「时间」与「计划时间」两列都写 planTime，与既有导出文件兼容。
     */
    fun dataRow(
        planDate: String?,
        planTime: String?,
        medName: String?,
        form: String?,
        strength: String?,
        taken: Boolean
    ): String = buildString {
        append(quote(planDate)).append(',')
        append(quote(planTime ?: "")).append(',')
        append(quote(medName)).append(',')
        append(quote(form)).append(',')
        append(quote(strength)).append(',')
        append(quote(planTime ?: "")).append(',')
        append(if (taken) STATUS_TAKEN else STATUS_SKIPPED)
    }

    /** 是否为数据行（跳过空行 / 版本注释行 / 表头行） */
    fun isDataLine(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (t.startsWith("#")) return false
        if (t.startsWith("日期")) return false
        return true
    }

    /** 日期列合法性（yyyy-MM-dd） */
    fun isValidDate(date: String): Boolean = DATE_RE.matches(date)

    /** 解析一行 CSV，支持引号包裹与双引号转义 */
    fun parseLine(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cur.append(ch)
                }
            } else if (ch == '"') {
                inQuotes = true
            } else if (ch == ',') {
                out.add(cur.toString())
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
