package com.londkirs.antigravityremote

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Renders the agent's Markdown into native views: text with styles, and code blocks with "copy". */
object Markdown {

    sealed class Block {
        data class Text(val src: String) : Block()
        data class Code(val lang: String, val code: String) : Block()
        data class Table(val rows: String) : Block()
    }

    fun blocks(src: String): List<Block> {
        val out = mutableListOf<Block>()
        val lines = src.replace("\r\n", "\n").split('\n')
        val text = StringBuilder()
        var i = 0
        fun flush() { if (text.isNotBlank()) out += Block.Text(text.toString().trim('\n')); text.setLength(0) }
        while (i < lines.size) {
            val line = lines[i]
            val fence = line.trimStart()
            if (fence.startsWith("```")) {
                flush()
                val lang = fence.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.append(lines[i]).append('\n'); i++ }
                out += Block.Code(lang, code.toString().trimEnd('\n'))
                i++; continue
            }
            if (line.trimStart().startsWith("|") && line.count { it == '|' } >= 2) {
                flush()
                val rows = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) { rows += lines[i].trim(); i++ }
                out += Block.Table(formatTable(rows))
                continue
            }
            text.append(line).append('\n'); i++
        }
        flush()
        return out
    }

    /** Aligns a Markdown table into monospace columns. */
    private fun formatTable(rows: List<String>): String {
        val cells = rows.filterNot { it.replace("|", "").replace("-", "").replace(":", "").isBlank() }
            .map { r -> r.trim().trim('|').split('|').map { it.trim().replace("**", "") } }
        val n = cells.maxOfOrNull { it.size } ?: 0
        val widths = (0 until n).map { c -> cells.maxOf { it.getOrNull(c)?.length ?: 0 } }
        return cells.mapIndexed { ri, r ->
            val line = (0 until n).joinToString("  │  ") { c -> (r.getOrNull(c) ?: "").padEnd(widths[c]) }
            if (ri == 0 && cells.size > 1) line + "\n" + widths.joinToString("──┼──") { "─".repeat(it) } else line
        }.joinToString("\n")
    }

    /** Inline + line-level styling for a text block. */
    fun spans(a: Activity, src: String, linkColor: Int = T.primary): CharSequence {
        val out = SpannableStringBuilder()
        val lines = src.split('\n')
        lines.forEachIndexed { idx, raw ->
            var line = raw
            val start = out.length
            val h = Regex("^(#{1,6})\\s+(.*)$").find(line)
            var quote = false
            var bullet = false
            when {
                h != null -> line = h.groupValues[2]
                line.trimStart().startsWith("> ") || line.trim() == ">" -> { line = line.trimStart().removePrefix(">").trimStart(); quote = true }
                Regex("^\\s*[-*+]\\s+").containsMatchIn(line) && !line.trim().matches(Regex("[-*_]{3,}")) -> {
                    val indent = line.length - line.trimStart().length
                    line = " ".repeat(indent / 2) + "•  " + line.trimStart().substring(1).trimStart(); bullet = true
                }
                line.trim().matches(Regex("[-*_]{3,}")) -> line = "────────"
            }
            inline(a, out, line, linkColor)
            if (h != null) {
                val size = when (h.groupValues[1].length) { 1 -> 1.3f; 2 -> 1.2f; 3 -> 1.1f; else -> 1.0f }
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(size), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (quote) {
                out.setSpan(if (android.os.Build.VERSION.SDK_INT >= 28) QuoteSpan(T.quoteBar, a.dp(3), a.dp(10)) else QuoteSpan(T.quoteBar),
                    start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(ForegroundColorSpan(T.muted), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (bullet) out.setSpan(LeadingMarginSpan.Standard(0, a.dp(14)), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (idx < lines.size - 1) out.append('\n')
        }
        return out
    }

    private val inlineRe = Regex("""\*\*(.+?)\*\*|__(.+?)__|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?!\w)|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)|~~(.+?)~~|`([^`\n]+)`|\[([^\]\n]+)]\(([^)\s]+)\)|(https?://[^\s<>()\[\]"']+[^\s<>()\[\]"'.,;:!?])""")

    private fun inline(a: Activity, out: SpannableStringBuilder, text: String, linkColor: Int) {
        var last = 0
        for (m in inlineRe.findAll(text)) {
            out.append(text, last, m.range.first)
            val g = m.groupValues
            val s = out.length
            when {
                g[1].isNotEmpty() || g[2].isNotEmpty() -> { inline(a, out, g[1].ifEmpty { g[2] }, linkColor); out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                g[3].isNotEmpty() || g[4].isNotEmpty() -> { out.append(g[3].ifEmpty { g[4] }); out.setSpan(StyleSpan(Typeface.ITALIC), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                g[5].isNotEmpty() -> { out.append(g[5]); out.setSpan(StrikethroughSpan(), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                g[6].isNotEmpty() -> {
                    out.append(" ").append(g[6]).append(" ")
                    out.setSpan(TypefaceSpan("monospace"), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(BackgroundColorSpan(T.codeBg), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                g[7].isNotEmpty() -> { out.append(g[7]); out.setSpan(link(a, g[8], linkColor), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                g[9].isNotEmpty() -> { out.append(g[9]); out.setSpan(link(a, g[9], linkColor), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            last = m.range.last + 1
        }
        out.append(text, last, text.length)
    }

    fun link(a: Activity, url: String, color: Int) = object : ClickableSpan() {
        override fun onClick(widget: View) = openLink(a, url)
        override fun updateDrawState(ds: TextPaint) { ds.color = color; ds.isUnderlineText = true }
    }

    fun openLink(a: Activity, url: String) {
        val path = url.removePrefix("file://")
        if (path.startsWith("/")) {
            val mime = MediaRef.mimeOf(path)
            if (mime != null) { MediaViewerActivity.open(a, listOf(MediaRef(path, mime)), 0); return }
            Media.openFile(a, path); return
        }
        runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Snack.show(a, a.tr("אין אפליקציה שיכולה לפתוח את הקישור", "No app can open this link")) }
    }

    /** Makes links tappable without stealing ordinary taps and long-presses from the bubble. */
    fun linkTouch(tv: TextView) {
        tv.setOnTouchListener { v, e ->
            val t = v as TextView
            val text = t.text as? Spannable ?: return@setOnTouchListener false
            if (e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_DOWN) return@setOnTouchListener false
            val layout: Layout = t.layout ?: return@setOnTouchListener false
            val x = e.x.toInt() - t.totalPaddingLeft + t.scrollX
            val y = e.y.toInt() - t.totalPaddingTop + t.scrollY
            val line = layout.getLineForVertical(y)
            if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return@setOnTouchListener false
            val off = layout.getOffsetForHorizontal(line, x.toFloat())
            val spans = text.getSpans(off, off, ClickableSpan::class.java)
            if (spans.isEmpty()) return@setOnTouchListener false
            if (e.action == MotionEvent.ACTION_UP) spans[0].onClick(t)
            true
        }
    }

    /** Builds the views for an agent message. [highlight] marks search hits. */
    fun render(a: Activity, src: String, textColor: Int, highlight: String? = null, maxWidth: Int): LinearLayout {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        blocks(src).forEach { b ->
            when (b) {
                is Block.Text -> {
                    val tv = Ui.text(a, mark(spans(a, b.src), highlight), 16f, textColor).apply {
                        setLineSpacing(0f, 1.12f); this.maxWidth = maxWidth
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    }
                    linkTouch(tv)
                    box.addView(tv)
                }
                is Block.Code -> box.addView(codeBlock(a, b.lang, b.code, highlight, maxWidth), lp(MATCH, WRAP).apply { topMargin = a.dp(6); bottomMargin = a.dp(6) })
                is Block.Table -> box.addView(codeBlock(a, "", b.rows, highlight, maxWidth, header = false), lp(MATCH, WRAP).apply { topMargin = a.dp(6); bottomMargin = a.dp(6) })
            }
        }
        return box
    }

    fun codeBlock(a: Activity, lang: String, code: String, highlight: String?, maxWidth: Int, header: Boolean = true): View {
        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(T.codeBg, a.dpf(12f), T.divider, 1)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        if (header) {
            val bar = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(a.dp(12), a.dp(2), a.dp(2), 0)
            }
            bar.addView(Ui.text(a, lang.ifBlank { "code" }, 12.5f, T.muted), lp(0, WRAP, 1f))
            bar.addView(Ui.iconButton(a, R.drawable.ic_copy, a.tr("העתקת הקוד", "Copy code"), T.muted) { Ui.copy(a, code) }.apply {
                layoutParams = lp(a.dp(38), a.dp(38))
            })
            card.addView(bar)
        }
        val tv = TextView(a).apply {
            text = mark(code, highlight)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, T.fs(13.5f))
            setTextColor(T.text)
            setPadding(a.dp(12), if (header) 0 else a.dp(10), a.dp(12), a.dp(10))
            setTextIsSelectable(false)
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val hs = HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tv) }
        card.addView(hs)
        card.minimumWidth = minOf(maxWidth, a.dp(220))
        return card
    }

    fun mark(text: CharSequence, q: String?): CharSequence {
        if (q.isNullOrBlank()) return text
        val sb = if (text is SpannableStringBuilder) text else SpannableStringBuilder(text)
        val lower = sb.toString().lowercase(); val ql = q.lowercase()
        var i = lower.indexOf(ql)
        while (i >= 0) {
            sb.setSpan(BackgroundColorSpan(T.highlight), i, i + ql.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (T.dark) sb.setSpan(ForegroundColorSpan(Color.WHITE), i, i + ql.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i = lower.indexOf(ql, i + ql.length)
        }
        return sb
    }

    /** Plain text without Markdown symbols (previews, notifications, copy). */
    fun plain(src: String): String = src
        .replace(Regex("```[a-zA-Z0-9]*\\n?"), "")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1")
}
