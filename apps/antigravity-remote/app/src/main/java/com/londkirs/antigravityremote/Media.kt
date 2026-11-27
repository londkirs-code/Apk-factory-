package com.londkirs.antigravityremote

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** Thumbnails, sharing, saving and attaching pictures, videos and files. */
object Media {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Loads a thumbnail (picture or video frame) into [target], off the main thread. */
    fun thumb(target: ImageView, path: String, maxPx: Int) {
        val key = "$path@$maxPx"
        target.tag = key
        cache.get(key)?.let { target.setImageBitmap(it); return }
        target.setImageDrawable(null)
        pool.execute {
            val bmp = runCatching { decode(path, maxPx) }.getOrNull()
            if (bmp != null) cache.put(key, bmp)
            main.post { if (target.tag == key) target.setImageBitmap(bmp) }
        }
    }

    fun decode(path: String, maxPx: Int): Bitmap? {
        val mime = MediaRef.mimeOf(path) ?: ""
        if (mime.startsWith("video/")) {
            @Suppress("DEPRECATION")
            val t = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND) ?: return null
            return scale(t, maxPx)
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.let { scale(it, maxPx) }
    }

    private fun scale(b: Bitmap, maxPx: Int): Bitmap {
        val f = maxPx.toFloat() / maxOf(b.width, b.height)
        return if (f < 1f) Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true) else b
    }

    /** Width / height of a picture or video (for laying out bubbles before the thumbnail loads). */
    fun size(path: String): Pair<Int, Int> {
        val mime = MediaRef.mimeOf(path) ?: ""
        if (mime.startsWith("video/")) return runCatching {
            val r = MediaMetadataRetriever(); r.setDataSource(path)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 16
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 9
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
            r.release()
            if (rot == 90 || rot == 270) h to w else w to h
        }.getOrDefault(16 to 9)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return if (o.outWidth > 0) o.outWidth to o.outHeight else 4 to 3
    }

    fun duration(path: String): Long = runCatching {
        val r = MediaMetadataRetriever(); r.setDataSource(path)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        r.release(); d
    }.getOrDefault(0L)

    fun clock(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    fun uriFor(ctx: Context, path: String): Uri = FilesProvider.uri(ctx, File(path))

    fun share(a: Activity, path: String, mime: String) {
        val uri = uriFor(a, path)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        a.startActivity(Intent.createChooser(i, a.tr("שיתוף", "Share")))
    }

    fun openFile(a: Activity, path: String) {
        val f = File(path)
        if (!f.isFile) { Snack.show(a, a.tr("הקובץ לא נמצא", "File not found")); return }
        val mime = MediaRef.mimeOf(path) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(a, path), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { a.startActivity(i) }.onFailure { Snack.show(a, a.tr("אין אפליקציה שפותחת את הקובץ", "No app can open this file")) }
    }

    /** Saves into the phone's Pictures / Movies (visible in the gallery). */
    fun saveToGallery(ctx: Context, path: String, mime: String): Boolean = runCatching {
        val f = File(path)
        val video = mime.startsWith("video/")
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Antigravity_" + f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Antigravity")
            }
            val coll = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = ctx.contentResolver.insert(coll, values)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "Antigravity")
            dir.mkdirs()
            val out = File(dir, "Antigravity_" + f.name)
            f.copyTo(out, overwrite = true)
            @Suppress("DEPRECATION")
            ctx.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(out)))
        }
        true
    }.getOrDefault(false)

    /** Downscales to ≤1600px and encodes as JPEG/base64 (the hub's web UI also sends JPEG). */
    fun encodeImage(ctx: Context, uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw java.io.IOException("decode failed")
        val out = scale(bmp, 1600)
        val bytes = java.io.ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    /** Copies a picked file into the agent's inbox folder so the agent can open it. */
    fun copyToInbox(ctx: Context, uri: Uri): File {
        val name = displayName(ctx, uri).replace(Regex("[^\\w.\\-א-ת ]"), "_").ifBlank { "file" }
        var out = File(ChatEngine.inbox(ctx), name)
        var n = 1
        while (out.exists()) { out = File(ChatEngine.inbox(ctx), name.substringBeforeLast('.') + "-$n" + (if (name.contains('.')) "." + name.substringAfterLast('.') else "")); n++ }
        ctx.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    /** Saves a base64 picture we sent, so our own bubble can show it. */
    fun saveSent(ctx: Context, b64: String): File {
        val f = File(ChatEngine.mediaDir(ctx), "out_${System.currentTimeMillis()}.jpg")
        f.writeBytes(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP))
        return f
    }
}

/**
 * Serves the app's own files to other apps (share, open, camera output) through content:// URIs.
 * Only files inside the app's storage are reachable; access is granted per intent.
 */
class FilesProvider : ContentProvider() {
    companion object {
        fun authority(ctx: Context) = ctx.packageName + ".files"
        fun uri(ctx: Context, f: File): Uri = Uri.Builder().scheme("content").authority(authority(ctx))
            .encodedPath(Uri.encode(f.absolutePath, "/")).build()
    }

    private fun file(uri: Uri): File {
        val f = File(uri.path ?: throw SecurityException()).canonicalFile
        val ctx = context ?: throw SecurityException()
        val roots = listOf(ctx.filesDir.canonicalFile, ctx.cacheDir.canonicalFile)
        if (roots.none { f.path.startsWith(it.path + File.separator) }) throw SecurityException("outside app storage")
        return f
    }

    override fun onCreate() = true
    override fun getType(uri: Uri): String = MediaRef.mimeOf(uri.path ?: "")
        ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(uri.path ?: "").extension.lowercase()) ?: "application/octet-stream"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = file(uri)
        val m = if (mode.contains('w')) {
            f.parentFile?.mkdirs()
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        } else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(f, m)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = file(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols)
        c.addRow(cols.map { if (it == OpenableColumns.SIZE) f.length() else f.name }.toTypedArray())
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
