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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private static final String LOGIN_URL = "https://new.mishnatyosef.org/sign-up";
    private static final String PREFS = "mishnat_pickup_preferences";
    private static final String TOKEN_KEY = "encrypted_access_token";
    private static final String KEY_ALIAS = "mishnat_pickup_auth_key_v1";
    // Unified lavender/purple palette with near-black text throughout the app.
    private static final int BG = Color.rgb(249, 247, 252);
    private static final int INK = Color.rgb(25, 22, 31);
    private static final int MUTED = Color.rgb(68, 63, 76);
    private static final int ORANGE = Color.rgb(105, 72, 157);
    private static final int ORANGE_PALE = Color.rgb(241, 235, 250);
    private static final int GREEN = Color.rgb(105, 72, 157);
    private static final int GREEN_PALE = Color.rgb(241, 235, 250);
    private static final int BORDER = Color.rgb(229, 222, 239);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService imageLoader = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private SharedPreferences preferences;
    private String accessToken = "";
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
    private boolean authHandled = false;
    private boolean loading = false;
    private volatile int authGeneration = 0;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureSystemBars();
        accessToken = readEncryptedToken();
        if (accessToken.isEmpty()) showLoginScreen("");
        else showHomeScreen(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (loginWebView != null) installTokenWatcher();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        network.shutdownNow();
        imageLoader.shutdownNow();
        if (loginWebView != null) {
            loginWebView.stopLoading();
            loginWebView.removeJavascriptInterface("PickupNative");
            loginWebView.destroy();
            loginWebView = null;
        }
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    // ---------------------------------------------------------------------
    // Login. The official site handles both email/password and SMS-code flows.
    // ---------------------------------------------------------------------

    private void showLoginScreen(String notice) {
        authHandled = false;
        loginWebView = new WebView(this);
        loginWebView.setFocusable(true);
        loginWebView.setFocusableInTouchMode(true);
        WebSettings settings = loginWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        loginWebView.setWebChromeClient(new WebChromeClient());
        loginWebView.addJavascriptInterface(new AuthBridge(), "PickupNative");
        loginWebView.setWebViewClient(new WebViewClient() {
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
        });

        LinearLayout root = verticalRoot();
        root.setPadding(dp(18), dp(20), dp(18), dp(16));
        root.addView(text("🧺  איסוף משנת יוסף", 28, INK, true));
        TextView sub = text("מתחברים לחשבון ובוחרים את ההזמנה האחרונה לאיסוף", 15, MUTED, false);
        addTop(root, sub, 6);

        LinearLayout methods = card();
        methods.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView methodTitle = text("אפשר להתחבר בשתי דרכים", 15, INK, true);
        methods.addView(methodTitle);
        TextView methodBody = text("אימייל וסיסמה, או מספר טלפון וקוד חד־פעמי ב־SMS. פרטי הכניסה מטופלים ישירות באתר משנת יוסף.", 13, MUTED, false);
        methodBody.setLineSpacing(dp(3), 1f);
        addTop(methods, methodBody, 5);
        addTop(root, methods, 14);

        if (notice != null && !notice.isEmpty()) {
            TextView warning = text(notice, 13, ORANGE, true);
            addTop(root, warning, 10);
        }

        FrameLayout browserFrame = new FrameLayout(this);
        browserFrame.setBackground(shape(Color.WHITE, 18, BORDER));
        browserFrame.setClipToOutline(true);
        LinearLayout.LayoutParams browserParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        browserParams.topMargin = dp(14);
        root.addView(browserFrame, browserParams);
        browserFrame.addView(loginWebView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        loginWebView.loadUrl(LOGIN_URL);
        loginWebView.post(() -> loginWebView.requestFocus());
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
                "window.__pickupTokenWatch=setInterval(function(){try{" +
                "var t=localStorage.getItem('token');" +
                "if(t&&t.length>12&&window.PickupNative){window.PickupNative.onToken(location.origin,t);clearInterval(window.__pickupTokenWatch);}" +
                "}catch(e){}},800);" +
                "}catch(e){}})();";
        loginWebView.evaluateJavascript(script, null);
    }

    private final class AuthBridge {
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
        authGeneration++;
        try {
            saveEncryptedToken(token);
            accessToken = token;
            if (loginWebView != null) {
                loginWebView.stopLoading();
                loginWebView.removeJavascriptInterface("PickupNative");
                loginWebView.destroy();
                loginWebView = null;
            }
            showHomeScreen(true);
        } catch (Exception e) {
            authHandled = false;
            showLoginScreen("לא ניתן לשמור את ההתחברות בצורה מאובטחת. נסו שוב.");
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
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
        // Keep the progress summary outside the scrolling list so it remains visible.
        LinearLayout screenRoot = verticalRoot();
        screenRoot.setBackgroundColor(BG);
        LinearLayout fixedHeader = verticalRoot();
        fixedHeader.setPadding(dp(18), dp(14), dp(18), 0);
        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setFocusable(false);
        pageScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        pageScroll.setBackgroundColor(BG);
        page = verticalRoot();
        page.setPadding(dp(18), 0, dp(18), dp(24));
        page.setFocusable(false);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        logoutButton = smallButton("התנתקות", Color.WHITE, MUTED);
        logoutButton.setId(View.generateViewId());
        logoutButton.setOnClickListener(v -> confirmLogout());
        top.addView(logoutButton);
        TextView title = text("רשימת איסוף", 24, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.rightMargin = dp(12);
        top.addView(title, titleParams);
        fixedHeader.addView(top);
        addTop(fixedHeader, text("מה חדש: תמיכה בטלפון מקשים, סימון פוקוס והתנתקות משופרת", 12, MUTED, false), 3);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resetButton = smallButton("איפוס איסוף", ORANGE_PALE, ORANGE);
        resetButton.setId(View.generateViewId());
        resetButton.setOnClickListener(v -> confirmResetCollection());
        actions.addView(resetButton);
        addTop(fixedHeader, actions, 8);
        LinearLayout progressCard = card();
        progressCard.setPadding(dp(15), dp(12), dp(15), dp(12));
        addTop(fixedHeader, progressCard, 10);
        LinearLayout progressHead = new LinearLayout(this);
        progressHead.setGravity(Gravity.CENTER_VERTICAL);
        progressHead.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        progressPercent = text("0%", 23, GREEN, true);
        progressPercent.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(dp(58), -2);
        progressPercent.setMinWidth(dp(58));
        percentParams.rightMargin = dp(12);
        progressHead.addView(progressPercent, percentParams);
        progressText = text("נאספו 0 מתוך 0", 14, INK, true);
        progressText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        progressText.setMaxLines(2);
        progressHead.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1f));
        progressCard.addView(progressHead);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(6));
        progressParams.topMargin = dp(8);
        progressCard.addView(progressBar, progressParams);
        addTop(page, sectionLabel("ממתינים לאיסוף", ""), 18);
        pendingList = verticalRoot();
        addTop(page, pendingList, 7);
        collectedSection = verticalRoot();
        collectedSection.addView(sectionLabel("נאספו", ""));
        collectedList = verticalRoot();
        addTop(collectedSection, collectedList, 7);
        addTop(page, collectedSection, 18);
        // Attach the screen content; otherwise the authenticated home screen is blank.
        pageScroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        screenRoot.addView(fixedHeader, new LinearLayout.LayoutParams(-1, -2));
        screenRoot.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(screenRoot);
        if (loadData) loadLatestOrder();
        else renderProducts(false);
    }

    private void confirmResetCollection() {
        if (collected.isEmpty()) return;
        showModernConfirm("איפוס האיסוף", "להחזיר את כל המוצרים לרשימת הממתינים?", "איפוס", () -> {
            collected.clear();
            saveCollectedState();
            renderProducts(false);
            pageScroll.post(this::focusFirstPending);
        });
    }

    private void confirmLogout() {
        showModernConfirm("התנתקות מהחשבון", "לצאת מהחשבון ולמחוק את פרטי ההתחברות השמורים במכשיר?", "התנתקות", this::logout);
    }

    private void showModernConfirm(String title, String message, String positive, Runnable action) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = verticalRoot();
        panel.setPadding(dp(22), dp(20), dp(22), dp(18));
        panel.setBackground(shape(ORANGE_PALE, 22, BORDER));
        TextView heading = text(title, 19, INK, true);
        heading.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        panel.addView(heading);
        TextView body = text(message, 14, INK, false);
        body.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        body.setLineSpacing(dp(3), 1f);
        addTop(panel, body, 9);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = smallButton("ביטול", Color.WHITE, MUTED);
        Button approve = smallButton(positive, ORANGE, INK);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        cancelParams.leftMargin = dp(6);
        buttons.addView(cancel, cancelParams);
        LinearLayout.LayoutParams approveParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        approveParams.rightMargin = dp(6);
        buttons.addView(approve, approveParams);
        addTop(panel, buttons, 18);
        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(360)), -2);
            }
            approve.requestFocus();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        approve.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(360)), -2);
        approve.requestFocus();
    }
    private void loadLatestOrder() {
        if (loading || accessToken.isEmpty()) return;
        loading = true;
        if (messageText != null) messageText.setText("מתחבר לשרת וטוען הזמנה אחרונה…");
        if (pendingList != null && products.isEmpty()) {
            pendingList.removeAllViews();
            pendingList.addView(text("טוען מוצרים…", 14, MUTED, false));
        }
        network.execute(() -> {
            try {
                JSONArray orders = apiGetArray("/api/orders");
                JSONObject latest = chooseLatestPickupOrder(orders);
                if (latest == null) throw new Exception("לא נמצאה הזמנה בחשבון.");
                long id = extractOrderId(latest);
                if (id <= 0) throw new Exception("לא נמצא מזהה להזמנה האחרונה.");
                JSONObject response = apiGetObject("/api/orders/" + id);
                JSONObject order = unwrapOrder(response);
                List<ProductRow> parsed = parseProducts(order);
                if (parsed.isEmpty()) throw new Exception("ההזמנה נמצאה, אבל לא נמצאו בה פריטים להצגה.");
                mainHandler.post(() -> {
                    if (accessToken.isEmpty() || isFinishing()) return;
                    loading = false;
                    orderData = order;
                    currentOrderId = String.valueOf(id);
                    products.clear(); products.addAll(parsed);
                    loadCollectedState();
                            renderProducts(false);
                });
            } catch (Exception e) {
                String msg = safeError(e);
                mainHandler.post(() -> {
                    if (accessToken.isEmpty() || isFinishing()) return;
                    loading = false;
                    if (isAuthError(msg)) {
                        clearToken();
                        accessToken = "";
                        showLoginScreen("פג תוקף ההתחברות. התחברו מחדש כדי להמשיך.");
                    } else {
                        if (messageText != null) messageText.setText(msg);
                        if (pendingList != null) {
                            pendingList.removeAllViews();
                            TextView error = text(msg, 14, ORANGE, true);
                            error.setLineSpacing(dp(3), 1f);
                            pendingList.addView(error);
                            Button retry = smallButton("לנסות שוב", ORANGE_PALE, ORANGE);
                            addTop(pendingList, retry, 10);
                            retry.setOnClickListener(v -> loadLatestOrder());
                        }
                    }
                });
            }
        });
    }

    private void loadCollectedState() {
        collected.clear();
        try {
            JSONArray saved = new JSONArray(preferences.getString("collected_" + currentOrderId, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                String key = saved.optString(i, "");
                if (!key.isEmpty()) collected.add(key);
            }
        } catch (Exception ignored) { }
    }

    private void saveCollectedState() {
        JSONArray saved = new JSONArray();
        for (ProductRow product : products) if (collected.contains(product.key)) saved.put(product.key);
        preferences.edit().putString("collected_" + currentOrderId, saved.toString()).apply();
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setClickable(true);
        row.setTag(product.key);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(9);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(shape(Color.rgb(242, 244, 247), 12, Color.TRANSPARENT));
        image.setClipToOutline(true);
        row.addView(image, new LinearLayout.LayoutParams(dp(70), dp(70)));
        if (!product.imageUrl.isEmpty()) loadProductImage(product.imageUrl, image);
        else image.setImageResource(android.R.drawable.ic_menu_gallery);
        LinearLayout details = verticalRoot();
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        detailsParams.leftMargin = dp(12);
        detailsParams.rightMargin = dp(10);
        row.addView(details, detailsParams);
        TextView name = text(product.name, 15, INK, true);
        name.setMaxLines(3);
        details.addView(name);
        TextView qty = text("כמות: " + product.amount, 13, MUTED, false);
        addTop(details, qty, 5);
        if (!product.note.isEmpty()) addTop(details, text(product.note, 12, MUTED, false), 4);
        ProductCard card = new ProductCard(row, name, product);
        row.setOnClickListener(v -> toggleProduct(product.key));
        row.setLayoutParams(rowParams);
        return card;
    }

    private void bindProductCard(ProductCard card, ProductRow product, boolean done) {
        card.product = product;
        card.row.setBackground(focusBackground(done ? GREEN_PALE : Color.WHITE, 17, done ? Color.rgb(190, 226, 204) : BORDER));
        card.row.setContentDescription((done ? "נאסף: " : "ממתין לאיסוף: ") + product.name + ", כמות " + product.amount);
        card.name.setText(product.name);
        if (done) card.name.setPaintFlags(card.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else card.name.setPaintFlags(card.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
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
    }

    private boolean sameProduct(ProductRow a, ProductRow b) {
        return a != null && b != null && a.key.equals(b.key) && a.name.equals(b.name)
                && a.imageUrl.equals(b.imageUrl) && a.amount.equals(b.amount) && a.note.equals(b.note);
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

    private android.graphics.drawable.Drawable focusBackground(int fill, int radius, int border) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = shape(fill == Color.WHITE ? ORANGE_PALE : GREEN_PALE, radius, ORANGE);
        focused.setStroke(dp(3), ORANGE);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, shape(fill, radius, border));
        return states;
    }

    private void loadProductImage(String imageUrl, ImageView target) {
        Bitmap cached = imageCache.get(imageUrl);
        if (cached != null) { target.setImageBitmap(cached); return; }
        target.setImageResource(android.R.drawable.ic_menu_gallery);
        target.setTag(imageUrl);
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
                    int targetPixels = Math.max(120, dp(78));
                    int sample = 1;
                    while (bounds.outWidth / (sample * 2) >= targetPixels && bounds.outHeight / (sample * 2) >= targetPixels) sample *= 2;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
                    if (bitmap != null) {
                        imageCache.put(imageUrl, bitmap);
                        mainHandler.post(() -> {
                            if (imageUrl.equals(target.getTag())) target.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void logout() {
        authGeneration++;
        loading = false;
        clearToken();
        accessToken = "";
        orderData = null;
        currentOrderId = "";
        products.clear();
        collected.clear();
        productCards.clear();
        if (loginWebView != null) {
            loginWebView.stopLoading();
            loginWebView.clearHistory();
            loginWebView.clearCache(true);
            loginWebView.clearFormData();
            loginWebView.removeJavascriptInterface("PickupNative");
            loginWebView.destroy();
            loginWebView = null;
        }
        WebStorage.getInstance().deleteAllData();
        CookieManager cookies = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= 21) {
            cookies.removeAllCookies(removed -> {
                cookies.flush();
                mainHandler.post(() -> showLoginScreen(""));
            });
        } else {
            cookies.removeAllCookie();
            showLoginScreen("");
        }
    }

    // ---------------------------------------------------------------------
    // API access. The endpoints and order payload shape are taken from the
    // uploaded v6.2 extension and checked against the existing APK Factory client.
    // ---------------------------------------------------------------------

    private JSONArray apiGetArray(String path) throws Exception {
        HttpResult response = authorizedRequest("GET", path, null);
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

    private JSONObject apiGetObject(String path) throws Exception {
        HttpResult response = authorizedRequest("GET", path, null);
        ensureSuccess(response);
        Object parsed = new JSONTokener(response.body).nextValue();
        if (!(parsed instanceof JSONObject)) throw new Exception("פרטי ההזמנה חזרו במבנה לא תקין.");
        return (JSONObject) parsed;
    }

    private HttpResult authorizedRequest(String method, String path, JSONObject body) throws Exception {
        final int requestGeneration = authGeneration;
        final String requestToken = accessToken;
        HttpResult response = rawRequest(method, path, requestToken, body);
        if (response.code == 401 && requestGeneration == authGeneration && !accessToken.isEmpty()) {
            HttpResult refreshed = rawRequest("POST", "/api/refresh", requestToken, new JSONObject().put("remember", 1));
            if (refreshed.code >= 200 && refreshed.code < 300
                    && requestGeneration == authGeneration && !accessToken.isEmpty()) {
                String newToken = extractToken(refreshed.body);
                if (!newToken.isEmpty()) {
                    accessToken = newToken;
                    saveEncryptedToken(newToken);
                    response = rawRequest(method, path, newToken, body);
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

    private LinearLayout verticalRoot() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = verticalRoot();
        layout.setPadding(dp(16), dp(14), dp(16), dp(14));
        layout.setBackground(shape(Color.WHITE, 20, BORDER));
        return layout;
    }

    private TextView sectionLabel(String title, String ignored) {
        return text(title, 19, INK, true);
    }

    private Button smallButton(String title, int background, int foreground) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(foreground);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setBackground(focusBackground(background, 14, background == Color.WHITE ? BORDER : Color.TRANSPARENT));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private void addTop(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String safeError(Exception exception) {
        if (exception == null) return "אירעה שגיאה לא צפויה.";
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return "אירעה שגיאה לא צפויה.";
        if (exception instanceof java.net.UnknownHostException || exception instanceof java.net.ConnectException) return "אין חיבור לאינטרנט או לשרת משנת יוסף.";
        if (exception instanceof java.net.SocketTimeoutException) return "השרת לא הגיב בזמן. אפשר לנסות שוב.";
        return message;
    }

    private boolean isAuthError(String message) { return message != null && message.startsWith("AUTH_REQUIRED:"); }

    private static final class HttpResult {
        final int code; final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
    }

    private static final class ProductCard {
        final LinearLayout row;
        final TextView name;
        ProductRow product;
        ProductCard(LinearLayout row, TextView name, ProductRow product) {
            this.row = row; this.name = name; this.product = product;
        }
    }

    private static final class ProductRow {
        final String key, name, imageUrl, amount, note;
        ProductRow(String key, String name, String imageUrl, String amount, String note) {
            this.key = key; this.name = name; this.imageUrl = imageUrl; this.amount = amount; this.note = note;
        }
    }
}
