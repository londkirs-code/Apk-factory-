package com.londkirs.apkbridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Welcome screens, shown once on first launch: what the app does, what pairing means, and a
 * choice of language. The last page opens the step-by-step pairing guide (or skips to the app).
 */
class OnboardingActivity : Activity() {

    private class Page(val emoji: String, val title: () -> String, val body: () -> String)

    private val pages = listOf(
        Page("🕴", { tr("ברוך הבא להבוס", "Welcome to The Boss") }, {
            tr("הבוס נותן לך שליטה מלאה בטלפון — בלי מחשב ובלי רוט.\n\n" +
                "• התקנת APK ו‑APKS/XAPK בלחיצה, גם כמה ביחד\n" +
                "• ניהול אפליקציות: הסרה מרובה, השבתה, מיון וסינון\n" +
                "• גיבוי ושחזור מלא, כולל Termux\n" +
                "• טרמינל ADB בסגנון צ׳אט עם קיצורים מוכנים",
                "The Boss gives you full control of your phone — no computer, no root.\n\n" +
                "• Install APK and APKS/XAPK in one tap, several at once\n" +
                "• Manage apps: bulk uninstall, disable, sort and filter\n" +
                "• Full backup and restore, Termux included\n" +
                "• A chat-style ADB terminal with ready-made shortcuts")
        }),
        Page("🔗", { tr("צימוד חד‑פעמי", "One-time pairing") }, {
            tr("כדי לקבל את הכוח הזה, הבוס מתחבר לטלפון דרך \"ניפוי באגים אלחוטי\" שמובנה ב‑Android.\n\n" +
                "עושים את זה פעם אחת בלבד (כ‑2 דקות), והמדריך מלווה אותך צעד אחר צעד עם בדיקה אוטומטית בכל שלב.\n\n" +
                "הכול נשאר בתוך הטלפון — שום מידע לא נשלח החוצה.",
                "To get this power, The Boss connects to your phone through Android's built-in \"Wireless debugging\".\n\n" +
                "You do it only once (about 2 minutes), and the guide walks you through it step by step with an automatic check at each step.\n\n" +
                "Everything stays on the phone — nothing is sent out.")
        }),
        Page("🌐", { tr("שפה", "Language") }, {
            tr("בחר את שפת האפליקציה. אפשר לשנות בכל זמן בהגדרות.", "Choose the app language. You can change it any time in Settings.")
        }),
    )
    private var index = 0
    private var startedHebrew = Lang.hebrew

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        index = savedInstanceState?.getInt("i") ?: 0
        startedHebrew = savedInstanceState?.getBoolean("h", Lang.hebrew) ?: Lang.hebrew
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("i", index)
        outState.putBoolean("h", startedHebrew)
    }

    private fun render() {
        val page = pages[index]
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = Lang.direction
            setBackgroundColor(BACKGROUND)
            setPadding(dp(24), dp(28), dp(24), dp(20))
        }
        // Top: "skip"
        root.addView(TextView(this).apply {
            text = tr("דלג", "Skip")
            textSize = 15f
            setTextColor(MUTED)
            gravity = Gravity.END
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isFocusable = true
            setOnClickListener { finishOnboarding(openGuide = false) }
            visibility = if (index < pages.size - 1) View.VISIBLE else View.INVISIBLE
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(TextView(this).apply {
            text = page.emoji
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(12))
        })
        content.addView(TextView(this).apply {
            text = page.title()
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        content.addView(TextView(this).apply {
            text = page.body()
            textSize = 16f
            setTextColor(INK)
            setLineSpacing(dp(3).toFloat(), 1f)
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (index == pages.size - 1) content.addView(languageChoice())
        root.addView(ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Dots
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            for (i in pages.indices) addView(View(this@OnboardingActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == index) ACCENT else 0xFFE8D6C4.toInt())
                }
            }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { setMargins(dp(4), 0, dp(4), 0) })
        })

        // Bottom buttons
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (index > 0) row.addView(button(tr("הקודם", "Back"), primary = false) { index--; render() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        val last = index == pages.size - 1
        val next = button(if (last) tr("🚀 התחל צימוד", "🚀 Start pairing") else tr("הבא", "Next"), primary = true) {
            if (last) finishOnboarding(openGuide = true) else { index++; render() }
        }
        row.addView(next, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        root.addView(row)
        if (last) root.addView(TextView(this).apply {
            text = tr("אחר כך, אכנס לבד", "Later, I'll explore on my own")
            textSize = 14f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
            isFocusable = true
            setOnClickListener { finishOnboarding(openGuide = false) }
        })
        setContentView(root)
        next.requestFocus()
    }

    private fun languageChoice() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(18), 0, 0)
        val mode = Lang.mode(this@OnboardingActivity)
        for ((value, label) in listOf("auto" to tr("📱 לפי שפת הטלפון", "📱 Same as the phone"), "he" to "🇮🇱 עברית", "en" to "🇬🇧 English")) { // i18n-ok
            addView(button((if (value == mode) "✔ " else "") + label, primary = value == mode) {
                Lang.setMode(this@OnboardingActivity, value)
                render()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
    }

    private fun finishOnboarding(openGuide: Boolean) {
        markSeen(this)
        if (Lang.hebrew != startedHebrew) { Lang.restart(this, openGuide); return }
        if (openGuide) startActivity(Intent(this, GuideActivity::class.java))
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && index > 0) { index--; render(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() = finishOnboarding(openGuide = false)

    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF75451F.toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) ACCENT else 0xFFFFE7D1.toInt())
        }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val ACCENT = 0xFFF09A45.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()

        /** First launch only, and not for users who already paired with an older version. */
        fun shouldShow(context: Context): Boolean =
            !context.getSharedPreferences("bridge", Context.MODE_PRIVATE).getBoolean("onboarding_seen", false) && !Bridge.isPaired

        fun markSeen(context: Context) =
            context.getSharedPreferences("bridge", Context.MODE_PRIVATE).edit().putBoolean("onboarding_seen", true).apply()
    }
}
