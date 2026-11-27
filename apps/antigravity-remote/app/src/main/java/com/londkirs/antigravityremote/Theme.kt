package com.londkirs.antigravityremote

import android.content.Context
import android.content.res.Configuration
import org.json.JSONArray
import org.json.JSONObject

/** Bilingual text: Hebrew on Hebrew devices, English elsewhere. */
fun Context.tr(he: String, en: String): String = if (L.isHebrew(this)) he else en

object L {
    fun isHebrew(ctx: Context): Boolean {
        val lang = ctx.resources.configuration.locales[0]?.language ?: return false
        return lang == "iw" || lang == "he"
    }
}

/**
 * Design tokens. Palette derived from the app icon: a strong blue as the brand colour, with the
 * icon's green / yellow / red used as accents and avatar colours.
 */
object T {
    var dark = false; private set
    var scale = 1f; private set
    /** Bumped whenever appearance settings change, so open screens can recreate themselves. */
    var stamp = 0L; private set

    fun init(ctx: Context) {
        dark = when (Settings.theme(ctx)) {
            "dark" -> true
            "light" -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        scale = Settings.fontScale(ctx)
    }

    fun changed(ctx: Context) { init(ctx); stamp++ }

    private fun c(light: Long, night: Long) = (if (dark) night else light).toInt()

    val bg get() = c(0xFFFFFFFF, 0xFF0E1116)
    val surface get() = c(0xFFFFFFFF, 0xFF161B22)
    val surface2 get() = c(0xFFF1F4F9, 0xFF1C232D)
    val surface3 get() = c(0xFFE6EBF3, 0xFF252E3A)
    val bar get() = c(0xFFFFFFFF, 0xFF161B22)
    val wallpaper get() = c(0xFFEDF1F7, 0xFF0B0F15)
    val wallpaperDots get() = c(0x14000000, 0x10FFFFFF)
    val text get() = c(0xFF0F172A, 0xFFE6EDF3)
    val muted get() = c(0xFF5F6B7A, 0xFF8B97A6)
    val faint get() = c(0xFF94A3B8, 0xFF5D6877)
    val divider get() = c(0xFFE4E9F0, 0xFF232B36)
    val primary get() = c(0xFF1F6FEB, 0xFF5B9BFF)
    val onPrimary get() = c(0xFFFFFFFF, 0xFF08111F)
    val primarySoft get() = c(0xFFE3EDFF, 0xFF1A2942)
    val outBubble get() = c(0xFFD6E6FF, 0xFF1D3A68)
    val inBubble get() = c(0xFFFFFFFF, 0xFF1C232D)
    val toolBg get() = c(0xFFE9EFF8, 0xFF19212C)
    val errorBg get() = c(0xFFFDECEC, 0xFF3A1D20)
    val danger get() = c(0xFFE5484D, 0xFFFF6B6B)
    val success get() = c(0xFF1F9D63, 0xFF3DD68C)
    val warning get() = c(0xFFE38B00, 0xFFF5B544)
    val tickRead get() = c(0xFF1F6FEB, 0xFF6EB0FF)
    val ripple get() = c(0x1F0F172A, 0x29FFFFFF)
    val selection get() = c(0x261F6FEB, 0x335B9BFF)
    val scrim get() = 0x80000000.toInt()
    val highlight get() = c(0xFFFFE27A, 0xFF8A6D00)
    val codeBg get() = c(0xFFF3F5F9, 0xFF0F141B)
    val quoteBar get() = c(0xFF1F6FEB, 0xFF5B9BFF)

    /** Avatar / accent colours from the icon gradient. */
    val accents = intArrayOf(
        0xFF1F6FEB.toInt(), 0xFF1F9D63.toInt(), 0xFFE38B00.toInt(), 0xFFE5484D.toInt(),
        0xFF7C4DFF.toInt(), 0xFF00A3BF.toInt(), 0xFFD6336C.toInt(), 0xFF5C7CFA.toInt(),
    )

    fun accentFor(key: String) = accents[Math.floorMod(key.hashCode(), accents.size)]

    /** Text size in sp, scaled by the user's font-size choice. */
    fun fs(sp: Float) = sp * scale
}

/** App-wide preferences. */
object Settings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun models(ctx: Context) = ctx.getSharedPreferences("models", Context.MODE_PRIVATE)

