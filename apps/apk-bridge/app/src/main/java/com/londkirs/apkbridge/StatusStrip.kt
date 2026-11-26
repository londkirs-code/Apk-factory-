package com.londkirs.apkbridge

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Status + progress strip for screens other than the main one (apps, backup): shows what is
 * running with a progress bar and a cancel button, then the final result until dismissed.
 */
class StatusStrip(private val activity: Activity) {
    private val d = activity.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val text = TextView(activity).apply {
        textSize = 14f
        setTextColor(0xFF30251D.toInt())
        maxLines = 6
    }
    private val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    private val cancel = Button(activity).apply {
        text = "בטל"
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(14), dp(6), dp(14), dp(6))
        setTextColor(0xFF75451F.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFFFFE7D1.toInt()) }
        setOnClickListener { Bridge.cancelCurrent() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }
    private val close = Button(activity).apply {
        text = "✕"
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(14), dp(6), dp(14), dp(6))
        setTextColor(0xFF75451F.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFFFFE7D1.toInt()) }
        setOnClickListener { finished = false; view.visibility = View.GONE }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    val view: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setBackgroundColor(0xFFFFF1E3.toInt())
        visibility = View.GONE
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(cancel)
        row.addView(close)
        addView(row)
        addView(bar)
    }

    private var sawBusy = false
    private var finished = false
    private val listener: (BridgeState) -> Unit = { render(it) }

    fun attach() = Bridge.addStateListener(listener)
    fun detach() = Bridge.removeStateListener(listener)

    private fun render(s: BridgeState) {
        if (s.busy) { sawBusy = true; finished = false }
        else if (sawBusy) { sawBusy = false; finished = true }   // keep the result visible until closed
        val show = s.busy || finished
        view.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        text.text = s.status
        bar.visibility = if (s.busy) View.VISIBLE else View.GONE
        bar.isIndeterminate = s.progress < 0
        if (s.progress >= 0) bar.progress = s.progress
        cancel.visibility = if (s.busy) View.VISIBLE else View.GONE
        close.visibility = if (s.busy) View.GONE else View.VISIBLE
    }
}
