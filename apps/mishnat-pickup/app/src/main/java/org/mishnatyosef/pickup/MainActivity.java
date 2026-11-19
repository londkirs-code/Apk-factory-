package org.mishnatyosef.pickup;

import android.app.Activity;
import android.app.Dialog;
import android.webkit.WebStorage;
import android.graphics.drawable.StateListDrawable;
import android.view.KeyEvent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * A focused pickup checklist for the latest Mishnat Yosef order.
 * Authentication is performed by Mishnat Yosef's own web sign-in page. The app never
 * receives or stores the user's password; only the site's access token is encrypted locally.
 */
public class MainActivity extends Activity {
    private static final String API = "https://v2.mishnatyosef.org";
    private static final String LOGIN_URL = "https://mishnatyosef.org/login";
    private static final String PREFS = "mishnat_pickup_preferences";
    private static final String TOKEN_KEY = "encrypted_access_token";
    private static final String PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY = "allow_offline_cache_after_auth_expiry";
    private static final String KEY_ALIAS = "mishnat_pickup_auth_key_v1";
    // כל צבעי הממשק מרוכזים ב-Ui.java.

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService imageLoader = Executors.newFixedThreadPool(3);
    // Offline image downloads must never block displaying the order.
    private final ExecutorService cacheWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private SharedPreferences preferences;
    private volatile String accessToken = "";
    private String activeOwner = "";
    private boolean cacheTrusted = false;
    private Button retryButton;
    private JSONObject orderData;
    private String currentOrderId = "";
    private final List<ProductRow> products = new ArrayList<>();
    private final Set<String> collected = new HashSet<>();
    private ScrollView pageScroll;
    private LinearLayout page;
    private LinearLayout pendingList;
    private LinearLayout collectedList;
    private LinearLayout collectedSection;
    private TextView progressText;
    private TextView progressPercent;
    private ProgressBar progressBar;
    private Button resetButton;
    private Button logoutButton;
    private final java.util.Map<String, ProductCard> productCards = new java.util.LinkedHashMap<>();
    private TextView messageText;
    private WebView loginWebView;
    private TextView loginTermsNotice;
    private boolean authHandled = false;
    private boolean loading = false;
    private volatile int authGeneration = 0;
    private long lastSyncAttemptAt = 0;
    private boolean wasComplete;
    private View celebration;
    private ImageViewerOverlay imageViewer;
    private final Runnable dismissCelebrationTask = this::dismissCelebration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        imageViewer = new ImageViewerOverlay(this, imageLoader);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureSystemBars();
        accessToken = readEncryptedToken();
        initializeCacheOwnership();
        if (accessToken.isEmpty()) showLoginScreen("");
        else showHomeScreen(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (loginWebView != null) installTokenWatcher();
        else if (!accessToken.isEmpty() && pendingList != null && !loading
                && System.currentTimeMillis() - lastSyncAttemptAt > 5 * 60 * 1000L) loadLatestOrder();
    }

    @Override protected void onPause() {
        if (imageViewer != null) imageViewer.reset();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (imageViewer != null) imageViewer.reset();
        dismissCelebration();
        wasComplete = false;
        super.onDestroy();
        network.shutdownNow();
        imageLoader.shutdownNow();
        // Let queued collection/cache commits finish instead of dropping the last user action.
        cacheWorker.shutdown();
        if (loginWebView != null) {
            loginWebView.stopLoading();
            loginWebView.removeJavascriptInterface("PickupNative");
            loginWebView.destroy();
            loginWebView = null;
        }
    }

    private void configureSystemBars() {
        Ui.systemBars(this);
    }

    // ---------------------------------------------------------------------
    // Login. The official site handles both email/password and SMS-code flows.
    // ---------------------------------------------------------------------

    private void showLoginScreen(String notice) {
        if (imageViewer != null) imageViewer.reset();
        dismissCelebration();
        wasComplete = false;
        authHandled = false;
        loginWebView = createLoginWebView();
        LinearLayout root = Ui.screen(this);
        root.addView(Ui.title(this, "איסוף משנת יוסף"));
        root.addView(Ui.text(this, "מתחברים לחשבון ובוחרים את ההזמנה האחרונה לאיסוף",
                Ui.T_BODY, Ui.MUTED, false), Ui.row(this, Ui.S1, Ui.S3));
        root.addView(buildLoginMethods());
        if (hasCachedOrder()) root.addView(buildCachedListButton());
        if (notice != null && !notice.isEmpty()) root.addView(Ui.banner(this, notice));
        loginTermsNotice = Ui.banner(this, "האתר מבקש לאשר את התקנון. אפשר לאשר אותו בחלון האתר למטה; לאחר האישור טעינת המוצרים תמשיך אוטומטית.");
        loginTermsNotice.setVisibility(View.GONE);
        root.addView(loginTermsNotice);
        root.addView(buildLoginBrowser());
        setContentView(root);
        Ui.systemBars(this);
        loginWebView.loadUrl(LOGIN_URL);
        loginWebView.post(() -> loginWebView.requestFocus());
    }