    fun theme(ctx: Context) = p(ctx).getString("theme", "system")!!
    fun setTheme(ctx: Context, v: String) { p(ctx).edit().putString("theme", v).apply(); T.changed(ctx) }

    fun fontScale(ctx: Context) = p(ctx).getFloat("font_scale", 1f)
    fun setFontScale(ctx: Context, v: Float) { p(ctx).edit().putFloat("font_scale", v).apply(); T.changed(ctx) }

    /** "" = built-in pattern, "color:#RRGGBB", "gradient:N", "image:/path". */
    fun wallpaper(ctx: Context) = p(ctx).getString("wallpaper", "")!!
    fun setWallpaper(ctx: Context, v: String) { p(ctx).edit().putString("wallpaper", v).apply() }

    fun enterSends(ctx: Context) = p(ctx).getBoolean("enter_sends", false)
    fun setEnterSends(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("enter_sends", v).apply()

    fun notifications(ctx: Context) = p(ctx).getBoolean("notifications", true)
    fun setNotifications(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("notifications", v).apply()

    fun voiceSendsNow(ctx: Context) = p(ctx).getBoolean("voice_sends_now", true)
    fun setVoiceSendsNow(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("voice_sends_now", v).apply()

    /** Kept in the "models" file used since 3.3, so existing choices survive the update. */
    fun autoPolicy(ctx: Context) = models(ctx).getString("auto_policy", "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER")!!
    fun setAutoPolicy(ctx: Context, v: String) = models(ctx).edit().putString("auto_policy", v).apply()
    fun autoRun(ctx: Context) = autoPolicy(ctx) != "CASCADE_COMMANDS_AUTO_EXECUTION_OFF"
    fun setAutoRun(ctx: Context, v: Boolean) =
        setAutoPolicy(ctx, if (v) "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER" else "CASCADE_COMMANDS_AUTO_EXECUTION_OFF")

    fun model(ctx: Context): String? = models(ctx).getString("model", null)
    fun setModel(ctx: Context, v: String) = models(ctx).edit().putString("model", v).apply()

    fun setupDone(ctx: Context) = p(ctx).getBoolean("setup_done", false)
    fun setSetupDone(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("setup_done", v).apply()

    fun tipSeen(ctx: Context, key: String) = p(ctx).getBoolean("tip_$key", false)
    fun setTipSeen(ctx: Context, key: String) = p(ctx).edit().putBoolean("tip_$key", true).apply()
    fun resetTips(ctx: Context) {
        val e = p(ctx).edit()
        p(ctx).all.keys.filter { it.startsWith("tip_") }.forEach { e.remove(it) }
        e.apply()
    }

    /** Saved prompts ("templates") offered with "/" in the composer. */
    data class Template(val title: String, val text: String)

    fun templates(ctx: Context): List<Template> {
        val raw = p(ctx).getString("templates", null) ?: return defaultTemplates(ctx)
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Template(o.optString("title"), o.optString("text")) } }
        }.getOrDefault(emptyList())
    }

    fun setTemplates(ctx: Context, list: List<Template>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("title", it.title).put("text", it.text)) }
        p(ctx).edit().putString("templates", a.toString()).apply()
    }

    private fun defaultTemplates(ctx: Context) = listOf(
        Template(ctx.tr("סיכום", "Summary"), ctx.tr("סכם בקצרה מה עשית עד עכשיו ומה נשאר.", "Briefly summarize what you did so far and what is left.")),
        Template(ctx.tr("בדיקה", "Check"), ctx.tr("הרץ את הבדיקות ותקן את מה שנכשל.", "Run the tests and fix whatever fails.")),
        Template(ctx.tr("הסבר", "Explain"), ctx.tr("הסבר לי בשפה פשוטה מה הקוד הזה עושה.", "Explain in simple words what this code does.")),
    )
}
