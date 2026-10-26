package com.example.turboboost

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

/**
 * Плавающая мини-панель поверх игр: температура, батарея, состояние турбо.
 * Тап по панели — закрыть. Включается в Настройки (нужно разрешение «поверх других приложений»).
 */
class MonitorOverlayService : Service() {
    private var tv: TextView? = null
    private var wm: WindowManager? = null
    private var lpRef: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var info: DeviceInfo? = null
    private var downX = 0f; private var downY = 0f; private var moved = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (tv == null) show()
        return START_STICKY
    }

    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 24; lp.y = 220
        val t = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(24, 12, 24, 12)
            background = GradientDrawable().apply { cornerRadius = 18f; setColor(0xB3000000.toInt()); setStroke(2, 0xFFF2580A.toInt()) }
            setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; moved = false; true }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val l = lpRef
                        if (l != null && wm != null) {
                            val dx = ev.rawX - downX; val dy = ev.rawY - downY
                            if (kotlin.math.abs(dx) > 20 || kotlin.math.abs(dy) > 20) moved = true
                            if (moved) {
                                l.x += dx.toInt(); l.y += dy.toInt()
                                downX = ev.rawX; downY = ev.rawY
                                try { wm?.updateViewLayout(view, l) } catch (_: Throwable) {}
                            }
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (!moved) stopSelf() // тап — закрыть
                        true
                    }
                    else -> false
                }
            }
        }
        tv = t
        lpRef = lp
        try { wm?.addView(t, lp) } catch (_: Throwable) { stopSelf(); return }
        tick()
    }

    private fun tick() {
        if (tv == null) return
        if (info == null) info = DeviceInfo(this)
        val i = info ?: return
        Thread {
            val temp: Float = try { i.cpuTemp() } catch (_: Throwable) { 0f }
            val bat: Int = try { i.batteryLevel() } catch (_: Throwable) { -1 }
            val active = AppPrefs(this).isActive()
            handler.post {
                try {
                    val t = tv ?: return@post
                    val sb = StringBuilder()
                    sb.append(if (temp > 1f) String.format(Locale.US, "%.0f°C", temp) else "—°C")
                    if (bat >= 0) sb.append(" · ").append(bat).append("%")
                    if (active) sb.append(" · ТУРБО")
                    t.text = sb.toString()
                } catch (_: Throwable) {}
                handler.postDelayed({ tick() }, 2000L)
            }
        }.start()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { tv?.let { wm?.removeView(it) } } catch (_: Throwable) {}
        tv = null
        super.onDestroy()
    }
}
