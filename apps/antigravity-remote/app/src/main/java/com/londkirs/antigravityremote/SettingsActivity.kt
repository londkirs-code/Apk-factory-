package com.londkirs.antigravityremote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File
import java.util.UUID

/** All settings in one place; the terminal, the hub web UI and remote connections live under "Advanced". */
class SettingsActivity : BaseActivity() {
    companion object { const val EXTRA_SECTION = "section"; private const val REQ_WALL = 71 }

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_SECTION) == "templates") { startActivity(Intent(this, TemplatesActivity::class.java)); finish(); return }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Ui.TopBar(this, tr("הגדרות", "Settings")))
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(32)) }
        col.addView(ScrollView(this).apply { addView(box) }, lp(MATCH, 0, 1f))
        setContentView(col)
    }

    override fun onResume() { super.onResume(); render() }

    private fun toggle(v: Boolean, f: (Boolean) -> Unit) = Toggle(this, v, f)

    private fun render() {
        box.removeAllViews()
        // Profile card
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        head.addView(Ui.agentAvatar(this, 64), lp(dp(64), dp(64)))
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPaddingRelative(dp(16), 0, 0, 0) }
        t.addView(Ui.text(this, "Antigravity", 20f, T.text, bold = true))
        val ver = AgyInstaller.installedVersion(this)
        t.addView(Ui.text(this, if (AgyInstaller.isInstalled(this)) tr("הסוכן מותקן", "Agent installed") + (ver?.let { " · $it" } ?: "") else tr("הסוכן לא מותקן", "Agent not installed"), 14f, T.muted))
        head.addView(t, lp(0, WRAP, 1f))
        box.addView(head)
        box.addView(Ui.divider(this))

        box.addView(Ui.sectionTitle(this, tr("מראה", "Appearance")))
        val themeName = when (Settings.theme(this)) { "light" -> tr("בהיר", "Light"); "dark" -> tr("כהה", "Dark"); else -> tr("לפי המערכת", "System") }
        box.addView(Ui.row(this, R.drawable.ic_moon, tr("ערכת נושא", "Theme"), themeName) {
            val cur = Settings.theme(this)
            Sheet(this).title(tr("ערכת נושא", "Theme"))
                .item(R.drawable.ic_phone, tr("לפי המערכת", "System"), checked = cur == "system") { Settings.setTheme(this, "system"); recreate() }
                .item(R.drawable.ic_wallpaper, tr("בהיר", "Light"), checked = cur == "light") { Settings.setTheme(this, "light"); recreate() }
                .item(R.drawable.ic_moon, tr("כהה", "Dark"), checked = cur == "dark") { Settings.setTheme(this, "dark"); recreate() }
                .show()
        })
        val scales = listOf(0.9f to tr("קטן", "Small"), 1f to tr("רגיל", "Normal"), 1.15f to tr("גדול", "Large"), 1.3f to tr("גדול מאוד", "Extra large"))
        val curScale = Settings.fontScale(this)
        box.addView(Ui.row(this, R.drawable.ic_textsize, tr("גודל הטקסט", "Text size"), scales.minByOrNull { Math.abs(it.first - curScale) }?.second) {
            val s = Sheet(this).title(tr("גודל הטקסט", "Text size"))
            scales.forEach { (v, name) -> s.item(R.drawable.ic_textsize, name, checked = Math.abs(v - curScale) < 0.01f) { Settings.setFontScale(this, v); recreate() } }
            s.show()
        })
        box.addView(Ui.row(this, R.drawable.ic_wallpaper, tr("רקע לשיחות", "Chat wallpaper"), tr("לכל השיחות שאין להן רקע משלהן", "For chats without their own")) {
            WallpaperPicker.show(this, Settings.wallpaper(this), false, onPick = { Settings.setWallpaper(this, it); snack(tr("הרקע עודכן", "Wallpaper updated")) }, onGallery = {
                runCatching { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_WALL) }
            })
        })

        box.addView(Ui.sectionTitle(this, tr("צ'אט", "Chat")))
        box.addView(Ui.row(this, R.drawable.ic_send, tr("Enter שולח", "Enter sends"), tr("מקש Enter במקלדת שולח את ההודעה", "The keyboard's Enter key sends the message"),
            toggle(Settings.enterSends(this)) { Settings.setEnterSends(this, it) }))
        box.addView(Ui.row(this, R.drawable.ic_bolt, tr("הרצת פקודות אוטומטית", "Run commands automatically"),
            tr("כשכבוי, הסוכן מבקש אישור לפני כל פקודה", "When off, the agent asks before each command"),
            toggle(Settings.autoRun(this)) { Settings.setAutoRun(this, it) }))
        box.addView(Ui.row(this, R.drawable.ic_mic, tr("שליחה מיידית של הודעה קולית", "Send voice messages right away"),
            tr("כשכבוי, אפשר לתקן את התמלול לפני השליחה", "When off, you can fix the transcript before sending"),
            toggle(Settings.voiceSendsNow(this)) { Settings.setVoiceSendsNow(this, it) }))
        box.addView(Ui.row(this, R.drawable.ic_sparkle, tr("מודל", "Model"), ChatEngine.currentModel(this)?.name ?: tr("ברירת המחדל של החשבון", "Account default")) { pickModel() })
        box.addView(Ui.row(this, R.drawable.ic_bookmark, tr("תבניות", "Templates"), tr("טקסטים שמורים לשליחה מהירה", "Saved texts for quick sending")) {
            startActivity(Intent(this, TemplatesActivity::class.java))
        })
        box.addView(Ui.row(this, R.drawable.ic_star, tr("הודעות מסומנות בכוכב", "Starred messages")) { StarredActivity.open(this, null) })

        box.addView(Ui.sectionTitle(this, tr("התראות", "Notifications")))
        box.addView(Ui.row(this, R.drawable.ic_bell, tr("התראות מהסוכן", "Agent notifications"),
            tr("כשהסוכן מסיים או מחכה לאישור, עם תשובה מתוך ההתראה", "When the agent finishes or needs approval, with reply from the notification"),
            toggle(Settings.notifications(this)) { Settings.setNotifications(this, it) }))
        if (Build.VERSION.SDK_INT >= 26) box.addView(Ui.row(this, R.drawable.ic_settings, tr("צליל ורטט", "Sound and vibration"), tr("בהגדרות המערכת", "In system settings")) {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)) }
        })

        box.addView(Ui.sectionTitle(this, tr("עזרה", "Help")))
        box.addView(Ui.row(this, R.drawable.ic_help, tr("מדריך הפעלה", "Setup guide"), tr("התקנה, התחברות ובחירת מודל, צעד אחר צעד", "Install, sign in and pick a model, step by step")) {
            startActivity(Intent(this, SetupActivity::class.java))
        })
        box.addView(Ui.row(this, R.drawable.ic_info, tr("איך זה עובד?", "How does it work?")) {
            Ui.info(this, tr("איך זה עובד?", "How does it work?"), tr(
                "• Antigravity רץ בתוך הטלפון שלכם, והאפליקציה מדברת איתו כמו בצ'אט.\n\n• הסוכן יכול לכתוב קוד, להריץ פקודות וליצור קבצים בתיקייה ~/projects. כשהוא מבקש אישור תופיע הודעה עם כפתורי אישור ודחייה.\n\n• הודעה קולית מתומללת בטלפון ונשלחת כטקסט, כי המודל לא שומע קול.\n\n• תמונות שהסוכן יוצר או מצלם מופיעות בשיחה. נגיעה פותחת אותן במסך מלא.\n\n• הטרמינל, הממשק הגרפי המלא וחיבור למחשב נמצאים ב\"מתקדם\".",
                "• Antigravity runs inside your phone, and the app talks to it like a chat.\n\n• The agent can write code, run commands and create files in ~/projects. When it asks for approval you get a message with Allow and Deny buttons.\n\n• Voice messages are transcribed on the phone and sent as text, because the model cannot hear audio.\n\n• Pictures the agent creates or captures appear in the chat. Tap to view full screen.\n\n• The terminal, the full visual UI and connecting to a computer are under \"Advanced\"."))
        })
        box.addView(Ui.row(this, R.drawable.ic_refresh, tr("להציג שוב את הטיפים", "Show tips again")) { Settings.resetTips(this); snack(tr("הטיפים יופיעו שוב", "Tips will show again")) })

        box.addView(Ui.sectionTitle(this, tr("מתקדם", "Advanced")))
        box.addView(Ui.row(this, R.drawable.ic_terminal, tr("טרמינל של הסוכן", "Agent terminal"), tr("agy בשורת פקודה", "agy on the command line")) {
            startActivity(Intent(this, TerminalActivity::class.java).putExtra(TerminalActivity.EXTRA_NEW, TerminalService.KIND_AGY))
        })
        box.addView(Ui.row(this, R.drawable.ic_terminal, tr("מסוף (Shell)", "Shell"), null) {
            startActivity(Intent(this, TerminalActivity::class.java).putExtra(TerminalActivity.EXTRA_NEW, TerminalService.KIND_SHELL))
        })
        box.addView(Ui.row(this, R.drawable.ic_globe, tr("ממשק גרפי מלא", "Full visual UI"), tr("הממשק של Google בתוך האפליקציה", "Google's interface inside the app")) {
            snack(tr("מפעיל…", "Starting…"))
            HubClient.ensureRunning(this) { ok ->
                if (ok) startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, TerminalService.hubUiUrl ?: TerminalService.HUB_URL))
                else startActivity(Intent(this, TerminalActivity::class.java))
            }
        })
        box.addView(Ui.row(this, R.drawable.ic_computer, tr("חיבור ל־Antigravity במחשב", "Connect to Antigravity on a computer"), tr("Remote Control, code-server ועוד", "Remote Control, code-server and more")) {
            startActivity(Intent(this, ConnectionsActivity::class.java))
        })
        if (AgyInstaller.isSupportedDevice()) box.addView(Ui.row(this, R.drawable.ic_download, tr("עדכון הסוכן", "Update the agent"), ver) {
            startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_STEP, 1))
        })
        box.addView(Ui.row(this, R.drawable.ic_stop, tr("עצירת הסוכן ברקע", "Stop the agent in the background"), tr("ייפתח מחדש כשתיכנסו לשיחה", "It restarts when you open a chat")) {
            Ui.confirm(this, tr("לעצור את הסוכן ואת כל מה שרץ ברקע?", "Stop the agent and everything running in the background?"), tr("עצירה", "Stop"), true) {
                startService(Intent(this, TerminalService::class.java).setAction(TerminalService.ACTION_STOP))
                snack(tr("נעצר", "Stopped"))
            }
        })

        box.addView(Ui.sectionTitle(this, tr("אודות", "About")))
        val appVer = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        box.addView(Ui.row(this, R.drawable.ic_info, tr("גרסת האפליקציה", "App version"), appVer))
    }

    private fun pickModel() {
        Thread {
            HubClient.attach(this)
            val l = if (HubClient.isAlive()) ChatEngine.loadModels() else emptyList()
            runOnUiThread {
                if (l.isEmpty()) { snack(tr("הסוכן צריך לפעול כדי לטעון מודלים. פתחו שיחה ונסו שוב.", "The agent must be running to load models. Open a chat and try again.")); return@runOnUiThread }
                val cur = ChatEngine.currentModel(this)
                val s = Sheet(this).title(tr("מודל", "Model"))
                l.forEach { m ->
                    s.item(R.drawable.ic_sparkle, m.name, m.quota?.let { tr("נותרו ${(it * 100).toInt()}%", "${(it * 100).toInt()}% left") }, checked = m == cur) {
                        Settings.setModel(this, m.value.toString()); Thread { runCatching { HubClient.rememberModel(m.value) } }.start(); render()
                    }
                }
                s.show()
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WALL && resultCode == RESULT_OK) data?.data?.let { u ->
            Thread {
                val f = File(Wallpapers.dir(this), "global.jpg")
                runCatching { contentResolver.openInputStream(u)!!.use { i -> f.outputStream().use { i.copyTo(it) } } }
                runOnUiThread { Settings.setWallpaper(this, "image:" + f.absolutePath); snack(tr("הרקע עודכן", "Wallpaper updated")) }
            }.start()
        }
    }
}

