package com.example.turboboost

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class TurboTileService : TileService() {

    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        val ctx = applicationContext
        val prefs = AppPrefs(ctx)
        Thread {
            var msg = ""
            try {
                if (!ShizukuBridge.ensure(ctx, 3000L)) {
                    msg = "Открой TurboBoost и дай доступ Shizuku"
                } else if (prefs.isActive()) {
                    val info = DeviceInfo(ctx)
                    info.revertAll(prefs.getLastPackage(), prefs)
                    OpLog.log("Тайл QS: выключено")
                    try { ctx.stopService(Intent(ctx, MonitorOverlayService::class.java)) } catch (_: Throwable) {}
                    try { ctx.stopService(Intent(ctx, GameOverlayService::class.java)) } catch (_: Throwable) {}
                    try { ctx.stopService(Intent(ctx, KeepAliveService::class.java)) } catch (_: Throwable) {}
                    msg = "TurboBoost: выключено"
                } else {
                    val info = DeviceInfo(ctx)
                    val p = prefs.getLastPackage()
                    val gpu = prefs.isGpuBoost() && info.isGpuBoostSupported()
                    info.activateMaxPerformance(prefs.getTargetHz(), prefs.getPerfMode(), gpu, p)
                    if (p.isNotBlank() && info.isVendorGameCmdSupported()) info.unlockGameFps(p, prefs.getTargetHz())
                    if (p.isNotBlank() && info.isRooted()) info.pinGameToPrimeCore(p)
                    if (info.isRooted()) info.boostInputDispatcher()
                    info.optimizePing()
                    val started = startAlive(ctx)
                    if (!started) {
                        msg = "Ошибка: сервис не запустился"
                    } else {
                        prefs.setActive(true)
                        if (prefs.isOverlayEnabled()) {
                            try { ctx.startService(Intent(ctx, MonitorOverlayService::class.java)) } catch (_: Throwable) {}
                        }
                        if (prefs.isGameOverlay()) {
                            try { ctx.startService(Intent(ctx, GameOverlayService::class.java)) } catch (_: Throwable) {}
                        }
                        OpLog.log("Тайл QS: включено")
                        msg = "TurboBoost: включено"
                    }
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

    private fun startAlive(ctx: android.content.Context): Boolean {
        return try {
            val i = Intent(ctx, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
            true
        } catch (_: Throwable) {
            false
        }
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
