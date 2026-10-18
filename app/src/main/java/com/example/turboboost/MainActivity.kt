package com.example.turboboost

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    var bootTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        info = DeviceInfo(this)
        prefs = AppPrefs(this)
        ui = OptimizerUI(this)
        bootTime = SystemClock.elapsedRealtime()

        ShizukuHelper.init(this)
        ShizukuHelper.onPermissionResult = { granted ->
            main.post {
                if (granted) {
                    ShizukuHelper.onServiceReady = { ready ->
                        main.post {
                            if (ready) buildMainUI() else showBlockedScreen()
                        }
                    }
                    ShizukuHelper.bindService()
                } else {
                    showBlockedScreen()
                }
            }
        }

        checkAccess()
    }

    private fun checkAccess() {
        if (!ShizukuHelper.isAvailable()) { showBlockedScreen(); return }
        if (!ShizukuHelper.hasPermission()) { ShizukuHelper.requestPermission(); showBlockedScreen(); return }
        ShizukuHelper.onServiceReady = { ready ->
            main.post {
                if (ready) buildMainUI() else showBlockedScreen()
            }
        }
        ShizukuHelper.bindService()
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

        ui.setGpuBoostState(prefs.isGpuBoost())
        ui.setBgLimitState(prefs.isBackgroundLimit())
        ui.statusResult.text = prefs.getLastPing().ifBlank { "Нажмите кнопку" }
        if (prefs.getLastFps() > 0) ui.cardFps.text = "FPS: ${prefs.getLastFps()}"

        ui.onDevOptions = { info.openDeveloperOptions() }

        ui.onModeSelected = { mode ->
            prefs.setPerfMode(mode)
            if (prefs.isActive()) {
                executor.execute {
                    val res = info.activateMaxPerformance(120, mode, prefs.isGpuBoost())
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

        ui.onGpuBoostToggle = {
            val enable = !prefs.isGpuBoost()
            prefs.setGpuBoost(enable)
            ui.setGpuBoostState(enable)
            if (prefs.isActive()) {
                executor.execute {
                    info.activateMaxPerformance(120, prefs.getPerfMode(), enable)
                }
            }
        }

        ui.onBgLimitToggle = {
            val enable = !prefs.isBackgroundLimit()
            prefs.setBackgroundLimit(enable)
            ui.setBgLimitState(enable)
            executor.execute {
                val res = if (enable) info.restrictBackground() else info.restoreBackground()
                main.post { ui.statusResult.text = res }
            }
        }

        ui.onTrimCaches = {
            executor.execute {
                val res = info.trimCaches()
                main.post {
                    prefs.setLastCacheTrim(System.currentTimeMillis())
                    ui.statusResult.text = res
                }
            }
        }

        ui.onPing = {
            executor.execute {
                val res = info.ping()
                main.post {
                    prefs.setLastPing(res)
                    ui.cardPing.text = "Пинг: $res"
                    ui.statusResult.text = res
                }
            }
        }

        ui.onShowGames = {
            executor.execute {
                val games = info.listGames()
                main.post {
                    if (games.isEmpty()) {
                        Toast.makeText(this, "Игры не найдены", Toast.LENGTH_SHORT).show()
                        return@post
                    }
                    val names = games.map { it.second }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Выберите игру")
                        .setItems(names) { _, which ->
                            val pkg = games[which].first
                            ui.setPackageText(pkg)
                            prefs.setLastPackage(pkg)
                        }
                        .show()
                }
            }
        }

        ui.onShowProfiles = {
            val pkg = prefs.getLastPackage()
            if (pkg.isBlank()) {
                Toast.makeText(this, "Сначала выберите игру", Toast.LENGTH_SHORT).show()
                return@
            }
            val profiles = prefs.getProfiles()
            val names = mutableListOf<String>()
            names.add("Сохранить профиль для текущей игры")
            if (profiles.isNotEmpty()) {
                names.add("Удалить профиль текущей игры")
                names.add("—— Профили ——")
                names.addAll(profiles.map { it.pkg })
            }
            AlertDialog.Builder(this)
                .setTitle("Профили")
                .setItems(names.toTypedArray()) { _, which ->
                    val choice = names[which]
                    when {
                        choice.startsWith("Сохранить") -> {
                            prefs.saveProfile(
                                GameProfile(
                                    pkg,
                                    prefs.getPerfMode(),
                                    prefs.getSenseLevel(),
                                    prefs.getThermalLevel(),
                                    prefs.isTurboActive(),
                                    prefs.getTargetHz()
                                )
                            )
                            Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show()
                        }
                        choice.startsWith("Удалить") -> {
                            prefs.deleteProfile(pkg)
                            Toast.makeText(this, "Профиль удалён", Toast.LENGTH_SHORT).show()
                        }
                        choice.startsWith("——") -> {}
                        else -> {
                            val p = profiles.firstOrNull { it.pkg == choice }
                            if (p != null) applyProfile(p)
                        }
                    }
                }
                .show()
        }

        ui.onToggle = { if (!busy) toggle() }

        ui.setActive(prefs.isActive())
        ui.setTurboState(prefs.isTurboActive())
        if (prefs.isActive()) startKeepAliveService()

        val storedBoot = prefs.getLastCacheTrim()
        if (storedBoot == 0L) prefs.setLastCacheTrim(0L)

        refresh()
        startLoop()
    }

    private fun applyProfile(p: GameProfile) {
        prefs.setPerfMode(p.mode)
        prefs.setSenseLevel(p.sense)
        prefs.setThermalLevel(p.thermal)
        prefs.setTargetHz(p.hz)
        ui.setMode(p.mode)
        if (p.sense >= 0) ui.setSenseLevel(p.sense)
        if (p.thermal >= 0) ui.setThermalLevel(p.thermal)
        executor.execute {
            info.applySense(p.sense)
            if (p.thermal == 0) info.resetThermal() else info.setThermalLimit(p.thermal * 10 + 40)
            if (prefs.isActive()) info.activateMaxPerformance(p.hz, p.mode, prefs.isGpuBoost())
            if (p.turbo != prefs.isTurboActive()) {
                val res = info.toggleTurbo(p.turbo)
                main.post {
                    prefs.setTurboActive(p.turbo)
                    ui.setTurboState(p.turbo)
                    ui.statusResult.text = res
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
        val pkg = prefs.getLastPackage()
        val mode = prefs.getPerfMode()

        if (newState) {
            ui.statusResult.text = "Оптимизация..."
            val hz = 120
            executor.execute {
                val result = info.activateMaxPerformance(hz, mode, prefs.isGpuBoost())
                if (pkg.isNotBlank()) info.unlockGameFps(pkg, hz)
                info.toggleTurbo(true)
                if (prefs.isBackgroundLimit()) info.restrictBackground()
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
                if (prefs.isBackgroundLimit()) info.restoreBackground()
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
        val now = SystemClock.elapsedRealtime()
        if (now < bootTime) {
            executor.execute {
                val real = info.checkSystemState()
                main.post {
                    if (!real && prefs.isActive()) {
                        prefs.setActive(false)
                        prefs.setTurboActive(false)
                        ui.setActive(false)
                        ui.setTurboState(false)
                        ui.statusResult.text = "Состояние сброшено после перезагрузки"
                    }
                }
            }
            bootTime = now
        }

        executor.execute {
            val bat = info.batteryLevel()
            val temp = info.cpuTemp()
            val chip = info.chipName()
            val gpu = info.gpuName()
            val pkg = prefs.getLastPackage()
            val fps = if (prefs.isFpsMonitor() && pkg.isNotBlank()) info.currentFps(pkg) else 0

            main.post {
                if (!uiBuilt) return@post
                ui.cardChip.text = "Чип: $chip"
                ui.cardGpu.text = "GPU: $gpu"
                ui.cardScreen.text = "Частота: фикс."
                ui.cardBattery.text = "Батарея: $bat%"
                ui.cardTemp.text = "Температура: ${"%.0f".format(temp)}°C"
                if (fps > 0) {
                    ui.cardFps.text = "FPS: $fps"
                    prefs.setLastFps(fps)
                } else if (prefs.getLastFps() > 0) {
                    ui.cardFps.text = "FPS: ${prefs.getLastFps()}"
                } else {
                    ui.cardFps.text = "FPS: —"
                }
                if (prefs.getLastPing().isNotBlank()) {
                    ui.cardPing.text = "Пинг: ${prefs.getLastPing()}"
                }
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

    override fun onPause() {
        super.onPause()
        if (uiBuilt) prefs.setUiScrollY(ui.scroll.scrollY)
    }

    override fun onResume() {
        super.onResume()
        if (!uiBuilt) checkAccess()
        else {
            val y = prefs.getUiScrollY()
            if (y > 0) ui.scroll.post { ui.scroll.scrollTo(0, y) }
        }
    }

    override fun onDestroy() {
        ShizukuHelper.destroy()
        executor.shutdown()
        super.onDestroy()
    }
}
