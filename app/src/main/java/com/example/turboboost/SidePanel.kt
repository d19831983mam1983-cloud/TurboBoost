package com.example.turboboost

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

@SuppressLint("ViewConstructor")
class SidePanel(
    context: Context,
    private val windowManager: WindowManager,
    private val onBoost: () -> Unit,
    private val onRam: () -> Unit,
    private val onSense: (Int) -> Unit,
    private val onFps: (Int) -> Unit
) : LinearLayout(context) {

    private lateinit var params: WindowManager.LayoutParams
    private lateinit var btnOpen: TextView
    private lateinit var panelContent: LinearLayout
    private var isOpen = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL

        btnOpen = TextView(context)
        btnOpen.text = "<"
        btnOpen.setTextColor(Color.WHITE)
        btnOpen.textSize = 18f
        btnOpen.gravity = Gravity.CENTER
        btnOpen.setPadding(0, 30, 0, 30)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadii = floatArrayOf(20f, 20f, 0f, 0f, 0f, 0f, 20f, 20f)
        bg.setColor(Color.parseColor("#AA5B8DEF"))
        btnOpen.background = bg
        addView(btnOpen, LayoutParams(40, LayoutParams.WRAP_CONTENT))

        panelContent = LinearLayout(context)
        panelContent.orientation = VERTICAL
        panelContent.setBackgroundColor(Color.parseColor("#EE0B0F1A"))
        panelContent.setPadding(20, 40, 20, 40)
        panelContent.visibility = GONE
        addView(panelContent, LayoutParams(250, LayoutParams.MATCH_PARENT))

        val title = TextView(context)
        title.text = "Game Turbo"
        title.setTextColor(Color.WHITE)
        title.textSize = 20f
        title.gravity = Gravity.CENTER
        panelContent.addView(title)

        addButton("ВКЛ / ВЫКЛ БУСТ", "#5B8DEF") { onBoost() }
        addButton("ОЧИСТИТЬ RAM", "#8B5CF6") { onRam() }
        
        addLabel("Сенса:")
        val senseRow = LinearLayout(context)
        senseRow.orientation = HORIZONTAL
        panelContent.addView(senseRow)
        for (i in 0..3) {
            val b = smallBtn("$i")
            b.setOnClickListener { onSense(i) }
            senseRow.addView(b, LayoutParams(0, 100, 1f))
        }

        addLabel("FPS:")
        val fpsRow = LinearLayout(context)
        fpsRow.orientation = HORIZONTAL
        panelContent.addView(fpsRow)
        val rates = listOf(60, 90, 120, 144)
        for (r in rates) {
            val b = smallBtn("$r")
            b.setOnClickListener { onFps(r) }
            fpsRow.addView(b, LayoutParams(0, 100, 1f))
        }

        btnOpen.setOnClickListener { toggle() }
        setupDrag()
    }

    private fun addButton(text: String, color: String, action: () -> Unit) {
        val btn = TextView(context)
        btn.text = text
        btn.setTextColor(Color.WHITE)
        btn.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 20f
        bg.setColor(Color.parseColor(color))
        btn.background = bg
        btn.setOnClickListener { action() }
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, 120)
        lp.topMargin = 20
        panelContent.addView(btn, lp)
    }

    private fun addLabel(text: String) {
        val tv = TextView(context)
        tv.text = text
        tv.setTextColor(Color.parseColor("#AAFFFFFF"))
        tv.textSize = 12f
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.topMargin = 20
        panelContent.addView(tv, lp)
    }

    private fun smallBtn(text: String): TextView {
        val tv = TextView(context)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.textSize = 14f
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 10f
        bg.setColor(Color.parseColor("#33FFFFFF"))
        tv.background = bg
        tv.setPadding(10, 10, 10, 10)
        return tv
    }

    private fun toggle() {
        isOpen = !isOpen
        if (isOpen) {
            panelContent.visibility = VISIBLE
            btnOpen.text = ">"
            // сдвигаем панель вправо, чтобы было видно контент
            params.x = 0
        } else {
            panelContent.visibility = GONE
            btnOpen.text = "<"
        }
        windowManager.updateViewLayout(this, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        btnOpen.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(this, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (Math.abs(event.rawX - initialTouchX) < 10 && Math.abs(event.rawY - initialTouchY) < 10) {
                        toggle()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun show() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        params.x = 0
        windowManager.addView(this, params)
    }

    fun hide() {
        try {
            windowManager.removeView(this)
        } catch (e: Exception) {}
    }
}
