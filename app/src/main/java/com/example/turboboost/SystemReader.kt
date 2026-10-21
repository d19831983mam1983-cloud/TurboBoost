package com.example.turboboost

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SystemReader {

    @Volatile
    var service: IUserService? = null

    @Volatile
    var directMode: Boolean = false

    private const val TIMEOUT_MS = 5000L
    private val executor = Executors.newFixedThreadPool(4)

    fun read(cmd: String): String = read(cmd, TIMEOUT_MS)

    fun read(cmd: String, timeoutMs: Long): String {
        val task = Callable {
            if (directMode) ShizukuHelper.execDirect(cmd)
            else service?.exec(cmd) ?: ""
        }
        return try {
            val future = executor.submit(task)
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            ""
        }
    }

    fun test(path: String): Boolean = read("test -e $path && echo ok").contains("ok")

    fun testWrite(path: String): Boolean = read("test -w $path && echo ok").contains("ok")

    fun tryWrite(path: String, value: String): Boolean {
        if (!testWrite(path)) return false
        read("echo $value > $path 2>/dev/null")
        return true
    }

    fun readMulti(commands: List<String>): List<String> = commands.map { read(it) }

    fun isReady(): Boolean = service != null || (directMode && ShizukuHelper.isAvailable())
}
