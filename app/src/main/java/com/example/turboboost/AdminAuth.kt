package com.example.turboboost

import android.content.Context
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Проверка пароля админа (ID «ПСИХОЗ» или «Owner»). Сервера нет: в приложении хранятся только
 * хеши паролей (PBKDF2-HMAC-SHA256, 120000 итераций, соль), сами пароли в коде не лежат.
 * Чтобы поменять пароль: python3 tools/make_admin_hash.py и подставить соль и хеш ниже.
 */
object AdminAuth {
    private const val ITER = 120000

    // 1 = ПСИХОЗ
    private const val SALT1 = "0451c6fba7b6f6468aa1e5f65a86d08c"
    private const val HASH1 = "dc8c1fc8b289f20436d4947ebba8b0288cbcf547dbca3a87f10be3f7474fc2d8"

    // 2 = Owner
    private const val SALT2 = "9e542c6f9dce67ca38e5a1669111677e"
    private const val HASH2 = "002a54b3ea6f9920c0c52fdff04100d0415f94e37918e0ba5fbf7507a1410d2b"

    private fun hexToBytes(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    private fun pbkdf2(pass: String, saltHex: String): String {
        val spec = PBEKeySpec(pass.toCharArray(), hexToBytes(saltHex), ITER, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val b = f.generateSecret(spec).encoded
        return b.joinToString("") { String.format("%02x", it) }
    }

    /**
     * Возвращает 1 (ПСИХОЗ), 2 (Owner), 0 (неверно) или -1 (временная блокировка после 5 ошибок).
     * Тяжёлая операция: вызывать не из главного потока.
     */
    fun verify(ctx: Context, idInput: String, pass: String): Int {
        val sp = ctx.getSharedPreferences("turbo_admin_guard", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now < sp.getLong("lock_until", 0L)) return -1

        val id = idInput.trim()
        val kind = when {
            id.equals("ПСИХОЗ", true) -> 1
            id.equals("Owner", true) -> 2
            else -> 0
        }
        // считаем хеш в любом случае, чтобы по времени нельзя было понять, существует ли такой ID
        val useKind = if (kind == 0) 1 else kind
        val calc = try { pbkdf2(pass, if (useKind == 1) SALT1 else SALT2) } catch (e: Throwable) { "" }
        val expected = if (useKind == 1) HASH1 else HASH2
        val ok = kind != 0 && MessageDigest.isEqual(calc.toByteArray(), expected.toByteArray())
        if (ok) {
            sp.edit().putInt("fails", 0).putLong("lock_until", 0L).apply()
            return kind
        }
        // счётчик ошибок крутим только при верно введённом ID (иначе можно случайно
        // заблокировать вход подбором несуществующих ID)
        if (kind == 0) return 0
        val fails = sp.getInt("fails", 0) + 1
        val e = sp.edit()
        if (fails >= 5) e.putInt("fails", 0).putLong("lock_until", now + 120000L) else e.putInt("fails", fails)
        e.apply()
        return 0
    }
}
