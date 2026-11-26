package com.londkirs.apkbridge

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Reads the Wireless Debugging screens in Settings (and only Settings — see
 * res/xml/pairing_accessibility.xml) to pair automatically:
 *  - "Pair device with pairing code" dialog: 6-digit code + IP:port → pairs right away.
 *  - Wireless Debugging main screen: IP:port → remembers the connection port.
 * Nothing is stored or sent anywhere except to this device's own adbd.
 */
class PairingAccessibilityService : AccessibilityService() {
    private var lastPairKey: String? = null
    private var lastConnectPort = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Bridge.init(this)
        Bridge.log("שירות הנגישות פעיל: קוד חיבור שיופיע בהגדרות יזוהה ויוזן אוטומטית.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != SETTINGS) return
        if (!Bridge.accessibilityAutoPair) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != SETTINGS) return
        val texts = ArrayList<String>()
        collect(root, texts, 0)
        if (texts.isEmpty()) return

        val code = texts.asSequence()
            .map { it.replace(" ", "").replace(" ", "") }
            .firstOrNull { CODE.matches(it) }
        val port = texts.asSequence().mapNotNull { IP_PORT.find(it) }.firstOrNull()
            ?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return

        if (code != null) {
            val key = "$port:$code"
            if (key == lastPairKey || Bridge.state.busy) return
            // Only remember the code once a pairing job actually started (another job may be running).
            if (Bridge.autoPair(port, code)) {
                lastPairKey = key
                Toast.makeText(this, "APK Bridge זיהה את קוד החיבור — מתחבר…", Toast.LENGTH_SHORT).show()
                Bridge.log("זוהה חלון חיבור במסך: פורט $port.")
            }
        } else if (port != lastConnectPort) {
            lastConnectPort = port
            if (Bridge.savedConnectPort != port) {
                Bridge.savedConnectPort = port
                Bridge.log("זוהה פורט החיבור של ניפוי הבאגים האלחוטי מהמסך: $port.")
            }
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > 300) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
    }

    override fun onInterrupt() {}

    companion object {
        private const val SETTINGS = "com.android.settings"
        private val CODE = Regex("^\\d{6}$")
        private val IP_PORT = Regex("(\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9a-fA-F:]+]):(\\d{2,5})")

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, PairingAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
