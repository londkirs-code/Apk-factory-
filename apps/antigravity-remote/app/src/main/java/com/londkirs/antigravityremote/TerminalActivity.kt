package com.londkirs.antigravityremote

import android.app.Activity
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/** Full-screen terminal running the official Antigravity CLI natively on the phone. */
class TerminalActivity : Activity(), ServiceConnection, TerminalService.Listener, TerminalViewClient {

    companion object {
        const val EXTRA_NEW = "new"   // "agy" or "shell"
        private const val MENU_NEW_AGY = 1
        private const val MENU_NEW_SHELL = 2
        private const val MENU_SWITCH = 3
        private const val MENU_CLOSE = 4
        private const val MENU_PASTE = 5
        private const val MENU_FONT_UP = 6
        private const val MENU_FONT_DOWN = 7
        private const val MENU_STOP_ALL = 8
        private const val MENU_KEYBOARD = 9
    }

    private lateinit var terminal: TerminalView
    private lateinit var title: TextView
    private lateinit var linkBar: LinearLayout
    private lateinit var linkText: TextView
    private var service: TerminalService? = null
    private var current: TerminalSession? = null
    private var ctrlOn = false
    private var altOn = false
    private lateinit var ctrlBtn: Button
    private lateinit var altBtn: Button
    private var fontSize = 0
    private var lastLink: String? = null
    private val prefs by lazy { getSharedPreferences("terminal", Context.MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        T.init(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1F36"))
            setPadding(dp(12), 0, dp(4), 0)
        }
        title = TextView(this).apply {
            setTextColor(Color.parseColor("#E8EAF6")); textSize = 14f; setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val menuBtn = Button(this).apply {
            text = "⋮"; textSize = 20f; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
            contentDescription = getString(R.string.more)
            setOnClickListener { showMenu(it) }
        }
        bar.addView(menuBtn, LinearLayout.LayoutParams(dp(48), dp(44)))
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        // Sign-in / link banner (agy prints or "opens" an OAuth URL).
        linkBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#22305E"))
            setPadding(dp(12), dp(4), dp(4), dp(4))
            visibility = View.GONE
        }
        linkText = TextView(this).apply {
            setTextColor(Color.parseColor("#E8EAF6")); textSize = 13f; setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        linkBar.addView(linkText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        linkBar.addView(Button(this).apply {
            setText(R.string.open_link); isAllCaps = false; textSize = 12f
            setOnClickListener { lastLink?.let { openLink(it) } }
        })
        linkBar.addView(Button(this).apply {
            text = "✕"; setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.WHITE)
            setOnClickListener { linkBar.visibility = View.GONE }
        }, LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(linkBar)

        terminal = TerminalView(this, null).apply {
            setTerminalViewClient(this@TerminalActivity)
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        // setTextSize() creates the TerminalRenderer; setTypeface() reads it, so the order matters.
        fontSize = prefs.getInt("font", dp(12))
        terminal.setTextSize(fontSize)
        terminal.setTypeface(Typeface.MONOSPACE)
        root.addView(terminal, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildExtraKeys())
        setContentView(root)

        val intent = Intent(this, TerminalService::class.java)
        startService(intent)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        service?.listener = null
        unbindService(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        service?.let { handleIntent(it) }
    }

    // ---- Service connection ---------------------------------------------------------------------

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val s = (binder as TerminalService.LocalBinder).service
        service = s
        s.listener = this
        handleIntent(s)
        s.takePendingUrl()?.let { onOpenUrl(it) }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    private fun handleIntent(s: TerminalService) {
        val want = intent.getStringExtra(EXTRA_NEW)
        intent.removeExtra(EXTRA_NEW)
        val session = when {
            want == TerminalService.KIND_SHELL -> s.startShell()
            want == TerminalService.KIND_AGY -> s.sessions.lastOrNull { s.kinds[it] == TerminalService.KIND_AGY && it.isRunning } ?: s.startAgy()
            s.sessions.isNotEmpty() -> current?.takeIf { it in s.sessions } ?: s.sessions.last()
            AgyInstaller.isInstalled(this) -> s.startAgy()
            else -> s.startShell()
        }
        show(session)
    }

    private fun show(session: TerminalSession) {
        current = session
        terminal.attachSession(session)
        terminal.onScreenUpdated()
        updateTitle()
        terminal.requestFocus()
    }

    private fun updateTitle() {
        val s = service ?: return
        val c = current ?: return
        val idx = s.sessions.indexOf(c) + 1
        val label = when (s.kinds[c]) {
            TerminalService.KIND_AGY -> "Antigravity (agy)"
            TerminalService.KIND_HUB -> "Antigravity UI (hub)"
            else -> getString(R.string.shell)
        }
        val state = if (c.isRunning || c.emulator == null) "" else " — " + getString(R.string.finished)
        title.text = "[$idx/${s.sessions.size}] ${c.title?.takeIf { it.isNotBlank() } ?: label}$state"
    }

    // ---- TerminalService.Listener ---------------------------------------------------------------

    override fun onSessionsChanged() {
        val s = service ?: return
        if (current !in s.sessions) {
            val next = s.sessions.lastOrNull()
            if (next != null) show(next) else { finish(); return }
        }
        updateTitle()
    }

    override fun onScreenChanged(session: TerminalSession) {
        if (session == current) terminal.onScreenUpdated()
        scanForLink(session)
    }

    override fun onTitleChanged(session: TerminalSession) { if (session == current) updateTitle() }

    override fun onOpenUrl(url: String) {
        showLink(url)
        openLink(url)
    }

    override fun onPasteRequest(session: TerminalSession) { paste() }

    override fun onBell(session: TerminalSession) {}

    private val urlRegex = Regex("""https://accounts\.google\.com/\S+|https?://(?:localhost|127\.0\.0\.1):\d+/\S*""")
    private var lastScan = 0L

    /** Fallback when agy prints the sign-in URL instead of invoking $BROWSER. */
    private fun scanForLink(session: TerminalSession) {
        val now = System.currentTimeMillis()
        if (now - lastScan < 700) return
        lastScan = now
        val text = session.emulator?.screen?.transcriptTextWithFullLinesJoined ?: return
        val tail = if (text.length > 6000) text.substring(text.length - 6000) else text
        val url = urlRegex.findAll(tail).lastOrNull()?.value?.trimEnd('.', ',', ')', '"', '\'') ?: return
        if (url != lastLink && url.startsWith("https://accounts.google.com/")) showLink(url)
    }

    private fun showLink(url: String) {
        lastLink = url
        linkText.text = getString(R.string.signin_link_found)
        linkBar.visibility = View.VISIBLE
    }

    private fun openLink(url: String) {
        // Chrome on the same phone can reach agy's localhost OAuth callback.
        Connections.openInChrome(this, url)
    }

    // ---- Extra keys row -------------------------------------------------------------------------

    private fun buildExtraKeys(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#121212"))
        }
        fun key(label: String, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; textSize = 14f
            setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
            minWidth = 0; minimumWidth = dp(46); minHeight = 0; minimumHeight = dp(40)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { action(); terminal.requestFocus() }
        }.also { row.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42))) }

        key("ESC") { sendKey(KeyEvent.KEYCODE_ESCAPE) }
        key("TAB") { sendKey(KeyEvent.KEYCODE_TAB) }
        ctrlBtn = key("CTRL") { ctrlOn = !ctrlOn; refreshModifiers() }
        altBtn = key("ALT") { altOn = !altOn; refreshModifiers() }
        key("↑") { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
        key("↓") { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        key("←") { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        key("→") { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        key("⏎") { sendKey(KeyEvent.KEYCODE_ENTER) }
        key("^C") { current?.write(byteArrayOf(3), 0, 1) }
        key("/") { sendText("/") }
        key("@") { sendText("@") }
        key("-") { sendText("-") }
        key("|") { sendText("|") }
        key("~") { sendText("~") }
        key("HOME") { sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
        key("END") { sendKey(KeyEvent.KEYCODE_MOVE_END) }
        key("PGUP") { sendKey(KeyEvent.KEYCODE_PAGE_UP) }
        key("PGDN") { sendKey(KeyEvent.KEYCODE_PAGE_DOWN) }
        key(getString(R.string.paste)) { paste() }
        key("⌨") { toggleKeyboard() }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun refreshModifiers() {
        ctrlBtn.setTextColor(if (ctrlOn) Color.parseColor("#7C8CFF") else Color.WHITE)
        altBtn.setTextColor(if (altOn) Color.parseColor("#7C8CFF") else Color.WHITE)
    }

    private fun sendKey(keyCode: Int) {
        var mod = 0
        if (ctrlOn) mod = mod or KeyHandler.KEYMOD_CTRL
        if (altOn) mod = mod or KeyHandler.KEYMOD_ALT
        terminal.handleKeyCode(keyCode, mod)
        if (ctrlOn || altOn) { ctrlOn = false; altOn = false; refreshModifiers() }
    }

    private fun sendText(text: String) {
        text.codePoints().forEach { terminal.inputCodePoint(it, false, false) }
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        current?.emulator?.paste(text)
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        terminal.requestFocus()
        @Suppress("DEPRECATION")
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        terminal.requestFocus()
        imm.showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
    }

    // ---- Menu -----------------------------------------------------------------------------------

    private fun showMenu(anchor: View) {
        val s = service
        val m = Menu(this)
        m.item(R.drawable.ic_sparkle, getString(R.string.new_agy)) { s?.let { show(it.startAgy()) } }
        m.item(R.drawable.ic_terminal, getString(R.string.new_shell)) { s?.let { show(it.startShell()) } }
        if ((s?.sessions?.size ?: 0) > 1) m.item(R.drawable.ic_refresh, getString(R.string.switch_session)) {
            s?.let { svc -> val i = svc.sessions.indexOf(current); show(svc.sessions[(i + 1) % svc.sessions.size]) }
        }
        m.item(R.drawable.ic_paste, getString(R.string.paste)) { paste() }
        m.item(R.drawable.ic_textsize, getString(R.string.keyboard)) { toggleKeyboard() }
        m.item(R.drawable.ic_add, getString(R.string.font_bigger)) { changeFont(dp(1)) }
        m.item(R.drawable.ic_textsize, getString(R.string.font_smaller)) { changeFont(-dp(1)) }
        m.item(R.drawable.ic_close, getString(R.string.close_session)) { current?.let { c -> confirm(R.string.close_session_confirm) { s?.remove(c) } } }
        m.item(R.drawable.ic_stop, getString(R.string.stop_all), danger = true) {
            confirm(R.string.stop_all_confirm) {
                startService(Intent(this, TerminalService::class.java).setAction(TerminalService.ACTION_STOP))
                finish()
            }
        }
        m.show(anchor)
    }

    private fun confirm(msg: Int, action: () -> Unit) = Ui.confirm(this, getString(msg), getString(android.R.string.ok), danger = true) { action() }

    private fun changeFont(delta: Int) {
        fontSize = (fontSize + delta).coerceIn(dp(6), dp(30))
        prefs.edit().putInt("font", fontSize).apply()
        terminal.setTextSize(fontSize)
    }

    // ---- TerminalViewClient ---------------------------------------------------------------------

    override fun onScale(scale: Float): Float {
        if (scale < 0.9f || scale > 1.1f) {
            changeFont(if (scale > 1f) dp(1) else -dp(1))
            return 1.0f
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent?) { showKeyboard() }
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = true
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Back leaves the terminal; sessions keep running in the background service.
            finish(); return true
        }
        return false
    }
    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
    override fun onLongPress(event: MotionEvent?) = false
    override fun readControlKey() = ctrlOn
    override fun readAltKey() = altOn
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        if (ctrlOn || altOn) runOnUiThread { ctrlOn = false; altOn = false; refreshModifiers() }
        return false
    }
    override fun onEmulatorSet() { updateTitle() }
    override fun logError(tag: String?, message: String?) { android.util.Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { android.util.Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { android.util.Log.e(tag, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { android.util.Log.e(tag, "error", e) }
}
