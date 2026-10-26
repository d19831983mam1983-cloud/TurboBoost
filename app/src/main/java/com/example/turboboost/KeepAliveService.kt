package com.example.turboboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class KeepAliveService : Service() {
    private val channelId = "turboboost_alive"
    private val notificationId = 1002
    private var guard: SessionGuard? = null

    override fun onCreate() { super.onCreate(); createChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            val prefs = AppPrefs(this)
            guard?.stop(); guard = null
            val appCtx = applicationContext
            // тяжёлый откат (shell-команды) не на главном потоке, иначе ANR
            Thread {
                try {
                    if (ShizukuBridge.ensure(appCtx, 1500L)) DeviceInfo(appCtx).revertAll(prefs.getLastPackage(), prefs)
                } catch (e: Throwable) {}
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
                    stopSelf()
                }
            }.start()
            return START_NOT_STICKY
        }
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(notificationId, notification)
        } catch (e: Throwable) {
            // Android 12+: foreground start from background is forbidden; HiOS/Transsion also kills aggressively
            try { stopSelf() } catch (_: Throwable) {}
            return START_NOT_STICKY
        }
        // авто-сессия живёт здесь: сервис работает, пока активна оптимизация
        val prefs = AppPrefs(this)
        if (prefs.isAutoSession()) {
            if (guard == null) guard = SessionGuard(this).also { it.start() }
        } else {
            guard?.stop(); guard = null
        }
        return START_NOT_STICKY
    }

    /**
     * Смахнули приложение из recent-ов. Сервис переднего плана живёт дальше, поэтому оптимизацию не откатываем:
     * иначе она выключалась бы у всех, кто закрывает TurboBoost перед игрой. Если оптимизация и так выключена, сервис не нужен.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            if (!AppPrefs(this).isActive()) {
                guard?.stop(); guard = null
                stopSelf()
            }
        } catch (_: Throwable) {}
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        guard?.stop(); guard = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "TurboBoost", NotificationManager.IMPORTANCE_LOW); channel.setShowBadge(false); val manager = getSystemService(NotificationManager::class.java); manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val prefs = AppPrefs(this); val active = prefs.isActive()
        val openIntent = Intent(this, MainActivity::class.java); val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, KeepAliveService::class.java).apply { action = "STOP" }; val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId).setContentTitle("TurboBoost").setContentText(if (active) "Оптимизация активна" else "Ожидание").setSmallIcon(R.drawable.ic_bolt).setContentIntent(openPending).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).addAction(android.R.drawable.ic_delete, "Выключить", stopPending).addAction(android.R.drawable.ic_menu_view, "Открыть", openPending).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
