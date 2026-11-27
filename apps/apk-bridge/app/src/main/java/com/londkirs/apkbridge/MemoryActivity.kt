package com.londkirs.apkbridge

import android.app.ActivityManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * 🧠 RAM mini app, on one compact screen: a drawn memory stick whose chips show what the RAM holds
 * (apps · cache · kernel · free), a strip of stats, a live chart of free memory, memory by kind of
 * process (one bar), the processes that hold the most (top 5, more on tap) and round actions.
 * Live values from Android every 2 s; the breakdown over ADB.
 */
class MemoryActivity : MiniAppActivity() {
    override val appTitle get() = tr("🧠 זיכרון RAM", "🧠 RAM")

    private lateinit var percent: TextView
    private lateinit var amounts: TextView
    private lateinit var state: TextView
    private lateinit var stick: RamStickView
    private lateinit var legendBody: LinearLayout
    private lateinit var stats: Map<String, TextView>
    private lateinit var chartValue: TextView
    private lateinit var chart: SparkLine
    private lateinit var kindsBar: StackedBar
    private lateinit var kindsLegend: LinearLayout
    private lateinit var procBody: LinearLayout
    private lateinit var actionsBody: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var startAvailMb = -1L
    private var beforeActionMb = -1L

    private val tick = object : Runnable {
        override fun run() { readLocal(); handler.postDelayed(this, 2000) }
    }

    private val sTotal get() = tr("📦 סה״כ", "📦 Total")
    private val sFree get() = tr("✅ פנוי", "✅ Free")
    private val sSwap get() = tr("💽 Swap", "💽 Swap")
    private val sZram get() = tr("🗜 ZRAM", "🗜 ZRAM")

    override fun build() {
        val hero = Mini.card(page)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        percent = TextView(this).apply { textSize = 30f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Theme.INK) }
        top.addView(percent)
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        amounts = TextView(this).apply { textSize = 13.5f; setTextColor(Theme.BODY); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        state = TextView(this).apply { textSize = 12.5f; setTextColor(Theme.MUTED); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        texts.addView(amounts); texts.addView(state)
        top.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(top)
        stick = RamStickView(this)
        hero.addView(stick, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)).apply { topMargin = dp(8); bottomMargin = dp(6) })
        legendBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hero.addView(legendBody)

        stats = Mini.stats(Mini.card(page), listOf(sTotal to "—", sFree to "—", sSwap to "—", sZram to "—"))

        chartValue = Mini.badge(this)
        val live = Mini.card(page, tr("📈 זיכרון פנוי", "📈 Free memory"), chartValue)
        chart = SparkLine(this).apply { color = MiniColors.PURPLE }
        live.addView(chart)

