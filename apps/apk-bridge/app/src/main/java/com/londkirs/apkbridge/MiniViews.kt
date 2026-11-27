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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((58 * d).toInt(), heightMeasureSpec))
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

/** A battery drawn like the icon on the phone: fill by level, % inside, a bolt while charging. */
class BatteryView(context: Context) : View(context) {
    var level = 0
        set(v) { field = v.coerceIn(0, 100); invalidate() }
    var charging = false
        set(v) { field = v; invalidate() }
    var color = MiniColors.GREEN
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d; color = 0xFF3A3029.toInt() }
    private val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3029.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(resolveSize((118 * d).toInt(), widthMeasureSpec), resolveSize((56 * d).toInt(), heightMeasureSpec))

    override fun onDraw(canvas: Canvas) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val w = width.toFloat(); val h = height.toFloat(); val capW = 6 * d; val s = frame.strokeWidth
        // Body, and the small cap on the "plus" side (left in Hebrew, like the system icon there).
        val body = if (rtl) RectF(capW + s, s, w - s, h - s) else RectF(s, s, w - capW - s, h - s)
        canvas.drawRoundRect(body, 9 * d, 9 * d, frame)
        val capRect = if (rtl) RectF(0f, h * 0.33f, capW + s, h * 0.67f) else RectF(w - capW - s, h * 0.33f, w, h * 0.67f)
        canvas.drawRoundRect(capRect, 2 * d, 2 * d, cap)
        val inset = 4.5f * d
        val inner = RectF(body.left + inset, body.top + inset, body.right - inset, body.bottom - inset)
        val fw = inner.width() * level / 100f
        fill.color = color
        val f = if (rtl) RectF(inner.right - fw, inner.top, inner.right, inner.bottom) else RectF(inner.left, inner.top, inner.left + fw, inner.bottom)
        if (fw > 0) canvas.drawRoundRect(f, 5 * d, 5 * d, fill)
        text.textSize = h * 0.36f
        text.color = if (level >= 55) 0xFFFFFFFF.toInt() else Theme.INK
        val label = "$level%"
        val cx = inner.centerX() + if (charging) (if (rtl) -h * 0.14f else h * 0.14f) else 0f
        canvas.drawText(label, cx, inner.centerY() + text.textSize * 0.36f, text)
        if (charging) {
            val bx = if (rtl) inner.right - h * 0.42f else inner.left + h * 0.2f
            val by = inner.top + inner.height() * 0.12f; val bh = inner.height() * 0.76f; val bw = bh * 0.55f
            val path = Path().apply {
                moveTo(bx + bw * 0.6f, by); lineTo(bx, by + bh * 0.56f); lineTo(bx + bw * 0.45f, by + bh * 0.56f)
                lineTo(bx + bw * 0.35f, by + bh); lineTo(bx + bw, by + bh * 0.42f); lineTo(bx + bw * 0.55f, by + bh * 0.42f); close()
            }
            bolt.color = if (level >= 30) 0xFFFFFFFF.toInt() else MiniColors.TEAL
            canvas.drawPath(path, bolt)
        }
    }
}

/**
 * A RAM stick (memory module): a board with eight chips and gold pins. The chips light up from one
 * side by what the memory holds (apps · cache · kernel …), grey where it is free.
 */
