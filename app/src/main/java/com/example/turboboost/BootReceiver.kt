package com.example.turboboost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val prefs = AppPrefs(ctx)
            if (!prefs.isActive() && !prefs.isAutoSession() && !prefs.isGameOverlay() && !prefs.isDisplayTweaked()) return
            val i = Intent(ctx, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
            OpLog.log("Boot: восстановление сервиса")
        } catch (_: Throwable) {}
    }
}
