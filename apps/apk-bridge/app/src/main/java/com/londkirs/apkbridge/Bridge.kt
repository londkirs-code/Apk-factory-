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
        if (!::app.isInitialized) app = context.applicationContext
    }

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

    fun loadOptions() = InstallOptions(
        allowDowngrade = prefs.getBoolean("opt_downgrade", false),
        grantPermissions = prefs.getBoolean("opt_grant", false),
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
        if (isWirelessDebuggingOn() == true || !canWriteSecureSettings()) return
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
        require(cleanCode.length == 6) { "קוד הזיווג חייב להכיל 6 ספרות." }
        val target = host.trim().ifBlank { LOCAL_HOST }
        val pairPort = port ?: run {
            log("מחפש את פורט הזיווג ברשת (mDNS)…")
            discover(PAIRING_SERVICE, target, 8_000)?.port
        } ?: error("לא נמצא שירות זיווג פעיל. ודא שחלון \"התאמת מכשיר עם קוד התאמה\" פתוח, או הזן את הפורט ידנית.")
        log("מזווג מול $target:$pairPort…")
        val result = withContext(Dispatchers.IO) {
            withTimeout(30_000) { AdbClient.pairTls(target, pairPort, cleanCode, keyProvider()) }
        }
        val error = result.exceptionOrNull()
        if (error != null) {
            log("הזיווג נכשל: ${explain(error)}")
            return false
        }
        isPaired = true
        targetHost = target
        log("הזיווג הצליח. המפתח נשמר באחסון הפרטי של האפליקציה.")
        return true
    }

    fun pair(host: String, port: Int?, code: String) = runJob("הזיווג") {
        status("מבצע זיווג מאובטח…")
        if (pairNow(host, port, code)) {
            status("הזיווג הצליח. מנסה להתחבר…")
            withClient { describeDevice(it) }
        } else {
            status("הזיווג נכשל. בדוק את הקוד ושהחלון עדיין פתוח, ונסה שוב.")
        }
    }

    // endregion

    // region connection

    private suspend fun connectTo(ep: Endpoint): AdbClient = withContext(Dispatchers.IO) {
        withTimeout(25_000) { AdbClient.connectTls(ep.host, ep.port, keyProvider(), handshakeTimeoutMs = 20_000) }
    }

    /** Connects (re-discovering the port if needed), runs [block], then closes. */
    suspend fun <T> withClient(block: suspend (AdbClient) -> T): T {
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
                throw IllegalStateException("המכשיר דחה את המפתח של APK Bridge. יש לבצע זיווג מחדש.", e)
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
        if (client == null) {
            val hint = when {
                isWirelessDebuggingOn() == false -> "ניפוי באגים אלחוטי כבוי. הפעל אותו באפשרויות למפתחים."
                !isOnWifi() -> "המכשיר לא מחובר ל־Wi‑Fi; ניפוי באגים אלחוטי דורש Wi‑Fi."
                else -> "ודא שניפוי באגים אלחוטי פעיל. ${lastError?.let { explain(it) } ?: ""}"
            }
            error("לא ניתן להתחבר ל־ADB. $hint")
        }
        val connected: AdbClient = client
        return try {
            log("מחובר.")
            block(connected)
        } finally {
            runCatching { connected.close() }
        }
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
        status(
            "${prefix}✔ $pkg הותקן בהצלחה" + (version?.let { " (versionCode $it)" } ?: "") + ".\n$installerText",
            100,
        )
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
            e is AdbException.NotPaired -> "המכשיר דחה את המפתח — יש לבצע זיווג מחדש."
            e is java.net.ConnectException -> "החיבור נדחה (הפורט סגור). ייתכן שהפורט השתנה או שניפוי הבאגים כבוי."
            e is java.net.SocketTimeoutException || e is kotlinx.coroutines.TimeoutCancellationException -> "תם הזמן להמתנה לתשובה."
            msg.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") -> "חתימת ה־APK שונה מזו של האפליקציה המותקנת. יש להסיר את הגרסה הקיימת קודם (כלים ← הסרה)."
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