class RamStickView(context: Context) : View(context) {
    var parts: List<Pair<Float, Int>> = emptyList()
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F6B55.toInt() }
    private val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD8AE4A.toInt() }
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2A26.toInt() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize((62 * d).toInt(), heightMeasureSpec))

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val pinsH = 9 * d
        canvas.drawRoundRect(RectF(0f, 0f, w, h - pinsH * 0.4f), 6 * d, 6 * d, board)
        // Gold pins along the bottom, with the notch a third of the way.
        val pinW = 3 * d; val gap = 2.2f * d
        var x = 8 * d
        while (x < w - 8 * d) {
            if (kotlin.math.abs(x - w * 0.36f) > 5 * d) canvas.drawRect(x, h - pinsH, x + pinW, h, pin)
            x += pinW + gap
        }
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val total = parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1e-6f)
        val n = 8; val pad = 7 * d; val cg = 5 * d
        val cw = (w - 2 * pad - (n - 1) * cg) / n
        val top = 7 * d; val bottom = h - pinsH - 6 * d
        for (i in 0 until n) {
            val slot = if (rtl) n - 1 - i else i
            val left = pad + slot * (cw + cg)
            val r = RectF(left, top, left + cw, bottom)
            canvas.drawRoundRect(r, 2.5f * d, 2.5f * d, chip)
            // This chip covers [i/n, (i+1)/n] of the memory: color the parts that fall in it.
            val c0 = i.toFloat() / n; val c1 = (i + 1f) / n
            var acc = 0f
            for ((value, color) in parts) {
                val a = acc / total; val b = (acc + value) / total
                acc += value
                val o0 = maxOf(a, c0); val o1 = minOf(b, c1)
                if (o1 <= o0) continue
                val f0 = (o0 - c0) * n; val f1 = (o1 - c0) * n
                paint.color = color
                val inner = RectF(r.left + 2 * d, r.top + 2 * d, r.right - 2 * d, r.bottom - 2 * d)
                val iw = inner.width()
                val seg = if (rtl) RectF(inner.right - iw * f1, inner.top, inner.right - iw * f0, inner.bottom)
                    else RectF(inner.left + iw * f0, inner.top, inner.left + iw * f1, inner.bottom)
                canvas.drawRoundRect(seg, 1.5f * d, 1.5f * d, paint)
            }
        }
    }
}

/** Compact layout helpers shared by the mini apps (small cards, stat strips, icon actions, short lists). */
object Mini {
    private fun dp(c: Context, v: Int) = c.dp(v)

