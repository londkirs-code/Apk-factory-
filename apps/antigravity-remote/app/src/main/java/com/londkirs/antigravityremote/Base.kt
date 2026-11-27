package com.londkirs.antigravityremote

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import kotlin.math.abs

/** Our colours on the window, status bar and navigation bar. */
fun paintWindow(a: Activity) {
    val window = a.window
    window.setBackgroundDrawable(ColorDrawable(T.bg))
    window.statusBarColor = T.bar
    window.navigationBarColor = T.bg
    var flags = window.decorView.systemUiVisibility
    flags = if (!T.dark) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    if (Build.VERSION.SDK_INT >= 26) {
        flags = if (!T.dark) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
    }
    window.decorView.systemUiVisibility = flags
}

/** Every screen: our colours on the window, status bar and navigation bar, and live theme changes. */
open class BaseActivity : Activity() {
    private var themeStamp = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        T.init(this)
        themeStamp = T.stamp
        super.onCreate(savedInstanceState)
        applyWindowColors()
    }

    fun applyWindowColors() = paintWindow(this)

    override fun onResume() {
        super.onResume()
        if (themeStamp != T.stamp) { themeStamp = T.stamp; recreate() }
    }

    fun snack(text: String, action: String? = null, onAction: (() -> Unit)? = null) = Snack.show(this, text, action, onAction = onAction)

    fun hideKeyboard(v: View? = currentFocus) {
        v ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    fun showKeyboard(v: View) {
        v.requestFocus()
        v.postDelayed({ (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(v, 0) }, 120)
    }
}

/**
 * Row that can be dragged sideways. [onStart]/[onEnd] fire when the row is pulled far enough
 * toward the physical left/right; [hint] draws what will happen behind the row.
 */
class SwipeRow(ctx: Context) : FrameLayout(ctx) {
    var onRight: (() -> Unit)? = null    // dragged to the right
    var onLeft: (() -> Unit)? = null     // dragged to the left
    var rightHint: ((Canvas, Float) -> Unit)? = null
    var leftHint: ((Canvas, Float) -> Unit)? = null
    var maxDrag = ctx.dp(110).toFloat()
    var trigger = ctx.dp(70).toFloat()
    var enabledSwipe = true
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f; private var downY = 0f
    private var dragging = false
    private var fired = false
    val content: View? get() = if (childCount > 0) getChildAt(0) else null

    init { setWillNotDraw(false) }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (!enabledSwipe) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; dragging = false; fired = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                val allowed = (dx > 0 && onRight != null) || (dx < 0 && onLeft != null)
                if (!dragging && allowed && abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.8f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    downX = e.x
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!dragging) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN && enabledSwipe) { downX = e.x; downY = e.y }
            return super.onTouchEvent(e)
        }
        val c = content ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                var dx = e.x - downX
                if (dx > 0 && onRight == null) dx = 0f
                if (dx < 0 && onLeft == null) dx = 0f
                val damped = Math.signum(dx) * minOf(abs(dx), maxDrag) * (1f - minOf(abs(dx), maxDrag) / (maxDrag * 3))
                c.translationX = damped
                if (!fired && abs(damped) >= trigger) { fired = true; Ui.haptic(this) }
                if (fired && abs(damped) < trigger) fired = false
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tx = c.translationX
                c.animate().translationX(0f).setDuration(180).withEndAction { invalidate() }.start()
                if (e.actionMasked == MotionEvent.ACTION_UP && abs(tx) >= trigger) {
                    if (tx > 0) onRight?.invoke() else onLeft?.invoke()
                }
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val tx = content?.translationX ?: 0f
        if (tx > 0) rightHint?.invoke(canvas, tx) else if (tx < 0) leftHint?.invoke(canvas, -tx)
    }

    /** Standard hint: coloured band with an icon, revealed behind the row. */
    fun bandHint(iconRes: Int, color: Int, fromLeft: Boolean): (Canvas, Float) -> Unit {
        val icon = context.getDrawable(iconRes)!!.mutate().apply { setTint(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val size = context.dp(24)
        return { c, d ->
            paint.color = color
            paint.alpha = (255 * minOf(1f, d / trigger)).toInt()
            if (fromLeft) c.drawRect(0f, 0f, d, height.toFloat(), paint) else c.drawRect(width - d, 0f, width.toFloat(), height.toFloat(), paint)
            val cx = if (fromLeft) (d / 2).toInt() else (width - d / 2).toInt()
            icon.setBounds(cx - size / 2, height / 2 - size / 2, cx + size / 2, height / 2 + size / 2)
            icon.alpha = (255 * minOf(1f, d / trigger)).toInt()
            icon.draw(c)
        }
    }

    /** Chat-style hint: a small reply circle that grows as you pull. */
    fun replyHint(fromLeft: Boolean): (Canvas, Float) -> Unit {
        val icon = context.getDrawable(R.drawable.ic_reply)!!.mutate().apply { setTint(T.primary) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = context.dp(17).toFloat()
        val s = context.dp(20)
        return { c, d ->
            val f = minOf(1f, d / trigger)
            val cx = if (fromLeft) context.dp(28).toFloat() else width - context.dp(28).toFloat()
            paint.color = T.surface; paint.alpha = (230 * f).toInt()
            c.drawCircle(cx, height / 2f, r * (0.6f + 0.4f * f), paint)
            icon.setBounds((cx - s / 2).toInt(), height / 2 - s / 2, (cx + s / 2).toInt(), height / 2 + s / 2)
            icon.alpha = (255 * f).toInt()
            icon.draw(c)
        }
    }
}

/** Chat backgrounds: built-in pattern, solid colours, gradients, or a picture from the gallery. */
object Wallpapers {
    val colors = listOf("#E8EEF7", "#F4EDE4", "#E6F2EA", "#F3E8F1", "#E3F0F5", "#FDF3D9", "#1E2633", "#0F1B2D", "#2A1F2E", "#13261F")
    val gradients = listOf(
        intArrayOf(0xFFDCE8FF.toInt(), 0xFFF3E7FF.toInt()),
        intArrayOf(0xFFE0F7EC.toInt(), 0xFFE3EEFF.toInt()),
        intArrayOf(0xFFFFF1D6.toInt(), 0xFFFFE0E0.toInt()),
        intArrayOf(0xFF1B2A44.toInt(), 0xFF2A1B3D.toInt()),
        intArrayOf(0xFF0E2A24.toInt(), 0xFF0E1E3A.toInt()),
    )

    fun dir(ctx: Context) = File(ctx.filesDir, "wallpapers").apply { mkdirs() }

    /** Effective wallpaper for a chat (its own, else the global one). */
    fun spec(ctx: Context, chat: Chat?) = chat?.wallpaper?.takeIf { it.isNotBlank() } ?: Settings.wallpaper(ctx)

    fun drawable(ctx: Context, spec: String): Drawable = when {
        spec.startsWith("color:") -> ColorDrawable(runCatching { Color.parseColor(spec.removePrefix("color:")) }.getOrDefault(T.wallpaper))
        spec.startsWith("gradient:") -> {
            val g = gradients.getOrElse(spec.removePrefix("gradient:").toIntOrNull() ?: 0) { gradients[0] }
            GradientDrawable(GradientDrawable.Orientation.TL_BR, g)
        }
        else -> Pattern(ctx)
    }

    /** Applies the wallpaper; pictures are drawn centre-cropped and slightly dimmed in dark mode. */
    fun apply(ctx: Context, target: ImageView, spec: String) {
        if (spec.startsWith("image:")) {
            val f = File(spec.removePrefix("image:"))
            val bmp = runCatching {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(f.absolutePath, o)
            }.getOrNull()
            if (bmp != null) {
                target.scaleType = ImageView.ScaleType.CENTER_CROP
                target.setImageBitmap(bmp)
                target.setColorFilter(if (T.dark) 0x66000000 else 0x00000000, android.graphics.PorterDuff.Mode.SRC_ATOP)
                return
            }
        }
        target.clearColorFilter()
        target.scaleType = ImageView.ScaleType.FIT_XY
        target.setImageDrawable(drawable(ctx, spec))
    }

    /** Default background: soft colour with a light dotted pattern. */
    class Pattern(private val ctx: Context) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(c: Canvas) {
            val b: Rect = bounds
            c.drawColor(T.wallpaper)
            paint.color = T.wallpaperDots
            val step = ctx.dp(22).toFloat()
            var y = 0f; var row = 0
            while (y < b.height() + step) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < b.width() + step) {
                    c.drawCircle(x, y, ctx.dpf(1.6f), paint)
                    x += step
                }
                y += step; row++
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
