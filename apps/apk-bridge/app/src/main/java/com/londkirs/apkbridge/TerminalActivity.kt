package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Runs any ADB shell command on the device and shows the output live. */
class TerminalActivity : Activity() {
    private lateinit var output: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var runButton: Button
    private lateinit var stopButton: Button
    private var running = false

    private val stateListener: (BridgeState) -> Unit = { state ->
        if (::runButton.isInitialized) {
            runButton.isEnabled = !state.busy
            runButton.alpha = if (state.busy) 0.5f else 1f
            stopButton.visibility = if (state.busy && running) View.VISIBLE else View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
        if (savedInstanceState != null) {
            output.text = savedInstanceState.getCharSequence(KEY_OUTPUT) ?: ""
        } else {
            appendLine("APK Bridge — מסוף ADB", INFO)
            appendLine("הפקודות רצות כמשתמש shell של ADB (לא root). אפשר להקליד גם \"adb shell …\" כמו במחשב.", INFO)
        }
    }

    override fun onStart() {
        super.onStart()
        Bridge.addStateListener(stateListener)
    }

    override fun onStop() {
        Bridge.removeStateListener(stateListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(KEY_OUTPUT, output.text)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1A17.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(8))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        top.addView(TextView(this).apply {
            text = "מסוף ADB"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFE7D1.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(smallButton("היסטוריה") { showHistory() })
        top.addView(smallButton("העתק") { copyOutput() })
        top.addView(smallButton("נקה") { output.text = "" })
        top.addView(smallButton("סגור") { finish() })
        root.addView(top, wrap(6))

        // Quick commands
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, cmd) in QUICK) {
            chips.addView(smallButton(label) { input.setText(cmd); input.setSelection(cmd.length) })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, wrap(6))

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(0xFFEDE3D9.toInt())
            setTextIsSelectable(true)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        outputScroll = ScrollView(this).apply {
            addView(output)
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFF0F0D0B.toInt()) }
        }
        root.addView(outputScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = dp(8) })

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "הקלד פקודה, למשל: pm list packages -3"
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF8A7868.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xFF2B2520.toInt())
                setStroke(dp(1), 0xFF5A4A3D.toInt())
            }
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) { submit(); true } else false
            }
        }
        bottom.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        stopButton = smallButton("עצור") { Bridge.cancelCurrent() }.apply { visibility = View.GONE }
        bottom.addView(stopButton)
        runButton = Button(this).apply {
            text = "הרץ"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            stateListAnimator = null
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFFF09A45.toInt()) }
            setOnClickListener { submit() }
        }
        bottom.addView(runButton)
        root.addView(bottom, wrap(0))

        Compat.applySystemBarInsets(this, root, includeIme = true)
        setContentView(root)
    }

    private fun submit() {
        val raw = input.text.toString().trim()
        if (raw.isEmpty()) return
        val cmd = Bridge.normalizeCommand(raw)
        if (cmd.isEmpty()) {
            appendLine("מסוף אינטראקטיבי (adb shell בלי פקודה) לא נתמך — הקלד פקודה מלאה.", ERROR)
            return
        }
        if (DANGEROUS.any { cmd.contains(it) }) {
            AlertDialog.Builder(this)
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
        appendLine("$ $cmd", PROMPT)
        running = true
        val started = Bridge.runCommand(
            raw,
            onOutput = { text, isError -> append(text, if (isError) ERROR else null) },
            onExit = { code ->
                running = false
                stopButton.visibility = View.GONE
                appendLine(if (code == null) "[הפקודה לא הושלמה — ראה את הסיבה ביומן במסך הראשי]" else "[קוד יציאה $code]", INFO)
            },
        )
        if (!started) {
            running = false
            appendLine("פעולה אחרת עדיין רצה — נסה שוב בעוד רגע.", ERROR)
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
        // Keep the view responsive on commands with huge output.
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
        val list = (listOf(cmd) + history().filter { it != cmd }).take(40)
        getSharedPreferences("terminal", Context.MODE_PRIVATE).edit().putString("history", list.joinToString("\n")).apply()
    }

    private fun showHistory() {
        val items = history()
        if (items.isEmpty()) {
            Toast.makeText(this, "אין עדיין היסטוריה.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("פקודות אחרונות")
            .setItems(items.toTypedArray()) { _, which ->
                input.setText(items[which])
                input.setSelection(items[which].length)
            }
            .setNegativeButton("סגור", null)
            .show()
    }

    private fun copyOutput() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("ADB output", output.text))
        Toast.makeText(this, "הפלט הועתק.", Toast.LENGTH_SHORT).show()
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setTextColor(0xFFFFE7D1.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFF3A302A.toInt()) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4) }
        setOnClickListener { action() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wrap(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    companion object {
        private const val KEY_OUTPUT = "output"
        private const val MAX_CHARS = 200_000
        private val PROMPT = 0xFFF09A45.toInt()
        private val ERROR = 0xFFFF7A6E.toInt()
        private val INFO = 0xFF8FB9A8.toInt()
        private val DANGEROUS = listOf("rm -r", "rm -f", "reboot", "wipe", "pm uninstall", "pm clear", "format", "dd ", "mkfs", "svc power")
        private val QUICK = listOf(
            "גרסת Android" to "getprop ro.build.version.release",
            "דגם" to "getprop ro.product.model",
            "אפליקציות שהותקנו" to "pm list packages -3",
            "סוללה" to "dumpsys battery",
            "מסך" to "wm size; wm density",
            "אחסון" to "df -h /data /sdcard",
            "זיכרון" to "cat /proc/meminfo | head -5",
            "תהליכים" to "top -b -n 1 | head -25",
            "רשת" to "ip addr show wlan0",
            "לוג אחרון" to "logcat -d -t 100",
            "צילום מסך" to "screencap -p /sdcard/Download/screenshot.png && echo saved to Download",
            "הפעלה מחדש" to "reboot",
        )
    }
}