    /** A white card; with [title] a small header (and an optional view at its end). Returns its body. */
    fun card(parent: LinearLayout, title: String? = null, trailing: View? = null): LinearLayout {
        val c = parent.context
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10))
            background = GradientDrawable().apply { cornerRadius = dp(c, 16).toFloat(); setColor(MiniColors.CARD) }
        }
        if (title != null) {
            val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(c).apply {
                text = title; textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.BODY)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (trailing != null) head.addView(trailing)
            card.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(c, 6) })
        }
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4))
        })
        return card
    }

    fun hint(c: Context, text: String) = TextView(c).apply {
        this.text = text; textSize = 12f; setTextColor(Theme.MUTED); textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    /** A small bold value text (for card headers). */
    fun badge(c: Context) = TextView(c).apply { textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK) }

    /** A strip of small stats, [columns] per row: label above, value below. Returns the value views by label. */
    fun stats(parent: LinearLayout, items: List<Pair<String, String>>, columns: Int = 4): Map<String, TextView> {
        val c = parent.context
        val values = linkedMapOf<String, TextView>()
        items.chunked(columns).forEachIndexed { r, row ->
            val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until columns) {
                val item = row.getOrNull(k)
                val cell = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(c, 2), dp(c, 4), dp(c, 2), dp(c, 4)) }
                if (item != null) {
                    cell.addView(TextView(c).apply { text = item.first; textSize = 11f; setTextColor(Theme.MUTED); gravity = Gravity.CENTER; maxLines = 1 })
                    val v = TextView(c).apply { text = item.second; textSize = 14.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK); gravity = Gravity.CENTER; maxLines = 1 }
                    cell.addView(v)
                    values[item.first] = v
                }
                line.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (r > 0) topMargin = dp(c, 2) })
        }
        return values
    }

    /** One compact row: name · value, with a thin bar under it. */
    fun barRow(parent: LinearLayout, name: String, value: String, fraction: Float, color: Int, onClick: (() -> Unit)? = null) {
        val c = parent.context
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(c, 3), 0, dp(c, 4))
            if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() }; Compat.focusRing(this, Theme.BROWN, 6f) }
        }
        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(c).apply {
            text = name; textSize = 13f; setTextColor(Theme.INK); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(c).apply { text = value; textSize = 12.5f; setTextColor(Theme.BODY) })
        row.addView(top)
        row.addView(StackedBar(c).apply { parts = listOf(fraction.coerceIn(0f, 1f) to color, (1f - fraction.coerceIn(0f, 1f)) to MiniColors.TRACK) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 4)).apply { topMargin = dp(c, 3) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** The first [shown] rows, and a small "show all (N)" link that opens the rest in place. */
    fun <T> shortList(parent: LinearLayout, rows: List<T>, shown: Int = 5, draw: (LinearLayout, Int, T) -> Unit) {
        val c = parent.context
        rows.take(shown).forEachIndexed { i, r -> draw(parent, i, r) }
        if (rows.size <= shown) return
        val rest = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        rows.drop(shown).forEachIndexed { i, r -> draw(rest, i + shown, r) }
        val more = TextView(c).apply {
            text = tr("הצג הכול (${rows.size}) ▾", "Show all (${rows.size}) ▾")
            textSize = 12.5f; setTextColor(Theme.LINK); setPadding(0, dp(c, 4), 0, dp(c, 2))
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            isClickable = true; isFocusable = true
        }
        more.setOnClickListener {
            val open = rest.visibility != View.VISIBLE
            rest.visibility = if (open) View.VISIBLE else View.GONE
            more.text = if (open) tr("הצג פחות ▴", "Show less ▴") else tr("הצג הכול (${rows.size}) ▾", "Show all (${rows.size}) ▾")
        }
        parent.addView(rest)
        parent.addView(more)
    }

    /** Legend dots in two columns: ● name  value. */
    fun legend(parent: LinearLayout, items: List<Triple<Int, String, String>>) {
        val c = parent.context
        items.chunked(2).forEach { pair ->
            val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0..1) {
                val it = pair.getOrNull(k)
                val cell = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(c, 2), dp(c, 4), dp(c, 2)) }
                if (it != null) {
                    cell.addView(View(c).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(it.first) } },
                        LinearLayout.LayoutParams(dp(c, 8), dp(c, 8)).apply { marginEnd = dp(c, 6) })
                    cell.addView(TextView(c).apply { text = it.second; textSize = 12f; setTextColor(Theme.BODY); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    cell.addView(TextView(c).apply { text = it.third; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK); setPadding(dp(c, 4), 0, 0, 0) })
                }
                line.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            parent.addView(line)
        }
    }

    /** One action: a round icon button with a short name under it (like Quick Settings). */
    class Action(val icon: String, val label: String, val active: Boolean = false, val danger: Boolean = false, val run: () -> Unit)

    fun actions(parent: LinearLayout, items: List<Action>, perRow: Int = 4) {
        val c = parent.context
        items.chunked(perRow).forEach { row ->
            val line = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until perRow) {
                val a = row.getOrNull(k)
                val cell = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(c, 2), dp(c, 4), dp(c, 2), dp(c, 4)) }
                if (a != null) {
                    cell.isClickable = true; cell.isFocusable = true
                    cell.setOnClickListener { a.run() }
                    cell.contentDescription = a.label
                    Compat.focusRing(cell, Theme.BROWN, 12f)
                    cell.addView(TextView(c).apply {
                        text = a.icon; textSize = 18f; gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(when { a.danger -> 0xFFFDE8E8.toInt(); a.active -> 0xFFD6F5E3.toInt(); else -> 0xFFF6EEE6.toInt() })
                            if (a.active) setStroke(dp(c, 2), MiniColors.GREEN)
                        }
                    }, LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)))
                    cell.addView(TextView(c).apply {
                        text = a.label; textSize = 11f; gravity = Gravity.CENTER; maxLines = 2
                        setTextColor(if (a.danger) MiniColors.RED else Theme.BODY); setPadding(0, dp(c, 3), 0, 0)
                    })
                }
                line.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            parent.addView(line)
        }
    }

    /** "1.2 GB" / "850 MB" from kilobytes. */
    fun kb(k: Long): String = if (k >= 1024 * 1024) String.format(java.util.Locale.US, "%.1f GB", k / 1048576.0)
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
            setPadding(0, dp(6), dp(4), dp(2))
        }
        bar.addView(topButton(tr("→", "←"), tr("חזרה", "Back")) { finish() })
        bar.addView(TextView(this).apply {
            text = appTitle; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Theme.INK)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(topButton("⟳", tr("רענן", "Refresh")) { refresh() })
        root.addView(bar)
        status = TextView(this).apply {
            textSize = 12.5f; setTextColor(Theme.MUTED); setPadding(dp(18), 0, dp(18), dp(4)); visibility = View.GONE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        root.addView(status)
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(12)) }
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
        text = label; textSize = 20f; setTextColor(Theme.INK); gravity = Gravity.CENTER
        setPadding(dp(12), dp(4), dp(12), dp(4)); contentDescription = description
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
