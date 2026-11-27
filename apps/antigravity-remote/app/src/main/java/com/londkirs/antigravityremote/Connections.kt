package com.londkirs.antigravityremote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/** A saved place to reach Antigravity running on a computer. */
data class Connection(val id: String, val name: String, val url: String, val chrome: Boolean)

object Connections {
    const val OFFICIAL_URL = "https://antigravity.google/"
    private const val PREFS = "connections"
    private const val KEY = "list"

    fun load(ctx: Context): MutableList<Connection> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        if (raw == null) {
            val defaults = mutableListOf(
                Connection("official", ctx.getString(R.string.default_official), OFFICIAL_URL, false)
            )
            save(ctx, defaults)
            return defaults
        }
        val out = mutableListOf<Connection>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Connection(o.getString("id"), o.getString("name"), o.getString("url"), o.optBoolean("chrome"))
            }
        }
        return out
    }

    fun save(ctx: Context, list: List<Connection>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("chrome", it.chrome))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** Accepts "host:port" style input too, and returns null when it is not a usable web address. */
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            val local = s.startsWith("192.168.") || s.startsWith("10.") || s.startsWith("localhost") ||
                s.startsWith("100.") || s.substringBefore('/').contains(':')
            s = (if (local) "http://" else "https://") + s
        }
        val uri = Uri.parse(s)
        return if (uri.host.isNullOrBlank()) null else s
    }

    /** Opens a URL in a Chrome Custom Tab (no support library needed), falling back to any browser. */
    fun openInChrome(ctx: Context, url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
            putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF1A1F36.toInt())
            putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 1)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chrome = "com.android.chrome"
        val hasChrome = runCatching { ctx.packageManager.getPackageInfo(chrome, 0) }.isSuccess
        if (hasChrome) intent.setPackage(chrome)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                (ctx as? android.app.Activity)?.let { Snack.show(it, it.getString(R.string.no_browser)) }
            }
        }
    }

    fun findUrl(text: CharSequence?): String? {
        if (text == null) return null
        val m = Regex("""https?://\S+""").find(text) ?: return null
        return m.value.trimEnd('.', ',', ')', '"', '\'')
    }
}
