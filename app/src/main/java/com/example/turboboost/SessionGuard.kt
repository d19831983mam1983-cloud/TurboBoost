package com.example.turboboost

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Авто-сессия (2.0): следит за foreground-приложением через UsageStatsManager.
 * Открыл игру с сохранённым профилем — профиль применяется сам;
 * закрыл игру — всё откатывается к исходному (revertAll).
 * Работает внутри KeepAliveService, только когда оптимизация активна
 * и включён тумблер «Авто-сессия» (нужно разрешение «Доступ к статистике использования»).
 */
class SessionGuard(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var appliedPkg = ""
    @Volatile private var busy = false
    private var away = 0

    fun start() { if (running) return; running = true; tick() }
    fun stop() { running = false; handler.removeCallbacksAndMessages(null) }

    private fun tick() {
        if (!running) return
        try { check() } catch (_: Throwable) {}
        handler.postDelayed({ tick() }, 5000L)
    }

    private fun check() {
        val prefs = AppPrefs(ctx)
        if (!prefs.isAutoSession() || !prefs.isActive()) return
        val foreground = ForegroundDetector.current(ctx) ?: return
        if (foreground == ctx.packageName) return

        val profile = prefs.findProfile(foreground)
        if (profile != null) away = 0
        if (profile != null && foreground != appliedPkg && !busy) {
            appliedPkg = foreground
            busy = true
            Thread {
                try { applyProfile(prefs, profile) } finally { busy = false }
            }.start()
        } else if (profile == null && appliedPkg.isNotEmpty() && !busy) {
            away++
            if (away < 2) return
            away = 0
            val p = appliedPkg
            appliedPkg = ""
            busy = true
            Thread {
                try {
                    DeviceInfo(ctx).revertAll(p, prefs)
                    OpLog.log("Авто-сессия: откат после $p")
                    // оптимизация выключена: фоновый сервис больше не нужен
                    try { ctx.stopService(android.content.Intent(ctx, KeepAliveService::class.java)) } catch (_: Throwable) {}
                } finally { busy = false }
            }.start()
        }
    }

    private fun applyProfile(prefs: AppPrefs, p: GameProfile) {
        val info = DeviceInfo(ctx)
        prefs.setPerfMode(p.mode); prefs.setSenseLevel(p.sense); prefs.setTargetHz(p.hz)
        prefs.setGpuBoost(p.gpuBoost); prefs.setBackgroundLimit(p.bgLimit)
        prefs.setLastPackage(p.pkg); prefs.setActiveProfilePkg(p.pkg)
        info.activateMaxPerformance(p.hz, p.mode, p.gpuBoost, p.pkg)
        info.unlockGameFps(p.pkg, p.hz)
        info.pinGameToPrimeCore(p.pkg)
        info.boostInputDispatcher()
        info.applySurfaceFlingerBoost(true)
        if (p.sense >= 0) info.applySense(p.sense)
        if (p.bgLimit) info.restrictBackground() else info.restoreBackground()
        OpLog.log("Авто-сессия: профиль ${p.pkg} применён")
    }
}
