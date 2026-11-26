package com.londkirs.apkbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.AdbException
import io.github.rhythmcache.adb.FileKeyProvider
import io.github.rhythmcache.adb.io.RandomAccessSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.zip.ZipFile

/** A network endpoint advertised by Wireless Debugging. */
data class Endpoint(val host: String, val port: Int)

/** Options passed to `cmd package install`. */
data class InstallOptions(
    val allowDowngrade: Boolean,
    val grantPermissions: Boolean,
    val allowTestPackages: Boolean,
    val bypassLowTargetSdk: Boolean,
    val registerPlayInstaller: Boolean,
)

/** UI-facing snapshot of what the bridge is doing. */
data class BridgeState(
    val busy: Boolean = false,
    val status: String = tr("מוכן.", "Ready."),
    val progress: Int = -1,
    val lastInstalledPackage: String? = null,
    /** Incremented after every successful install, so the UI can offer to open the app once. */
    val installSeq: Int = 0,
)

/** What a selected APK is, shown before installing. */
data class ApkPreview(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
)

/**
 * Process-wide ADB engine. Work runs in an application scope, so rotating the
 * screen or leaving the activity does not cancel an installation in progress.
 */
object Bridge {
    const val PAIRING_SERVICE = "_adb-tls-pairing._tcp."
    const val CONNECT_SERVICE = "_adb-tls-connect._tcp."
    const val LOCAL_HOST = "127.0.0.1"
    private const val PREFS = "bridge"
    internal const val PLAY_STORE = "com.android.vending"
    internal const val INSTALL_CHANNEL = "installs"
    const val LEGACY_PORT = 5555
    val LEGACY_HELP = tr("ב־Android ${Build.VERSION.RELEASE} אין ניפוי באגים אלחוטי עם קוד צימוד (קיים רק מ־Android 11). ", "Android ${Build.VERSION.RELEASE} has no wireless debugging with a pairing code (only from Android 11). ") +
        tr("כדי להשתמש באפליקציית הבוס: הפעל \"ניפוי באגים ב־USB\" באפשרויות למפתחים, חבר פעם אחת למחשב והרץ: adb tcpip 5555 — ", "To use The Boss: turn on \"USB debugging\" in Developer options, connect once to a computer and run: adb tcpip 5555 — ") +
        tr("ואז אפשר לנתק את הכבל. יש לחזור על כך אחרי כל הפעלה מחדש של המכשיר.", "then you can unplug the cable. Repeat this after every restart of the device.")
    const val WIFI_HELPER_PKG = "com.londkirs.apkbridge.wifihelper"
    private const val WIFI_HELPER_ACTION = "com.londkirs.apkbridge.wifihelper.ENABLE_WIFI"
    private const val WIFI_HELPER_ASSET = "wifi-helper.apk"
    internal val KNOWN_ABIS = listOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "mips64", "mips")

    internal lateinit var app: Context
    internal val main = Handler(Looper.getMainLooper())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val logLines = ArrayDeque<String>()
    private val logListeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val stateListeners = CopyOnWriteArraySet<(BridgeState) -> Unit>()

    @Volatile var state = BridgeState()
        private set

    @Volatile private var currentJob: Job? = null

    /** Saved, so a celebration that is still waiting survives the app being closed. */
    private var celebrationPending: Boolean
        get() = prefs.getBoolean("celebration_pending", false)
        set(value) { prefs.edit().putBoolean("celebration_pending", value).commit() }

    /** True once per successful pairing; the screen that shows the confetti consumes it. */
    fun consumeCelebration(): Boolean = synchronized(this) { celebrationPending.also { if (it) celebrationPending = false } }

    /** Set when a "test connection" (or connection after pairing) reached the device in this session. */
    @Volatile var lastConnectionOk = false

    fun init(context: Context) {
        Lang.init(context)
        if (::app.isInitialized) return
        app = context.applicationContext
        applyAutomaticDefaultsOnce()
    }

    /**
     * One-time switch to "as automatic as possible" defaults, also for installs that
     * already saved older preferences. Every item can still be turned off in Settings.
     */
    private fun applyAutomaticDefaultsOnce() {
        if (prefs.getBoolean("defaults_v3", false)) return
        prefs.edit()
            .putBoolean("keep_adb_without_wifi", true)
            .putString("auto_install_mode", "auto")
            .putBoolean("opt_grant", true)
            .putBoolean("a11y_auto_pair", true)
            .putBoolean("auto_wifi", true)
            .putBoolean("auto_enable_wireless", true)
            .putBoolean("auto_setup", true)
            .putBoolean("defaults_v3", true)
            .apply()
    }

    /** After a successful ADB connection, grant this app everything it can use (see [autoSetup]). */
    var autoSetupEnabled: Boolean
        get() = prefs.getBoolean("auto_setup", true)
        set(value) = prefs.edit().putBoolean("auto_setup", value).apply()

    // region preferences

    internal val prefs get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var targetHost: String
        get() = prefs.getString("target_host", LOCAL_HOST).orEmpty().ifBlank { LOCAL_HOST }
        set(value) = prefs.edit().putString("target_host", value.trim().ifBlank { LOCAL_HOST }).apply()

    var savedConnectPort: Int
        get() = prefs.getInt("connect_port", 0)
        set(value) = prefs.edit().putInt("connect_port", value).apply()

    var isPaired: Boolean
        get() = prefs.getBoolean("paired", false)
        set(value) = prefs.edit().putBoolean("paired", value).apply()

    /** What to do with a file opened/shared into the app: "off", "ask" or "auto". */
    var autoInstallMode: String
        get() = prefs.getString("auto_install_mode", null) ?: "auto"
        set(value) = prefs.edit().putString("auto_install_mode", value).apply()

    /** Only auto-install files that update an app that is already installed. */

    /** After a successful install: "ask", "auto" or "never". */
    var openAfterInstall: String
        get() = prefs.getString("open_after_install", "ask") ?: "ask"
        set(value) = prefs.edit().putString("open_after_install", value).apply()

    var installNotifications: Boolean
        get() = prefs.getBoolean("install_notifications", true)
        set(value) = prefs.edit().putBoolean("install_notifications", value).apply()

    var accessibilityAutoPair: Boolean
        get() = prefs.getBoolean("a11y_auto_pair", true)
        set(value) = prefs.edit().putBoolean("a11y_auto_pair", value).apply()

    /** Open Android's Wi‑Fi panel and wait for Wi‑Fi whenever a connection is needed and Wi‑Fi is off. */
    /** Keep a plain-TCP adbd on 127.0.0.1:5555 so ADB keeps working without Wi‑Fi (until reboot). */
    var keepAdbWithoutWifi: Boolean
        get() = prefs.getBoolean("keep_adb_without_wifi", true)
        set(value) = prefs.edit().putBoolean("keep_adb_without_wifi", value).apply()

    var autoWifi: Boolean
        get() = prefs.getBoolean("auto_wifi", true)
        set(value) = prefs.edit().putBoolean("auto_wifi", value).apply()

    var autoEnableWireless: Boolean
        get() = prefs.getBoolean("auto_enable_wireless", true)
        set(value) = prefs.edit().putBoolean("auto_enable_wireless", value).apply()

    /** Removes the ADB key and pairing state; the next connection needs a fresh pairing. */
    fun forgetPairing() {
        File(app.filesDir, "adbkey").delete()
        File(app.filesDir, "adbkey.pub").delete()
        isPaired = false
        savedConnectPort = 0
        log(tr("הצימוד נמחק. בפעם הבאה יידרש צימוד חדש.", "Pairing removed. Next time a new pairing will be needed."))
    }

    var logExpanded: Boolean
        get() = prefs.getBoolean("log_expanded", false)
        set(value) = prefs.edit().putBoolean("log_expanded", value).apply()

    fun lastLogLine(): String = synchronized(logLines) { logLines.lastOrNull().orEmpty() }

    fun loadOptions() = InstallOptions(
        allowDowngrade = prefs.getBoolean("opt_downgrade", false),
        grantPermissions = prefs.getBoolean("opt_grant", true),
        allowTestPackages = prefs.getBoolean("opt_test", true),
        bypassLowTargetSdk = prefs.getBoolean("opt_bypass_low_target", true),
        registerPlayInstaller = prefs.getBoolean("opt_play_installer", true),
    )

    fun saveOptions(o: InstallOptions) = prefs.edit()
        .putBoolean("opt_downgrade", o.allowDowngrade)
        .putBoolean("opt_grant", o.grantPermissions)
        .putBoolean("opt_test", o.allowTestPackages)
        .putBoolean("opt_bypass_low_target", o.bypassLowTargetSdk)
        .putBoolean("opt_play_installer", o.registerPlayInstaller)
        .apply()

    // endregion

    // region log + state

    private val wifiPanelListeners = CopyOnWriteArraySet<() -> Unit>()
    fun addWifiPanelListener(l: () -> Unit) { wifiPanelListeners += l }
    fun removeWifiPanelListener(l: () -> Unit) { wifiPanelListeners -= l }

    /**
     * Android 10+ does not let regular apps switch Wi‑Fi on. The closest thing is the system
     * Wi‑Fi panel (one tap), so open it — or post a notification when the app is not visible —
     * and continue automatically once Wi‑Fi is connected.
     */
    suspend fun ensureWifi(): Boolean {
        if (isOnWifi()) return true
        if (!autoWifi) return false
        if (isWifiHelperInstalled()) {
            log(tr("אין חיבור Wi‑Fi — מדליק Wi‑Fi דרך עזר ה־Wi‑Fi…", "No Wi‑Fi connection — turning Wi‑Fi on through the Wi‑Fi helper…"))
            update { it.copy(status = tr("מדליק Wi‑Fi וממתין לחיבור לרשת…", "Turning Wi‑Fi on and waiting for a network…"), progress = -1) }
            if (requestWifiFromHelper()) {
                val up = withTimeoutOrNull(25_000) {
                    while (!isOnWifi()) delay(700)
                    true
                } ?: false
                if (up) {
                    log(tr("Wi‑Fi הודלק והתחבר. ממשיך…", "Wi‑Fi is on and connected. Continuing…"))
                    delay(1500)
                    return true
                }
                log(tr("ה־Wi‑Fi הודלק אבל לא התחבר לרשת שמורה בתוך 25 שניות.", "Wi‑Fi was turned on but didn't connect to a saved network within 25 seconds."))
            } else {
                log(tr("עזר ה־Wi‑Fi לא הצליח להדליק את ה־Wi‑Fi.", "The Wi‑Fi helper couldn't turn Wi‑Fi on."))
            }
        }
        log(tr("אין חיבור Wi‑Fi — פותח את חלונית ה־Wi‑Fi של Android.", "No Wi‑Fi connection — opening Android's Wi‑Fi panel."))
        if (wifiPanelListeners.isEmpty()) {
            notifyInfo(tr("הבוס צריך Wi‑Fi", "The Boss needs Wi‑Fi"), tr("הקש כדי להדליק Wi‑Fi. הפעולה תמשיך אוטומטית.", "Tap to turn Wi‑Fi on. The action will continue automatically."), Compat.wifiPanelIntent())
        } else {
            main.post { wifiPanelListeners.forEach { it() } }
        }
        update { it.copy(status = tr("ממתין לחיבור Wi‑Fi… (הדלק Wi‑Fi בחלונית שנפתחה)", "Waiting for Wi‑Fi… (turn Wi‑Fi on in the panel that opened)"), progress = -1) }
        val connected = withTimeoutOrNull(120_000) {
            while (!isOnWifi()) delay(1000)
            true
        } ?: false
        if (connected) {
            log(tr("Wi‑Fi מחובר. ממשיך…", "Wi‑Fi connected. Continuing…"))
            delay(1500)
        } else {
            log(tr("לא התקבל חיבור Wi‑Fi בתוך 2 דקות.", "No Wi‑Fi connection within 2 minutes."))
        }
        return connected
    }

    fun isWifiHelperInstalled(): Boolean = runCatching {
        app.packageManager.getPackageInfo(WIFI_HELPER_PKG, 0); true
    }.getOrDefault(false)

    fun isWifiHelperBundled(): Boolean = runCatching { app.assets.open(WIFI_HELPER_ASSET).close(); true }.getOrDefault(false)

    /** Asks the targetSdk-28 helper app to call WifiManager.setWifiEnabled(true). */
    private suspend fun requestWifiFromHelper(): Boolean {
        val reply = CompletableDeferred<Int>()
        val intent = android.content.Intent(WIFI_HELPER_ACTION)
            .setClassName(WIFI_HELPER_PKG, "$WIFI_HELPER_PKG.EnableWifiReceiver")
            .addFlags(android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        return runCatching {
            app.sendOrderedBroadcast(
                intent, null,
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: Context, i: android.content.Intent) { reply.complete(resultCode) }
                },
                main, -1, null, null,
            )
            (withTimeoutOrNull(5_000) { reply.await() } ?: 0) == 1
        }.getOrDefault(false)
    }

    /** Re-sends the current state so open screens refresh (e.g. after the helper was installed). */
    fun refreshState() = update { it }

    var helperOffered: Boolean
        get() = prefs.getBoolean("wifi_helper_offered", false)
        set(value) = prefs.edit().putBoolean("wifi_helper_offered", value).apply()

    fun addLogListener(l: (String) -> Unit) { logListeners += l }
    fun removeLogListener(l: (String) -> Unit) { logListeners -= l }
    fun addStateListener(l: (BridgeState) -> Unit) { stateListeners += l; l(state) }
    fun removeStateListener(l: (BridgeState) -> Unit) { stateListeners -= l }

    fun logSnapshot(): String = synchronized(logLines) { logLines.joinToString("\n") }

    fun clearLog() {
        synchronized(logLines) { logLines.clear() }
        main.post { logListeners.forEach { it("") } }
    }

    fun log(message: String) {
        val line = "[${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}] $message"
        synchronized(logLines) {
            logLines.addLast(line)
            while (logLines.size > 400) logLines.removeFirst()
        }
        main.post { logListeners.forEach { it(line) } }
    }

    internal fun update(transform: (BridgeState) -> BridgeState) {
        val next = synchronized(this) { transform(state).also { state = it } }
        main.post { stateListeners.forEach { it(next) } }
    }

    internal fun status(text: String, progress: Int = -1) {
        log(text)
        update { it.copy(status = text, progress = progress) }
    }

    /** Runs [block] as the single active bridge job; refuses to start a second one. */
    internal fun runJob(name: String, block: suspend () -> Unit): Boolean {
        synchronized(this) {
            if (currentJob?.isActive == true) {
                log(tr("פעולה אחרת עדיין רצה — המתן לסיומה.", "Another action is still running — wait for it to finish."))
                return false
            }
            update { it.copy(busy = true, progress = -1) }
            currentJob = scope.launch {
                try {
                    block()
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    status(tr("$name נכשל: ${explain(e)}", "$name failed: ${explain(e)}"))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    status(tr("$name בוטל.", "$name cancelled."))
                } catch (e: Throwable) {
                    log(tr("פרטים טכניים: ${details(e)}", "Technical details: ${details(e)}"))
                    status(tr("$name נכשל: ${explain(e)}", "$name failed: ${explain(e)}"))
                } finally {
                    update { it.copy(busy = false) }
                }
            }
        }
        return true
    }

    fun cancelCurrent() { currentJob?.cancel() }

    /** Public entry points for longer operations (backup / restore) that live outside this file. */
    fun launchJob(name: String, block: suspend () -> Unit): Boolean = runJob(name, block)
    fun progress(text: String, percent: Int) = update { it.copy(status = text, progress = percent) }
    fun announce(text: String, percent: Int = -1) = status(text, percent)

    // endregion

    // region environment helpers

    fun keyProvider() = FileKeyProvider(
        File(app.filesDir, "adbkey"),
        identityComment = "apkbridge@${Build.MODEL.replace(' ', '_')}",
    )

    fun isOnWifi(): Boolean = runCatching {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
    }.getOrDefault(false)

    fun canWriteSecureSettings() =
        app.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** null = unknown (setting not readable on this build). */
    fun isWirelessDebuggingOn(): Boolean? = runCatching {
        Settings.Global.getInt(app.contentResolver, "adb_wifi_enabled", 0) == 1
    }.getOrNull()

    /** Turns Wireless Debugging on by itself when WRITE_SECURE_SETTINGS was granted earlier. */
    private suspend fun ensureWirelessDebugging() {
        if (!autoEnableWireless || isWirelessDebuggingOn() == true || !canWriteSecureSettings()) return
        if (!isOnWifi()) {
            log(tr("אין חיבור Wi‑Fi — Android לא יפעיל ניפוי באגים אלחוטי בלי Wi‑Fi.", "No Wi‑Fi connection — Android won't turn on wireless debugging without Wi‑Fi."))
            return
        }
        val ok = runCatching {
            Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 1)
        }.getOrDefault(false)
        if (ok) {
            log(tr("ניפוי באגים אלחוטי הופעל אוטומטית. ממתין לעליית השירות…", "Wireless debugging was turned on automatically. Waiting for the service to start…"))
            delay(2500)
        }
    }

    internal fun isLocalTarget(host: String) =
        host == LOCAL_HOST || host.equals("localhost", true) || host == "::1"

    internal fun localAddresses(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .toSet()
    }.getOrDefault(emptySet())

    @Suppress("DEPRECATION")
    internal fun addressesOf(info: NsdServiceInfo): List<InetAddress> =
        if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)

    // endregion


    // region pairing

    suspend fun pairNow(host: String, port: Int?, code: String): Boolean {
        val cleanCode = code.filter(Char::isDigit)
        require(cleanCode.length == 6) { tr("קוד הצימוד חייב להכיל 6 ספרות.", "The pairing code must have 6 digits.") }
        val target = host.trim().ifBlank { LOCAL_HOST }
        val pairPort = port ?: run {
            log(tr("מחפש את פורט הצימוד ברשת (mDNS)…", "Looking for the pairing port on the network (mDNS)…"))
            Mdns.discover(PAIRING_SERVICE, target, 8_000)?.port
        } ?: error(tr("לא נמצא שירות צימוד פעיל. ודא שחלון \"התאמת מכשיר עם קוד התאמה\" פתוח, או הזן את הפורט ידנית.", "No active pairing service found. Make sure the \"Pair device with pairing code\" window is open, or enter the port manually."))
        log(tr("מבצע צימוד מול $target:$pairPort…", "Pairing with $target:$pairPort…"))
        val result = withContext(Dispatchers.IO) {
            withTimeout(30_000) { AdbClient.pairTls(target, pairPort, cleanCode, keyProvider()) }
        }
        val error = result.exceptionOrNull()
        if (error != null) {
            log(tr("הצימוד נכשל: ${explain(error)}", "Pairing failed: ${explain(error)}"))
            return false
        }
        isPaired = true
        targetHost = target
        log(tr("הצימוד הצליח. המפתח נשמר באחסון הפרטי של האפליקציה.", "Paired. The key is saved in the app's private storage."))
        // 🎉 Confetti on the visible screen (or as soon as the user comes back to the app).
        celebrationPending = true
        main.post { Celebration.celebrate() }
        return true
    }

    fun pair(host: String, port: Int?, code: String) = runJob(tr("הצימוד", "Pairing")) {
        status(tr("מבצע צימוד מאובטח…", "Pairing securely…"))
        if (pairNow(host, port, code)) {
            status(tr("הצימוד הצליח. מתחבר…", "Paired. Connecting…"))
            withClient { describeDevice(it) }
        } else {
            status(tr("הצימוד נכשל. בדוק את הקוד ושהחלון עדיין פתוח, ונסה שוב.", "Pairing failed. Check the code and that the window is still open, then try again."))
        }
    }

    /** Called by the accessibility service when it reads a pairing code off the Settings screen. */
    fun autoPair(port: Int, code: String) = runJob(tr("הצימוד האוטומטי", "Automatic pairing")) {
        status(tr("זוהה קוד צימוד במסך — מבצע צימוד אוטומטית…", "Pairing code detected on screen — pairing automatically…"))
        if (pairNow(LOCAL_HOST, port, code)) {
            notifyInfo(tr("הבוס צומד בהצלחה", "The Boss is paired"), tr("הצימוד האוטומטי הצליח. אפשר לחזור לאפליקציית הבוס ולהתקין.", "Automatic pairing succeeded. You can go back to The Boss and install."), null)
            status(tr("הצימוד האוטומטי הצליח. בודק חיבור…", "Automatic pairing succeeded. Checking the connection…"))
            withClient { describeDevice(it) }
        } else {
            status(tr("הצימוד האוטומטי נכשל. אם הקוד התחלף, הוא ינוסה שוב אוטומטית.", "Automatic pairing failed. If the code changed, it will be retried automatically."))
        }
    }

    // endregion

    // region connection

    private suspend fun connectTo(ep: Endpoint): AdbClient = withContext(Dispatchers.IO) {
        withTimeout(25_000) { AdbClient.connectTls(ep.host, ep.port, keyProvider(), handshakeTimeoutMs = 20_000) }
    }

    /** Connects (re-discovering the port if needed), runs [block], then closes. */
    suspend fun <T> withClient(block: suspend (AdbClient) -> T): T {
        // 1. Persistent mode: adbd in TCP mode on loopback works even with Wi‑Fi off.
        if (keepAdbWithoutWifi && isLocalTarget(targetHost) && isLocalPortOpen(LEGACY_PORT)) {
            log(tr("מתחבר דרך ADB קבוע (127.0.0.1:$LEGACY_PORT) — לא נדרש Wi‑Fi…", "Connecting through persistent ADB (127.0.0.1:$LEGACY_PORT) — no Wi‑Fi needed…"))
            val legacy = try {
                connectLegacy(30_000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) null else throw e
            } catch (e: Throwable) {
                log(tr("החיבור הקבוע נכשל: ${explain(e)}", "Persistent connection failed: ${explain(e)}"))
                null
            }
            if (legacy != null) {
                return try {
                    log(tr("מחובר (ADB קבוע).", "Connected (persistent ADB)."))
                    block(legacy).also { runCatching { autoSetup(legacy) } }
                } finally {
                    runCatching { legacy.close() }
                }
            }
        }
        if (!Compat.hasWirelessDebugging) return withLegacyClient(targetHost, block)
        ensureWifi()
        ensureWirelessDebugging()
        val host = targetHost
        val tried = mutableSetOf<Endpoint>()
        var lastError: Throwable? = null

        suspend fun attempt(ep: Endpoint): AdbClient? {
            if (!tried.add(ep)) return null
            log(tr("מתחבר ל־${ep.host}:${ep.port}…", "Connecting to ${ep.host}:${ep.port}…"))
            return try {
                connectTo(ep).also {
                    savedConnectPort = ep.port
                    isPaired = true
                }
            } catch (e: AdbException.NotPaired) {
                isPaired = false
                throw IllegalStateException(tr("המכשיר דחה את המפתח של הבוס. יש לבצע צימוד מחדש.", "The device rejected The Boss's key. Pair again."), e)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastError = e
                log(tr("החיבור ל־${ep.port} לא הגיב בזמן.", "The connection to ${ep.port} didn't respond in time."))
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                log(tr("החיבור ל־${ep.host}:${ep.port} נכשל: ${explain(e)}", "Connecting to ${ep.host}:${ep.port} failed: ${explain(e)}"))
                log(tr("פרטים טכניים: ${details(e)}", "Technical details: ${details(e)}"))
                null
            }
        }

        val local = isLocalTarget(host)

        // Some ROMs only accept the TLS port on the Wi‑Fi address, not on loopback.
        suspend fun attemptAll(port: Int): AdbClient? {
            attempt(Endpoint(host, port))?.let { return it }
            if (local) for (ip in wifiIpv4Addresses()) attempt(Endpoint(ip, port))?.let { return it }
            return null
        }

        var client: AdbClient? = null
        val saved = savedConnectPort
        if (saved > 0) client = attemptAll(saved)
        if (client == null) {
            log(tr("מאתר את פורט Wireless Debugging (mDNS)…", "Finding the Wireless Debugging port (mDNS)…"))
            val found = Mdns.discover(CONNECT_SERVICE, host, 10_000)
            if (found == null) log(tr("גילוי mDNS לא מצא את שירות החיבור.", "mDNS discovery didn't find the connection service."))
            else {
                log(tr("mDNS מצא את פורט ${found.port}.", "mDNS found port ${found.port}."))
                client = attempt(found) ?: attemptAll(found.port)
            }
        }
        if (client == null && local) {
            // mDNS is flaky on many devices. adbd's TLS port is a random high port on this
            // device, so scan loopback and keep only ports that answer with ADB's A_STLS.
            log(tr("סורק פורטים מקומיים כדי למצוא את ADB…", "Scanning local ports to find ADB…"))
            val ports = scanLocalAdbPorts()
            log(if (ports.isEmpty()) tr("לא נמצא פורט ADB פתוח במכשיר.", "No open ADB port found on the device.") else tr("נמצאו פורטים של ADB: ${ports.joinToString()}", "Found ADB ports: ${ports.joinToString()}"))
            for (port in ports) {
                client = attemptAll(port)
                if (client != null) break
            }
        }
        if (client == null && !local) {
            // An older target device (Android 4–10) only offers classic ADB on port 5555.
            log(tr("מנסה ADB קלאסי ב־$host:$LEGACY_PORT (מכשיר יעד ישן)…", "Trying classic ADB at $host:$LEGACY_PORT (older target device)…"))
            client = runCatching { connectLegacy(60_000, host) }.getOrNull()
        }
        if (client == null) {
            val hint = when {
                isWirelessDebuggingOn() == false -> tr("ניפוי באגים אלחוטי כבוי. הפעל אותו באפשרויות למפתחים.", "Wireless debugging is off. Turn it on in Developer options.")
                !isOnWifi() -> tr("המכשיר לא מחובר ל־Wi‑Fi; ניפוי באגים אלחוטי דורש Wi‑Fi.", "The device isn't connected to Wi‑Fi; wireless debugging needs Wi‑Fi.")
                else -> tr("ודא שניפוי באגים אלחוטי פעיל. ${lastError?.let { explain(it) } ?: ""}", "Make sure wireless debugging is on. ${lastError?.let { explain(it) } ?: ""}")
            }
            error(tr("לא ניתן להתחבר ל־ADB. $hint", "Can't connect to ADB. $hint"))
        }
        val connected: AdbClient = client
        var result: Result<T>? = null
        try {
            log(tr("מחובר.", "Connected."))
            result = Result.success(block(connected))
        } finally {
            var renewed = false
            if (result != null && local) {
                runCatching { autoSetup(connected) }.onFailure { log(tr("ההגדרה האוטומטית נכשלה: ${explain(it)}", "Automatic setup failed: ${explain(it)}")) }
                // Keep ADB alive without Wi‑Fi: start (or, after a reboot, renew) adbd's TCP mode on 5555.
                if (keepAdbWithoutWifi && !isLocalPortOpen(LEGACY_PORT)) {
                    log(tr("מפעיל ADB קבוע (פורט $LEGACY_PORT) — יישאר פעיל גם בלי Wi‑Fi עד הפעלה מחדש של המכשיר.", "Turning on persistent ADB (port $LEGACY_PORT) — stays on without Wi‑Fi until the device restarts."))
                    renewed = runCatching {
                        connected.shell("settings put global adb_enabled 1")
                        log("ADB: ${connected.tcpip(LEGACY_PORT)}")
                    }.isSuccess
                }
            }
            runCatching { connected.close() }
            if (renewed && !prefs.getBoolean("legacy_authorized", false)) authorizeLegacyOnce()
        }
        return result!!.getOrThrow()
    }

    /** First time only: connect to 5555 so Android asks "Allow debugging?" while the user is here. */
    private suspend fun authorizeLegacyOnce() {
        delay(3_000)
        status(tr("אשר את החלון \"לאפשר ניפוי באגים?\" (סמן \"אפשר תמיד\") — כדי ש־ADB יישאר פעיל גם בלי Wi‑Fi.", "Confirm the \"Allow USB debugging?\" window (check \"Always allow\") — so ADB stays on without Wi‑Fi."))
        val ok = runCatching { connectLegacy(60_000).close() }.isSuccess
        if (ok) {
            prefs.edit().putBoolean("legacy_authorized", true).apply()
            status(tr("✔ ADB קבוע פעיל: הבוס יעבוד גם כשה־Wi‑Fi כבוי, עד ההפעלה מחדש הבאה של המכשיר.", "✔ Persistent ADB is on: The Boss works even when Wi‑Fi is off, until the device's next restart."))
        } else {
            log(tr("החיבור הקבוע עדיין לא אושר; ינוסה שוב בפעולה הבאה.", "The persistent connection wasn't approved yet; it will be retried on the next action."))
        }
    }

    /**
     * Runs once per missing item after a successful connection to this device: grants the
     * permissions APK Bridge can use, enables its accessibility service, exempts it from battery
     * optimization and installs the Wi‑Fi helper. Each step is skipped when already in place.
     */
    private suspend fun autoSetup(c: AdbClient) {
        if (!autoSetupEnabled) return
        val pkg = app.packageName
        suspend fun run(label: String, cmd: String) {
            val r = c.shell(cmd)
            val out = (r.stdoutText + r.stderrText).trim()
            log(if (r.exitCode == 0) tr("✔ הוגדר אוטומטית: $label", "✔ Set up automatically: $label") else tr("לא ניתן להגדיר $label: ${out.ifBlank { "exit ${r.exitCode}" }}", "Can't set up $label: ${out.ifBlank { "exit ${r.exitCode}" }}"))
        }
        if (!canWriteSecureSettings()) {
            run(tr("הפעלה אוטומטית של ניפוי באגים אלחוטי", "Automatic turning on of wireless debugging"), "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            run(tr("הרשאת התראות", "Notification permission"), "pm grant $pkg android.permission.POST_NOTIFICATIONS")
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            run(tr("התקנת אפליקציות (עזר ה־Wi‑Fi)", "Installing apps (Wi‑Fi helper)"), "appops set $pkg REQUEST_INSTALL_PACKAGES allow")
        }
        val power = app.getSystemService(android.os.PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(pkg)) {
            run(tr("החרגה מחיסכון בסוללה", "Battery optimization exemption"), "dumpsys deviceidle whitelist +$pkg")
        }
        if (!hasUsageAccess()) {
            // Lets the apps manager show real app size (data + cache) and "last used".
            run(tr("גישה לנתוני שימוש (גודל ושימוש אחרון של אפליקציות)", "Usage access (apps' size and last use)"), "appops set $pkg GET_USAGE_STATS allow")
        }
        if (Compat.hasWirelessDebugging && accessibilityAutoPair && !PairingAccessibilityService.isEnabled(app)) {
            val me = android.content.ComponentName(app, PairingAccessibilityService::class.java).flattenToString()
            val current = runCatching {
                Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull().orEmpty()
            val list = (current.split(':').filter { it.isNotBlank() } + me).distinct().joinToString(":")
            run(
                tr("שירות הנגישות לצימוד אוטומטי", "Accessibility service for automatic pairing"),
                "settings put secure enabled_accessibility_services '$list' && settings put secure accessibility_enabled 1",
            )
        }
        if (autoWifi && !isWifiHelperInstalled() && isWifiHelperBundled()) {
            val file = File(app.cacheDir, "wifi-helper.apk")
            runCatching {
                app.assets.open(WIFI_HELPER_ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
                c.install.install(RandomAccessSource.of(file), listOf("-r")).collect { }
                log(tr("✔ הוגדר אוטומטית: עזר ה־Wi‑Fi הותקן.", "✔ Set up automatically: the Wi‑Fi helper was installed."))
            }.onFailure { log(tr("התקנת עזר ה־Wi‑Fi נכשלה: ${explain(it)}", "Installing the Wi‑Fi helper failed: ${explain(it)}")) }
            file.delete()
        }
    }

    private fun isLocalPortOpen(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(LOCAL_HOST, port), 400); true }
    }.getOrDefault(false)

    /** Plain-TCP ADB with RSA auth. The first time Android may ask "Allow debugging?" — tick "Always allow". */
    private suspend fun connectLegacy(handshakeMs: Long, host: String = LOCAL_HOST): AdbClient = withContext(Dispatchers.IO) {
        withTimeout(handshakeMs + 5_000) {
            AdbClient.connectTcp(host, LEGACY_PORT, keyProvider(), handshakeMs)
        }
    }

    /** Android 8–10 (or an old target device): no Wireless Debugging, only classic ADB over TCP. */
    private suspend fun <T> withLegacyClient(host: String, block: suspend (AdbClient) -> T): T {
        val local = isLocalTarget(host)
        val target = if (local) LOCAL_HOST else host
        log(tr("מתחבר ל־ADB הקלאסי ב־$target:$LEGACY_PORT…", "Connecting to classic ADB at $target:$LEGACY_PORT…"))
        if (local && !isLocalPortOpen(LEGACY_PORT)) error(LEGACY_HELP)
        val c = try {
            connectLegacy(60_000, target)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            error(tr("החיבור לא אושר בזמן. אשר את החלון \"לאפשר ניפוי באגים?\" (סמן \"אפשר תמיד\") ונסה שוב.", "The connection wasn't approved in time. Confirm the \"Allow USB debugging?\" window (check \"Always allow\") and try again."))
        }
        return try {
            log(tr("מחובר (ADB קלאסי).", "Connected (classic ADB)."))
            block(c).also { if (local) runCatching { autoSetup(c) } }
        } finally {
            runCatching { c.close() }
        }
    }

    /** Switches adbd to TCP mode on 5555 and authorizes this app on it, so ADB survives Wi‑Fi off. */
    fun enableKeepAdb() = runJob(tr("הפעלת ADB קבוע", "Turning on persistent ADB")) {
        keepAdbWithoutWifi = true
        if (!isLocalPortOpen(LEGACY_PORT)) {
            status(tr("מעביר את ADB למצב קבוע (פורט $LEGACY_PORT)…", "Switching ADB to persistent mode (port $LEGACY_PORT)…"))
            withClient { c ->
                // adbd must stay alive after Wireless Debugging turns off with Wi‑Fi, so keep USB debugging on.
                c.shell("settings put global adb_enabled 1")
                log("ADB: ${c.tcpip(LEGACY_PORT)}")
            }
            delay(3_000)
        }
        status(tr("מאשר את החיבור הקבוע… אם מופיע חלון \"לאפשר ניפוי באגים?\" — סמן \"אפשר תמיד מהמחשב הזה\" ולחץ אישור.", "Approving the persistent connection… If an \"Allow USB debugging?\" window appears — check \"Always allow from this computer\" and tap OK."))
        val c = connectLegacy(90_000)
        try {
            val model = c.shell("getprop ro.product.model").stdoutText.trim()
            status(tr("✔ ADB קבוע פעיל ($model): הבוס יעבוד גם כש־Wi‑Fi כבוי, עד ההפעלה מחדש הבאה של המכשיר. אחרי הפעלה מחדש הוא יחודש אוטומטית בחיבור הראשון דרך Wi‑Fi.", "✔ Persistent ADB is on ($model): The Boss works even when Wi‑Fi is off, until the device's next restart. After a restart it's renewed automatically on the first Wi‑Fi connection."))
        } finally {
            runCatching { c.close() }
        }
    }

    fun disableKeepAdb() = runJob(tr("כיבוי ADB קבוע", "Turning off persistent ADB")) {
        keepAdbWithoutWifi = false
        if (isLocalPortOpen(LEGACY_PORT)) {
            runCatching {
                val c = connectLegacy(20_000)
                try { c.open("usb:").close() } finally { runCatching { c.close() } }
            }
        }
        status(tr("ADB קבוע כובה. הפורט $LEGACY_PORT ייסגר לכל המאוחר בהפעלה מחדש של המכשיר.", "Persistent ADB turned off. Port $LEGACY_PORT closes at the latest when the device restarts."))
    }

    private fun wifiIpv4Addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    /** Finds loopback ports whose server replies to an ADB CNXN with A_STLS (the Wireless Debugging port). */
    private suspend fun scanLocalAdbPorts(): List<Int> = withContext(Dispatchers.IO) {
        val open = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val all = (30000..65535).toList()
        val chunk = (all.size + 63) / 64
        coroutineScope {
            all.chunked(chunk).forEach { ports ->
                launch {
                    for (port in ports) {
                        val ok = runCatching {
                            Socket().use { it.connect(InetSocketAddress(LOCAL_HOST, port), 200); true }
                        }.getOrDefault(false)
                        if (ok) open += port
                    }
                }
            }
        }
        open.sorted().filter { probeStls(it) }
    }

    private fun probeStls(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LOCAL_HOST, port), 500)
            socket.soTimeout = 1500
            val payload = "host::\u0000".toByteArray(Charsets.US_ASCII)
            val header = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val cnxn = 0x4e584e43
            header.putInt(cnxn).putInt(0x01000001).putInt(256 * 1024).putInt(payload.size)
                .putInt(payload.sumOf { it.toInt() and 0xFF }).putInt(cnxn xor -1)
            socket.getOutputStream().apply { write(header.array()); write(payload); flush() }
            val reply = ByteArray(4)
            var read = 0
            val input = socket.getInputStream()
            while (read < 4) {
                val r = input.read(reply, read, 4 - read)
                if (r < 0) return@runCatching false
                read += r
            }
            java.nio.ByteBuffer.wrap(reply).order(java.nio.ByteOrder.LITTLE_ENDIAN).int == 0x534c5453
        }
    }.getOrDefault(false)

    private suspend fun describeDevice(client: AdbClient) {
        val info = client.shell("echo \"\$(getprop ro.product.manufacturer) \$(getprop ro.product.model) · Android \$(getprop ro.build.version.release) (SDK \$(getprop ro.build.version.sdk))\"")
            .stdoutText.trim()
        lastConnectionOk = true
        status(tr("מחובר ומוכן: $info", "Connected and ready: $info"))
    }

    fun testConnection() = runJob(tr("בדיקת החיבור", "Connection check")) {
        status(tr("בודק חיבור…", "Checking the connection…"))
        withClient { describeDevice(it) }
    }

    // endregion


    // region tools

    /**
     * Runs any shell command as the ADB "shell" user and streams its output.
     * Accepts "adb shell …" / "adb …" prefixes as typed on a computer.
     * [onOutput] is called on the main thread with (text, isError); [onExit] with the exit code (null = failed/cancelled).
     */
    fun runCommand(raw: String, onOutput: (String, Boolean) -> Unit, onExit: (Int?) -> Unit): Boolean {
        val cmd = normalizeCommand(raw)
        if (cmd.isBlank()) return false
        var exit: Int? = null
        val started = runJob(tr("הפקודה", "Command")) {
            status(tr("מריץ: $cmd", "Running: $cmd"))
            try {
                withClient { c ->
                    c.shellFlow(cmd).collect { chunk ->
                        when (chunk) {
                            is io.github.rhythmcache.adb.ShellChunk.Stdout -> main.post { onOutput(chunk.text, false) }
                            is io.github.rhythmcache.adb.ShellChunk.Stderr -> main.post { onOutput(chunk.text, true) }
                            is io.github.rhythmcache.adb.ShellChunk.Exit -> exit = chunk.code
                        }
                    }
                }
                status(tr("הפקודה הסתיימה (קוד יציאה ${exit ?: 0}).", "Command finished (exit code ${exit ?: 0})."))
                if (exit == null) exit = 0
            } finally {
                val code = exit
                main.post { onExit(code) }
            }
        }
        return started
    }

    /** Smart "adb shell" handling (no doubling, prompts, code blocks, host commands) — see [CommandParser]. */
    fun normalizeCommand(raw: String): String = CommandParser.normalize(raw)

    /** "Usage access" lets the apps manager read real app sizes and last-used times. */
    fun hasUsageAccess(): Boolean = runCatching {
        val ops = app.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName)
        }
        mode == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun grantUsageAccess(onDone: () -> Unit) = runJob(tr("מתן גישה לנתוני שימוש", "Granting usage access")) {
        try {
            withClient { c -> c.shell("appops set ${app.packageName} GET_USAGE_STATS allow") }
            status(if (hasUsageAccess()) tr("✔ ניתנה גישה לנתוני שימוש.", "✔ Usage access granted.") else tr("הפקודה נשלחה; ייתכן שיש לפתוח את המסך מחדש.", "Command sent; you may need to reopen the screen."))
        } finally {
            main.post(onDone)
        }
    }

    /**
     * Runs one shell command per item over a single ADB connection and reports a summary.
     * [items] are (label, command). [onDone] runs on the main thread when finished.
     */
    fun batch(title: String, items: List<Pair<String, String>>, onDone: () -> Unit) = runJob(title) {
        val failed = mutableListOf<String>()
        var ok = 0
        try {
            withClient { c ->
                items.forEachIndexed { i, (label, cmd) ->
                    update { it.copy(progress = i * 100 / items.size.coerceAtLeast(1), status = "$title: $label (${i + 1}/${items.size})") }
                    val r = c.shell(cmd)
                    val out = (r.stdoutText + r.stderrText).trim()
                    if (r.exitCode == 0 && !out.contains("Failure") && !out.startsWith("Error") && !out.contains("Exception")) {
                        ok++
                        log("✔ $title: $label")
                    } else {
                        failed += label
                        log("✖ $title: $label — ${out.ifBlank { "exit ${r.exitCode}" }}")
                    }
                }
            }
            status(
                tr("$title: הצליח $ok מתוך ${items.size}.", "$title: $ok of ${items.size} succeeded.") +
                    if (failed.isNotEmpty()) tr("\nנכשל: ${failed.joinToString(", ")} (פרטים ביומן).", "\nFailed: ${failed.joinToString(", ")} (details in the log).") else "",
                100,
            )
        } finally {
            main.post(onDone)
        }
    }

    fun uninstall(pkg: String, keepData: Boolean) = runJob(tr("הסרת ההתקנה", "Uninstall")) {
        val name = pkg.trim()
        require(name.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { tr("שם חבילה לא תקין.", "Invalid package name.") }
        status(tr("מסיר את $name…", "Uninstalling $name…"))
        withClient { client ->
            val r = client.shell("pm uninstall ${if (keepData) "-k " else ""}$name")
            val out = (r.stdoutText + r.stderrText).trim()
            if (out.contains("Success")) status(tr("✔ $name הוסר.", "✔ $name uninstalled.")) else error(out.ifBlank { tr("תשובה ריקה", "empty response") })
        }
    }

    fun grantAutoEnable() = runJob(tr("הענקת ההרשאה", "Granting the permission")) {
        status(tr("מעניק לאפליקציית הבוס הרשאה להפעיל ניפוי באגים אלחוטי בעצמו…", "Giving The Boss permission to turn on wireless debugging by itself…"))
        withClient { client ->
            val r = client.shell("pm grant ${app.packageName} android.permission.WRITE_SECURE_SETTINGS")
            val out = (r.stdoutText + r.stderrText).trim()
            if (r.exitCode != 0) error(out.ifBlank { "exit ${r.exitCode}" })
        }
        status(
            if (canWriteSecureSettings()) tr("✔ ההרשאה ניתנה. מעכשיו הבוס יפעיל את ניפוי הבאגים האלחוטי בעצמו כשהוא כבוי (כל עוד יש Wi‑Fi).", "✔ Permission granted. From now on The Boss turns wireless debugging on by itself when it's off (as long as there's Wi‑Fi).")
            else tr("הפקודה נשלחה; ההרשאה תיכנס לתוקף לאחר הפעלה מחדש של האפליקציה.", "Command sent; the permission takes effect after the app restarts.")
        )
    }

    // endregion

    /** A notification with a tap action (used when no screen of the app is open). */
    internal fun notifyInfo(title: String, text: String, target: android.content.Intent?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = app.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(INSTALL_CHANNEL) == null) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(INSTALL_CHANNEL, tr("תוצאות התקנה", "Install results"), android.app.NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = (target ?: android.content.Intent(app, MainActivity::class.java))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = android.app.PendingIntent.getActivity(
            app, title.hashCode(), intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = android.app.Notification.Builder(app, INSTALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(title.hashCode(), n)
    }

    /** True while a screen of the app is open and listening to the state. */
    internal val isScreenListening get() = stateListeners.isNotEmpty()

    // region utilities

    fun displayName(uri: Uri): String = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file.apk"

    fun sizeOf(uri: Uri): Long = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
        }
    }.getOrNull() ?: -1L

    internal fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "?"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
    }

    /** Full cause chain, for the log, so a failure can be diagnosed from a copied log. */
    fun details(e: Throwable): String = generateSequence(e) { it.cause }.take(6)
        .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message ?: ""}".trim() }

    /** Turns low-level failures into a short actionable Hebrew explanation. */
    fun explain(e: Throwable): String {
        val msg = generateSequence(e) { it.cause }.mapNotNull { it.message }.firstOrNull().orEmpty()
        return when {
            e is AdbException.NotPaired -> tr("המכשיר דחה את המפתח — יש לבצע צימוד מחדש.", "The device rejected the key — pair again.")
            e is java.net.ConnectException -> tr("החיבור נדחה (הפורט סגור). ייתכן שהפורט השתנה או שניפוי הבאגים כבוי.", "Connection refused (the port is closed). The port may have changed or debugging is off.")
            e is java.net.SocketTimeoutException || e is kotlinx.coroutines.TimeoutCancellationException -> tr("תם הזמן להמתנה לתשובה.", "Timed out waiting for a response.")
            msg.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") -> tr("חתימת ה־APK שונה מזו של האפליקציה המותקנת. יש להסיר את הגרסה הקיימת קודם (לשונית אפליקציות ← הסר).", "The APK's signature differs from the installed app's. Uninstall the existing version first (Apps tab → Uninstall).")
            msg.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> tr("הגרסה ישנה מהמותקנת. סמן \"אפשר שנמוך גרסה\" ונסה שוב.", "This version is older than the installed one. Check \"Allow downgrade\" and try again.")
            msg.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION") -> tr("האפליקציה מיועדת לגרסת Android ישנה מדי. סמן \"עקוף חסימת targetSdk נמוך\".", "The app targets an Android version that is too old. Check \"Bypass low targetSdk block\".")
            msg.contains("INSTALL_FAILED_TEST_ONLY") -> tr("זה APK מסוג test-only. סמן \"אפשר חבילות בדיקה\".", "This is a test-only APK. Check \"Allow test packages\".")
            msg.contains("INSTALL_FAILED_NO_MATCHING_ABIS") -> tr("האפליקציה לא תומכת במעבד של המכשיר.", "The app doesn't support the device's processor.")
            msg.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> tr("אין מספיק מקום פנוי במכשיר.", "Not enough free space on the device.")
            msg.contains("INSTALL_FAILED_OLDER_SDK") -> tr("האפליקציה דורשת גרסת Android חדשה יותר.", "The app requires a newer Android version.")
            msg.contains("INSTALL_FAILED_MISSING_SPLIT") -> tr("חסרים חלקי APK (split). בחר את החבילה המלאה (APKS/XAPK) או את כל הקבצים יחד.", "APK parts (splits) are missing. Choose the full package (APKS/XAPK) or all the files together.")
            msg.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES") -> tr("ה־APK לא חתום.", "The APK isn't signed.")
            msg.contains("INSTALL_FAILED_USER_RESTRICTED") -> tr("ההתקנה נחסמה. בשיאומי/MIUI יש להפעיל \"Install via USB\" באפשרויות למפתחים.", "Install blocked. On Xiaomi/MIUI turn on \"Install via USB\" in Developer options.")
            msg.contains("INSTALL_FAILED_ALREADY_EXISTS") -> tr("החבילה כבר קיימת וה־Package Manager סירב להחליף אותה.", "The package already exists and Package Manager refused to replace it.")
            msg.isNotBlank() -> msg
            else -> e.javaClass.simpleName
        }
    }

    // endregion
}
