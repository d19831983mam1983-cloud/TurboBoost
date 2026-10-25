package com.example.turboboost

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ID аккаунта: радужный (ПСИХОЗ), красный (Owner) или обычный серый. */
fun makeIdView(ctx: Context, a: Account, sizeSp: Float): TextView {
    val tv: TextView = if (a.idStyle == AccountStore.STYLE_RAINBOW) RainbowTextView(ctx) else TextView(ctx)
    tv.text = if (a.idStyle == AccountStore.STYLE_PLAIN) "#" + a.displayId else a.displayId
    tv.textSize = sizeSp
    tv.typeface = Typeface.DEFAULT_BOLD
    tv.letterSpacing = 0.06f
    when (a.idStyle) {
        AccountStore.STYLE_RED -> {
            tv.setTextColor(Color.parseColor("#FF2D2D"))
            tv.setShadowLayer(12f, 0f, 0f, Color.parseColor("#88FF2D2D"))
        }
        AccountStore.STYLE_RAINBOW -> tv.setTextColor(Color.WHITE)
        else -> tv.setTextColor(Color.parseColor("#AAFFFFFF"))
    }
    return tv
}

/** Профиль в стиле FACEIT: баннер, аватар в рамке, ник, ID, статистика. */
class ProfileScreen(
    private val ctx: Context,
    private val store: AccountStore,
    private val gamesCount: Int
) {
    var onBack: (() -> Unit)? = null
    var onPickAvatar: (() -> Unit)? = null
    var onPickBanner: (() -> Unit)? = null
    var onOpenAdmin: (() -> Unit)? = null
    var onRefresh: (() -> Unit)? = null
    var onIdClick: (() -> Unit)? = null

    fun build(): FrameLayout {
        val root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#05070B"))

        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(ctx)
        scroll.isFillViewport = true
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val me = store.current()
        if (me == null) buildAuth(content) else buildProfile(content, me)

        val back = TextView(ctx)
        back.text = "←  Назад"
        back.setTextColor(Color.WHITE)
        back.textSize = 13f
        back.setPadding(dp(14), dp(8), dp(14), dp(8))
        back.background = round(Color.parseColor("#88000000"), 18)
        back.setOnClickListener { onBack?.invoke() }
        val lp = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        lp.topMargin = dp(40)
        lp.leftMargin = dp(14)
        root.addView(back, lp)
        return root
    }

    // ---------------- авторизация ----------------

    private fun buildAuth(content: LinearLayout) {
        content.setPadding(dp(20), dp(110), dp(20), dp(24))

        val title = TextView(ctx)
        title.text = "Профиль TurboBoost"
        title.setTextColor(Color.WHITE)
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        content.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val sub = TextView(ctx)
        sub.text = "Войди или создай аккаунт. Данные хранятся на этом устройстве."
        sub.setTextColor(Color.parseColor("#99FFFFFF"))
        sub.textSize = 13f
        content.addView(sub, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(6)
            bottomMargin = dp(20)
        })

        val nick = field("Ник", false)
        val pass = field("Пароль", true)
        content.addView(nick, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        content.addView(pass, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        content.addView(btn("Войти", "#5B8DEF") {
            val a = store.login(nick.text.toString(), pass.text.toString())
            if (a == null) Toast.makeText(ctx, "Неверный ник или пароль", Toast.LENGTH_SHORT).show()
            else onRefresh?.invoke()
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)).apply { bottomMargin = dp(10) })

        content.addView(btn("Создать аккаунт", "#2A3550") {
            val r = store.register(nick.text.toString(), pass.text.toString())
            if (r.first == null) Toast.makeText(ctx, r.second, Toast.LENGTH_SHORT).show()
            else onRefresh?.invoke()
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)))
    }

    // ---------------- профиль ----------------

    private fun buildProfile(content: LinearLayout, me: Account) {
        // баннер
        val bannerFrame = FrameLayout(ctx)
        val bannerView = ImageView(ctx)
        bannerView.scaleType = ImageView.ScaleType.CENTER_CROP
        val bb = loadBitmap(me.banner)
        if (bb != null) bannerView.setImageBitmap(bb) else bannerView.setImageDrawable(defaultBanner(me))
        bannerFrame.addView(bannerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val shade = View(ctx)
        shade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#E605070B"))
        )
        bannerFrame.addView(shade, FrameLayout.LayoutParams(MATCH_PARENT, dp(90)).apply { gravity = Gravity.BOTTOM })
        content.addView(bannerFrame, LinearLayout.LayoutParams(MATCH_PARENT, dp(190)))

        // аватар в рамке, заходит на баннер
        val ring = FrameLayout(ctx)
        val ringBg = GradientDrawable()
        ringBg.shape = GradientDrawable.OVAL
        ringBg.setColor(ringColor(me))
        ring.background = ringBg
        ring.setPadding(dp(3), dp(3), dp(3), dp(3))
        val ab = loadBitmap(me.avatar)
        if (ab != null) {
            val iv = ImageView(ctx)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(ab)
            clipOval(iv)
            ring.addView(iv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            val letter = TextView(ctx)
            letter.text = me.nick.take(1).uppercase()
            letter.setTextColor(Color.WHITE)
            letter.textSize = 38f
            letter.typeface = Typeface.DEFAULT_BOLD
            letter.gravity = Gravity.CENTER
            val lb = GradientDrawable()
            lb.shape = GradientDrawable.OVAL
            lb.setColor(Color.parseColor("#2A3550"))
            letter.background = lb
            ring.addView(letter, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        content.addView(ring, LinearLayout.LayoutParams(dp(104), dp(104)).apply {
            topMargin = -dp(52)
            leftMargin = dp(18)
        })

        // ник, роль, ID
        val nameRow = LinearLayout(ctx)
        nameRow.orientation = LinearLayout.HORIZONTAL
        nameRow.gravity = Gravity.CENTER_VERTICAL
        val name = TextView(ctx)
        name.text = me.nick
        name.setTextColor(Color.WHITE)
        name.textSize = 26f
        name.typeface = Typeface.DEFAULT_BOLD
        nameRow.addView(name)
        val badge = TextView(ctx)
        badge.text = if (me.isAdmin) "ADMIN" else "USER"
        badge.textSize = 10f
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.setTextColor(Color.parseColor(if (me.isAdmin) "#FF6B6B" else "#AAFFFFFF"))
        badge.setPadding(dp(8), dp(3), dp(8), dp(3))
        badge.background = round(Color.parseColor(if (me.isAdmin) "#33FF2D2D" else "#22FFFFFF"), 8)
        nameRow.addView(badge, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(10) })
        content.addView(nameRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            leftMargin = dp(20)
            topMargin = dp(10)
        })
        val idView = makeIdView(ctx, me, 16f)
        idView.setPadding(dp(4), dp(8), dp(24), dp(8))
        idView.setOnClickListener { onIdClick?.invoke() }
        content.addView(idView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            leftMargin = dp(16)
        })

        // статистика
        val stats = LinearLayout(ctx)
        stats.orientation = LinearLayout.HORIZONTAL
        val date = try {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(me.created))
        } catch (_: Throwable) {
            "—"
        }
        stats.addView(statCard("$gamesCount", "ИГР В ЛАУНЧЕРЕ"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        stats.addView(statCard(if (me.isAdmin) "АДМИН" else "ИГРОК", "РОЛЬ"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        stats.addView(statCard(date, "С НАМИ С"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        content.addView(stats, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            topMargin = dp(20)
        })

        // кнопки
        val pickRow = LinearLayout(ctx)
        pickRow.orientation = LinearLayout.HORIZONTAL
        pickRow.addView(btn("Сменить аватар", "#2A3550") { onPickAvatar?.invoke() },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) })
        pickRow.addView(btn("Сменить баннер", "#2A3550") { onPickBanner?.invoke() },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        content.addView(pickRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            topMargin = dp(18)
        })

        if (me.isAdmin) {
            content.addView(btn("Админ-панель", "#C0262D") { onOpenAdmin?.invoke() },
                LinearLayout.LayoutParams(MATCH_PARENT, dp(46)).apply {
                    leftMargin = dp(16)
                    rightMargin = dp(16)
                    topMargin = dp(10)
                })
        }

        content.addView(btn("Выйти из аккаунта", "#1A1F2B") {
            store.logout()
            onRefresh?.invoke()
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(46)).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
            topMargin = dp(10)
            bottomMargin = dp(28)
        })
    }

    // ---------------- помощники ----------------

    private fun ringColor(a: Account): Int = Color.parseColor(
        when (a.idStyle) {
            AccountStore.STYLE_RAINBOW -> "#C04DFF"
            AccountStore.STYLE_RED -> "#FF2D2D"
            else -> "#5B8DEF"
        }
    )

    private fun defaultBanner(a: Account): GradientDrawable {
        val colors = when (a.idStyle) {
            AccountStore.STYLE_RAINBOW -> intArrayOf(Color.parseColor("#4B1FA8"), Color.parseColor("#C04DFF"), Color.parseColor("#00C7FF"))
            AccountStore.STYLE_RED -> intArrayOf(Color.parseColor("#3A0A0E"), Color.parseColor("#C0262D"))
            else -> intArrayOf(Color.parseColor("#121A2E"), Color.parseColor("#2F5BD3"))
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
    }

    private fun statCard(value: String, label: String): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(dp(8), dp(14), dp(8), dp(14))
        c.background = round(Color.parseColor("#14FFFFFF"), 14)
        val v = TextView(ctx)
        v.text = value
        v.setTextColor(Color.WHITE)
        v.textSize = 17f
        v.typeface = Typeface.DEFAULT_BOLD
        v.gravity = Gravity.CENTER
        val l = TextView(ctx)
        l.text = label
        l.setTextColor(Color.parseColor("#88FFFFFF"))
        l.textSize = 9f
        l.gravity = Gravity.CENTER
        c.addView(v)
        c.addView(l)
        return c
    }

    private fun field(hint: String, password: Boolean): EditText {
        val e = EditText(ctx)
        e.hint = hint
        e.setHintTextColor(Color.parseColor("#66FFFFFF"))
        e.setTextColor(Color.WHITE)
        e.textSize = 15f
        e.isSingleLine = true
        e.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        e.background = round(Color.parseColor("#22FFFFFF"), 12)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    private fun btn(text: String, color: String, click: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.background = round(Color.parseColor(color), 14)
        t.setOnClickListener { click() }
        return t
    }

    private fun round(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setColor(color)
        return g
    }

    private fun clipOval(v: View) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        v.clipToOutline = true
    }

    private fun loadBitmap(path: String): Bitmap? {
        if (path.isBlank()) return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (_: Throwable) {
            null
        }
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
