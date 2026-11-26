package com.londkirs.apkbridge

import android.net.Uri
import android.os.Build
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.io.RandomAccessSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import com.londkirs.apkbridge.Bridge.app
import com.londkirs.apkbridge.Bridge.status
import com.londkirs.apkbridge.Bridge.log
import com.londkirs.apkbridge.Bridge.update
import com.londkirs.apkbridge.Bridge.withClient
import com.londkirs.apkbridge.Bridge.runJob
import com.londkirs.apkbridge.Bridge.formatSize
import com.londkirs.apkbridge.Bridge.displayName
import com.londkirs.apkbridge.Bridge.sizeOf
import com.londkirs.apkbridge.Bridge.sanitize
import com.londkirs.apkbridge.Bridge.KNOWN_ABIS
import com.londkirs.apkbridge.Bridge.PLAY_STORE
import com.londkirs.apkbridge.Bridge.state
import com.londkirs.apkbridge.Bridge.openAfterInstall
import com.londkirs.apkbridge.Bridge.installNotifications
import com.londkirs.apkbridge.Bridge.isScreenListening
import com.londkirs.apkbridge.Bridge.notifyInfo

/**
 * Installing APK / APKS / XAPK / APKM files over ADB: unpacking bundles, picking the splits that
 * match the device, OBB files, install flags, the result notification and the APK preview.
 * (Moved out of Bridge.kt; uses its connection, log and state.)
 */
object Installer {

    private class InstallUnit(val label: String, val apks: List<File>, val obbs: List<Pair<String, File>>, val packageName: String?)

