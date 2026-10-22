package com.example.turboboost

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    lateinit var ui: OptimizerUI
    lateinit var info: DeviceInfo
    lateinit var prefs: AppPrefs
    val ex = Executors.newFixedThreadPool(3)
    val main = Handler(Looper.getMainLooper())
    var busy = false
    var uiBuilt = false
    private var lastSlow = 0L

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        info = DeviceInfo(this); prefs = AppPrefs(this); ui = OptimizerUI(this)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        ShizukuHelper.init(this)
        ShizukuHelper.onPermissionResult = { g -> main.post { if (g) bind() else blocked() } }
        ShizukuHelper.onBindTimeout = { main.post { if (!uiBuilt) blocked() } }
        ex.execute {
            val hasRoot = SystemReader.checkRoot()
            main.post {
                if (hasRoot) {
                    SystemReader.rootMode = true
                    SystemReader.directMode = false
                    bind()
                } else {
                    check()
                }
            }
        }
    }

    private fun check() {
        if (SystemReader.rootMode) { bind(); return }
        if (!ShizukuHelper.isAvailable()) { blocked(); return }
        if (!ShizukuHelper.hasPermission()) { ShizukuHelper.requestPermission(); blocked(); return }
        bind()
    }

    private fun bind() {
        ShizukuHelper.onServiceReady = { r -> main.post { if (r) buildMain() else blocked() } }
        if (SystemReader.rootMode) {
            buildMain()
        } else {
            ShizukuHelper.bindService()
        }
    }

    private fun blocked() {
        uiBuilt = false
        busy = false
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#05070A")); setPadding(60, 60, 60, 60)
        }
        box.addView(android.widget.TextView(this).apply { text = "Доступ закрыт"; setTextColor(Color.WHITE); textSize = 28f; gravity = android.view.Gravity.CENTER })
        box.addView(android.widget.TextView(this).apply {
            text = "Нет Root или Shizuku.\n\nЗапустите Shizuku, выдайте разрешение и нажмите «Проверить снова»."
            setTextColor(Color.parseColor("#AAFFFFFF")); textSize = 15f; gravity = android.view.Gravity.CENTER
        }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 })
        box.addView(makeBtn("ОТКРЫТЬ SHIZUKU", "#7BA7F5") { openShizuku() }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 })
        box.addView(makeBtn("ПРОВЕРИТЬ СНОВА", "#5B8DEF") { check() }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        box.addView(android.widget.TextView(this).apply {
            text = "ДИАГНОСТИКА"; setTextColor(Color.parseColor("#CCFFFFFF")); textSize = 14f; gravity = android.view.Gravity.CENTER
            setPadding(40, 20, 40, 20); setOnClickListener { blockedDiag() }
        }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        setContentView(box)
    }

    private fun makeBtn(t: String, c: String, cl: () -> Unit) = android.widget.TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 16f; gravity = android.view.Gravity.CENTER
        setPadding(40, 28, 40, 28)
        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; cornerRadius = 24f; setColor(Color.parseColor(c)) }
        setOnClickListener { cl() }
    }

    private fun openShizuku() {
        val pkg = "moe.shizuku.privileged.api"
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            startActivity(i ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        } catch (e: Throwable) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))) } catch (t: Throwable) {}
        }
    }

    private fun blockedDiag() {
        val avail = ShizukuHelper.isAvailable()
        val perm = ShizukuHelper.hasPermission()
        val ready = SystemReader.isReady()
        val mode = when {
            SystemReader.rootMode -> "Root"
            SystemReader.directMode -> "Shizuku Direct"
            SystemReader.service != null -> "Shizuku UserService"
            else -> "Нет"
        }
        val ver = try { if (avail) rikka.shizuku.Shizuku.getVersion().toString() else "—" } catch (e: Throwable) { "—" }
        val t = "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                "Режим: $mode\n" +
                "Shizuku: ${if (avail) "✓" else "✗"}\n" +
                "Разрешение: ${if (perm) "✓" else "✗"}\n" +
                "Готов: ${if (ready) "✓" else "✗"}\n" +
                "Версия Shizuku: $ver"
        AlertDialog.Builder(this).setTitle("Диагностика").setMessage(t).setPositiveButton("ОК", null).show()
    }

    private fun buildMain() {
        if (uiBuilt) return
        uiBuilt = true
        busy = false
        setContentView(ui.build())
        val ap = prefs.getActiveProfilePkg()
        ui.setPackageText(if (ap.isNotBlank()) ap else prefs.getLastPackage())
        ui.onPackageChanged = { prefs.setLastPackage(it) }
        ui.setMode(prefs.getPerfMode()); ui.setHz(prefs.getTargetHz())
        if (prefs.getSenseLevel() >= 0) ui.setSenseLevel(prefs.getSenseLevel())
        ui.setGpuBoostState(prefs.isGpuBoost()); ui.setBgLimitState(prefs.isBackgroundLimit())
        ui.setStatus("Готов к работе")
        
        ui.setThreadsState(prefs.isThreadsBoost()); ui.setMglruState(prefs.isMglru())
        
        if (prefs.isThreadsBoost()) ex.execute { info.boostThreadPriorities() }
        if (prefs.isMglru()) ex.execute { info.toggleMglru(true) }

        ui.onThreadsToggle = { val a = !prefs.isThreadsBoost(); prefs.setThreadsBoost(a); ui.setThreadsState(a); async { if (a) info.boostThreadPriorities() else info.resetThreadPriorities() } }
        ui.onMglruToggle = { val a = !prefs.isMglru(); prefs.setMglru(a); ui.setMglruState(a); async { info.toggleMglru(a) } }
        ui.onGameSpace = { if (info.openGameSpace() == "Game Space не найден") Toast.makeText(this, "Игровой режим не найден", Toast.LENGTH_LONG).show() }

        if (info.hasJoyose()) {
            ui.setJoyoseVisible(true); ui.setJoyoseState(prefs.isJoyoseDisabled())
            ui.onJoyoseToggle = { val d = !prefs.isJoyoseDisabled(); prefs.setJoyoseDisabled(d); ui.setJoyoseState(d); async { if (d) info.disableJoyose() else info.enableJoyose() } }
        }

        ui.onDiagnostics = { diag() }
        ui.onModeSelected = { m -> prefs.setPerfMode(m); if (prefs.isActive()) ex.execute { val r = info.activateMaxPerformance(prefs.getTargetHz(), m, prefs.isGpuBoost()); main.post { ui.setStatus(r) } } }
        ui.onHzSelected = { hz -> prefs.setTargetHz(hz); ui.setHz(hz); if (prefs.isActive()) ex.execute { val r = info.activateMaxPerformance(hz, prefs.getPerfMode(), prefs.isGpuBoost()); main.post { ui.setStatus(r) } } }
        ui.onPresetSelected = { preset(it) }
        ui.onSenseSelected = { prefs.setSenseLevel(it); async { info.applySense(it) } }
        ui.onGpuBoostToggle = { val e = !prefs.isGpuBoost(); prefs.setGpuBoost(e); ui.setGpuBoostState(e); if (prefs.isActive()) ex.execute { info.activateMaxPerformance(prefs.getTargetHz(), prefs.getPerfMode(), e) } }
        ui.onBgLimitToggle = { val e = !prefs.isBackgroundLimit(); prefs.setBackgroundLimit(e); ui.setBgLimitState(e); async { if (e) info.restrictBackground() else info.restoreBackground() } }
        ui.onShowGames = { ex.execute { val g = info.listGames(); main.post { games(g) } } }
        ui.onShowProfiles = { profiles() }
        ui.onToggle = { if (!busy) toggle() }

        ui.setActive(prefs.isActive())
        if (prefs.isActive()) alive(true)
        refresh(); loop()
    }    private fun async(b: () -> String) { ex.execute { val r = b(); main.post { ui.setStatus(r) } } }

    private fun games(g: List<Pair<String, String>>) {
        if (g.isEmpty()) { Toast.makeText(this, "Игры не найдены", Toast.LENGTH_SHORT).show(); return }
        ui.showGlassList("Выберите игру", g.map { it.second }) { w ->
            val p = g[w].first
            ui.setPackageText(p)
            prefs.setLastPackage(p)
            async { info.compilePackage(p) }
        }
    }

    private fun diag() {
        ex.execute {
            val a = prefs.isActive()
            val mode = when {
                SystemReader.rootMode -> "Root"
                SystemReader.directMode -> "Shizuku (Direct)"
                SystemReader.service != null -> "Shizuku (UserService)"
                else -> "Нет"
            }
            val t = "Режим: $mode\n" +
                "Готов: ${if (SystemReader.isReady()) "✓" else "✗"}\n" +
                "Оптимизация: ${if (a) "вкл" else "выкл"}\n" +
                "MGLRU: ${if (info.isMglruAvailable()) "есть" else "нет"}\n" +
                "Чип: ${info.chipName()}\n" +
                "GPU: ${info.gpuName()}\n" +
                "Класс: ${info.tierName()}\n" +
                "Температура: ${"%.0f".format(info.cpuTemp())}°C\n" +
                "Батарея: ${info.batteryLevel()}%${if (info.isCharging()) " ⚡" else ""}"
            main.post { AlertDialog.Builder(this).setTitle("Диагностика").setMessage(t).setPositiveButton("ОК", null).show() }
        }
    }

    private fun ready(): Boolean {
        if (!SystemReader.isReady()) { Toast.makeText(this, "Нет доступа (Root/Shizuku)", Toast.LENGTH_SHORT).show(); return false }
        return true
    }

    private fun preset(p: Int) {
        var m = 1; var s = 0; var g = false; var b = false; var hz = 60
        when (p) { 1 -> { m = 0; s = 1; g = true; b = true; hz = 120 }; 2 -> { m = 0; s = 3; g = true; b = true; hz = 144 } }
        prefs.setPerfMode(m); prefs.setSenseLevel(s); prefs.setGpuBoost(g); prefs.setBackgroundLimit(b); prefs.setTargetHz(hz)
        ui.setMode(m); ui.setSenseLevel(s); ui.setGpuBoostState(g); ui.setBgLimitState(b); ui.setHz(hz); ui.setPreset(p)
        if (prefs.isActive()) {
            if (!ready()) return
            ex.execute {
                val r = info.activateMaxPerformance(hz, m, g)
                info.applySense(s)
                if (b) info.restrictBackground() else info.restoreBackground()
                main.post { ui.setStatus("Пресет применён: $r") }
            }
        } else ui.setStatus("Пресет выбран, нажмите ОПТИМИЗИРОВАТЬ")
    }

    private fun profiles() {
        val p = prefs.getLastPackage()
        if (p.isBlank()) { Toast.makeText(this, "Сначала выберите игру", Toast.LENGTH_SHORT).show(); return }
        val ps = prefs.getProfiles()
        
        val items = mutableListOf("Сохранить профиль для текущей игры")
        if (ps.isNotEmpty()) {
            items.add("Удалить профиль текущей игры")
            items.add("── Профили ──")
            items.addAll(ps.map { it.pkg })
        }

        ui.showGlassList("Профили", items) { w ->
            val c = items[w]
            when {
                c.startsWith("Сохранить") -> {
                    prefs.saveProfile(GameProfile(p, prefs.getPerfMode(), prefs.getSenseLevel(), 0, false, prefs.getTargetHz(), prefs.isGpuBoost(), prefs.isBackgroundLimit()))
                    Toast.makeText(this, "Сохранён", Toast.LENGTH_SHORT).show()
                }
                c.startsWith("Удалить") -> {
                    prefs.deleteProfile(p)
                    Toast.makeText(this, "Удалён", Toast.LENGTH_SHORT).show()
                }
                c.startsWith("──") -> {}
                else -> ps.firstOrNull { it.pkg == c }?.let { applyProfile(it) }
            }
        }
    }

    private fun applyProfile(p: GameProfile) {
        prefs.setPerfMode(p.mode); prefs.setSenseLevel(p.sense); prefs.setTargetHz(p.hz); prefs.setGpuBoost(p.gpuBoost); prefs.setBackgroundLimit(p.bgLimit); prefs.setLastPackage(p.pkg); prefs.setActiveProfilePkg(p.pkg)
        ui.setMode(p.mode); if (p.sense >= 0) ui.setSenseLevel(p.sense); ui.setHz(p.hz); ui.setGpuBoostState(p.gpuBoost); ui.setBgLimitState(p.bgLimit); ui.setPackageText(p.pkg)
        ex.execute {
            info.applySense(p.sense)
            if (prefs.isActive()) info.activateMaxPerformance(p.hz, p.mode, p.gpuBoost)
            if (p.bgLimit) info.restrictBackground() else info.restoreBackground()
        }
    }

    fun toggle() {
        if (!ready() || busy) return
        busy = true
        
        val timeoutRunnable = Runnable {
            if (busy) {
                busy = false
                if (uiBuilt) ui.setStatus("Готово (таймаут)")
            }
        }
        main.postDelayed(timeoutRunnable, 15000)
        
        if (!prefs.isActive() && info.batteryLevel() <= 10) { 
            Toast.makeText(this, "Заряд ≤ 10%", Toast.LENGTH_SHORT).show()
            busy = false
            main.removeCallbacks(timeoutRunnable)
            return 
        }
        
        val on = !prefs.isActive()
        val p = prefs.getLastPackage()
        val m = prefs.getPerfMode()
        val hz = prefs.getTargetHz()
        
        if (on) {
            if (uiBuilt) ui.setStatus("Оптимизация...")
            ex.execute {
                try {
                    val r = info.activateMaxPerformance(hz, m, prefs.isGpuBoost())
                    if (p.isNotBlank()) info.unlockGameFps(p, hz)
                    if (prefs.isBackgroundLimit()) info.restrictBackground()
                    info.applySurfaceFlingerBoost(true)
                    main.removeCallbacks(timeoutRunnable)
                    main.post { 
                        busy = false
                        if (!uiBuilt) return@post
                        prefs.setActive(true); ui.setActive(true); ui.setStatus(r); alive(true); refresh() 
                    }
                } catch (e: Throwable) {
                    main.removeCallbacks(timeoutRunnable)
                    main.post { 
                        busy = false
                        if (!uiBuilt) return@post
                        ui.setStatus("Ошибка: ${e.message ?: "неизвестно"}") 
                    }
                }
            }
        } else {
            if (uiBuilt) ui.setStatus("Отключение...")
            ex.execute {
                try {
                    val r = info.deactivateMaxPerformance()
                    if (p.isNotBlank()) info.resetGameFps(p)
                    if (prefs.isBackgroundLimit()) info.restoreBackground()
                    info.applySurfaceFlingerBoost(false)
                    main.removeCallbacks(timeoutRunnable)
                    main.post { 
                        busy = false
                        if (!uiBuilt) return@post
                        prefs.setActive(false); ui.setActive(false); ui.setStatus(r); alive(false); refresh() 
                    }
                } catch (e: Throwable) {
                    main.removeCallbacks(timeoutRunnable)
                    main.post { 
                        busy = false
                        if (!uiBuilt) return@post
                        ui.setStatus("Ошибка: ${e.message ?: "неизвестно"}") 
                    }
                }
            }
        }
    }

    fun alive(start: Boolean) {
        val i = Intent(this, KeepAliveService::class.java)
        if (start) { if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, i) else startService(i) } else stopService(i)
    }

    fun loop() {
        main.postDelayed(object : Runnable {
            override fun run() { try { if (uiBuilt) refresh() } catch (e: Throwable) { Log.e("TurboBoost", "refresh", e) }; main.postDelayed(this, 3000) }
        }, 200)
    }

    fun refresh() {
        if (!uiBuilt) return
        val now = System.currentTimeMillis(); val slow = now - lastSlow >= 15000L; if (slow) lastSlow = now
        ex.execute {
            try {
                val bat = info.batteryLevel(); val temp = info.cpuTemp(); val active = prefs.isActive()

                if (slow) {
                    if (active) {
                        val pkg = prefs.getLastPackage()
                        if (pkg.isNotBlank()) info.prioritizeGameThreads(pkg)
                    }
                    main.post { try { if (uiBuilt) ui.setAppliedState(active, active) } catch (e: Throwable) { Log.e("TurboBoost", "slow ui", e) } }
                }

                main.post {
                    try {
                        if (!uiBuilt) return@post
                        ui.setOverheat(temp > 75f, false)
                        ui.setBatteryLowBlock(bat <= 10)
                        if (active && bat <= 10) {
                            busy = true
                            ex.execute {
                                info.deactivateMaxPerformance()
                                main.post { prefs.setActive(false); ui.setActive(false); ui.setAppliedState(false, false); ui.setStatus("Автоотключение: батарея ≤ 10%"); busy = false; alive(false) }
                            }
                        }
                    } catch (e: Throwable) { Log.e("TurboBoost", "ui", e) }
                }
            } catch (e: Throwable) { Log.e("TurboBoost", "exec", e) }
        }
    }

    override fun onPause() { super.onPause(); if (uiBuilt) prefs.setUiScrollY(ui.scroll1.scrollY) }

    override fun onResume() {
        super.onResume()
        if (!uiBuilt) {
            check()
        } else {
            val y = prefs.getUiScrollY()
            if (y > 0) ui.scroll1.post { ui.scroll1.scrollTo(0, y) }
            
            val active = prefs.isActive()
            ui.setActive(active)
            ui.setStatus(if (active) "Активно" else "Готов к работе")
            ui.setGpuBoostState(prefs.isGpuBoost())
            ui.setBgLimitState(prefs.isBackgroundLimit())
            if (active) refresh()
            busy = false
        }
    }

    override fun onDestroy() { ShizukuHelper.destroy(); ex.shutdown(); super.onDestroy() }
}
