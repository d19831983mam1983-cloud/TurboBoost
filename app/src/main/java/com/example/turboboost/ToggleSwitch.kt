package com.example.turboboost

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class ToggleSwitch(context: Context) : View(context) {

    var on = false
        private set
    var onColor: Int = 0xFFF2580A.toInt()
    var offColor: Int = 0x33FFFFFF
    var thumbColor: Int = Color.WHITE
    var doubleBorder = false
    var borderColor: Int = 0xFFFF8A00.toInt()
    var onToggle: ((Boolean) -> Unit)? = null

    private var animProgress = 0f
    private var animator: ValueAnimator? = null
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val next = !on
            setOn(next, true)
            onToggle?.invoke(next)
        }
    }

    fun setOn(value: Boolean, animate: Boolean = true) {
        if (on == value) return
        on = value
        val target = if (value) 1f else 0f
        animator?.cancel()
        if (animate) {
            animator = ValueAnimator.ofFloat(animProgress, target).apply {
                duration = 180L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            animProgress = target
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(52), dp(32))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = h / 2f
        val p = animProgress

        if (doubleBorder) {
            val bw = dp(2).toFloat()
            val inset = bw + dp(2)
            val innerR = r - inset
            outerBorderPaint.style = Paint.Style.STROKE
            outerBorderPaint.strokeWidth = bw
            outerBorderPaint.color = borderColor
            canvas.drawRoundRect(bw / 2f, bw / 2f, w - bw / 2f, h - bw / 2f, r, r, outerBorderPaint)
            trackPaint.color = blend(offColor, onColor, p)
            canvas.drawRoundRect(inset, inset, w - inset, h - inset, innerR, innerR, trackPaint)
            innerBorderPaint.style = Paint.Style.STROKE
            innerBorderPaint.strokeWidth = dp(1).toFloat()
            innerBorderPaint.color = borderColor
            innerBorderPaint.alpha = 200
            canvas.drawRoundRect(inset, inset, w - inset, h - inset, innerR, innerR, innerBorderPaint)
            val thumbR = innerR - dp(2)
            val left = inset + dp(2) + thumbR
            val right = w - left
            val cx = left + (right - left) * p
            val cy = h / 2f
            thumbPaint.color = thumbColor
            thumbPaint.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x55000000)
            canvas.drawCircle(cx, cy, thumbR, thumbPaint)
        } else {
            trackPaint.color = blend(offColor, onColor, p)
            canvas.drawRoundRect(0f, 0f, w, h, r, r, trackPaint)
            val thumbR = r - dp(3)
            val left = dp(3) + thumbR
            val right = w - dp(3) - thumbR
            val cx = left + (right - left) * p
            val cy = h / 2f
            thumbPaint.color = thumbColor
            thumbPaint.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x55000000)
            canvas.drawCircle(cx, cy, thumbR, thumbPaint)
        }
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * t).toInt()
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
