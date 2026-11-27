package com.londkirs.antigravityremote

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
fun flp(w: Int, h: Int, gravity: Int = Gravity.NO_GRAVITY) = FrameLayout.LayoutParams(w, h, gravity)

/** Shared building blocks: every surface, button and popup in the app is drawn by us. */
object Ui {

    fun round(color: Int, radius: Float, strokeColor: Int = 0, strokePx: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

    fun roundCorners(color: Int, tl: Float, tr: Float, br: Float, bl: Float) = GradientDrawable().apply {
        setColor(color); cornerRadii = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
    }

    fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    /** Background with a ripple clipped to the same rounded shape. */
    fun ripple(content: Drawable?, radius: Float = 0f): Drawable =
        RippleDrawable(ColorStateList.valueOf(T.ripple), content, round(Color.WHITE, radius))

    fun borderless(): Drawable = RippleDrawable(ColorStateList.valueOf(T.ripple), null, null)

    fun text(ctx: Context, s: CharSequence?, size: Float = 15f, color: Int = T.text, bold: Boolean = false, lines: Int = 0) =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, T.fs(size))
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END; if (lines == 1) isSingleLine = true }
            includeFontPadding = true
        }

    fun icon(ctx: Context, res: Int, tint: Int = T.muted, sizeDp: Int = 24) = ImageView(ctx).apply {
        setImageResource(res); setColorFilter(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    }

    /** Round touch target with an icon (48dp). */
    fun iconButton(ctx: Context, res: Int, desc: String, tint: Int = T.text, onClick: (View) -> Unit) = ImageView(ctx).apply {
        setImageResource(res); setColorFilter(tint)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = desc
        background = borderless()
        isClickable = true; isFocusable = true
        setOnClickListener { onClick(it) }
        setOnLongClickListener { Snack.show(ctx as Activity, desc); true }
        layoutParams = LinearLayout.LayoutParams(ctx.dp(48), ctx.dp(48))
    }

    enum class Style { PRIMARY, SECONDARY, TEXT, DANGER, TONAL }

    fun button(ctx: Context, label: String, style: Style = Style.PRIMARY, iconRes: Int = 0, onClick: () -> Unit): TextView {
        val (bg, fg) = when (style) {
            Style.PRIMARY -> T.primary to T.onPrimary
            Style.DANGER -> T.danger to Color.WHITE
            Style.TONAL -> T.primarySoft to T.primary
            Style.SECONDARY -> T.surface2 to T.text
            Style.TEXT -> Color.TRANSPARENT to T.primary
        }
        return TextView(ctx).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, T.fs(15f))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(fg)
            gravity = Gravity.CENTER
            minHeight = ctx.dp(46); minimumHeight = ctx.dp(46)
            setPadding(ctx.dp(20), ctx.dp(10), ctx.dp(20), ctx.dp(10))
            background = ripple(round(bg, ctx.dpf(23f)), ctx.dpf(23f))
            isClickable = true; isFocusable = true
            if (iconRes != 0) {
                val d = ctx.getDrawable(iconRes)!!.mutate()
                d.setTint(fg)
                d.setBounds(0, 0, ctx.dp(20), ctx.dp(20))
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = ctx.dp(8)
            }
            setOnClickListener { onClick() }
        }
    }

    fun chip(ctx: Context, label: String, selected: Boolean, iconRes: Int = 0, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, T.fs(14f))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (selected) T.primary else T.muted)
        gravity = Gravity.CENTER
        setPadding(ctx.dp(14), ctx.dp(7), ctx.dp(14), ctx.dp(7))
        background = ripple(round(if (selected) T.primarySoft else T.surface2, ctx.dpf(18f)), ctx.dpf(18f))
        if (iconRes != 0) {
            val d = ctx.getDrawable(iconRes)!!.mutate(); d.setTint(if (selected) T.primary else T.muted)
            d.setBounds(0, 0, ctx.dp(16), ctx.dp(16)); setCompoundDrawablesRelative(d, null, null, null)
            compoundDrawablePadding = ctx.dp(6)
        }
        setOnClickListener { onClick() }
    }

    fun badge(ctx: Context, count: Int, color: Int = T.primary) = TextView(ctx).apply {
        text = if (count > 99) "99+" else count.toString()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        minWidth = ctx.dp(22); minimumWidth = ctx.dp(22)
        setPadding(ctx.dp(6), ctx.dp(1), ctx.dp(6), ctx.dp(1))
        background = round(color, ctx.dpf(11f))
    }

    fun divider(ctx: Context, insetStartDp: Int = 0) = View(ctx).apply {
        setBackgroundColor(T.divider)
        layoutParams = LinearLayout.LayoutParams(MATCH, 1).apply { marginStart = ctx.dp(insetStartDp) }
    }

    fun field(ctx: Context, hint: String, value: String = "", multiLine: Boolean = false, ltr: Boolean = false) = EditText(ctx).apply {
        setHint(hint); setText(value)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, T.fs(16f))
        setTextColor(T.text); setHintTextColor(T.faint)
        background = round(T.surface2, ctx.dpf(14f))
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
        if (multiLine) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; maxLines = 8; minLines = 3; gravity = Gravity.TOP or Gravity.START }
        else isSingleLine = true
        if (ltr) textDirection = View.TEXT_DIRECTION_LTR
        setSelection(text.length)
    }

    /** Round avatar: emoji or the first letter, on a colour picked from the icon palette. */
    fun avatar(ctx: Context, label: String, key: String, sizeDp: Int, emoji: String = "") = TextView(ctx).apply {
        val e = emoji.isNotBlank()
        text = if (e) emoji else label.trim().firstOrNull()?.uppercase() ?: "A"
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.dp(sizeDp) * (if (e) 0.5f else 0.42f))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = if (e) circle(T.surface3) else GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(T.accentFor(key), shade(T.accentFor(key)))).apply { shape = GradientDrawable.OVAL }
        layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    }

    /** The Antigravity icon as a round avatar. */
    fun agentAvatar(ctx: Context, sizeDp: Int) = ImageView(ctx).apply {
        setImageResource(R.drawable.agy_avatar)
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setOval(0, 0, view.width, view.height) }
        }
        layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    }

    fun shade(color: Int): Int {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv); hsv[2] *= 0.78f
        return Color.HSVToColor(hsv)
    }

    /** Settings-style row: icon, title, optional subtitle, optional trailing view. */
    fun row(ctx: Context, iconRes: Int, title: String, subtitle: String? = null, trailing: View? = null,
            iconTint: Int = T.muted, titleColor: Int = T.text, onClick: (() -> Unit)? = null): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(56)
            setPadding(ctx.dp(20), ctx.dp(10), ctx.dp(16), ctx.dp(10))
            if (iconRes != 0) addView(icon(ctx, iconRes, iconTint), lp(ctx.dp(24), ctx.dp(24)).apply { marginEnd = ctx.dp(20) })
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(text(ctx, title, 16f, titleColor))
            if (!subtitle.isNullOrBlank()) texts.addView(text(ctx, subtitle, 13.5f, T.muted).apply { setPadding(0, ctx.dp(2), 0, 0) })
            addView(texts, lp(0, WRAP, 1f))
            if (trailing != null) addView(trailing)
            if (onClick != null) {
                background = ripple(null); isClickable = true
                setOnClickListener { onClick() }
            }
        }

    fun sectionTitle(ctx: Context, title: String) = text(ctx, title, 13.5f, T.primary, bold = true).apply {
        setPadding(ctx.dp(20), ctx.dp(20), ctx.dp(20), ctx.dp(6))
    }

    /** A screen's top bar: optional back arrow, title + subtitle, and action icons. */
    class TopBar(val activity: Activity, title: String, back: Boolean = true) : LinearLayout(activity) {
        val titleView: TextView
        val subtitleView: TextView
        val actions: LinearLayout
        val lead: LinearLayout
        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(T.bar)
            elevation = activity.dpf(1f)
            setPaddingRelative(activity.dp(if (back) 4 else 20), 0, activity.dp(4), 0)
            minimumHeight = activity.dp(60)
            lead = LinearLayout(activity).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            if (back) lead.addView(iconButton(activity, R.drawable.ic_back, activity.tr("חזרה", "Back")) { activity.onBackPressed() })
            addView(lead)
            val texts = LinearLayout(activity).apply { orientation = VERTICAL; setPaddingRelative(activity.dp(if (back) 4 else 0), 0, 0, 0) }
            titleView = text(activity, title, if (back) 19f else 24f, if (back) T.text else T.primary, bold = true, lines = 1)
            subtitleView = text(activity, "", 13f, T.muted, lines = 1).apply { visibility = GONE }
            texts.addView(titleView); texts.addView(subtitleView)
            addView(texts, lp(0, WRAP, 1f))
            actions = LinearLayout(activity).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            addView(actions)
        }
        fun subtitle(s: CharSequence?) { subtitleView.text = s; subtitleView.visibility = if (s.isNullOrEmpty()) GONE else VISIBLE }
        fun action(res: Int, desc: String, onClick: (View) -> Unit): View =
            iconButton(activity, res, desc, T.text, onClick).also { actions.addView(it) }
    }

    fun copy(activity: Activity, text: String, label: String? = null) {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
        Snack.show(activity, label ?: activity.tr("הועתק", "Copied"))
    }

    fun clipboard(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.takeIf { it.isNotEmpty() }
    }

    fun haptic(v: View) { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

    // ---- Dialog shortcuts ---------------------------------------------------------------------

    fun confirm(a: Activity, message: String, ok: String, danger: Boolean = false, title: String? = null, onOk: () -> Unit) {
        Dlg(a).title(title).message(message).negative(a.tr("ביטול", "Cancel")).positive(ok, danger) { onOk() }.show()
    }

    fun prompt(a: Activity, title: String, value: String, hint: String, ok: String = a.tr("שמירה", "Save"),
               multiLine: Boolean = false, onOk: (String) -> Unit) {
        val f = field(a, hint, value, multiLine)
        Dlg(a).title(title).view(f).negative(a.tr("ביטול", "Cancel")).positive(ok) { onOk(f.text.toString().trim()) }.show()
        f.requestFocus()
        f.postDelayed({ (a.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(f, 0) }, 200)
    }

    /** Long text (errors, command output) with copy. */
    fun info(a: Activity, title: String?, body: String, mono: Boolean = false) {
        val tv = text(a, body, if (mono) 13f else 15f, T.text).apply {
            setTextIsSelectable(true)
            if (mono) { typeface = Typeface.MONOSPACE; textDirection = View.TEXT_DIRECTION_LTR }
            setLineSpacing(0f, 1.15f)
        }
        val scroll = ScrollView(a).apply { addView(tv) }
        Dlg(a).title(title).view(scroll, maxHeightDp = 420)
            .neutral(a.tr("העתקה", "Copy")) { copy(a, body) }
            .positive(a.tr("סגירה", "Close")) {}
            .show()
    }
}

/** Our own dialog: rounded card, title, text or custom content, and up to three buttons. */
class Dlg(private val a: Activity) {
    private var title: String? = null
    private var message: CharSequence? = null
    private var content: View? = null
    private var maxH = 0
    private var cancelable = true
    private data class B(val label: String, val style: Ui.Style, val action: () -> Boolean)
    private var pos: B? = null; private var neg: B? = null; private var neu: B? = null
    private var onDismiss: (() -> Unit)? = null

    fun title(t: String?) = apply { title = t }
    fun message(m: CharSequence?) = apply { message = m }
    fun view(v: View, maxHeightDp: Int = 0) = apply { content = v; maxH = maxHeightDp }
    fun cancelable(c: Boolean) = apply { cancelable = c }
    fun positive(label: String, danger: Boolean = false, action: () -> Unit) = apply { pos = B(label, if (danger) Ui.Style.DANGER else Ui.Style.PRIMARY) { action(); true } }
    /** Positive button that keeps the dialog open when [action] returns false (validation). */
    fun positiveCheck(label: String, action: () -> Boolean) = apply { pos = B(label, Ui.Style.PRIMARY, action) }
    fun negative(label: String, action: () -> Unit = {}) = apply { neg = B(label, Ui.Style.TEXT) { action(); true } }
    fun neutral(label: String, action: () -> Unit) = apply { neu = B(label, Ui.Style.TEXT) { action(); true } }
    fun onDismiss(f: () -> Unit) = apply { onDismiss = f }

    fun show(): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(T.surface, a.dpf(26f))
            setPadding(a.dp(24), a.dp(22), a.dp(24), a.dp(14))
            elevation = a.dpf(8f)
        }
        title?.let { card.addView(Ui.text(a, it, 20f, T.text, bold = true).apply { setPadding(0, 0, 0, a.dp(10)) }) }
        message?.let {
            card.addView(Ui.text(a, it, 15.5f, T.muted).apply { setLineSpacing(0f, 1.2f); setPadding(0, 0, 0, a.dp(8)) })
        }
        content?.let { v ->
            (v.parent as? ViewGroup)?.removeView(v)
            val wrap = if (maxH > 0) object : FrameLayout(a) {
                override fun onMeasure(w: Int, h: Int) {
                    super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(a.dp(maxH), View.MeasureSpec.AT_MOST))
                }
            }.apply { addView(v) } else v
            card.addView(wrap, lp(MATCH, WRAP).apply { bottomMargin = a.dp(6) })
        }
        val buttons = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun add(b: B?) {
            b ?: return
            buttons.addView(Ui.button(a, b.label, b.style) { if (b.action()) d.dismiss() }.apply {
                setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(8)); minHeight = a.dp(42)
            }, lp(WRAP, WRAP).apply { marginStart = a.dp(6) })
        }
        add(neu)
        buttons.addView(View(a), lp(0, 1, 1f))
        add(neg); add(pos)
        if (buttons.childCount > 1) card.addView(buttons, lp(MATCH, WRAP).apply { topMargin = a.dp(8) })
        d.setContentView(card)
        d.setCancelable(cancelable)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val w = minOf(a.resources.displayMetrics.widthPixels - a.dp(40), a.dp(460))
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
            setWindowAnimations(0)
            setDimAmount(0.45f)
        }
        d.setOnDismissListener { onDismiss?.invoke() }
        card.alpha = 0f; card.scaleX = 0.92f; card.scaleY = 0.92f
        d.show()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).setInterpolator(DecelerateInterpolator()).start()
        return d
    }
}

