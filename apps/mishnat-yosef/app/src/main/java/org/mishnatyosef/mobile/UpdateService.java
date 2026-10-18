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
    private static final String ACTION_REFRESH = "org.mishnatyosef.mobile.UPDATE_REFRESH";
    private static final String ACTION_INSTALL_PENDING = "org.mishnatyosef.mobile.UPDATE_INSTALL_PENDING";
    private static final Uri APK_URI = Uri.parse("content://org.mishnatyosef.mobile.updatefiles/apk");
    private static final int NOTIFICATION_ID = 7332;
    private static final int LEGACY_UPDATE_NOTIFICATION_ID = 7333;
    private Handler handler;
    private volatile boolean checking = false;
    private volatile boolean manualCheckPending = false;
    private volatile boolean updateReady = false;
    private volatile String currentNotificationMessage = "בדיקת עדכונים פעילה ברקע";

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
                stopSelf();
                return;
            }
            // Re-post the foreground notification every cycle so it is restored if Android/OEM software drops it.
            startForeground(NOTIFICATION_ID, buildNotification(currentNotificationMessage, true));
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

    public static void refreshNotification(Context c) {
        Intent i = new Intent(c, UpdateService.class).setAction(ACTION_REFRESH);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) {}
    }

    public static void tryInstallPending(Context c) {
        Intent i = new Intent(c, UpdateService.class).setAction(ACTION_INSTALL_PENDING);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) {}
    }

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(LEGACY_UPDATE_NOTIFICATION_ID);
        SharedPreferences saved = getSharedPreferences(PREFS, MODE_PRIVATE);
        File savedApk = updateFile();
        String downloaded = saved.getString("app_update_downloaded_version", "");
        String latest = saved.getString("app_update_latest_version", "");
        updateReady = !downloaded.isEmpty() && downloaded.equals(latest) && savedApk.isFile() && savedApk.length() > 100000;
        if (updateReady) currentNotificationMessage = "עדכון " + downloaded + " הורד — לחץ על ההתראה כדי להתקין";
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        boolean manual = "org.mishnatyosef.mobile.UPDATE_CHECK_NOW".equals(action);
        boolean refresh = ACTION_REFRESH.equals(action);
        boolean installPending = ACTION_INSTALL_PENDING.equals(action);
        boolean automatic = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false);
        if (!manual && !refresh && !installPending && !automatic) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification(currentNotificationMessage, true));
        if (installPending) {
            File apk = updateFile();
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            String downloaded = p.getString("app_update_downloaded_version", "");
            String latest = p.getString("app_update_latest_version", "");
            updateReady = !downloaded.isEmpty() && downloaded.equals(latest) && apk.isFile() && apk.length() > 100000;
            long lastInstallAttempt = p.getLong("app_update_last_install_attempt", 0L);
            boolean attemptedRecently = System.currentTimeMillis() - lastInstallAttempt < 120000L;
            if (updateReady && !attemptedRecently) {
                updateNotification("עדכון " + downloaded + " מוכן — פותח את ההתקנה…", true);
                if (!launchInstallerNow()) {
                    updateNotification("עדכון " + downloaded + " מוכן — לחץ כאן להתקנה", true);
                }
            } else if (updateReady) {
                updateNotification("עדכון " + downloaded + " מוכן — לחץ כאן להתקנה", true);
            } else {
                updateNotification("אין כרגע עדכון שהורד ומוכן להתקנה", automatic);
            }
            if (!automatic) handler.postDelayed(() -> { if (!automatic) stopSelf(); }, 1500);
            return START_STICKY;
        }
        if (refresh) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(LEGACY_UPDATE_NOTIFICATION_ID);
            updateNotification(currentNotificationMessage, automatic || updateReady);
            if (automatic) {
                UpdateBootReceiver.scheduleWatchdog(this);
                handler.removeCallbacks(loop);
                checkUpdate(false);
                handler.postDelayed(loop, intervalMillis());
            } else if (updateReady) {
                launchInstallerNow();
            } else {
                stopSelf();
            }
            return START_STICKY;
        }
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) {
            UpdateBootReceiver.scheduleWatchdog(this);
        }
        handler.removeCallbacks(loop);
        if (manual) {
            manualCheckPending = true;
            updateNotification("בודק כעת אם קיימת גרסה חדשה…", true);
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
        Notification.Builder builder = b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(updateReady ? "משנת יוסף — עדכון מוכן" : "משנת יוסף · עדכונים")
                .setContentText(message)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true);
        if (updateReady) builder.setContentIntent(installerPendingIntent());
        return builder.build();
    }

    private void updateNotification(String message, boolean ongoing) {
        currentNotificationMessage = message;
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
        if (checking) {
            if (tellNoUpdate) {
                manualCheckPending = true;
                saveStatus("בדיקה כבר מתבצעת — ממתין לתוצאה", null, false);
                updateNotification("בדיקת עדכונים מתבצעת…", getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false));
            }
            return;
        }
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
                    updateReady = false;
                    saveStatus(status, latest, true);
                    if (tellNoUpdate || manualCheckPending || getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) postResult(status, false);
                    return;
                }

                File apk = updateFile();
                String downloadedVersion = getSharedPreferences(PREFS, MODE_PRIVATE).getString("app_update_downloaded_version", "");
                if (latest.equals(downloadedVersion) && apk.isFile() && apk.length() > 100000) {
                    String status = "עדכון " + latest + " כבר הורד — לחץ על ההתראה כדי להתקין";
                    updateReady = true;
                    saveStatus(status, latest, true);
                    postResult(status, true);
                    return;
                }

                File parent = apk.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("לא ניתן ליצור תיקיית הורדה");
                saveStatus("נמצא עדכון " + latest + " — מוריד כעת…", latest, false);
                download(APK + "?t=" + System.currentTimeMillis(), apk);
                if (!apk.isFile() || apk.length() < 100000) throw new IOException("קובץ APK קטן מדי או פגום");

                updateReady = true;
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
                if (tellNoUpdate || manualCheckPending || getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false)) postResult(message, false);
            } finally {
                checking = false;
                manualCheckPending = false;
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
            return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(APK_URI, "application/vnd.android.package-archive");
        i.setClipData(ClipData.newUri(getContentResolver(), "עדכון משנת יוסף", APK_URI));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private PendingIntent installerPendingIntent() {
        Intent target = installerIntent();
        return PendingIntent.getActivity(this, NOTIFICATION_ID, target,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean launchInstallerNow() {
        try {
            Intent target = installerIntent();
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(target);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("app_update_last_install_attempt", System.currentTimeMillis()).apply();
            return true;
        } catch (Exception ignored) {
            return false; // Android may block background activity launches; the notification remains the fallback.
        }
    }

    private void postResult(String message, boolean hasUpdate) {
        handler.post(() -> {
            boolean automatic = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("app_update_auto", false);
            updateReady = hasUpdate;
            updateNotification(message, automatic || hasUpdate);
            if (hasUpdate) {
                // Reuse the foreground notification ID so the user sees only one notification.
                // Android can block background installer launches; tapping this notification opens it.
                launchInstallerNow();
            } else if (!automatic) {
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