package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 💾 Backup & restore: choose what to back up (or everything), and restore it later. */
class BackupActivity : Activity() {
    private val backupBoxes = linkedMapOf<String, CheckBox>()
    private val backupFileBoxes = linkedMapOf<String, CheckBox>()
    private lateinit var restoreCard: LinearLayout
    private lateinit var restoreInfo: TextView
    private lateinit var restoreOptions: LinearLayout
    private lateinit var restoreButton: Button
    private val restoreBoxes = linkedMapOf<String, CheckBox>()
    private val restoreFileBoxes = linkedMapOf<String, CheckBox>()
    private var inspection: BackupEngine.Inspection? = null
    private lateinit var strip: StatusStrip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
    }

    override fun onStart() {
        super.onStart()
        strip.attach()
    }

    override fun onStop() {
        strip.detach()
        super.onStop()
    }

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
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(20))
        }
        page.addView(TextView(this).apply {
            text = "💾 גיבוי ושחזור"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, wrap(12))

        // ── Backup
        val backup = card(page)
        backup.addView(heading("גיבוי"), wrap(4))
        backup.addView(note("בחר מה לגבות. הגיבוי נשמר בתיקייה שתבחר (בטלפון, בכרטיס SD או בכונן מחובר), בתוך תיקייה חדשה עם התאריך."), wrap(8))
        backupBoxes[BackupEngine.APPS] = check(backup, "📦 אפליקציות (כולל ההרשאות שלהן)", true)
        backupBoxes[BackupEngine.CONTACTS] = check(backup, "👤 אנשי קשר", true)
        backupBoxes[BackupEngine.CALLS] = check(backup, "📞 יומן שיחות", true)
        backupBoxes[BackupEngine.SMS] = check(backup, "💬 הודעות SMS", true)
        backupBoxes[BackupEngine.SETTINGS] = check(backup, "⚙ הגדרות טלפון", true)
        backup.addView(TextView(this).apply {
            text = "📁 קבצים"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
            setPadding(0, dp(8), 0, dp(2))
        })
        for (set in BackupEngine.FILE_SETS) backupFileBoxes[set.key] = check(backup, set.label, set.key in setOf("dcim", "documents", "whatsapp"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("סמן הכול (גיבוי מלא)", false) { setAll(true) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        row.addView(button("נקה סימון", false) { setAll(false) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        backup.addView(row, wrap(8))
        backup.addView(button("▶  גבה עכשיו", true) { startBackup() }, wrap(8))
        backup.addView(box(
            "💡 מה לא נכלל: הנתונים הפנימיים של אפליקציות (כניסה לחשבון, שמירות במשחקים, הגדרות בתוך אפליקציות) וסיסמאות Wi‑Fi — Android חוסם אותם בלי Root. " +
                "וואטסאפ: נשמר הגיבוי המקומי שלה; כדי שיהיה עדכני, גבה קודם בתוך וואטסאפ ← הגדרות ← צ'אטים ← גיבוי."
        ), wrap(0))

        // ── Restore
        restoreCard = card(page)
        restoreCard.addView(heading("שחזור"), wrap(4))
        restoreCard.addView(note("בחר תיקיית גיבוי (שמתחילה ב־TheBoss-backup), או את התיקייה שמכילה את הגיבויים — וייבחר החדש ביותר."), wrap(8))
        restoreCard.addView(button("📂 בחר תיקיית גיבוי", false) { pickFolder(REQUEST_RESTORE) }, wrap(8))
        restoreInfo = body("")
        restoreInfo.visibility = View.GONE
        restoreCard.addView(restoreInfo, wrap(6))
        restoreOptions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        restoreCard.addView(restoreOptions, wrap(6))
        restoreButton = button("▶  שחזר", true) { startRestore() }.apply { visibility = View.GONE }
        restoreCard.addView(restoreButton, wrap(0))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BACKGROUND)
            addView(page)
        }
        strip = StatusStrip(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BACKGROUND) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(strip.view)
        root.addView(BottomNav.build(this, BottomNav.Tab.BACKUP) { BottomNav.navigate(this, it) })
        Compat.applySystemBarInsets(this, root, includeIme = false)
        setContentView(root)
    }

    private fun setAll(on: Boolean) {
        backupBoxes.values.forEach { it.isChecked = on }
        backupFileBoxes.values.forEach { it.isChecked = on }
    }

    // region backup

    private fun startBackup() {
        val items = backupBoxes.filterValues { it.isChecked }.keys
        val files = backupFileBoxes.filterValues { it.isChecked }.keys
        if (items.isEmpty() && files.isEmpty()) {
            toast("סמן לפחות דבר אחד לגיבוי.")
            return
        }
        if (Bridge.state.busy) { toast("פעולה אחרת עדיין רצה — נסה שוב בעוד רגע."); return }
        pickFolder(REQUEST_BACKUP)
    }

    private fun runBackup(tree: Uri) {
        val items = backupBoxes.filterValues { it.isChecked }.keys
        val files = backupFileBoxes.filterValues { it.isChecked }.keys
        val started = BackupEngine.backup(this, tree, items, files) { result ->
            if (result != null && !isFinishing) {
                AlertDialog.Builder(this).setTitle("✔ הגיבוי הסתיים").setMessage(result.summary.substringAfter('\n'))
                    .setPositiveButton("סגור", null).show()
            }
        }
        if (!started) toast("פעולה אחרת עדיין רצה — נסה שוב בעוד רגע.")
    }

    // endregion

    // region restore

    private fun inspect(tree: Uri) {
        restoreInfo.visibility = View.VISIBLE
        restoreInfo.text = "קורא את הגיבוי…"
        Bridge.scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { BackupEngine.inspect(this@BackupActivity, tree) } }
            runOnUiThread {
                result.onSuccess { showRestoreOptions(it) }.onFailure {
                    inspection = null
                    restoreOptions.removeAllViews()
                    restoreButton.visibility = View.GONE
                    restoreInfo.text = "⚠️ ${it.message ?: "לא ניתן לקרוא את הגיבוי."}"
                }
            }
        }
    }

    private fun showRestoreOptions(ins: BackupEngine.Inspection) {
        inspection = ins
        restoreInfo.text = ins.description()
        restoreOptions.removeAllViews()
        restoreBoxes.clear()
        restoreFileBoxes.clear()
        val counts = ins.manifest.optJSONObject("items")
        fun count(key: String) = counts?.optInt(key, -1)?.takeIf { it >= 0 }?.let { " ($it)" } ?: ""
        if (BackupEngine.APPS in ins.available) restoreBoxes[BackupEngine.APPS] = check(restoreOptions, "📦 אפליקציות${count(BackupEngine.APPS)}", true)
        if (BackupEngine.CONTACTS in ins.available) restoreBoxes[BackupEngine.CONTACTS] = check(restoreOptions, "👤 אנשי קשר${count(BackupEngine.CONTACTS)}", true)
        if (BackupEngine.CALLS in ins.available) restoreBoxes[BackupEngine.CALLS] = check(restoreOptions, "📞 יומן שיחות${count(BackupEngine.CALLS)}", true)
        if (BackupEngine.SMS in ins.available) restoreBoxes[BackupEngine.SMS] = check(restoreOptions, "💬 הודעות SMS${count(BackupEngine.SMS)}", true)
        if (BackupEngine.SETTINGS in ins.available) restoreBoxes[BackupEngine.SETTINGS] = check(restoreOptions, "⚙ הגדרות טלפון", true)
        for (key in ins.fileSets) {
            val label = BackupEngine.FILE_SETS.firstOrNull { it.key == key }?.label ?: key
            restoreFileBoxes[key] = check(restoreOptions, label, true)
        }
        if (restoreBoxes.isEmpty() && restoreFileBoxes.isEmpty()) {
            restoreInfo.text = ins.description() + "\n⚠️ הגיבוי ריק."
            restoreButton.visibility = View.GONE
        } else {
            restoreButton.visibility = View.VISIBLE
        }
    }

    private fun startRestore() {
        val ins = inspection ?: return
        val items = restoreBoxes.filterValues { it.isChecked }.keys
        val files = restoreFileBoxes.filterValues { it.isChecked }.keys
        if (items.isEmpty() && files.isEmpty()) { toast("סמן לפחות דבר אחד לשחזור."); return }
        AlertDialog.Builder(this)
            .setTitle("לשחזר?")
            .setMessage(
                "• אפליקציות שכבר מותקנות בגרסה זהה או חדשה יותר לא יותקנו מחדש — רק ההרשאות שלהן יוחזרו.\n" +
                    "• שיחות והודעות שכבר נמצאות בטלפון לא ישוכפלו.\n" +
                    "• קבצים בשם זהה יוחלפו בגרסה מהגיבוי.\n" +
                    "• אנשי קשר: בסוף ייפתח מסך הייבוא של אפליקציית אנשי הקשר — שם מאשרים."
            )
            .setPositiveButton("שחזר") { _, _ ->
                val started = BackupEngine.restore(this, ins, items, files) { result ->
                    if (result == null || isFinishing) return@restore
                    result.contactsVcf?.let { importContacts(it) }
                    AlertDialog.Builder(this).setTitle("✔ השחזור הסתיים").setMessage(result.summary.substringAfter('\n'))
                        .setPositiveButton("סגור", null).show()
                }
                if (!started) toast("פעולה אחרת עדיין רצה — נסה שוב בעוד רגע.")
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    /** Contacts are imported by the phone's own Contacts app (it asks where to save them). */
    private fun importContacts(vcf: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(vcf, "text/x-vcard")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure {
            toast("לא נמצאה אפליקציית אנשי קשר לייבוא. הקובץ contacts.vcf נמצא בתיקיית הגיבוי.")
        }
    }

    // endregion

    // region folder picker

    private fun pickFolder(request: Int) {
        runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), request) }
            .onFailure { toast("לא נמצא בוחר תיקיות.") }
    }

    @Deprecated("Framework Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val tree = data?.data
        if (resultCode != RESULT_OK || tree == null) return
        runCatching {
            contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        when (requestCode) {
            REQUEST_BACKUP -> runBackup(tree)
            REQUEST_RESTORE -> inspect(tree)
        }
    }

    // endregion

    // region views

    private fun card(parent: LinearLayout) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(0xFFFFFFFF.toInt())
            setStroke(dp(1), 0xFFF2E2D2.toInt())
        }
        parent.addView(this, wrap(14))
    }

    private fun heading(t: String) = TextView(this).apply {
        text = t; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK)
    }

    private fun body(t: String) = TextView(this).apply { text = t; textSize = 14.5f; setTextColor(0xFF55483E.toInt()) }

    private fun note(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(MUTED) }

    private fun box(t: String) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(0xFF3F342B.toInt())
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFFFFF6D6.toInt()); setStroke(dp(1), 0xFFF2D58A.toInt()) }
    }

    private fun check(parent: LinearLayout, label: String, checked: Boolean) = CheckBox(this).apply {
        text = label
        textSize = 15f
        isChecked = checked
        setTextColor(INK)
        parent.addView(this, wrap(0))
    }

    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        stateListAnimator = null
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF75451F.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(if (primary) ACCENT else 0xFFFFE7D1.toInt()) }
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wrap(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    // endregion

    companion object {
        private const val REQUEST_BACKUP = 6101
        private const val REQUEST_RESTORE = 6102
        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val ACCENT = 0xFFF09A45.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()
    }
}
