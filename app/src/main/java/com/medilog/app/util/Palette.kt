package com.medilog.app.util

import android.content.Context
import com.medilog.app.R

/**
 * 药物图标与配色。
 *
 * 审计 P1-2：名称类数据（颜色名 / 剂型名 / 图标名）全部改为读取 string-array 资源，
 * 跟随系统 locale；仅保留与展示无关的色值常量在本对象内。
 */
object Palette {
    /** 药丸底色（iOS 系统色八色） */
    val COLORS = intArrayOf(
        0xFF007AFF.toInt(), // 蓝 systemBlue
        0xFFAF52DE.toInt(), // 紫 systemPurple
        0xFF30B0C7.toInt(), // 青 systemTeal
        0xFF34C759.toInt(), // 绿 systemGreen
        0xFFFFCC00.toInt(), // 黄 systemYellow
        0xFFFF9500.toInt(), // 橙 systemOrange
        0xFFFF3B30.toInt(), // 红 systemRed
        0xFFFF2D55.toInt()  // 粉 systemPink
    )

    /** 颜色名（locale 感知） */
    fun colorNames(ctx: Context): Array<String> = ctx.resources.getStringArray(R.array.color_names)

    fun colorName(ctx: Context, index: Int): String {
        val names = colorNames(ctx)
        return names[if (index in names.indices) index else 0]
    }

    /** 剂型选项（locale 感知） */
    fun forms(ctx: Context): Array<String> = ctx.resources.getStringArray(R.array.form_names)

    /** 图标索引对应的 vector 资源 id */
    fun iconRes(icon: Int): Int = when (icon) {
        1 -> R.drawable.ic_capsule
        2 -> R.drawable.ic_bottle
        3 -> R.drawable.ic_drops
        4 -> R.drawable.ic_injection
        5 -> R.drawable.ic_inhaler
        else -> R.drawable.ic_pill
    }

    /** 图标名（locale 感知） */
    fun iconName(ctx: Context, icon: Int): String {
        val names = ctx.resources.getStringArray(R.array.icon_names)
        return if (icon in names.indices) names[icon] else names[0]
    }
}
