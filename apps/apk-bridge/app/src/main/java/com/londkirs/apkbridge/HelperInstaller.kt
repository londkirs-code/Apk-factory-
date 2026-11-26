package com.londkirs.apkbridge

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Installs the bundled Wi‑Fi helper through Android's regular package installer
 * (PackageInstaller session + the system confirmation screen). No ADB and no
 * Wireless Debugging are needed; the user only allows "install unknown apps"
 * once and taps "Install".
 */
object HelperInstaller {
    private const val ASSET = "wifi-helper.apk"

    /** Set while the user is in the "install unknown apps" screen, to resume afterwards. */
    @Volatile var waitingForPermission = false
        private set

    fun start(activity: Activity) {
        if (Bridge.isWifiHelperInstalled()) {
            Toast.makeText(activity, "עזר ה־Wi‑Fi כבר מותקן.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!activity.packageManager.canRequestPackageInstalls()) {
            waitingForPermission = true
            Toast.makeText(activity, "אפשר לאפליקציית הבוס להתקין אפליקציות, וחזור — ההתקנה תמשיך לבד.", Toast.LENGTH_LONG).show()
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                )
            }
            return
        }
        waitingForPermission = false
        val context = activity.applicationContext
        Bridge.log("מתקין את עזר ה־Wi‑Fi דרך מתקין החבילות של Android…")
        Bridge.scope.launch(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(Bridge.WIFI_HELPER_PKG)
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    context.assets.open(ASSET).use { input ->
                        session.openWrite("base.apk", 0, -1).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                    val pending = PendingIntent.getBroadcast(
                        context, 7, Intent(context, HelperInstallReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or Compat.pendingIntentMutable,
                    )
                    session.commit(pending.intentSender)
                }
            }.onFailure { Bridge.log("התקנת עזר ה־Wi‑Fi נכשלה: ${Bridge.explain(it)}") }
        }
    }

    /** Call from onResume: continues the install after the user granted "install unknown apps". */
    fun resumeIfPermitted(activity: Activity) {
        if (waitingForPermission && activity.packageManager.canRequestPackageInstalls()) start(activity)
    }
}

/** Receives the PackageInstaller result: shows the confirmation screen, then reports the outcome. */
class HelperInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Bridge.init(context)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Bridge.log("לא ניתן להציג את מסך אישור ההתקנה: ${Bridge.explain(it)}") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Bridge.log("✔ עזר ה־Wi‑Fi הותקן. מעכשיו Wi‑Fi כבוי יודלק אוטומטית כשצריך.")
                Bridge.refreshState()
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> Bridge.log("התקנת עזר ה־Wi‑Fi בוטלה.")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Bridge.log("התקנת עזר ה־Wi‑Fi נכשלה. $msg")
            }
        }
    }
}
