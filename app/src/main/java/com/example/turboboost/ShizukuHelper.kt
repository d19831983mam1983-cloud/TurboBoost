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
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object ShizukuHelper {
    private const val REQUEST_CODE = 1001
    private const val BIND_TIMEOUT_MS = 10_000L
    private const val EXEC_TIMEOUT_MS = 5_000L

    @Volatile var onPermissionResult: ((Boolean) -> Unit)? = null
    @Volatile var onServiceReady: ((Boolean) -> Unit)? = null
    @Volatile var onBindTimeout: (() -> Unit)? = null

    @Volatile private var bound = false
    @Volatile private var destroyed = false
    @Volatile private var binding = false
    @Volatile private var unbindAt = 0L
    private var appContext: Context? = null
    private var initialized = false
    private var pendingTimeout: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private fun postMain(block: () -> Unit) {
        val run = Runnable { try { block() } catch (e: Throwable) { e.printStackTrace() } }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else handler.post(run)
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == REQUEST_CODE) {
                val granted = result == PackageManager.PERMISSION_GRANTED
                postMain { onPermissionResult?.invoke(granted) }
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        SystemReader.service = null
        bound = false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            cancelTimeout()
            binding = false
            if (destroyed) return
            if (binder == null || !binder.pingBinder()) {
                postMain { enableDirectMode() }
                return
            }
            val svc = IUserService.Stub.asInterface(binder)
            if (svc == null) {
                postMain { enableDirectMode() }
                return
            }
            SystemReader.service = svc
            SystemReader.directMode = false
            bound = true
            postMain { onServiceReady?.invoke(true) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cancelTimeout()
            binding = false
            if (destroyed || System.currentTimeMillis() - unbindAt < 2000L) return
            SystemReader.service = null
            bound = false
            postMain { enableDirectMode() }
        }
    }

    private fun args(ctx: Context) = Shizuku.UserServiceArgs(
        ComponentName(ctx.packageName, UserService::class.java.name)
    ).daemon(false).processNameSuffix("exec").debuggable(false).version(1)

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        destroyed = false
        if (initialized) return
        initialized = true
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (e: Throwable) { e.printStackTrace() }
    }

    fun destroy() {
        destroyed = true
        onPermissionResult = null
        onServiceReady = null
        onBindTimeout = null
        cancelTimeout()
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {}
        unbindService()
        initialized = false
    }

    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try {
        when {
            !Shizuku.pingBinder() -> false
            Shizuku.isPreV11() -> true
            else -> Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    } catch (_: Throwable) { false }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) { postMain { onPermissionResult?.invoke(false) }; return }
            if (Shizuku.isPreV11() || hasPermission()) { postMain { onPermissionResult?.invoke(true) }; return }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            postMain { onPermissionResult?.invoke(false) }
        }
    }

    fun bindService() {
        val ctx = appContext ?: return
        if (destroyed) return
        if (bound) { postMain { onServiceReady?.invoke(true) }; return }
        if (binding) return
        if (!hasPermission()) { postMain { onServiceReady?.invoke(false) }; return }
        binding = true
        startTimeout()
        try {
            Shizuku.bindUserService(args(ctx), connection)
        } catch (e: Throwable) {
            e.printStackTrace()
            cancelTimeout()
            postMain { enableDirectMode() }
        }
    }

    fun unbindService() {
        val ctx = appContext ?: return
        cancelTimeout()
        unbindAt = System.currentTimeMillis()
        binding = false
        try { Shizuku.unbindUserService(args(ctx), connection, true) } catch (_: Throwable) {}
        SystemReader.service = null
        bound = false
    }

    private fun enableDirectMode() {
        binding = false
        if (destroyed) return
        SystemReader.service = null
        SystemReader.directMode = true
        onServiceReady?.invoke(true)
    }

    private fun startTimeout() {
        cancelTimeout()
        val r = Runnable {
            if (!bound && !destroyed) { enableDirectMode(); onBindTimeout?.invoke() }
        }
        pendingTimeout = r
        handler.postDelayed(r, BIND_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        pendingTimeout?.let { handler.removeCallbacks(it) }
        pendingTimeout = null
    }    private val newProcessString by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java, Array<String>::class.java, String::class.java
            ).apply { isAccessible = true }
        } catch (_: Throwable) { null }
    }

    private val newProcessFile by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java, Array<String>::class.java, File::class.java
            ).apply { isAccessible = true }
        } catch (_: Throwable) { null }
    }

    private fun startProcess(cmd: Array<String>, env: Array<String>?, dir: String?): Process? {
        newProcessString?.let { m ->
            try { return m.invoke(null, cmd, env, dir) as? Process } catch (_: Throwable) {}
        }
        newProcessFile?.let { m ->
            try { return m.invoke(null, cmd, env, dir?.let { File(it) }) as? Process } catch (_: Throwable) {}
        }
        return null
    }

    fun execDirect(cmd: String): String {
        val candidates = listOf(
            arrayOf("sh", "-c", cmd),
            arrayOf(cmd)
        )
        val envs = listOf(
            null,
            arrayOf("PATH=/system/bin:/system/xbin:/sbin:/vendor/bin:/apex/com.android.runtime/bin")
        )
        for (c in candidates) {
            for (env in envs) {
                val proc = startProcess(c, env, null) ?: continue
                val out = readProcess(proc, EXEC_TIMEOUT_MS)
                if (out.isNotEmpty()) return out
            }
        }
        return ""
    }

    private fun readProcess(proc: Process, timeoutMs: Long): String {
        val sb = StringBuilder()
        val errThread = thread(isDaemon = true) {
            try { BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText() } } catch (_: Throwable) {}
        }
        val reader = thread(isDaemon = true) {
            try { BufferedReader(InputStreamReader(proc.inputStream)).use { sb.append(it.readText()) } } catch (_: Throwable) {}
        }
        return try {
            if (!proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
                reader.join(300)
                errThread.join(100)
                ""
            } else {
                reader.join(3000)
                errThread.join(100)
                sb.toString().trim()
            }
        } catch (_: Throwable) {
            try { proc.destroyForcibly() } catch (_: Throwable) {}
            ""
        } finally {
            try { proc.inputStream.close() } catch (_: Throwable) {}
            try { proc.errorStream.close() } catch (_: Throwable) {}
            try { proc.outputStream.close() } catch (_: Throwable) {}
            try { proc.destroy() } catch (_: Throwable) {}
        }
    }
}
