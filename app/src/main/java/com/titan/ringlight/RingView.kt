package com.titan.ringlight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

class RingView(ctx: Context) : View(ctx) {

    var colors: List<Int> = emptyList()
        set(v) { field = v; kick() }
    var radius = 40f
    var thickness = 8f
    var pulse = true
    var segments = false
    var maxAlpha = 1f

    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val oval = RectF()
    private val start = SystemClock.uptimeMillis()

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (animating()) postDelayed(this, 33) // ~30 fps, бережём батарею
        }
    }

    private fun animating() = isAttachedToWindow && visibility == VISIBLE && colors.isNotEmpty() &&
        (pulse || (!segments && colors.size > 1))

    fun kick() {
        removeCallbacks(tick)
        invalidate()
        if (animating()) postDelayed(tick, 33)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); kick() }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val c = colors
        if (c.isEmpty()) return
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        // период 2 с, минимум яркости в момент смены цвета
        val a = if (pulse) 0.3f + 0.7f * (0.5f - 0.5f * cos(t * PI).toFloat()) else 1f
        val cx = width / 2f
        val cy = height / 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        core.strokeWidth = thickness
        halo.strokeWidth = thickness * 2.6f

        if (segments && c.size > 1) {
            val sweep = 360f / c.size
            val gap = min(6f, sweep * 0.15f)
            c.forEachIndexed { i, col ->
                paint(col, a)
                val s = -90f + i * sweep + gap / 2
                canvas.drawArc(oval, s, sweep - gap, false, halo)
                canvas.drawArc(oval, s, sweep - gap, false, core)
            }
        } else {
            val idx = if (c.size > 1) (t / 2f).toInt() % c.size else 0
            paint(c[idx], a)
            canvas.drawCircle(cx, cy, radius, halo)
            canvas.drawCircle(cx, cy, radius, core)
        }
    }

    private fun paint(col: Int, a: Float) {
        val base = (a * maxAlpha * 255).toInt().coerceIn(0, 255)
        core.color = col; core.alpha = base
        halo.color = col; halo.alpha = (base * 0.3f).toInt()
    }
}
