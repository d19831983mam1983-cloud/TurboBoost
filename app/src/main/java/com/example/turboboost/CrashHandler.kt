package com.example.turboboost

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler private constructor(private val app: Application) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        
        val log = """
            Time: $time
            Thread: ${thread.name}
            Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
            Shizuku Mode: ${if (SystemReader.rootMode) "Root" else if (SystemReader.directMode) "Direct" else if (SystemReader.service != null) "UserService" else "None"}
            
            $stack
        """.trimIndent()

        try {
            val f = File(app.filesDir, "last_crash.txt")
            f.writeText(log)
        } catch (e: Throwable) {}

        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install(app: Application) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(app))
        }
    }
}
