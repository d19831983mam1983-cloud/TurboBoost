package com.example.turboboost

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import java.util.concurrent.Executors

class SidePanelService : Service() {

    private var panel: SidePanel? = null
    private lateinit var windowManager: WindowManager
    private lateinit var info: DeviceInfo
    private lateinit var prefs: AppPrefs
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkGame()
            main.postDelayed(this, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        info = DeviceInfo(this)
        prefs = AppPrefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCheck()
        return START_STICKY
    }

    private fun startCheck() {
        main.removeCallbacks(checkRunnable)
        main.post(checkRunnable)
    }

    private fun checkGame() {
        val targetPkg = prefs.getLastPackage()
        if (targetPkg.isBlank()) {
            hidePanel()
            return
        }

        executor.execute {
            val current = SystemReader.read("dumpsys window | grep mCurrentFocus")
            val isGameActive = current.contains(targetPkg)

            main.post {
                if (isGameActive) showPanel() else hidePanel()
            }
        }
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
                        info.toggleTurbo(false)
                        prefs.setActive(false)
                        prefs.setTurboActive(false)
                    } else {
                        info.activateMaxPerformance(prefs.getTargetHz(), prefs.getPerfMode())
                        info.toggleTurbo(true)
                        prefs.setActive(true)
                        prefs.setTurboActive(true)
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
                    info.unlockGameFps(prefs.getLastPackage(), hz)
                }
            }
        )
        panel?.show()
    }

    private fun hidePanel() {
        if (panel != null) {
            panel?.hide()
            panel = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(checkRunnable)
        hidePanel()
        executor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
