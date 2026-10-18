package com.example.turboboost

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuHelper {

    private const val REQUEST_CODE = 1001
    private const val BIND_TIMEOUT_MS = 8000L
    var onPermissionResult: ((Boolean) -> Unit)? = null
    var onServiceReady: ((Boolean) -> Unit)? = null

    private var bound = false
    private var appContext: Context? = null

    private val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE) {
            onPermissionResult?.invoke(result == PackageManager.PERMISSION_GRANTED)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            try {
                SystemReader.service = IUserService.Stub.asInterface(binder)
                bound = true
                onServiceReady?.invoke(true)
            } catch (e: Throwable) {
                onServiceReady?.invoke(false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            SystemReader.service = null
            bound = false
            onServiceReady?.invoke(false)
        }
    }

    private fun args(ctx: Context): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, UserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("exec")
            .debuggable(false)
            .version(1)
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
        unbindService()
    }

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return true
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                onPermissionResult?.invoke(false)
                return
            }
            if (Shizuku.isPreV11() || hasPermission()) {
                onPermissionResult?.invoke(true)
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            onPermissionResult?.invoke(false)
        }
    }

    fun bindService() {
        val ctx = appContext ?: return
        if (bound || !hasPermission()) {
            onServiceReady?.invoke(bound)
            return
        }
        try {
            Shizuku.bindUserService(args(ctx), connection)
        } catch (e: Throwable) {
            onServiceReady?.invoke(false)
        }
    }

    fun unbindService() {
        val ctx = appContext ?: return
        if (!bound) return
        try {
            Shizuku.unbindUserService(args(ctx), connection, true)
        } catch (e: Throwable) {}
        SystemReader.service = null
        bound = false
    }
}