/** Bottom sheet with a drag handle: actions, choices or custom content. */
class Sheet(private val a: Activity) {
    data class Item(val icon: Int, val label: String, val subtitle: String? = null, val checked: Boolean? = null,
                    val tint: Int = 0, val enabled: Boolean = true, val action: () -> Unit)

    private var title: String? = null
    private val items = mutableListOf<Item>()
    private var content: View? = null
    private var grid: List<Item>? = null
    var dialog: Dialog? = null; private set

    fun title(t: String?) = apply { title = t }
    fun item(icon: Int, label: String, subtitle: String? = null, checked: Boolean? = null, tint: Int = 0, enabled: Boolean = true, action: () -> Unit) =
        apply { items += Item(icon, label, subtitle, checked, tint, enabled, action) }
    fun grid(list: List<Item>) = apply { grid = list }
    fun view(v: View) = apply { content = v }

    fun dismiss() { dialog?.dismiss() }

    fun show(): Sheet {
        val d = Dialog(a)
        dialog = d
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val r = a.dpf(26f)
        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.roundCorners(T.surface, r, r, 0f, 0f)
            setPadding(0, a.dp(10), 0, a.dp(16))
        }
        val handle = FrameLayout(a)
        handle.addView(View(a).apply { background = Ui.round(T.surface3, a.dpf(3f)) }, flp(a.dp(40), a.dp(5), Gravity.CENTER))
        card.addView(handle, lp(MATCH, a.dp(20)))
        title?.let { card.addView(Ui.text(a, it, 18f, T.text, bold = true).apply { setPadding(a.dp(24), a.dp(4), a.dp(24), a.dp(10)) }) }
        val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        grid?.let { list ->
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(a.dp(12), a.dp(6), a.dp(12), a.dp(6)) }
            list.forEach { it2 ->
                val cell = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, a.dp(8), 0, a.dp(8))
                    background = Ui.ripple(null, a.dpf(16f)); isClickable = true
                    setOnClickListener { d.dismiss(); it2.action() }
                }
                val color = if (it2.tint != 0) it2.tint else T.primary
                val circle = FrameLayout(a).apply { background = Ui.circle(color) }
                circle.addView(Ui.icon(a, it2.icon, Color.WHITE, 26), flp(a.dp(26), a.dp(26), Gravity.CENTER))
                cell.addView(circle, lp(a.dp(56), a.dp(56)))
                cell.addView(Ui.text(a, it2.label, 13.5f, T.text).apply { gravity = Gravity.CENTER; setPadding(0, a.dp(6), 0, 0) })
                row.addView(cell, lp(0, WRAP, 1f))
            }
            body.addView(row)
        }
        items.forEach { it2 ->
            val trailing = when (it2.checked) {
                true -> Ui.icon(a, R.drawable.ic_check_circle, T.primary)
                false -> Ui.icon(a, R.drawable.ic_circle, T.faint)
                null -> null
            }
            val row = Ui.row(a, it2.icon, it2.label, it2.subtitle, trailing,
                iconTint = if (it2.tint != 0) it2.tint else T.muted,
                titleColor = if (it2.tint == T.danger) T.danger else T.text) { d.dismiss(); it2.action() }
            if (!it2.enabled) { row.alpha = 0.4f; row.isEnabled = false; row.setOnClickListener(null) }
            body.addView(row)
        }
        content?.let { (it.parent as? ViewGroup)?.removeView(it); body.addView(it) }
        val maxH = (a.resources.displayMetrics.heightPixels * 0.82).toInt()
        val scroll = object : ScrollView(a) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }.apply { addView(body); isVerticalScrollBarEnabled = false }
        card.addView(scroll)
        // Drag the handle down to close.
        var downY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> { card.translationY = maxOf(0f, e.rawY - downY); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (card.translationY > a.dp(90)) d.dismiss() else card.animate().translationY(0f).setDuration(150).start(); true
                }
                else -> false
            }
        }
        d.setContentView(card)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(0)
            setDimAmount(0.4f)
        }
        card.translationY = a.dp(400).toFloat()
        d.show()
        card.animate().translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator(1.6f)).start()
        return this
    }
}

