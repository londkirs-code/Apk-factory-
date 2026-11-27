package com.londkirs.antigravityremote

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import java.util.concurrent.Executors

/** WhatsApp-style conversation with the Antigravity agent running on this phone (agy --hub). */
class ChatActivity : Activity() {

    companion object {
        const val EXTRA_ID = "chat_id"
        private const val POLL_MS = 1200L
    }

    private lateinit var chat: Chat
    private lateinit var status: TextView
    private lateinit var titleView: TextView
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: TextView
    private lateinit var bannerButton: Button
    private lateinit var scroll: ScrollView
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var sendBtn: TextView

    private val io = Executors.newSingleThreadExecutor()
    private val main by lazy { Handler(mainLooper) }
    private val prefs by lazy { getSharedPreferences("chat_state", Context.MODE_PRIVATE) }
    private var hubReady = false
    private var authOk = false
    private var resumed = false
    private var polling = false
    private var busy = false
    private var lastSignature = ""
    private var pending = mutableListOf<Pair<String, Int>>()   // (text, user-message count when sent) until echoed
    private var userCount = 0
    private var pendingImage: Pair<String, String>? = null      // mime, base64
    private var activeSlash: HubClient.SlashCommand? = null
    private var slashCache: List<HubClient.SlashCommand> = emptyList()
    private lateinit var extras: LinearLayout
    private lateinit var suggestions: LinearLayout
    private lateinit var suggestionsBox: ScrollView
    private val autoPolicy get() = modelPrefs.getString("auto_policy", "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER")!!
    private var sending = false
    private var lastItems: List<ChatItem> = emptyList()
    private var quote: String? = null                 // message being replied to
    private var searchQuery: String? = null
    private lateinit var downFab: TextView
    private val rtl get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private var models: List<HubClient.ModelChoice> = emptyList()
    private val modelPrefs by lazy { getSharedPreferences("models", Context.MODE_PRIVATE) }

    /** The model to send: the user's pick if still offered, else the account default (first in the list). */
    private fun currentModel(): HubClient.ModelChoice? {
        val saved = modelPrefs.getString("model", null)
        return models.firstOrNull { it.value.toString() == saved } ?: models.firstOrNull()
    }

    private fun loadModels(): List<HubClient.ModelChoice> {
        if (models.isEmpty()) models = runCatching { HubClient.models() }.getOrDefault(emptyList())
        return models
    }

