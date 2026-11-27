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
    private var pending = mutableListOf<String>()   // sent, not yet echoed by the hub
    private var sending = false
    private var lastItems: List<ChatItem> = emptyList()

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
        header.addView(ChatUi.avatar(this, "AG", 40))
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
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(6), dp(6), dp(6), dp(8))
        }
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
        sendBtn = TextView(this).apply {
            text = "➤"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = ChatUi.circle(ChatUi.SEND)
            contentDescription = getString(R.string.send)
            if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) rotationY = 180f
            setOnClickListener { onSendOrStop() }
        }
        bar.addView(sendBtn, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(6) })
        root.addView(bar)
        setContentView(root)
    }

    private fun setStatus(text: String) { status.text = text }

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
        val echoed = r.items.filter { it.kind == ChatItem.USER }.map { it.text }
        pending.removeAll { p -> echoed.any { it == p.trim() } }
        busy = r.busy || pending.isNotEmpty() || sending
        setStatus(getString(if (busy) R.string.chat_typing else if (authOk) R.string.chat_online else R.string.chat_signed_out))
        sendBtn.text = if (r.busy) "■" else "➤"

        val all = r.items.toMutableList()
        pending.forEach { all += ChatItem(ChatItem.USER, it, "🕓") }
        if (busy) all += ChatItem(ChatItem.TYPING, "")
        val sig = all.joinToString("|") { "${it.kind}:${it.stepIndex}:${it.text.length}:${it.text.hashCode()}" }
        if (sig != lastSignature) {
            val atBottom = !scroll.canScrollVertically(1)
            lastSignature = sig
            messages.removeAllViews()
            all.forEach { messages.addView(viewFor(it)) }
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

    private fun viewFor(item: ChatItem): View = when (item.kind) {
        ChatItem.USER -> bubble(item, outgoing = true)
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

    private fun bubble(item: ChatItem, outgoing: Boolean): View {
        val r = row(if (outgoing) Gravity.END else Gravity.START)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ChatUi.rounded(if (outgoing) ChatUi.OUT_BUBBLE else ChatUi.IN_BUBBLE, dp(10).toFloat())
            setPadding(dp(10), dp(6), dp(10), dp(4))
            elevation = dp(1).toFloat()
            setOnLongClickListener { copy(item.text); true }
        }
        box.addView(TextView(this).apply {
            text = if (outgoing) item.text else ChatUi.markdown(item.text)
            textSize = 16f; setTextColor(ChatUi.TEXT)
            maxWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()
            setLineSpacing(0f, 1.1f)
        })
        if (item.time.isNotEmpty()) box.addView(TextView(this).apply {
            text = item.time; textSize = 11f; setTextColor(ChatUi.MUTED)
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        r.addView(box)
        return r
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
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            if (busy) stop()
            return
        }
        if (!hubReady) { Toast.makeText(this, R.string.chat_not_ready, Toast.LENGTH_SHORT).show(); return }
        input.setText("")
        pending += text
        sending = true
        if (chat.title.isBlank()) {
            chat.title = text.lineSequence().first().take(40)
            titleView.text = chat.title
        }
        chat.preview = text.take(200); chat.updated = System.currentTimeMillis()
        ChatStore.update(this, chat)
        render(StepMapper.Result(lastItems, false, null, null))
        io.execute {
            val res = runCatching {
                if (!started) {
                    HubClient.startCascade(chat.cascadeId, java.io.File(AgyInstaller.homeDir(this), "projects").absolutePath)
                    prefs.edit().putBoolean("started_${chat.id}", true).apply()
                }
                HubClient.sendMessage(chat.cascadeId, text, UUID.randomUUID().toString())
            }
            main.post {
                sending = false
                res.onFailure { e ->
                    pending.remove(text)
                    input.setText(text)
                    AlertDialog.Builder(this).setTitle(R.string.chat_send_failed)
                        .setMessage(e.message ?: e.toString())
                        .setPositiveButton(R.string.close, null).show()
                    checkAuth()
                }
                schedulePoll(200)
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
        m.menu.add(0, 1, 0, R.string.chat_stop)
        m.menu.add(0, 2, 1, R.string.visual_ui)
        m.menu.add(0, 3, 2, R.string.terminal)
        m.menu.add(0, 4, 3, R.string.chat_signin)
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> stop()
                2 -> openVisualUi()
                3 -> startActivity(Intent(this, TerminalActivity::class.java))
                4 -> signIn()
            }
            true
        }
        m.show()
    }
}
