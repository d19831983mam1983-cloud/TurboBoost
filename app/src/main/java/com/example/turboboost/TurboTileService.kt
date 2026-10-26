package com.example.turboboost

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/** Тайл быстрых настроек (шторка): вкл/выкл турбо-режима одним тапом. */
class TurboTileService : TileService() {

    override fun onStartListening() { update() }

    override fun onClick() {
        val ctx = applicationContext
        val prefs = AppPrefs(ctx)
        Thread {
            var msg = ""
            try {
                if (!ShizukuBridge.ensure(ctx, 3000L)) {
                    msg = "Открой TurboBoost и дай доступ Shizuku"
                } else if (prefs.isActive()) {
                    DeviceInfo(ctx).revertAll(prefs.getLastPackage(), prefs)
                    OpLog.log("Тайл QS: выключено")
                    try { ctx.stopService(Intent(ctx, KeepAliveService::class.java)) } catch (_: Throwable) {}
                    msg = "TurboBoost: выключено"
                } else {
                    val info = DeviceInfo(ctx)
                    val p = prefs.getLastPackage()
                    info.activateMaxPerformance(prefs.getTargetHz(), prefs.getPerfMode(), prefs.isGpuBoost(), p)
                    if (p.isNotBlank()) { info.unlockGameFps(p, prefs.getTargetHz()); info.pinGameToPrimeCore(p) }
                    info.boostInputDispatcher()
                    info.applySurfaceFlingerBoost(true)
                    info.optimizePing()
                    prefs.setActive(true)
                    OpLog.log("Тайл QS: включено")
                    runOnMain { try { ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java)) } catch (_: Throwable) {} }
                    msg = "TurboBoost: включено"
                }
            } catch (e: Throwable) {
                msg = "Ошибка: " + (e.message ?: "неизвестно")
            }
            runOnMain {
                update()
                if (msg.isNotBlank()) android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun runOnMain(b: () -> Unit) {
        try { android.os.Handler(android.os.Looper.getMainLooper()).post { b() } } catch (_: Throwable) {}
    }

    private fun update() {
        try {
            val t = qsTile ?: return
            t.state = if (AppPrefs(this).isActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            t.updateTile()
        } catch (_: Throwable) {}
    }
}
