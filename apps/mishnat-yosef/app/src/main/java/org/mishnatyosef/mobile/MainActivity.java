package org.mishnatyosef.mobile;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.ArrayAdapter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Build verification checkpoint: keep this source in the APK build trigger.
public class MainActivity extends Activity {
    private static final String API = "https://v2.mishnatyosef.org";
    private static final String PREFS = "mishnat_auth";
    private static final String IMAGE_BASE = "https://images.mishnatyosef.org/images/items/";
    private static final long REFRESH_MARGIN_MS = 300000L;
    private static final int PAGE_SIZE = 50;
    private static final long SAVE_DEBOUNCE_MS = 500L;
    private static final long SEARCH_DEBOUNCE_MS = 250L;

    // API calls (login, lists, summaries). Images and order edits have their own threads,
    // so 400+ image downloads can never block an API call or a quantity update.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Order edits run strictly in order on a single thread.
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    // Incremented on every new screen; async results of an old screen are dropped.
    private final AtomicInteger screenGen = new AtomicInteger();

    // Static: survives Activity re-creation (rotation). Sized as 1/8 of the app heap, in bytes.
    private static final LruCache<String, Bitmap> imageCache =
            new LruCache<String, Bitmap>((int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8)) {
                @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
            };
    private final Set<String> failedImages = Collections.synchronizedSet(new HashSet<String>());

