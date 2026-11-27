package com.londkirs.antigravityremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

/**
 * Owns the terminal sessions so agents keep running while the screen is off or the app is
 * in the background (foreground service + partial wake lock).
 */
class TerminalService : Service(), TerminalSessionClient {

    interface Listener {
        fun onSessionsChanged()
        fun onScreenChanged(session: TerminalSession)
        fun onTitleChanged(session: TerminalSession)
        fun onOpenUrl(url: String)
        fun onPasteRequest(session: TerminalSession)
        fun onBell(session: TerminalSession)
    }

    inner class LocalBinder : Binder() {
        val service: TerminalService get() = this@TerminalService
    }

    companion object {
        private const val CHANNEL = "agy-sessions"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.londkirs.antigravityremote.STOP"
        const val KIND_AGY = "agy"
        const val KIND_SHELL = "shell"
        const val KIND_HUB = "hub"
        const val HUB_PORT = 4400
        const val HUB_URL = "http://localhost:4400/"
    }

    val sessions = mutableListOf<TerminalSession>()
    val kinds = HashMap<TerminalSession, String>()
    var listener: Listener? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var urlObserver: FileObserver? = null
    private var pendingUrl: String? = null

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sessions.toList().forEach { it.finishIfRunning() }
            sessions.clear()
            kinds.clear()
            afterSessionsChanged()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        AgyInstaller.prepareDirs(this)
        watchUrlFile()
    }

    override fun onDestroy() {
        urlObserver?.stopWatching()
        sessions.toList().forEach { it.finishIfRunning() }
        releaseWakeLock()
        super.onDestroy()
    }

    // ---- Sessions ----------------------------------------------------------------------------

    fun environment(): Array<String> {
        val files = filesDir.absolutePath
        val home = AgyInstaller.homeDir(this).absolutePath
        val env = linkedMapOf(
            "HOME" to home,
            "TMPDIR" to AgyInstaller.tmpDir(this).absolutePath,
            "PATH" to "$files/bin:/system/bin:/system/xbin:/vendor/bin",
            "SHELL" to "/system/bin/sh",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "LANG" to "en_US.UTF-8",
            "BROWSER" to "$files/bin/open-url",
            "ANTIGRAVITY_ANDROID_MOBILE" to "1",
            "ANDROID_HOSTED_APP" to packageName,
        )
        // Android runtime variables that system tools (am, cmd, toybox) need.
        listOf(
            "ANDROID_ROOT", "ANDROID_DATA", "ANDROID_ART_ROOT", "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT",
            "ANDROID_RUNTIME_ROOT", "BOOTCLASSPATH", "DEX2OATBOOTCLASSPATH", "SYSTEMSERVERCLASSPATH",
            "EXTERNAL_STORAGE", "ANDROID_STORAGE", "ASEC_MOUNTPOINT"
        ).forEach { k -> System.getenv(k)?.let { env[k] = it } }
        return env.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    /** Starts agy in ~/projects; when agy exits the user lands in a shell instead of a dead screen. */
    fun startAgy(): TerminalSession {
        val agy = AgyInstaller.binary(this).absolutePath
        val script = "cd \"\$HOME/projects\" 2>/dev/null || cd \"\$HOME\"\n" +
            "\"$agy\"\n" +
            "rc=\$?\n" +
            "echo\n" +
            "echo \"[agy exited with code \$rc]  Type 'agy' to start it again.\"\n" +
            "exec /system/bin/sh -i\n"
        return start(arrayOf("sh", "-c", script), KIND_AGY)
    }

    /**
     * Starts agy's local web UI ("hub", as used by the Termux community scripts) on
     * http://localhost:HUB_PORT; the app shows it in its WebView. Output stays in a terminal
     * session so startup errors remain visible.
     */
    fun startHub(): TerminalSession {
        val agy = AgyInstaller.binary(this).absolutePath
        // A fresh CSRF token per hub start: agy reads it from the environment (ResolveCsrfTokensFromEnv)
        // and the app sends it back in x-antigravity-csrf-token.
        val token = HubClient.newToken(this)
        val script = "cd \"\$HOME/projects\" 2>/dev/null || cd \"\$HOME\"\n" +
            // Same data dir as the interactive CLI, so the hub reuses the Google sign-in done in agy.
            "export AGY_ENABLE_HUB=1 GOMEMLIMIT=1536MiB AGY_NO_UPDATE_PROMPT=1 CI=1\n" +
            "export ANTIGRAVITY_SERVER_CSRF_TOKEN=$token ANTIGRAVITY_CSRF_TOKEN=$token\n" +
            "echo \"Starting Antigravity UI on http://localhost:$HUB_PORT ...\"\n" +
            "\"$agy\" --hub --hub-port=$HUB_PORT --app_data_dir=antigravity-cli --add-dir=\"\$HOME/projects\"\n" +
            "rc=\$?\n" +
            "echo\n" +
            "echo \"[agy --hub exited with code \$rc]\"\n" +
            "exec /system/bin/sh -i\n"
        return start(arrayOf("sh", "-c", script), KIND_HUB)
    }

    fun hubSession(): TerminalSession? = sessions.lastOrNull { kinds[it] == KIND_HUB && it.isRunning }

    fun startShell(): TerminalSession =
        start(arrayOf("sh", "-i"), KIND_SHELL)

    private fun start(args: Array<String>, kind: String): TerminalSession {
        val cwd = File(AgyInstaller.homeDir(this), "projects").let { if (it.isDirectory) it else AgyInstaller.homeDir(this) }
        val session = TerminalSession("/system/bin/sh", cwd.absolutePath, args, environment(), 5000, this)
        sessions += session
        kinds[session] = kind
        acquireWakeLock()
        startService(Intent(this, TerminalService::class.java))
        refreshNotification()
        listener?.onSessionsChanged()
        return session
    }

    fun remove(session: TerminalSession) {
        session.finishIfRunning()
        sessions.remove(session)
        kinds.remove(session)
        afterSessionsChanged()
    }

    private fun afterSessionsChanged() {
        if (sessions.isEmpty()) {
            releaseWakeLock()
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
        } else {
            refreshNotification()
        }
        listener?.onSessionsChanged()
    }

    fun takePendingUrl(): String? = pendingUrl.also { pendingUrl = null }

    // ---- BROWSER bridge --------------------------------------------------------------------------

    private fun watchUrlFile() {
        val dir = filesDir
        @Suppress("DEPRECATION")
        urlObserver = object : FileObserver(dir.absolutePath, FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != ".open-url") return
                val f = File(dir, ".open-url")
                val url = runCatching { f.readText().trim() }.getOrNull()
                f.delete()
                if (url.isNullOrEmpty()) return
                android.os.Handler(mainLooper).post { deliverUrl(url) }
            }
        }.also { it.startWatching() }
    }

    private fun deliverUrl(url: String) {
        val l = listener
        if (l != null) l.onOpenUrl(url) else {
            pendingUrl = url
            refreshNotification(url)
            Connections.openInChrome(this, url)
        }
    }

    // ---- Notification / wake lock ----------------------------------------------------------------

    private fun buildNotification(url: String? = null): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val open = PendingIntent.getActivity(this, 0, Intent(this, TerminalActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 1, Intent(this, TerminalService::class.java).setAction(ACTION_STOP), flags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val text = if (url != null) getString(R.string.notif_signin) else
            resources.getQuantityString(R.plurals.notif_sessions, sessions.size.coerceAtLeast(1), sessions.size.coerceAtLeast(1))
        return b.setSmallIcon(R.drawable.ic_stat_agy)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(0, getString(R.string.stop_all), stop).build())
            .build()
    }

    private fun refreshNotification(url: String? = null) {
        if (sessions.isEmpty()) return
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(url))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "antigravity:agents").apply {
            setReferenceCounted(false); acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    // ---- TerminalSessionClient -------------------------------------------------------------------

    override fun onTextChanged(changedSession: TerminalSession) { listener?.onScreenChanged(changedSession) }
    override fun onTitleChanged(changedSession: TerminalSession) { listener?.onTitleChanged(changedSession) }
    override fun onSessionFinished(finishedSession: TerminalSession) {
        // Keep the finished session visible (shows exit status) until the user closes it.
        listener?.onSessionsChanged()
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text == null) return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) { session?.let { listener?.onPasteRequest(it) } }
    override fun onBell(session: TerminalSession) { listener?.onBell(session) }
    override fun onColorsChanged(session: TerminalSession) { listener?.onScreenChanged(session) }
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null
    override fun logError(tag: String?, message: String?) { Log.e(tag, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag, "error", e) }
}
