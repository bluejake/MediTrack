package com.medilog.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.annotation.DrawableRes

/**
 * 把 Android 原生 Drawable（含 layer-list / shape 等非 VectorDrawable 资源）
 * 包装成 Compose Painter。
 *
 * 背景：Compose 的 painterResource() 仅支持 VectorDrawable 与位图，
 * 而环境光晕 bg_ambient 是 layer-list + 径向渐变 shape，用 painterResource 会抛
 * IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported。
 */
class DrawablePainter(private val drawable: Drawable) : Painter() {

    override val intrinsicSize = androidx.compose.ui.geometry.Size(
        drawable.intrinsicWidth.coerceAtLeast(0).toFloat(),
        drawable.intrinsicHeight.coerceAtLeast(0).toFloat()
    )

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

/** 记忆化地获取一个 Drawable 资源的 Painter（支持 layer-list）。 */
@Composable
fun rememberDrawablePainter(@DrawableRes resId: Int): DrawablePainter {
    val ctx = LocalContext.current
    return remember(resId, ctx) {
        val d = ContextCompat.getDrawable(ctx, resId)
            ?: throw IllegalStateException("Drawable 资源不存在: $resId")
        DrawablePainter(d)
    }
}
