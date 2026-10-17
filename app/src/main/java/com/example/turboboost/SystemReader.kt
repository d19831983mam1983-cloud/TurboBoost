package com.example.turboboost

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object SystemReader {

    fun read(cmd: String): String {
        return if (ShizukuHelper.hasPermission()) {
            execViaShizuku(cmd)
        } else {
            execViaRuntime(cmd)
        }
    }

    fun test(path: String): Boolean {
        val out = read("test -e $path && echo ok")
        return out.contains("ok")
    }

    private fun execViaRuntime(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText().trim()
            p.waitFor()
            out
        } catch (e: Throwable) {
            ""
        }
    }

    private fun execViaShizuku(cmd: String): String {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as rikka.shizuku.ShizukuRemoteProcess
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            process.waitFor()
            out
        } catch (e: Throwable) {
            execViaRuntime(cmd)
        }
    }
}
