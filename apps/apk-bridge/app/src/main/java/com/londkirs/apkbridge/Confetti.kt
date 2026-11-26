package com.londkirs.apkbridge

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen confetti burst. It is drawn on top of the current screen and does not take
 * touches, so the user can keep using the app while it falls.
 */
class ConfettiView(context: Context) : View(context) {
    private class Piece(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var angle: Float, val spin: Float, val w: Float, val h: Float,
        val color: Int, val round: Boolean, val wobble: Float,
    )

    private val pieces = mutableListOf<Piece>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var last = 0L
    private var elapsed = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun burst(onEnd: () -> Unit) {
        post {
            val d = resources.displayMetrics.density
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            repeat(170) { i ->
                // Two cannons from the bottom corners plus a shower from the top.
                val fromTop = i % 3 == 0
                val left = i % 2 == 0
                val angle = Math.toRadians(if (left) Random.nextDouble(55.0, 80.0) else Random.nextDouble(100.0, 125.0))
                val speed = Random.nextFloat() * 900f * d / 1.6f + 650f * d / 1.6f
                pieces += Piece(
                    x = if (fromTop) Random.nextFloat() * w else if (left) w * 0.05f else w * 0.95f,
                    y = if (fromTop) -Random.nextFloat() * h * 0.4f else h * 0.95f,
                    vx = if (fromTop) (Random.nextFloat() - 0.5f) * 120f * d else (cos(angle) * speed).toFloat(),
                    vy = if (fromTop) Random.nextFloat() * 120f * d else -(sin(angle) * speed).toFloat(),
                    angle = Random.nextFloat() * 360f,
                    spin = (Random.nextFloat() - 0.5f) * 720f,
                    w = (6 + Random.nextInt(6)) * d,
                    h = (10 + Random.nextInt(8)) * d,
                    color = COLORS[Random.nextInt(COLORS.size)],
                    round = Random.nextInt(4) == 0,
                    wobble = Random.nextFloat() * 6f,
                )
            }
            last = System.nanoTime()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DURATION_MS
                interpolator = LinearInterpolator()
                addUpdateListener { invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
                })
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
        last = now
        elapsed += dt
        val d = resources.displayMetrics.density
        val gravity = 900f * d / 1.6f
        val fade = if (elapsed > DURATION_MS / 1000f - 0.8f) ((DURATION_MS / 1000f - elapsed) / 0.8f).coerceIn(0f, 1f) else 1f
        for (p in pieces) {
            p.vy += gravity * dt
            p.vx *= 0.985f
            p.vy = p.vy.coerceAtMost(520f * d / 1.6f)  // flutter instead of dropping like a stone
            p.x += (p.vx + sin(elapsed * p.wobble + p.spin) * 30f * d) * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            paint.color = p.color
            paint.alpha = (255 * fade).toInt()
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            // Scale X with the spin so the paper seems to turn over.
            canvas.scale(cos(Math.toRadians(p.angle.toDouble() * 2)).toFloat().coerceIn(-1f, 1f).let { if (kotlin.math.abs(it) < 0.15f) 0.15f else it }, 1f)
            if (p.round) canvas.drawCircle(0f, 0f, p.w / 1.6f, paint)
            else { rect.set(-p.w / 2, -p.h / 2, p.w / 2, p.h / 2); canvas.drawRect(rect, paint) }
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DURATION_MS = 4200L
        private val COLORS = intArrayOf(
            0xFFF09A45.toInt(), 0xFFFFC145.toInt(), 0xFF00A884.toInt(), 0xFF53BDEB.toInt(),
            0xFFE5534B.toInt(), 0xFF8E5BE8.toInt(), 0xFF25D366.toInt(), 0xFFFF6FB5.toInt(),
        )

        /** Shows a confetti burst over the whole activity window. */
        fun show(activity: Activity) {
            if (activity.isFinishing || activity.isDestroyed) return
            val root = activity.window.decorView as? ViewGroup ?: return
            val view = ConfettiView(activity)
            root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            view.burst { root.post { root.removeView(view) } }
        }
    }
}

/**
 * Celebrates a successful pairing — but only while the user is really looking at an APK Bridge
 * screen (resumed *and* focused, so not hidden behind Settings, the notification shade or a
 * system dialog). Otherwise it waits; the "pending" flag is saved, so it is not lost even if the
 * app process is closed in the meantime.
 */
object Celebration {
    private var visible: Activity? = null

    fun onResume(activity: Activity) {
        visible = activity
        tryShow()
    }

    fun onPause(activity: Activity) {
        if (visible === activity) visible = null
    }

    /** Call from Activity.onWindowFocusChanged: focus arrives after onResume and returns after the shade closes. */
    fun onFocus(activity: Activity, hasFocus: Boolean) {
        if (hasFocus && visible === activity) tryShow()
    }

    /** Called on the main thread by [Bridge] right after a successful pairing. */
    fun celebrate() = tryShow()

    private fun tryShow() {
        val a = visible ?: return
        if (!a.hasWindowFocus() || a.isFinishing || a.isDestroyed) return  // wait for the user
        if (Bridge.consumeCelebration()) ConfettiView.show(a)
    }
}