        val kinds = Mini.card(page, tr("🧩 לפי סוג", "🧩 By kind"))
        kindsBar = StackedBar(this)
        kinds.addView(kindsBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)).apply { bottomMargin = dp(6) })
        kindsLegend = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        kinds.addView(kindsLegend)
        kindsLegend.addView(Mini.hint(this, tr("נטען…", "Loading…")))

        procBody = Mini.card(page, tr("🏆 תופסים הכי הרבה", "🏆 Using the most"))
        procBody.addView(Mini.hint(this, tr("נטען…", "Loading…")))

        actionsBody = Mini.card(page)
        renderActions()
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    // region live (Android, no ADB)

    private fun memInfo(): ActivityManager.MemoryInfo? =
        getSystemService(ActivityManager::class.java)?.let { am -> ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) } }

    private fun readLocal() {
        val mi = memInfo() ?: return
        val totalMb = mi.totalMem / 1048576
        val availMb = mi.availMem / 1048576
        val usedMb = totalMb - availMb
        if (startAvailMb < 0) startAvailMb = availMb
        val used = usedMb.toFloat() / totalMb.coerceAtLeast(1)
        percent.text = "${(used * 100).toInt()}%"
        percent.setTextColor(when { used > 0.85f -> MiniColors.RED; used > 0.7f -> 0xFFB7791F.toInt(); else -> Theme.INK })
        amounts.text = tr("${gb(usedMb)} בשימוש מתוך ${gb(totalMb)}", "${gb(usedMb)} used of ${gb(totalMb)}")
        state.text = (if (mi.lowMemory) tr("🔴 זיכרון נמוך", "🔴 Low memory") else if (used > 0.85f) tr("🟡 עמוס", "🟡 Busy") else tr("🟢 תקין", "🟢 OK")) +
            tr(" · סף: ${gb(mi.threshold / 1048576)}", " · limit: ${gb(mi.threshold / 1048576)}")
        if (stick.parts.isEmpty()) stick.parts = listOf(usedMb.toFloat() to MiniColors.PURPLE, availMb.toFloat() to 0xFF5B6B64.toInt())
        chart.add(availMb.toFloat())
        val delta = availMb - startAvailMb
        chartValue.text = "$availMb MB" + if (delta != 0L) String.format(Locale.US, " (%+d)", delta) else ""
        stats[sTotal]?.text = gb(totalMb)
        stats[sFree]?.text = gb(availMb)
        if (beforeActionMb >= 0) {
            val freed = availMb - beforeActionMb
            if (freed > 20) showStatus(tr("✔ התפנו כ־$freed MB", "✔ About $freed MB freed"))
        }
    }

    private fun gb(mb: Long) = if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"

    // endregion

    // region ADB: breakdown, kinds, processes

    override fun refresh() {
        readLocal()
        adb("cat /proc/meminfo; echo '#MEMINFO'; dumpsys meminfo 2>/dev/null") { out ->
            if (out == null) {
                kindsLegend.removeAllViews(); kindsLegend.addView(Mini.hint(this, tr("נדרש חיבור ADB (צימוד).", "Needs an ADB connection (pairing).")))
                procBody.removeAllViews(); procBody.addView(Mini.hint(this, tr("נדרש חיבור ADB (צימוד).", "Needs an ADB connection (pairing).")))
                return@adb
            }
            renderMeminfo(out.substringBefore("#MEMINFO"))
            val dump = out.substringAfter("#MEMINFO", "")
            renderKinds(dump)
            renderProcesses(dump)
        }
    }

    /** /proc/meminfo → the stick's chips and the legend: apps · cache · kernel · buffers · free. */
    private fun renderMeminfo(text: String) {
        val m = HashMap<String, Long>()
        Regex("(?m)^(\\w+(?:\\(\\w+\\))?):\\s+(\\d+) kB").findAll(text).forEach { m[it.groupValues[1]] = it.groupValues[2].toLong() }
        val total = m["MemTotal"] ?: return
        val free = m["MemFree"] ?: 0
        val buffers = m["Buffers"] ?: 0
        val cached = (m["Cached"] ?: 0) + (m["SReclaimable"] ?: 0) - (m["Shmem"] ?: 0).coerceAtMost(m["Cached"] ?: 0)
        val kernel = (m["Slab"] ?: 0) - (m["SReclaimable"] ?: 0) + (m["KernelStack"] ?: 0) + (m["PageTables"] ?: 0) + (m["VmallocUsed"] ?: 0).coerceAtMost(total / 10)
        val apps = (total - free - buffers - cached - kernel).coerceAtLeast(0)
        val parts = listOf(
            Triple(tr("אפליקציות", "Apps"), apps, MiniColors.PURPLE),
            Triple(tr("מטמון", "Cache"), cached, MiniColors.TEAL),
            Triple(tr("מערכת", "Kernel"), kernel, MiniColors.ORANGE),
            Triple("Buffers", buffers, MiniColors.YELLOW),
            Triple(tr("פנוי", "Free"), free, 0xFF5B6B64.toInt()),
        ).filter { it.second > 0 }
        stick.parts = parts.map { it.second.toFloat() to it.third }
        legendBody.removeAllViews()
        Mini.legend(legendBody, parts.map { Triple(it.third, it.first, Mini.kb(it.second)) })
        val swapTotal = m["SwapTotal"] ?: 0
        val swapUsed = swapTotal - (m["SwapFree"] ?: 0)
        stats[sSwap]?.text = if (swapTotal > 0) "${Mini.kb(swapUsed)}/${Mini.kb(swapTotal)}" else tr("אין", "None")
    }

    private fun num(s: String) = s.replace(",", "").toLongOrNull() ?: 0L

    /** "Total PSS by OOM adjustment" → one bar of the kinds (foreground, services, cached…) with a legend. */
    private fun renderKinds(dump: String) {
        kindsLegend.removeAllViews()
        val section = Regex("Total [PR]SS by OOM adjustment:\\n([\\s\\S]*?)\\n\\s*\\n").find(dump)?.groupValues?.get(1)
        // Kind lines only ("412,345K: Native"); the processes under each kind end in "(pid …)" and don't match.
        val kinds = section?.lines()?.mapNotNull { Regex("^\\s+([\\d,]+)K: ([A-Za-z][A-Za-z ]*?)\\s*$").find(it) }
            ?.map { kindName(it.groupValues[2]) to num(it.groupValues[1]) }?.filter { it.second > 0 }
            ?.groupBy({ it.first }, { it.second })?.map { (k, v) -> k to v.sum() }?.sortedByDescending { it.second }.orEmpty()
        if (kinds.isEmpty()) { kindsBar.parts = emptyList(); kindsLegend.addView(Mini.hint(this, tr("הטלפון לא החזיר פירוט.", "The phone returned no breakdown."))); return }
        val colored = kinds.mapIndexed { i, (name, kb) -> Triple(MiniColors.PALETTE[i % MiniColors.PALETTE.size], name, kb) }
        kindsBar.parts = colored.map { it.third.toFloat() to it.first }
        Mini.legend(kindsLegend, colored.map { Triple(it.first, it.second, Mini.kb(it.third)) })
        Regex("ZRAM:\\s+([\\d,]+)K physical used for\\s+([\\d,]+)K in swap").find(dump)?.let {
            stats[sZram]?.text = Mini.kb(num(it.groupValues[1]))
        } ?: run { stats[sZram]?.text = "—" }
    }

    private fun kindName(raw: String) = when (raw.trim().lowercase()) {
        "native" -> tr("רכיבי מערכת", "Native")
        "system" -> tr("מערכת Android", "Android")
        "persistent", "persistent service" -> tr("קבועים", "Persistent")
        "foreground" -> tr("האפליקציה הפתוחה", "Open app")
        "visible" -> tr("נראים", "Visible")
        "perceptible", "perceptible low", "perceptible medium" -> tr("ברקע פעיל", "Perceptible")
        "backup" -> tr("גיבוי", "Backup")
        "heavy weight" -> tr("כבדים", "Heavy")
        "a services", "b services" -> tr("שירותים", "Services")
        "home" -> tr("מסך הבית", "Home")
        "previous" -> tr("הקודמת", "Previous")
        "cached" -> tr("במטמון", "Cached")
        else -> raw
    }

    /** "Total PSS by process": the processes that hold the most memory (top 5, tap: actions). */
    private fun renderProcesses(dump: String) {
        procBody.removeAllViews()
        val section = Regex("Total PSS by process:\\n([\\s\\S]*?)\\n\\s*\\n").find(dump)?.groupValues?.get(1)
            ?: Regex("Total RSS by process:\\n([\\s\\S]*?)\\n\\s*\\n").find(dump)?.groupValues?.get(1)
        val rows = section?.lines()?.mapNotNull { Regex("^\\s*([\\d,]+)K: (\\S+) \\(pid (\\d+)").find(it) }
            ?.map { it.groupValues[2] to num(it.groupValues[1]) }.orEmpty().take(20)
        if (rows.isEmpty()) { procBody.addView(Mini.hint(this, tr("הטלפון לא החזיר רשימת תהליכים.", "The phone returned no process list."))); return }
        val max = rows.first().second.toFloat()
        Mini.shortList(procBody, rows) { parent, i, (process, kb) ->
            val pkg = process.substringBefore(':')
            val isApp = pkg.contains('.') && pkg != "system"
            val label = if (isApp) appLabel(pkg) + if (process.contains(':')) " (${process.substringAfter(':')})" else "" else process
            Mini.barRow(parent, label, Mini.kb(kb), kb / max, MiniColors.PALETTE[i % MiniColors.PALETTE.size], if (isApp) ({ appMenu(pkg, label) }) else null)
        }
    }

    // endregion

    private fun renderActions() {
        actionsBody.removeAllViews()
        fun run(label: String, command: String, confirm: String? = null) {
            beforeActionMb = memInfo()?.let { it.availMem / 1048576 } ?: -1
            act(label, command, confirm)
        }
        Mini.actions(actionsBody, listOf(
            Mini.Action("🧠", tr("פנה RAM", "Free RAM")) { run(tr("פינוי RAM", "Free RAM"), TerminalShortcuts.FREE_RAM) },
            Mini.Action("💤", tr("סגור רקע", "Close background")) { run(tr("סגירת תהליכים ברקע", "Close background processes"), "am kill-all && echo done") },
            Mini.Action("🗜", tr("דחוס", "Compact")) { run(tr("דחיסת זיכרון", "Compact memory"), "am compact system 2>&1 | tail -1; echo done") },
            Mini.Action("🧨", tr("אגרסיבי", "Aggressive"), danger = true) {
                run(tr("פינוי אגרסיבי", "Aggressive cleanup"), TerminalShortcuts.AGGRESSIVE_RAM,
                    tr("כל האפליקציות שהורדת ורצות עכשיו ייסגרו (חוץ מהבוס, המקלדת ומסך הבית). הן לא ישלחו התראות עד שתפתח אותן שוב. להמשיך?",
                        "Every downloaded app that is running now will be closed (except The Boss, the keyboard and the home screen). They won't send notifications until you open them again. Continue?"))
            },
        ))
    }
}
