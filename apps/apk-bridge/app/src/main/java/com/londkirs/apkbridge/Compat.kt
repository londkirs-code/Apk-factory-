package com.londkirs.apkbridge

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowInsets

/** Small wrappers so the app runs on Android 8 (API 26) and newer. */
object Compat {
    /** Wireless Debugging with pairing codes exists only on Android 11+. */
    val hasWirelessDebugging: Boolean get() = Build.VERSION.SDK_INT >= 30

    fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    /** PendingIntent.FLAG_MUTABLE exists from Android 12; older versions are mutable by default. */
    val pendingIntentMutable: Int get() = if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0

    fun wifiPanelIntent(): Intent =
        Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)

    /**
     * Keeps content clear of the status/navigation bars (and the keyboard). targetSdk 35 forces
     * edge-to-edge on Android 15+, so on Android 11+ insets are applied by hand; older versions
     * are not edge-to-edge and simply fit the system windows.
     */
    fun applySystemBarInsets(activity: Activity, root: View, includeIme: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { v, insets ->
                var types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                if (includeIme) types = types or WindowInsets.Type.ime()
                val bars = insets.getInsets(types)
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        } else {
            root.fitsSystemWindows = true
        }
    }
}
