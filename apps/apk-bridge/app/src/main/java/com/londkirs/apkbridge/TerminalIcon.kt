package com.londkirs.apkbridge

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan

/** Small black terminal window with a green ">_" prompt (used as Termux's icon in lists). */
class TerminalIcon(private val sizePx: Int) : Drawable() {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt() }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3A3A.toInt() }
    private val prompt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF39FF6A.toInt()
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    init { setBounds(0, 0, sizePx, sizePx) }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat(); val h = b.height().toFloat()
        val r = w * 0.16f
        canvas.drawRoundRect(RectF(b.left.toFloat(), b.top + h * 0.08f, b.right.toFloat(), b.bottom - h * 0.08f), r, r, bg)
        // title bar
        canvas.drawRoundRect(RectF(b.left.toFloat(), b.top + h * 0.08f, b.right.toFloat(), b.top + h * 0.26f), r, r, bar)
        prompt.textSize = h * 0.46f
        canvas.drawText(">_", b.left + w * 0.14f, b.top + h * 0.72f, prompt)
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun setAlpha(alpha: Int) { bg.alpha = alpha; bar.alpha = alpha; prompt.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** Replaces a leading "🐚" in [text] with the terminal icon, sized to the text. */
        fun inText(text: String, textSizePx: Float): CharSequence {
            if (!text.startsWith("🐚")) return text
            val s = SpannableString(text)
            val icon = TerminalIcon((textSizePx * 1.25f).toInt())
            s.setSpan(ImageSpan(icon, ImageSpan.ALIGN_BOTTOM), 0, "🐚".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return s
        }
    }
}
