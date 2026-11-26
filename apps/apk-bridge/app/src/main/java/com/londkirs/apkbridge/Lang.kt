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

/** Base of every screen: applies the app's language (see [Lang.wrap]). */
abstract class BossActivity : android.app.Activity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))
}