/** Floating menu anchored to a view, drawn as one of our cards. */
class Menu(private val a: Activity) {
    private data class Item(val icon: Int, val label: String, val enabled: Boolean, val danger: Boolean, val action: () -> Unit)
    private val items = mutableListOf<Item>()

    fun item(icon: Int, label: String, enabled: Boolean = true, danger: Boolean = false, action: () -> Unit) =
        apply { items += Item(icon, label, enabled, danger, action) }

    fun show(anchor: View) {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(T.surface, a.dpf(16f))
            elevation = a.dpf(10f)
            setPadding(0, a.dp(6), 0, a.dp(6))
        }
        val pw = PopupWindow(box, WRAP, WRAP, true)
        items.forEach { it2 ->
            val color = if (it2.danger) T.danger else T.text
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = a.dp(48); minimumWidth = a.dp(220)
                setPadding(a.dp(16), 0, a.dp(24), 0)
                background = Ui.ripple(null)
                if (it2.icon != 0) addView(Ui.icon(a, it2.icon, if (it2.danger) T.danger else T.muted, 22), lp(a.dp(22), a.dp(22)).apply { marginEnd = a.dp(16) })
                addView(Ui.text(a, it2.label, 15.5f, color))
                isEnabled = it2.enabled; alpha = if (it2.enabled) 1f else 0.4f
                setOnClickListener { pw.dismiss(); it2.action() }
            }
            box.addView(row, lp(MATCH, WRAP))
        }
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.isOutsideTouchable = true
        pw.elevation = a.dpf(10f)
        pw.animationStyle = 0
        box.alpha = 0f; box.scaleX = 0.9f; box.scaleY = 0.9f
        box.pivotY = 0f
        box.pivotX = if (anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL) 0f else a.dp(220).toFloat()
        pw.showAsDropDown(anchor, 0, -anchor.height + a.dp(8), Gravity.END)
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
    }
}

