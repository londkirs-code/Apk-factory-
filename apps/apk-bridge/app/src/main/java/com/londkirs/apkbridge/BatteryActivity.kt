package com.londkirs.apkbridge

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/**
 * 🔋 Battery mini app, on one compact screen: a drawn battery (level, charging bolt) with state and
 * time left, a strip of stats, a live current chart, what uses the battery since the last full
 * charge (top 5, more on tap), and round action buttons. Live values from Android every 2 s; the
 * rest over ADB.
 */
class BatteryActivity : MiniAppActivity() {
    override val appTitle get() = tr("🔋 סוללה", "🔋 Battery")

    private lateinit var battery: BatteryView
    private lateinit var stateText: TextView
    private lateinit var timeText: TextView
    private lateinit var flowText: TextView
    private lateinit var stats: Map<String, TextView>
    private lateinit var chartValue: TextView
    private lateinit var chart: SparkLine
    private lateinit var usageBody: LinearLayout
    private lateinit var actionsBody: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val recentCurrent = ArrayDeque<Float>()
    private var saverOn: Boolean? = null
    private var stayOn: Boolean? = null

    private val tick = object : Runnable {
        override fun run() { readLocal(); handler.postDelayed(this, 2000) }
    }

    private val sTemp get() = tr("🌡 חום", "🌡 Temp")
    private val sVolt get() = tr("⚡ מתח", "⚡ Voltage")
    private val sHealth get() = tr("❤ מצב", "❤ Health")
    private val sWear get() = tr("🩺 קיבולת", "🩺 Capacity")
    private val sNow get() = tr("🔋 טעינה", "🔋 Charge")
    private val sDesign get() = tr("📐 מקורית", "📐 Design")
    private val sCycles get() = tr("🔁 מחזורים", "🔁 Cycles")
    private val sTech get() = tr("🧪 סוג", "🧪 Type")

    override fun build() {
        // Hero: the battery drawing, and the state next to it.
        val hero = Mini.card(page)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        battery = BatteryView(this)
        row.addView(battery)
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        stateText = TextView(this).apply { textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Theme.INK); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        timeText = TextView(this).apply { textSize = 13f; setTextColor(Theme.BODY); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        flowText = TextView(this).apply { textSize = 12.5f; setTextColor(Theme.MUTED); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        texts.addView(stateText); texts.addView(timeText); texts.addView(flowText)
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(row)

        stats = Mini.stats(Mini.card(page), listOf(sTemp to "—", sVolt to "—", sHealth to "—", sWear to "—", sNow to "—", sDesign to "—", sCycles to "—", sTech to "—"))

        chartValue = Mini.badge(this)
        val live = Mini.card(page, tr("⚡ זרם בזמן אמת", "⚡ Live current"), chartValue)
        chart = SparkLine(this).apply { color = MiniColors.BLUE }
        live.addView(chart)

        usageBody = Mini.card(page, tr("🔥 צריכה מאז הטעינה המלאה", "🔥 Use since the last full charge"))
        usageBody.addView(Mini.hint(this, tr("נטען…", "Loading…")))

        actionsBody = Mini.card(page)
        renderActions()
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    // region live values (Android, no ADB)

    private fun readLocal() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val bm = getSystemService(BatteryManager::class.java)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
        val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)          // mV
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f    // °C
        val tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty()
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        // Current: most phones report µA, some mA; the sign differs between makers, so the state decides.
        val raw = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        val mA = if (raw == Int.MIN_VALUE || raw == 0) null else abs(if (abs(raw) > 10_000) raw / 1000f else raw.toFloat())
        val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 && it != Int.MIN_VALUE }
        val nowMah = counter?.let { if (it > 100_000) it / 1000 else it }

        battery.level = level
        battery.charging = charging
        battery.color = when { charging -> MiniColors.TEAL; level <= 15 -> MiniColors.RED; level <= 35 -> MiniColors.YELLOW; else -> MiniColors.GREEN }
        stateText.text = statusText(status) + if (plugged != 0) " · " + pluggedText(plugged) else ""

        if (mA != null) {
            recentCurrent.addLast(mA); while (recentCurrent.size > 10) recentCurrent.removeFirst()
            chart.add(mA)
            chartValue.text = (if (charging) "+" else "−") + String.format(Locale.US, "%.0f mA", mA)
            flowText.text = String.format(Locale.US, "%.2f W · %.2f V", mA * voltage / 1_000_000f, voltage / 1000f)
        } else {
            chartValue.text = "—"
            flowText.text = String.format(Locale.US, "%.2f V", voltage / 1000f)
        }

        // Time left / to full, from the average current of the last ~20 s.
        val avg = recentCurrent.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        timeText.text = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> tr("✔ טעונה במלואה", "✔ Fully charged")
            charging -> {
                val ms: Long = if (Build.VERSION.SDK_INT >= 28) bm?.computeChargeTimeRemaining() ?: -1L else -1L
                val minutes = when {
                    ms > 0 -> ms / 60000
                    avg != null && avg > 50 && nowMah != null && level in 1..99 -> ((nowMah * 100f / level - nowMah) / avg * 60).toLong()
                    else -> -1L
                }
                if (minutes > 0) tr("⏱ מלאה בעוד ${duration(minutes)}", "⏱ Full in ${duration(minutes)}") else tr("⏱ בטעינה", "⏱ Charging")
            }
            avg != null && avg > 20 && nowMah != null -> tr("⏱ נותרו כ־${duration((nowMah / avg * 60).toLong())}", "⏱ About ${duration((nowMah / avg * 60).toLong())} left")
            else -> ""
        }

