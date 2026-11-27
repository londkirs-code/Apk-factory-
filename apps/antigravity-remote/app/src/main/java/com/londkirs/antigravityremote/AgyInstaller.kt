package com.londkirs.antigravityremote

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Installs Google's official Android (Bionic, NDK-built) build of the Antigravity CLI.
 * This is the same artifact the official install.sh picks for Termux (platform "android_arm64").
 * The binary is downloaded from Google at runtime and verified against the manifest's SHA-512;
 * it is never bundled in or redistributed with this APK.
 */
object AgyInstaller {
    private const val MANIFEST_URL =
        "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/android_arm64.json"

    data class Release(val version: String, val url: String, val sha512: String)

    fun interface Progress {
        fun update(stage: Int, done: Long, total: Long)
    }

    const val STAGE_MANIFEST = 0
    const val STAGE_DOWNLOAD = 1
    const val STAGE_VERIFY = 2
    const val STAGE_EXTRACT = 3

    fun isSupportedDevice(): Boolean = Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }

    fun root(ctx: Context) = ctx.filesDir
    fun binDir(ctx: Context) = File(ctx.filesDir, "bin")
    fun homeDir(ctx: Context) = File(ctx.filesDir, "home")
    fun tmpDir(ctx: Context) = File(ctx.filesDir, "tmp")
    fun binary(ctx: Context) = File(binDir(ctx), "agy")
    private fun versionFile(ctx: Context) = File(ctx.filesDir, "agy.version")

    fun isInstalled(ctx: Context) = binary(ctx).let { it.isFile && it.canExecute() && it.length() > 0 }

    fun installedVersion(ctx: Context): String? =
        versionFile(ctx).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    fun fetchRelease(): Release {
        val json = JSONObject(httpGet(MANIFEST_URL).use { it.bufferedReader().readText() })
        val url = json.getString("url")
        val sha = json.getString("sha512").lowercase()
        require(url.startsWith("https://")) { "Unexpected download URL" }
        require(Regex("^[0-9a-f]{128}$").matches(sha)) { "Malformed checksum in manifest" }
        return Release(json.optString("version", "?"), url, sha)
    }

    /** Full install/update. Blocking: call from a background thread. */
    fun install(ctx: Context, progress: Progress): Release {
        progress.update(STAGE_MANIFEST, 0, 0)
        val release = fetchRelease()
        prepareDirs(ctx)

        val payload = File(ctx.cacheDir, "agy-download.tar.gz")
        val digest = MessageDigest.getInstance("SHA-512")
        val conn = open(release.url)
        val total = conn.contentLengthLong
        DigestInputStream(BufferedInputStream(conn.inputStream), digest).use { input ->
            FileOutputStream(payload).use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastReport = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done - lastReport > 256 * 1024) {
                        progress.update(STAGE_DOWNLOAD, done, total); lastReport = done
                    }
                }
                progress.update(STAGE_DOWNLOAD, done, total)
            }
        }
        conn.disconnect()

        progress.update(STAGE_VERIFY, 0, 0)
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != release.sha512) {
            payload.delete()
            throw IOException("SHA-512 mismatch: the download is corrupted or was tampered with")
        }

        progress.update(STAGE_EXTRACT, 0, payload.length())
        val staged = File(binDir(ctx), "agy.new")
        val found = if (release.url.contains(".tar.gz")) {
            GZIPInputStream(BufferedInputStream(payload.inputStream()), 64 * 1024).use { extractTarEntry(it, "antigravity", staged) }
        } else {
            payload.copyTo(staged, overwrite = true); true
        }
        payload.delete()
        if (!found) throw IOException("The release archive does not contain the 'antigravity' binary")
        if (!staged.setExecutable(true, true)) throw IOException("Could not mark the binary executable")
        val target = binary(ctx)
        if (!staged.renameTo(target)) {
            target.delete()
            if (!staged.renameTo(target)) throw IOException("Could not move the binary into place")
        }
        versionFile(ctx).writeText(release.version)
        return release
    }

    fun uninstall(ctx: Context) {
        binary(ctx).delete()
        versionFile(ctx).delete()
    }

    fun prepareDirs(ctx: Context) {
        listOf(binDir(ctx), homeDir(ctx), tmpDir(ctx), File(homeDir(ctx), "projects")).forEach { it.mkdirs() }
        // BROWSER helper: agy calls it with the sign-in URL; the app watches this file and opens the URL.
        val opener = File(binDir(ctx), "open-url")
        val script = "#!/system/bin/sh\n" +
            "printf '%s\\n' \"\$1\" > \"${ctx.filesDir.absolutePath}/.open-url.tmp\" && " +
            "mv \"${ctx.filesDir.absolutePath}/.open-url.tmp\" \"${ctx.filesDir.absolutePath}/.open-url\"\n"
        if (!opener.isFile || opener.readText() != script) {
            opener.writeText(script)
        }
        opener.setExecutable(true, true)
        listOf("xdg-open", "termux-open-url", "termux-open").forEach { name ->
            val f = File(binDir(ctx), name)
            if (!f.exists()) {
                f.writeText("#!/system/bin/sh\nexec \"${opener.absolutePath}\" \"\$@\"\n")
                f.setExecutable(true, true)
            }
        }
    }

    /** Minimal ustar/GNU/PAX reader: streams out one regular file by name. */
    private fun extractTarEntry(input: InputStream, wanted: String, out: File): Boolean {
        val header = ByteArray(512)
        var longName: String? = null
        while (true) {
            if (!readFully(input, header)) return false
            if (header.all { it == 0.toByte() }) return false
            val rawName = cString(header, 0, 100)
            val prefix = cString(header, 345, 155)
            val size = parseOctal(header, 124, 12)
            val type = header[156].toInt().toChar()
            var name = longName ?: if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
            longName = null
            when (type) {
                'L' -> { // GNU long name for the next entry
                    val data = ByteArray(size.toInt()); readFully(input, data); skipPadding(input, size)
                    longName = String(data).trimEnd('\u0000'); continue
                }
                'x' -> { // PAX header: honour "path"
                    val data = ByteArray(size.toInt()); readFully(input, data); skipPadding(input, size)
                    Regex("""\d+ path=([^\n]+)\n""").find(String(data))?.let { longName = it.groupValues[1] }
                    continue
                }
            }
            name = name.removePrefix("./")
            val regular = type == '0' || type == '\u0000'
            if (regular && name == wanted) {
                FileOutputStream(out).use { os ->
                    val buf = ByteArray(64 * 1024)
                    var left = size
                    while (left > 0) {
                        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) throw IOException("Truncated archive")
                        os.write(buf, 0, n); left -= n
                    }
                }
                return true
            }
            skipFully(input, size); skipPadding(input, size)
        }
    }

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun parseOctal(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) { // GNU base-256 size
            var v = 0L
            for (i in off + 1 until off + len) v = (v shl 8) or (b[i].toLong() and 0xff)
            return v
        }
        val s = cString(b, off, len).trim()
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) return read == buf.size
            read += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("Truncated archive")
            left -= n
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val pad = (512 - (size % 512)) % 512
        skipFully(input, pad)
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "antigravity-android-launcher")
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for $url")
        return c
    }

    private fun httpGet(url: String): InputStream = open(url).inputStream
}
