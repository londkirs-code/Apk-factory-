package com.londkirs.apkbridge

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The bottom tab bar shared by the main screens: Connection · Install · Terminal · Apps · Settings.
 * Connection and Install are pages of [MainActivity]; the others are their own screens.
 */
object BottomNav {
    enum class Tab(val icon: String, val label: String) {
        CONNECT("🔗", "חיבור"),
        INSTALL("📦", "התקנה"),
        TERMINAL("⌨", "טרמינל"),
        APPS("📱", "אפליקציות"),
        SETTINGS("⚙", "הגדרות"),
    }

    const val EXTRA_TAB = "com.londkirs.apkbridge.TAB"
    private const val ACCENT = 0xFFE07B1F.toInt()
    private const val INK = 0xFF6D5B4C.toInt()

    fun build(activity: Activity, selected: Tab, onSelect: (Tab) -> Unit): View {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = dp(8).toFloat()
        }
        wrapper.addView(View(activity).apply { setBackgroundColor(0xFFF0E2D3.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        for (tab in Tab.values()) {
            val isSelected = tab == selected
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(2), 0, dp(2))
                isClickable = true
                contentDescription = tab.label
                setOnClickListener { if (!isSelected || tab == Tab.CONNECT || tab == Tab.INSTALL) onSelect(tab) }
                Compat.focusRing(this, ACCENT, 12f)
            }
            item.addView(TextView(activity).apply {
                text = tab.icon
                textSize = 19f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(2), dp(14), dp(2))
                if (isSelected) background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFFFFE7D1.toInt()) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            item.addView(TextView(activity).apply {
                text = tab.label
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) ACCENT else INK)
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                maxLines = 1
            })
            bar.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        wrapper.addView(bar)
        return wrapper
    }

    /**
     * Default navigation for the secondary screens (Apps, Settings): Connection/Install return to
     * [MainActivity] on that page; Apps/Settings replace the current secondary screen; Terminal opens
     * as its own app window. No slide animation, so it feels like switching tabs.
     */
    @Suppress("DEPRECATION")
    fun navigate(from: Activity, tab: Tab) {
        val isMain = from is MainActivity
        when (tab) {
            Tab.CONNECT, Tab.INSTALL -> {
                from.startActivity(
                    Intent(from, MainActivity::class.java)
                        .putExtra(EXTRA_TAB, tab.name)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                if (!isMain) from.finish()
            }
            Tab.TERMINAL -> from.startActivity(Intent(from, TerminalActivity::class.java))
            Tab.APPS, Tab.SETTINGS -> {
                val target = if (tab == Tab.APPS) AppsActivity::class.java else SettingsActivity::class.java
                if (from.javaClass == target) return
                from.startActivity(Intent(from, target))
                if (!isMain) from.finish()
            }
        }
        from.overridePendingTransition(0, 0)
    }
}
