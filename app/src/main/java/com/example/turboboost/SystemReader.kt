package com.example.turboboost

import com.example.turboboost.IUserService

object SystemReader {

    @Volatile
    var service: IUserService? = null

    fun read(cmd: String): String {
        val s = service ?: return ""
        return try {
            s.exec(cmd)
        } catch (e: Throwable) {
            ""
        }
    }

    fun test(path: String): Boolean {
        return read("test -e $path && echo ok").contains("ok")
    }

    fun isReady(): Boolean = service != null
}
