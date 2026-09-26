package com.example.poolwatch

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

/**
 * Ultra-lightweight 2D pool for Wear OS / Oppo Watch 2.
 * - Pure Canvas drawing, no bitmaps, no external physics lib
 * - 30 FPS target, early exit when all balls stopped
 * - Minimalist black/white theme
 * - Touch: drag from cue ball to set angle + power
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var running = false
    private val holder: SurfaceHolder = getHolder()

    // Game state
    private val balls = ArrayList<Ball>(8)
    private var cueBall: Ball? = null
    private var tableLeft = 0f
    private var tableTop = 0f
    private var tableRight = 0f
    private var tableBottom = 0f
    private var ballRadius = 12f
    private val friction = 0.985f
    private val restitution = 0.92f   // bounce energy keep
    private val pockets = ArrayList<PointF>(6)

    // Aiming
    private var aiming = false
    private var aimAngle = 0f
    private var aimPower = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val maxPower = 420f

    // Paints (reused)
    private val bgPaint = Paint().apply { color = Color.rgb(20, 20, 20) }
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(180, 180, 180)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val aimLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val powerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var score = 0
    private var message = "拖动白球瞄准击球"
    private var lastFrameTime = 0L

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        setupTable()
        resetBalls()
        running = true
        thread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        setupTable()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            thread?.join(300)
        } catch (_: InterruptedException) {}
        thread = null
    }

    private fun setupTable() {
        val w = width.toFloat()
        val h = height.toFloat()
        // leave small margin for watch bezel / round screen
        val margin = min(w, h) * 0.06f
        tableLeft = margin
        tableTop = margin
        tableRight = w - margin
        tableBottom = h - margin
        ballRadius = min(w, h) * 0.035f

        pockets.clear()
        val pr = ballRadius * 1.8f
        // 4 corners + 2 mid sides (simple)
        pockets.add(PointF(tableLeft, tableTop))
        pockets.add(PointF(tableRight, tableTop))
        pockets.add(PointF(tableLeft, tableBottom))
        pockets.add(PointF(tableRight, tableBottom))
        pockets.add(PointF((tableLeft + tableRight) / 2, tableTop))
        pockets.add(PointF((tableLeft + tableRight) / 2, tableBottom))
    }

    private fun resetBalls() {
        balls.clear()
        val cx = (tableLeft + tableRight) / 2
        val cy = (tableTop + tableBottom) / 2
        val r = ballRadius

        // Cue ball (white) on left
        cueBall = Ball(tableLeft + (tableRight - tableLeft) * 0.28f, cy, r, isCue = true)
        balls.add(cueBall!!)

        // Black object balls – simple triangle / scattered for small screen
        val positions = listOf(
            PointF(cx + r * 2.2f, cy),
            PointF(cx + r * 4.0f, cy - r * 1.2f),
            PointF(cx + r * 4.0f, cy + r * 1.2f),
            PointF(cx + r * 5.8f, cy),
            PointF(cx + r * 5.8f, cy - r * 2.4f),
            PointF(cx + r * 5.8f, cy + r * 2.4f)
        )
        for (p in positions) {
            balls.add(Ball(p.x, p.y, r, isCue = false))
        }
        score = 0
        message = "拖动白球瞄准"
        aiming = false
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val dt = if (lastFrameTime == 0L) 0.016f
                     else ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.008f, 0.033f)
            lastFrameTime = now

            val anyMoving = updatePhysics(dt)
            drawFrame()

            // Power saving: longer sleep when idle
            val sleepMs = if (anyMoving || aiming) 16L else 40L
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun updatePhysics(dt: Float): Boolean {
        var moving = false
        // Update positions
        for (b in balls) {
            b.update(friction, dt * 60f) // scale to ~60Hz feel
            if (b.isMoving()) moving = true
        }

        // Wall collisions
        for (b in balls) {
            if (!b.active) continue
            if (b.x - b.radius < tableLeft) {
                b.x = tableLeft + b.radius
                b.vx = -b.vx * restitution
            } else if (b.x + b.radius > tableRight) {
                b.x = tableRight - b.radius
                b.vx = -b.vx * restitution
            }
            if (b.y - b.radius < tableTop) {
                b.y = tableTop + b.radius
                b.vy = -b.vy * restitution
            } else if (b.y + b.radius > tableBottom) {
                b.y = tableBottom - b.radius
                b.vy = -b.vy * restitution
            }
        }

        // Ball-ball collisions (simple elastic)
        for (i in balls.indices) {
            val a = balls[i]
            if (!a.active) continue
            for (j in i + 1 until balls.size) {
                val b = balls[j]
                if (!b.active) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = sqrt(dx * dx + dy * dy)
                val minDist = a.radius + b.radius
                if (dist < minDist && dist > 0.001f) {
                    // Separate
                    val overlap = minDist - dist
                    val nx = dx / dist
                    val ny = dy / dist
                    a.x -= nx * overlap * 0.5f
                    a.y -= ny * overlap * 0.5f
                    b.x += nx * overlap * 0.5f
                    b.y += ny * overlap * 0.5f
                    // Velocity exchange along normal
                    val dvx = a.vx - b.vx
                    val dvy = a.vy - b.vy
                    val dvn = dvx * nx + dvy * ny
                    if (dvn > 0) continue // already separating
                    val impulse = -(1f + restitution) * dvn / 2f
                    a.vx += impulse * nx
                    a.vy += impulse * ny
                    b.vx -= impulse * nx
                    b.vy -= impulse * ny
                    moving = true
                }
            }
        }

        // Pocket check
        val pocketR = ballRadius * 1.6f
        for (b in balls) {
            if (!b.active) continue
            for (p in pockets) {
                val dx = b.x - p.x
                val dy = b.y - p.y
                if (dx * dx + dy * dy < pocketR * pocketR) {
                    b.active = false
                    b.vx = 0f
                    b.vy = 0f
                    if (!b.isCue) {
                        score++
                        message = "进球! $score"
                    } else {
                        message = "白球落袋，重置"
                        // simple respawn cue after short delay handled next frame
                    }
                    break
                }
            }
        }

        // Respawn cue if pocketed and everything stopped
        if (cueBall?.active == false && !moving) {
            cueBall?.apply {
                active = true
                x = tableLeft + (tableRight - tableLeft) * 0.28f
                y = (tableTop + tableBottom) / 2
                vx = 0f
                vy = 0f
            }
            message = "白球复位"
        }

        // Win condition
        val remaining = balls.count { it.active && !it.isCue }
        if (remaining == 0 && !moving) {
            message = "通关! 再来一局"
            // auto reset after a moment could be added
        }

        return moving
    }

    private fun drawFrame() {
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas == null) return
            // Background
            canvas.drawColor(Color.rgb(15, 15, 15))
            // Table area (flat)
            canvas.drawRect(tableLeft, tableTop, tableRight, tableBottom, bgPaint)
            // Rails
            canvas.drawRect(tableLeft, tableTop, tableRight, tableBottom, railPaint)
            // Pockets
            val pr = ballRadius * 1.7f
            for (p in pockets) {
                canvas.drawCircle(p.x, p.y, pr, pocketPaint)
            }
            // Balls
            for (b in balls) b.draw(canvas)
            // Aiming guide
            if (aiming && cueBall != null && cueBall!!.active) {
                val c = cueBall!!
                val len = 40f + aimPower * 0.25f
                val ex = c.x + cos(aimAngle) * len
                val ey = c.y + sin(aimAngle) * len
                canvas.drawLine(c.x, c.y, ex, ey, aimLinePaint)
                // power arc near finger
                canvas.drawCircle(c.x, c.y, 8f + aimPower * 0.04f, powerPaint)
            }
            // HUD
            canvas.drawText(message, width / 2f, tableTop - 8f, textPaint)
            canvas.drawText("得分 $score", width / 2f, tableBottom + 24f, textPaint)
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = cueBall ?: return true
        if (!c.active) return true
        // only aim when all stopped
        val anyMoving = balls.any { it.isMoving() }
        if (anyMoving) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx = event.x - c.x
                val dy = event.y - c.y
                if (dx * dx + dy * dy < (c.radius * 3f) * (c.radius * 3f)) {
                    aiming = true
                    touchStartX = event.x
                    touchStartY = event.y
                    aimPower = 0f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (aiming) {
                    val dx = c.x - event.x   // opposite direction = hit direction
                    val dy = c.y - event.y
                    aimAngle = atan2(dy, dx)
                    val dist = sqrt(dx * dx + dy * dy)
                    aimPower = (dist * 2.2f).coerceIn(0f, maxPower)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (aiming && aimPower > 15f) {
                    c.setVelocity(aimAngle, aimPower)
                    message = "击球!"
                }
                aiming = false
                aimPower = 0f
            }
        }
        return true
    }

    fun restart() {
        resetBalls()
    }
}
