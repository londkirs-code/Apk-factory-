package com.londkirs.apkbridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.launch

/** Colors of the mini apps' charts (clear, modern, readable on white cards). */
object MiniColors {
    val GREEN = 0xFF2DB964.toInt()
    val BLUE = 0xFF2F80ED.toInt()
    val ORANGE = 0xFFF09A45.toInt()
    val RED = 0xFFE5484D.toInt()
    val PURPLE = 0xFF8E5CF7.toInt()
    val TEAL = 0xFF14A5A0.toInt()
    val YELLOW = 0xFFF5B82E.toInt()
    val TRACK = 0xFFF1E9E1.toInt()
    val CARD = 0xFFFFFFFF.toInt()
    val PALETTE = listOf(BLUE, PURPLE, TEAL, ORANGE, GREEN, YELLOW, RED, 0xFF6B7280.toInt())
}

/** A round gauge: a thick arc for [fraction], a big value in the middle and a small caption under it. */
class RingGauge(context: Context) : View(context) {
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var color = MiniColors.GREEN
        set(v) { field = v; invalidate() }
    var value = ""
        set(v) { field = v; invalidate() }
    var caption = ""
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14 * d; color = MiniColors.TRACK; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14 * d; strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Theme.INK }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Theme.MUTED }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (170 * d).toInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val pad = 10 * d
        val r = RectF((width - s) / 2 + pad, (height - s) / 2 + pad, (width + s) / 2 - pad, (height + s) / 2 - pad)
        canvas.drawArc(r, 135f, 270f, false, track)
        arc.color = color
        if (fraction > 0f) canvas.drawArc(r, 135f, 270f * fraction, false, arc)
        big.textSize = s * 0.2f
        small.textSize = s * 0.075f
        canvas.drawText(value, width / 2f, height / 2f + big.textSize * 0.3f, big)
        canvas.drawText(caption, width / 2f, height / 2f + big.textSize * 0.3f + small.textSize * 1.6f, small)
    }
}

/** A small live line chart (last [capacity] points), with a soft fill under the line. */
class SparkLine(context: Context) : View(context) {
    private val points = ArrayDeque<Float>()
    var capacity = 60
    var color = MiniColors.BLUE
        set(v) { field = v; invalidate() }
    /** Fixed scale (e.g. 0..100 %); null = from the data. */
    var minValue: Float? = null
    var maxValue: Float? = null

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MiniColors.TRACK; strokeWidth = 1 * d }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    fun add(value: Float) {
        points.addLast(value)
        while (points.size > capacity) points.removeFirst()
        invalidate()
    }

    fun clear() { points.clear(); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((96 * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val pad = 6 * d
        for (i in 0..2) canvas.drawLine(0f, pad + (h - 2 * pad) * i / 2, w, pad + (h - 2 * pad) * i / 2, grid)
        if (points.size < 2) return
        val lo = minValue ?: points.min()
        var hi = maxValue ?: points.max()
        if (hi - lo < 1e-3f) hi = lo + 1f
        fun x(i: Int) = w * i / (capacity - 1).coerceAtLeast(1)
        fun y(v: Float) = h - pad - (h - 2 * pad) * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        val start = capacity - points.size
        val path = Path()
        points.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(start + i), y(v)) else path.lineTo(x(start + i), y(v)) }
        val area = Path(path).apply { lineTo(x(start + points.size - 1), h); lineTo(x(start), h); close() }
        fill.shader = LinearGradient(0f, 0f, 0f, h, (color and 0x00FFFFFF) or 0x40000000, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawPath(area, fill)
        line.color = color
        canvas.drawPath(path, line)
        dot.color = color
        canvas.drawCircle(x(start + points.size - 1), y(points.last()), 4 * d, dot)
    }
}

/** A rounded bar made of colored parts (e.g. RAM: apps · cache · free). */
class StackedBar(context: Context) : View(context) {
    var parts: List<Pair<Float, Int>> = emptyList()
        set(v) { field = v; invalidate() }
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((16 * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = h / 2
        clip.reset(); clip.addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW)
        canvas.save(); canvas.clipPath(clip)
        paint.color = MiniColors.TRACK; canvas.drawRect(0f, 0f, w, h, paint)
        val total = parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1e-6f)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var x = 0f
        for ((value, color) in parts) {
            val pw = w * value / total
            paint.color = color
            if (rtl) canvas.drawRect(w - x - pw, 0f, w - x, h, paint) else canvas.drawRect(x, 0f, x + pw, h, paint)
            x += pw
        }
        canvas.restore()
    }
}

