package com.londkirs.apkbridge

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Choosing the buttons of a home-screen widget. Opens right after the widget is added, from the
 * widget's own settings (Android 12+: long-press → settings) and from the terminal ⚙. Offers app
 * actions and every terminal shortcut, by category. The row widget takes up to four (in the order
 * they were ticked), the single-button widget takes one.
 */
class WidgetConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var slots = ShortcutWidget.MAX
    private val chosen = mutableListOf<ShortcutWidget.Pin>()
    private val boxes = mutableListOf<Pair<ShortcutWidget.Pin, CheckBox>>()
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the user backs out, the launcher doesn't add the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        slots = ShortcutWidget.slotsOf(this, widgetId)
        chosen += ShortcutWidget.pins(this, widgetId, slots)
        buildUi()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.direction
            setPadding(dp(16), dp(18), dp(16), dp(16))
        }
        page.addView(TextView(this).apply {
            text = if (slots == 1) tr("📌 כפתור לווידג'ט", "📌 Widget button") else tr("📌 קיצורים לווידג'ט", "📌 Widget shortcuts")
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, wrap(4))
        page.addView(TextView(this).apply {
            text = if (slots == 1) tr("בחר פעולה אחת לכפתור.", "Choose one action for the button.")
                else tr("בחר עד $slots. הם יופיעו בווידג'ט לפי סדר הסימון.", "Choose up to $slots. They appear on the widget in the order you tick them.")
            textSize = 14f
            setTextColor(MUTED)
        }, wrap(10))

        section(page, tr("🕴 פעולות באפליקציה", "🕴 App actions"), ShortcutWidget.appActions())
        for ((group, pins) in TerminalActivity.shortcutChoices(this)) section(page, "💬 $group", pins)

        summary = TextView(this).apply { textSize = 13.5f; setTextColor(INK); setPadding(dp(4), dp(8), dp(4), dp(8)) }
        val save = Button(this).apply {
            text = tr("✔ שמור", "✔ Save")
            isAllCaps = false
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(ACCENT) }
            setOnClickListener { save() }
            Compat.focusRing(this, INK)
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.direction
            setPadding(dp(16), dp(6), dp(16), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(summary)
            addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BACKGROUND) }
        root.addView(ScrollView(this).apply { addView(page) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottom)
        Compat.applySystemBarInsets(this, root, includeIme = false)
        setContentView(root)
        renderSummary()
    }

    private fun section(page: LinearLayout, title: String, pins: List<ShortcutWidget.Pin>) {
        if (pins.isEmpty()) return
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0xFFFFFFFF.toInt()); setStroke(dp(1), 0xFFF2E2D2.toInt()) }
        }
        card.addView(TextView(this).apply { text = title; textSize = 15.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK) }, wrap(2))
        for (pin in pins) {
            val cb = CheckBox(this).apply {
                text = pin.label
                textSize = 15f
                setTextColor(INK)
                isChecked = chosen.any { it.command == pin.command }
                setOnCheckedChangeListener { box, on -> toggle(pin, box as CheckBox, on) }
            }
            boxes += pin to cb
            card.addView(cb)
        }
        page.addView(card, wrap(12))
    }

    private fun toggle(pin: ShortcutWidget.Pin, box: CheckBox, on: Boolean) {
        if (on) {
            if (chosen.any { it.command == pin.command }) return
            if (slots == 1) {
                // One button: the new choice replaces the old one.
                chosen.clear()
                boxes.filter { it.second !== box && it.second.isChecked }.forEach { it.second.isChecked = false }
            } else if (chosen.size >= slots) {
                box.isChecked = false
                Toast.makeText(this, tr("אפשר לבחור עד $slots. הסר סימון מקיצור אחר קודם.", "You can choose up to $slots. Untick another one first."), Toast.LENGTH_SHORT).show()
                return
            }
            chosen += pin
        } else {
            chosen.removeAll { it.command == pin.command }
        }
        renderSummary()
    }

    private fun renderSummary() {
        summary.text = if (chosen.isEmpty()) tr("לא נבחר כלום.", "Nothing chosen.")
            else tr("בווידג'ט: ", "On the widget: ") + chosen.joinToString("  ·  ") { it.label }
    }

    private fun save() {
        if (chosen.isEmpty()) { Toast.makeText(this, tr("בחר לפחות פעולה אחת.", "Choose at least one action."), Toast.LENGTH_SHORT).show(); return }
        ShortcutWidget.save(this, widgetId, chosen)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        Toast.makeText(this, tr("✔ הווידג'ט עודכן.", "✔ Widget updated."), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wrap(bottom: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }

    companion object {
        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val ACCENT = 0xFFF09A45.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()

        /** Opens the settings of a placed widget (from inside the app). */
        fun open(context: Context, widgetId: Int) {
            context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
    }
}
