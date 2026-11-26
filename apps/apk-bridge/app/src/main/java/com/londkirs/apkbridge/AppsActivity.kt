package com.londkirs.apkbridge

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Installed apps: search, open, uninstall (via ADB), save the APK (split apps as .apks), app info. */
class AppsActivity : Activity() {
    private data class AppRow(
        val label: String,
        val pkg: String,
        val version: String,
        val system: Boolean,
        val apks: List<String>,
        val size: Long,
    )

    private var all: List<AppRow> = emptyList()
    private var shown: List<AppRow> = emptyList()
    private val icons = HashMap<String, Drawable?>()
    private lateinit var adapter: BaseAdapter
    private lateinit var search: EditText
    private lateinit var showSystem: CheckBox
    private lateinit var countText: TextView
    private var pendingSave: AppRow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Bridge.init(this)
        buildUi()
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

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(14), dp(14), dp(14), 0)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "אפליקציות מותקנות"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top, matchWrap(8))

        search = EditText(this).apply {
            hint = "חיפוש לפי שם או חבילה"
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
                override fun afterTextChanged(s: Editable?) = filter()
            })
        }
        root.addView(search, matchWrap(4))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        showSystem = CheckBox(this).apply {
            text = "הצג אפליקציות מערכת"
            setTextColor(INK)
            setOnCheckedChangeListener { _, _ -> filter() }
        }
        row.addView(showSystem, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        countText = TextView(this).apply { setTextColor(MUTED); textSize = 12f }
        row.addView(countText)
        root.addView(row, matchWrap(4))

        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = (convertView as? LinearLayout) ?: rowView()
                val app = shown[position]
                (view.getChildAt(0) as ImageView).setImageDrawable(iconOf(app.pkg))
                val texts = view.getChildAt(1) as LinearLayout
                (texts.getChildAt(0) as TextView).text = app.label
                (texts.getChildAt(1) as TextView).text =
                    "${app.pkg}\n${app.version} · ${Bridge.formatSize(app.size)}" + if (app.apks.size > 1) " · ${app.apks.size} חלקים" else ""
                return view
            }
        }
        val list = ListView(this).apply {
            this.adapter = this@AppsActivity.adapter
            divider = null
            setOnItemClickListener { _, _, position, _ -> actions(shown[position]) }
            // Keypad phones: rows are selectable with the arrows and opened with the center key.
            itemsCanFocus = false
            isFocusable = true
            selector = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0x33F09A45)
                setStroke(dp(2), 0xFFF09A45.toInt())
            }
            setDrawSelectorOnTop(false)
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BACKGROUND) }
        outer.addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        outer.addView(BottomNav.build(this, BottomNav.Tab.APPS) { BottomNav.navigate(this, it) })
        Compat.applySystemBarInsets(this, outer, includeIme = true)
        setContentView(outer)
    }

    private fun rowView() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
        addView(ImageView(this@AppsActivity), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        val texts = LinearLayout(this@AppsActivity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this@AppsActivity).apply { textSize = 15f; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD })
        texts.addView(TextView(this@AppsActivity).apply { textSize = 12f; setTextColor(MUTED) })
        addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun iconOf(pkg: String): Drawable? = icons.getOrPut(pkg) {
        runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    private fun load() {
        countText.text = "טוען…"
        Bridge.scope.launch {
            val pm = packageManager
            val rows = runCatching {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0).mapNotNull { info ->
                    val ai = info.applicationInfo ?: return@mapNotNull null
                    val apks = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
                    AppRow(
                        label = pm.getApplicationLabel(ai).toString(),
                        pkg = info.packageName,
                        version = "${info.versionName ?: "?"} (${Compat.versionCode(info)})",
                        system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                        apks = apks,
                        size = apks.sumOf { File(it).length() },
                    )
                }.sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            runOnUiThread {
                all = rows
                filter()
            }
        }
    }

    private fun filter() {
        if (!::adapter.isInitialized) return
        val q = search.text.toString().trim().lowercase()
        shown = all.filter { (showSystem.isChecked || !it.system) && (q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q)) }
        countText.text = "${shown.size} אפליקציות"
        adapter.notifyDataSetChanged()
    }

    private fun actions(app: AppRow) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        fun item(label: String, action: () -> Unit) { items.add(label to action) }
        packageManager.getLaunchIntentForPackage(app.pkg)?.let { launch ->
            item("פתח") { runCatching { startActivity(launch) } }
        }
        item("שמור APK" + if (app.apks.size > 1) " (חבילת ‎.apks)" else "") { save(app) }
        if (app.pkg != packageName) {
            item("הסר התקנה (ADB)") { confirmUninstall(app, keepData = false) }
            item("הסר התקנה ושמור נתונים (ADB)") { confirmUninstall(app, keepData = true) }
        }
        item("פרטי אפליקציה") {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))) }
        }
        item("העתק שם חבילה") {
            getSystemService(android.content.ClipboardManager::class.java)
                ?.setPrimaryClip(android.content.ClipData.newPlainText("package", app.pkg))
            toast("הועתק: ${app.pkg}")
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun confirmUninstall(app: AppRow, keepData: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("להסיר את ${app.label}?")
            .setMessage(if (keepData) "האפליקציה תוסר והנתונים שלה יישמרו להתקנה הבאה." else "האפליקציה והנתונים שלה יימחקו.")
            .setPositiveButton("הסר") { _, _ ->
                Bridge.uninstall(app.pkg, keepData)
                toast("מסיר… המצב מוצג במסך הראשי.")
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun save(app: AppRow) {
        pendingSave = app
        val split = app.apks.size > 1
        val safeName = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (split) "application/octet-stream" else "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_TITLE, "$safeName-${app.version.substringBefore(' ')}.${if (split) "apks" else "apk"}")
        }
        runCatching { startActivityForResult(intent, REQUEST_SAVE) }.onFailure { toast("לא נמצא מסך שמירת קבצים.") }
    }

    @Deprecated("Framework Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val app = pendingSave
        val target = data?.data
        if (requestCode != REQUEST_SAVE || resultCode != RESULT_OK || app == null || target == null) return
        pendingSave = null
        toast("שומר את ${app.label}…")
        Bridge.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(target)?.use { out ->
                        if (app.apks.size == 1) {
                            File(app.apks[0]).inputStream().use { it.copyTo(out, 256 * 1024) }
                        } else {
                            ZipOutputStream(out).use { zip ->
                                for (path in app.apks) {
                                    val name = if (path == app.apks[0]) "base.apk" else File(path).name
                                    zip.putNextEntry(ZipEntry(name))
                                    File(path).inputStream().use { it.copyTo(zip, 256 * 1024) }
                                    zip.closeEntry()
                                }
                            }
                        }
                    } ?: error("no output stream")
                }.isSuccess
            }
            Bridge.log(if (ok) "נשמר APK של ${app.label} (${app.pkg})." else "שמירת ה־APK של ${app.label} נכשלה.")
            runOnUiThread { toast(if (ok) "✔ נשמר." else "השמירה נכשלה.") }
        }
    }

    private fun smallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        stateListAnimator = null
        setTextColor(0xFF75451F.toInt())
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFFFFE7D1.toInt()) }
        setOnClickListener { action() }
        Compat.focusRing(this, 0xFF75451F.toInt())
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun matchWrap(bottom: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottom) }

    companion object {
        private const val REQUEST_SAVE = 5101
        private val INK = 0xFF30251D.toInt()
        private val MUTED = 0xFF8A7868.toInt()
        private val BACKGROUND = 0xFFFFF9F3.toInt()
    }
}
