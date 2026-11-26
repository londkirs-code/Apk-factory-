package com.londkirs.apkbridge

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.launch

class BridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Bridge.init(this)
        PairingService.createChannel(this)
    }
}

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var filesText: TextView
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var manualBox: LinearLayout
    private lateinit var host: EditText
    private lateinit var pairPort: EditText
    private lateinit var pin: EditText
    private lateinit var connectionPage: LinearLayout
    private lateinit var installPage: LinearLayout
    private lateinit var pageScroll: ScrollView
    private lateinit var pageTitle: TextView
    private lateinit var navHolder: android.widget.FrameLayout
    private var currentTab = BottomNav.Tab.CONNECT
    private lateinit var pairControls: LinearLayout
    private lateinit var repairToggle: Button
    private lateinit var pairStateText: TextView
    private val optionBoxes = mutableMapOf<String, CheckBox>()
    private lateinit var guideTitle: TextView
    private lateinit var guideInfo: TextView
    private lateinit var guideButton: Button
    private lateinit var installButton: Button
    private lateinit var cancelButton: Button
    private lateinit var openAppButton: Button
    private lateinit var logHeader: TextView
    private lateinit var logPreview: TextView
    private lateinit var logBody: LinearLayout
    private lateinit var a11yText: TextView
    private lateinit var a11yButton: Button
    private var shownInstallSeq = 0
    private var previewJob: kotlinx.coroutines.Job? = null
    private var previewText: String? = null
    private lateinit var optionsSummary: TextView
    private val busyViews = mutableListOf<View>()

    private var selected: List<Uri> = emptyList()

    private val stateListener: (BridgeState) -> Unit = { render(it) }
    private val logListener: (String) -> Unit = { line ->
        if (line.isEmpty()) logText.text = "" else {
            if (logText.text.isNotEmpty()) logText.append("\n")
            logText.append(line)
        }
        logPreview.text = line
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private val wifiPanelListener: () -> Unit = { openWifiPanel() }

    /** First launch: offer to install the bundled Wi‑Fi helper with Android's normal installer. */
    private fun offerWifiHelperOnce() {
        if (Bridge.helperOffered || Bridge.isWifiHelperInstalled() || !Bridge.isWifiHelperBundled()) return
        Bridge.helperOffered = true
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("להתקין את עזר ה־Wi‑Fi?", "Install the Wi‑Fi helper?"))
            .setMessage(
                tr("אפליקציה זעירה שמאפשרת לאפליקציית הבוס להדליק Wi‑Fi לבד כשהוא כבוי (ניפוי באגים אלחוטי עובד רק עם Wi‑Fi).\n\n", "A tiny app that lets The Boss turn Wi‑Fi on by itself when it's off (wireless debugging only works with Wi‑Fi).\n\n") +
                    tr("ההתקנה נעשית דרך מתקין האפליקציות הרגיל של Android — בלי ניפוי באגים. ייתכן שתתבקש פעם אחת לאשר לאפליקציית הבוס להתקין אפליקציות.\n\n", "It's installed through Android's regular app installer — no debugging needed. You may be asked once to allow The Boss to install apps.\n\n") +
                    tr("אפשר להתקין גם מאוחר יותר: ⚙ הגדרות ← צימוד וחיבור.", "You can also install it later: ⚙ Settings → Pairing & connection.")
            )
            .setPositiveButton(tr("התקן", "Install")) { _, _ -> HelperInstaller.start(this) }
            .setNegativeButton(tr("לא עכשיו", "Not now"), null)
            .show()
    }

    private fun confirmKeepAdb() {
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("ADB קבוע (גם בלי Wi‑Fi)", "Persistent ADB (even without Wi‑Fi)"))
            .setMessage(
                tr("הבוס יעביר את ADB למצב רשת רגיל בפורט 5555 (כמו adb tcpip 5555), ויתחבר אליו דרך הכתובת הפנימית של המכשיר — כך הוא עובד גם כשה־Wi‑Fi כבוי.\n\n", "The Boss switches ADB to regular network mode on port 5555 (like adb tcpip 5555) and connects to it through the device's internal address — so it works even when Wi‑Fi is off.\n\n") +
                    tr("• בפעם הראשונה Android ישאל \"לאפשר ניפוי באגים?\" — סמן \"אפשר תמיד\" ואשר.\n", "• The first time Android asks \"Allow debugging?\" — check \"Always allow\" and confirm.\n") +
                    tr("• ניפוי באגים ב־USB יודלק (נדרש כדי ש־ADB יישאר פעיל).\n", "• USB debugging will be turned on (needed so ADB stays on).\n") +
                    tr("• המצב נמחק בהפעלה מחדש של המכשיר, ומתחדש אוטומטית בחיבור הבא דרך Wi‑Fi.\n", "• The mode is cleared when the device restarts, and renewed automatically on the next Wi‑Fi connection.\n") +
                    tr("• כשה־Wi‑Fi דלוק, מכשירים אחרים ברשת יוכלו לנסות להתחבר לפורט 5555 — אבל כל חיבור חדש דורש אישור שלך במסך.\n\n", "• While Wi‑Fi is on, other devices on the network could try to connect to port 5555 — but every new connection needs your approval on screen.\n\n") +
                    tr("נדרש חיבור ADB פעיל עכשיו (Wi‑Fi וצימוד).", "An active ADB connection is needed now (Wi‑Fi and pairing).")
            )
            .setPositiveButton(tr("הפעל", "Turn on")) { _, _ -> Bridge.enableKeepAdb() }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
            .show()
    }

    private fun openWifiPanel() {
        try {
            startActivity(Compat.wifiPanelIntent())
        } catch (_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        selected = savedInstanceState?.getParcelableArrayList<Uri>(KEY_SELECTED).orEmpty()
        shownInstallSeq = savedInstanceState?.getInt(KEY_SHOWN_SEQ, Bridge.state.installSeq) ?: Bridge.state.installSeq
        buildUi()
        // Start on Install once the device is paired, otherwise on Connection.
        val requested = savedInstanceState?.getString(KEY_TAB) ?: intent?.getStringExtra(BottomNav.EXTRA_TAB)
        showTab(requested?.let { runCatching { BottomNav.Tab.valueOf(it) }.getOrNull() }
            ?: if (Bridge.isPaired || selected.isNotEmpty()) BottomNav.Tab.INSTALL else BottomNav.Tab.CONNECT)
        logText.text = Bridge.logSnapshot()
        logPreview.text = Bridge.lastLogLine()
        applyLogExpanded()
        if (savedInstanceState == null) {
            if (intent?.getBooleanExtra(EXTRA_PICK_FILES, false) == true) chooseFiles()
            else if (intent?.getBooleanExtra(EXTRA_OPEN_GUIDE, false) == true) startActivity(Intent(this, GuideActivity::class.java))
            else if (OnboardingActivity.shouldShow(this)) startActivity(Intent(this, OnboardingActivity::class.java))
        }
        if (savedInstanceState == null) {
            handleIncoming(intent)
            offerWifiHelperOnce()
        }
        renderSelection()
    }

    override fun onStart() {
        super.onStart()
        Bridge.addStateListener(stateListener)
        Bridge.addLogListener(logListener)
        Bridge.addWifiPanelListener(wifiPanelListener)
        refreshConnectionInfo()
    }

    override fun onStop() {
        Bridge.removeStateListener(stateListener)
        Bridge.removeLogListener(logListener)
        Bridge.removeWifiPanelListener(wifiPanelListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Celebration.onResume(this)
        HelperInstaller.resumeIfPermitted(this)
        refreshConnectionInfo()
        refreshOptionsSummary()
        applyLogExpanded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Celebration.onFocus(this, hasFocus)
    }

    override fun onPause() {
        Celebration.onPause(this)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(KEY_SELECTED, ArrayList(selected))
        outState.putInt(KEY_SHOWN_SEQ, shownInstallSeq)
        outState.putString(KEY_TAB, currentTab.name)
    }

    /** Keypad phones: MENU opens Settings; the number keys jump to the main actions. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val focusInText = currentFocus is EditText
        // Keypad phones: 1–6 switch tabs (like the bottom bar), 0 opens/closes the log, MENU opens Settings.
        val tabs = BottomNav.Tab.values()
        when {
            keyCode == android.view.KeyEvent.KEYCODE_MENU -> { onTab(BottomNav.Tab.SETTINGS); return true }
            !focusInText && keyCode in android.view.KeyEvent.KEYCODE_1..android.view.KeyEvent.KEYCODE_6 -> {
                onTab(tabs[keyCode - android.view.KeyEvent.KEYCODE_1]); return true
            }
            !focusInText && keyCode == android.view.KeyEvent.KEYCODE_0 -> {
                if (currentTab != BottomNav.Tab.CONNECT) showTab(BottomNav.Tab.CONNECT)
                Bridge.logExpanded = !Bridge.logExpanded; applyLogExpanded(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(BottomNav.EXTRA_TAB)?.let { name ->
            runCatching { BottomNav.Tab.valueOf(name) }.getOrNull()?.let(::showTab)
        }
        handleIncoming(intent)
        if (intent.getBooleanExtra(EXTRA_PICK_FILES, false)) chooseFiles()
    }

    /** Bottom bar: Connection/Install are pages here; the other tabs open their own screens. */
    private fun onTab(tab: BottomNav.Tab) {
        when (tab) {
            BottomNav.Tab.CONNECT, BottomNav.Tab.INSTALL -> showTab(tab)
            else -> BottomNav.navigate(this, tab)
        }
    }

    private fun showTab(tab: BottomNav.Tab) {
        if (tab != BottomNav.Tab.CONNECT && tab != BottomNav.Tab.INSTALL) return
        currentTab = tab
        connectionPage.visibility = if (tab == BottomNav.Tab.CONNECT) View.VISIBLE else View.GONE
        installPage.visibility = if (tab == BottomNav.Tab.INSTALL) View.VISIBLE else View.GONE
        pageTitle.text = if (tab == BottomNav.Tab.CONNECT) tr("חיבור לטלפון", "Connect to the phone") else tr("התקנת אפליקציות", "Install apps")
        navHolder.removeAllViews()
        navHolder.addView(BottomNav.build(this, tab, ::onTab))
        pageScroll.scrollTo(0, 0)
    }

    // region UI construction

    private fun buildUi() {
        // ── Header: icon, name and the current page title.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = Lang.direction
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = tr("הבוס", "The Boss")
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        })
        pageTitle = TextView(this).apply { textSize = 13f; setTextColor(MUTED) }
        titles.addView(pageTitle)
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // ── Pages
        val pages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.direction
            setPadding(dp(14), dp(4), dp(14), dp(16))
        }
        connectionPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        installPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pages.addView(connectionPage)
        pages.addView(installPage)
        buildConnectionPage(connectionPage)
        buildInstallPage(installPage)
        pages.addView(TextView(this).apply {
            text = tr("גרסה ${versionName()}", "Version ${versionName()}")
            textSize = 11f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
        }, matchWrap())
        pageScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(pages)
        }

        // ── Status strip (always visible above the bottom bar): what is happening right now.
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.direction
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(0xFFFFF1E3.toInt()) }
        }
        statusText = TextView(this).apply {
            textSize = 13.5f
            setTextColor(INK)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        status.addView(statusText)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        status.addView(progressBar, matchWrap(2))
        val statusButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cancelButton = button(tr("בטל פעולה", "Cancel action"), primary = false) { Bridge.cancelCurrent() }.apply { visibility = View.GONE }
        openAppButton = button(tr("פתח את האפליקציה שהותקנה", "Open the installed app"), primary = true) { openInstalledApp() }.apply { visibility = View.GONE }
        statusButtons.addView(cancelButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        statusButtons.addView(openAppButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        status.addView(statusButtons)

        navHolder = android.widget.FrameLayout(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
        }
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(pageScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(navHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        Compat.applySystemBarInsets(this, root, includeIme = true)
        setContentView(root)
    }

    /** 🔗 Connection: state → pairing → log. */
    private fun buildConnectionPage(page: LinearLayout) {
        // Card 1: connection state + a single test button.
        val state = card(page)
        state.addView(heading(tr("מצב החיבור", "Connection status")), matchWrap())
        connectionText = body("")
        state.addView(connectionText, matchWrap(8))
        state.addView(button(tr("בדוק חיבור", "Check connection"), primary = true) { Bridge.testConnection() }.busyAware(), matchWrap(0))

        // Card 2: step-by-step guide — the section for this phone's Android version is open.
        buildGuide(page)

        // Card 3: pairing — needed once.
        val pair = card(page)
        pair.addView(heading(tr("צימוד (פעם אחת)", "Pairing (once)")), matchWrap())
        pairStateText = body("")
        pair.addView(pairStateText, matchWrap(6))
        // Once paired, the pairing controls are folded away behind "pair again".
        repairToggle = button(tr("צימוד מחדש / מכשיר אחר ▾", "Pair again / another device ▾"), primary = false) {
            pairControls.visibility = if (pairControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        pair.addView(repairToggle, matchWrap(0))
        pairControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        pair.addView(pairControls)
        a11yText = body("")
        pairControls.addView(a11yText, matchWrap(6))
        a11yButton = button(tr("הפעל צימוד אוטומטי", "Turn on automatic pairing"), primary = true) { openAccessibilitySetup() }
        pairControls.addView(a11yButton, matchWrap())
        val notifyPair = button(tr("צימוד עם קוד דרך התראה", "Pair with code via notification"), primary = false) { startNotificationPairing() }.busyAware()
        pairControls.addView(notifyPair, matchWrap())
        if (!Compat.hasWirelessDebugging) {
            // Android 8–10: no pairing codes; the state card explains classic ADB on port 5555.
            pair.visibility = View.GONE
        }
        pairControls.addView(button(tr("פתח אפשרויות למפתחים", "Open Developer options"), primary = false) { openDeveloperOptions() }, matchWrap())
        pairControls.addView(button(tr("צימוד ידני / מכשיר אחר ▾", "Manual pairing / another device ▾"), primary = false) {
            manualBox.visibility = if (manualBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, matchWrap(0))
        manualBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(0, dp(10), 0, 0) }
        manualBox.addView(note(tr("127.0.0.1 הוא הטלפון הזה. אפשר להזין IP של טלפון אחר באותה רשת. פורט הצימוד נמצא לבד אם משאירים ריק.", "127.0.0.1 is this phone. You can enter the IP of another phone on the same network. The pairing port is found automatically if left empty.")), matchWrap(6))
        host = field(tr("כתובת יעד (127.0.0.1)", "Target address (127.0.0.1)"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).apply { setText(Bridge.targetHost) }
        pairPort = field(tr("פורט צימוד (לא חובה)", "Pairing port (optional)"), InputType.TYPE_CLASS_NUMBER)
        pin = field(tr("קוד צימוד בן 6 ספרות", "6-digit pairing code"), InputType.TYPE_CLASS_NUMBER)
        manualBox.addView(host, matchWrap())
        manualBox.addView(pairPort, matchWrap())
        manualBox.addView(pin, matchWrap())
        manualBox.addView(button(tr("בצע צימוד", "Pair"), primary = true) { manualPair() }.busyAware(), matchWrap())
        manualBox.addView(button(tr("שמור כתובת יעד", "Save target address"), primary = false) {
            Bridge.targetHost = host.text.toString()
            Bridge.savedConnectPort = 0
            host.setText(Bridge.targetHost)
            refreshConnectionInfo()
            toast(tr("כתובת היעד נשמרה.", "Target address saved."))
        }, matchWrap(0))
        pairControls.addView(manualBox)

        // Card 3: log — collapsed by default.
        val logCard = card(page)
        logHeader = heading("").apply {
            Compat.focusRing(this, 0xFF75451F.toInt(), 8f)
            setOnClickListener { Bridge.logExpanded = !Bridge.logExpanded; applyLogExpanded() }
        }
        logCard.addView(logHeader, matchWrap(2))
        logPreview = TextView(this).apply {
            Compat.focusRing(this, 0xFF75451F.toInt(), 8f)
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(MUTED)
            textDirection = View.TEXT_DIRECTION_ANY_RTL
            setOnClickListener { Bridge.logExpanded = true; applyLogExpanded() }
        }
        logCard.addView(logPreview, matchWrap(2))
        logBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        logText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF4A3F36.toInt())
            setTextIsSelectable(true)
            textDirection = View.TEXT_DIRECTION_ANY_RTL
        }
        logScroll = ScrollView(this).apply {
            addView(logText)
            isNestedScrollingEnabled = true
        }
        logBody.addView(logScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240)).apply { bottomMargin = dp(8) })
        val logButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        logButtons.addView(button(tr("העתק יומן", "Copy log"), primary = false) { copyLog() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        logButtons.addView(button(tr("נקה", "Clear"), primary = false) { Bridge.clearLog() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        logBody.addView(logButtons, matchWrap(0))
        logCard.addView(logBody, matchWrap(0))
    }

    /** 📖 Short card that opens the step-by-step guide; folds to one line once the phone is paired. */
    private fun buildGuide(page: LinearLayout) {
        val guide = card(page)
        guideTitle = heading(tr("📖 איך מתחברים?", "📖 How do I connect?"))
        guide.addView(guideTitle, matchWrap(2))
        guideInfo = body(
            tr("בטלפון שלך: אנדרואיד ${Build.VERSION.RELEASE}\n", "Your phone: Android ${Build.VERSION.RELEASE}\n") +
                if (Compat.hasWirelessDebugging) tr("מתאים לך: חיבור בלי מחשב (כ־5 דקות)", "Best for you: connecting without a computer (about 5 minutes)") else tr("מתאים לך: חיבור עם מחשב (כ־10 דקות, פעם אחת)", "Best for you: connecting with a computer (about 10 minutes, once)")
        )
        guide.addView(guideInfo, matchWrap(10))
        guideButton = button(tr("▶  התחל מדריך שלב אחר שלב", "▶  Start the step-by-step guide"), primary = true) {
            startActivity(Intent(this, GuideActivity::class.java))
        }
        guide.addView(guideButton, matchWrap(0))
    }

    /** 📦 Install: pick files → install; options live in Settings. */
    private fun buildInstallPage(page: LinearLayout) {
        val files = card(page)
        files.addView(heading(tr("קבצים להתקנה", "Files to install")), matchWrap())
        filesText = body("")
        files.addView(filesText, matchWrap(10))
        files.addView(button(tr("בחר קבצים (APK / APKS / XAPK / APKM)", "Choose files (APK / APKS / XAPK / APKM)"), primary = false) { chooseFiles() }.busyAware(), matchWrap())
        installButton = button(tr("התקן", "Install"), primary = true) { installSelected() }.busyAware()
        files.addView(installButton, matchWrap(0))

        // Install options right here (no extra window). Every change is saved immediately.
        val opts = card(page)
        opts.addView(heading(tr("אפשרויות התקנה", "Install options")), matchWrap())
        val o = Bridge.loadOptions()
        option(opts, "play", tr("רשום את Google Play כמקור ההתקנה", "Record Google Play as the install source"), o.registerPlayInstaller)
        option(opts, "downgrade", tr("אפשר דריסה בגרסה ישנה יותר (שנמוך)", "Allow replacing with an older version (downgrade)"), o.allowDowngrade)
        option(opts, "grant", tr("תן לאפליקציה את כל ההרשאות מראש", "Grant the app all permissions up front"), o.grantPermissions)
        option(opts, "test", tr("אפשר חבילות בדיקה (test-only)", "Allow test packages (test-only)"), o.allowTestPackages)
        if (Build.VERSION.SDK_INT >= 34) option(opts, "bypass", tr("אפשר אפליקציות ישנות שנחסמו ב־Android 14+", "Allow old apps blocked on Android 14+"), o.bypassLowTargetSdk)
        opts.addView(note(tr("רישום Google Play הוא סימון בלבד: ההתקנה נעשית דרך ADB, בלי רישיון או אימות של Play.", "Recording Google Play is only a label: the install is done through ADB, without a Play license or verification.")), matchWrap(8))
        optionsSummary = note("")
        opts.addView(optionsSummary, matchWrap(4))
        opts.addView(button(tr("התקנה אוטומטית ועוד ←", "Automatic install and more →"), primary = false) { onTab(BottomNav.Tab.SETTINGS) }, matchWrap(0))

        val tip = card(page)
        tip.addView(heading(tr("טיפ", "Tip")), matchWrap())
        tip.addView(body(tr("אפשר גם לפתוח או לשתף קובץ APK מכל אפליקציה (מנהל קבצים, דפדפן, וואטסאפ, טלגרם) ולבחור באפליקציית הבוס — והוא יותקן לפי ההגדרות.", "You can also open or share an APK file from any app (file manager, browser, WhatsApp, Telegram) and choose The Boss — it will be installed according to the settings.")), matchWrap(0))
    }

    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(0xFFFFFFFF.toInt())
            setStroke(dp(1), 0xFFF2E2D2.toInt())
        }
        elevation = dp(1).toFloat()
        parent.addView(this, matchWrap(14))
    }

    private fun heading(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(INK)
        setPadding(0, 0, 0, dp(6))
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(0xFF55483E.toInt())
        setLineSpacing(0f, 1.1f)
    }

    private fun note(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(MUTED)
        setPadding(0, dp(4), 0, 0)
    }

    private fun field(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        inputType = type
        setSingleLine(true)
        setTextColor(INK)
        setHintTextColor(0xFF9B8D80.toInt())
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(0xFFFFFBF7.toInt())
            setStroke(dp(1), 0xFFE8D6C4.toInt())
        }
    }

    private fun checkBox(label: String, checked: Boolean) = CheckBox(this).apply {
        text = label
        textSize = 14f
        isChecked = checked
        setTextColor(INK)
    }


    private fun button(textValue: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 15f
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF75451F.toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) ACCENT else 0xFFFFE7D1.toInt())
        }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    private fun Button.busyAware() = also { busyViews += it }

    // endregion

    // region rendering

    private fun render(state: BridgeState) {
        statusText.text = state.status
        val showProgress = state.busy || state.progress in 0..99
        progressBar.visibility = if (showProgress) View.VISIBLE else View.GONE
        progressBar.isIndeterminate = state.progress < 0
        if (state.progress >= 0) progressBar.progress = state.progress
        cancelButton.visibility = if (state.busy) View.VISIBLE else View.GONE
        busyViews.forEach { it.isEnabled = !state.busy; it.alpha = if (state.busy) 0.5f else 1f }
        val pkg = state.lastInstalledPackage
        openAppButton.visibility = if (!state.busy && pkg != null && packageManager.getLaunchIntentForPackage(pkg) != null) View.VISIBLE else View.GONE
        if (!state.busy) refreshConnectionInfo()
        if (!state.busy && state.installSeq > shownInstallSeq) {
            shownInstallSeq = state.installSeq
            if (pkg != null) offerToOpen(pkg)
        }
    }

    private fun offerToOpen(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        if (isFinishing || isDestroyed) return
        when (Bridge.openAfterInstall) {
            "never" -> return
            "auto" -> {
                runCatching { startActivity(launch) }.onFailure { toast(tr("לא ניתן לפתוח את האפליקציה.", "Can't open the app.")) }
                return
            }
        }
        val label = Bridge.appLabel(pkg) ?: pkg
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("✔ $label הותקן", "✔ $label installed"))
            .setMessage(tr("לפתוח את האפליקציה עכשיו?", "Open the app now?"))
            .setPositiveButton(tr("פתח", "Open")) { _, _ ->
                runCatching { startActivity(launch) }.onFailure { toast(tr("לא ניתן לפתוח את האפליקציה.", "Can't open the app.")) }
            }
            .setNegativeButton(tr("לא עכשיו", "Not now"), null)
            .show()
    }

    private fun applyLogExpanded() {
        val open = Bridge.logExpanded
        logHeader.text = if (open) tr("יומן ▴", "Log ▴") else tr("יומן ▾  (הקש לפתיחה)", "Log ▾  (tap to open)")
        logBody.visibility = if (open) View.VISIBLE else View.GONE
        logPreview.visibility = if (open) View.GONE else View.VISIBLE
        if (open) logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun openAccessibilitySetup() {
        if (PairingAccessibilityService.isEnabled(this)) {
            openDeveloperOptions()
            toast(tr("פתח \"ניפוי באגים אלחוטי\" ← \"התאמת מכשיר עם קוד התאמה\". הקוד יזוהה ויוזן אוטומטית.", "Open \"Wireless debugging\" → \"Pair device with pairing code\". The code is detected and entered automatically."))
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("צימוד אוטומטי", "Automatic pairing"))
            .setMessage(
                tr("הבוס יקרא רק את מסכי ההגדרות (com.android.settings), יזהה את קוד הצימוד והפורט ויבצע את הצימוד בעצמו. שום מידע לא נשמר או נשלח מחוץ למכשיר.\n\n", "The Boss reads only the settings screens (com.android.settings), detects the pairing code and port and pairs by itself. Nothing is stored or sent outside the device.\n\n") +
                    tr("במסך הבא: בחר \"הבוס\" (או The Boss) ← הפעל.\n\n", "On the next screen: choose \"The Boss\" (or הבוס) → turn on.\n\n") +
                    tr("אם האפשרות אפורה (Android 13+): פתח פרטי אפליקציה ← ⋮ למעלה ← \"אפשר הגדרות מוגבלות\", וחזור לכאן.", "If the option is grey (Android 13+): open App info → ⋮ at the top → \"Allow restricted settings\", and come back here.")
            )
            .setPositiveButton(tr("להגדרות נגישות", "Accessibility settings")) { _, _ -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            .setNeutralButton(tr("פרטי אפליקציה", "App info")) { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
            .show()
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)) } catch (_: ActivityNotFoundException) { openDeveloperOptions() }
    }

    private fun refreshConnectionInfo() {
        if (!::connectionText.isInitialized) return
        if (!Compat.hasWirelessDebugging) {
            connectionText.text = tr("מצב ADB קלאסי (Android ${Build.VERSION.RELEASE}).\n\n${Bridge.LEGACY_HELP}\n\n", "Classic ADB mode (Android ${Build.VERSION.RELEASE}).\n\n${Bridge.LEGACY_HELP}\n\n") +
                tr("בחיבור הראשון יופיע \"לאפשר ניפוי באגים?\" — סמן \"אפשר תמיד\". אפשר גם להתקין על מכשיר אחר: צימוד ידני / מכשיר אחר ← כתובת היעד.", "On the first connection \"Allow debugging?\" appears — check \"Always allow\". You can also install on another device: Manual pairing / another device → target address.")
            return
        }
        val wifi = Bridge.isOnWifi()
        val wireless = Bridge.isWirelessDebuggingOn()
        val lines = mutableListOf<String>()
        lines += if (Bridge.isPaired) tr("✔ צומד בעבר עם המכשיר (${Bridge.targetHost})", "✔ Previously paired with the device (${Bridge.targetHost})") else tr("✖ עדיין לא בוצע צימוד", "✖ Not paired yet")
        lines += if (wifi) tr("✔ מחובר ל־Wi‑Fi", "✔ Connected to Wi‑Fi") else tr("✖ אין Wi‑Fi — ניפוי באגים אלחוטי דורש Wi‑Fi", "✖ No Wi‑Fi — wireless debugging needs Wi‑Fi")
        lines += when (wireless) {
            true -> tr("✔ ניפוי באגים אלחוטי פעיל", "✔ Wireless debugging is on")
            false -> if (Bridge.canWriteSecureSettings()) tr("• ניפוי באגים אלחוטי כבוי — יופעל אוטומטית בעת הצורך", "• Wireless debugging is off — it will be turned on automatically when needed") else tr("✖ ניפוי באגים אלחוטי כבוי", "✖ Wireless debugging is off")
            null -> tr("• מצב ניפוי באגים אלחוטי לא ידוע", "• Wireless debugging status unknown")
        }
        if (Bridge.canWriteSecureSettings()) lines += tr("✔ הפעלה אוטומטית מאופשרת", "✔ Automatic turn-on is allowed")
        if (Bridge.keepAdbWithoutWifi) lines += tr("✔ ADB קבוע מופעל (עובד גם בלי Wi‑Fi עד הפעלה מחדש)", "✔ Persistent ADB is on (works without Wi‑Fi until restart)")
        if (Bridge.savedConnectPort > 0) lines += tr("• פורט חיבור אחרון: ${Bridge.savedConnectPort}", "• Last connection port: ${Bridge.savedConnectPort}")
        connectionText.text = lines.joinToString("\n")
        val a11y = PairingAccessibilityService.isEnabled(this)
        a11yText.text = if (a11y) {
            tr("✔ צימוד אוטומטי פעיל: פתח \"התאמת מכשיר עם קוד התאמה\" בהגדרות, והקוד יזוהה ויוזן לבד.", "✔ Automatic pairing is on: open \"Pair device with pairing code\" in Settings, and the code is detected and entered by itself.")
        } else {
            tr("צימוד אוטומטי: הבוס יכול לקרוא את קוד הצימוד מהמסך ולצמד בעצמו (דורש הפעלת שירות נגישות).", "Automatic pairing: The Boss can read the pairing code from the screen and pair by itself (requires turning on an accessibility service).")
        }
        // Permission already granted → no "enable" button.
        a11yButton.visibility = if (a11y) View.GONE else View.VISIBLE
        a11yButton.text = tr("הפעל צימוד אוטומטי (נגישות)", "Turn on automatic pairing (accessibility)")
        val paired = Bridge.isPaired
        pairStateText.text = if (paired) tr("✔ הטלפון מצומד. אין צורך לצמד שוב.", "✔ The phone is paired. No need to pair again.") else tr("צריך לצמד פעם אחת. הדרך הקלה: צימוד אוטומטי — ואז פותחים בהגדרות \"התאמת מכשיר עם קוד התאמה\".", "You need to pair once. The easy way: automatic pairing — then open \"Pair device with pairing code\" in Settings.")
        repairToggle.visibility = if (paired) View.VISIBLE else View.GONE
        // Not paired: show the controls. Paired: fold them away the first time (the user can open them again).
        if (!paired) pairControls.visibility = View.VISIBLE
        else if (pairControls.tag == null) pairControls.visibility = View.GONE
        pairControls.tag = "init"
        // Already paired: the guide card shrinks to a single small button.
        guideInfo.visibility = if (paired) View.GONE else View.VISIBLE
        guideTitle.visibility = if (paired) View.GONE else View.VISIBLE
        guideButton.text = if (paired) tr("📖 מדריך התחברות", "📖 Connection guide") else tr("▶  התחל מדריך שלב אחר שלב", "▶  Start the step-by-step guide")

    }

    private fun renderSelection() {
        if (!::filesText.isInitialized) return
        filesText.text = if (selected.isEmpty()) {
            tr("עדיין לא נבחרו קבצים.", "No files chosen yet.")
        } else {
            selected.joinToString("\n") { "• ${Bridge.displayName(it)} (${Bridge.formatSize(Bridge.sizeOf(it))})" } +
                (previewText?.let { "\n\n$it" } ?: "")
        }
        installButton.text = if (selected.size > 1) tr("התקן ${selected.size} קבצים", "Install ${selected.size} files") else tr("התקן", "Install")
    }

    // endregion

    // region actions

    private fun openSettingsScreen() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun option(parent: LinearLayout, key: String, label: String, checked: Boolean) {
        val box = checkBox(label, checked)
        box.setOnCheckedChangeListener { _, _ -> Bridge.saveOptions(currentOptions()) }
        optionBoxes[key] = box
        parent.addView(box, matchWrap(0))
    }

    private fun currentOptions(): InstallOptions {
        val saved = Bridge.loadOptions()
        return InstallOptions(
            allowDowngrade = optionBoxes["downgrade"]?.isChecked ?: saved.allowDowngrade,
            grantPermissions = optionBoxes["grant"]?.isChecked ?: saved.grantPermissions,
            allowTestPackages = optionBoxes["test"]?.isChecked ?: saved.allowTestPackages,
            bypassLowTargetSdk = optionBoxes["bypass"]?.isChecked ?: saved.bypassLowTargetSdk,
            registerPlayInstaller = optionBoxes["play"]?.isChecked ?: saved.registerPlayInstaller,
        )
    }

    private fun refreshOptionsSummary() {
        if (!::optionsSummary.isInitialized) return
        // Settings may have changed them; reflect the saved values in the checkboxes.
        val o = Bridge.loadOptions()
        optionBoxes["play"]?.isChecked = o.registerPlayInstaller
        optionBoxes["downgrade"]?.isChecked = o.allowDowngrade
        optionBoxes["grant"]?.isChecked = o.grantPermissions
        optionBoxes["test"]?.isChecked = o.allowTestPackages
        optionBoxes["bypass"]?.isChecked = o.bypassLowTargetSdk
        val auto = when (Bridge.autoInstallMode) {
            "auto" -> tr("עדכונים מיידית, אפליקציות חדשות עם שאלה", "updates immediately, new apps with a question")
            "ask" -> tr("עם שאלה", "with a question")
            else -> tr("כבויה", "off")
        }
        val open = when (Bridge.openAfterInstall) { "auto" -> tr("מיד", "immediately"); "never" -> tr("לא", "no"); else -> tr("לשאול", "ask") }
        optionsSummary.text = tr("התקנה אוטומטית של קובץ שנפתח: $auto · פתיחה אחרי התקנה: $open", "Auto-install of an opened file: $auto · Open after install: $open")
    }

    private fun startNotificationPairing() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        if (!Bridge.isOnWifi()) {
            if (Bridge.autoWifi) {
                if (Bridge.isWifiHelperInstalled()) {
                    toast(tr("מדליק Wi‑Fi… כשהוא מתחבר, לחץ שוב.", "Turning Wi‑Fi on… once it connects, tap again."))
                    Bridge.scope.launch { Bridge.ensureWifi() }
                } else {
                    toast(tr("ניפוי באגים אלחוטי דורש Wi‑Fi — הדלק אותו בחלונית, ואז לחץ שוב.", "Wireless debugging needs Wi‑Fi — turn it on in the panel, then tap again."))
                    openWifiPanel()
                }
                return
            }
            toast(tr("שים לב: ניפוי באגים אלחוטי דורש חיבור Wi‑Fi.", "Note: wireless debugging needs a Wi‑Fi connection."))
        }
        PairingService.start(this)
        Bridge.log(tr("צימוד דרך התראה: פתח \"ניפוי באגים אלחוטי\" ← \"התאמת מכשיר עם קוד התאמה\" והזן את הקוד בהתראה.", "Pairing via notification: open \"Wireless debugging\" → \"Pair device with pairing code\" and enter the code in the notification."))
        toast(tr("פתח \"ניפוי באגים אלחוטי\" ← \"התאמת מכשיר עם קוד התאמה\", והזן את הקוד בהתראה שתופיע.", "Open \"Wireless debugging\" → \"Pair device with pairing code\", and enter the code in the notification that appears."))
        openDeveloperOptions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startNotificationPairing()
        } else {
            toast(tr("בלי הרשאת התראות אפשר להשתמש בצימוד ידני (אפשר במסך מפוצל).", "Without notification permission you can use manual pairing (possible in split screen)."))
            manualBox.visibility = View.VISIBLE
        }
    }

    private fun openDeveloperOptions() {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in intents) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                if (i.action == Settings.ACTION_DEVICE_INFO_SETTINGS) {
                    toast(tr("אפשרויות למפתחים כבויות: הקש 7 פעמים על \"מספר Build\" כדי להפעיל אותן.", "Developer options are off: tap \"Build number\" 7 times to turn them on."))
                }
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    private fun manualPair() {
        val code = pin.text.toString().trim()
        if (code.length != 6 || !code.all(Char::isDigit)) {
            toast(tr("קוד הצימוד חייב להכיל 6 ספרות.", "The pairing code must have 6 digits."))
            return
        }
        Bridge.pair(host.text.toString(), pairPort.text.toString().toIntOrNull(), code)
    }

    private fun chooseFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/vnd.android.package-archive",
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
                "application/vnd.apkm",
                "application/xapk-package-archive",
            ))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, REQUEST_FILES)
        } catch (_: ActivityNotFoundException) {
            toast(tr("לא נמצא בוחר קבצים במכשיר.", "No file picker found on the device."))
        }
    }

    @Deprecated("Framework Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILES || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add) }
        if (uris.isEmpty()) data.data?.let(uris::add)
        uris.forEach { uri ->
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        setSelection(uris)
    }

    @Suppress("DEPRECATION")
    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            )
            Intent.ACTION_SEND_MULTIPLE ->
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            setSelection(uris)
            Bridge.log(tr("התקבלו ${uris.size} קבצים מאפליקציה אחרת.", "Received ${uris.size} files from another app."))
            maybeAutoInstall()
        }
    }

    private fun setSelection(uris: List<Uri>) {
        if (uris.isEmpty()) return
        selected = uris
        previewText = null
        if (::installPage.isInitialized) showTab(BottomNav.Tab.INSTALL)
        renderSelection()
        loadPreview()
    }

    /** Applies the "automatic install" setting to files that were opened/shared into the app. */
    private fun maybeAutoInstall() {
        val mode = Bridge.autoInstallMode
        if (mode == "off") return
        if (!Bridge.isPaired) {
            Bridge.log(tr("התקנה אוטומטית דולגה: עדיין לא בוצע צימוד.", "Automatic install skipped: not paired yet."))
            return
        }
        if (Bridge.state.busy) return
        val files = selected
        // Security: any app can open a file in The Boss. So only a real update (newer version of an
        // installed app — Android checks it is signed by the same developer) installs without asking,
        // and then without the risky options (downgrade, test packages, all permissions…).
        // A new app always asks first.
        val only = files.singleOrNull()
        if (only == null || !Bridge.displayName(only).endsWith(".apk", true)) {
            confirmInstall(files, tr("לא ניתן לבדוק אוטומטית אם זה עדכון.", "Can't check automatically whether this is an update."))
            return
        }
        Bridge.scope.launch {
            val p = Bridge.preview(only)
            runOnUiThread {
                if (selected != files) return@runOnUiThread
                val installed = p?.installedVersionCode
                when {
                    p == null || installed == null -> confirmInstall(files, tr("זו אינה אפליקציה שכבר מותקנת, ולכן היא לא מותקנת אוטומטית.", "This isn't an app that's already installed, so it isn't installed automatically."))
                    installed >= p.versionCode -> confirmInstall(files, tr("זה לא עדכון לגרסה חדשה יותר, ולכן זה לא מותקן אוטומטית.", "This isn't an update to a newer version, so it isn't installed automatically."))
                    mode == "auto" -> Bridge.install(files, InstallOptions(
                        allowDowngrade = false, grantPermissions = false, allowTestPackages = false,
                        bypassLowTargetSdk = false, registerPlayInstaller = Bridge.loadOptions().registerPlayInstaller,
                    ))
                    else -> confirmInstall(files, null)
                }
            }
        }
    }

    private fun confirmInstall(files: List<Uri>, reason: String?) {
        if (isFinishing || isDestroyed) return
        val names = files.joinToString("\n") { "• ${Bridge.displayName(it)}" }
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("להתקין עכשיו?", "Install now?"))
            .setMessage((reason?.let { "$it\n\n" } ?: "") + names)
            .setPositiveButton(tr("התקן", "Install")) { _, _ -> if (selected == files) installSelected() }
            .setNegativeButton(tr("לא עכשיו", "Not now"), null)
            .show()
    }

    private fun loadPreview() {
        previewJob?.cancel()
        val only = selected.singleOrNull() ?: return
        if (!Bridge.displayName(only).endsWith(".apk", ignoreCase = true)) return
        previewJob = Bridge.scope.launch {
            val p = Bridge.preview(only) ?: return@launch
            val text = buildString {
                append(tr("${p.label} · ${p.packageName}\nגרסה ${p.versionName ?: "?"} (${p.versionCode})", "${p.label} · ${p.packageName}\nVersion ${p.versionName ?: "?"} (${p.versionCode})"))
                val installedCode = p.installedVersionCode
                when {
                    installedCode == null -> append(tr("\nלא מותקנת כרגע — התקנה חדשה.", "\nNot installed now — new install."))
                    installedCode < p.versionCode -> append(tr("\nעדכון: מותקנת ${p.installedVersionName} ($installedCode).", "\nUpdate: ${p.installedVersionName} ($installedCode) is installed."))
                    installedCode == p.versionCode -> append(tr("\nאותה גרסה כבר מותקנת — התקנה מחדש.", "\nThe same version is already installed — reinstall."))
                    else -> append(tr("\n⚠ שנמוך: מותקנת גרסה חדשה יותר ${p.installedVersionName} ($installedCode). סמן \"אפשר שנמוך גרסה\".", "\n⚠ Downgrade: a newer version ${p.installedVersionName} ($installedCode) is installed. Check \"Allow downgrade\"."))
                }
            }
            runOnUiThread {
                if (selected.singleOrNull() == only) {
                    previewText = text
                    renderSelection()
                }
            }
        }
    }

    private fun installSelected() {
        if (selected.isEmpty()) {
            chooseFiles()
            return
        }
        Bridge.install(selected, Bridge.loadOptions())
    }

    private fun openInstalledApp() {
        val pkg = Bridge.state.lastInstalledPackage ?: return
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) toast(tr("לאפליקציה אין מסך פתיחה.", "The app has no launch screen.")) else startActivity(launch)
    }

    private fun copyLog() {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(tr("הבוס log", "The Boss log"), Bridge.logSnapshot()))
        toast(tr("היומן הועתק.", "Log copied."))
    }

    // endregion

    private fun versionName(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        info.versionName ?: "?"
    }.getOrDefault("?")

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap(bottom: Int = 9) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    companion object {
        const val EXTRA_OPEN_GUIDE = "open_guide"
        /** From the widget's "📦 Install APK": open the file picker right away. */
        const val EXTRA_PICK_FILES = "pick_files"
        private const val REQUEST_FILES = 4102
        private const val REQUEST_NOTIFICATIONS = 4103
        private const val KEY_SELECTED = "selected_uris"
        private const val KEY_SHOWN_SEQ = "shown_install_seq"
        private const val KEY_TAB = "tab"

        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val ACCENT = 0xFFF09A45.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()
    }
}
