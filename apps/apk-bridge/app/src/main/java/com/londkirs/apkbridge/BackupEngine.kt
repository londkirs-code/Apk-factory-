package com.londkirs.apkbridge

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.io.RandomAccessSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and restore over ADB (no root):
 *  apps (APKs + granted permissions + install source), contacts (vCard), call log, SMS,
 *  phone settings, and files from shared storage (camera, pictures, downloads, WhatsApp, …).
 * App-internal data (/data/data) is not reachable without root and is not included.
 */
object BackupEngine {
    const val APPS = "apps"
    const val CONTACTS = "contacts"
    const val CALLS = "calls"
    const val SMS = "sms"
    const val SETTINGS = "settings"
    const val TERMUX = "termux"

    const val TERMUX_PKG = "com.termux"
    private const val TERMUX_FILES = "/data/data/com.termux/files"
    private const val TERMUX_BASH = "$TERMUX_FILES/usr/bin/bash"
    /** Shared-storage folder where Termux writes / reads the archive (Termux can, ADB can too). */
    private const val TERMUX_SHARED = "/sdcard/TheBoss/termux"
    private const val TERMUX_ARCHIVE = "termux_full_backup.tar.gz"

    /** What the user has to run once in Termux so other apps may send it commands. */
    const val TERMUX_SETUP_COMMAND =
        "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"

