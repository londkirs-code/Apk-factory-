package com.londkirs.apkbridge

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/**
 * 🔋 Battery mini app: level and state, live current and temperature charts, time left, health and
 * capacity, which apps use the battery (since the last full charge), and battery actions.
 * Live values come from Android itself every 2 s (no ADB needed); the rest over ADB.
 */
class BatteryActivity : MiniAppActivity() {
    override val appTitle get() = tr("🔋 סוללה", "🔋 Battery")

    private lateinit var ring: RingGauge
    private lateinit var timeLeft: TextView
    private lateinit var currentValue: TextView
    private lateinit var currentChart: SparkLine
    private lateinit var tempValue: TextView
    private lateinit var tempChart: SparkLine
    private lateinit var tiles: Map<String, TextView>
    private lateinit var healthBody: LinearLayout
    private lateinit var usageBody: LinearLayout
    private lateinit var actionsBody: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val recentCurrent = ArrayDeque<Float>()
    private var designMah = 0
    private var saverOn: Boolean? = null
    private var stayOn: Boolean? = null

    private val tick = object : Runnable {
        override fun run() { readLocal(); handler.postDelayed(this, 2000) }
    }

    // region labels (one place for the tiles' names)
    private val tTemp get() = tr("🌡 טמפרטורה", "🌡 Temperature")
    private val tVolt get() = tr("⚡ מתח", "⚡ Voltage")
    private val tPower get() = tr("🔌 הספק", "🔌 Power")
    private val tHealth get() = tr("❤ מצב", "❤ Health")
    private val tSource get() = tr("🔗 מקור טעינה", "🔗 Charging from")
    private val tCharge get() = tr("🔋 טעינה נוכחית", "🔋 Charge now")
    private val tTech get() = tr("🧪 סוג", "🧪 Type")
    private val tCycles get() = tr("🔁 מחזורי טעינה", "🔁 Charge cycles")
    // endregion

