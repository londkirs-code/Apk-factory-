package com.londkirs.antigravityremote

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File
import java.util.Date

/** Chat details: name & avatar, media, starred and pinned messages, lists, wallpaper, mute, delete. */
class ChatInfoActivity : BaseActivity() {
    companion object {
        private const val EXTRA_ID = "chat_id"
        private const val REQ_WALL = 61
        fun open(a: Activity, id: String) = a.startActivity(Intent(a, ChatInfoActivity::class.java).putExtra(EXTRA_ID, id))
    }

    private lateinit var chat: Chat
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chat = ChatStore.get(this, intent.getStringExtra(EXTRA_ID) ?: "") ?: run { finish(); return }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.TopBar(this, tr("פרטי השיחה", "Chat info")))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(24)) }
        col.addView(ScrollView(this).apply { addView(body) }, lp(MATCH, 0, 1f))
        setContentView(col)
    }

    override fun onResume() {
        super.onResume()
        chat = ChatStore.get(this, chat.id) ?: run { finish(); return }
        render()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(T.surface)
    }

    private fun gap() = body.addView(View(this).apply { setBackgroundColor(T.surface2) }, lp(MATCH, dp(10)))

    private fun render() {
        body.removeAllViews()
        body.setBackgroundColor(T.surface2)
        val items = ChatCache.load(this, chat.id)
        val meta = MsgMeta.load(this, chat.id)

        // Header
        val head = card().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(16), dp(24), dp(16), dp(16)) }
        val avatar = chatAvatar(this, chat, 104)
        avatar.setOnClickListener { pickEmoji() }
        head.addView(avatar, lp(dp(104), dp(104)))
        head.addView(Ui.text(this, chat.name(this), 22f, T.text, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(2))
            setOnClickListener { rename() }
        })
        head.addView(Ui.text(this, tr("שיחה עם Antigravity", "Chat with Antigravity") + (ChatEngine.currentModel(this)?.let { " · " + it.name } ?: ""), 14f, T.muted).apply { gravity = Gravity.CENTER })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        fun action(icon: Int, label: String, on: Boolean = false, f: () -> Unit) = actions.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = Ui.ripple(Ui.round(if (on) T.primarySoft else T.surface2, dpf(16f)), dpf(16f))
            setPadding(dp(4), dp(12), dp(4), dp(10))
            addView(Ui.icon(this@ChatInfoActivity, icon, T.primary, 24))
            addView(Ui.text(this@ChatInfoActivity, label, 13f, T.text).apply { setPadding(0, dp(6), 0, 0); gravity = Gravity.CENTER })
            setOnClickListener { f() }
        }, lp(0, WRAP, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        action(R.drawable.ic_edit, tr("שינוי שם", "Rename")) { rename() }
        action(if (chat.muted) R.drawable.ic_mute else R.drawable.ic_bell, if (chat.muted) tr("מושתק", "Muted") else tr("השתקה", "Mute"), chat.muted) { edit { it.muted = !it.muted } }
        action(R.drawable.ic_star, if (chat.favorite) tr("במועדפים", "Favourite") else tr("מועדפים", "Favourite"), chat.favorite) { edit { it.favorite = !it.favorite } }
        action(if (chat.archived) R.drawable.ic_unarchive else R.drawable.ic_archive, if (chat.archived) tr("הוצאה מהארכיון", "Unarchive") else tr("ארכיון", "Archive"), chat.archived) { edit { it.archived = !it.archived } }
        head.addView(actions, lp(MATCH, WRAP))
        body.addView(head)
        gap()

        // Media, links, files
        val media = items.flatMap { it.media }.distinctBy { it.path }.filter { File(it.path).isFile }
        val links = GalleryActivity.links(items)
        val mediaCard = card()
        mediaCard.addView(Ui.row(this, R.drawable.ic_image, tr("מדיה, קישורים וקבצים", "Media, links and files"),
            tr("${media.size} מדיה · ${links.size} קישורים", "${media.size} media · ${links.size} links"),
            Ui.icon(this, R.drawable.ic_chevron, T.faint, 20)) { GalleryActivity.open(this, chat.id) })
        if (media.isNotEmpty()) {
            val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), 0, dp(16), dp(14)) }
            media.takeLast(8).reversed().forEachIndexed { i, m ->
                strip.addView(thumb(m, dp(84)) { MediaViewerActivity.open(this, media, media.indexOf(m)) }, lp(dp(84), dp(84)).apply { marginEnd = dp(6) })
            }
            mediaCard.addView(HorizontalScrollView(this).apply { addView(strip); isHorizontalScrollBarEnabled = false })
        }
        body.addView(mediaCard)
        gap()

        // Starred & pinned
        val msgCard = card()
        msgCard.addView(Ui.row(this, R.drawable.ic_star, tr("הודעות מסומנות בכוכב", "Starred messages"), meta.stars.size.toString(),
            Ui.icon(this, R.drawable.ic_chevron, T.faint, 20)) { StarredActivity.open(this, chat.id) })
        meta.pins.mapNotNull { k -> items.firstOrNull { it.key == k } }.forEach { p ->
            msgCard.addView(Ui.row(this, R.drawable.ic_pin, Markdown.plain(p.text).replace('\n', ' ').take(80).ifBlank { "📷" }, tr("נעוצה · ", "Pinned · ") + p.time) {
                ChatActivity.open(this, chat.id, scrollKey = p.key)
            })
        }
        body.addView(msgCard)
        gap()

        // Lists
        val listCard = card()
        listCard.addView(Ui.sectionTitle(this, tr("רשימות", "Lists")))
        val (sv, strip) = chipStrip(this)
        ChatLists.all(this).forEach { l ->
            val on = l.id in chat.lists
            strip.addView(Ui.chip(this, l.name, on, if (on) R.drawable.ic_check else 0) {
                edit { if (!it.lists.remove(l.id)) it.lists += l.id }
            }, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
        }
        strip.addView(Ui.chip(this, tr("רשימה חדשה", "New list"), false, R.drawable.ic_add) {
            Ui.prompt(this, tr("רשימה חדשה", "New list"), "", tr("למשל: פיתוח", "e.g. Development"), tr("יצירה", "Create")) { name ->
                if (name.isNotBlank()) { val l = ChatLists.add(this, name); edit { it.lists += l.id } }
            }
        })
        listCard.addView(sv)
        body.addView(listCard)
        gap()

        // Settings for this chat
        val set = card()
        set.addView(Ui.row(this, R.drawable.ic_wallpaper, tr("רקע לשיחה", "Chat wallpaper"),
            if (chat.wallpaper.isBlank()) tr("כמו בהגדרות הכלליות", "Same as general settings") else tr("רקע מותאם", "Custom")) {
            WallpaperPicker.show(this, chat.wallpaper, true, onPick = { s -> edit { it.wallpaper = s } }, onGallery = {
                runCatching { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_WALL) }
            })
        })
        set.addView(Ui.row(this, R.drawable.ic_mood, tr("תמונת שיחה", "Chat picture"), when (chat.emoji) { "" -> tr("האות הראשונה", "First letter"); "agy" -> tr("הסמל של Antigravity", "Antigravity icon"); else -> chat.emoji }) { pickEmoji() })
        val started = ChatStore.isStarted(this, chat)
        set.addView(Ui.row(this, R.drawable.ic_folder, tr("תיקיית עבודה", "Working folder"), ChatEngine.workDir(this, chat).replace(AgyInstaller.homeDir(this).absolutePath, "~")) {
            if (started) Ui.info(this, tr("תיקיית עבודה", "Working folder"), tr("התיקייה נקבעת כשהשיחה מתחילה. כדי לעבוד בתיקייה אחרת פתחו שיחה חדשה.\n\n", "The folder is set when the chat starts. Open a new chat to work in another folder.\n\n") + ChatEngine.workDir(this, chat))
            else Ui.prompt(this, tr("תיקיית עבודה", "Working folder"), ChatEngine.workDir(this, chat), "~/projects/my-app") { v ->
                val path = v.replaceFirst("~", AgyInstaller.homeDir(this).absolutePath)
                edit { it.workDir = if (path == ChatEngine.defaultWorkDir(this)) "" else path }
            }
        })
        set.addView(Ui.row(this, R.drawable.ic_share, tr("ייצוא השיחה", "Export chat")) {
            val text = items.filter { it.kind == ChatItem.USER || it.kind == ChatItem.AGENT }.joinToString("\n\n") {
                (if (it.kind == ChatItem.USER) tr("אני", "Me") else "Antigravity") + " [${it.time}]: " + it.text
            }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("שיתוף", "Share")))
        })
        if (meta.hidden.isNotEmpty()) set.addView(Ui.row(this, R.drawable.ic_refresh, tr("שחזור הודעות שנמחקו אצלי", "Restore messages deleted for me"), meta.hidden.size.toString()) {
            meta.hidden.clear(); meta.save(); snack(tr("ההודעות חזרו", "Messages restored")); render()
        })
        set.addView(Ui.row(this, R.drawable.ic_delete, tr("מחיקת השיחה", "Delete chat"), iconTint = T.danger, titleColor = T.danger) {
            Ui.confirm(this, tr("למחוק את השיחה מהטלפון? אי אפשר לבטל.", "Delete this chat from the phone? This cannot be undone."), tr("מחיקה", "Delete"), true) {
                ChatStore.delete(this, chat.id)
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
        })
        body.addView(set)
    }

    private fun thumb(m: MediaRef, size: Int, onClick: () -> Unit): View {
        val f = FrameLayout(this)
        val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = Ui.round(T.surface3, dpf(10f)); clipToOutline = true }
        f.addView(iv, flp(MATCH, MATCH))
        Media.thumb(iv, m.path, size)
        if (m.isVideo) f.addView(Ui.icon(this, R.drawable.ic_play, Color.WHITE, 28), flp(dp(28), dp(28), Gravity.CENTER))
        f.setOnClickListener { onClick() }
        return f
    }

    private fun edit(f: (Chat) -> Unit) { chat = ChatStore.edit(this, chat.id, f) ?: chat; render() }

    private fun rename() = Ui.prompt(this, tr("שם השיחה", "Chat name"), chat.title, tr("שם", "Name")) { v -> edit { it.title = v } }

    private fun pickEmoji() {
        val emojis = listOf("🚀", "🤖", "💡", "🛠️", "📱", "🧪", "📊", "🎨", "🔥", "⭐", "📝", "🌍", "🧠", "🎮", "📦", "🔒", "🐞", "⚙️", "💬", "📚")
        val grid = GridLayout(this).apply { columnCount = 5 }
        var sheet: Sheet? = null
        emojis.forEach { e ->
            grid.addView(Ui.text(this, e, 28f).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
                background = Ui.ripple(null, dpf(12f))
                setOnClickListener { sheet?.dismiss(); edit { it.emoji = e } }
            }, GridLayout.LayoutParams().apply { width = (resources.displayMetrics.widthPixels - dp(32)) / 5; height = dp(60) })
        }
        val s = Sheet(this).title(tr("תמונת שיחה", "Chat picture")).view(LinearLayout(this).apply { setPadding(dp(16), 0, dp(16), 0); addView(grid) })
        s.item(R.drawable.ic_sparkle, tr("הסמל של Antigravity", "Antigravity icon")) { edit { it.emoji = "agy" } }
        s.item(R.drawable.ic_textsize, tr("האות הראשונה של השם", "First letter of the name")) { edit { it.emoji = "" } }
        sheet = s
        s.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WALL && resultCode == RESULT_OK) data?.data?.let { u ->
            Thread {
                val f = File(Wallpapers.dir(this), "chat_${chat.id}.jpg")
                runCatching { contentResolver.openInputStream(u)!!.use { i -> f.outputStream().use { i.copyTo(it) } } }
                runOnUiThread { edit { it.wallpaper = "image:" + f.absolutePath } }
            }.start()
        }
    }
}

