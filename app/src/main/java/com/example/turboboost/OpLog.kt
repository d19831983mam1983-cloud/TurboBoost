package com.example.turboboost

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал операций: кольцевой буфер последних действий приложения.
 * Хранится в SharedPreferences, переживает перезапуск. Виден в Настройки → Журнал.
 */
object OpLog {
    private const val PREF = "turbo_oplog"
    private const val KEY = "entries"
    private const val MAX = 200

    @Volatile private var ctx: Context? = null

    fun init(context: Context) { ctx = context.applicationContext }

    @Synchronized
    fun log(text: String) {
        val c = ctx ?: return
        try {
            val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val arr = JSONArray(sp.getString(KEY, "[]") ?: "[]")
            val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            arr.put("$stamp  $text")
            while (arr.length() > MAX) arr.remove(0)
            sp.edit().putString(KEY, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    fun entries(): List<String> {
        val c = ctx ?: return emptyList()
        return try {
            val arr = JSONArray(c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
            (arr.length() - 1 downTo 0).map { arr.optString(it, "") }
        } catch (_: Throwable) { emptyList() }
    }

    fun clear() {
        try { ctx?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()?.remove(KEY)?.apply() } catch (_: Throwable) {}
    }
}
