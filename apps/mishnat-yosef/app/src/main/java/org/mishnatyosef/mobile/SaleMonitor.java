package org.mishnatyosef.mobile;

import android.app.AlarmManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class SaleMonitor extends BroadcastReceiver {
    private static final String PREFS = "mishnat_auth";
    private static final String API = "https://v2.mishnatyosef.org";
    private static final String START = "org.mishnatyosef.mobile.START_SALE_MONITOR";
    private static final String CHECK = "org.mishnatyosef.mobile.CHECK_OPEN_SALES";
    private static final String CHANNEL = "new_open_sales";
    private static final int ALARM_ID = 8217;
    private static final long FIVE_MINUTES = 5L * 60L * 1000L;

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (p.getBoolean("sale_monitor_active", false)) schedule(context, CHECK, System.currentTimeMillis() + 60000L);
            else scheduleNextStart(context);
            return;
        }
        if (START.equals(action)) {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!p.getBoolean("sale_monitor_enabled", true) || !isWithinMonitoringWindow(context, Calendar.getInstance())) { scheduleNextStart(context); return; }
            p.edit().putBoolean("sale_monitor_active", true).remove("sale_monitor_ids").putBoolean("sale_monitor_baseline_done", false).apply();
            runCheckAsync(context, true);
        } else if (CHECK.equals(action)) {
            if (!isWithinMonitoringWindow(context, Calendar.getInstance())) { stopMonitoring(context); scheduleNextStart(context); return; }
            runCheckAsync(context, false);
        }
    }

    private void runCheckAsync(Context context, boolean firstCheck) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try { check(context.getApplicationContext(), firstCheck); }
            finally { pending.finish(); }
        }, "mishnat-sale-monitor").start();
    }

    public static void scheduleNextStart(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("sale_monitor_enabled", true)) { stopMonitoring(context); return; }
        if (p.getBoolean("sale_monitor_active", false)) {
            if (isWithinMonitoringWindow(context, Calendar.getInstance())) schedule(context, CHECK, System.currentTimeMillis() + FIVE_MINUTES);
            else { p.edit().putBoolean("sale_monitor_active", false).remove("sale_monitor_ids").putBoolean("sale_monitor_baseline_done", false).apply(); scheduleNextStart(context); }
            return;
        }
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        int startDay = p.getInt("monitor_start_day", Calendar.THURSDAY);
        int[] startTime = parseTime(p.getString("monitor_start_time", "19:00"), 19, 0);
        int days = (startDay - now.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        next.add(Calendar.DAY_OF_YEAR, days);
        next.set(Calendar.HOUR_OF_DAY, startTime[0]);
        next.set(Calendar.MINUTE, startTime[1]);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 7);
        if (isWithinMonitoringWindow(context, now)) schedule(context, START, System.currentTimeMillis() + 1000L);
        else schedule(context, START, next.getTimeInMillis());
    }

    public static void stopMonitoring(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putBoolean("sale_monitor_active", false).remove("sale_monitor_ids").putBoolean("sale_monitor_baseline_done", false).apply();
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) for (String action : new String[]{START, CHECK}) {
            Intent i = new Intent(context, SaleMonitor.class); i.setAction(action);
            PendingIntent pi = PendingIntent.getBroadcast(context, ALARM_ID, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }
    }

    private static int[] parseTime(String value, int defaultHour, int defaultMinute) {
        try { String[] parts = value.split(":"); return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])}; }
        catch (Exception e) { return new int[]{defaultHour, defaultMinute}; }
    }

    private static boolean isWithinMonitoringWindow(Context context, Calendar now) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int startDay = p.getInt("monitor_start_day", Calendar.THURSDAY);
        int endDay = p.getInt("monitor_end_day", Calendar.SUNDAY);
        int[] st = parseTime(p.getString("monitor_start_time", "19:00"), 19, 0);
        int[] et = parseTime(p.getString("monitor_end_time", "10:00"), 10, 0);
        Calendar start = (Calendar) now.clone();
        int back = (now.get(Calendar.DAY_OF_WEEK) - startDay + 7) % 7;
        start.add(Calendar.DAY_OF_YEAR, -back);
        start.set(Calendar.HOUR_OF_DAY, st[0]); start.set(Calendar.MINUTE, st[1]); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);
        int forward = (endDay - startDay + 7) % 7;
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, forward);
        end.set(Calendar.HOUR_OF_DAY, et[0]); end.set(Calendar.MINUTE, et[1]); end.set(Calendar.SECOND, 0); end.set(Calendar.MILLISECOND, 0);
        if (!end.after(start)) end.add(Calendar.DAY_OF_YEAR, 7);
        return !now.before(start) && now.before(end);
    }

    private static void schedule(Context context, String action, long when) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, SaleMonitor.class);
        i.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, ALARM_ID, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
    }

    private void check(Context context, boolean firstCheck) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = p.getString("token", "");
        if (token.isEmpty()) {
            p.edit().putBoolean("sale_monitor_active", false).remove("sale_monitor_ids").putBoolean("sale_monitor_baseline_done", false).apply();
            scheduleNextStart(context);
            return;
        }
        try {
            JSONArray sales = fetchSales(context, token);
            if (sales == null) {
                schedule(context, CHECK, System.currentTimeMillis() + FIVE_MINUTES);
                return;
            }
            Set<String> current = new HashSet<>();
            JSONObject newSale = null;
            Set<String> previous = parseIds(p.getString("sale_monitor_ids", ""));
            boolean baselineDone = p.getBoolean("sale_monitor_baseline_done", false);
            for (int i = 0; i < sales.length(); i++) {
                JSONObject sale = sales.optJSONObject(i);
                if (sale == null) continue;
                String id = sale.optString("id", "");
                if (id.isEmpty() || "null".equals(id)) continue;
                current.add(id);
                if (!firstCheck && baselineDone && !previous.contains(id) && newSale == null) newSale = sale;
            }
            if (firstCheck || !baselineDone) {
                p.edit().putString("sale_monitor_ids", joinIds(current)).putBoolean("sale_monitor_baseline_done", true).apply();
            } else if (newSale != null) {
                notifySale(context, newSale);
                p.edit().putBoolean("sale_monitor_active", false).remove("sale_monitor_ids").putBoolean("sale_monitor_baseline_done", false).apply();
                scheduleNextStart(context);
                return;
            } else {
                p.edit().putString("sale_monitor_ids", joinIds(current)).apply();
            }
        } catch (Exception ignored) {
            // Temporary network errors do not cancel monitoring.
        }
        schedule(context, CHECK, System.currentTimeMillis() + FIVE_MINUTES);
    }

    private JSONArray fetchSales(Context context, String token) throws Exception {
        HttpResult r = request("GET", "/api/profile/open_sales", token);
        if (r.code == 401) {
            HttpResult refreshed = request("POST", "/api/refresh", token);
            if (refreshed.code == 200) {
                JSONObject body = new JSONObject(refreshed.body);
                String next = body.optString("access_token", body.optString("token", ""));
                JSONObject data = body.optJSONObject("data");
                if (next.isEmpty() && data != null) next = data.optString("access_token", data.optString("token", ""));
                if (!next.isEmpty()) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("token", next).putLong("expires_at", System.currentTimeMillis() + 55L * 60L * 1000L).apply();
                    r = request("GET", "/api/profile/open_sales", next);
                }
            }
        }
        if (r.code < 200 || r.code >= 300) return null;
        Object parsed = new org.json.JSONTokener(r.body).nextValue();
        if (parsed instanceof JSONArray) return (JSONArray) parsed;
        if (parsed instanceof JSONObject) {
            JSONArray data = ((JSONObject) parsed).optJSONArray("data");
            return data == null ? new JSONArray() : data;
        }
        return new JSONArray();
    }

    private HttpResult request(String method, String path, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Origin", "https://mishnatyosef.org");
        c.setRequestProperty("Referer", "https://mishnatyosef.org/");
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        if (method.equals("POST")) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] payload = "{\"remember\":1}".getBytes(StandardCharsets.UTF_8);
            try (java.io.OutputStream os = c.getOutputStream()) { os.write(payload); }
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder response = new StringBuilder();
        if (stream != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) response.append(line);
            }
        }
        c.disconnect();
        return new HttpResult(code, response.toString());
    }

    private void notifySale(Context context, JSONObject sale) {
        ensureNotificationChannel(context);
        int id = sale.optInt("id", -1);
        String name = sale.optString("custom_name", "");
        if (name.isEmpty()) name = sale.optString("name", "");
        if (name.isEmpty()) {
            JSONObject parent = sale.optJSONObject("parent");
            if (parent != null) name = parent.optString("week", "");
        }
        if (name.isEmpty()) name = "מכירה חדשה";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("notify_enabled", true)) return;
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("sale_id", id);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent content = PendingIntent.getActivity(context, 9000 + Math.max(id, 0), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean soundEnabled = prefs.getBoolean("notify_sound", true);
        boolean vibrateEnabled = prefs.getBoolean("notify_vibrate", true);
        String channelId = CHANNEL + (soundEnabled ? "_s1" : "_s0") + (vibrateEnabled ? "_v1" : "_v0");
        createChannel(context, channelId, soundEnabled, vibrateEnabled);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channelId) : new Notification.Builder(context);
        int defaults = 0;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (soundEnabled) defaults |= Notification.DEFAULT_SOUND;
            if (vibrateEnabled) defaults |= Notification.DEFAULT_VIBRATE;
        }
        Notification notification = builder.setDefaults(defaults).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("נפתחה מכירה במשנת יוסף")
                .setContentText(name)
                .setStyle(new Notification.BigTextStyle().bigText("נפתחה מכירה חדשה: " + name + ". לחצי כדי לפתוח אותה."))
                .setContentIntent(content).setAutoCancel(true).setWhen(System.currentTimeMillis()).build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) nm.notify(9000 + Math.max(id, 0), notification);
    }

    public static void ensureNotificationChannel(Context context) { createChannel(context, CHANNEL + "_s1_v1", true, true); }

    private static void createChannel(Context context, String id, boolean sound, boolean vibrate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(id, "מכירות חדשות", NotificationManager.IMPORTANCE_HIGH);
                channel.setSound(sound ? android.provider.Settings.System.DEFAULT_NOTIFICATION_URI : null, null);
                channel.enableVibration(vibrate);
                if (vibrate) channel.setVibrationPattern(new long[]{0, 250, 150, 250});
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Set<String> parseIds(String raw) {
        Set<String> ids = new HashSet<>();
        if (raw == null || raw.isEmpty()) return ids;
        for (String id : raw.split(",")) if (!id.isEmpty()) ids.add(id);
        return ids;
    }

    private String joinIds(Set<String> ids) {
        StringBuilder b = new StringBuilder();
        for (String id : ids) { if (b.length() > 0) b.append(','); b.append(id); }
        return b.toString();
    }

    private static class HttpResult {
        final int code; final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body; }
    }
}
