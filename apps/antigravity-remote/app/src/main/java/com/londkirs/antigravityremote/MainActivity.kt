package com.londkirs.antigravityremote

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Date

/** Home: the list of chats, like WhatsApp's main screen. Also the archive (with [EXTRA_ARCHIVE]). */
class MainActivity : BaseActivity() {
    companion object { const val EXTRA_ARCHIVE = "archive" }

    private val archiveMode get() = intent.getBooleanExtra(EXTRA_ARCHIVE, false)
    private lateinit var top: Ui.TopBar
    private lateinit var selBar: LinearLayout
    private lateinit var selCount: TextView
    private lateinit var selActions: LinearLayout
    private lateinit var searchBar: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: TextView
    private lateinit var bannerBtn: TextView
    private lateinit var chips: LinearLayout
    private lateinit var chipScroll: View
    private lateinit var listBox: LinearLayout
    private lateinit var fabView: View
    private var filter = "all"            // all | unread | fav | list:<id>
    private var query: String? = null
    private val selected = LinkedHashSet<String>()
    private val main by lazy { Handler(mainLooper) }
    private val storeListener: () -> Unit = { main.removeCallbacks(rerender); main.postDelayed(rerender, 150) }
    private val rerender = Runnable { if (!isFinishing) render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // People upgrading from 3.x already set things up.
        if (!Settings.setupDone(this) && AgyInstaller.isInstalled(this)) Settings.setSetupDone(this, true)
        if (!archiveMode && !Settings.setupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java)); finish(); return
        }
        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val headerFrame = FrameLayout(this)
        top = Ui.TopBar(this, if (archiveMode) tr("בארכיון", "Archived") else "Antigravity", back = archiveMode)
        top.action(R.drawable.ic_search, tr("חיפוש", "Search")) { openSearch() }
        top.action(R.drawable.ic_more, tr("עוד", "More")) { mainMenu(it) }
        headerFrame.addView(top, flp(MATCH, dp(62)))
        selBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(T.bar); elevation = dpf(1f); visibility = View.GONE
            addView(Ui.iconButton(this@MainActivity, R.drawable.ic_close, tr("ביטול בחירה", "Clear selection")) { clearSelection() })
            selCount = Ui.text(this@MainActivity, "", 19f, T.text, bold = true)
            addView(selCount, lp(0, WRAP, 1f).apply { marginStart = dp(8) })
            selActions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            addView(selActions)
        }
        headerFrame.addView(selBar, flp(MATCH, dp(62)))
        searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(T.bar); elevation = dpf(1f); visibility = View.GONE
            addView(Ui.iconButton(this@MainActivity, R.drawable.ic_back, tr("סגירת החיפוש", "Close search")) { closeSearch() })
            searchField = EditText(this@MainActivity).apply {
                hint = tr("חיפוש בשיחות ובהודעות…", "Search chats and messages…"); isSingleLine = true
                setTextColor(T.text); setHintTextColor(T.faint); background = null
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, T.fs(17f))
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(e: Editable?) { query = e?.toString()?.trim()?.ifEmpty { null }; render() }
                })
            }
            addView(searchField, lp(0, WRAP, 1f))
        }
        headerFrame.addView(searchBar, flp(MATCH, dp(62)))
        col.addView(headerFrame, lp(MATCH, dp(62)))

        banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(T.primarySoft, dpf(16f))
            setPadding(dp(14), dp(8), dp(8), dp(8)); visibility = View.GONE
        }
        banner.addView(Ui.icon(this, R.drawable.ic_info, T.primary, 22), lp(dp(22), dp(22)).apply { marginEnd = dp(10) })
        bannerText = Ui.text(this, "", 14f, T.text)
        banner.addView(bannerText, lp(0, WRAP, 1f))
        bannerBtn = Ui.button(this, "", Ui.Style.PRIMARY) {}.apply { minHeight = dp(38); setPadding(dp(14), dp(4), dp(14), dp(4)) }
        banner.addView(bannerBtn)
        col.addView(banner, lp(MATCH, WRAP).apply { setMargins(dp(12), dp(8), dp(12), 0) })

        val (sv, strip) = chipStrip(this)
        chips = strip; chipScroll = sv
        col.addView(sv, lp(MATCH, WRAP))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(96)) }
        col.addView(ScrollView(this).apply { addView(listBox); isVerticalScrollBarEnabled = false }, lp(MATCH, 0, 1f))
        root.addView(col, flp(MATCH, MATCH))
        fabView = fab(this, R.drawable.ic_chat_compose, tr("שיחה חדשה", "New chat")) { newChat() }
        if (!archiveMode) root.addView(fabView, flp(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(18), 0, dp(18), dp(22)) })
        setContentView(root)
        if (!archiveMode) CoachMark.show(this, fabView, "home_fab", tr("כאן פותחים שיחה חדשה עם הסוכן.", "Start a new chat with the agent here."))
    }

    override fun onResume() {
        super.onResume()
        ChatStore.addListener(storeListener)
        if (AgyInstaller.isInstalled(this)) ChatWatcher.start(this)
        render()
        checkEngine()
    }

    override fun onPause() {
        ChatStore.removeListener(storeListener)
        super.onPause()
    }

    override fun onBackPressed() {
        when {
            selected.isNotEmpty() -> clearSelection()
            searchBar.visibility == View.VISIBLE -> closeSearch()
            else -> super.onBackPressed()
        }
    }

    // ---- engine status --------------------------------------------------------------------------

    private fun showBanner(text: String?, button: String? = null, action: (() -> Unit)? = null) {
        if (text == null || archiveMode) { banner.visibility = View.GONE; return }
        bannerText.text = text
        bannerBtn.text = button ?: ""; bannerBtn.visibility = if (button == null) View.GONE else View.VISIBLE
        bannerBtn.setOnClickListener { action?.invoke() }
        banner.visibility = View.VISIBLE
    }

    private fun checkEngine() {
        if (!AgyInstaller.isInstalled(this)) {
            showBanner(tr("הסוכן עוד לא מותקן בטלפון.", "The agent is not installed on this phone yet."), tr("להתקנה", "Install")) {
                startActivity(Intent(this, SetupActivity::class.java))
            }
            return
        }
        HubClient.attach(this)
        Thread {
            val alive = HubClient.isAlive()
            val auth = if (alive) runCatching { HubClient.hasValidAuth() }.getOrDefault(true) else true
            runOnUiThread {
                when {
                    !alive -> showBanner(tr("הסוכן כבוי. הוא יופעל כשתפתחו שיחה.", "The agent is off. It starts when you open a chat."), tr("הפעלה", "Start")) { startEngine() }
                    !auth -> showBanner(tr("צריך להתחבר עם חשבון Google.", "Sign in with Google to chat."), tr("התחברות", "Sign in")) {
                        startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_STEP, 2))
                    }
                    else -> showBanner(null)
                }
            }
        }.start()
    }

    private fun startEngine() {
        showBanner(tr("מפעיל את הסוכן…", "Starting the agent…"))
        HubClient.ensureRunning(this) { ok ->
            if (ok) { snack(tr("הסוכן פועל", "The agent is running")); ChatWatcher.start(this); checkEngine() }
            else showBanner(tr("הסוכן לא עלה.", "The agent did not start."), tr("פרטים", "Details")) { startActivity(Intent(this, TerminalActivity::class.java)) }
        }
    }

    // ---- list -----------------------------------------------------------------------------------

    private fun visibleChats(all: List<Chat>): List<Chat> {
        val base = all.filter { it.archived == archiveMode }
        val filtered = when {
            filter == "unread" -> base.filter { it.isUnread }
            filter == "fav" -> base.filter { it.favorite }
            filter.startsWith("list:") -> base.filter { filter.removePrefix("list:") in it.lists }
            else -> base
        }
        return filtered.sortedWith(compareByDescending<Chat> { it.pinned }.thenByDescending { it.updated })
    }

    private fun render() {
        val all = ChatStore.all(this)
        renderChips(all)
        listBox.removeAllViews()
        val q = query
        if (q != null) { renderSearch(all, q); return }
        val archived = all.count { it.archived }
        if (!archiveMode && filter == "all" && archived > 0) {
            listBox.addView(Ui.row(this, R.drawable.ic_archive, tr("בארכיון", "Archived"), null,
                Ui.text(this, archived.toString(), 13f, T.primary, bold = true), iconTint = T.primary) {
                startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_ARCHIVE, true))
            }.apply { setPadding(dp(30), dp(8), dp(24), dp(8)) })
        }
        val chats = visibleChats(all)
        if (chats.isEmpty()) {
            val (title, sub) = when {
                archiveMode -> tr("אין שיחות בארכיון", "No archived chats") to tr("החליקו שיחה ימינה כדי להעביר אותה לכאן.", "Swipe a chat right to move it here.")
                filter == "unread" -> tr("הכול נקרא", "All caught up") to tr("אין תשובות חדשות מהסוכן.", "No new replies from the agent.")
                filter == "fav" -> tr("אין מועדפים", "No favourites") to tr("לחיצה ארוכה על שיחה ואז ⭐.", "Long-press a chat, then ⭐.")
                filter.startsWith("list:") -> tr("הרשימה ריקה", "This list is empty") to tr("לחיצה ארוכה על שיחה ואז \"הוספה לרשימה\".", "Long-press a chat, then \"Add to list\".")
                else -> tr("עדיין אין שיחות", "No chats yet") to tr("לחצו על הכפתור למטה כדי לדבר עם Antigravity.", "Tap the button below to talk to Antigravity.")
            }
            listBox.addView(empty(R.drawable.ic_chat, title, sub))
            return
        }
        chats.forEach { listBox.addView(chatRow(it)) }
        if (!archiveMode && chats.isNotEmpty()) listBox.getChildAt(listBox.childCount - chats.size)?.let {
            CoachMark.show(this, it, "home_swipe", tr("טיפ: החליקו שיחה ימינה לארכיון ושמאלה לנעיצה. לחיצה ארוכה בוחרת כמה שיחות.",
                "Tip: swipe a chat right to archive, left to pin. Long-press to select several."), 1500)
        }
    }

    private fun renderChips(all: List<Chat>) {
        chips.removeAllViews()
        chipScroll.visibility = if (query != null || archiveMode) View.GONE else View.VISIBLE
        fun add(key: String, label: String, onLong: ((View) -> Unit)? = null) {
            val c = Ui.chip(this, label, filter == key) { filter = key; render() }
            if (onLong != null) c.setOnLongClickListener { onLong(it); true }
            chips.addView(c, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
        }
        add("all", tr("הכול", "All"))
        val unread = all.count { !it.archived && it.isUnread }
        add("unread", tr("לא נקראו", "Unread") + if (unread > 0) " $unread" else "")
        add("fav", tr("מועדפים", "Favourites"))
        ChatLists.all(this).forEach { l ->
            add("list:" + l.id, l.name) { v ->
                Menu(this).item(R.drawable.ic_edit, tr("שינוי שם", "Rename")) {
                    Ui.prompt(this, tr("שם הרשימה", "List name"), l.name, tr("שם", "Name")) { ChatLists.rename(this, l.id, it); render() }
                }.item(R.drawable.ic_delete, tr("מחיקת הרשימה", "Delete list"), danger = true) {
                    Ui.confirm(this, tr("למחוק את הרשימה? השיחות עצמן נשארות.", "Delete the list? The chats stay."), tr("מחיקה", "Delete"), true) {
                        ChatLists.delete(this, l.id); if (filter == "list:" + l.id) filter = "all"; render()
                    }
                }.show(v)
            }
        }
        chips.addView(Ui.chip(this, "", false, R.drawable.ic_add) { newList(null) }.apply { contentDescription = tr("רשימה חדשה", "New list") })
    }

    private fun timeLabel(ms: Long): String {
        val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = ms }
        return when {
            DateUtils.isToday(ms) -> DateFormat.getTimeFormat(this).format(Date(ms))
            DateUtils.isToday(ms + DateUtils.DAY_IN_MILLIS) -> tr("אתמול", "Yesterday")
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.timeInMillis - ms < 6 * DateUtils.DAY_IN_MILLIS ->
                DateUtils.formatDateTime(this, ms, DateUtils.FORMAT_SHOW_WEEKDAY)
            else -> DateFormat.getDateFormat(this).format(Date(ms))
        }
    }

    private fun chatRow(c: Chat): View {
        val swipe = SwipeRow(this)
        val sel = c.id in selected
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = Ui.ripple(android.graphics.drawable.ColorDrawable(if (sel) T.selection else T.bg))
            minimumHeight = dp(76)
        }
        val av = FrameLayout(this)
        av.addView(chatAvatar(this, c, 52), flp(dp(52), dp(52)))
        if (sel) av.addView(FrameLayout(this).apply {
            background = Ui.circle(T.primary)
            addView(Ui.icon(this@MainActivity, R.drawable.ic_check, T.onPrimary, 14), flp(dp(14), dp(14), Gravity.CENTER))
        }, flp(dp(22), dp(22), Gravity.BOTTOM or Gravity.END))
        row.addView(av, lp(dp(54), dp(54)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPaddingRelative(dp(14), 0, 0, 0) }
        val topLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topLine.addView(Ui.text(this, c.name(this), 17f, T.text, bold = true, lines = 1), lp(0, WRAP, 1f))
        topLine.addView(Ui.text(this, timeLabel(c.updated), 12.5f, if (c.isUnread && !c.muted) T.primary else T.muted).apply { setPaddingRelative(dp(8), 0, 0, 0) })
        texts.addView(topLine)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, 0) }
        val preview = SpannableHelper.preview(this, c)
        if (preview.first != 0) bottom.addView(Ui.icon(this, preview.first, preview.third, 16), lp(dp(16), dp(16)).apply { marginEnd = dp(4) })
        bottom.addView(Ui.text(this, preview.second, 14.5f, if (c.busy || c.waiting) preview.third else T.muted, lines = 1), lp(0, WRAP, 1f))
        if (c.muted) bottom.addView(Ui.icon(this, R.drawable.ic_mute, T.faint, 16), lp(dp(16), dp(16)).apply { marginStart = dp(6) })
        if (c.pinned > 0) bottom.addView(Ui.icon(this, R.drawable.ic_pin, T.faint, 16), lp(dp(16), dp(16)).apply { marginStart = dp(6) })
        if (c.unread > 0) bottom.addView(Ui.badge(this, c.unread, if (c.muted) T.faint else T.primary), lp(WRAP, WRAP).apply { marginStart = dp(8) })
        else if (c.markedUnread) bottom.addView(View(this).apply { background = Ui.circle(T.primary) }, lp(dp(12), dp(12)).apply { marginStart = dp(8) })
        texts.addView(bottom)
        row.addView(texts, lp(0, WRAP, 1f))
        swipe.addView(row, flp(MATCH, WRAP))

        row.setOnClickListener {
            if (selected.isNotEmpty()) toggle(c.id) else ChatActivity.open(this, c.id)
        }
        row.setOnLongClickListener { toggle(c.id); true }
        swipe.onRight = {
            ChatStore.edit(this, c.id) { it.archived = !it.archived }
            snack(if (c.archived) tr("הוצא מהארכיון", "Unarchived") else tr("הועבר לארכיון", "Archived"), tr("ביטול", "Undo")) {
                ChatStore.edit(this, c.id) { it.archived = c.archived }
            }
        }
        swipe.rightHint = swipe.bandHint(if (c.archived) R.drawable.ic_unarchive else R.drawable.ic_archive, T.primary, fromLeft = true)
        swipe.onLeft = { togglePin(listOf(c)) }
        swipe.leftHint = swipe.bandHint(R.drawable.ic_pin, T.success, fromLeft = false)
        return swipe
    }

    private fun renderSearch(all: List<Chat>, q: String) {
        val chats = all.filter { it.title.contains(q, true) || it.preview.contains(q, true) }
        if (chats.isNotEmpty()) {
            listBox.addView(Ui.sectionTitle(this, tr("שיחות", "Chats")))
            chats.forEach { listBox.addView(chatRow(it)) }
        }
        val hits = mutableListOf<Triple<Chat, ChatItem, String>>()
        for (c in all) {
            for (it in ChatCache.load(this, c.id)) {
                if ((it.kind == ChatItem.USER || it.kind == ChatItem.AGENT) && it.text.contains(q, true)) {
                    val plain = Markdown.plain(it.text).replace('\n', ' ')
                    val i = plain.indexOf(q, ignoreCase = true).coerceAtLeast(0)
                    hits += Triple(c, it, (if (i > 30) "…" else "") + plain.substring(maxOf(0, i - 30)).take(120))
                }
                if (hits.size >= 60) break
            }
        }
        if (hits.isNotEmpty()) {
            listBox.addView(Ui.sectionTitle(this, tr("הודעות", "Messages")))
            hits.forEach { (c, item, snippet) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(10), dp(20), dp(10))
                    background = Ui.ripple(null)
                    setOnClickListener { ChatActivity.open(this@MainActivity, c.id, scrollKey = item.key) }
                }
                val t = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                t.addView(Ui.text(this, c.name(this), 15.5f, T.text, bold = true, lines = 1), lp(0, WRAP, 1f))
                t.addView(Ui.text(this, item.time, 12.5f, T.muted))
                row.addView(t)
                row.addView(Ui.text(this, Markdown.mark((if (item.kind == ChatItem.USER) tr("אני: ", "Me: ") else "") + snippet, q), 14.5f, T.muted, lines = 2))
                listBox.addView(row)
            }
        }
        if (chats.isEmpty() && hits.isEmpty()) listBox.addView(empty(R.drawable.ic_search, tr("לא נמצא", "Nothing found"), tr("נסו מילה אחרת.", "Try another word.")))
    }

    // ---- search mode ----------------------------------------------------------------------------

    private fun openSearch() {
        searchBar.visibility = View.VISIBLE; top.visibility = View.INVISIBLE
        showKeyboard(searchField)
    }

    private fun closeSearch() {
        searchField.setText(""); query = null
        searchBar.visibility = View.GONE; top.visibility = View.VISIBLE
        hideKeyboard(searchField); render()
    }

    // ---- selection ------------------------------------------------------------------------------

    private fun toggle(id: String) {
        if (!selected.remove(id)) selected += id
        Ui.haptic(listBox)
        refreshSelection(); render()
    }

    private fun clearSelection() { selected.clear(); refreshSelection(); render() }

    private fun refreshSelection() {
        val on = selected.isNotEmpty()
        selBar.visibility = if (on) View.VISIBLE else View.GONE
        top.visibility = if (on || searchBar.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        if (!on) return
        selCount.text = selected.size.toString()
        selActions.removeAllViews()
        val chats = ChatStore.all(this).filter { it.id in selected }
        fun act(icon: Int, label: String, f: (View) -> Unit) = selActions.addView(Ui.iconButton(this, icon, label) { f(it) })
        val allPinned = chats.all { it.pinned > 0 }
        act(R.drawable.ic_pin, if (allPinned) tr("ביטול נעיצה", "Unpin") else tr("נעיצה", "Pin")) { togglePin(chats); clearSelection() }
        val allMuted = chats.all { it.muted }
        act(if (allMuted) R.drawable.ic_bell else R.drawable.ic_mute, if (allMuted) tr("ביטול השתקה", "Unmute") else tr("השתקה", "Mute")) {
            ChatStore.editMany(this, selected) { it.muted = !allMuted }; clearSelection()
        }
        act(if (archiveMode) R.drawable.ic_unarchive else R.drawable.ic_archive, if (archiveMode) tr("הוצאה מהארכיון", "Unarchive") else tr("ארכיון", "Archive")) {
            val ids = selected.toSet()
            ChatStore.editMany(this, ids) { it.archived = !archiveMode }
            clearSelection()
            snack(if (archiveMode) tr("הוצא מהארכיון", "Unarchived") else tr("${ids.size} שיחות הועברו לארכיון", "${ids.size} chats archived"), tr("ביטול", "Undo")) {
                ChatStore.editMany(this, ids) { it.archived = archiveMode }
            }
        }
        act(R.drawable.ic_delete, tr("מחיקה", "Delete")) {
            val ids = selected.toSet()
            Ui.confirm(this, if (ids.size == 1) tr("למחוק את השיחה?", "Delete this chat?") else tr("למחוק ${ids.size} שיחות?", "Delete ${ids.size} chats?"), tr("מחיקה", "Delete"), true) {
                ChatStore.deleteSelected(this, ids); clearSelection()
            }
        }
        act(R.drawable.ic_more, tr("עוד", "More")) { anchor ->
            val anyUnread = chats.any { it.isUnread }
            val allFav = chats.all { it.favorite }
            Menu(this)
                .item(if (anyUnread) R.drawable.ic_done_all else R.drawable.ic_chat, if (anyUnread) tr("סימון כנקרא", "Mark as read") else tr("סימון כלא נקרא", "Mark as unread")) {
                    ChatStore.editMany(this, selected) { if (anyUnread) { it.seenCount = it.agentCount; it.unread = 0; it.markedUnread = false } else it.markedUnread = true }
                    clearSelection()
                }
                .item(R.drawable.ic_star, if (allFav) tr("הסרה מהמועדפים", "Remove from favourites") else tr("הוספה למועדפים", "Add to favourites")) {
                    ChatStore.editMany(this, selected) { it.favorite = !allFav }; clearSelection()
                }
                .item(R.drawable.ic_label, tr("הוספה לרשימה", "Add to list")) { addToList(selected.toSet()) }
                .item(R.drawable.ic_select, tr("בחירת הכול", "Select all")) {
                    selected.addAll(visibleChats(ChatStore.all(this)).map { it.id }); refreshSelection(); render()
                }
                .show(anchor)
        }
    }

    private fun togglePin(chats: List<Chat>) {
        val unpin = chats.all { it.pinned > 0 }
        if (!unpin && ChatStore.all(this).count { it.pinned > 0 && it.id !in chats.map { c -> c.id } } + chats.size > 3) {
            snack(tr("אפשר לנעוץ עד 3 שיחות", "You can pin up to 3 chats")); return
        }
        ChatStore.editMany(this, chats.map { it.id }) { it.pinned = if (unpin) 0 else System.currentTimeMillis() }
    }

    private fun addToList(ids: Set<String>) {
        val sheet = Sheet(this).title(tr("הוספה לרשימה", "Add to list"))
        ChatLists.all(this).forEach { l ->
            sheet.item(R.drawable.ic_label, l.name) { ChatStore.editMany(this, ids) { it.lists += l.id }; clearSelection(); snack(tr("נוסף לרשימה ", "Added to ") + l.name) }
        }
        sheet.item(R.drawable.ic_add, tr("רשימה חדשה", "New list")) { newList(ids) }
        sheet.show()
    }

    private fun newList(ids: Set<String>?) {
        Ui.prompt(this, tr("רשימה חדשה", "New list"), "", tr("למשל: פיתוח, בדיקות", "e.g. Development, Tests"), tr("יצירה", "Create")) { name ->
            if (name.isBlank()) return@prompt
            val l = ChatLists.add(this, name)
            if (ids != null) { ChatStore.editMany(this, ids) { it.lists += l.id }; clearSelection() }
            filter = "list:" + l.id
            render()
        }
    }

    private fun newChat() {
        val c = ChatStore.create(this)
        if (filter.startsWith("list:")) ChatStore.edit(this, c.id) { it.lists += filter.removePrefix("list:") }
        ChatActivity.open(this, c.id)
    }

    private fun mainMenu(anchor: View) {
        val m = Menu(this)
        m.item(R.drawable.ic_chat_compose, tr("שיחה חדשה", "New chat")) { newChat() }
        m.item(R.drawable.ic_label, tr("רשימה חדשה", "New list")) { newList(null) }
        m.item(R.drawable.ic_select, tr("בחירת שיחות", "Select chats")) {
            visibleChats(ChatStore.all(this)).firstOrNull()?.let { toggle(it.id) } ?: snack(tr("אין שיחות לבחירה", "No chats to select"))
        }
        m.item(R.drawable.ic_star, tr("הודעות מסומנות בכוכב", "Starred messages")) { StarredActivity.open(this, null) }
        if (!archiveMode) m.item(R.drawable.ic_archive, tr("ארכיון", "Archive")) { startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_ARCHIVE, true)) }
        m.item(R.drawable.ic_help, tr("מדריך הפעלה", "Setup guide")) { startActivity(Intent(this, SetupActivity::class.java)) }
        m.item(R.drawable.ic_settings, tr("הגדרות", "Settings")) { startActivity(Intent(this, SettingsActivity::class.java)) }
        m.show(anchor)
    }
}

