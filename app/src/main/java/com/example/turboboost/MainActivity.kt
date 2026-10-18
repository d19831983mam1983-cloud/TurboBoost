package com.example.turboboost

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    lateinit var ui: OptimizerUI
    lateinit var info: DeviceInfo
    lateinit var prefs: AppPrefs
    val executor = Executors.newSingleThreadExecutor()
    val main = Handler(Looper.getMainLooper())
    var busy = false
    var uiBuilt = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        info = DeviceInfo(this)
        prefs = AppPrefs(this)
        ui = OptimizerUI(this)

        ShizukuHelper.init()
        ShizukuHelper.onPermissionResult = { granted ->
            main.post {
                if (granted) buildMainUI()
                else showBlockedScreen()
            }
        }

        checkAccess()
    }

    private fun checkAccess() {
        if (!ShizukuHelper.isAvailable()) { showBlockedScreen(); return }
        if (!ShizukuHelper.hasPermission()) { ShizukuHelper.requestPermission(); showBlockedScreen(); return }
        buildMainUI()
    }

    private fun showBlockedScreen() {
        uiBuilt = false
        val blocked = android.widget.LinearLayout(this)
        blocked.orientation = android.widget.LinearLayout.VERTICAL
        blocked.gravity = android.view.Gravity.CENTER
        blocked.setBackgroundColor(Color.parseColor("#0B0F1A"))
        blocked.setPadding(60, 60, 60, 60)

        val title = android.widget.TextView(this)
        title.text = "Доступ закрыт"
        title.setTextColor(Color.WHITE)
        title.textSize = 28f
        title.gravity = android.view.Gravity.CENTER
        blocked.addView(title)

        val message = android.widget.TextView(this)
        message.text = "Для работы нужен Shizuku с разрешением."
        message.setTextColor(Color.parseColor("#AAFFFFFF"))
        message.textSize = 15f
        message.gravity = android.view.Gravity.CENTER
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 40
        blocked.addView(message, lp)

        val retryBtn = android.widget.TextView(this)
        retryBtn.text = "ПРОВЕРИТЬ СНОВА"
        retryBtn.setTextColor(Color.WHITE)
        retryBtn.textSize = 18f
        retryBtn.gravity = android.view.Gravity.CENTER
        retryBtn.setPadding(40, 30, 40, 30)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        bg.cornerRadius = 24f
        bg.setColor(Color.parseColor("#5B8DEF"))
        retryBtn.background = bg
        retryBtn.setOnClickListener { checkAccess() }
        val lpBtn = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lpBtn.topMargin = 60
        blocked.addView(retryBtn, lpBtn)

        setContentView(blocked)
    }

    private fun buildMainUI() {
        if (uiBuilt) return
        uiBuilt = true
        setContentView(ui.build())

        ui.setPackageText(prefs.getLastPackage())
        ui.setMode(prefs.getPerfMode())

        val savedSense = prefs.getSenseLevel()
        if (savedSense >= 0) ui.setSenseLevel(savedSense)
        val savedThermal = prefs.getThermalLevel()
        if (savedThermal >= 0) ui.setThermalLevel(savedThermal)

        ui.onDevOptions = { info.openDeveloperOptions() }

        ui.onModeSelected = { mode ->
            prefs.setPerfMode(mode)
            if (prefs.isActive()) {
                executor.execute {
                    val res = info.activateMaxPerformance(120, mode)
                    main.post { ui.statusResult.text = res }
                }
            }
        }

        ui.onCompile = { pkg ->
            prefs.setLastPackage(pkg)
            executor.execute {
                val res = info.compilePackage(pkg)
                main.post { ui.statusResult.text = res }
            }
        }

        ui.onKeep = { pkg ->
            prefs.setLastPackage(pkg)
            executor.execute {
                val res = info.tryKeepAlive(pkg)
                main.post { ui.statusResult.text = res }
            }
        }

        ui.onSenseSelected = { level ->
            prefs.setSenseLevel(level)
            executor.execute {
                val res = info.applySense(level)
                main.post { ui.statusResult.text = res }
            }
        }

        ui.onThermalSelected = { level ->
            prefs.setThermalLevel(level)
            executor.execute {
                val res = if (level == 0) info.resetThermal() else info.setThermalLimit(level * 10 + 40)
                main.post { ui.statusResult.text = res }
            }
        }

        ui.onTurboToggle = {
            if (!busy) {
                val enable = !prefs.isTurboActive()
                executor.execute {
                    val res = info.toggleTurbo(enable)
                    main.post {
                        prefs.setTurboActive(enable)
                        ui.setTurboState(enable)
                        ui.statusResult.text = res
                    }
                }
            }
        }

        ui.onToggle = { if (!busy) toggle() }

        ui.setActive(prefs.isActive())
        ui.setTurboState(prefs.isTurboActive())
        if (prefs.isActive()) startKeepAliveService()

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
        val pkg = prefs.getLastPackage()
        val mode = prefs.getPerfMode()

        if (newState) {
            ui.statusResult.text = "Оптимизация..."
            val hz = 120
            executor.execute {
                val result = info.activateMaxPerformance(hz, mode)
                if (pkg.isNotBlank()) info.unlockGameFps(pkg, hz)
                info.toggleTurbo(true)
                main.post {
                    prefs.setActive(true)
                    prefs.setTurboActive(true)
                    ui.setActive(true)
                    ui.setTurboState(true)
                    ui.statusResult.text = result
                    busy = false
                    startKeepAliveService()
                    refresh()
                }
            }
        } else {
            ui.statusResult.text = "Отключение..."
            executor.execute {
                val result = info.deactivateMaxPerformance()
                if (pkg.isNotBlank()) info.resetGameFps(pkg)
                info.toggleTurbo(false)
                main.post {
                    prefs.setActive(false)
                    prefs.setTurboActive(false)
                    ui.setActive(false)
                    ui.setTurboState(false)
                    ui.statusResult.text = result
                    busy = false
                    stopKeepAliveService()
                    refresh()
                }
            }
        }
    }

    fun startKeepAliveService() {
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    fun stopKeepAliveService() {
        val intent = Intent(this, KeepAliveService::class.java)
        stopService(intent)
    }

    fun startLoop() {
        val r = object : Runnable {
            override fun run() {
                if (uiBuilt) refresh()
                main.postDelayed(this, 3000)
            }
        }
        main.postDelayed(r, 200)
    }

    fun refresh() {
        if (!uiBuilt) return
        executor.execute {
            val bat = info.batteryLevel()
            val temp = info.cpuTemp()
            val chip = info.chipName()

            main.post {
                if (!uiBuilt) return@post
                ui.cardChip.text = "Чип: $chip"
                ui.cardScreen.text = "Частота: фикс."
                ui.cardBattery.text = "Батарея: $bat%"
                ui.cardTemp.text = "Температура: ${"%.0f".format(temp)}°C"
                ui.setBatteryLowBlock(bat <= 10)

                if (prefs.isActive() && bat <= 10) {
                    busy = true
                    executor.execute {
                        info.deactivateMaxPerformance()
                        info.toggleTurbo(false)
                        main.post {
                            prefs.setActive(false)
                            prefs.setTurboActive(false)
                            ui.setActive(false)
                            ui.setTurboState(false)
                            ui.statusResult.text = "Автоотключение: батарея ≤ 10%"
                            busy = false
                            stopKeepAliveService()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!uiBuilt) checkAccess()
    }

    override fun onDestroy() {
        ShizukuHelper.destroy()
        executor.shutdown()
        super.onDestroy()
    }
}
