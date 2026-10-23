package com.example.turboboost

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LauncherScreen(private val ctx: Context) {

    var onBack: (() -> Unit)? = null
    var onAddGame: (() -> Unit)? = null
    var onGameClick: ((String) -> Unit)? = null

    fun build(games: List<Pair<String, String>>): FrameLayout {
        val root = FrameLayout(ctx)
        root.setBackgroundColor(Color.parseColor("#000000"))

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val scroll = ScrollView(ctx).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setPadding(0, dp(72), 0, 0)
        }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        content.addView(TextView(ctx).apply {
            text = "Мои игры"
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 14f
            setPadding(dp(4), dp(16), 0, dp(8))
        })

        if (games.isEmpty()) {
            content.addView(TextView(ctx).apply {
                text = "Нет добавленных игр. Нажми «Добавить игру»."
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, dp(40))
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        } else {
            games.forEach { (pkg, name) ->
                val btn = TextView(ctx).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(16), dp(20), dp(16))
                    background = glassItem()
                    setOnClickListener {
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                            onGameClick?.invoke(pkg)
                        }
                    }
                }
                content.addView(btn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                })
            }
        }

        val addBtn = TextView(ctx).apply {
            text = "ДОБАВИТЬ ИГРУ"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = glassItem()
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onAddGame?.invoke()
            }
        }
        content.addView(addBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        val topBar = buildTopBar()
        root.addView(topBar, FrameLayout.LayoutParams(MATCH_PARENT, dp(72)))

        return root
    }

    private fun buildTopBar(): FrameLayout {
        val bar = FrameLayout(ctx)
        bar.setBackgroundColor(Color.parseColor("#000000"))

        val h = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(h, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val back = ImageView(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onBack?.invoke() }
        }
        h.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })

        val title = TextView(ctx).apply {
            text = "Лаунчер игр"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER_VERTICAL
        }
        h.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        return bar
    }

    private fun glassItem() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        colors = intArrayOf(0x33FFFFFF, 0x1AFFFFFF, 0x22FFFFFF)
        gradientType = GradientDrawable.LINEAR_GRADIENT
        orientation = GradientDrawable.Orientation.LEFT_RIGHT
        setStroke(dp(1), 0xAAFFFFFF.toInt())
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
