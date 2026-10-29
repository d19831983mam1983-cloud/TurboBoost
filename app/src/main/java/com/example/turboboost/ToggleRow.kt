package com.example.turboboost

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

class ToggleRow(context: Context, title: String) : LinearLayout(context) {

    val switch: ToggleSwitch
    var onToggle: ((Boolean) -> Unit)? = null

    var onColor: Int
        get() = switch.onColor
        set(v) { switch.onColor = v }

    var doubleBorder: Boolean
        get() = switch.doubleBorder
        set(v) { switch.doubleBorder = v }

    var borderColor: Int
        get() = switch.borderColor
        set(v) { switch.borderColor = v }

    private val titleView: TextView
    private val bgDrawable: GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        colors = intArrayOf(0x1AFFFFFF, 0x10FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.TL_BR
        setStroke(dp(1), 0x22FFFFFF)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = bgDrawable

        titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = false
        }
        addView(titleView, LayoutParams(0, WRAP_CONTENT, 1f))

        switch = ToggleSwitch(context).apply {
            onToggle = { v -> this@ToggleRow.onToggle?.invoke(v) }
        }
        addView(switch, LayoutParams(dp(52), dp(32)))

        isClickable = true
        setOnClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val next = !switch.on
            switch.setOn(next, true)
            onToggle?.invoke(next)
        }
    }

    fun setOn(value: Boolean) { switch.setOn(value, true) }
    fun setOnInstant(value: Boolean) { switch.setOn(value, false) }

    fun applyGridStyle(active: Boolean, border: Int) {
        doubleBorder = true
        borderColor = border
        bgDrawable.colors = if (active)
            intArrayOf(0xFF4A4A4A.toInt(), 0xFF3A3A3A.toInt())
        else
            intArrayOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt())
        bgDrawable.gradientType = GradientDrawable.LINEAR_GRADIENT
        bgDrawable.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        bgDrawable.setStroke(dp(2), border)
        background = bgDrawable
    }

    fun clearGridStyle() {
        doubleBorder = false
        bgDrawable.colors = intArrayOf(0x1AFFFFFF, 0x10FFFFFF)
        bgDrawable.orientation = GradientDrawable.Orientation.TL_BR
        bgDrawable.setStroke(dp(1), 0x22FFFFFF)
        background = bgDrawable
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
