package com.londkirs.antigravityremote

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.abs

/** WhatsApp-style conversation with the Antigravity agent running on this phone (agy --hub). */
class ChatActivity : BaseActivity() {

    companion object {
        const val EXTRA_ID = "chat_id"
        const val EXTRA_SCROLL_KEY = "scroll_key"
        const val EXTRA_TEXT = "text"
        private const val POLL_MS = 1200L
        private const val REQ_GALLERY = 41
        private const val REQ_CAMERA = 43
        private const val REQ_FILE = 44
        private const val REQ_WALLPAPER = 45
        private const val REQ_MIC = 9

        fun open(a: android.app.Activity, chatId: String, scrollKey: String? = null, text: String? = null) {
            a.startActivity(Intent(a, ChatActivity::class.java).putExtra(EXTRA_ID, chatId)
                .apply { scrollKey?.let { putExtra(EXTRA_SCROLL_KEY, it) }; text?.let { putExtra(EXTRA_TEXT, it) } })
        }
    }

    // ---- state ----------------------------------------------------------------------------------
    private lateinit var chat: Chat
    private lateinit var meta: MsgMeta
    private val io = Executors.newSingleThreadExecutor()
    private val main by lazy { Handler(mainLooper) }
    private var hubReady = false
    private var authOk = true
    private var resumed = false
    private var polling = false
    private var busy = false
    private var waitingApproval = false
    private var busyLabel: String? = null
    private var sending = false
    private var items: List<ChatItem> = emptyList()
    private var rows: List<Row> = emptyList()
    private class Pending(val text: String, val media: List<MediaRef>, val userCount: Int, val voice: String?)
    private val pending = mutableListOf<Pending>()
    private var lastSig = ""
    private val selected = LinkedHashSet<String>()
    private var searchQuery: String? = null
    private var searchHits: List<Int> = emptyList()
    private var searchPos = -1
    private val expanded = HashSet<String>()
    private var quote: ChatItem? = null
    private val pendingImages = mutableListOf<Pair<String, File>>()   // base64, local preview file
    private val pendingFiles = mutableListOf<File>()
    private var activeSlash: HubClient.SlashCommand? = null
    private var slashCache: List<HubClient.SlashCommand> = emptyList()
    private var unreadFrom = -1
    private var unreadAtOpen = 0
    private var newWhileUp = 0
    private var scrollKey: String? = null
    private var flashKey: String? = null
    private var cameraFile: File? = null
    private var pinIndex = 0

    // ---- views ----------------------------------------------------------------------------------
    private lateinit var wall: ImageView
    private lateinit var headerFrame: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var avatarHolder: FrameLayout
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionCount: TextView
    private lateinit var selectionActions: LinearLayout
    private lateinit var searchBar: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var searchCount: TextView
    private lateinit var pinBanner: LinearLayout
    private lateinit var pinText: TextView
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: TextView
    private lateinit var bannerButton: TextView
    private lateinit var list: ListView
    private lateinit var downFab: FrameLayout
    private lateinit var downBadge: TextView
    private lateinit var suggestBox: ScrollView
    private lateinit var suggestions: LinearLayout
    private lateinit var quotePanel: LinearLayout
    private lateinit var quoteTitle: TextView
    private lateinit var quoteText: TextView
    private lateinit var attachStrip: HorizontalScrollView
    private lateinit var attachRow: LinearLayout
    private lateinit var composer: LinearLayout
    private lateinit var inputRow: LinearLayout
    private lateinit var input: EditText
    private lateinit var cameraBtn: View
    private lateinit var sendBtn: ImageView
    private lateinit var voicePanel: LinearLayout
    private lateinit var voiceTime: TextView
    private lateinit var voiceHint: TextView
    private lateinit var voiceDot: View
    private lateinit var voiceLock: LinearLayout
    private val adapter = RowAdapter()
    private val viewCache = HashMap<String, View>()

    private val maxBubble get() = (resources.displayMetrics.widthPixels * 0.8).toInt()
    private val rtl get() = window.decorView.layoutDirection == View.LAYOUT_DIRECTION_RTL