/** Starred messages, of one chat or of all chats. */
class StarredActivity : BaseActivity() {
    companion object {
        private const val EXTRA_ID = "chat_id"
        fun open(a: Activity, chatId: String?) = a.startActivity(Intent(a, StarredActivity::class.java).apply { chatId?.let { putExtra(EXTRA_ID, it) } })
    }

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.TopBar(this, tr("הודעות מסומנות בכוכב", "Starred messages")))
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ScrollView(this).apply { addView(box) }, lp(MATCH, 0, 1f))
        setContentView(col)
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        box.removeAllViews()
        val only = intent.getStringExtra(EXTRA_ID)
        val chats = ChatStore.all(this).associateBy { it.id }
        val all = (if (only != null) listOf(only) else MsgMeta.allChats(this).toList()).flatMap { id ->
            val m = MsgMeta.load(this, id); m.stars.values.map { id to it }
        }.filter { chats.containsKey(it.first) }.sortedByDescending { it.second.millis }
        if (all.isEmpty()) {
            box.addView(empty(R.drawable.ic_star, tr("אין עדיין הודעות מסומנות", "No starred messages yet"),
                tr("לחיצה ארוכה על הודעה ואז ⭐ שומרת אותה כאן.", "Long-press a message and tap ⭐ to keep it here.")))
            return
        }
        all.forEach { (chatId, s) ->
            val c = chats[chatId]!!
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = Ui.ripple(null)
                setOnClickListener { ChatActivity.open(this@StarredActivity, chatId, scrollKey = s.key) }
                setOnLongClickListener {
                    Menu(this@StarredActivity).item(R.drawable.ic_star_outline, tr("הסרת הכוכב", "Unstar")) {
                        val m = MsgMeta.load(this@StarredActivity, chatId); m.stars.remove(s.key); m.save(); render()
                    }.item(R.drawable.ic_copy, tr("העתקה", "Copy")) { Ui.copy(this@StarredActivity, s.text) }.show(it)
                    true
                }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(Ui.text(this, (if (s.kind == ChatItem.USER) tr("אני", "Me") else "Antigravity") + "  ›  " + c.name(this), 13.5f, T.primary, bold = true, lines = 1), lp(0, WRAP, 1f))
            if (s.millis > 0) top.addView(Ui.text(this, DateFormat.getDateFormat(this).format(Date(s.millis)), 12f, T.muted))
            row.addView(top)
            row.addView(Ui.text(this, s.text, 15f, T.text, lines = 4).apply { setPadding(0, dp(4), 0, 0) })
            box.addView(row)
            box.addView(Ui.divider(this, 20))
        }
    }
}

