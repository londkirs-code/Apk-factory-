package com.londkirs.apkbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

/**
 * Android closes the "Pair device with pairing code" dialog as soon as the user
 * switches apps, which invalidates the code. This foreground service discovers the
 * pairing port in the background and asks for the code through a notification
 * reply field, so the user never has to leave the Settings screen.
 */
class PairingService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Bridge.init(this)
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelfCleanly(); return START_NOT_STICKY }
            ACTION_CODE -> {
                startAsForeground(progress(getString(R.string.pair_working)))
                val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
                val port = intent.getIntExtra(EXTRA_PORT, 0)
                pairWith(port, code)
            }
            else -> {
                startAsForeground(progress(getString(R.string.pair_searching)))
                search()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(n: Notification) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun search() {
        job?.cancel()
        job = Bridge.scope.launch {
            Bridge.log("שירות החיבור פעיל: מחפש חלון חיבור פתוח…")
            val ep = Bridge.discover(Bridge.PAIRING_SERVICE, Bridge.LOCAL_HOST, SEARCH_TIMEOUT_MS)
            if (ep == null) {
                Bridge.log("לא נמצא חלון חיבור פתוח בזמן שהוקצב.")
                notifyFinal(getString(R.string.pair_not_found), success = false)
                stopSelfCleanly()
            } else {
                Bridge.log("נמצא שירות חיבור בפורט ${ep.port}. הזן את הקוד בהתראה.")
                notify(askForCode(ep.port, getString(R.string.pair_found, ep.port)))
            }
        }
    }

    private fun pairWith(port: Int, code: String) {
        job?.cancel()
        job = Bridge.scope.launch {
            val ok = try {
                Bridge.pairNow(Bridge.LOCAL_HOST, port.takeIf { it > 0 }, code)
            } catch (e: TimeoutCancellationException) {
                Bridge.log("החיבור נכשל: ${Bridge.explain(e)}"); false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Bridge.log("החיבור נכשל: ${Bridge.explain(e)}"); false
            }
            if (ok) {
                notifyFinal(getString(R.string.pair_success), success = true)
                stopSelfCleanly()
                // Verify right away that ADB commands work, so problems show up in the log immediately.
                Bridge.testConnection()
            } else {
                // Let the user retry: the dialog may still be open with a new code.
                notify(askForCode(port, getString(R.string.pair_failed_retry)))
            }
        }
    }

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun base(text: String) = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_bridge)
        .setContentTitle(getString(R.string.pair_title))
        .setContentText(text)
        .setStyle(Notification.BigTextStyle().bigText(text))
        .setContentIntent(openApp(this))
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .addAction(
            Notification.Action.Builder(null as Icon?, getString(R.string.pair_cancel), servicePending(ACTION_STOP, 2)).build()
        )

    private fun progress(text: String) = base(text).setProgress(0, 0, true).build()

    private fun askForCode(port: Int, text: String): Notification {
        val input = RemoteInput.Builder(KEY_CODE).setLabel(getString(R.string.pair_code_hint)).build()
        val reply = Intent(this, PairingReplyReceiver::class.java).putExtra(EXTRA_PORT, port)
        val pending = PendingIntent.getBroadcast(
            this, 1, reply, PendingIntent.FLAG_UPDATE_CURRENT or Compat.pendingIntentMutable,
        )
        val action = Notification.Action.Builder(null as Icon?, getString(R.string.pair_enter_code), pending)
            .addRemoteInput(input)
            .build()
        return base(text).setOnlyAlertOnce(false).addAction(action).build()
    }

    private fun servicePending(action: String, request: Int) = PendingIntent.getService(
        this, request, Intent(this, PairingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(n: Notification) =
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)

    private fun notifyFinal(text: String, success: Boolean) {
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(getString(if (success) R.string.pair_done_title else R.string.pair_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(this))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(RESULT_NOTIFICATION_ID, n)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "pairing"
        const val ACTION_START = "com.londkirs.apkbridge.PAIR_START"
        const val ACTION_STOP = "com.londkirs.apkbridge.PAIR_STOP"
        const val ACTION_CODE = "com.londkirs.apkbridge.PAIR_CODE"
        const val EXTRA_CODE = "code"
        const val EXTRA_PORT = "port"
        const val KEY_CODE = "pairing_code"
        private const val NOTIFICATION_ID = 41
        private const val RESULT_NOTIFICATION_ID = 42
        private const val SEARCH_TIMEOUT_MS = 5 * 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PairingService::class.java).setAction(ACTION_START))
        }

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.channel_pairing), NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }

        fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Receives the pairing code typed into the notification and hands it to [PairingService]. */
class PairingReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PairingService.KEY_CODE)?.toString().orEmpty()
        val port = intent.getIntExtra(PairingService.EXTRA_PORT, 0)
        context.startForegroundService(
            Intent(context, PairingService::class.java)
                .setAction(PairingService.ACTION_CODE)
                .putExtra(PairingService.EXTRA_CODE, code)
                .putExtra(PairingService.EXTRA_PORT, port)
        )
    }
}
