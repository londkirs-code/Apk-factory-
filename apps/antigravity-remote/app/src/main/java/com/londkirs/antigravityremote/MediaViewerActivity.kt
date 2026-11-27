package com.londkirs.antigravityremote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import kotlin.math.abs

/** Full-screen pictures and videos: pinch to zoom, swipe between items, share, save, reply. */
class MediaViewerActivity : BaseActivity() {
    companion object {
        private const val EXTRA_PATHS = "paths"
        private const val EXTRA_MIMES = "mimes"
        private const val EXTRA_INDEX = "index"
        const val EXTRA_REPLY_PATH = "reply_path"
        const val REQ = 77

        fun open(a: Activity, list: List<MediaRef>, index: Int, forResult: Boolean = false) {
            val i = Intent(a, MediaViewerActivity::class.java)
                .putExtra(EXTRA_PATHS, list.map { it.path }.toTypedArray())
                .putExtra(EXTRA_MIMES, list.map { it.mime }.toTypedArray())
                .putExtra(EXTRA_INDEX, index)
            if (forResult) a.startActivityForResult(i, REQ) else a.startActivity(i)
        }
    }

    private lateinit var items: List<MediaRef>
    private var index = 0
    private lateinit var stage: FrameLayout
    private lateinit var chrome: LinearLayout
    private lateinit var counter: TextView
    private lateinit var controls: LinearLayout
    private var video: VideoView? = null
    private var image: ZoomImageView? = null
    private val handler = Handler()
    private var chromeVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paths = intent.getStringArrayExtra(EXTRA_PATHS) ?: emptyArray()
        val mimes = intent.getStringArrayExtra(EXTRA_MIMES) ?: emptyArray()
        items = paths.indices.map { MediaRef(paths[it], mimes.getOrElse(it) { MediaRef.mimeOf(paths[it]) ?: "image/*" }) }
        if (items.isEmpty()) { finish(); return }
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, items.size - 1)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        stage = FrameLayout(this)
        root.addView(stage, flp(MATCH, MATCH))

        chrome = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        chrome.addView(Ui.iconButton(this, R.drawable.ic_back, tr("חזרה", "Back"), Color.WHITE) { finish() })
        counter = Ui.text(this, "", 16f, Color.WHITE, bold = true)
        chrome.addView(counter, lp(0, WRAP, 1f))
        if (callingActivity != null) chrome.addView(Ui.iconButton(this, R.drawable.ic_reply, tr("השב על זה", "Reply to this"), Color.WHITE) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_REPLY_PATH, items[index].path)); finish()
        })
        chrome.addView(Ui.iconButton(this, R.drawable.ic_share, tr("שיתוף", "Share"), Color.WHITE) { Media.share(this, items[index].path, items[index].mime) })
        chrome.addView(Ui.iconButton(this, R.drawable.ic_save, tr("שמירה לגלריה", "Save to gallery"), Color.WHITE) { save() })
        root.addView(chrome, flp(MATCH, WRAP, Gravity.TOP))

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(8), dp(8), dp(16), dp(16))
            visibility = View.GONE
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        root.addView(controls, flp(MATCH, WRAP, Gravity.BOTTOM))
        setContentView(root)
        show(0)
    }

    private fun save() {
        val m = items[index]
        Thread {
            val ok = Media.saveToGallery(this, m.path, m.mime)
            runOnUiThread {
                if (ok) snack(tr("נשמר בגלריה", "Saved to gallery"))
                else if (Build.VERSION.SDK_INT < 29) {
                    requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 5)
                } else snack(tr("השמירה נכשלה", "Saving failed"))
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 5 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) save()
    }

    private fun toggleChrome() {
        chromeVisible = !chromeVisible
        val a = if (chromeVisible) 1f else 0f
        chrome.animate().alpha(a).setDuration(150).start()
        controls.animate().alpha(a).setDuration(150).start()
    }

    private fun show(direction: Int) {
        handler.removeCallbacksAndMessages(null)
        video?.stopPlayback()
        stage.removeAllViews()
        video = null; image = null
        val m = items[index]
        counter.text = if (items.size > 1) "${index + 1} / ${items.size}" else m.name
        if (m.isVideo) showVideo(m) else showImage(m)
        if (direction != 0) {
            stage.translationX = direction * stage.width * 0.25f; stage.alpha = 0f
            stage.animate().translationX(0f).alpha(1f).setDuration(180).start()
        }
    }

    private fun go(delta: Int) {
        val next = index + delta
        if (next !in items.indices) return
        index = next; show(delta)
    }

    private fun showImage(m: MediaRef) {
        controls.visibility = View.GONE
        val iv = ZoomImageView(this) { dir -> go(dir) }
        iv.onTap = { toggleChrome() }
        image = iv
        stage.addView(iv, flp(MATCH, MATCH))
        Thread {
            var animated: android.graphics.drawable.Drawable? = null
            if (Build.VERSION.SDK_INT >= 28 && (m.mime == "image/gif" || m.mime == "image/webp")) {
                animated = runCatching { android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(java.io.File(m.path))) }.getOrNull()
            }
            val bmp = if (animated == null) Media.decode(m.path, 2400) else null
            runOnUiThread {
                if (animated != null) {
                    iv.setImageDrawable(animated)
                    if (Build.VERSION.SDK_INT >= 28 && animated is android.graphics.drawable.AnimatedImageDrawable) animated.start()
                } else if (bmp != null) iv.setImageBitmap(bmp)
                else snack(tr("לא ניתן להציג את הקובץ", "Cannot show this file"))
            }
        }.start()
    }

    private fun showVideo(m: MediaRef) {
        val vv = VideoView(this)
        video = vv
        val holder = FrameLayout(this)
        holder.addView(vv, flp(MATCH, WRAP, Gravity.CENTER))
        stage.addView(holder, flp(MATCH, MATCH))
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { toggleChrome(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || abs(vx) < abs(vy) * 1.5f) return false
                val rtl = window.decorView.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val d = if (vx < 0) 1 else -1
                go(if (rtl) -d else d); return true
            }
        })
        holder.setOnTouchListener { _, e -> detector.onTouchEvent(e); true }

        controls.removeAllViews(); controls.visibility = View.VISIBLE
        val play = Ui.iconButton(this, R.drawable.ic_pause, tr("ניגון", "Play"), Color.WHITE) {}
        val time = Ui.text(this, "0:00", 13f, Color.WHITE)
        val seek = ProgressLine(this).apply { fraction = 0f }
        controls.addView(play)
        controls.addView(seek, lp(0, dp(6), 1f).apply { marginEnd = dp(12) })
        controls.addView(time)
        play.setOnClickListener {
            if (vv.isPlaying) { vv.pause(); play.setImageResource(R.drawable.ic_play) } else { vv.start(); play.setImageResource(R.drawable.ic_pause); tick(vv, seek, time) }
        }
        seek.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                val f = (e.x / v.width).coerceIn(0f, 1f)
                if (vv.duration > 0) { vv.seekTo((vv.duration * f).toInt()); seek.fraction = f }
            }
            true
        }
        vv.setOnPreparedListener { mp -> mp.isLooping = false; vv.start(); tick(vv, seek, time) }
        vv.setOnCompletionListener { play.setImageResource(R.drawable.ic_play) }
        vv.setOnErrorListener { _, _, _ -> snack(tr("לא ניתן לנגן את הסרטון", "Cannot play this video")); true }
        vv.setVideoPath(m.path)
    }

    private fun tick(vv: VideoView, seek: ProgressLine, time: TextView) {
        handler.removeCallbacksAndMessages(null)
        val r = object : Runnable {
            override fun run() {
                if (video !== vv) return
                val d = vv.duration
                if (d > 0) seek.fraction = vv.currentPosition.toFloat() / d
                time.text = Media.clock(vv.currentPosition.toLong()) + " / " + Media.clock(maxOf(0, d).toLong())
                if (vv.isPlaying) handler.postDelayed(this, 250)
            }
        }
        handler.post(r)
    }

    override fun onPause() { video?.pause(); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); video?.stopPlayback(); super.onDestroy() }
}

