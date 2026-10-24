package com.example.turboboost

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}

/**
 * Catches uncaught exceptions, writes a detailed crash report to internal + external
 * storage, and allows showing / sharing it on the next launch.
 */
object CrashHandler {
    private const val TAG = "TurboBoostCrash"
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 12000

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildReport(app, thread, throwable)
                // Always write to internal filesDir (reliable)
                File(app.filesDir, FILE_NAME).writeText(report)
                // Also try app-specific external dir (visible in file managers)
                try {
                    app.getExternalFilesDir(null)?.let { dir ->
                        File(dir, FILE_NAME).writeText(report)
                    }
                } catch (_: Throwable) {}
                Log.e(TAG, "=== CRASH REPORT ===\n$report")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to save crash report", t)
            }
            // Let the system / previous handler kill the process
            prev?.uncaughtException(thread, throwable)
        }
    }

    private fun buildReport(ctx: Context, thread: Thread, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val stack = sw.toString()

        val pkgInfo = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        } catch (_: Throwable) { null }

        val versionName = pkgInfo?.versionName ?: "?"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            pkgInfo?.longVersionCode?.toString() ?: "?"
        } else {
            @Suppress("DEPRECATION")
            pkgInfo?.versionCode?.toString() ?: "?"
        }

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val mem = try {
            val rt = Runtime.getRuntime()
            val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
            val max = rt.maxMemory() / 1024 / 1024
            "Heap used/max: ${used}MB / ${max}MB"
        } catch (_: Throwable) { "Heap: n/a" }

        return buildString {
            appendLine("===== TurboBoost Crash Report =====")
            appendLine("Time: $time")
            appendLine("App: ${ctx.packageName} v$versionName ($versionCode)")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Thread: ${thread.name}")
            appendLine(mem)
            appendLine()
            appendLine("--- Stacktrace ---")
            append(stack)
        }
    }

    /** Returns the saved crash text (or null). Does not delete the file. */
    fun pending(ctx: Context): String? = try {
        val f = File(ctx.filesDir, FILE_NAME)
        if (f.exists()) f.readText().take(MAX_CHARS) else null
    } catch (_: Throwable) { null }

    fun clear(ctx: Context) {
        try { File(ctx.filesDir, FILE_NAME).delete() } catch (_: Throwable) {}
        try { ctx.getExternalFilesDir(null)?.let { File(it, FILE_NAME).delete() } } catch (_: Throwable) {}
    }

    /** Full path to the external copy (if any) so user can find it in a file manager. */
    fun externalPath(ctx: Context): String? = try {
        ctx.getExternalFilesDir(null)?.let { File(it, FILE_NAME).absolutePath }
    } catch (_: Throwable) { null }
}
