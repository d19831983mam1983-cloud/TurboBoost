package com.example.turboboost

import android.content.Context
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
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
            p.redirectErrorStream(true)
            val out = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText().trim() }
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                out
            } else out
        } catch (e: Throwable) {
            ""
        }
    }
}
