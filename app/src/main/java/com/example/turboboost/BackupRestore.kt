package com.example.turboboost

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Экспорт/импорт профилей, списка игр и настроек одним JSON-файлом. */
object BackupRestore {

    fun exportJson(ctx: Context): File? {
        return try {
            val prefs = AppPrefs(ctx)
            val root = JSONObject()
            val prof = JSONArray()
            for (p in prefs.getProfiles()) {
                val o = JSONObject()
                o.put("pkg", p.pkg); o.put("mode", p.mode); o.put("sense", p.sense); o.put("thermal", p.thermal)
                o.put("turbo", p.turbo); o.put("hz", p.hz); o.put("gpuBoost", p.gpuBoost); o.put("bgLimit", p.bgLimit)
                prof.put(o)
            }
            root.put("profiles", prof)
            root.put("games", JSONArray(prefs.getSavedGames()))
            root.put("settings", JSONObject().apply {
                put("hz", prefs.getTargetHz())
                put("mode", prefs.getPerfMode())
                put("sense", prefs.getSenseLevel())
                put("gpu", prefs.isGpuBoost())
                put("bgLimit", prefs.isBackgroundLimit())
            })
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val f = File(dir, "turboboost_backup.json")
            f.writeText(root.toString(2))
            OpLog.log("Бэкап экспортирован: ${f.absolutePath}")
            f
        } catch (e: Throwable) { null }
    }

    fun share(ctx: Context, f: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Экспорт TurboBoost").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) {
            try {
                val i = Intent(Intent.ACTION_VIEW).apply { data = Uri.fromFile(f); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ctx.startActivity(i)
            } catch (_: Throwable) {}
        }
    }

    fun importJson(ctx: Context, uri: Uri): String {
        return try {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return "Файл пустой"
            val root = JSONObject(text)
            val prefs = AppPrefs(ctx)
            var n = 0
            val prof = root.optJSONArray("profiles")
            if (prof != null) {
                for (i in 0 until prof.length()) {
                    val o = prof.optJSONObject(i) ?: continue
                    val pkg = o.optString("pkg", "")
                    if (pkg.isBlank()) continue
                    prefs.saveProfile(GameProfile(pkg, o.optInt("mode", 0), o.optInt("sense", 0), o.optInt("thermal", 0), o.optBoolean("turbo", false), o.optInt("hz", 120), o.optBoolean("gpuBoost", false), o.optBoolean("bgLimit", false)))
                    n++
                }
            }
            val games = root.optJSONArray("games")
            if (games != null) for (i in 0 until games.length()) prefs.saveGame(games.optString(i, ""))
            OpLog.log("Бэкап импортирован: $n профилей")
            "Импортировано профилей: $n"
        } catch (e: Throwable) { "Ошибка импорта: файл повреждён или не бэкап TurboBoost" }
    }
}
