package com.example.turboboost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Прицел поверх игры.
 * База — центр экрана + смещение offsetX/offsetY (в dp), чтобы можно было подвинуть вбок / вверх / вниз.
 * Стили 0..11.
 */
class CrosshairView(context: Context) : View(context) {

    var style = 1
    var colorIndex = 0
    var sizeStep = 3 // 1..5
    var opacity = 100 // 10..100 %
    var outline = true
    var gap = 2 // 0..5
    /** Смещение от центра, dp. +X вправо, +Y вниз. */
    var offsetXDp = 0
    var offsetYDp = 0

    companion object {
        val PALETTE: IntArray = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFF3B30.toInt(), 0xFF34C759.toInt(),
            0xFFF2580A.toInt(), 0xFF00C7FF.toInt(), 0xFFFFE600.toInt(),
            0xFFFF2D95.toInt(), 0xFFBF5AF2.toInt()
        )
        val STYLE_NAMES = arrayOf(
            "Точка", "Крест", "Круг", "Крест+точка", "T-образный",
            "Скобки", "Ромб", "Плюс+точка", "Галочка", "Снайпер",
            "Крест+зазор", "Двойное кольцо"
        )
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun px(v: Float) = v * resources.displayMetrics.density

    fun applyFrom(prefs: AppPrefs) {
        style = prefs.getCrosshairStyle()
        colorIndex = prefs.getCrosshairColor()
        sizeStep = prefs.getCrosshairSize()
        opacity = prefs.getCrosshairOpacity()
        outline = prefs.isCrosshairOutline()
        gap = prefs.getCrosshairGap()
        offsetXDp = prefs.getCrosshairOffsetX()
        offsetYDp = prefs.getCrosshairOffsetY()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // центр экрана + пользовательское смещение
        val cx = width / 2f + px(offsetXDp.toFloat())
        val cy = height / 2f + px(offsetYDp.toFloat())
        val u = px(4f) * (sizeStep / 3f).coerceAtLeast(0.5f)
        val g = px(2f) * gap
        val alpha = (opacity.coerceIn(10, 100) * 2.55f).toInt().coerceIn(25, 255)
        val baseColor = PALETTE[colorIndex.coerceIn(0, PALETTE.lastIndex)]
        val color = (baseColor and 0x00FFFFFF) or (alpha shl 24)

        paint.color = color
        paint.strokeWidth = px(2f).coerceAtLeast(2f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        outlinePaint.set(paint)
        outlinePaint.color = (0xFF000000.toInt() and 0x00FFFFFF) or ((alpha * 0.55f).toInt().coerceIn(20, 180) shl 24)
        outlinePaint.strokeWidth = paint.strokeWidth + px(1.6f)

        fun strokeLine(x1: Float, y1: Float, x2: Float, y2: Float) {
            if (outline) canvas.drawLine(x1, y1, x2, y2, outlinePaint)
            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        fun strokeCircle(r: Float) {
            if (outline) canvas.drawCircle(cx, cy, r, outlinePaint)
            canvas.drawCircle(cx, cy, r, paint)
        }

        fun fillDot(r: Float) {
            paint.style = Paint.Style.FILL
            if (outline) {
                outlinePaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, r + px(0.8f), outlinePaint)
                outlinePaint.style = Paint.Style.STROKE
            }
            canvas.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
        }

        when (style.coerceIn(0, 11)) {
            0 -> fillDot(u * 0.85f)
            1 -> {
                strokeLine(cx - u * 2.5f, cy, cx - u * 0.55f - g, cy)
                strokeLine(cx + u * 0.55f + g, cy, cx + u * 2.5f, cy)
                strokeLine(cx, cy - u * 2.5f, cx, cy - u * 0.55f - g)
                strokeLine(cx, cy + u * 0.55f + g, cx, cy + u * 2.5f)
            }
            2 -> strokeCircle(u * 2.0f)
            3 -> {
                strokeLine(cx - u * 2.5f, cy, cx + u * 2.5f, cy)
                strokeLine(cx, cy - u * 2.5f, cx, cy + u * 2.5f)
                fillDot(u * 0.5f)
            }
            4 -> {
                strokeLine(cx, cy - u * 2.3f, cx, cy + u * 0.9f)
                strokeLine(cx - u * 1.7f, cy - u * 1.45f, cx + u * 1.7f, cy - u * 1.45f)
            }
            5 -> {
                // скобки [ ]
                val arm = u * 1.4f
                strokeLine(cx - u * 2.2f, cy - arm, cx - u * 2.2f, cy + arm)
                strokeLine(cx - u * 2.2f, cy - arm, cx - u * 1.2f, cy - arm)
                strokeLine(cx - u * 2.2f, cy + arm, cx - u * 1.2f, cy + arm)
                strokeLine(cx + u * 2.2f, cy - arm, cx + u * 2.2f, cy + arm)
                strokeLine(cx + u * 2.2f, cy - arm, cx + u * 1.2f, cy - arm)
                strokeLine(cx + u * 2.2f, cy + arm, cx + u * 1.2f, cy + arm)
            }
            6 -> {
                path.reset()
                path.moveTo(cx, cy - u * 2.1f)
                path.lineTo(cx + u * 1.6f, cy)
                path.lineTo(cx, cy + u * 2.1f)
                path.lineTo(cx - u * 1.6f, cy)
                path.close()
                if (outline) canvas.drawPath(path, outlinePaint)
                canvas.drawPath(path, paint)
            }
            7 -> {
                strokeLine(cx - u * 2.4f, cy, cx - u * 0.7f - g, cy)
                strokeLine(cx + u * 0.7f + g, cy, cx + u * 2.4f, cy)
                strokeLine(cx, cy - u * 2.4f, cx, cy - u * 0.7f - g)
                strokeLine(cx, cy + u * 0.7f + g, cx, cy + u * 2.4f)
                fillDot(u * 0.45f)
            }
            8 -> {
                // галочка V
                strokeLine(cx - u * 1.8f, cy, cx - u * 0.2f, cy + u * 1.5f)
                strokeLine(cx - u * 0.2f, cy + u * 1.5f, cx + u * 2.0f, cy - u * 1.6f)
            }
            9 -> {
                // снайпер: круг + крест с зазором
                strokeCircle(u * 2.4f)
                strokeLine(cx - u * 3.2f, cy, cx - u * 1.1f, cy)
                strokeLine(cx + u * 1.1f, cy, cx + u * 3.2f, cy)
                strokeLine(cx, cy - u * 3.2f, cx, cy - u * 1.1f)
                strokeLine(cx, cy + u * 1.1f, cx, cy + u * 3.2f)
                fillDot(u * 0.35f)
            }
            10 -> {
                strokeLine(cx - u * 2.6f, cy, cx - u * 0.9f - g, cy)
                strokeLine(cx + u * 0.9f + g, cy, cx + u * 2.6f, cy)
                strokeLine(cx, cy - u * 2.6f, cx, cy - u * 0.9f - g)
                strokeLine(cx, cy + u * 0.9f + g, cx, cy + u * 2.6f)
            }
            11 -> {
                strokeCircle(u * 1.5f)
                strokeCircle(u * 2.6f)
            }
        }
    }
}