    private SharedPreferences prefs;
    private LinearLayout root;
    private EditText emailField;
    private EditText passwordField;
    private TextView statusView;
    private Button loginButton;
    private boolean busy = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(230, 94, 20));
        getWindow().setNavigationBarColor(Color.rgb(191, 70, 12));
        SaleMonitor.ensureNotificationChannel(this);
        requestMonitoringPermissions(false);
        if (hasToken()) {
            SaleMonitor.scheduleNextStart(this);
            if (getIntent() != null && getIntent().hasExtra("sale_id")) {
                openSaleFromNotification(getIntent().getIntExtra("sale_id", -1));
            } else {
                showDashboard();
            }
        } else showLogin();
    }

    // Needs android:launchMode="singleTop" (or singleTask) on the activity in the manifest.
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (hasToken() && intent != null && intent.hasExtra("sale_id")) {
            openSaleFromNotification(intent.getIntExtra("sale_id", -1));
        }
    }

    @Override protected void onPause() {
        super.onPause();
        flushPendingSaves();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        imageExecutor.shutdownNow();
        saveExecutor.shutdown(); // let queued order edits finish
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private boolean isStale(int gen) { return screenGen.get() != gen; }

    private String msg(Exception e, String fallback) {
        return e.getMessage() == null ? fallback : e.getMessage();
    }

    private String shorten(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(34, 49, 46));
        v.setGravity(Gravity.RIGHT);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(4), dp(8), dp(4), dp(8));
        return v;
    }

    private TextView compactText(String value, float size, boolean bold) {
        TextView v = text(value, size, bold);
        v.setPadding(dp(4), dp(2), dp(4), dp(2));
        return v;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(239, 108, 0));
        bg.setCornerRadius(dp(18));
        b.setBackground(bg);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        b.setMinHeight(dp(52));
        return b;
    }

    private void beginScreen(String heading) { beginScreen(heading, false); }

    // fixedList=true: no outer ScrollView, the caller adds its own ListView that fills the screen.
    private void beginScreen(String heading, boolean fixedList) {
        screenGen.incrementAndGet();
        if (searchRunnable != null) { uiHandler.removeCallbacks(searchRunnable); searchRunnable = null; }
        productList = null;
        productAdapter = null;
        orderInfoView = null;

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(255, 249, 244));
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(255, 249, 244));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (fixedList) {
            root.setPadding(dp(10), dp(10), dp(10), dp(4));
            screen.addView(root, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView title = compactText("משנת יוסף · " + heading, 16, true);
            title.setTextColor(Color.rgb(230, 94, 20));
            title.setGravity(Gravity.CENTER);
            root.addView(title, new LinearLayout.LayoutParams(-1, -2));
            statusView = compactText("", 12, false);
            root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
        } else {
            root.setPadding(dp(22), dp(28), dp(22), dp(18));
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
            screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

            TextView title = text("משנת יוסף", 28, true);
            title.setTextColor(Color.rgb(230, 94, 20));
            title.setGravity(Gravity.CENTER);
            root.addView(title, new LinearLayout.LayoutParams(-1, -2));
            TextView sub = text(heading, 16, false);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, 0, 0, dp(24));
            root.addView(sub, new LinearLayout.LayoutParams(-1, -2));
            statusView = text("", 14, false);
            root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
        }

        if (hasToken()) {
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setGravity(Gravity.CENTER);
            nav.setBackgroundColor(Color.WHITE);
            nav.setElevation(dp(8));
            String[] labels = {"ראשי", "מכירות פתוחות", "זיכויים", "היסטוריה"};
            int[] icons = {android.R.drawable.ic_menu_agenda, android.R.drawable.ic_menu_view, android.R.drawable.ic_menu_recent_history, android.R.drawable.ic_menu_info_details};
            Runnable[] actions = {this::showDashboard, this::showOpenSales, this::loadFeatureTransactions, this::loadFeatureOrders};
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                item.setPadding(dp(2), dp(5), dp(2), dp(5));
                ImageView icon = new ImageView(this);
                icon.setImageResource(icons[i]);
                icon.setColorFilter(Color.rgb(230, 94, 20));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
                item.addView(icon, ip);
                TextView label = text(labels[i], 11, true);
                label.setGravity(Gravity.CENTER);
                label.setPadding(0, dp(2), 0, 0);
                item.addView(label, new LinearLayout.LayoutParams(-1, -2));
                item.setOnClickListener(v -> actions[index].run());
                nav.addView(item, new LinearLayout.LayoutParams(0, dp(60), 1));
            }
            screen.addView(nav, new LinearLayout.LayoutParams(-1, dp(60)));
        }
        setContentView(screen);
    }

    private void showLogin() {
        beginScreen("התחברות לחשבון");
        emailField = new EditText(this);
        emailField.setSingleLine(true);
        emailField.setHint("כתובת אימייל");
        emailField.setInputType(33);
        emailField.setText(prefs.getString("email", ""));
        root.addView(emailField, new LinearLayout.LayoutParams(-1, dp(56)));

        passwordField = new EditText(this);
        passwordField.setSingleLine(true);
        passwordField.setHint("סיסמה");
        passwordField.setInputType(129);
        root.addView(passwordField, new LinearLayout.LayoutParams(-1, dp(56)));

        loginButton = button("התחברות");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(18);
        bp.bottomMargin = dp(12);
        root.addView(loginButton, bp);
        loginButton.setOnClickListener(v -> login());
    }

    private void showDashboard() {
        beginScreen("מחובר לחשבון שלך");
        TextView account = text("חשבון: " + prefs.getString("email", ""), 14, false);
        root.addView(account, new LinearLayout.LayoutParams(-1, -2));
        statusView.setText("טוען את המכירות הפתוחות…");
        TextView hint = text("בחרי פעולה מהתפריט הקבוע בתחתית המסך.", 15, false);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        addHomeNotificationSettings();
        Button refresh = button("רענון מכירות פתוחות");
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(-1, -2);
        refreshLp.topMargin = dp(8);
        refreshLp.bottomMargin = dp(12);
        root.addView(refresh, refreshLp);
        refresh.setOnClickListener(v -> loadOpenSales());
        Button logout = button("התנתקות");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(12);
        root.addView(logout, lp);
        logout.setOnClickListener(v -> {
            SaleMonitor.stopMonitoring(this);
            prefs.edit().remove("token").remove("expires_at").apply();
            allRows.clear();
            shownRows.clear();
            featureQuantities.clear();
            confirmedAmounts.clear();
            showLogin();
        });
        loadOpenSales();
    }

    private void requestMonitoringPermissions(boolean forceExactAlarmSettings) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && (forceExactAlarmSettings || !prefs.getBoolean("notification_permission_requested", false))) {
            prefs.edit().putBoolean("notification_permission_requested", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4101);
            return;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()
                    && (forceExactAlarmSettings || !prefs.getBoolean("exact_alarm_settings_prompted", false))) {
                prefs.edit().putBoolean("exact_alarm_settings_prompted", true).apply();
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
                catch (Exception ignored) { }
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 4101) requestMonitoringPermissions(false);
    }

    private void addHomeNotificationSettings() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(7), dp(10), dp(7));
        card.setBackgroundColor(Color.rgb(255, 248, 241));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(3); cardLp.bottomMargin = dp(3);
        root.addView(card, cardLp);

        TextView status = text(notificationStatusText(), 13, false);
        status.setPadding(dp(4), dp(3), dp(4), dp(5));
        card.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button settings = button("הגדרות התראה");
        settings.setTextSize(12);
        settings.setMinHeight(dp(34));
        settings.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(-2, dp(36));
        settingsLp.gravity = Gravity.END;
        card.addView(settings, settingsLp);
        settings.setOnClickListener(v -> showNotificationSettingsDialog(status));
    }

    private String notificationStatusText() {
        boolean enabled = prefs.getBoolean("sale_monitor_enabled", true);
        int[] days = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
        String[] names = {"יום א׳", "יום ב׳", "יום ג׳", "יום ד׳", "יום ה׳", "יום ו׳", "יום ש׳"};
        int start = prefs.getInt("monitor_start_day", Calendar.THURSDAY);
        int end = prefs.getInt("monitor_end_day", Calendar.SUNDAY);
        String startName = names[indexOf(days, start)];
        String endName = names[indexOf(days, end)];
        String startTime = prefs.getString("monitor_start_time", "19:00");
        String endTime = prefs.getString("monitor_end_time", "10:00");
        int interval = prefs.getInt("monitor_interval_minutes", 5);
        if (!enabled) return "ההתראה כבויה. ניתן להפעיל דרך ההגדרות.";
        return "ההתראה פועלת מ" + startName + " " + startTime + " עד " + endName + " " + endTime
                + " · בדיקה כל " + interval + " דקות";
    }

    private void showNotificationSettingsDialog(TextView homeStatus) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(8), dp(18), dp(4));
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Switch toggle = new Switch(this);
        toggle.setText("התראה על מכירה חדשה");
        toggle.setTextSize(15);
        toggle.setChecked(prefs.getBoolean("sale_monitor_enabled", true));
        panel.addView(toggle, new LinearLayout.LayoutParams(-1, dp(44)));

        String[] days = {"א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"};
        int[] calendarDays = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, days);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        LinearLayout startRow = new LinearLayout(this);
        startRow.setGravity(Gravity.CENTER_VERTICAL);
        startRow.addView(text("מתחילה ביום", 13, false), new LinearLayout.LayoutParams(dp(95), -2));
        Spinner startDay = new Spinner(this);
        startDay.setAdapter(dayAdapter);
        startDay.setSelection(indexOf(calendarDays, prefs.getInt("monitor_start_day", Calendar.THURSDAY)));
        startRow.addView(startDay, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView startTime = dialogTimeField(prefs.getString("monitor_start_time", "19:00"));
        startRow.addView(startTime, new LinearLayout.LayoutParams(dp(76), dp(42)));
        panel.addView(startRow, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout endRow = new LinearLayout(this);
        endRow.setGravity(Gravity.CENTER_VERTICAL);
        endRow.addView(text("מסתיימת ביום", 13, false), new LinearLayout.LayoutParams(dp(95), -2));
        Spinner endDay = new Spinner(this);
        endDay.setAdapter(dayAdapter);
        endDay.setSelection(indexOf(calendarDays, prefs.getInt("monitor_end_day", Calendar.SUNDAY)));
        endRow.addView(endDay, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView endTime = dialogTimeField(prefs.getString("monitor_end_time", "10:00"));
        endRow.addView(endTime, new LinearLayout.LayoutParams(dp(76), dp(42)));
        panel.addView(endRow, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);
        intervalRow.addView(text("בדיקה כל כמה דקות?", 13, false), new LinearLayout.LayoutParams(0, -2, 1));
        Integer[] intervals = {1, 2, 3, 5, 10, 15, 20, 30, 60};
        String[] intervalLabels = {"1", "2", "3", "5", "10", "15", "20", "30", "60"};
        ArrayAdapter<String> intervalAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, intervalLabels);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner intervalSpinner = new Spinner(this);
        intervalSpinner.setAdapter(intervalAdapter);
        intervalSpinner.setSelection(indexOf(intervals, prefs.getInt("monitor_interval_minutes", 5)));
        intervalRow.addView(intervalSpinner, new LinearLayout.LayoutParams(dp(90), dp(42)));
        intervalRow.addView(text("דקות", 13, false));
        panel.addView(intervalRow, new LinearLayout.LayoutParams(-1, dp(46)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("הגדרות התראה")
                .setView(panel)
                .setNegativeButton("ביטול", null)
                .setPositiveButton("שמירה", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String st = startTime.getText().toString().trim(), et = endTime.getText().toString().trim();
            if (!validTime(st) || !validTime(et)) {
                Toast.makeText(this, "יש לבחור שעה תקינה", Toast.LENGTH_LONG).show();
                return;
            }
            int interval = intervals[intervalSpinner.getSelectedItemPosition()];
            prefs.edit().putBoolean("sale_monitor_enabled", toggle.isChecked())
                    .putInt("monitor_start_day", calendarDays[startDay.getSelectedItemPosition()])
                    .putInt("monitor_end_day", calendarDays[endDay.getSelectedItemPosition()])
                    .putString("monitor_start_time", st).putString("monitor_end_time", et)
                    .putInt("monitor_interval_minutes", interval).apply();
            if (toggle.isChecked()) {
                requestMonitoringPermissions(false);
                SaleMonitor.stopMonitoring(this);
                SaleMonitor.scheduleNextStart(this);
            } else {
                SaleMonitor.stopMonitoring(this);
            }
            homeStatus.setText(notificationStatusText());
            Toast.makeText(this, "הגדרות ההתראה נשמרו", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private TextView dialogTimeField(String initialTime) {
        TextView time = text(initialTime, 14, true);
        time.setGravity(Gravity.CENTER);
        time.setPadding(dp(3), 0, dp(3), 0);
        time.setBackground(timePickerBackground());
        time.setOnClickListener(v -> {
            String[] parts = time.getText().toString().split(":");
            int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]);
            new TimePickerDialog(this, (picker, h, m) -> time.setText(String.format(java.util.Locale.US, "%02d:%02d", h, m)), hour, minute, true).show();
        });
        return time;
    }

    private android.graphics.drawable.GradientDrawable timePickerBackground() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), Color.rgb(230, 94, 20));
        return bg;
    }

    private int indexOf(int[] values, int target) { for (int i = 0; i < values.length; i++) if (values[i] == target) return i; return 0; }
    private int indexOf(Integer[] values, int target) { for (int i = 0; i < values.length; i++) if (values[i] == target) return i; return 3; }
    private boolean validTime(String value) { if (value == null || !value.matches("[0-2][0-9]:[0-5][0-9]")) return false; return Integer.parseInt(value.substring(0, 2)) < 24; }

    private boolean hasToken() {
        return !prefs.getString("token", "").isEmpty();
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        if (statusView != null) statusView.setText(message);
        if (loginButton != null) loginButton.setEnabled(!value);
    }

    private void login() {
        if (busy) return;
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            statusView.setText("יש למלא אימייל וסיסמה.");
            return;
        }
        hideKeyboard();
        setBusy(true, "מתחבר…");
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                body.put("remember", 1);
                HttpResult result = request("POST", "/api/login", null, body);
                if (result.code == 401 || result.code == 403 || result.code == 422) {
                    throw new Exception("פרטי ההתחברות אינם נכונים.");
                }
                if (result.code < 200 || result.code >= 300) {
                    throw new Exception("השרת החזיר שגיאה (" + result.code + ").");
                }
                JSONObject data = new JSONObject(result.body);
                String token = pickToken(data);
                if (token.isEmpty()) throw new Exception("השרת לא החזיר טוקן התחברות מוכר.");
                long expiry = parseExpiry(data, token);
                prefs.edit().putString("token", token).putString("email", email)
                        .putLong("expires_at", expiry).apply();
                runOnUiThread(() -> {
                    busy = false;
                    loginButton = null;
                    SaleMonitor.scheduleNextStart(this);
                    showDashboard();
                });
            } catch (Exception e) {
                String message = msg(e, "ההתחברות נכשלה.");
                runOnUiThread(() -> setBusy(false, message));
            }
        });
    }

    private void showOpenSales() {
        beginScreen("הזמנות פתוחות");
        statusView.setText("טוען הזמנות פתוחות…");
        loadOpenSales();
    }

    private void openSaleFromNotification(int saleId) {
        if (saleId < 0) { showOpenSales(); return; }
        beginScreen("פתיחת המכירה החדשה…");
        final int gen = screenGen.get();
        statusView.setText("טוען את המכירה מההתראה…");
        executor.execute(() -> {
            try {
                HttpResult r = authRequest("GET", "/api/profile/open_sales", null);
                if (r.code < 200 || r.code >= 300) throw new Exception("לא ניתן לטעון את המכירה. פתחי את המכירות הפתוחות ורענני.");
                JSONArray sales = extractArray(r.body, "data");
                JSONObject found = null;
                for (int i = 0; i < sales.length(); i++) {
                    JSONObject sale = sales.optJSONObject(i);
                    if (sale != null && sale.optInt("id", -1) == saleId) { found = sale; break; }
                }
                final JSONObject selected = found;
                runOnUiThread(() -> {
                    if (isStale(gen)) return;
                    if (selected != null) loadFeatureProducts(selected);
                    else {
                        showOpenSales();
                        Toast.makeText(this, "המכירה כבר לא פתוחה. מציג את המכירות העדכניות.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                String message = msg(e, "טעינת המכירה נכשלה.");
                runOnUiThread(() -> {
                    if (isStale(gen)) return;
                    showOpenSales();
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadOpenSales() {
        final int gen = screenGen.get();
        if (statusView != null) statusView.setText("טוען מכירות פתוחות…");
        executor.execute(() -> {
            try {
                HttpResult r = authRequest("GET", "/api/profile/open_sales", null);
                if (r.code == 401) {
                    prefs.edit().remove("token").remove("expires_at").apply();
                    runOnUiThread(() -> {
                        SaleMonitor.stopMonitoring(this);
                        showLogin();
                        Toast.makeText(this, "נדרשת התחברות מחדש", Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                if (r.code < 200 || r.code >= 300) throw new Exception("טעינת המכירות נכשלה (" + r.code + ").");
                final JSONArray rows = extractArray(r.body, "data");
                runOnUiThread(() -> {
                    if (isStale(gen)) return; // the user already moved to another screen
                    beginScreen("הזמנות פתוחות");
                    statusView.setText("נמצאו " + rows.length() + " מכירות פתוחות. לחצי על מכירה כדי להיכנס ולהזמין.");
                    if (rows.length() == 0) { root.addView(text("אין כרגע מכירות פתוחות.", 15, false)); return; }
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject sale = rows.optJSONObject(i); if (sale == null) continue;
                        String name = firstOrderString(sale, "custom_name", "name", "title");
                        JSONObject parent = sale.optJSONObject("parent");
                        if (name.isEmpty() && parent != null) name = firstOrderString(parent, "week", "name", "title");
                        if (name.isEmpty()) name = "מכירה #" + sale.optString("id", "?");
                        String dates = "";
                        String begin = firstOrderString(sale, "begin", "start", "start_at");
                        String finish = firstOrderString(sale, "end", "finish", "end_at");
                        if (!begin.isEmpty()) dates += "פתיחה: " + begin;
                        if (!finish.isEmpty()) dates += (dates.isEmpty() ? "" : "   ·   ") + "סגירה: " + finish;
                        JSONObject order = sale.optJSONObject("order");
                        boolean hasOrder = order != null && order.optLong("id", order.optLong("order_id", -1)) > 0;
                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(dp(14), dp(10), dp(14), dp(10));
                        card.setBackgroundColor(Color.WHITE);
                        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                        cp.bottomMargin = dp(10);
                        root.addView(card, cp);
                        card.addView(text(name, 17, true));
                        if (!dates.isEmpty()) card.addView(text(dates, 12, false));
                        card.addView(text(hasOrder ? "קיימת הזמנה — לחצי להמשך" : "עדיין לא נפתחה הזמנה — לחצי כדי להתחיל", 13, true));
                        LinearLayout actions = new LinearLayout(this);
                        actions.setOrientation(LinearLayout.HORIZONTAL);
                        actions.setGravity(Gravity.CENTER_VERTICAL);
                        Button enter = button(hasOrder ? "כניסה להזמנה  ›" : "פתיחת הזמנה  ›");
                        Button info = button("פרטי מכירה");
                        info.setTextSize(12); info.setMinHeight(dp(42)); enter.setMinHeight(dp(42));
                        actions.addView(enter, new LinearLayout.LayoutParams(0, -2, 1));
                        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-2, -2); infoLp.leftMargin = dp(6);
                        actions.addView(info, infoLp);
                        card.addView(actions);
                        final JSONObject selected = sale;
                        enter.setOnClickListener(v -> loadFeatureProducts(selected));
                        card.setOnClickListener(v -> loadFeatureProducts(selected));
                        info.setOnClickListener(v -> showSaleDetails(selected));
                    }
                });
            } catch (Exception e) {
                String message = msg(e, "אירעה שגיאה בטעינה.");
                runOnUiThread(() -> { if (!isStale(gen) && statusView != null) statusView.setText(message); });
            }
        });
    }

    // ---------------------------------------------------------------- auth / network

    private synchronized String refreshToken(String token) {
        // Another thread may have refreshed already - reuse its token instead of refreshing twice.
        String current = prefs.getString("token", "");
        if (!current.isEmpty() && !current.equals(token)) return current;
        try {
            JSONObject payload = new JSONObject();
            payload.put("remember", 1);
            HttpResult r = request("POST", "/api/refresh", token, payload);
            if (r.code != 200) r = request("POST", "/api/refresh", token, null);
            if (r.code != 200) return "";
            JSONObject data = new JSONObject(r.body);
            String next = pickToken(data);
            if (!next.isEmpty()) {
                long expiry = parseExpiry(data, next);
                prefs.edit().putString("token", next).putLong("expires_at", expiry).apply();
                return next;
            }
        } catch (Exception ignored) { }
        return "";
    }

    private String freshToken() {
        String token = prefs.getString("token", "");
        long expiry = prefs.getLong("expires_at", 0L);
        if (!token.isEmpty() && expiry > 0 && System.currentTimeMillis() > expiry - REFRESH_MARGIN_MS) {
            String refreshed = refreshToken(token);
            if (!refreshed.isEmpty()) token = refreshed;
        }
        return token;
    }

    // Request with the saved token: refreshes it when close to expiry, and retries once on 401.
    private HttpResult authRequest(String method, String path, JSONObject body) throws Exception {
        String token = freshToken();
        HttpResult r = request(method, path, token, body);
        if (r.code == 401) {
            String refreshed = refreshToken(token);
            if (!refreshed.isEmpty()) r = request(method, path, refreshed, body);
        }
        return r;
    }

    private String pickToken(JSONObject data) {
        String token = data.optString("access_token", "");
        if (token.isEmpty()) token = data.optString("token", "");
        JSONObject nested = data.optJSONObject("data");
        if (token.isEmpty() && nested != null) token = nested.optString("access_token", "");
        if (token.isEmpty() && nested != null) token = nested.optString("token", "");
        return token;
    }

    private long parseExpiry(JSONObject data, String token) {
        long value = data.optLong("expires_in", 0L);
        if (value > 0) return value > 1000000000L ? value * 1000L : System.currentTimeMillis() + value * 1000L;
        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = parts[1].replace('-', '+').replace('_', '/');
                while (payload.length() % 4 != 0) payload += "=";
                JSONObject claims = new JSONObject(new String(android.util.Base64.decode(payload, android.util.Base64.DEFAULT), StandardCharsets.UTF_8));
                long exp = claims.optLong("exp", 0L);
                if (exp > 0) return exp * 1000L;
            }
        } catch (Exception ignored) { }
        return System.currentTimeMillis() + 55L * 60L * 1000L;
    }

    private HttpResult request(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Origin", "https://mishnatyosef.org");
            c.setRequestProperty("Referer", "https://mishnatyosef.org/");
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
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
            return new HttpResult(code, response.toString());
        } finally {
            c.disconnect();
        }
    }

    // Accepts either a bare JSON array, or an object holding the array under one of the given keys.
    private JSONArray extractArray(String body, String... keys) throws Exception {
        Object val = new org.json.JSONTokener(body).nextValue();
        if (val instanceof JSONArray) return (JSONArray) val;
        if (val instanceof JSONObject) {
            JSONObject o = (JSONObject) val;
            for (String k : keys) {
                JSONArray a = o.optJSONArray(k);
                if (a != null) return a;
            }
        }
        return new JSONArray();
    }

    // ---------------------------------------------------------------- images

    // Loads a product image into the view: memory cache -> background download -> decode downsampled.
    // The view's tag holds the URL it should show, so recycled views never get a stale image,
    // and queued downloads for views that were already re-bound are skipped.
    private void loadImage(final ImageView view, final String url, final int targetPx) {
        view.setTag(url);
        if (url == null || url.isEmpty()) { view.setImageDrawable(null); return; }
        Bitmap cached = imageCache.get(url);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageDrawable(null);
        if (failedImages.contains(url) || imageExecutor.isShutdown()) return;
        try {
            imageExecutor.execute(() -> {
                if (!url.equals(view.getTag())) return;
                Bitmap b = downloadBitmap(url, targetPx);
                if (b == null) { failedImages.add(url); return; }
                imageCache.put(url, b);
                runOnUiThread(() -> { if (alive() && url.equals(view.getTag())) view.setImageBitmap(b); });
            });
        } catch (RejectedExecutionException ignored) { }
    }

    private Bitmap downloadBitmap(String url, int targetPx) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 6 * 1024 * 1024) return null;
            }
            byte[] data = out.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetPx && o.outHeight / (sample * 2) >= targetPx) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String imageFromFile(JSONObject img) {
        String url = firstOrderString(img, "url", "src", "path", "original", "large", "thumbnail");
        if (!url.isEmpty()) return url;
        String filename = img.optString("filename", "");
        if (filename.isEmpty()) return "";
        String ext = img.optString("extension", "");
        if (ext.startsWith(".")) ext = ext.substring(1);
        boolean hasExt = filename.matches("(?i).*\\.[a-z0-9]{2,5}$");
        return IMAGE_BASE + filename + (hasExt || ext.isEmpty() ? "" : "." + ext);
    }

    private String firstImageFrom(JSONObject obj) {
        JSONArray images = obj.optJSONArray("images");
        if (images == null || images.length() == 0) return "";
        JSONObject img = images.optJSONObject(0);
        if (img != null) return imageFromFile(img);
        Object first = images.opt(0);
        return first == null || first == JSONObject.NULL ? "" : String.valueOf(first);
    }

    private String normalizeImageUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = url.startsWith("//") ? "https:" + url : IMAGE_BASE + url.replaceFirst("^/+", "");
        }
        return url.startsWith("http") ? url : "";
    }

    private String featureImageUrl(JSONObject p) {
        String url = firstOrderString(p, "featured_image", "image_url", "imageUrl", "picture_url");
        if (url.isEmpty()) url = firstImageFrom(p);
        if (url.isEmpty()) {
            JSONObject product = p.optJSONObject("product");
            if (product != null) {
                url = firstOrderString(product, "featured_image", "image_url", "imageUrl");
                if (url.isEmpty()) url = firstImageFrom(product);
            }
        }
        return normalizeImageUrl(url);
    }

    private String orderImageUrl(JSONObject item, JSONObject product) {
        String url = firstOrderString(product, "thumb_featured_image", "thumbFeaturedImage", "featured_image", "featuredImage", "image_url", "imageUrl", "image_path", "imagePath", "picture_url", "pictureUrl", "url");
        if (url.isEmpty()) url = firstOrderString(item, "thumb_featured_image", "thumbFeaturedImage", "featured_image", "featuredImage", "image_url", "imageUrl", "image_path", "imagePath", "picture_url", "pictureUrl");
        if (url.isEmpty()) url = firstImageFrom(product);
        if (url.isEmpty()) url = firstImageFrom(item);
        if (url.isEmpty()) {
            JSONObject img = product.optJSONObject("image");
            if (img == null) img = item.optJSONObject("image");
            if (img != null) url = imageFromFile(img);
        }
        return normalizeImageUrl(url);
    }

    // ---------------------------------------------------------------- open order (products screen)

    private static class ProductRow {
        final JSONObject json;
        final int id, itemSalesId, max, initialAmount;
        final String name, priceText, unit, imageUrl, searchKey;
        final boolean disabled;
        final double stock;

        ProductRow(JSONObject json, int id, int itemSalesId, String name, String priceText, String unit, int max,
                   boolean disabled, double stock, String imageUrl, String searchKey, int initialAmount) {
            this.json = json; this.id = id; this.itemSalesId = itemSalesId; this.name = name;
            this.priceText = priceText; this.unit = unit; this.max = max; this.disabled = disabled;
            this.stock = stock; this.imageUrl = imageUrl; this.searchKey = searchKey; this.initialAmount = initialAmount;
        }

        boolean canOrder() { return !disabled && stock != 0; }
    }

    private static class CardHolder {
        LinearLayout card;
        ImageView image;
        TextView name, price, limit, unavailable, qty;
        Button minus, plus, info;
        ProductRow bound;
    }

    private static class RowHolder { CardHolder a, b; }

    private JSONObject featureSale = null;
    private long featureOrderId = -1;
    private final ArrayList<ProductRow> allRows = new ArrayList<>();
    private final ArrayList<ProductRow> shownRows = new ArrayList<>();
    // Current quantity per product (what the user sees) and last quantity confirmed by the server.
    private final ConcurrentHashMap<Integer, Integer> featureQuantities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> confirmedAmounts = new ConcurrentHashMap<>();
    private final HashMap<Integer, Runnable> pendingSaves = new HashMap<>(); // UI thread only
    private int inFlightSaves = 0; // UI thread only
    private String productQuery = "";
    private ListView productList;
    private ProductAdapter productAdapter;
    private TextView orderInfoView;
    private Runnable searchRunnable;

    private int featureProductId(JSONObject p) { return p.optInt("item_salesID", p.optInt("id", p.optInt("itemID", -1))); }

    private long extractOrderId(JSONObject obj) {
        if (obj == null) return -1;
        long id = obj.optLong("id", obj.optLong("order_id", obj.optLong("orderID", -1)));
        JSONObject nested = obj.optJSONObject("order");
        if (id <= 0 && nested != null) id = nested.optLong("id", nested.optLong("order_id", -1));
        JSONObject data = obj.optJSONObject("data");
        if (id <= 0 && data != null && data != obj) id = extractOrderId(data);
        return id;
    }

    private long findOrCreateOrder(JSONObject sale, String token) throws Exception {
        JSONObject existing = sale.optJSONObject("order");
        long existingId = extractOrderId(existing);
        if (existingId > 0) return existingId;

        HttpResult cardsResponse = request("GET", "/api/payment_methods/cards", token, null);
        if (cardsResponse.code < 200 || cardsResponse.code >= 300)
            throw new Exception("לא ניתן לטעון את אמצעי התשלום השמורים (" + cardsResponse.code + ").");
        JSONArray cards = extractArray(cardsResponse.body, "data");
        int cardId = -1;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject c = cards.optJSONObject(i);
            if (c != null && (c.optBoolean("is_default", false) || c.optBoolean("default", false))) {
                cardId = c.optInt("id", c.optInt("card_id", -1)); if (cardId > 0) break;
            }
        }
        if (cardId <= 0 && cards.length() == 1) {
            JSONObject c = cards.optJSONObject(0);
            if (c != null) cardId = c.optInt("id", c.optInt("card_id", -1));
        }
        if (cardId <= 0) throw new Exception("לא נמצא כרטיס ברירת מחדל שמור. הגדירי כרטיס ברירת מחדל באתר משנת יוסף ונסי שוב.");
        JSONObject body = new JSONObject(); body.put("card_id", cardId);
        int saleId = sale.optInt("id", -1);
        if (saleId <= 0) throw new Exception("מזהה המכירה חסר.");
        HttpResult created = request("POST", "/api/create_order/" + saleId, token, body);
        if (created.code < 200 || created.code >= 300)
            throw new Exception("פתיחת ההזמנה נכשלה (" + created.code + "): " + shorten(created.body));
        try {
            long id = extractOrderId(new JSONObject(created.body));
            if (id > 0) return id;
        } catch (Exception ignored) { }

        // Some server versions only return success; resolve the new order by sale id.
        HttpResult ordersResponse = request("GET", "/api/orders", token, null);
        if (ordersResponse.code >= 200 && ordersResponse.code < 300) {
            JSONArray orders = extractArray(ordersResponse.body, "data");
            for (int i = 0; i < orders.length(); i++) {
                JSONObject o = orders.optJSONObject(i); if (o == null) continue;
                int sid = o.optInt("saleID", o.optInt("sale_id", -2));
                JSONObject osale = o.optJSONObject("sale");
                if (sid < 0 && osale != null) sid = osale.optInt("id", -2);
                if (sid == saleId) { long id = extractOrderId(o); if (id > 0) return id; }
            }
        }
        throw new Exception("השרת אישר פתיחת הזמנה אך לא החזיר מזהה. בדקי בהיסטוריית ההזמנות לפני ניסיון חוזר.");
    }

    private void appendProductDetail(StringBuilder out, String label, String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) return;
        if (out.length() > 0) out.append("\n");
        out.append(label).append(": ").append(value.trim());
    }

    private void showFeatureProductDetails(JSONObject p) {
        if (p == null || !alive()) return;
        StringBuilder out = new StringBuilder();
        appendProductDetail(out, "שם מוצר", firstOrderString(p, "name", "custom_name", "title"));
        appendProductDetail(out, "מחיר", firstOrderString(p, "price", "unit_price", "price_list"));
        appendProductDetail(out, "כמות בהזמנה", String.valueOf(featureQuantities.getOrDefault(featureProductId(p), 0)));
        appendProductDetail(out, "זמינות", firstOrderString(p, "available"));
        appendProductDetail(out, "יחידה", firstOrderString(p, "item_unit", "units", "unit"));
        appendProductDetail(out, "פרטים", firstOrderString(p, "description", "details", "approx"));
        JSONObject product = p.optJSONObject("product");
        if (product != null) {
            appendProductDetail(out, "תיאור מוצר", firstOrderString(product, "description", "details", "name"));
        }
        String title = firstOrderString(p, "name", "custom_name", "title");
        new AlertDialog.Builder(this)
                .setTitle(title.isEmpty() ? "פרטי מוצר" : title)
                .setMessage(out.length() == 0 ? "אין פרטים נוספים זמינים." : out.toString())
                .setPositiveButton("סגירה", null)
                .show();
    }

    private void showSaleDetails(JSONObject sale) {
        if (sale == null || !alive()) return;
        StringBuilder out = new StringBuilder();
        appendProductDetail(out, "מזהה מכירה", firstOrderString(sale, "id"));
        appendProductDetail(out, "שם מכירה", firstOrderString(sale, "custom_name", "name", "title"));
        appendProductDetail(out, "תחילת מכירה", firstOrderString(sale, "begin", "start", "start_at"));
        appendProductDetail(out, "סיום מכירה", firstOrderString(sale, "end", "finish", "end_at"));
        JSONObject parent = sale.optJSONObject("parent");
        if (parent != null) {
            appendProductDetail(out, "שבוע", firstOrderString(parent, "week", "name", "title"));
            appendProductDetail(out, "פרטי שבוע", firstOrderString(parent, "name", "title", "description"));
        }
        JSONObject order = sale.optJSONObject("order");
        appendProductDetail(out, "מצב הזמנה", extractOrderId(order) > 0 ? "הזמנה קיימת #" + extractOrderId(order) : "טרם נפתחה הזמנה");
        try {
            java.util.Iterator<String> keys = sale.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if ("parent".equals(key) || "order".equals(key) || "id".equals(key) || "begin".equals(key) || "end".equals(key)) continue;
                Object value = sale.opt(key);
                if (value instanceof String || value instanceof Number || value instanceof Boolean)
                    appendProductDetail(out, key, String.valueOf(value));
            }
        } catch (Exception ignored) { }
        new AlertDialog.Builder(this).setTitle("פרטי המכירה").setMessage(out.length() == 0 ? "אין פרטים נוספים זמינים." : out.toString())
                .setPositiveButton("סגירה", null)
                .setNegativeButton("כניסה להזמנה", (d, w) -> loadFeatureProducts(sale)).show();
    }

    private double numericAvailable(JSONObject p) {
        if (!p.has("available") || p.isNull("available")) return -1;
        Object value = p.opt("available");
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).trim()); } catch (Exception ignored) { return -1; }
        }
        // Some API versions return a boolean/object here; it is not a stock count.
        return -1;
    }

    // Everything that needs JSON parsing / regex is computed once here (background thread),
    // so binding a row while scrolling is just setText + setVisibility.
    private ProductRow makeRow(JSONObject p, int amount) {
        int id = featureProductId(p);
        JSONObject product = p.optJSONObject("product");
        String name = firstOrderString(p, "name", "full_name", "title");
        if (name.isEmpty() && product != null) name = firstOrderString(product, "name", "full_name", "title");
        if (name.isEmpty()) name = "מוצר #" + id;
        String priceText = firstOrderString(p, "price", "unit_price", "price_list");
        if (priceText.isEmpty() && product != null) priceText = firstOrderString(product, "price", "unit_price");
        String unit = firstOrderString(p, "item_unit", "unit", "item_type");
        return new ProductRow(p, id, p.optInt("item_salesID", p.optInt("item_sales_id", -1)), name, priceText, unit,
                p.optInt("max_per_man", 0), p.optBoolean("disable", false), numericAvailable(p),
                featureImageUrl(p), name.toLowerCase(java.util.Locale.ROOT), amount);
    }

    private HashMap<Integer, Integer> loadSavedAmounts(long orderId) {
        HashMap<Integer, Integer> saved = new HashMap<>();
        try {
            HttpResult orderResponse = authRequest("GET", "/api/orders/" + orderId, null);
            if (orderResponse.code >= 200 && orderResponse.code < 300) {
                JSONObject response = new JSONObject(orderResponse.body);
                JSONObject order = response.optJSONObject("order");
                if (order == null) order = response.optJSONObject("data");
                if (order == null) order = response;
                JSONArray savedItems = orderItemsFrom(order);
                for (int i = 0; i < savedItems.length(); i++) {
                    JSONObject item = savedItems.optJSONObject(i);
                    if (item == null) continue;
                    JSONObject savedProduct = orderProductFrom(item);
                    int itemId = item.optInt("item_salesID", item.optInt("item_sales_id", -1));
                    if (itemId <= 0) itemId = savedProduct.optInt("item_salesID", savedProduct.optInt("item_sales_id", -1));
                    if (itemId <= 0) itemId = item.optInt("itemID", savedProduct.optInt("itemID", -1));
                    int amount = item.optInt("amount", item.optInt("quantity", item.optInt("qty", 0)));
                    if (itemId > 0) saved.put(itemId, Math.max(0, amount));
                }
            }
        } catch (Exception ignored) { }
        return saved;
    }

    // Opens (or creates) the order and loads the products page by page.
    // The first page is shown immediately; the remaining pages are appended while the user already scrolls.
    private void loadFeatureProducts(final JSONObject sale) {
        featureSale = sale;
        failedImages.clear();
        beginScreen("פתיחת הזמנה");
        final int gen = screenGen.get();
        statusView.setText("פותח הזמנה וטוען מוצרים…");
        final int[] liveGen = {gen};                       // UI thread only
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        executor.execute(() -> {
            try {
                final long orderId = findOrCreateOrder(sale, freshToken());
                // Remember the order on the sale so a refresh never tries to create a second order.
                try { JSONObject known = new JSONObject(); known.put("id", orderId); sale.put("order", known); } catch (Exception ignored) { }
                HashMap<Integer, Integer> saved = loadSavedAmounts(orderId);

                for (int page = 1; page <= 100; page++) {
                    if (cancelled.get()) return;
                    HttpResult r = authRequest("GET", "/api/get_items_for_sale/" + orderId + "/null?page=" + page + "&limit=" + PAGE_SIZE, null);
                    if (r.code < 200 || r.code >= 300)
                        throw new Exception("טעינת המוצרים נכשלה (" + r.code + ").");
                    JSONArray a = extractArray(r.body, "data", "items", "products");
                    final ArrayList<ProductRow> rows = new ArrayList<>();
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject p = a.optJSONObject(i);
                        if (p == null) continue;
                        int id = featureProductId(p);
                        int amount = saved.containsKey(id) ? saved.get(id) : Math.max(0, p.optInt("amount", 0));
                        rows.add(makeRow(p, amount));
                    }
                    final boolean first = page == 1;
                    final boolean last = a.length() < PAGE_SIZE || page == 100;
                    runOnUiThread(() -> {
                        if (screenGen.get() != liveGen[0]) { cancelled.set(true); return; } // user left the screen
                        if (first) {
                            featureOrderId = orderId;
                            allRows.clear();
                            featureQuantities.clear();
                            confirmedAmounts.clear();
                            productQuery = "";
                            renderFeatureProducts();
                            liveGen[0] = screenGen.get();
                        }
                        appendRows(rows);
                        if (statusView != null)
                            statusView.setText(last ? (allRows.isEmpty() ? "אין מוצרים במכירה זו." : "נטענו " + allRows.size() + " מוצרים")
                                    : "נטענו " + allRows.size() + " מוצרים, טוען עוד…");
                    });
                    if (last) break;
                }
            } catch (Exception e) {
                String message = msg(e, "פתיחת ההזמנה נכשלה.");
                runOnUiThread(() -> {
                    if (screenGen.get() != liveGen[0]) return;
                    if (statusView != null) statusView.setText(message);
                });
            }
        });
    }

    private void appendRows(ArrayList<ProductRow> rows) {
        for (ProductRow r : rows) {
            allRows.add(r);
            featureQuantities.put(r.id, r.initialAmount);
            confirmedAmounts.put(r.id, r.initialAmount);
        }
        applyFilter();
        updateOrderInfo();
    }

    private void applyFilter() {
        shownRows.clear();
        String q = productQuery.trim().toLowerCase(java.util.Locale.ROOT);
        for (ProductRow r : allRows) if (q.isEmpty() || r.searchKey.contains(q)) shownRows.add(r);
        if (productAdapter != null) productAdapter.notifyDataSetChanged();
    }

    private void updateOrderInfo() {
        if (orderInfoView == null) return;
        int types = 0, units = 0;
        for (int q : featureQuantities.values()) if (q > 0) { types++; units += q; }
        orderInfoView.setText("הזמנה #" + featureOrderId + " · " + types + " סוגי פריטים · " + units
                + " יחידות · הסכום הסופי בסיכום ההזמנה");
    }

    private Button quantityButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(18); b.setAllCaps(false);
        b.setTextColor(Color.rgb(230, 94, 20));
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setMinWidth(0); b.setMinimumWidth(0);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(Color.rgb(255, 239, 224));
        shape.setStroke(dp(1), Color.rgb(230, 94, 20));
        b.setBackground(shape);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        return b;
    }

    private void renderFeatureProducts() {
        String title = featureSale == null ? "מוצרים להזמנה" : firstOrderString(featureSale, "custom_name", "name");
        if (title.isEmpty() && featureSale != null && featureSale.optJSONObject("parent") != null)
            title = firstOrderString(featureSale.optJSONObject("parent"), "week", "name");
        beginScreen(title.isEmpty() ? "מוצרים להזמנה" : title, true);

        LinearLayout topActions = new LinearLayout(this);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(Gravity.CENTER);
        Button back = button("מכירות"); back.setTextSize(11); back.setMinHeight(dp(40));
        Button saleInfo = button("פרטי מכירה"); saleInfo.setTextSize(11); saleInfo.setMinHeight(dp(40));
        Button summary = button("סיכום הזמנה"); summary.setTextSize(11); summary.setMinHeight(dp(40));
        topActions.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        topActions.addView(saleInfo, new LinearLayout.LayoutParams(0, -2, 1));
        topActions.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(topActions);
        back.setOnClickListener(v -> showOpenSales());
        saleInfo.setOnClickListener(v -> showSaleDetails(featureSale));
        summary.setOnClickListener(v -> loadServerOrderSummary());

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("חיפוש מוצר…");
        search.setTextSize(14);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                final String q = s.toString();
                if (searchRunnable != null) uiHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    productQuery = q;
                    applyFilter();
                    if (productList != null) productList.setSelection(0);
                };
                uiHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }
        });
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));

        orderInfoView = compactText("", 12, true);
        root.addView(orderInfoView, new LinearLayout.LayoutParams(-1, -2));

        productList = new ListView(this);
        productList.setDivider(null);
        productList.setDividerHeight(0);
        productList.setSelector(android.R.color.transparent);
        productList.setScrollingCacheEnabled(false);
        productList.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        productAdapter = new ProductAdapter();
        productList.setAdapter(productAdapter);
        root.addView(productList, new LinearLayout.LayoutParams(-1, 0, 1));

        applyFilter();
        updateOrderInfo();
    }

    // Two products per list row. Only the rows on screen exist as views (about 8-10 in total),
    // and they are re-used while scrolling, no matter how many products the sale has.
    private class ProductAdapter extends BaseAdapter {
        @Override public int getCount() { return (shownRows.size() + 1) / 2; }
        @Override public Object getItem(int position) { return null; }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder h;
            if (convertView == null) {
                h = new RowHolder();
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setPadding(0, 0, 0, dp(8));
                h.a = buildCard();
                h.b = buildCard();
                LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(0, -2, 1);
                lpA.setMargins(dp(3), 0, dp(3), 0);
                LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, -2, 1);
                lpB.setMargins(dp(3), 0, dp(3), 0);
                row.addView(h.a.card, lpA);
                row.addView(h.b.card, lpB);
                row.setTag(h);
                convertView = row;
            } else {
                h = (RowHolder) convertView.getTag();
            }
            int i = position * 2;
            if (i < shownRows.size()) bindCard(h.a, shownRows.get(i));
            int j = i + 1;
            if (j < shownRows.size()) {
                h.b.card.setVisibility(View.VISIBLE);
                bindCard(h.b, shownRows.get(j));
            } else {
                h.b.card.setVisibility(View.INVISIBLE);
                h.b.bound = null;
            }
            return convertView;
        }
    }

    private CardHolder buildCard() {
        final CardHolder h = new CardHolder();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Color.WHITE); cardBg.setCornerRadius(dp(12));
        cardBg.setStroke(dp(1), Color.rgb(238, 220, 205));
        card.setBackground(cardBg);
        h.card = card;

        h.image = new ImageView(this);
        h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        h.image.setBackgroundColor(Color.rgb(250, 239, 229));
        card.addView(h.image, new LinearLayout.LayoutParams(-1, dp(105)));

        h.name = text("", 13, true);
        h.name.setGravity(Gravity.CENTER);
        h.name.setMaxLines(3);
        h.name.setMinLines(2);
        h.name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(h.name, new LinearLayout.LayoutParams(-1, -2));

        h.price = text("", 12, true);
        h.price.setGravity(Gravity.CENTER);
        card.addView(h.price, new LinearLayout.LayoutParams(-1, -2));

        h.limit = text("", 11, false);
        h.limit.setGravity(Gravity.CENTER);
        h.limit.setVisibility(View.GONE);
        card.addView(h.limit);

        h.unavailable = text("", 11, true);
        h.unavailable.setGravity(Gravity.CENTER);
        h.unavailable.setVisibility(View.GONE);
        card.addView(h.unavailable);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        h.minus = quantityButton("−");
        h.plus = quantityButton("+");
        h.qty = text("0", 15, true);
        h.qty.setGravity(Gravity.CENTER);
        controls.addView(h.minus);
        controls.addView(h.qty, new LinearLayout.LayoutParams(dp(30), dp(34)));
        controls.addView(h.plus);
        card.addView(controls);

        h.info = button("פרטים");
        h.info.setTextSize(11); h.info.setMinHeight(dp(32));
        h.info.setPadding(dp(3), 0, dp(3), 0);
        card.addView(h.info, new LinearLayout.LayoutParams(-1, dp(34)));

        h.minus.setOnClickListener(v -> { if (h.bound != null) changeQuantity(h, -1); });
        h.plus.setOnClickListener(v -> { if (h.bound != null) changeQuantity(h, 1); });
        h.info.setOnClickListener(v -> { if (h.bound != null) showFeatureProductDetails(h.bound.json); });
        return h;
    }

    private void bindCard(CardHolder h, ProductRow r) {
        h.bound = r;
        h.name.setText(r.name);
        h.price.setText("מחיר: " + (r.priceText.isEmpty() ? "—" : r.priceText + " ₪") + (r.unit.isEmpty() ? "" : " · " + r.unit));
        if (r.max > 0) {
            h.limit.setText("מקס׳ " + r.max);
            h.limit.setVisibility(View.VISIBLE);
        } else h.limit.setVisibility(View.GONE);
        boolean can = r.canOrder();
        if (!can) {
            h.unavailable.setText(r.disabled ? "לא זמין" : "אזל מהמלאי");
            h.unavailable.setVisibility(View.VISIBLE);
        } else h.unavailable.setVisibility(View.GONE);
        h.qty.setText(String.valueOf(featureQuantities.getOrDefault(r.id, 0)));
        h.minus.setEnabled(can);
        h.plus.setEnabled(can);
        loadImage(h.image, r.imageUrl, dp(150));
    }

    // +/- only updates the screen instantly; the server call is debounced and sent in the background.
    // No reload of the order and no re-render of the list.
    private void changeQuantity(CardHolder h, int delta) {
        ProductRow r = h.bound;
        int cur = featureQuantities.getOrDefault(r.id, 0);
        int n = Math.max(0, cur + delta);
        if (n == cur) return;
        if (delta > 0) {
            if (r.max > 0 && n > r.max) { Toast.makeText(this, "מותר להזמין עד " + r.max + " יחידות", Toast.LENGTH_SHORT).show(); return; }
            if (r.stock >= 0 && n > r.stock) { Toast.makeText(this, "נותרו במלאי " + (long) r.stock + " יחידות בלבד", Toast.LENGTH_SHORT).show(); return; }
        }
        featureQuantities.put(r.id, n);
        h.qty.setText(String.valueOf(n));
        updateOrderInfo();
        scheduleSave(r);
    }

    private void scheduleSave(final ProductRow r) {
        if (featureOrderId <= 0) { Toast.makeText(this, "לא נמצאה הזמנה", Toast.LENGTH_SHORT).show(); return; }
        final long orderId = featureOrderId;
        Runnable old = pendingSaves.remove(r.id);
        if (old != null) uiHandler.removeCallbacks(old);
        Runnable task = () -> {
            pendingSaves.remove(r.id);
            inFlightSaves++;
            try {
                saveExecutor.execute(() -> persistQuantity(orderId, r));
            } catch (RejectedExecutionException e) {
                inFlightSaves--;
            }
            updateSaveStatus(false);
        };
        pendingSaves.put(r.id, task);
        uiHandler.postDelayed(task, SAVE_DEBOUNCE_MS);
        updateSaveStatus(false);
    }

    // Sends everything that is still waiting for the debounce right now (leaving the app / opening the summary).
    private void flushPendingSaves() {
        for (Runnable task : new ArrayList<>(pendingSaves.values())) {
            uiHandler.removeCallbacks(task);
            task.run();
        }
    }

    // Runs on saveExecutor: always sends the latest quantity of the product, in order.
    private void persistQuantity(final long orderId, final ProductRow r) {
        final int desired = featureQuantities.getOrDefault(r.id, 0);
        final int confirmed = confirmedAmounts.getOrDefault(r.id, 0);
        String error = null;
        if (desired != confirmed) {
            try {
                HttpResult result;
                if (desired == 0) {
                    if (r.itemSalesId <= 0) throw new Exception("חסר מזהה פריט להסרה מההזמנה.");
                    result = authRequest("POST", "/api/remove_item/" + orderId + "/" + r.itemSalesId, null);
                } else {
                    JSONObject item = new JSONObject(r.json.toString());
                    item.put("amount", desired);
                    if (!item.has("selected_subitems") || item.isNull("selected_subitems")) item.put("selected_subitems", new JSONArray());
                    result = authRequest("POST", (confirmed > 0 ? "/api/update_item/" : "/api/add_item/") + orderId, item);
                }
                if (result.code < 200 || result.code >= 300)
                    throw new Exception("עדכון ההזמנה נכשל (" + result.code + "): " + shorten(result.body));
                confirmedAmounts.put(r.id, desired);
            } catch (Exception e) {
                error = msg(e, "עדכון ההזמנה נכשל");
            }
        }
        final String err = error;
        runOnUiThread(() -> {
            inFlightSaves = Math.max(0, inFlightSaves - 1);
            if (err != null) {
                // Roll the screen back to what the server really has (unless the user already changed it again).
                if (featureQuantities.getOrDefault(r.id, 0) == desired && !pendingSaves.containsKey(r.id))
                    featureQuantities.put(r.id, confirmedAmounts.getOrDefault(r.id, 0));
                if (productAdapter != null) productAdapter.notifyDataSetChanged();
                updateOrderInfo();
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            }
            updateSaveStatus(err != null);
        });
    }

    private void updateSaveStatus(boolean hadError) {
        if (statusView == null || productList == null) return; // not on the products screen
        if (!pendingSaves.isEmpty() || inFlightSaves > 0) statusView.setText("שומר שינויים…");
        else statusView.setText(hadError ? "" : "השינויים נשמרו ✓");
    }

    private void loadServerOrderSummary() {
        if (featureOrderId <= 0) { Toast.makeText(this, "לא נמצאה הזמנה", Toast.LENGTH_SHORT).show(); return; }
        flushPendingSaves();
        statusView.setText("טוען סיכום הזמנה מהשרת…");
        final long orderId = featureOrderId;
        final int gen = screenGen.get();
        // Runs on saveExecutor so it is guaranteed to come after every queued quantity update.
        saveExecutor.execute(() -> {
            try {
                HttpResult r = authRequest("GET", "/api/orders/" + orderId, null);
                if (r.code < 200 || r.code >= 300) throw new Exception("טעינת סיכום ההזמנה נכשלה (" + r.code + ").");
                JSONObject response = new JSONObject(r.body);
                JSONObject order = response.optJSONObject("order");
                if (order == null) order = response.optJSONObject("data");
                if (order == null) order = response;
                JSONArray items = orderItemsFrom(order);
                String total = firstOrderString(order, "total_list", "total", "total_price", "totalPrice", "grand_total", "grandTotal", "sum", "amount_due");
                String status = firstOrderString(order, "status", "order_status");
                StringBuilder out = new StringBuilder();
                if (!status.isEmpty()) out.append("סטטוס: ").append(status).append("\n");
                out.append("פריטים בהזמנה לפי השרת: ").append(items.length()).append("\n\n");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i); if (item == null) continue;
                    JSONObject product = orderProductFrom(item);
                    String name = firstOrderString(product, "name", "full_name", "item_name", "title");
                    if (name.isEmpty()) name = firstOrderString(item, "name", "item_name", "title");
                    if (name.isEmpty()) name = "פריט " + (i + 1);
                    String qty = firstOrderString(item, "amount", "quantity", "qty", "count");
                    if (qty.isEmpty()) qty = firstOrderString(product, "amount", "quantity", "qty", "count");
                    String price = firstOrderString(item, "total_price", "totalPrice", "total_list", "totalList", "subtotal", "sum", "price", "unit_price");
                    if (price.isEmpty()) price = firstOrderString(product, "total_price", "totalPrice", "price", "unit_price");
                    out.append(name).append(" × ").append(qty.isEmpty() ? "—" : qty);
                    if (!price.isEmpty()) out.append(" · ").append(price).append(price.matches(".*[₪$€].*") ? "" : " ₪");
                    out.append("\n");
                }
                if (items.length() == 0) out.append("השרת לא החזיר פריטים להזמנה זו.\n");
                out.append("\nעלות כוללת לפי השרת: ").append(total.isEmpty() ? "השרת לא החזיר סכום כולל" : total + (total.matches(".*[₪$€].*") ? "" : " ₪"));
                final String message = out.toString();
                runOnUiThread(() -> {
                    if (!isStale(gen) && statusView != null) statusView.setText("");
                    if (!alive()) return;
                    new AlertDialog.Builder(this).setTitle("סיכום הזמנה מהשרת")
                            .setMessage(message).setPositiveButton("סגירה", null)
                            .setNeutralButton("רענון הזמנה", (d, w) -> loadFeatureProducts(featureSale)).show();
                });
            } catch (Exception e) {
                String message = msg(e, "טעינת סיכום ההזמנה נכשלה.");
                runOnUiThread(() -> {
                    if (!isStale(gen) && statusView != null) statusView.setText("");
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ---------------------------------------------------------------- order history / details

    private void loadFeatureOrders() {
        beginScreen("היסטוריית הזמנות");
        final int gen = screenGen.get();
        statusView.setText("טוען הזמנות…");
        executor.execute(() -> {
            try {
                HttpResult r = authRequest("GET", "/api/orders", null);
                if (r.code < 200 || r.code >= 300) throw new Exception("שגיאת שרת (" + r.code + ")");
                final JSONArray orders = extractArray(r.body, "data");
                runOnUiThread(() -> {
                    if (isStale(gen)) return;
                    beginScreen("היסטוריית הזמנות");

                    if (orders.length() == 0) statusView.setText("לא נמצאו הזמנות.");
                    for (int i = 0; i < orders.length(); i++) {
                        JSONObject o = orders.optJSONObject(i); if (o == null) continue;
                        JSONObject sale = o.optJSONObject("sale");
                        if (sale == null) sale = new JSONObject();
                        JSONObject parent = sale.optJSONObject("parent");
                        String saleName = firstOrderString(sale, "custom_name");
                        if (saleName.isEmpty() && parent != null) saleName = firstOrderString(parent, "week");
                        if (saleName.isEmpty()) saleName = "מכירה " + firstOrderString(o, "saleID", "sale_id");
                        String orderIdText = firstOrderString(o, "id");
                        TextView t = text(saleName + "  ·  הזמנה #" + orderIdText + "  ›", 16, true);
                        root.addView(t);
                        final long orderId = o.optLong("id", -1);
                        if (orderId > 0) t.setOnClickListener(v -> loadFeatureOrderDetails(orderId));
                        String status = firstOrderString(o, "status");
                        String total = firstOrderString(o, "total_list", "total", "amount", "sum");
                        root.addView(text((status.isEmpty() ? "" : "סטטוס: " + status + " · ") + (total.isEmpty() ? "" : "סכום: " + total + " ₪"), 13, false));
                    }
                });
            } catch (Exception e) {
                String message = msg(e, "טעינת ההזמנות נכשלה.");
                runOnUiThread(() -> { if (!isStale(gen) && statusView != null) statusView.setText(message); });
            }
        });
    }

    private JSONArray orderItemsFrom(JSONObject order) {
        if (order == null) return new JSONArray();
        // The documented endpoint returns { "order": { "products": [...] }, "sale": {...} }.
        JSONArray products = order.optJSONArray("products");
        if (products != null) return products;
        String[] keys = {"items", "order_items", "orderItems", "details", "lines", "order_details", "orderDetails", "order_lines", "orderLines"};
        for (String key : keys) { JSONArray a = order.optJSONArray(key); if (a != null) return a; }
        String[] nestedKeys = {"order", "data", "result", "order_detail", "orderDetail"};
        for (String key : nestedKeys) {
            JSONObject nested = order.optJSONObject(key);
            if (nested != null && nested != order) {
                JSONArray a = orderItemsFrom(nested);
                if (a.length() > 0) return a;
            }
        }
        return new JSONArray();
    }

    private JSONObject orderProductFrom(JSONObject item) {
        if (item == null) return new JSONObject();
        JSONObject product = item.optJSONObject("product");
        if (product != null) return product;
        String[] keys = {"item", "sale_item", "saleItem", "product_details", "productDetails"};
        for (String key : keys) {
            JSONObject p = item.optJSONObject(key);
            if (p != null) return p;
        }
        return item;
    }

    private String firstOrderString(JSONObject obj, String... keys) {
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            String s = String.valueOf(value).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
        }
        return "";
    }

    private String orderPrice(JSONObject item, JSONObject product, String quantity) {
        // For /api/orders/{id}, each order.products entry contains its own price.
        String price = firstOrderString(item, "price", "unit_price", "unitPrice", "item_price", "itemPrice", "price_list", "priceList", "total_price", "totalPrice", "total_list", "totalList", "subtotal", "sum", "amount_price", "amountPrice");
        if (price.isEmpty()) price = firstOrderString(product, "price", "unit_price", "unitPrice", "item_price", "itemPrice", "price_list", "priceList", "total_price", "totalPrice", "total_list", "totalList");
        if (price.isEmpty()) return "—";
        if (price.matches(".*[₪$€].*")) return price;
        return price + " ₪";
    }

    private void addOrderItemRow(JSONObject item, int index) {
        JSONObject product = orderProductFrom(item);
        String name = firstOrderString(product, "name", "full_name", "fullName", "item_name", "product_name", "title", "custom_name");
        if (name.isEmpty()) name = firstOrderString(item, "name", "full_name", "fullName", "item_name", "product_name", "title", "custom_name");
        if (name.isEmpty()) name = "פריט " + (index + 1);
        String quantity = firstOrderString(item, "amount", "quantity", "qty", "count");
        if (quantity.isEmpty()) quantity = firstOrderString(product, "amount", "quantity", "qty", "count");
        if (quantity.isEmpty()) quantity = "—";
        String price = orderPrice(item, product, quantity);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(238, 220, 205));
        card.setBackground(bg);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(8);
        root.addView(card, cardParams);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(250, 239, 229));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        imageParams.setMargins(dp(4), 0, dp(10), 0);
        card.addView(image, imageParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        TextView productName = text(name, 15, true);
        productName.setPadding(0, dp(2), 0, dp(5));
        details.addView(productName);
        TextView qtyView = text("כמות: " + quantity, 13, false);
        qtyView.setPadding(0, dp(2), 0, dp(2));
        details.addView(qtyView);
        TextView priceView = text("מחיר: " + price, 13, true);
        priceView.setTextColor(Color.rgb(191, 70, 12));
        priceView.setPadding(0, dp(2), 0, dp(2));
        details.addView(priceView);

        loadImage(image, orderImageUrl(item, product), dp(80));
    }

    private void renderFeatureOrderDetails(long id, String body) {
        beginScreen("פרטי הזמנה #" + id);
        Button back = button("חזרה להיסטוריה");
        root.addView(back);
        back.setOnClickListener(v -> loadFeatureOrders());
        try {
            JSONObject response = new JSONObject(body);
            JSONObject order = response.optJSONObject("order");
            if (order == null) order = response.optJSONObject("data");
            if (order == null) order = response;
            JSONObject sale = response.optJSONObject("sale");
            if (sale == null) sale = order.optJSONObject("sale");
            String saleName = firstOrderString(sale == null ? new JSONObject() : sale, "custom_name");
            JSONObject parent = sale == null ? null : sale.optJSONObject("parent");
            if (saleName.isEmpty() && parent != null) saleName = firstOrderString(parent, "week");
            if (!saleName.isEmpty()) root.addView(text("שם המכירה: " + saleName, 17, true));
            TextView heading = text("פרטי ההזמנה", 18, true);
            root.addView(heading);
            String status = firstOrderString(order, "status", "order_status");
            String total = firstOrderString(order, "total_list", "total", "total_price", "totalPrice", "amount", "sum");
            if (!status.isEmpty()) root.addView(text("סטטוס: " + status, 14, false));
            if (!total.isEmpty()) root.addView(text("סכום הזמנה: " + total + " ₪", 15, true));

            JSONArray items = orderItemsFrom(order);
            if (items.length() == 0) {
                root.addView(text("לא נמצאו פריטים בהזמנה בתשובת השרת.", 14, false));
                return;
            }
            root.addView(text("פריטים בהזמנה", 17, true));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) addOrderItemRow(item, i);
            }
        } catch (Exception e) {
            statusView.setText("לא ניתן להציג את פרטי ההזמנה.");
        }
    }

    private void loadFeatureOrderDetails(long id) {
        final int gen = screenGen.get();
        executor.execute(() -> {
            try {
                HttpResult r = authRequest("GET", "/api/orders/" + id, null);
                if (r.code < 200 || r.code >= 300) throw new Exception("שגיאת שרת (" + r.code + ")");
                String body = r.body;
                runOnUiThread(() -> { if (!isStale(gen)) renderFeatureOrderDetails(id, body); });
            } catch (Exception e) {
                String message = msg(e, "טעינת ההזמנה נכשלה.");
                runOnUiThread(() -> { if (!isStale(gen) && statusView != null) statusView.setText(message); });
            }
        });
    }

    // ---------------------------------------------------------------- credits / charges

    private void loadFeatureTransactions() {
        beginScreen("זיכויים וחיובים");
        final int gen = screenGen.get();
        statusView.setText("טוען את כל התנועות…");
        executor.execute(() -> {
            try {
                java.util.LinkedHashMap<String, JSONObject> unique = new java.util.LinkedHashMap<>();
                int page = 1, lastPage = 1;
                while (page <= Math.min(lastPage, 60)) {
                    if (isStale(gen)) return; // user left the screen - stop downloading pages
                    HttpResult response = authRequest("GET", "/api/profile/transactions?page=" + page, null);
                    if (response.code < 200 || response.code >= 300)
                        throw new Exception("טעינת הזיכויים נכשלה (עמוד " + page + ", שגיאה " + response.code + ").");
                    JSONObject payload = new JSONObject(response.body);
                    JSONObject transactions = payload.optJSONObject("transactions");
                    if (transactions == null) transactions = new JSONObject();
                    if (page == 1) {
                        addTransactionList(unique, payload.opt("open_transactions"));
                        addTransactionList(unique, payload.opt("kupot_transactions"));
                        addTransactionList(unique, payload.opt("kupot_open_transactions"));
                    }
                    JSONArray data = transactions.optJSONArray("data");
                    if (data == null) data = payload.optJSONArray("transactions");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject tx = data.optJSONObject(i);
                            if (tx != null) unique.putIfAbsent(tx.optString("id", "page" + page + "_" + i), tx);
                        }
                    }
                    lastPage = Math.max(1, transactions.optInt("last_page", 1));
                    page++;
                }
                java.util.ArrayList<JSONObject> rows = new java.util.ArrayList<>(unique.values());
                rows.sort((a, b) -> b.optString("time", "").compareTo(a.optString("time", "")));
                final java.util.ArrayList<JSONObject> finalRows = rows;
                runOnUiThread(() -> {
                    if (isStale(gen)) return;
                    beginScreen("זיכויים וחיובים");
                    TextView summary = text("", 15, true);
                    root.addView(summary);
                    LinearLayout filters = new LinearLayout(this);
                    filters.setOrientation(LinearLayout.HORIZONTAL);
                    filters.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams filtersParams = new LinearLayout.LayoutParams(-1, -2);
                    filtersParams.bottomMargin = dp(10);
                    root.addView(filters, filtersParams);
                    Button all = button("הכול");
                    Button credits = button("זיכויים");
                    Button charges = button("חיובים");
                    for (Button filter : new Button[]{all, credits, charges}) {
                        filter.setTextSize(12);
                        filter.setMinHeight(dp(44));
                        filter.setPadding(dp(2), dp(2), dp(2), dp(2));
                        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(0, -2, 1);
                        filterParams.setMargins(dp(2), 0, dp(2), 0);
                        filters.addView(filter, filterParams);
                    }
                    LinearLayout list = new LinearLayout(this);
                    list.setOrientation(LinearLayout.VERTICAL);
                    root.addView(list, new LinearLayout.LayoutParams(-1, -2));
                    Runnable[] render = new Runnable[1];
                    final String[] mode = {"all"};
                    render[0] = () -> {
                        list.removeAllViews();
                        double creditTotal = 0, chargeTotal = 0;
                        int creditCount = 0, chargeCount = 0;
                        for (JSONObject tx : finalRows) {
                            double amount = tx.optDouble("skum", 0);
                            boolean canceled = tx.optBoolean("canceled", false);
                            if (!canceled && amount < 0) { creditTotal += -amount; creditCount++; }
                            if (!canceled && amount > 0) { chargeTotal += amount; chargeCount++; }
                            if ("credit".equals(mode[0]) && amount >= 0) continue;
                            if ("charge".equals(mode[0]) && amount <= 0) continue;
                            JSONObject product = tx.optJSONObject("product");
                            if (product == null) product = new JSONObject();
                            String name = product.optString("full_name", product.optString("name", ""));
                            String note = android.text.Html.fromHtml(tx.optString("note", ""), android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                            JSONObject sale = firstTransactionObject(tx, "sale", "sale_data", "saleData");
                            JSONObject original = firstTransactionObject(tx, "original_sale", "originalSale", "original_sale_data", "originalSaleData");
                            String saleName = transactionSaleName(tx, sale, false);
                            String originalName = transactionSaleName(tx, original, true);
                            String kind = amount < 0 ? "💚 זיכוי" : (amount > 0 ? "🔴 חיוב" : "ללא סכום");
                            String date = tx.optString("time", "");
                            String description = name.isEmpty() ? note : name;
                            if (description.isEmpty()) description = originalName.isEmpty() ? saleName : originalName;
                            LinearLayout card = new LinearLayout(this);
                            card.setOrientation(LinearLayout.VERTICAL);
                            card.setPadding(dp(12), dp(8), dp(12), dp(8));
                            card.setBackgroundColor(Color.WHITE);
                            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                            cp.bottomMargin = dp(8);
                            list.addView(card, cp);
                            TextView heading = text(kind + ": " + String.format(java.util.Locale.US, "%.2f", Math.abs(amount)) + " ₪", 16, true);
                            heading.setTextColor(amount < 0 ? Color.rgb(20, 130, 70) : Color.rgb(175, 45, 45));
                            card.addView(heading);
                            if (!description.isEmpty()) card.addView(text("פריט / הערה: " + description, 14, false));
                            if (tx.has("amount")) card.addView(text("כמות: " + tx.optString("amount"), 13, false));
                            if (!saleName.isEmpty()) card.addView(text("מכירה: " + saleName, 13, false));
                            if (!originalName.isEmpty()) card.addView(text("מכירה מקורית: " + originalName, 13, false));
                            if (!date.isEmpty()) card.addView(text("תאריך: " + date, 12, false));
                            if (canceled) card.addView(text("בוטל — לא נספר בסיכום", 12, true));
                        }
                        summary.setText("זיכויים: " + String.format(java.util.Locale.US, "%.2f", creditTotal) + " ₪ (" + creditCount + ")"
                                + "\nחיובים: " + String.format(java.util.Locale.US, "%.2f", chargeTotal) + " ₪ (" + chargeCount + ")"
                                + "\nיתרה: " + String.format(java.util.Locale.US, "%.2f", creditTotal - chargeTotal) + " ₪"
                                + "\nסה״כ תנועות: " + finalRows.size());
                        if (finalRows.isEmpty()) statusView.setText("לא נמצאו תנועות בחשבון.");
                        else statusView.setText("");
                    };
                    all.setOnClickListener(v -> { mode[0] = "all"; render[0].run(); });
                    credits.setOnClickListener(v -> { mode[0] = "credit"; render[0].run(); });
                    charges.setOnClickListener(v -> { mode[0] = "charge"; render[0].run(); });
                    render[0].run();
                });
            } catch (Exception e) {
                String message = msg(e, "טעינת הזיכויים נכשלה.");
                runOnUiThread(() -> { if (!isStale(gen) && statusView != null) statusView.setText(message); });
            }
        });
    }

    private JSONObject firstTransactionObject(JSONObject tx, String... keys) {
        for (String key : keys) {
            JSONObject value = tx.optJSONObject(key);
            if (value != null) return value;
        }
        return null;
    }

    private String transactionSaleName(JSONObject tx, JSONObject sale, boolean original) {
        String[] directKeys = original
                ? new String[]{"original_sale_name", "originalSaleName", "original_sale_custom_name", "originalSaleCustomName"}
                : new String[]{"sale_name", "saleName", "sale_custom_name", "saleCustomName"};
        String name = firstOrderString(tx, directKeys);
        if (!name.isEmpty()) return name;
        if (sale != null) {
            name = firstOrderString(sale, "custom_name", "name", "sale_name", "title", "week", "label");
            if (name.isEmpty()) {
                JSONObject parent = firstTransactionObject(sale, "parent", "sale_parent");
                if (parent != null) name = firstOrderString(parent, "week", "name", "title", "custom_name");
            }
            if (name.isEmpty()) {
                JSONObject nested = firstTransactionObject(sale, "sale", "original_sale", "originalSale");
                if (nested != null && nested != sale) name = transactionSaleName(tx, nested, original);
            }
            if (!name.isEmpty()) return name;
        }
        String id = original
                ? firstOrderString(tx, "original_sale_id", "originalSaleId", "original_saleID")
                : firstOrderString(tx, "sale_id", "saleId", "saleID");
        if (id.isEmpty() && sale != null) id = firstOrderString(sale, "id", "sale_id", "saleID");
        if (!id.isEmpty()) return "מכירה #" + id;
        return "";
    }

    private void addTransactionList(java.util.Map<String, JSONObject> target, Object value) {
        JSONArray array = null;
        if (value instanceof JSONArray) array = (JSONArray) value;
        else if (value instanceof JSONObject) array = ((JSONObject) value).optJSONArray("data");
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject tx = array.optJSONObject(i);
            if (tx != null) target.putIfAbsent(tx.optString("id", "extra_" + target.size()), tx);
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (imm != null && focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    private static class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body; }
    }
}