/** Grid of a chat's media, plus its links and files. */
class GalleryActivity : BaseActivity() {
    companion object {
        private const val EXTRA_ID = "chat_id"
        fun open(a: Activity, id: String) = a.startActivity(Intent(a, GalleryActivity::class.java).putExtra(EXTRA_ID, id))
        private val urlRe = Regex("""https?://[^\s<>()\[\]"'`]+[^\s<>()\[\]"'`.,;:!?]""")
        private val pathRe = Regex("""(?:file://)?(/[^\s"'<>()\[\]`]+\.[A-Za-z0-9]{1,6})\b""")

        fun links(items: List<ChatItem>) = items.flatMap { urlRe.findAll(it.text).map { m -> m.value }.toList() }.distinct()
        fun files(items: List<ChatItem>) = items.flatMap { pathRe.findAll(it.text).map { m -> m.groupValues[1] }.toList() }.distinct()
            .filter { MediaRef.mimeOf(it) == null && File(it).isFile }
    }

    private var tab = 0
    private lateinit var content: LinearLayout
    private lateinit var tabs: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.TopBar(this, tr("מדיה, קישורים וקבצים", "Media, links and files")))
        val (sv, strip) = chipStrip(this)
        tabs = strip
        col.addView(sv)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ScrollView(this).apply { addView(content) }, lp(MATCH, 0, 1f))
        setContentView(col)
        render()
    }

    private fun render() {
        val items = ChatCache.load(this, intent.getStringExtra(EXTRA_ID) ?: "")
        tabs.removeAllViews()
        listOf(tr("מדיה", "Media"), tr("קישורים", "Links"), tr("קבצים", "Files")).forEachIndexed { i, t ->
            tabs.addView(Ui.chip(this, t, i == tab) { tab = i; render() }, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
        }
        content.removeAllViews()
        when (tab) {
            0 -> {
                val media = items.flatMap { it.media }.distinctBy { it.path }.filter { File(it.path).isFile }.reversed()
                if (media.isEmpty()) { content.addView(empty(R.drawable.ic_image, tr("אין מדיה בשיחה", "No media in this chat"), null)); return }
                val size = resources.displayMetrics.widthPixels / 3
                val grid = GridLayout(this).apply { columnCount = 3 }
                media.forEachIndexed { i, m ->
                    val f = FrameLayout(this)
                    val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(T.surface3) }
                    f.addView(iv, flp(MATCH, MATCH))
                    Media.thumb(iv, m.path, size)
                    if (m.isVideo) f.addView(Ui.icon(this, R.drawable.ic_play, Color.WHITE, 28), flp(dp(28), dp(28), Gravity.CENTER))
                    f.setOnClickListener { MediaViewerActivity.open(this, media, i) }
                    grid.addView(f, GridLayout.LayoutParams().apply { width = size - dp(2); height = size - dp(2); setMargins(dp(1), dp(1), dp(1), dp(1)) })
                }
                content.addView(grid)
            }
            1 -> {
                val links = links(items).reversed()
                if (links.isEmpty()) { content.addView(empty(R.drawable.ic_link, tr("אין קישורים בשיחה", "No links in this chat"), null)); return }
                links.forEach { u ->
                    content.addView(Ui.row(this, R.drawable.ic_link, android.net.Uri.parse(u).host ?: u, u) { Markdown.openLink(this, u) }.apply {
                        setOnLongClickListener { Ui.copy(this@GalleryActivity, u); true }
                    })
                }
            }
            else -> {
                val files = files(items).reversed()
                if (files.isEmpty()) { content.addView(empty(R.drawable.ic_file, tr("אין קבצים בשיחה", "No files in this chat"), null)); return }
                files.forEach { p ->
                    val f = File(p)
                    content.addView(Ui.row(this, R.drawable.ic_file, f.name, p.replace(AgyInstaller.homeDir(this).absolutePath, "~") + " · " + android.text.format.Formatter.formatShortFileSize(this, f.length())) {
                        Media.openFile(this, p)
                    })
                }
            }
        }
    }
}