    private WebView createLoginWebView() {
        WebView web = new WebView(this);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new AuthBridge(), "PickupNative");
        web.setWebViewClient(createLoginWebViewClient());
        return web;
    }

    private WebViewClient createLoginWebViewClient() {
        return new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleLoginNavigation(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleLoginNavigation(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installTokenWatcher();
            }
        };
    }

    private LinearLayout buildLoginMethods() {
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.shape(this, Ui.SURFACE, Ui.R_CARD, 1, Ui.P200));
        box.setPadding(Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3),
                Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3));
        box.addView(Ui.text(this, "אפשר להתחבר בשתי דרכים", Ui.T_SECTION, Ui.P800, true));
        String body = "אימייל וסיסמה, או מספר טלפון וקוד חד־פעמי ב־SMS. פרטי הכניסה מטופלים ישירות באתר משנת יוסף.";
        box.addView(Ui.text(this, body, Ui.T_NOTE, Ui.MUTED, false),
                Ui.row(this, Ui.S2, Ui.S1));
        return box;
    }

    private Button buildCachedListButton() {
        Button button = Ui.button(this, "הצגת הרשימה השמורה", Ui.Kind.SOFT);
        button.setOnClickListener(v -> openCachedListFromLogin());
        return button;
    }

    private FrameLayout buildLoginBrowser() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.shape(this, Ui.SURFACE, Ui.R_CARD, 1, Ui.P200));
        frame.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1f);
        params.topMargin = Ui.dp(this, Ui.S3);
        frame.setLayoutParams(params);
        frame.addView(loginWebView, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private void openCachedListFromLogin() {
        if (!cacheTrusted || !hasCachedOrder()) return;
        if (loginWebView != null) {
            loginWebView.stopLoading();
            loginWebView.removeJavascriptInterface("PickupNative");
            if (loginWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) loginWebView.getParent()).removeView(loginWebView);
            }
            loginWebView.destroy();
            loginWebView = null;
        }
        showHomeScreen(false);
    }

    private boolean handleLoginNavigation(Uri uri) {
        String host = uri == null ? "" : uri.getHost();
        if (isTrustedHost(host)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
        return true;
    }

    private boolean isTrustedHost(String host) {
        return "new.mishnatyosef.org".equalsIgnoreCase(host)
                || "mishnatyosef.org".equalsIgnoreCase(host)
                || "www.mishnatyosef.org".equalsIgnoreCase(host)
                || "v2.mishnatyosef.org".equalsIgnoreCase(host)
                || "images.mishnatyosef.org".equalsIgnoreCase(host)
                || "dev.mishnatyosef.org".equalsIgnoreCase(host);
    }

    private void installTokenWatcher() {
        if (loginWebView == null || authHandled) return;
        String script = "(function(){try{" +
                "if(window.__pickupTokenWatch)clearInterval(window.__pickupTokenWatch);" +
                "var stable=0,notice=false;" +
                "function visible(e){if(!e)return false;var s=getComputedStyle(e),r=e.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0;}" +
                "function termsRequired(){" +
                "var body=(document.body&&document.body.innerText||'').toLowerCase();" +
                "if(!/(תקנון|תנאי שימוש|terms of service|terms and conditions)/.test(body))return false;" +
                "var controls=Array.from(document.querySelectorAll('input[type=checkbox],button,[role=button],input[type=submit]'));" +
                "return controls.some(function(e){var t=((e.innerText||e.value||e.getAttribute('aria-label')||'')+'').toLowerCase();return visible(e)&&/(אישור|מסכים|המשך|accept|agree|confirm)/.test(t);});" +
                "}" +
                "window.__pickupTokenWatch=setInterval(function(){try{" +
                "var t=localStorage.getItem('token'),need=termsRequired();" +
                "if(need){stable=0;if(!notice&&window.PickupNative){notice=true;window.PickupNative.onTermsRequired(true);}}" +
                "else{if(notice&&window.PickupNative){notice=false;window.PickupNative.onTermsRequired(false);}if(t&&t.length>12&&window.PickupNative){stable++;if(stable>=4){window.PickupNative.onToken(location.origin,t);clearInterval(window.__pickupTokenWatch);}}else stable=0;}" +
                "}catch(e){}},800);" +
                "}catch(e){}})();";
        loginWebView.evaluateJavascript(script, null);
    }

    private final class AuthBridge {
        @JavascriptInterface public void onTermsRequired(boolean required) {
            mainHandler.post(() -> {
                if (loginTermsNotice != null && loginWebView != null && !authHandled)
                    loginTermsNotice.setVisibility(required ? View.VISIBLE : View.GONE);
            });
        }
        @JavascriptInterface public void onToken(String origin, String token) {
            Uri source = Uri.parse(origin == null ? "" : origin);
            if (!"https".equalsIgnoreCase(source.getScheme()) || !isTrustedHost(source.getHost())) return;
            if (token == null || token.trim().length() < 12 || token.contains("\n")) return;
            mainHandler.post(() -> acceptLoginToken(token.trim()));
        }
    }

    private void acceptLoginToken(String token) {
        if (authHandled || isFinishing()) return;
        authHandled = true;
        final int generation = ++authGeneration;
        final String newOwner = DataGuard.ownerFromToken(token);
        final String tokenFingerprint = DataGuard.tokenFingerprint(token);
        final String oldOwner = preferences.getString(DataGuard.K_OWNER, "");
        final String oldFingerprint = preferences.getString(DataGuard.K_TOKEN_FINGERPRINT, "");
        boolean sameAccount = (!newOwner.isEmpty() && newOwner.equals(oldOwner))
                || (newOwner.isEmpty() && !oldFingerprint.isEmpty()
                    && oldFingerprint.equals(tokenFingerprint));
        boolean identityPending = newOwner.isEmpty() && !sameAccount && !oldOwner.isEmpty();
        try {
            saveEncryptedToken(token);
            preferences.edit().remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY).apply();
            accessToken = token;
            activeOwner = !newOwner.isEmpty() ? newOwner : (sameAccount ? oldOwner : "");
            cacheTrusted = sameAccount && isCacheManifestValid(activeOwner);
            destroyLoginWebView();
            if (!sameAccount && !identityPending) {
                invalidateSavedOrderPreferences(newOwner);
                queueOrderDataWipe(generation);
            }
            showHomeScreen(true);
        } catch (Exception e) {
            authHandled = false;
            showLoginScreen("לא ניתן לשמור את ההתחברות בצורה מאובטחת. נסו שוב.");
        }
    }

    private void destroyLoginWebView() {
        if (loginWebView == null) return;
        loginWebView.stopLoading();
        loginWebView.removeJavascriptInterface("PickupNative");
        loginWebView.destroy();
        loginWebView = null;
    }

    /** Removes old-account state immediately; the serialized cache worker removes its files. */
    private void invalidateSavedOrderPreferences(String owner) {
        SharedPreferences.Editor edit = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(DataGuard.COLLECTED_PREFIX)) edit.remove(key);
        }
        edit.remove(DataGuard.K_ORDER).remove(DataGuard.K_PRINT)
                .remove(DataGuard.K_TOKEN_FINGERPRINT)
                .remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY)
                .putString(DataGuard.K_OWNER, owner == null ? "" : owner).commit();
        collected.clear();
        imageCache.evictAll();
        orderData = null;
        currentOrderId = "";
        products.clear();
        productCards.clear();
    }

    /** Safely adopts a legacy cache only while its original encrypted login is still present. */
    private void initializeCacheOwnership() {
        cacheTrusted = false;
        activeOwner = "";
        if (accessToken.isEmpty()) {
            // A saved list remains available while signed out, but only when its
            // owner, order ID and content fingerprint all validate locally.
            activeOwner = preferences.getString(DataGuard.K_OWNER, "");
            cacheTrusted = isCacheManifestValid(activeOwner);
            if (!cacheTrusted
                    && (!activeOwner.isEmpty() || hasAnyOrderCacheFiles())) {
                invalidateSavedOrderPreferences("");
                queueOrderDataWipe(authGeneration);
            }
            return;
        }

        preferences.edit().remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY).apply();
        String tokenOwner = DataGuard.ownerFromToken(accessToken);
        String savedOwner = preferences.getString(DataGuard.K_OWNER, "");
        String savedToken = preferences.getString(DataGuard.K_TOKEN_FINGERPRINT, "");
        String tokenFingerprint = DataGuard.tokenFingerprint(accessToken);
        if (!tokenOwner.isEmpty()) {
            activeOwner = tokenOwner;
            if (savedOwner.isEmpty() || !savedOwner.equals(tokenOwner)) {
                invalidateSavedOrderPreferences(tokenOwner);
                queueOrderDataWipe(authGeneration);
                return;
            }
            cacheTrusted = isCacheManifestValid(tokenOwner);
            return;
        }
        if (!savedOwner.isEmpty() && !savedToken.isEmpty()
                && savedToken.equals(tokenFingerprint)) {
            activeOwner = savedOwner;
            cacheTrusted = isCacheManifestValid(savedOwner);
            return;
        }
        // Opaque/rotated tokens are quarantined until /api/orders returns a stable owner ID.
        // The cache is not displayed, but can still be retained if the server confirms the same account.
        activeOwner = "";
        cacheTrusted = false;
    }

    private boolean hasAnyOrderCacheFiles() {
        for (String name : DataGuard.CACHE_DIRS)
            if (new File(getFilesDir(), name).exists()) return true;
        return false;
    }

    private void queueOrderDataWipe(int generation) {
        cacheWorker.execute(() -> {
            if (generation == authGeneration) wipeOrderData();
        });
    }

    /** Clears cached order data and any transient celebration overlay. */
    private void wipeOrderData() {
        Runnable cleanup = () -> {
            if (imageViewer != null) imageViewer.reset();
            dismissCelebration();
            wasComplete = false;
        };
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run();
        else mainHandler.post(cleanup);
        DataGuard.wipeOrderData(getFilesDir(), preferences);
    }

    private boolean isCacheManifestValid(String owner) {
        File manifest = new File(new File(getFilesDir(), "pickup_order_cache"), "order.json");
        if (!manifest.isFile() || owner == null || owner.isEmpty()) return false;
        try (FileInputStream in = new FileInputStream(manifest);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            JSONObject saved = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            String expectedPrint = preferences.getString(DataGuard.K_PRINT, "");
            if (!owner.equals(saved.optString("owner", ""))
                    || !String.valueOf(saved.optLong("order_id", -1))
                        .equals(preferences.getString(DataGuard.K_ORDER, ""))
                    || expectedPrint.isEmpty()) return false;
            String manifestPrint = saved.optString("fingerprint", "");
            if (expectedPrint.equals(manifestPrint)) return true;
            if (!manifestPrint.isEmpty()) return false;

            // Version-1 manifests used a digest that did not include image URLs.
            JSONArray rows = saved.optJSONArray("products");
            if (rows == null || rows.length() == 0) return false;
            List<ProductRow> restored = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) return false;
                restored.add(new ProductRow(first(row, "key"), first(row, "name"),
                        first(row, "image_url"), first(row, "amount"), first(row, "note")));
            }
            int version = saved.optInt("fingerprint_version", 1);
            String calculated = version >= 2
                    ? DataGuard.fingerprint(restored) : DataGuard.legacyFingerprint(restored);
            return expectedPrint.equals(calculated);
        } catch (Exception ignored) { return false; }
    }

    /** True only when a non-empty order has every product marked as collected. */
    private boolean isAllCollected() {
        if (products.isEmpty()) return false;
        for (ProductRow row : products) {
            if (!collected.contains(row.key)) return false;
        }
        return true;
    }

    /** Synchronize loaded/reset data without treating it as a user completion. */
    private void syncCompletionState() {
        wasComplete = isAllCollected();
    }

    /** Celebrate only a user-triggered transition from incomplete to complete. */
    private void checkCelebration() {
        boolean complete = isAllCollected();
        boolean justCompleted = complete && !wasComplete;
        wasComplete = complete;
        if (justCompleted) showCelebration();
    }

    private void showCelebration() {
        dismissCelebration();

        FrameLayout layer = new FrameLayout(this);
        layer.setFocusable(false);
        layer.setClickable(false);
        layer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        // The message animates independently: a quick zoom-in, then a soft zoom-and-fade.
        TextView celebrationMessage = Ui.celebrationCard(this, "הידד! אספת את כל המוצרים בהצלחה!");
        if (android.os.Build.VERSION.SDK_INT >= 12) {
            celebrationMessage.setScaleX(0.62f);
            celebrationMessage.setScaleY(0.62f);
            celebrationMessage.setAlpha(0f);
        }
        layer.addView(celebrationMessage);

        float animScale = 1f;
        try {
            animScale = Settings.Global.getFloat(
                    getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Exception ignored) { }
        boolean animated = animScale > 0f;
        if (animated) {
            celebrationMessage.animate()
                    .scaleX(1.10f).scaleY(1.10f).alpha(1f)
                    .setDuration(420L)
                    .withEndAction(() -> celebrationMessage.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(130L)
                            .withEndAction(() -> celebrationMessage.postDelayed(() ->
                                    celebrationMessage.animate()
                                            .scaleX(1.38f).scaleY(1.38f).alpha(0f)
                                            .setDuration(650L)
                                            .start(), 350L))
                            .start())
                    .start();
            ConfettiView confetti = new ConfettiView(this);
            confetti.setOnDone(this::dismissCelebration);
            // Added last so confetti is the topmost visual layer.
            layer.addView(confetti, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        addContentView(layer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        celebration = layer;
        if (!animated) mainHandler.postDelayed(dismissCelebrationTask, 1200L);
    }

    /** Removes the overlay and stops its animator without changing the current focus. */
    private void dismissCelebration() {
        mainHandler.removeCallbacks(dismissCelebrationTask);
        View layer = celebration;
        celebration = null;
        if (layer == null) return;
        if (layer instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) layer;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ConfettiView) ((ConfettiView) child).stop();
            }
        }
        if (layer.getParent() instanceof ViewGroup) {
            ((ViewGroup) layer.getParent()).removeView(layer);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (celebration != null && event.getAction() == KeyEvent.ACTION_DOWN) dismissCelebration();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (loginWebView != null && loginWebView.canGoBack()) loginWebView.goBack();
        else super.onBackPressed();
    }

    // ---------------------------------------------------------------------
    // Main pickup checklist screen
    // ---------------------------------------------------------------------

    private void showHomeScreen(boolean loadData) {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout header = buildHomeHeader();
        pageScroll = buildProductScroll();
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.systemBars(this);
        if (cacheTrusted && hasCachedOrder()) loadCachedOrder();
        renderProducts(false);
        wireFocusOrder();
        if (loadData && !accessToken.isEmpty()) loadLatestOrder();
    }

    private LinearLayout buildHomeHeader() {
        // Keep the title and the collected/progress bar fixed; all controls stay scrollable.
        LinearLayout header = Ui.column(this);
        header.setPadding(Ui.dp(this, Ui.S5), Ui.dp(this, Ui.S4),
                Ui.dp(this, Ui.S5), Ui.dp(this, Ui.S2));
        header.addView(buildHomeTitleRow());
        header.addView(buildProgressCard(), Ui.row(this, Ui.S2, 0));
        return header;
    }

    private LinearLayout buildHomeTitleRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        logoutButton = Ui.button(this, accessToken.isEmpty() ? "התחברות" : "התנתקות", Ui.Kind.SOFT);
        logoutButton.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        logoutButton.setOnClickListener(v -> {
            if (accessToken.isEmpty()) showLoginScreen("");
            else confirmLogout();
        });
        TextView title = Ui.text(this, "רשימת איסוף", Ui.T_TITLE, Ui.P900, true);
        row.addView(logoutButton);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private Button buildRetryButton() {
        retryButton = Ui.button(this, "לנסות לסנכרן שוב", Ui.Kind.SOFT);
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> loadLatestOrder());
        return retryButton;
    }

    private LinearLayout buildHomeActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, 0, 0, Ui.dp(this, Ui.S3));
        resetButton = Ui.button(this, "איפוס ליקוט", Ui.Kind.SOFT);
        resetButton.setOnClickListener(v -> confirmResetCollection());
        Button clear = Ui.button(this, "מחיקת כל הנתונים", Ui.Kind.STRONG);
        clear.setOnClickListener(v -> confirmClearSavedData());
        // Explicit height and bottom breathing room prevent the compact buttons being clipped.
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f);
        first.setMargins(0, Ui.dp(this, Ui.S2), Ui.dp(this, Ui.S1), Ui.dp(this, Ui.S2));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1f);
        second.setMargins(Ui.dp(this, Ui.S1), Ui.dp(this, Ui.S2), 0, Ui.dp(this, Ui.S2));
        actions.addView(resetButton, first);
        actions.addView(clear, second);
        return actions;
    }

    private LinearLayout buildProgressCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.shape(this, Ui.SURFACE, Ui.R_CARD, 1, Ui.P200));
        card.setPadding(Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3),
                Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        progressPercent = Ui.text(this, "0%", Ui.T_TITLE, Ui.P800, true);
        progressPercent.setGravity(Gravity.CENTER);
        progressPercent.setMinWidth(Ui.dp(this, Ui.PERCENT_WIDTH));
        LinearLayout.LayoutParams percent = new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.PERCENT_WIDTH), -2);
        percent.rightMargin = Ui.dp(this, Ui.S3);
        head.addView(progressPercent, percent);
        progressText = Ui.text(this, "נאספו 0 מתוך 0", Ui.T_BODY, Ui.P800, true);
        progressText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        head.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(head);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        Ui.styleProgress(this, progressBar);
        LinearLayout.LayoutParams progressParams = (LinearLayout.LayoutParams) progressBar.getLayoutParams();
        progressParams.topMargin = Ui.dp(this, Ui.S2);
        card.addView(progressBar, progressParams);
        return card;
    }

    private ScrollView buildProductScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setBackgroundColor(Ui.BG);
        page = Ui.column(this);
        page.setPadding(Ui.dp(this, Ui.S5), Ui.dp(this, Ui.S2),
                Ui.dp(this, Ui.S5), Ui.dp(this, Ui.S6));
        page.setFocusable(false);

        // Secondary controls and notices belong to the scrolling content, not the sticky header.
        page.addView(Ui.text(this,
                "מה חדש: הרשימה נשמרת גם אחרי התנתקות, חשבונות מופרדים וסימוני ליקוט נשמרים לפי פריט וכמות",
                Ui.T_NOTE, Ui.MUTED, false), Ui.row(this, Ui.S1, Ui.S2));
        messageText = Ui.banner(this, "");
        messageText.setVisibility(View.GONE);
        page.addView(messageText);
        page.addView(buildRetryButton());
        page.addView(buildHomeActions());

        pendingList = Ui.column(this);
        collectedList = Ui.column(this);
        page.addView(Ui.section(this, "ממתינים לליקוט"));
        page.addView(pendingList);
        collectedSection = buildCollectedSection();
        page.addView(collectedSection);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private LinearLayout buildCollectedSection() {
        LinearLayout section = Ui.column(this);
        section.addView(Ui.section(this, "כבר לוקט"));
        section.addView(collectedList);
        return section;
    }

    private void showSyncMessage(String value, boolean canRetry) {
        if (messageText != null) {
            messageText.setText(value == null ? "" : value);
            messageText.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (retryButton != null) retryButton.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    private void confirmResetCollection() {
        if (collected.isEmpty()) return;
        showModernConfirm("איפוס האיסוף", "להחזיר את כל המוצרים לרשימת הממתינים?", "איפוס", () -> {
            collected.clear();
            syncCompletionState();
            saveCollectedState();
            renderProducts(false);
            pageScroll.post(this::focusFirstPending);
        });
    }

    private void confirmLogout() {
        showModernConfirm("התנתקות מהחשבון",
                "לצאת מהחשבון ולמחוק את פרטי ההתחברות, ההזמנה השמורה, התמונות וסימוני הליקוט מהמכשיר?",
                "התנתקות", this::logout);
    }

    private void confirmClearSavedData() {
        showModernConfirm("מחיקת כל הנתונים השמורים", "למחוק מהמכשיר את ההזמנה, התמונות ואת סימוני האיסוף? הנתונים בחשבון המקוון לא יימחקו.", "מחיקה", this::clearAllSavedData);
    }

    private void clearAllSavedData() {
        dismissCelebration();
        wasComplete = false;
        final int generation = ++authGeneration;
        loading = false;
        accessToken = "";
        activeOwner = "";
        cacheTrusted = false;
        preferences.edit().remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY).commit();
        clearOrderMemory();
        destroyLoginWebView();
        WebStorage.getInstance().deleteAllData();
        clearLoginCookies(() -> cacheWorker.execute(() -> {
            if (generation != authGeneration) return;
            wipeOrderData();
            clearToken();
            mainHandler.post(() -> {
                if (generation == authGeneration && !isFinishing())
                    showLoginScreen("כל הנתונים המקומיים נמחקו, כולל פרטי ההתחברות. אפשר להתחבר מחדש.");
            });
        }));
    }

    private void clearLoginCookies(Runnable afterClear) {
        CookieManager cookies = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= 21) {
            cookies.removeAllCookies(removed -> { cookies.flush(); afterClear.run(); });
        } else {
            cookies.removeAllCookie();
            afterClear.run();
        }
    }

    private void showModernConfirm(String title, String message, String positive, Runnable action) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, title, Ui.T_NAME, Ui.P900, true));
        panel.addView(Ui.text(this, message, Ui.T_BODY, Ui.MUTED, false),
                Ui.row(this, Ui.S2, Ui.S3));
        panel.addView(buildConfirmButtons(dialog, positive, action));
        dialog.setContentView(panel);
        dialog.show();
        styleConfirmWindow(dialog);
    }

    private LinearLayout buildConfirmButtons(Dialog dialog, String positive, Runnable action) {
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = Ui.button(this, "ביטול", Ui.Kind.SOFT);
        Button approve = Ui.button(this, positive, Ui.Kind.STRONG);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1f);
        first.setMargins(0, 0, Ui.dp(this, Ui.S1), 0);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1f);
        second.setMargins(Ui.dp(this, Ui.S1), 0, 0, 0);
        buttons.addView(cancel, first);
        buttons.addView(approve, second);
        cancel.setOnClickListener(v -> dialog.dismiss());
        approve.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
        dialog.setOnShowListener(d -> approve.requestFocus());
        return buttons;
    }

    private void styleConfirmWindow(Dialog dialog) {
        if (dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels
                - Ui.dp(this, Ui.DIALOG_SIDE_MARGIN), Ui.dp(this, Ui.DIALOG_MAX_WIDTH));
        dialog.getWindow().setLayout(width, -2);
    }

    private void loadLatestOrder() {
        if (loading || accessToken.isEmpty()) return;
        final RequestSession session = new RequestSession(authGeneration, accessToken);
        loading = true;
        lastSyncAttemptAt = System.currentTimeMillis();
        showSyncMessage("מתחבר לשרת וטוען הזמנה אחרונה…", false);
        network.execute(() -> {
            try {
                JSONArray orders = apiGetArray("/api/orders", session);
                String owner = DataGuard.ownerFromToken(session.token);
                if (owner.isEmpty()) owner = DataGuard.ownerFromOrders(orders);
                if (owner.isEmpty()
                        && DataGuard.tokenFingerprint(session.token).equals(
                            preferences.getString(DataGuard.K_TOKEN_FINGERPRINT, "")))
                    owner = preferences.getString(DataGuard.K_OWNER, "");
                JSONObject latest = chooseLatestPickupOrder(orders);
                if (latest == null) throw new Exception("לא נמצאה הזמנה בחשבון.");
                long id = extractOrderId(latest);
                if (id <= 0) throw new Exception("לא נמצא מזהה להזמנה האחרונה.");
                JSONObject order = unwrapOrder(apiGetObject("/api/orders/" + id, session));
                List<ProductRow> parsed = parseProducts(order);
                if (parsed.isEmpty()) throw new Exception("ההזמנה נמצאה, אבל לא נמצאו בה פריטים להצגה.");
                if (!session.isCurrent(this)) return;
                final String resolvedOwner = owner;
                final String resolvedToken = session.token;
                mainHandler.post(() -> onFreshOrder(session.generation, resolvedToken,
                        resolvedOwner, id, order, parsed));
            } catch (Exception error) {
                String message = safeError(error);
                mainHandler.post(() -> onOrderLoadError(session, message));
            }
        });
    }

    private void onOrderLoadError(RequestSession session, String message) {
        if (!session.isCurrent(this) || isFinishing()) return;
        loading = false;
        if (isAuthError(message)) {
            final boolean useCache = cacheTrusted && hasCachedOrder();
            final int generation = ++authGeneration;
            accessToken = "";
            cacheWorker.execute(() -> {
                if (generation != authGeneration) return;
                clearToken();
                SharedPreferences.Editor state = preferences.edit();
                if (useCache) state.putBoolean(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY, true);
                else state.remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY);
                state.commit();
            });
            if (useCache) {
                showHomeScreen(false);
                showSyncMessage("ההתחברות פגה. מוצגת ההזמנה המקומית השמורה.", false);
            } else {
                cacheTrusted = false;
                showLoginScreen("פג תוקף ההתחברות. התחברו מחדש כדי להמשיך.");
            }
            return;
        }
        if (products.isEmpty() && cacheTrusted) loadCachedOrder();
        renderProducts(false);
        showSyncMessage(products.isEmpty() ? message
                : "מוצגת ההזמנה השמורה מהמכשיר. הסנכרון נכשל: " + message,
                !accessToken.isEmpty());
    }

    private void loadCachedOrder() {
        if (!cacheTrusted || !hasCachedOrder()) return;
        File root = new File(getFilesDir(), "pickup_order_cache");
        File manifest = new File(root, "order.json");
        try (FileInputStream in = new FileInputStream(manifest);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            JSONObject saved = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            long id = saved.optLong("order_id", -1);
            JSONArray rows = saved.optJSONArray("products");
            if (id <= 0 || rows == null || rows.length() == 0) throw new Exception("invalid cache");
            List<ProductRow> restored = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) throw new Exception("invalid item");
                String imageFile = first(row, "image_file");
                String imagePath = "";
                if (!imageFile.isEmpty()) {
                    File image = new File(root, imageFile);
                    if (!image.isFile() || image.length() == 0) throw new Exception("missing image");
                    imagePath = image.getAbsolutePath();
                }
                restored.add(new ProductRow(first(row, "key"), first(row, "name"),
                        first(row, "image_url"), first(row, "amount"), first(row, "note"), imagePath));
            }
            // Version 1 caches predate image URLs being included in the content digest.
            // Keep them readable offline; the next online sync upgrades their fingerprint to v2.
            int fingerprintVersion = saved.optInt("fingerprint_version", 1);
            if (fingerprintVersion >= 2
                    && !DataGuard.fingerprint(restored).equals(preferences.getString(DataGuard.K_PRINT, "")))
                throw new Exception("cache fingerprint mismatch");
            currentOrderId = String.valueOf(id);
            products.clear();
            products.addAll(restored);
            loadCollectedState();
            syncCompletionState();
        } catch (Exception error) {
            cacheTrusted = false;
            cacheWorker.execute(() -> DataGuard.wipeOrderData(getFilesDir(), preferences));
        }
    }

    private boolean hasCachedOrder() {
        return cacheTrusted && isCacheManifestValid(activeOwner);
    }

    private void onFreshOrder(int generation, String requestToken, String owner,
                              long orderId, JSONObject order, List<ProductRow> rows) {
        if (generation != authGeneration || !requestToken.equals(accessToken) || isFinishing()) return;
        loading = false;
        String stableOwner = owner == null ? "" : owner;
        if (stableOwner.isEmpty()) {
            cacheTrusted = false;
            activeOwner = "";
            clearOrderMemory();
            currentOrderId = String.valueOf(orderId);
            products.addAll(rows);
            syncCompletionState();
            renderProducts(false);
            showSyncMessage("לא ניתן לאמת את זהות החשבון; הרשימה מוצגת אך לא תישמר במטמון.", false);
            cacheWorker.execute(() -> {
                if (generation == authGeneration) wipeOrderData();
            });
            return;
        }

        String fingerprint = DataGuard.fingerprint(rows);
        DataGuard.Verdict verdict = DataGuard.judge(preferences, stableOwner, orderId, fingerprint);
        Set<String> retainedTicks = new HashSet<>();
        if (verdict != DataGuard.Verdict.SAME && imageViewer != null) imageViewer.reset();
        if (verdict == DataGuard.Verdict.CONTENT_CHANGED) {
            retainedTicks.addAll(readCollectedTicks(String.valueOf(orderId)));
            // A just-tapped item may still be queued on cacheWorker; include the live in-memory
            // state so a server edit cannot discard the latest user action.
            for (ProductRow previous : products) {
                if (collected.contains(previous.key))
                    retainedTicks.add(DataGuard.tick(previous));
            }
        }
        if (verdict == DataGuard.Verdict.NEW_ACCOUNT || verdict == DataGuard.Verdict.NEW_ORDER) {
            // Invalidate old metadata before the asynchronous disk wipe so logout cannot
            // accidentally re-expose the previous account/order during that window.
            invalidateSavedOrderPreferences(stableOwner);
        } else if (verdict == DataGuard.Verdict.CONTENT_CHANGED) {
            DataGuard.clearOrderMeta(preferences);
        }
        activeOwner = stableOwner;
        cacheTrusted = true;
        orderData = order;
        currentOrderId = String.valueOf(orderId);
        products.clear();
        products.addAll(rows);
        collected.clear();
        if (verdict == DataGuard.Verdict.SAME) {
            loadCollectedState();
        } else if (verdict == DataGuard.Verdict.CONTENT_CHANGED) {
            for (ProductRow row : rows)
                if (retainedTicks.contains(DataGuard.tick(row))) collected.add(row.key);
        }
        syncCompletionState();
        renderProducts(false);
        wireFocusOrder();
        showSyncMessage("", false);

        boolean rewrite = verdict != DataGuard.Verdict.SAME || !isCacheManifestValid(stableOwner);
        if (!rewrite) {
            saveCollectedState();
            cacheWorker.execute(() -> {
                if (generation == authGeneration && requestToken.equals(accessToken))
                    DataGuard.saveMeta(preferences, stableOwner, orderId, fingerprint,
                            DataGuard.tokenFingerprint(requestToken));
            });
            return;
        }

        final String tokenFingerprint = DataGuard.tokenFingerprint(requestToken);
        final String orderKey = String.valueOf(orderId);
        final JSONArray ticks = collectedTicksJson();
        final DataGuard.Verdict decision = verdict;
        cacheWorker.execute(() -> {
            if (generation != authGeneration || !requestToken.equals(accessToken)) return;
            if (decision != DataGuard.Verdict.SAME) wipeOrderData();
            else DataGuard.wipeCacheFiles(getFilesDir());
            if (generation != authGeneration || !requestToken.equals(accessToken)) return;
            writeCollectedState(orderKey, ticks);
            try {
                cacheOrderIfComplete(generation, requestToken, stableOwner, orderId,
                        order, rows, fingerprint);
                if (generation != authGeneration || !requestToken.equals(accessToken)) return;
                DataGuard.saveMeta(preferences, stableOwner, orderId, fingerprint, tokenFingerprint);
            } catch (Exception cacheError) {
                android.util.Log.w("MishnatPickup",
                        "Offline cache update failed; stale snapshot was not retained", cacheError);
                if (generation == authGeneration && requestToken.equals(accessToken)) {
                    DataGuard.wipeCacheFiles(getFilesDir());
                    // Keep the online order identity and its checkmarks even when its offline
                    // image snapshot failed. The next sync can retry the cache without resetting
                    // collection progress or mistaking the same order for a new one.
                    DataGuard.saveMeta(preferences, stableOwner, orderId, fingerprint, tokenFingerprint);
                }
                mainHandler.post(() -> {
                    if (generation == authGeneration && requestToken.equals(accessToken) && !isFinishing())
                        showSyncMessage("ההזמנה נטענה, אך שמירת העותק הלא־מקוון נכשלה.", false);
                });
            }
        });
    }

    private Set<String> readCollectedTicks(String orderId) {
        Set<String> saved = new HashSet<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(DataGuard.COLLECTED_PREFIX + orderId, "[]"));
            for (int i = 0; i < array.length(); i++) saved.add(array.optString(i, ""));
        } catch (Exception ignored) { }
        return saved;
    }

    private JSONArray collectedTicksJson() {
        JSONArray saved = new JSONArray();
        for (ProductRow row : products)
            if (collected.contains(row.key)) saved.put(DataGuard.tick(row));
        return saved;
    }

    private void writeCollectedState(String orderId, JSONArray ticks) {
        if (orderId != null && !orderId.isEmpty())
            preferences.edit().putString(DataGuard.COLLECTED_PREFIX + orderId, ticks.toString()).commit();
    }

    private void cacheOrderIfComplete(int generation, String requestToken, String owner,
                                      long orderId, JSONObject order, List<ProductRow> rows,
                                      String fingerprint) throws Exception {
        if (generation != authGeneration || !requestToken.equals(accessToken)) return;
        File root = new File(getFilesDir(), "pickup_order_cache");
        File temp = new File(getFilesDir(), "pickup_order_cache_tmp");
        File backup = new File(getFilesDir(), "pickup_order_cache_backup");
        deleteRecursively(temp);
        if (!temp.mkdirs()) throw new Exception("לא ניתן ליצור תיקיית שמירה");
        JSONArray savedRows = new JSONArray();
        for (int i = 0; i < rows.size(); i++) {
            if (generation != authGeneration || !requestToken.equals(accessToken)) {
                deleteRecursively(temp);
                return;
            }
            ProductRow row = rows.get(i);
            String imageFile = "";
            if (!row.imageUrl.isEmpty()) {
                imageFile = "image_" + i + ".img";
                if (!downloadImageToFile(row.imageUrl, new File(temp, imageFile))) {
                    deleteRecursively(temp);
                    throw new Exception("לא כל תמונות ההזמנה ירדו. ננסה שוב בסנכרון הבא.");
                }
            }
            JSONObject item = new JSONObject();
            item.put("key", row.key);
            item.put("name", row.name);
            item.put("image_url", row.imageUrl);
            item.put("amount", row.amount);
            item.put("note", row.note);
            item.put("image_file", imageFile);
            savedRows.put(item);
        }
        JSONObject snapshot = new JSONObject();
        snapshot.put("order_id", orderId);
        snapshot.put("owner", owner);
        snapshot.put("fingerprint", fingerprint);
        snapshot.put("fingerprint_version", 2);
        snapshot.put("sort_value", orderSortValue(order));
        snapshot.put("saved_at", System.currentTimeMillis());
        snapshot.put("products", savedRows);
        try (FileOutputStream out = new FileOutputStream(new File(temp, "order.json"))) {
            out.write(snapshot.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (generation != authGeneration || !requestToken.equals(accessToken)) {
            deleteRecursively(temp);
            return;
        }
        deleteRecursively(backup);
        boolean hadOld = root.exists();
        if (hadOld && !root.renameTo(backup)) {
            deleteRecursively(temp);
            throw new Exception("לא ניתן להחליף את המטמון בבטחה");
        }
        if (generation != authGeneration || !requestToken.equals(accessToken)) {
            if (hadOld) backup.renameTo(root);
            deleteRecursively(temp);
            return;
        }
        if (!temp.renameTo(root)) {
            if (hadOld) backup.renameTo(root);
            deleteRecursively(temp);
            throw new Exception("שמירת ההזמנה נכשלה");
        }
        if (generation != authGeneration || !requestToken.equals(accessToken)) {
            deleteRecursively(root);
            if (hadOld) backup.renameTo(root);
            return;
        }
        deleteRecursively(backup);
    }

    private boolean downloadImageToFile(String url, File destination) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000); connection.setReadTimeout(18000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return false;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192]; int n; long total = 0;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 8L * 1024L * 1024L) return false;
                    out.write(buffer, 0, n);
                }
                out.getFD().sync();
            }
            Bitmap bitmap = BitmapFactory.decodeFile(destination.getAbsolutePath());
            if (bitmap == null) return false;
            bitmap.recycle();
            return destination.length() > 0;
        } catch (Exception e) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private void loadCollectedState() {
        collected.clear();
        if (currentOrderId.isEmpty()) { syncCompletionState(); return; }
        Set<String> saved = readCollectedTicks(currentOrderId);
        for (ProductRow row : products)
            if (saved.contains(DataGuard.tick(row))) collected.add(row.key);
        saveCollectedState();
        syncCompletionState();
    }

    private void saveCollectedState() {
        if (currentOrderId.isEmpty()) return;
        final String orderId = currentOrderId;
        final int generation = authGeneration;
        final JSONArray ticks = collectedTicksJson();
        cacheWorker.execute(() -> {
            if (generation == authGeneration) writeCollectedState(orderId, ticks);
        });
    }

    private void renderProducts(boolean ignoredScrollFlag) {
        if (pendingList == null || collectedList == null) return;
        View focused = getCurrentFocus();
        String focusedKey = focused == null ? null : (String) focused.getTag();
        boolean sameKeys = productCards.size() == products.size();
        if (sameKeys) for (ProductRow product : products) {
            ProductCard existing = productCards.get(product.key);
            if (existing == null || !sameProduct(existing.product, product)) { sameKeys = false; break; }
        }
        if (!sameKeys) {
            pendingList.removeAllViews();
            collectedList.removeAllViews();
            productCards.clear();
            for (ProductRow product : products) productCards.put(product.key, createProductCard(product));
        }
        for (ProductRow product : products) {
            ProductCard card = productCards.get(product.key);
            if (card == null) continue;
            boolean done = collected.contains(product.key);
            bindProductCard(card, product, done);
            LinearLayout target = done ? collectedList : pendingList;
            ViewGroup parent = (ViewGroup) card.row.getParent();
            if (parent != target) {
                if (parent != null) parent.removeView(card.row);
                target.addView(card.row, insertionIndex(target, product, done));
            } else {
                int wanted = insertionIndex(target, product, done);
                int current = target.indexOfChild(card.row);
                if (current != wanted) {
                    target.removeView(card.row);
                    target.addView(card.row, Math.min(wanted, target.getChildCount()));
                }
            }
        }
        updateProgress();
        wireFocusOrder();
        if (!sameKeys) {
            pageScroll.post(() -> {
                ProductCard restore = focusedKey == null ? null : productCards.get(focusedKey);
                if (restore == null || !restore.row.requestFocus()) focusFirstPending();
            });
        }
    }

    private int insertionIndex(LinearLayout target, ProductRow product, boolean done) {
        int index = 0;
        for (ProductRow candidate : products) {
            if (candidate.key.equals(product.key)) break;
            if (collected.contains(candidate.key) == done) index++;
        }
        return Math.min(index, target.getChildCount());
    }

    private ProductCard createProductCard(ProductRow product) {
        Ui.CardViews views = Ui.productCard(this);
        ProductCard card = new ProductCard(views, product);
        views.root.setTag(product.key);
        if (!product.imagePath.isEmpty()) {
            Bitmap local = BitmapFactory.decodeFile(product.imagePath);
            if (local != null) views.image.setImageBitmap(local);
            else views.image.setImageResource(android.R.drawable.ic_menu_gallery);
        } else if (!product.imageUrl.isEmpty()) {
            loadProductImage(product.imageUrl, views.image);
        } else {
            views.image.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        views.image.setOnClickListener(v -> openProductImage(card));
        views.image.setFocusable(false);
        views.image.setFocusableInTouchMode(false);
        views.root.setOnClickListener(v -> toggleProduct(product.key));
        return card;
    }

    private void openProductImage(ProductCard card) {
        if (card == null || card.product == null || imageViewer == null) return;
        ProductRow product = card.product;
        Bitmap preview = null;
        android.graphics.drawable.Drawable drawable = card.views.image.getDrawable();
        if (drawable instanceof BitmapDrawable) preview = ((BitmapDrawable) drawable).getBitmap();
        File localFile = cachedImageFile(product);
        imageViewer.show(product.name, product.amount, preview, localFile);
    }

    /** Resolves only an already-published local image; opening the viewer never starts a download. */
    private File cachedImageFile(ProductRow product) {
        if (product == null) return null;
        if (product.imagePath != null && !product.imagePath.isEmpty()) {
            File direct = new File(product.imagePath);
            if (direct.isFile() && direct.length() > 0) return direct;
        }
        File root = new File(getFilesDir(), "pickup_order_cache");
        File manifest = new File(root, "order.json");
        try (FileInputStream in = new FileInputStream(manifest);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            JSONObject saved = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            JSONArray rows = saved.optJSONArray("products");
            if (rows == null) return null;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !product.key.equals(first(row, "key"))) continue;
                String imageFile = first(row, "image_file");
                if (imageFile.isEmpty()) return null;
                File image = new File(root, imageFile);
                return image.isFile() && image.length() > 0 ? image : null;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void bindProductCard(ProductCard card, ProductRow product, boolean done) {
        card.product = product;
        Ui.bindProduct(card.views, product.name, product.note, product.amount, done);
    }

    private void toggleProduct(String key) {
        ProductCard moving = productCards.get(key);
        if (moving == null) return;
        boolean wasCollected = collected.contains(key);
        LinearLayout source = wasCollected ? collectedList : pendingList;
        ProductRow product = findProduct(key);
        if (product == null) return;
        int oldIndex = source.indexOfChild(moving.row);
        View neighbor = null;
        if (oldIndex >= 0) {
            if (oldIndex + 1 < source.getChildCount()) neighbor = source.getChildAt(oldIndex + 1);
            else if (oldIndex > 0) neighbor = source.getChildAt(oldIndex - 1);
        }
        if (neighbor != null) neighbor.requestFocus();
        if (wasCollected) collected.remove(key); else collected.add(key);
        saveCollectedState();
        LinearLayout target = wasCollected ? pendingList : collectedList;
        ViewGroup parent = (ViewGroup) moving.row.getParent();
        if (parent != null) parent.removeView(moving.row);
        bindProductCard(moving, product, !wasCollected);
        target.addView(moving.row, insertionIndex(target, product, !wasCollected));
        updateProgress();
        wireFocusOrder();
        if (neighbor == null) moving.row.requestFocus();
        checkCelebration();
    }

    private boolean sameProduct(ProductRow a, ProductRow b) {
        return a != null && b != null && a.key.equals(b.key) && a.name.equals(b.name)
                && a.imageUrl.equals(b.imageUrl) && a.amount.equals(b.amount) && a.note.equals(b.note)
                && a.imagePath.equals(b.imagePath);
    }

    private ProductRow findProduct(String key) {
        for (ProductRow p : products) if (p.key.equals(key)) return p;
        return null;
    }

    private void updateProgress() {
        int done = 0;
        for (ProductRow product : products) if (collected.contains(product.key)) done++;
        int percent = products.isEmpty() ? 0 : (int) Math.round(done * 100.0 / products.size());
        if (progressText != null) progressText.setText("נאספו " + done + " מתוך " + products.size());
        if (progressPercent != null) progressPercent.setText(percent + "%");
        if (progressBar != null) progressBar.setProgress(percent);
    }

    private void wireFocusOrder() {
        if (pendingList == null || collectedList == null) return;
        View first = pendingList.getChildCount() > 0 ? pendingList.getChildAt(0)
                : (collectedList.getChildCount() > 0 ? collectedList.getChildAt(0) : null);
        if (first != null) first.setNextFocusUpId(resetButton == null ? View.NO_ID : resetButton.getId());
        View last = collectedList.getChildCount() > 0 ? collectedList.getChildAt(collectedList.getChildCount() - 1)
                : (pendingList.getChildCount() > 0 ? pendingList.getChildAt(pendingList.getChildCount() - 1) : null);
        if (last != null) last.setNextFocusDownId(logoutButton == null ? View.NO_ID : logoutButton.getId());
        if (resetButton != null && first != null) resetButton.setNextFocusDownId(first.getId());
        if (logoutButton != null && last != null) logoutButton.setNextFocusUpId(last.getId());
    }

    private void focusFirstPending() {
        if (pendingList != null && pendingList.getChildCount() > 0) pendingList.getChildAt(0).requestFocus();
        else if (collectedList != null && collectedList.getChildCount() > 0) collectedList.getChildAt(0).requestFocus();
        else if (resetButton != null) resetButton.requestFocus();
    }

    private void loadProductImage(String imageUrl, ImageView target) {
        Bitmap cached = imageCache.get(imageUrl);
        if (cached != null) { target.setImageBitmap(cached); return; }
        target.setImageResource(android.R.drawable.ic_menu_gallery);
        target.setTag(imageUrl);
        final int imageGeneration = authGeneration;
        imageLoader.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setInstanceFollowRedirects(true);
                try (InputStream in = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    int total = 0;
                    while ((read = in.read(buffer)) != -1 && total < 6 * 1024 * 1024) {
                        bytes.write(buffer, 0, read);
                        total += read;
                    }
                    byte[] imageBytes = bytes.toByteArray();
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);
                    int targetPixels = Math.max(120, Ui.imageSize(this));
                    int sample = 1;
                    while (bounds.outWidth / (sample * 2) >= targetPixels && bounds.outHeight / (sample * 2) >= targetPixels) sample *= 2;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
                    if (bitmap != null) {
                        if (imageGeneration != authGeneration) {
                            bitmap.recycle();
                            return;
                        }
                        imageCache.put(imageUrl, bitmap);
                        mainHandler.post(() -> {
                            if (imageGeneration == authGeneration && imageUrl.equals(target.getTag()))
                                target.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void logout() {
        if (imageViewer != null) imageViewer.reset();
        dismissCelebration();
        wasComplete = false;
        final int generation = ++authGeneration;
        loading = false;
        String trustedOwner = activeOwner;
        boolean preserveOfflineList = cacheTrusted && trustedOwner != null
                && !trustedOwner.isEmpty() && isCacheManifestValid(trustedOwner);
        if (!preserveOfflineList) {
            invalidateSavedOrderPreferences("");
            queueOrderDataWipe(generation);
        }
        accessToken = "";
        clearToken();
        activeOwner = preserveOfflineList ? trustedOwner : "";
        cacheTrusted = preserveOfflineList;
        preferences.edit().remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY).commit();
        clearOrderMemory();
        destroyLoginWebView();
        WebStorage.getInstance().deleteAllData();
        clearLoginCookies(() -> mainHandler.post(() -> {
            if (generation == authGeneration && !isFinishing()) showLoginScreen("");
        }));
    }

    private void clearOrderMemory() {
        dismissCelebration();
        wasComplete = false;
        products.clear();
        collected.clear();
        productCards.clear();
        orderData = null;
        currentOrderId = "";
        imageCache.evictAll();
    }

    // ---------------------------------------------------------------------
    // API access. The endpoints and order payload shape are taken from the
    // uploaded v6.2 extension and checked against the existing APK Factory client.
    // ---------------------------------------------------------------------

    private JSONArray apiGetArray(String path, RequestSession session) throws Exception {
        HttpResult response = authorizedRequest(session, "GET", path, null);
        ensureSuccess(response);
        Object parsed = new JSONTokener(response.body).nextValue();
        if (parsed instanceof JSONArray) return (JSONArray) parsed;
        if (parsed instanceof JSONObject) {
            JSONObject root = (JSONObject) parsed;
            JSONArray array = root.optJSONArray("data");
            if (array == null) array = root.optJSONArray("orders");
            if (array == null) array = root.optJSONArray("items");
            if (array == null && root.optJSONObject("data") != null) {
                JSONObject nested = root.optJSONObject("data");
                array = nested.optJSONArray("orders");
                if (array == null) array = nested.optJSONArray("items");
                if (array == null) array = nested.optJSONArray("data");
            }
            if (array == null) throw new Exception("השרת החזיר רשימת הזמנות במבנה לא מוכר.");
            return array;
        }
        throw new Exception("השרת לא החזיר רשימת הזמנות תקינה.");
    }

    private JSONObject apiGetObject(String path, RequestSession session) throws Exception {
        HttpResult response = authorizedRequest(session, "GET", path, null);
        ensureSuccess(response);
        Object parsed = new JSONTokener(response.body).nextValue();
        if (!(parsed instanceof JSONObject)) throw new Exception("פרטי ההזמנה חזרו במבנה לא תקין.");
        return (JSONObject) parsed;
    }

    private HttpResult authorizedRequest(RequestSession session, String method,
                                         String path, JSONObject body) throws Exception {
        if (!session.isCurrent(this)) throw new Exception("STALE_AUTH: בקשה של סשן קודם בוטלה.");
        HttpResult response = rawRequest(method, path, session.token, body);
        if (!session.isCurrent(this)) throw new Exception("STALE_AUTH: תשובת סשן קודם בוטלה.");
        if (response.code == 401) {
            HttpResult refreshed = rawRequest("POST", "/api/refresh", session.token,
                    new JSONObject().put("remember", 1));
            if (!session.isCurrent(this)) throw new Exception("STALE_AUTH: רענון של סשן קודם בוטל.");
            if (refreshed.code >= 200 && refreshed.code < 300) {
                String newToken = extractToken(refreshed.body);
                if (!newToken.isEmpty()) {
                    session.token = newToken;
                    accessToken = newToken;
                    saveEncryptedToken(newToken);
                    response = rawRequest(method, path, newToken, body);
                    if (!session.isCurrent(this))
                        throw new Exception("STALE_AUTH: תשובת רענון ישנה בוטלה.");
                }
            }
        }
        return response;
    }

    private HttpResult rawRequest(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(22000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Origin", "https://mishnatyosef.org");
        connection.setRequestProperty("Referer", "https://mishnatyosef.org/");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] bytes = (body == null ? new JSONObject() : body).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
        }
        connection.disconnect();
        return new HttpResult(code, text.toString());
    }

    private void ensureSuccess(HttpResult response) throws Exception {
        if (response.code == 401) throw new Exception("AUTH_REQUIRED: ההתחברות פגה או אינה תקפה.");
        if (response.code < 200 || response.code >= 300) {
            String message = "השרת החזיר שגיאה (" + response.code + ").";
            try {
                JSONObject error = new JSONObject(response.body);
                String detail = first(error, "message", "error", "title");
                if (!detail.isEmpty() && detail.length() < 180) message = detail;
            } catch (Exception ignored) { }
            throw new Exception(message);
        }
    }

    private static String extractToken(String body) {
        try {
            JSONObject root = new JSONObject(body);
            String token = first(root, "access_token", "token");
            JSONObject data = root.optJSONObject("data");
            if (token.isEmpty() && data != null) token = first(data, "access_token", "token");
            return token;
        } catch (Exception e) { return ""; }
    }

    // ---------------------------------------------------------------------
    // Order selection and product parsing
    // ---------------------------------------------------------------------

    private JSONObject chooseLatestPickupOrder(JSONArray orders) {
        List<JSONObject> valid = new ArrayList<>();
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null || extractOrderId(order) <= 0) continue;
            String status = first(order, "status", "order_status", "state").toLowerCase(Locale.ROOT);
            if (status.contains("cancel") || status.contains("בוטל") || status.contains("rejected") || status.contains("deleted")) continue;
            valid.add(order);
        }
        if (valid.isEmpty()) return null;
        Collections.sort(valid, (a, b) -> {
            long ta = orderSortValue(a), tb = orderSortValue(b);
            if (ta != tb) return Long.compare(tb, ta);
            return Long.compare(extractOrderId(b), extractOrderId(a));
        });
        return valid.get(0);
    }

    private long orderSortValue(JSONObject order) {
        String[] dateKeys = {"created_at", "createdAt", "order_date", "orderDate", "date", "updated_at", "updatedAt"};
        for (String key : dateKeys) {
            String value = first(order, key);
            if (value.isEmpty()) continue;
            long parsed = parseDate(value);
            if (parsed > 0) return parsed;
            try { return Long.parseLong(value); } catch (Exception ignored) { }
        }
        return extractOrderId(order);
    }

    private long parseDate(String value) {
        String[] formats = {"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(value);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) { }
        }
        return 0;
    }

    private JSONObject unwrapOrder(JSONObject response) {
        JSONObject order = response.optJSONObject("order");
        if (order != null) return order;
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            order = data.optJSONObject("order");
            return order != null ? order : data;
        }
        return response;
    }

    private List<ProductRow> parseProducts(JSONObject order) {
        List<ProductRow> result = new ArrayList<>();
        JSONArray orderProducts = order.optJSONArray("products");
        JSONArray orderItems = order.optJSONArray("items");
        if (orderProducts == null) orderProducts = orderItems;
        if (orderProducts == null) return result;
        for (int i = 0; i < orderProducts.length(); i++) {
            JSONObject row = orderProducts.optJSONObject(i);
            if (row == null) continue;
            JSONObject data = row.optJSONObject("data");
            JSONObject item = findMatchingItem(orderItems, row);
            JSONObject saleProduct = nestedProduct(item);
            String id = first(row, "item_salesID", "item_sales_id", "itemID", "id");
            if (id.isEmpty() && data != null) id = first(data, "item_salesID", "item_sales_id", "itemID", "id");
            String key = (id.isEmpty() ? "line" : id) + "_" + i;
            String name = first(data, "full_name", "name", "product_name");
            if (name.isEmpty()) name = first(row, "full_name", "name", "product_name");
            if (name.isEmpty()) name = first(saleProduct, "full_name", "name", "title");
            if (name.isEmpty()) name = "מוצר ללא שם";
            String image = first(data, "featured_image", "image", "image_url");
            if (image.isEmpty()) image = first(row, "featured_image", "image", "image_url");
            if (image.isEmpty()) image = first(saleProduct, "featured_image", "image", "image_url");
            if (!image.isEmpty() && !image.startsWith("http://") && !image.startsWith("https://")) {
                image = "https://images.mishnatyosef.org/images/items/" + image;
            }
            String amount = first(row, "amount", "quantity", "qty");
            if (amount.isEmpty() && data != null) amount = first(data, "amount", "quantity", "qty");
            if (amount.isEmpty()) amount = "1";
            String note = first(saleProduct, "note");
            result.add(new ProductRow(key, name, image, amount, note));
        }
        return result;
    }

    private JSONObject findMatchingItem(JSONArray items, JSONObject product) {
        if (items == null) return null;
        String id = first(product, "item_salesID", "item_sales_id", "itemID", "id");
        if (id.isEmpty()) id = first(product.optJSONObject("data"), "item_salesID", "item_sales_id", "itemID", "id");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(first(item, "item_salesID", "item_sales_id", "itemID", "id"))) return item;
        }
        return null;
    }

    private JSONObject nestedProduct(JSONObject item) {
        if (item == null) return null;
        JSONObject itemSale = item.optJSONObject("item_sale");
        return itemSale == null ? null : itemSale.optJSONObject("product");
    }

    private long extractOrderId(JSONObject order) {
        if (order == null) return -1;
        long id = order.optLong("id", order.optLong("order_id", order.optLong("orderID", -1)));
        if (id <= 0 && order.optJSONObject("order") != null) id = extractOrderId(order.optJSONObject("order"));
        if (id <= 0 && order.optJSONObject("data") != null) id = extractOrderId(order.optJSONObject("data"));
        return id;
    }

    private static String first(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
        }
        return "";
    }

    // ---------------------------------------------------------------------
    // Encrypted token storage
    // ---------------------------------------------------------------------

    private void saveEncryptedToken(String token) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        preferences.edit().putString(TOKEN_KEY, value).apply();
    }

    private String readEncryptedToken() {
        String stored = preferences.getString(TOKEN_KEY, "");
        if (stored.isEmpty()) return "";
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) throw new Exception("Invalid token storage");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            clearToken();
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private void clearToken() {
        preferences.edit().remove(TOKEN_KEY).apply();
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------------
    // Small native UI helpers
    // ---------------------------------------------------------------------

    private String safeError(Exception exception) {
        if (exception == null) return "אירעה שגיאה לא צפויה.";
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return "אירעה שגיאה לא צפויה.";
        if (exception instanceof java.net.UnknownHostException || exception instanceof java.net.ConnectException) return "אין חיבור לאינטרנט או לשרת משנת יוסף.";
        if (exception instanceof java.net.SocketTimeoutException) return "השרת לא הגיב בזמן. אפשר לנסות שוב.";
        return message;
    }

    private boolean isAuthError(String message) { return message != null && message.startsWith("AUTH_REQUIRED:"); }

    private static final class RequestSession {
        final int generation;
        volatile String token;
        RequestSession(int generation, String token) {
            this.generation = generation;
            this.token = token == null ? "" : token;
        }
        boolean isCurrent(MainActivity activity) {
            return generation == activity.authGeneration && token.equals(activity.accessToken);
        }
    }

    private static final class HttpResult {
        final int code; final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
    }

    private static final class ProductCard {
        final Ui.CardViews views;
        final LinearLayout row;
        final TextView name;
        ProductRow product;
        ProductCard(Ui.CardViews views, ProductRow product) {
            this.views = views;
            this.row = views.root;
            this.name = views.name;
            this.product = product;
        }
    }

    static final class ProductRow {
        final String key, name, imageUrl, amount, note, imagePath;
        ProductRow(String key, String name, String imageUrl, String amount, String note) {
            this(key, name, imageUrl, amount, note, "");
        }
        ProductRow(String key, String name, String imageUrl, String amount, String note, String imagePath) {
            this.key = key; this.name = name; this.imageUrl = imageUrl; this.amount = amount;
            this.note = note; this.imagePath = imagePath == null ? "" : imagePath;
        }
    }
}