/** Bottom message with an optional action (our replacement for Toast). */
object Snack {
    fun show(a: Activity, text: String, action: String? = null, long: Boolean = false, onAction: (() -> Unit)? = null) {
        val root = a.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        root.findViewWithTag<View>("snack")?.let { root.removeView(it) }
        val bar = LinearLayout(a).apply {
            tag = "snack"
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = Ui.round(if (T.dark) 0xFFE6EDF3.toInt() else 0xFF1E2633.toInt(), a.dpf(14f))
            elevation = a.dpf(8f)
            setPadding(a.dp(18), a.dp(6), a.dp(8), a.dp(6))
            minimumHeight = a.dp(50)
        }
        bar.addView(Ui.text(a, text, 14.5f, if (T.dark) 0xFF0E1116.toInt() else Color.WHITE, lines = 3), lp(0, WRAP, 1f))
        if (action != null) bar.addView(Ui.text(a, action, 14.5f, if (T.dark) T.primary else 0xFF8AB8FF.toInt(), bold = true).apply {
            setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10))
            background = Ui.ripple(null, a.dpf(10f))
            setOnClickListener { onAction?.invoke(); dismiss(root, bar) }
        })
        root.addView(bar, flp(MATCH, WRAP, Gravity.BOTTOM).apply { setMargins(a.dp(12), 0, a.dp(12), a.dp(16)) })
        bar.translationY = a.dp(80).toFloat(); bar.alpha = 0f
        bar.animate().translationY(0f).alpha(1f).setDuration(180).start()
        bar.postDelayed({ dismiss(root, bar) }, if (long || action != null) 4500L else 2400L)
    }

    private fun dismiss(root: ViewGroup, bar: View) {
        if (bar.parent == null) return
        bar.animate().alpha(0f).translationY(bar.height / 2f).setDuration(150).withEndAction { root.removeView(bar) }.start()
    }
}

