package com.example.turboboost

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SystemReader {
    @Volatile var service: IUserService? = null
    @Volatile var directMode: Boolean = false
    @Volatile var rootMode: Boolean = false
    private const val TIMEOUT_MS = 5000L
    private val executor = Executors.newCachedThreadPool()

    fun checkRoot(): Boolean {
        return try {
            val proc: Process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val sb = StringBuilder()
            val outT = Thread { try { sb.append(proc.inputStream.bufferedReader().readText()) } catch (_: Throwable) {} }
            val errT = Thread { try { proc.errorStream.bufferedReader().readText() } catch (_: Throwable) {} }
            outT.isDaemon = true
            errT.isDaemon = true
            outT.start()
            errT.start()
            val finished = proc.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                false
            } else {
                outT.join(500)
                val res = sb.toString().contains("uid=0")
                proc.destroy()
                res
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun read(cmd: String): String = read(cmd, TIMEOUT_MS)

    fun read(cmd: String, timeoutMs: Long): String {
        if (executor.isShutdown) return ""
        val future = try {
            executor.submit(Callable {
                try {
                    when {
                        rootMode -> execRoot(cmd, timeoutMs)
                        directMode -> ShizukuHelper.execDirect(cmd)
                        else -> {
                            val s = service
                            if (s == null) "" else try { s.exec(cmd) } catch (_: Throwable) { "" }
                        }
                    }
                } catch (_: Throwable) { "" }
            })
        } catch (_: Throwable) { return "" }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            try { future.cancel(true) } catch (_: Throwable) {}
            ""
        }
    }

    private fun execRoot(cmd: String, timeoutMs: Long): String {
        var p: Process? = null
        return try {
            p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val proc = p
            val sb = StringBuilder()
            val errThread = Thread {
                try { proc.errorStream.bufferedReader().readText() } catch (_: Throwable) {}
            }.apply { isDaemon = true; start() }
            val outThread = Thread {
                try { sb.append(proc.inputStream.bufferedReader().readText()) } catch (_: Throwable) {}
            }.apply { isDaemon = true; start() }
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                try { proc.destroyForcibly() } catch (_: Throwable) {}
                try { outThread.join(500) } catch (_: Throwable) {}
                try { errThread.join(200) } catch (_: Throwable) {}
                ""
            } else {
                try { outThread.join(2000) } catch (_: Throwable) {}
                try { errThread.join(200) } catch (_: Throwable) {}
                sb.toString().trim()
            }
        } catch (_: Throwable) {
            ""
        } finally {
            try { p?.destroy() } catch (_: Throwable) {}
        }
    }

    fun test(path: String): Boolean = read("test -e $path && echo ok").contains("ok")
    fun testWrite(path: String): Boolean = read("test -w $path && echo ok").contains("ok")
    fun isReady(): Boolean = rootMode || service != null || (directMode && ShizukuHelper.isAvailable())
}
