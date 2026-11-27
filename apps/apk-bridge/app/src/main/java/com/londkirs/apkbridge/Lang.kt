package com.londkirs.apkbridge

import android.content.Context
import android.view.View
import java.util.Locale

/**
 * App language. Every user-visible text is written as tr(hebrew, english) so both languages are
 * always added together; the build (checkI18n in app/build.gradle) fails on a Hebrew literal
 * that is not inside tr(...).
 * Mode: "auto" (follows the phone), "he" or "en" — chosen in Settings.
 */
object Lang {
    @Volatile var hebrew: Boolean = isHebrewLocale()
        private set

    private fun isHebrewLocale() = Locale.getDefault().language.let { it == "iw" || it == "he" }

    fun mode(context: Context): String =
        context.getSharedPreferences("bridge", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"

    fun init(context: Context) {
        hebrew = when (mode(context)) {
            "he" -> true
            "en" -> false
            else -> isHebrewLocale()
        }
    }

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences("bridge", Context.MODE_PRIVATE).edit().putString("lang", mode).commit()  // commit, not apply: restart() kills the process right after
        init(context)
    }

    /**
     * Restarts the app so every screen (and texts kept in memory) uses the new language.
     * [openGuide] opens the pairing guide right after the restart.
     */
    fun restart(activity: android.app.Activity, openGuide: Boolean = false) {
        val intent = android.content.Intent(activity, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_OPEN_GUIDE, openGuide)
        activity.startActivity(intent)
        activity.finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * The app's language for a screen: with "he" / "en" chosen in Settings, dialogs, system views
     * and resources follow it too (not only the texts in tr()). "auto" keeps the phone's language.
     */
    fun wrap(base: Context): Context {
        init(base)
        if (mode(base) == "auto") return base
        @Suppress("DEPRECATION")
        val locale = if (hebrew) Locale("iw") else Locale.ENGLISH
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Layout direction for the screens' root containers. */
    val direction: Int get() = if (hebrew) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
}

fun tr(he: String, en: String): String = if (Lang.hebrew) he else en

/**
 * Base of every screen: applies the app's language (see [Lang.wrap]), and on the tab screens lets a
 * sideways swipe move to the neighbouring tab (in the bottom bar's order; mirrored in Hebrew).
 */
abstract class BossActivity : android.app.Activity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))

    /** The tab this screen shows (null: not a tab screen, no swiping). */
    protected open val swipeTab: BottomNav.Tab? get() = null

    /** False while something else uses sideways swipes (e.g. an open side panel). */
    protected open fun swipeAllowed() = true

    /** Moves to [tab]; [forward] = the next tab (for the slide direction). */
    protected open fun onSwipeTo(tab: BottomNav.Tab, forward: Boolean) = BottomNav.navigate(this, tab, slide = if (forward) 1 else -1)

    private var swipeStartedInScroller = false

    private val swipe by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean {
                swipeStartedInScroller = startsInHorizontalScroller(e.rawX, e.rawY)
                return false
            }

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val tab = swipeTab ?: return false
                if (swipeStartedInScroller || !swipeAllowed()) return false
                val dx = e2.rawX - start.rawX
                val dy = e2.rawY - start.rawY
                val density = resources.displayMetrics.density
                if (kotlin.math.abs(dx) < 90 * density || kotlin.math.abs(dx) < 2 * kotlin.math.abs(dy) || kotlin.math.abs(velocityX) < 350 * density) return false
                // LTR: swipe left = next tab. RTL (Hebrew): the bar runs right to left, so swipe right = next.
                val forward = if (Lang.hebrew) dx > 0 else dx < 0
                val tabs = BottomNav.Tab.entries
                val target = tabs.getOrNull(tabs.indexOf(tab) + if (forward) 1 else -1) ?: return false
                onSwipeTo(target, forward)
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (swipeTab != null) swipe.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** A swipe that starts on a sideways-scrolling row (shortcut chips…) scrolls that row instead. */
    private fun startsInHorizontalScroller(x: Float, y: Float): Boolean {
        fun hit(v: android.view.View): Boolean {
            if (v.visibility != android.view.View.VISIBLE) return false
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            if (x < loc[0] || x > loc[0] + v.width || y < loc[1] || y > loc[1] + v.height) return false
            if (v is android.widget.HorizontalScrollView && (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1))) return true
            if (v is android.widget.SeekBar) return true
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) if (hit(v.getChildAt(i))) return true
            return false
        }
        return hit(window.decorView)
    }
}
