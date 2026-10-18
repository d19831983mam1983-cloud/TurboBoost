package com.example.turboboost

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
        ShizukuHelper.init(this)
        ShizukuHelper.onPermissionResult = { g -> main.post { if (g) bindAndBuild() else showBlockedScreen() } }
        checkAccess()
    }

    private fun checkAccess() {
        if (!ShizukuHelper.isAvailable()) { showBlockedScreen(); return }
        if (!ShizukuHelper.hasPermission()) { ShizukuHelper.requestPermission(); showBlockedScreen(); return }
        bindAndBuild()
    }

    private fun bindAndBuild() {
        ShizukuHelper.onServiceReady = { r -> main.post { if (r) buildMainUI() else showBlockedScreen() } }
        ShizukuHelper.bindService()
    }

    private fun showBlockedScreen() {
        uiBuilt = false
        val box = android.widget.LinearLayout(this)
        box.orientation = android.widget.LinearLayout.VERTICAL
        box.gravity = android.view.Gravity.CENTER
        box.setBackgroundColor(Color.parseColor("#0B0F1A"))
        box.setPadding(60, 60, 60, 60)
        val t = android.widget.TextView(this)
        t.text = "Доступ закрыт"
        t.setTextColor(Color.WHITE)
        t.textSize = 28f
        t.gravity = android.view.Gravity.CENTER
        box.addView(t)
        val m = android.widget.TextView(this)
        m.text = "Для работы нужен Shizuku с разрешением."
        m.setTextColor(Color.parseColor("#AAFFFFFF"))
        m.textSize = 15f
        m.gravity = android.view.Gravity.CENTER
        val lp = android.widget.LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = 40
        box.addView(m, lp)
        val b = android.widget.TextView(this)
        b.text = "ПРОВЕРИТЬ СНОВА"
        b.setTextColor(Color.WHITE)
        b.textSize = 18f
        b.gravity = android.view.Gravity.CENTER
        b.setPadding(40, 30, 40, 30)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        bg.cornerRadius = 24f
        bg.setColor(Color.parseColor("#5B8DEF"))
        b.background = bg
        b.setOnClickListener { checkAccess() }
        val lp2 = android.widget.LinearLayout.LayoutParams(-1, -2)
        lp2.topMargin = 60
        box.addView(b, lp2)
        setContentView(box)
    }

    private fun buildMainUI() {
        if (uiBuilt) return
        uiBuilt = true
        setContentView(ui.build())
        ui.setPackageText(prefs.getLastPackage())
        ui.setMode(prefs.getPerfMode())
        ui.setHz(prefs.getTargetHz())
        if (prefs.getSenseLevel() >= 0) ui.setSenseLevel(prefs.getSenseLevel())
        if (prefs.getThermalLevel() >= 0) ui.setThermalLevel(prefs.getThermalLevel())
        ui.setGpuBoostState(prefs.isGpuBoost())
        ui.setBgLimitState(prefs.isBackgroundLimit())
        ui.statusResult.text = prefs.getLastPing().ifBlank { "Нажмите кнопку" }
        if (prefs.getLastFps() > 0) ui.cardFps.text = "FPS: ${prefs.getLastFps()}"

        ui.onDevOptions = { info.openDeveloperOptions() }

        ui.onModeSelected = { mode ->
            prefs.setPerfMode(mode)
            if (prefs.isActive()) executor.execute { val r = info.activateMaxPerformance(prefs.getTargetHz(), mode, prefs.isGpuBoost()); main.post { ui.statusResult.text = r } }
        }
        ui.onHzSelected = { hz ->
            prefs.setTargetHz(hz); ui.setHz(hz)
            if (prefs.isActive()) executor.execute { val r = info.activateMaxPerformance(hz, prefs.getPerfMode(), prefs.isGpuBoost()); main.post { ui.statusResult.text = r } }
        }
        ui.onPresetSelected = { applyPreset(it) }
        ui.onCompile = { pkg -> prefs.setLastPackage(pkg); runAsync { info.compilePackage(pkg) } }
        ui.onKeep = { pkg -> prefs.setLastPackage(pkg); runAsync { info.tryKeepAlive(pkg) } }
        ui.onSenseSelected = { lv -> prefs.setSenseLevel(lv); runAsync { info.applySense(lv) } }
        ui.onThermalSelected = { lv -> prefs.setThermalLevel(lv); runAsync { if (lv == 0) info.resetThermal() else info.setThermalLimit(lv * 10 + 40) } }
        ui.onTurboToggle = {
            if (!busy && checkReady()) {
                val en = !prefs.isTurboActive()
                executor.execute {
                    val r = info.toggleTurbo(en)
                    main.post { prefs.setTurboActive(en); ui.setTurboState(en); ui.statusResult.text = r }
                }
            }
        }
        ui.onGpuBoostToggle = {
            val en = !prefs.isGpuBoost(); prefs.setGpuBoost(en); ui.setGpuBoostState(en)
            if (prefs.isActive()) executor.execute { info.activateMaxPerformance(prefs.getTargetHz(), prefs.getPerfMode(), en) }
        }
        ui.onBgLimitToggle = {
            val en = !prefs.isBackgroundLimit(); prefs.setBackgroundLimit(en); ui.setBgLimitState(en)
            runAsync { if (en) info.restrictBackground() else info.restoreBackground() }
        }
        ui.onTrimCaches = { executor.execute { val r = info.trimCaches(); main.post { prefs.setLastCacheTrim(System.currentTimeMillis()); ui.statusResult.text = r } } }
        ui.onPing = { executor.execute { val r = info.ping(); main.post { prefs.setLastPing(r); ui.cardPing.text = "Пинг: $r"; ui.statusResult.text = r } } }
        ui.onShowGames = { executor.execute { val g = info.listGames(); main.post { showGames(g) } } }
        ui.onShowProfiles = { showProfilesDialog() }
        ui.onToggle = { if (!busy) toggle() }

        ui.setActive(prefs.isActive())
        ui.setTurboState(prefs.isTurboActive())
        if (prefs.isActive()) startKeepAliveService()
        refresh()
        startLoop()
    }

    private fun runAsync(block: () -> String) {
        executor.execute { val r = block(); main.post { ui.statusResult.text = r } }
    }

    private fun showGames(games: List<Pair<String, String>>) {
        if (games.isEmpty()) { Toast.makeText(this, "Игры не найдены", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Выберите игру").setItems(games.map { it.second }.toTypedArray()) { _, w ->
            val pkg = games[w].first
            ui.setPackageText(pkg)
            prefs.setLastPackage(pkg)
        }.show()
    }

    private fun checkReady(): Boolean {
        if (!ShizukuHelper.hasPermission()) { Toast.makeText(this, "Нет разрешения Shizuku", Toast.LENGTH_SHORT).show(); return false }
        if (!SystemReader.isReady()) { Toast.makeText(this, "UserService не готов, подождите", Toast.LENGTH_SHORT).show(); return false }
        return true
    }

    private fun applyPreset(preset: Int) {
        var mode = 1; var sense = 0; var thermal = 0; var gpu = false; var bg = false; var turbo = false; var hz = 60
        when (preset) {
            1 -> { mode = 0; sense = 1; thermal = 2; gpu = true; bg = true; turbo = true; hz = 120 }
            2 -> { mode = 0; sense = 3; thermal = 3; gpu = true; bg = true; turbo = true; hz = 144 }
        }
        prefs.setPerfMode(mode); prefs.setSenseLevel(sense); prefs.setThermalLevel(thermal)
        prefs.setGpuBoost(gpu); prefs.setBackgroundLimit(bg); prefs.setTargetHz(hz)
        ui.setMode(mode); ui.setSenseLevel(sense); ui.setThermalLevel(thermal)
        ui.setGpuBoostState(gpu); ui.setBgLimitState(bg); ui.setHz(hz); ui.setPreset(preset)
        if (prefs.isActive()) {
            if (!checkReady()) return
            executor.execute {
                val r = info.activateMaxPerformance(hz, mode, gpu)
                info.applySense(sense)
                if (thermal == 0) info.resetThermal() else info.setThermalLimit(thermal * 10 + 40)
                if (turbo != prefs.isTurboActive()) info.toggleTurbo(turbo)
                if (bg) info.restrictBackground() else info.restoreBackground()
                main.post { prefs.setTurboActive(turbo); ui.setTurboState(turbo); ui.statusResult.text = "Пресет применён: $r" }
            }
        } else {
            ui.statusResult.text = "Пресет выбран, нажмите ОПТИМИЗИРОВАТЬ"
        }
    }

    private fun showProfilesDialog() {
        val pkg = prefs.getLastPackage()
        if (pkg.isBlank()) { Toast.makeText(this, "Сначала выберите игру", Toast.LENGTH_SHORT).show(); return }
        val profiles = prefs.getProfiles()
        val names = mutableListOf<String>()
        names.add("Сохранить профиль для текущей игры")
        if (profiles.isNotEmpty()) {
            names.add("Удалить профиль текущей игры")
            names.add("—— Профили ——")
            names.addAll(profiles.map { it.pkg })
        }
        AlertDialog.Builder(this).setTitle("Профили").setItems(names.toTypedArray()) { _, w ->
            val c = names[w]
            when {
                c.startsWith("Сохранить") -> {
                    prefs.saveProfile(GameProfile(pkg, prefs.getPerfMode(), prefs.getSenseLevel(), prefs.getThermalLevel(), prefs.isTurboActive(), prefs.getTargetHz()))
                    Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show()
                }
                c.startsWith("Удалить") -> { prefs.deleteProfile(pkg); Toast.makeText(this, "Профиль удалён", Toast.LENGTH_SHORT).show() }
                c.startsWith("——") -> {}
                else -> { val p = profiles.firstOrNull { it.pkg == c }; if (p != null) applyProfile(p) }
            }
        }.show()
    }

    private fun applyProfile(p: GameProfile) {
        prefs.setPerfMode(p.mode); prefs.setSenseLevel(p.sense); prefs.setThermalLevel(p.thermal); prefs.setTargetHz(p.hz)
        ui.setMode(p.mode); if (p.sense >= 0) ui.setSenseLevel(p.sense); if (p.thermal >= 0) ui.setThermalLevel(p.thermal); ui.setHz(p.hz)
        executor.execute {
            info.applySense(p.sense)
            if (p.thermal == 0) info.resetThermal() else info.setThermalLimit(p.thermal * 10 + 40)
            if (prefs.isActive()) info.activateMaxPerformance(p.hz, p.mode, prefs.isGpuBoost())
            if (p.turbo != prefs.isTurboActive()) {
                val r = info.toggleTurbo(p.turbo)
                main.post { prefs.setTurboActive(p.turbo); ui.setTurboState(p.turbo); ui.statusResult.text = r }
            }
        }
    }

    fun toggle() {
        if (!checkReady()) return
        busy = true
        if (!prefs.isActive() && info.batteryLevel() <= 10) {
            Toast.makeText(this, "Заряд ≤ 10%", Toast.LENGTH_SHORT).show(); busy = false; return
        }
        val on = !prefs.isActive()
        val pkg = prefs.getLastPackage()
        val mode = prefs.getPerfMode()
        val hz = prefs.getTargetHz()
        if (on) {
            ui.statusResult.text = "Оптимизация..."
            executor.execute {
                val r = info.activateMaxPerformance(hz, mode, prefs.isGpuBoost())
                if (pkg.isNotBlank()) info.unlockGameFps(pkg, hz)
                info.toggleTurbo(true)
                if (prefs.isBackgroundLimit()) info.restrictBackground()
                main.post {
                    prefs.setActive(true); prefs.setTurboActive(true)
                    ui.setActive(true); ui.setTurboState(true); ui.statusResult.text = r
                    busy = false; startKeepAliveService(); refresh()
                }
            }
        } else {
            ui.statusResult.text = "Отключение..."
            executor.execute {
                val r = info.deactivateMaxPerformance()
                if (pkg.isNotBlank()) info.resetGameFps(pkg)
                info.toggleTurbo(false)
                if (prefs.isBackgroundLimit()) info.restoreBackground()
                main.post {
                    prefs.setActive(false); prefs.setTurboActive(false)
                    ui.setActive(false); ui.setTurboState(false); ui.statusResult.text = r
                    busy = false; stopKeepAliveService(); refresh()
                }
            }
        }
    }

    fun startKeepAliveService() {
        val i = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, i) else startService(i)
    }

    fun stopKeepAliveService() { stopService(Intent(this, KeepAliveService::class.java)) }

    fun startLoop() {
        main.postDelayed(object : Runnable {
            override fun run() { if (uiBuilt) refresh(); main.postDelayed(this, 3000) }
        }, 200)
    }

    fun refresh() {
        if (!uiBuilt) return
        executor.execute {
            val bat = info.batteryLevel()
            val temp = info.cpuTemp()
            val chip = info.chipName()
            val gpu = info.gpuName()
            val pkg = prefs.getLastPackage()
            val fps = if (prefs.isFpsMonitor() && pkg.isNotBlank()) info.currentFps(pkg) else 0
            val active = prefs.isActive()
            val gov = if (active) info.checkSystemState() else false
            val dns = if (active) info.verifyDnsPublic() else false
            val bbr = if (active) info.verifyBbrPublic() else false
            main.post {
                if (!uiBuilt) return@post
                ui.cardChip.text = "Чип: $chip"
                ui.cardGpu.text = "GPU: $gpu"
                ui.cardScreen.text = "Частота: ${prefs.getTargetHz()} Гц"
                ui.cardBattery.text = "Батарея: $bat%"
                ui.cardTemp.text = "Температура: ${"%.0f".format(temp)}°C"
                if (fps > 0) { ui.cardFps.text = "FPS: $fps"; prefs.setLastFps(fps) }
                else if (prefs.getLastFps() > 0) ui.cardFps.text = "FPS: ${prefs.getLastFps()}"
                else ui.cardFps.text = "FPS: —"
                if (prefs.getLastPing().isNotBlank()) ui.cardPing.text = "Пинг: ${prefs.getLastPing()}"
                ui.setAppliedState(active, gov, dns, bbr)
                ui.setBatteryLowBlock(bat <= 10)
                if (active && bat <= 10) {
                    busy = true
                    executor.execute {
                        info.deactivateMaxPerformance(); info.toggleTurbo(false)
                        main.post {
                            prefs.setActive(false); prefs.setTurboActive(false)
                            ui.setActive(false); ui.setTurboState(false); ui.setAppliedState(false, false, false, false)
                            ui.statusResult.text = "Автоотключение: батарея ≤ 10%"
                            busy = false; stopKeepAliveService()
                        }
                    }
                }
            }
        }
    }

    override fun onPause() { super.onPause(); if (uiBuilt) prefs.setUiScrollY(ui.scroll.scrollY) }

    override fun onResume() {
        super.onResume()
        if (!uiBuilt) checkAccess()
        else { val y = prefs.getUiScrollY(); if (y > 0) ui.scroll.post { ui.scroll.scrollTo(0, y) } }
    }

    override fun onDestroy() { ShizukuHelper.destroy(); executor.shutdown(); super.onDestroy() }
}
