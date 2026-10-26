package com.example.turboboost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Частицы поверх экрана. НЕ перехватывают касания.
 * Центр экрана (зона прицела) всегда очищен — частицы туда не залетают.
 */
class SnowView(context: Context) : View(context), Choreographer.FrameCallback {

    private data class Flake(
        var x: Float, var y: Float, var radius: Float,
        var speed: Float, var drift: Float, var alpha: Int, var wobble: Float,
        var rot: Float = 0f, var spin: Float = 0f
    )

    private val flakes = ArrayList<Flake>(120)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rand = Random(System.currentTimeMillis())
    private var time = 0f
    private var lastFrameNs = 0L
    private var running = false
    private val choreographer = Choreographer.getInstance()

    /** Радиус «чистой» зоны вокруг центра (доля от min(w,h)). */
    var clearCenterRatio = 0.28f

    /** 0=снег 1=дождь 2=искры 3=листья */
    var mode = 0
        set(v) { field = v.coerceIn(0, 3); rebuild() }

    var density = 25
        set(v) { field = v.coerceIn(0, 100); rebuild() }
    var speedStep = 2
        set(v) { field = v.coerceIn(1, 5) }
    var sizeStep = 2
        set(v) { field = v.coerceIn(1, 5); rebuild() }

    init {
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun applyFrom(prefs: AppPrefs) {
        density = prefs.getSnowDensity().coerceIn(0, 100)
        speedStep = prefs.getSnowSpeed()
        sizeStep = prefs.getSnowSize()
        mode = prefs.getParticleMode()
        clearCenterRatio = prefs.getParticleClearCenter() / 100f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnim()
    }

    override fun onDetachedFromWindow() {
        stopAnim()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startAnim() else stopAnim()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun startAnim() {
        if (running) return
        running = true
        lastFrameNs = 0L
        choreographer.postFrameCallback(this)
    }

    private fun stopAnim() {
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || !isAttachedToWindow || visibility != VISIBLE) {
            running = false
            return
        }
        val dt = if (lastFrameNs == 0L) 0.016f
        else ((frameTimeNanos - lastFrameNs) / 1_000_000_000f).coerceIn(0.008f, 0.05f)
        lastFrameNs = frameTimeNanos
        time += dt
        step(dt)
        invalidate()
        choreographer.postFrameCallback(this)
    }

    private fun clearRadius(): Float {
        if (width <= 0 || height <= 0) return 0f
        return minOf(width, height) * clearCenterRatio.coerceIn(0.1f, 0.45f)
    }

    private fun inClearZone(x: Float, y: Float): Boolean {
        val cr = clearRadius()
        if (cr <= 0f) return false
        return hypot(x - width / 2f, y - height / 2f) < cr
    }

    private fun rebuild() {
        if (width <= 0 || height <= 0) return
        flakes.clear()
        if (density <= 0) return
        var guard = 0
        while (flakes.size < density && guard < density * 8) {
            guard++
            val f = spawn(rand.nextFloat() * height)
            if (!inClearZone(f.x, f.y)) flakes.add(f)
        }
    }

    private fun spawn(y: Float = -20f): Flake {
        val baseR = when (mode) {
            1 -> (rand.nextFloat() * 1.2f + 0.6f) * (sizeStep / 2.5f)
            2 -> (rand.nextFloat() * 1.8f + 0.8f) * (sizeStep / 2.2f)
            3 -> (rand.nextFloat() * 2.5f + 1.2f) * (sizeStep / 2f)
            else -> (rand.nextFloat() * 2.2f + 1f) * (sizeStep / 2f)
        }
        var x = rand.nextFloat() * width.coerceAtLeast(1)
        var yy = y
        var tries = 0
        while (inClearZone(x, yy) && tries < 12) {
            x = rand.nextFloat() * width.coerceAtLeast(1)
            if (yy > height * 0.3f && yy < height * 0.7f) {
                yy = if (rand.nextBoolean()) rand.nextFloat() * height * 0.25f
                else height * 0.75f + rand.nextFloat() * height * 0.25f
            }
            tries++
        }
        return Flake(
            x = x, y = yy, radius = baseR,
            speed = when (mode) {
                1 -> rand.nextFloat() * 4f + 3f
                2 -> rand.nextFloat() * 2.5f + 1.5f
                3 -> rand.nextFloat() * 1.2f + 0.5f
                else -> rand.nextFloat() * 1.5f + 0.6f
            },
            drift = when (mode) {
                1 -> rand.nextFloat() * 0.4f - 0.2f
                2 -> rand.nextFloat() * 1.4f - 0.7f
                3 -> rand.nextFloat() * 1.2f - 0.6f
                else -> rand.nextFloat() * 0.8f - 0.4f
            },
            alpha = when (mode) {
                1 -> 100 + rand.nextInt(80)
                2 -> 120 + rand.nextInt(80)
                else -> 90 + rand.nextInt(100)
            },
            wobble = rand.nextFloat() * 6.28f,
            rot = rand.nextFloat() * 6.28f,
            spin = (rand.nextFloat() - 0.5f) * 2.5f
        )
    }

    private fun step(dt: Float) {
        if (width <= 0 || height <= 0 || density <= 0) return
        val spd = (0.8f + speedStep * 0.7f) * 60f * dt
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in flakes.indices) {
            val f = flakes[i]
            when (mode) {
                1 -> { f.y += f.speed * spd * 1.8f; f.x += f.drift * spd * 0.3f }
                2 -> {
                    f.y += f.speed * spd * 0.6f
                    f.x += f.drift * spd + sin(time * 2.2f + f.wobble) * 0.9f
                    f.rot += f.spin * dt
                }
                3 -> {
                    f.y += f.speed * spd * 0.7f
                    f.x += f.drift * spd + sin(time * 1.1f + f.wobble) * 1.4f
                    f.rot += f.spin * dt
                }
                else -> {
                    f.y += f.speed * spd
                    f.x += f.drift * spd * 0.5f + sin(time * 1.5f + f.wobble) * 0.55f
                }
            }
            if (inClearZone(f.x, f.y)) {
                val cx = w / 2f; val cy = h / 2f
                val dx = f.x - cx; val dy = f.y - cy
                val len = hypot(dx, dy).coerceAtLeast(0.001f)
                val cr = clearRadius() + 8f
                f.x = cx + dx / len * cr
                f.y = cy + dy / len * cr
            }
            if (f.y > h + 16f || f.y < -40f) {
                flakes[i] = spawn(-16f)
            } else {
                if (f.x > w + 16f) f.x = -16f
                if (f.x < -16f) f.x = w + 16f
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (density <= 0) return
        for (f in flakes) {
            if (inClearZone(f.x, f.y)) continue
            when (mode) {
                1 -> {
                    paint.color = Color.argb(f.alpha, 180, 210, 255)
                    paint.strokeWidth = f.radius * 0.7f
                    paint.strokeCap = Paint.Cap.ROUND
                    canvas.drawLine(f.x, f.y, f.x + f.drift * 2f, f.y + f.radius * 6f, paint)
                }
                2 -> {
                    paint.color = Color.argb(f.alpha, 255, 160 + (f.wobble * 20).toInt() % 60, 40)
                    canvas.drawCircle(f.x, f.y, f.radius, paint)
                }
                3 -> {
                    paint.color = Color.argb(f.alpha, 40 + (f.wobble * 30).toInt() % 80, 140, 50)
                    canvas.save()
                    canvas.rotate(Math.toDegrees(f.rot.toDouble()).toFloat(), f.x, f.y)
                    canvas.drawOval(f.x - f.radius, f.y - f.radius * 0.55f, f.x + f.radius, f.y + f.radius * 0.55f, paint)
                    canvas.restore()
                }
                else -> {
                    paint.color = Color.argb(f.alpha, 255, 255, 255)
                    canvas.drawCircle(f.x, f.y, f.radius, paint)
                }
            }
        }
    }
}