    fun install(uris: List<Uri>, options: InstallOptions) = runJob(tr("ההתקנה", "Install")) {
        require(uris.isNotEmpty()) { tr("לא נבחרו קבצים.", "No files selected.") }
        val work = File(app.cacheDir, "install-work").apply { deleteRecursively(); mkdirs() }
        try {
            status(tr("מכין ${uris.size} קבצים…", "Preparing ${uris.size} files…"))
            val units = prepareUnits(uris, work)
            var ok = 0
            withClient { client ->
                for ((i, unit) in units.withIndex()) {
                    val prefix = if (units.size > 1) "(${i + 1}/${units.size}) " else ""
                    installUnit(client, unit, options, prefix)
                    ok++
                }
            }
            if (units.size > 1) status(tr("הותקנו בהצלחה $ok מתוך ${units.size} חבילות.", "Installed $ok of ${units.size} packages successfully."), 100)
        } finally {
            withContext(Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    private suspend fun installUnit(client: AdbClient, unit: InstallUnit, options: InstallOptions, prefix: String) {
        val flags = buildList {
            add("-r")
            if (options.allowDowngrade) add("-d")
            if (options.grantPermissions) add("-g")
            if (options.allowTestPackages) add("-t")
            if (options.bypassLowTargetSdk && Build.VERSION.SDK_INT >= 34) add("--bypass-low-target-sdk-block")
            if (options.registerPlayInstaller) { add("-i"); add(PLAY_STORE) }
        }
        val total = unit.apks.sumOf { it.length() }
        status(tr("${prefix}מתקין ${unit.label} (${unit.apks.size} APK, ${formatSize(total)})…", "${prefix}Installing ${unit.label} (${unit.apks.size} APK, ${formatSize(total)})…"), 0)
        val flow = if (unit.apks.size == 1) {
            client.install.install(RandomAccessSource.of(unit.apks[0]), flags)
        } else {
            client.install.installMultiple(unit.apks.map { it.name to RandomAccessSource.of(it) }, flags)
        }
        var lastPercent = -1
        flow.collect { p ->
            val percent = p.percentage.toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                update { it.copy(progress = percent, status = tr("${prefix}מעביר ${unit.label}: $percent%", "${prefix}Transferring ${unit.label}: $percent%")) }
            }
        }
        log(tr("${prefix}Package Manager החזיר Success.", "${prefix}Package Manager returned Success."))

        val pkg = unit.packageName
        if (pkg != null && unit.obbs.isNotEmpty()) pushObbs(client, pkg, unit.obbs)
        if (pkg == null) {
            status(tr("${prefix}ההתקנה הסתיימה בהצלחה.", "${prefix}Install finished successfully."), 100)
            return
        }
        val line = client.shell("pm list packages -i --show-versioncode ${shellQuote(pkg)}").stdoutText
            .lines().map { it.trim() }
            .firstOrNull { it == "package:$pkg" || it.startsWith("package:$pkg ") }
        val installer = line?.substringAfter("installer=", "")?.substringBefore(' ')?.takeIf { it.isNotBlank() && it != "null" }
        val version = line?.substringAfter("versionCode:", "")?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        update { it.copy(lastInstalledPackage = pkg) }
        val installerText = when {
            installer == PLAY_STORE -> tr("מקור ההתקנה הרשום: Google Play (com.android.vending)", "Recorded install source: Google Play (com.android.vending)")
            installer != null -> tr("מקור ההתקנה הרשום: $installer", "Recorded install source: $installer")
            else -> tr("מקור ההתקנה: לא רשום", "Install source: not recorded")
        }
        val label = appLabel(pkg) ?: pkg
        status(
            tr("${prefix}✔ $label ($pkg) הותקן בהצלחה", "${prefix}✔ $label ($pkg) installed successfully") + (version?.let { " (versionCode $it)" } ?: "") + ".\n$installerText",
            100,
        )
        update { it.copy(lastInstalledPackage = pkg, installSeq = it.installSeq + 1) }
        // When the app screen is not visible, offer "open" through a notification instead of a dialog.
        if (!isScreenListening && installNotifications) {
            val launch = app.packageManager.getLaunchIntentForPackage(pkg)?.takeIf { openAfterInstall != "never" }
            notifyInfo(tr("✔ $label הותקן", "✔ $label installed"), if (launch != null) tr("הקש כדי לפתוח את האפליקציה.", "Tap to open the app.") else tr("ההתקנה הושלמה.", "Install complete."), launch)
        }
    }

    fun appLabel(pkg: String): String? = runCatching {
        val pm = app.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    /** Reads name/version of a single selected .apk and compares it to the installed version. */
    suspend fun preview(uri: Uri): ApkPreview? = withContext(Dispatchers.IO) {
        runCatching {
            val size = sizeOf(uri)
            if (size > 400L * 1024 * 1024) return@runCatching null
            val file = File(app.cacheDir, "preview.apk")
            app.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it, 256 * 1024) }
            } ?: return@runCatching null
            try {
                val pm = app.packageManager
                val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return@runCatching null
                val ai = info.applicationInfo
                val label = if (ai != null) {
                    ai.sourceDir = file.absolutePath
                    ai.publicSourceDir = file.absolutePath
                    pm.getApplicationLabel(ai).toString()
                } else info.packageName
                val installed = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull()
                ApkPreview(
                    label = label,
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = Compat.versionCode(info),
                    installedVersionName = installed?.versionName,
                    installedVersionCode = installed?.let { Compat.versionCode(it) },
                )
            } finally {
                file.delete()
            }
        }.getOrNull()
    }

    private suspend fun pushObbs(client: AdbClient, pkg: String, obbs: List<Pair<String, File>>) {
        val dir = "/sdcard/Android/obb/$pkg"
        client.shell("mkdir -p ${shellQuote(dir)}")
        for ((name, file) in obbs) {
            val safe = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            log(tr("מעתיק קובץ OBB: $safe (${formatSize(file.length())})", "Copying OBB file: $safe (${formatSize(file.length())})"))
            val size = file.length().coerceAtLeast(1)
            FileInputStream(file).use { input ->
                client.sync.push(input, "$dir/$safe") { done ->
                    update { it.copy(progress = (done * 100 / size).toInt(), status = tr("מעתיק OBB: ${done * 100 / size}%", "Copying OBB: ${done * 100 / size}%")) }
                }
            }
        }
        log(tr("קובצי OBB הועתקו אל $dir.", "OBB files copied to $dir."))
    }

