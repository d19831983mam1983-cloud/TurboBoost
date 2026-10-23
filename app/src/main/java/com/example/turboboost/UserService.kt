package com.example.turboboost

import android.content.Context
import androidx.annotation.Keep
import com.example.turboboost.IUserService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

@Keep
class UserService : IUserService.Stub {

    constructor() : super()

    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val errThread = Thread {
                try { p.errorStream.bufferedReader().readText() } catch (e: Throwable) {}
            }
            errThread.isDaemon = true
            errThread.start()
            val out = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText().trim() }
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly()
            }
            out
        } catch (e: Throwable) {
            ""
        }
    }
}
