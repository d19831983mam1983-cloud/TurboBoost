package com.example.turboboost

import com.example.turboboost.IUserService
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SystemReader {

    @Volatile
    var service: IUserService? = null

    private const val TIMEOUT_MS = 5000L
    private val executor = Executors.newSingleThreadExecutor()

    fun read(cmd: String): String {
        val s = service ?: return ""
        return try {
            val future = executor.submit(Callable { s.exec(cmd) })
            future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) {
            try { executor.shutdownNow() } catch (t: Throwable) {}
            ""
        }
    }

    fun readMulti(commands: List<String>): List<String> = commands.map { read(it) }

    fun test(path: String): Boolean = read("test -e $path && echo ok").contains("ok")

    fun testWrite(path: String): Boolean = read("test -w $path && echo ok").contains("ok")

    fun isReady(): Boolean = service != null
}