/** Layout helpers shared by the mini apps (cards, stat tiles, bar rows, action chips). */
object Mini {
    private fun dp(c: Context, v: Int) = c.dp(v)

    /** A white card with an optional title, added to [parent]. Returns its body. */
    fun card(parent: LinearLayout, title: String? = null, trailing: View? = null): LinearLayout {
        val c = parent.context
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
            background = GradientDrawable().apply { cornerRadius = dp(c, 20).toFloat(); setColor(MiniColors.CARD) }
            elevation = dp(c, 2).toFloat()
        }
        if (title != null) {
            val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(c).apply {
                text = title; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (trailing != null) head.addView(trailing)
            card.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(c, 10) })
        }
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 8))
        })
        return card
    }

    /** Grey small text. */
    fun hint(c: Context, text: String) = TextView(c).apply {
        this.text = text; textSize = 12.5f; setTextColor(Theme.MUTED); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    /** Tiles of (label, value) two per row; returns the value views by label, for live updates. */
    fun tiles(parent: LinearLayout, items: List<Pair<String, String>>): Map<String, TextView> {
        val c = parent.context
        val values = linkedMapOf<String, TextView>()
        items.chunked(2).forEach { row ->
            val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { i, (label, value) ->
                val tile = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10))
                    background = GradientDrawable().apply { cornerRadius = dp(c, 14).toFloat(); setColor(0xFFFBF6F1.toInt()) }
                }
                tile.addView(TextView(c).apply { text = label; textSize = 12f; setTextColor(Theme.MUTED); textAlignment = View.TEXT_ALIGNMENT_VIEW_START })
                val v = TextView(c).apply { text = value; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK); textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
                tile.addView(v)
                values[label] = v
                line.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i == 0 && row.size > 1) marginEnd = dp(c, 8)
                })
            }
            if (row.size == 1) line.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(c, 8) })
            parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(c, 8) })
        }
        return values
    }

    /** A row: name and value on one line, a thin proportional bar under it. Tappable when [onClick] is set. */
    fun barRow(parent: LinearLayout, name: String, value: String, fraction: Float, color: Int, onClick: (() -> Unit)? = null) {
        val c = parent.context
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 2), dp(c, 6), dp(c, 2), dp(c, 6))
            if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() }; Compat.focusRing(this, Theme.BROWN, 8f) }
        }
        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(TextView(c).apply {
            text = name; textSize = 14f; setTextColor(Theme.INK); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(c).apply { text = value; textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.BODY) })
        row.addView(top)
        row.addView(StackedBar(c).apply { parts = listOf(fraction.coerceIn(0f, 1f) to color, (1f - fraction.coerceIn(0f, 1f)) to MiniColors.TRACK) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 6)).apply { topMargin = dp(c, 5) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** A legend line: colored dot, name, value. */
    fun legend(parent: LinearLayout, color: Int, name: String, value: String) {
        val c = parent.context
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(c, 3), 0, dp(c, 3)) }
        row.addView(View(c).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
            LinearLayout.LayoutParams(dp(c, 10), dp(c, 10)).apply { marginEnd = dp(c, 8) })
        row.addView(TextView(c).apply { text = name; textSize = 13.5f; setTextColor(Theme.BODY); textAlignment = View.TEXT_ALIGNMENT_VIEW_START },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(c).apply { text = value; textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK) })
        parent.addView(row)
    }

    /** Action buttons in a wrapping grid, two per row. */
    fun actions(parent: LinearLayout, items: List<Pair<String, () -> Unit>>, danger: Set<String> = emptySet()) {
        val c = parent.context
        items.chunked(2).forEach { row ->
            val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { i, (label, action) ->
                line.addView(TextView(c).apply {
                    text = label
                    textSize = 14f
                    gravity = Gravity.CENTER
                    val bad = label in danger
                    setTextColor(if (bad) MiniColors.RED else Theme.BROWN)
                    setPadding(dp(c, 10), dp(c, 12), dp(c, 10), dp(c, 12))
                    background = GradientDrawable().apply { cornerRadius = dp(c, 14).toFloat(); setColor(if (bad) 0xFFFDECEC.toInt() else Theme.SOFT) }
                    isClickable = true; isFocusable = true
                    setOnClickListener { action() }
                    Compat.focusRing(this, Theme.BROWN, 14f)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i == 0 && row.size > 1) marginEnd = dp(c, 8) })
            }
            if (row.size == 1) line.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(c, 8) })
            parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(c, 8) })
        }
    }

    /** "1.2 GB" / "850 MB" from kilobytes. */
    fun kb(k: Long): String = if (k >= 1024 * 1024) String.format(java.util.Locale.US, "%.2f GB", k / 1048576.0)
        else String.format(java.util.Locale.US, "%.0f MB", k / 1024.0)
}