    // ---- lifecycle ------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)
        chat = id?.let { ChatStore.get(this, it) } ?: ChatStore.create(this)
        meta = MsgMeta.load(this, chat.id)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        unreadAtOpen = chat.unread
        if (unreadAtOpen > 0) unreadFrom = chat.seenCount
        scrollKey = intent.getStringExtra(EXTRA_SCROLL_KEY)
        buildUi()
        input.setText(intent.getStringExtra(EXTRA_TEXT) ?: chat.draft)
        input.setSelection(input.text.length)
        items = ChatCache.load(this, chat.id)
        render()
        connect()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        ChatWatcher.openChatId = chat.id
        Notifier.cancel(this, chat.id)
        ChatStore.get(this, chat.id)?.let { fresh -> chat = fresh; titleView.text = chat.name(this); updateAvatar() }
        meta = MsgMeta.load(this, chat.id)
        Wallpapers.apply(this, wall, Wallpapers.spec(this, chat))
        schedulePoll(0)
    }

    override fun onPause() {
        resumed = false
        if (ChatWatcher.openChatId == chat.id) ChatWatcher.openChatId = null
        val draft = input.text.toString()
        ChatStore.edit(this, chat.id) { it.draft = draft; it.seenCount = it.agentCount; it.unread = 0; it.markedUnread = false }
        recorder?.cancel(); recorder = null
        VoicePlayer.stop()
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onBackPressed() {
        when {
            recorder != null -> cancelRecording()
            selected.isNotEmpty() -> clearSelection()
            searchQuery != null || searchBar.visibility == View.VISIBLE -> closeSearch()
            suggestBox.visibility == View.VISIBLE -> suggestBox.visibility = View.GONE
            else -> super.onBackPressed()
        }
    }

    // ---- layout ---------------------------------------------------------------------------------

    private fun buildUi() {
        val root = FrameLayout(this)
        wall = ImageView(this)
        root.addView(wall, flp(MATCH, MATCH))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(column, flp(MATCH, MATCH))

        headerFrame = FrameLayout(this).apply { setBackgroundColor(T.bar); elevation = dpf(2f) }
        header = buildHeader()
        selectionBar = buildSelectionBar()
        searchBar = buildSearchBar()
        headerFrame.addView(header, flp(MATCH, dp(62)))
        headerFrame.addView(selectionBar, flp(MATCH, dp(62)))
        headerFrame.addView(searchBar, flp(MATCH, dp(62)))
        column.addView(headerFrame, lp(MATCH, dp(62)))

        pinBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(T.surface); elevation = dpf(1f)
            setPadding(dp(16), dp(8), dp(8), dp(8)); visibility = View.GONE
            background = Ui.ripple(android.graphics.drawable.ColorDrawable(T.surface))
            setOnClickListener { jumpToPinned() }
            setOnLongClickListener { pinnedMenu(it); true }
        }
        pinBanner.addView(View(this).apply { background = Ui.round(T.primary, dpf(2f)) }, lp(dp(3), dp(32)).apply { marginEnd = dp(12) })
        val pinTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pinTexts.addView(Ui.text(this, tr("הודעה נעוצה", "Pinned message"), 13f, T.primary, bold = true))
        pinText = Ui.text(this, "", 14f, T.muted, lines = 1)
        pinTexts.addView(pinText)
        pinBanner.addView(pinTexts, lp(0, WRAP, 1f))
        pinBanner.addView(Ui.icon(this, R.drawable.ic_pin, T.muted, 20))
        column.addView(pinBanner, lp(MATCH, WRAP))

        banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(T.primarySoft)
            setPadding(dp(16), dp(6), dp(8), dp(6)); visibility = View.GONE
        }
        bannerText = Ui.text(this, "", 14f, T.text)
        banner.addView(bannerText, lp(0, WRAP, 1f))
        bannerButton = Ui.button(this, "", Ui.Style.PRIMARY) {}.apply { minHeight = dp(38); setPadding(dp(14), dp(4), dp(14), dp(4)) }
        banner.addView(bannerButton)
        column.addView(banner, lp(MATCH, WRAP))

        val listFrame = FrameLayout(this)
        list = ListView(this).apply {
            divider = null; dividerHeight = 0
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            transcriptMode = AbsListView.TRANSCRIPT_MODE_NORMAL
            isStackFromBottom = true
            clipToPadding = false
            setPadding(0, dp(6), 0, dp(8))
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            adapter = this@ChatActivity.adapter
        }
        listFrame.addView(list, flp(MATCH, MATCH))
        downFab = FrameLayout(this).apply {
            background = Ui.ripple(Ui.circle(T.surface), dpf(22f)); elevation = dpf(4f); visibility = View.GONE
            setOnClickListener { scrollToBottom(true) }
        }
        downFab.addView(Ui.icon(this, R.drawable.ic_expand, T.muted), flp(dp(24), dp(24), Gravity.CENTER))
        downBadge = Ui.badge(this, 0).apply { visibility = View.GONE }
        val fabWrap = FrameLayout(this).apply { clipChildren = false }
        fabWrap.addView(downFab, flp(dp(44), dp(44), Gravity.BOTTOM))
        fabWrap.addView(downBadge, flp(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        listFrame.addView(fabWrap, flp(dp(48), dp(62), Gravity.BOTTOM or Gravity.END).apply { marginEnd = dp(12); bottomMargin = dp(10) })
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView?, s: Int) {}
            override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) { updateDownFab() }
        })
        column.addView(listFrame, lp(MATCH, 0, 1f))

        suggestions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        suggestBox = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(dp(280), View.MeasureSpec.AT_MOST))
        }.apply {
            addView(suggestions); visibility = View.GONE
            background = Ui.round(T.surface, dpf(18f)); elevation = dpf(6f)
        }
        column.addView(suggestBox, lp(MATCH, WRAP).apply { setMargins(dp(8), 0, dp(8), dp(4)) })

        composer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(8)) }
        buildComposer()
        column.addView(composer, lp(MATCH, WRAP))
        setContentView(root)
        updateAvatar()
        refreshComposer()
        CoachMark.show(this, sendBtn, "chat_mic", tr("החזיקו את המיקרופון כדי להקליט הודעה קולית. היא תומלל ותישלח כטקסט.",
            "Hold the microphone to record a voice message. It is transcribed and sent as text."), 1200)
    }

    private fun buildHeader(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(2), 0, dp(2), 0)
        addView(Ui.iconButton(this@ChatActivity, R.drawable.ic_back, tr("חזרה", "Back")) { onBackPressed() }.apply { layoutParams = lp(dp(40), dp(48)) })
        avatarHolder = FrameLayout(this@ChatActivity)
        addView(avatarHolder, lp(dp(40), dp(40)))
        val texts = LinearLayout(this@ChatActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(10), dp(6), dp(6), dp(6))
            background = Ui.ripple(null, dpf(10f))
            setOnClickListener { ChatInfoActivity.open(this@ChatActivity, chat.id) }
        }
        titleView = Ui.text(this@ChatActivity, chat.name(this@ChatActivity), 17.5f, T.text, bold = true, lines = 1)
        statusView = Ui.text(this@ChatActivity, tr("מתחבר…", "Connecting…"), 13f, T.muted, lines = 1)
        texts.addView(titleView); texts.addView(statusView)
        addView(texts, lp(0, WRAP, 1f))
        addView(Ui.iconButton(this@ChatActivity, R.drawable.ic_sparkle, tr("מודל", "Model"), T.text) { pickModel() })
        addView(Ui.iconButton(this@ChatActivity, R.drawable.ic_more, tr("עוד", "More"), T.text) { showMenu(it) })
    }

    private fun updateAvatar() {
        avatarHolder.removeAllViews()
        val v = chatAvatar(this, chat, 40)
        avatarHolder.addView(v, flp(dp(40), dp(40)))
        avatarHolder.setOnClickListener { ChatInfoActivity.open(this, chat.id) }
    }

    private fun buildSelectionBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(T.bar); visibility = View.GONE
        addView(Ui.iconButton(this@ChatActivity, R.drawable.ic_close, tr("ביטול בחירה", "Clear selection")) { clearSelection() })
        selectionCount = Ui.text(this@ChatActivity, "", 19f, T.text, bold = true)
        addView(selectionCount, lp(0, WRAP, 1f).apply { marginStart = dp(8) })
        selectionActions = LinearLayout(this@ChatActivity).apply { orientation = LinearLayout.HORIZONTAL }
        addView(selectionActions)
    }

    private fun buildSearchBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(T.bar); visibility = View.GONE
        addView(Ui.iconButton(this@ChatActivity, R.drawable.ic_back, tr("סגירת החיפוש", "Close search")) { closeSearch() })
        searchField = EditText(this@ChatActivity).apply {
            hint = tr("חיפוש בשיחה…", "Search this chat…"); isSingleLine = true
            setTextColor(T.text); setHintTextColor(T.faint); background = null
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, T.fs(17f))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) { runSearch(e?.toString().orEmpty()) }
            })
        }
        addView(searchField, lp(0, WRAP, 1f))
        searchCount = Ui.text(this@ChatActivity, "", 13f, T.muted)
        addView(searchCount)
        addView(Ui.iconButton(this@ChatActivity, R.drawable.ic_up, tr("הקודם", "Previous")) { stepSearch(-1) })
        addView(Ui.iconButton(this@ChatActivity, R.drawable.ic_expand, tr("הבא", "Next")) { stepSearch(1) })
    }

    private fun buildComposer() {
        quotePanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(T.surface, dpf(18f)); visibility = View.GONE
            setPadding(dp(10), dp(8), dp(4), dp(8))
        }
        quotePanel.addView(View(this).apply { background = Ui.round(T.primary, dpf(2f)) }, lp(dp(4), dp(38)).apply { marginEnd = dp(10) })
        val qt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        quoteTitle = Ui.text(this, "", 13.5f, T.primary, bold = true)
        quoteText = Ui.text(this, "", 14f, T.muted, lines = 2)
        qt.addView(quoteTitle); qt.addView(quoteText)
        quotePanel.addView(qt, lp(0, WRAP, 1f))
        quotePanel.addView(Ui.iconButton(this, R.drawable.ic_close, tr("ביטול ציטוט", "Cancel reply"), T.muted) { quote = null; refreshComposer() })
        composer.addView(quotePanel, lp(MATCH, WRAP).apply { bottomMargin = dp(4); marginEnd = dp(54) })

        attachRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        attachStrip = HorizontalScrollView(this).apply { addView(attachRow); isHorizontalScrollBarEnabled = false; visibility = View.GONE }
        composer.addView(attachStrip, lp(MATCH, WRAP))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val fieldBox = FrameLayout(this)
        inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
            background = Ui.round(T.surface, dpf(24f)); elevation = dpf(1f)
            minimumHeight = dp(48)
        }
        inputRow.addView(Ui.iconButton(this, R.drawable.ic_bookmark, tr("תבניות ופקודות", "Templates & commands"), T.muted) { showTemplates() }.apply { layoutParams = lp(dp(44), dp(48)) })
        input = EditText(this).apply {
            hint = tr("הודעה", "Message")
            background = null
            setPadding(0, dp(12), dp(4), dp(12))
            setTextColor(T.text); setHintTextColor(T.faint)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, T.fs(16.5f))
            maxLines = 6
            if (Settings.enterSends(this@ChatActivity)) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_SEND
                setOnEditorActionListener { _, id, ev -> if (id == EditorInfo.IME_ACTION_SEND || ev?.keyCode == KeyEvent.KEYCODE_ENTER) { onSendClick(); true } else false }
            } else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) { updateSuggestions(e?.toString().orEmpty()); refreshSendButton() }
            })
        }
        inputRow.addView(input, lp(0, WRAP, 1f))
        inputRow.addView(Ui.iconButton(this, R.drawable.ic_chat_attach, tr("צירוף", "Attach"), T.muted) { attachSheet() }.apply { layoutParams = lp(dp(42), dp(48)) })
        cameraBtn = Ui.iconButton(this, R.drawable.ic_camera, tr("מצלמה", "Camera"), T.muted) { openCamera() }.apply { layoutParams = lp(dp(44), dp(48)) }
        inputRow.addView(cameraBtn)
        fieldBox.addView(inputRow, flp(MATCH, WRAP))

        voicePanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(T.surface, dpf(24f)); elevation = dpf(1f)
            minimumHeight = dp(48); visibility = View.GONE
            setPadding(dp(16), 0, dp(8), 0)
        }
        voiceDot = View(this).apply { background = Ui.circle(T.danger) }
        voicePanel.addView(voiceDot, lp(dp(10), dp(10)).apply { marginEnd = dp(10) })
        voiceTime = Ui.text(this, "0:00", 16f, T.text)
        voicePanel.addView(voiceTime, lp(WRAP, WRAP).apply { marginEnd = dp(12) })
        voiceHint = Ui.text(this, "", 14f, T.muted, lines = 1).apply { gravity = Gravity.CENTER }
        voicePanel.addView(voiceHint, lp(0, WRAP, 1f))
        voiceLock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        voiceLock.addView(Ui.iconButton(this, R.drawable.ic_delete, tr("מחיקת ההקלטה", "Delete recording"), T.danger) { cancelRecording() })
        voicePanel.addView(voiceLock)
        fieldBox.addView(voicePanel, flp(MATCH, dp(48), Gravity.BOTTOM))
        bar.addView(fieldBox, lp(0, WRAP, 1f))

        sendBtn = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            background = Ui.ripple(Ui.circle(T.primary), dpf(24f))
            elevation = dpf(2f)
            setColorFilter(T.onPrimary)
            setOnClickListener { onSendClick() }
            setOnTouchListener { v, e -> micTouch(v, e) }
        }
        bar.addView(sendBtn, lp(dp(48), dp(48)).apply { marginStart = dp(6) })
        composer.addView(bar, lp(MATCH, WRAP))
    }

    // ---- banners & status -----------------------------------------------------------------------

    private fun setStatus(text: String, color: Int = T.muted) { statusView.text = text; statusView.setTextColor(color) }

    private fun showBanner(text: String?, button: String? = null, action: (() -> Unit)? = null) {
        if (text == null) { banner.visibility = View.GONE; return }
        bannerText.text = text
        bannerButton.visibility = if (button != null) View.VISIBLE else View.GONE
        bannerButton.text = button ?: ""
        bannerButton.setOnClickListener { action?.invoke() }
        banner.visibility = View.VISIBLE
    }

    private fun refreshStatus() {
        when {
            !hubReady -> return
            waitingApproval -> setStatus(tr("מחכה לאישור שלך", "Waiting for your approval"), T.warning)
            busy && busyLabel != null -> setStatus(tr("עובד: ", "Working: ") + busyLabel, T.primary)
            busy -> setStatus(tr("מקליד…", "typing…"), T.primary)
            !authOk -> setStatus(tr("לא מחובר לחשבון", "Signed out"), T.danger)
            else -> setStatus(tr("מחובר", "online"))
        }
    }

    // ---- connection -----------------------------------------------------------------------------

    private fun connect() {
        if (!AgyInstaller.isInstalled(this)) {
            showBanner(tr("הסוכן עוד לא מותקן בטלפון.", "The agent is not installed yet."), tr("להתקנה", "Install")) {
                startActivity(Intent(this, SetupActivity::class.java))
            }
            setStatus(tr("לא מחובר", "offline")); return
        }
        showBanner(tr("מפעיל את הסוכן…", "Starting the agent…"))
        HubClient.ensureRunning(this) { ok ->
            hubReady = ok
            if (!ok) {
                setStatus(tr("לא מחובר", "offline"), T.danger)
                showBanner(tr("הסוכן לא עלה.", "The agent did not start."), tr("פרטים", "Details")) {
                    startActivity(Intent(this, TerminalActivity::class.java))
                }
                return@ensureRunning
            }
            showBanner(null)
            ChatWatcher.start(this)
            io.execute { ChatEngine.loadModels() }
            checkAuth()
            schedulePoll(0)
        }
    }

    private fun checkAuth() {
        io.execute {
            val ok = runCatching { HubClient.hasValidAuth() }.getOrElse { true }
            main.post {
                authOk = ok
                if (ok) { showBanner(null); refreshStatus() }
                else {
                    refreshStatus()
                    showBanner(tr("צריך להתחבר עם חשבון Google.", "Sign in with Google to chat."), tr("התחברות", "Sign in")) { signIn() }
                }
            }
        }
    }

    private fun signIn() {
        showBanner(tr("ממתין להתחברות ב־Chrome…", "Waiting for sign-in in Chrome…"))
        Thread { runCatching { HubClient.login() } }.start()
        io.execute {
            for (i in 0 until 150) {
                if (runCatching { HubClient.hasValidAuth() }.getOrDefault(false)) {
                    main.post { authOk = true; showBanner(null); refreshStatus(); snack(tr("מחובר!", "Signed in!")) }
                    return@execute
                }
                Thread.sleep(2000)
            }
            main.post { checkAuth() }
        }
    }

    // ---- polling --------------------------------------------------------------------------------

    private fun schedulePoll(delay: Long) {
        if (polling) return
        polling = true
        main.postDelayed({ polling = false; poll() }, delay)
    }

    private fun poll() {
        if (!resumed || isFinishing) return
        if (!hubReady || !ChatStore.isStarted(this, chat)) { schedulePoll(POLL_MS); return }
        io.execute {
            val r = runCatching { ChatEngine.fetch(this, chat) }
            r.getOrNull()?.let { ChatEngine.apply(this, chat.id, it, viewing = true) }
            main.post {
                r.onSuccess { onResult(it) }.onFailure { if (!busy) setStatus(tr("מתחבר מחדש…", "Reconnecting…")) }
                schedulePoll(if (busy) POLL_MS else POLL_MS * 2)
            }
        }
    }

    private fun onResult(r: StepMapper.Result) {
        val oldAgents = items.count { it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA }
        items = r.items
        val users = r.items.count { it.kind == ChatItem.USER }
        pending.removeAll { users > it.userCount }
        busy = r.busy || pending.isNotEmpty() || sending
        busyLabel = r.busyLabel
        waitingApproval = r.waiting
        refreshStatus()
        val newAgents = r.items.count { it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA } - oldAgents
        if (newAgents > 0 && !atBottom()) newWhileUp += newAgents
        refreshSendButton()
        render()
    }

    // ---- rows -----------------------------------------------------------------------------------

    private class Row(val type: Int, val key: String, val item: ChatItem? = null, val label: String = "",
                      val tools: List<ChatItem> = emptyList(), val pending: Pending? = null, val tail: Boolean = true)

    private object RT { const val DATE = 0; const val OUT = 1; const val IN = 2; const val TOOLS = 3; const val ERROR = 4
        const val APPROVAL = 5; const val TYPING = 6; const val UNREAD = 7; const val PENDING = 8 }

    private fun buildRows(): List<Row> {
        val out = mutableListOf<Row>()
        var lastDay = ""
        var agentOrdinal = 0
        var prevSide = -1
        var i = 0
        val all = items
        while (i < all.size) {
            val it = all[i]
            val isAgent = it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA
            val ordinal = if (isAgent) agentOrdinal++ else -1
            if (it.key in meta.hidden) { i++; continue }
            if (it.millis > 0) {
                val day = dayLabel(it.millis)
                if (day != lastDay) { out += Row(RT.DATE, "date:$day", label = day); lastDay = day; prevSide = -1 }
            }
            if (isAgent && unreadAtOpen > 0 && ordinal == unreadFrom) { out += Row(RT.UNREAD, "unread"); prevSide = -1 }
            when (it.kind) {
                ChatItem.TOOL -> {
                    val group = mutableListOf(it)
                    var j = i + 1
                    while (j < all.size && all[j].kind == ChatItem.TOOL) { if (all[j].key !in meta.hidden) group += all[j]; j++ }
                    out += Row(RT.TOOLS, "tools:${it.stepIndex}", tools = group)
                    i = j; prevSide = -1; continue
                }
                ChatItem.USER -> { out += Row(RT.OUT, it.key, it, tail = prevSide != 1); prevSide = 1 }
                ChatItem.AGENT, ChatItem.MEDIA -> { out += Row(RT.IN, it.key, it, tail = prevSide != 2); prevSide = 2 }
                ChatItem.ERROR -> { out += Row(RT.ERROR, it.key, it); prevSide = -1 }
                ChatItem.APPROVAL -> { out += Row(RT.APPROVAL, it.key, it); prevSide = -1 }
            }
            i++
        }
        pending.forEachIndexed { idx, p -> out += Row(RT.PENDING, "pending:$idx:${p.text.hashCode()}", pending = p, tail = prevSide != 1); prevSide = 1 }
        if (busy) out += Row(RT.TYPING, "typing")
        return out
    }

    private fun sigOf(r: Row): String {
        val it = r.item
        val sb = StringBuilder(r.key).append('|').append(r.tail)
        if (it != null) sb.append('|').append(it.text.hashCode()).append('|').append(it.media.size).append('|').append(it.time)
        if (r.type == RT.OUT) sb.append('|').append(replied(it!!))
        if (r.type == RT.TOOLS) sb.append('|').append(r.tools.joinToString(",") { t -> t.text.hashCode().toString() }).append(r.key in expanded)
        if (r.type == RT.TYPING) sb.append(busyLabel)
        if (r.type == RT.UNREAD) sb.append(unreadAtOpen)
        sb.append('|').append(r.key in selected).append(meta.stars.containsKey(r.key)).append(meta.pins.contains(r.key))
            .append(searchQuery).append(r.key == flashKey)
        return sb.toString()
    }

    private fun replied(item: ChatItem): Boolean {
        val idx = items.indexOf(item)
        return idx >= 0 && items.drop(idx + 1).any { it.kind == ChatItem.AGENT || it.kind == ChatItem.MEDIA }
    }

    private fun render() {
        val newRows = buildRows()
        val sig = newRows.joinToString("\n") { sigOf(it) }
        refreshPinBanner()
        if (sig == lastSig) return
        lastSig = sig
        val wasBottom = atBottom()
        rows = newRows
        val keep = rows.map { sigOf(it) }.toSet()
        viewCache.keys.retainAll(keep)
        adapter.notifyDataSetChanged()
        if (scrollKey != null) {
            val idx = rows.indexOfFirst { it.key == scrollKey }
            if (idx >= 0) {
                flashKey = scrollKey; scrollKey = null
                list.post { list.setSelectionFromTop(idx, dp(120)); flash() }
            }
        } else if (wasBottom || rows.lastOrNull()?.type == RT.PENDING) scrollToBottom(false)
        updateDownFab()
    }

    private fun flash() {
        main.postDelayed({ flashKey = null; lastSig = ""; render() }, 1600)
        lastSig = ""; render()
    }

    private fun atBottom(): Boolean {
        if (list.childCount == 0) return true
        val last = list.lastVisiblePosition
        return last >= adapter.count - 1 && (list.getChildAt(list.childCount - 1)?.bottom ?: 0) <= list.height - list.paddingBottom + dp(40)
    }

    private fun scrollToBottom(smooth: Boolean) {
        newWhileUp = 0
        list.post { if (smooth && adapter.count < 60) list.smoothScrollToPosition(adapter.count - 1) else list.setSelection(adapter.count - 1) }
    }

    private fun updateDownFab() {
        val show = !atBottom()
        downFab.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) newWhileUp = 0
        downBadge.visibility = if (show && newWhileUp > 0) View.VISIBLE else View.GONE
        downBadge.text = newWhileUp.toString()
    }

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 1
        override fun getItemViewType(p: Int) = AbsListView.ITEM_VIEW_TYPE_IGNORE
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            val r = rows[p]
            val sig = sigOf(r)
            return viewCache.getOrPut(sig) { buildRow(r) }.also { (it.parent as? ViewGroup)?.let { g -> if (g !== parent) g.removeView(it) } }
        }
    }

    // ---- row views ------------------------------------------------------------------------------

    private fun dayLabel(ms: Long): String {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = ms
        val d = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        return when (today - d) {
            0 -> tr("היום", "Today")
            1 -> tr("אתמול", "Yesterday")
            else -> DateFormat.getMediumDateFormat(this).format(Date(ms))
        }
    }

    private fun buildRow(r: Row): View = when (r.type) {
        RT.DATE -> centered(Ui.text(this, r.label, 12.5f, T.muted, bold = true).apply {
            background = Ui.round(T.surface, dpf(10f)); elevation = dpf(0.5f)
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }, top = 10, bottom = 6)
        RT.UNREAD -> FrameLayout(this).apply {
            setBackgroundColor(T.primarySoft)
            addView(Ui.text(this@ChatActivity, if (unreadAtOpen == 1) tr("הודעה אחת שלא נקראה", "1 unread message") else tr("$unreadAtOpen הודעות שלא נקראו", "$unreadAtOpen unread messages"),
                13f, T.primary, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6)) }, flp(MATCH, WRAP))
            layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
            setPadding(0, dp(6), 0, dp(6))
        }
        RT.OUT, RT.IN, RT.PENDING -> bubbleRow(r)
        RT.TOOLS -> toolsRow(r)
        RT.ERROR -> centered(chip(R.drawable.ic_warning, r.item!!.text, T.errorBg, T.danger) { showError(r.item) }, 4, 4)
        RT.APPROVAL -> approvalRow(r.item!!)
        else -> typingRow()
    }

    private fun centered(v: View, top: Int, bottom: Int): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(top), dp(16), dp(bottom))
        addView(v)
        layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
    }

    private fun chip(icon: Int, text: String, bg: Int, tint: Int, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = Ui.ripple(Ui.round(bg, dpf(12f)), dpf(12f))
        setPadding(dp(12), dp(6), dp(12), dp(6))
        addView(Ui.icon(this@ChatActivity, icon, tint, 16), lp(dp(16), dp(16)).apply { marginEnd = dp(8) })
        addView(Ui.text(this@ChatActivity, text, 13f, T.text, lines = 3).apply { maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt() })
        setOnClickListener { onClick() }
    }

    private fun bubbleRow(r: Row): View {
        val item = r.item
        val outgoing = r.type != RT.IN
        val swipe = SwipeRow(this)
        swipe.layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (outgoing) Gravity.END else Gravity.START
            setPadding(dp(10), dp(if (r.tail) 6 else 2), dp(10), 0)
        }
        val key = r.key
        if (key in selected) line.setBackgroundColor(T.selection)
        if (key == flashKey) line.setBackgroundColor(T.selection)
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val color = if (outgoing) T.outBubble else T.inBubble
            background = Ui.ripple(bubbleBg(color, outgoing, r.tail), dpf(16f))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            elevation = dpf(0.6f)
        }
        val textColor = T.text
        val text = item?.text ?: r.pending!!.text
        val media = item?.media ?: r.pending!!.media
        var body = text
        // Quote block ("> …" lines at the start, as we send replies).
        if (body.startsWith("> ")) {
            val lines = body.split('\n')
            val quoted = lines.takeWhile { it.startsWith(">") }
            val rest = lines.drop(quoted.size).dropWhile { it.isBlank() }.joinToString("\n")
            bubble.addView(quoteBlock(quoted.joinToString("\n") { it.removePrefix(">").trimStart() }), lp(MATCH, WRAP).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            body = rest
        }
        val voice = if (outgoing) (r.pending?.voice ?: meta.voiceFor(text)) else null
        if (voice != null) bubble.addView(voiceView(voice), lp(dp(240), WRAP))
        if (media.isNotEmpty()) bubble.addView(mediaGrid(media, item), lp(WRAP, WRAP))
        if (body.isNotBlank()) {
            val content = Markdown.render(this, body, textColor, searchQuery, maxBubble - dp(24))
            content.setPadding(dp(8), dp(4), dp(8), 0)
            bubble.addView(content, lp(WRAP, WRAP))
            content.isClickable = false
        }
        // Footer: star, time, ticks.
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END; setPadding(dp(8), 0, dp(6), dp(2)) }
        if (meta.stars.containsKey(key)) footer.addView(Ui.icon(this, R.drawable.ic_star, T.muted, 12), lp(dp(12), dp(12)).apply { marginEnd = dp(4) })
        if (meta.pins.contains(key)) footer.addView(Ui.icon(this, R.drawable.ic_pin, T.muted, 12), lp(dp(12), dp(12)).apply { marginEnd = dp(4) })
        footer.addView(Ui.text(this, item?.time ?: "", 11.5f, T.muted))
        if (outgoing) {
            val tick = when {
                r.type == RT.PENDING -> Ui.icon(this, R.drawable.ic_clock, T.muted, 14)
                replied(item!!) -> Ui.icon(this, R.drawable.ic_done_all, T.tickRead, 16)
                else -> Ui.icon(this, R.drawable.ic_done_all, T.muted, 16)
            }
            footer.addView(tick, lp(dp(16), dp(16)).apply { marginStart = dp(4) })
        }
        bubble.addView(footer, lp(MATCH, WRAP))
        bubble.minimumWidth = dp(72)
        line.addView(bubble, lp(WRAP, WRAP))
        swipe.addView(line, flp(MATCH, WRAP))

        if (item != null) {
            bubble.setOnLongClickListener { toggleSelect(key); true }
            bubble.setOnClickListener {
                if (selected.isNotEmpty()) toggleSelect(key)
                else if (body.isNotBlank()) Ui.copy(this, Markdown.plain(body))
            }
            line.setOnClickListener { if (selected.isNotEmpty()) toggleSelect(key) }
            // Swipe toward the inside of the screen to reply.
            if (rtl) { swipe.onLeft = { startReply(item) }; swipe.leftHint = swipe.replyHint(fromLeft = false) }
            else { swipe.onRight = { startReply(item) }; swipe.rightHint = swipe.replyHint(fromLeft = true) }
        } else swipe.enabledSwipe = false
        return swipe
    }

    private fun bubbleBg(color: Int, outgoing: Boolean, tail: Boolean): android.graphics.drawable.Drawable {
        val r = dpf(16f); val t = dpf(4f)
        val sharpLeft = tail && (outgoing == rtl)
        val sharpRight = tail && !sharpLeft
        return Ui.roundCorners(color, if (sharpLeft) t else r, if (sharpRight) t else r, r, r)
    }

    private fun quoteBlock(text: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = Ui.round(if (T.dark) 0x22FFFFFF else 0x0F000000, dpf(10f))
        addView(View(this@ChatActivity).apply { background = Ui.round(T.primary, dpf(2f)) }, lp(dp(4), MATCH))
        val col = LinearLayout(this@ChatActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        col.addView(Ui.text(this@ChatActivity, "Antigravity", 13f, T.primary, bold = true))
        col.addView(Ui.text(this@ChatActivity, Markdown.plain(text), 13.5f, T.muted, lines = 3).apply { maxWidth = maxBubble - dp(40) })
        addView(col)
        setOnClickListener {
            val target = items.firstOrNull { i -> i.text.isNotBlank() && Markdown.plain(i.text).contains(Markdown.plain(text).take(40)) }
            target?.let { t -> val idx = rows.indexOfFirst { it.key == t.key }; if (idx >= 0) { flashKey = t.key; list.setSelectionFromTop(idx, dp(120)); flash() } }
        }
    }

    private fun mediaGrid(media: List<MediaRef>, item: ChatItem?): View {
        val w = maxBubble - dp(8)
        fun cell(m: MediaRef, width: Int, height: Int, extra: Int): View {
            val f = FrameLayout(this)
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Ui.round(T.surface3, dpf(12f))
                clipToOutline = true
            }
            f.addView(iv, flp(MATCH, MATCH))
            Media.thumb(iv, m.path, maxOf(width, height))
            if (m.isVideo) {
                val play = FrameLayout(this).apply { background = Ui.circle(0x88000000.toInt()) }
                play.addView(Ui.icon(this, R.drawable.ic_play, Color.WHITE, 30), flp(dp(30), dp(30), Gravity.CENTER))
                f.addView(play, flp(dp(52), dp(52), Gravity.CENTER))
                val dur = TextView(this).apply {
                    setTextColor(Color.WHITE); textSize = 12f; setPadding(dp(6), dp(2), dp(6), dp(2))
                    background = Ui.round(0x88000000.toInt(), dpf(8f))
                }
                f.addView(dur, flp(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), 0, 0, dp(6)) })
                io.execute { val d = Media.duration(m.path); main.post { dur.text = Media.clock(d) } }
            }
            if (extra > 0) f.addView(TextView(this).apply {
                text = "+$extra"; setTextColor(Color.WHITE); textSize = 24f; gravity = Gravity.CENTER
                background = Ui.round(0x77000000, dpf(12f))
            }, flp(MATCH, MATCH))
            f.setOnClickListener {
                if (selected.isNotEmpty() && item != null) { toggleSelect(item.key); return@setOnClickListener }
                MediaViewerActivity.open(this, allMedia(), allMedia().indexOfFirst { it.path == m.path }.coerceAtLeast(0), forResult = true)
            }
            f.setOnLongClickListener { if (item != null) toggleSelect(item.key); true }
            f.layoutParams = LinearLayout.LayoutParams(width, height).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) }
            return f
        }
        if (media.size == 1) {
            val m = media[0]
            val (iw, ih) = Media.size(m.path)
            val ratio = (ih.toFloat() / iw.coerceAtLeast(1)).coerceIn(0.5f, 1.4f)
            return cell(m, w, (w * ratio).toInt(), 0)
        }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val shown = media.take(4)
        val cw = w / 2 - dp(2)
        shown.chunked(2).forEachIndexed { ri, pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { ci, m ->
                val extra = if (ri == 1 && ci == 1 && media.size > 4) media.size - 4 else 0
                row.addView(cell(m, if (pair.size == 1) w else cw, cw, extra))
            }
            grid.addView(row)
        }
        return grid
    }

    private fun allMedia(): List<MediaRef> = items.flatMap { it.media }.distinctBy { it.path }

    private fun voiceView(path: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(8), dp(2))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val exists = File(path).isFile
        val play = ImageView(this).apply {
            setImageResource(if (exists) R.drawable.ic_play else R.drawable.ic_mic)
            setColorFilter(T.onPrimary); scaleType = ImageView.ScaleType.CENTER
            background = Ui.ripple(Ui.circle(T.primary), dpf(20f))
        }
        row.addView(play, lp(dp(40), dp(40)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        val bar = ProgressLine(this).apply { fraction = 0f }
        col.addView(bar, lp(MATCH, dp(5)))
        val time = Ui.text(this, if (exists) "…" else tr("הכתבה", "Dictation"), 12f, T.muted)
        col.addView(time, lp(WRAP, WRAP).apply { topMargin = dp(4) })
        row.addView(col, lp(0, WRAP, 1f))
        if (exists) {
            io.execute { val d = Media.duration(path); main.post { if (!VoicePlayer.isPlaying || VoicePlayer.currentPath != path) time.text = Media.clock(d) } }
            play.setOnClickListener {
                VoicePlayer.toggle(path) {
                    val mine = VoicePlayer.currentPath == path
                    play.setImageResource(if (mine && VoicePlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                    if (mine && VoicePlayer.duration > 0) {
                        bar.fraction = VoicePlayer.position.toFloat() / VoicePlayer.duration
                        time.text = Media.clock(VoicePlayer.position.toLong())
                    } else { bar.fraction = 0f }
                }
            }
        }
        return row
    }

    private fun toolsRow(r: Row): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
        }
        val open = r.key in expanded
        val running = r.tools.lastOrNull()?.text?.endsWith("…") == true
        val label = if (r.tools.size == 1) r.tools[0].text
            else (if (running) tr("עובד… ", "Working… ") else "") + tr("בוצעו ${r.tools.size} פעולות", "${r.tools.size} actions")
        val head = chip(if (r.tools.size == 1) StepMapper.toolIcon(r.tools[0].step) else R.drawable.ic_list, label, T.toolBg, T.primary) {
            if (r.tools.size == 1) showDetails(r.tools[0])
            else { if (open) expanded -= r.key else expanded += r.key; lastSig = ""; render() }
        }
        if (r.tools.size > 1) (head as LinearLayout).addView(Ui.icon(this, if (open) R.drawable.ic_up else R.drawable.ic_expand, T.muted, 18), lp(dp(18), dp(18)).apply { marginStart = dp(6) })
        box.addView(head)
        if (open && r.tools.size > 1) {
            val listBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.round(T.surface, dpf(14f)); elevation = dpf(0.5f)
                setPadding(0, dp(4), 0, dp(4))
            }
            r.tools.forEach { t ->
                listBox.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = Ui.ripple(null)
                    addView(Ui.icon(this@ChatActivity, StepMapper.toolIcon(t.step), T.muted, 18), lp(dp(18), dp(18)).apply { marginEnd = dp(10) })
                    addView(Ui.text(this@ChatActivity, t.text, 13.5f, T.text, lines = 2), lp(0, WRAP, 1f))
                    addView(Ui.text(this@ChatActivity, t.time, 11.5f, T.faint))
                    setOnClickListener { showDetails(t) }
                }, lp(MATCH, WRAP))
            }
            box.addView(listBox, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        }
        return box
    }

    private fun approvalRow(item: ChatItem): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(T.surface, dpf(18f), T.warning, dp(1)); elevation = dpf(1f)
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ui.icon(this, R.drawable.ic_shield, T.warning, 22), lp(dp(22), dp(22)).apply { marginEnd = dp(10) })
        head.addView(Ui.text(this, tr("הסוכן מבקש אישור", "The agent asks for approval"), 16f, T.text, bold = true))
        box.addView(head)
        if (item.text.isNotBlank()) box.addView(Ui.text(this, item.text, 14f, T.muted).apply {
            setPadding(0, dp(8), 0, dp(10)); typeface = Typeface.MONOSPACE; textDirection = View.TEXT_DIRECTION_LTR
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Ui.button(this, tr("אישור", "Allow"), Ui.Style.PRIMARY, R.drawable.ic_check) { answer(item, true) }, lp(0, WRAP, 1f))
        buttons.addView(View(this), lp(dp(10), 1))
        buttons.addView(Ui.button(this, tr("דחייה", "Deny"), Ui.Style.SECONDARY, R.drawable.ic_close) { answer(item, false) }, lp(0, WRAP, 1f))
        box.addView(buttons)
        return LinearLayout(this).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addView(box, lp(MATCH, WRAP))
            layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
        }
    }

    private fun typingRow(): View {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(4))
            layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
        }
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = bubbleBg(T.inBubble, false, true); elevation = dpf(0.6f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(TypingDots(this@ChatActivity))
        }
        line.addView(b)
        return line
    }

    // ---- selection ------------------------------------------------------------------------------

    private fun toggleSelect(key: String) {
        if (!selected.remove(key)) selected += key
        Ui.haptic(list)
        refreshSelection()
        lastSig = ""; render()
    }

    private fun clearSelection() { selected.clear(); refreshSelection(); lastSig = ""; render() }

    private fun selectedItems(): List<ChatItem> = items.filter { it.key in selected }

    private fun refreshSelection() {
        val on = selected.isNotEmpty()
        selectionBar.visibility = if (on) View.VISIBLE else View.GONE
        header.visibility = if (on || searchBar.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        if (!on) return
        selectionCount.text = selected.size.toString()
        selectionActions.removeAllViews()
        val sel = selectedItems()
        val one = sel.singleOrNull()
        fun act(icon: Int, label: String, f: (View) -> Unit) = selectionActions.addView(Ui.iconButton(this, icon, label) { f(it) })
        if (one != null) act(R.drawable.ic_reply, tr("השב", "Reply")) { startReply(one); clearSelection() }
        val allStarred = sel.all { meta.stars.containsKey(it.key) }
        act(if (allStarred) R.drawable.ic_star_outline else R.drawable.ic_star, if (allStarred) tr("הסר כוכב", "Unstar") else tr("כוכב", "Star")) { star(sel, !allStarred) }
        if (one != null) act(R.drawable.ic_pin, if (meta.pins.contains(one.key)) tr("ביטול נעיצה", "Unpin") else tr("נעיצה", "Pin")) { togglePin(one) }
        act(R.drawable.ic_copy, tr("העתקה", "Copy")) { Ui.copy(this, textOf(sel)); clearSelection() }
        act(R.drawable.ic_forward, tr("העברה", "Forward")) { forward(textOf(sel)) }
        act(R.drawable.ic_more, tr("עוד", "More")) { anchor ->
            val m = Menu(this)
            m.item(R.drawable.ic_share, tr("שיתוף", "Share")) { shareText(textOf(sel)); clearSelection() }
            if (one != null) {
                m.item(R.drawable.ic_info, tr("פרטי הודעה", "Message info")) { messageInfo(one) }
                m.item(R.drawable.ic_select, tr("בחירת טקסט", "Select text")) { Ui.info(this, null, Markdown.plain(one.text)); clearSelection() }
                if (one.kind == ChatItem.USER) {
                    m.item(R.drawable.ic_edit, tr("עריכה ושליחה מחדש", "Edit & resend")) { input.setText(stripQuote(one.text)); input.setSelection(input.text.length); showKeyboard(input); clearSelection() }
                    m.item(R.drawable.ic_refresh, tr("שליחה מחדש", "Send again")) { clearSelection(); input.setText(stripQuote(one.text)); onSendClick() }
                }
                val media = one.media
                if (media.isNotEmpty()) m.item(R.drawable.ic_save, tr("שמירה לגלריה", "Save to gallery")) {
                    io.execute { val ok = media.all { Media.saveToGallery(this, it.path, it.mime) }; main.post { snack(if (ok) tr("נשמר בגלריה", "Saved to gallery") else tr("השמירה נכשלה", "Saving failed")) } }
                    clearSelection()
                }
            }
            m.item(R.drawable.ic_delete, tr("מחיקה אצלי", "Delete for me"), danger = true) { hide(sel) }
            m.show(anchor)
        }
    }

    private fun stripQuote(t: String) = t.split('\n').dropWhile { it.startsWith(">") }.dropWhile { it.isBlank() }.joinToString("\n")

    private fun textOf(sel: List<ChatItem>): String = if (sel.size == 1) Markdown.plain(sel[0].text)
        else sel.joinToString("\n\n") { (if (it.kind == ChatItem.USER) tr("אני", "Me") else "Antigravity") + " [" + it.time + "]: " + Markdown.plain(it.text) }

    private fun star(sel: List<ChatItem>, on: Boolean) {
        sel.forEach { if (on) meta.stars[it.key] = MsgMeta.Star(it.key, Markdown.plain(it.text).ifBlank { "📷" }, it.millis, it.kind) else meta.stars.remove(it.key) }
        meta.save(); clearSelection()
        snack(if (on) tr("סומן בכוכב", "Starred") else tr("הכוכב הוסר", "Unstarred"))
    }

    private fun togglePin(item: ChatItem) {
        if (!meta.pins.remove(item.key)) {
            if (meta.pins.size >= 3) meta.pins.removeAt(0)
            meta.pins += item.key
            snack(tr("ההודעה ננעצה", "Message pinned"))
        }
        meta.save(); clearSelection()
    }

    private fun hide(sel: List<ChatItem>) {
        val keys = sel.map { it.key }
        meta.hidden += keys; meta.save(); clearSelection()
        snack(tr("נמחק מהתצוגה", "Deleted for you"), tr("ביטול", "Undo")) { meta.hidden -= keys.toSet(); meta.save(); lastSig = ""; render() }
    }

    private fun shareText(text: String) =
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("שיתוף", "Share")))

    private fun forward(text: String) {
        clearSelection()
        val sheet = Sheet(this).title(tr("העברה אל…", "Forward to…"))
        sheet.item(R.drawable.ic_add, tr("שיחה חדשה", "New chat")) {
            val c = ChatStore.create(this); ChatActivity.open(this, c.id, text = text)
        }
        ChatStore.all(this).filter { !it.archived && it.id != chat.id }.take(30).forEach { c ->
            sheet.item(R.drawable.ic_chat, c.name(this), c.preview.take(60)) {
                ChatStore.edit(this, c.id) { it.draft = (it.draft + "\n" + text).trim() }
                ChatActivity.open(this, c.id)
            }
        }
        sheet.show()
    }

    private fun messageInfo(item: ChatItem) {
        clearSelection()
        val idx = items.indexOf(item)
        val after = items.drop(idx + 1)
        val answer = if (item.kind == ChatItem.USER) after.firstOrNull { it.kind == ChatItem.AGENT } else null
        val tools = if (item.kind == ChatItem.USER) after.takeWhile { it.kind != ChatItem.USER }.count { it.kind == ChatItem.TOOL } else 0
        val full = if (item.millis > 0) DateFormat.getLongDateFormat(this).format(Date(item.millis)) + " " + item.time else item.time
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun line(icon: Int, title: String, value: String) = box.addView(Ui.row(this, icon, title, value).apply { setPadding(0, dp(6), 0, dp(6)) })
        line(R.drawable.ic_clock, if (item.kind == ChatItem.USER) tr("נשלחה", "Sent") else tr("התקבלה", "Received"), full)
        if (answer != null) line(R.drawable.ic_done_all, tr("נענתה", "Answered"), answer.time)
        if (item.kind == ChatItem.USER) line(R.drawable.ic_list, tr("פעולות שבוצעו", "Actions taken"), tools.toString())
        line(R.drawable.ic_sparkle, tr("מודל", "Model"), ChatEngine.currentModel(this)?.name ?: "—")
        line(R.drawable.ic_file, tr("אורך", "Length"), tr("${item.text.length} תווים", "${item.text.length} characters"))
        Dlg(this).title(tr("פרטי הודעה", "Message info")).view(box).positive(tr("סגירה", "Close")) {}.show()
    }

    // ---- pinned ---------------------------------------------------------------------------------

    private fun refreshPinBanner() {
        val pinned = meta.pins.mapNotNull { k -> items.firstOrNull { it.key == k } }
        if (pinned.isEmpty()) { pinBanner.visibility = View.GONE; return }
        pinIndex = pinIndex.coerceIn(0, pinned.size - 1)
        val it = pinned[pinIndex]
        pinText.text = (if (pinned.size > 1) "${pinIndex + 1}/${pinned.size}  " else "") + Markdown.plain(it.text).replace('\n', ' ').ifBlank { "📷" }
        pinBanner.visibility = View.VISIBLE
    }

    private fun jumpToPinned() {
        val pinned = meta.pins.mapNotNull { k -> items.firstOrNull { it.key == k } }
        if (pinned.isEmpty()) return
        val target = pinned[pinIndex.coerceIn(0, pinned.size - 1)]
        val idx = rows.indexOfFirst { it.key == target.key }
        if (idx >= 0) { flashKey = target.key; list.setSelectionFromTop(idx, dp(120)); flash() }
        pinIndex = (pinIndex + 1) % pinned.size
        refreshPinBanner()
    }

    private fun pinnedMenu(anchor: View) {
        Menu(this)
            .item(R.drawable.ic_pin, tr("ביטול כל הנעיצות", "Unpin all")) { meta.pins.clear(); meta.save(); lastSig = ""; render() }
            .show(anchor)
    }

    // ---- search ---------------------------------------------------------------------------------

    private fun openSearch() {
        searchBar.visibility = View.VISIBLE; header.visibility = View.INVISIBLE
        searchField.setText(searchQuery ?: "")
        showKeyboard(searchField)
    }

    private fun closeSearch() {
        searchBar.visibility = View.GONE; header.visibility = View.VISIBLE
        searchQuery = null; searchHits = emptyList(); searchPos = -1
        hideKeyboard(searchField)
        lastSig = ""; render()
    }

    private fun runSearch(q: String) {
        searchQuery = q.trim().ifEmpty { null }
        searchHits = if (searchQuery == null) emptyList() else rows.indices.filter { i ->
            val r = rows[i]
            (r.item?.text?.contains(searchQuery!!, true) == true) || r.tools.any { it.text.contains(searchQuery!!, true) }
        }
        searchPos = searchHits.size - 1
        searchCount.text = if (searchQuery == null) "" else if (searchHits.isEmpty()) "0" else "${searchHits.size - searchPos}/${searchHits.size}"
        lastSig = ""; render()
        if (searchPos >= 0) list.setSelectionFromTop(searchHits[searchPos], dp(100))
    }

    private fun stepSearch(d: Int) {
        if (searchHits.isEmpty()) return
        searchPos = (searchPos + if (d < 0) -1 else 1).coerceIn(0, searchHits.size - 1)
        searchCount.text = "${searchHits.size - searchPos}/${searchHits.size}"
        list.setSelectionFromTop(searchHits[searchPos], dp(100))
    }

    // ---- composer -------------------------------------------------------------------------------

    private fun startReply(item: ChatItem) {
        quote = item
        Ui.haptic(list)
        refreshComposer()
        showKeyboard(input)
    }

    private fun refreshComposer() {
        val q = quote
        if (q != null) {
            quotePanel.visibility = View.VISIBLE
            quoteTitle.text = if (q.kind == ChatItem.USER) tr("אני", "You") else "Antigravity"
            quoteText.text = Markdown.plain(q.text).replace('\n', ' ').ifBlank { if (q.media.isNotEmpty()) "📷 " + tr("תמונה", "Photo") else "" }
        } else quotePanel.visibility = View.GONE
        attachRow.removeAllViews()
        activeSlash?.let { c -> attachRow.addView(attachChip(R.drawable.ic_bolt, "/" + c.title) { activeSlash = null; refreshComposer() }) }
        pendingImages.forEachIndexed { i, (_, f) ->
            val cell = FrameLayout(this)
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = Ui.round(T.surface3, dpf(12f)); clipToOutline = true }
            Media.thumb(iv, f.absolutePath, dp(72))
            cell.addView(iv, flp(dp(72), dp(72)))
            cell.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_close); setColorFilter(Color.WHITE); background = Ui.circle(0xAA000000.toInt())
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnClickListener { pendingImages.removeAt(i); refreshComposer() }
            }, flp(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(3), dp(3), 0) })
            attachRow.addView(cell, lp(dp(72), dp(72)).apply { marginEnd = dp(6) })
        }
        pendingFiles.forEachIndexed { i, f ->
            val icon = if (MediaRef.mimeOf(f.name)?.startsWith("video/") == true) R.drawable.ic_video else R.drawable.ic_file
            attachRow.addView(attachChip(icon, f.name) { pendingFiles.removeAt(i); refreshComposer() })
        }
        attachStrip.visibility = if (attachRow.childCount > 0) View.VISIBLE else View.GONE
        refreshSendButton()
    }

    private fun attachChip(icon: Int, label: String, onRemove: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = Ui.round(T.surface, dpf(16f)); elevation = dpf(1f)
        setPadding(dp(10), dp(6), dp(4), dp(6))
        addView(Ui.icon(this@ChatActivity, icon, T.primary, 18), lp(dp(18), dp(18)).apply { marginEnd = dp(6) })
        addView(Ui.text(this@ChatActivity, label, 13.5f, T.text, lines = 1).apply { maxWidth = dp(180) })
        addView(ImageView(this@ChatActivity).apply {
            setImageResource(R.drawable.ic_close); setColorFilter(T.muted); setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { onRemove() }
        }, lp(dp(26), dp(26)))
        layoutParams = lp(WRAP, WRAP).apply { marginEnd = dp(6); topMargin = dp(20) }
    }

    private val hasContent get() = input.text.isNotBlank() || activeSlash != null || pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()

    private fun refreshSendButton() {
        val (icon, label) = when {
            hasContent -> R.drawable.ic_send to tr("שליחה", "Send")
            busy && !sending -> R.drawable.ic_stop to tr("עצירת הסוכן", "Stop the agent")
            else -> R.drawable.ic_mic to tr("החזיקו להקלטה", "Hold to record")
        }
        sendBtn.setImageResource(icon)
        sendBtn.contentDescription = label
        cameraBtn.visibility = if (input.text.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onSendClick() {
        if (recorder != null) { if (voiceLocked) finishRecording(); return }
        if (!hasContent) {
            if (busy) stop() else snack(tr("החזיקו את המיקרופון כדי להקליט", "Hold the microphone to record"))
            return
        }
        send(input.text.toString().trim(), voice = null)
    }

    private fun send(typed: String, voice: String?) {
        if (!hubReady) { snack(tr("הסוכן עוד לא מוכן", "The agent is not ready yet")); return }
        val q = quote
        val fileNote = if (pendingFiles.isEmpty()) "" else pendingFiles.joinToString("\n", prefix = if (typed.isEmpty()) "" else "\n\n") { f ->
            tr("צירפתי קובץ: ", "Attached file: ") + f.absolutePath
        }
        val body = typed + fileNote
        val text = if (q != null && body.isNotEmpty()) Markdown.plain(q.text).ifBlank { "📷" }.lineSequence().take(6).joinToString("\n") { "> $it" } + "\n\n" + body else body
        val slash = activeSlash
        val images = pendingImages.toList()
        val files = pendingFiles.toList()
        input.setText(""); quote = null; activeSlash = null; pendingImages.clear(); pendingFiles.clear()
        suggestBox.visibility = View.GONE
        refreshComposer()
        val shown = buildString { if (slash != null) append("/").append(slash.info.optString("name")).append(if (text.isNotEmpty()) " " else ""); append(text) }.trim()
        val userCount = items.count { it.kind == ChatItem.USER }
        pending += Pending(shown, images.map { MediaRef(it.second.absolutePath, "image/jpeg") }, userCount, voice)
        if (voice != null) { meta.voice[text] = voice; meta.save() }
        sending = true; busy = true
        if (chat.title.isBlank()) {
            chat.title = shown.lineSequence().first().take(40).ifBlank { tr("תמונה", "Photo") }
            titleView.text = chat.title
            ChatStore.edit(this, chat.id) { it.title = chat.title }
        }
        ChatStore.edit(this, chat.id) { it.preview = shown.take(200).ifBlank { "📷" }; it.updated = System.currentTimeMillis(); it.draft = "" }
        lastSig = ""; render(); scrollToBottom(false)
        io.execute {
            val res = runCatching { ChatEngine.send(this, chat, text, slash?.info, images.map { "image/jpeg" to it.first }) }
            main.post {
                sending = false
                res.onFailure { e ->
                    pending.removeAll { it.text == shown }
                    input.setText(typed); quote = q; activeSlash = slash
                    pendingImages.addAll(images); pendingFiles.addAll(files)
                    refreshComposer()
                    Dlg(this).title(tr("השליחה נכשלה", "Sending failed")).message(e.message ?: e.toString())
                        .positive(tr("סגירה", "Close")) {}.show()
                    checkAuth()
                }
                lastSig = ""; render()
                schedulePoll(200)
            }
        }
    }

    private fun stop() {
        io.execute {
            runCatching { HubClient.cancel(chat.cascadeId) }
            main.post { snack(tr("הסוכן נעצר", "Agent stopped")); schedulePoll(200) }
        }
    }

    private fun answer(item: ChatItem, allow: Boolean) {
        val reply = StepMapper.interactionReply(item, allow)
        if (reply == null) { openVisualUi(); return }
        io.execute {
            val res = runCatching { HubClient.respond(chat.cascadeId, reply) }
            main.post {
                res.onFailure { snack(it.message ?: tr("שגיאה", "Error")) }
                schedulePoll(200)
            }
        }
    }

    // ---- slash commands & templates ------------------------------------------------------------

    private fun updateSuggestions(text: String) {
        if (activeSlash != null || !text.startsWith("/") || text.contains(' ') || text.contains('\n')) { suggestBox.visibility = View.GONE; return }
        if (slashCache.isEmpty() && hubReady) io.execute {
            val l = runCatching { HubClient.slashCommands(if (ChatStore.isStarted(this, chat)) chat.cascadeId else null, ChatEngine.currentModel(this)?.value, Settings.autoPolicy(this)) }.getOrDefault(emptyList())
            main.post { if (l.isNotEmpty()) { slashCache = l; updateSuggestions(input.text.toString()) } }
        }
        val q = text.removePrefix("/").lowercase()
        suggestions.removeAllViews()
        Settings.templates(this).filter { it.title.lowercase().contains(q) }.forEach { t ->
            suggestions.addView(suggestRow(R.drawable.ic_bookmark, t.title, t.text) { input.setText(t.text); input.setSelection(input.text.length); suggestBox.visibility = View.GONE })
        }
        slashCache.filter { it.title.lowercase().contains(q) || it.info.optString("name").lowercase().contains(q) }.take(12).forEach { c ->
            suggestions.addView(suggestRow(R.drawable.ic_bolt, "/" + c.title, c.description) { activeSlash = c; input.setText(""); suggestBox.visibility = View.GONE; refreshComposer() })
        }
        suggestBox.visibility = if (suggestions.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun suggestRow(icon: Int, title: String, sub: String, onClick: () -> Unit) =
        Ui.row(this, icon, title, sub.take(120), iconTint = T.primary) { onClick() }.apply { minimumHeight = dp(52) }

    private fun showTemplates() {
        val sheet = Sheet(this).title(tr("תבניות ופקודות", "Templates & commands"))
        Settings.templates(this).forEach { t ->
            sheet.item(R.drawable.ic_bookmark, t.title, t.text.take(80)) { input.setText(t.text); input.setSelection(input.text.length); showKeyboard(input) }
        }
        sheet.item(R.drawable.ic_bolt, tr("פקודות הסוכן (/)", "Agent commands (/)"), tr("הצגת הפקודות שהסוכן מכיר", "Show the commands the agent knows")) {
            input.setText("/"); input.setSelection(1); showKeyboard(input)
        }
        sheet.item(R.drawable.ic_edit, tr("ניהול תבניות", "Manage templates")) { startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, "templates")) }
        sheet.show()
    }

    // ---- attachments ----------------------------------------------------------------------------

    private fun attachSheet() {
        Sheet(this).grid(listOf(
            Sheet.Item(R.drawable.ic_image, tr("גלריה", "Gallery"), tint = 0xFF7C4DFF.toInt()) { pickGallery() },
            Sheet.Item(R.drawable.ic_camera, tr("מצלמה", "Camera"), tint = 0xFFE5484D.toInt()) { openCamera() },
            Sheet.Item(R.drawable.ic_video, tr("סרטון", "Video"), tint = 0xFFE38B00.toInt()) { pickVideo() },
            Sheet.Item(R.drawable.ic_file, tr("קובץ", "File"), tint = 0xFF1F6FEB.toInt()) { pickFile() },
            Sheet.Item(R.drawable.ic_bookmark, tr("תבנית", "Template"), tint = 0xFF1F9D63.toInt()) { showTemplates() },
        )).show()
    }

    private fun pickGallery() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*")).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        runCatching { startActivityForResult(i, REQ_GALLERY) }
    }

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        runCatching { startActivityForResult(i, REQ_GALLERY) }
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        runCatching { startActivityForResult(i, REQ_FILE) }
    }

    private fun openCamera() {
        val f = File(File(cacheDir, "camera").apply { mkdirs() }, "photo_${System.currentTimeMillis()}.jpg")
        cameraFile = f
        val uri = FilesProvider.uri(this, f)
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("photo", uri)
        runCatching { startActivityForResult(i, REQ_CAMERA) }.onFailure { snack(tr("אין אפליקציית מצלמה", "No camera app")) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            MediaViewerActivity.REQ -> {
                val path = data?.getStringExtra(MediaViewerActivity.EXTRA_REPLY_PATH) ?: return
                items.firstOrNull { it.media.any { m -> m.path == path } }?.let { startReply(it) }
            }
            REQ_CAMERA -> cameraFile?.takeIf { it.length() > 0 }?.let { addImage(Uri.fromFile(it)) }
            REQ_GALLERY, REQ_FILE -> {
                val uris = mutableListOf<Uri>()
                data?.clipData?.let { c -> for (i in 0 until c.itemCount) uris += c.getItemAt(i).uri }
                if (uris.isEmpty()) data?.data?.let { uris += it }
                uris.forEach { u ->
                    val type = contentResolver.getType(u).orEmpty()
                    if (requestCode == REQ_GALLERY && type.startsWith("image/") && !type.contains("gif")) addImage(u) else addFile(u)
                }
            }
            REQ_WALLPAPER -> data?.data?.let { u ->
                io.execute {
                    val f = File(Wallpapers.dir(this), "chat_${chat.id}.jpg")
                    runCatching { contentResolver.openInputStream(u)!!.use { i -> f.outputStream().use { i.copyTo(it) } } }
                    main.post { setWallpaper("image:" + f.absolutePath) }
                }
            }
        }
    }

    private fun addImage(u: Uri) {
        io.execute {
            val b64 = runCatching { Media.encodeImage(this, u) }.getOrNull()
            val f = b64?.let { runCatching { Media.saveSent(this, it) }.getOrNull() }
            main.post {
                if (b64 == null || f == null) snack(tr("לא הצלחתי לטעון את התמונה", "Could not load the picture"))
                else { pendingImages += b64 to f; refreshComposer() }
            }
        }
    }

    private fun addFile(u: Uri) {
        io.execute {
            val f = runCatching { Media.copyToInbox(this, u) }.getOrNull()
            main.post {
                if (f == null) snack(tr("לא הצלחתי לצרף את הקובץ", "Could not attach the file"))
                else { pendingFiles += f; refreshComposer() }
            }
        }
    }

    // ---- voice messages -------------------------------------------------------------------------

    private var recorder: VoiceRecorder? = null
    private var voiceLocked = false
    private var recStart = 0L
    private var downX = 0f; private var downY = 0f
    private var micDown = false

    private fun micTouch(v: View, e: MotionEvent): Boolean {
        if (hasContent || (busy && !sending && recorder == null)) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (recorder != null) return false
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC); return true
                }
                micDown = true; downX = e.rawX; downY = e.rawY
                startRecording()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!micDown || recorder == null || voiceLocked) return true
                val dx = e.rawX - downX; val dy = e.rawY - downY
                val towardStart = if (rtl) dx > 0 else dx < 0
                if (towardStart && abs(dx) > dp(120)) { cancelRecording(); micDown = false; return true }
                if (dy < -dp(80)) { lockRecording(); return true }
                v.translationX = if (towardStart) dx * 0.5f else 0f
                v.translationY = minOf(0f, dy * 0.4f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().translationX(0f).translationY(0f).setDuration(120).start()
                if (!micDown) return true
                micDown = false
                if (recorder != null && !voiceLocked) {
                    if (System.currentTimeMillis() - recStart < 600) { cancelRecording(); snack(tr("החזיקו את הכפתור כדי להקליט", "Hold the button to record")) }
                    else finishRecording()
                }
                return true
            }
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) snack(tr("אפשר להקליט עכשיו: החזיקו את המיקרופון", "You can record now: hold the microphone"))
    }

    private val recTicker = object : Runnable {
        override fun run() {
            if (recorder == null) return
            voiceTime.text = Media.clock(System.currentTimeMillis() - recStart)
            voiceDot.alpha = if ((System.currentTimeMillis() / 500) % 2 == 0L) 1f else 0.25f
            main.postDelayed(this, 250)
        }
    }

    private fun startRecording() {
        VoicePlayer.stop()
        voiceLocked = false
        recStart = System.currentTimeMillis()
        Ui.haptic(sendBtn)
        val rec = VoiceRecorder(this, object : VoiceRecorder.Listener {
            override fun onLevel(level: Float) { voiceDot.scaleX = 1f + level; voiceDot.scaleY = 1f + level }
            override fun onPartial(text: String) { if (voiceLocked || text.isNotBlank()) voiceHint.text = text }
            override fun onFinished(file: File?, transcript: String, durationMs: Long) = voiceDone(file, transcript)
            override fun onError(message: String) { snack(message); resetVoiceUi() ; recorder = null }
        })
        recorder = rec
        inputRow.visibility = View.INVISIBLE
        voicePanel.visibility = View.VISIBLE
        voiceLock.visibility = View.GONE
        voiceHint.text = if (rtl) tr("החליקו ימינה לביטול ›  ·  למעלה לנעילה", "‹ slide to cancel · up to lock") else tr("‹ החליקו לביטול  ·  למעלה לנעילה", "‹ slide to cancel · up to lock")
        main.post(recTicker)
        rec.start()
    }

    private fun lockRecording() {
        voiceLocked = true; micDown = false
        Ui.haptic(sendBtn)
        voiceLock.visibility = View.VISIBLE
        voiceHint.text = tr("מקליט… לחצו ➤ לשליחה", "Recording… tap ➤ to send")
        sendBtn.setImageResource(R.drawable.ic_send)
        sendBtn.animate().translationY(0f).translationX(0f).setDuration(120).start()
    }

    private fun finishRecording() {
        val r = recorder ?: return
        voiceHint.text = tr("מתמלל…", "Transcribing…")
        voiceLock.visibility = View.GONE
        r.stop()
    }

    private fun cancelRecording() {
        recorder?.cancel(); recorder = null
        resetVoiceUi()
    }

    private fun resetVoiceUi() {
        main.removeCallbacks(recTicker)
        voicePanel.visibility = View.GONE
        inputRow.visibility = View.VISIBLE
        voiceLocked = false
        refreshSendButton()
    }

    private fun voiceDone(file: File?, transcript: String) {
        recorder = null
        resetVoiceUi()
        if (transcript.isNotBlank() && Settings.voiceSendsNow(this)) { send(transcript, file?.absolutePath ?: ""); return }
        // Review: play back, fix the text, then send.
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (file != null) box.addView(voiceView(file.absolutePath), lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
        if (transcript.isBlank()) box.addView(Ui.text(this, tr("לא הצלחתי לתמלל. אפשר להקליד את הטקסט:", "Could not transcribe. You can type the text:"), 14f, T.muted).apply { setPadding(0, 0, 0, dp(8)) })
        val f = Ui.field(this, tr("מה נאמר בהקלטה", "What was said"), transcript, multiLine = true)
        box.addView(f)
        Dlg(this).title(tr("הודעה קולית", "Voice message")).view(box)
            .negative(tr("מחיקה", "Delete")) { file?.delete() }
            .positiveCheck(tr("שליחה", "Send")) {
                val t = f.text.toString().trim()
                if (t.isEmpty()) { snack(tr("צריך טקסט כדי שהסוכן יבין", "The agent needs text")); false }
                else { send(t, file?.absolutePath ?: ""); true }
            }.show()
    }

    // ---- menus & dialogs ------------------------------------------------------------------------

    private fun pickModel() {
        io.execute {
            val l = ChatEngine.loadModels()
            main.post {
                if (l.isEmpty()) { snack(tr("רשימת המודלים עוד לא זמינה", "Models are not available yet")); return@post }
                val cur = ChatEngine.currentModel(this)
                val sheet = Sheet(this).title(tr("מודל", "Model"))
                l.forEach { m ->
                    val sub = m.quota?.let { tr("נותרו ${(it * 100).toInt()}% מהמכסה", "${(it * 100).toInt()}% of quota left") }
                    sheet.item(R.drawable.ic_sparkle, m.name, sub, checked = m == cur) {
                        Settings.setModel(this, m.value.toString())
                        io.execute { HubClient.rememberModel(m.value) }
                        slashCache = emptyList()
                        snack(tr("המודל: ", "Model: ") + m.name)
                    }
                }
                sheet.show()
            }
        }
    }

    private fun showMenu(anchor: View) {
        val auto = Settings.autoRun(this)
        Menu(this)
            .item(R.drawable.ic_search, tr("חיפוש", "Search")) { openSearch() }
            .item(R.drawable.ic_info, tr("פרטי השיחה", "Chat info")) { ChatInfoActivity.open(this, chat.id) }
            .item(if (auto) R.drawable.ic_bolt else R.drawable.ic_shield, if (auto) tr("פקודות: רצות אוטומטית", "Commands: run automatically") else tr("פקודות: שואל לפני כל אחת", "Commands: ask before each")) {
                Settings.setAutoRun(this, !auto)
                snack(if (!auto) tr("מעכשיו פקודות ירוצו אוטומטית", "Commands now run automatically") else tr("מעכשיו הסוכן ישאל לפני כל פקודה", "The agent now asks before each command"))
            }
            .item(R.drawable.ic_wallpaper, tr("רקע לשיחה", "Chat wallpaper")) { wallpaperSheet() }
            .item(if (chat.muted) R.drawable.ic_bell else R.drawable.ic_mute, if (chat.muted) tr("ביטול השתקה", "Unmute") else tr("השתקה", "Mute")) {
                chat = ChatStore.edit(this, chat.id) { it.muted = !it.muted } ?: chat
                snack(if (chat.muted) tr("השיחה הושתקה", "Chat muted") else tr("ההתראות חזרו", "Notifications on"))
            }
            .item(R.drawable.ic_paste, tr("הדבקה", "Paste")) {
                val t = Ui.clipboard(this)
                if (t == null) snack(tr("הלוח ריק", "Clipboard is empty")) else { input.append(t); showKeyboard(input) }
            }
            .item(R.drawable.ic_share, tr("ייצוא השיחה", "Export chat")) { shareText(transcript()) }
            .item(R.drawable.ic_stop, tr("עצירת הסוכן", "Stop the agent"), enabled = busy) { stop() }
            .item(R.drawable.ic_globe, tr("ממשק גרפי מלא", "Full visual UI")) { openVisualUi() }
            .item(R.drawable.ic_account, tr("התחברות מחדש ל־Google", "Sign in again")) { signIn() }
            .item(R.drawable.ic_delete, tr("מחיקת השיחה", "Delete chat"), danger = true) {
                Ui.confirm(this, tr("למחוק את השיחה מהטלפון?", "Delete this chat from the phone?"), tr("מחיקה", "Delete"), danger = true) {
                    ChatStore.delete(this, chat.id); finish()
                }
            }
            .show(anchor)
    }

    private fun wallpaperSheet() = WallpaperPicker.show(this, chat.wallpaper, allowInherit = true,
        onPick = { setWallpaper(it) }, onGallery = {
            runCatching { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_WALLPAPER) }
        })

    private fun setWallpaper(spec: String) {
        chat = ChatStore.edit(this, chat.id) { it.wallpaper = spec } ?: chat
        Wallpapers.apply(this, wall, Wallpapers.spec(this, chat))
    }

    private fun transcript(): String = items.filter { it.kind == ChatItem.USER || it.kind == ChatItem.AGENT || it.kind == ChatItem.TOOL }
        .joinToString("\n\n") {
            when (it.kind) {
                ChatItem.USER -> tr("אני", "Me") + " [${it.time}]: " + it.text
                ChatItem.AGENT -> "Antigravity [${it.time}]: " + it.text
                else -> "  • " + it.text
            }
        }

    private fun showDetails(item: ChatItem) {
        val out = StepMapper.details(item.step)
        Ui.info(this, item.text.take(80), if (out != null) out.takeLast(8000) else item.text, mono = true)
    }

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
        Ui.info(this, tr("פרטי השגיאה", "Error details"), full.take(8000))
    }

    private fun openVisualUi() {
        startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, TerminalService.hubUiUrl ?: TerminalService.HUB_URL))
    }

}

