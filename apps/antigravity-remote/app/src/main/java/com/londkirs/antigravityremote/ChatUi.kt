package com.londkirs.antigravityremote

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.widget.TextView

/** Shared WhatsApp-like look. */
object ChatUi {
    val HEADER = Color.parseColor("#075E54")
    val HEADER_DARK = Color.parseColor("#054C44")
    val ACCENT = Color.parseColor("#25D366")
    val SEND = Color.parseColor("#00A884")
    val WALLPAPER = Color.parseColor("#ECE5DD")
    val OUT_BUBBLE = Color.parseColor("#DCF8C6")
    val IN_BUBBLE = Color.WHITE
    val TOOL_BG = Color.parseColor("#E1F2FB")
    val ERROR_BG = Color.parseColor("#FDE2E1")
    val TEXT = Color.parseColor("#111B21")
    val MUTED = Color.parseColor("#667781")

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    /** Round avatar with initials. */
    fun avatar(ctx: Context, text: String, sizeDp: Int, color: Int = Color.parseColor("#4A5AE8")) = TextView(ctx).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = (sizeDp / 2.6f)
        setTypeface(typeface, Typeface.BOLD)
        background = circle(color)
        val px = dp(ctx, sizeDp)
        layoutParams = android.widget.LinearLayout.LayoutParams(px, px)
    }

    /** Very small Markdown subset: ```code```, `inline`, **bold**, headings. */
    fun markdown(src: String): CharSequence {
        val out = SpannableStringBuilder()
        val parts = src.split("```")
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 1) {
                val code = part.substringAfter('\n', part).trimEnd()
                val start = out.length
                out.append(code)
                out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(BackgroundColorSpan(Color.parseColor("#14000000")), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (idx < parts.size - 1) out.append('\n')
            } else {
                inline(out, part.replace(Regex("(?m)^#{1,6}\\s*"), ""))
            }
        }
        return out
    }

    private fun inline(out: SpannableStringBuilder, text: String) {
        val re = Regex("""\*\*(.+?)\*\*|`([^`\n]+)`""")
        var last = 0
        for (m in re.findAll(text)) {
            out.append(text, last, m.range.first)
            val start = out.length
            if (m.groupValues[1].isNotEmpty()) {
                out.append(m.groupValues[1])
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                out.append(m.groupValues[2])
                out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(BackgroundColorSpan(Color.parseColor("#14000000")), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            last = m.range.last + 1
        }
        out.append(text, last, text.length)
    }
}
