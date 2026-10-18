package org.mishnatyosef.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public class UpdateBootReceiver extends BroadcastReceiver {
    private static final String PREFS = "mishnat_auth";
    private static final String ACTION_WATCHDOG = "org.mishnatyosef.mobile.UPDATE_SERVICE_WATCHDOG";
    private static final int WATCHDOG_REQUEST = 7334;
    private static final long WATCHDOG_INTERVAL_MS = 15L * 60L * 1000L;

    public static void scheduleWatchdog(Context context) {
        Intent intent = new Intent(context, UpdateBootReceiver.class).setAction(ACTION_WATCHDOG);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, WATCHDOG_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS;
        if (Build.VERSION.SDK_INT >= 23) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
        } else {
            alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean automatic = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("app_update_auto", false);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (automatic) {
                scheduleWatchdog(context);
                UpdateService.start(context);
            }
            return;
        }
        if (ACTION_WATCHDOG.equals(action) && automatic) {
            // Schedule the next recovery before starting the service, in case starting it fails.
            scheduleWatchdog(context);
            UpdateService.start(context);
        }
    }
}
