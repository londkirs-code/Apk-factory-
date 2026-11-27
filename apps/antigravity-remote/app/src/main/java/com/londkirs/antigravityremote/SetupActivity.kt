package com.londkirs.antigravityremote

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Interactive setup guide. Every step does the work itself (install, start, sign in, pick a model)
 * and shows whether it worked; on failure it explains what to do and offers a retry.
 */
class SetupActivity : BaseActivity() {
    companion object { const val EXTRA_STEP = "step" }

    private enum class St { TODO, RUNNING, DONE, FAILED }

    private val main by lazy { Handler(mainLooper) }
    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var dots: LinearLayout
    private var installState = St.TODO
    private var installLabel = ""
    private var installFraction = -1f
    private var installError: String? = null
    private var signState = St.TODO
    private var signLabel = ""
    private var signError: String? = null
    private var models: List<HubClient.ModelChoice> = emptyList()
    private var modelsLoading = false
    private var focus = -1
    private var progress: ProgressLine? = null
    private var progressLabel: TextView? = null

    private val prefs by lazy { getSharedPreferences("setup", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = Ui.TopBar(this, tr("מדריך הפעלה", "Setup guide"), back = Settings.setupDone(this))
        top.action(R.drawable.ic_computer, tr("חיבור למחשב במקום", "Connect to a computer instead")) { remoteOnly() }
        col.addView(top)
        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4)) }
        col.addView(dots)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        scroll = ScrollView(this).apply { addView(box) }
        col.addView(scroll, lp(MATCH, 0, 1f))
        setContentView(col)
        focus = intent.getIntExtra(EXTRA_STEP, -1)
        if (AgyInstaller.isInstalled(this)) installState = St.DONE
        render()
        if (installState == St.DONE) checkSignIn(silent = true)
    }

    // ---- state ----------------------------------------------------------------------------------

    private val deviceOk get() = AgyInstaller.isSupportedDevice()
    private val modelDone get() = Settings.model(this) != null || prefs.getBoolean("model_skipped", false)

    private fun current(): Int = when {
        !deviceOk -> 0
        installState != St.DONE -> 1
        signState != St.DONE -> 2
        !modelDone -> 3
        else -> 4
    }

    private fun stateOf(i: Int): St = when (i) {
        0 -> if (deviceOk) St.DONE else St.FAILED
        1 -> installState
        2 -> signState
        3 -> if (modelDone) St.DONE else St.TODO
        else -> if (current() == 4) St.TODO else St.TODO
    }

    // ---- rendering ------------------------------------------------------------------------------

    private fun render() {
        val cur = current()
        dots.removeAllViews()
        for (i in 0..4) dots.addView(View(this).apply {
            background = Ui.round(if (i <= cur) T.primary else T.surface3, dpf(4f))
        }, lp(if (i == cur) dp(26) else dp(8), dp(8)).apply { setMargins(dp(3), 0, dp(3), 0) })
        box.removeAllViews()
        progress = null; progressLabel = null
        box.addView(Ui.text(this, tr("בואו נפעיל את Antigravity בטלפון", "Let's get Antigravity running on your phone"), 22f, T.text, bold = true).apply { setPadding(dp(4), dp(8), dp(4), dp(4)) })
        box.addView(Ui.text(this, tr("חמישה צעדים קצרים. כל צעד מתבצע בלחיצה אחת, ואפשר לצאת ולחזור בכל שלב.",
            "Five short steps. Each one is a single tap, and you can leave and come back at any time."), 15f, T.muted).apply { setPadding(dp(4), 0, dp(4), dp(12)); setLineSpacing(0f, 1.2f) })
        val titles = listOf(
            tr("הטלפון מתאים", "Your phone is compatible"),
            tr("התקנת הסוכן", "Install the agent"),
            tr("הפעלה והתחברות ל־Google", "Start and sign in with Google"),
            tr("בחירת מודל", "Choose a model"),
            tr("מתחילים לדבר", "Start chatting"),
        )
        titles.forEachIndexed { i, t -> box.addView(stepCard(i, t, i == cur || i == focus)) }
        if (focus >= 0) { val f = focus; focus = -1; scroll.post { box.getChildAt(2 + f)?.let { scroll.smoothScrollTo(0, it.top) } } }
    }

    private fun stepCard(i: Int, title: String, open: Boolean): View {
        val st = stateOf(i)
        val cur = current()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(if (open) T.surface else T.surface2, dpf(22f), if (open) T.primary else T.divider, dp(if (open) 2 else 1))
            elevation = if (open) dpf(3f) else 0f
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badge = FrameLayout(this).apply {
            background = Ui.circle(when (st) { St.DONE -> T.success; St.FAILED -> T.danger; else -> if (i <= cur) T.primary else T.surface3 })
        }
        if (st == St.RUNNING) badge.addView(Spinner(this, 20, T.onPrimary), flp(dp(20), dp(20), Gravity.CENTER))
        else if (st == St.DONE) badge.addView(Ui.icon(this, R.drawable.ic_check, T.onPrimary, 18), flp(dp(18), dp(18), Gravity.CENTER))
        else if (st == St.FAILED) badge.addView(Ui.icon(this, R.drawable.ic_close, T.onPrimary, 18), flp(dp(18), dp(18), Gravity.CENTER))
        else badge.addView(Ui.text(this, (i + 1).toString(), 15f, if (i <= cur) T.onPrimary else T.muted, bold = true).apply { gravity = Gravity.CENTER }, flp(MATCH, MATCH))
        head.addView(badge, lp(dp(34), dp(34)).apply { marginEnd = dp(12) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Ui.text(this, title, 17f, if (i <= cur || st == St.DONE) T.text else T.muted, bold = true))
        val summary = summary(i, st)
        if (summary != null) texts.addView(Ui.text(this, summary, 13.5f, if (st == St.FAILED) T.danger else T.muted).apply { setPadding(0, dp(2), 0, 0) })
        head.addView(texts, lp(0, WRAP, 1f))
        card.addView(head)
        if (!open) card.setOnClickListener { focus = i; render() }
        if (open) content(i, st, card)
        return LinearLayout(this).apply { setPadding(0, dp(6), 0, dp(6)); addView(card, lp(MATCH, WRAP)) }
    }

    private fun summary(i: Int, st: St): String? = when (i) {
        0 -> if (deviceOk) tr("מעבד arm64 ומקום פנוי", "arm64 processor and free space") else tr("הטלפון לא נתמך", "This phone is not supported")
        1 -> when (st) {
            St.DONE -> tr("מותקן", "Installed") + (AgyInstaller.installedVersion(this)?.let { " · $it" } ?: "")
            St.RUNNING -> installLabel
            St.FAILED -> tr("ההתקנה נכשלה", "Installation failed")
            else -> tr("כ־100MB, מהשרת הרשמי של Google", "About 100MB, from Google's official server")
        }
        2 -> when (st) {
            St.DONE -> tr("מחובר", "Signed in")
            St.RUNNING -> signLabel
            St.FAILED -> signError?.let { tr("נכשל", "Failed") }
            else -> null
        }
        3 -> if (st == St.DONE) (ChatEngine.currentModel(this)?.name ?: tr("ברירת המחדל של החשבון", "Account default")) else null
        else -> null
    }

    private fun explain(card: LinearLayout, text: String) = card.addView(Ui.text(this, text, 15f, T.muted).apply {
        setLineSpacing(0f, 1.2f); setPadding(0, dp(12), 0, dp(4))
    })

    private fun action(card: LinearLayout, label: String, style: Ui.Style = Ui.Style.PRIMARY, icon: Int = 0, f: () -> Unit) =
        card.addView(Ui.button(this, label, style, icon) { f() }, lp(MATCH, WRAP).apply { topMargin = dp(10) })

    private fun content(i: Int, st: St, card: LinearLayout) {
        when (i) {
            0 -> if (deviceOk) {
                val free = filesDir.usableSpace / 1048576
                explain(card, tr("הסוכן רץ ישירות בטלפון שלכם. יש מספיק מקום פנוי (${free}MB).", "The agent runs directly on your phone. There is enough free space (${free}MB)."))
            } else {
                explain(card, tr("Antigravity רץ רק בטלפונים עם מעבד 64 ביט (arm64). אפשר עדיין להתחבר ל־Antigravity שרץ במחשב.",
                    "Antigravity only runs on 64-bit (arm64) phones. You can still connect to Antigravity running on a computer."))
                action(card, tr("חיבור למחשב", "Connect to a computer"), icon = R.drawable.ic_computer) { remoteOnly() }
            }
            1 -> when (st) {
                St.RUNNING -> {
                    val label = Ui.text(this, installLabel, 14f, T.muted).apply { setPadding(0, dp(12), 0, dp(8)) }
                    val bar = ProgressLine(this).apply { fraction = installFraction }
                    card.addView(label); card.addView(bar)
                    progress = bar; progressLabel = label
                }
                St.FAILED -> {
                    explain(card, tr("משהו השתבש בהורדה. בדקו שיש חיבור לאינטרנט ונסו שוב.", "Something went wrong with the download. Check your internet connection and try again."))
                    action(card, tr("ניסיון נוסף", "Try again"), icon = R.drawable.ic_refresh) { install() }
                    action(card, tr("פרטי השגיאה", "Error details"), Ui.Style.TEXT) { Ui.info(this, tr("פרטי השגיאה", "Error details"), installError ?: "", mono = true) }
                }
                St.DONE -> {
                    explain(card, tr("הסוכן מותקן. אפשר לבדוק אם יש גרסה חדשה.", "The agent is installed. You can check for a newer version."))
                    action(card, tr("עדכון לגרסה האחרונה", "Update to the latest version"), Ui.Style.TONAL, R.drawable.ic_download) { install() }
                }
                else -> {
                    explain(card, tr("מורידים את גרסת ה־Android הרשמית של Antigravity מ־Google ובודקים שהקובץ תקין. זה לוקח דקה או שתיים.",
                        "We download Google's official Android build of Antigravity and verify it. It takes a minute or two."))
                    action(card, tr("התקנה", "Install"), icon = R.drawable.ic_download) { install() }
                }
            }
            2 -> when (st) {
                St.RUNNING -> {
                    explain(card, signLabel)
                    card.addView(LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0); addView(Spinner(this@SetupActivity, 28)) }, lp(MATCH, WRAP))
                }
                St.FAILED -> {
                    explain(card, signError ?: "")
                    action(card, tr("ניסיון נוסף", "Try again"), icon = R.drawable.ic_refresh) { startAndSignIn() }
                    action(card, tr("פתיחת המסוף לפרטים", "Open the terminal for details"), Ui.Style.TEXT) { startActivity(Intent(this, TerminalActivity::class.java)) }
                }
                St.DONE -> explain(card, tr("הסוכן פועל ומחובר לחשבון Google שלכם.", "The agent is running and signed in to your Google account."))
                else -> {
                    explain(card, tr("מפעילים את הסוכן ברקע ומתחברים עם חשבון Google. Chrome ייפתח לבחירת החשבון, ואחר כך חוזרים לכאן.",
                        "We start the agent in the background and sign in with Google. Chrome opens to pick the account; then come back here."))
                    action(card, tr("הפעלה והתחברות", "Start and sign in"), icon = R.drawable.ic_account) { startAndSignIn() }
                }
            }
            3 -> {
                explain(card, tr("המודל הוא ה\"מוח\" שעונה לכם. אפשר להחליף בכל רגע מתוך השיחה (✦).", "The model is the \"brain\" that answers you. You can switch at any time from the chat (✦)."))
                if (models.isEmpty()) {
                    if (!modelsLoading) loadModels()
                    card.addView(LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0); addView(Spinner(this@SetupActivity, 26)) }, lp(MATCH, WRAP))
                } else {
                    val cur = ChatEngine.currentModel(this)
                    models.forEach { m ->
                        val sub = m.quota?.let { tr("נותרו ${(it * 100).toInt()}% מהמכסה", "${(it * 100).toInt()}% of quota left") }
                        card.addView(Ui.row(this, R.drawable.ic_sparkle, m.name, sub,
                            Ui.icon(this, if (m == cur && Settings.model(this) != null) R.drawable.ic_check_circle else R.drawable.ic_circle, if (m == cur) T.primary else T.faint),
                            iconTint = T.primary) {
                            Settings.setModel(this, m.value.toString())
                            Thread { runCatching { HubClient.rememberModel(m.value) } }.start()
                            render()
                        }.apply { setPadding(dp(4), dp(8), dp(4), dp(8)) })
                    }
                }
                action(card, tr("ברירת המחדל מספיקה לי", "The default is fine"), Ui.Style.TEXT) { prefs.edit().putBoolean("model_skipped", true).apply(); render() }
            }
            4 -> {
                explain(card, tr("הכול מוכן! נסו אחת מההצעות או פתחו שיחה משלכם.", "All set! Try one of these or start your own chat."))
                val ideas = listOf(
                    tr("מה אתה יודע לעשות?", "What can you do?"),
                    tr("צור לי אפליקציית Android פשוטה של רשימת קניות", "Create a simple Android shopping-list app"),
                    tr("כתוב סקריפט Python שמסדר קבצים לפי תאריך", "Write a Python script that sorts files by date"),
                )
                ideas.forEach { idea ->
                    card.addView(Ui.row(this, R.drawable.ic_chat, idea, null, Ui.icon(this, R.drawable.ic_send, T.primary, 20), iconTint = T.primary) {
                        finishSetup(); val c = ChatStore.create(this); ChatActivity.open(this, c.id, text = idea)
                    }.apply { background = Ui.ripple(Ui.round(T.surface2, dpf(14f)), dpf(14f)); setPadding(dp(14), dp(8), dp(14), dp(8)) },
                        lp(MATCH, WRAP).apply { topMargin = dp(8) })
                }
                action(card, tr("לרשימת השיחות", "Go to my chats"), icon = R.drawable.ic_chevron) { finishSetup(); startActivity(Intent(this, MainActivity::class.java)); finish() }
            }
        }
    }

    // ---- actions --------------------------------------------------------------------------------

    private fun install() {
        installState = St.RUNNING; installLabel = tr("בודק מה הגרסה האחרונה…", "Checking the latest version…"); installFraction = -1f
        render()
        Thread {
            try {
                val release = AgyInstaller.install(this) { stage, done, total ->
                    main.post {
                        installLabel = when (stage) {
                            AgyInstaller.STAGE_MANIFEST -> tr("בודק מה הגרסה האחרונה…", "Checking the latest version…")
                            AgyInstaller.STAGE_DOWNLOAD -> tr("מוריד… ${done / 1048576}MB", "Downloading… ${done / 1048576}MB") + if (total > 0) " / ${total / 1048576}MB" else ""
                            AgyInstaller.STAGE_VERIFY -> tr("בודק שהקובץ תקין…", "Verifying the file…")
                            else -> tr("מחלץ…", "Extracting…")
                        }
                        installFraction = if (stage == AgyInstaller.STAGE_DOWNLOAD && total > 0) done.toFloat() / total else -1f
                        progressLabel?.text = installLabel
                        progress?.fraction = installFraction
                    }
                }
                main.post { installState = St.DONE; snack(tr("הותקן: ", "Installed: ") + release.version); render(); startAndSignIn() }
            } catch (e: Exception) {
                main.post { installState = St.FAILED; installError = e.message ?: e.toString(); render() }
            }
        }.start()
    }

    private fun checkSignIn(silent: Boolean) {
        HubClient.attach(this)
        Thread {
            val alive = HubClient.isAlive()
            val ok = alive && runCatching { HubClient.hasValidAuth() }.getOrDefault(false)
            main.post { if (ok) { signState = St.DONE; render() } else if (!silent) startAndSignIn() }
        }.start()
    }

    private fun startAndSignIn() {
        signState = St.RUNNING; signLabel = tr("מפעיל את הסוכן… (עד דקה בפעם הראשונה)", "Starting the agent… (up to a minute the first time)")
        render()
        HubClient.ensureRunning(this) { ok ->
            if (!ok) {
                signState = St.FAILED
                signError = tr("הסוכן לא עלה. לפעמים עוזר לנסות שוב; אם זה חוזר, פתחו את המסוף כדי לראות מה קרה.",
                    "The agent did not start. Trying again often helps; if it keeps failing, open the terminal to see what happened.")
                render(); return@ensureRunning
            }
            ChatWatcher.start(this)
            Thread {
                if (runCatching { HubClient.hasValidAuth() }.getOrDefault(false)) { main.post { signState = St.DONE; render() }; return@Thread }
                main.post { signLabel = tr("נפתח Chrome. בחרו חשבון Google, אשרו, וחזרו לכאן.", "Chrome is opening. Pick a Google account, approve, and come back here."); render() }
                Thread { runCatching { HubClient.login() } }.start()
                for (k in 0 until 150) {
                    Thread.sleep(2000)
                    if (runCatching { HubClient.hasValidAuth() }.getOrDefault(false)) {
                        main.post { signState = St.DONE; snack(tr("מחובר!", "Signed in!")); render() }
                        return@Thread
                    }
                }
                main.post {
                    signState = St.FAILED
                    signError = tr("ההתחברות לא הושלמה. נסו שוב ובחרו חשבון ב־Chrome.", "Sign-in was not completed. Try again and choose an account in Chrome.")
                    render()
                }
            }.start()
        }
    }

    private fun loadModels() {
        modelsLoading = true
        Thread {
            val l = ChatEngine.loadModels(force = true)
            main.post { modelsLoading = false; models = l; if (l.isEmpty()) prefs.edit().putBoolean("model_skipped", true).apply(); render() }
        }.start()
    }

    private fun finishSetup() = Settings.setSetupDone(this, true)

    private fun remoteOnly() {
        finishSetup()
        startActivity(Intent(this, ConnectionsActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (signState == St.RUNNING) return
        if (installState == St.DONE && signState != St.DONE) checkSignIn(silent = true)
    }
}
