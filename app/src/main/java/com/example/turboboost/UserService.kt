package com.example.turboboost

import android.content.Context
import androidx.annotation.Keep
import com.example.turboboost.IUserService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class UserService() : IUserService.Stub() {

    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText().trim()
            p.waitFor()
            out
        } catch (e: Throwable) {
            ""
        }
    }
}
