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
import com.londkirs.apkbridge.TerminalShortcuts.GROUP_ORDER
import com.londkirs.apkbridge.TerminalShortcuts.MINE
import com.londkirs.apkbridge.TerminalShortcuts.Shortcut
import com.londkirs.apkbridge.TerminalShortcuts.groupLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ADB terminal as a WhatsApp-style chat (light, like the regular WhatsApp): every command is an outgoing message,
 * its output is the reply. Shortcuts run on tap and go to the input line on long-press;
 * the user can add, edit and delete shortcuts. Commands run as the ADB "shell" user via [Bridge].
 */
class TerminalActivity : BossActivity() {
    private class Message(
        val outgoing: Boolean,
        val ts: Long,             // when it was sent (ms); 0 for messages saved before 1.8
        var text: CharSequence,
        var status: String = "",  // outgoing: "run" | "ok" | "err:<code>" | "stop"
        val legacyTime: String = "",
    ) {
        val time: String get() = if (ts > 0) SimpleDateFormat("HH:mm", Locale.US).format(Date(ts)) else legacyTime
    }


    private val messages = mutableListOf<Message>()
    private val bubbleTexts = HashMap<Message, TextView>()
    private val bubbleStatus = HashMap<Message, TextView>()
    private val rowViews = HashMap<Message, View>()
    private val dayViews = HashMap<Message, View>()   // the date chip shown above the first message of a day
    private var chatId = 0L
    private lateinit var titleView: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var drawerList: LinearLayout
    private lateinit var scrim: View

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
        chatId = TerminalChats.currentId(this)
        loadChat()
        if (messages.isEmpty()) welcome()
        rebuildChips()
        renderTitle()
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
            setPadding(0, dp(8), dp(6), dp(8))
        }
        // Back, in the corner (right in Hebrew, left in English), pointing the way back.
        header.addView(iconButton(tr("→", "←")) { finish() }.apply {
            contentDescription = tr("חזרה", "Back")
            textSize = 22f
            setPadding(dp(12), dp(6), dp(6), dp(6))
        })
        header.addView(TextView(this).apply {
            text = ">_"
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(HEADER)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) }
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(2); marginEnd = dp(10) })
        // Tapping the chat's name opens the chats (new chat, earlier chats).
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = tr("שיחות", "Chats")
            setOnClickListener { openDrawer() }
            Compat.focusRing(this, HEADER_TEXT, 8f)
        }
        titleView = TextView(this).apply {
            text = "ADB Terminal"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(HEADER_TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titles.addView(titleView)
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

        // Side panel with the chats (opens with ☰), over a dimmed screen.
        val frame = android.widget.FrameLayout(this)
        frame.addView(root, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        scrim = View(this).apply {
            setBackgroundColor(0x66000000)
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        frame.addView(scrim, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        drawer = buildDrawer()
        val width = minOf(dp(320), (resources.displayMetrics.widthPixels * 0.82f).toInt())
        frame.addView(drawer, android.widget.FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        frame.layoutDirection = Lang.direction
        Compat.applySystemBarInsets(this, frame, includeIme = true)
        setContentView(frame)
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

    private fun now() = System.currentTimeMillis()

    private fun welcome() {
        addMessage(Message(false, now(), tr("שלום! כאן אפשר להריץ כל פקודת ADB.\n\n", "Hi! Here you can run any ADB command.\n\n") +
            tr("• הקלד פקודה ולחץ ➤ (אפשר גם \"adb shell …\").\n", "• Type a command and tap ➤ (\"adb shell …\" works too).\n") +
            tr("• קיצור: לחיצה מריצה מיד, לחיצה ארוכה מעבירה לעריכה.\n", "• Shortcut: tap runs it right away, long-press moves it to editing.\n") +
            tr("• + מוסיף קיצור משלך. עריכה ומחיקה: בתפריט ⋮ ← עריכת קיצורים.\n", "• + adds your own shortcut. Edit and delete: ⋮ menu → Edit shortcuts.\n") +
            tr("• לחיצה על הודעה מעתיקה אותה. לחיצה ארוכה: הרצה שוב, עריכה, שיתוף, שמירה כקובץ ועוד.\n", "• Tap a message to copy it. Long-press: run again, edit, share, save as a file and more.\n") +
            tr("• לחיצה על שם השיחה למעלה: כל השיחות, ושיחה חדשה.\n", "• Tap the chat name at the top: all chats, and a new chat.\n") +
            tr("• לחיצה על קטגוריה פותחת את הקיצורים שלה, ולחיצה נוספת מקפלת אותם.\n", "• Tap a category to open its shortcuts; tap it again to fold them away.\n") +
            tr("• boss torch — פנס. בקטגוריות למטה: הקלטת מסך, נקודה חמה, התראה, תצוגה ועוד.\n\n", "• boss torch — flashlight. In the categories below: screen recording, hotspot, notification, display and more.\n\n") +
            tr("הפקודות רצות כמשתמש shell (לא root).", "Commands run as the shell user (not root).")))
    }

    private fun addMessage(m: Message) {
        val previous = messages.lastOrNull { it.ts > 0 }
        if (m.ts > 0 && (previous == null || !sameDay(previous.ts, m.ts))) {
            val chip = dayChip(m.ts)
            dayViews[m] = chip
            list.addView(chip)
        }
        messages += m
        val row = bubble(m)
        rowViews[m] = row
        list.addView(row)
        while (messages.size > MAX_MESSAGES) removeMessageViews(messages.removeAt(0))
        scrollDown()
    }

    private fun removeMessageViews(m: Message) {
        bubbleTexts.remove(m); bubbleStatus.remove(m)
        rowViews.remove(m)?.let { list.removeView(it) }
        dayViews.remove(m)?.let { list.removeView(it) }
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        return f.format(Date(a)) == f.format(Date(b))
    }

    @Suppress("DEPRECATION")
    private fun dateLocale() = if (Lang.hebrew) Locale("iw") else Locale.ENGLISH

    /** "Today", "Yesterday", the weekday for the last week, or the date. */
    private fun dayLabel(ts: Long): String {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        return when {
            sameDay(ts, now) -> tr("היום", "Today")
            sameDay(ts, now - day) -> tr("אתמול", "Yesterday")
            now - ts < 6 * day -> SimpleDateFormat("EEEE", dateLocale()).format(Date(ts))
            else -> SimpleDateFormat("d MMMM yyyy", dateLocale()).format(Date(ts))
        }
    }

    /** The date chip between days, as in WhatsApp. */
    private fun dayChip(ts: Long) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(6), 0, dp(6))
        addView(TextView(this@TerminalActivity).apply {
            text = dayLabel(ts)
            textSize = 12f
            setTextColor(0xFF54656F.toInt())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFFFFFFFF.toInt()) }
            elevation = dp(1).toFloat()
        })
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
        // Tap copies the message; on keypad phones (no touch) OK opens the message menu.
        box.setOnClickListener { if (box.isInTouchMode) copy(m.text.toString()) else messageMenu(m) }
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

    /** Long-press menu of a message (title: its date and time). */
    private fun messageMenu(m: Message) {
        val raw = m.text.toString()
        val i = messages.indexOf(m)
        // The command and its reply, as a pair.
        val command = if (m.outgoing) m else messages.getOrNull(i - 1)?.takeIf { it.outgoing }
        val reply = if (m.outgoing) messages.getOrNull(i + 1)?.takeIf { !it.outgoing } else m
        val pair = if (command != null && reply != null) "$ ${command.text}\n${reply.text}" else null
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += tr("📋 העתק", "📋 Copy") to { copy(raw) }
        if (pair != null) actions += tr("📋 העתק פקודה ותוצאה", "📋 Copy command and output") to { copy(pair) }
        if (m.outgoing) {
            actions += tr("▶ הרץ שוב", "▶ Run again") to { submit(raw) }
            actions += tr("✏ ערוך", "✏ Edit") to { setInput(raw) }
            actions += tr("⭐ שמור כקיצור", "⭐ Save as shortcut") to { addShortcut(raw) }
        } else {
            actions += tr("🔍 בחר חלק מהטקסט", "🔍 Select part of the text") to { selectText(m) }
        }
        actions += tr("📤 שתף", "📤 Share") to { share(pair ?: raw) }
        actions += tr("💾 שמור כקובץ", "💾 Save as file") to { saveAsFile(pair ?: raw) }
        actions += tr("🗑 מחק הודעה", "🗑 Delete message") to { deleteMessage(m) }
        val title = if (m.ts > 0) SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", dateLocale()).format(Date(m.ts)) else m.time
        AlertDialog.Builder(this, DIALOG)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /** The message in a dialog where any part of it can be selected and copied. */
    private fun selectText(m: Message) {
        val view = TextView(this).apply {
            text = m.text
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(TEXT)
            setTextIsSelectable(true)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            textDirection = View.TEXT_DIRECTION_LTR
        }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("סמן את החלק להעתקה", "Select the part to copy"))
            .setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton(tr("סגור", "Close"), null)
            .show()
    }

    private fun share(text: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
        runCatching { startActivity(android.content.Intent.createChooser(send, tr("שיתוף", "Share"))) }
    }

    private var pendingFileText: String? = null

    /** Saves the text to a .txt file the user picks (Android's "save as" screen). */
    private fun saveAsFile(text: String) {
        pendingFileText = text
        val name = "TheBoss-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(android.content.Intent.EXTRA_TITLE, name)
        runCatching { startActivityForResult(intent, REQUEST_SAVE_TEXT) }
            .onFailure { Toast.makeText(this, tr("לא נמצא מסך שמירת קבצים.", "No file-saving screen found."), Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SAVE_TEXT) return
        val uri = data?.data
        val text = pendingFileText
        pendingFileText = null
        if (resultCode != RESULT_OK || uri == null || text == null) return
        val ok = runCatching { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no stream") }.isSuccess
        Toast.makeText(this, if (ok) tr("✔ נשמר.", "✔ Saved.") else tr("השמירה נכשלה.", "Saving failed."), Toast.LENGTH_SHORT).show()
    }

    private fun deleteMessage(m: Message) {
        if (m === current || m === currentReply) return
        messages.remove(m)
        removeMessageViews(m)
        saveChat()
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
        if (raw.equals("boss", true) || raw.startsWith("boss ", true)) { runLocal(raw); return }
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

    /**
     * Commands the app runs itself, where the shell can't: "boss torch [on|off]" (flashlight),
     * "boss help".
     */
    private fun runLocal(raw: String) {
        val args = raw.trim().split(Regex("\\s+")).drop(1)
        val msg = Message(true, now(), raw, "run")
        addMessage(msg)
        saveHistory(raw)
        input.setText("")
        fun reply(text: String, ok: Boolean) {
            msg.status = if (ok) "ok" else "err:1"
            renderMeta(msg)
            addMessage(Message(false, now(), text))
            saveChat()
        }
        when (args.firstOrNull()?.lowercase()) {
            "torch", "flashlight", "פנס" -> {  // i18n-ok: a word the user may type, not a text the app shows
                val want = when (args.getOrNull(1)?.lowercase()) { "on" -> true; "off" -> false; else -> null }
                Torch.set(this, want) { on ->
                    when (on) {
                        true -> reply(tr("🔦 הפנס פועל", "🔦 Flashlight on"), true)
                        false -> reply(tr("🔦 הפנס כבוי", "🔦 Flashlight off"), true)
                        null -> reply(tr("לא ניתן להפעיל את הפנס (אין פנס, או שהמצלמה בשימוש).", "Can't switch the flashlight (no flash, or the camera is in use)."), false)
                    }
                    ShortcutWidget.refresh(this)
                }
            }
            else -> reply(tr("פקודות של הבוס:\n  boss torch       פנס — הפעל/כבה\n  boss torch on    פנס — הפעל\n  boss torch off   פנס — כבה",
                "The Boss commands:\n  boss torch       flashlight on/off\n  boss torch on    flashlight on\n  boss torch off   flashlight off"), args.firstOrNull()?.lowercase() == "help")
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
                    addMessage(Message(false, now(), if (code == null) tr("הפקודה לא הושלמה. הסיבה מופיעה ביומן של הבוס.", "The command didn't complete. The reason is in The Boss's log.") else if (code == 0) tr("✔ בוצע (אין פלט)", "✔ Done (no output)") else tr("(אין פלט, קוד יציאה $code)", "(no output, exit code $code)")))
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
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && drawerOpen()) { closeDrawer(); return true }
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

    private fun shown(sc: Shortcut) = TerminalShortcuts.shown(sc)
    /** Parsed once and kept until the list changes (the chip bar asks for it several times per redraw). */
    private var shortcutCache: MutableList<Shortcut>? = null
    private fun shortcuts(): MutableList<Shortcut> = (shortcutCache ?: TerminalShortcuts.load(this).also { shortcutCache = it }).toMutableList()

    private fun saveShortcuts(items: List<Shortcut>) {
        TerminalShortcuts.save(this, items)
        shortcutCache = null
        rebuildChips()
    }

    private fun groups() = TerminalShortcuts.groups(shortcuts())

    private fun currentGroup(): String {
        val g = prefs().getString("shortcut_group", GROUP_ORDER.first()) ?: GROUP_ORDER.first()
        return if (g in groups()) g else groups().first()
    }

    private fun shortcutsInGroup(): List<Shortcut> { val g = currentGroup(); return shortcuts().filter { it.group == g } }

    private fun rebuildChips() {
        groupChips.removeAllViews()
        // Paste is in the input line and editing shortcuts in the ⋮ menu, so here only "+" and the categories.
        groupChips.addView(chip("+", accent = true).apply {
            contentDescription = tr("קיצור חדש", "New shortcut")
            setOnClickListener { addShortcut(input.text.toString().trim()) }
        })
        val selected = currentGroup()
        val open = prefs().getBoolean("shortcuts_open", true)
        // Tap a category: its shortcuts open below. Tap the open one again: they fold away (more room for the chat).
        for (g in groups()) {
            val isOpen = open && g == selected
            groupChips.addView(chip(groupLabel(g) + if (isOpen) " ▴" else "", accent = false, selected = isOpen).apply {
                setOnClickListener {
                    if (isOpen) prefs().edit().putBoolean("shortcuts_open", false).apply()
                    else prefs().edit().putString("shortcut_group", g).putBoolean("shortcuts_open", true).apply()
                    rebuildChips()
                    chipsScroll.scrollTo(0, 0)
                }
            })
        }
        chipsScroll.visibility = if (open) View.VISIBLE else View.GONE
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
        labels += tr("📌 ווידג'טים של מסך הבית", "📌 Home-screen widgets")
        labels += tr("↺ שחזר קיצורי ברירת מחדל", "↺ Restore default shortcuts")
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("עריכת קיצורים", "Edit shortcuts"))
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    items.size -> addShortcut("")
                    items.size + 1 -> pickWidgetShortcuts()
                    items.size + 2 -> {
                        TerminalShortcuts.reset(this)
                        shortcutCache = null
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

    /** Home-screen widgets: open the settings of a placed widget, or explain how to add one. */
    private fun pickWidgetShortcuts() {
        val placed = ShortcutWidget.placed(this)
        if (placed.isEmpty()) {
            AlertDialog.Builder(this, DIALOG)
                .setTitle(tr("📌 ווידג'טים למסך הבית", "📌 Home-screen widgets"))
                .setMessage(tr("יש שני ווידג'טים: שורת קיצורים (עד 4) וכפתור אחד.\n\nלהוספה: לחיצה ארוכה על מסך הבית ← ווידג'טים ← הבוס. מיד אחרי ההוספה נפתח מסך לבחירת הקיצורים.\n\nאפשר לבחור כל קיצור מהטרמינל, וגם פעולות של האפליקציה (התקנת APK, גיבוי, אפליקציות ועוד).",
                    "There are two widgets: a row of shortcuts (up to 4) and a single button.\n\nTo add one: long-press the home screen → Widgets → The Boss. Right after adding, a screen opens to choose the shortcuts.\n\nYou can choose any terminal shortcut, and app actions too (install APK, backup, apps and more)."))
                .setPositiveButton(tr("הבנתי", "Got it"), null)
                .show()
            return
        }
        if (placed.size == 1) { WidgetConfigActivity.open(this, placed[0].first); return }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("איזה ווידג'ט לערוך?", "Which widget to edit?"))
            .setItems(placed.map { it.second }.toTypedArray()) { _, i -> WidgetConfigActivity.open(this, placed[i].first) }
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
        messages.clear(); bubbleTexts.clear(); bubbleStatus.clear(); rowViews.clear(); dayViews.clear()
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
        if (chatId == 0L) return
        val arr = JSONArray()
        messages.takeLast(MAX_SAVED).forEach {
            arr.put(JSONObject().put("o", it.outgoing).put("ts", it.ts).put("t", it.time)
                .put("x", it.text.toString().takeLast(MAX_REPLY)).put("s", if (it.status == "run") "stop" else it.status))
        }
        TerminalChats.save(this, chatId, arr, messages.firstOrNull { it.outgoing }?.text?.toString())
        renderTitle()
    }

    private fun loadChat() {
        val arr = TerminalChats.messages(this, chatId) ?: return
        runCatching {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                addMessage(Message(o.getBoolean("o"), o.optLong("ts", 0), o.getString("x"), o.optString("s"), o.optString("t")))
            }
        }
    }

    // endregion

    // region chats (side panel)

    private fun renderTitle() {
        if (!::titleView.isInitialized) return
        val chat = TerminalChats.list(this).firstOrNull { it.id == chatId }
        titleView.text = (chat?.title?.takeIf { it.isNotBlank() } ?: "ADB Terminal") + "  ▾"
    }

    private fun buildDrawer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFFFFFFFF.toInt())
        elevation = dp(12).toFloat()
        visibility = View.GONE
        isClickable = true  // touches on the panel don't reach the chat behind it
        addView(TextView(this@TerminalActivity).apply {
            text = tr("💬 שיחות", "💬 Chats")
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(HEADER_TEXT)
            setBackgroundColor(HEADER)
            setPadding(dp(16), dp(18), dp(16), dp(14))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(TextView(this@TerminalActivity).apply {
            text = tr("＋  שיחה חדשה", "＋  New chat")
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ACCENT)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { newChat() }
            Compat.focusRing(this, ACCENT, 8f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(View(this@TerminalActivity).apply { setBackgroundColor(0xFFE9EDEF.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        drawerList = LinearLayout(this@TerminalActivity).apply { orientation = LinearLayout.VERTICAL }
        addView(ScrollView(this@TerminalActivity).apply { addView(drawerList) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(TextView(this@TerminalActivity).apply {
            text = tr("לחיצה ארוכה על שיחה: שינוי שם או מחיקה", "Long-press a chat: rename or delete")
            textSize = 12f
            setTextColor(MUTED)
            setPadding(dp(16), dp(8), dp(16), dp(12))
        })
    }

    private fun renderDrawer() {
        drawerList.removeAllViews()
        val chats = TerminalChats.list(this)
        val dateFormat = SimpleDateFormat("d.M.yy · HH:mm", Locale.US)
        for (chat in chats) {
            val selected = chat.id == chatId
            drawerList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                if (selected) setBackgroundColor(0xFFE7F8F2.toInt())
                isClickable = true
                isFocusable = true
                addView(TextView(this@TerminalActivity).apply {
                    text = TerminalChats.titleOf(chat)
                    textSize = 15f
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(TEXT)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                })
                addView(TextView(this@TerminalActivity).apply {
                    text = dateFormat.format(Date(chat.updated)) + "  ·  " + tr("${chat.count} הודעות", "${chat.count} messages")
                    textSize = 12f
                    setTextColor(MUTED)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                })
                setOnClickListener { switchChat(chat.id) }
                setOnLongClickListener { chatMenu(chat); true }
                Compat.focusRing(this, ACCENT, 6f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun drawerOpen() = ::drawer.isInitialized && drawer.visibility == View.VISIBLE

    private fun openDrawer() {
        saveChat()
        renderDrawer()
        val w = drawer.layoutParams.width.toFloat()
        drawer.translationX = if (Lang.hebrew) w else -w
        drawer.visibility = View.VISIBLE
        scrim.visibility = View.VISIBLE
        drawer.animate().translationX(0f).setDuration(180).start()
        drawerList.getChildAt(0)?.requestFocus()
    }

    private fun closeDrawer() {
        if (!drawerOpen()) return
        val w = drawer.width.toFloat()
        drawer.animate().translationX(if (Lang.hebrew) w else -w).setDuration(160).withEndAction { drawer.visibility = View.GONE }.start()
        scrim.visibility = View.GONE
    }

    private fun showChat(id: Long) {
        chatId = id
        TerminalChats.setCurrent(this, id)
        messages.clear(); bubbleTexts.clear(); bubbleStatus.clear(); rowViews.clear(); dayViews.clear()
        list.removeAllViews()
        current = null; currentReply = null
        loadChat()
        renderTitle()
    }

    private fun switchChat(id: Long) {
        if (Bridge.state.busy && current != null) {
            Toast.makeText(this, tr("המתן לסיום הפקודה שרצה.", "Wait for the running command to finish."), Toast.LENGTH_SHORT).show()
            return
        }
        if (id != chatId) { saveChat(); showChat(id) }
        closeDrawer()
    }

    private fun newChat() {
        if (Bridge.state.busy && current != null) {
            Toast.makeText(this, tr("המתן לסיום הפקודה שרצה.", "Wait for the running command to finish."), Toast.LENGTH_SHORT).show()
            return
        }
        saveChat()
        showChat(TerminalChats.create(this).id)
        closeDrawer()
        input.requestFocus()
    }

    private fun chatMenu(chat: TerminalChats.Chat) {
        AlertDialog.Builder(this, DIALOG)
            .setTitle(TerminalChats.titleOf(chat))
            .setItems(arrayOf(tr("✏ שינוי שם", "✏ Rename"), tr("🗑 מחק שיחה", "🗑 Delete chat"))) { _, which ->
                if (which == 0) renameChat(chat) else confirmDeleteChat(chat)
            }
            .show()
    }

    private fun renameChat(chat: TerminalChats.Chat) {
        val field = EditText(this).apply { setText(chat.title); setSingleLine(true); setSelection(text.length) }
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("שם השיחה", "Chat name"))
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) })
            .setPositiveButton(tr("שמור", "Save")) { _, _ ->
                TerminalChats.rename(this, chat.id, field.text.toString().trim())
                renderDrawer(); renderTitle()
            }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
            .show()
    }

    private fun confirmDeleteChat(chat: TerminalChats.Chat) {
        AlertDialog.Builder(this, DIALOG)
            .setTitle(tr("למחוק את השיחה?", "Delete the chat?"))
            .setMessage(TerminalChats.titleOf(chat))
            .setPositiveButton(tr("מחק", "Delete")) { _, _ ->
                TerminalChats.delete(this, chat.id)
                if (chat.id == chatId) showChat(TerminalChats.currentId(this))
                renderDrawer()
            }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
            .show()
    }

    // endregion


    companion object {
        private const val MAX_MESSAGES = 200
        private const val REQUEST_SAVE_TEXT = 7301
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

    }
}