/** Preview line of a chat row: (icon, text, colour). */
object SpannableHelper {
    fun preview(a: BaseActivity, c: Chat): Triple<Int, CharSequence, Int> = when {
        c.draft.isNotBlank() -> {
            val s = android.text.SpannableStringBuilder(a.tr("טיוטה: ", "Draft: "))
            s.setSpan(android.text.style.ForegroundColorSpan(T.danger), 0, s.length, 0)
            s.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, s.length, 0)
            s.append(c.draft.replace('\n', ' '))
            Triple(0, s, T.muted)
        }
        c.waiting -> Triple(R.drawable.ic_shield, a.tr("מחכה לאישור שלך", "Needs your approval"), T.warning)
        c.busy -> Triple(0, a.tr("מקליד…", "typing…"), T.primary)
        c.lastFromMe && c.preview.isNotBlank() -> Triple(R.drawable.ic_done_all, Markdown.plain(c.preview).replace('\n', ' '), if (c.replied) T.tickRead else T.muted)
        else -> Triple(0, Markdown.plain(c.preview).replace('\n', ' ').ifBlank { a.tr("אין עדיין הודעות", "No messages yet") }, T.muted)
    }
}

/** A chat's picture: its emoji, the Antigravity icon ("agy"), or its first letter on a palette colour. */
fun chatAvatar(a: android.app.Activity, c: Chat, sizeDp: Int): View =
    if (c.emoji == "agy") Ui.agentAvatar(a, sizeDp) else Ui.avatar(a, c.name(a), c.id, sizeDp, c.emoji)