/** Antigravity running on a computer: Remote Control, code-server, tunnels and other addresses. */
class ConnectionsActivity : BaseActivity() {
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = android.widget.FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = Ui.TopBar(this, tr("חיבור למחשב", "Connect to a computer"))
        top.action(R.drawable.ic_paste, tr("הדבקת קישור לסשן", "Paste a session link")) { pasteLink() }
        top.action(R.drawable.ic_help, tr("איך מפעילים במחשב", "How to enable it on the computer")) { guide() }
        col.addView(top)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(96)) }
        col.addView(ScrollView(this).apply { addView(box) }, lp(MATCH, 0, 1f))
        root.addView(col)
        root.addView(fab(this, R.drawable.ic_add, tr("חיבור חדש", "New connection")) { edit(null) },
            android.widget.FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(18), 0, dp(18), dp(22)) })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        box.removeAllViews()
        box.addView(Ui.text(this, tr("פותחים Antigravity שרץ במחשב שלכם. במחשב: Settings ← Enable Remote Control, ואז בוחרים כאן את החיבור הרשמי.",
            "Open Antigravity running on your computer. On the computer: Settings → Enable Remote Control, then pick the official connection here."), 14.5f, T.muted).apply {
            setPadding(dp(8), dp(4), dp(8), dp(12)); setLineSpacing(0f, 1.2f)
        })
        Connections.load(this).forEach { c ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.ripple(Ui.round(T.surface, dpf(20f), T.divider, 1), dpf(20f))
                setPadding(dp(16), dp(14), dp(16), dp(12))
                setOnClickListener { open(c) }
                setOnLongClickListener { edit(c); true }
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(Ui.icon(this, if (c.url.startsWith(Connections.OFFICIAL_URL)) R.drawable.ic_sparkle else R.drawable.ic_computer, T.primary, 24), lp(dp(24), dp(24)).apply { marginEnd = dp(12) })
            val tt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            tt.addView(Ui.text(this, c.name, 17f, T.text, bold = true))
            tt.addView(Ui.text(this, c.url, 12.5f, T.muted, lines = 1).apply { textDirection = View.TEXT_DIRECTION_LTR })
            head.addView(tt, lp(0, WRAP, 1f))
            head.addView(Ui.iconButton(this, R.drawable.ic_edit, tr("עריכה", "Edit"), T.muted) { edit(c) })
            card.addView(head)
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            btns.addView(Ui.button(this, tr("פתיחה באפליקציה", "Open in app"), Ui.Style.PRIMARY) { openInApp(c.url) }, lp(0, WRAP, 1f))
            btns.addView(View(this), lp(dp(8), 1))
            btns.addView(Ui.button(this, tr("ב־Chrome", "In Chrome"), Ui.Style.TONAL) { Connections.openInChrome(this, c.url) }, lp(0, WRAP, 1f))
            card.addView(btns)
            box.addView(card, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
        }
    }

    private fun open(c: Connection) = if (c.chrome) Connections.openInChrome(this, c.url) else openInApp(c.url)
    private fun openInApp(url: String) = startActivity(Intent(this, WebActivity::class.java).putExtra(WebActivity.EXTRA_URL, url))

    private fun pasteLink() {
        val url = Connections.findUrl(Ui.clipboard(this))
        if (url == null) snack(tr("אין קישור בלוח", "No link in the clipboard")) else openInApp(url)
    }

    private fun guide() = Ui.info(this, getString(R.string.guide_title), getString(R.string.guide_body))

    private fun edit(existing: Connection?) {
        val name = Ui.field(this, tr("שם", "Name"), existing?.name ?: "")
        val url = Ui.field(this, "https://…", existing?.url ?: "", ltr = true)
        var chrome = existing?.chrome == true
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        v.addView(name); v.addView(url, lp(MATCH, WRAP).apply { topMargin = dp(10) })
        v.addView(Ui.row(this, R.drawable.ic_open, tr("לפתוח ב־Chrome", "Open in Chrome"), tr("מומלץ אם Google חוסם התחברות בתוך האפליקציה", "Recommended if Google blocks in-app sign-in"),
            Toggle(this, chrome) { chrome = it }).apply { setPadding(0, dp(10), 0, 0) })
        val d = Dlg(this).title(if (existing == null) tr("חיבור חדש", "New connection") else tr("עריכת חיבור", "Edit connection")).view(v)
            .negative(tr("ביטול", "Cancel"))
            .positiveCheck(tr("שמירה", "Save")) {
                val normalized = Connections.normalize(url.text.toString())
                if (normalized == null) { url.error = tr("כתובת לא תקינה", "Invalid address"); false }
                else {
                    val label = name.text.toString().trim().ifEmpty { Uri.parse(normalized).host ?: normalized }
                    val list = Connections.load(this)
                    val item = Connection(existing?.id ?: UUID.randomUUID().toString(), label, normalized, chrome)
                    val i = list.indexOfFirst { it.id == item.id }
                    if (i >= 0) list[i] = item else list += item
                    Connections.save(this, list); render(); true
                }
            }
        if (existing != null) d.neutral(tr("מחיקה", "Delete")) {
            Ui.confirm(this, tr("למחוק את החיבור?", "Delete this connection?"), tr("מחיקה", "Delete"), true) {
                Connections.save(this, Connections.load(this).filterNot { it.id == existing.id }); render()
            }
        }
        d.show()
    }
}
