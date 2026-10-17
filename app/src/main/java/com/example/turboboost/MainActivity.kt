package com.example.turboboost

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
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

        ui.currentHz = prefs.getTargetHz()
        ui.updateHzSelection()
        ui.setSenseState(prefs.isSenseActive())

        ui.onHzSelected = { hz ->
            prefs.setTargetHz(hz)
            if (prefs.isActive()) {
                executor.execute {
                    val res = info.activateMaxPerformance(hz)
                    main.post { ui.statusResult.text = res }
                }
            }
        }

        ui.onSenseToggle = {
            executor.execute {
                if (prefs.isSenseActive()) {
                    val res = info.deactivateSense()
                    prefs.setSenseActive(false)
                    main.post {
                        ui.setSenseState(false)
                        ui.statusResult.text = res
                    }
                } else {
                    val res = info.activateSense()
                    prefs.setSenseActive(true)
                    main.post {
                        ui.setSenseState(true)
                        ui.statusResult.text = res
                    }
                }
            }
        }

        ShizukuHelper.init()
        ShizukuHelper.onPermissionResult = { granted ->
            main.post {
                if (granted) {
                    ui.statusResult.text = "Shizuku: разрешено"
                    syncSystemState()
                    refresh()
                } else {
                    ui.statusResult.text = "Shizuku: нет разрешения"
                }
            }
        }

        ui.onToggle = {
            if (!busy) {
                toggle()
            }
        }

        if (ShizukuHelper.isAvailable() && !ShizukuHelper.hasPermission()) {
            ShizukuHelper.requestPermission()
        } else if (!ShizukuHelper.isAvailable()) {
            ui.statusResult.text = "Shizuku не запущен"
        }

        syncSystemState()
        refresh()
        startLoop()
    }

    fun syncSystemState() {
        executor.execute {
            val isSystemBoosted = info.checkSystemState()
            if (prefs.isActive() && !isSystemBoosted) {
                prefs.setActive(false)
                main.post {
                    ui.setActive(false)
                    ui.statusResult.text = "Система перезагружена. Включите заново."
                }
            } else {
                main.post {
                    ui.setActive(prefs.isActive())
                }
            }
        }
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
            val hz = prefs.getTargetHz()
            executor.execute {
                val result = info.activateMaxPerformance(hz)
                main.post {
                    prefs.setActive(true)
                    ui.setActive(true)
                    ui.statusResult.text = result
                    busy = false
                    refresh()
                }
            }
        } else {
            ui.statusResult.text = "Отключение..."
            executor.execute {
                val result = info.deactivateMaxPerformance()
                main.post {
                    prefs.setActive(false)
                    ui.setActive(false)
                    ui.statusResult.text = result
                    busy = false
                    refresh()
                }
            }
        }
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
            val hz = info.currentHz()
            val bat = info.batteryLevel()
            val temp = info.cpuTemp()
            val chip = info.chipName()

            main.post {
                ui.cardChip.text = "Чип: $chip"
                ui.cardScreen.text = "Текущая частота: $hz Hz"
                ui.cardBattery.text = "Батарея: $bat%"
                ui.cardTemp.text = "Температура: ${"%.0f".format(temp)}°C"
                ui.setBatteryLowBlock(bat <= 10)

                if (prefs.isActive() && bat <= 10) {
                    busy = true
                    executor.execute {
                        info.deactivateMaxPerformance()
                        main.post {
                            prefs.setActive(false)
                            ui.setActive(false)
                            ui.statusResult.text = "Автоотключение: батарея ≤ 10%"
                            busy = false
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
