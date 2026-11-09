package com.example.turboboost

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class LiquidBackgroundView(context: Context) : View(context), SensorEventListener {

    private data class Flake(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speed: Float,
        val drift: Float,
        var alpha: Int,
        var rotation: Float
    )

    private val flakes = mutableListOf<Flake>()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaint1 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaint2 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaint3 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix1 = Matrix()
    private val matrix2 = Matrix()
    private val matrix3 = Matrix()
    private val rand = Random(System.currentTimeMillis())
    private var time = 0f
    private var active = true
    private var isActiveMode = false
    private var themeIndex = 0
    private var bgBitmap: Bitmap? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = resources.displayMetrics.density * 1.2f
    }

    fun setBackgroundImage(bmp: Bitmap?) { bgBitmap = bmp; invalidate() }

    fun setActive(a: Boolean) { isActiveMode = a; updateGradients(); invalidate() }

    fun setTheme(index: Int, activeMode: Boolean) {
        themeIndex = index
        isActiveMode = activeMode
        updateGradients()
        invalidate()
    }

    private fun currentPrimary(): Int {
        val t = ThemePalette.get(themeIndex)
        return if (isActiveMode) t.secondary else t.primary
    }

    private fun currentSecondary(): Int {
        val t = ThemePalette.get(themeIndex)
        return if (isActiveMode) t.primary else t.secondary
    }

    private fun updateGradients() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val t = ThemePalette.get(themeIndex)
        if (t.isGrid) {
            val top = if (isActiveMode) 0xFF3A3A3A.toInt() else 0xFF2A2A2A.toInt()
            val mid = if (isActiveMode) 0xFF2A2A2A.toInt() else 0xFF1F1F1F.toInt()
            val bot = if (isActiveMode) 0xFF252525.toInt() else 0xFF1A1A1A.toInt()
            bgPaint.shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(top, mid, 0xFF1A1A1A.toInt(), bot, top),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
            blobPaint1.shader = null; blobPaint2.shader = null; blobPaint3.shader = null
            return
        }
        val top = if (isActiveMode) darken(t.secondary) else darken(t.primary)
        val mid = if (isActiveMode) t.secondary else t.primary
        val bot = darken(if (isActiveMode) t.primary else t.secondary)
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h,
            intArrayOf(0xFF05070A.toInt(), top, mid, bot, 0xFF05070A.toInt()),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
        val b1 = if (isActiveMode) t.blob2 else t.blob1
        val b2 = if (isActiveMode) t.blob3 else t.blob2
        val b3 = if (isActiveMode) t.blob1 else t.blob3
        blobPaint1.shader = RadialGradient(w * 0.3f, h * 0.3f, w * 0.7f, intArrayOf(b1, 0x00000000), null, Shader.TileMode.CLAMP)
        blobPaint2.shader = RadialGradient(w * 0.7f, h * 0.6f, w * 0.8f, intArrayOf(b2, 0x00000000), null, Shader.TileMode.CLAMP)
        blobPaint3.shader = RadialGradient(w * 0.5f, h * 0.9f, w * 0.6f, intArrayOf(b3, 0x00000000), null, Shader.TileMode.CLAMP)
    }

    private fun darken(c: Int): Int {
        val r = (Color.red(c) * 0.4f).toInt().coerceIn(0, 255)
        val g = (Color.green(c) * 0.4f).toInt().coerceIn(0, 255)
        val b = (Color.blue(c) * 0.4f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun drawGrid(canvas: Canvas) {
        val t = ThemePalette.get(themeIndex)
        val density = resources.displayMetrics.density
        val step = 56f * density
        val twoPi = (2.0 * Math.PI).toFloat()
        val path = Path()

        gridPaint.color = if (isActiveMode) t.secondary else t.primary
        gridPaint.alpha = if (isActiveMode) 90 else 55
        gridPaint.strokeWidth = 1.2f * density

        val fineAmp = 3f * density
        val fineWave = 360f * density
        val fineSpeed = 0.5f
        val fineSeg = 20

        var x = 0f; var li = 0
        while (x < width) {
            path.reset()
            val phase = li * 0.6f
            var i = 0
            while (i <= fineSeg) {
                val yy = height.toFloat() * i / fineSeg
                val off = fineAmp * sin(yy / fineWave * twoPi + time * fineSpeed + phase)
                if (i == 0) path.moveTo(x + off, yy) else path.lineTo(x + off, yy)
                i++
            }
            canvas.drawPath(path, gridPaint)
            x += step; li++
        }

        var y = 0f; li = 0
        while (y < height) {
            path.reset()
            val phase = li * 0.6f + 1.3f
            var i = 0
            while (i <= fineSeg) {
                val xx = width.toFloat() * i / fineSeg
                val off = fineAmp * sin(xx / fineWave * twoPi + time * fineSpeed + phase)
                if (i == 0) path.moveTo(xx, y + off) else path.lineTo(xx, y + off)
                i++
            }
            canvas.drawPath(path, gridPaint)
            y += step; li++
        }

        gridPaint.strokeWidth = 2f * density
        gridPaint.alpha = if (isActiveMode) 130 else 80

        val coarseAmp = 5f * density
        val coarseWave = 480f * density
        val coarseSpeed = 0.35f
        val coarseSeg = 26
        val step2 = step * 4f

        x = 0f; li = 0
        while (x < width) {
            path.reset()
            val phase = li * 0.5f
            var i = 0
            while (i <= coarseSeg) {
                val yy = height.toFloat() * i / coarseSeg
                val off = coarseAmp * sin(yy / coarseWave * twoPi + time * coarseSpeed + phase)
                if (i == 0) path.moveTo(x + off, yy) else path.lineTo(x + off, yy)
                i++
            }
            canvas.drawPath(path, gridPaint)
            x += step2; li++
        }

        y = 0f; li = 0
        while (y < height) {
            path.reset()
            val phase = li * 0.5f + 1.8f
            var i = 0
            while (i <= coarseSeg) {
                val xx = width.toFloat() * i / coarseSeg
                val off = coarseAmp * sin(xx / coarseWave * twoPi + time * coarseSpeed + phase)
                if (i == 0) path.moveTo(xx, y + off) else path.lineTo(xx, y + off)
                i++
            }
            canvas.drawPath(path, gridPaint)
            y += step2; li++
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try { if (accelerometer != null) sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI) } catch (_: Throwable) {}
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { sensorManager?.unregisterListener(this) } catch (_: Throwable) {}
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        active = visibility == VISIBLE
        if (active) invalidate()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
            if (delta > 12f) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > 800) {
                    lastShakeTime = now
                    val cx = width / 2f; val cy = height / 2f
                    for (f in flakes) {
                        val dx = f.x - cx; val dy = f.y - cy
                        val dist = hypot(dx, dy).coerceAtLeast(1f)
                        val force = rand.nextFloat() * 18f + 12f
                        f.x += (dx / dist) * force
                        f.y += (dy / dist) * force
                    }
                }
            }
            lastX = x; lastY = y; lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        flakes.clear()
        for (i in 0 until 30) {
            flakes.add(Flake(
                rand.nextFloat() * w, rand.nextFloat() * h,
                rand.nextFloat() * 2.5f + 1f, rand.nextFloat() * 1.5f + 0.5f,
                rand.nextFloat() * 0.6f - 0.3f, 100 + rand.nextInt(155),
                rand.nextFloat() * 360f
            ))
        }
        updateGradients()
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) return
        time += 0.01f
        val t = ThemePalette.get(themeIndex)
        val bmp = bgBitmap
        if (bmp != null) {
            val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            val dx = (width - bmp.width * scale) / 2f
            val dy = (height - bmp.height * scale) / 2f
            canvas.save(); canvas.translate(dx, dy); canvas.scale(scale, scale)
            canvas.drawBitmap(bmp, 0f, 0f, null)
            canvas.restore()
        } else {
            canvas.drawPaint(bgPaint)
        }
        if (t.isGrid) {
            drawGrid(canvas)
        } else {
            matrix1.setTranslate(sin(time * 0.5f) * width * 0.3f, cos(time * 0.7f) * height * 0.2f)
            blobPaint1.shader?.setLocalMatrix(matrix1)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint1)
            matrix2.setTranslate(cos(time * 0.4f) * width * 0.4f, sin(time * 0.6f) * height * 0.3f)
            blobPaint2.shader?.setLocalMatrix(matrix2)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint2)
            matrix3.setTranslate(sin(time * 0.3f) * width * 0.2f, cos(time * 0.5f) * height * 0.4f)
            blobPaint3.shader?.setLocalMatrix(matrix3)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint3)
        }
        val flakeColor = if (isActiveMode) currentSecondary() else currentPrimary()
        for (f in flakes) {
            f.y += f.speed
            f.x += f.drift + sin(time + f.y * 0.01f) * 0.3f
            f.rotation += 2f
            if (f.y > height + 10) { f.y = -10f; f.x = rand.nextFloat() * width }
            if (f.x > width + 10) f.x = -10f
            if (f.x < -10) f.x = width + 10f
            if (isActiveMode) {
                flakePaint.color = Color.argb(f.alpha, Color.red(flakeColor), Color.green(flakeColor), Color.blue(flakeColor))
                flakePaint.setShadowLayer(f.radius * 2, 0f, 0f, flakeColor)
                canvas.save(); canvas.rotate(f.rotation, f.x, f.y)
                canvas.drawOval(f.x - f.radius, f.y - f.radius * 0.5f, f.x + f.radius, f.y + f.radius * 0.5f, flakePaint)
                canvas.restore()
            } else {
                flakePaint.color = Color.argb(f.alpha, Color.red(flakeColor), Color.green(flakeColor), Color.blue(flakeColor))
                flakePaint.setShadowLayer(f.radius * 3, 0f, 0f, flakeColor)
                canvas.drawCircle(f.x, f.y, f.radius, flakePaint)
            }
        }
        if (active) postInvalidateOnAnimation()
    }
}