/** Saved prompts, offered with "/" and the bookmark button in the chat. */
class TemplatesActivity : BaseActivity() {
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.TopBar(this, tr("תבניות", "Templates")))
        col.addView(Ui.text(this, tr("טקסטים שמורים שמופיעים בשיחה כשמקלידים / או לוחצים על הסימנייה.", "Saved texts that appear in the chat when you type / or tap the bookmark."), 14f, T.muted).apply {
            setPadding(dp(20), dp(12), dp(20), dp(4))
        })
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(90)) }
        col.addView(ScrollView(this).apply { addView(box) }, lp(MATCH, 0, 1f))
        root.addView(col)
        root.addView(fab(this, R.drawable.ic_add, tr("תבנית חדשה", "New template")) { edit(-1) }, flp(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(18), 0, dp(18), dp(22)) })
        setContentView(root)
        render()
    }

    private fun render() {
        box.removeAllViews()
        Settings.templates(this).forEachIndexed { i, t ->
            box.addView(Ui.row(this, R.drawable.ic_bookmark, t.title, t.text.take(140), Ui.iconButton(this, R.drawable.ic_delete, tr("מחיקה", "Delete"), T.muted) {
                Settings.setTemplates(this, Settings.templates(this).filterIndexed { j, _ -> j != i }); render()
            }, iconTint = T.primary) { edit(i) })
        }
    }

    private fun edit(index: Int) {
        val list = Settings.templates(this).toMutableList()
        val cur = list.getOrNull(index)
        val title = Ui.field(this, tr("שם קצר", "Short name"), cur?.title ?: "")
        val text = Ui.field(this, tr("הטקסט שיישלח", "Text to send"), cur?.text ?: "", multiLine = true)
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(title); addView(text, lp(MATCH, WRAP).apply { topMargin = dp(10) }) }
        Dlg(this).title(if (cur == null) tr("תבנית חדשה", "New template") else tr("עריכת תבנית", "Edit template")).view(v)
            .negative(tr("ביטול", "Cancel"))
            .positiveCheck(tr("שמירה", "Save")) {
                val t = Settings.Template(title.text.toString().trim(), text.text.toString().trim())
                if (t.title.isEmpty() || t.text.isEmpty()) { snack(tr("צריך שם וטקסט", "Name and text are required")); false }
                else { if (cur == null) list += t else list[index] = t; Settings.setTemplates(this, list); render(); true }
            }.show()
    }
}