/** On/off switch drawn by us. */
class Toggle(ctx: Context, checked: Boolean, private val onChange: (Boolean) -> Unit) : View(ctx) {
    var checked = checked; private set
    private var pos = if (checked) 1f else 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    init {
        layoutParams = LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(32))
        isClickable = true
        setOnClickListener { set(!this.checked, true) }
    }
    fun set(v: Boolean, notify: Boolean) {
        if (v == checked) return
        checked = v
        ValueAnimator.ofFloat(pos, if (v) 1f else 0f).apply {
            duration = 160; addUpdateListener { pos = it.animatedValue as Float; invalidate() }; start()
        }
        Ui.haptic(this)
        if (notify) onChange(v)
    }
    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val w = width.toFloat()
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        rect.set(0f, h * 0.12f, w, h * 0.88f)
        paint.color = blend(T.surface3, T.primary, pos)
        c.drawRoundRect(rect, h, h, paint)
        val r = h * 0.3f
        val p = if (rtl) 1f - pos else pos
        val cx = h * 0.5f + (w - h) * p
        paint.color = if (pos > 0.5f) T.onPrimary else Color.WHITE
        c.drawCircle(cx, h / 2, r + context.dpf(1f) * pos, paint)
    }
    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) * (1 - t) + (b shr s and 0xFF) * t).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
}

