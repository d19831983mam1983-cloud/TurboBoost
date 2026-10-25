package com.example.turboboost

/** Игры, которые нельзя добавлять в приложение (PUBG Mobile и все его версии). */
object GameBlocklist {
    const val MESSAGE = "Эту игру добавить нельзя"

    fun isBlocked(pkg: String?, label: String? = null): Boolean {
        val p = (pkg ?: "").trim().lowercase()
        val l = (label ?: "").trim().lowercase()
        if (p.contains("pubg")) return true
        if (p == "com.tencent.ig" || p == "com.tencent.iglite") return true
        if (l.contains("pubg")) return true
        return false
    }
}
