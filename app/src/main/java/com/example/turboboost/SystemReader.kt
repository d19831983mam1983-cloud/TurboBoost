package com.example.turboboost

import java.io.BufferedReader
import java.io.InputStreamReader

object SystemReader {

    fun read(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText().trim()
            p.waitFor()
            out
        } catch (e: Throwable) {
            ""
        }
    }

    fun test(path: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "test -e $path"))
            p.waitFor() == 0
        } catch (e: Throwable) {
            false
        }
    }
}
