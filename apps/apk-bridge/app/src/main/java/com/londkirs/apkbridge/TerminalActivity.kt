package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ADB terminal as a WhatsApp-style chat (dark mode): every command is an outgoing message,
 * its output is the reply. Shortcuts run on tap and go to the input line on long-press;
 * the user can add, edit and delete shortcuts. Commands run as the ADB "shell" user via [Bridge].
 */
class TerminalActivity : Activity() {
    private class Message(
        val outgoing: Boolean,
        val time: String,
        var text: CharSequence,
        var status: String = "",  // outgoing: "run" | "ok" | "err:<code>" | "stop"
    )

    private class Shortcut(var label: String, var command: String)

    private val messages = mutableListOf<Message>()
    private val bubbleTexts = HashMap<Message, TextView>()
    private val bubbleStatus = HashMap<Message, TextView>()

    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var subtitle: TextView
    private lateinit var chips: LinearLayout
    private lateinit var sendButton: TextView
    private var current: Message? = null
    private var currentReply: Message? = null
    private var historyIndex = -1

    private val stateListener: (BridgeState) -> Unit = { renderState(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
        loadChat()
        if (messages.isEmpty()) welcome()
        rebuildChips()
    }

    override fun onStart() {
        super.onStart()
        Bridge.addStateListener(stateListener)
    }

    override fun onStop() {
        Bridge.removeStateListener(stateListener)
        saveChat()
        super.onStop()
    }

    // region layout

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CHAT_BG)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        // Header (like a WhatsApp conversation header)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(HEADER)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        header.addView(iconButton("→") { finish() })
        header.addView(TextView(this).apply {
            text = ">_"
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF0B1F17.toInt()) }
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4); marginEnd = dp(10) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "ADB Terminal"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
        })
        subtitle = TextView(this).apply { textSize = 12.5f; setTextColor(MUTED); maxLines = 1 }
        titles.addView(subtitle)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(iconButton("⟲") { showHistory() })
        header.addView(iconButton("⋮") { menu() })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Messages
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        scroll = ScrollView(this).apply {
            addView(list)
            isFillViewport = true
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Shortcuts row
        chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Input bar: [ pill input ] (➤)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(6), dp(4), dp(6), dp(8))
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(INPUT_BG) }
            setPadding(dp(6), 0, dp(14), 0)
        }
        pill.addView(iconButton("+") { addShortcut(input.text.toString().trim()) }.apply { setTextColor(MUTED) })
        input = EditText(this).apply {
            hint = "הקלד פקודה"
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            background = null
            maxLines = 5
            minHeight = dp(46)
            setPadding(dp(4), dp(10), dp(4), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textDirection = View.TEXT_DIRECTION_LTR
            if (Build.VERSION.SDK_INT >= 29) {
                textCursorDrawable = GradientDrawable().apply { setColor(ACCENT); setSize(dp(2), dp(20)) }
            }
        }
        pill.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        sendButton = TextView(this).apply {
            text = "➤"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            // The arrow points right; mirror it so it points "forward" in a right-to-left chat.
            scaleX = -1f
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) }
            setOnClickListener { if (Bridge.state.busy && current != null) Bridge.cancelCurrent() else submit(input.text.toString()) }
        }
        bar.addView(sendButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        Compat.applySystemBarInsets(this, root, includeIme = true)
        setContentView(root)
        renderState(Bridge.state)
    }

    private fun iconButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 20f
        setTextColor(MUTED)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        isClickable = true
        setOnClickListener { action() }
    }

    private fun renderState(state: BridgeState) {
        if (!::subtitle.isInitialized) return
        val running = state.busy && current != null
        subtitle.text = when {
            running -> "מריץ…"
            state.busy -> "עסוק…"
            Bridge.isPaired || Bridge.keepAdbWithoutWifi -> "Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"
            else -> "לא מזווג — זווג קודם ב־APK Bridge"
        }
        subtitle.setTextColor(if (running) ACCENT else MUTED)
        sendButton.text = if (running) "■" else "➤"
        sendButton.scaleX = if (running) 1f else -1f
        (sendButton.background as? GradientDrawable)?.setColor(if (running) STOP else ACCENT)
    }

    // endregion

    // region messages

    private fun now() = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    private fun welcome() {
        addMessage(Message(false, now(), "שלום! כאן אפשר להריץ כל פקודת ADB.\n\n" +
            "• הקלד פקודה ולחץ ➤ (אפשר גם \"adb shell …\").\n" +
            "• קיצור: לחיצה מריצה מיד, לחיצה ארוכה מעבירה לעריכה.\n" +
            "• + מוסיף קיצור משלך, ⚙ עורך ומוחק.\n" +
            "• לחיצה ארוכה על הודעה: העתקה, הרצה שוב או עריכה.\n\n" +
            "הפקודות רצות כמשתמש shell (לא root)."))
    }

    private fun addMessage(m: Message) {
        messages += m
        list.addView(bubble(m))
        while (messages.size > MAX_MESSAGES) {
            val old = messages.removeAt(0)
            bubbleTexts.remove(old); bubbleStatus.remove(old)
            list.removeViewAt(0)
        }
        scrollDown()
    }

    private fun bubble(m: Message): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // As in Hebrew (right-to-left) WhatsApp: own messages on the left, replies on the right.
            gravity = if (m.outgoing) Gravity.LEFT else Gravity.RIGHT
            setPadding(0, dp(2), 0, dp(2))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(if (m.outgoing) OUT_BG else IN_BG)
                val r = dp(10).toFloat(); val tail = dp(2).toFloat()
                        // Order: top-left, top-right, bottom-right, bottom-left. Outgoing (left) has its tail top-left.
                cornerRadii = if (m.outgoing) floatArrayOf(tail, tail, r, r, r, r, r, r) else floatArrayOf(r, r, tail, tail, r, r, r, r)
            }
        }
        val text = TextView(this).apply {
            this.text = m.text
            textSize = if (m.outgoing) 15f else 13f
            typeface = Typeface.MONOSPACE
            setTextColor(TEXT)
            textDirection = if (isPlainHebrew(m)) View.TEXT_DIRECTION_ANY_RTL else View.TEXT_DIRECTION_LTR
            setLineSpacing(0f, 1.08f)
        }
        box.addView(text)
        val meta = TextView(this).apply {
            textSize = 11f
            setTextColor(MUTED)
            gravity = Gravity.END
            textDirection = View.TEXT_DIRECTION_LTR
        }
        box.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bubbleTexts[m] = text
        bubbleStatus[m] = meta
        renderMeta(m)
        box.setOnLongClickListener { messageMenu(m); true }
        val maxWidth = (resources.displayMetrics.widthPixels * 0.86f).toInt()
        text.maxWidth = maxWidth - dp(20)
        row.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun isPlainHebrew(m: Message) = !m.outgoing && m.text.any { it in '֐'..'׿' } && !m.text.contains('$')

    private fun renderMeta(m: Message) {
        val meta = bubbleStatus[m] ?: return
        val tick = when {
            !m.outgoing -> ""
            m.status == "run" -> "  ◷"
            m.status == "ok" -> "  ✓✓"
            m.status == "stop" -> "  ⊘"
            m.status.startsWith("err:") -> "  ✗ ${m.status.removePrefix("err:")}"
            else -> ""
        }
        val sb = SpannableStringBuilder(m.time + tick)
        if (tick.isNotEmpty()) {
            val color = when {
                m.status == "ok" -> TICK_BLUE
                m.status.startsWith("err:") || m.status == "stop" -> ERROR
                else -> MUTED
            }
            sb.setSpan(ForegroundColorSpan(color), m.time.length, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        meta.text = sb
    }

    private fun appendReply(text: String, error: Boolean) {
        val reply = currentReply ?: Message(false, now(), "").also { currentReply = it; addMessage(it) }
        val sb = SpannableStringBuilder(reply.text)
        val start = sb.length
        sb.append(text)
        if (error) sb.setSpan(ForegroundColorSpan(ERROR), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val trimmed: CharSequence = if (sb.length > MAX_REPLY) sb.subSequence(sb.length - MAX_REPLY, sb.length) else sb
        reply.text = trimmed
        bubbleTexts[reply]?.text = trimmed
        scrollDown()
    }

    private fun scrollDown() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun messageMenu(m: Message) {
        val raw = m.text.toString()
        val items = mutableListOf("העתק")
        if (m.outgoing) { items += "הרץ שוב"; items += "ערוך"; items += "שמור כקיצור" }
        AlertDialog.Builder(this, DIALOG)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "העתק" -> copy(raw)
                    "הרץ שוב" -> submit(raw)
                    "ערוך" -> setInput(raw)
                    "שמור כקיצור" -> addShortcut(raw)
                }
            }
            .show()
    }

    // endregion

    // region running commands

    private fun submit(rawInput: String) {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return
        when (raw) {
            "clear", "cls" -> { clearChat(); input.setText(""); return }
            "exit" -> { finish(); return }
        }
        val cmd = Bridge.normalizeCommand(raw)
        if (cmd.isEmpty()) {
            addMessage(Message(false, now(), "מסוף אינטראקטיבי (adb shell בלי פקודה) לא נתמך — כתוב פקודה מלאה."))
            return
        }
        if (DANGEROUS.any { cmd.contains(it) }) {
            AlertDialog.Builder(this, DIALOG)
                .setTitle("פקודה שעלולה להזיק")
                .setMessage("הפקודה עלולה למחוק נתונים, להסיר אפליקציות או להפעיל מחדש את המכשיר:\n\n$cmd\n\nלהריץ בכל זאת?")
                .setPositiveButton("הרץ") { _, _ -> execute(raw, cmd) }
                .setNegativeButton("ביטול", null)
                .show()
        } else {
            execute(raw, cmd)
        }
    }

    private fun execute(raw: String, cmd: String) {
        if (Bridge.state.busy) {
            Toast.makeText(this, "פעולה אחרת עדיין רצה — נסה שוב בעוד רגע.", Toast.LENGTH_SHORT).show()
            return
        }
        val msg = Message(true, now(), cmd, "run")
        addMessage(msg)
        current = msg
        currentReply = null
        historyIndex = -1
        val started = Bridge.runCommand(
            raw,
            onOutput = { text, isError -> appendReply(text, isError) },
            onExit = { code ->
                msg.status = when (code) {
                    null -> "stop"
                    0 -> "ok"
                    else -> "err:$code"
                }
                renderMeta(msg)
                if (currentReply == null) {
                    addMessage(Message(false, now(), if (code == null) "הפקודה לא הושלמה. הסיבה מופיעה ביומן של APK Bridge." else "(אין פלט)"))
                }
                current = null
                currentReply = null
                renderState(Bridge.state)
                saveChat()
            },
        )
        if (!started) {
            msg.status = "stop"
            renderMeta(msg)
            current = null
            return
        }
        saveHistory(raw)
        input.setText("")
        renderState(Bridge.state)
    }

    private fun setInput(cmd: String) {
        input.setText(cmd)
        input.setSelection(cmd.length)
        input.requestFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(input, 0)
    }

    // endregion

    // region shortcuts

    private fun shortcuts(): MutableList<Shortcut> {
        val json = prefs().getString("shortcuts", null) ?: return DEFAULT_SHORTCUTS.map { Shortcut(it.first, it.second) }.toMutableList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Shortcut(o.getString("l"), o.getString("c")) } }.toMutableList()
        }.getOrElse { DEFAULT_SHORTCUTS.map { Shortcut(it.first, it.second) }.toMutableList() }
    }

    private fun saveShortcuts(items: List<Shortcut>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("l", it.label).put("c", it.command)) }
        prefs().edit().putString("shortcuts", arr.toString()).apply()
        rebuildChips()
    }

    private fun rebuildChips() {
        chips.removeAllViews()
        chips.addView(chip("+ קיצור", accent = true).apply { setOnClickListener { addShortcut(input.text.toString().trim()) } })
        chips.addView(chip("⚙", accent = true).apply { setOnClickListener { manageShortcuts() } })
        for (s in shortcuts()) {
            chips.addView(chip(s.label, accent = false).apply {
                setOnClickListener { submit(s.command) }                     // tap: run now
                setOnLongClickListener { setInput(s.command); true }         // long-press: edit in the input line
            })
        }
    }

    private fun chip(label: String, accent: Boolean) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(if (accent) ACCENT else TEXT)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(HEADER)
            setStroke(dp(1), if (accent) ACCENT else 0xFF2F3F48.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
        isClickable = true
        isLongClickable = true
    }

    private fun shortcutDialog(title: String, initial: Shortcut?, onSave: (Shortcut) -> Unit, onDelete: (() -> Unit)?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply { hint = "שם הקיצור"; setText(initial?.label.orEmpty()); setSingleLine(true) }
        val command = EditText(this).apply {
            hint = "פקודה, למשל: pm list packages -3"
            setText(initial?.command.orEmpty())
            typeface = Typeface.MONOSPACE
            textDirection = View.TEXT_DIRECTION_LTR
        }
        box.addView(name); box.addView(command)
        val b = AlertDialog.Builder(this, DIALOG)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("שמור") { _, _ ->
                val c = command.text.toString().trim()
                if (c.isEmpty()) return@setPositiveButton
                val l = name.text.toString().trim().ifEmpty { c.take(18) }
                onSave(Shortcut(l, c))
            }
            .setNegativeButton("ביטול", null)
        if (onDelete != null) b.setNeutralButton("מחק") { _, _ -> onDelete() }
        b.show()
    }

    private fun addShortcut(prefill: String) {
        shortcutDialog("קיצור חדש", if (prefill.isNotEmpty()) Shortcut("", prefill) else null, { s ->
            saveShortcuts(shortcuts().apply { add(0, s) })
            Toast.makeText(this, "הקיצור \"${s.label}\" נוסף.", Toast.LENGTH_SHORT).show()
        }, null)
    }

    private fun manageShortcuts() {
        val items = shortcuts()
        val labels = items.map { "${it.label}   ·   ${it.command}" }.toMutableList()
        labels += "＋ הוסף קיצור"
        labels += "↺ שחזר קיצורי ברירת מחדל"
        AlertDialog.Builder(this, DIALOG)
            .setTitle("עריכת קיצורים")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    items.size -> addShortcut("")
                    items.size + 1 -> {
                        prefs().edit().remove("shortcuts").apply()
                        rebuildChips()
                    }
                    else -> shortcutDialog("עריכת קיצור", items[which], { s ->
                        items[which] = s
                        saveShortcuts(items)
                    }, {
                        items.removeAt(which)
                        saveShortcuts(items)
                    })
                }
            }
            .setNegativeButton("סגור", null)
            .show()
    }

    // endregion

    // region menus, history, persistence

    private fun menu() {
        val items = arrayOf("עריכת קיצורים", "העתק את כל השיחה", "נקה שיחה", "סגור")
        AlertDialog.Builder(this, DIALOG)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> manageShortcuts()
                    1 -> copy(messages.joinToString("\n\n") { (if (it.outgoing) "$ " else "") + it.text })
                    2 -> clearChat()
                    3 -> finish()
                }
            }
            .show()
    }

    private fun clearChat() {
        messages.clear(); bubbleTexts.clear(); bubbleStatus.clear()
        list.removeAllViews()
        current = null; currentReply = null
        saveChat()
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ADB", text))
        Toast.makeText(this, "הועתק.", Toast.LENGTH_SHORT).show()
    }

    private fun prefs() = getSharedPreferences("terminal", Context.MODE_PRIVATE)

    private fun history(): List<String> =
        prefs().getString("history", "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun saveHistory(cmd: String) {
        val list = (listOf(cmd) + history().filter { it != cmd }).take(60)
        prefs().edit().putString("history", list.joinToString("\n")).apply()
    }

    private fun showHistory() {
        val items = history()
        if (items.isEmpty()) {
            Toast.makeText(this, "אין עדיין היסטוריה.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle("פקודות אחרונות")
            .setItems(items.toTypedArray()) { _, which -> setInput(items[which]) }
            .setNegativeButton("סגור", null)
            .show()
    }

    /** Keeps the conversation between visits (plain text; colors of stderr are not kept). */
    private fun saveChat() {
        val arr = JSONArray()
        messages.takeLast(MAX_SAVED).forEach {
            arr.put(JSONObject().put("o", it.outgoing).put("t", it.time).put("x", it.text.toString().takeLast(MAX_REPLY)).put("s", if (it.status == "run") "stop" else it.status))
        }
        prefs().edit().putString("chat", arr.toString()).apply()
    }

    private fun loadChat() {
        val json = prefs().getString("chat", null) ?: return
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                addMessage(Message(o.getBoolean("o"), o.getString("t"), o.getString("x"), o.optString("s")))
            }
        }
    }

    // endregion

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_MESSAGES = 200
        private const val MAX_SAVED = 80
        private const val MAX_REPLY = 60_000
        private val DIALOG = android.R.style.Theme_Material_Dialog_Alert

        // WhatsApp dark-mode palette
        private val CHAT_BG = 0xFF0B141A.toInt()
        private val HEADER = 0xFF1F2C34.toInt()
        private val INPUT_BG = 0xFF2A3942.toInt()
        private val OUT_BG = 0xFF005C4B.toInt()
        private val IN_BG = 0xFF202C33.toInt()
        private val ACCENT = 0xFF00A884.toInt()
        private val TEXT = 0xFFE9EDEF.toInt()
        private val MUTED = 0xFF8696A0.toInt()
        private val TICK_BLUE = 0xFF53BDEB.toInt()
        private val ERROR = 0xFFFF6B6B.toInt()
        private val STOP = 0xFFE5534B.toInt()

        private val DANGEROUS = listOf("rm -r", "rm -f", "reboot", "wipe", "pm uninstall", "pm clear", "format", "dd ", "mkfs", "svc power")
        private val DEFAULT_SHORTCUTS = listOf(
            "גרסה" to "getprop ro.build.version.release",
            "דגם" to "getprop ro.product.model",
            "אפליקציות" to "pm list packages -3",
            "סוללה" to "dumpsys battery",
            "מסך" to "wm size; wm density",
            "אחסון" to "df -h /data /sdcard",
            "זיכרון" to "cat /proc/meminfo | head -5",
            "תהליכים" to "top -b -n 1 | head -25",
            "רשת" to "ip addr show wlan0",
            "לוג" to "logcat -d -t 100",
            "צילום מסך" to "screencap -p /sdcard/Download/screenshot.png && echo saved to Download",
            "הפעלה מחדש" to "reboot",
        )
    }
}
