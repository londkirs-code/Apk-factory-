package org.mishnatyosef.mobile;

import android.app.Activity;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
        getWindow().setStatusBarColor(Color.rgb(0, 105, 92));
        getWindow().setNavigationBarColor(Color.rgb(0, 77, 64));
        if (hasToken()) showDashboard(); else showLogin();
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
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0, 121, 107)));
        return b;
    }

    private void beginScreen(String heading) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(18));
        root.setBackgroundColor(Color.rgb(247, 250, 249));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        TextView title = text("משנת יוסף", 28, true);
        title.setTextColor(Color.rgb(0, 105, 92));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = text(heading, 16, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(24));
        root.addView(sub, new LinearLayout.LayoutParams(-1, -2));
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
        root.addView(loginButton, bp);
        statusView = text("", 14, false);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
        loginButton.setOnClickListener(v -> login());
    }

    private void showDashboard() {
        beginScreen("מחובר לחשבון שלך");
        TextView account = text("חשבון: " + prefs.getString("email", ""), 14, false);
        root.addView(account, new LinearLayout.LayoutParams(-1, -2));
        statusView = text("טוען את המכירות הפתוחות…", 15, false);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
        addFeatureMenu("📦 היסטוריית הזמנות", this::loadFeatureOrders);
        addFeatureMenu("💳 זיכויים ותנועות בחשבון", this::loadFeatureTransactions);
        addFeatureMenu("🛒 הזמנה חדשה", this::loadFeatureSales);
        Button refresh = button("רענון מכירות פתוחות");
        root.addView(refresh, new LinearLayout.LayoutParams(-1, -2));
        refresh.setOnClickListener(v -> loadOpenSales());
        Button logout = button("התנתקות");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        root.addView(logout, lp);
        logout.setOnClickListener(v -> {
            prefs.edit().remove("token").remove("expires_at").apply();
            showLogin();
        });
        loadOpenSales();
    }

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
                    showDashboard();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "ההתחברות נכשלה." : e.getMessage();
                runOnUiThread(() -> setBusy(false, message));
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

    private void addFeatureMenu(String label, Runnable action) { Button b=button(label); root.addView(b,new LinearLayout.LayoutParams(-1,-2)); b.setOnClickListener(v->action.run()); }
    private void loadFeatureSales() { showFeatureMessage("הזמנה חדשה", "ממשק ההזמנה עדיין בבנייה."); }
    private void loadFeatureOrders() { showFeatureMessage("היסטוריית הזמנות", "טעינת היסטוריית ההזמנות עדיין בבנייה."); }
    private void loadFeatureTransactions() { showFeatureMessage("זיכויים", "טעינת הזיכויים עדיין בבנייה."); }
    private void showFeatureMessage(String title, String message) { beginScreen(title); statusView.setText(message); Button b=button("חזרה לתפריט"); root.addView(b,new LinearLayout.LayoutParams(-1,-2)); b.setOnClickListener(v->showDashboard()); }
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
