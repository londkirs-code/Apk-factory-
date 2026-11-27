package com.londkirs.antigravityremote

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the hub web UI's network calls (see assets/recorder.js) into a JSON-lines file so the
 * calls behind each feature can be reproduced in the native chat. Secrets are redacted.
 */
class NetRecorder(private val ctx: Context) {

    companion object {
        private const val MAX_FILE = 25L * 1024 * 1024
        private val SECRET_JSON = Regex(
            """("[A-Za-z0-9_]*(?:[Tt]oken|[Ss]ecret|[Pp]assword|[Aa]pi[Kk]ey|[Cc]ookie|[Aa]uthorization|refresh|access_token)[A-Za-z0-9_]*"\s*:\s*)"[^"]*""""
        )
        private val BEARER = Regex("""(?i)(bearer\s+)[A-Za-z0-9._\-]+""")
        private val GOOGLE_TOKEN = Regex("""ya29\.[A-Za-z0-9._\-]+|1//[A-Za-z0-9._\-]{20,}""")

        fun redact(s: String): String =
            GOOGLE_TOKEN.replace(BEARER.replace(SECRET_JSON.replace(s) { it.groupValues[1] + "\"<redacted>\"" }) { it.groupValues[1] + "<redacted>" }, "<redacted>")
    }

    private val prefs = ctx.getSharedPreferences("recorder", Context.MODE_PRIVATE)
    val file: File get() = File(File(ctx.filesDir, "recordings").apply { mkdirs() }, "current.jsonl")

    /** Host of the page currently shown; only the local hub may write. Set by WebActivity. */
    @Volatile var pageIsHub = false

    var enabled: Boolean
        get() = prefs.getBoolean("on", false)
        set(v) { prefs.edit().putBoolean("on", v).apply() }

    @JavascriptInterface
    fun enabled(): Boolean = enabled && pageIsHub

    @JavascriptInterface
    fun log(line: String) {
        if (!enabled()) return
        append(line)
    }

    fun note(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        append("{\"t\":\"note\",\"ts\":${System.currentTimeMillis()},\"text\":\"$escaped\"}")
    }

    @Synchronized private fun append(line: String) {
        val f = file
        if (f.length() > MAX_FILE) return
        f.appendText(redact(line.replace('\n', ' ')) + "\n")
    }

    fun entries(): Int = runCatching { file.useLines { it.count() } }.getOrDefault(0)

    fun clear() { file.delete() }

    /** Copies the recording to Downloads and returns a share Intent (null if empty). */
    fun export(): Intent? {
        val src = file
        if (!src.isFile || src.length() == 0L) return null
        val name = "agy-recording-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".jsonl"
        val uri: Uri = if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            ctx.contentResolver.openOutputStream(u)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            u
        } else {
            // Before Android 10: share the text itself (file:// URIs are not allowed).
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, name)
                putExtra(Intent.EXTRA_TEXT, src.readText().takeLast(400_000))
            }
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
