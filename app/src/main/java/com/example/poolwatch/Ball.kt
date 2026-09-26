package com.example.poolwatch

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color

/**
 * Lightweight ball for 2D pool. Pure math, no allocations in hot path.
 */
class Ball(
    var x: Float,
    var y: Float,
    val radius: Float,
    val isCue: Boolean = false
) {
    var vx: Float = 0f
    var vy: Float = 0f
    var active: Boolean = true   // false when pocketed

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (isCue) Color.WHITE else Color.BLACK
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = if (isCue) Color.LTGRAY else Color.DKGRAY
    }

    fun update(friction: Float, dt: Float) {
        if (!active) return
        x += vx * dt
        y += vy * dt
        // simple linear friction
        vx *= friction
        vy *= friction
        // stop tiny movements to save CPU
        if (vx * vx + vy * vy < 0.5f) {
            vx = 0f
            vy = 0f
        }
    }

    fun isMoving(): Boolean = active && (vx * vx + vy * vy > 0.5f)

    fun draw(canvas: Canvas) {
        if (!active) return
        canvas.drawCircle(x, y, radius, paint)
        canvas.drawCircle(x, y, radius, stroke)
    }

    fun speed(): Float = kotlin.math.sqrt(vx * vx + vy * vy)

    fun setVelocity(angleRad: Float, power: Float) {
        vx = kotlin.math.cos(angleRad) * power
        vy = kotlin.math.sin(angleRad) * power
    }
}