/** Centred empty-state message with an icon. */
fun BaseActivity.empty(icon: Int, title: String, sub: String?): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(32), dp(72), dp(32), dp(32))
    val circle = FrameLayout(this@empty).apply { background = Ui.circle(T.primarySoft) }
    circle.addView(Ui.icon(this@empty, icon, T.primary, 40), flp(dp(40), dp(40), Gravity.CENTER))
    addView(circle, lp(dp(96), dp(96)))
    addView(Ui.text(this@empty, title, 18f, T.text, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(6)) })
    if (sub != null) addView(Ui.text(this@empty, sub, 14.5f, T.muted).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) })
}

/** Floating action button. */
fun fab(a: Activity, icon: Int, desc: String, onClick: () -> Unit): View = FrameLayout(a).apply {
    background = Ui.ripple(Ui.round(T.primary, a.dpf(20f)), a.dpf(20f))
    elevation = a.dpf(6f)
    contentDescription = desc
    addView(Ui.icon(a, icon, T.onPrimary, 26), flp(a.dp(26), a.dp(26), Gravity.CENTER))
    layoutParams = FrameLayout.LayoutParams(a.dp(60), a.dp(60))
    setOnClickListener { onClick() }
    setOnLongClickListener { Snack.show(a, desc); true }
}
