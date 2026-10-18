package com.example.turboboost

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.WindowManager
import java.util.concurrent.Executors

class SidePanelService : Service() {

    private var panel: SidePanel? = null
    private lateinit var windowManager: WindowManager
    private lateinit var info: DeviceInfo
    private lateinit var prefs: AppPrefs
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        info = DeviceInfo(this)
        prefs = AppPrefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showPanel()
        return START_STICKY
    }

    private fun showPanel() {
        if (panel != null) return

        panel = SidePanel(
            this,
            windowManager,
            onBoost = {
                executor.execute {
                    if (prefs.isActive()) {
                        info.deactivateMaxPerformance()
                        prefs.setActive(false)
                    } else {
                        info.activateMaxPerformance(prefs.getTargetHz())
                        prefs.setActive(true)
                    }
                }
            },
            onRam = {
                executor.execute {
                    SystemReader.read("am kill-all")
                    SystemReader.read("echo 1 > /proc/sys/vm/drop_caches")
                }
            },
            onSense = { level ->
                executor.execute { info.applySense(level) }
            },
            onFps = { hz ->
                executor.execute {
                    prefs.setTargetHz(hz)
                    info.activateMaxPerformance(hz)
                }
            }
        )
        panel?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        panel?.hide()
        panel = null
        executor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