    fun isTermuxInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(TERMUX_PKG, 0); true }.getOrDefault(false)

    data class FileSet(val key: String, val label: String, val path: String)

    val FILE_SETS = listOf(
        FileSet("dcim", "📷 מצלמה (DCIM)", "/sdcard/DCIM"),
        FileSet("pictures", "🖼 תמונות (Pictures)", "/sdcard/Pictures"),
        FileSet("download", "⬇ הורדות", "/sdcard/Download"),
        FileSet("documents", "📄 מסמכים", "/sdcard/Documents"),
        FileSet("movies", "🎬 סרטונים (Movies)", "/sdcard/Movies"),
        FileSet("music", "🎵 מוזיקה", "/sdcard/Music"),
        FileSet("whatsapp", "💚 וואטסאפ (גיבוי ומדיה)", "/sdcard/Android/media/com.whatsapp/WhatsApp"),
    )

    /** Phone settings that are safe to write back (others are device- or version-specific). */
    private val RESTORABLE_SYSTEM_SETTINGS = setOf(
        "screen_brightness", "screen_brightness_mode", "screen_off_timeout", "font_scale",
        "accelerometer_rotation", "haptic_feedback_enabled", "sound_effects_enabled", "dtmf_tone",
        "vibrate_when_ringing", "lockscreen_sounds_enabled", "show_touches", "pointer_speed",
        "time_12_24", "screen_auto_brightness_adj",
    )

    data class Result(val summary: String, val contactsVcf: Uri?)

    // region backup

    fun backup(context: Context, tree: Uri, items: Set<String>, fileSets: Set<String>, onDone: (Result?) -> Unit): Boolean {
        val app = context.applicationContext
        val cr = app.contentResolver
        return Bridge.launchJob("הגיבוי") {
            var result: Result? = null
            try {
                Bridge.announce("מכין תיקיית גיבוי…", 0)
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                val folder = withContext(Dispatchers.IO) { Saf.mkdir(cr, Saf.root(tree), "TheBoss-backup-$stamp") }
                val manifest = JSONObject()
                    .put("format", 1)
                    .put("created", System.currentTimeMillis())
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("android", Build.VERSION.RELEASE)
                val counts = JSONObject()
                val lines = mutableListOf<String>()
                val steps = items.size + fileSets.size
                var step = 0
                fun stepPct() = (step * 100 / steps.coerceAtLeast(1)).coerceIn(0, 99)

                Bridge.withClient { c ->
                    if (CONTACTS in items) {
                        Bridge.progress("מגבה אנשי קשר…", stepPct())
                        c.shell("pm grant ${app.packageName} android.permission.READ_CONTACTS")
                        val n = withContext(Dispatchers.IO) { backupContacts(app, folder) }
                        counts.put(CONTACTS, n); lines += "👤 אנשי קשר: $n"
                        step++
                    }
                    if (CALLS in items) {
                        Bridge.progress("מגבה יומן שיחות…", stepPct())
                        val rows = contentQuery(c, "content://call_log/calls", listOf("number", "date", "duration", "type", "new"), "name")
                        withContext(Dispatchers.IO) { Saf.writeText(cr, folder, "calls.json", toJson(rows).toString()) }
                        counts.put(CALLS, rows.size); lines += "📞 שיחות: ${rows.size}"
                        step++
                    }
                    if (SMS in items) {
                        Bridge.progress("מגבה הודעות SMS…", stepPct())
                        val rows = contentQuery(c, "content://sms", listOf("address", "date", "date_sent", "type", "read", "seen"), "body")
                        withContext(Dispatchers.IO) { Saf.writeText(cr, folder, "sms.json", toJson(rows).toString()) }
                        counts.put(SMS, rows.size); lines += "💬 הודעות: ${rows.size}"
                        step++
                    }
                    if (SETTINGS in items) {
                        Bridge.progress("מגבה הגדרות טלפון…", stepPct())
                        val text = "[system]\n" + c.shell("settings list system").stdoutText +
                            "\n[secure]\n" + c.shell("settings list secure").stdoutText +
                            "\n[global]\n" + c.shell("settings list global").stdoutText
                        withContext(Dispatchers.IO) { Saf.writeText(cr, folder, "settings.txt", text) }
                        counts.put(SETTINGS, 1); lines += "⚙ הגדרות טלפון"
                        step++
                    }
                    if (TERMUX in items) {
                        lines += "🐚 טרמוקס: " + backupTermux(c, app, folder) { msg -> Bridge.progress(msg, stepPct()) }
                        counts.put(TERMUX, 1)
                        step++
                    }
                    if (APPS in items) {
                        val n = backupApps(app, folder) { label, i, total ->
                            Bridge.progress("מגבה אפליקציות: $label ($i/$total)", stepPct())
                        }
                        counts.put(APPS, n); lines += "📦 אפליקציות: $n"
                        step++
                    }
                    if (fileSets.isNotEmpty()) {
                        val filesDir = withContext(Dispatchers.IO) { Saf.mkdir(cr, folder, "files") }
                        val done = JSONArray()
                        for (set in FILE_SETS.filter { it.key in fileSets }) {
                            val n = backupFiles(c, app, filesDir, set) { i, total ->
                                Bridge.progress("מגבה ${set.label}: $i/$total", stepPct())
                            }
                            done.put(set.key)
                            lines += "${set.label}: $n קבצים"
                            step++
                        }
                        counts.put("files", done)
                    }
                }
                manifest.put("items", counts)
                withContext(Dispatchers.IO) { Saf.writeText(cr, folder, "manifest.json", manifest.toString(2)) }
                val summary = "✔ הגיבוי הסתיים (תיקייה TheBoss-backup-$stamp):\n" + lines.joinToString("\n")
                Bridge.announce(summary, 100)
                result = Result(summary, null)
            } finally {
                val r = result
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(r) }
            }
        }
    }

    private fun backupContacts(context: Context, folder: Uri): Int {
        val cr = context.contentResolver
        val keys = mutableListOf<String>()
        cr.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let(keys::add)
        } ?: error("אין גישה לאנשי הקשר.")
        val out = Saf.create(cr, folder, "text/x-vcard", "contacts.vcf")
        cr.openOutputStream(out)?.use { os ->
            for (chunk in keys.chunked(100)) {
                val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(chunk.joinToString(":")))
                runCatching { cr.openInputStream(uri)?.use { it.copyTo(os) } }
            }
        }
        return keys.size
    }

    private suspend fun backupApps(context: Context, folder: Uri, onProgress: (String, Int, Int) -> Unit): Int {
        val pm = context.packageManager
        val cr = context.contentResolver
        @Suppress("DEPRECATION")
        val apps = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).filter { info ->
            val ai = info.applicationInfo ?: return@filter false
            info.packageName != context.packageName &&
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
        }
        val appsDir = withContext(Dispatchers.IO) { Saf.mkdir(cr, folder, "apps") }
        val index = JSONArray()
        apps.forEachIndexed { i, info ->
            val ai = info.applicationInfo!!
            val label = pm.getApplicationLabel(ai).toString()
            onProgress(label, i + 1, apps.size)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = Saf.mkdir(cr, appsDir, info.packageName)
                    val files = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
                    files.forEachIndexed { n, path ->
                        val name = if (n == 0) "base.apk" else File(path).name
                        val doc = Saf.create(cr, dir, "application/vnd.android.package-archive", name)
                        cr.openOutputStream(doc)?.use { out -> File(path).inputStream().use { it.copyTo(out, 256 * 1024) } }
                    }
                    Saf.writeText(cr, dir, "app.json", appJson(pm, info, label).toString(2))
                }.isSuccess
            }
            if (ok) index.put(JSONObject().put("package", info.packageName).put("label", label))
            else Bridge.log("גיבוי $label נכשל.")
        }
        withContext(Dispatchers.IO) { Saf.writeText(cr, folder, "apps.json", index.toString(2)) }
        return index.length()
    }

    private fun appJson(pm: PackageManager, info: PackageInfo, label: String): JSONObject {
        val granted = JSONArray()
        val req = info.requestedPermissions
        val flags = info.requestedPermissionsFlags
        if (req != null && flags != null) {
            for (i in req.indices) if ((flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) granted.put(req[i])
        }
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(info.packageName).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(info.packageName)
        }.getOrNull()
        return JSONObject()
            .put("package", info.packageName)
            .put("label", label)
            .put("versionName", info.versionName ?: "")
            .put("versionCode", Compat.versionCode(info))
            .put("installer", installer ?: "")
            .put("permissions", granted)
    }

    /** Pulls every file of a shared-storage folder over ADB (works for Android/media too). */
    private suspend fun backupFiles(c: AdbClient, context: Context, filesDir: Uri, set: FileSet, onProgress: (Int, Int) -> Unit): Int {
        val cr = context.contentResolver
        val list = c.shell("find '${set.path}' -type f 2>/dev/null").stdoutText.lines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.contains("/.") }
        if (list.isEmpty()) return 0
        val root = withContext(Dispatchers.IO) { Saf.mkdir(cr, filesDir, set.key) }
        val dirs = HashMap<String, Uri>().apply { put("", root) }
        fun dirFor(rel: String): Uri {
            dirs[rel]?.let { return it }
            val parentRel = rel.substringBeforeLast('/', "")
            val parent = dirFor(parentRel)
            return Saf.mkdir(cr, parent, rel.substringAfterLast('/')).also { dirs[rel] = it }
        }
        var ok = 0
        list.forEachIndexed { i, path ->
            onProgress(i + 1, list.size)
            val rel = path.removePrefix(set.path).trimStart('/')
            val done = runCatching {
                val parent = withContext(Dispatchers.IO) { dirFor(rel.substringBeforeLast('/', "")) }
                val doc = withContext(Dispatchers.IO) { Saf.create(cr, parent, "application/octet-stream", rel.substringAfterLast('/')) }
                val out = withContext(Dispatchers.IO) { cr.openOutputStream(doc) } ?: error("no stream")
                out.use { c.sync.pull(path, it) }
            }.isSuccess
            if (done) ok++ else Bridge.log("לא ניתן לגבות $path")
        }
        return ok
    }

    // endregion

    // region inspect + restore

    data class Inspection(
        val tree: Uri,
        val folder: Uri,
        val manifest: JSONObject,
        val available: Set<String>,
        val fileSets: List<String>,
    ) {
        fun description(): String {
            val items = manifest.optJSONObject("items") ?: JSONObject()
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(manifest.optLong("created")))
            return "גיבוי מ־$date · ${manifest.optString("device")} (Android ${manifest.optString("android")})"
        }
    }

    /** Accepts the backup folder itself, or a folder that contains backups (the newest is used). */
    fun inspect(context: Context, tree: Uri): Inspection {
        val cr = context.contentResolver
        val root = Saf.root(tree)
        val folder = if (Saf.child(cr, tree, root, "manifest.json") != null) root else {
            Saf.children(cr, tree, root).filter { it.isDir && it.name.startsWith("TheBoss-backup-") }
                .maxByOrNull { it.name }?.uri ?: error("לא נמצא גיבוי בתיקייה הזו. בחר תיקייה שמתחילה ב־TheBoss-backup.")
        }
        val manifestDoc = Saf.child(cr, tree, folder, "manifest.json") ?: error("חסר קובץ manifest.json בגיבוי.")
        val manifest = JSONObject(Saf.readText(cr, manifestDoc.uri))
        val names = Saf.children(cr, tree, folder).map { it.name }.toSet()
        val available = buildSet {
            if ("apps" in names) add(APPS)
            if ("contacts.vcf" in names) add(CONTACTS)
            if ("calls.json" in names) add(CALLS)
            if ("sms.json" in names) add(SMS)
            if ("settings.txt" in names) add(SETTINGS)
            if ("termux" in names) add(TERMUX)
        }
        val sets = if ("files" in names) {
            val filesDir = Saf.child(cr, tree, folder, "files")!!.uri
            Saf.children(cr, tree, filesDir).filter { it.isDir }.map { it.name }
        } else emptyList()
        return Inspection(tree, folder, manifest, available, sets)
    }

    fun restore(context: Context, inspection: Inspection, items: Set<String>, fileSets: Set<String>, onDone: (Result?) -> Unit): Boolean {
        val app = context.applicationContext
        val cr = app.contentResolver
        val tree = inspection.tree
        val folder = inspection.folder
        return Bridge.launchJob("השחזור") {
            var result: Result? = null
            try {
                val lines = mutableListOf<String>()
                var vcf: Uri? = null
                val steps = items.size + fileSets.size
                var step = 0
                fun stepPct() = (step * 100 / steps.coerceAtLeast(1)).coerceIn(0, 99)
                Bridge.withClient { c ->
                    if (APPS in items) {
                        val (ok, skipped) = restoreApps(c, app, tree, folder) { label, i, total ->
                            Bridge.progress("משחזר אפליקציות: $label ($i/$total)", stepPct())
                        }
                        lines += "📦 אפליקציות: הותקנו $ok" + if (skipped > 0) ", $skipped כבר היו מותקנות (הוחזרו ההרשאות)" else ""
                        step++
                    }
                    if (CALLS in items) {
                        Bridge.progress("משחזר יומן שיחות…", stepPct())
                        lines += "📞 שיחות: " + restoreCalls(c, cr, tree, folder)
                        step++
                    }
                    if (SMS in items) {
                        Bridge.progress("משחזר הודעות SMS…", stepPct())
                        lines += "💬 הודעות: " + restoreSms(c, cr, tree, folder)
                        step++
                    }
                    if (SETTINGS in items) {
                        Bridge.progress("משחזר הגדרות טלפון…", stepPct())
                        lines += "⚙ הגדרות: " + restoreSettings(c, cr, tree, folder)
                        step++
                    }
                    if (TERMUX in items) {
                        lines += "🐚 טרמוקס: " + restoreTermux(c, app, tree, folder) { msg -> Bridge.progress(msg, stepPct()) }
                        step++
                    }
                    if (fileSets.isNotEmpty()) {
                        val filesDir = Saf.child(cr, tree, folder, "files")?.uri
                        for (key in fileSets) {
                            val set = FILE_SETS.firstOrNull { it.key == key } ?: continue
                            val src = filesDir?.let { Saf.child(cr, tree, it, key)?.uri } ?: continue
                            val n = restoreFiles(c, cr, tree, src, set) { i ->
                                Bridge.progress("משחזר ${set.label}: $i קבצים", stepPct())
                            }
                            lines += "${set.label}: $n קבצים"
                            step++
                        }
                    }
                    if (CONTACTS in items) {
                        vcf = Saf.child(cr, tree, folder, "contacts.vcf")?.uri
                        lines += "👤 אנשי קשר: נפתח מסך הייבוא של אנשי הקשר — אשר שם."
                        step++
                    }
                }
                val summary = "✔ השחזור הסתיים:\n" + lines.joinToString("\n")
                Bridge.announce(summary, 100)
                result = Result(summary, vcf)
            } finally {
                val r = result
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(r) }
            }
        }
    }

    private suspend fun restoreApps(c: AdbClient, context: Context, tree: Uri, folder: Uri, onProgress: (String, Int, Int) -> Unit): Pair<Int, Int> {
        val cr = context.contentResolver
        val pm = context.packageManager
        val appsDir = Saf.child(cr, tree, folder, "apps")?.uri ?: return 0 to 0
        val dirs = Saf.children(cr, tree, appsDir).filter { it.isDir }
        var installed = 0
        var skipped = 0
        dirs.forEachIndexed { i, dir ->
            val files = Saf.children(cr, tree, dir.uri)
            val meta = files.firstOrNull { it.name == "app.json" }?.let { runCatching { JSONObject(Saf.readText(cr, it.uri)) }.getOrNull() }
            val pkg = meta?.optString("package")?.takeIf { it.isNotBlank() } ?: dir.name
            val label = meta?.optString("label")?.takeIf { it.isNotBlank() } ?: pkg
            onProgress(label, i + 1, dirs.size)
            val have = runCatching { Compat.versionCode(pm.getPackageInfo(pkg, 0)) }.getOrNull()
            val want = meta?.optLong("versionCode") ?: 0L
            if (have != null && have >= want) {
                skipped++
            } else {
                val apks = files.filter { it.name.endsWith(".apk") }.sortedBy { if (it.name == "base.apk") 0 else 1 }
                val flags = buildList {
                    add("-r"); add("-t")
                    if (meta?.optString("installer") == "com.android.vending") { add("-i"); add("com.android.vending") }
                }
                val pfds = apks.map { cr.openFileDescriptor(it.uri, "r") ?: error("לא ניתן לקרוא ${it.name}") }
                try {
                    val ok = runCatching {
                        val flow = if (pfds.size == 1) {
                            c.install.install(RandomAccessSource.of(pfds[0].fileDescriptor, pfds[0].statSize), flags)
                        } else {
                            c.install.installMultiple(apks.indices.map { k -> apks[k].name to RandomAccessSource.of(pfds[k].fileDescriptor, pfds[k].statSize) }, flags)
                        }
                        flow.collect { }
                    }
                    if (ok.isSuccess) installed++ else Bridge.log("התקנת $label נכשלה: ${Bridge.explain(ok.exceptionOrNull()!!)}")
                } finally {
                    pfds.forEach { runCatching { it.close() } }
                }
            }
            // Give back the runtime permissions the app had.
            val perms = meta?.optJSONArray("permissions")
            if (perms != null && perms.length() > 0) {
                val cmd = (0 until perms.length()).joinToString(" ; ") { "pm grant $pkg ${perms.getString(it)} 2>/dev/null" }
                runCatching { c.shell(cmd) }
            }
        }
        return installed to skipped
    }

    private suspend fun restoreCalls(c: AdbClient, cr: android.content.ContentResolver, tree: Uri, folder: Uri): String {
        val doc = Saf.child(cr, tree, folder, "calls.json") ?: return "אין בגיבוי"
        val rows = JSONArray(Saf.readText(cr, doc.uri))
        val existing = contentQuery(c, "content://call_log/calls", listOf("number"), "date").map { it["number"] + "|" + it["date"] }.toSet()
        val commands = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            if ((r.optString("number") + "|" + r.optString("date")) in existing) continue
            commands += "content insert --uri content://call_log/calls" +
                " --bind ${q("number:s:" + r.optString("number"))}" +
                " --bind date:l:${r.optString("date").toLongOrNull() ?: 0}" +
                " --bind duration:l:${r.optString("duration").toLongOrNull() ?: 0}" +
                " --bind type:i:${r.optString("type").toIntOrNull() ?: 1}" +
                " --bind new:i:0" +
                (r.optString("name").takeIf { it.isNotBlank() && it != "NULL" }?.let { " --bind ${q("name:s:$it")}" } ?: "")
        }
        val failed = runBatched(c, commands)
        return "נוספו ${commands.size - failed}" + (if (failed > 0) ", נכשלו $failed" else "") + ", ${rows.length() - commands.size} כבר היו"
    }

    /** SMS: the shell may insert once WRITE_SMS is allowed for it; restored to default afterwards. */
    private suspend fun restoreSms(c: AdbClient, cr: android.content.ContentResolver, tree: Uri, folder: Uri): String {
        val doc = Saf.child(cr, tree, folder, "sms.json") ?: return "אין בגיבוי"
        val rows = JSONArray(Saf.readText(cr, doc.uri))
        val existing = contentQuery(c, "content://sms", listOf("address"), "date").map { it["address"] + "|" + it["date"] }.toSet()
        val commands = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            if ((r.optString("address") + "|" + r.optString("date")) in existing) continue
            commands += "content insert --uri content://sms" +
                " --bind ${q("address:s:" + r.optString("address"))}" +
                " --bind ${q("body:s:" + r.optString("body").let { if (it == "NULL") "" else it })}" +
                " --bind date:l:${r.optString("date").toLongOrNull() ?: 0}" +
                " --bind date_sent:l:${r.optString("date_sent").toLongOrNull() ?: 0}" +
                " --bind type:i:${r.optString("type").toIntOrNull() ?: 1}" +
                " --bind read:i:1 --bind seen:i:1"
        }
        if (commands.isEmpty()) return "כל ${rows.length()} ההודעות כבר היו בטלפון"
        c.shell("appops set com.android.shell WRITE_SMS allow")
        val before = contentQuery(c, "content://sms", listOf("address"), "date").size
        try {
            runBatched(c, commands)
        } finally {
            c.shell("appops set com.android.shell WRITE_SMS default")
        }
        val after = contentQuery(c, "content://sms", listOf("address"), "date").size
        val added = (after - before).coerceAtLeast(0)
        return if (added == 0) "הטלפון לא אפשר לשחזר הודעות (חסום ביצרן/בגרסה הזו)"
        else "נוספו $added מתוך ${commands.size}" + if (added < commands.size) " (חלק נחסמו)" else ""
    }

    private suspend fun restoreSettings(c: AdbClient, cr: android.content.ContentResolver, tree: Uri, folder: Uri): String {
        val doc = Saf.child(cr, tree, folder, "settings.txt") ?: return "אין בגיבוי"
        val text = Saf.readText(cr, doc.uri)
        val system = text.substringAfter("[system]\n", "").substringBefore("\n[secure]")
        val commands = system.lines().mapNotNull { line ->
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "")
            if (key in RESTORABLE_SYSTEM_SETTINGS && value.isNotBlank() && value != "null") "settings put system $key ${q(value)}" else null
        }
        val failed = runBatched(c, commands)
        return "הוחזרו ${commands.size - failed} הגדרות (בהירות, זמן כיבוי מסך, גודל גופן, סיבוב ועוד)"
    }

    private suspend fun restoreFiles(c: AdbClient, cr: android.content.ContentResolver, tree: Uri, src: Uri, set: FileSet, onProgress: (Int) -> Unit): Int {
        var count = 0
        suspend fun walk(dir: Uri, rel: String) {
            val children = withContext(Dispatchers.IO) { Saf.children(cr, tree, dir) }
            val target = if (rel.isEmpty()) set.path else "${set.path}/$rel"
            if (children.any { !it.isDir }) c.shell("mkdir -p ${q(target)}")
            for (ch in children) {
                if (ch.isDir) {
                    walk(ch.uri, if (rel.isEmpty()) ch.name else "$rel/${ch.name}")
                } else {
                    val ok = runCatching {
                        val input = withContext(Dispatchers.IO) { cr.openInputStream(ch.uri) } ?: error("no stream")
                        input.use { c.sync.push(it, "$target/${ch.name}") }
                    }.isSuccess
                    if (ok) { count++; onProgress(count) } else Bridge.log("לא ניתן לשחזר ${ch.name}")
                }
            }
        }
        walk(src, "")
        // Let the gallery and other apps see the restored files.
        runCatching { c.shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d ${q("file://" + set.path)}") }
        return count
    }

    // endregion

    // region Termux

    /**
     * Full Termux backup (home + usr) as one .tar.gz. Termux's files are private to Termux, so the
     * archive is created *by Termux itself* through its official RUN_COMMAND interface, with the
     * complete command spelled out here (no aliases or scripts from the user's .bashrc are needed).
     */
    private val TERMUX_BACKUP_SCRIPT = """
        OUT='$TERMUX_SHARED'
        mkdir -p "${'$'}OUT" || exit 1
        rm -f "${'$'}OUT/$TERMUX_ARCHIVE" "${'$'}OUT/$TERMUX_ARCHIVE.part" "${'$'}OUT/done" "${'$'}OUT/failed" "${'$'}OUT/log.txt"
        cd '$TERMUX_FILES' || { echo 'no termux files dir' > "${'$'}OUT/failed"; exit 1; }
        tar -czf "${'$'}OUT/$TERMUX_ARCHIVE.part" --exclude='./home/.cache' --exclude='./home/storage' ./home ./usr > "${'$'}OUT/log.txt" 2>&1
        RC=${'$'}?
        if [ "${'$'}RC" -eq 0 ] || [ "${'$'}RC" -eq 1 ]; then
          mv "${'$'}OUT/$TERMUX_ARCHIVE.part" "${'$'}OUT/$TERMUX_ARCHIVE" && echo ok > "${'$'}OUT/done"
        else
          echo "tar exit ${'$'}RC" > "${'$'}OUT/failed"
        fi
    """.trimIndent()

    /** Full Termux restore: extract home + usr back into Termux's own folder and fix ownership. */
    private val TERMUX_RESTORE_SCRIPT = """
        OUT='$TERMUX_SHARED'
        IN="${'$'}OUT/$TERMUX_ARCHIVE"
        rm -f "${'$'}OUT/done" "${'$'}OUT/failed" "${'$'}OUT/log.txt"
        [ -f "${'$'}IN" ] || { echo 'archive missing' > "${'$'}OUT/failed"; exit 1; }
        cd '$TERMUX_FILES' || { echo 'no termux files dir' > "${'$'}OUT/failed"; exit 1; }
        tar -xzf "${'$'}IN" > "${'$'}OUT/log.txt" 2>&1
        RC=${'$'}?
        U=${'$'}(id -u)
        chown -R "${'$'}U:${'$'}U" '$TERMUX_FILES/usr' '$TERMUX_FILES/home' 2>/dev/null
        if [ "${'$'}RC" -eq 0 ]; then echo ok > "${'$'}OUT/done"; else echo "tar exit ${'$'}RC" > "${'$'}OUT/failed"; fi
    """.trimIndent()

    /** Sends a full bash script to Termux (RUN_COMMAND, in the background). */
    private fun runInTermux(context: Context, script: String) {
        val intent = android.content.Intent("com.termux.RUN_COMMAND")
            .setClassName(TERMUX_PKG, "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", "$TERMUX_FILES/home")
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        context.startForegroundService(intent)
    }

    /** Lets this app send commands to Termux, and Termux write to shared storage. */
    private suspend fun prepareTermux(c: AdbClient, context: Context) {
        c.shell(
            "pm grant ${context.packageName} com.termux.permission.RUN_COMMAND 2>/dev/null ; " +
                "pm grant $TERMUX_PKG android.permission.READ_EXTERNAL_STORAGE 2>/dev/null ; " +
                "pm grant $TERMUX_PKG android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null ; " +
                "appops set $TERMUX_PKG MANAGE_EXTERNAL_STORAGE allow 2>/dev/null ; " +
                "mkdir -p '$TERMUX_SHARED'"
        )
    }

    /**
     * Waits for Termux to write "done" (ok) or "failed". While the archive grows the wait goes on;
     * if nothing at all happens for a while, Termux most likely does not accept external commands.
     */
    private suspend fun waitForTermux(c: AdbClient, watchFile: String, onProgress: (String) -> Unit): String? {
        var lastSize = -1L
        var idleSince = System.currentTimeMillis()
        var sawActivity = false
        while (true) {
            val out = c.shell(
                "if [ -f '$TERMUX_SHARED/done' ]; then echo DONE; " +
                    "elif [ -f '$TERMUX_SHARED/failed' ]; then echo FAILED; cat '$TERMUX_SHARED/failed'; tail -n 3 '$TERMUX_SHARED/log.txt' 2>/dev/null; " +
                    "else echo WAIT; stat -c %s '$watchFile' 2>/dev/null; [ -f '$TERMUX_SHARED/log.txt' ] && echo LOG; fi"
            ).stdoutText.trim().lines()
            when (out.firstOrNull()) {
                "DONE" -> return null
                "FAILED" -> return out.drop(1).joinToString(" ").ifBlank { "טרמוקס דיווח על שגיאה" }
            }
            val size = out.getOrNull(1)?.toLongOrNull() ?: -1L
            if (out.any { it == "LOG" }) sawActivity = true
            if (size != lastSize) {
                lastSize = size
                idleSince = System.currentTimeMillis()
                if (size > 0) { sawActivity = true; onProgress(Bridge.formatSize(size)) }
            }
            val idle = System.currentTimeMillis() - idleSince
            if (!sawActivity && idle > 45_000) return "NO_RESPONSE"
            if (sawActivity && idle > 10 * 60_000) return "טרמוקס הפסיק להתקדם (10 דקות בלי שינוי)"
            kotlinx.coroutines.delay(2_000)
        }
    }

    private fun termuxHelp(reason: String) = if (reason == "NO_RESPONSE")
        "טרמוקס לא הגיב. צריך לאשר פעם אחת שאפליקציות אחרות ישלחו לו פקודות — בטרמוקס מריצים:\n$TERMUX_SETUP_COMMAND\nואז מנסים שוב."
    else "נכשל: $reason"

    private suspend fun backupTermux(c: AdbClient, context: Context, folder: Uri, onProgress: (String) -> Unit): String {
        if (!isTermuxInstalled(context)) return "טרמוקס לא מותקן — דילוג"
        val cr = context.contentResolver
        prepareTermux(c, context)
        onProgress("טרמוקס: יוצר קובץ גיבוי מלא (home + usr)…")
        runCatching { runInTermux(context, TERMUX_BACKUP_SCRIPT) }.onFailure {
            return "לא ניתן לשלוח פקודה לטרמוקס: ${Bridge.explain(it)} (השאר את מסך הבוס פתוח בזמן הגיבוי)"
        }
        val error = waitForTermux(c, "$TERMUX_SHARED/$TERMUX_ARCHIVE.part") { size -> onProgress("טרמוקס: נארז $size…") }
        if (error != null) return termuxHelp(error)
        onProgress("טרמוקס: מעתיק את הקובץ לתיקיית הגיבוי…")
        val size = c.shell("stat -c %s '$TERMUX_SHARED/$TERMUX_ARCHIVE'").stdoutText.trim().toLongOrNull() ?: 0L
        val ok = runCatching {
            val dir = withContext(Dispatchers.IO) { Saf.mkdir(cr, folder, "termux") }
            val doc = withContext(Dispatchers.IO) { Saf.create(cr, dir, "application/gzip", TERMUX_ARCHIVE) }
            val out = withContext(Dispatchers.IO) { cr.openOutputStream(doc) } ?: error("no stream")
            out.use { c.sync.pull("$TERMUX_SHARED/$TERMUX_ARCHIVE", it) }
            withContext(Dispatchers.IO) {
                Saf.writeText(
                    cr, dir, "README.txt",
                    "Full Termux backup (home + usr) made by The Boss.\n" +
                        "Manual restore inside Termux:\n" +
                        "  cd /data/data/com.termux/files && tar -xzf /path/to/$TERMUX_ARCHIVE && chown -R \$(id -u):\$(id -u) usr home\n",
                )
            }
        }
        c.shell("rm -rf '$TERMUX_SHARED'")
        return if (ok.isSuccess) "גיבוי מלא (${Bridge.formatSize(size)})" else "העתקת הקובץ נכשלה: ${Bridge.explain(ok.exceptionOrNull()!!)}"
    }

    private suspend fun restoreTermux(c: AdbClient, context: Context, tree: Uri, folder: Uri, onProgress: (String) -> Unit): String {
        if (!isTermuxInstalled(context)) return "טרמוקס לא מותקן. אפשר לשחזר אותו קודם עם \"אפליקציות\", ואז לשחזר שוב את טרמוקס."
        val cr = context.contentResolver
        val dir = Saf.child(cr, tree, folder, "termux")?.uri ?: return "אין בגיבוי"
        val archive = Saf.child(cr, tree, dir, TERMUX_ARCHIVE) ?: return "קובץ הגיבוי חסר"
        prepareTermux(c, context)
        onProgress("טרמוקס: מעתיק את קובץ הגיבוי לטלפון…")
        c.shell("rm -f '$TERMUX_SHARED/done' '$TERMUX_SHARED/failed'")
        val pushed = runCatching {
            val input = withContext(Dispatchers.IO) { cr.openInputStream(archive.uri) } ?: error("no stream")
            input.use { c.sync.push(it, "$TERMUX_SHARED/$TERMUX_ARCHIVE") }
        }
        if (pushed.isFailure) return "העתקת הקובץ נכשלה: ${Bridge.explain(pushed.exceptionOrNull()!!)}"
        onProgress("טרמוקס: מחלץ ומשחזר (home + usr)…")
        runCatching { runInTermux(context, TERMUX_RESTORE_SCRIPT) }.onFailure {
            c.shell("rm -rf '$TERMUX_SHARED'")
            return "לא ניתן לשלוח פקודה לטרמוקס: ${Bridge.explain(it)} (השאר את מסך הבוס פתוח בזמן השחזור)"
        }
        val error = waitForTermux(c, "$TERMUX_SHARED/log.txt") { onProgress("טרמוקס: משחזר…") }
        c.shell("rm -rf '$TERMUX_SHARED'")
        if (error != null) return termuxHelp(error)
        // Restart Termux so it starts with the restored files.
        c.shell("am force-stop $TERMUX_PKG")
        return "שוחזר במלואו (${Bridge.formatSize(archive.size)}). טרמוקס הופעל מחדש — פתח אותו."
    }

    // endregion

    // region helpers

    /** Runs many shell commands, 25 per round-trip. Returns how many failed. */
    private suspend fun runBatched(c: AdbClient, commands: List<String>): Int {
        var failed = 0
        for (chunk in commands.chunked(25)) {
            val script = chunk.joinToString("\n") { "$it >/dev/null 2>&1 || echo FAIL" }
            val out = c.shell(script).stdoutText
            failed += Regex("FAIL").findAll(out).count()
        }
        return failed
    }

    /** Single-quotes a value for the device shell. */
    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Runs `content query` and parses its "Row: N a=1, b=2, body=…" output. Columns are parsed
     * in the order requested, and the free-text column goes last so that commas or new lines
     * inside it (SMS bodies, names) cannot break the parsing.
     */
    suspend fun contentQuery(c: AdbClient, uri: String, columns: List<String>, lastColumn: String): List<Map<String, String>> {
        val all = columns + lastColumn
        val out = c.shell("content query --uri $uri --projection ${all.joinToString(":")}").stdoutText
        if (out.isBlank() || out.trim() == "No result found.") return emptyList()
        val rows = Regex("(?m)^Row: \\d+ ").split(out).map { it.trimEnd('\n', '\r') }.filter { it.isNotBlank() }
        return rows.mapNotNull { row ->
            val map = HashMap<String, String>()
            var pos = 0
            for ((i, col) in all.withIndex()) {
                val start = row.indexOf("$col=", pos).takeIf { it >= 0 } ?: return@mapNotNull null
                val valueStart = start + col.length + 1
                val end = if (i == all.lastIndex) row.length else {
                    row.indexOf(", ${all[i + 1]}=", valueStart).takeIf { it >= 0 } ?: return@mapNotNull null
                }
                map[col] = row.substring(valueStart, end)
                pos = end
            }
            map
        }
    }

    private fun toJson(rows: List<Map<String, String>>) = JSONArray().apply {
        rows.forEach { put(JSONObject(it as Map<*, *>)) }
    }

    // endregion
}