/** Determinate or indeterminate progress line. */
class ProgressLine(ctx: Context) : View(ctx) {
    var fraction = -1f; set(v) { field = v; invalidate() }
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; if (fraction < 0) invalidate() }
    }
    init { layoutParams = LinearLayout.LayoutParams(MATCH, ctx.dp(6)) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val w = width.toFloat()
        paint.color = T.surface3; c.drawRoundRect(0f, 0f, w, h, h, h, paint)
        paint.color = T.primary
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        if (fraction >= 0) {
            val fw = w * fraction.coerceIn(0f, 1f)
            if (rtl) c.drawRoundRect(w - fw, 0f, w, h, h, h, paint) else c.drawRoundRect(0f, 0f, fw, h, h, h, paint)
        } else {
            val seg = w * 0.3f
            var x = -seg + (w + seg) * phase
            if (rtl) x = w - x - seg
            c.drawRoundRect(maxOf(0f, x), 0f, minOf(w, x + seg), h, h, h, paint)
        }
    }
}

/** Small spinning arc. */
class Spinner(ctx: Context, sizeDp: Int = 22, private val color: Int = T.primary) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = ctx.dpf(2.6f) }
    private var angle = 0f
    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }
    init { layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp)) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        paint.color = color
        val s = paint.strokeWidth
        c.drawArc(s, s, width - s, height - s, angle, 270f, false, paint)
    }
}

