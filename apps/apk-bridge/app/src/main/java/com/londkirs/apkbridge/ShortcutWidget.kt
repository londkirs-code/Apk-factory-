package com.londkirs.apkbridge

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Home-screen widgets (black). [ShortcutWidget]: a row of up to four buttons plus 🕴 that opens the
 * app. [SingleShortcutWidget]: one button. Each placed widget has its own choice, made in
 * [WidgetConfigActivity] right after it is added (and again from the widget's settings or the
 * terminal ⚙). A button is either a shell command (run in the background over ADB, result in a
 * short message) or an app action ("@…": opens a screen of the app).
 */
open class ShortcutWidget : AppWidgetProvider() {

    data class Pin(val label: String, val command: String)

    /** Number of buttons this widget shows. */
    protected open val slots: Int = 4
    protected open val layout: Int = R.layout.widget_shortcuts

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(context, id, slots, layout))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = prefs(context).edit()
        ids.forEach { edit.remove(key(it)) }
        edit.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_RUN) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val pin = pins(context, id, slots).getOrNull(intent.getIntExtra(EXTRA_INDEX, -1)) ?: return
        Bridge.init(context)
        val app = context.applicationContext
        Toast.makeText(app, "⏳ ${pin.label}", Toast.LENGTH_SHORT).show()
        val pending = goAsync()
        Bridge.scope.launch {
            val message = try {
                withTimeoutOrNull(60_000) {
                    Bridge.withClient { c ->
                        val r = c.shell(CommandParser.normalize(pin.command))
                        val out = (r.stdoutText + r.stderrText).trim().lines().filter { it.isNotBlank() }.takeLast(2).joinToString("\n")
                        (if (r.exitCode == 0) "✔ " else "✖ ") + pin.label + if (out.isNotEmpty()) "\n${out.take(160)}" else ""
                    }
                } ?: "✖ ${pin.label} — ${tr("אין חיבור (נסה לפתוח את האפליקציה)", "no connection (try opening the app)")}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                "✖ ${pin.label} — ${Bridge.explain(e)}"
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_RUN = "com.londkirs.apkbridge.WIDGET_RUN"
        private const val EXTRA_INDEX = "index"
        private const val PREFS = "terminal"
        private const val OLD_KEY = "widget_pins"          // one list for all widgets (1.4–1.4.4)
        const val MAX = 4
        private val SLOTS = intArrayOf(R.id.w1, R.id.w2, R.id.w3, R.id.w4)

        val WIFI_TOGGLE = "if [ \"\$(settings get global wifi_on)\" != \"0\" ]; then cmd wifi set-wifi-enabled disabled && echo 'Wi-Fi off'; " +
            "else cmd wifi set-wifi-enabled enabled && echo 'Wi-Fi on'; fi"
        val BT_TOGGLE = "if [ \"\$(settings get global bluetooth_on)\" = \"1\" ]; then cmd bluetooth_manager disable && echo 'Bluetooth off'; " +
            "else cmd bluetooth_manager enable && echo 'Bluetooth on'; fi"
        private const val CHECK = "echo \"Connected: \$(getprop ro.product.manufacturer) \$(getprop ro.product.model), Android \$(getprop ro.build.version.release)\""

        /** App actions (open a screen) and app features, offered next to the terminal shortcuts. */
        fun appActions() = listOf(
            Pin(tr("📦 התקן APK", "📦 Install APK"), "@pick"),
            Pin(tr("🔌 בדוק חיבור", "🔌 Check connection"), CHECK),
            Pin(tr("💬 טרמינל", "💬 Terminal"), "@tab:TERMINAL"),
            Pin(tr("📱 אפליקציות", "📱 Apps"), "@tab:APPS"),
            Pin(tr("💾 גיבוי", "💾 Backup"), "@tab:BACKUP"),
            Pin(tr("🔗 חיבור", "🔗 Connection"), "@tab:CONNECT"),
            Pin(tr("⚙ הגדרות", "⚙ Settings"), "@tab:SETTINGS"),
            Pin(tr("📖 מדריך צימוד", "📖 Pairing guide"), "@guide"),
        )

        private fun defaults(slots: Int) = if (slots == 1) listOf(Pin(tr("🧠 פנה RAM", "🧠 Free RAM"), "@ram")) else listOf(
            Pin("📶 Wi‑Fi", WIFI_TOGGLE),
            Pin(tr("🔵 בלוטות׳", "🔵 Bluetooth"), BT_TOGGLE),
            Pin(tr("🔒 נעל", "🔒 Lock"), "input keyevent KEYCODE_SLEEP"),
            Pin(tr("📦 התקן APK", "📦 Install APK"), "@pick"),
        )

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun key(id: Int) = "widget_pins_$id"

        private fun parse(json: String?): List<Pin>? = json?.let {
            runCatching {
                val arr = JSONArray(it)
                (0 until arr.length()).map { i -> arr.getJSONObject(i) }.map { o -> Pin(o.getString("l"), o.getString("c")) }
            }.getOrNull()
        }

        /** The buttons of widget [id] (its own choice, else the older shared list, else defaults). */
        fun pins(context: Context, id: Int, slots: Int): List<Pin> {
            val p = prefs(context)
            val list = parse(p.getString(key(id), null))
                ?: (if (slots > 1) parse(p.getString(OLD_KEY, null)) else null)
                ?: defaults(slots)
            return list.map { resolve(context, it) }.take(slots)
        }

        /** "@ram" stands for the terminal's RAM shortcut (its command lives there). */
        private fun resolve(context: Context, pin: Pin): Pin = if (pin.command != "@ram") pin else
            TerminalActivity.shortcutChoices(context).flatMap { it.second }.firstOrNull { it.command.contains("send-trim-memory") }
                ?.let { Pin(pin.label, it.command) } ?: pin

        fun save(context: Context, id: Int, pins: List<Pin>) {
            val arr = JSONArray()
            pins.take(MAX).forEach { arr.put(JSONObject().put("l", it.label).put("c", it.command)) }
            prefs(context).edit().putString(key(id), arr.toString()).commit()
            refresh(context)
        }

        /** Number of buttons of a placed widget, from its provider. */
        fun slotsOf(context: Context, id: Int): Int =
            if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className == SingleShortcutWidget::class.java.name) 1 else MAX

        /** Placed widgets as (id, description) — for "which widget to edit". */
        fun placed(context: Context): List<Pair<Int, String>> {
            val m = AppWidgetManager.getInstance(context)
            val row = m.getAppWidgetIds(ComponentName(context, ShortcutWidget::class.java)).map { id ->
                id to tr("שורת קיצורים: ", "Shortcut row: ") + pins(context, id, MAX).joinToString(", ") { it.label }
            }
            val single = m.getAppWidgetIds(ComponentName(context, SingleShortcutWidget::class.java)).map { id ->
                id to tr("כפתור אחד: ", "Single button: ") + pins(context, id, 1).joinToString { it.label }
            }
            return row + single
        }

        /** Re-draws every placed widget. */
        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            for (id in m.getAppWidgetIds(ComponentName(context, ShortcutWidget::class.java))) m.updateAppWidget(id, views(context, id, MAX, R.layout.widget_shortcuts))
            for (id in m.getAppWidgetIds(ComponentName(context, SingleShortcutWidget::class.java))) m.updateAppWidget(id, views(context, id, 1, R.layout.widget_single))
        }

        private fun flags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        /** App actions open the screen directly (an activity, not a broadcast: Android 12+ requires it). */
        private fun actionIntent(context: Context, command: String): Intent? = when {
            command == "@pick" -> Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PICK_FILES, true)
                .putExtra(BottomNav.EXTRA_TAB, BottomNav.Tab.INSTALL.name)
            command == "@guide" -> Intent(context, GuideActivity::class.java)
            command.startsWith("@tab:") -> when (command.removePrefix("@tab:")) {
                "TERMINAL" -> Intent(context, TerminalActivity::class.java)
                "APPS" -> Intent(context, AppsActivity::class.java)
                "BACKUP" -> Intent(context, BackupActivity::class.java)
                "SETTINGS" -> Intent(context, SettingsActivity::class.java)
                else -> Intent(context, MainActivity::class.java).putExtra(BottomNav.EXTRA_TAB, command.removePrefix("@tab:"))
            }
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        private fun views(context: Context, id: Int, slots: Int, layout: Int): RemoteViews {
            val v = RemoteViews(context.packageName, layout)
            if (slots > 1) {
                val open = PendingIntent.getActivity(context, id * 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags())
                v.setOnClickPendingIntent(R.id.w_app, open)
            }
            val pins = pins(context, id, slots)
            SLOTS.take(slots).forEachIndexed { i, slot ->
                val pin = pins.getOrNull(i)
                if (pin == null) {
                    v.setViewVisibility(slot, View.GONE)
                    return@forEachIndexed
                }
                v.setViewVisibility(slot, View.VISIBLE)
                v.setTextViewText(slot, pin.label)
                val target = actionIntent(context, pin.command)
                val pending = if (target != null) PendingIntent.getActivity(context, id * 10 + 1 + i, target, flags())
                else PendingIntent.getBroadcast(
                    context, id * 10 + 1 + i,
                    Intent(context, if (slots == 1) SingleShortcutWidget::class.java else ShortcutWidget::class.java)
                        .setAction(ACTION_RUN).putExtra(EXTRA_INDEX, i).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    flags(),
                )
                v.setOnClickPendingIntent(slot, pending)
            }
            return v
        }
    }
}

/** One-button home-screen widget (1×1). */
class SingleShortcutWidget : ShortcutWidget() {
    override val slots = 1
    override val layout = R.layout.widget_single
}
