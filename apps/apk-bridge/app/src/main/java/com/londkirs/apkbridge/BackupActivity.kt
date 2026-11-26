package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 💾 Backup & restore. Pick exactly what goes in: which apps, which storage (internal / memory
 * card), which folders (ready-made or any folder), Termux, contacts, calls, SMS and settings.
 */
class BackupActivity : Activity() {
    // ── backup state
    private val backupBoxes = linkedMapOf<String, CheckBox>()
    private var backupApps: Set<String>? = null            // null = all user apps
    private lateinit var backupAppsButton: Button
    private lateinit var foldersBox: LinearLayout
    private val folders = mutableListOf<Pair<BackupEngine.FileSet, CheckBox>>()
    private lateinit var termuxBackupStatus: LinearLayout

    // ── restore state
    private lateinit var restoreInfo: TextView
    private lateinit var restoreOptions: LinearLayout
    private lateinit var restoreButton: Button
    private val restoreBoxes = linkedMapOf<String, CheckBox>()
    private val restoreFileBoxes = linkedMapOf<String, CheckBox>()
    private var restoreApps: Set<String>? = null
    private var inspection: BackupEngine.Inspection? = null
    private lateinit var termuxRestoreStatus: LinearLayout

    private lateinit var strip: StatusStrip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
    }

    override fun onStart() { super.onStart(); strip.attach() }
    override fun onStop() { strip.detach(); super.onStop() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); Celebration.onFocus(this, hasFocus) }
    override fun onPause() { Celebration.onPause(this); super.onPause() }
    override fun onResume() { super.onResume(); Celebration.onResume(this) }

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

        // ───────── Backup
        val backup = card(page)
        backup.addView(heading("גיבוי"), wrap(4))
        backup.addView(note("סמן מה לגבות. הגיבוי נשמר בתיקייה שתבחר, בתוך תיקייה חדשה עם התאריך."), wrap(8))
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(button("סמן הכול (גיבוי מלא)", false) { setAll(true) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        quick.addView(button("נקה סימון", false) { setAll(false) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        backup.addView(quick, wrap(10))

        section(backup, "📦 אפליקציות")
        backupBoxes[BackupEngine.APPS] = check(backup, "אפליקציות (כולל ההרשאות ומקור ההתקנה)", true)
        backupAppsButton = button("", false) { chooseBackupApps() }
        backup.addView(backupAppsButton, wrap(6))
        renderBackupAppsButton()

        section(backup, "👤 נתונים אישיים")
        backupBoxes[BackupEngine.CONTACTS] = check(backup, "👤 אנשי קשר", true)
        backupBoxes[BackupEngine.CALLS] = check(backup, "📞 יומן שיחות", true)
        backupBoxes[BackupEngine.SMS] = check(backup, "💬 הודעות SMS", true)
        backupBoxes[BackupEngine.SETTINGS] = check(backup, "⚙ הגדרות טלפון", true)
        val termuxBox = check(backup, "🐚 טרמוקס — גיבוי מלא ‎.tar (home + usr)", false)
        backupBoxes[BackupEngine.TERMUX] = termuxBox
        termuxBackupStatus = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        backup.addView(termuxBackupStatus, wrap(4))
        termuxBox.setOnCheckedChangeListener { _, on -> if (on) verifyTermux(termuxBox, termuxBackupStatus) else termuxBackupStatus.visibility = View.GONE }

        section(backup, "📁 קבצים ותיקיות")
        foldersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        backup.addView(foldersBox)
        val volumes = BackupEngine.storageVolumes(this)
        // Ready-made folders on internal storage, then a "whole card" option for each memory card.
        for (set in BackupEngine.FILE_SETS) addFolder(set, set.key in setOf("dcim", "documents", "whatsapp"))
        for ((label, path) in volumes.drop(1)) addFolder(BackupEngine.FileSet("card_" + path.hashCode().toUInt(), "$label — כל הכרטיס", path), false)
        val folderButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        folderButtons.addView(button("+ הוסף תיקייה", false) { browse(volumes) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        backup.addView(folderButtons, wrap(10))

        backup.addView(button("▶  גבה עכשיו", true) { startBackup() }, wrap(8))
        backup.addView(box(
            "💡 לא נכלל (Android חוסם בלי Root): הנתונים הפנימיים של אפליקציות (כניסה לחשבון, שמירות במשחקים) וסיסמאות Wi‑Fi. " +
                "וואטסאפ: נשמר הגיבוי המקומי שלה — כדי שיהיה עדכני, גבה קודם בתוך וואטסאפ ← הגדרות ← צ'אטים ← גיבוי."
        ), wrap(0))

        // ───────── Restore
        val restore = card(page)
        restore.addView(heading("שחזור"), wrap(4))
        restore.addView(note("בחר תיקיית גיבוי (שמתחילה ב־TheBoss-backup), או את התיקייה שמכילה את הגיבויים — וייבחר החדש ביותר."), wrap(8))
        restore.addView(button("📂 בחר תיקיית גיבוי", false) { pickFolder(REQUEST_RESTORE) }, wrap(8))
        restoreInfo = body("").apply { visibility = View.GONE }
        restore.addView(restoreInfo, wrap(6))
        restoreOptions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        restore.addView(restoreOptions, wrap(6))
        restoreButton = button("▶  שחזר", true) { startRestore() }.apply { visibility = View.GONE }
        restore.addView(restoreButton, wrap(0))

        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(BACKGROUND); addView(page) }
        strip = StatusStrip(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BACKGROUND) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(strip.view)
        root.addView(BottomNav.build(this, BottomNav.Tab.BACKUP) { BottomNav.navigate(this, it) })
        Compat.applySystemBarInsets(this, root, includeIme = true)
        setContentView(root)
    }

    private fun setAll(on: Boolean) {
        backupBoxes.forEach { (key, box) -> if (key != BackupEngine.TERMUX || !on || BackupEngine.isTermuxInstalled(this)) box.isChecked = on }
        if (on) backupApps = null
        renderBackupAppsButton()
        folders.forEach { it.second.isChecked = on }
    }

    // region apps selection

    private fun installedUserApps(): List<Pair<String, String>> {
        @Suppress("DEPRECATION")
        return packageManager.getInstalledPackages(0).mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || info.packageName == packageName) return@mapNotNull null
            info.packageName to packageManager.getApplicationLabel(ai).toString()
        }.sortedBy { it.second.lowercase() }
    }

    private fun renderBackupAppsButton() {
        val total = installedUserApps().size
        val chosen = backupApps?.size ?: total
        backupAppsButton.text = "בחר אפליקציות ›  ($chosen מתוך $total)"
    }

    private fun chooseBackupApps() {
        val all = installedUserApps()
        appPicker("אפליקציות לגיבוי", all, backupApps ?: all.map { it.first }.toSet()) { chosen ->
            backupApps = if (chosen.size == all.size) null else chosen
            backupBoxes[BackupEngine.APPS]?.isChecked = chosen.isNotEmpty()
            renderBackupAppsButton()
        }
    }

    /** Multi-choice list with search, "all" and "none". */
    private fun appPicker(title: String, apps: List<Pair<String, String>>, initial: Set<String>, onDone: (Set<String>) -> Unit) {
        val chosen = initial.toMutableSet()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val search = EditText(this).apply { hint = "🔍 חיפוש"; setSingleLine(true) }
        box.addView(search)
        val list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)))
        var shown = apps
        fun refresh() {
            val q = search.text.toString().trim().lowercase()
            shown = apps.filter { q.isEmpty() || it.second.lowercase().contains(q) || it.first.contains(q) }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, shown.map { it.second })
            shown.forEachIndexed { i, a -> list.setItemChecked(i, a.first in chosen) }
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val pkg = shown[pos].first
            if (list.isItemChecked(pos)) chosen += pkg else chosen -= pkg
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        refresh()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("אישור") { _, _ -> onDone(chosen) }
            .setNeutralButton("הכול / כלום") { _, _ -> onDone(if (chosen.size == apps.size) emptySet() else apps.map { it.first }.toSet()) }
            .setNegativeButton("ביטול", null)
            .show()
    }

    // endregion

    // region folders

    private fun addFolder(set: BackupEngine.FileSet, checked: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val cb = CheckBox(this).apply {
            text = set.label + if (!BackupEngine.FILE_SETS.any { it.key == set.key }) "\n${set.path}" else ""
            textSize = 15f
            isChecked = checked
            setTextColor(INK)
        }
        row.addView(cb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (set.key.startsWith("custom_")) {
            row.addView(TextView(this).apply {
                text = "✕"
                textSize = 18f
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setTextColor(MUTED)
                isClickable = true
                setOnClickListener { foldersBox.removeView(row); folders.removeAll { it.first.key == set.key } }
                Compat.focusRing(this, INK, 10f)
            })
        }
        foldersBox.addView(row, wrap(0))
        folders += set to cb
    }

    /** Folder browser over ADB: pick a storage, walk into folders, and choose one. */
    private fun browse(volumes: List<Pair<String, String>>) {
        if (volumes.size == 1) openDir(volumes[0].second, volumes[0].second)
        else AlertDialog.Builder(this)
            .setTitle("באיזה אחסון?")
            .setItems(volumes.map { it.first }.toTypedArray()) { _, i -> openDir(volumes[i].second, volumes[i].second) }
            .show()
    }

    private fun openDir(root: String, path: String) {
        Toast.makeText(this, "טוען תיקיות…", Toast.LENGTH_SHORT).show()
        Bridge.scope.launch {
            val dirs = runCatching { BackupEngine.listDirs(path) }
            runOnUiThread {
                dirs.onFailure { toast("לא ניתן לקרוא תיקיות: ${Bridge.explain(it)}") }.onSuccess { list ->
                    val items = buildList {
                        add("✔ בחר את התיקייה הזו")
                        if (path != root) add("⬆ תיקייה למעלה")
                        addAll(list.map { "📁 $it" })
                    }
                    AlertDialog.Builder(this@BackupActivity)
                        .setTitle(path)
                        .setItems(items.toTypedArray()) { _, i ->
                            val choice = items[i]
                            when {
                                i == 0 -> {
                                    val key = "custom_" + path.hashCode().toUInt()
                                    if (folders.any { it.first.path == path }) toast("התיקייה כבר ברשימה.")
                                    else addFolder(BackupEngine.FileSet(key, "📁 " + path.substringAfterLast('/'), path), true)
                                }
                                choice.startsWith("⬆") -> openDir(root, path.substringBeforeLast('/'))
                                else -> openDir(root, "$path/${choice.removePrefix("📁 ")}")
                            }
                        }
                        .setNegativeButton("ביטול", null)
                        .show()
                }
            }
        }
    }

    // endregion

    // region Termux check

    /** Ticking Termux checks it is installed and accepts commands; only if not, the setup line appears. */
    private fun verifyTermux(target: CheckBox, status: LinearLayout) {
        status.removeAllViews()
        status.visibility = View.VISIBLE
        status.addView(note("⏳ בודק את טרמוקס…"))
        BackupEngine.checkTermux(this) { result, error ->
            status.removeAllViews()
            when (result) {
                BackupEngine.TermuxStatus.READY -> status.addView(TextView(this).apply {
                    text = "✔ טרמוקס מותקן ומוכן."
                    textSize = 14f
                    setTextColor(0xFF1E8E3E.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                })
                BackupEngine.TermuxStatus.NOT_INSTALLED -> {
                    status.addView(note("טרמוקס לא מותקן בטלפון."))
                    target.isChecked = false
                    status.visibility = View.VISIBLE
                }
                BackupEngine.TermuxStatus.NO_ACCESS -> {
                    status.addView(box(
                        "טרמוקס מותקן אבל עדיין לא מקבל פקודות מהבוס. פעם אחת: מעתיקים את השורה, פותחים את טרמוקס, מדביקים ולוחצים Enter:\n" +
                            BackupEngine.TERMUX_SETUP_COMMAND
                    ), wrap(4))
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(button("העתק ופתח את טרמוקס", true) {
                        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("termux", BackupEngine.TERMUX_SETUP_COMMAND))
                        packageManager.getLaunchIntentForPackage(BackupEngine.TERMUX_PKG)?.let { runCatching { startActivity(it) } }
                        toast("הועתק. הדבק בטרמוקס ולחץ Enter, ואז חזור ולחץ \"בדוק שוב\".")
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
                    row.addView(button("בדוק שוב", false) { verifyTermux(target, status) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    status.addView(row)
                }
                BackupEngine.TermuxStatus.NO_CONNECTION -> {
                    status.addView(note("אין חיבור ADB כרגע (${error ?: ""}). התחבר בלשונית חיבור, ואז סמן שוב."))
                    status.addView(button("בדוק שוב", false) { verifyTermux(target, status) })
                }
            }
        }
    }

    // endregion

    // region backup

    private fun startBackup() {
        val items = backupBoxes.filterValues { it.isChecked }.keys
        val sets = folders.filter { it.second.isChecked }.map { it.first }
        if (items.isEmpty() && sets.isEmpty()) { toast("סמן לפחות דבר אחד לגיבוי."); return }
        if (BackupEngine.APPS in items && backupApps?.isEmpty() == true) { toast("לא נבחרו אפליקציות."); return }
        if (Bridge.state.busy) { toast("פעולה אחרת עדיין רצה — נסה שוב בעוד רגע."); return }
        pickFolder(REQUEST_BACKUP)
    }

    private fun runBackup(tree: Uri) {
        val items = backupBoxes.filterValues { it.isChecked }.keys
        val sets = folders.filter { it.second.isChecked }.map { it.first }
        val started = BackupEngine.backup(this, tree, items, backupApps, sets) { result ->
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
        restoreApps = null
        restoreInfo.text = ins.description()
        restoreOptions.removeAllViews()
        restoreBoxes.clear()
        restoreFileBoxes.clear()
        val counts = ins.manifest.optJSONObject("items")
        fun count(key: String) = counts?.optInt(key, -1)?.takeIf { it >= 0 }?.let { " ($it)" } ?: ""
        if (BackupEngine.APPS in ins.available) {
            restoreBoxes[BackupEngine.APPS] = check(restoreOptions, "📦 אפליקציות${count(BackupEngine.APPS)}", true)
            if (ins.apps.isNotEmpty()) {
                val pick = button("", false) {}
                fun label() { pick.text = "בחר אפליקציות לשחזור ›  (${restoreApps?.size ?: ins.apps.size} מתוך ${ins.apps.size})" }
                label()
                pick.setOnClickListener {
                    appPicker("אפליקציות לשחזור", ins.apps, restoreApps ?: ins.apps.map { it.first }.toSet()) { chosen ->
                        restoreApps = if (chosen.size == ins.apps.size) null else chosen
                        restoreBoxes[BackupEngine.APPS]?.isChecked = chosen.isNotEmpty()
                        label()
                    }
                }
                restoreOptions.addView(pick, wrap(6))
            }
        }
        if (BackupEngine.CONTACTS in ins.available) restoreBoxes[BackupEngine.CONTACTS] = check(restoreOptions, "👤 אנשי קשר${count(BackupEngine.CONTACTS)}", true)
        if (BackupEngine.CALLS in ins.available) restoreBoxes[BackupEngine.CALLS] = check(restoreOptions, "📞 יומן שיחות${count(BackupEngine.CALLS)}", true)
        if (BackupEngine.SMS in ins.available) restoreBoxes[BackupEngine.SMS] = check(restoreOptions, "💬 הודעות SMS${count(BackupEngine.SMS)}", true)
        if (BackupEngine.SETTINGS in ins.available) restoreBoxes[BackupEngine.SETTINGS] = check(restoreOptions, "⚙ הגדרות טלפון", true)
        if (BackupEngine.TERMUX in ins.available) {
            val tb = check(restoreOptions, "🐚 טרמוקס — שחזור מלא (home + usr)", false)
            restoreBoxes[BackupEngine.TERMUX] = tb
            termuxRestoreStatus = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            restoreOptions.addView(termuxRestoreStatus, wrap(4))
            tb.setOnCheckedChangeListener { _, on -> if (on) verifyTermux(tb, termuxRestoreStatus) else termuxRestoreStatus.visibility = View.GONE }
        }
        for (set in ins.fileSets) {
            restoreFileBoxes[set.key] = check(restoreOptions, "${set.label}\n${set.path}", true)
        }
        restoreButton.visibility = if (restoreBoxes.isEmpty() && restoreFileBoxes.isEmpty()) View.GONE else View.VISIBLE
        if (restoreButton.visibility == View.GONE) restoreInfo.text = ins.description() + "\n⚠️ הגיבוי ריק."
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
                    "• קבצים בשם זהה יוחלפו בגרסה מהגיבוי. תיקייה מכרטיס זיכרון שלא קיים כאן תשוחזר לזיכרון הפנימי.\n" +
                    "• אנשי קשר: בסוף ייפתח מסך הייבוא של אפליקציית אנשי הקשר — שם מאשרים.\n" +
                    "• טרמוקס: הקבצים שלו יוחלפו בקבצים מהגיבוי, והוא יופעל מחדש. השאר את המסך פתוח עד הסוף."
            )
            .setPositiveButton("שחזר") { _, _ ->
                val started = BackupEngine.restore(this, ins, items, restoreApps, files) { result ->
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
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(vcf, "text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure { toast("לא נמצאה אפליקציית אנשי קשר לייבוא. הקובץ contacts.vcf נמצא בתיקיית הגיבוי.") }
    }

    // endregion

    // region folder picker (destination / backup source)

    private fun pickFolder(request: Int) {
        runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), request) }
            .onFailure { toast("לא נמצא בוחר תיקיות.") }
    }

    @Deprecated("Framework Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val tree = data?.data
        if (resultCode != RESULT_OK || tree == null) return
        runCatching { contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
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
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFFFFFFFF.toInt()); setStroke(dp(1), 0xFFF2E2D2.toInt()) }
        parent.addView(this, wrap(14))
    }

    private fun section(parent: LinearLayout, t: String) = parent.addView(TextView(this).apply {
        text = t; textSize = 15.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK); setPadding(0, dp(10), 0, dp(2))
    })

    private fun heading(t: String) = TextView(this).apply { text = t; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(INK) }
    private fun body(t: String) = TextView(this).apply { text = t; textSize = 14.5f; setTextColor(0xFF55483E.toInt()) }
    private fun note(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(MUTED) }

    private fun box(t: String) = TextView(this).apply {
        text = t
        textSize = 13.5f
        setTextColor(0xFF3F342B.toInt())
        setTextIsSelectable(true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0xFFFFF6D6.toInt()); setStroke(dp(1), 0xFFF2D58A.toInt()) }
    }

    private fun check(parent: LinearLayout, label: String, checked: Boolean) = CheckBox(this).apply {
        text = label; textSize = 15f; isChecked = checked; setTextColor(INK)
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
    private fun wrap(bottom: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }

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