    private suspend fun prepareUnits(uris: List<Uri>, work: File): List<InstallUnit> = withContext(Dispatchers.IO) {
        val bundles = mutableListOf<InstallUnit>()
        val loose = mutableListOf<File>()
        for ((index, uri) in uris.withIndex()) {
            val name = displayName(uri)
            val copy = File(work, "src-$index-${sanitize(name)}")
            app.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it, 256 * 1024) }
            } ?: error(tr("לא ניתן לקרוא את הקובץ $name", "Can't read the file $name"))
            if (copy.length() == 0L) error(tr("הקובץ $name ריק.", "The file $name is empty."))
            log(tr("נקרא $name (${formatSize(copy.length())}).", "Read $name (${formatSize(copy.length())})."))

            val entries = runCatching { ZipFile(copy).use { z -> z.entries().toList().filter { !it.isDirectory }.map { it.name } } }
                .getOrElse { error(tr("$name אינו APK או חבילת APKS/XAPK/APKM תקינה.", "$name isn't a valid APK or APKS/XAPK/APKM package.")) }
            if ("AndroidManifest.xml" in entries) {
                loose += copy
                continue
            }
            val apkEntries = entries.filter { it.endsWith(".apk", ignoreCase = true) }
            if (apkEntries.isEmpty()) error(tr("$name אינו מכיל קובצי APK. ייתכן שהוא מוצפן או פגום.", "$name contains no APK files. It may be encrypted or damaged."))
            val dir = File(work, "bundle-$index").apply { mkdirs() }
            val apks = mutableListOf<File>()
            val obbs = mutableListOf<Pair<String, File>>()
            ZipFile(copy).use { zip ->
                for ((n, entryName) in entries.withIndex()) {
                    val isApk = entryName.endsWith(".apk", true)
                    val isObb = entryName.endsWith(".obb", true)
                    if (!isApk && !isObb) continue
                    val out = File(dir, "$n-${sanitize(entryName.substringAfterLast('/'))}")
                    zip.getInputStream(zip.getEntry(entryName)).use { input -> out.outputStream().use { input.copyTo(it, 256 * 1024) } }
                    if (isApk) apks += out else obbs += entryName to out
                }
            }
            copy.delete()
            val filtered = filterAbiSplits(apks)
            val pkg = filtered.firstNotNullOfOrNull { packageNameOf(it) }
            log(tr("$name: חבילה עם ${filtered.size} קובצי APK", "$name: package with ${filtered.size} APK files") + (if (obbs.isNotEmpty()) tr(" ו־${obbs.size} קובצי OBB", " and ${obbs.size} OBB files") else "") + (pkg?.let { " ($it)" } ?: "") + ".")
            bundles += InstallUnit(pkg ?: name, filtered, obbs, pkg)
        }

        if (loose.isNotEmpty()) {
            val byPkg = linkedMapOf<String, MutableList<File>>()
            val unknown = mutableListOf<File>()
            for (f in loose) {
                val pkg = packageNameOf(f)
                if (pkg == null) unknown += f else byPkg.getOrPut(pkg) { mutableListOf() } += f
            }
            when {
                unknown.isNotEmpty() && byPkg.size == 1 -> byPkg.values.first() += unknown
                unknown.isNotEmpty() && byPkg.isEmpty() -> byPkg["?"] = unknown
                unknown.isNotEmpty() -> error(tr("לא ניתן לזהות את שם החבילה של ${unknown.size} מהקבצים שנבחרו.", "Can't identify the package name of ${unknown.size} of the selected files."))
            }
            for ((pkg, files) in byPkg) {
                val real = pkg.takeIf { it != "?" }
                bundles += InstallUnit(real ?: files.first().name, filterAbiSplits(files), emptyList(), real)
            }
        }
        bundles
    }

    /** Drops native-library splits for ABIs this device cannot run. */
    private fun filterAbiSplits(apks: List<File>): List<File> {
        val supported = Build.SUPPORTED_ABIS.map { it.replace('-', '_').lowercase() }.toSet()
        fun abiOf(f: File): String? {
            val n = f.name.lowercase()
            return KNOWN_ABIS.firstOrNull { n.contains("config.$it.") || n.endsWith(".$it.apk") || n.endsWith("_$it.apk") }
        }
        val abiSplits = apks.filter { abiOf(it) != null }
        if (abiSplits.isEmpty()) return apks
        val keep = abiSplits.filter { abiOf(it) in supported }
        if (keep.isEmpty()) return apks
        val dropped = abiSplits - keep.toSet()
        if (dropped.isNotEmpty()) log(tr("דילוג על ${dropped.size} חלקי ABI שאינם מתאימים למעבד של המכשיר.", "Skipping ${dropped.size} ABI parts that don't match the device's processor."))
        return apks.filter { abiOf(it) == null || it in keep }
    }

    private fun packageNameOf(file: File): String? = runCatching {
        app.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
    }.getOrNull()

}
