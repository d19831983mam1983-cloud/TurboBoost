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
        var p: Process? = null
        return try {
            p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val proc = p
            val sb = StringBuilder()
            val outT = Thread { try { sb.append(proc.inputStream.bufferedReader().readText()) } catch (e: Throwable) {} }
            val errT = Thread { try { proc.errorStream.bufferedReader().readText() } catch (e: Throwable) {} }
            outT.isDaemon = true; errT.isDaemon = true; outT.start(); errT.start()
            if (!proc.waitFor(3, TimeUnit.SECONDS)) { proc.destroyForcibly(); false }
            else { outT.join(500); sb.toString().contains("uid=0") }
        } catch (e: Throwable) { false }
        finally { try { p?.destroy() } catch (_: Throwable) {} }
    }

    fun read(cmd: String): String = read(cmd, TIMEOUT_MS)

    fun read(cmd: String, timeoutMs: Long): String {
        val task = Callable { when { rootMode -> execRoot(cmd); directMode -> ShizukuHelper.execDirect(cmd); else -> service?.exec(cmd) ?: "" } }
        var future: java.util.concurrent.Future<String>? = null
        return try {
            future = executor.submit(task)
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            try { future?.cancel(true) } catch (t: Throwable) {}
            ""
        }
    }

    private fun execRoot(cmd: String): String {
        var p: Process? = null
        return try {
            p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val proc = p
            val sb = StringBuilder()
            val outT = Thread { try { sb.append(proc.inputStream.bufferedReader().readText()) } catch (e: Throwable) {} }
            val errT = Thread { try { proc.errorStream.bufferedReader().readText() } catch (e: Throwable) {} }
            outT.isDaemon = true; errT.isDaemon = true; outT.start(); errT.start()
            if (!proc.waitFor(5, TimeUnit.SECONDS)) { proc.destroyForcibly(); "" }
            else { outT.join(1000); sb.toString().trim() }
        } catch (e: Throwable) { "" }
        finally { try { p?.destroy() } catch (_: Throwable) {} }
    }

    fun test(path: String): Boolean = read("test -e $path && echo ok").contains("ok")
    fun testWrite(path: String): Boolean = read("test -w $path && echo ok").contains("ok")
    fun isReady(): Boolean = rootMode || service != null || (directMode && ShizukuHelper.isAvailable())
}
