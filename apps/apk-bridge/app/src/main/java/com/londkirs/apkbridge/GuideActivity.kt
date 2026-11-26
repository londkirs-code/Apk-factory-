package com.londkirs.apkbridge

import android.Manifest
import android.app.Activity
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Step-by-step connection guide for people with no technical background: one step per screen,
 * big text, an action button where it helps, and an automatic ✔ when the app can see the step is done.
 */
class GuideActivity : Activity() {
    private enum class Part(val label: String) { WIRELESS(tr("📱 בלי מחשב", "📱 Without a computer")), COMPUTER(tr("💻 עם מחשב", "💻 With a computer")), FAQ(tr("❓ שאלות ותקלות", "❓ Questions & problems")) }

    private class Step(
        val title: String,
        val body: String,
        val tip: String? = null,
        val warn: String? = null,
        val otherPhones: String? = null,
        val actions: List<Pair<String, () -> Unit>> = emptyList(),
        val copies: List<Pair<String, String>> = emptyList(),
        val check: (() -> Boolean)? = null,
        val okText: String = tr("זוהה! אפשר להמשיך לשלב הבא.", "Detected! You can go on to the next step."),
    )

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var progressText: TextView
    private lateinit var dots: LinearLayout
    private lateinit var partBar: LinearLayout
    private lateinit var navBar: LinearLayout
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var allButton: Button
    private var part = Part.WIRELESS
    private var index = 0
    private var showAll = false
    private val checkViews = mutableListOf<Pair<TextView, Step>>()
    private lateinit var checkBar: TextView
    private var pulse: android.animation.AnimatorSet? = null
    private var nextHighlighted = false
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            refreshChecks()
            handler.postDelayed(this, 1500)
        }
    }

    private val brand = Build.MANUFACTURER.lowercase()
    private val isSamsung = brand.contains("samsung")
    private val isXiaomi = brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        part = savedInstanceState?.getString("part")?.let { runCatching { Part.valueOf(it) }.getOrNull() }
            ?: if (Compat.hasWirelessDebugging) Part.WIRELESS else Part.COMPUTER
        index = savedInstanceState?.getInt("index") ?: 0
        showAll = savedInstanceState?.getBoolean("all") ?: false
        buildUi()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("part", part.name)
        outState.putInt("index", index)
        outState.putBoolean("all", showAll)
    }

    override fun onResume() {
        super.onResume()
        Celebration.onResume(this)
        handler.post(poll)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Celebration.onFocus(this, hasFocus)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        Celebration.onPause(this)
        super.onPause()
    }

    // region layout

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.direction
            setBackgroundColor(BACKGROUND)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(12), dp(6))
        }
        header.addView(TextView(this).apply {
            text = "→"
            textSize = 22f
            setTextColor(INK)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            isClickable = true
            contentDescription = tr("חזרה", "Back")
            setOnClickListener { finish() }
            Compat.focusRing(this, ACCENT, 14f)
        })
        header.addView(TextView(this).apply {
            text = tr("📖 מדריך התחברות", "📖 Connection guide")
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        allButton = smallButton(tr("הצג הכול ברצף", "Show all in a row")) { showAll = !showAll; render() }
        header.addView(allButton)
        root.addView(header)

        root.addView(TextView(this).apply {
            text = tr("בטלפון שלך: אנדרואיד ${Build.VERSION.RELEASE}", "Your phone: Android ${Build.VERSION.RELEASE}") +
                (if (Compat.hasWirelessDebugging) tr(" · מתאים לך: בלי מחשב", " · Best for you: without a computer") else tr(" · מתאים לך: עם מחשב", " · Best for you: with a computer"))
            textSize = 13.5f
            setTextColor(MUTED)
            setPadding(dp(18), 0, dp(18), dp(6))
        })

        partBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(2), dp(12), dp(8))
        }
        root.addView(partBar)

        val progress = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), dp(6))
        }
        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        progress.addView(dots, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        progressText = TextView(this).apply { textSize = 13f; setTextColor(MUTED) }
        progress.addView(progressText)
        root.addView(progress)

        // Pinned at the top (not at the bottom of a long step): has the app already seen this step done?
        checkBar = TextView(this).apply {
            textSize = 15.5f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            visibility = View.GONE
        }
        root.addView(checkBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(14); marginEnd = dp(14); bottomMargin = dp(6)
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(16))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = dp(6).toFloat()
        }
        // Right-to-left: "previous" sits on the right, "next" on the left.
        prevButton = bigButton(tr("→ הקודם", "← Previous"), primary = false) { go(-1) }
        nextButton = bigButton(tr("הבא ←", "Next →"), primary = true) { go(+1) }
        navBar.addView(prevButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
        navBar.addView(nextButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(navBar)

        Compat.applySystemBarInsets(this, root, includeIme = false)
        setContentView(root)
    }

    private fun render() {
        // Part selector
        partBar.removeAllViews()
        for (p in Part.values()) {
            val selected = p == part
            partBar.addView(TextView(this).apply {
                text = p.label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (selected) 0xFFFFFFFF.toInt() else INK)
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(dp(8), dp(9), dp(8), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(if (selected) ACCENT else 0xFFFFFFFF.toInt())
                    setStroke(dp(1), if (selected) ACCENT else 0xFFE8D6C4.toInt())
                }
                isClickable = true
                setOnClickListener { part = p; index = 0; render() }
                Compat.focusRing(this, INK, 18f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        }

        content.removeAllViews()
        checkViews.clear()
        val steps = stepsFor(part)
        val wizard = part != Part.FAQ && !showAll
        allButton.visibility = if (part == Part.FAQ) View.GONE else View.VISIBLE
        allButton.text = if (showAll) tr("שלב אחר שלב", "Step by step") else tr("הצג הכול ברצף", "Show all in a row")

        when {
            part == Part.FAQ -> { faq(); progressText.text = ""; dots.removeAllViews() }
            showAll -> {
                steps.forEachIndexed { i, s -> stepCard(i + 1, s, inlineCheck = true) }
                progressText.text = tr("${steps.size} שלבים", "${steps.size} steps")
                dots.removeAllViews()
            }
            else -> {
                index = index.coerceIn(0, steps.lastIndex)
                stepCard(index + 1, steps[index], inlineCheck = false)
                progressText.text = tr("שלב ${index + 1} מתוך ${steps.size}", "Step ${index + 1} of ${steps.size}")
                dots.removeAllViews()
                for (i in steps.indices) {
                    dots.addView(View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (i <= index) ACCENT else 0xFFE8D6C4.toInt())
                        }
                    }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(5) })
                }
            }
        }
        navBar.visibility = if (wizard) View.VISIBLE else View.GONE
        prevButton.isEnabled = index > 0
        prevButton.alpha = if (index > 0) 1f else 0.4f
        nextButton.text = if (wizard && index == steps.lastIndex) tr("סיום ✓", "Finish ✓") else tr("הבא ←", "Next →")
        refreshChecks()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun go(delta: Int) {
        val steps = stepsFor(part)
        if (delta > 0 && index == steps.lastIndex) {
            // "Finish": back to wherever the user opened the guide from.
            Toast.makeText(this, tr("🎉 הכול מוכן!", "🎉 All set!"), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        index = (index + delta).coerceIn(0, steps.lastIndex)
        render()
    }

    private fun stepCard(number: Int, step: Step, inlineCheck: Boolean) {
        val card = card()
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = number.toString()
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) }
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        titleRow.addView(TextView(this).apply {
            text = step.title
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(titleRow, wrap(12))
        card.addView(bodyText(step.body), wrap(8))

        step.otherPhones?.let { other ->
            val otherText = bodyText(other).apply { visibility = View.GONE }
            card.addView(linkText(tr("📱 יש לי טלפון אחר ▾", "📱 I have a different phone ▾")) {
                otherText.visibility = if (otherText.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }, wrap(4))
            card.addView(otherText, wrap(8))
        }
        step.tip?.let { card.addView(box("💡 $it", 0xFFFFF6D6.toInt(), 0xFFF2D58A.toInt()), wrap(8)) }
        step.warn?.let { card.addView(box("⚠️ $it", 0xFFFFE9DC.toInt(), 0xFFF5B08A.toInt()), wrap(8)) }
        for ((label, command) in step.copies) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = command
                textSize = 15f
                typeface = Typeface.MONOSPACE
                textDirection = View.TEXT_DIRECTION_LTR
                setTextColor(INK)
                setTextIsSelectable(true)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFFF4EEE8.toInt()) }
                contentDescription = label
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
            row.addView(smallButton(tr("העתק", "Copy")) { copy(command) })
            row.addView(smallButton(tr("שתף", "Share")) { share(command) })
            card.addView(row, wrap(8))
        }
        for ((label, action) in step.actions) {
            card.addView(bigButton(label, primary = false) { action() }, wrap(8))
        }
        // In "show all" mode each card shows its own status; step by step it is the pinned bar at the top.
        if (step.check != null && inlineCheck) {
            val status = TextView(this).apply { textSize = 15f; setPadding(dp(4), dp(4), dp(4), 0) }
            card.addView(status, wrap(0))
            checkViews += status to step
        }
    }

    private fun isDone(step: Step) = runCatching { step.check?.invoke() == true }.getOrDefault(false)

    private fun refreshChecks() {
        for ((view, step) in checkViews) {
            val ok = isDone(step)
            view.text = if (ok) "✔ ${step.okText}" else tr("○ עדיין לא זוהה — אפשר לבצע ולחזור לכאן.", "○ Not detected yet — you can do it and come back here.")
            view.setTextColor(if (ok) 0xFF1E8E3E.toInt() else MUTED)
            view.typeface = if (ok) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        // Step-by-step: pinned bar + "next" button that lights up and pulses once the step is done.
        val wizard = part != Part.FAQ && !showAll
        val step = if (wizard) stepsFor(part).getOrNull(index) else null
        if (step?.check == null) {
            checkBar.visibility = View.GONE
            highlightNext(false)
            return
        }
        val ok = isDone(step)
        checkBar.visibility = View.VISIBLE
        checkBar.text = if (ok) "✔ ${step.okText}" else tr("○ עדיין לא זוהה — בצע את השלב, והסימן יופיע כאן לבד.", "○ Not detected yet — do the step, and the check mark appears here by itself.")
        checkBar.setTextColor(if (ok) 0xFF1E6B34.toInt() else 0xFF6D5B4C.toInt())
        checkBar.typeface = if (ok) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        checkBar.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (ok) 0xFFDDF5E3.toInt() else 0xFFF4EEE8.toInt())
            setStroke(dp(1), if (ok) 0xFF7FCB93.toInt() else 0xFFE8D6C4.toInt())
        }
        highlightNext(ok)
    }

    /** Green, gently pulsing "next" button, so it is clear that you can move on. */
    private fun highlightNext(on: Boolean) {
        if (on == nextHighlighted) return
        nextHighlighted = on
        pulse?.cancel()
        nextButton.scaleX = 1f
        nextButton.scaleY = 1f
        (nextButton.background as? GradientDrawable)?.setColor(if (on) 0xFF1E8E3E.toInt() else ACCENT)
        if (on) {
            val sx = android.animation.ObjectAnimator.ofFloat(nextButton, View.SCALE_X, 1f, 1.07f).apply {
                repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
            }
            val sy = android.animation.ObjectAnimator.ofFloat(nextButton, View.SCALE_Y, 1f, 1.07f).apply {
                repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
            }
            pulse = android.animation.AnimatorSet().apply {
                playTogether(sx, sy)
                duration = 550
                start()
            }
        }
    }

    private fun faq() {
        val items = listOf(
            tr("לחצתי 7 פעמים ולא קרה כלום.", "I tapped 7 times and nothing happened.") to
                tr("בודקים שלוחצים על השורה הנכונה: **מספר Build** (בשיאומי: **גרסת MIUI** או **גרסת OS**). אם הופיעה ההודעה **אתה כבר מפתח** — הכול בסדר, ממשיכים.", "Make sure you tap the right line: **Build number** (on Xiaomi: **MIUI version** or **OS version**). If the message **You are already a developer** appeared — all good, carry on."),
            tr("אני לא מוצא את ניפוי באגים אלחוטי.", "I can't find Wireless debugging.") to
                tr("הוא קיים רק באנדרואיד 11 ומעלה. אם יש לך גרסה ישנה יותר — עוברים ל־**💻 עם מחשב** למעלה.", "It exists only on Android 11 and up. If you have an older version — switch to **💻 With a computer** above."),
            tr("ניפוי הבאגים האלחוטי נכבה לבד.", "Wireless debugging turned off by itself.") to
                tr("זה קורה כשה־Wi‑Fi מתנתק. בדרך כלל הבוס מדליק אותו שוב לבד. אם לא — מדליקים ידנית כמו בשלב 3.", "This happens when Wi‑Fi disconnects. Usually The Boss turns it back on by itself. If not — turn it on manually as in step 3."),
            tr("יש לי שיאומי — החיבור עובד אבל ההתקנה נכשלת.", "I have a Xiaomi — the connection works but installing fails.") to
                tr("באפשרויות למפתחים מדליקים גם **ניפוי באגים ב־USB (הגדרות אבטחה)** וגם **התקנה דרך USB**. ייתכן שיידרש כרטיס SIM בטלפון וכניסה לחשבון שיאומי.", "In Developer options turn on both **USB debugging (Security settings)** and **Install via USB**. A SIM card in the phone and a Xiaomi account sign-in may be required."),
            tr("יש לי אופו / ריאלמי / ויוו — ההתקנה נחסמת.", "I have an Oppo / Realme / Vivo — installing is blocked.") to
                tr("באפשרויות למפתחים מכבים את **ניטור הרשאות**.", "In Developer options turn off **Permission monitoring**."),
            tr("המתג של הצימוד האוטומטי אפור ולא נלחץ.", "The automatic pairing switch is grey and can't be tapped.") to
                tr("הגדרות ← **אפליקציות** ← **הבוס** ← **שלוש הנקודות ⋮** למעלה ← **אפשר הגדרות מוגבלות**. ואז מנסים שוב.", "Settings → **Apps** → **The Boss** → **three dots ⋮** at the top → **Allow restricted settings**. Then try again."),
            tr("שום דבר לא עוזר.", "Nothing helps.") to
                tr("1. בהגדרות ← אפשרויות למפתחים ← ניפוי באגים אלחוטי ← **מכשירים מותאמים** — לוחצים על **apkbridge** ← **שכח**.\n", "1. In Settings → Developer options → Wireless debugging → **Paired devices** — tap **apkbridge** → **Forget**.\n") +
                tr("2. באפליקציית הבוס ← ⚙ הגדרות ← **שכח צימוד**.\n", "2. In The Boss → ⚙ Settings → **Forget pairing**.\n") +
                tr("3. מתחילים שוב את המדריך משלב 5.\n", "3. Start the guide again from step 5.\n") +
                tr("4. עדיין לא עובד? לוחצים כאן **העתק יומן** ושולחים אותו לתמיכה.", "4. Still not working? Tap **Copy log** here and send it to support."),
        )
        for ((q, a) in items) {
            val card = card()
            card.addView(TextView(this).apply {
                text = q
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(INK)
            }, wrap(6))
            card.addView(bodyText(a), wrap(0))
        }
        val tools = card()
        tools.addView(bigButton(tr("פתח אפשרויות למפתחים", "Open Developer options"), primary = false) { openDeveloperOptions() }, wrap(8))
        tools.addView(bigButton(tr("פרטי האפליקציה (הגדרות מוגבלות)", "App info (restricted settings)"), primary = false) { openAppDetails() }, wrap(8))
        tools.addView(bigButton(tr("העתק יומן", "Copy log"), primary = false) { copy(Bridge.logSnapshot()) }, wrap(0))
    }

    // endregion

    // region steps

    private fun stepsFor(p: Part): List<Step> = when (p) {
        Part.WIRELESS -> wirelessSteps()
        Part.COMPUTER -> computerSteps()
        Part.FAQ -> emptyList()
    }

    private val devOptionsSamsung =
        tr("1. פותחים את **הגדרות** (גלגל השיניים).\n2. גוללים עד הסוף ולוחצים **אודות הטלפון**.\n3. לוחצים **מידע תוכנה**.\n", "1. Open **Settings** (the gear).\n2. Scroll to the bottom and tap **About phone**.\n3. Tap **Software information**.\n") +
            tr("4. מוצאים את השורה **מספר Build**.\n5. לוחצים עליה **7 פעמים ברצף**, בקצב רגיל.\n6. אם הטלפון מבקש — מקלידים את קוד הנעילה.\n", "4. Find the line **Build number**.\n5. Tap it **7 times in a row**, at a normal pace.\n6. If the phone asks — enter your lock code.\n") +
            tr("7. מופיעה הודעה **מצב מפתח הופעל**. מצוין!", "7. The message **Developer mode has been turned on** appears. Great!")
    private val devOptionsXiaomi =
        tr("1. **הגדרות** ← **אודות הטלפון**.\n2. לוחצים **7 פעמים** על **גרסת MIUI** (או **גרסת OS** / **HyperOS**).\n", "1. **Settings** → **About phone**.\n2. Tap **7 times** on **MIUI version** (or **OS version** / **HyperOS**).\n") +
            tr("3. אם הטלפון מבקש — מקלידים את קוד הנעילה.\n4. מופיעה הודעה **אתה עכשיו מפתח**. מצוין!", "3. If the phone asks — enter your lock code.\n4. The message **You are now a developer** appears. Great!")
    private val devOptionsOther =
        tr("1. **הגדרות** ← **אודות הטלפון**.\n2. לוחצים **7 פעמים** על **מספר Build**.\n", "1. **Settings** → **About phone**.\n2. Tap **7 times** on **Build number**.\n") +
            tr("3. אם הטלפון מבקש — מקלידים את קוד הנעילה.\n4. מופיעה הודעה **אתה עכשיו מפתח**. מצוין!", "3. If the phone asks — enter your lock code.\n4. The message **You are now a developer** appears. Great!")

    private fun devOptionsStep(): Step {
        val (mine, other) = when {
            isSamsung -> devOptionsSamsung to tr("**שיאומי / רדמי / פוקו:**\n$devOptionsXiaomi\n\n**שאר הטלפונים:**\n$devOptionsOther", "**Xiaomi / Redmi / Poco:**\n$devOptionsXiaomi\n\n**Other phones:**\n$devOptionsOther")
            isXiaomi -> devOptionsXiaomi to tr("**סמסונג:**\n$devOptionsSamsung\n\n**שאר הטלפונים:**\n$devOptionsOther", "**Samsung:**\n$devOptionsSamsung\n\n**Other phones:**\n$devOptionsOther")
            else -> devOptionsOther to tr("**סמסונג:**\n$devOptionsSamsung\n\n**שיאומי / רדמי / פוקו:**\n$devOptionsXiaomi", "**Samsung:**\n$devOptionsSamsung\n\n**Xiaomi / Redmi / Poco:**\n$devOptionsXiaomi")
        }
        return Step(
            title = tr("לפתוח את אפשרויות למפתחים", "Open Developer options"),
            body = tr("זו תיקייה נסתרת בהגדרות. צריך לגלות אותה פעם אחת.\n\n$mine", "It's a hidden folder in Settings. You need to reveal it once.\n\n$mine"),
            otherPhones = other,
            tip = tr("הכפתור למטה פותח את מסך אודות הטלפון. אחרי שזה נעשה — הכפתור השני קופץ ישר לאפשרויות למפתחים.", "The button below opens the About phone screen. Once that's done — the second button jumps straight to Developer options."),
            actions = listOf(tr("פתח את אודות הטלפון", "Open About phone") to { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) }),
            check = { Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 },
            okText = tr("אפשרויות למפתחים פתוחות!", "Developer options are unlocked!"),
        )
    }

    private fun devOptionsLocation() = when {
        isSamsung -> tr("בסמסונג: בתחתית רשימת ההגדרות, מתחת ל**אודות הטלפון**.", "On Samsung: at the bottom of the Settings list, below **About phone**.")
        isXiaomi -> tr("בשיאומי: הגדרות ← **הגדרות נוספות** ← **אפשרויות למפתחים**.", "On Xiaomi: Settings → **Additional settings** → **Developer options**.")
        else -> tr("הגדרות ← **מערכת** ← **אפשרויות למפתחים**.", "Settings → **System** → **Developer options**.")
    }

    private fun wirelessSteps() = listOf(
        devOptionsStep(),
        Step(
            title = tr("לוודא שיש Wi‑Fi", "Make sure there's Wi‑Fi"),
            body = tr("• הטלפון צריך להיות מחובר לרשת **Wi‑Fi** (סמל המניפה למעלה).\n", "• The phone must be connected to a **Wi‑Fi** network (the fan icon at the top).\n") +
                tr("• **לא צריך אינטרנט** — מספיק להיות מחוברים לרשת, למשל של הבית או העבודה.", "• **No internet needed** — being connected to a network is enough, e.g. at home or at work."),
            actions = listOf(tr("פתח הגדרות Wi‑Fi", "Open Wi‑Fi settings") to { tryStart(Compat.wifiPanelIntent()) }),
            check = { Bridge.isOnWifi() },
            okText = tr("מחובר ל־Wi‑Fi!", "Connected to Wi‑Fi!"),
        ),
        Step(
            title = tr("להדליק ניפוי באגים אלחוטי", "Turn on Wireless debugging"),
            body = tr("1. פותחים את **אפשרויות למפתחים**.\n   ${devOptionsLocation()}\n", "1. Open **Developer options**.\n   ${devOptionsLocation()}\n") +
                tr("2. גוללים למטה עד **ניפוי באגים אלחוטי**.\n", "2. Scroll down to **Wireless debugging**.\n") +
                tr("3. מדליקים את המתג לידו.\n", "3. Turn on the switch next to it.\n") +
                tr("4. מופיעה שאלה אם לאפשר ברשת הזו — לוחצים **אישור** (או **אפשר**).", "4. When asked whether to allow on this network — tap **Allow** (or **OK**)."),
            actions = listOf(tr("פתח אפשרויות למפתחים", "Open Developer options") to { openDeveloperOptions() }),
            check = { Bridge.isWirelessDebuggingOn() == true },
            okText = tr("ניפוי באגים אלחוטי דולק!", "Wireless debugging is on!"),
        ),
        Step(
            title = tr("להדליק צימוד אוטומטי", "Turn on automatic pairing"),
            body = tr("1. לוחצים על הכפתור **הפעל צימוד אוטומטי** כאן למטה.\n", "1. Tap the **Turn on automatic pairing** button below.\n") +
                tr("2. נפתחת רשימת הנגישות של הטלפון.\n", "2. The phone's Accessibility list opens.\n") +
                tr("3. מחפשים **הבוס** (או **The Boss**) ולוחצים עליו (אם לא רואים — מחפשים תחת **אפליקציות מותקנות** או **שירותים שהורדו**).\n", "3. Look for **The Boss** (or **הבוס**) and tap it (if you don't see it — look under **Installed apps** or **Downloaded services**).\n") +
                tr("4. מדליקים את המתג ומאשרים.", "4. Turn on the switch and confirm."),
            warn = tr("אם המתג אפור ולא נלחץ (נפוץ באנדרואיד 13 ומעלה): הגדרות ← **אפליקציות** ← **הבוס** ← **שלוש הנקודות ⋮** למעלה ← **אפשר הגדרות מוגבלות**. אחר כך חוזרים לכאן ומנסים שוב.", "If the switch is grey and can't be tapped (common on Android 13 and up): Settings → **Apps** → **The Boss** → **three dots ⋮** at the top → **Allow restricted settings**. Then come back here and try again."),
            actions = listOf(
                tr("הפעל צימוד אוטומטי", "Turn on automatic pairing") to { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                tr("פרטי האפליקציה (אם המתג אפור)", "App info (if the switch is grey)") to { openAppDetails() },
            ),
            check = { PairingAccessibilityService.isEnabled(this) },
            okText = tr("הצימוד האוטומטי פעיל!", "Automatic pairing is on!"),
        ),
        Step(
            title = tr("הצימוד עצמו", "The pairing itself"),
            body = tr("1. חוזרים ל**הגדרות** ← **אפשרויות למפתחים**.\n", "1. Go back to **Settings** → **Developer options**.\n") +
                tr("2. לוחצים **על המילים** ניפוי באגים אלחוטי (לא על המתג) — כדי להיכנס פנימה.\n", "2. Tap **the words** Wireless debugging (not the switch) — to go inside.\n") +
                tr("3. לוחצים על **התאמת מכשיר עם קוד התאמה**.\n", "3. Tap **Pair device with pairing code**.\n") +
                tr("4. נפתח חלון עם **קוד של 6 ספרות**.\n", "4. A window opens with a **6-digit code**.\n") +
                tr("5. **לא עושים כלום — רק מחכים כמה שניות.** הבוס קורא את הקוד ומבצע את הצימוד לבד.\n", "5. **Do nothing — just wait a few seconds.** The Boss reads the code and pairs by itself.\n") +
                tr("6. מופיעה הודעה **הבוס צומד בהצלחה** — ומטר קונפטי 🎉", "6. The message **The Boss is paired** appears — and a shower of confetti 🎉"),
            tip = tr("אם לא הדלקת צימוד אוטומטי: לוחצים כאן **צימוד עם קוד דרך התראה**, נכנסים ל**התאמת מכשיר עם קוד התאמה**, מושכים את ההתראות מלמעלה ← **הזן קוד** ← מקלידים את 6 הספרות. לא סוגרים את חלון הקוד בזמן הזה.", "If you didn't turn on automatic pairing: tap **Pair with code via notification** here, go into **Pair device with pairing code**, pull down the notifications → **Enter code** → type the 6 digits. Don't close the code window meanwhile."),
            actions = listOf(
                tr("פתח אפשרויות למפתחים", "Open Developer options") to { openDeveloperOptions() },
                tr("צימוד עם קוד דרך התראה", "Pair with code via notification") to { notificationPairing() },
            ),
            check = { Bridge.isPaired },
            okText = tr("הצימוד הצליח!", "Paired successfully!"),
        ),
        Step(
            title = tr("אישור אחרון", "Last confirmation"),
            body = tr("1. בפעם הראשונה מופיעה שאלה: **לאפשר ניפוי באגים?**\n", "1. The first time a question appears: **Allow debugging?**\n") +
                tr("2. מסמנים ✔ את **אפשר תמיד מהמחשב הזה**.\n", "2. Check ✔ **Always allow from this computer**.\n") +
                tr("3. לוחצים **אישור**.", "3. Tap **OK**."),
            tip = tr("אם השאלה לא הופיעה — זה בסדר, ממשיכים לשלב הבא.", "If the question didn't appear — that's fine, go on to the next step."),
        ),
        Step(
            title = tr("לבדוק שהכול עובד", "Check that everything works"),
            body = tr("1. לוחצים על **בדוק חיבור** כאן למטה.\n", "1. Tap **Check connection** below.\n") +
                tr("2. אם כתוב **מחובר ומוכן** — סיימתם! 🎉", "2. If it says **Connected and ready** — you're done! 🎉"),
            actions = listOf(tr("בדוק חיבור", "Check connection") to { Bridge.testConnection() }),
            check = { Bridge.lastConnectionOk },
            okText = tr("מחובר ומוכן!", "Connected and ready!"),
        ),
    )

    private fun computerSteps() = listOf(
        devOptionsStep().let {
            Step(it.title, tr("בגרסאות אנדרואיד 8, 9 ו־10 אין ניפוי באגים אלחוטי, ולכן צריך מחשב לרגע קצר: מחשב (Windows או Mac) וכבל USB — כבל הטעינה של הטלפון מתאים בדרך כלל.\n\n", "Android 8, 9 and 10 have no wireless debugging, so you need a computer for a short moment: a computer (Windows or Mac) and a USB cable — the phone's charging cable usually works.\n\n") + it.body,
                tip = tr("יש לך אנדרואיד 11 ומעלה ואין Wi‑Fi? הדרך הזו עובדת בדיוק אותו דבר גם אצלך.", "Have Android 11 or newer but no Wi‑Fi? This way works exactly the same for you too."),
                otherPhones = it.otherPhones, actions = it.actions, check = it.check, okText = it.okText)
        },
        Step(
            title = tr("להדליק ניפוי באגים ב־USB", "Turn on USB debugging"),
            body = tr("1. פותחים את **אפשרויות למפתחים**.\n   ${devOptionsLocation()}\n", "1. Open **Developer options**.\n   ${devOptionsLocation()}\n") +
                tr("2. מוצאים **ניפוי באגים ב־USB** ומדליקים.\n3. לוחצים **אישור**.", "2. Find **USB debugging** and turn it on.\n3. Tap **OK**."),
            actions = listOf(tr("פתח אפשרויות למפתחים", "Open Developer options") to { openDeveloperOptions() }),
            check = { Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 },
            okText = tr("ניפוי באגים ב־USB דולק!", "USB debugging is on!"),
        ),
        Step(
            title = tr("להוריד למחשב את הכלי של גוגל (פעם אחת)", "Download Google's tool to the computer (once)"),
            body = tr("1. במחשב פותחים את הדפדפן ונכנסים לכתובת שלמטה.\n", "1. On the computer open the browser and go to the address below.\n") +
                tr("2. לוחצים על **Download SDK Platform-Tools for Windows** (או **for Mac**).\n", "2. Click **Download SDK Platform-Tools for Windows** (or **for Mac**).\n") +
                tr("3. מסמנים ✔ את ההסכמה ולוחצים על כפתור ההורדה.\n", "3. Check ✔ the agreement and click the download button.\n") +
                tr("4. מוצאים את הקובץ שירד — בדרך כלל בתיקייה **הורדות**.\n", "4. Find the downloaded file — usually in the **Downloads** folder.\n") +
                tr("5. לוחצים עליו **בלחצן הימני** ← **חלץ הכל** ← **חלץ**.\n", "5. **Right-click** it → **Extract All** → **Extract**.\n") +
                tr("6. נוצרת תיקייה בשם **platform-tools**.", "6. A folder named **platform-tools** is created."),
            tip = tr("לחיצה על **שתף** שולחת את הכתובת למחשב — למשל בוואטסאפ ווב או במייל — כדי לא להקליד אותה.", "Tapping **Share** sends the address to the computer — e.g. via WhatsApp Web or email — so you don't have to type it."),
            copies = listOf(tr("כתובת ההורדה", "Download address") to "https://developer.android.com/tools/releases/platform-tools"),
        ),
        Step(
            title = tr("לפתוח את חלון הפקודות במחשב", "Open the command window on the computer"),
            body = tr("**ב־Windows:**\n1. נכנסים לתיקייה **platform-tools**.\n", "**On Windows:**\n1. Go into the **platform-tools** folder.\n") +
                tr("2. לוחצים פעם אחת על **שורת הכתובת** למעלה (איפה שכתוב שם התיקייה).\n", "2. Click once on the **address bar** at the top (where the folder name is written).\n") +
                tr("3. מוחקים, מקלידים **cmd** ולוחצים **Enter**.\n4. נפתח חלון שחור — זה חלון הפקודות.\n\n", "3. Delete it, type **cmd** and press **Enter**.\n4. A black window opens — that's the command window.\n\n") +
                tr("**ב־Mac:**\n1. לוחצים בלחצן ימני על התיקייה **platform-tools**.\n2. בוחרים **שירותים** ← **טרמינל חדש בתיקייה**.", "**On Mac:**\n1. Right-click the **platform-tools** folder.\n2. Choose **Services** → **New Terminal at Folder**."),
        ),
        Step(
            title = tr("לחבר את הטלפון", "Connect the phone"),
            body = tr("1. מחברים את הטלפון למחשב בכבל.\n", "1. Connect the phone to the computer with the cable.\n") +
                tr("2. בחלון השחור מקלידים את הפקודה שלמטה ולוחצים **Enter**.\n", "2. In the black window type the command below and press **Enter**.\n") +
                tr("3. בטלפון מופיעה שאלה **לאפשר ניפוי באגים ב־USB?** — מסמנים ✔ **אפשר תמיד מהמחשב הזה** ולוחצים **אישור**.\n", "3. On the phone the question **Allow USB debugging?** appears — check ✔ **Always allow from this computer** and tap **OK**.\n") +
                tr("4. מקלידים שוב את אותה פקודה ולוחצים **Enter**.\n", "4. Type the same command again and press **Enter**.\n") +
                tr("5. צריכה להופיע שורה עם מספרים ואחריהם המילה **device**. מצוין!", "5. A line with numbers followed by the word **device** should appear. Great!"),
            tip = tr("ב־Mac מקלידים ‎./adb devices (עם נקודה ולוכסן בהתחלה).", "On Mac type ./adb devices (with a dot and slash at the start)."),
            copies = listOf(tr("פקודה", "Command") to "adb devices"),
        ),
        Step(
            title = tr("הפקודה החשובה", "The important command"),
            body = tr("1. מקלידים בחלון השחור את הפקודה שלמטה ולוחצים **Enter**.\n", "1. In the black window type the command below and press **Enter**.\n") +
                tr("2. אמורה להופיע הודעה שמתחילה ב־**restarting in TCP mode**.\n", "2. A message starting with **restarting in TCP mode** should appear.\n") +
                tr("3. עכשיו אפשר **לנתק את הכבל** — המחשב כבר לא נחוץ.", "3. Now you can **unplug the cable** — the computer isn't needed anymore."),
            tip = tr("ב־Mac: ‎./adb tcpip 5555", "On Mac: ./adb tcpip 5555"),
            copies = listOf(tr("פקודה", "Command") to "adb tcpip 5555"),
        ),
        Step(
            title = tr("לבדוק באפליקציית הבוס", "Check in The Boss"),
            body = tr("1. לוחצים על **בדוק חיבור** כאן למטה.\n", "1. Tap **Check connection** below.\n") +
                tr("2. אם מופיעה שוב השאלה **לאפשר ניפוי באגים?** — מסמנים ✔ **אפשר תמיד** ומאשרים.\n", "2. If the question **Allow debugging?** appears again — check ✔ **Always allow** and confirm.\n") +
                tr("3. אם כתוב **מחובר ומוכן** — סיימתם! 🎉", "3. If it says **Connected and ready** — you're done! 🎉"),
            tip = tr("אחרי שהטלפון נכבה או מופעל מחדש — חוזרים רק על שלבים 4, 5 ו־6 (כבל ← adb tcpip 5555 ← לנתק). זה לוקח פחות מדקה.", "After the phone is turned off or restarted — repeat only steps 4, 5 and 6 (cable → adb tcpip 5555 → unplug). It takes less than a minute."),
            actions = listOf(tr("בדוק חיבור", "Check connection") to { Bridge.testConnection() }),
            check = { Bridge.lastConnectionOk },
            okText = tr("מחובר ומוכן!", "Connected and ready!"),
        ),
    )

    // endregion

    // region actions

    private fun openDeveloperOptions() {
        if (!tryStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
            Toast.makeText(this, tr("אפשרויות למפתחים עדיין סגורות — פתח אותן קודם (שלב 1).", "Developer options are still locked — unlock them first (step 1)."), Toast.LENGTH_LONG).show()
            openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
        }
    }

    private fun openSettings(action: String) {
        if (!tryStart(Intent(action))) tryStart(Intent(Settings.ACTION_SETTINGS))
    }

    private fun openAppDetails() {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun notificationPairing() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            return
        }
        PairingService.start(this)
        openDeveloperOptions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) notificationPairing()
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(tr("הבוס", "The Boss"), text))
        Toast.makeText(this, tr("הועתק.", "Copied."), Toast.LENGTH_SHORT).show()
    }

    private fun share(text: String) {
        tryStart(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), tr("שליחה למחשב", "Send to computer")))
    }

    // endregion

    // region view helpers

    /** Text with **bold** markers turned into bold spans. */
    private fun rich(raw: String): CharSequence {
        val sb = SpannableStringBuilder()
        var rest = raw
        while (true) {
            val start = rest.indexOf("**")
            val end = if (start >= 0) rest.indexOf("**", start + 2) else -1
            if (start < 0 || end < 0) { sb.append(rest); break }
            sb.append(rest.substring(0, start))
            val s = sb.length
            sb.append(rest.substring(start + 2, end))
            sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            rest = rest.substring(end + 2)
        }
        return sb
    }

    private fun bodyText(raw: String) = TextView(this).apply {
        text = rich(raw)
        textSize = 17f
        setTextColor(0xFF3F342B.toInt())
        setLineSpacing(0f, 1.25f)
        textDirection = View.TEXT_DIRECTION_ANY_RTL
    }

    private fun linkText(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(0xFFB85F12.toInt())
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(6), dp(4), dp(6))
        isClickable = true
        setOnClickListener { action() }
        Compat.focusRing(this, ACCENT, 8f)
    }

    private fun box(raw: String, fill: Int, stroke: Int) = TextView(this).apply {
        text = rich(raw)
        textSize = 15.5f
        setTextColor(0xFF3F342B.toInt())
        setLineSpacing(0f, 1.2f)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(fill); setStroke(dp(1), stroke) }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(0xFFFFFFFF.toInt())
            setStroke(dp(1), 0xFFF2E2D2.toInt())
        }
        content.addView(this, wrap(12))
    }

    private fun bigButton(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF75451F.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(if (primary) ACCENT else 0xFFFFE7D1.toInt()) }
        setPadding(dp(12), dp(14), dp(12), dp(14))
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setTextColor(0xFF75451F.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFFFFE7D1.toInt()) }
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4) }
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wrap(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    // endregion

    companion object {
        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val ACCENT = 0xFFF09A45.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()
    }
}
