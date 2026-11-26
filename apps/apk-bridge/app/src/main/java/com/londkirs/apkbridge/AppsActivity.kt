package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Installed apps: filters, sorting, search, and multi-select with bulk actions
 * (uninstall, disable/enable, force-stop, clear data, restore removed system apps, back up APKs).
 */
class AppsActivity : Activity() {
    private class AppRow(
        val label: String,
        val pkg: String,
        val version: String,
        val versionCode: Long,
        val system: Boolean,
        val updatedSystem: Boolean,
        val enabled: Boolean,
        val installed: Boolean,
        val launcher: Boolean,
        val apks: List<String>,
        val apkSize: Long,
        val totalSize: Long?,      // app + data + cache, when usage access is granted
        val firstInstall: Long,
        val lastUpdate: Long,
        val lastUsed: Long?,       // when usage access is granted
        val installer: String?,
    ) {
        val size get() = totalSize ?: apkSize
        val split get() = apks.size > 1
    }

    private enum class Filter(val label: String) {
        ALL("הכול"), USER("שהורדו"), LAUNCHER("במסך הבית"), SYSTEM("מערכת"), DISABLED("מושבתות"),
        PLAY("מ־Play"), OTHER_SOURCE("ממקור אחר"), SPLIT("מפוצלות"), REMOVED("מערכת שהוסרו"),
    }

    private enum class Sort(val label: String) {
        NAME("שם (א←ת)"), NAME_DESC("שם (ת←א)"),
        SIZE_DESC("גודל (הגדול ראשון)"), SIZE_ASC("גודל (הקטן ראשון)"),
        INSTALL_NEW("תאריך התקנה (החדש ראשון)"), INSTALL_OLD("תאריך התקנה (הישן ראשון)"),
        UPDATE_NEW("עדכון אחרון (החדש ראשון)"),
        UNUSED("לא בשימוש הכי הרבה זמן"), RECENT("בשימוש לאחרונה"),
        PACKAGE("שם חבילה"),
    }

    private var all: List<AppRow> = emptyList()
    private var shown: List<AppRow> = emptyList()
    private val selected = linkedSetOf<String>()
    private val icons = HashMap<String, Drawable?>()
    private var filter = Filter.USER
    private var sort = Sort.NAME

