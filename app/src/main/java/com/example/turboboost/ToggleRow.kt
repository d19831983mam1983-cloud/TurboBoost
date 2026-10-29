package com.example.turboboost

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

class ToggleRow(context: Context, title: String) : LinearLayout(context) {

    val switch: ToggleSwitch
    var onToggle: ((Boolean) -> Unit)? = null

    var onColor: Int
        get() = switch.onColor
        set(v) { switch.onColor = v }

    var titleColor: Int = Color.WHITE
        set(v) { titleView.setTextColor(v); field = v }

    private val titleView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            colors = intArrayOf(0x1AFFFFFF, 0x10FFFFFF)
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(1), 0x22FFFFFF)
        }

        titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = false
        }
        addView(titleView, LayoutParams(0, WRAP_CONTENT, 1f))

        switch = ToggleSwitch(context).apply {
            onToggle = { v -> onToggle?.invoke(v) }
        }
        addView(switch, LayoutParams(dp(52), dp(30)))

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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