/** ImageView with pinch zoom, double-tap zoom, panning, and fling-to-next when not zoomed. */
class ZoomImageView(ctx: Context, private val onSwipe: (Int) -> Unit) : ImageView(ctx) {
    var onTap: (() -> Unit)? = null
    private val m = Matrix()
    private var base = 1f
    private var scale = 1f
    private val values = FloatArray(9)

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val target = (scale * d.scaleFactor).coerceIn(1f, 6f)
            val f = target / scale
            scale = target
            m.postScale(f, f, d.focusX, d.focusY); clamp(); imageMatrix = m
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > 1.1f) fit() else { val f = 2.5f; scale = f; m.postScale(f, f, e.x, e.y); clamp(); imageMatrix = m }
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scale <= 1.01f) return false
            m.postTranslate(-dx, -dy); clamp(); imageMatrix = m; return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (scale > 1.01f || e1 == null || abs(vx) < abs(vy) * 1.5f) return false
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            val d = if (vx < 0) 1 else -1
            onSwipe(if (rtl) -d else d); return true
        }
    })

    init { scaleType = ScaleType.MATRIX }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) { super.setImageDrawable(drawable); post { fit() } }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); fit() }

    private fun fit() {
        val d = drawable ?: return
        if (width == 0 || d.intrinsicWidth <= 0) return
        base = minOf(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        scale = 1f
        m.reset(); m.postScale(base, base)
        m.postTranslate((width - d.intrinsicWidth * base) / 2, (height - d.intrinsicHeight * base) / 2)
        imageMatrix = m
    }

    private fun clamp() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(r)
        var dx = 0f; var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2 - r.left else { if (r.left > 0) dx = -r.left; if (r.right < width) dx = width - r.right }
        if (r.height() <= height) dy = (height - r.height()) / 2 - r.top else { if (r.top > 0) dy = -r.top; if (r.bottom < height) dy = height - r.bottom }
        m.postTranslate(dx, dy)
        m.getValues(values)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        if (scale > 1.01f) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}
