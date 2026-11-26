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
    private enum class Part(val label: String) { WIRELESS("📱 בלי מחשב"), COMPUTER("💻 עם מחשב"), FAQ("❓ שאלות ותקלות") }

    private class Step(
        val title: String,
        val body: String,
        val tip: String? = null,
        val warn: String? = null,
        val otherPhones: String? = null,
        val actions: List<Pair<String, () -> Unit>> = emptyList(),
        val copies: List<Pair<String, String>> = emptyList(),
        val check: (() -> Boolean)? = null,
        val okText: String = "זוהה! אפשר להמשיך לשלב הבא.",
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
    private var finished = false
    private val checkViews = mutableListOf<Pair<TextView, Step>>()
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

    override fun onPause() {
        handler.removeCallbacks(poll)
        Celebration.onPause(this)
        super.onPause()
    }

    // region layout

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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
            contentDescription = "חזרה"
            setOnClickListener { finish() }
            Compat.focusRing(this, ACCENT, 14f)
        })
        header.addView(TextView(this).apply {
            text = "📖 מדריך התחברות"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        allButton = smallButton("הצג הכול ברצף") { showAll = !showAll; finished = false; render() }
        header.addView(allButton)
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "בטלפון שלך: אנדרואיד ${Build.VERSION.RELEASE}" +
                (if (Compat.hasWirelessDebugging) " · מתאים לך: בלי מחשב" else " · מתאים לך: עם מחשב")
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
        prevButton = bigButton("→ הקודם", primary = false) { go(-1) }
        nextButton = bigButton("הבא ←", primary = true) { go(+1) }
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
                setOnClickListener { part = p; index = 0; finished = false; render() }
                Compat.focusRing(this, INK, 18f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        }

        content.removeAllViews()
        checkViews.clear()
        val steps = stepsFor(part)
        val wizard = part != Part.FAQ && !showAll && !finished
        allButton.visibility = if (part == Part.FAQ) View.GONE else View.VISIBLE
        allButton.text = if (showAll) "שלב אחר שלב" else "הצג הכול ברצף"

        when {
            part == Part.FAQ -> { faq(); progressText.text = ""; dots.removeAllViews() }
            finished -> { finishScreen(); progressText.text = ""; dots.removeAllViews() }
            showAll -> {
                steps.forEachIndexed { i, s -> stepCard(i + 1, s) }
                progressText.text = "${steps.size} שלבים"
                dots.removeAllViews()
            }
            else -> {
                index = index.coerceIn(0, steps.lastIndex)
                stepCard(index + 1, steps[index])
                progressText.text = "שלב ${index + 1} מתוך ${steps.size}"
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
        nextButton.text = if (wizard && index == steps.lastIndex) "סיום ✓" else "הבא ←"
        refreshChecks()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun go(delta: Int) {
        val steps = stepsFor(part)
        if (delta > 0 && index == steps.lastIndex) { finished = true; render(); return }
        index = (index + delta).coerceIn(0, steps.lastIndex)
        render()
    }

    private fun stepCard(number: Int, step: Step) {
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
            card.addView(linkText("📱 יש לי טלפון אחר ▾") {
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
            row.addView(smallButton("העתק") { copy(command) })
            row.addView(smallButton("שתף") { share(command) })
            card.addView(row, wrap(8))
        }
        for ((label, action) in step.actions) {
            card.addView(bigButton(label, primary = false) { action() }, wrap(8))
        }
        if (step.check != null) {
            val status = TextView(this).apply { textSize = 15f; setPadding(dp(4), dp(4), dp(4), 0) }
            card.addView(status, wrap(0))
            checkViews += status to step
        }
    }

    private fun refreshChecks() {
        for ((view, step) in checkViews) {
            val ok = runCatching { step.check?.invoke() == true }.getOrDefault(false)
            view.text = if (ok) "✔ ${step.okText}" else "○ עדיין לא זוהה — אפשר לבצע ולחזור לכאן."
            view.setTextColor(if (ok) 0xFF1E8E3E.toInt() else MUTED)
            view.typeface = if (ok) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun finishScreen() {
        val card = card()
        card.addView(TextView(this).apply {
            text = "🎉"
            textSize = 56f
            gravity = Gravity.CENTER
        }, wrap(6))
        card.addView(TextView(this).apply {
            text = "סיימת! הכול מוכן"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(INK)
        }, wrap(8))
        card.addView(bodyText(
            if (part == Part.WIRELESS) "מעכשיו לא צריך לחזור על ההגדרה. גם כשה־Wi‑Fi כבוי האפליקציה ממשיכה לעבוד, ואחרי הפעלה מחדש של הטלפון הכול מתחדש לבד."
            else "אחרי שהטלפון נכבה או מופעל מחדש, חוזרים רק על שלבים 4, 5 ו־6 (כבל ← adb tcpip 5555 ← לנתק)."
        ).apply { gravity = Gravity.CENTER }, wrap(14))
        card.addView(bigButton("עבור להתקנת אפליקציות", primary = true) {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(BottomNav.EXTRA_TAB, BottomNav.Tab.INSTALL.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }, wrap(8))
        card.addView(bigButton("חזרה למדריך", primary = false) { finished = false; render() }, wrap(0))
    }

    private fun faq() {
        val items = listOf(
            "לחצתי 7 פעמים ולא קרה כלום." to
                "בודקים שלוחצים על השורה הנכונה: **מספר Build** (בשיאומי: **גרסת MIUI** או **גרסת OS**). אם הופיעה ההודעה **אתה כבר מפתח** — הכול בסדר, ממשיכים.",
            "אני לא מוצא את ניפוי באגים אלחוטי." to
                "הוא קיים רק באנדרואיד 11 ומעלה. אם יש לך גרסה ישנה יותר — עוברים ל־**💻 עם מחשב** למעלה.",
            "ניפוי הבאגים האלחוטי נכבה לבד." to
                "זה קורה כשה־Wi‑Fi מתנתק. בדרך כלל APK Bridge מדליקה אותו שוב לבד. אם לא — מדליקים ידנית כמו בשלב 3.",
            "יש לי שיאומי — החיבור עובד אבל ההתקנה נכשלת." to
                "באפשרויות למפתחים מדליקים גם **ניפוי באגים ב־USB (הגדרות אבטחה)** וגם **התקנה דרך USB**. ייתכן שיידרש כרטיס SIM בטלפון וכניסה לחשבון שיאומי.",
            "יש לי אופו / ריאלמי / ויוו — ההתקנה נחסמת." to
                "באפשרויות למפתחים מכבים את **ניטור הרשאות**.",
            "המתג של הצימוד האוטומטי אפור ולא נלחץ." to
                "הגדרות ← **אפליקציות** ← **APK Bridge** ← **שלוש הנקודות ⋮** למעלה ← **אפשר הגדרות מוגבלות**. ואז מנסים שוב.",
            "שום דבר לא עוזר." to
                "1. בהגדרות ← אפשרויות למפתחים ← ניפוי באגים אלחוטי ← **מכשירים מותאמים** — לוחצים על APK Bridge ← **שכח**.\n" +
                "2. ב־APK Bridge ← ⚙ הגדרות ← **שכח צימוד**.\n" +
                "3. מתחילים שוב את המדריך משלב 5.\n" +
                "4. עדיין לא עובד? לוחצים כאן **העתק יומן** ושולחים אותו לתמיכה.",
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
        tools.addView(bigButton("פתח אפשרויות למפתחים", primary = false) { openDeveloperOptions() }, wrap(8))
        tools.addView(bigButton("פרטי האפליקציה (הגדרות מוגבלות)", primary = false) { openAppDetails() }, wrap(8))
        tools.addView(bigButton("העתק יומן", primary = false) { copy(Bridge.logSnapshot()) }, wrap(0))
    }

    // endregion

    // region steps

    private fun stepsFor(p: Part): List<Step> = when (p) {
        Part.WIRELESS -> wirelessSteps()
        Part.COMPUTER -> computerSteps()
        Part.FAQ -> emptyList()
    }

    private val devOptionsSamsung =
        "1. פותחים את **הגדרות** (גלגל השיניים).\n2. גוללים עד הסוף ולוחצים **אודות הטלפון**.\n3. לוחצים **מידע תוכנה**.\n" +
            "4. מוצאים את השורה **מספר Build**.\n5. לוחצים עליה **7 פעמים ברצף**, בקצב רגיל.\n6. אם הטלפון מבקש — מקלידים את קוד הנעילה.\n" +
            "7. מופיעה הודעה **מצב מפתח הופעל**. מצוין!"
    private val devOptionsXiaomi =
        "1. **הגדרות** ← **אודות הטלפון**.\n2. לוחצים **7 פעמים** על **גרסת MIUI** (או **גרסת OS** / **HyperOS**).\n" +
            "3. אם הטלפון מבקש — מקלידים את קוד הנעילה.\n4. מופיעה הודעה **אתה עכשיו מפתח**. מצוין!"
    private val devOptionsOther =
        "1. **הגדרות** ← **אודות הטלפון**.\n2. לוחצים **7 פעמים** על **מספר Build**.\n" +
            "3. אם הטלפון מבקש — מקלידים את קוד הנעילה.\n4. מופיעה הודעה **אתה עכשיו מפתח**. מצוין!"

    private fun devOptionsStep(): Step {
        val (mine, other) = when {
            isSamsung -> devOptionsSamsung to "**שיאומי / רדמי / פוקו:**\n$devOptionsXiaomi\n\n**שאר הטלפונים:**\n$devOptionsOther"
            isXiaomi -> devOptionsXiaomi to "**סמסונג:**\n$devOptionsSamsung\n\n**שאר הטלפונים:**\n$devOptionsOther"
            else -> devOptionsOther to "**סמסונג:**\n$devOptionsSamsung\n\n**שיאומי / רדמי / פוקו:**\n$devOptionsXiaomi"
        }
        return Step(
            title = "לפתוח את אפשרויות למפתחים",
            body = "זו תיקייה נסתרת בהגדרות. צריך לגלות אותה פעם אחת.\n\n$mine",
            otherPhones = other,
            tip = "הכפתור למטה פותח את מסך אודות הטלפון. אחרי שזה נעשה — הכפתור השני קופץ ישר לאפשרויות למפתחים.",
            actions = listOf("פתח את אודות הטלפון" to { openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) }),
            check = { Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 },
            okText = "אפשרויות למפתחים פתוחות!",
        )
    }

    private fun devOptionsLocation() = when {
        isSamsung -> "בסמסונג: בתחתית רשימת ההגדרות, מתחת ל**אודות הטלפון**."
        isXiaomi -> "בשיאומי: הגדרות ← **הגדרות נוספות** ← **אפשרויות למפתחים**."
        else -> "הגדרות ← **מערכת** ← **אפשרויות למפתחים**."
    }

    private fun wirelessSteps() = listOf(
        devOptionsStep(),
        Step(
            title = "לוודא שיש Wi‑Fi",
            body = "• הטלפון צריך להיות מחובר לרשת **Wi‑Fi** (סמל המניפה למעלה).\n" +
                "• **לא צריך אינטרנט** — מספיק להיות מחוברים לרשת, למשל של הבית או העבודה.",
            actions = listOf("פתח הגדרות Wi‑Fi" to { tryStart(Compat.wifiPanelIntent()) }),
            check = { Bridge.isOnWifi() },
            okText = "מחובר ל־Wi‑Fi!",
        ),
        Step(
            title = "להדליק ניפוי באגים אלחוטי",
            body = "1. פותחים את **אפשרויות למפתחים**.\n   ${devOptionsLocation()}\n" +
                "2. גוללים למטה עד **ניפוי באגים אלחוטי**.\n" +
                "3. מדליקים את המתג לידו.\n" +
                "4. מופיעה שאלה אם לאפשר ברשת הזו — לוחצים **אישור** (או **אפשר**).",
            actions = listOf("פתח אפשרויות למפתחים" to { openDeveloperOptions() }),
            check = { Bridge.isWirelessDebuggingOn() == true },
            okText = "ניפוי באגים אלחוטי דולק!",
        ),
        Step(
            title = "להדליק צימוד אוטומטי",
            body = "1. לוחצים על הכפתור **הפעל צימוד אוטומטי** כאן למטה.\n" +
                "2. נפתחת רשימת הנגישות של הטלפון.\n" +
                "3. מחפשים **APK Bridge** ולוחצים עליו (אם לא רואים — מחפשים תחת **אפליקציות מותקנות** או **שירותים שהורדו**).\n" +
                "4. מדליקים את המתג ומאשרים.",
            warn = "אם המתג אפור ולא נלחץ (נפוץ באנדרואיד 13 ומעלה): הגדרות ← **אפליקציות** ← **APK Bridge** ← **שלוש הנקודות ⋮** למעלה ← **אפשר הגדרות מוגבלות**. אחר כך חוזרים לכאן ומנסים שוב.",
            actions = listOf(
                "הפעל צימוד אוטומטי" to { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                "פרטי האפליקציה (אם המתג אפור)" to { openAppDetails() },
            ),
            check = { PairingAccessibilityService.isEnabled(this) },
            okText = "הצימוד האוטומטי פעיל!",
        ),
        Step(
            title = "הצימוד עצמו",
            body = "1. חוזרים ל**הגדרות** ← **אפשרויות למפתחים**.\n" +
                "2. לוחצים **על המילים** ניפוי באגים אלחוטי (לא על המתג) — כדי להיכנס פנימה.\n" +
                "3. לוחצים על **התאמת מכשיר עם קוד התאמה**.\n" +
                "4. נפתח חלון עם **קוד של 6 ספרות**.\n" +
                "5. **לא עושים כלום — רק מחכים כמה שניות.** APK Bridge קוראת את הקוד ומבצעת את הצימוד לבד.\n" +
                "6. מופיעה הודעה **APK Bridge צומד בהצלחה** — ומטר קונפטי 🎉",
            tip = "אם לא הדלקת צימוד אוטומטי: לוחצים כאן **צימוד עם קוד דרך התראה**, נכנסים ל**התאמת מכשיר עם קוד התאמה**, מושכים את ההתראות מלמעלה ← **הזן קוד** ← מקלידים את 6 הספרות. לא סוגרים את חלון הקוד בזמן הזה.",
            actions = listOf(
                "פתח אפשרויות למפתחים" to { openDeveloperOptions() },
                "צימוד עם קוד דרך התראה" to { notificationPairing() },
            ),
            check = { Bridge.isPaired },
            okText = "הצימוד הצליח!",
        ),
        Step(
            title = "אישור אחרון",
            body = "1. בפעם הראשונה מופיעה שאלה: **לאפשר ניפוי באגים?**\n" +
                "2. מסמנים ✔ את **אפשר תמיד מהמחשב הזה**.\n" +
                "3. לוחצים **אישור**.",
            tip = "אם השאלה לא הופיעה — זה בסדר, ממשיכים לשלב הבא.",
        ),
        Step(
            title = "לבדוק שהכול עובד",
            body = "1. לוחצים על **בדוק חיבור** כאן למטה.\n" +
                "2. אם כתוב **מחובר ומוכן** — סיימתם! 🎉",
            actions = listOf("בדוק חיבור" to { Bridge.testConnection() }),
            check = { Bridge.lastConnectionOk },
            okText = "מחובר ומוכן!",
        ),
    )

    private fun computerSteps() = listOf(
        devOptionsStep().let {
            Step(it.title, "בגרסאות אנדרואיד 8, 9 ו־10 אין ניפוי באגים אלחוטי, ולכן צריך מחשב לרגע קצר: מחשב (Windows או Mac) וכבל USB — כבל הטעינה של הטלפון מתאים בדרך כלל.\n\n" + it.body,
                tip = "יש לך אנדרואיד 11 ומעלה ואין Wi‑Fi? הדרך הזו עובדת בדיוק אותו דבר גם אצלך.",
                otherPhones = it.otherPhones, actions = it.actions, check = it.check, okText = it.okText)
        },
        Step(
            title = "להדליק ניפוי באגים ב־USB",
            body = "1. פותחים את **אפשרויות למפתחים**.\n   ${devOptionsLocation()}\n" +
                "2. מוצאים **ניפוי באגים ב־USB** ומדליקים.\n3. לוחצים **אישור**.",
            actions = listOf("פתח אפשרויות למפתחים" to { openDeveloperOptions() }),
            check = { Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 },
            okText = "ניפוי באגים ב־USB דולק!",
        ),
        Step(
            title = "להוריד למחשב את הכלי של גוגל (פעם אחת)",
            body = "1. במחשב פותחים את הדפדפן ונכנסים לכתובת שלמטה.\n" +
                "2. לוחצים על **Download SDK Platform-Tools for Windows** (או **for Mac**).\n" +
                "3. מסמנים ✔ את ההסכמה ולוחצים על כפתור ההורדה.\n" +
                "4. מוצאים את הקובץ שירד — בדרך כלל בתיקייה **הורדות**.\n" +
                "5. לוחצים עליו **בלחצן הימני** ← **חלץ הכל** ← **חלץ**.\n" +
                "6. נוצרת תיקייה בשם **platform-tools**.",
            tip = "לחיצה על **שתף** שולחת את הכתובת למחשב — למשל בוואטסאפ ווב או במייל — כדי לא להקליד אותה.",
            copies = listOf("כתובת ההורדה" to "https://developer.android.com/tools/releases/platform-tools"),
        ),
        Step(
            title = "לפתוח את חלון הפקודות במחשב",
            body = "**ב־Windows:**\n1. נכנסים לתיקייה **platform-tools**.\n" +
                "2. לוחצים פעם אחת על **שורת הכתובת** למעלה (איפה שכתוב שם התיקייה).\n" +
                "3. מוחקים, מקלידים **cmd** ולוחצים **Enter**.\n4. נפתח חלון שחור — זה חלון הפקודות.\n\n" +
                "**ב־Mac:**\n1. לוחצים בלחצן ימני על התיקייה **platform-tools**.\n2. בוחרים **שירותים** ← **טרמינל חדש בתיקייה**.",
        ),
        Step(
            title = "לחבר את הטלפון",
            body = "1. מחברים את הטלפון למחשב בכבל.\n" +
                "2. בחלון השחור מקלידים את הפקודה שלמטה ולוחצים **Enter**.\n" +
                "3. בטלפון מופיעה שאלה **לאפשר ניפוי באגים ב־USB?** — מסמנים ✔ **אפשר תמיד מהמחשב הזה** ולוחצים **אישור**.\n" +
                "4. מקלידים שוב את אותה פקודה ולוחצים **Enter**.\n" +
                "5. צריכה להופיע שורה עם מספרים ואחריהם המילה **device**. מצוין!",
            tip = "ב־Mac מקלידים ‎./adb devices (עם נקודה ולוכסן בהתחלה).",
            copies = listOf("פקודה" to "adb devices"),
        ),
        Step(
            title = "הפקודה החשובה",
            body = "1. מקלידים בחלון השחור את הפקודה שלמטה ולוחצים **Enter**.\n" +
                "2. אמורה להופיע הודעה שמתחילה ב־**restarting in TCP mode**.\n" +
                "3. עכשיו אפשר **לנתק את הכבל** — המחשב כבר לא נחוץ.",
            tip = "ב־Mac: ‎./adb tcpip 5555",
            copies = listOf("פקודה" to "adb tcpip 5555"),
        ),
        Step(
            title = "לבדוק ב־APK Bridge",
            body = "1. לוחצים על **בדוק חיבור** כאן למטה.\n" +
                "2. אם מופיעה שוב השאלה **לאפשר ניפוי באגים?** — מסמנים ✔ **אפשר תמיד** ומאשרים.\n" +
                "3. אם כתוב **מחובר ומוכן** — סיימתם! 🎉",
            tip = "אחרי שהטלפון נכבה או מופעל מחדש — חוזרים רק על שלבים 4, 5 ו־6 (כבל ← adb tcpip 5555 ← לנתק). זה לוקח פחות מדקה.",
            actions = listOf("בדוק חיבור" to { Bridge.testConnection() }),
            check = { Bridge.lastConnectionOk },
            okText = "מחובר ומוכן!",
        ),
    )

    // endregion

    // region actions

    private fun openDeveloperOptions() {
        if (!tryStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
            Toast.makeText(this, "אפשרויות למפתחים עדיין סגורות — פתח אותן קודם (שלב 1).", Toast.LENGTH_LONG).show()
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
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("APK Bridge", text))
        Toast.makeText(this, "הועתק.", Toast.LENGTH_SHORT).show()
    }

    private fun share(text: String) {
        tryStart(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "שליחה למחשב"))
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
