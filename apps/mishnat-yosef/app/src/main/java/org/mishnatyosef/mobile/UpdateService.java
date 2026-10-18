package org.mishnatyosef.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.net.*;
import java.util.regex.*;

public class UpdateService extends Service {
    private static final String PREFS = "mishnat_auth";
    private static final String CHANNEL = "app_update_monitor_v2";
    private static final String META = "https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/apps/mishnat-yosef/app/build.gradle";
    private static final String APK = "https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/mishnat-yosef/app.apk";
    private static final String INSTALL_ACTION = "org.mishnatyosef.mobile.INSTALL_DOWNLOADED_UPDATE";
    private static final Uri APK_URI = Uri.parse("content://org.mishnatyosef.mobile.updatefiles/apk");
    private static final int NOTIFICATION_ID = 7332;
    private static final int UPDATE_NOTIFICATION_ID = 7333;
    private Handler handler;
    private volatile boolean checking = false;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
                stopSelf();
                return;
            }
            // Re-post the foreground notification every cycle so it is restored if Android/OEM software drops it.
            startForeground(NOTIFICATION_ID, buildNotification("בדיקת עדכונים פעילה ברקע", true));
            checkUpdate(false);
            long sec = Math.max(5, getSharedPreferences(PREFS, MODE_PRIVATE).getInt("app_update_interval_seconds", 300));
            handler.postDelayed(this, sec * 1000L);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, UpdateService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) {}
    }

    public static void stop(Context c) { c.stopService(new Intent(c, UpdateService.class)); }

    public static void checkNow(Context c) {
        Intent i = new Intent(c, UpdateService.class).setAction("org.mishnatyosef.mobile.UPDATE_CHECK_NOW");
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) {}
    }

    public static void refreshNotification(Context c) { start(c); }

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean manual = intent != null && "org.mishnatyosef.mobile.UPDATE_CHECK_NOW".equals(intent.getAction());
        if (!manual && !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification("בדיקת עדכונים פעילה ברקע", true));
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
            UpdateBootReceiver.scheduleWatchdog(this);
        }
        handler.removeCallbacks(loop);
        if (manual) {
            checkUpdate(true);
            if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
                handler.postDelayed(loop, intervalMillis());
            }
        } else {
            checkUpdate(false);
            handler.postDelayed(loop, intervalMillis());
        }
        return START_STICKY;
    }

    private long intervalMillis() {
        return Math.max(5, getSharedPreferences(PREFS, MODE_PRIVATE).getInt("app_update_interval_seconds", 300)) * 1000L;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                // A new channel is used because Android does not allow the app to raise the importance of an existing channel.
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "עדכוני משנת יוסף — מצב ובדיקות", NotificationManager.IMPORTANCE_DEFAULT));
            }
        }
    }

    // The background-service notification intentionally has no click action: it must not open the app.
    private Notification buildNotification(String message, boolean ongoing) {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_notification", true) && ongoing) {
            message = "בדיקת עדכונים פעילה ברקע";
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("משנת יוסף · עדכונים")
                .setContentText(message)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String message, boolean ongoing) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(message, ongoing));
    }

    private File updateFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return dir == null ? new File(getFilesDir(), "mishnat-yosef-update.apk") : new File(dir, "mishnat-yosef-update.apk");
    }

    private void saveStatus(String status, String latest, boolean markChecked) {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("app_update_status", status);
        if (latest != null) e.putString("app_update_latest_version", latest);
        if (markChecked) e.putLong("app_update_last_check_time", System.currentTimeMillis());
        e.apply();
    }

    private void checkUpdate(boolean tellNoUpdate) {
        if (checking) return;
        checking = true;
        new Thread(() -> {
            try {
                String gradle = readText(META + "?t=" + System.currentTimeMillis());
                Matcher m = Pattern.compile("versionName\\s+'([^']+)'").matcher(gradle);
                if (!m.find()) throw new IOException("לא נמצא מספר גרסה בקובץ המקור");
                String latest = m.group(1);
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                String installed = pi.versionName == null ? "0" : pi.versionName;

                if (compareVersions(latest, installed) <= 0) {
                    String status = "האפליקציה מעודכנת (גרסה " + installed + ")";
                    saveStatus(status, latest, true);
                    if (tellNoUpdate) postResult(status, false);
                    return;
                }

                File apk = updateFile();
                String downloadedVersion = getSharedPreferences(PREFS, MODE_PRIVATE).getString("app_update_downloaded_version", "");
                if (latest.equals(downloadedVersion) && apk.isFile() && apk.length() > 100000) {
                    String status = "עדכון " + latest + " כבר הורד — מנסה לפתוח התקנה";
                    saveStatus(status, latest, true);
                    postResult(status, true);
                    return;
                }

                File parent = apk.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("לא ניתן ליצור תיקיית הורדה");
                saveStatus("נמצא עדכון " + latest + " — מוריד כעת…", latest, false);
                download(APK + "?t=" + System.currentTimeMillis(), apk);
                if (!apk.isFile() || apk.length() < 100000) throw new IOException("קובץ APK קטן מדי או פגום");

                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString("app_update_downloaded_path", apk.getAbsolutePath())
                        .putString("app_update_downloaded_version", latest)
                        .apply();
                String status;
                if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                    status = "עדכון " + latest + " הורד; נדרש לאשר התקנה ממקור זה";
                } else {
                    status = "עדכון " + latest + " הורד — מפעיל התקנה";
                }
                saveStatus(status, latest, true);
                postResult(status, true);
            } catch (Exception e) {
                String message = "בדיקת העדכון נכשלה: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                saveStatus(message, null, true);
                if (tellNoUpdate) postResult(message, false);
            } finally {
                checking = false;
            }
        }, "mishnat-update-check").start();
    }

    private String readText(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "MishnatYosef-Updater");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private void download(String u, File f) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "MishnatYosef-Updater");
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
        } finally { c.disconnect(); }
    }

    private int compareVersions(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i]) : 0;
            long q = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i]) : 0;
            if (p != q) return p > q ? 1 : -1;
        }
        return 0;
    }

    private Intent installerIntent() {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(APK_URI, "application/vnd.android.package-archive");
        i.setClipData(ClipData.newUri(getContentResolver(), "עדכון משנת יוסף", APK_URI));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private PendingIntent installerPendingIntent() {
        Intent target = installerIntent();
        return PendingIntent.getActivity(this, UPDATE_NOTIFICATION_ID, target,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean launchInstallerNow() {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) return false;
        try {
            startActivity(installerIntent());
            return true;
        } catch (Exception ignored) {
            return false; // Android may block background activity launches; the notification remains the fallback.
        }
    }

    private void postResult(String message, boolean hasUpdate) {
        handler.post(() -> {
            boolean automatic = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false);
            updateNotification(message, automatic);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (hasUpdate) {
                // Tapping this notification launches the Android installer (or the required Android permission screen), never MainActivity.
                Notification.Builder b = Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
                Notification n = b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("משנת יוסף — עדכון מוכן")
                        .setContentText(message)
                        .setContentIntent(installerPendingIntent())
                        .setAutoCancel(false)
                        .setOnlyAlertOnce(true)
                        .build();
                if (nm != null) nm.notify(UPDATE_NOTIFICATION_ID, n);
                // Try immediately. If Android blocks background launches, the notification's click action opens the installer.
                launchInstallerNow();
            } else if (!automatic) {
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(message, false));
                stopSelf();
            }
        });
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Keep a recovery alarm even if the task/service is removed by the system or an OEM task manager.
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
            UpdateBootReceiver.scheduleWatchdog(this);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        if (handler != null) handler.removeCallbacks(loop);
        // START_STICKY is the first recovery path; the watchdog alarm is the second.
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
            UpdateBootReceiver.scheduleWatchdog(this);
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}