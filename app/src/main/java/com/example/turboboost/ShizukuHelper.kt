package com.example.turboboost

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

    private const val REQUEST_CODE = 1001
    var onPermissionResult: ((Boolean) -> Unit)? = null
    var onServiceReady: ((Boolean) -> Unit)? = null
    var onBindTimeout: (() -> Unit)? = null

    private var appContext: Context? = null
    private val handler = Handler(Looper.getMainLooper())

    private val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE) {
            onPermissionResult?.invoke(result == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {}
    }

    fun destroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {}
    }

    fun isAvailable(): Boolean {
        return try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return true
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) { false }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) { onPermissionResult?.invoke(false); return }
            if (Shizuku.isPreV11() || hasPermission()) { onPermissionResult?.invoke(true); return }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) { onPermissionResult?.invoke(false) }
    }

    fun bindService() {
        if (!hasPermission()) {
            onServiceReady?.invoke(false)
            return
        }
        enableDirectMode()
    }

    fun unbindService() {}

    private fun enableDirectMode() {
        SystemReader.directMode = true
        SystemReader.service = null
        onServiceReady?.invoke(true)
    }

    fun execDirect(cmd: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process ?: return ""
            val errThread = Thread { try { process.errorStream.bufferedReader().readText() } catch (e: Throwable) {} }
            errThread.isDaemon = true
            errThread.start()
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            process.waitFor()
            out
        } catch (e: Throwable) { "" }
    }
}
