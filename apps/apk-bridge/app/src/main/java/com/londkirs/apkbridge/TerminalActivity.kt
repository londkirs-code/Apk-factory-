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
 * ADB terminal as a WhatsApp-style chat (light, like the regular WhatsApp): every command is an outgoing message,
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

    private class Shortcut(var label: String, var command: String, var group: String = MINE)

    private val messages = mutableListOf<Message>()
    private val bubbleTexts = HashMap<Message, TextView>()
    private val bubbleStatus = HashMap<Message, TextView>()

    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var subtitle: TextView
    private lateinit var chips: LinearLayout
    private lateinit var groupChips: LinearLayout
    private lateinit var chipsScroll: HorizontalScrollView
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

    override fun onResume() {
        super.onResume()
        Celebration.onResume(this)
    }

    override fun onPause() {
        Celebration.onPause(this)
        super.onPause()
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
            layoutDirection = Lang.direction
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
            setTextColor(HEADER)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) }
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4); marginEnd = dp(10) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "ADB Terminal"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(HEADER_TEXT)
        })
        subtitle = TextView(this).apply { textSize = 12.5f; setTextColor(HEADER_SUB); maxLines = 1 }
        titles.addView(subtitle)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(iconButton("⧉") { copyChat() }.apply { contentDescription = tr("העתק שיחה", "Copy chat") })
        header.addView(iconButton("🗑") { confirmClearChat() }.apply { contentDescription = tr("נקה שיחה", "Clear chat"); textSize = 18f })
        header.addView(iconButton("⟲") { showHistory() }.apply { contentDescription = tr("היסטוריה", "History") })
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

        // Shortcuts: a row of categories (with paste / add / edit), and the shortcuts of the chosen category.
        groupChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), 0)
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(groupChips)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        chipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        root.addView(chipsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
        pill.addView(iconButton("📋") { pasteFromClipboard() }.apply { textSize = 18f; Compat.focusRing(this, ACCENT, 20f) })
        input = EditText(this).apply {
            hint = tr("הקלד פקודה", "Type a command")
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            background = null
            maxLines = 5
            minHeight = dp(46)
            setPadding(dp(4), dp(10), dp(4), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setHorizontallyScrolling(false)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            textDirection = View.TEXT_DIRECTION_LTR
            setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || enter) { submit(text.toString()); true } else false
            }
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
            Compat.focusRing(this, 0xFFFFFFFF.toInt(), 24f)
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
        setTextColor(HEADER_TEXT)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        isClickable = true
        setOnClickListener { action() }
        Compat.focusRing(this, HEADER_TEXT, 20f)
    }

    private fun renderState(state: BridgeState) {
        if (!::subtitle.isInitialized) return
        val running = state.busy && current != null
        subtitle.text = when {
            running -> tr("מריץ…", "Running…")
            state.busy -> tr("עסוק…", "Busy…")
            Bridge.isPaired || Bridge.keepAdbWithoutWifi -> "Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"
            else -> tr("לא מצומד — בצע צימוד קודם באפליקציית הבוס", "Not paired — pair first in The Boss")
        }
        subtitle.setTextColor(if (running) HEADER_TEXT else HEADER_SUB)
        sendButton.text = if (running) "■" else "➤"
        sendButton.scaleX = if (running) 1f else -1f
        (sendButton.background as? GradientDrawable)?.setColor(if (running) STOP else ACCENT)
    }

    // endregion

    // region messages

    private fun now() = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    private fun welcome() {
        addMessage(Message(false, now(), tr("שלום! כאן אפשר להריץ כל פקודת ADB.\n\n", "Hi! Here you can run any ADB command.\n\n") +
            tr("• הקלד פקודה ולחץ ➤ (אפשר גם \"adb shell …\").\n", "• Type a command and tap ➤ (\"adb shell …\" works too).\n") +
            tr("• קיצור: לחיצה מריצה מיד, לחיצה ארוכה מעבירה לעריכה.\n", "• Shortcut: tap runs it right away, long-press moves it to editing.\n") +
            tr("• + מוסיף קיצור משלך, ⚙ עורך ומוחק.\n", "• + adds your own shortcut, ⚙ edits and deletes.\n") +
            tr("• לחיצה ארוכה על הודעה: העתקה, הרצה שוב או עריכה.\n\n", "• Long-press a message: copy, run again or edit.\n\n") +
            tr("הפקודות רצות כמשתמש shell (לא root).", "Commands run as the shell user (not root).")))
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
            // As in WhatsApp: own messages on the left in Hebrew (right-to-left), on the right in English.
            gravity = if (m.outgoing == Lang.hebrew) Gravity.LEFT else Gravity.RIGHT
            setPadding(0, dp(2), 0, dp(2))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(if (m.outgoing) OUT_BG else IN_BG)
                val r = dp(10).toFloat(); val tail = dp(2).toFloat()
                // Order: top-left, top-right, bottom-right, bottom-left. The tail is on the bubble's outer top corner.
                cornerRadii = if (m.outgoing == Lang.hebrew) floatArrayOf(tail, tail, r, r, r, r, r, r) else floatArrayOf(r, r, tail, tail, r, r, r, r)
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
        box.elevation = dp(1).toFloat()  // the soft shadow WhatsApp bubbles have
        box.setOnLongClickListener { messageMenu(m); true }
        // Keypad: the arrows step through messages; OK opens the message menu.
        box.setOnClickListener { if (!box.isInTouchMode) messageMenu(m) }
        Compat.focusRing(box, ACCENT, 10f)
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
        val items = mutableListOf(tr("העתק", "Copy"))
        if (m.outgoing) { items += tr("הרץ שוב", "Run again"); items += tr("ערוך", "Edit"); items += tr("שמור כקיצור", "Save as shortcut") }
        AlertDialog.Builder(this, DIALOG)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    tr("העתק", "Copy") -> copy(raw)
                    tr("הרץ שוב", "Run again") -> submit(raw)
                    tr("ערוך", "Edit") -> setInput(raw)
                    tr("שמור כקיצור", "Save as shortcut") -> addShortcut(raw)
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
        val parsed = CommandParser.parse(raw)
        val cmd = parsed.command
        if (cmd.isNullOrBlank()) {
            addMessage(Message(false, now(), parsed.note ?: tr("לא נמצאה פקודה להרצה.", "No command found to run.")))
            return
        }
        // Tell the user once what was understood (e.g. "adb shell" removed, adb uninstall → pm uninstall).
        parsed.note?.let { addMessage(Message(false, now(), it)) }
        if (DANGEROUS.any { cmd.contains(it) }) {
            AlertDialog.Builder(this, DIALOG)
                .setTitle(tr("פקודה שעלולה להזיק", "Potentially harmful command"))
                .setMessage(tr("הפקודה עלולה למחוק נתונים, להסיר אפליקציות או להפעיל מחדש את המכשיר:\n\n$cmd\n\nלהריץ בכל זאת?", "This command may delete data, uninstall apps or restart the device:\n\n$cmd\n\nRun it anyway?"))
                .setPositiveButton(tr("הרץ", "Run")) { _, _ -> execute(raw, cmd) }
                .setNegativeButton(tr("ביטול", "Cancel"), null)
                .show()
        } else {
            execute(raw, cmd)
        }
    }

    private fun execute(raw: String, cmd: String) {
        if (Bridge.state.busy) {
            Toast.makeText(this, tr("פעולה אחרת עדיין רצה — נסה שוב בעוד רגע.", "Another action is still running — try again in a moment."), Toast.LENGTH_SHORT).show()
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
                    addMessage(Message(false, now(), if (code == null) tr("הפקודה לא הושלמה. הסיבה מופיעה ביומן של הבוס.", "The command didn't complete. The reason is in The Boss's log.") else tr("(אין פלט)", "(no output)")))
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

    // region clipboard

    private fun clipboardText(): String? = runCatching {
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        clip.getItemAt(0).coerceToText(this)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** 📋: pastes the clipboard; if it looks like a command, offers to run it right away. */
    private fun pasteFromClipboard() {
        val text = clipboardText()
        if (text == null) {
            Toast.makeText(this, tr("הלוח ריק.", "The clipboard is empty."), Toast.LENGTH_SHORT).show()
            return
        }
        if (CommandParser.looksLikeCommand(text)) offerClipboardCommand(text, fromPasteButton = true) else setInput(text)
    }

    private fun offerClipboardCommand(text: String, fromPasteButton: Boolean) {
        val parsed = CommandParser.parse(text)
        val cmd = parsed.command
        if (cmd.isNullOrBlank()) {
            if (fromPasteButton) setInput(text)
            parsed.note?.let { addMessage(Message(false, now(), it)) }
            return
        }
        val message = buildString {
            append(if (fromPasteButton) tr("זוהתה פקודה בלוח:", "Command detected in the clipboard:") else tr("בלוח יש פקודה שנראית רלוונטית:", "The clipboard has a command that looks relevant:"))
            append("\n\n").append(cmd)
            parsed.note?.let { append("\n\n").append(it) }
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("להריץ את הפקודה?", "Run the command?"))
            .setMessage(message)
            .setPositiveButton(tr("הרץ", "Run")) { _, _ -> submit(text) }
            .setNeutralButton(tr("לעריכה", "Edit")) { _, _ -> setInput(cmd) }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
            .show()
    }

    /** When the user returns with a new command on the clipboard (e.g. copied from a guide), ask once. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Celebration.onFocus(this, hasFocus)
        if (!hasFocus || !prefs().getBoolean("clipboard_detect", true) || Bridge.state.busy) return
        val text = clipboardText() ?: return
        val key = text.hashCode()
        if (prefs().getInt("clipboard_offered", 0) == key) return
        prefs().edit().putInt("clipboard_offered", key).apply()
        if (CommandParser.looksLikeCommand(text)) offerClipboardCommand(text, fromPasteButton = false)
    }

    // endregion

    // region keypad

    /**
     * Keypad / D-pad phones: arrows move between messages, shortcuts and buttons (with a focus ring);
     * OK clicks, holding OK long-clicks. Extra keys: CALL sends, MENU opens the menu,
     * 1–9 run the matching shortcut and * pastes, while the input line is not focused.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val typing = input.hasFocus()
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_MENU -> { menu(); return true }
            android.view.KeyEvent.KEYCODE_CALL -> { submit(input.text.toString()); return true }
        }
        if (!typing) {
            if (keyCode in android.view.KeyEvent.KEYCODE_1..android.view.KeyEvent.KEYCODE_9) {
                val index = keyCode - android.view.KeyEvent.KEYCODE_1
                shortcutsInGroup().getOrNull(index)?.let { submit(it.command); return true }
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_STAR) { pasteFromClipboard(); return true }
            if (keyCode == android.view.KeyEvent.KEYCODE_POUND) { input.requestFocus(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun keyHelp() {
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("קיצורי מקשים", "Keyboard shortcuts"))
            .setMessage(
                tr("חצים — מעבר בין הודעות, קיצורים וכפתורים\n", "Arrows — move between messages, shortcuts and buttons\n") +
                    tr("OK — לחיצה · החזקת OK — לחיצה ארוכה (עריכת קיצור / תפריט הודעה)\n", "OK — tap · Hold OK — long-press (edit shortcut / message menu)\n") +
                    tr("חיוג (ירוק) — שליחת הפקודה\n", "Call (green) — send the command\n") +
                    tr("תפריט — תפריט המסוף\n", "Menu — terminal menu\n") +
                    tr("1–9 — הרצת הקיצור המתאים\n", "1–9 — run the matching shortcut\n") +
                    tr("* — הדבקה מהלוח\n", "* — paste from clipboard\n") +
                    tr("# — מעבר לשורת ההקלדה\n", "# — go to the input line\n") +
                    tr("חזור — יציאה", "Back — exit")
            )
            .setPositiveButton(tr("הבנתי", "Got it"), null)
            .show()
    }

    // endregion

    private fun setInput(cmd: String) {
        input.setText(cmd)
        input.setSelection(cmd.length)
        input.requestFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(input, 0)
    }

    // endregion

    // region shortcuts

    private fun defaults() = DEFAULT_SHORTCUTS.map { Shortcut(it.label, it.command, it.group) }.toMutableList()

    /** Built-in shortcuts are shown in the current language, whichever language they were saved in. */
    private fun shown(sc: Shortcut): String =
        DEFAULT_SHORTCUTS.firstOrNull { it.command == sc.command && (sc.label == it.he || sc.label == it.en) }?.label ?: sc.label

    private fun shortcuts(): MutableList<Shortcut> {
        val json = prefs().getString("shortcuts", null) ?: return defaults()
        val list = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { o -> Shortcut(o.getString("l"), o.getString("c"), o.optString("g", "").let { g -> OLD_GROUPS[g] ?: g }) }
            }.toMutableList()
        }.getOrElse { return defaults() }
        // Lists saved by older versions: give each item a category and add the new built-in shortcuts once.
        if (prefs().getInt("shortcuts_v", 1) < SHORTCUTS_VERSION) {
            val byCommand = DEFAULT_SHORTCUTS.associate { it.command to it.group }
            list.forEach { if (it.group.isBlank()) it.group = byCommand[it.command] ?: MINE }
            val have = list.map { it.command }.toSet()
            defaults().filter { it.command !in have }.forEach { list += it }
            prefs().edit().putInt("shortcuts_v", SHORTCUTS_VERSION).apply()
            saveShortcutsQuietly(list)
        }
        list.forEach { if (it.group.isBlank()) it.group = MINE }
        return list
    }

    private fun saveShortcutsQuietly(items: List<Shortcut>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("l", it.label).put("c", it.command).put("g", it.group)) }
        prefs().edit().putString("shortcuts", arr.toString()).putInt("shortcuts_v", SHORTCUTS_VERSION).apply()
    }

    private fun saveShortcuts(items: List<Shortcut>) {
        saveShortcutsQuietly(items)
        rebuildChips()
    }

    private fun groups(): List<String> {
        val present = shortcuts().map { it.group }.toSet()
        return (GROUP_ORDER.filter { it in present } + present.filter { it !in GROUP_ORDER }).ifEmpty { listOf(MINE) }
    }

    private fun currentGroup(): String {
        val g = prefs().getString("shortcut_group", GROUP_ORDER.first()) ?: GROUP_ORDER.first()
        return if (g in groups()) g else groups().first()
    }

    private fun shortcutsInGroup() = shortcuts().filter { it.group == currentGroup() }

    private fun rebuildChips() {
        groupChips.removeAllViews()
        groupChips.addView(chip(tr("📋 הדבק", "📋 Paste"), accent = true).apply { setOnClickListener { pasteFromClipboard() } })
        groupChips.addView(chip("+", accent = true).apply {
            contentDescription = tr("קיצור חדש", "New shortcut")
            setOnClickListener { addShortcut(input.text.toString().trim()) }
        })
        groupChips.addView(chip("⚙", accent = true).apply {
            contentDescription = tr("עריכת קיצורים", "Edit shortcuts")
            setOnClickListener { manageShortcuts() }
        })
        val selected = currentGroup()
        for (g in groups()) {
            groupChips.addView(chip(groupLabel(g), accent = false, selected = g == selected).apply {
                setOnClickListener {
                    prefs().edit().putString("shortcut_group", g).apply()
                    rebuildChips()
                    chipsScroll.scrollTo(0, 0)
                }
            })
        }
        chips.removeAllViews()
        for ((i, sc) in shortcutsInGroup().withIndex()) {
            // Keypad phones: the first nine shortcuts of the category also run with the number keys 1–9.
            val label = if (i < 9) "${i + 1}· ${shown(sc)}" else shown(sc)
            chips.addView(chip(label, accent = false).apply {
                setOnClickListener { submit(sc.command) }                     // tap: run now
                setOnLongClickListener { setInput(sc.command); true }         // long-press: edit in the input line
            })
        }
    }

    private fun chip(label: String, accent: Boolean, selected: Boolean = false) = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(if (selected) 0xFFFFFFFF.toInt() else if (accent) ACCENT else TEXT)
        if (selected) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (selected) HEADER else 0xFFFFFFFF.toInt())
            setStroke(dp(1), if (selected) HEADER else if (accent) ACCENT else 0xFFD1D7DB.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
        isClickable = true
        isLongClickable = true
        Compat.focusRing(this, ACCENT, 16f)
    }

    private fun shortcutDialog(title: String, initial: Shortcut?, onSave: (Shortcut) -> Unit, onDelete: (() -> Unit)?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply { hint = tr("שם הקיצור", "Shortcut name"); setText(initial?.label.orEmpty()); setSingleLine(true) }
        val command = EditText(this).apply {
            hint = tr("פקודה, למשל: pm list packages -3", "Command, e.g.: pm list packages -3")
            setText(initial?.command.orEmpty())
            typeface = Typeface.MONOSPACE
            textDirection = View.TEXT_DIRECTION_LTR
        }
        box.addView(name); box.addView(command)
        val b = AlertDialog.Builder(this, DIALOG)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(tr("שמור", "Save")) { _, _ ->
                val c = command.text.toString().trim()
                if (c.isEmpty()) return@setPositiveButton
                val l = name.text.toString().trim().ifEmpty { c.take(18) }
                onSave(Shortcut(l, c, initial?.group?.takeIf { it.isNotBlank() } ?: MINE))
            }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
        if (onDelete != null) b.setNeutralButton(tr("מחק", "Delete")) { _, _ -> onDelete() }
        b.show()
    }

    private fun addShortcut(prefill: String) {
        shortcutDialog(tr("קיצור חדש", "New shortcut"), if (prefill.isNotEmpty()) Shortcut("", prefill) else null, { s ->
            prefs().edit().putString("shortcut_group", s.group).apply()  // show the category it went to
            saveShortcuts(shortcuts().apply { add(0, s) })
            Toast.makeText(this, tr("הקיצור \"${s.label}\" נוסף.", "Shortcut \"${s.label}\" added."), Toast.LENGTH_SHORT).show()
        }, null)
    }

    private fun manageShortcuts() {
        val items = shortcuts()
        val labels = items.map { "[${groupLabel(it.group)}] ${shown(it)}   ·   ${it.command}" }.toMutableList()
        labels += tr("＋ הוסף קיצור", "＋ Add shortcut")
        labels += tr("📌 קיצורים בווידג'ט של מסך הבית", "📌 Home-screen widget shortcuts")
        labels += tr("↺ שחזר קיצורי ברירת מחדל", "↺ Restore default shortcuts")
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("עריכת קיצורים", "Edit shortcuts"))
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    items.size -> addShortcut("")
                    items.size + 1 -> pickWidgetShortcuts()
                    items.size + 2 -> {
                        prefs().edit().remove("shortcuts").putInt("shortcuts_v", SHORTCUTS_VERSION).apply()
                        rebuildChips()
                    }
                    else -> shortcutDialog(tr("עריכת קיצור", "Edit shortcut"), items[which], { s ->
                        items[which] = s
                        saveShortcuts(items)
                    }, {
                        items.removeAt(which)
                        saveShortcuts(items)
                    })
                }
            }
            .setNegativeButton(tr("סגור", "Close"), null)
            .show()
    }

    /** Choose up to four shortcuts for the home-screen widget. */
    private fun pickWidgetShortcuts() {
        val items = shortcuts()
        val pinned = ShortcutWidget.pins(this).map { it.command }.toSet()
        val checked = BooleanArray(items.size) { items[it].command in pinned }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("📌 קיצורים בווידג'ט (עד ${ShortcutWidget.MAX})", "📌 Widget shortcuts (up to ${ShortcutWidget.MAX})"))
            .setMultiChoiceItems(items.map { shown(it) }.toTypedArray(), checked) { d, which, on ->
                if (on && checked.count { it } > ShortcutWidget.MAX) {
                    checked[which] = false
                    (d as AlertDialog).listView.setItemChecked(which, false)
                    Toast.makeText(this, tr("אפשר לבחור עד ${ShortcutWidget.MAX} קיצורים.", "You can choose up to ${ShortcutWidget.MAX} shortcuts."), Toast.LENGTH_SHORT).show()
                } else checked[which] = on
            }
            .setPositiveButton(tr("שמור", "Save")) { _, _ ->
                ShortcutWidget.savePins(this, items.filterIndexed { i, _ -> checked[i] }.map { ShortcutWidget.Pin(shown(it), it.command) })
                Toast.makeText(this, tr("הווידג'ט עודכן. להוספה: לחיצה ארוכה על מסך הבית ← ווידג'טים ← הבוס.", "Widget updated. To add it: long-press the home screen → Widgets → The Boss."), Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
            .show()
    }

    // endregion

    // region menus, history, persistence

    private fun menu() {
        val detect = prefs().getBoolean("clipboard_detect", true)
        val items = arrayOf(
            tr("הדבק מהלוח", "Paste from clipboard"),
            tr("עריכת קיצורים", "Edit shortcuts"),
            (if (detect) "✓ " else "") + tr("שאל כשבלוח יש פקודה", "Ask when the clipboard has a command"),
            tr("העתק את כל השיחה", "Copy the whole chat"),
            tr("נקה שיחה", "Clear chat"),
            tr("קיצורי מקשים", "Keyboard shortcuts"),
            tr("סגור", "Close"),
        )
        AlertDialog.Builder(this, DIALOG)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pasteFromClipboard()
                    1 -> manageShortcuts()
                    2 -> prefs().edit().putBoolean("clipboard_detect", !detect).apply()
                    3 -> copyChat()
                    4 -> confirmClearChat()
                    5 -> keyHelp()
                    6 -> finish()
                }
            }
            .show()
    }

    /** Copies the whole conversation: commands as "$ cmd", replies as plain text. */
    private fun copyChat() {
        if (messages.isEmpty()) {
            Toast.makeText(this, tr("השיחה ריקה.", "The chat is empty."), Toast.LENGTH_SHORT).show()
            return
        }
        copy(messages.joinToString("\n\n") { (if (it.outgoing) "$ " else "") + it.text })
    }

    private fun confirmClearChat() {
        if (messages.isEmpty()) return
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("לנקות את השיחה?", "Clear the chat?"))
            .setMessage(tr("כל ההודעות בצ'אט יימחקו. הקיצורים וההיסטוריה של הפקודות יישארו.", "All messages in the chat will be deleted. Shortcuts and command history stay."))
            .setPositiveButton(tr("נקה", "Clear")) { _, _ -> clearChat() }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
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
        Toast.makeText(this, tr("הועתק.", "Copied."), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, tr("אין עדיין היסטוריה.", "No history yet."), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("פקודות אחרונות", "Recent commands"))
            .setItems(items.toTypedArray()) { _, which -> setInput(items[which]) }
            .setNegativeButton(tr("סגור", "Close"), null)
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
        private val DIALOG = android.R.style.Theme_Material_Light_Dialog_Alert

        // WhatsApp light palette: teal header, beige chat, light-green own messages, white replies.
        private val CHAT_BG = 0xFFEFEAE2.toInt()
        private val HEADER = 0xFF008069.toInt()
        private val HEADER_TEXT = 0xFFFFFFFF.toInt()
        private val HEADER_SUB = 0xFFD9F1EA.toInt()
        private val INPUT_BG = 0xFFFFFFFF.toInt()
        private val OUT_BG = 0xFFD9FDD3.toInt()
        private val IN_BG = 0xFFFFFFFF.toInt()
        private val ACCENT = 0xFF00A884.toInt()
        private val TEXT = 0xFF111B21.toInt()
        private val MUTED = 0xFF667781.toInt()
        private val TICK_BLUE = 0xFF53BDEB.toInt()
        private val ERROR = 0xFFD93025.toInt()
        private val STOP = 0xFFE5534B.toInt()

        private val DANGEROUS = listOf("rm -r", "rm -f", "reboot", "wipe", "pm uninstall", "pm clear", "format", "dd ", "mkfs", "svc power")
        private const val MINE = "mine"
        private const val SHORTCUTS_VERSION = 4
        private val GROUP_ORDER = listOf("toggles", "screen", "info", "maint", "tools", MINE)
        private fun groupLabel(id: String) = when (id) {
            "toggles" -> tr("מתגים", "Toggles")
            "screen" -> tr("מסך ומקשים", "Screen & keys")
            "info" -> tr("מידע", "Info")
            "maint" -> tr("תחזוקה", "Maintenance")
            "tools" -> tr("כלים", "Tools")
            MINE -> tr("שלי", "Mine")
            else -> id
        }
        /** Category names saved by versions before 1.4 (Hebrew) → stable ids. */
        private val OLD_GROUPS = mapOf("מתגים" to "toggles", "מסך ומקשים" to "screen", "מידע" to "info", "תחזוקה" to "maint", "כלים" to "tools", "שלי" to MINE) // i18n-ok

        /** Built-in shortcut: category id, Hebrew label, English label, command. */
        private class Def(val group: String, val he: String, val en: String, val command: String) {
            val label get() = tr(he, en)
        }

        /** Commands try the modern "cmd" form first and fall back to "svc". */
        private val DEFAULT_SHORTCUTS = listOf(
            // Toggles
            Def("toggles", "📶 Wi‑Fi הפעל/כבה", "📶 Wi‑Fi toggle", ShortcutWidget.WIFI_TOGGLE),
            Def("toggles", "🔵 בלוטות׳ הפעל/כבה", "🔵 Bluetooth toggle", ShortcutWidget.BT_TOGGLE),
            Def("toggles", "📶 Wi‑Fi פועל", "📶 Wi‑Fi on", "cmd wifi set-wifi-enabled enabled 2>/dev/null || svc wifi enable"),
            Def("toggles", "📶 Wi‑Fi כבוי", "📶 Wi‑Fi off", "cmd wifi set-wifi-enabled disabled 2>/dev/null || svc wifi disable"),
            Def("toggles", "🔵 בלוטות׳ פועל", "🔵 Bluetooth on", "cmd bluetooth_manager enable 2>/dev/null || svc bluetooth enable"),
            Def("toggles", "🔵 בלוטות׳ כבוי", "🔵 Bluetooth off", "cmd bluetooth_manager disable 2>/dev/null || svc bluetooth disable"),
            Def("toggles", "📱 נתונים פועל", "📱 Mobile data on", "svc data enable"),
            Def("toggles", "📱 נתונים כבוי", "📱 Mobile data off", "svc data disable"),
            Def("toggles", "✈ מצב טיסה פועל", "✈ Airplane mode on", "cmd connectivity airplane-mode enable"),
            Def("toggles", "✈ מצב טיסה כבוי", "✈ Airplane mode off", "cmd connectivity airplane-mode disable"),
            Def("toggles", "📍 מיקום פועל", "📍 Location on", "cmd location set-location-enabled true"),
            Def("toggles", "📍 מיקום כבוי", "📍 Location off", "cmd location set-location-enabled false"),
            Def("toggles", "🔕 נא לא להפריע", "🔕 Do not disturb", "cmd notification set_dnd priority"),
            Def("toggles", "🔔 בטל נא לא להפריע", "🔔 Do not disturb off", "cmd notification set_dnd off"),
            Def("toggles", "🔋 חיסכון בסוללה פועל", "🔋 Battery saver on", "cmd power set-mode 1"),
            Def("toggles", "🔋 חיסכון בסוללה כבוי", "🔋 Battery saver off", "cmd power set-mode 0"),
            Def("toggles", "🔄 סיבוב אוטומטי פועל", "🔄 Auto-rotate on", "settings put system accelerometer_rotation 1"),
            Def("toggles", "🔄 סיבוב אוטומטי כבוי", "🔄 Auto-rotate off", "settings put system accelerometer_rotation 0"),
            Def("toggles", "NFC פועל", "NFC on", "svc nfc enable"),
            Def("toggles", "NFC כבוי", "NFC off", "svc nfc disable"),
            // Screen & keys
            Def("screen", "📳 רטט", "📳 Vibrate", "cmd vibrator_manager synced oneshot 600 2>/dev/null || cmd vibrator vibrate 600"),
            Def("screen", "☀ בהירות מלאה", "☀ Full brightness", "settings put system screen_brightness_mode 0; settings put system screen_brightness 255"),
            Def("screen", "🌗 בהירות אוטומטית", "🌗 Auto brightness", "settings put system screen_brightness_mode 1"),
            Def("screen", "🔊 עוצמה +", "🔊 Volume +", "input keyevent KEYCODE_VOLUME_UP"),
            Def("screen", "🔉 עוצמה −", "🔉 Volume −", "input keyevent KEYCODE_VOLUME_DOWN"),
            Def("screen", "🔇 השתק", "🔇 Mute", "input keyevent KEYCODE_VOLUME_MUTE"),
            Def("screen", "⏯ נגן/השהה", "⏯ Play/Pause", "input keyevent KEYCODE_MEDIA_PLAY_PAUSE"),
            Def("screen", "💡 הער מסך", "💡 Wake screen", "input keyevent KEYCODE_WAKEUP"),
            Def("screen", "🔒 נעל מסך", "🔒 Lock screen", "input keyevent KEYCODE_SLEEP"),
            Def("screen", "🏠 בית", "🏠 Home", "input keyevent KEYCODE_HOME"),
            Def("screen", "↩ חזור", "↩ Back", "input keyevent KEYCODE_BACK"),
            Def("screen", "🗂 אפליקציות אחרונות", "🗂 Recent apps", "input keyevent KEYCODE_APP_SWITCH"),
            Def("screen", "⏱ מסך נכבה אחרי 10 דק׳", "⏱ Screen off after 10 min", "settings put system screen_off_timeout 600000"),
            Def("screen", "📸 צילום מסך", "📸 Screenshot", "screencap -p /sdcard/Download/screenshot.png && echo saved to Download"),
            // Info
            Def("info", "גרסה", "Version", "getprop ro.build.version.release"),
            Def("info", "דגם", "Model", "getprop ro.product.model"),
            Def("info", "סוללה", "Battery", "dumpsys battery"),
            Def("info", "מסך", "Screen", "wm size; wm density"),
            Def("info", "אחסון", "Storage", "df -h /data /sdcard"),
            Def("info", "זיכרון", "Memory", "cat /proc/meminfo | head -5"),
            Def("info", "רשת", "Network", "ip addr show wlan0"),
            Def("info", "אפליקציה פתוחה", "Foreground app", "dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'"),
            Def("info", "זמן פעילות", "Uptime", "uptime"),
            Def("info", "תהליכים", "Processes", "top -b -n 1 | head -25"),
            Def("info", "לוג", "Log", "logcat -d -t 100"),
            // Tools
            Def("tools", "אפליקציות שהותקנו", "Installed apps", "pm list packages -3"),
            Def("tools", "⚙ פתח הגדרות", "⚙ Open settings", "am start -a android.settings.SETTINGS"),
            Def("tools", "🧹 סגור אפליקציות ברקע", "🧹 Close background apps", "am kill-all"),
            Def("tools", "♻ הפעלה מחדש", "♻ Restart", "reboot"),
            // Maintenance: cache cleaning and phone health (output from the device, in English)
            Def("maint", "🧹 נקה מטמון לכל האפליקציות", "🧹 Clear cache of all apps",
                "b=$(df /data | awk 'NR==2{print $4}'); pm trim-caches 999G; a=$(df /data | awk 'NR==2{print $4}'); echo \"Cache cleared. Freed: $(( (a-b)/1024 )) MB\""),
            Def("maint", "🔋 בריאות סוללה", "🔋 Battery health",
                "dumpsys battery | awk -F': ' '/ level:/{print \"Level: \"$2\"%\"} /temperature:/{printf \"Temperature: %.1f C\\n\",$2/10} " +
                    "/health:/{h=$2; print \"Health: \" (h==2?\"Good\":h==3?\"Overheat\":h==4?\"Dead\":h==5?\"Over voltage\":h==7?\"Cold\":\"Unknown\")} " +
                    "/voltage:/{print \"Voltage: \"$2\" mV\"} /status:/{st=$2; print \"Status: \" (st==2?\"Charging\":st==3?\"Discharging\":st==5?\"Full\":\"Not charging\")}'; " +
                    "f=/sys/class/power_supply/battery; [ -r \$f/cycle_count ] && echo \"Charge cycles: $(cat \$f/cycle_count)\"; " +
                    "if [ -r \$f/charge_full ] && [ -r \$f/charge_full_design ]; then echo \"Capacity left: $(( $(cat \$f/charge_full) * 100 / $(cat \$f/charge_full_design) ))% of original\"; fi"),
            Def("maint", "🌡 טמפרטורות", "🌡 Temperatures",
                "for z in /sys/class/thermal/thermal_zone*; do t=$(cat \$z/temp 2>/dev/null); n=$(cat \$z/type 2>/dev/null); [ -n \"\$t\" ] && [ \"\$t\" -gt 0 ] && echo \"\$n: $((t/1000)) C\"; done | head -15"),
            Def("maint", "💾 ניצול אחסון", "💾 Storage usage",
                "df -h /data | tail -1 | awk '{print \"Used \"$3\" of \"$2\" (\"$5\"), free \"$4}'; echo; " +
                    "dumpsys diskstats | grep -E '^(App Size|App Data Size|App Cache Size|Photos Size|Videos Size|Audio Size|Downloads Size|System Size|Other Size):' | " +
                    "awk -F': ' '{printf \"%-16s %.2f GB\\n\", $1, $2/1073741824}'"),
            Def("maint", "📂 התיקיות הגדולות", "📂 Largest folders",
                "du -sh /sdcard/* /sdcard/Android/media/* 2>/dev/null | sort -rh | head -12"),
        )
    }
}
