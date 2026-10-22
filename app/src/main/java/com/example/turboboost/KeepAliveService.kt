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

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            val prefs = AppPrefs(this)
            val info = DeviceInfo(this)
            try {
                info.deactivateMaxPerformance()
                info.applySurfaceFlingerBoost(false)
            } catch (e: Throwable) {}
            prefs.setActive(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "TurboBoost", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val prefs = AppPrefs(this)
        val active = prefs.isActive()
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, KeepAliveService::class.java).apply { action = "STOP" }
        val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("TurboBoost")
            .setContentText(if (active) "Оптимизация активна" else "Ожидание")
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_delete, "Выключить", stopPending)
            .addAction(android.R.drawable.ic_menu_view, "Открыть", openPending)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
