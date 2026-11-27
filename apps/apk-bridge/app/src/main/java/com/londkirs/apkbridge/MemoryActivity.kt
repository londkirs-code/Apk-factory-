package com.londkirs.apkbridge

import android.app.ActivityManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * 🧠 RAM mini app: how much memory is used and free (live chart), what it is used for (apps,
 * cache, kernel, swap / ZRAM), memory by kind of process, the processes that use the most, and
 * actions to free memory. Live values from Android every 2 s; the breakdown over ADB.
 */
class MemoryActivity : MiniAppActivity() {
    override val appTitle get() = tr("🧠 זיכרון RAM", "🧠 RAM")

    private lateinit var ring: RingGauge
    private lateinit var ringNote: TextView
    private lateinit var breakdown: StackedBar
    private lateinit var legendBody: LinearLayout
    private lateinit var liveValue: TextView
    private lateinit var liveChart: SparkLine
    private lateinit var tiles: Map<String, TextView>
    private lateinit var kindsBody: LinearLayout
    private lateinit var procBody: LinearLayout
    private lateinit var actionsBody: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private var startAvailMb = -1L
    private var beforeActionMb = -1L

    private val tick = object : Runnable {
        override fun run() { readLocal(); handler.postDelayed(this, 2000) }
    }

    private val tTotal get() = tr("📦 סה״כ", "📦 Total")
    private val tAvail get() = tr("✅ פנוי לשימוש", "✅ Available")
    private val tUsed get() = tr("📱 בשימוש", "📱 In use")
    private val tCached get() = tr("🗂 מטמון", "🗂 Cache")
    private val tSwap get() = tr("💽 Swap בשימוש", "💽 Swap used")
    private val tZram get() = tr("🗜 ZRAM", "🗜 ZRAM")
    private val tLow get() = tr("⚠ סף זיכרון נמוך", "⚠ Low-memory limit")
    private val tState get() = tr("🚦 מצב", "🚦 State")

