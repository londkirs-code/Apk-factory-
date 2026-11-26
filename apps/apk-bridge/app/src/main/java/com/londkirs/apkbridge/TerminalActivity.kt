package com.londkirs.apkbridge

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Stand-alone ADB terminal (own launcher icon and task): black screen, phosphor-green
 * monospace text, live output. Commands run as the ADB "shell" user through [Bridge].
 */
class TerminalActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var statusDot: TextView
    private lateinit var statusText: TextView
    private lateinit var stopButton: TextView
    private var cursorBlink: ObjectAnimator? = null
    private var running = false
    private var historyIndex = -1

    private val prompt: String by lazy { "shell@${Build.MODEL.replace(' ', '_').lowercase()}:/ $" }

    private val stateListener: (BridgeState) -> Unit = { state -> renderState(state) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
        if (savedInstanceState != null) {
            output.text = savedInstanceState.getCharSequence(KEY_OUTPUT) ?: ""
        } else {
            banner()
        }
    }

    override fun onStart() {
        super.onStart()
        Bridge.addStateListener(stateListener)
        input.requestFocus()
    }

    override fun onStop() {
        Bridge.removeStateListener(stateListener)
        super.onStop()
    }

    override fun onDestroy() {
        cursorBlink?.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(KEY_OUTPUT, output.text)
    }

    // region UI

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        // Title bar: ● ADB TERMINAL ........ [HIST] [COPY] [CLR] [×]
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply { setColor(0xFF050A06.toInt()) }
        }
        statusDot = mono("●", 14f, DIM)
        bar.addView(statusDot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(mono("ADB TERMINAL", 16f, GREEN).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); glow(this, 10f) })
        statusText = mono("מוכן", 11f, DIM).apply { textDirection = View.TEXT_DIRECTION_ANY_RTL }
        titles.addView(statusText)
        bar.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(barButton("HIST") { showHistory() })
        bar.addView(barButton("COPY") { copyOutput() })
        bar.addView(barButton("CLR") { output.text = "" })
        bar.addView(barButton("✕") { finish() })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this).apply { setBackgroundColor(0xFF0F3D1C.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        // Output
        output = mono("", 12.5f, GREEN).apply {
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.12f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            glow(this, 6f)
        }
        outputScroll = ScrollView(this).apply {
            addView(output)
            isVerticalScrollBarEnabled = true
            setOnClickListener { input.requestFocus() }
        }
        root.addView(outputScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Quick commands
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        for ((label, cmd) in QUICK) chips.addView(chip(label) { setInput(cmd) })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF030603.toInt())
            addView(chips)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Prompt line:  shell@model:/ $ ▌ [input] ......... [▲] [■] [⏎]
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(6), dp(8))
            background = GradientDrawable().apply { setColor(0xFF050A06.toInt()) }
        }
        line.addView(mono("$", 16f, GREEN).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); glow(this, 8f) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        input = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(GREEN)
            setHintTextColor(0xFF1C5A2E.toInt())
            hint = "type a command…"
            background = null
            setPadding(0, dp(8), 0, dp(8))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_SEND
            textDirection = View.TEXT_DIRECTION_LTR
            glow(this, 6f)
            if (Build.VERSION.SDK_INT >= 29) {
                textCursorDrawable = GradientDrawable().apply { setColor(GREEN); setSize(dp(8), dp(18)) }
            }
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) { submit(); true } else false
            }
        }
        line.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(barButton("▲") { recallPrevious() })
        stopButton = barButton("■", RED) { Bridge.cancelCurrent() }.apply { visibility = View.GONE }
        line.addView(stopButton)
        line.addView(barButton("⏎") { submit() }.apply { textSize = 18f })
        root.addView(View(this).apply { setBackgroundColor(0xFF0F3D1C.toInt()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        Compat.applySystemBarInsets(this, root, includeIme = true)
        setContentView(root)

        cursorBlink = ObjectAnimator.ofFloat(statusDot, View.ALPHA, 1f, 0.15f).apply {
            duration = 450
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
    }

    private fun banner() {
        appendLine("▄▀█ █▀▄ █▄▄   ▀█▀ █▀▀ █▀█ █▀▄▀█", GREEN)
        appendLine("█▀█ █▄▀ █▄█    █  ██▄ █▀▄ █ ▀ █", GREEN)
        appendLine("APK Bridge · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}", DIM)
        appendLine("פקודות רצות כמשתמש shell (לא root). אפשר גם \"adb shell …\".", DIM)
        appendLine("", null)
    }

    private fun mono(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        typeface = Typeface.MONOSPACE
        setTextColor(color)
    }

    /** Soft phosphor glow around green text. */
    private fun glow(view: TextView, radius: Float) = view.setShadowLayer(radius, 0f, 0f, 0x8833FF66.toInt())

    private fun barButton(label: String, color: Int = GREEN, action: () -> Unit) = mono(label, 13f, color).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(6), dp(9), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(0x00000000)
            setStroke(dp(1), if (color == GREEN) 0xFF1F7A3A.toInt() else color)
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(5) }
        isClickable = true
        setOnClickListener { action() }
    }

    private fun chip(label: String, action: () -> Unit) = mono(label, 12f, GREEN).apply {
        setPadding(dp(10), dp(5), dp(10), dp(5))
        textDirection = View.TEXT_DIRECTION_ANY_RTL
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xFF07150B.toInt())
            setStroke(dp(1), 0xFF1F7A3A.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
        isClickable = true
        setOnClickListener { action() }
    }

    private fun renderState(state: BridgeState) {
        if (!::statusText.isInitialized) return
        val busy = state.busy
        statusText.text = state.status.lineSequence().firstOrNull().orEmpty().take(80)
        statusDot.setTextColor(if (busy) AMBER else GREEN)
        if (busy && running) {
            if (cursorBlink?.isRunning != true) cursorBlink?.start()
            stopButton.visibility = View.VISIBLE
        } else {
            cursorBlink?.cancel()
            statusDot.alpha = 1f
            stopButton.visibility = View.GONE
        }
    }

    // endregion

    // region commands

    private fun setInput(cmd: String) {
        input.setText(cmd)
        input.setSelection(cmd.length)
        input.requestFocus()
    }

    private fun submit() {
        val raw = input.text.toString().trim()
        if (raw.isEmpty()) return
        when (raw) {
            "clear", "cls" -> { output.text = ""; input.setText(""); return }
            "exit" -> { finish(); return }
        }
        val cmd = Bridge.normalizeCommand(raw)
        if (cmd.isEmpty()) {
            appendLine("interactive shell is not supported — type a full command.", RED)
            return
        }
        if (DANGEROUS.any { cmd.contains(it) }) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("פקודה שעלולה להזיק")
                .setMessage("הפקודה עלולה למחוק נתונים, להסיר אפליקציות או להפעיל מחדש את המכשיר:\n\n$cmd\n\nלהריץ בכל זאת?")
                .setPositiveButton("הרץ") { _, _ -> execute(raw, cmd) }
                .setNegativeButton("ביטול", null)
                .show()
        } else {
            execute(raw, cmd)
        }
    }

    private fun execute(raw: String, cmd: String) {
        appendLine("$prompt $cmd", PROMPT)
        running = true
        historyIndex = -1
        val started = Bridge.runCommand(
            raw,
            onOutput = { text, isError -> append(text, if (isError) RED else null) },
            onExit = { code ->
                running = false
                renderState(Bridge.state)
                when (code) {
                    null -> appendLine("[interrupted — see the log on the main screen]", AMBER)
                    0 -> appendLine("", null)
                    else -> appendLine("[exit $code]", AMBER)
                }
            },
        )
        if (!started) {
            running = false
            appendLine("busy: another operation is still running.", AMBER)
            return
        }
        saveHistory(raw)
        input.setText("")
    }

    private fun append(text: String, color: Int?) {
        if (color == null) output.append(text) else {
            val span = SpannableString(text)
            span.setSpan(ForegroundColorSpan(color), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            output.append(span)
        }
        if (output.length() > MAX_CHARS) {
            output.text = output.text.subSequence(output.length() - MAX_CHARS / 2, output.length())
        }
        outputScroll.post { outputScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun appendLine(text: String, color: Int?) {
        val prefix = if (output.text.isNotEmpty() && !output.text.endsWith("\n")) "\n" else ""
        append("$prefix$text\n", color)
    }

    private fun history(): List<String> =
        getSharedPreferences("terminal", Context.MODE_PRIVATE).getString("history", "").orEmpty()
            .split('\n').filter { it.isNotBlank() }

    private fun saveHistory(cmd: String) {
        val list = (listOf(cmd) + history().filter { it != cmd }).take(60)
        getSharedPreferences("terminal", Context.MODE_PRIVATE).edit().putString("history", list.joinToString("\n")).apply()
    }

    /** ▲ walks back through earlier commands, like the up arrow in a shell. */
    private fun recallPrevious() {
        val items = history()
        if (items.isEmpty()) return
        historyIndex = (historyIndex + 1) % items.size
        setInput(items[historyIndex])
    }

    private fun showHistory() {
        val items = history()
        if (items.isEmpty()) {
            Toast.makeText(this, "אין עדיין היסטוריה.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("history")
            .setItems(items.toTypedArray()) { _, which -> setInput(items[which]) }
            .setNegativeButton("סגור", null)
            .show()
    }

    private fun copyOutput() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ADB output", output.text))
        Toast.makeText(this, "הפלט הועתק.", Toast.LENGTH_SHORT).show()
    }

    // endregion

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_OUTPUT = "output"
        private const val MAX_CHARS = 200_000
        private val BLACK = 0xFF000000.toInt()
        private val GREEN = 0xFF39FF6A.toInt()
        private val DIM = 0xFF1FA04A.toInt()
        private val PROMPT = 0xFFB6FFC8.toInt()
        private val RED = 0xFFFF5F56.toInt()
        private val AMBER = 0xFFFFC145.toInt()
        private val DANGEROUS = listOf("rm -r", "rm -f", "reboot", "wipe", "pm uninstall", "pm clear", "format", "dd ", "mkfs", "svc power")
        private val QUICK = listOf(
            "גרסה" to "getprop ro.build.version.release",
            "דגם" to "getprop ro.product.model",
            "אפליקציות" to "pm list packages -3",
            "סוללה" to "dumpsys battery",
            "מסך" to "wm size; wm density",
            "אחסון" to "df -h /data /sdcard",
            "זיכרון" to "cat /proc/meminfo | head -5",
            "תהליכים" to "top -b -n 1 | head -25",
            "רשת" to "ip addr show wlan0",
            "לוג" to "logcat -d -t 100",
            "צילום מסך" to "screencap -p /sdcard/Download/screenshot.png && echo saved to Download",
            "הפעלה מחדש" to "reboot",
        )
    }
}
