package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** All APK Bridge preferences in one place. Every change is saved immediately. */
class SettingsActivity : BossActivity() {
    override val swipeTab get() = BottomNav.Tab.SETTINGS
    private lateinit var a11yState: TextView
    private lateinit var wirelessState: TextView
    private lateinit var wifiHelperState: TextView
    private lateinit var wifiHelperButton: Button
    private lateinit var a11yButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
    }

    private val stateListener: (BridgeState) -> Unit = { if (!it.busy) refresh() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Celebration.onFocus(this, hasFocus)
    }

    override fun onPause() {
        Celebration.onPause(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Celebration.onResume(this)
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
            layoutDirection = Lang.direction
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = tr("⚙ הגדרות", "⚙ Settings")
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(top, matchWrap(14))

        // Language
        val lang = card(page)
        lang.addView(heading(tr("🌐 שפה", "🌐 Language")), matchWrap())
        lang.addView(radios(
            listOf("auto" to tr("לפי שפת הטלפון", "Same as the phone"), "he" to tr("עברית", "עברית (Hebrew)"), "en" to "English"),
            Lang.mode(this),
        ) { mode ->
            val wasHebrew = Lang.hebrew
            Lang.setMode(this, mode)
            if (Lang.hebrew != wasHebrew) Lang.restart(this)
        }, matchWrap(4))

        // Automatic install
        val auto = card(page)
        auto.addView(heading(tr("התקנה אוטומטית", "Automatic install")), matchWrap())
        auto.addView(body(tr("מה לעשות כשקובץ APK / APKS / XAPK / APKM נפתח או משותף אל הבוס מאפליקציה אחרת (מנהל קבצים, דפדפן, טלגרם…):", "What to do when an APK / APKS / XAPK / APKM file is opened or shared to The Boss from another app (file manager, browser, Telegram…):")), matchWrap(4))
        auto.addView(radios(
            listOf("off" to tr("כבוי — רק לבחור את הקובץ, ולהתקין בלחיצה", "Off — just choose the file, and install with a tap"), "ask" to tr("לשאול אותי לפני ההתקנה", "Ask me before installing"), "auto" to tr("עדכונים — להתקין מיד בלי לשאול", "Updates — install right away without asking")),
            Bridge.autoInstallMode,
        ) { Bridge.autoInstallMode = it; refresh() }, matchWrap(4))
        auto.addView(note(tr("🔒 לבטיחותך: כל אפליקציה יכולה לפתוח קובץ בהבוס, ולכן בלי שאלה מותקן רק עדכון לגרסה חדשה יותר של אפליקציה שכבר מותקנת (Android מוודא שהוא מאותו מפתח), ובלי האפשרויות המסוכנות (שנמוך, חבילות בדיקה, כל ההרשאות). אפליקציה חדשה — תמיד עם שאלה. פועל רק אחרי צימוד.", "🔒 For your safety: any app can open a file in The Boss, so only an update to a newer version of an installed app (Android verifies it's from the same developer) installs without asking, and without the risky options (downgrade, test packages, all permissions). A new app always asks. Works only after pairing.")), matchWrap())

        // After install
        val after = card(page)
        after.addView(heading(tr("אחרי התקנה", "After install")), matchWrap())
        after.addView(body(tr("פתיחת האפליקציה שהותקנה:", "Opening the installed app:")), matchWrap(4))
        after.addView(radios(
            listOf("ask" to tr("לשאול אם לפתוח", "Ask whether to open"), "auto" to tr("לפתוח מיד", "Open right away"), "never" to tr("לא לפתוח", "Don't open")),
            Bridge.openAfterInstall,
        ) { Bridge.openAfterInstall = it }, matchWrap(4))
        after.addView(checkBox(tr("התראה על סיום התקנה כשאפליקציית הבוס ברקע", "Notify when an install finishes while The Boss is in the background"), Bridge.installNotifications) {
            Bridge.installNotifications = it
        }, matchWrap(0))

        // Pairing & connection
        val pair = card(page)
        pair.addView(heading(tr("צימוד וחיבור", "Pairing & connection")), matchWrap())
        pair.addView(checkBox(tr("הגדרה אוטומטית אחרי חיבור מוצלח (הרשאות, נגישות, סוללה, עזר Wi‑Fi)", "Automatic setup after a successful connection (permissions, accessibility, battery, Wi‑Fi helper)"), Bridge.autoSetupEnabled) {
            Bridge.autoSetupEnabled = it
        }, matchWrap(0))
        pair.addView(note(tr("אחרי חיבור ADB ראשון הבוס נותן לעצמו דרך ADB: הדלקה אוטומטית של ניפוי באגים, התראות, התקנת אפליקציות, החרגה מחיסכון בסוללה ושירות הנגישות — ומתקין את עזר ה־Wi‑Fi.", "After the first ADB connection The Boss grants itself through ADB: automatic turn-on of debugging, notifications, app installs, battery optimization exemption and the accessibility service — and installs the Wi‑Fi helper.")), matchWrap(12))
        pair.addView(checkBox(tr("צימוד אוטומטי: לזהות את קוד הצימוד מהמסך (נגישות)", "Automatic pairing: detect the pairing code from the screen (accessibility)"), Bridge.accessibilityAutoPair) {
            Bridge.accessibilityAutoPair = it
            refresh()
        }, matchWrap(0))
        a11yState = note("")
        pair.addView(a11yState, matchWrap(4))
        a11yButton = button(tr("הפעל בהגדרות הנגישות", "Turn on in Accessibility settings"), false) {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: ActivityNotFoundException) {}
        }
        pair.addView(a11yButton, matchWrap(12))
        pair.addView(checkBox(tr("להדליק Wi‑Fi כשצריך: לפתוח את חלונית ה־Wi‑Fi אוטומטית ולהמשיך כשהוא מתחבר", "Turn Wi‑Fi on when needed: open the Wi‑Fi panel automatically and continue once it connects"), Bridge.autoWifi) {
            Bridge.autoWifi = it
        }, matchWrap(0))
        wifiHelperState = note("")
        pair.addView(wifiHelperState, matchWrap(4))
        wifiHelperButton = button(tr("התקן את עזר ה־Wi‑Fi", "Install the Wi‑Fi helper"), false) {
            HelperInstaller.start(this)
        }
        pair.addView(wifiHelperButton, matchWrap(12))
        pair.addView(checkBox(tr("להדליק ניפוי באגים אלחוטי אוטומטית כשהוא כבוי", "Turn wireless debugging on automatically when it's off"), Bridge.autoEnableWireless) {
            Bridge.autoEnableWireless = it
            refresh()
        }, matchWrap(0))
        wirelessState = note("")
        pair.addView(wirelessState, matchWrap(12))
        pair.addView(checkBox(tr("ADB קבוע — לעבוד גם כש־Wi‑Fi כבוי (פורט 5555, עד הפעלה מחדש, מתחדש אוטומטית)", "Persistent ADB — works even when Wi‑Fi is off (port 5555, until restart, renewed automatically)"), Bridge.keepAdbWithoutWifi) {
            if (it) Bridge.enableKeepAdb() else Bridge.disableKeepAdb()
        }, matchWrap(0))
        pair.addView(note(tr("ההפעלה דורשת חיבור ADB פעיל. בפעם הראשונה סמן \"אפשר תמיד\" בחלון \"לאפשר ניפוי באגים?\".", "Turning it on needs an active ADB connection. The first time check \"Always allow\" in the \"Allow debugging?\" window.")), matchWrap(12))
        pair.addView(button(tr("אפס פורט חיבור שמור", "Reset saved connection port"), false) {
            Bridge.savedConnectPort = 0
            toast(tr("הפורט נמחק; הוא יאותר מחדש בחיבור הבא.", "The port was cleared; it will be found again on the next connection."))
        }, matchWrap())
        pair.addView(button(tr("שכח צימוד (מחק מפתח ADB)", "Forget pairing (delete ADB key)"), false) {
            AlertDialog.Builder(this)
                .setTitle(tr("לשכוח את הצימוד?", "Forget the pairing?"))
                .setMessage(tr("מפתח ה־ADB של הבוס יימחק ויהיה צורך לצמד מחדש. מומלץ גם למחוק את \"apkbridge\" מרשימת המכשירים המותאמים בהגדרות ניפוי הבאגים האלחוטי.", "The Boss's ADB key will be deleted and you'll need to pair again. It's also recommended to remove \"apkbridge\" from the paired devices list in the Wireless debugging settings."))
                .setPositiveButton(tr("מחק", "Delete")) { _, _ -> Bridge.forgetPairing(); toast(tr("הצימוד נמחק.", "Pairing removed.")) }
                .setNegativeButton(tr("ביטול", "Cancel"), null)
                .show()
        }, matchWrap())

        // Display
        val display = card(page)
        display.addView(heading(tr("תצוגה", "Display")), matchWrap())
        display.addView(checkBox(tr("להציג את היומן פתוח", "Show the log expanded"), Bridge.logExpanded) { Bridge.logExpanded = it }, matchWrap(0))
        display.addView(button(tr("👋 הצג את מסך הפתיחה", "👋 Show the welcome screens"), false) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }, matchWrap())

        val scroll = ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            isFillViewport = true
            addView(page)
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BACKGROUND) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(BottomNav.build(this, BottomNav.Tab.SETTINGS) { BottomNav.navigate(this, it) })
        Compat.applySystemBarInsets(this, root, includeIme = false)
        setContentView(root)
    }

    private fun refresh() {
        if (!::a11yState.isInitialized) return
        val enabled = PairingAccessibilityService.isEnabled(this)
        // Already enabled → no button to enable it.
        a11yButton.visibility = if (enabled || !Bridge.accessibilityAutoPair) View.GONE else View.VISIBLE
        a11yState.text = when {
            !Bridge.accessibilityAutoPair -> tr("כבוי באפליקציית הבוס (השירות לא יפעל גם אם מופעל בנגישות).", "Off in The Boss (the service won't work even if enabled in Accessibility).")
            enabled -> tr("✔ שירות הנגישות מופעל.", "✔ The accessibility service is on.")
            else -> tr("שירות הנגישות עדיין לא מופעל: הגדרות נגישות ← הבוס ← הפעל.", "The accessibility service isn't on yet: Accessibility settings → The Boss → turn on.")
        }
        wirelessState.text = if (Bridge.canWriteSecureSettings()) {
            tr("✔ להרשאה הדרושה יש אישור.", "✔ The required permission is granted.")
        } else {
            tr("ההרשאה תינתן אוטומטית בחיבור ה־ADB הבא (הגדרה אוטומטית).", "The permission will be granted automatically on the next ADB connection (automatic setup).")
        }
        val helper = Bridge.isWifiHelperInstalled()
        wifiHelperState.text = when {
            helper -> tr("✔ עזר ה־Wi‑Fi מותקן: Wi‑Fi כבוי יודלק לבד, בלי הקשה.", "✔ The Wi‑Fi helper is installed: Wi‑Fi that's off is turned on by itself, no tap needed.")
            Bridge.isWifiHelperBundled() -> tr("בלי עזר ה־Wi‑Fi תיפתח חלונית Wi‑Fi שבה צריך להקיש פעם אחת. עזר ה־Wi‑Fi הוא אפליקציה קטנה (מובנית באפליקציית הבוס) שבנויה ל־Android 9, ולכן Android עדיין מרשה לה להדליק Wi‑Fi — כמו Termux:API. ההתקנה חד־פעמית דרך מתקין האפליקציות של Android — לא נדרש ניפוי באגים.", "Without the Wi‑Fi helper a Wi‑Fi panel opens where you tap once. The Wi‑Fi helper is a small app (built into The Boss) made for Android 9, so Android still lets it turn Wi‑Fi on — like Termux:API. It's installed once through Android's app installer — no debugging needed.")
            else -> tr("עזר ה־Wi‑Fi לא נכלל בגרסה הזו.", "The Wi‑Fi helper isn't included in this version.")
        }
        wifiHelperButton.visibility = if (!helper && Bridge.isWifiHelperBundled()) View.VISIBLE else View.GONE
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

    private fun card(parent: LinearLayout) = Ui.card(parent)

    private fun heading(value: String) = Ui.heading(this, value)

    private fun body(value: String) = Ui.body(this, value)

    private fun note(value: String) = Ui.note(this, value)

    private fun button(textValue: String, primary: Boolean, action: () -> Unit) = Ui.button(this, textValue, primary, action)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun matchWrap(bottom: Int = 9) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    // endregion

    companion object {
        private val INK = Theme.INK
        private val MUTED = Theme.MUTED
        private val BACKGROUND = Theme.BACKGROUND
    }
}
