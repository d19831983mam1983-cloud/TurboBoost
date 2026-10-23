package com.example.turboboost

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler private constructor(private val app: Application) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val log = "Time: $time\nThread: ${thread.name}\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n\n$stack"
        
        try {
            val f = File(app.filesDir, "last_crash.txt")
            f.writeText(log)
        } catch (e: Throwable) {}

        mainHandler.post {
            try {
                showCrashScreen(log)
            } catch (e: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        
        Thread.sleep(1000)
        android.os.Process.killProcess(android.os.Process.myPid())
        kotlin.system.exitProcess(1)
    }

    private fun showCrashScreen(log: String) {
        var currentActivity: Activity? = null
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { currentActivity = activity }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { currentActivity = activity }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivityStarted(activity: Activity) { currentActivity = activity }
        })
        
        val act = currentActivity ?: return
        
        act.runOnUiThread {
            val root = FrameLayout(act).apply {
                setBackgroundColor(Color.parseColor("#0A0A0A"))
            }

            val scroll = ScrollView(act)
            val box = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 96, 48, 48)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            scroll.addView(box)
            root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

            box.addView(TextView(act).apply {
                text = "TurboBoost крашнулся"
                setTextColor(Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 24)
            })

            box.addView(TextView(act).apply {
                text = "Отправь лог разработчику, чтобы он починил баг."
                setTextColor(Color.parseColor("#AAFFFFFF"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 32)
            })

            val logView = TextView(act).apply {
                text = log
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 11f
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                setTextIsSelectable(true)
            }
            box.addView(logView, LinearLayout.LayoutParams(-1, 600))

            val shareBtn = TextView(act).apply {
                text = "ПОДЕЛИТЬСЯ ЛОГОМ"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(48, 32, 48, 32)
                setBackgroundColor(Color.parseColor("#4F46E5"))
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "TurboBoost Crash Log")
                        putExtra(Intent.EXTRA_TEXT, log)
                    }
                    act.startActivity(Intent.createChooser(intent, "Поделиться логом"))
                }
            }
            box.addView(shareBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24 })

            val copyBtn = TextView(act).apply {
                text = "СКОПИРОВАТЬ"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(48, 32, 48, 32)
                setBackgroundColor(Color.parseColor("#1F2937"))
                setOnClickListener {
                    val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Crash Log", log))
                    Toast.makeText(act, "Скопировано", Toast.LENGTH_SHORT).show()
                }
            }
            box.addView(copyBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })

            val closeBtn = TextView(act).apply {
                text = "ЗАКРЫТЬ"
                setTextColor(Color.parseColor("#AAFFFFFF"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(48, 32, 48, 32)
                setOnClickListener {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(1)
                }
            }
            box.addView(closeBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })

            act.setContentView(root)
        }
    }

    companion object {
        fun install(app: Application) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(app))
        }
    }
}
