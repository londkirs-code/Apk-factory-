package com.londkirs.antigravityremote

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class WebActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        private const val REQ_FILE = 11
        private const val REQ_MIC = 12
        private const val PREFS = "web"
        private const val MENU_DESKTOP = 1
        private const val MENU_SCREEN = 2
        private const val MENU_CHROME = 3
        private const val MENU_HOME = 4
        private const val MENU_REC = 5
        private const val MENU_REC_NOTE = 6
        private const val MENU_REC_SHARE = 7
        private const val MENU_REC_CLEAR = 8
    }

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var title: TextView
    private lateinit var banner: LinearLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private var mobileUserAgent = ""
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var content: FrameLayout
    private lateinit var chromeClient: WebChromeClient
    private lateinit var recorder: NetRecorder
    private var docStartScript = false
    private lateinit var recBadge: TextView
    private val recorderJs by lazy { assets.open("recorder.js").bufferedReader().readText() }

    private fun isHubUrl(url: String?) = url != null &&
        (url.startsWith("http://localhost:${TerminalService.HUB_PORT}") || url.startsWith("http://127.0.0.1:${TerminalService.HUB_PORT}"))

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E1222"))
        }

        // Compact toolbar so the remote session gets almost the whole screen.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1F36"))
            setPadding(dp(4), 0, dp(4), 0)
        }
        bar.addView(iconButton(android.R.drawable.ic_media_previous, R.string.back) { goBack() })
        title = TextView(this).apply {
            setTextColor(Color.parseColor("#E8EAF6"))
            textSize = 14f
            setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(6), 0, dp(6), 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        recBadge = TextView(this).apply {
            text = "● REC"; setTextColor(Color.parseColor("#FF5252")); textSize = 12f
            setPadding(dp(6), 0, dp(6), 0); visibility = View.GONE
            setOnClickListener { addNote() }
        }
        bar.addView(recBadge)
        bar.addView(iconButton(android.R.drawable.ic_popup_sync, R.string.reload) { web.reload() })
        val more = iconButton(android.R.drawable.ic_menu_more, R.string.more) {}
        more.setOnClickListener { showMenu(it) }
        bar.addView(more)
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)))

        banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#3A2F0B"))
            setPadding(dp(12), dp(6), dp(8), dp(6))
            visibility = View.GONE
        }
        banner.addView(TextView(this).apply {
            setText(R.string.signin_banner)
            setTextColor(Color.parseColor("#FFE7A3"))
            textSize = 13f
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        banner.addView(Button(this).apply {
            setText(R.string.continue_in_chrome)
            isAllCaps = false
            textSize = 12f
            setOnClickListener { Connections.openInChrome(this@WebActivity, web.url ?: Connections.OFFICIAL_URL) }
        })
        root.addView(banner)

        web = WebView(this)
        content = FrameLayout(this)
        content.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        configureWebView()
        applyKeepScreenOn()

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            load(resolveStartUrl(intent))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        load(resolveStartUrl(intent))
    }

    /** The local agy hub checks a CSRF header; send it with the page request. */
    private fun load(url: String) {
        val isHub = url.startsWith("http://localhost:${TerminalService.HUB_PORT}") || url.startsWith("http://127.0.0.1:${TerminalService.HUB_PORT}")
        val token = if (isHub) HubClient.token(this) else null
        if (token != null) web.loadUrl(url, mapOf("x-antigravity-csrf-token" to token, "x-codeium-csrf-token" to token))
        else web.loadUrl(url)
    }

    private fun resolveStartUrl(intent: Intent?): String {
        intent ?: return Connections.OFFICIAL_URL
        intent.getStringExtra(EXTRA_URL)?.let { return it }
        intent.data?.toString()?.let { return it }
        Connections.findUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { return it }
        return Connections.OFFICIAL_URL
    }

    private fun iconButton(icon: Int, desc: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = getString(desc)
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { onClick() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        @Suppress("DEPRECATION")
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.cacheMode = WebSettings.LOAD_DEFAULT

        // Google rejects sign-in from user agents that advertise an embedded WebView ("; wv").
        // Present a regular Chrome user agent; Chrome remains available as a fallback.
        mobileUserAgent = s.userAgentString
            .replace("; wv)", ")")
            .replace(Regex("""Version/\d+(\.\d+)*\s"""), "")
        applyUserAgent()

        recorder = NetRecorder(this)
        web.addJavascriptInterface(recorder, "AgyRec")
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web, recorderJs,
                setOf("http://localhost:${TerminalService.HUB_PORT}", "http://127.0.0.1:${TerminalService.HUB_PORT}"))
            docStartScript = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false
                // intent:, mailto:, market: … are handed to Android.
                return try {
                    val intent = if (scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    else Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                recorder.pageIsHub = isHubUrl(url)
                if (recorder.pageIsHub && !docStartScript) view.evaluateJavascript(recorderJs, null)
                updateRecBadge()
                progress.visibility = View.VISIBLE
                updateChrome(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = View.GONE
                updateChrome(url)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val msg = if (!isOnline()) getString(R.string.offline) else error.description.toString()
                Toast.makeText(this@WebActivity, getString(R.string.load_error, msg), Toast.LENGTH_LONG).show()
            }
        }

        chromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, t: String?) {
                if (!t.isNullOrBlank()) title.text = t
            }

            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handlePermission(request) }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView?.let { onHideCustomView() }
                customView = view
                customViewCallback = callback
                content.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }

            override fun onHideCustomView() {
                customView?.let { content.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
        web.webChromeClient = chromeClient

        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val req = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                    setTitle(name)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Connections.openInChrome(this, url)
            }
        }
    }

    private fun updateChrome(url: String?) {
        val host = url?.let { Uri.parse(it).host } ?: ""
        if (title.text.isNullOrBlank()) title.text = host
        banner.visibility = if (host == "accounts.google.com") View.VISIBLE else View.GONE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null
    }

    private fun handlePermission(request: PermissionRequest) {
        val wantsMic = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        if (wantsMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = request
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        val allowed = request.resources.filter {
            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
        }
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        val req = pendingPermission ?: return
        pendingPermission = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            handlePermission(req)
        } else {
            Toast.makeText(this, R.string.mic_denied, Toast.LENGTH_SHORT).show()
            req.deny()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    private fun showMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, MENU_DESKTOP, 0, R.string.desktop_mode).apply {
            isCheckable = true; isChecked = prefs.getBoolean("desktop", false)
        }
        menu.menu.add(0, MENU_SCREEN, 1, R.string.keep_screen_on).apply {
            isCheckable = true; isChecked = prefs.getBoolean("screen_on", true)
        }
        menu.menu.add(0, MENU_CHROME, 2, R.string.open_externally)
        menu.menu.add(0, MENU_HOME, 3, R.string.home)
        if (isHubUrl(web.url)) {
            menu.menu.add(0, MENU_REC, 4, if (recorder.enabled) R.string.rec_stop else R.string.rec_start)
            menu.menu.add(0, MENU_REC_NOTE, 5, R.string.rec_note)
            menu.menu.add(0, MENU_REC_SHARE, 6, getString(R.string.rec_share, recorder.entries()))
            menu.menu.add(0, MENU_REC_CLEAR, 7, R.string.rec_clear)
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DESKTOP -> {
                    prefs.edit().putBoolean("desktop", !prefs.getBoolean("desktop", false)).apply()
                    applyUserAgent()
                    web.reload()
                }
                MENU_SCREEN -> {
                    prefs.edit().putBoolean("screen_on", !prefs.getBoolean("screen_on", true)).apply()
                    applyKeepScreenOn()
                }
                MENU_CHROME -> Connections.openInChrome(this, web.url ?: Connections.OFFICIAL_URL)
                MENU_REC -> {
                    recorder.enabled = !recorder.enabled
                    updateRecBadge()
                    if (recorder.enabled) {
                        recorder.note("recording started")
                        Toast.makeText(this, R.string.rec_started_hint, Toast.LENGTH_LONG).show()
                        web.reload() // capture the page's startup calls too
                    }
                }
                MENU_REC_NOTE -> addNote()
                MENU_REC_SHARE -> recorder.export()?.let { startActivity(Intent.createChooser(it, getString(R.string.rec_share_title))) }
                    ?: Toast.makeText(this, R.string.rec_empty, Toast.LENGTH_SHORT).show()
                MENU_REC_CLEAR -> { recorder.clear(); Toast.makeText(this, R.string.rec_cleared, Toast.LENGTH_SHORT).show() }
                MENU_HOME -> {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
            }
            true
        }
        menu.show()
    }

    private fun updateRecBadge() {
        recBadge.visibility = if (recorder.enabled && recorder.pageIsHub) View.VISIBLE else View.GONE
    }

    /** Labels the next part of the recording ("attached an image", "typed /"…). */
    private fun addNote() {
        val input = android.widget.EditText(this).apply { setHint(R.string.rec_note_hint) }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.rec_note)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { recorder.note(it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyUserAgent() {
        val desktop = prefs.getBoolean("desktop", false)
        web.settings.userAgentString = if (desktop) {
            mobileUserAgent.replace(Regex("""\(Linux; Android[^)]*\)"""), "(X11; Linux x86_64)").replace(" Mobile", "")
        } else mobileUserAgent
    }

    private fun applyKeepScreenOn() {
        if (prefs.getBoolean("screen_on", true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun goBack() {
        when {
            customView != null -> chromeClient.onHideCustomView()
            web.canGoBack() -> web.goBack()
            else -> finish()
        }
    }

    @Deprecated("Back is handled manually for API 24 compatibility")
    override fun onBackPressed() {
        goBack()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        (web.parent as? FrameLayout)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
