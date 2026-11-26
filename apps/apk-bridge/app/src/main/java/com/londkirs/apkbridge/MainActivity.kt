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
            .setTitle("להתקין את עזר ה־Wi‑Fi?")
            .setMessage(
                "אפליקציה זעירה שמאפשרת לאפליקציית הבוס להדליק Wi‑Fi לבד כשהוא כבוי (ניפוי באגים אלחוטי עובד רק עם Wi‑Fi).\n\n" +
                    "ההתקנה נעשית דרך מתקין האפליקציות הרגיל של Android — בלי ניפוי באגים. ייתכן שתתבקש פעם אחת לאשר לאפליקציית הבוס להתקין אפליקציות.\n\n" +
                    "אפשר להתקין גם מאוחר יותר: ⚙ הגדרות ← צימוד וחיבור."
            )
            .setPositiveButton("התקן") { _, _ -> HelperInstaller.start(this) }
            .setNegativeButton("לא עכשיו", null)
            .show()
    }

    private fun confirmKeepAdb() {
        android.app.AlertDialog.Builder(this)
            .setTitle("ADB קבוע (גם בלי Wi‑Fi)")
            .setMessage(
                "הבוס יעביר את ADB למצב רשת רגיל בפורט 5555 (כמו adb tcpip 5555), ויתחבר אליו דרך הכתובת הפנימית של המכשיר — כך הוא עובד גם כשה־Wi‑Fi כבוי.\n\n" +
                    "• בפעם הראשונה Android ישאל \"לאפשר ניפוי באגים?\" — סמן \"אפשר תמיד\" ואשר.\n" +
                    "• ניפוי באגים ב־USB יודלק (נדרש כדי ש־ADB יישאר פעיל).\n" +
                    "• המצב נמחק בהפעלה מחדש של המכשיר, ומתחדש אוטומטית בחיבור הבא דרך Wi‑Fi.\n" +
                    "• כשה־Wi‑Fi דלוק, מכשירים אחרים ברשת יוכלו לנסות להתחבר לפורט 5555 — אבל כל חיבור חדש דורש אישור שלך במסך.\n\n" +
                    "נדרש חיבור ADB פעיל עכשיו (Wi‑Fi וצימוד)."
            )
            .setPositiveButton("הפעל") { _, _ -> Bridge.enableKeepAdb() }
            .setNegativeButton("ביטול", null)
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
        // Keypad phones: 1–5 switch tabs (like the bottom bar), 0 opens/closes the log, MENU opens Settings.
        val tabs = BottomNav.Tab.values()
        when {
            keyCode == android.view.KeyEvent.KEYCODE_MENU -> { onTab(BottomNav.Tab.SETTINGS); return true }
            !focusInText && keyCode in android.view.KeyEvent.KEYCODE_1..android.view.KeyEvent.KEYCODE_5 -> {
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
        pageTitle.text = if (tab == BottomNav.Tab.CONNECT) "חיבור לטלפון" else "התקנת אפליקציות"
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        header.addView(android.widget.ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_art)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            // The launcher bitmap has adaptive-icon padding (76 of 108dp); enlarge it to fill the slot.
            scaleX = 1.4f
            scaleY = 1.4f
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "הבוס"
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(4), dp(14), dp(16))
        }
        connectionPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        installPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pages.addView(connectionPage)
        pages.addView(installPage)
        buildConnectionPage(connectionPage)
        buildInstallPage(installPage)
        pages.addView(TextView(this).apply {
            text = "גרסה ${versionName()}"
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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
        cancelButton = button("בטל פעולה", primary = false) { Bridge.cancelCurrent() }.apply { visibility = View.GONE }
        openAppButton = button("פתח את האפליקציה שהותקנה", primary = true) { openInstalledApp() }.apply { visibility = View.GONE }
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
        state.addView(heading("מצב החיבור"), matchWrap())
        connectionText = body("")
        state.addView(connectionText, matchWrap(8))
        state.addView(button("בדוק חיבור", primary = true) { Bridge.testConnection() }.busyAware(), matchWrap(0))

        // Card 2: step-by-step guide — the section for this phone's Android version is open.
        buildGuide(page)

        // Card 3: pairing — needed once.
        val pair = card(page)
        pair.addView(heading("צימוד (פעם אחת)"), matchWrap())
        pairStateText = body("")
        pair.addView(pairStateText, matchWrap(6))
        // Once paired, the pairing controls are folded away behind "pair again".
        repairToggle = button("צימוד מחדש / מכשיר אחר ▾", primary = false) {
            pairControls.visibility = if (pairControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        pair.addView(repairToggle, matchWrap(0))
        pairControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        pair.addView(pairControls)
        a11yText = body("")
        pairControls.addView(a11yText, matchWrap(6))
        a11yButton = button("הפעל צימוד אוטומטי", primary = true) { openAccessibilitySetup() }
        pairControls.addView(a11yButton, matchWrap())
        val notifyPair = button("צימוד עם קוד דרך התראה", primary = false) { startNotificationPairing() }.busyAware()
        pairControls.addView(notifyPair, matchWrap())
        if (!Compat.hasWirelessDebugging) {
            // Android 8–10: no pairing codes; the state card explains classic ADB on port 5555.
            pair.visibility = View.GONE
        }
        pairControls.addView(button("פתח אפשרויות למפתחים", primary = false) { openDeveloperOptions() }, matchWrap())
        pairControls.addView(button("צימוד ידני / מכשיר אחר ▾", primary = false) {
            manualBox.visibility = if (manualBox.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, matchWrap(0))
        manualBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(0, dp(10), 0, 0) }
        manualBox.addView(note("127.0.0.1 הוא הטלפון הזה. אפשר להזין IP של טלפון אחר באותה רשת. פורט הצימוד נמצא לבד אם משאירים ריק."), matchWrap(6))
        host = field("כתובת יעד (127.0.0.1)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).apply { setText(Bridge.targetHost) }
        pairPort = field("פורט צימוד (לא חובה)", InputType.TYPE_CLASS_NUMBER)
        pin = field("קוד צימוד בן 6 ספרות", InputType.TYPE_CLASS_NUMBER)
        manualBox.addView(host, matchWrap())
        manualBox.addView(pairPort, matchWrap())
        manualBox.addView(pin, matchWrap())
        manualBox.addView(button("בצע צימוד", primary = true) { manualPair() }.busyAware(), matchWrap())
        manualBox.addView(button("שמור כתובת יעד", primary = false) {
            Bridge.targetHost = host.text.toString()
            Bridge.savedConnectPort = 0
            host.setText(Bridge.targetHost)
            refreshConnectionInfo()
            toast("כתובת היעד נשמרה.")
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
        logButtons.addView(button("העתק יומן", primary = false) { copyLog() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        logButtons.addView(button("נקה", primary = false) { Bridge.clearLog() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        logBody.addView(logButtons, matchWrap(0))
        logCard.addView(logBody, matchWrap(0))
    }

    /** 📖 Short card that opens the step-by-step guide; folds to one line once the phone is paired. */
    private fun buildGuide(page: LinearLayout) {
        val guide = card(page)
        guideTitle = heading("📖 איך מתחברים?")
        guide.addView(guideTitle, matchWrap(2))
        guideInfo = body(
            "בטלפון שלך: אנדרואיד ${Build.VERSION.RELEASE}\n" +
                if (Compat.hasWirelessDebugging) "מתאים לך: חיבור בלי מחשב (כ־5 דקות)" else "מתאים לך: חיבור עם מחשב (כ־10 דקות, פעם אחת)"
        )
        guide.addView(guideInfo, matchWrap(10))
        guideButton = button("▶  התחל מדריך שלב אחר שלב", primary = true) {
            startActivity(Intent(this, GuideActivity::class.java))
        }
        guide.addView(guideButton, matchWrap(0))
    }

    /** 📦 Install: pick files → install; options live in Settings. */
    private fun buildInstallPage(page: LinearLayout) {
        val files = card(page)
        files.addView(heading("קבצים להתקנה"), matchWrap())
        filesText = body("")
        files.addView(filesText, matchWrap(10))
        files.addView(button("בחר קבצים (APK / APKS / XAPK / APKM)", primary = false) { chooseFiles() }.busyAware(), matchWrap())
        installButton = button("התקן", primary = true) { installSelected() }.busyAware()
        files.addView(installButton, matchWrap(0))

        // Install options right here (no extra window). Every change is saved immediately.
        val opts = card(page)
        opts.addView(heading("אפשרויות התקנה"), matchWrap())
        val o = Bridge.loadOptions()
        option(opts, "play", "רשום את Google Play כמקור ההתקנה", o.registerPlayInstaller)
        option(opts, "downgrade", "אפשר דריסה בגרסה ישנה יותר (שנמוך)", o.allowDowngrade)
        option(opts, "grant", "תן לאפליקציה את כל ההרשאות מראש", o.grantPermissions)
        option(opts, "test", "אפשר חבילות בדיקה (test-only)", o.allowTestPackages)
        if (Build.VERSION.SDK_INT >= 34) option(opts, "bypass", "אפשר אפליקציות ישנות שנחסמו ב־Android 14+", o.bypassLowTargetSdk)
        opts.addView(note("רישום Google Play הוא סימון בלבד: ההתקנה נעשית דרך ADB, בלי רישיון או אימות של Play."), matchWrap(8))
        optionsSummary = note("")
        opts.addView(optionsSummary, matchWrap(4))
        opts.addView(button("התקנה אוטומטית ועוד ←", primary = false) { onTab(BottomNav.Tab.SETTINGS) }, matchWrap(0))

        val tip = card(page)
        tip.addView(heading("טיפ"), matchWrap())
        tip.addView(body("אפשר גם לפתוח או לשתף קובץ APK מכל אפליקציה (מנהל קבצים, דפדפן, וואטסאפ, טלגרם) ולבחור באפליקציית הבוס — והוא יותקן לפי ההגדרות."), matchWrap(0))
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
                runCatching { startActivity(launch) }.onFailure { toast("לא ניתן לפתוח את האפליקציה.") }
                return
            }
        }
        val label = Bridge.appLabel(pkg) ?: pkg
        android.app.AlertDialog.Builder(this)
            .setTitle("✔ $label הותקן")
            .setMessage("לפתוח את האפליקציה עכשיו?")
            .setPositiveButton("פתח") { _, _ ->
                runCatching { startActivity(launch) }.onFailure { toast("לא ניתן לפתוח את האפליקציה.") }
            }
            .setNegativeButton("לא עכשיו", null)
            .show()
    }

    private fun applyLogExpanded() {
        val open = Bridge.logExpanded
        logHeader.text = if (open) "יומן ▴" else "יומן ▾  (הקש לפתיחה)"
        logBody.visibility = if (open) View.VISIBLE else View.GONE
        logPreview.visibility = if (open) View.GONE else View.VISIBLE
        if (open) logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun openAccessibilitySetup() {
        if (PairingAccessibilityService.isEnabled(this)) {
            openDeveloperOptions()
            toast("פתח \"ניפוי באגים אלחוטי\" ← \"התאמת מכשיר עם קוד התאמה\". הקוד יזוהה ויוזן אוטומטית.")
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("צימוד אוטומטי")
            .setMessage(
                "הבוס יקרא רק את מסכי ההגדרות (com.android.settings), יזהה את קוד הצימוד והפורט ויבצע את הצימוד בעצמו. שום מידע לא נשמר או נשלח מחוץ למכשיר.\n\n" +
                    "במסך הבא: בחר \"הבוס\" ← הפעל.\n\n" +
                    "אם האפשרות אפורה (Android 13+): פתח פרטי אפליקציה ← ⋮ למעלה ← \"אפשר הגדרות מוגבלות\", וחזור לכאן."
            )
            .setPositiveButton("להגדרות נגישות") { _, _ -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            .setNeutralButton("פרטי אפליקציה") { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)) } catch (_: ActivityNotFoundException) { openDeveloperOptions() }
    }

    private fun refreshConnectionInfo() {
        if (!::connectionText.isInitialized) return
        if (!Compat.hasWirelessDebugging) {
            connectionText.text = "מצב ADB קלאסי (Android ${Build.VERSION.RELEASE}).\n\n${Bridge.LEGACY_HELP}\n\n" +
                "בחיבור הראשון יופיע \"לאפשר ניפוי באגים?\" — סמן \"אפשר תמיד\". אפשר גם להתקין על מכשיר אחר: צימוד ידני / מכשיר אחר ← כתובת היעד."
            return
        }
        val wifi = Bridge.isOnWifi()
        val wireless = Bridge.isWirelessDebuggingOn()
        val lines = mutableListOf<String>()
        lines += if (Bridge.isPaired) "✔ צומד בעבר עם המכשיר (${Bridge.targetHost})" else "✖ עדיין לא בוצע צימוד"
        lines += if (wifi) "✔ מחובר ל־Wi‑Fi" else "✖ אין Wi‑Fi — ניפוי באגים אלחוטי דורש Wi‑Fi"
        lines += when (wireless) {
            true -> "✔ ניפוי באגים אלחוטי פעיל"
            false -> if (Bridge.canWriteSecureSettings()) "• ניפוי באגים אלחוטי כבוי — יופעל אוטומטית בעת הצורך" else "✖ ניפוי באגים אלחוטי כבוי"
            null -> "• מצב ניפוי באגים אלחוטי לא ידוע"
        }
        if (Bridge.canWriteSecureSettings()) lines += "✔ הפעלה אוטומטית מאופשרת"
        if (Bridge.keepAdbWithoutWifi) lines += "✔ ADB קבוע מופעל (עובד גם בלי Wi‑Fi עד הפעלה מחדש)"
        if (Bridge.savedConnectPort > 0) lines += "• פורט חיבור אחרון: ${Bridge.savedConnectPort}"
        connectionText.text = lines.joinToString("\n")
        val a11y = PairingAccessibilityService.isEnabled(this)
        a11yText.text = if (a11y) {
            "✔ צימוד אוטומטי פעיל: פתח \"התאמת מכשיר עם קוד התאמה\" בהגדרות, והקוד יזוהה ויוזן לבד."
        } else {
            "צימוד אוטומטי: הבוס יכול לקרוא את קוד הצימוד מהמסך ולצמד בעצמו (דורש הפעלת שירות נגישות)."
        }
        // Permission already granted → no "enable" button.
        a11yButton.visibility = if (a11y) View.GONE else View.VISIBLE
        a11yButton.text = "הפעל צימוד אוטומטי (נגישות)"
        val paired = Bridge.isPaired
        pairStateText.text = if (paired) "✔ הטלפון מצומד. אין צורך לצמד שוב." else "צריך לצמד פעם אחת. הדרך הקלה: צימוד אוטומטי — ואז פותחים בהגדרות \"התאמת מכשיר עם קוד התאמה\"."
        repairToggle.visibility = if (paired) View.VISIBLE else View.GONE
        // Not paired: show the controls. Paired: fold them away the first time (the user can open them again).
        if (!paired) pairControls.visibility = View.VISIBLE
        else if (pairControls.tag == null) pairControls.visibility = View.GONE
        pairControls.tag = "init"
        // Already paired: the guide card shrinks to a single small button.
        guideInfo.visibility = if (paired) View.GONE else View.VISIBLE
        guideTitle.visibility = if (paired) View.GONE else View.VISIBLE
        guideButton.text = if (paired) "📖 מדריך התחברות" else "▶  התחל מדריך שלב אחר שלב"

    }

    private fun renderSelection() {
        if (!::filesText.isInitialized) return
        filesText.text = if (selected.isEmpty()) {
            "עדיין לא נבחרו קבצים."
        } else {
            selected.joinToString("\n") { "• ${Bridge.displayName(it)} (${Bridge.formatSize(Bridge.sizeOf(it))})" } +
                (previewText?.let { "\n\n$it" } ?: "")
        }
        installButton.text = if (selected.size > 1) "התקן ${selected.size} קבצים" else "התקן"
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
            "auto" -> "מיידית" + if (Bridge.autoInstallUpdatesOnly) " (רק עדכונים)" else ""
            "ask" -> "עם שאלה" + if (Bridge.autoInstallUpdatesOnly) " (רק עדכונים)" else ""
            else -> "כבויה"
        }
        val open = when (Bridge.openAfterInstall) { "auto" -> "מיד"; "never" -> "לא"; else -> "לשאול" }
        optionsSummary.text = "התקנה אוטומטית של קובץ שנפתח: $auto · פתיחה אחרי התקנה: $open"
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
                    toast("מדליק Wi‑Fi… כשהוא מתחבר, לחץ שוב.")
                    Bridge.scope.launch { Bridge.ensureWifi() }
                } else {
                    toast("ניפוי באגים אלחוטי דורש Wi‑Fi — הדלק אותו בחלונית, ואז לחץ שוב.")
                    openWifiPanel()
                }
                return
            }
            toast("שים לב: ניפוי באגים אלחוטי דורש חיבור Wi‑Fi.")
        }
        PairingService.start(this)
        Bridge.log("צימוד דרך התראה: פתח \"ניפוי באגים אלחוטי\" ← \"התאמת מכשיר עם קוד התאמה\" והזן את הקוד בהתראה.")
        toast("פתח \"ניפוי באגים אלחוטי\" ← \"התאמת מכשיר עם קוד התאמה\", והזן את הקוד בהתראה שתופיע.")
        openDeveloperOptions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startNotificationPairing()
        } else {
            toast("בלי הרשאת התראות אפשר להשתמש בצימוד ידני (אפשר במסך מפוצל).")
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
                    toast("אפשרויות למפתחים כבויות: הקש 7 פעמים על \"מספר Build\" כדי להפעיל אותן.")
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
            toast("קוד הצימוד חייב להכיל 6 ספרות.")
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
            toast("לא נמצא בוחר קבצים במכשיר.")
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
            Bridge.log("התקבלו ${uris.size} קבצים מאפליקציה אחרת.")
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
            Bridge.log("התקנה אוטומטית דולגה: עדיין לא בוצע צימוד.")
            return
        }
        if (Bridge.state.busy) return
        val files = selected
        if (!Bridge.autoInstallUpdatesOnly) {
            if (mode == "auto") installSelected() else confirmInstall(files, null)
            return
        }
        // Updates-only: install automatically only when the APK updates an installed app.
        val only = files.singleOrNull()
        if (only == null || !Bridge.displayName(only).endsWith(".apk", true)) {
            confirmInstall(files, "לא ניתן לבדוק אוטומטית אם זה עדכון.")
            return
        }
        Bridge.scope.launch {
            val p = Bridge.preview(only)
            runOnUiThread {
                if (selected != files) return@runOnUiThread
                when {
                    p?.installedVersionCode == null -> confirmInstall(files, "זו אינה אפליקציה שכבר מותקנת, ולכן היא לא מותקנת אוטומטית.")
                    mode == "auto" -> installSelected()
                    else -> confirmInstall(files, null)
                }
            }
        }
    }

    private fun confirmInstall(files: List<Uri>, reason: String?) {
        if (isFinishing || isDestroyed) return
        val names = files.joinToString("\n") { "• ${Bridge.displayName(it)}" }
        android.app.AlertDialog.Builder(this)
            .setTitle("להתקין עכשיו?")
            .setMessage((reason?.let { "$it\n\n" } ?: "") + names)
            .setPositiveButton("התקן") { _, _ -> if (selected == files) installSelected() }
            .setNegativeButton("לא עכשיו", null)
            .show()
    }

    private fun loadPreview() {
        previewJob?.cancel()
        val only = selected.singleOrNull() ?: return
        if (!Bridge.displayName(only).endsWith(".apk", ignoreCase = true)) return
        previewJob = Bridge.scope.launch {
            val p = Bridge.preview(only) ?: return@launch
            val text = buildString {
                append("${p.label} · ${p.packageName}\nגרסה ${p.versionName ?: "?"} (${p.versionCode})")
                val installedCode = p.installedVersionCode
                when {
                    installedCode == null -> append("\nלא מותקנת כרגע — התקנה חדשה.")
                    installedCode < p.versionCode -> append("\nעדכון: מותקנת ${p.installedVersionName} ($installedCode).")
                    installedCode == p.versionCode -> append("\nאותה גרסה כבר מותקנת — התקנה מחדש.")
                    else -> append("\n⚠ שנמוך: מותקנת גרסה חדשה יותר ${p.installedVersionName} ($installedCode). סמן \"אפשר שנמוך גרסה\".")
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
        if (launch == null) toast("לאפליקציה אין מסך פתיחה.") else startActivity(launch)
    }

    private fun copyLog() {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("הבוס log", Bridge.logSnapshot()))
        toast("היומן הועתק.")
    }

    // endregion

    private fun versionName(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        "${info.versionName} (${Compat.versionCode(info)})"
    }.getOrDefault("?")

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap(bottom: Int = 9) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    companion object {
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
