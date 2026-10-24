package com.example.turboboost

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}

object CrashHandler {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                val text = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                    "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                    "Thread: ${t.name}\n\n$sw"
                File(app.filesDir, FILE).writeText(text)
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun pending(ctx: Context): String? = try {
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) f.readText().take(6000) else null
    } catch (_: Throwable) { null }

    fun clear(ctx: Context) { try { File(ctx.filesDir, FILE).delete() } catch (_: Throwable) {} }
}
