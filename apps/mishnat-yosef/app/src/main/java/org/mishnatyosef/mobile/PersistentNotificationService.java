package org.mishnatyosef.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.List;

public class PersistentNotificationService extends Service {
    private static final String CHANNEL_ID = "persistent_notifications";
    private static final int NOTIFICATION_ID = 74102;
    private static final String PREFS = "mishnat_auth";

    @Override public void onCreate() { super.onCreate(); ensureChannel(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("persistent_notifications_enabled", false)) { stopSelf(startId); return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId); return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification(prefs));
        return START_STICKY;
    }

    private Notification buildNotification(SharedPreferences p) {
        List<String> enabled = new ArrayList<>();
        if (p.getBoolean("sale_monitor_enabled", true)) enabled.add("התראות על פתיחת מכירה");
        if (p.getBoolean("closing_reminder_enabled", false)) enabled.add("תזכורת לפני סגירת ההזמנה");
        boolean out = p.getBoolean("stock_global_notify_out", false) || p.getBoolean("stock_notify_out", true);
        boolean back = p.getBoolean("stock_global_notify_return", false) || p.getBoolean("stock_notify_return", true);
        if (out && back) enabled.add("עדכונים על מוצרים שאזלו וחזרו למלאי");
        else if (out) enabled.add("עדכונים על מוצרים שאזלו מהמלאי");
        else if (back) enabled.add("עדכונים על מוצרים שחזרו למלאי");
        String content = enabled.isEmpty() ? "אין כרגע סוגי התראות פעילים" : "פעילה לצורך קבלת " + join(enabled) + " וכו׳";
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 74103, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle("משנת יוסף").setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE).build();
    }

    private String join(List<String> items) {
        StringBuilder out = new StringBuilder();
        for (String item : items) { if (out.length() > 0) out.append(", "); out.append(item); }
        return out.toString();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "התראה קבועה של משנת יוסף", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("מציגה אילו התראות ועדכוני מלאי פעילים");
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