/** Three bouncing dots ("typing…"). */
class TypingDots(ctx: Context, private val color: Int = T.muted) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    init { layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(20)) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        paint.color = color
        val r = context.dpf(3.5f)
        for (i in 0 until 3) {
            val p = ((t - i * 0.18f) + 1f) % 1f
            val lift = if (p < 0.5f) Math.sin(p * Math.PI * 2).toFloat().coerceAtLeast(0f) else 0f
            paint.alpha = (150 + 105 * lift).toInt()
            c.drawCircle(width / 2f + (i - 1) * r * 3.4f, height / 2f - lift * context.dpf(4f), r, paint)
        }
    }
}

/** One-time tip bubble pointing at a view. */
object CoachMark {
    fun show(a: Activity, anchor: View, key: String, text: String, delayMs: Long = 600) {
        if (Settings.tipSeen(a, key)) return
        anchor.postDelayed({
            if (a.isFinishing || !anchor.isShown || Settings.tipSeen(a, key)) return@postDelayed
            val root = a.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return@postDelayed
            if (root.findViewWithTag<View>("coach") != null) return@postDelayed
            Settings.setTipSeen(a, key)
            val layer = FrameLayout(a).apply { tag = "coach"; setBackgroundColor(0x33000000); isClickable = true }
            val bubble = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.round(T.primary, a.dpf(18f))
                elevation = a.dpf(10f)
                setPadding(a.dp(18), a.dp(14), a.dp(18), a.dp(8))
            }
            bubble.addView(Ui.text(a, text, 15f, T.onPrimary).apply { setLineSpacing(0f, 1.15f) })
            bubble.addView(Ui.text(a, a.tr("הבנתי", "Got it"), 14.5f, T.onPrimary, bold = true).apply {
                setPadding(a.dp(4), a.dp(10), a.dp(4), a.dp(6)); gravity = Gravity.END
            }, lp(MATCH, WRAP))
            val loc = IntArray(2); anchor.getLocationInWindow(loc)
            val rloc = IntArray(2); root.getLocationInWindow(rloc)
            val ax = loc[0] - rloc[0]; val ay = loc[1] - rloc[1]
            val below = ay + anchor.height / 2 < root.height / 2
            val w = minOf(root.width - a.dp(32), a.dp(320))
            val params = flp(w, WRAP)
            params.leftMargin = (ax + anchor.width / 2 - w / 2).coerceIn(a.dp(16), root.width - w - a.dp(16))
            layer.addView(bubble, params)
            root.addView(layer, flp(MATCH, MATCH))
            bubble.post {
                bubble.y = if (below) (ay + anchor.height + a.dp(8)).toFloat() else (ay - bubble.height - a.dp(8)).toFloat()
                bubble.alpha = 0f; bubble.scaleX = 0.8f; bubble.scaleY = 0.8f
                bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator()).setDuration(260).start()
            }
            layer.setOnClickListener { layer.animate().alpha(0f).setDuration(150).withEndAction { root.removeView(layer) }.start() }
        }, delayMs)
    }
}

/** Horizontal strip of chips. */
fun chipStrip(ctx: Context): Pair<HorizontalScrollView, LinearLayout> {
    val inner = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(14), ctx.dp(6), ctx.dp(14), ctx.dp(8)) }
    val sv = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(inner) }
    return sv to inner
}
