package com.example.turboboost

import android.content.Context

/** Подключение к Shizuku из фона (сервис, плитка), когда окна приложения нет. */
object ShizukuBridge {
    fun ensure(ctx: Context, waitMs: Long): Boolean {
        if (SystemReader.isReady()) return true
        try {
            ShizukuHelper.init(ctx.applicationContext)
            if (ShizukuHelper.hasPermission()) ShizukuHelper.bindService()
        } catch (_: Throwable) {
        }
        val end = System.currentTimeMillis() + waitMs
        while (System.currentTimeMillis() < end) {
            if (SystemReader.isReady()) return true
            try {
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                break
            }
        }
        return SystemReader.isReady()
    }
}
