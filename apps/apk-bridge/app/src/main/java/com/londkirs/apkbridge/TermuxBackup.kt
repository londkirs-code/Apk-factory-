package com.londkirs.apkbridge

import android.content.Context
import android.net.Uri
import io.github.rhythmcache.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full Termux backup / restore. Termux's files are private to Termux, so Termux itself packs and
 * unpacks them (through its RUN_COMMAND intent, with the full command — no aliases needed), into a
 * shared-storage folder that ADB copies to / from the backup.
 */
object TermuxBackup {
    const val PKG = "com.termux"
    private const val TERMUX_FILES = "/data/data/com.termux/files"
    private const val TERMUX_BASH = "$TERMUX_FILES/usr/bin/bash"
    /** Shared-storage folder where Termux writes / reads the archive (Termux can, ADB can too). */
    private const val TERMUX_SHARED = "/sdcard/TheBoss/termux"
    private const val TERMUX_ARCHIVE = "termux_full_backup.tar.gz"

    /** What the user has to run once in Termux so other apps may send it commands. */
    const val SETUP_COMMAND =
        "mkdir -p ~/.termux && echo \"allow-external-apps = true\" >> ~/.termux/termux.properties && termux-reload-settings"

    fun isInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)


    enum class Status { READY, NOT_INSTALLED, NO_ACCESS, NO_CONNECTION }

    /**
     * Checks that Termux is installed and accepts commands from The Boss: grants what ADB can,
     * sends a tiny command, and waits for the answer file.
     */
    fun check(context: Context, onResult: (Status, String?) -> Unit) {
        val app = context.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        if (!isInstalled(app)) { onResult(Status.NOT_INSTALLED, null); return }
        Bridge.scope.launch {
            val result: Pair<Status, String?> = try {
                Bridge.withClient { c ->
                    prepareTermux(c, app)
                    c.shell("rm -f '$TERMUX_SHARED/ping'")
                    runInTermux(app, "mkdir -p '$TERMUX_SHARED' && echo ok > '$TERMUX_SHARED/ping'")
                    var ok = false
                    repeat(12) {
                        if (!ok) {
                            kotlinx.coroutines.delay(1000)
                            ok = c.shell("cat '$TERMUX_SHARED/ping' 2>/dev/null").stdoutText.trim() == "ok"
                        }
                    }
                    c.shell("rm -f '$TERMUX_SHARED/ping'")
                    (if (ok) Status.READY else Status.NO_ACCESS) to null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Status.NO_CONNECTION to Bridge.explain(e)
            }
            main.post { onResult(result.first, result.second) }
        }
    }

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
            .setClassName(PKG, "com.termux.app.RunCommandService")
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
                "pm grant $PKG android.permission.READ_EXTERNAL_STORAGE 2>/dev/null ; " +
                "pm grant $PKG android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null ; " +
                "appops set $PKG MANAGE_EXTERNAL_STORAGE allow 2>/dev/null ; " +
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
                "FAILED" -> return out.drop(1).joinToString(" ").ifBlank { tr("טרמוקס דיווח על שגיאה", "Termux reported an error") }
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
            if (sawActivity && idle > 10 * 60_000) return tr("טרמוקס הפסיק להתקדם (10 דקות בלי שינוי)", "Termux stopped progressing (10 minutes without change)")
            kotlinx.coroutines.delay(2_000)
        }
    }

    private fun termuxHelp(reason: String) = if (reason == "NO_RESPONSE")
        tr("טרמוקס לא הגיב. צריך לאשר פעם אחת שאפליקציות אחרות ישלחו לו פקודות — בטרמוקס מריצים:\n$SETUP_COMMAND\nואז מנסים שוב.", "Termux didn't respond. You need to allow once that other apps may send it commands — in Termux run:\n$SETUP_COMMAND\nthen try again.")
    else tr("נכשל: $reason", "failed: $reason")

    suspend fun backup(c: AdbClient, context: Context, folder: Uri, onProgress: (String) -> Unit): String {
        if (!isInstalled(context)) return tr("טרמוקס לא מותקן — דילוג", "Termux isn't installed — skipped")
        val cr = context.contentResolver
        prepareTermux(c, context)
        onProgress(tr("טרמוקס: יוצר קובץ גיבוי מלא (home + usr)…", "Termux: creating a full backup file (home + usr)…"))
        runCatching { runInTermux(context, TERMUX_BACKUP_SCRIPT) }.onFailure {
            return tr("לא ניתן לשלוח פקודה לטרמוקס: ${Bridge.explain(it)} (השאר את מסך הבוס פתוח בזמן הגיבוי)", "Can't send a command to Termux: ${Bridge.explain(it)} (keep The Boss open during the backup)")
        }
        val error = waitForTermux(c, "$TERMUX_SHARED/$TERMUX_ARCHIVE.part") { size -> onProgress(tr("טרמוקס: נארז $size…", "Termux: packed $size…")) }
        if (error != null) return termuxHelp(error)
        onProgress(tr("טרמוקס: מעתיק את הקובץ לתיקיית הגיבוי…", "Termux: copying the file to the backup folder…"))
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
        return if (ok.isSuccess) tr("גיבוי מלא (${Bridge.formatSize(size)})", "full backup (${Bridge.formatSize(size)})") else tr("העתקת הקובץ נכשלה: ${Bridge.explain(ok.exceptionOrNull()!!)}", "copying the file failed: ${Bridge.explain(ok.exceptionOrNull()!!)}")
    }

    suspend fun restore(c: AdbClient, context: Context, tree: Uri, folder: Uri, onProgress: (String) -> Unit): String {
        if (!isInstalled(context)) return tr("טרמוקס לא מותקן. אפשר לשחזר אותו קודם עם \"אפליקציות\", ואז לשחזר שוב את טרמוקס.", "Termux isn't installed. You can restore it first with \"Apps\", then restore Termux again.")
        val cr = context.contentResolver
        val dir = Saf.child(cr, tree, folder, "termux")?.uri ?: return tr("אין בגיבוי", "not in the backup")
        val archive = Saf.child(cr, tree, dir, TERMUX_ARCHIVE) ?: return tr("קובץ הגיבוי חסר", "backup file missing")
        prepareTermux(c, context)
        onProgress(tr("טרמוקס: מעתיק את קובץ הגיבוי לטלפון…", "Termux: copying the backup file to the phone…"))
        c.shell("rm -f '$TERMUX_SHARED/done' '$TERMUX_SHARED/failed'")
        val pushed = runCatching {
            val input = withContext(Dispatchers.IO) { cr.openInputStream(archive.uri) } ?: error("no stream")
            input.use { c.sync.push(it, "$TERMUX_SHARED/$TERMUX_ARCHIVE") }
        }
        if (pushed.isFailure) return tr("העתקת הקובץ נכשלה: ${Bridge.explain(pushed.exceptionOrNull()!!)}", "copying the file failed: ${Bridge.explain(pushed.exceptionOrNull()!!)}")
        onProgress(tr("טרמוקס: מחלץ ומשחזר (home + usr)…", "Termux: extracting and restoring (home + usr)…"))
        runCatching { runInTermux(context, TERMUX_RESTORE_SCRIPT) }.onFailure {
            c.shell("rm -rf '$TERMUX_SHARED'")
            return tr("לא ניתן לשלוח פקודה לטרמוקס: ${Bridge.explain(it)} (השאר את מסך הבוס פתוח בזמן השחזור)", "Can't send a command to Termux: ${Bridge.explain(it)} (keep The Boss open during the restore)")
        }
        val error = waitForTermux(c, "$TERMUX_SHARED/log.txt") { onProgress(tr("טרמוקס: משחזר…", "Termux: restoring…")) }
        c.shell("rm -rf '$TERMUX_SHARED'")
        if (error != null) return termuxHelp(error)
        // Restart Termux so it starts with the restored files.
        c.shell("am force-stop $PKG")
        return tr("שוחזר במלואו (${Bridge.formatSize(archive.size)}). טרמוקס הופעל מחדש — פתח אותו.", "fully restored (${Bridge.formatSize(archive.size)}). Termux was restarted — open it.")
    }

    private fun q(value: String) = shellQuote(value)
}
