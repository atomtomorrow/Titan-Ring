package com.titan.ringlight

import android.content.Context
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import kotlin.math.max
import kotlin.math.min

/** Ищет вырез фронтальной камеры и возвращает центр и радиус (в пикселях экрана). */
object Geometry {
    data class Ring(val cx: Float, val cy: Float, val r: Float, val auto: Boolean)

    fun cutout(ctx: Context): Ring {
        val d = ctx.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val density = ctx.resources.displayMetrics.density
        val cut = d?.cutout
        var bounds: RectF? = null
        if (cut != null) {
            if (Build.VERSION.SDK_INT >= 31) {
                cut.cutoutPath?.let { p ->
                    val r = RectF(); p.computeBounds(r, true)
                    if (!r.isEmpty) bounds = r
                }
            }
            if (bounds == null) cut.boundingRects.firstOrNull()?.let { bounds = RectF(it) }
        }
        val b = bounds
        val base = if (b != null) {
            Ring(b.centerX(), b.centerY(), min(b.width(), b.height()) / 2f, true)
        } else {
            Ring(ctx.resources.displayMetrics.widthPixels / 2f, 24 * density, 12 * density, false)
        }
        return base.copy(
            cx = base.cx + Prefs.int(ctx, Prefs.K_DX, 0),
            cy = base.cy + Prefs.int(ctx, Prefs.K_DY, 0),
            r = max(2f, base.r + Prefs.int(ctx, Prefs.K_DR, 0))
        )
    }
}
