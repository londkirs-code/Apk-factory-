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
    val status: String = "מוכן.",
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
    private const val PLAY_STORE = "com.android.vending"
    private const val INSTALL_CHANNEL = "installs"
    const val LEGACY_PORT = 5555
    val LEGACY_HELP = "ב־Android ${Build.VERSION.RELEASE} אין ניפוי באגים אלחוטי עם קוד חיבור (קיים רק מ־Android 11). " +
        "כדי להשתמש ב־APK Bridge: הפעל \"ניפוי באגים ב־USB\" באפשרויות למפתחים, חבר פעם אחת למחשב והרץ: adb tcpip 5555 — " +
        "ואז אפשר לנתק את הכבל. יש לחזור על כך אחרי כל הפעלה מחדש של המכשיר."
    const val WIFI_HELPER_PKG = "com.londkirs.apkbridge.wifihelper"
    private const val WIFI_HELPER_ACTION = "com.londkirs.apkbridge.wifihelper.ENABLE_WIFI"
    private const val WIFI_HELPER_ASSET = "wifi-helper.apk"
    private val KNOWN_ABIS = listOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "mips64", "mips")

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val logLines = ArrayDeque<String>()
    private val logListeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val stateListeners = CopyOnWriteArraySet<(BridgeState) -> Unit>()

    @Volatile var state = BridgeState()
        private set

    @Volatile private var currentJob: Job? = null

    fun init(context: Context) {
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

    private val prefs get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
    var autoInstallUpdatesOnly: Boolean
        get() = prefs.getBoolean("auto_install_updates_only", false)
        set(value) = prefs.edit().putBoolean("auto_install_updates_only", value).apply()

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
        log("החיבור נמחק. בפעם הבאה יידרש חיבור חדש.")
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
            log("אין חיבור Wi‑Fi — מדליק Wi‑Fi דרך עזר ה־Wi‑Fi…")
            update { it.copy(status = "מדליק Wi‑Fi וממתין לחיבור לרשת…", progress = -1) }
            if (requestWifiFromHelper()) {
                val up = withTimeoutOrNull(25_000) {
                    while (!isOnWifi()) delay(700)
                    true
                } ?: false
                if (up) {
                    log("Wi‑Fi הודלק והתחבר. ממשיך…")
                    delay(1500)
                    return true
                }
                log("ה־Wi‑Fi הודלק אבל לא התחבר לרשת שמורה בתוך 25 שניות.")
            } else {
                log("עזר ה־Wi‑Fi לא הצליח להדליק את ה־Wi‑Fi.")
            }
        }
        log("אין חיבור Wi‑Fi — פותח את חלונית ה־Wi‑Fi של Android.")
        if (wifiPanelListeners.isEmpty()) {
            notifyInfo("APK Bridge צריך Wi‑Fi", "הקש כדי להדליק Wi‑Fi. הפעולה תמשיך אוטומטית.", Compat.wifiPanelIntent())
        } else {
            main.post { wifiPanelListeners.forEach { it() } }
        }
        update { it.copy(status = "ממתין לחיבור Wi‑Fi… (הדלק Wi‑Fi בחלונית שנפתחה)", progress = -1) }
        val connected = withTimeoutOrNull(120_000) {
            while (!isOnWifi()) delay(1000)
            true
        } ?: false
        if (connected) {
            log("Wi‑Fi מחובר. ממשיך…")
            delay(1500)
        } else {
            log("לא התקבל חיבור Wi‑Fi בתוך 2 דקות.")
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

    private fun update(transform: (BridgeState) -> BridgeState) {
        val next = synchronized(this) { transform(state).also { state = it } }
        main.post { stateListeners.forEach { it(next) } }
    }

    private fun status(text: String, progress: Int = -1) {
        log(text)
        update { it.copy(status = text, progress = progress) }
    }

    /** Runs [block] as the single active bridge job; refuses to start a second one. */
    private fun runJob(name: String, block: suspend () -> Unit): Boolean {
        synchronized(this) {
            if (currentJob?.isActive == true) {
                log("פעולה אחרת עדיין רצה — המתן לסיומה.")
                return false
            }
            update { it.copy(busy = true, progress = -1) }
            currentJob = scope.launch {
                try {
                    block()
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    status("$name נכשל: ${explain(e)}")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    status("$name בוטל.")
                } catch (e: Throwable) {
                    log("פרטים טכניים: ${details(e)}")
                    status("$name נכשל: ${explain(e)}")
                } finally {
                    update { it.copy(busy = false) }
                }
            }
        }
        return true
    }

    fun cancelCurrent() { currentJob?.cancel() }

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
            log("אין חיבור Wi‑Fi — Android לא יפעיל ניפוי באגים אלחוטי בלי Wi‑Fi.")
            return
        }
        val ok = runCatching {
            Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 1)
        }.getOrDefault(false)
        if (ok) {
            log("ניפוי באגים אלחוטי הופעל אוטומטית. ממתין לעליית השירות…")
            delay(2500)
        }
    }

    private fun isLocalTarget(host: String) =
        host == LOCAL_HOST || host.equals("localhost", true) || host == "::1"

    private fun localAddresses(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .toSet()
    }.getOrDefault(emptySet())

    @Suppress("DEPRECATION")
    private fun addressesOf(info: NsdServiceInfo): List<InetAddress> =
        if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)

    // endregion

    // region mDNS discovery

    /**
     * Finds a Wireless Debugging service via mDNS. For the local device the
     * returned host is 127.0.0.1, which adbd accepts and which survives Wi‑Fi IP changes.
     */
    @Suppress("DEPRECATION")
    suspend fun discover(serviceType: String, host: String, timeoutMs: Long): Endpoint? {
        val nsd = app.getSystemService(NsdManager::class.java) ?: return null
        val wantLocal = isLocalTarget(host)
        val locals = localAddresses()
        val result = CompletableDeferred<Endpoint?>()
        val lock = Any()
        val queue = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        var fallback: Endpoint? = null

        fun accept(info: NsdServiceInfo) {
            val addrs = addressesOf(info).mapNotNull { it.hostAddress?.substringBefore('%') }
            val port = info.port
            if (port <= 0) return
            val matches = if (wantLocal) addrs.any { it in locals } else addrs.any { it == host }
            val endpointHost = if (wantLocal) LOCAL_HOST
                else addrs.firstOrNull { it == host } ?: addrs.firstOrNull() ?: host
            val ep = Endpoint(endpointHost, port)
            if (matches) result.complete(ep) else synchronized(lock) { if (fallback == null) fallback = ep }
        }

        fun resolveNext() {
            val next = synchronized(lock) {
                if (resolving || result.isCompleted) null else queue.removeFirstOrNull()?.also { resolving = true }
            } ?: return
            // A fresh listener per call: NsdManager rejects re-using one that is still registered,
            // and older Android versions only resolve one service at a time.
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    synchronized(lock) { resolving = false }
                    accept(info)
                    resolveNext()
                }
            }
            runCatching { nsd.resolveService(next, listener) }.onFailure {
                synchronized(lock) { resolving = false }
            }
        }

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("גילוי mDNS נכשל (קוד $errorCode).")
                result.complete(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { queue.addLast(serviceInfo) }
                resolveNext()
            }
        }

        val multicast = runCatching {
            (app.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
                .createMulticastLock("apkbridge-mdns").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        var started = false
        try {
            started = runCatching {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discovery)
            }.isSuccess
            if (!started) return null
            return withTimeoutOrNull(timeoutMs) { result.await() } ?: synchronized(lock) { fallback }
        } finally {
            if (started) runCatching { nsd.stopServiceDiscovery(discovery) }
            runCatching { multicast?.release() }
        }
    }

    // endregion

    // region pairing

    suspend fun pairNow(host: String, port: Int?, code: String): Boolean {
        val cleanCode = code.filter(Char::isDigit)
        require(cleanCode.length == 6) { "קוד החיבור חייב להכיל 6 ספרות." }
        val target = host.trim().ifBlank { LOCAL_HOST }
        val pairPort = port ?: run {
            log("מחפש את פורט החיבור ברשת (mDNS)…")
            discover(PAIRING_SERVICE, target, 8_000)?.port
        } ?: error("לא נמצא שירות חיבור פעיל. ודא שחלון \"התאמת מכשיר עם קוד התאמה\" פתוח, או הזן את הפורט ידנית.")
        log("מתחבר מול $target:$pairPort…")
        val result = withContext(Dispatchers.IO) {
            withTimeout(30_000) { AdbClient.pairTls(target, pairPort, cleanCode, keyProvider()) }
        }
        val error = result.exceptionOrNull()
        if (error != null) {
            log("החיבור נכשל: ${explain(error)}")
            return false
        }
        isPaired = true
        targetHost = target
        log("החיבור הצליח. המפתח נשמר באחסון הפרטי של האפליקציה.")
        return true
    }

    fun pair(host: String, port: Int?, code: String) = runJob("החיבור") {
        status("מבצע חיבור מאובטח…")
        if (pairNow(host, port, code)) {
            status("הקוד אושר. מתחבר…")
            withClient { describeDevice(it) }
        } else {
            status("החיבור נכשל. בדוק את הקוד ושהחלון עדיין פתוח, ונסה שוב.")
        }
    }

    /** Called by the accessibility service when it reads a pairing code off the Settings screen. */
    fun autoPair(port: Int, code: String) = runJob("החיבור האוטומטי") {
        status("זוהה קוד חיבור במסך — מתחבר אוטומטית…")
        if (pairNow(LOCAL_HOST, port, code)) {
            notifyInfo("APK Bridge התחבר בהצלחה", "החיבור האוטומטי הצליח. אפשר לחזור ל־APK Bridge ולהתקין.", null)
            status("החיבור האוטומטי הצליח. בודק חיבור…")
            withClient { describeDevice(it) }
        } else {
            status("החיבור האוטומטי נכשל. אם הקוד התחלף, הוא ינוסה שוב אוטומטית.")
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
            log("מתחבר דרך ADB קבוע (127.0.0.1:$LEGACY_PORT) — לא נדרש Wi‑Fi…")
            val legacy = try {
                connectLegacy(30_000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) null else throw e
            } catch (e: Throwable) {
                log("החיבור הקבוע נכשל: ${explain(e)}")
                null
            }
            if (legacy != null) {
                return try {
                    log("מחובר (ADB קבוע).")
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
            log("מתחבר ל־${ep.host}:${ep.port}…")
            return try {
                connectTo(ep).also {
                    savedConnectPort = ep.port
                    isPaired = true
                }
            } catch (e: AdbException.NotPaired) {
                isPaired = false
                throw IllegalStateException("המכשיר דחה את המפתח של APK Bridge. יש לבצע חיבור מחדש.", e)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastError = e
                log("החיבור ל־${ep.port} לא הגיב בזמן.")
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                log("החיבור ל־${ep.host}:${ep.port} נכשל: ${explain(e)}")
                log("פרטים טכניים: ${details(e)}")
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
            log("מאתר את פורט Wireless Debugging (mDNS)…")
            val found = discover(CONNECT_SERVICE, host, 10_000)
            if (found == null) log("גילוי mDNS לא מצא את שירות החיבור.")
            else {
                log("mDNS מצא את פורט ${found.port}.")
                client = attempt(found) ?: attemptAll(found.port)
            }
        }
        if (client == null && local) {
            // mDNS is flaky on many devices. adbd's TLS port is a random high port on this
            // device, so scan loopback and keep only ports that answer with ADB's A_STLS.
            log("סורק פורטים מקומיים כדי למצוא את ADB…")
            val ports = scanLocalAdbPorts()
            log(if (ports.isEmpty()) "לא נמצא פורט ADB פתוח במכשיר." else "נמצאו פורטים של ADB: ${ports.joinToString()}")
            for (port in ports) {
                client = attemptAll(port)
                if (client != null) break
            }
        }
        if (client == null && !local) {
            // An older target device (Android 4–10) only offers classic ADB on port 5555.
            log("מנסה ADB קלאסי ב־$host:$LEGACY_PORT (מכשיר יעד ישן)…")
            client = runCatching { connectLegacy(60_000, host) }.getOrNull()
        }
        if (client == null) {
            val hint = when {
                isWirelessDebuggingOn() == false -> "ניפוי באגים אלחוטי כבוי. הפעל אותו באפשרויות למפתחים."
                !isOnWifi() -> "המכשיר לא מחובר ל־Wi‑Fi; ניפוי באגים אלחוטי דורש Wi‑Fi."
                else -> "ודא שניפוי באגים אלחוטי פעיל. ${lastError?.let { explain(it) } ?: ""}"
            }
            error("לא ניתן להתחבר ל־ADB. $hint")
        }
        val connected: AdbClient = client
        var result: Result<T>? = null
        try {
            log("מחובר.")
            result = Result.success(block(connected))
        } finally {
            var renewed = false
            if (result != null && local) {
                runCatching { autoSetup(connected) }.onFailure { log("ההגדרה האוטומטית נכשלה: ${explain(it)}") }
                // Keep ADB alive without Wi‑Fi: start (or, after a reboot, renew) adbd's TCP mode on 5555.
                if (keepAdbWithoutWifi && !isLocalPortOpen(LEGACY_PORT)) {
                    log("מפעיל ADB קבוע (פורט $LEGACY_PORT) — יישאר פעיל גם בלי Wi‑Fi עד הפעלה מחדש של המכשיר.")
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
        status("אשר את החלון \"לאפשר ניפוי באגים?\" (סמן \"אפשר תמיד\") — כדי ש־ADB יישאר פעיל גם בלי Wi‑Fi.")
        val ok = runCatching { connectLegacy(60_000).close() }.isSuccess
        if (ok) {
            prefs.edit().putBoolean("legacy_authorized", true).apply()
            status("✔ ADB קבוע פעיל: APK Bridge יעבוד גם כשה־Wi‑Fi כבוי, עד ההפעלה מחדש הבאה של המכשיר.")
        } else {
            log("החיבור הקבוע עדיין לא אושר; ינוסה שוב בפעולה הבאה.")
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
            log(if (r.exitCode == 0) "✔ הוגדר אוטומטית: $label" else "לא ניתן להגדיר $label: ${out.ifBlank { "exit ${r.exitCode}" }}")
        }
        if (!canWriteSecureSettings()) {
            run("הפעלה אוטומטית של ניפוי באגים אלחוטי", "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            run("הרשאת התראות", "pm grant $pkg android.permission.POST_NOTIFICATIONS")
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            run("התקנת אפליקציות (עזר ה־Wi‑Fi)", "appops set $pkg REQUEST_INSTALL_PACKAGES allow")
        }
        val power = app.getSystemService(android.os.PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(pkg)) {
            run("החרגה מחיסכון בסוללה", "dumpsys deviceidle whitelist +$pkg")
        }
        if (Compat.hasWirelessDebugging && accessibilityAutoPair && !PairingAccessibilityService.isEnabled(app)) {
            val me = android.content.ComponentName(app, PairingAccessibilityService::class.java).flattenToString()
            val current = runCatching {
                Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull().orEmpty()
            val list = (current.split(':').filter { it.isNotBlank() } + me).distinct().joinToString(":")
            run(
                "שירות הנגישות לחיבור אוטומטי",
                "settings put secure enabled_accessibility_services '$list' && settings put secure accessibility_enabled 1",
            )
        }
        if (autoWifi && !isWifiHelperInstalled() && isWifiHelperBundled()) {
            val file = File(app.cacheDir, "wifi-helper.apk")
            runCatching {
                app.assets.open(WIFI_HELPER_ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
                c.install.install(RandomAccessSource.of(file), listOf("-r")).collect { }
                log("✔ הוגדר אוטומטית: עזר ה־Wi‑Fi הותקן.")
            }.onFailure { log("התקנת עזר ה־Wi‑Fi נכשלה: ${explain(it)}") }
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
        log("מתחבר ל־ADB הקלאסי ב־$target:$LEGACY_PORT…")
        if (local && !isLocalPortOpen(LEGACY_PORT)) error(LEGACY_HELP)
        val c = try {
            connectLegacy(60_000, target)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            error("החיבור לא אושר בזמן. אשר את החלון \"לאפשר ניפוי באגים?\" (סמן \"אפשר תמיד\") ונסה שוב.")
        }
        return try {
            log("מחובר (ADB קלאסי).")
            block(c).also { if (local) runCatching { autoSetup(c) } }
        } finally {
            runCatching { c.close() }
        }
    }

    /** Switches adbd to TCP mode on 5555 and authorizes this app on it, so ADB survives Wi‑Fi off. */
    fun enableKeepAdb() = runJob("הפעלת ADB קבוע") {
        keepAdbWithoutWifi = true
        if (!isLocalPortOpen(LEGACY_PORT)) {
            status("מעביר את ADB למצב קבוע (פורט $LEGACY_PORT)…")
            withClient { c ->
                // adbd must stay alive after Wireless Debugging turns off with Wi‑Fi, so keep USB debugging on.
                c.shell("settings put global adb_enabled 1")
                log("ADB: ${c.tcpip(LEGACY_PORT)}")
            }
            delay(3_000)
        }
        status("מאשר את החיבור הקבוע… אם מופיע חלון \"לאפשר ניפוי באגים?\" — סמן \"אפשר תמיד מהמחשב הזה\" ולחץ אישור.")
        val c = connectLegacy(90_000)
        try {
            val model = c.shell("getprop ro.product.model").stdoutText.trim()
            status("✔ ADB קבוע פעיל ($model): APK Bridge יעבוד גם כש־Wi‑Fi כבוי, עד ההפעלה מחדש הבאה של המכשיר. אחרי הפעלה מחדש הוא יחודש אוטומטית בחיבור הראשון דרך Wi‑Fi.")
        } finally {
            runCatching { c.close() }
        }
    }

    fun disableKeepAdb() = runJob("כיבוי ADB קבוע") {
        keepAdbWithoutWifi = false
        if (isLocalPortOpen(LEGACY_PORT)) {
            runCatching {
                val c = connectLegacy(20_000)
                try { c.open("usb:").close() } finally { runCatching { c.close() } }
            }
        }
        status("ADB קבוע כובה. הפורט $LEGACY_PORT ייסגר לכל המאוחר בהפעלה מחדש של המכשיר.")
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
        status("מחובר ומוכן: $info")
    }

    fun testConnection() = runJob("בדיקת החיבור") {
        status("בודק חיבור…")
        withClient { describeDevice(it) }
    }

    // endregion

    // region install

    private class InstallUnit(val label: String, val apks: List<File>, val obbs: List<Pair<String, File>>, val packageName: String?)

    fun install(uris: List<Uri>, options: InstallOptions) = runJob("ההתקנה") {
        require(uris.isNotEmpty()) { "לא נבחרו קבצים." }
        val work = File(app.cacheDir, "install-work").apply { deleteRecursively(); mkdirs() }
        try {
            status("מכין ${uris.size} קבצים…")
            val units = prepareUnits(uris, work)
            var ok = 0
            withClient { client ->
                for ((i, unit) in units.withIndex()) {
                    val prefix = if (units.size > 1) "(${i + 1}/${units.size}) " else ""
                    installUnit(client, unit, options, prefix)
                    ok++
                }
            }
            if (units.size > 1) status("הותקנו בהצלחה $ok מתוך ${units.size} חבילות.", 100)
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
        status("${prefix}מתקין ${unit.label} (${unit.apks.size} APK, ${formatSize(total)})…", 0)
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
                update { it.copy(progress = percent, status = "${prefix}מעביר ${unit.label}: $percent%") }
            }
        }
        log("${prefix}Package Manager החזיר Success.")

        val pkg = unit.packageName
        if (pkg != null && unit.obbs.isNotEmpty()) pushObbs(client, pkg, unit.obbs)
        if (pkg == null) {
            status("${prefix}ההתקנה הסתיימה בהצלחה.", 100)
            return
        }
        val line = client.shell("pm list packages -i --show-versioncode $pkg").stdoutText
            .lines().map { it.trim() }
            .firstOrNull { it == "package:$pkg" || it.startsWith("package:$pkg ") }
        val installer = line?.substringAfter("installer=", "")?.substringBefore(' ')?.takeIf { it.isNotBlank() && it != "null" }
        val version = line?.substringAfter("versionCode:", "")?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        update { it.copy(lastInstalledPackage = pkg) }
        val installerText = when {
            installer == PLAY_STORE -> "מקור ההתקנה הרשום: Google Play (com.android.vending)"
            installer != null -> "מקור ההתקנה הרשום: $installer"
            else -> "מקור ההתקנה: לא רשום"
        }
        val label = appLabel(pkg) ?: pkg
        status(
            "${prefix}✔ $label ($pkg) הותקן בהצלחה" + (version?.let { " (versionCode $it)" } ?: "") + ".\n$installerText",
            100,
        )
        update { it.copy(lastInstalledPackage = pkg, installSeq = it.installSeq + 1) }
        // When the app screen is not visible, offer "open" through a notification instead of a dialog.
        if (stateListeners.isEmpty() && installNotifications) {
            val launch = app.packageManager.getLaunchIntentForPackage(pkg)?.takeIf { openAfterInstall != "never" }
            notifyInfo("✔ $label הותקן", if (launch != null) "הקש כדי לפתוח את האפליקציה." else "ההתקנה הושלמה.", launch)
        }
    }

    fun appLabel(pkg: String): String? = runCatching {
        val pm = app.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun notifyInfo(title: String, text: String, target: android.content.Intent?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = app.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(INSTALL_CHANNEL) == null) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(INSTALL_CHANNEL, "תוצאות התקנה", android.app.NotificationManager.IMPORTANCE_DEFAULT)
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
        client.shell("mkdir -p '$dir'")
        for ((name, file) in obbs) {
            val safe = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            log("מעתיק קובץ OBB: $safe (${formatSize(file.length())})")
            val size = file.length().coerceAtLeast(1)
            FileInputStream(file).use { input ->
                client.sync.push(input, "$dir/$safe") { done ->
                    update { it.copy(progress = (done * 100 / size).toInt(), status = "מעתיק OBB: ${done * 100 / size}%") }
                }
            }
        }
        log("קובצי OBB הועתקו אל $dir.")
    }

    private suspend fun prepareUnits(uris: List<Uri>, work: File): List<InstallUnit> = withContext(Dispatchers.IO) {
        val bundles = mutableListOf<InstallUnit>()
        val loose = mutableListOf<File>()
        for ((index, uri) in uris.withIndex()) {
            val name = displayName(uri)
            val copy = File(work, "src-$index-${sanitize(name)}")
            app.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it, 256 * 1024) }
            } ?: error("לא ניתן לקרוא את הקובץ $name")
            if (copy.length() == 0L) error("הקובץ $name ריק.")
            log("נקרא $name (${formatSize(copy.length())}).")

            val entries = runCatching { ZipFile(copy).use { z -> z.entries().toList().filter { !it.isDirectory }.map { it.name } } }
                .getOrElse { error("$name אינו APK או חבילת APKS/XAPK/APKM תקינה.") }
            if ("AndroidManifest.xml" in entries) {
                loose += copy
                continue
            }
            val apkEntries = entries.filter { it.endsWith(".apk", ignoreCase = true) }
            if (apkEntries.isEmpty()) error("$name אינו מכיל קובצי APK. ייתכן שהוא מוצפן או פגום.")
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
            log("$name: חבילה עם ${filtered.size} קובצי APK" + (if (obbs.isNotEmpty()) " ו־${obbs.size} קובצי OBB" else "") + (pkg?.let { " ($it)" } ?: "") + ".")
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
                unknown.isNotEmpty() -> error("לא ניתן לזהות את שם החבילה של ${unknown.size} מהקבצים שנבחרו.")
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
        if (dropped.isNotEmpty()) log("דילוג על ${dropped.size} חלקי ABI שאינם מתאימים למעבד של המכשיר.")
        return apks.filter { abiOf(it) == null || it in keep }
    }

    private fun packageNameOf(file: File): String? = runCatching {
        app.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
    }.getOrNull()

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
        val started = runJob("הפקודה") {
            status("מריץ: $cmd")
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
                status("הפקודה הסתיימה (קוד יציאה ${exit ?: 0}).")
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

    fun uninstall(pkg: String, keepData: Boolean) = runJob("הסרת ההתקנה") {
        val name = pkg.trim()
        require(name.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "שם חבילה לא תקין." }
        status("מסיר את $name…")
        withClient { client ->
            val r = client.shell("pm uninstall ${if (keepData) "-k " else ""}$name")
            val out = (r.stdoutText + r.stderrText).trim()
            if (out.contains("Success")) status("✔ $name הוסר.") else error(out.ifBlank { "תשובה ריקה" })
        }
    }

    fun grantAutoEnable() = runJob("הענקת ההרשאה") {
        status("מעניק ל־APK Bridge הרשאה להפעיל ניפוי באגים אלחוטי בעצמו…")
        withClient { client ->
            val r = client.shell("pm grant ${app.packageName} android.permission.WRITE_SECURE_SETTINGS")
            val out = (r.stdoutText + r.stderrText).trim()
            if (r.exitCode != 0) error(out.ifBlank { "exit ${r.exitCode}" })
        }
        status(
            if (canWriteSecureSettings()) "✔ ההרשאה ניתנה. מעכשיו APK Bridge יפעיל את ניפוי הבאגים האלחוטי בעצמו כשהוא כבוי (כל עוד יש Wi‑Fi)."
            else "הפקודה נשלחה; ההרשאה תיכנס לתוקף לאחר הפעלה מחדש של האפליקציה."
        )
    }

    // endregion

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

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

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
            e is AdbException.NotPaired -> "המכשיר דחה את המפתח — יש לבצע חיבור מחדש."
            e is java.net.ConnectException -> "החיבור נדחה (הפורט סגור). ייתכן שהפורט השתנה או שניפוי הבאגים כבוי."
            e is java.net.SocketTimeoutException || e is kotlinx.coroutines.TimeoutCancellationException -> "תם הזמן להמתנה לתשובה."
            msg.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") -> "חתימת ה־APK שונה מזו של האפליקציה המותקנת. יש להסיר את הגרסה הקיימת קודם (לשונית אפליקציות ← הסר)."
            msg.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> "הגרסה ישנה מהמותקנת. סמן \"אפשר שנמוך גרסה\" ונסה שוב."
            msg.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION") -> "האפליקציה מיועדת לגרסת Android ישנה מדי. סמן \"עקוף חסימת targetSdk נמוך\"."
            msg.contains("INSTALL_FAILED_TEST_ONLY") -> "זה APK מסוג test-only. סמן \"אפשר חבילות בדיקה\"."
            msg.contains("INSTALL_FAILED_NO_MATCHING_ABIS") -> "האפליקציה לא תומכת במעבד של המכשיר."
            msg.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> "אין מספיק מקום פנוי במכשיר."
            msg.contains("INSTALL_FAILED_OLDER_SDK") -> "האפליקציה דורשת גרסת Android חדשה יותר."
            msg.contains("INSTALL_FAILED_MISSING_SPLIT") -> "חסרים חלקי APK (split). בחר את החבילה המלאה (APKS/XAPK) או את כל הקבצים יחד."
            msg.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES") -> "ה־APK לא חתום."
            msg.contains("INSTALL_FAILED_USER_RESTRICTED") -> "ההתקנה נחסמה. בשיאומי/MIUI יש להפעיל \"Install via USB\" באפשרויות למפתחים."
            msg.contains("INSTALL_FAILED_ALREADY_EXISTS") -> "החבילה כבר קיימת וה־Package Manager סירב להחליף אותה."
            msg.isNotBlank() -> msg
            else -> e.javaClass.simpleName
        }
    }

    // endregion
}
