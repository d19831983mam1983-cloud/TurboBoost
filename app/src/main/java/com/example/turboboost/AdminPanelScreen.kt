package com.example.turboboost

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Админ-панель. Доступна только админам: права проверяются и здесь, и в AccountStore при каждом действии. */
class AdminPanelScreen(
    private val ctx: Context,
    private val store: AccountStore,
    private val me: Long
) {
    var onBack: (() -> Unit)? = null
    var onRefresh: (() -> Unit)? = null

    fun build(): FrameLayout {
        val root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#05070B"))

        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(100), dp(16), dp(28))
        val scroll = ScrollView(ctx)
        scroll.isFillViewport = true
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val back = TextView(ctx)
        back.text = "←  Назад"
        back.setTextColor(Color.WHITE)
        back.textSize = 13f
        back.setPadding(dp(14), dp(8), dp(14), dp(8))
        back.background = round(Color.parseColor("#88000000"), 18)
        back.setOnClickListener { onBack?.invoke() }
        root.addView(back, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = dp(40)
            leftMargin = dp(14)
        })

        // защита: обычным пользователям панель недоступна
        if (!store.isAdmin(me)) {
            content.addView(text("Нет доступа", 22f, "#FFFFFF", true))
            content.addView(text("Админ-панель только для администраторов.", 13f, "#99FFFFFF", false))
            return root
        }

        val all = store.all()
        content.addView(text("Админ-панель", 26f, "#FFFFFF", true))
        content.addView(text("Управление аккаунтами TurboBoost", 13f, "#99FFFFFF", false).apply {
            setPadding(0, dp(2), 0, dp(16))
        })

        val stats = LinearLayout(ctx)
        stats.orientation = LinearLayout.HORIZONTAL
        val admins = all.count { it.isAdmin }
        stats.addView(statCard("${all.size}", "АККАУНТОВ"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        stats.addView(statCard("$admins", "АДМИНОВ"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        stats.addView(statCard("${all.size - admins}", "ИГРОКОВ"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        content.addView(stats, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(18) })

        content.addView(text("АККАУНТЫ", 11f, "#88FFFFFF", true).apply { setPadding(dp(4), 0, 0, dp(6)) })
        for (a in all) addAccountCard(content, a)

        // место под будущие функции админов
        val future = text("Скоро здесь появятся новые инструменты для админов.", 12f, "#88FFFFFF", false)
        future.gravity = Gravity.CENTER
        future.setPadding(dp(14), dp(16), dp(14), dp(16))
        future.background = round(Color.parseColor("#0FFFFFFF"), 14)
        content.addView(future, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(10)
            bottomMargin = dp(16)
        })

        val reset = TextView(ctx)
        reset.text = "Сбросить базу аккаунтов"
        reset.setTextColor(Color.WHITE)
        reset.textSize = 14f
        reset.typeface = Typeface.DEFAULT_BOLD
        reset.gravity = Gravity.CENTER
        reset.background = round(Color.parseColor("#C0262D"), 14)
        reset.setOnClickListener {
            confirm("Сбросить базу?", "Будут удалены ВСЕ аккаунты, включая админов, вместе с аватарками и баннерами. Это нельзя отменить.") {
                val msg = store.resetAll(me)
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                onBack?.invoke()
            }
        }
        content.addView(reset, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))
        return root
    }

    private fun addAccountCard(content: LinearLayout, a: Account) {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(14), dp(12), dp(14), dp(12))
        card.background = round(Color.parseColor("#14FFFFFF"), 14)

        val left = LinearLayout(ctx)
        left.orientation = LinearLayout.VERTICAL
        left.addView(text(a.nick, 16f, "#FFFFFF", true))
        left.addView(makeIdView(ctx, a, 13f))
        card.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        if (a.isAdmin) {
            val badge = text("ADMIN", 10f, "#FF6B6B", true)
            badge.setPadding(dp(8), dp(3), dp(8), dp(3))
            badge.background = round(Color.parseColor("#33FF2D2D"), 8)
            card.addView(badge)
        } else {
            card.addView(smallBtn("Новый ID", "#2F5BD3") {
                val msg = store.regenId(me, a.id)
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                onRefresh?.invoke()
            }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(34)).apply { rightMargin = dp(6) })
            card.addView(smallBtn("Удалить", "#C0262D") {
                confirm("Удалить аккаунт?", "Аккаунт ${a.nick} будет удалён.") {
                    val msg = store.deleteAccount(me, a.id)
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    onRefresh?.invoke()
                }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(34)))
        }
        content.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
    }

    private fun confirm(title: String, msg: String, ok: () -> Unit) {
        try {
            AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Да") { _, _ -> ok() }
                .setNegativeButton("Отмена", null)
                .show()
        } catch (_: Throwable) {
        }
    }

    private fun statCard(value: String, label: String): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(dp(8), dp(14), dp(8), dp(14))
        c.background = round(Color.parseColor("#14FFFFFF"), 14)
        c.addView(text(value, 20f, "#FFFFFF", true).apply { gravity = Gravity.CENTER })
        c.addView(text(label, 9f, "#88FFFFFF", false).apply { gravity = Gravity.CENTER })
        return c
    }

    private fun smallBtn(label: String, color: String, click: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = label
        t.setTextColor(Color.WHITE)
        t.textSize = 12f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(dp(12), 0, dp(12), 0)
        t.background = round(Color.parseColor(color), 10)
        t.setOnClickListener { click() }
        return t
    }

    private fun text(s: String, size: Float, color: String, bold: Boolean): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun round(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setColor(color)
        return g
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