    private lateinit var adapter: BaseAdapter
    private lateinit var search: EditText
    private lateinit var countText: TextView
    private lateinit var sortButton: Button
    private lateinit var filterRow: LinearLayout
    private lateinit var usageBanner: LinearLayout
    private lateinit var bottom: FrameLayout
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionCount: TextView
    private lateinit var strip: StatusStrip
    private var pendingBackup: List<AppRow> = emptyList()
    private var pendingSave: AppRow? = null

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val prefs by lazy { getSharedPreferences("apps", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        filter = runCatching { Filter.valueOf(prefs.getString("filter", Filter.USER.name)!!) }.getOrDefault(Filter.USER)
        sort = runCatching { Sort.valueOf(prefs.getString("sort", Sort.NAME.name)!!) }.getOrDefault(Sort.NAME)
        buildUi()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Celebration.onFocus(this, hasFocus)
    }

    override fun onStart() {
        super.onStart()
        strip.attach()
    }

    override fun onStop() {
        strip.detach()
        super.onStop()
    }

    override fun onPause() {
        Celebration.onPause(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Celebration.onResume(this)
        load()
    }

    @Deprecated("Back key leaves selection mode first")
    override fun onBackPressed() {
        if (selected.isNotEmpty()) clearSelection() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // region layout

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(14), dp(12), dp(14), 0)
        }
        root.addView(TextView(this).apply {
            text = "אפליקציות"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, wrap(8))

        search = EditText(this).apply {
            hint = "🔍 חיפוש לפי שם או חבילה"
            setSingleLine(true)
            textSize = 15f
            setTextColor(INK)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xFFFFFFFF.toInt())
                setStroke(dp(1), 0xFFE8D6C4.toInt())
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = applyFilter()
            })
        }
        root.addView(search, wrap(6))

        filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filterRow)
        }, wrap(6))
        renderFilters()

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sortButton = smallButton("") { chooseSort() }
        row.addView(sortButton)
        countText = TextView(this).apply { setTextColor(MUTED); textSize = 12.5f; setPadding(dp(8), 0, 0, 0) }
        row.addView(countText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(smallButton("בחר") { if (shown.isNotEmpty()) toggle(shown.first().pkg) })
        root.addView(row, wrap(6))

        usageBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0xFFFFF6D6.toInt()) }
            visibility = View.GONE
        }
        usageBanner.addView(TextView(this).apply {
            text = "💡 כדי לראות גודל מלא ושימוש אחרון — צריך גישה לנתוני שימוש."
            textSize = 13f
            setTextColor(INK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        usageBanner.addView(smallButton("אשר") { Bridge.grantUsageAccess { load() } })
        root.addView(usageBanner, wrap(6))

        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = (convertView as? LinearLayout) ?: rowView()
                bindRow(view, shown[position])
                return view
            }
        }
        val list = ListView(this).apply {
            this.adapter = this@AppsActivity.adapter
            divider = null
            itemsCanFocus = false
            isFocusable = true
            selector = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0x33F09A45)
                setStroke(dp(2), ACCENT)
            }
            // Tap: open the app's actions (or toggle it while selecting). Long-press: start selecting.
            setOnItemClickListener { _, _, position, _ ->
                val app = shown[position]
                if (selected.isNotEmpty()) toggle(app.pkg) else actions(app)
            }
            setOnItemLongClickListener { _, _, position, _ -> toggle(shown[position].pkg); true }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom: tab bar normally, selection bar while apps are selected.
        bottom = FrameLayout(this)
        selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFF30251D.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            elevation = dp(8).toFloat()
        }
        selectionBar.addView(barButton("✕") { clearSelection() })
        selectionCount = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        selectionBar.addView(selectionCount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        selectionBar.addView(barButton("בחר הכול") { selectAllShown() })
        selectionBar.addView(barButton("🗑 הסר", danger = true) { bulkUninstall() })
        selectionBar.addView(barButton("⋮ עוד") { bulkMenu() })

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BACKGROUND) }
        outer.addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // Progress of uninstall / disable / … right here, like when installing.
        strip = StatusStrip(this)
        outer.addView(strip.view)
        outer.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        renderBottom()
        Compat.applySystemBarInsets(this, outer, includeIme = true)
        setContentView(outer)
    }

    private fun renderFilters() {
        filterRow.removeAllViews()
        for (f in Filter.values()) {
            val on = f == filter
            filterRow.addView(TextView(this).apply {
                text = f.label
                textSize = 13f
                setTextColor(if (on) 0xFFFFFFFF.toInt() else INK)
                typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (on) ACCENT else 0xFFFFFFFF.toInt())
                    setStroke(dp(1), if (on) ACCENT else 0xFFE8D6C4.toInt())
                }
                isClickable = true
                setOnClickListener {
                    filter = f
                    prefs.edit().putString("filter", f.name).apply()
                    renderFilters()
                    applyFilter()
                }
                Compat.focusRing(this, INK, 16f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
    }

    private fun renderBottom() {
        bottom.removeAllViews()
        if (selected.isEmpty()) {
            bottom.addView(BottomNav.build(this, BottomNav.Tab.APPS) { BottomNav.navigate(this, it) })
        } else {
            selectionCount.text = "${selected.size} נבחרו"
            bottom.addView(selectionBar)
        }
    }

    private fun rowView() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
        addView(CheckBox(this@AppsActivity).apply {
            isClickable = false
            isFocusable = false
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(ImageView(this@AppsActivity), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        val texts = LinearLayout(this@AppsActivity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this@AppsActivity).apply { textSize = 15f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
        texts.addView(TextView(this@AppsActivity).apply { textSize = 12f; setTextColor(MUTED) })
        addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun bindRow(view: LinearLayout, app: AppRow) {
        val box = view.getChildAt(0) as CheckBox
        box.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
        box.isChecked = app.pkg in selected
        (view.getChildAt(1) as ImageView).apply {
            setImageDrawable(icons.getOrPut(app.pkg) { runCatching { packageManager.getApplicationIcon(app.pkg) }.getOrNull() })
            alpha = if (app.enabled && app.installed) 1f else 0.4f
        }
        val texts = view.getChildAt(2) as LinearLayout
        val badges = buildList {
            if (!app.installed) add("הוסרה")
            else if (!app.enabled) add("מושבתת")
            if (app.system) add("מערכת")
        }
        (texts.getChildAt(0) as TextView).text = app.label + if (badges.isEmpty()) "" else "  · ${badges.joinToString(" · ")}"
        (texts.getChildAt(1) as TextView).text = "${app.pkg}\n${app.version} · ${Bridge.formatSize(app.size)}" +
            (if (app.split) " · ${app.apks.size} חלקים" else "") + " · ${sortDetail(app)}"
        view.setBackgroundColor(if (app.pkg in selected) 0x22F09A45 else 0)
    }

    /** The extra detail on each row follows the chosen sort (install date, last used, …). */
    private fun sortDetail(app: AppRow): String = when (sort) {
        Sort.UPDATE_NEW -> "עודכן ${dateFormat.format(Date(app.lastUpdate))}"
        Sort.UNUSED, Sort.RECENT -> app.lastUsed?.let { if (it <= 0) "לא בשימוש" else "בשימוש ${ago(it)}" } ?: "הותקן ${dateFormat.format(Date(app.firstInstall))}"
        else -> "הותקן ${dateFormat.format(Date(app.firstInstall))}"
    }

    private fun ago(time: Long): String {
        val days = ((System.currentTimeMillis() - time) / 86_400_000L).coerceAtLeast(0)
        return when {
            days == 0L -> "היום"
            days == 1L -> "אתמול"
            days < 30 -> "לפני $days ימים"
            days < 365 -> "לפני ${days / 30} חודשים"
            else -> "לפני ${days / 365} שנים"
        }
    }

    // endregion

    // region data

    @Suppress("DEPRECATION")
    private fun load() {
        countText.text = "טוען…"
        usageBanner.visibility = if (Bridge.hasUsageAccess()) View.GONE else View.VISIBLE
        Bridge.scope.launch {
            val rows = runCatching { readApps() }.getOrDefault(emptyList())
            runOnUiThread {
                all = rows
                selected.retainAll(rows.map { it.pkg }.toSet())
                applyFilter()
                renderBottom()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun readApps(): List<AppRow> {
        val pm = packageManager
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName }.toSet()
        val usage = Bridge.hasUsageAccess()
        val lastUsed: Map<String, Long> = if (!usage) emptyMap() else runCatching {
            val usm = getSystemService(UsageStatsManager::class.java) ?: return@runCatching emptyMap()
            val now = System.currentTimeMillis()
            usm.queryAndAggregateUsageStats(now - 2L * 365 * 86_400_000L, now).mapValues { it.value.lastTimeUsed }
        }.getOrDefault(emptyMap())
        val storage = if (usage) getSystemService(StorageStatsManager::class.java) else null
        val user = Process.myUserHandle()
        return pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES).mapNotNull { info ->
            val ai = info.applicationInfo ?: return@mapNotNull null
            val installed = (ai.flags and ApplicationInfo.FLAG_INSTALLED) != 0
            val apks = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
            val total = storage?.let { s ->
                runCatching { s.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user) }
                    .getOrNull()?.let { it.appBytes + it.dataBytes + it.cacheBytes }
            }
            val installer = runCatching {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(info.packageName).installingPackageName
                else pm.getInstallerPackageName(info.packageName)
            }.getOrNull()
            AppRow(
                label = pm.getApplicationLabel(ai).toString(),
                pkg = info.packageName,
                version = info.versionName ?: "?",
                versionCode = Compat.versionCode(info),
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                updatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                enabled = ai.enabled,
                installed = installed,
                launcher = info.packageName in launcher,
                apks = apks,
                apkSize = apks.sumOf { File(it).length() },
                totalSize = total,
                firstInstall = info.firstInstallTime,
                lastUpdate = info.lastUpdateTime,
                lastUsed = if (usage) lastUsed[info.packageName] ?: 0L else null,
                installer = installer,
            )
        }
    }

    private fun applyFilter() {
        if (!::adapter.isInitialized) return
        val q = search.text.toString().trim().lowercase()
        shown = all.filter { a ->
            val passes = when (filter) {
                Filter.ALL -> a.installed
                Filter.USER -> a.installed && !a.system
                Filter.LAUNCHER -> a.installed && a.launcher
                Filter.SYSTEM -> a.installed && a.system
                Filter.DISABLED -> a.installed && !a.enabled
                Filter.PLAY -> a.installed && a.installer == "com.android.vending"
                Filter.OTHER_SOURCE -> a.installed && !a.system && a.installer != "com.android.vending"
                Filter.SPLIT -> a.installed && a.split
                Filter.REMOVED -> !a.installed
            }
            passes && (q.isEmpty() || a.label.lowercase().contains(q) || a.pkg.contains(q))
        }.let { list ->
            when (sort) {
                Sort.NAME -> list.sortedBy { it.label.lowercase() }
                Sort.NAME_DESC -> list.sortedByDescending { it.label.lowercase() }
                Sort.SIZE_DESC -> list.sortedByDescending { it.size }
                Sort.SIZE_ASC -> list.sortedBy { it.size }
                Sort.INSTALL_NEW -> list.sortedByDescending { it.firstInstall }
                Sort.INSTALL_OLD -> list.sortedBy { it.firstInstall }
                Sort.UPDATE_NEW -> list.sortedByDescending { it.lastUpdate }
                Sort.UNUSED -> list.sortedBy { it.lastUsed ?: Long.MAX_VALUE }
                Sort.RECENT -> list.sortedByDescending { it.lastUsed ?: 0L }
                Sort.PACKAGE -> list.sortedBy { it.pkg }
            }
        }
        val totalSize = shown.sumOf { it.size }
        countText.text = "${shown.size} אפליקציות · ${Bridge.formatSize(totalSize)}"
        sortButton.text = "⇅ ${sort.label}"
        adapter.notifyDataSetChanged()
    }

    private fun chooseSort() {
        val sorts = Sort.values()
        val needsUsage = setOf(Sort.UNUSED, Sort.RECENT)
        val labels = sorts.map { s ->
            (if (s == sort) "✓ " else "    ") + s.label + if (s in needsUsage && !Bridge.hasUsageAccess()) " (דורש גישה לנתוני שימוש)" else ""
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("מיון לפי")
            .setItems(labels) { _, which ->
                sort = sorts[which]
                prefs.edit().putString("sort", sort.name).apply()
                applyFilter()
            }
            .show()
    }

    // endregion

    // region selection & bulk actions

    private fun toggle(pkg: String) {
        if (!selected.add(pkg)) selected.remove(pkg)
        renderBottom()
        adapter.notifyDataSetChanged()
    }

    private fun selectAllShown() {
        val shownPkgs = shown.map { it.pkg }
        if (selected.containsAll(shownPkgs)) selected.removeAll(shownPkgs.toSet()) else selected.addAll(shownPkgs)
        renderBottom()
        adapter.notifyDataSetChanged()
    }

    private fun clearSelection() {
        selected.clear()
        renderBottom()
        adapter.notifyDataSetChanged()
    }

    private fun selectedApps() = all.filter { it.pkg in selected && it.pkg != packageName }

    private fun names(apps: List<AppRow>) =
        apps.take(12).joinToString("\n") { "• ${it.label}" } + if (apps.size > 12) "\n…ועוד ${apps.size - 12}" else ""

    /** Runs a bulk action after a confirmation that lists the apps. */
    private fun confirmBulk(title: String, warning: String?, apps: List<AppRow>, command: (AppRow) -> String) {
        if (apps.isEmpty()) {
            Toast.makeText(this, "לא נבחרו אפליקציות מתאימות.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("$title — ${apps.size} אפליקציות")
            .setMessage((warning?.let { "⚠️ $it\n\n" } ?: "") + names(apps))
            .setPositiveButton("אישור") { _, _ ->
                val started = Bridge.batch(title, apps.map { it.label to command(it) }) { load() }
                if (started) {
                    clearSelection()
                } else {
                    Toast.makeText(this, "פעולה אחרת עדיין רצה — נסה שוב בעוד רגע.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    /** User apps are uninstalled; system apps are removed for this user only (and can be restored). */
    private fun uninstallCommand(app: AppRow, keepData: Boolean) =
        if (app.system) "pm uninstall ${if (keepData) "-k " else ""}--user 0 ${app.pkg}"
        else "pm uninstall ${if (keepData) "-k " else ""}${app.pkg}"

    private fun bulkUninstall(keepData: Boolean = false) {
        val apps = selectedApps().filter { it.installed }
        val hasSystem = apps.any { it.system }
        confirmBulk(
            if (keepData) "הסרה עם שמירת נתונים" else "הסרה",
            if (hasSystem) "חלק מהאפליקציות הן אפליקציות מערכת. הסרה שלהן עלולה לפגוע בפעולת הטלפון. אפשר להחזיר אותן אחר כך מהסינון \"מערכת שהוסרו\"." else null,
            apps,
        ) { uninstallCommand(it, keepData) }
    }

    private fun bulkMenu() {
        val items = listOf<Pair<String, () -> Unit>>(
            "🗑 הסר ושמור נתונים" to { bulkUninstall(keepData = true) },
            "⏸ השבת" to {
                confirmBulk("השבתה", "אפליקציה מושבתת לא תפעל ולא תופיע במסך הבית, עד שתפעיל אותה שוב.", selectedApps().filter { it.installed && it.enabled }) {
                    "pm disable-user --user 0 ${it.pkg}"
                }
            },
            "▶ הפעל (בטל השבתה)" to {
                confirmBulk("הפעלה", null, selectedApps().filter { it.installed && !it.enabled }) { "pm enable ${it.pkg}" }
            },
            "⏹ עצור בכוח" to {
                confirmBulk("עצירה בכוח", null, selectedApps().filter { it.installed }) { "am force-stop ${it.pkg}" }
            },
            "🧹 נקה נתונים" to {
                confirmBulk("ניקוי נתונים", "כל הנתונים של האפליקציות (כניסה לחשבון, הגדרות, קבצים) יימחקו. אי אפשר לבטל.", selectedApps().filter { it.installed }) {
                    "pm clear ${it.pkg}"
                }
            },
            "♻ החזר אפליקציות מערכת שהוסרו" to {
                confirmBulk("החזרה", null, selectedApps().filter { !it.installed }) { "cmd package install-existing ${it.pkg}" }
            },
            "💾 גבה APK לתיקייה" to { backupToFolder(selectedApps().filter { it.installed }) },
            "📋 העתק רשימה" to {
                val text = selectedApps().joinToString("\n") { "${it.label} — ${it.pkg} (${it.version})" }
                getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("apps", text))
                Toast.makeText(this, "הרשימה הועתקה.", Toast.LENGTH_SHORT).show()
            },
        )
        AlertDialog.Builder(this)
            .setTitle("${selected.size} נבחרו")
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun backupToFolder(apps: List<AppRow>) {
        if (apps.isEmpty()) return
        pendingBackup = apps
        runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_FOLDER) }
            .onFailure { Toast.makeText(this, "לא נמצא בוחר תיקיות.", Toast.LENGTH_SHORT).show() }
    }

    // endregion

    // region single app

    private fun actions(app: AppRow) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        fun item(label: String, action: () -> Unit) { items.add(label to action) }
        if (!app.installed) {
            item("♻ החזר את האפליקציה") {
                Bridge.batch("החזרה", listOf(app.label to "cmd package install-existing ${app.pkg}")) { load() }
            }
        } else {
            packageManager.getLaunchIntentForPackage(app.pkg)?.let { launch ->
                item("פתח") { runCatching { startActivity(launch) } }
            }
            item("💾 שמור APK" + if (app.split) " (חבילת ‎.apks)" else "") { save(app) }
            if (app.pkg != packageName) {
                item("🗑 הסר") { selected.clear(); selected.add(app.pkg); bulkUninstall() }
                item("🗑 הסר ושמור נתונים") { selected.clear(); selected.add(app.pkg); bulkUninstall(keepData = true) }
                if (app.enabled) item("⏸ השבת") {
                    Bridge.batch("השבתה", listOf(app.label to "pm disable-user --user 0 ${app.pkg}")) { load() }
                } else item("▶ הפעל") {
                    Bridge.batch("הפעלה", listOf(app.label to "pm enable ${app.pkg}")) { load() }
                }
                item("⏹ עצור בכוח") { Bridge.batch("עצירה בכוח", listOf(app.label to "am force-stop ${app.pkg}")) { load() } }
            }
        }
        item("ℹ פרטי אפליקציה") {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))) }
        }
        item("☑ סמן (לפעולה על כמה אפליקציות)") { toggle(app.pkg) }
        val details = buildString {
            append("${app.pkg}\nגרסה ${app.version} (${app.versionCode})\n")
            append("גודל: ${Bridge.formatSize(app.size)}" + if (app.totalSize == null) " (קובץ ה־APK בלבד)" else " (כולל נתונים ומטמון)")
            append("\nהותקן: ${dateFormat.format(Date(app.firstInstall))} · עודכן: ${dateFormat.format(Date(app.lastUpdate))}")
            app.lastUsed?.let { append("\nשימוש אחרון: ${if (it <= 0) "לא ידוע" else ago(it)}") }
            append("\nמקור התקנה: ${app.installer ?: "לא רשום"}")
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setMessage(details)
            .setPositiveButton("פעולות…") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(app.label)
                    .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
                    .show()
            }
            .setNegativeButton("סגור", null)
            .show()
    }

    private fun save(app: AppRow) {
        pendingSave = app
        val safeName = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (app.split) "application/octet-stream" else "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_TITLE, "$safeName-${app.version}.${if (app.split) "apks" else "apk"}")
        }
        runCatching { startActivityForResult(intent, REQUEST_SAVE) }.onFailure { Toast.makeText(this, "לא נמצא מסך שמירת קבצים.", Toast.LENGTH_SHORT).show() }
    }

    /** Writes one app to an output stream: a plain .apk, or a .apks zip for split apps. */
    private fun writeApp(app: AppRow, out: java.io.OutputStream) {
        if (!app.split) {
            File(app.apks[0]).inputStream().use { it.copyTo(out, 256 * 1024) }
        } else {
            ZipOutputStream(out).use { zip ->
                for (path in app.apks) {
                    zip.putNextEntry(ZipEntry(if (path == app.apks[0]) "base.apk" else File(path).name))
                    File(path).inputStream().use { it.copyTo(zip, 256 * 1024) }
                    zip.closeEntry()
                }
            }
        }
    }

    @Deprecated("Framework Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val target = data?.data
        if (resultCode != RESULT_OK || target == null) return
        when (requestCode) {
            REQUEST_SAVE -> {
                val app = pendingSave ?: return
                pendingSave = null
                Bridge.scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { contentResolver.openOutputStream(target)?.use { writeApp(app, it) } ?: error("no stream") }.isSuccess
                    }
                    Bridge.log(if (ok) "נשמר APK של ${app.label}." else "שמירת ה־APK של ${app.label} נכשלה.")
                    runOnUiThread { Toast.makeText(this@AppsActivity, if (ok) "✔ נשמר." else "השמירה נכשלה.", Toast.LENGTH_SHORT).show() }
                }
            }
            REQUEST_FOLDER -> {
                val apps = pendingBackup
                pendingBackup = emptyList()
                runCatching { contentResolver.takePersistableUriPermission(target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                Toast.makeText(this, "מגבה ${apps.size} אפליקציות…", Toast.LENGTH_SHORT).show()
                Bridge.scope.launch {
                    val dir = DocumentsContract.buildDocumentUriUsingTree(target, DocumentsContract.getTreeDocumentId(target))
                    var ok = 0
                    for (app in apps) {
                        val done = withContext(Dispatchers.IO) {
                            runCatching {
                                val safe = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                val name = "$safe-${app.version}.${if (app.split) "apks" else "apk"}"
                                val mime = if (app.split) "application/octet-stream" else "application/vnd.android.package-archive"
                                val doc = DocumentsContract.createDocument(contentResolver, dir, mime, name) ?: error("create failed")
                                contentResolver.openOutputStream(doc)?.use { writeApp(app, it) } ?: error("no stream")
                            }.isSuccess
                        }
                        if (done) ok++ else Bridge.log("גיבוי ${app.label} נכשל.")
                    }
                    Bridge.log("גיבוי APK: נשמרו $ok מתוך ${apps.size} אפליקציות.")
                    runOnUiThread { Toast.makeText(this@AppsActivity, "✔ גובו $ok מתוך ${apps.size}.", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    // endregion

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setTextColor(0xFF75451F.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFFFFE7D1.toInt()) }
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    private fun barButton(label: String, danger: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13.5f
        isAllCaps = false
        stateListAnimator = null
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(if (danger) 0xFFD93025.toInt() else 0xFF4A3B30.toInt()) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) }
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFFFFFFFF.toInt())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wrap(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    companion object {
        private const val REQUEST_SAVE = 5101
        private const val REQUEST_FOLDER = 5102
        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val ACCENT = 0xFFF09A45.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()
    }
}
