package com.example.turboboost

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SystemReader {

    @Volatile var service: IUserService? = null
    @Volatile var directMode: Boolean = false
    @Volatile var rootMode: Boolean = false

    private const val TIMEOUT_MS = 5000L
    private val executor = Executors.newFixedThreadPool(4)

    fun checkRoot(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val errThread = Thread {
                try { p.errorStream.bufferedReader().readText() } catch (e: Throwable) {}
            }
            errThread.isDaemon = true
            errThread.start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("uid=0")
        } catch (e: Throwable) {
            false
        }
    }

    fun read(cmd: String): String = read(cmd, TIMEOUT_MS)

    fun read(cmd: String, timeoutMs: Long): String {
        val task = Callable {
            when {
                rootMode -> execRoot(cmd)
                directMode -> ShizukuHelper.execDirect(cmd)
                else -> service?.exec(cmd) ?: ""
            }
        }
        return try {
            val future = executor.submit(task)
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            ""
        }
    }

    private fun execRoot(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val errThread = Thread {
                try { p.errorStream.bufferedReader().readText() } catch (e: Throwable) {}
            }
            errThread.isDaemon = true
            errThread.start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out
        } catch (e: Throwable) {
            ""
        }
    }

    fun test(path: String): Boolean = read("test -e $path && echo ok").contains("ok")
    fun testWrite(path: String): Boolean = read("test -w $path && echo ok").contains("ok")

    fun isReady(): Boolean = rootMode || service != null || (directMode && ShizukuHelper.isAvailable())
}