    private fun dp(v: Int) = ChatUi.dp(this, v)
    private val started get() = prefs.getBoolean("started_${chat.id}", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chat = ChatStore.get(this, intent.getStringExtra(EXTRA_ID) ?: "") ?: ChatStore.create(this)
        window.statusBarColor = ChatUi.HEADER_DARK
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildUi()
        setStatus(getString(R.string.chat_connecting))
        connect()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        schedulePoll(0)
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    // ---- UI ---------------------------------------------------------------------------------------

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ChatUi.WALLPAPER) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ChatUi.HEADER)
            setPadding(dp(4), 0, dp(4), 0)
        }
        header.addView(TextView(this).apply {
            text = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "→" else "←"
            textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(40), dp(56)))
        header.addView(ChatUi.avatarImage(this, 40))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        titleView = TextView(this).apply {
            text = chat.title.ifBlank { "Antigravity" }
            textSize = 17f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        status = TextView(this).apply { textSize = 13f; setTextColor(Color.parseColor("#D9FDD3")); setSingleLine() }
        titles.addView(titleView); titles.addView(status)
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "⋮"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setOnClickListener { showMenu(it) }
        }, LinearLayout.LayoutParams(dp(44), dp(56)))
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FFF3C4"))
            setPadding(dp(14), dp(8), dp(8), dp(8))
            visibility = View.GONE
        }
        bannerText = TextView(this).apply { textSize = 14f; setTextColor(ChatUi.TEXT) }
        banner.addView(bannerText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bannerButton = Button(this).apply { isAllCaps = false; textSize = 13f }
        banner.addView(bannerButton)
        root.addView(banner)

        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        scroll = ScrollView(this).apply { addView(messages); isFillViewport = true }
        val frame = FrameLayout(this)
        frame.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        downFab = TextView(this).apply {
            text = "⌄"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(ChatUi.MUTED)
            background = ChatUi.circle(Color.WHITE); elevation = dp(4).toFloat()
            visibility = View.GONE
            setOnClickListener { scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
        }
        frame.addView(downFab, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(14), dp(12)) })
        scroll.setOnScrollChangeListener { _, _, _, _, _ ->
            downFab.visibility = if (scroll.canScrollVertically(1)) View.VISIBLE else View.GONE
        }
        root.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ChatUi.rounded(Color.WHITE, dp(12).toFloat())
            elevation = dp(4).toFloat()
        }
        suggestionsBox = ScrollView(this).apply { addView(suggestions); visibility = View.GONE }
        root.addView(suggestionsBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(8), 0, dp(8), 0)
        })
        extras = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), 0)
            visibility = View.GONE
        }
        root.addView(extras)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(6), dp(6), dp(6), dp(8))
        }
        bar.addView(TextView(this).apply {
            text = "📎"; textSize = 22f; gravity = Gravity.CENTER
            contentDescription = getString(R.string.chat_attach)
            setOnClickListener { pickImage() }
        }, LinearLayout.LayoutParams(dp(40), dp(48)))

        input = EditText(this).apply {
            setHint(R.string.chat_hint)
            background = ChatUi.rounded(Color.WHITE, dp(24).toFloat())
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setTextColor(ChatUi.TEXT); setHintTextColor(ChatUi.MUTED)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 6
            minHeight = dp(48)
        }
        bar.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: android.text.Editable?) { updateSuggestions(e?.toString().orEmpty()); updateSendButton() }
        })
        sendBtn = TextView(this).apply {
            text = "🎤"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = ChatUi.circle(ChatUi.SEND)
            contentDescription = getString(R.string.send)
            setOnClickListener { onSendOrStop() }
        }
        bar.addView(sendBtn, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(6) })
        root.addView(bar)
        setContentView(root)
    }

    private fun setStatus(text: String) { status.text = text }

    private var runningNow = false

    /** WhatsApp-style: ➤ when there is something to send, 🎤 when empty, ■ to stop a running agent. */
    private fun updateSendButton() {
        val hasContent = input.text.isNotBlank() || activeSlash != null || pendingImage != null
        sendBtn.text = when { hasContent -> "➤"; runningNow -> "■"; else -> "🎤" }
        sendBtn.rotationY = if (rtl && sendBtn.text == "➤") 180f else 0f
    }

    private fun showBanner(text: String?, button: String? = null, action: (() -> Unit)? = null) {
        if (text == null) { banner.visibility = View.GONE; return }
        bannerText.text = text
        bannerButton.visibility = if (button != null) View.VISIBLE else View.GONE
        bannerButton.text = button ?: ""
        bannerButton.setOnClickListener { action?.invoke() }
        banner.visibility = View.VISIBLE
    }

    // ---- Connection / auth ------------------------------------------------------------------------

    private fun connect() {
        if (!AgyInstaller.isInstalled(this)) {
            showBanner(getString(R.string.chat_need_install), getString(R.string.settings_screen)) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            setStatus(getString(R.string.chat_offline))
            return
        }
        showBanner(getString(R.string.chat_starting_engine))
        HubClient.ensureRunning(this) { ok ->
            hubReady = ok
            if (!ok) {
                setStatus(getString(R.string.chat_offline))
                showBanner(getString(R.string.hub_failed), getString(R.string.terminal)) {
                    startActivity(Intent(this, TerminalActivity::class.java))
                }
                return@ensureRunning
            }
            showBanner(null)
            io.execute { loadModels() }
            checkAuth()
            schedulePoll(0)
        }
    }

    private fun checkAuth() {
        io.execute {
            val ok = runCatching { HubClient.hasValidAuth() }.getOrElse { true } // unknown → let the send report it
            main.post {
                authOk = ok
                if (ok) { showBanner(null); setStatus(getString(R.string.chat_online)) }
                else {
                    setStatus(getString(R.string.chat_signed_out))
                    showBanner(getString(R.string.chat_signin_needed), getString(R.string.chat_signin)) { signIn() }
                }
            }
        }
    }

    private fun signIn() {
        showBanner(getString(R.string.chat_signin_wait))
        // Login stays open until Chrome returns to the hub's callback port: run it on its own thread.
        Thread { runCatching { HubClient.login() } }.start()
        io.execute {
            for (i in 0 until 150) {
                if (runCatching { HubClient.hasValidAuth() }.getOrDefault(false)) {
                    main.post { authOk = true; showBanner(null); setStatus(getString(R.string.chat_online)) }
                    return@execute
                }
                Thread.sleep(2000)
            }
            main.post { checkAuth() }
        }
    }

    // ---- Polling ----------------------------------------------------------------------------------

    private fun schedulePoll(delay: Long) {
        if (polling) return
        polling = true
        main.postDelayed({ polling = false; poll() }, delay)
    }

    private fun poll() {
        if (!resumed || isFinishing) return
        if (!hubReady || !started) { if (resumed) schedulePoll(POLL_MS); return }
        io.execute {
            val result = runCatching { StepMapper.map(HubClient.steps(chat.cascadeId)) }
            main.post {
                result.onSuccess { render(it) }
                    .onFailure { if (!busy) setStatus(getString(R.string.chat_reconnecting)) }
                schedulePoll(POLL_MS)
            }
        }
    }

    private fun render(r: StepMapper.Result) {
        lastItems = r.items
        // Drop pending bubbles once the hub has echoed them.
        userCount = r.items.count { it.kind == ChatItem.USER }
        pending.removeAll { userCount > it.second }
        busy = r.busy || pending.isNotEmpty() || sending
        setStatus(getString(if (busy) R.string.chat_typing else if (authOk) R.string.chat_online else R.string.chat_signed_out))
        runningNow = r.busy
        updateSendButton()

        val all = r.items.toMutableList()
        pending.forEach { all += ChatItem(ChatItem.USER, it.first, "🕓") }
        if (busy) all += ChatItem(ChatItem.TYPING, "")
        val sig = all.joinToString("|") { "${it.kind}:${it.stepIndex}:${it.text.length}:${it.text.hashCode()}" }
        if (sig != lastSignature) {
            val atBottom = !scroll.canScrollVertically(1)
            lastSignature = sig
            messages.removeAllViews()
            var lastDay = ""
            all.forEachIndexed { idx, it ->
                if (it.millis > 0) {
                    val day = dayLabel(it.millis)
                    if (day != lastDay) { messages.addView(dateChip(day)); lastDay = day }
                }
                val replied = it.kind == ChatItem.USER && all.drop(idx + 1).any { x -> x.kind == ChatItem.AGENT }
                messages.addView(viewFor(it, replied))
            }
            if (atBottom || all.lastOrNull()?.kind == ChatItem.TYPING) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        // Keep the chat list preview fresh.
        val preview = r.lastAgentText ?: r.lastUserText
        if (preview != null && preview != chat.preview) {
            chat.preview = preview.take(200); chat.updated = System.currentTimeMillis()
            ChatStore.update(this, chat)
        }
    }

    // ---- Message views ----------------------------------------------------------------------------

    private fun dayLabel(ms: Long): String {
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        cal.timeInMillis = ms
        val d = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        return when (today - d) {
            0 -> getString(R.string.chat_today)
            1 -> getString(R.string.chat_yesterday)
            else -> android.text.format.DateFormat.getMediumDateFormat(this).format(java.util.Date(ms))
        }
    }

    private fun dateChip(label: String): View {
        val r = row(Gravity.CENTER_HORIZONTAL)
        r.addView(TextView(this).apply {
            text = label; textSize = 12.5f; setTextColor(Color.parseColor("#54656F"))
            background = ChatUi.rounded(ChatUi.DATE_CHIP, dp(8).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        (r.layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(8), 0, dp(4))
        return r
    }

    private fun viewFor(item: ChatItem, replied: Boolean = false): View = when (item.kind) {
        ChatItem.USER -> bubble(item, outgoing = true, replied = replied)
        ChatItem.AGENT -> bubble(item, outgoing = false)
        ChatItem.TOOL -> chip(item.text, ChatUi.TOOL_BG) { showDetails(item) }
        ChatItem.ERROR -> chip("⚠️ " + item.text, ChatUi.ERROR_BG) { showError(item) }
        ChatItem.APPROVAL -> approval(item)
        else -> typing()
    }

    private fun row(gravity: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) }
    }

    private fun bubble(item: ChatItem, outgoing: Boolean, replied: Boolean = false): View {
        val r = row(if (outgoing) Gravity.END else Gravity.START)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ChatUi.bubbleBg(this@ChatActivity, if (outgoing) ChatUi.OUT_BUBBLE else ChatUi.IN_BUBBLE, outgoing, rtl, tail = true)
            setPadding(dp(10), dp(6), dp(10), dp(4))
            elevation = dp(1).toFloat()
            setOnLongClickListener { messageMenu(item, outgoing); true }
        }
        val body: CharSequence = if (outgoing) item.text else ChatUi.markdown(item.text)
        box.addView(TextView(this).apply {
            text = highlight(body)
            textSize = 16f; setTextColor(ChatUi.TEXT)
            maxWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()
            setLineSpacing(0f, 1.1f)
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.WEB_URLS)
            setOnLongClickListener { messageMenu(item, outgoing); true }
        })
        val meta = android.text.SpannableStringBuilder(item.time)
        if (outgoing && item.time != "🕓") {
            val start = meta.length
            meta.append(if (meta.isNotEmpty()) "  ✓✓" else "✓✓")
            meta.setSpan(android.text.style.ForegroundColorSpan(if (replied) ChatUi.TICK_BLUE else ChatUi.MUTED), start, meta.length, 0)
        }
        if (meta.isNotEmpty()) box.addView(TextView(this).apply {
            text = meta; textSize = 11f; setTextColor(ChatUi.MUTED)
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        r.addView(box)
        return r
    }

    private fun highlight(text: CharSequence): CharSequence {
        val q = searchQuery?.takeIf { it.isNotBlank() } ?: return text
        val sb = android.text.SpannableStringBuilder(text)
        val lower = text.toString().lowercase(); val ql = q.lowercase()
        var i = lower.indexOf(ql)
        while (i >= 0) {
            sb.setSpan(android.text.style.BackgroundColorSpan(Color.parseColor("#FFE066")), i, i + ql.length, 0)
            i = lower.indexOf(ql, i + ql.length)
        }
        return sb
    }

    /** Long-press actions, like The Boss chat plus WhatsApp's reply/share. */
    private fun messageMenu(item: ChatItem, outgoing: Boolean) {
        val actions = mutableListOf<Pair<String, () -> Unit>>(
            getString(R.string.msg_reply) to { setQuote(item.text) },
            getString(R.string.copy) to { copy(item.text) },
            getString(R.string.msg_share) to { share(item.text) },
            getString(R.string.msg_select) to { selectText(item.text) },
        )
        if (outgoing) {
            actions += getString(R.string.msg_edit) to { input.setText(item.text); input.setSelection(input.text.length); input.requestFocus(); Unit }
            actions += getString(R.string.msg_resend) to { input.setText(item.text); onSendOrStop() }
        }
        AlertDialog.Builder(this)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun setQuote(text: String) {
        quote = text
        refreshExtras()
        input.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun share(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.msg_share)))
    }

    private fun selectText(text: String) {
        val tv = TextView(this).apply {
            this.text = text; setTextIsSelectable(true); textSize = 16f
            setPadding(dp(20), dp(16), dp(20), dp(8)); setTextColor(ChatUi.TEXT)
        }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton(R.string.close, null).show()
    }

    /** Whole conversation as text ("Me:"/"Antigravity:"), for copy / share. */
    private fun transcript(): String = lastItems.filter { it.kind == ChatItem.USER || it.kind == ChatItem.AGENT || it.kind == ChatItem.TOOL }
        .joinToString("\n\n") {
            when (it.kind) {
                ChatItem.USER -> getString(R.string.chat_me) + ": " + it.text
                ChatItem.AGENT -> "Antigravity: " + it.text
                else -> "  " + it.text
            }
        }

    private fun searchInChat() {
        val field = EditText(this).apply { setHint(R.string.chat_search_hint); setText(searchQuery ?: ""); setSingleLine() }
        AlertDialog.Builder(this).setTitle(R.string.chat_search).setView(field)
            .setPositiveButton(R.string.chat_search) { _, _ ->
                searchQuery = field.text.toString().trim().ifEmpty { null }
                lastSignature = ""
                render(StepMapper.Result(lastItems, runningNow, null, null))
                val q = searchQuery ?: return@setPositiveButton
                val hits = lastItems.count { it.text.contains(q, ignoreCase = true) }
                Toast.makeText(this, getString(R.string.chat_search_hits, hits), Toast.LENGTH_SHORT).show()
                // Scroll to the first matching row.
                scroll.post {
                    for (i in 0 until messages.childCount) {
                        val v = messages.getChildAt(i)
                        if (textOf(v).contains(q, ignoreCase = true)) { scroll.smoothScrollTo(0, v.top); break }
                    }
                }
            }
            .setNeutralButton(R.string.chat_search_clear) { _, _ -> searchQuery = null; lastSignature = ""; render(StepMapper.Result(lastItems, runningNow, null, null)) }
            .show()
    }

    private fun textOf(v: View): String = when (v) {
        is TextView -> v.text.toString()
        is ViewGroup -> (0 until v.childCount).joinToString(" ") { textOf(v.getChildAt(it)) }
        else -> ""
    }

    private fun startVoice() {
        val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.chat_voice_prompt))
        try { startActivityForResult(i, 42) } catch (e: Exception) {
            Toast.makeText(this, R.string.chat_voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun chip(text: String, color: Int, onClick: () -> Unit): View {
        val r = row(Gravity.CENTER_HORIZONTAL)
        r.addView(TextView(this).apply {
            this.text = text
            textSize = 12.5f; setTextColor(Color.parseColor("#3B4A54"))
            background = ChatUi.rounded(color, dp(8).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            maxWidth = (resources.displayMetrics.widthPixels * 0.9).toInt()
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { onClick() }
        })
        return r
    }

    private fun approval(item: ChatItem): View {
        val r = row(Gravity.START)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ChatUi.rounded(Color.WHITE, dp(10).toFloat())
            setPadding(dp(12), dp(10), dp(12), dp(8))
            elevation = dp(1).toFloat()
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.chat_approval_title)
            textSize = 15f; setTextColor(ChatUi.TEXT); setTypeface(typeface, Typeface.BOLD)
        })
        if (item.text.isNotBlank()) box.addView(TextView(this).apply {
            text = item.text; textSize = 14f; setTextColor(ChatUi.MUTED)
            maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()
            setPadding(0, dp(4), 0, dp(6))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: Int, color: Int, allow: Boolean) = TextView(this).apply {
            setText(label); textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            background = ChatUi.rounded(color, dp(18).toFloat())
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setOnClickListener { answer(item, allow) }
        }
        buttons.addView(btn(R.string.chat_allow, ChatUi.SEND, true))
        buttons.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        buttons.addView(btn(R.string.chat_deny, Color.parseColor("#D9534F"), false))
        box.addView(buttons)
        r.addView(box)
        return r
    }

    private fun typing(): View {
        val r = row(Gravity.START)
        r.addView(TextView(this).apply {
            text = "● ● ●"
            textSize = 14f; setTextColor(ChatUi.MUTED)
            background = ChatUi.rounded(ChatUi.IN_BUBBLE, dp(10).toFloat())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            alpha = 0.85f
        })
        return r
    }

    // ---- Actions ----------------------------------------------------------------------------------

    private fun onSendOrStop() {
        val typed = input.text.toString().trim()
        val q = quote
        val text = if (q != null && typed.isNotEmpty())
            q.lineSequence().take(6).joinToString("\n") { "> $it" } + "\n\n" + typed else typed
        if (typed.isNotEmpty()) quote = null
        val slash = activeSlash
        val image = pendingImage
        if (text.isEmpty() && slash == null && image == null) {
            if (runningNow) stop() else startVoice()
            return
        }
        if (!hubReady) { Toast.makeText(this, R.string.chat_not_ready, Toast.LENGTH_SHORT).show(); return }
        input.setText("")
        activeSlash = null; pendingImage = null; refreshExtras(); suggestionsBox.visibility = View.GONE
        val shown = buildString {
            if (slash != null) append("/").append(slash.info.optString("name")).append(if (text.isNotEmpty()) " " else "")
            append(text)
            if (image != null) append("\n🖼️")
        }.trim()
        pending += shown to userCount
        sending = true
        if (chat.title.isBlank()) {
            chat.title = shown.lineSequence().first().take(40)
            titleView.text = chat.title
        }
        chat.preview = shown.take(200); chat.updated = System.currentTimeMillis()
        ChatStore.update(this, chat)
        render(StepMapper.Result(lastItems, false, null, null))
        io.execute {
            val res = runCatching {
                loadModels()
                val model = currentModel()?.value
                if (!started) {
                    HubClient.startCascade(chat.cascadeId, java.io.File(AgyInstaller.homeDir(this), "projects").absolutePath, model)
                    prefs.edit().putBoolean("started_${chat.id}", true).apply()
                }
                val items = org.json.JSONArray()
                if (slash != null) items.put(org.json.JSONObject().put("item",
                    org.json.JSONObject().put("slashCommand", org.json.JSONObject().put("info", slash.info))))
                if (text.isNotEmpty()) items.put(org.json.JSONObject().put("text", text))
                var media: org.json.JSONArray? = null
                if (image != null) {
                    val uri = HubClient.saveMedia(image.first, image.second)
                    media = org.json.JSONArray().put(org.json.JSONObject()
                        .put("mimeType", image.first).put("inlineData", image.second)
                        .apply { if (uri.isNotBlank()) put("uri", uri) })
                }
                HubClient.sendMessage(chat.cascadeId, items, UUID.randomUUID().toString(), model, autoPolicy, media)
            }
            main.post {
                sending = false
                res.onFailure { e ->
                    pending.removeAll { it.first == shown }
                    input.setText(typed); quote = q
                    activeSlash = slash; pendingImage = image; refreshExtras()
                    AlertDialog.Builder(this).setTitle(R.string.chat_send_failed)
                        .setMessage(e.message ?: e.toString())
                        .setPositiveButton(R.string.close, null).show()
                    checkAuth()
                }
                schedulePoll(200)
            }
        }
    }

    // ---- Slash commands ---------------------------------------------------------------------------

    private fun updateSuggestions(text: String) {
        if (activeSlash != null || !text.startsWith("/") || text.contains(' ') || text.contains('\n')) {
            suggestionsBox.visibility = View.GONE; return
        }
        if (slashCache.isEmpty()) {
            io.execute {
                val list = runCatching { HubClient.slashCommands(if (started) chat.cascadeId else null, currentModel()?.value, autoPolicy) }
                    .getOrDefault(emptyList())
                main.post { slashCache = list; if (list.isNotEmpty()) updateSuggestions(input.text.toString()) }
            }
            return
        }
        val q = text.removePrefix("/").lowercase()
        val matches = slashCache.filter { it.title.lowercase().contains(q) || it.info.optString("name").lowercase().contains(q) }.take(8)
        suggestions.removeAllViews()
        matches.forEach { c ->
            suggestions.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener { chooseSlash(c) }
                addView(TextView(this@ChatActivity).apply {
                    this.text = "/" + c.title; textSize = 15f; setTextColor(ChatUi.TEXT); setTypeface(typeface, Typeface.BOLD)
                    textDirection = View.TEXT_DIRECTION_LTR
                })
                if (c.description.isNotBlank()) addView(TextView(this@ChatActivity).apply {
                    this.text = c.description; textSize = 12.5f; setTextColor(ChatUi.MUTED); maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            })
        }
        suggestionsBox.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
        if (matches.size > 4) suggestionsBox.layoutParams = suggestionsBox.layoutParams.apply { height = dp(260) }
        else suggestionsBox.layoutParams = suggestionsBox.layoutParams.apply { height = LinearLayout.LayoutParams.WRAP_CONTENT }
    }

    private fun chooseSlash(c: HubClient.SlashCommand) {
        activeSlash = c
        input.setText("")
        suggestionsBox.visibility = View.GONE
        refreshExtras()
    }

    /** Chips above the input: the chosen slash command and the attached image. */
    private fun refreshExtras() {
        extras.removeAllViews()
        fun chip(label: String, onRemove: () -> Unit) = TextView(this).apply {
            text = "$label  ✕"; textSize = 13.5f; setTextColor(ChatUi.TEXT)
            background = ChatUi.rounded(Color.parseColor("#D1F4CC"), dp(14).toFloat())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { onRemove(); refreshExtras() }
        }
        quote?.let { q ->
            extras.addView(chip("↩ " + q.replace('\n', ' ').take(40)) { quote = null })
            extras.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        }
        activeSlash?.let { c ->
            extras.addView(chip("/" + c.title) { activeSlash = null })
            extras.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        }
        pendingImage?.let { extras.addView(chip("🖼️ " + getString(R.string.chat_image)) { pendingImage = null }) }
        extras.visibility = if (extras.childCount > 0) View.VISIBLE else View.GONE
        updateSendButton()
    }

    // ---- Images -----------------------------------------------------------------------------------

    private fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        runCatching { startActivityForResult(Intent.createChooser(i, getString(R.string.chat_attach)), 41) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) {
            val said = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            val cur = input.text.toString()
            input.setText(if (cur.isBlank()) said else "$cur $said")
            input.setSelection(input.text.length)
            return
        }
        val uri = data?.data
        if (requestCode != 41 || resultCode != RESULT_OK || uri == null) return
        io.execute {
            val encoded = runCatching { encodeImage(uri) }.getOrNull()
            main.post {
                if (encoded == null) Toast.makeText(this, R.string.chat_image_failed, Toast.LENGTH_SHORT).show()
                else { pendingImage = "image/jpeg" to encoded; refreshExtras() }
            }
        }
    }

    /** Downscales to ≤1600px and encodes as JPEG/base64 (the web UI also sends JPEG). */
    private fun encodeImage(uri: android.net.Uri): String {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bmp = contentResolver.openInputStream(uri)!!.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw java.io.IOException("decode failed")
        val scale = 1600f / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val bytes = java.io.ByteArrayOutputStream().also { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun pickModel() {
        io.execute {
            val list = loadModels()
            main.post {
                if (list.isEmpty()) { Toast.makeText(this, R.string.chat_not_ready, Toast.LENGTH_SHORT).show(); return@post }
                val cur = list.indexOf(currentModel())
                AlertDialog.Builder(this)
                    .setTitle(R.string.chat_model_title)
                    .setSingleChoiceItems(list.map { m -> m.name + (m.quota?.let { "  ·  ${(it * 100).toInt()}%" } ?: "") }.toTypedArray(), cur) { d, which ->
                        val chosen = list[which]
                        modelPrefs.edit().putString("model", chosen.value.toString()).apply()
                        io.execute { HubClient.rememberModel(chosen.value) }
                        slashCache = emptyList()
                        d.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun stop() {
        io.execute {
            runCatching { HubClient.cancel(chat.cascadeId) }
            main.post { schedulePoll(200) }
        }
    }

    private fun answer(item: ChatItem, allow: Boolean) {
        val reply = StepMapper.interactionReply(item, allow)
        if (reply == null) {
            // An interaction type this screen does not know: finish it in the full UI.
            openVisualUi(); return
        }
        io.execute {
            val res = runCatching { HubClient.respond(chat.cascadeId, reply) }
            main.post {
                res.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                schedulePoll(200)
            }
        }
    }

    private fun showDetails(item: ChatItem) {
        val out = StepMapper.details(item.step)
        AlertDialog.Builder(this)
            .setMessage(if (out != null) item.text + "\n\n" + out.takeLast(6000) else item.text)
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.copy) { _, _ -> copy(out ?: item.text) }
            .show()
            .findViewById<TextView>(android.R.id.message)?.typeface = Typeface.MONOSPACE
    }

    /** Full error text from the step (the bubble shows only the short user message). */
    private fun showError(item: ChatItem) {
        val s = item.step
        val err = s?.optJSONObject("errorMessage")?.optJSONObject("error") ?: s?.optJSONObject("error")
        val full = buildString {
            append(item.text)
            if (err != null) for (k in listOf("shortError", "modelErrorMessage", "fullError", "details")) {
                val v = err.optString(k)
                if (v.isNotBlank() && v != item.text) append("\n\n").append(k).append(":\n").append(v)
            }
        }
        AlertDialog.Builder(this)
            .setMessage(full.take(8000))
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.copy) { _, _ -> copy(full) }
            .show()
    }

    private fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("chat", text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun openVisualUi() {
        startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, TerminalService.hubUiUrl ?: TerminalService.HUB_URL))
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 5, 0, getString(R.string.chat_model, currentModel()?.name ?: "…"))
        m.menu.add(0, 6, 0, if (autoPolicy == "CASCADE_COMMANDS_AUTO_EXECUTION_OFF") R.string.chat_exec_ask else R.string.chat_exec_auto)
        m.menu.add(0, 1, 0, R.string.chat_stop)
        m.menu.add(0, 2, 1, R.string.visual_ui)
        m.menu.add(0, 3, 2, R.string.terminal)
        m.menu.add(0, 4, 3, R.string.chat_signin)
        m.menu.add(0, 7, 4, R.string.chat_search)
        m.menu.add(0, 8, 5, R.string.chat_copy_all)
        m.menu.add(0, 9, 6, R.string.chat_share_all)
        m.menu.add(0, 10, 7, R.string.chat_paste)
        m.menu.add(0, 11, 8, R.string.chat_delete)
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> stop()
                2 -> openVisualUi()
                3 -> startActivity(Intent(this, TerminalActivity::class.java))
                4 -> signIn()
                5 -> pickModel()
                7 -> searchInChat()
                8 -> copy(transcript())
                9 -> share(transcript())
                10 -> {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.takeIf { c -> c.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
                    if (t.isNullOrEmpty()) Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
                    else { input.setText(input.text.toString() + t); input.setSelection(input.text.length) }
                }
                11 -> AlertDialog.Builder(this).setMessage(R.string.chat_delete_confirm)
                    .setPositiveButton(R.string.delete) { _, _ -> ChatStore.delete(this, chat.id); finish() }
                    .setNegativeButton(R.string.cancel, null).show()
                6 -> {
                    val next = if (autoPolicy == "CASCADE_COMMANDS_AUTO_EXECUTION_OFF") "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER" else "CASCADE_COMMANDS_AUTO_EXECUTION_OFF"
                    modelPrefs.edit().putString("auto_policy", next).apply()
                    Toast.makeText(this, if (next == "CASCADE_COMMANDS_AUTO_EXECUTION_OFF") R.string.chat_exec_now_ask else R.string.chat_exec_now_auto, Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        m.show()
    }
}
