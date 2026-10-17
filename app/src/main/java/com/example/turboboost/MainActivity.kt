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

        ui.onToggle = {
            if (!busy) {
                toggle()
            }
        }

        ui.setActive(prefs.isActive())
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
        prefs.setActive(newState)
        ui.setActive(newState)
        if (newState) {
            ui.statusResult.text = "Режим включён (без root частота не меняется)"
        } else {
            ui.statusResult.text = "Режим выключен"
        }
        busy = false
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
                        prefs.setActive(false)
                        ui.setActive(false)
                        ui.statusResult.text = "Автоотключение: батарея ≤ 10%"
                    } else if (temp > th) {
                        prefs.setActive(false)
                        ui.setActive(false)
                        ui.statusResult.text = "Автоотключение: перегрев"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