/** Wallpaper choices shared by the chat, chat info and settings screens. */
object WallpaperPicker {
    fun show(a: BaseActivity, current: String, allowInherit: Boolean, onPick: (String) -> Unit, onGallery: () -> Unit) {
        val content = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(16), 0, a.dp(16), a.dp(8)) }
        var sheet: Sheet? = null
        fun swatch(d: android.graphics.drawable.Drawable, selected: Boolean, f: () -> Unit) = FrameLayout(a).apply {
            background = d
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setRoundRect(0, 0, view.width, view.height, a.dpf(14f)) }
            }
            if (selected) addView(Ui.icon(a, R.drawable.ic_check_circle, T.primary, 28), flp(a.dp(28), a.dp(28), Gravity.CENTER))
            setOnClickListener { sheet?.dismiss(); f() }
        }
        fun grid(specs: List<String>) {
            specs.chunked(5).forEach { chunk ->
                val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                chunk.forEach { s -> row.addView(swatch(Wallpapers.drawable(a, s), s == current) { onPick(s) }, lp(0, a.dp(84), 1f).apply { setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(4)) }) }
                repeat(5 - chunk.size) { row.addView(View(a), lp(0, 1, 1f)) }
                content.addView(row)
            }
        }
        content.addView(Ui.text(a, a.tr("רקעים", "Backgrounds"), 14f, T.muted, bold = true).apply { setPadding(a.dp(4), a.dp(4), 0, a.dp(4)) })
        grid(listOf("pattern") + Wallpapers.gradients.indices.map { "gradient:$it" })
        content.addView(Ui.text(a, a.tr("צבעים", "Colours"), 14f, T.muted, bold = true).apply { setPadding(a.dp(4), a.dp(10), 0, a.dp(4)) })
        grid(Wallpapers.colors.map { "color:$it" })
        val s = Sheet(a).title(a.tr("רקע לשיחה", "Chat wallpaper")).view(content)
        s.item(R.drawable.ic_image, a.tr("תמונה מהגלריה", "Picture from gallery")) { onGallery() }
        if (allowInherit) s.item(R.drawable.ic_refresh, a.tr("כמו בהגדרות הכלליות", "Same as general settings")) { onPick("") }
        sheet = s
        s.show()
    }
}
