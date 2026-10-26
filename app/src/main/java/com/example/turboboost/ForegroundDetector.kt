package com.example.turboboost

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

/**
 * Единый определитель приложения на экране.
 * 1) При доступном Shizuku: dumpsys window (точно, в фоновом потоке, ответ кэшируется).
 * 2) Иначе по событиям UsageStats (нужен «Доступ к статистике использования»).
 * Раньше использовался lastTimeUsed: он обновляется только когда приложение уходит с экрана,
 * поэтому через 15 секунд игры определение пропадало, а оверлей оставался поверх всего.
 */
object ForegroundDetector {
    @Volatile private var cached: String? = null
    @Volatile private var lastAsk = 0L
    private var worker: Thread? = null
    private var lastQuery = 0L
    private var usageCurrent: String? = null

    /** Системные окна поверх игры (шторка, клавиатура, диалоги прав): это не выход из игры. */
    private fun isTransient(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "android" || p == "com.android.systemui" ||
            p.contains("permissioncontroller") || p.contains("packageinstaller") ||
            p.contains("inputmethod") || p.contains("keyboard")
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        return try {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    /** Можно ли вообще определить приложение на экране. */
    fun available(ctx: Context): Boolean = SystemReader.isReady() || hasUsageAccess(ctx)

    /** Пакет на экране или null, если определить не удалось. Безопасно вызывать из главного потока. */
    fun current(ctx: Context): String? {
        lastAsk = System.currentTimeMillis()
        if (SystemReader.isReady()) {
            ensureWorker(ctx.applicationContext)
            val c = cached
            if (c != null) return c
        }
        return if (hasUsageAccess(ctx)) fromUsageEvents(ctx) else cached
    }

    private fun ensureWorker(app: Context) {
        if (worker?.isAlive == true) return
        val t = Thread {
            val info = DeviceInfo(app)
            while (System.currentTimeMillis() - lastAsk < 15000L) {
                try {
                    if (SystemReader.isReady()) {
                        val p = info.foregroundPackage()
                        if (p.isNotBlank() && !isTransient(p)) cached = p
                    }
                } catch (_: Throwable) {
                }
                try {
                    Thread.sleep(2000L)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        t.isDaemon = true
        worker = t
        t.start()
    }

    @Synchronized
    @Suppress("DEPRECATION")
    private fun fromUsageEvents(ctx: Context): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return usageCurrent
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - 6L * 3600L * 1000L else lastQuery - 2000L
        try {
            val events = usm.queryEvents(from, now) ?: return usageCurrent
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> if (!isTransient(e.packageName)) usageCurrent = e.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (usageCurrent == e.packageName) usageCurrent = null
                }
            }
            lastQuery = now
        } catch (_: Throwable) {
        }
        return usageCurrent
    }
}
