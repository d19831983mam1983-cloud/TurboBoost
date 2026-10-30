package com.example.turboboost

import android.content.Context
import android.os.Handler
import android.os.Looper

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
        info.activateMaxPerformance(p.hz, p.mode, p.gpuBoost && info.isGpuBoostSupported(), p.pkg)
        if (info.isVendorGameCmdSupported()) info.unlockGameFps(p.pkg, p.hz)
        if (info.isRooted()) info.pinGameToPrimeCore(p.pkg)
        if (info.isRooted()) info.boostInputDispatcher()
        if (p.sense >= 0) info.applySense(p.sense)
        else info.applyCustomSense(
            prefs.getPointerSpeed(),
            prefs.getSenseTouchRate(),
            prefs.isTouchPredictionOff(),
            prefs.isTouchBlockingZero(),
            prefs.isMotionAggregationOff()
        )
        OpLog.log("Авто-сессия: профиль ${p.pkg} применён")
    }
}
