package org.mishnatyosef.mobile;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.ArrayAdapter;
import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Build verification checkpoint: keep this source in the APK build trigger.
public class MainActivity extends Activity {
    private static final String API = "https://v2.mishnatyosef.org";
    private static final String PREFS = "mishnat_auth";
    private static final long REFRESH_MARGIN_MS = 300000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
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

    private void beginScreen(String heading) {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(255, 249, 244));
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(255, 249, 244));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
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

        if (hasToken()) {
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setGravity(Gravity.CENTER);
            nav.setBackgroundColor(Color.WHITE);
            nav.setElevation(dp(8));
            String[] labels = {"הזמנות פתוחות", "זיכויים", "היסטוריה", "ראשי"};
            int[] icons = {android.R.drawable.ic_menu_view, android.R.drawable.ic_menu_recent_history, android.R.drawable.ic_menu_info_details, android.R.drawable.ic_menu_agenda};
            Runnable[] actions = {this::showOpenSales, this::loadFeatureTransactions, this::loadFeatureOrders, this::showDashboard};
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
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(-1, -2); refreshLp.topMargin = dp(8); refreshLp.bottomMargin = dp(12); root.addView(refresh, refreshLp);
        refresh.setOnClickListener(v -> loadOpenSales());
        Button logout = button("התנתקות");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(12);
        root.addView(logout, lp);
        logout.setOnClickListener(v -> {
            prefs.edit().remove("token").remove("expires_at").apply();
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
        card.setPadding(dp(10), dp(5), dp(10), dp(5));
        card.setBackgroundColor(Color.rgb(255, 248, 241));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(3); cardLp.bottomMargin = dp(3);
        root.addView(card, cardLp);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = text("התראה על מכירה חדשה", 15, true);
        heading.setTextColor(Color.rgb(230, 94, 20));
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setText("");
        toggle.setTextSize(12);
        toggle.setChecked(prefs.getBoolean("sale_monitor_enabled", true));
        top.addView(toggle, new LinearLayout.LayoutParams(-2, dp(36)));
        toggle.setOnCheckedChangeListener((button, on) -> {
            prefs.edit().putBoolean("sale_monitor_enabled", on).apply();
            if (on) { requestMonitoringPermissions(false); SaleMonitor.scheduleNextStart(this); }
            else SaleMonitor.stopMonitoring(this);
        });
        String[] days = {"א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"};
        int[] calendarDays = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, days);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(times, new LinearLayout.LayoutParams(-1, dp(36)));
        times.addView(text("מ־", 12, false), new LinearLayout.LayoutParams(-2, -2));
        Spinner startDay = new Spinner(this);
        startDay.setAdapter(adapter);
        startDay.setSelection(indexOf(calendarDays, prefs.getInt("monitor_start_day", Calendar.THURSDAY)));
        times.addView(startDay, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView startTime = text(prefs.getString("monitor_start_time", "19:00"), 13, true);
        startTime.setGravity(Gravity.CENTER);
        startTime.setPadding(dp(3), 0, dp(3), 0);
        startTime.setBackground(timePickerBackground());
        times.addView(startTime, new LinearLayout.LayoutParams(dp(62), dp(36)));
        startTime.setOnClickListener(v -> {
            String[] parts = startTime.getText().toString().split(":");
            int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]);
            new TimePickerDialog(this, (picker, h, m) -> startTime.setText(String.format(java.util.Locale.US, "%02d:%02d", h, m)), hour, minute, true).show();
        });
        LinearLayout.LayoutParams untilLp = new LinearLayout.LayoutParams(-2, -2);
        untilLp.leftMargin = dp(3);
        times.addView(text("עד", 12, false), untilLp);
        Spinner endDay = new Spinner(this);
        endDay.setAdapter(adapter);
        endDay.setSelection(indexOf(calendarDays, prefs.getInt("monitor_end_day", Calendar.SUNDAY)));
        times.addView(endDay, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView endTime = text(prefs.getString("monitor_end_time", "10:00"), 13, true);
        endTime.setGravity(Gravity.CENTER);
        endTime.setPadding(dp(3), 0, dp(3), 0);
        endTime.setBackground(timePickerBackground());
        times.addView(endTime, new LinearLayout.LayoutParams(dp(62), dp(36)));
        endTime.setOnClickListener(v -> {
            String[] parts = endTime.getText().toString().split(":");
            int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]);
            new TimePickerDialog(this, (picker, h, m) -> endTime.setText(String.format(java.util.Locale.US, "%02d:%02d", h, m)), hour, minute, true).show();
        });
        Button save = button("שמירה");
        save.setTextSize(12); save.setMinHeight(dp(30)); save.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-2, dp(32));
        saveLp.gravity = Gravity.END;
        card.addView(save, saveLp);
        save.setOnClickListener(v -> {
            String st = startTime.getText().toString().trim(), et = endTime.getText().toString().trim();
            if (!validTime(st) || !validTime(et)) { Toast.makeText(this, "יש להזין שעה בפורמט 19:00", Toast.LENGTH_LONG).show(); return; }
            prefs.edit().putInt("monitor_start_day", calendarDays[startDay.getSelectedItemPosition()])
                    .putInt("monitor_end_day", calendarDays[endDay.getSelectedItemPosition()])
                    .putString("monitor_start_time", st).putString("monitor_end_time", et).apply();
            SaleMonitor.stopMonitoring(this); SaleMonitor.scheduleNextStart(this);
            Toast.makeText(this, "הגדרות ההתראה נשמרו", Toast.LENGTH_SHORT).show();
        });
    }

    private android.graphics.drawable.GradientDrawable timePickerBackground() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), Color.rgb(230, 94, 20));
        return bg;
    }

    private int indexOf(int[] values, int target) { for (int i = 0; i < values.length; i++) if (values[i] == target) return i; return 0; }
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
                String message = e.getMessage() == null ? "ההתחברות נכשלה." : e.getMessage();
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
        statusView.setText("טוען את המכירה מההתראה…");
        executor.execute(() -> {
            try {
                String token = prefs.getString("token", "");
                HttpResult r = request("GET", "/api/profile/open_sales", token, null);
                if (r.code == 401) {
                    String refreshed = refreshToken(token);
                    if (!refreshed.isEmpty()) r = request("GET", "/api/profile/open_sales", refreshed, null);
                }
                if (r.code < 200 || r.code >= 300) throw new Exception("לא ניתן לטעון את המכירה. פתחי את המכירות הפתוחות ורענני.");
                JSONArray sales;
                if (r.body.trim().startsWith("[")) sales = new JSONArray(r.body);
                else sales = new JSONObject(r.body).optJSONArray("data");
                JSONObject found = null;
                if (sales != null) {
                    for (int i = 0; i < sales.length(); i++) {
                        JSONObject sale = sales.optJSONObject(i);
                        if (sale != null && sale.optInt("id", -1) == saleId) { found = sale; break; }
                    }
                }
                final JSONObject selected = found;
                runOnUiThread(() -> {
                    if (selected != null) loadFeatureProducts(selected);
                    else {
                        showOpenSales();
                        Toast.makeText(this, "המכירה כבר לא פתוחה. מציג את המכירות העדכניות.", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showOpenSales();
                    Toast.makeText(this, e.getMessage() == null ? "טעינת המכירה נכשלה." : e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadOpenSales() {
        if (busy) return;
        setBusy(true, "טוען מכירות פתוחות…");
        executor.execute(() -> {
            try {
                String token = prefs.getString("token", "");
                long expiry = prefs.getLong("expires_at", 0L);
                if (!token.isEmpty() && expiry > 0 && System.currentTimeMillis() > expiry - REFRESH_MARGIN_MS) {
                    String refreshed = refreshToken(token);
                    if (!refreshed.isEmpty()) token = refreshed;
                }
                HttpResult r = request("GET", "/api/profile/open_sales", token, null);
                if (r.code == 401) {
                    String refreshed = refreshToken(token);
                    if (!refreshed.isEmpty()) r = request("GET", "/api/profile/open_sales", refreshed, null);
                }
                if (r.code == 401) {
                    prefs.edit().remove("token").remove("expires_at").apply();
                    runOnUiThread(() -> { busy = false; showLogin(); Toast.makeText(this, "נדרשת התחברות מחדש", Toast.LENGTH_LONG).show(); });
                    return;
                }
                if (r.code < 200 || r.code >= 300) throw new Exception("טעינת המכירות נכשלה (" + r.code + ").");
                JSONArray sales;
                if (r.body.trim().startsWith("[")) {
                    sales = new JSONArray(r.body);
                } else {
                    JSONObject obj = new JSONObject(r.body);
                    sales = obj.optJSONArray("data");
                    if (sales == null) sales = new JSONArray();
                }
                StringBuilder out = new StringBuilder("מכירות פתוחות: ").append(sales.length()).append("\n\n");
                for (int i = 0; i < sales.length(); i++) {
                    JSONObject sale = sales.optJSONObject(i);
                    if (sale == null) continue;
                    String name = sale.optString("custom_name", "");
                    if (name.isEmpty()) {
                        JSONObject parent = sale.optJSONObject("parent");
                        if (parent != null) name = parent.optString("week", "");
                    }
                    if (name.isEmpty()) name = sale.optString("name", "מכירה " + (i + 1));
                    out.append("• ").append(name);
                    String status = sale.optString("status", "");
                    if (!status.isEmpty()) out.append(" — ").append(status);
                    out.append("\n");
                }
                String finalText = out.toString();
                runOnUiThread(() -> { busy = false; statusView.setText(finalText); });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "אירעה שגיאה בטעינה." : e.getMessage();
                runOnUiThread(() -> { busy = false; statusView.setText(message); });
            }
        });
    }

    private String refreshToken(String token) {
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
        c.disconnect();
        return new HttpResult(code, response.toString());
    }

    private void addFeatureMenu(String label, Runnable action) { Button b=button(label); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(12); root.addView(b,lp); b.setOnClickListener(v->action.run()); }
    private JSONArray featureProducts=new JSONArray();
    private JSONObject featureSale=null;
    private final java.util.Map<Integer,Integer> featureQuantities=new java.util.LinkedHashMap<>();
    private void loadFeatureSales() {
        beginScreen("בחירת מכירה להזמנה");
        statusView.setText("טוען מכירות פתוחות…");
        executor.execute(() -> {
            try {
                HttpResult r=request("GET","/api/profile/open_sales",prefs.getString("token",""),null);
                if(r.code<200||r.code>=300) throw new Exception("שגיאת שרת ("+r.code+")");
                JSONArray a=new JSONArray();Object value=new org.json.JSONTokener(r.body).nextValue();
                if(value instanceof JSONArray)a=(JSONArray)value;else if(value instanceof JSONObject)a=((JSONObject)value).optJSONArray("data");
                final JSONArray rows=a==null?new JSONArray():a;
                runOnUiThread(() -> {
                    beginScreen("בחירת מכירה להזמנה");

                    if(rows.length()==0)statusView.setText("לא נמצאו מכירות פתוחות.");
                    for(int i=0;i<rows.length();i++){JSONObject sale=rows.optJSONObject(i);if(sale==null)continue;
                        String name=sale.optString("custom_name",sale.optString("name","מכירה"));
                        JSONObject parent=sale.optJSONObject("parent");if(name.isEmpty()&&parent!=null)name=parent.optString("week","מכירה");
                        final JSONObject selected=sale;Button b=button(name);root.addView(b);b.setOnClickListener(v->loadFeatureProducts(selected));
                    }
                });
            } catch(Exception e){runOnUiThread(()->statusView.setText(e.getMessage()));}
        });
    }
    private int featureProductId(JSONObject p){return p.optInt("item_salesID",p.optInt("id",p.optInt("itemID",-1)));}
    private void loadFeatureProducts(JSONObject sale) {
        featureSale=sale;int saleId=sale.optInt("id",-1);
        executor.execute(() -> {
            try {
                JSONArray all=new JSONArray();
                for(int page=1;page<=100;page++){
                    HttpResult r=request("GET","/api/get_items_for_sale/"+saleId+"/null?page="+page+"&limit=50",prefs.getString("token",""),null);
                    if(r.code<200||r.code>=300)throw new Exception("שגיאת שרת ("+r.code+")");
                    JSONArray a=new JSONArray();Object val=new org.json.JSONTokener(r.body).nextValue();
                    if(val instanceof JSONArray)a=(JSONArray)val;else if(val instanceof JSONObject)a=((JSONObject)val).optJSONArray("data");
                    if(a==null)a=new JSONArray();for(int i=0;i<a.length();i++)all.put(a.get(i));if(a.length()<50)break;
                }
                featureProducts=all;featureQuantities.clear();for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null)featureQuantities.put(featureProductId(p),0);}
                runOnUiThread(this::renderFeatureProducts);
            }catch(Exception e){runOnUiThread(()->statusView.setText(e.getMessage()));}
        });
    }
    private void renderFeatureProducts() {
        String title=featureSale==null?"מוצרים":featureSale.optString("custom_name",featureSale.optString("name","מוצרים"));
        beginScreen(title);Button back=button("חזרה למכירות");root.addView(back);back.setOnClickListener(v->loadFeatureSales());
        for(int i=0;i<featureProducts.length();i++){
            JSONObject p=featureProducts.optJSONObject(i);if(p==null)continue;int id=featureProductId(p);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);root.addView(row);
            row.addView(text(p.optString("name","מוצר")+" · "+p.optString("price","")+" ₪",14,true),new LinearLayout.LayoutParams(0,-2,1));
            TextView qty=text("0",16,true);row.addView(qty);Button minus=button("−"),plus=button("+");row.addView(minus);row.addView(plus);
            minus.setOnClickListener(v->{int n=Math.max(0,featureQuantities.getOrDefault(id,0)-1);featureQuantities.put(id,n);qty.setText(""+n);});
            plus.setOnClickListener(v->{int n=featureQuantities.getOrDefault(id,0)+1;int max=p.optInt("max_per_man",0);if(max>0&&n>max){Toast.makeText(this,"הגעת למגבלת הכמות",Toast.LENGTH_SHORT).show();return;}featureQuantities.put(id,n);qty.setText(""+n);});
        }
        Button next=button("המשך לאמצעי תשלום");root.addView(next);next.setOnClickListener(v->loadFeatureCards());
    }
    private JSONArray featureCards=new JSONArray();
    private void loadFeatureCards() {
        executor.execute(() -> {
            try {
                HttpResult r=request("GET","/api/payment_methods/cards",prefs.getString("token",""),null);
                if(r.code<200||r.code>=300)throw new Exception("שגיאת שרת ("+r.code+")");
                Object value=new org.json.JSONTokener(r.body).nextValue();JSONArray a=new JSONArray();
                if(value instanceof JSONArray)a=(JSONArray)value;else if(value instanceof JSONObject)a=((JSONObject)value).optJSONArray("data");
                featureCards=a==null?new JSONArray():a;
                runOnUiThread(this::renderFeatureCards);
            }catch(Exception e){runOnUiThread(()->statusView.setText(e.getMessage()));}
        });
    }
    private void renderFeatureCards() {
        beginScreen("בחירת אמצעי תשלום");RadioGroup group=new RadioGroup(this);group.setOrientation(RadioGroup.VERTICAL);root.addView(group);
        for(int i=0;i<featureCards.length();i++){JSONObject card=featureCards.optJSONObject(i);if(card==null)continue;
            int id=card.optInt("id",card.optInt("card_id",-1));if(id<0)continue;
            RadioButton rb=new RadioButton(this);rb.setText(card.optString("brand","כרטיס")+" •••• "+card.optString("last4",card.optString("last_four","")));
            rb.setTag(id);group.addView(rb);if(card.optBoolean("default",false)||card.optBoolean("is_default",false))rb.setChecked(true);
        }
        Button create=button("אישור ויצירת הזמנה");root.addView(create);create.setOnClickListener(v->{
            RadioButton selected=group.findViewById(group.getCheckedRadioButtonId());
            if(selected==null){statusView.setText("בחר אמצעי תשלום.");return;}
            int count=0;for(Integer q:featureQuantities.values())count+=q;
            if(count==0){statusView.setText("בחר לפחות מוצר אחד.");return;}
            int cardId=(Integer)selected.getTag();
            new android.app.AlertDialog.Builder(this).setTitle("אישור הזמנה")
                .setMessage("ליצור הזמנה עם "+count+" יחידות?")
                .setNegativeButton("ביטול",null).setPositiveButton("יצירה",(d,w)->createFeatureOrder(cardId)).show();
        });
    }
    private void createFeatureOrder(int cardId) {
        executor.execute(() -> {
            try {
                int saleId=featureSale.optInt("id",-1);JSONObject body=new JSONObject();body.put("card_id",cardId);
                HttpResult r=request("POST","/api/create_order/"+saleId,prefs.getString("token",""),body);
                if(r.code<200||r.code>=300)throw new Exception("יצירת ההזמנה נכשלה ("+r.code+").");
                JSONObject response=new JSONObject(r.body);JSONObject data=response.optJSONObject("data");if(data!=null)response=data;
                long orderId=response.optLong("id",response.optLong("order_id",-1));
                if(orderId<0)throw new Exception("לא התקבל מזהה הזמנה. בדוק את היסטוריית ההזמנות לפני ניסיון חוזר.");
                for(int i=0;i<featureProducts.length();i++){
                    JSONObject p=featureProducts.optJSONObject(i);if(p==null)continue;
                    int amount=featureQuantities.getOrDefault(featureProductId(p),0);if(amount<=0)continue;
                    JSONObject item=new JSONObject(p.toString());item.put("amount",amount);item.put("selected_subitems",new JSONArray());
                    HttpResult add=request("POST","/api/add_item/"+orderId,prefs.getString("token",""),item);
                    if(add.code<200||add.code>=300)throw new Exception("ההזמנה נוצרה אך הוספת אחד המוצרים נכשלה ("+add.code+"). פתח את ההיסטוריה לבדיקה.");
                }
                runOnUiThread(()->{beginScreen("ההזמנה נוצרה");statusView.setText("מספר הזמנה: "+orderId);Button b=button("היסטוריית הזמנות");root.addView(b);b.setOnClickListener(v->loadFeatureOrders());});
            }catch(Exception e){runOnUiThread(()->statusView.setText(e.getMessage()));}
        });
    }
    private void loadFeatureOrders() {
        beginScreen("היסטוריית הזמנות");
        statusView.setText("טוען הזמנות…");
        executor.execute(() -> {
            try {
                HttpResult r=request("GET","/api/orders",prefs.getString("token",""),null);
                if(r.code<200||r.code>=300) throw new Exception("שגיאת שרת ("+r.code+")");
                JSONArray a=new JSONArray();
                Object value=new org.json.JSONTokener(r.body).nextValue();
                if(value instanceof JSONArray) a=(JSONArray)value;
                else if(value instanceof JSONObject) a=((JSONObject)value).optJSONArray("data");
                final JSONArray orders=a==null?new JSONArray():a;
                runOnUiThread(() -> {
                    beginScreen("היסטוריית הזמנות");

                    if(orders.length()==0) statusView.setText("לא נמצאו הזמנות.");
                    for(int i=0;i<orders.length();i++){
                        JSONObject o=orders.optJSONObject(i);if(o==null)continue;
                        JSONObject sale = o.optJSONObject("sale");
                        if (sale == null) sale = new JSONObject();
                        JSONObject parent = sale.optJSONObject("parent");
                        String saleName = firstOrderString(sale, "custom_name");
                        if (saleName.isEmpty() && parent != null) saleName = firstOrderString(parent, "week");
                        if (saleName.isEmpty()) saleName = "מכירה " + firstOrderString(o, "saleID", "sale_id");
                        String orderIdText = firstOrderString(o, "id");
                        TextView t=text(saleName + "  ·  הזמנה #" + orderIdText + "  ›",16,true);root.addView(t);
                        final long orderId=o.optLong("id",-1);
                        if(orderId>0)t.setOnClickListener(v->loadFeatureOrderDetails(orderId));
                        String status = firstOrderString(o, "status");
                        String total = firstOrderString(o, "total_list", "total", "amount", "sum");
                        root.addView(text((status.isEmpty() ? "" : "סטטוס: " + status + " · ") + (total.isEmpty() ? "" : "סכום: " + total + " ₪"),13,false));
                    }
                });
            } catch(Exception e) { runOnUiThread(() -> statusView.setText(e.getMessage())); }
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

    private String orderImageUrl(JSONObject item, JSONObject product) {
        final String imageBase = "https://images.mishnatyosef.org/images/items/";
        String url = firstOrderString(product, "thumb_featured_image", "thumbFeaturedImage", "featured_image", "featuredImage", "image_url", "imageUrl", "image_path", "imagePath", "picture_url", "pictureUrl", "url");
        if (url.isEmpty()) url = firstOrderString(item, "thumb_featured_image", "thumbFeaturedImage", "featured_image", "featuredImage", "image_url", "imageUrl", "image_path", "imagePath", "picture_url", "pictureUrl");
        // API product images use filename + extension, not necessarily a complete URL.
        JSONArray images = product.optJSONArray("images");
        if (images == null) images = item.optJSONArray("images");
        if (url.isEmpty() && images != null && images.length() > 0) {
            JSONObject img = images.optJSONObject(0);
            if (img != null) {
                String filename = firstOrderString(img, "filename");
                String extension = firstOrderString(img, "extension");
                url = firstOrderString(img, "url", "src", "path", "original", "large", "thumbnail");
                if (url.isEmpty() && !filename.isEmpty()) {
                    String cleanExtension = extension.startsWith(".") ? extension.substring(1) : extension;
                    boolean filenameHasExtension = filename.matches("(?i).*\\\\.[a-z0-9]{2,5}$");
                    url = imageBase + filename + (filenameHasExtension || cleanExtension.isEmpty() ? "" : "." + cleanExtension);
                }
            } else {
                Object first = images.opt(0);
                if (first != null) url = String.valueOf(first);
            }
        }
        if (url.isEmpty()) {
            JSONObject img = product.optJSONObject("image");
            if (img == null) img = item.optJSONObject("image");
            if (img != null) url = firstOrderString(img, "url", "src", "path", "original", "large", "thumbnail");
        }
        if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.startsWith("//")) url = "https:" + url;
            else if (url.startsWith("/")) url = imageBase + url.substring(1);
            else url = imageBase + url;
        }
        return url.startsWith("http://") || url.startsWith("https://") ? url : "";
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

        String imageUrl = orderImageUrl(item, product);
        if (!imageUrl.isEmpty()) {
            final String requestedUrl = imageUrl;
            executor.execute(() -> {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new URL(requestedUrl).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setInstanceFollowRedirects(true);
                    c.connect();
                    InputStream stream = c.getInputStream();
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(stream);
                    stream.close();
                    c.disconnect();
                    if (bitmap != null) runOnUiThread(() -> image.setImageBitmap(bitmap));
                } catch (Exception ignored) { }
            });
        }
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
        executor.execute(() -> {
            try {
                HttpResult r = request("GET", "/api/orders/" + id, prefs.getString("token", ""), null);
                if (r.code < 200 || r.code >= 300) throw new Exception("שגיאת שרת (" + r.code + ")");
                String body = r.body;
                runOnUiThread(() -> renderFeatureOrderDetails(id, body));
            } catch (Exception e) {
                runOnUiThread(() -> statusView.setText(e.getMessage()));
            }
        });
    }
    private void loadFeatureTransactions() {
        beginScreen("זיכויים וחיובים");
        statusView.setText("טוען את כל התנועות…");
        executor.execute(() -> {
            try {
                final String token = prefs.getString("token", "");
                java.util.LinkedHashMap<String, JSONObject> unique = new java.util.LinkedHashMap<>();
                String nextPath = "/api/profile/transactions?page=1";
                int page = 1, lastPage = 1;
                while (page <= Math.min(lastPage, 60)) {
                    HttpResult response = request("GET", nextPath, token, null);
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
                    nextPath = "/api/profile/transactions?page=" + page;
                }
                java.util.ArrayList<JSONObject> rows = new java.util.ArrayList<>(unique.values());
                rows.sort((a,b) -> b.optString("time", "").compareTo(a.optString("time", "")));
                final java.util.ArrayList<JSONObject> finalRows = rows;
                runOnUiThread(() -> {
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
                            heading.setTextColor(amount < 0 ? Color.rgb(20,130,70) : Color.rgb(175,45,45));
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
                            + "\nיתרה: " + String.format(java.util.Locale.US, "%.2f", creditTotal-chargeTotal) + " ₪"
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
                String message = e.getMessage() == null ? "טעינת הזיכויים נכשלה." : e.getMessage();
                runOnUiThread(() -> statusView.setText(message));
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

    private void showFeatureMessage(String title, String message) { beginScreen(title); statusView.setText(message); }
    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (imm != null && focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private static class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body; }
    }
}