    override fun build() {
        val hero = Mini.card(page)
        ring = RingGauge(this)
        hero.addView(ring, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)))
        timeLeft = TextView(this).apply { textSize = 15f; setTextColor(Theme.BODY); gravity = android.view.Gravity.CENTER }
        hero.addView(timeLeft)

        val live = Mini.card(page, tr("⚡ זרם בזמן אמת", "⚡ Live current"))
        currentValue = TextView(this).apply { textSize = 26f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Theme.INK); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        live.addView(currentValue)
        currentChart = SparkLine(this).apply { color = MiniColors.BLUE }
        live.addView(currentChart)
        live.addView(Mini.hint(this, tr("מתעדכן כל 2 שניות. בזמן טעינה הזרם נכנס, בזמן שימוש הוא יוצא.", "Updates every 2 seconds. While charging current flows in; while in use it flows out.")))

        val temp = Mini.card(page, tr("🌡 טמפרטורה", "🌡 Temperature"))
        tempValue = TextView(this).apply { textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Theme.INK); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        temp.addView(tempValue)
        tempChart = SparkLine(this).apply { color = MiniColors.ORANGE }
        temp.addView(tempChart)

        val data = Mini.card(page, tr("📊 נתונים", "📊 Details"))
        tiles = Mini.tiles(data, listOf(tTemp to "—", tVolt to "—", tPower to "—", tHealth to "—", tSource to "—", tCharge to "—", tTech to "—", tCycles to "—"))

        healthBody = Mini.card(page, tr("🩺 בריאות הסוללה", "🩺 Battery health"))
        healthBody.addView(Mini.hint(this, tr("נטען דרך ADB…", "Loading over ADB…")))

        usageBody = Mini.card(page, tr("🔥 מי צורך סוללה", "🔥 What uses the battery"))
        usageBody.addView(Mini.hint(this, tr("נטען דרך ADB…", "Loading over ADB…")))

        actionsBody = Mini.card(page, tr("🛠 פעולות", "🛠 Actions"))
        renderActions()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

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
        val nowMah = counter?.let { if (it > 100_000) it / 1000 else it }  // µAh on most phones

        ring.fraction = level / 100f
        ring.value = "$level%"
        ring.color = when { charging -> MiniColors.TEAL; level <= 15 -> MiniColors.RED; level <= 35 -> MiniColors.YELLOW; else -> MiniColors.GREEN }
        ring.caption = statusText(status) + if (plugged != 0) " · " + pluggedText(plugged) else ""

        if (mA != null) {
            recentCurrent.addLast(mA); while (recentCurrent.size > 10) recentCurrent.removeFirst()
            currentChart.add(mA)
            currentValue.text = (if (charging) "+" else "−") + String.format(Locale.US, "%.0f mA", mA)
        } else currentValue.text = tr("הטלפון לא מדווח זרם", "The phone doesn't report current")
        tempChart.add(temp)
        tempValue.text = String.format(Locale.US, "%.1f°C", temp) + if (temp < 5) tr("   ⚠ ערך חריג — ייתכן שהחיישן מדווח ביחידות אחרות", "   ⚠ unusual — the sensor may use other units") else ""

        // Time left / to full, from the average current of the last ~20 s.
        val avg = recentCurrent.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        timeLeft.text = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> tr("✔ טעונה במלואה", "✔ Fully charged")
            charging -> {
                val ms: Long = if (Build.VERSION.SDK_INT >= 28) bm?.computeChargeTimeRemaining() ?: -1L else -1L
                val minutes = when {
                    ms > 0 -> ms / 60000
                    avg != null && avg > 50 && nowMah != null && level in 1..99 -> ((nowMah * 100f / level - nowMah) / avg * 60).toLong()
                    else -> -1L
                }
                if (minutes > 0) tr("⏱ טעינה מלאה בעוד ${duration(minutes)}", "⏱ Full in ${duration(minutes)}") else tr("⏱ בטעינה", "⏱ Charging")
            }
            avg != null && avg > 20 && nowMah != null -> tr("⏱ נותרו כ־${duration((nowMah / avg * 60).toLong())} בקצב הנוכחי", "⏱ About ${duration((nowMah / avg * 60).toLong())} left at this rate")
            else -> ""
        }

        tiles[tTemp]?.text = String.format(Locale.US, "%.1f°C", temp)
        tiles[tVolt]?.text = String.format(Locale.US, "%.2f V", voltage / 1000f)
        tiles[tPower]?.text = if (mA != null && voltage > 0) String.format(Locale.US, "%.2f W", mA * voltage / 1_000_000f) else "—"
        tiles[tHealth]?.text = healthText(health)
        tiles[tSource]?.text = if (plugged == 0) tr("לא מחובר", "Not plugged") else pluggedText(plugged)
        tiles[tCharge]?.text = nowMah?.let { "$it mAh" } ?: "—"
        tiles[tTech]?.text = tech.ifBlank { "—" }
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
        p and BatteryManager.BATTERY_PLUGGED_AC != 0 -> tr("מטען קיר", "Wall charger")
        p and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
        p and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> tr("אלחוטי", "Wireless")
        else -> tr("מחובר", "Plugged")
    }

    private fun healthText(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> tr("תקינה", "Good")
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> tr("חמה מדי", "Overheat")
        BatteryManager.BATTERY_HEALTH_DEAD -> tr("לא תקינה", "Dead")
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> tr("מתח גבוה", "Over voltage")
        BatteryManager.BATTERY_HEALTH_COLD -> tr("קרה מדי", "Cold")
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> tr("תקלה", "Failure")
        else -> tr("לא ידוע", "Unknown")
    }

    // endregion

    // region ADB: health, usage, settings

    override fun refresh() {
        readLocal()
        showStatus(null)
        val cmd = "f=/sys/class/power_supply/battery; for k in cycle_count charge_full charge_full_design; do [ -r \$f/\$k ] && echo \"\$k=\$(cat \$f/\$k)\"; done; " +
            "echo \"saver=\$(settings get global low_power)\"; echo \"stayon=\$(settings get global stay_on_while_plugged_in)\"; echo '#STATS'; " +
            "dumpsys batterystats --charged 2>/dev/null | sed -n '/Estimated power use/,\$p' | head -160; echo '#UIDS'; pm list packages -U 2>/dev/null"
        adb(cmd) { out -> if (out != null) renderAdb(out) else { renderHealth(emptyMap()); usageBody.removeAllViews(); usageBody.addView(Mini.hint(this, tr("נדרש חיבור ADB (צימוד).", "Needs an ADB connection (pairing)."))) } }
    }

    private fun renderAdb(out: String) {
        val head = out.substringBefore("#STATS")
        val stats = out.substringAfter("#STATS", "").substringBefore("#UIDS")
        val uids = out.substringAfter("#UIDS", "")
        val kv = head.lines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        saverOn = kv["saver"]?.let { it == "1" }
        stayOn = kv["stayon"]?.let { it != "0" && it != "null" }
        Regex("Capacity: ([\\d.]+)").find(stats)?.let { designMah = it.groupValues[1].toFloat().toInt() }
        renderHealth(kv)
        renderUsage(stats, uids)
        renderActions()
    }

    private fun renderHealth(kv: Map<String, String>) {
        healthBody.removeAllViews()
        val cycles = kv["cycle_count"]?.toIntOrNull()?.takeIf { it > 1 }
        tiles[tCycles]?.text = cycles?.toString() ?: "—"
        val full = kv["charge_full"]?.toDoubleOrNull()
        val design = kv["charge_full_design"]?.toDoubleOrNull()
        // The two are sometimes in different units (µAh / mAh / ×10): normalize the ratio.
        val pct = if (full != null && design != null && design > 0 && full > 0) {
            var r = full / design
            while (r > 1.2) r /= 10
            while (r < 0.12) r *= 10
            (r * 100).toInt().takeIf { it in 30..110 }
        } else null
        if (pct != null) {
            healthBody.addView(Mini.hint(this, tr("קיבולת שנשארה מהמקורית", "Capacity left of the original")))
            val color = when { pct >= 85 -> MiniColors.GREEN; pct >= 70 -> MiniColors.YELLOW; else -> MiniColors.RED }
            Mini.barRow(healthBody, tr("בריאות", "Health"), "$pct%", pct / 100f, color)
            healthBody.addView(Mini.hint(this, when {
                pct >= 85 -> tr("✔ הסוללה במצב טוב.", "✔ The battery is in good shape.")
                pct >= 70 -> tr("הסוללה התבלתה מעט — זה רגיל אחרי שנה־שנתיים.", "The battery has worn a little — normal after a year or two.")
                else -> tr("⚠ הסוללה שחוקה. אם הטלפון נכבה מהר, שווה להחליף סוללה.", "⚠ The battery is worn. If the phone dies quickly, consider replacing it.")
            }))
        } else {
            healthBody.addView(Mini.hint(this, tr("הטלפון לא חושף את הקיבולת המקורית מול הנוכחית.", "The phone doesn't expose original vs. current capacity.")))
        }
        if (designMah > 0) Mini.tiles(healthBody, listOf(tr("📐 קיבולת מקורית", "📐 Design capacity") to "$designMah mAh"))
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
        usageBody.addView(Mini.hint(this, tr("מאז הטעינה המלאה האחרונה · לחיצה על אפליקציה: פעולות", "Since the last full charge · tap an app for actions")))
        val top = rows.sortedByDescending { it.third }.take(14)
        val total = rows.sumOf { it.third }.coerceAtLeast(0.01)
        top.forEachIndexed { i, (name, pkg, mah) ->
            val share = (mah / total).toFloat()
            Mini.barRow(usageBody, name, String.format(Locale.US, "%.0f mAh · %d%%", mah, (share * 100).toInt()), share / (top.first().third / total).toFloat(),
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
        val saver = saverOn
        val stay = stayOn
        val resetStats = tr("♻ אפס סטטיסטיקה", "♻ Reset statistics")
        Mini.actions(actionsBody, listOf(
            (if (saver == true) tr("🔋 חיסכון: פועל ✓", "🔋 Saver: on ✓") else tr("🔋 חיסכון בסוללה", "🔋 Battery saver")) to {
                act(tr("חיסכון בסוללה", "Battery saver"), if (saver == true) "cmd power set-mode 0 && echo off" else "cmd power set-mode 1 && echo on")
            },
            (if (stay == true) tr("🔌 דלוק בטעינה ✓", "🔌 Awake charging ✓") else tr("🔌 דלוק בזמן טעינה", "🔌 Awake while charging")) to {
                act(tr("מסך בזמן טעינה", "Screen while charging"), if (stay == true) "svc power stayon false && echo normal" else "svc power stayon true && echo 'stays awake'")
            },
            tr("📊 שימוש בסוללה (Android)", "📊 Battery usage (Android)") to {
                runCatching { startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
                Unit
            },
            tr("🚀 אופטימיזציית סוללה", "🚀 Battery optimization") to {
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                Unit
            },
            tr("🎭 הצג 100% (תצוגה)", "🎭 Show 100% (display)") to { act(tr("הצגת 100%", "Show 100%"), "dumpsys battery set level 100 && echo 'display only'") },
            tr("↺ סוללה אמיתית", "↺ Real battery") to { act(tr("ערכים אמיתיים", "Real values"), "dumpsys battery reset && echo reset") },
            resetStats to {
                act(resetStats, "dumpsys batterystats --reset && echo reset",
                    tr("נתוני הצריכה של כל האפליקציות יתאפסו ויתחילו להיספר מעכשיו. להמשיך?", "Usage data of all apps is cleared and starts counting from now. Continue?"))
            },
        ), danger = setOf(resetStats))
        actionsBody.addView(Mini.hint(this, tr("\"הצג 100%\" משנה רק את המספר על המסך, לא את הטעינה. ↺ מחזיר.", "\"Show 100%\" only changes the number on screen, not the charge. ↺ undoes it.")))
    }

    // endregion
}
