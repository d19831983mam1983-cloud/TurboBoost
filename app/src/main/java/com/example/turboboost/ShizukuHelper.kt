package com.example.turboboost

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {
    private const val REQUEST_CODE = 1001
    private const val BIND_TIMEOUT_MS = 10000L
    var onPermissionResult: ((Boolean) -> Unit)? = null
    var onServiceReady: ((Boolean) -> Unit)? = null
    var onBindTimeout: (() -> Unit)? = null
    private var bound = false; private var appContext: Context? = null
    private var pendingTimeout: Runnable? = null; private val handler = Handler(Looper.getMainLooper())

    private val listener = Shizuku.OnRequestPermissionResultListener { code, result -> if (code == REQUEST_CODE) onPermissionResult?.invoke(result == PackageManager.PERMISSION_GRANTED) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            cancelTimeout()
            try { SystemReader.service = IUserService.Stub.asInterface(binder); SystemReader.directMode = false; bound = true; onServiceReady?.invoke(true) } catch (e: Throwable) { onServiceReady?.invoke(false) }
        }
        override fun onServiceDisconnected(name: ComponentName?) { cancelTimeout(); SystemReader.service = null; bound = false; onServiceReady?.invoke(false) }
    }

    private fun args(ctx: Context): Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, UserService::class.java.name)).daemon(false).processNameSuffix("exec").debuggable(false).version(1)

    fun init(ctx: Context) { appContext = ctx.applicationContext; try { Shizuku.addRequestPermissionResultListener(listener) } catch (e: Throwable) {} }
    fun destroy() { cancelTimeout(); try { Shizuku.removeRequestPermissionResultListener(listener) } catch (e: Throwable) {}; unbindService() }
    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun hasPermission(): Boolean {
        return try { if (!Shizuku.pingBinder()) return false; if (Shizuku.isPreV11()) return true; Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
    }

    fun requestPermission() {
        try { if (!Shizuku.pingBinder()) { onPermissionResult?.invoke(false); return }; if (Shizuku.isPreV11() || hasPermission()) { onPermissionResult?.invoke(true); return }; Shizuku.requestPermission(REQUEST_CODE) } catch (e: Throwable) { onPermissionResult?.invoke(false) }
    }

    fun bindService() {
        val ctx = appContext ?: return
        if (bound || !hasPermission()) { onServiceReady?.invoke(bound); return }
        startTimeout()
        try { Shizuku.bindUserService(args(ctx), connection) } catch (e: Throwable) { cancelTimeout(); enableDirectMode() }
    }

    fun unbindService() {
        val ctx = appContext ?: return; if (!bound) return
        try { Shizuku.unbindUserService(args(ctx), connection, true) } catch (e: Throwable) {}
        SystemReader.service = null; bound = false
    }

    private fun enableDirectMode() { SystemReader.directMode = true; onServiceReady?.invoke(true) }
    private fun startTimeout() { cancelTimeout(); val r = Runnable { if (!bound) { enableDirectMode(); onBindTimeout?.invoke() } }; pendingTimeout = r; handler.postDelayed(r, BIND_TIMEOUT_MS) }
    private fun cancelTimeout() { pendingTimeout?.let { handler.removeCallbacks(it) }; pendingTimeout = null }

    fun execDirect(cmd: String): String {
        val variants = listOf(
            Triple(arrayOf<String>(), arrayOf<String>(), ""),
            Triple(arrayOf<String>(), arrayOf<String>(), ""),
            Triple(arrayOf<String>(), arrayOf<String>(), "")
        )
        val attempts = listOf(
            { invokeNewProcess(arrayOf("sh", "-c", cmd), null, null) },
            { invokeNewProcess(arrayOf(cmd), null, null) },
            { invokeNewProcess(arrayOf("sh", "-c", cmd), arrayOf("PATH=/system/bin:/system/xbin:/sbin"), null) }
        )
        for (a in attempts) {
            val r = a()
            if (r.isNotEmpty()) return r
        }
        return ""
    }

    private fun invokeNewProcess(cmd: Array<String>, env: Array<String>?, dir: String?): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, cmd, env, dir) as? Process ?: return ""
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            process.waitFor()
            out
        } catch (e: Throwable) {
            try {
                val m2 = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, java.io.File::class.java)
                m2.isAccessible = true
                val process = m2.invoke(null, cmd, env, dir?.let { java.io.File(it) }) as? Process ?: return ""
                val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
                process.waitFor()
                out
            } catch (t: Throwable) { "" }
        }
    }
}
