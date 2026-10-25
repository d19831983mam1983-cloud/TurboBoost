package com.example.turboboost

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

data class Account(
    val id: Long,
    val nick: String,
    val salt: String,
    val hash: String,
    val role: String,
    val displayId: String,
    val idStyle: Int,
    val avatar: String,
    val banner: String,
    val created: Long
) {
    val isAdmin: Boolean get() = role == "admin"
}

/**
 * Локальная база аккаунтов (хранится на этом устройстве).
 * Все новые аккаунты обычные игроки со случайным ID. Админ-доступ («ПСИХОЗ» с радужными буквами
 * или «Owner» красными) открывается отдельно: тап по ID в профиле + пароль (см. AdminAuth).
 */
class AccountStore(private val ctx: Context) {

    companion object {
        const val STYLE_PLAIN = 0
        const val STYLE_RAINBOW = 1
        const val STYLE_RED = 2
        private const val PREF = "turbo_accounts"
        private const val DB_VERSION = 2
    }

    private val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    init {
        // сброс базы всех аккаунтов при первом запуске этой версии
        if (sp.getInt("db_version", 0) < DB_VERSION) {
            wipeFiles()
            sp.edit().clear().putInt("db_version", DB_VERSION).apply()
        }
    }

    private fun wipeFiles() {
        try {
            ctx.filesDir.listFiles()?.forEach {
                if (it.name.startsWith("avatar_") || it.name.startsWith("banner_")) it.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun load(): List<Account> {
        val out = ArrayList<Account>()
        try {
            val arr = JSONArray(sp.getString("accounts", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Account(
                        o.getLong("id"), o.getString("nick"), o.getString("salt"), o.getString("hash"),
                        o.optString("role", "user"), o.optString("displayId", ""), o.optInt("idStyle", 0),
                        o.optString("avatar", ""), o.optString("banner", ""), o.optLong("created", 0L)
                    )
                )
            }
        } catch (_: Throwable) {
        }
        return out
    }

    private fun save(list: List<Account>) {
        val arr = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("nick", a.nick)
            o.put("salt", a.salt)
            o.put("hash", a.hash)
            o.put("role", a.role)
            o.put("displayId", a.displayId)
            o.put("idStyle", a.idStyle)
            o.put("avatar", a.avatar)
            o.put("banner", a.banner)
            o.put("created", a.created)
            arr.put(o)
        }
        sp.edit().putString("accounts", arr.toString()).apply()
    }

    private fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { String.format("%02x", it) }
    }

    private fun hashPw(salt: String, pass: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val d = md.digest((salt + pass).toByteArray(Charsets.UTF_8))
        return d.joinToString("") { String.format("%02x", it) }
    }

    private fun randomId(list: List<Account>): String {
        val rnd = SecureRandom()
        while (true) {
            val v = (100000 + rnd.nextInt(900000)).toString()
            if (list.none { it.displayId == v }) return v
        }
    }

    fun all(): List<Account> = load()

    fun current(): Account? {
        val id = sp.getLong("session", -1L)
        if (id < 0) return null
        return load().firstOrNull { it.id == id }
    }

    fun isAdmin(id: Long): Boolean = load().any { it.id == id && it.isAdmin }

    fun logout() {
        sp.edit().remove("session").apply()
    }

    /** Возвращает аккаунт или текст ошибки. */
    fun register(nickRaw: String, pass: String): Pair<Account?, String> {
        val nick = nickRaw.trim()
        if (!Regex("^[A-Za-z0-9_А-Яа-яЁё]{3,16}$").matches(nick)) {
            return Pair(null, "Ник: 3–16 символов (буквы, цифры, _)")
        }
        if (pass.length < 4) return Pair(null, "Пароль: минимум 4 символа")
        val list = load()
        if (list.any { it.nick.equals(nick, ignoreCase = true) }) return Pair(null, "Такой ник уже занят")

        val role = "user"
        val did = randomId(list)
        val style = STYLE_PLAIN
        val salt = newSalt()
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1L
        val acc = Account(id, nick, salt, hashPw(salt, pass), role, did, style, "", "", System.currentTimeMillis())
        save(list + acc)
        sp.edit().putLong("session", id).apply()
        return Pair(acc, "")
    }

    fun login(nickRaw: String, pass: String): Account? {
        val a = load().firstOrNull { it.nick.equals(nickRaw.trim(), ignoreCase = true) } ?: return null
        val ok = MessageDigest.isEqual(hashPw(a.salt, pass).toByteArray(), a.hash.toByteArray())
        if (!ok) return null
        sp.edit().putLong("session", a.id).apply()
        return a
    }

    fun setImage(id: Long, avatar: Boolean, path: String) {
        val list = load().map {
            if (it.id != id) it else if (avatar) it.copy(avatar = path) else it.copy(banner = path)
        }
        save(list)
    }

    /** Открыть админ-доступ текущему аккаунту. kind: 1 = ПСИХОЗ (радуга), 2 = Owner (красный). */
    fun grantAdmin(accId: Long, kind: Int): Boolean {
        if (kind != 1 && kind != 2) return false
        val list = load()
        if (list.none { it.id == accId }) return false
        val name = if (kind == 1) "ПСИХОЗ" else "Owner"
        val style = if (kind == 1) STYLE_RAINBOW else STYLE_RED
        val taken = list.map { it.displayId }.toMutableList()
        val updated = list.map { a ->
            when {
                a.id == accId -> a.copy(role = "admin", displayId = name, idStyle = style)
                a.idStyle == style -> {
                    // этот админ-ID мог быть у другого аккаунта на устройстве: переносим его сюда
                    val rid = randomIdAvoiding(taken)
                    taken.add(rid)
                    a.copy(role = "user", displayId = rid, idStyle = STYLE_PLAIN)
                }
                else -> a
            }
        }
        save(updated)
        return true
    }

    /** Снять админ-доступ: аккаунт снова обычный игрок со случайным ID. */
    fun revokeAdmin(accId: Long) {
        val list = load()
        val rid = randomId(list)
        save(list.map { if (it.id == accId) it.copy(role = "user", displayId = rid, idStyle = STYLE_PLAIN) else it })
    }

    private fun randomIdAvoiding(taken: List<String>): String {
        val rnd = SecureRandom()
        while (true) {
            val v = (100000 + rnd.nextInt(900000)).toString()
            if (!taken.contains(v)) return v
        }
    }

    // ---------- действия админа: проверка прав выполняется здесь, на уровне данных ----------

    private fun adminOk(requesterId: Long, list: List<Account>): Boolean {
        return sp.getLong("session", -1L) == requesterId && list.any { it.id == requesterId && it.isAdmin }
    }

    fun deleteAccount(requesterId: Long, targetId: Long): String {
        val list = load()
        if (!adminOk(requesterId, list)) return "Нет доступа"
        val t = list.firstOrNull { it.id == targetId } ?: return "Аккаунт не найден"
        if (t.isAdmin) return "Админов удалить нельзя"
        try {
            if (t.avatar.isNotBlank()) java.io.File(t.avatar).delete()
            if (t.banner.isNotBlank()) java.io.File(t.banner).delete()
        } catch (_: Throwable) {
        }
        save(list.filter { it.id != targetId })
        return "Аккаунт ${t.nick} удалён"
    }

    fun regenId(requesterId: Long, targetId: Long): String {
        val list = load()
        if (!adminOk(requesterId, list)) return "Нет доступа"
        val t = list.firstOrNull { it.id == targetId } ?: return "Аккаунт не найден"
        if (t.isAdmin) return "ID админа менять нельзя"
        val newId = randomId(list)
        save(list.map { if (it.id == targetId) it.copy(displayId = newId) else it })
        return "Новый ID: #$newId"
    }

    fun resetAll(requesterId: Long): String {
        val list = load()
        if (!adminOk(requesterId, list)) return "Нет доступа"
        wipeFiles()
        sp.edit().putString("accounts", "[]").remove("session").apply()
        return "База аккаунтов сброшена"
    }
}
