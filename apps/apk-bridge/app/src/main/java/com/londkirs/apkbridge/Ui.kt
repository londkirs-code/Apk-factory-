package com.londkirs.apkbridge

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** The app's colors (warm light theme). The terminal has its own WhatsApp-style palette. */
object Theme {
    val INK = 0xFF30251D.toInt()
    val BODY = 0xFF55483E.toInt()
    val MUTED = 0xFF8A7868.toInt()
    val ACCENT = 0xFFF09A45.toInt()
    val BACKGROUND = 0xFFFFF9F3.toInt()
    val BROWN = 0xFF75451F.toInt()
    val SOFT = 0xFFFFE7D1.toInt()
    val BORDER = 0xFFF2E2D2.toInt()
    val LINK = 0xFFB4621E.toInt()
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Building blocks shared by the screens (one look everywhere, one place to change it). */
object Ui {
    fun matchWrap(context: Context, bottom: Int = 9) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = context.dp(bottom) }

    /** A white rounded card, added to [parent]. */
    fun card(parent: LinearLayout): LinearLayout {
        val c = parent.context
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(c.dp(16), c.dp(14), c.dp(16), c.dp(12))
            background = GradientDrawable().apply {
                cornerRadius = c.dp(18).toFloat()
                setColor(0xFFFFFFFF.toInt())
                setStroke(c.dp(1), Theme.BORDER)
            }
            elevation = c.dp(1).toFloat()
            parent.addView(this, matchWrap(c, 14))
        }
    }

    fun heading(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Theme.INK)
        setPadding(0, 0, 0, context.dp(6))
    }

    fun body(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 14f
        setTextColor(Theme.BODY)
        setLineSpacing(0f, 1.1f)
    }

    fun note(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 12.5f
        setTextColor(Theme.MUTED)
        setPadding(0, context.dp(3), 0, 0)
    }

    /** Orange (primary) or light button. */
    fun button(context: Context, label: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else Theme.BROWN)
        background = GradientDrawable().apply {
            cornerRadius = context.dp(14).toFloat()
            setColor(if (primary) Theme.ACCENT else Theme.SOFT)
        }
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        setOnClickListener { action() }
        Compat.focusRing(this, Theme.BROWN)
    }
}