        stats[sTemp]?.text = String.format(Locale.US, "%.1f°", temp) + if (temp < 5) " ⚠" else ""
        stats[sVolt]?.text = String.format(Locale.US, "%.2fV", voltage / 1000f)
        stats[sHealth]?.text = healthText(health)
        stats[sNow]?.text = nowMah?.let { "$it" } ?: "—"
        stats[sTech]?.text = tech.ifBlank { "—" }
    }

    private fun duration(minutes: Long): String {
        val h = minutes / 60; val m = minutes % 60
        return if (h > 0) tr("$h שע׳ $m דק׳", "${h}h ${m}m") else tr("$m דק׳", "${m}m")
    }

    private fun statusText(s: Int) = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> tr("בטעינה", "Charging")
        BatteryManager.BATTERY_STATUS_DISCHARGING -> tr("פורקת", "Discharging")
        BatteryManager.BATTERY_STATUS_FULL -> tr("מלאה", "Full")
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> tr("לא בטעינה", "Not charging")
        else -> tr("לא ידוע", "Unknown")
    }

    private fun pluggedText(p: Int) = when {
        p and BatteryManager.BATTERY_PLUGGED_AC != 0 -> tr("מטען", "Charger")
        p and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
        p and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> tr("אלחוטי", "Wireless")
        else -> tr("מחובר", "Plugged")
    }

    private fun healthText(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> tr("תקינה", "Good")
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> tr("חמה", "Hot")
        BatteryManager.BATTERY_HEALTH_DEAD -> tr("תקולה", "Dead")
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> tr("מתח גבוה", "High V")
        BatteryManager.BATTERY_HEALTH_COLD -> tr("קרה", "Cold")
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> tr("תקלה", "Failure")
        else -> "—"
    }

    // endregion

    // region ADB: health, usage, settings

    override fun refresh() {
        readLocal()
        showStatus(null)
        val cmd = "f=/sys/class/power_supply/battery; for k in cycle_count charge_full charge_full_design; do [ -r \$f/\$k ] && echo \"\$k=\$(cat \$f/\$k)\"; done; " +
            "echo \"saver=\$(settings get global low_power)\"; echo \"stayon=\$(settings get global stay_on_while_plugged_in)\"; echo '#STATS'; " +
            "dumpsys batterystats --charged 2>/dev/null | sed -n '/Estimated power use/,\$p' | head -160; echo '#UIDS'; pm list packages -U 2>/dev/null"
        adb(cmd) { out ->
            if (out != null) renderAdb(out) else {
                usageBody.removeAllViews()
                usageBody.addView(Mini.hint(this, tr("נדרש חיבור ADB (צימוד).", "Needs an ADB connection (pairing).")))
            }
        }
    }

    private fun renderAdb(out: String) {
        val head = out.substringBefore("#STATS")
        val statsText = out.substringAfter("#STATS", "").substringBefore("#UIDS")
        val uids = out.substringAfter("#UIDS", "")
        val kv = head.lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        saverOn = kv["saver"]?.let { it == "1" }
        stayOn = kv["stayon"]?.let { it != "0" && it != "null" }
        stats[sCycles]?.text = kv["cycle_count"]?.toIntOrNull()?.takeIf { it > 1 }?.toString() ?: "—"
        stats[sDesign]?.text = Regex("Capacity: ([\\d.]+)").find(statsText)?.groupValues?.get(1)?.toFloat()?.toInt()?.let { "$it" } ?: "—"
        // Capacity left of the original. The two values are sometimes in different units: normalize the ratio.
        val full = kv["charge_full"]?.toDoubleOrNull()
        val design = kv["charge_full_design"]?.toDoubleOrNull()
        val pct = if (full != null && design != null && design > 0 && full > 0) {
            var r = full / design
            while (r > 1.2) r /= 10
            while (r < 0.12) r *= 10
            (r * 100).toInt().takeIf { it in 30..110 }
        } else null
        stats[sWear]?.apply {
            text = pct?.let { "$it%" } ?: "—"
            setTextColor(when { pct == null -> Theme.INK; pct >= 85 -> MiniColors.GREEN; pct >= 70 -> 0xFFB7791F.toInt(); else -> MiniColors.RED })
        }
        renderUsage(statsText, uids)
        renderActions()
    }

    /** Apps (and parts of the phone) by battery use since the last full charge, from batterystats. */
    private fun renderUsage(stats: String, uidList: String) {
        usageBody.removeAllViews()
        val uidToPkg = HashMap<Int, String>()
        Regex("package:(\\S+) uid:(\\d+)").findAll(uidList).forEach { m ->
            val uid = m.groupValues[2].toInt()
            if (uid !in uidToPkg) uidToPkg[uid] = m.groupValues[1]
        }
        val rows = mutableListOf<Triple<String, String?, Double>>()  // name, package, mAh
        val seen = HashSet<String>()
        for (line in stats.lines()) {
            val m = Regex("(?i)^\\s*uid\\s+(\\S+?):\\s+([\\d.]+)").find(line)
            if (m != null) {
                val token = m.groupValues[1]
                val mah = m.groupValues[2].toDoubleOrNull() ?: continue
                if (!seen.add(token) || mah < 0.05) continue
                val uid = parseUid(token)
                val pkg = uid?.let { uidToPkg[it] }
                val name = when {
                    uid != null && uid < 10000 -> systemName(uid)
                    pkg != null -> appLabel(pkg)
                    else -> token
                }
                rows += Triple(name, pkg?.takeIf { (uid ?: 0) >= 10000 }, mah)
                continue
            }
            val c = Regex("(?i)^\\s*(screen|cell standby|mobile_radio|idle|wifi|bluetooth|phone calls|flashlight|camera|gnss|cpu|audio|video|sensors)\\s*:\\s+([\\d.]+)").find(line)
            if (c != null) {
                val key = c.groupValues[1].lowercase()
                val mah = c.groupValues[2].toDoubleOrNull() ?: continue
                if (seen.add("c:$key") && mah >= 0.05) rows += Triple(componentName(key), null, mah)
            }
        }
        if (rows.isEmpty()) {
            usageBody.addView(Mini.hint(this, tr("אין עדיין נתוני צריכה (או שהטלפון לא חושף אותם).", "No usage data yet (or the phone doesn't expose it).")))
            return
        }
        val sorted = rows.sortedByDescending { it.third }.take(20)
        val total = rows.sumOf { it.third }.coerceAtLeast(0.01)
        val top = sorted.first().third
        Mini.shortList(usageBody, sorted) { parent, i, (name, pkg, mah) ->
            Mini.barRow(parent, name, String.format(Locale.US, "%d%% · %.0f mAh", (mah / total * 100).toInt(), mah), (mah / top).toFloat(),
                MiniColors.PALETTE[i % MiniColors.PALETTE.size], pkg?.let { p -> { appMenu(p, name) } })
        }
    }

    private fun parseUid(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        val m = Regex("u(\\d+)a(\\d+)").matchEntire(token) ?: return null
        return m.groupValues[1].toInt() * 100_000 + 10_000 + m.groupValues[2].toInt()
    }

    private fun systemName(uid: Int) = when (uid) {
        0 -> tr("⚙ ליבת המערכת", "⚙ Kernel")
        1000 -> tr("🤖 מערכת Android", "🤖 Android system")
        1001 -> tr("📶 רשת סלולרית", "📶 Mobile network")
        1010 -> "📶 Wi‑Fi"
        1013, 1041 -> tr("🎵 מדיה", "🎵 Media")
        1002 -> tr("🔵 בלוטות׳", "🔵 Bluetooth")
        else -> tr("⚙ שירות מערכת ($uid)", "⚙ System service ($uid)")
    }

    private fun componentName(key: String) = when (key) {
        "screen" -> tr("📱 מסך", "📱 Screen")
        "cell standby", "mobile_radio" -> tr("📶 רשת סלולרית", "📶 Mobile network")
        "idle" -> tr("💤 המתנה", "💤 Idle")
        "wifi" -> "📶 Wi‑Fi"
        "bluetooth" -> tr("🔵 בלוטות׳", "🔵 Bluetooth")
        "phone calls" -> tr("📞 שיחות", "📞 Calls")
        "flashlight" -> tr("🔦 פנס", "🔦 Flashlight")
        "camera" -> tr("📷 מצלמה", "📷 Camera")
        "gnss" -> tr("📍 GPS", "📍 GPS")
        "cpu" -> tr("⚙ מעבד", "⚙ CPU")
        "audio" -> tr("🔊 שמע", "🔊 Audio")
        "video" -> tr("🎬 וידאו", "🎬 Video")
        else -> tr("📡 חיישנים", "📡 Sensors")
    }

    private fun renderActions() {
        actionsBody.removeAllViews()
        val saver = saverOn == true
        val stay = stayOn == true
        val reset = tr("איפוס נתונים", "Reset stats")
        Mini.actions(actionsBody, listOf(
            Mini.Action("🔋", tr("חיסכון", "Saver"), active = saver) {
                act(tr("חיסכון בסוללה", "Battery saver"), if (saver) "cmd power set-mode 0 && echo off" else "cmd power set-mode 1 && echo on")
            },
            Mini.Action("🔌", tr("דלוק בטעינה", "Awake charging"), active = stay) {
                act(tr("מסך בזמן טעינה", "Screen while charging"), if (stay) "svc power stayon false && echo normal" else "svc power stayon true && echo 'stays awake'")
            },
            Mini.Action("📊", tr("שימוש", "Usage")) { runCatching { startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) } },
            Mini.Action("🚀", tr("אופטימיזציה", "Optimize")) { runCatching { startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
            Mini.Action("🎭", tr("הצג 100%", "Show 100%")) { act(tr("הצגת 100% (רק בתצוגה)", "Show 100% (display only)"), "dumpsys battery set level 100 && echo 'display only'") },
            Mini.Action("↺", tr("סוללה אמיתית", "Real battery")) { act(tr("ערכים אמיתיים", "Real values"), "dumpsys battery reset && echo reset") },
            Mini.Action("♻", reset, danger = true) {
                act(reset, "dumpsys batterystats --reset && echo reset",
                    tr("נתוני הצריכה של כל האפליקציות יתאפסו ויתחילו להיספר מעכשיו. להמשיך?", "Usage data of all apps is cleared and starts counting from now. Continue?"))
            },
        ))
    }

    // endregion
}