/**
 * Base of the mini apps: a clean top bar (back · title · refresh), a scrolling page, and ADB
 * commands run in the background with the result back on the screen.
 */
abstract class MiniAppActivity : BossActivity() {
    protected lateinit var page: LinearLayout
    private lateinit var status: TextView
    protected abstract val appTitle: String
    protected open val accent: Int get() = MiniColors.GREEN

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF7F2ED.toInt()); layoutDirection = Lang.direction }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), dp(6), dp(6))
        }
        bar.addView(topButton(tr("→", "←"), tr("חזרה", "Back")) { finish() })
        bar.addView(TextView(this).apply {
            text = appTitle; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(topButton("⟳", tr("רענן", "Refresh")) { refresh() })
        root.addView(bar)
        status = TextView(this).apply {
            textSize = 12.5f; setTextColor(Theme.MUTED); setPadding(dp(18), 0, dp(18), dp(4)); visibility = View.GONE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        root.addView(status)
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(24)) }
        root.addView(android.widget.ScrollView(this).apply { addView(page) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        Compat.applySystemBarInsets(this, root, includeIme = false)
        setContentView(root)
        build()
        refresh()
    }

    /** Creates the cards (once). */
    protected abstract fun build()

    /** Reloads the data shown (the ⟳ button, after actions, on start). */
    protected abstract fun refresh()

    private fun topButton(label: String, description: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 22f; setTextColor(Theme.INK); gravity = Gravity.CENTER
        setPadding(dp(14), dp(6), dp(14), dp(6)); contentDescription = description
        isClickable = true; isFocusable = true; setOnClickListener { action() }
        Compat.focusRing(this, Theme.BROWN, 18f)
    }

    protected fun showStatus(text: String?) {
        status.text = text.orEmpty()
        status.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** Runs a shell command over ADB in the background; [onResult] gets the output on the screen thread (null = failed). */
    protected fun adb(command: String, onResult: (String?) -> Unit) {
        Bridge.scope.launch {
            val out = runCatching { kotlinx.coroutines.withTimeout(30_000) { Bridge.withClient { it.shell(command).stdoutText } } }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                out.onFailure { showStatus(tr("אין חיבור ADB: ${Bridge.explain(it)}", "No ADB connection: ${Bridge.explain(it)}")) }
                onResult(out.getOrNull())
            }
        }
    }

    /** Runs an action command, tells the user the result, then refreshes. */
    protected fun act(label: String, command: String, confirm: String? = null) {
        fun go() {
            android.widget.Toast.makeText(this, "⏳ $label", android.widget.Toast.LENGTH_SHORT).show()
            adb(command) { out ->
                val line = out?.trim()?.lines()?.lastOrNull { it.isNotBlank() }
                android.widget.Toast.makeText(this, if (out == null) "✖ $label" else "✔ $label" + (line?.let { "\n$it" } ?: ""), android.widget.Toast.LENGTH_LONG).show()
                refresh()
            }
        }
        if (confirm == null) go() else android.app.AlertDialog.Builder(this)
            .setTitle(label).setMessage(confirm)
            .setPositiveButton(tr("המשך", "Continue")) { _, _ -> go() }
            .setNegativeButton(tr("ביטול", "Cancel"), null)
            .show()
    }

    /** Actions for one app (from a usage list): stop, limit in the background, app info. */
    protected fun appMenu(pkg: String, label: String) {
        if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) return
        val items = listOf(
            tr("⏹ עצור בכוח", "⏹ Force stop") to { act(tr("עצירה: $label", "Stop: $label"), "am force-stop $pkg && echo stopped") },
            tr("🧹 בקש לשחרר זיכרון", "🧹 Ask to release memory") to { act(tr("שחרור זיכרון: $label", "Release memory: $label"), "am send-trim-memory $pkg COMPLETE 2>&1 || am send-trim-memory $pkg RUNNING_CRITICAL 2>&1; echo done") },
            tr("🚫 אל תרוץ ברקע", "🚫 Don't run in background") to { act(tr("הגבלת רקע: $label", "Background limit: $label"), "cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore && echo 'Background: blocked'") },
            tr("✅ אפשר ריצה ברקע", "✅ Allow background") to { act(tr("ביטול הגבלה: $label", "Allow background: $label"), "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow && echo 'Background: allowed'") },
            tr("ℹ פרטי אפליקציה", "ℹ App info") to {
                runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))) }
                Unit
            },
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("$label\n$pkg")
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .show()
    }

    protected fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
