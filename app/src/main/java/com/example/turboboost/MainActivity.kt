package com.example.turboboost

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    lateinit var ui: OptimizerUI
    lateinit var info: DeviceInfo
    lateinit var prefs: AppPrefs
    val executor = Executors.newSingleThreadExecutor()
    val main = Handler(Looper.getMainLooper())
    var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        info = DeviceInfo(this)
        prefs = AppPrefs(this)
        ui = OptimizerUI(this)
        setContentView(ui.build())

        ShizukuHelper.init()
        ShizukuHelper.onPermissionResult = { granted ->
            main.post {
                if (granted) {
                    ui.statusResult.text = "Shizuku: разрешено"
                    refresh()
                } else {
                    ui.statusResult.text = "Shizuku: нет разрешения"
                }
            }
        }

        ui.onToggle = {
            if (!busy) {
                if (prefs.isActive()) {
                    ui.btnToggle.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                        ui.btnToggle.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator()).start()
                    }.start()
                }
                toggle()
            }
        }

        ui.setActive(prefs.isActive())

        if (ShizukuHelper.isAvailable() && !ShizukuHelper.hasPermission()) {
            ShizukuHelper.requestPermission()
        } else if (!ShizukuHelper.isAvailable()) {
            ui.statusResult.text = "Shizuku не запущен"
        }

        if (prefs.isActive() && Settings.canDrawOverlays(this)) {
            startFloating()
        }

        refresh()
        startLoop()
    }

    fun toggle() {
        busy = true
        val level = info.batteryLevel()
        if (!prefs.isActive() && level <= 10) {
            Toast.makeText(this, "Заряд ≤ 10%", Toast.LENGTH_SHORT).show()
            busy = false
            return
        }

        val newState = !prefs.isActive()

        if (newState) {
            ui.statusResult.text = "Включение..."
            executor.execute {
                val result = info.activateBoost(prefs)
                main.post {
                    prefs.setActive(true)
                    ui.setActive(true)
                    ui.statusResult.text = result
                    busy = false
                    refresh()
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    } else {
                        startFloating()
                    }
                }
            }
        } else {
            ui.statusResult.text = "Отключение..."
            executor.execute {
                val result = info.deactivateBoost(prefs)
                main.post {
                    prefs.setActive(false)
                    ui.setActive(false)
                    ui.statusResult.text = result
                    busy = false
                    stopFloating()
                    refresh()
                }
            }
        }
    }

    fun startFloating() {
        val intent = Intent(this, FloatingService::class.java)
        startService(intent)
    }

    fun stopFloating() {
        val intent = Intent(this, FloatingService::class.java)
        stopService(intent)
    }

    fun startLoop() {
        val r = object : Runnable {
            override fun run() {
                refresh()
                main.postDelayed(this, 3000)
            }
        }
        main.postDelayed(r, 200)
    }

    fun refresh() {
        executor.execute {
            val cores = info.coreCount()
            val gov = if (cores > 0) info.cpuGovernor(0) else "?"
            val cur = if (cores > 0) info.cpuCurFreq(0) else 0L
            val max = if (cores > 0) info.cpuMaxFreq(0) else 0L
            val gpu = info.gpuPath()
            val gpuMax = info.gpuMaxFreq(gpu)
            val hz = info.maxRefreshRate()
            val bat = info.batteryLevel()
            val temp = info.cpuTemp()
            val chip = info.chipName()
            val th = info.thermalThreshold()

            main.post {
                ui.cardChip.text = "Чип: $chip"
                ui.cardCpu.text = "CPU: $gov · ${cur / 1000} МГц (макс ${max / 1000})"
                ui.cardGpu.text = if (gpu.isNotEmpty())
                    "GPU: макс ${gpuMax / 1_000_000} МГц"
                else
                    "GPU: не найдено"
                ui.cardScreen.text = "Экран: до $hz Hz"
                ui.cardBattery.text = "Батарея: $bat%"
                ui.cardTemp.text = "Температура: ${"%.0f".format(temp)}°C (порог ${"%.0f".format(th)}°C)"
                ui.setBatteryLowBlock(bat <= 10)

                if (prefs.isActive()) {
                    if (bat <= 10) {
                        busy = true
                        executor.execute {
                            info.deactivateBoost(prefs)
                            main.post {
                                prefs.setActive(false)
                                ui.setActive(false)
                                ui.statusResult.text = "Автоотключение: батарея ≤ 10%"
                                busy = false
                                stopFloating()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ShizukuHelper.destroy()
        executor.shutdown()
        super.onDestroy()
    }
}
