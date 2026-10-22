package com.example.turboboost

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class SplashScreen(context: Context) : FrameLayout(context) {

    init {
        setBackgroundColor(Color.parseColor("#05070A"))
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        addView(box, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val title = TextView(context).apply {
            text = "TurboBoost"
            setTextColor(Color.WHITE)
            textSize = 34f
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
            setShadowLayer(16f, 0f, 0f, Color.parseColor("#887BA7F5"))
        }
        box.addView(title)

        val sub = TextView(context).apply {
            text = "Анализ устройства..."
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }
        box.addView(sub)

        val dots = TextView(context).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        }
        box.addView(dots)

        val handler = Handler(Looper.getMainLooper())
        var dotCount = 0
        val dotRunnable = object : Runnable {
            override fun run() {
                dotCount = (dotCount + 1) % 4
                dots.text = ".".repeat(dotCount)
                handler.postDelayed(this, 400)
            }
        }
        handler.post(dotRunnable)

        postDelayed({ handler.removeCallbacks(dotRunnable) }, 3000)
    }

    fun start(onDone: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            animate().alpha(0f).setDuration(400).withEndAction { onDone() }.start()
        }, 3000)
    }
}
