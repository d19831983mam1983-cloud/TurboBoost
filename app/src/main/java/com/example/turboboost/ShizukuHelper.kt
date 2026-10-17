package com.example.turboboost

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val REQUEST_CODE = 1001

    var onPermissionResult: ((Boolean) -> Unit)? = null

    private val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE) {
            onPermissionResult?.invoke(result == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun init() {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun destroy() {
        Shizuku.removeRequestPermissionResultListener(listener)
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
}
