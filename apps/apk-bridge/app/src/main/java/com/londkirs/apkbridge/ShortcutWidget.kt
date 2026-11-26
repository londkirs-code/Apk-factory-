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
 * Home-screen widget: up to four terminal shortcuts the user pinned (⚙ in the terminal), plus a
 * button that opens the app. Tapping a shortcut runs its command over ADB in the background.
 */
class ShortcutWidget : AppWidgetProvider() {

    data class Pin(val label: String, val command: String)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_RUN) return
        val pin = pins(context).getOrNull(intent.getIntExtra(EXTRA_INDEX, -1)) ?: return
        Bridge.init(context)
        val app = context.applicationContext
        Toast.makeText(app, "⏳ ${pin.label}", Toast.LENGTH_SHORT).show()
        val pending = goAsync()
        Bridge.scope.launch {
            val message = try {
                withTimeoutOrNull(25_000) {
                    Bridge.withClient { c ->
                        val r = c.shell(CommandParser.normalize(pin.command))
                        val out = (r.stdoutText + r.stderrText).trim().lineSequence().firstOrNull().orEmpty()
                        (if (r.exitCode == 0) "✔ " else "✖ ") + pin.label + if (out.isNotEmpty()) " — ${out.take(80)}" else ""
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
        private const val KEY = "widget_pins"
        const val MAX = 4
        private val SLOTS = intArrayOf(R.id.w1, R.id.w2, R.id.w3, R.id.w4)

        val WIFI_TOGGLE = "if [ \"\$(settings get global wifi_on)\" != \"0\" ]; then cmd wifi set-wifi-enabled disabled && echo 'Wi-Fi off'; " +
            "else cmd wifi set-wifi-enabled enabled && echo 'Wi-Fi on'; fi"
        val BT_TOGGLE = "if [ \"\$(settings get global bluetooth_on)\" = \"1\" ]; then cmd bluetooth_manager disable && echo 'Bluetooth off'; " +
            "else cmd bluetooth_manager enable && echo 'Bluetooth on'; fi"

        private fun defaults() = listOf(
            Pin("📶 Wi‑Fi", WIFI_TOGGLE),
            Pin(tr("🔵 בלוטות׳", "🔵 Bluetooth"), BT_TOGGLE),
            Pin(tr("🔒 נעל", "🔒 Lock"), "input keyevent KEYCODE_SLEEP"),
            Pin(tr("🧹 מטמון", "🧹 Cache"), "pm trim-caches 999G && echo done"),
        )

        fun pins(context: Context): List<Pin> {
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return defaults()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getJSONObject(it) }.map { Pin(it.getString("l"), it.getString("c")) }
            }.getOrElse { defaults() }
        }

        fun savePins(context: Context, pins: List<Pin>) {
            val arr = JSONArray()
            pins.take(MAX).forEach { arr.put(JSONObject().put("l", it.label).put("c", it.command)) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
            refresh(context)
        }

        /** Re-draws every placed widget (after the pinned shortcuts changed). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ShortcutWidget::class.java))
            for (id in ids) manager.updateAppWidget(id, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_shortcuts)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_app, open)
            val pins = pins(context)
            SLOTS.forEachIndexed { i, slot ->
                val pin = pins.getOrNull(i)
                if (pin == null) {
                    v.setViewVisibility(slot, View.GONE)
                } else {
                    v.setViewVisibility(slot, View.VISIBLE)
                    v.setTextViewText(slot, pin.label)
                    val run = PendingIntent.getBroadcast(
                        context, 100 + i,
                        Intent(context, ShortcutWidget::class.java).setAction(ACTION_RUN).putExtra(EXTRA_INDEX, i),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    v.setOnClickPendingIntent(slot, run)
                }
            }
            return v
        }
    }
}
