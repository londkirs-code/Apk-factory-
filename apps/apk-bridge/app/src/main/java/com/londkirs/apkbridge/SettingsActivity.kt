package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** All APK Bridge preferences in one place. Every change is saved immediately. */
class SettingsActivity : Activity() {
    private lateinit var a11yState: TextView
    private lateinit var wirelessState: TextView
    private lateinit var updatesOnly: CheckBox
    private lateinit var wifiHelperState: TextView
    private lateinit var wifiHelperButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
    }

    private val stateListener: (BridgeState) -> Unit = { if (!it.busy) refresh() }

    override fun onResume() {
        super.onResume()
        HelperInstaller.resumeIfPermitted(this)
        refresh()
    }

    override fun onStart() {
        super.onStart()
        Bridge.addStateListener(stateListener)
    }

    override fun onStop() {
        Bridge.removeStateListener(stateListener)
        super.onStop()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "⚙ הגדרות"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(button("סגור", false) { finish() })
        page.addView(top, matchWrap(14))

        // Automatic install
        val auto = card(page)
        auto.addView(heading("התקנה אוטומטית"), matchWrap())
        auto.addView(body("מה לעשות כשקובץ APK / APKS / XAPK / APKM נפתח או משותף אל APK Bridge מאפליקציה אחרת (מנהל קבצים, דפדפן, טלגרם…):"), matchWrap(4))
        auto.addView(radios(
            listOf("off" to "כבוי — רק לבחור את הקובץ, ולהתקין בלחיצה", "ask" to "לשאול אותי לפני ההתקנה", "auto" to "להתקין מיד בלי לשאול"),
            Bridge.autoInstallMode,
        ) { Bridge.autoInstallMode = it; refresh() }, matchWrap(4))
        updatesOnly = checkBox("להתקין אוטומטית רק עדכונים של אפליקציות שכבר מותקנות", Bridge.autoInstallUpdatesOnly) {
            Bridge.autoInstallUpdatesOnly = it
        }
        auto.addView(updatesOnly, matchWrap(0))
        auto.addView(note("התקנה אוטומטית פועלת רק אחרי שבוצע זיווג. קובץ שאינו עדכון (כשהאפשרות למעלה מסומנת) ייבחר בלבד, ותוצג שאלה."), matchWrap())

        // After install
        val after = card(page)
        after.addView(heading("אחרי התקנה"), matchWrap())
        after.addView(body("פתיחת האפליקציה שהותקנה:"), matchWrap(4))
        after.addView(radios(
            listOf("ask" to "לשאול אם לפתוח", "auto" to "לפתוח מיד", "never" to "לא לפתוח"),
            Bridge.openAfterInstall,
        ) { Bridge.openAfterInstall = it }, matchWrap(4))
        after.addView(checkBox("התראה על סיום התקנה כש־APK Bridge ברקע", Bridge.installNotifications) {
            Bridge.installNotifications = it
        }, matchWrap(0))

        // Install options
        val opts = card(page)
        opts.addView(heading("אפשרויות התקנה"), matchWrap())
        val o = Bridge.loadOptions()
        opts.addView(checkBox("רשום את Google Play כמקור ההתקנה (‎-i)", o.registerPlayInstaller) {
            Bridge.saveOptions(Bridge.loadOptions().copy(registerPlayInstaller = it))
        }, matchWrap(0))
        opts.addView(checkBox("אפשר שנמוך גרסה (‎-d)", o.allowDowngrade) {
            Bridge.saveOptions(Bridge.loadOptions().copy(allowDowngrade = it))
        }, matchWrap(0))
        opts.addView(checkBox("הענק את כל ההרשאות מראש (‎-g)", o.grantPermissions) {
            Bridge.saveOptions(Bridge.loadOptions().copy(grantPermissions = it))
        }, matchWrap(0))
        opts.addView(checkBox("אפשר חבילות בדיקה (‎-t)", o.allowTestPackages) {
            Bridge.saveOptions(Bridge.loadOptions().copy(allowTestPackages = it))
        }, matchWrap(0))
        if (Build.VERSION.SDK_INT >= 34) {
            opts.addView(checkBox("עקוף חסימת targetSdk נמוך (Android 14+)", o.bypassLowTargetSdk) {
                Bridge.saveOptions(Bridge.loadOptions().copy(bypassLowTargetSdk = it))
            }, matchWrap(0))
        }
        opts.addView(note("רישום Google Play כמקור ההתקנה הוא סימון בלבד: ההתקנה מתבצעת דרך ADB, והאפליקציה אינה מקבלת רישוי, רכישה או אימות של Play."), matchWrap())

        // Pairing & connection
        val pair = card(page)
        pair.addView(heading("זיווג וחיבור"), matchWrap())
        pair.addView(checkBox("זיווג אוטומטי: לזהות את קוד הזיווג מהמסך (נגישות)", Bridge.accessibilityAutoPair) {
            Bridge.accessibilityAutoPair = it
            refresh()
        }, matchWrap(0))
        a11yState = note("")
        pair.addView(a11yState, matchWrap(4))
        pair.addView(button("הגדרות נגישות", false) {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: ActivityNotFoundException) {}
        }, matchWrap(12))
        pair.addView(checkBox("להדליק Wi‑Fi כשצריך: לפתוח את חלונית ה־Wi‑Fi אוטומטית ולהמשיך כשהוא מתחבר", Bridge.autoWifi) {
            Bridge.autoWifi = it
        }, matchWrap(0))
        wifiHelperState = note("")
        pair.addView(wifiHelperState, matchWrap(4))
        wifiHelperButton = button("התקן את עזר ה־Wi‑Fi", false) {
            HelperInstaller.start(this)
        }
        pair.addView(wifiHelperButton, matchWrap(12))
        pair.addView(checkBox("להדליק ניפוי באגים אלחוטי אוטומטית כשהוא כבוי", Bridge.autoEnableWireless) {
            Bridge.autoEnableWireless = it
            refresh()
        }, matchWrap(0))
        wirelessState = note("")
        pair.addView(wirelessState, matchWrap(12))
        pair.addView(button("אפס פורט חיבור שמור", false) {
            Bridge.savedConnectPort = 0
            toast("הפורט נמחק; הוא יאותר מחדש בחיבור הבא.")
        }, matchWrap())
        pair.addView(button("שכח זיווג (מחק מפתח ADB)", false) {
            AlertDialog.Builder(this)
                .setTitle("לשכוח את הזיווג?")
                .setMessage("מפתח ה־ADB של APK Bridge יימחק ויהיה צורך לזווג מחדש. מומלץ גם למחוק את \"APK Bridge\" מרשימת המכשירים המזווגים בהגדרות ניפוי הבאגים האלחוטי.")
                .setPositiveButton("מחק") { _, _ -> Bridge.forgetPairing(); toast("הזיווג נמחק.") }
                .setNegativeButton("ביטול", null)
                .show()
        }, matchWrap())

        // Display
        val display = card(page)
        display.addView(heading("תצוגה"), matchWrap())
        display.addView(checkBox("להציג את היומן פתוח", Bridge.logExpanded) { Bridge.logExpanded = it }, matchWrap(0))

        val root = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
            addView(page)
        }
        window.setDecorFitsSystemWindows(false)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(root)
    }

    private fun refresh() {
        if (!::a11yState.isInitialized) return
        val enabled = PairingAccessibilityService.isEnabled(this)
        a11yState.text = when {
            !Bridge.accessibilityAutoPair -> "כבוי ב־APK Bridge (השירות לא יפעל גם אם מופעל בנגישות)."
            enabled -> "✔ שירות הנגישות מופעל."
            else -> "שירות הנגישות עדיין לא מופעל: הגדרות נגישות ← APK Bridge ← הפעל."
        }
        wirelessState.text = if (Bridge.canWriteSecureSettings()) {
            "✔ להרשאה הדרושה יש אישור."
        } else {
            "דורש הרשאה חד־פעמית: במסך הראשי ← כלים ← \"אפשר הפעלה אוטומטית של ניפוי באגים אלחוטי\"."
        }
        val helper = Bridge.isWifiHelperInstalled()
        wifiHelperState.text = when {
            helper -> "✔ עזר ה־Wi‑Fi מותקן: Wi‑Fi כבוי יודלק לבד, בלי הקשה."
            Bridge.isWifiHelperBundled() -> "בלי עזר ה־Wi‑Fi תיפתח חלונית Wi‑Fi שבה צריך להקיש פעם אחת. עזר ה־Wi‑Fi הוא אפליקציה קטנה (מובנית ב־APK Bridge) שבנויה ל־Android 9, ולכן Android עדיין מרשה לה להדליק Wi‑Fi — כמו Termux:API. ההתקנה חד־פעמית דרך מתקין האפליקציות של Android — לא נדרש ניפוי באגים."
            else -> "עזר ה־Wi‑Fi לא נכלל בגרסה הזו."
        }
        wifiHelperButton.visibility = if (!helper && Bridge.isWifiHelperBundled()) View.VISIBLE else View.GONE
        updatesOnly.isEnabled = Bridge.autoInstallMode != "off"
        updatesOnly.alpha = if (updatesOnly.isEnabled) 1f else 0.5f
    }

    // region view helpers

    private fun radios(choices: List<Pair<String, String>>, current: String, onChange: (String) -> Unit): RadioGroup {
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        choices.forEachIndexed { i, (value, label) ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 14f
                setTextColor(INK)
                tag = value
                isChecked = value == current || (i == 0 && choices.none { it.first == current })
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            (g.findViewById<RadioButton>(checkedId)?.tag as? String)?.let(onChange)
        }
        return group
    }

    private fun checkBox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = label
        textSize = 14f
        isChecked = checked
        setTextColor(INK)
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(0xFFFFFFFF.toInt())
            setStroke(dp(1), 0xFFF2E2D2.toInt())
        }
        parent.addView(this, matchWrap(14))
    }

    private fun heading(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(INK)
        setPadding(0, 0, 0, dp(6))
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(0xFF55483E.toInt())
    }

    private fun note(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(MUTED)
        setPadding(0, dp(2), 0, 0)
    }

    private fun button(textValue: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 15f
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF75451F.toInt())
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(if (primary) 0xFFF09A45.toInt() else 0xFFFFE7D1.toInt())
        }
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { action() }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap(bottom: Int = 9) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    // endregion

    companion object {
        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()
    }
}
