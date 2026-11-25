package com.londkirs.apkbridge

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.FileKeyProvider
import io.github.rhythmcache.adb.io.RandomAccessSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var host: EditText
    private lateinit var pairPort: EditText
    private lateinit var pin: EditText
    private lateinit var connectPort: EditText
    private lateinit var status: TextView
    private var selectedApk: Uri? = null
    private var nsd: NsdManager? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        buildUi()
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(0xFFFFF9F3.toInt())
        }
        val title = TextView(this).apply {
            text = "APK Bridge"
            textSize = 28f
            setTextColor(0xFF30251D.toInt())
            gravity = Gravity.CENTER
        }
        page.addView(title, matchWrap())
        page.addView(label("התקנת APK דרך Wireless Debugging"), matchWrap())
        page.addView(label("1. הפעל במכשיר: אפשרויות למפתחים ← ניפוי באגים אלחוטי."), matchWrap())
        host = field("כתובת IP של המכשיר (למשל 192.168.1.20)")
        pairPort = field("Pairing port")
        pin = field("קוד זיווג בן 6 ספרות")
        connectPort = field("Connect port (אפשר להשאיר ריק לגילוי אוטומטי)")
        page.addView(host, matchWrap())
        page.addView(pairPort, matchWrap())
        page.addView(pin, matchWrap())
        page.addView(connectPort, matchWrap())
        page.addView(actionButton("בצע זיווג חד־פעמי", true) { pairDevice() }, matchWrap())
        page.addView(actionButton("אתר חיבור Wireless Debugging", false) { discoverConnectPort() }, matchWrap())
        page.addView(actionButton("בחר קובץ APK", false) { chooseApk() }, matchWrap())
        page.addView(actionButton("התקן APK שנבחר", true) { installSelected() }, matchWrap())
        status = TextView(this).apply {
            text = "מוכן. יש לבצע זיווג פעם אחת, ולאחר מכן אפשר לפתוח APK מכל מנהל קבצים."
            textSize = 14f
            setTextColor(0xFF55483E.toInt())
            setPadding(0, dp(18), 0, 0)
        }
        page.addView(status, matchWrap())
        val scroll = ScrollView(this)
        scroll.addView(page)
        setContentView(scroll)
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(0xFF6D5B4C.toInt())
        setPadding(0, dp(10), 0, dp(5))
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        singleLine = true
        setTextColor(0xFF30251D.toInt())
        setHintTextColor(0xFF9B8D80.toInt())
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(0xFFFFFFFF.toInt())
    }

    private fun actionButton(textValue: String, primary: Boolean, action: () -> Unit) =
        Button(this).apply {
            text = textValue
            textSize = 15f
            isAllCaps = false
            setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF75451F.toInt())
            setBackgroundColor(if (primary) 0xFFF09A45.toInt() else 0xFFFFE7D1.toInt())
            setOnClickListener { action() }
        }

    private fun pairDevice() {
        val ip = host.text.toString().trim()
        val port = pairPort.text.toString().toIntOrNull()
        val code = pin.text.toString().trim()
        if (ip.isBlank() || port == null || code.length != 6 || !code.all(Char::isDigit)) {
            message("צריך כתובת IP, פורט זיווג וקוד בן 6 ספרות.")
            return
        }
        busy("מבצע זיווג מאובטח…")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    AdbClient.pairTls(ip, port, code, keyProvider())
                }
                if (result.isSuccess) {
                    getPreferences(MODE_PRIVATE).edit().putString("paired_host", ip).apply()
                    host.setText(ip)
                    status.text = "הזיווג הצליח. כעת הפעל גילוי אוטומטי או הזן את Connect port שמופיע במסך Wireless Debugging."
                    message("הזיווג הצליח.")
                } else {
                    status.text = "הזיווג נכשל: ${result.exceptionOrNull()?.message ?: "לא התקבל אישור"}"
                }
            } catch (e: Exception) {
                status.text = "שגיאת זיווג: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun discoverConnectPort() {
        val ip = host.text.toString().trim().ifBlank {
            getPreferences(MODE_PRIVATE).getString("paired_host", "").orEmpty()
        }
        if (ip.isBlank()) { message("הזן כתובת IP תחילה."); return }
        host.setText(ip)
        val manager = nsd ?: return
        busy("מחפש שירות Wireless Debugging ברשת המקומית…")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_adb-tls-connect")) {
                    manager.stopServiceDiscovery(this)
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            status.text = "נמצא שירות ADB אך לא ניתן לפתור את הכתובת. אפשר להזין Connect port ידנית."
                        }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            runOnUiThread {
                                val resolvedHost = info.host?.hostAddress ?: ip
                                host.setText(resolvedHost)
                                connectPort.setText(info.port.toString())
                                status.text = "נמצא חיבור Wireless Debugging: $resolvedHost:${info.port}"
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                status.text = "גילוי mDNS נכשל ($errorCode). הזן Connect port ידנית."
                manager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                manager.stopServiceDiscovery(this)
            }
        }
        discovery = listener
        manager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun chooseApk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        startActivityForResult(intent, REQUEST_APK)
    }

    @Deprecated("Legacy result callback retained for API 30 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_APK && resultCode == Activity.RESULT_OK) {
            data?.data?.let { selectedApk = it; status.text = "נבחר קובץ: ${displayName(it)}" }
        }
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val incoming = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
        if (incoming != null) {
            selectedApk = incoming
            status.text = "התקבל קובץ להתקנה: ${displayName(incoming)}"
            if (getPreferences(MODE_PRIVATE).getString("paired_host", null) != null) {
                status.append("\nאפשר ללחוץ על התקן APK שנבחר.")
            }
        }
    }

    private fun installSelected() {
        val uri = selectedApk
        if (uri == null) { message("בחר או פתח קובץ APK תחילה."); return }
        val ip = host.text.toString().trim().ifBlank {
            getPreferences(MODE_PRIVATE).getString("paired_host", "").orEmpty()
        }
        val port = connectPort.text.toString().toIntOrNull()
        if (ip.isBlank() || port == null) {
            message("נדרש IP ו־Connect port. אפשר לנסות גילוי אוטומטי.")
            return
        }
        busy("מכין את קובץ ההתקנה…")
        scope.launch {
            var temp: File? = null
            try {
                temp = withContext(Dispatchers.IO) {
                    val out = File(cacheDir, "incoming-${System.currentTimeMillis()}.apk")
                    contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("לא ניתן לקרוא את קובץ ה־APK")
                    out
                }
                val packageName = packageManager.getPackageArchiveInfo(temp!!.absolutePath, 0)?.packageName
                    ?: error("הקובץ שנבחר אינו APK תקין או שלא ניתן לקרוא את שם החבילה")
                status.text = "מתחבר למכשיר ומתקין…"
                val verification = withContext(Dispatchers.IO) {
                    AdbClient.connectTls(ip, port, keyProvider()).use { client ->
                        val source = RandomAccessSource.of(temp!!)
                        source.use {
                            client.install.install(it, flags = listOf("-r", "-i", "com.android.vending")).collect { progress ->
                                withContext(Dispatchers.Main) {
                                    status.text = "התקנה: ${progress.percentage}% — ${progress.statusText}"
                                }
                            }
                        }
                        client.shell("pm list packages -i ${packageName}").stdoutText.trim()
                    }
                }
                val playListed = verification.contains("installer=com.android.vending")
                status.text = if (playListed) {
                    "ההתקנה הסתיימה בהצלחה. Android מציג את Google Play כמקור ההתקנה הרשום עבור ${packageName}.\n\n${verification}"
                } else {
                    "ההתקנה הסתיימה, אך Android לא אישר ש-Google Play הוא מקור ההתקנה.\n\nתוצאת הבדיקה: ${verification}"
                }
                message(if (playListed) "ההתקנה הסתיימה; מקור ההתקנה הרשום הוא Google Play." else "ההתקנה הסתיימה; זיהוי Google Play לא אומת.")
            } catch (e: Exception) {
                status.text = "ההתקנה נכשלה: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                withContext(Dispatchers.IO) { temp?.delete() }
            }
        }
    }

    private fun keyProvider() = FileKeyProvider(File(filesDir, "adbkey"))

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else uri.lastPathSegment ?: "APK"
        } ?: uri.lastPathSegment ?: "APK"
    }.getOrDefault("APK")

    private fun busy(text: String) { status.text = text }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(9)
    }

    override fun onDestroy() {
        discovery?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        scope.cancel()
        super.onDestroy()
    }

    companion object { private const val REQUEST_APK = 4102 }
}
