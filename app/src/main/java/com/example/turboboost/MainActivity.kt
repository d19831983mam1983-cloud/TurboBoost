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
    private val tHist = ArrayDeque<Float>()

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
        check()
    }

    private fun check() {
        if (!ShizukuHelper.isAvailable()) { blocked(); return }
        if (!ShizukuHelper.hasPermission()) { ShizukuHelper.requestPermission(); blocked(); return }
        bind()
    }

    private fun bind() {
        ShizukuHelper.onServiceReady = { r -> main.post { if (r) buildMain() else blocked() } }
        ShizukuHelper.bindService()
    }

    private fun blocked() {
        uiBuilt = false
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#05070A")); setPadding(60, 60, 60, 60)
        }
        box.addView(android.widget.TextView(this).apply { text = "Доступ закрыт"; setTextColor(Color.WHITE); textSize = 28f; gravity = android.view.Gravity.CENTER })
        box.addView(android.widget.TextView(this).apply {
            text = "Shizuku не запущен или нет разрешения.\n\nЗапустите Shizuku, выдайте разрешение и нажмите «Проверить снова»."
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
        val avail = ShizukuHelper.isAvailable(); val perm = ShizukuHelper.hasPermission(); val ready = SystemReader.isReady()
        val ver = try { if (avail) rikka.shizuku.Shizuku.getVersion().toString() else "—" } catch (e: Throwable) { "—" }
        val t = "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nShizuku: ${if (avail) "✓" else "✗"}\nРазрешение: ${if (perm) "✓" else "✗"}\nUserService/Direct: ${if (ready) "✓" else "✗"}\nВерсия: $ver"
        AlertDialog.Builder(this).setTitle("Диагностика").setMessage(t).setPositiveButton("ОК", null).show()
    }

    private fun buildMain() {
        if (uiBuilt) return
        uiBuilt = true
        setContentView(ui.build())
        val ap = prefs.getActiveProfilePkg()
        ui.setPackageText(if (ap.isNotBlank()) ap else prefs.getLastPackage())
        ui.onPackageChanged = { prefs.setLastPackage(it) }
        ui.setMode(prefs.getPerfMode()); ui.setHz(prefs.getTargetHz())
        if (prefs.getSenseLevel() >= 0) ui.setSenseLevel(prefs.getSenseLevel())
        if (prefs.getThermalLevel() >= 0) ui.setThermalLevel(prefs.getThermalLevel())
        ui.setGpuBoostState(prefs.isGpuBoost()); ui.setBgLimitState(prefs.isBackgroundLimit())
        ui.statusResult.text = "Готов к работе"
        
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
        ui.onModeSelected = { m -> prefs.setPerfMode(m); if (prefs.isActive()) ex.execute { val r = info.activateMaxPerformance(prefs.getTargetHz(), m, prefs.isGpuBoost()); main.post { ui.statusResult.text = r } } }
        ui.onHzSelected = { hz -> prefs.setTargetHz(hz); ui.setHz(hz); if (prefs.isActive()) ex.execute { val r = info.activateMaxPerformance(hz, prefs.getPerfMode(), prefs.isGpuBoost()); main.post { ui.statusResult.text = r } } }
        ui.onPresetSelected = { preset(it) }
        ui.onSenseSelected = { prefs.setSenseLevel(it); async { info.applySense(it) } }
        ui.onThermalSelected = { prefs.setThermalLevel(it); async { if (it == 0) info.resetThermal() else info.setThermalLimit(it * 10 + 40) } }
        ui.onGpuBoostToggle = { val e = !prefs.isGpuBoost(); prefs.setGpuBoost(e); ui.setGpuBoostState(e); if (prefs.isActive()) ex.execute { info.activateMaxPerformance(prefs.getTargetHz(), prefs.getPerfMode(), e) } }
        ui.onBgLimitToggle = { val e = !prefs.isBackgroundLimit(); prefs.setBackgroundLimit(e); ui.setBgLimitState(e); async { if (e) info.restrictBackground() else info.restoreBackground() } }
        ui.onShowGames = { ex.execute { val g = info.listGames(); main.post { games(g) } } }
        ui.onShowProfiles = { profiles() }
        ui.onToggle = { if (!busy) toggle() }

        ui.setActive(prefs.isActive())
        if (prefs.isActive()) alive(true)
        refresh(); loop()
    }    private fun async(b: () -> String) { ex.execute { val r = b(); main.post { ui.statusResult.text = r } } }

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
            val t = "Shizuku: ${if (ShizukuHelper.isAvailable()) "✓" else "✗"}\n" +
                "Разрешение: ${if (ShizukuHelper.hasPermission()) "✓" else "✗"}\n" +
                "UserService/Direct: ${if (SystemReader.isReady()) "✓" else "✗"}\n" +
                "Оптимизация: ${if (a) "вкл" else "выкл"}\n" +
                "Governor: ${if (a && info.checkSystemState()) "✓" else "✗"}\n" +
                "MGLRU: ${if (info.isMglruAvailable()) "есть" else "нет"}\n" +
                "Чип: ${info.chipName()}\n" +
                "GPU: ${info.gpuName()}\n" +
                "Температура: ${"%.0f".format(info.cpuTemp())}°C\n" +
                "Батарея: ${info.batteryLevel()}%${if (info.isCharging()) " ⚡" else ""}"
            main.post { AlertDialog.Builder(this).setTitle("Диагностика").setMessage(t).setPositiveButton("ОК", null).show() }
        }
    }

    private fun ready(): Boolean {
        if (!ShizukuHelper.hasPermission()) { Toast.makeText(this, "Нет разрешения Shizuku", Toast.LENGTH_SHORT).show(); return false }
        if (!SystemReader.isReady()) { Toast.makeText(this, "Сервис не готов", Toast.LENGTH_SHORT).show(); return false }
        return true
    }

    private fun preset(p: Int) {
        var m = 1; var s = 0; var t = 0; var g = false; var b = false; var hz = 60
        when (p) { 1 -> { m = 0; s = 1; t = 2; g = true; b = true; hz = 120 }; 2 -> { m = 0; s = 3; t = 3; g = true; b = true; hz = 144 } }
        prefs.setPerfMode(m); prefs.setSenseLevel(s); prefs.setThermalLevel(t); prefs.setGpuBoost(g); prefs.setBackgroundLimit(b); prefs.setTargetHz(hz)
        ui.setMode(m); ui.setSenseLevel(s); ui.setThermalLevel(t); ui.setGpuBoostState(g); ui.setBgLimitState(b); ui.setHz(hz); ui.setPreset(p)
        if (prefs.isActive()) {
            if (!ready()) return
            ex.execute {
                val r = info.activateMaxPerformance(hz, m, g)
                info.applySense(s)
                if (t == 0) info.resetThermal() else info.setThermalLimit(t * 10 + 40)
                if (b) info.restrictBackground() else info.restoreBackground()
                main.post { ui.statusResult.text = "Пресет применён: $r" }
            }
        } else ui.statusResult.text = "Пресет выбран, нажмите ОПТИМИЗИРОВАТЬ"
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
                    prefs.saveProfile(GameProfile(p, prefs.getPerfMode(), prefs.getSenseLevel(), prefs.getThermalLevel(), false, prefs.getTargetHz(), prefs.isGpuBoost(), prefs.isBackgroundLimit()))
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
        prefs.setPerfMode(p.mode); prefs.setSenseLevel(p.sense); prefs.setThermalLevel(p.thermal); prefs.setTargetHz(p.hz); prefs.setGpuBoost(p.gpuBoost); prefs.setBackgroundLimit(p.bgLimit); prefs.setLastPackage(p.pkg); prefs.setActiveProfilePkg(p.pkg)
        ui.setMode(p.mode); if (p.sense >= 0) ui.setSenseLevel(p.sense); if (p.thermal >= 0) ui.setThermalLevel(p.thermal); ui.setHz(p.hz); ui.setGpuBoostState(p.gpuBoost); ui.setBgLimitState(p.bgLimit); ui.setPackageText(p.pkg)
        ex.execute {
            info.applySense(p.sense)
            if (p.thermal == 0) info.resetThermal() else info.setThermalLimit(p.thermal * 10 + 40)
            if (prefs.isActive()) info.activateMaxPerformance(p.hz, p.mode, p.gpuBoost)
            if (p.bgLimit) info.restrictBackground() else info.restoreBackground()
        }
    }

    fun toggle() {
        if (!ready()) return
        busy = true
        if (!prefs.isActive() && info.batteryLevel() <= 10) { Toast.makeText(this, "Заряд ≤ 10%", Toast.LENGTH_SHORT).show(); busy = false; return }
        val on = !prefs.isActive(); val p = prefs.getLastPackage(); val m = prefs.getPerfMode(); val hz = prefs.getTargetHz()
        if (on) {
            ui.statusResult.text = "Оптимизация..."
            ex.execute {
                val r = info.activateMaxPerformance(hz, m, prefs.isGpuBoost())
                if (p.isNotBlank()) info.unlockGameFps(p, hz)
                if (prefs.isBackgroundLimit()) info.restrictBackground()
                info.applySurfaceFlingerBoost(true)
                info.applyMemoryTweaks(true)
                main.post { prefs.setActive(true); ui.setActive(true); ui.statusResult.text = r; busy = false; alive(true); refresh() }
            }
        } else {
            ui.statusResult.text = "Отключение..."
            ex.execute {
                val r = info.deactivateMaxPerformance()
                if (p.isNotBlank()) info.resetGameFps(p)
                if (prefs.isBackgroundLimit()) info.restoreBackground()
                info.applySurfaceFlingerBoost(false)
                info.applyMemoryTweaks(false)
                main.post { prefs.setActive(false); ui.setActive(false); ui.statusResult.text = r; busy = false; alive(false); refresh() }
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
                val bat = info.batteryLevel(); val temp = info.cpuTemp(); val charging = info.isCharging(); val active = prefs.isActive(); val th = prefs.getThermalLevel()
                tHist.addLast(temp); if (tHist.size > 5) tHist.removeFirst()
                val growth = if (tHist.size >= 2) tHist.last() - tHist.first() else 0f

                if (temp > 80f && active) {
                    info.deactivateMaxPerformance(); prefs.setActive(false)
                    main.post { if (uiBuilt) { ui.setActive(false); ui.setAppliedState(false, false); ui.setOverheat(false, true); ui.statusResult.text = "КРИТИЧЕСКИЙ ПЕРЕГРЕВ — оптимизация выключена"; busy = false; alive(false) } }
                    return@execute
                }

                if (active && th > 0) {
                    var t = if (charging) (th * 10 + 50).coerceAtMost(70) else (th * 10 + 40)
                    if (growth > 4f && t > 50) t -= 5
                    info.setThermalLimit(t)
                }

                if (slow) {
                    val gov = if (active) info.checkSystemState() else false
                    main.post { try { if (uiBuilt) ui.setAppliedState(active, gov) } catch (e: Throwable) { Log.e("TurboBoost", "slow ui", e) } }
                }

                main.post {
                    try {
                        if (!uiBuilt) return@post
                        ui.setOverheat(temp > 75f, false); ui.setBatteryLowBlock(bat <= 10)
                        if (active && bat <= 10) {
                            busy = true
                            ex.execute {
                                info.deactivateMaxPerformance()
                                main.post { prefs.setActive(false); ui.setActive(false); ui.setAppliedState(false, false); ui.statusResult.text = "Автоотключение: батарея ≤ 10%"; busy = false; alive(false) }
                            }
                        }
                    } catch (e: Throwable) { Log.e("TurboBoost", "ui", e) }
                }
            } catch (e: Throwable) { Log.e("TurboBoost", "exec", e) }
        }
    }

    override fun onPause() { super.onPause(); if (uiBuilt) prefs.setUiScrollY(ui.scroll1.scrollY) }
    override fun onResume() { super.onResume(); if (!uiBuilt) check() else { val y = prefs.getUiScrollY(); if (y > 0) ui.scroll1.post { ui.scroll1.scrollTo(0, y) } } }
    override fun onDestroy() { ShizukuHelper.destroy(); ex.shutdown(); super.onDestroy() }
}
