package com.example.turboboost

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: LinearLayout
    private lateinit var tvFps: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvBat: TextView
    private lateinit var info: DeviceInfo
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        info = DeviceInfo(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createView()
        startUpdates()
    }

    private fun createView() {
        floatView = LinearLayout(this)
        floatView.orientation = LinearLayout.VERTICAL
        floatView.gravity = Gravity.CENTER
        floatView.setPadding(dp(16), dp(8), dp(16), dp(8))

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dp(16).toFloat()
        bg.setColor(Color.parseColor("#990B0F1A"))
        bg.setStroke(dp(1), Color.parseColor("#55FFFFFF"))
        floatView.background = bg

        tvFps = TextView(this)
        tvFps.setTextColor(Color.parseColor("#4ADE80"))
        tvFps.textSize = 12f
        tvFps.gravity = Gravity.CENTER

        tvTemp = TextView(this)
        tvTemp.setTextColor(Color.parseColor("#FBBF24"))
        tvTemp.textSize = 12f
        tvTemp.gravity = Gravity.CENTER

        tvBat = TextView(this)
        tvBat.setTextColor(Color.WHITE)
        tvBat.textSize = 12f
        tvBat.gravity = Gravity.CENTER

        floatView.addView(tvFps)
        floatView.addView(tvTemp)
        floatView.addView(tvBat)

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 100

        windowManager.addView(floatView, params)
    }

    private fun startUpdates() {
        val r = object : Runnable {
            override fun run() {
                executor.execute {
                    val bat = info.batteryLevel()
                    val temp = info.cpuTemp()
                    val fps = info.currentFps()
                    main.post {
                        tvFps.text = "FPS: ${"%.0f".format(fps)}"
                        tvTemp.text = "TEMP: ${"%.1f".format(temp)}°C"
                        tvBat.text = "BAT: $bat%"
                    }
                }
                main.postDelayed(this, 1500)
            }
        }
        main.post(r)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        if (::floatView.isInitialized) {
            windowManager.removeView(floatView)
        }
        executor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
