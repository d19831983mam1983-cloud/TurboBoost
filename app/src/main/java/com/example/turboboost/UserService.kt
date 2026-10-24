package com.example.turboboost

import android.content.Context
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

@Keep
class UserService : IUserService.Stub {
    constructor() : super()
    @Keep constructor(context: Context) : this()

    @Keep override fun destroy() { exitProcess(0) }

    @Keep override fun exec(cmd: String): String {
        var p: Process? = null
        return try {
            p = ProcessBuilder("sh", "-c", cmd).start()
            val proc = p
            val sb = StringBuilder()
            val errThread = thread(isDaemon = true) {
                try { BufferedReader(InputStreamReader(proc.errorStream)).use { it.readText() } } catch (_: Exception) {}
            }
            val reader = thread(isDaemon = true) {
                try { BufferedReader(InputStreamReader(proc.inputStream)).use { sb.append(it.readText()) } } catch (_: Exception) {}
            }
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                reader.join(500)
                errThread.join(200)
                return ""
            }
            reader.join(5000)
            errThread.join(200)
            sb.toString().trim()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            ""
        } catch (e: Exception) {
            ""
        } finally {
            p?.let {
                try { it.inputStream.close() } catch (_: Exception) {}
                try { it.errorStream.close() } catch (_: Exception) {}
                try { it.outputStream.close() } catch (_: Exception) {}
                try { it.destroy() } catch (_: Exception) {}
            }
        }
    }
}
