package com.example.turboboost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class SettingsScreen(private val ctx: Context) {

    lateinit var root: FrameLayout
    var onBack: (() -> Unit)? = null
    var onThemeSelected: ((Int) -> Unit)? = null
    var onPickBackground: (() -> Unit)? = null
    var onResetBackground: (() -> Unit)? = null

    private lateinit var snowView: SettingsSnowView

    fun build(): FrameLayout {
        root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#000000"))

        snowView = SettingsSnowView(ctx)
        root.addView(snowView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setPadding(0, dp(72), 0, 0)
        }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        content.addView(tv("Темы", 14f, Color.parseColor("#AAFFFFFF")).apply {
            setPadding(dp(4), dp(16), 0, dp(8))
        })

        ThemePalette.names.forEachIndexed { i, name ->
            val item = TextView(ctx).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = glassItem()
                setOnClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onThemeSelected?.invoke(i)
                }
            }
            content.addView(item, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            })
        }

        content.addView(tv("Фон", 14f, Color.parseColor("#AAFFFFFF")).apply {
            setPadding(dp(4), dp(24), 0, dp(8))
        })

        val pickBtn = TextView(ctx).apply {
            text = "Выбрать фон из галереи"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = glassItem()
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onPickBackground?.invoke()
            }
        }
        content.addView(pickBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        val resetBtn = TextView(ctx).apply {
            text = "Сбросить фон"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = glassItem()
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onResetBackground?.invoke()
            }
        }
        content.addView(resetBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        val topBar = buildTopBar()
        root.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))

        return root
    }

    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(ctx)
        bar.setBackgroundColor(Color.parseColor("#000000"))

        val h = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val back = ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onBack?.invoke() }
        }
        h.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })

        val title = TextView(ctx).apply {
            text = "Настройки"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER_VERTICAL
        }
        h.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        return bar
    }

    private fun glassItem() = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        colors = intArrayOf(0x33FFFFFF, 0x1AFFFFFF, 0x22FFFFFF)
        gradientType = android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT
        orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
        setStroke(dp(1), 0xAAFFFFFF.toInt())
    }

    private fun tv(text: String, size: Float, color: Int): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        textSize = size
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    class SettingsSnowView(context: Context) : View(context), SensorEventListener {
        private data class Flake(
            var x: Float, var y: Float, val radius: Float,
            val speed: Float, val drift: Float, var alpha: Int, var rotation: Float
        )
        private val flakes = mutableListOf<Flake>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rand = Random(System.currentTimeMillis())
        private var time = 0f
        private var active = true

        private var sensorManager: SensorManager? = null
        private var accelerometer: Sensor? = null
        private var lastShakeTime = 0L
        private var lastX = 0f
        private var lastY = 0f
        private var lastZ = 0f

        init {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            sensorManager?.unregisterListener(this)
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            active = visibility == VISIBLE
            if (active) invalidate()
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
                if (delta > 12f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 800) {
                        lastShakeTime = now
                        val cx = width / 2f
                        val cy = height / 2f
                        for (f in flakes) {
                            val dx = f.x - cx
                            val dy = f.y - cy
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
                flakes.add(
                    Flake(
                        rand.nextFloat() * w,
                        rand.nextFloat() * h,
                        rand.nextFloat() * 2.5f + 1f,
                        rand.nextFloat() * 1.5f + 0.5f,
                        rand.nextFloat() * 0.6f - 0.3f,
                        100 + rand.nextInt(155),
                        rand.nextFloat() * 360f
                    )
                )
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (!active) return
            time += 0.01f
            canvas.drawColor(Color.parseColor("#000000"))

            glowPaint.shader = RadialGradient(
                width * 0.5f, height * 0.3f, width * 0.7f,
                intArrayOf(0x22FFFFFF, 0x00000000), null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

            for (f in flakes) {
                f.y += f.speed
                f.x += f.drift + sin(time + f.y * 0.01f) * 0.3f
                f.rotation += 2f
                if (f.y > height + 10) { f.y = -10f; f.x = rand.nextFloat() * width }
                if (f.x > width + 10) f.x = -10f
                if (f.x < -10) f.x = width + 10f

                paint.color = Color.argb(f.alpha, 255, 255, 255)
                paint.setShadowLayer(f.radius * 3, 0f, 0f, Color.argb(f.alpha / 3, 255, 255, 255))
                canvas.drawCircle(f.x, f.y, f.radius, paint)
            }
            invalidate()
        }
    }
}