    override fun build() {
        val hero = Mini.card(page)
        ring = RingGauge(this).apply { color = MiniColors.PURPLE }
        hero.addView(ring, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)))
        ringNote = TextView(this).apply { textSize = 15f; setTextColor(Theme.BODY); gravity = android.view.Gravity.CENTER }
        hero.addView(ringNote)
        breakdown = StackedBar(this)
        hero.addView(breakdown, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16)).apply { topMargin = dp(14); bottomMargin = dp(8) })
        legendBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hero.addView(legendBody)

        val live = Mini.card(page, tr("📈 זיכרון פנוי לאורך זמן", "📈 Free memory over time"))
        liveValue = TextView(this).apply { textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Theme.INK); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
        live.addView(liveValue)
        liveChart = SparkLine(this).apply { color = MiniColors.PURPLE }
        live.addView(liveChart)
        live.addView(Mini.hint(this, tr("מתעדכן כל 2 שניות. אחרי פינוי אפשר לראות כאן כמה התפנה.", "Updates every 2 seconds. After freeing memory you can see here how much was freed.")))

        val data = Mini.card(page, tr("📊 נתונים", "📊 Details"))
        tiles = Mini.tiles(data, listOf(tTotal to "—", tAvail to "—", tUsed to "—", tCached to "—", tSwap to "—", tZram to "—", tLow to "—", tState to "—"))

        kindsBody = Mini.card(page, tr("🧩 לפי סוג", "🧩 By kind"))
        kindsBody.addView(Mini.hint(this, tr("נטען דרך ADB…", "Loading over ADB…")))
        procBody = Mini.card(page, tr("🏆 מי תופס הכי הרבה זיכרון", "🏆 What uses the most memory"))
        procBody.addView(Mini.hint(this, tr("נטען דרך ADB… (כמה שניות)", "Loading over ADB… (a few seconds)")))
        actionsBody = Mini.card(page, tr("🛠 פעולות", "🛠 Actions"))
        renderActions()
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    // region live (Android, no ADB)

    private fun readLocal() {
        val am = getSystemService(ActivityManager::class.java) ?: return
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = mi.totalMem / 1048576
        val availMb = mi.availMem / 1048576
        val usedMb = totalMb - availMb
        if (startAvailMb < 0) startAvailMb = availMb
        val usedFraction = usedMb.toFloat() / totalMb.coerceAtLeast(1)
        ring.fraction = usedFraction
        ring.value = "${(usedFraction * 100).toInt()}%"
        ring.caption = tr("בשימוש", "in use")
        ring.color = when { usedFraction > 0.85f -> MiniColors.RED; usedFraction > 0.7f -> MiniColors.YELLOW; else -> MiniColors.PURPLE }
        ringNote.text = tr("${gb(usedMb)} מתוך ${gb(totalMb)} · פנויים ${gb(availMb)}", "${gb(usedMb)} of ${gb(totalMb)} · ${gb(availMb)} free")
        liveChart.add(availMb.toFloat())
        val delta = availMb - startAvailMb
        liveValue.text = tr("$availMb MB פנויים", "$availMb MB free") + if (delta != 0L) String.format(Locale.US, "   (%+d MB)", delta) else ""
        tiles[tTotal]?.text = gb(totalMb)
        tiles[tAvail]?.text = gb(availMb)
        tiles[tUsed]?.text = gb(usedMb)
        tiles[tLow]?.text = gb(mi.threshold / 1048576)
        tiles[tState]?.text = if (mi.lowMemory) tr("🔴 נמוך", "🔴 Low") else if (usedFraction > 0.85f) tr("🟡 עמוס", "🟡 Busy") else tr("🟢 תקין", "🟢 OK")
        if (beforeActionMb >= 0 && availMb != beforeActionMb) {
            val freed = availMb - beforeActionMb
            if (freed > 20) showStatus(tr("✔ התפנו כ־$freed MB", "✔ About $freed MB freed"))
        }
    }

    private fun gb(mb: Long) = if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0) else "$mb MB"

    // endregion

    // region ADB: breakdown, kinds, processes

    override fun refresh() {
        readLocal()
        adb("cat /proc/meminfo; echo '#MEMINFO'; dumpsys meminfo 2>/dev/null") { out ->
            if (out == null) {
                kindsBody.removeAllViews(); kindsBody.addView(Mini.hint(this, tr("נדרש חיבור ADB (צימוד).", "Needs an ADB connection (pairing).")))
                procBody.removeAllViews(); procBody.addView(Mini.hint(this, tr("נדרש חיבור ADB (צימוד).", "Needs an ADB connection (pairing).")))
                return@adb
            }
            val proc = out.substringBefore("#MEMINFO")
            val dump = out.substringAfter("#MEMINFO", "")
            renderMeminfo(proc)
            renderKinds(dump)
            renderProcesses(dump)
        }
    }

    /** /proc/meminfo → the colored bar: apps · cache · buffers · kernel · free. */
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
            Triple(tr("📱 אפליקציות ותהליכים", "📱 Apps and processes"), apps, MiniColors.PURPLE),
            Triple(tr("🗂 מטמון (מתפנה לבד כשצריך)", "🗂 Cache (freed by itself when needed)"), cached, MiniColors.TEAL),
            Triple(tr("🧱 ליבה ומערכת", "🧱 Kernel and system"), kernel, MiniColors.ORANGE),
            Triple(tr("📥 Buffers", "📥 Buffers"), buffers, MiniColors.YELLOW),
            Triple(tr("⚪ פנוי לגמרי", "⚪ Completely free"), free, 0xFFD9CFC5.toInt()),
        ).filter { it.second > 0 }
        breakdown.parts = parts.map { it.second.toFloat() to it.third }
        legendBody.removeAllViews()
        parts.forEach { (name, kb, color) -> Mini.legend(legendBody, color, name, Mini.kb(kb)) }
        tiles[tCached]?.text = Mini.kb(cached)
        val swapTotal = m["SwapTotal"] ?: 0
        val swapUsed = swapTotal - (m["SwapFree"] ?: 0)
        tiles[tSwap]?.text = if (swapTotal > 0) "${Mini.kb(swapUsed)} / ${Mini.kb(swapTotal)}" else tr("אין", "None")
    }

    private fun num(s: String) = s.replace(",", "").toLongOrNull() ?: 0L

    /** "Total PSS by OOM adjustment": memory by kind (foreground, services, cached…). */
    private fun renderKinds(dump: String) {
        kindsBody.removeAllViews()
        val section = Regex("Total [PR]SS by OOM adjustment:\\n([\\s\\S]*?)\\n\\s*\\n").find(dump)?.groupValues?.get(1)
        // Kind lines only ("412,345K: Native"); the processes under each kind end in "(pid …)" and don't match.
        val kinds = section?.lines()?.mapNotNull { Regex("^\\s+([\\d,]+)K: ([A-Za-z][A-Za-z ]*?)\\s*$").find(it) }
            ?.map { kindName(it.groupValues[2]) to num(it.groupValues[1]) }?.filter { it.second > 0 }.orEmpty()
        if (kinds.isEmpty()) { kindsBody.addView(Mini.hint(this, tr("הטלפון לא החזיר פירוט.", "The phone returned no breakdown."))); return }
        val max = kinds.maxOf { it.second }.toFloat()
        kinds.forEachIndexed { i, (name, kb) -> Mini.barRow(kindsBody, name, Mini.kb(kb), kb / max, MiniColors.PALETTE[i % MiniColors.PALETTE.size]) }
        kindsBody.addView(Mini.hint(this, tr("\"במטמון\" = אפליקציות שנשמרו בזיכרון לפתיחה מהירה. Android מפנה אותן לבד כשצריך מקום.",
            "\"Cached\" = apps kept in memory to open fast. Android frees them by itself when it needs room.")))
        // The summary lines (ZRAM)
        Regex("ZRAM:\\s+([\\d,]+)K physical used for\\s+([\\d,]+)K in swap").find(dump)?.let {
            tiles[tZram]?.text = tr("${Mini.kb(num(it.groupValues[1]))} דוחסים ${Mini.kb(num(it.groupValues[2]))}", "${Mini.kb(num(it.groupValues[1]))} holds ${Mini.kb(num(it.groupValues[2]))}")
        } ?: run { tiles[tZram]?.text = "—" }
    }

    private fun kindName(raw: String) = when (raw.trim().lowercase()) {
        "native" -> tr("⚙ רכיבי מערכת (Native)", "⚙ Native")
        "system" -> tr("🤖 מערכת Android", "🤖 Android system")
        "persistent", "persistent service" -> tr("📌 קבועים (טלפון, ממשק)", "📌 Persistent (phone, UI)")
        "foreground" -> tr("🟢 האפליקציה הפתוחה", "🟢 The open app")
        "visible" -> tr("👁 נראים", "👁 Visible")
        "perceptible", "perceptible low", "perceptible medium" -> tr("🎵 מורגשים (מוזיקה, ניווט)", "🎵 Perceptible (music, navigation)")
        "backup" -> tr("💾 גיבוי", "💾 Backup")
        "heavy weight" -> tr("🏋 כבדים", "🏋 Heavy")
        "a services", "b services" -> tr("🔧 שירותים ברקע", "🔧 Background services")
        "home" -> tr("🏠 מסך הבית", "🏠 Home screen")
        "previous" -> tr("↩ האפליקציה הקודמת", "↩ The previous app")
        "cached" -> tr("🗂 במטמון (מתפנה לבד)", "🗂 Cached (freed by itself)")
        else -> raw
    }

    /** "Total PSS by process": the processes that hold the most memory (tap: actions). */
    private fun renderProcesses(dump: String) {
        procBody.removeAllViews()
        val section = Regex("Total PSS by process:\\n([\\s\\S]*?)\\n\\s*\\n").find(dump)?.groupValues?.get(1)
            ?: Regex("Total RSS by process:\\n([\\s\\S]*?)\\n\\s*\\n").find(dump)?.groupValues?.get(1)
        val rows = section?.lines()?.mapNotNull { Regex("^\\s*([\\d,]+)K: (\\S+) \\(pid (\\d+)").find(it) }
            ?.map { it.groupValues[2] to num(it.groupValues[1]) }.orEmpty()
        if (rows.isEmpty()) { procBody.addView(Mini.hint(this, tr("הטלפון לא החזיר רשימת תהליכים.", "The phone returned no process list."))); return }
        procBody.addView(Mini.hint(this, tr("לחיצה על אפליקציה: עצירה, שחרור זיכרון, הגבלת רקע", "Tap an app: stop, release memory, background limit")))
        val top = rows.take(15)
        val max = top.first().second.toFloat()
        top.forEachIndexed { i, (process, kb) ->
            val pkg = process.substringBefore(':')
            val isApp = pkg.contains('.') && pkg != "system"
            val label = if (isApp) appLabel(pkg) + if (process.contains(':')) " (${process.substringAfter(':')})" else "" else process
            Mini.barRow(procBody, label, Mini.kb(kb), kb / max, MiniColors.PALETTE[i % MiniColors.PALETTE.size], if (isApp) ({ appMenu(pkg, label) }) else null)
        }
    }

    // endregion

    private fun renderActions() {
        actionsBody.removeAllViews()
        val aggressive = tr("🧨 פינוי אגרסיבי", "🧨 Aggressive cleanup")
        fun run(label: String, command: String, confirm: String? = null) {
            beforeActionMb = getSystemService(ActivityManager::class.java)?.let { am -> ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem / 1048576 } ?: -1
            act(label, command, confirm)
        }
        Mini.actions(actionsBody, listOf(
            tr("🧠 פנה RAM", "🧠 Free RAM") to { run(tr("פינוי RAM", "Free RAM"), TerminalShortcuts.FREE_RAM) },
            aggressive to {
                run(aggressive, TerminalShortcuts.AGGRESSIVE_RAM,
                    tr("כל האפליקציות שהורדת ורצות עכשיו ייסגרו (חוץ מהבוס, המקלדת ומסך הבית). הן לא ישלחו התראות עד שתפתח אותן שוב. להמשיך?",
                        "Every downloaded app that is running now will be closed (except The Boss, the keyboard and the home screen). They won't send notifications until you open them again. Continue?"))
            },
            tr("💤 סגור תהליכים ברקע", "💤 Close background") to { run(tr("סגירת תהליכים ברקע", "Close background processes"), "am kill-all && echo done") },
            tr("🗜 דחוס זיכרון", "🗜 Compact memory") to { run(tr("דחיסת זיכרון", "Compact memory"), "am compact system 2>&1 | tail -1; echo done") },
        ), danger = setOf(aggressive))
        actionsBody.addView(Mini.hint(this, tr("טיפ: Android משתמש ב־RAM פנוי כמטמון כדי לפתוח אפליקציות מהר — זיכרון \"מלא\" לא תמיד בעיה.",
            "Tip: Android uses free RAM as cache to open apps fast — \"full\" memory isn't always a problem.")))
    }
}
