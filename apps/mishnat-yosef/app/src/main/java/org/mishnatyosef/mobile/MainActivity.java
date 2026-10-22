package org.mishnatyosef.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.ProgressBar;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;

// Build verification checkpoint: keep this source in the APK build trigger.
public class MainActivity extends Activity {
    private static final String API = "https://v2.mishnatyosef.org";
    private static final String IMG_BASE = "https://images.mishnatyosef.org/images/items/";
    private static final String PREFS = "mishnat_auth";
    private static final String PREF_PENDING_NOTIFICATION_SALE_ID = "pending_notification_sale_id";
    private static final String PREF_PENDING_STOCK_SALE_ID = "pending_stock_sale_id";
    private static final String PREF_PENDING_STOCK_SID = "pending_stock_product_sid";
    private static final long REFRESH_MARGIN_MS = 300000L;

    // ---- centralized semantic palette
    private AppPalette.Theme activePalette = AppPalette.DAY;
    private int ORANGE = activePalette.orange;
    private int ORANGE_DARK = activePalette.orangeDark;
    private int ORANGE_LIGHT = activePalette.orangeLight;
    private int BG = activePalette.background;
    private int SURFACE = activePalette.surface;
    private int INK = activePalette.textPrimary;
    private int MUTED = activePalette.textSecondary;
    private int LINE = activePalette.border;
    private int GREEN = activePalette.success;
    private int GREEN_LIGHT = activePalette.successSurface;
    private int RED = activePalette.error;
    private int RED_LIGHT = activePalette.errorSurface;
    private int ON_ORANGE = activePalette.textOnAccent;
    private int HEADER_BG = activePalette.headerBackground;
    private int HEADER_FG = activePalette.headerForeground;
    private int MINUS_FILL = activePalette.minusFill;
    private int STAR_BG = activePalette.starBackground;
    private int NO_ORDER_BG = activePalette.noOrderBackground;
    private int SWITCH_OFF_THUMB = activePalette.switchThumbOff;
    private int SWITCH_OFF_TRACK = activePalette.switchTrackOff;
    private boolean nightMode = false;
    private boolean purpleTheme = false;
    // Global UI scale: text follows Android fontScale; dp-based layout metrics scale in dp().
    private float uiScale = 1.0f;
    private float uiScaleAtActivityStart = 1.0f;
    // Preserve the open-sales list position while navigating to other bottom tabs.
    private int salesListScrollY = 0;
    private boolean showingSalesList = false;
    // Preserve the real ListView position when leaving the order screen.
    private int orderListFirstVisiblePosition = 0;
    private int orderListTopOffset = 0;
    private boolean showingOrderScreen = false;
    private boolean returnToOrderScreenOnSalesTab = false;
    // Closed orders reuse the regular product grid; order-changing controls are visible but disabled.
    private boolean closedOrderReadOnly = false;
    // Shared overlay keeps keyboard focus visible without replacing each view's own background.
    private View keyboardFocusHighlightView;
    private Drawable keyboardFocusHighlightDrawable;
    private View.OnLayoutChangeListener keyboardFocusHighlightLayoutListener;

    private void applyPalette() {
        nightMode = prefs.getBoolean("night_mode", false);
        purpleTheme = prefs.getBoolean("purple_theme", false);
        activePalette = AppPalette.forTheme(nightMode, purpleTheme);
        ORANGE = activePalette.orange;
        ORANGE_DARK = activePalette.orangeDark;
        ORANGE_LIGHT = activePalette.orangeLight;
        BG = activePalette.background;
        SURFACE = activePalette.surface;
        INK = activePalette.textPrimary;
        MUTED = activePalette.textSecondary;
        LINE = activePalette.border;
        GREEN = activePalette.success;
        GREEN_LIGHT = activePalette.successSurface;
        RED = activePalette.error;
        RED_LIGHT = activePalette.errorSurface;
        ON_ORANGE = activePalette.textOnAccent;
        HEADER_BG = activePalette.headerBackground;
        HEADER_FG = activePalette.headerForeground;
        MINUS_FILL = activePalette.minusFill;
        STAR_BG = activePalette.starBackground;
        NO_ORDER_BG = activePalette.noOrderBackground;
        SWITCH_OFF_THUMB = activePalette.switchThumbOff;
        SWITCH_OFF_TRACK = activePalette.switchTrackOff;
    }

    /**
     * Snapshot of the palette before a live day/night switch. Existing view trees are recolored
     * in place so scroll offsets, search text/focus, open tab, and expanded screens stay untouched.
     */
    private final class PaletteSnapshot {
        final int orange = ORANGE, orangeDark = ORANGE_DARK, orangeLight = ORANGE_LIGHT;
        final int bg = BG, surface = SURFACE, ink = INK, muted = MUTED, line = LINE;
        final int green = GREEN, greenLight = GREEN_LIGHT, red = RED, redLight = RED_LIGHT;
        final int onOrange = ON_ORANGE, headerBg = HEADER_BG, headerFg = HEADER_FG;
        final int minusFill = MINUS_FILL, starBg = STAR_BG, noOrderBg = NO_ORDER_BG;
        final int switchOffThumb = SWITCH_OFF_THUMB, switchOffTrack = SWITCH_OFF_TRACK;
    }

    private int remapPaletteColor(int color, PaletteSnapshot old) {
        if (color == old.headerFg) return HEADER_FG;
        if (color == old.orange) return ORANGE;
        if (color == old.orangeDark) return ORANGE_DARK;
        if (color == old.orangeLight) return ORANGE_LIGHT;
        if (color == old.bg) return BG;
        if (color == old.ink) return INK;
        if (color == old.muted) return MUTED;
        if (color == old.line) return LINE;
        if (color == old.green) return GREEN;
        if (color == old.greenLight) return GREEN_LIGHT;
        if (color == old.red) return RED;
        if (color == old.redLight) return RED_LIGHT;
        if (color == old.onOrange) return ON_ORANGE;
        // The inactive bottom-navigation icons use this dedicated dark-brown color.
        if (color == AppPalette.NAV_INACTIVE_DAY || color == AppPalette.NAV_INACTIVE_PURPLE) return INK;
        return color;
    }

    private int remapDrawableColor(int color, PaletteSnapshot old) {
        // White surfaces (including history-sale cards) must become dark surfaces at night.
        if (color == old.surface) return SURFACE;
        if (color == old.headerBg) return HEADER_BG;
        if (color == old.minusFill) return MINUS_FILL;
        if (color == old.starBg) return STAR_BG;
        if (color == old.noOrderBg) return NO_ORDER_BG;
        if (color == old.switchOffThumb) return SWITCH_OFF_THUMB;
        if (color == old.switchOffTrack) return SWITCH_OFF_TRACK;
        return remapPaletteColor(color, old);
    }

    private void rethemeDrawable(Drawable drawable, PaletteSnapshot old) {
        if (drawable == null) return;
        if (drawable instanceof ColorDrawable) {
            ColorDrawable colorDrawable = (ColorDrawable) drawable;
            colorDrawable.setColor(remapDrawableColor(colorDrawable.getColor(), old));
            return;
        }
        if (drawable instanceof AppGradientDrawable) {
            AppGradientDrawable gradient = (AppGradientDrawable) drawable;
            gradient.appFillColor = remapDrawableColor(gradient.appFillColor, old);
            gradient.setColor(gradient.appFillColor);
            if (gradient.hasAppStroke) {
                gradient.appStrokeColor = remapPaletteColor(gradient.appStrokeColor, old);
                gradient.setStroke(gradient.appStrokeWidth, gradient.appStrokeColor);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 24 && drawable instanceof GradientDrawable) {
            // Non-app custom shapes do not retain their original stroke metadata; recolor their fill.
            GradientDrawable gradient = (GradientDrawable) drawable;
            ColorStateList fill = gradient.getColor();
            if (fill != null) gradient.setColor(ColorStateList.valueOf(
                    remapDrawableColor(fill.getDefaultColor(), old)));
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && drawable instanceof RippleDrawable) {
            RippleDrawable ripple = (RippleDrawable) drawable;
            ripple.setColor(ColorStateList.valueOf(Color.argb(AppPalette.RIPPLE_ALPHA,
                    Color.red(ORANGE), Color.green(ORANGE), Color.blue(ORANGE))));
            for (int i = 0; i < ripple.getNumberOfLayers(); i++) {
                rethemeDrawable(ripple.getDrawable(i), old);
            }
        }
    }

    private void rethemeViewTree(View view, PaletteSnapshot old, Set<View> visited) {
        if (view == null || !visited.add(view)) return;
        rethemeDrawable(view.getBackground(), old);
        if ("historySaleCard".equals(view.getTag())) {
            view.setBackground(ripple(shape(nightMode ? Color.BLACK : SURFACE, 14, LINE), 14));
        } else if ("openSaleCard".equals(view.getTag())) {
            view.setBackground(ripple(shape(nightMode ? Color.BLACK : SURFACE, 14,
                    nightMode ? Color.BLACK : LINE), 14));
        }
        if ("appHeader".equals(view.getTag())) view.setBackgroundColor(HEADER_BG);
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(remapPaletteColor(textView.getCurrentTextColor(), old));
            if (textView.getCurrentTextColor() == old.onOrange) textView.setTextColor(ON_ORANGE);
            if ("appHeaderText".equals(textView.getTag())) textView.setTextColor(HEADER_FG);
            textView.setHintTextColor(remapPaletteColor(textView.getHintTextColors().getDefaultColor(), old));
            if ("openSaleTitle".equals(textView.getTag())) {
                textView.setTextColor(nightMode ? Color.WHITE : INK);
            } else if ("openSaleInfo".equals(textView.getTag())) {
                // Keep the compact chip's existing height/padding so the theme switch cannot
                // shift the sales list vertically.
                textView.setTextColor(ORANGE_DARK);
                textView.setBackground(ripple(shape(
                        nightMode ? ORANGE_LIGHT : Color.TRANSPARENT,
                        ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
            }
            if ("colorThemeOrangeOption".equals(textView.getTag())) {
                styleColorThemeOption(textView, false);
            } else if ("colorThemePurpleOption".equals(textView.getTag())) {
                styleColorThemeOption(textView, true);
            }
            if ("nightModeToggle".equals(textView.getTag())) {
                textView.setText(nightToggleLabel());
                textView.setContentDescription(nightMode ? "מעבר למצב יום" : "הפעלת מצב לילה");
                textView.setTextColor(Color.BLACK);
                textView.setBackground(ripple(nightToggleBackground(), 15));
                textView.setElevation(dp(2));
            }
        }
        if (view instanceof Switch) tintSwitch((Switch) view);
        if (view instanceof IconView) {
            IconView icon = (IconView) view;
            icon.setIconColor(remapPaletteColor(icon.getIconColor(), old));
            if (icon.getIconColor() == old.onOrange) icon.setIconColor(ON_ORANGE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                rethemeViewTree(group.getChildAt(i), old, visited);
            }
        }
        view.invalidate();
    }

    private void showKeyboardFocusHighlight(View focusedView) {
        if (keyboardFocusHighlightView != null) {
            if (keyboardFocusHighlightDrawable != null) {
                keyboardFocusHighlightView.getOverlay().remove(keyboardFocusHighlightDrawable);
            }
            if (keyboardFocusHighlightLayoutListener != null) {
                keyboardFocusHighlightView.removeOnLayoutChangeListener(keyboardFocusHighlightLayoutListener);
            }
        }
        keyboardFocusHighlightView = null;
        keyboardFocusHighlightDrawable = null;
        keyboardFocusHighlightLayoutListener = null;
        if (focusedView == null || !focusedView.isFocusable() || !focusedView.isShown()
                || focusedView.getWidth() <= 0 || focusedView.getHeight() <= 0) return;

        // Draw above the normal background so custom buttons always get a translucent fill and frame.
        GradientDrawable highlight = shape(Color.argb(AppPalette.FOCUS_FILL_ALPHA, Color.red(ORANGE), Color.green(ORANGE), Color.blue(ORANGE)), 10,
                AppPalette.FOCUS_BORDER, 4);
        keyboardFocusHighlightView = focusedView;
        keyboardFocusHighlightDrawable = highlight;
        keyboardFocusHighlightLayoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (keyboardFocusHighlightDrawable == highlight) {
                highlight.setBounds(0, 0, view.getWidth(), view.getHeight());
                highlight.invalidateSelf();
            }
        };
        highlight.setBounds(0, 0, focusedView.getWidth(), focusedView.getHeight());
        focusedView.addOnLayoutChangeListener(keyboardFocusHighlightLayoutListener);
        focusedView.getOverlay().add(highlight);
        focusedView.invalidate();
    }

    private void refreshKeyboardFocusHighlight() {
        View focused = getCurrentFocus();
        if (focused != null) showKeyboardFocusHighlight(focused);
    }

    private void toggleNightMode() {
        PaletteSnapshot old = new PaletteSnapshot();
        prefs.edit().putBoolean("night_mode", !nightMode).apply();
        applyPalette();
        setTheme(nightMode ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar); // dialogs follow the mode too
        rethemeActiveUi(old);
    }

    private void rethemeActiveUi(PaletteSnapshot old) {
        Set<View> visited = new HashSet<>();
        rethemeViewTree(currentScreen, old, visited);
        // Cached tabs and product rows must change too, or old colors can reappear on navigation/scroll.
        Set<ProductAdapter> adapters = new HashSet<>();
        if (productAdapter != null) adapters.add(productAdapter);
        for (TabScreenState state : tabScreenCache.values()) {
            if (state != null) {
                rethemeViewTree(state.screen, old, visited);
                if (state.productAdapter != null) adapters.add(state.productAdapter);
            }
        }
        for (ProductAdapter adapter : adapters) adapter.rethemeCachedRows(old, visited);
        getWindow().setStatusBarColor(HEADER_BG);
        getWindow().setNavigationBarColor(SURFACE);
        styleSystemBars();
        refreshKeyboardFocusHighlight();
    }

    private void changeColorTheme(boolean usePurple) {
        if (purpleTheme == usePurple) return;
        PaletteSnapshot old = new PaletteSnapshot();
        prefs.edit().putBoolean("purple_theme", usePurple).apply();
        applyPalette();
        rethemeActiveUi(old);
        toast(usePurple ? "ערכת הסגול הופעלה" : "ערכת הכתום הופעלה", false);
    }

    private void styleColorThemeOption(TextView option, boolean usePurple) {
        boolean selected = purpleTheme == usePurple;
        int swatch = usePurple ? AppPalette.PURPLE_SWATCH : AppPalette.ORANGE_SWATCH;
        option.setText((selected ? "✓  " : "") + (usePurple ? "סגול מלכותי" : "כתום קלאסי"));
        option.setTextColor(Color.WHITE);
        option.setGravity(Gravity.CENTER);
        option.setBackground(ripple(shape(swatch, 12, selected ? ORANGE : LINE, selected ? 3 : 1), 12));
        option.setElevation(selected ? dp(2) : 0);
        option.setPadding(dp(8), dp(10), dp(8), dp(10));
        option.setContentDescription((usePurple ? "סגול מלכותי" : "כתום קלאסי")
                + (selected ? ", ערכת הצבעים הנבחרת" : ", בחירת ערכת צבעים"));
        option.setSelected(selected);
    }

    // ---- button styles
    private static final int PRIMARY = 0, TONAL = 1, OUTLINE = 2, DANGER = 3, FLAT = 4;

    private static final String[] HEB_DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();   // product loading (keeps `executor` free for order updates)
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(4);          // parallel product pages
    private final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "img-prefetch");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LruCache<String, Bitmap> imageCache;
    private SharedPreferences prefs;

    // ---- current screen
    private View currentScreen;
    private int activeBottomTab = 0;
    private final Map<Integer, TabScreenState> tabScreenCache = new LinkedHashMap<>();
    private LinearLayout root;
    private FrameLayout bottomHolder;
    private ScrollView scrollView;
    private TextView statusView;
    private EditText emailField;
    private EditText passwordField;
    private TextView loginButton;
    // In-app updater state; download and installation remain user-confirmed.
    private TextView updateStatusView, updateNotesView, updateCheckButton, updateDownloadButton, updateInstallButton;
    private ProgressBar updateProgressBar;
    private boolean updateDownloadInProgress = false;
    private int updateLatestCode = 0;
    private String updateLatestName = "", updateLatestSha256 = "", updateReleaseNotes = "";
    private boolean updateDownloadVerifying = false;
    private boolean autoDownloadAfterCheck = false;
    private static final String UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/mishnat-yosef/update.json";
    private static final String UPDATE_APK_URL = "https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/mishnat-yosef/app.apk";
    private boolean busy = false;
    private Runnable backAction = null;
    private int screenToken = 0;
    private EditText activeProductSearch;
    private View productSearchClearButton;
    private android.app.Dialog imageViewerDialog;
    private SeekBar actionScaleBar;
    private TextView actionScaleValue;
    private boolean simpleKeyboardNavigation() { return prefs.getBoolean("simple_keyboard_navigation", true); }

    // ---- ordering state
    private JSONObject featureSale = null;
    private long featureOrderId = -1;
    private final List<JSONObject> featureProducts = new ArrayList<>();
    private final Map<String, Integer> featureQuantities = new LinkedHashMap<>();
    private final Map<String, Integer> stockDraftQuantities = new LinkedHashMap<>();
    private final Set<String> pending = new HashSet<>();
    private String filterText = "";
    private ListView productList;
    private ProductAdapter productAdapter;
    private final List<GridRow> gridRows = new ArrayList<>();
    private boolean productsLoading = false;
    private boolean productsPartial = false;
    private boolean emptyMsgShown = false;
    private long productsLoadedAt = 0L;
    private int currentSaleId = -1; // sale whose products are in featureProducts right now (not a history order)
    private final Map<Integer, SaleSnapshot> saleSnapshots = new LinkedHashMap<>();
    private JSONObject favQtyCache = null;
    private static final long STALE_MS = 5L * 60L * 1000L;
    private static final int PAGE_LIMIT = 100;
    // When page refresh is disabled, update only the changed product quantity and the order bar.
    private String lastChangedProductSid = null;
    private final Runnable productsRefresh = () -> {
        if (prefs != null && prefs.getBoolean("experimental_skip_cached_quantity_grid_rebuild", false)
                && lastChangedProductSid != null && !lastChangedProductSid.isEmpty()) {
            View targetCard = findTaggedView(currentScreen, "productCard:" + lastChangedProductSid);
            View quantityView = findTaggedView(targetCard, "productQty");
            Integer quantity = featureQuantities.get(lastChangedProductSid);
            if (quantityView instanceof TextView && quantity != null) {
                ((TextView) quantityView).setText(String.valueOf(quantity));
            }
            updateOrderBar();
            return;
        }
        refreshVisibleProductRows();
        updateOrderBar();
    };
    private TextView barInfo, barTotal;
    private View orderSummaryButton;

    // ---- order summary state
    private OrderData summary = null;
    private boolean collectMode = false;
    private boolean summaryFromHistory = false;
    private int historyTab = 1;
    private static final int TAB_COLLECT = 4;
    private static final int ICON_CART = 0, ICON_CLOCK = 1, ICON_WALLET = 2, ICON_PERSON = 3, ICON_CHECK = 4;
    private LinearLayout summaryList, collectedBox, collectedList;
    private TextView summaryTotal, summarySub, progressView;
    private boolean collectionCelebrationShown = false;

    // ---- history state
    private int histPage = 0, histLast = 1;
    private String histSearch = "";
    private LinearLayout histList;
    private TextView histMore;
    private boolean histLoading = false;
    private int histRequestId = 0;

    // ---- favourites / screens / files
    private final Set<String> favIds = new HashSet<>();
    private String viewMode = "all";
    private int screenKind = 0; // 1 = products, 2 = order summary
    private String pendingExport = "";
    private static final int REQ_EXPORT = 7101, REQ_IMPORT = 7102;
    private final List<String> apiLog = Collections.synchronizedList(new ArrayList<String>());
    private boolean refreshDead = false;
    private FrameLayout topHolder;
    private LinearLayout catStripRow;
    private HorizontalScrollView catStripScroll;
    private final Map<String, Integer> catHeaders = new LinkedHashMap<>(); // category -> row index in the list
    private final Map<String, TextView> catChips = new LinkedHashMap<>();
    private final Map<String, Integer> catCounts = new LinkedHashMap<>();
    private String activeCat = "";
    private String pendingCategoryJump = "";

    // ======================================================================== data classes

    /**
     * Retain each tab's live view tree and associated state. Reusing the same view hierarchy
     * preserves scroll, input text, filters, local UI modes, and expanded content.
     */
    private static class TabScreenState {
        View screen;
        LinearLayout root;
        ScrollView scrollView;
        FrameLayout topHolder, bottomHolder;
        TextView statusView, barInfo, barTotal, summaryTotal, summarySub, progressView, histMore;
        EditText activeProductSearch;
        View productSearchClearButton;
        ListView productList;
        ProductAdapter productAdapter;
        LinearLayout histList, summaryList, collectedBox, collectedList, catStripRow;
        HorizontalScrollView catStripScroll;
        Runnable backAction;
        int screenKind;
        boolean showingSalesList, showingOrderScreen;
        int salesListScrollY, orderListFirstVisiblePosition, orderListTopOffset;
        boolean returnToOrderScreenOnSalesTab;
        JSONObject featureSale, favQtyCache;
        long featureOrderId;
        int currentSaleId;
        List<JSONObject> featureProducts;
        Map<String, Integer> featureQuantities;
        Set<String> pending, favIds;
        Map<Integer, SaleSnapshot> saleSnapshots;
        String filterText, viewMode, activeCat, histSearch;
        int histPage, histLast, historyTab;
        boolean productsLoading, productsPartial, emptyMsgShown, summaryFromHistory, collectMode;
        long productsLoadedAt;
        OrderData summary;
        List<GridRow> gridRows;
        Map<String, Integer> catHeaders, catCounts;
        Map<String, TextView> catChips;
    }

    private static class HttpResult {
        final int code;
        final String body;
        final String newToken;
        HttpResult(int code, String body, String newToken) { this.code = code; this.body = body; this.newToken = newToken; }
    }

    private static class ImportEntry {
        String pid = "";
        String name = "";
        String query = null;
        double amount = 1;
    }

    private static class PlanItem {
        JSONObject p;
        String name = "";
        int amount;
        int cur;
    }

    private static class AuthException extends Exception {
        AuthException() { super("נדרשת התחברות מחדש"); }
    }

    private static class OrderLine {
        String sid = "";
        String name = "";
        double price = 0;
        double amount = 0;
        JSONObject raw = new JSONObject();
    }

    private static class OrderData {
        long id;
        String status = "";
        String when = "";
        String saleName = "";
        long saleId = 0;
        double total = -1;
        double credits = 0;
        final List<OrderLine> lines = new ArrayList<>();
    }

    private static class GridRow {
        String header;     // category title row when not null
        JSONObject a, b, c, d, e;   // otherwise up to five product cards
    }

    private static class SaleSnapshot {
        long orderId = -1;
        long loadedAt = 0L;
        String saleName = "";
        final List<JSONObject> products = new ArrayList<>();
        final Map<String, Integer> quantities = new LinkedHashMap<>();
        final Set<String> favs = new HashSet<>();
    }

    private interface PageCallback { void onPage(List<JSONObject> items, int total); }

    private static class Stepper {
        LinearLayout view;
        TextView minus, plus, qty;
    }

    // ======================================================================== lifecycle




    /** Re-binds the rows currently on screen in place. Unlike notifyDataSetChanged() this never makes the
     *  ListView re-take focus, so the focused +/- button keeps focus and the list does not jump. */
    private void refreshVisibleProductFocusVisuals() {
        if (productList == null) return;
        for (int i = 0; i < productList.getChildCount(); i++) {
            View rowView = productList.getChildAt(i);
            if (rowView == null || !(rowView.getTag() instanceof RowHolder)) continue;
            RowHolder holder = (RowHolder) rowView.getTag();
            for (CardHolder card : new CardHolder[]{holder.first, holder.second, holder.third, holder.fourth, holder.fifth}) {
                if (card.p != null && card.card.getVisibility() == View.VISIBLE) card.updateFocusVisuals();
            }
        }
    }

    private void refreshVisibleProductRows() {
        if (productList == null || productAdapter == null) return;
        View focusedBeforeRefresh = getCurrentFocus();
        CardHolder focusedHolder = findProductCardHolder(focusedBeforeRefresh);
        String focusedSid = focusedHolder != null && focusedHolder.p != null ? sidOf(focusedHolder.p) : null;
        String focusedRole = navigationRole(focusedBeforeRefresh);
        int first = productList.getFirstVisiblePosition();
        int count = productList.getChildCount();
        for (int i = 0; i < count; i++) {
            View rowView = productList.getChildAt(i);
            int position = first + i;
            if (rowView == null || position < 0 || position >= gridRows.size()) continue;
            if (!(rowView.getTag() instanceof RowHolder)) continue;
            if (productAdapter.getItemViewType(position) != 1) continue;
            productAdapter.getView(position, rowView, productList);
        }
        if (focusedSid != null) {
            final String restoreSid = focusedSid;
            final String restoreRole = focusedRole;
            productList.post(() -> {
                if (productList == null || !showingOrderScreen) return;
                CardHolder targetCard = findVisibleProductCardBySid(restoreSid);
                if (targetCard == null) return;
                View target = navigationTargetForRole(targetCard, restoreRole);
                if (!requestProductFocus(target)) requestProductFocus(targetCard.card);
            });
        }
    }

    private CardHolder findVisibleProductCardBySid(String sid) {
        if (productList == null || sid == null) return null;
        for (int i = 0; i < productList.getChildCount(); i++) {
            View row = productList.getChildAt(i);
            if (row == null || !(row.getTag() instanceof RowHolder)) continue;
            RowHolder holder = (RowHolder) row.getTag();
            for (CardHolder card : new CardHolder[]{holder.first, holder.second, holder.third, holder.fourth, holder.fifth}) {
                if (card.p != null && sid.equals(sidOf(card.p)) && card.card.getVisibility() == View.VISIBLE) return card;
            }
        }
        return null;
    }

    /** Closest control inside the same card in the pressed direction (e.g. + -> qty -> - on one line). */
    /** Which control of a card to land on when arriving from another card. */
    /** Scrolls the target product row into view if needed, then puts focus on the right control inside it. */
    /** Scroll independently of the currently focused button or product. */
    /** Focus the active bottom tab; # and * shortcuts switch tabs immediately. */
    private View findTaggedView(View view, String tag) {
        if (view == null) return null;
        if (tag.equals(view.getTag())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedView(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }
    /**
     * Product-screen D-pad navigation uses the real view tree and spatial focus search.
     * If Android cannot find a target in the requested direction among attached views, move
     * one adapter row and retry with the same control role. This avoids jumping to unrelated
     * toolbar controls and supports product rows that are not currently attached to the ListView.
     */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (handleGlobalKeyboardEvent(event)) return true;
        int key = event.getKeyCode();
        boolean direction = key == android.view.KeyEvent.KEYCODE_DPAD_UP
                || key == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                || key == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN || !direction
                || screenKind != 1 || productList == null) {
            return super.dispatchKeyEvent(event);
        }
        View current = getCurrentFocus();
        if (activeProductSearch != null && activeProductSearch.hasFocus()
                && isSoftKeyboardVisible()) {
            return super.dispatchKeyEvent(event);
        }
        // Keep the existing path from the order summary back into product navigation.
        if (!(current == orderSummaryButton && key == android.view.KeyEvent.KEYCODE_DPAD_UP)) {
            // Toolbar-only spatial navigation; the product-grid handler below stays unchanged.
            if (showingOrderScreen && isOrderToolbarView(current)) {
                if (moveFocusWithinOrderToolbar(current, key)) return true;
            }
        }
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && showingOrderScreen && catStripRow != null
                && isDescendantOf(current, catStripRow)) {
            if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                return moveFocusWithinCategoryStrip(current, key);
            }
            if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    || key == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                if (orderSummaryButton != null && orderSummaryButton.isShown()) {
                    requestProductFocus(orderSummaryButton);
                    return true;
                }
                View firstProduct = firstProductNavigationTarget();
                if (firstProduct != null) requestProductFocus(firstProduct);
                return firstProduct != null;
            }
        }
        if (current == orderSummaryButton && key == android.view.KeyEvent.KEYCODE_DPAD_UP) {
            return moveFocusFromOrderSummaryToLastProduct();
        }
        if (current == null || !isProductNavigationView(current)) {
            // Don't hijack D-pad navigation from the toolbar or other controls into the product grid.
            return super.dispatchKeyEvent(event);
        }
        CardHolder focusedCard = findProductCardHolder(current);
        if (focusedCard == null) return true;
        String role = navigationRole(current);
        int[] location = findCardRowAndColumn(focusedCard);
        if (location == null) return true;
        int rowPosition = location[0], column = location[1];

        if ((key == android.view.KeyEvent.KEYCODE_DPAD_LEFT || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                && !simpleKeyboardNavigation() && isStepperRole(role)) {
            if (moveWithinProductControls(focusedCard, current, role, key)) return true;
            moveFocusHorizontallyToCard(rowPosition, column,
                    key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? -1 : 1, role);
            return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveFocusHorizontallyToCard(rowPosition, column,
                    key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? -1 : 1,
                    simpleKeyboardNavigation() ? "card" : role);
            return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_DPAD_UP) {
            String previousLayer = previousVerticalRole(focusedCard, role);
            if (previousLayer != null) requestProductFocus(navigationTargetForRole(focusedCard, previousLayer));
            else moveFocusToProductRow(rowPosition, column, -1, "card");
            return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
            String nextLayer = nextVerticalRole(focusedCard, role);
            if (nextLayer != null) requestProductFocus(navigationTargetForRole(focusedCard, nextLayer));
            else moveFocusToProductRow(rowPosition, column, 1, "card");
            return true;
        }
        return true;
    }

    // Keyboard contract: global shortcuts are handled before product D-pad navigation.
    // Repeated ACTION_DOWN events are intentionally ignored for one-shot commands.
    private boolean handleGlobalKeyboardEvent(android.view.KeyEvent event) {
        if (event == null) return false;
        int key = event.getKeyCode();
        int action = event.getAction();

        // The image viewer is modal: any key press dismisses it before other shortcuts run.
        if (isImageViewerShowing()) {
            if (action == android.view.KeyEvent.ACTION_DOWN) dismissImageViewer();
            return action == android.view.KeyEvent.ACTION_DOWN || action == android.view.KeyEvent.ACTION_UP;
        }
        if (action != android.view.KeyEvent.ACTION_DOWN) return false;

        if (key == android.view.KeyEvent.KEYCODE_SEARCH && activeProductSearch != null && activeProductSearch.hasFocus()) {
            hideKeyboard();
            activeProductSearch.clearFocus();
            return true;
        }
        // A physical Menu key always opens the appropriate menu, even if a text field
        // currently owns focus. On order screens, use that screen's own three-dot menu.
        if (key == android.view.KeyEvent.KEYCODE_MENU) {
            openKeyboardMenu();
            return true;
        }
        boolean textEditing = isTextEditorFocused() && isSoftKeyboardVisible();
        if (textEditing) return false;

        View focusedForKeyboard = getCurrentFocus();
        if (focusedForKeyboard instanceof SeekBar && focusedForKeyboard == actionScaleBar) {
            int progress = actionScaleBar.getProgress();
            int next = progress;
            if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                next += key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            } else if (key == android.view.KeyEvent.KEYCODE_PAGE_UP) next += 4;
            else if (key == android.view.KeyEvent.KEYCODE_PAGE_DOWN) next -= 4;
            else if (key == android.view.KeyEvent.KEYCODE_MOVE_HOME) next = 0;
            else if (key == android.view.KeyEvent.KEYCODE_MOVE_END) next = actionScaleBar.getMax();
            else next = -1;
            if (next >= 0) {
                next = Math.max(0, Math.min(actionScaleBar.getMax(), next));
                actionScaleBar.setProgress(next);
                changeUiScale(70 + next * 5);
                return true;
            }
        }
        boolean shiftedThree = key == android.view.KeyEvent.KEYCODE_3 && event.isShiftPressed();
        boolean shiftedEight = key == android.view.KeyEvent.KEYCODE_8 && event.isShiftPressed();
        boolean repeated = event.getRepeatCount() > 0;
        boolean repeatable = key == android.view.KeyEvent.KEYCODE_1
                || key == android.view.KeyEvent.KEYCODE_2
                || (key == android.view.KeyEvent.KEYCODE_3 && !shiftedThree)
                || (key == android.view.KeyEvent.KEYCODE_8 && !shiftedEight);
        if (repeated && !repeatable) {
            if (key == android.view.KeyEvent.KEYCODE_4 || key == android.view.KeyEvent.KEYCODE_5
                    || key == android.view.KeyEvent.KEYCODE_6 || key == android.view.KeyEvent.KEYCODE_0
                    || key == android.view.KeyEvent.KEYCODE_7 || key == android.view.KeyEvent.KEYCODE_9) return true;
            return false;
        }

        // Some physical keyboards report the printed * and # keys with swapped
        // Android key codes. Keep the user's intended symbol behavior consistent:
        // # goes to the previous tab, * to the next tab; + always goes to the next tab.
        if (key == android.view.KeyEvent.KEYCODE_STAR || key == android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY || shiftedEight) {
            cycleKeyboardTab(1); return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_POUND || shiftedThree) {
            cycleKeyboardTab(-1); return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_PLUS || key == android.view.KeyEvent.KEYCODE_NUMPAD_ADD) {
            cycleKeyboardTab(1); return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_1) { scrollCurrentScreenByPage(-1); return true; }
        if (key == android.view.KeyEvent.KEYCODE_3) { scrollCurrentScreenByPage(1); return true; }
        if (key == android.view.KeyEvent.KEYCODE_2) { scrollCurrentScreenToEdge(false); return true; }
        if (key == android.view.KeyEvent.KEYCODE_8) { scrollCurrentScreenToEdge(true); return true; }
        if (key == android.view.KeyEvent.KEYCODE_4) { changeUiScale(prefs.getInt("ui_scale_percent", 100) - 5); return true; }
        if (key == android.view.KeyEvent.KEYCODE_6) { changeUiScale(prefs.getInt("ui_scale_percent", 100) + 5); return true; }
        if (key == android.view.KeyEvent.KEYCODE_5) { commitCardColumns(cardColumns() + 1 > 5 ? 1 : cardColumns() + 1, false); return true; }
        if (key == android.view.KeyEvent.KEYCODE_0) {
            changeUiScale(100);
            commitCardColumns(3, false);
            if (nightMode) toggleNightMode();
            return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_7) { jumpCategoryBy(-1); return true; }
        if (key == android.view.KeyEvent.KEYCODE_9) { jumpCategoryBy(1); return true; }
        if (key == android.view.KeyEvent.KEYCODE_SOFT_LEFT) {
            openProductDetailsOrMoreOptions(); return true;
        }
        if (key == android.view.KeyEvent.KEYCODE_SOFT_RIGHT) {
            if (orderSummaryButton != null && orderSummaryButton.isShown()) orderSummaryButton.performClick();
            else openProductDetailsOrMoreOptions();
            return true;
        }
        return false;
    }

    private boolean isTextEditorFocused() {
        View focus = getCurrentFocus();
        return focus instanceof EditText || (focus != null && focus.onCheckIsTextEditor());
    }

    private boolean isSoftKeyboardVisible() {
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        if (decor == null) return false;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsets wi = decor.getRootWindowInsets();
            return wi != null && wi.isVisible(WindowInsets.Type.ime());
        }
        android.graphics.Rect visible = new android.graphics.Rect();
        decor.getWindowVisibleDisplayFrame(visible);
        return decor.getRootView().getHeight() - visible.bottom > dp(160);
    }

    private void cycleKeyboardTab(int direction) {
        final int[] order = {0, 1, TAB_COLLECT, 2, 3};
        int index = 0;
        for (int i = 0; i < order.length; i++) if (order[i] == activeBottomTab) { index = i; break; }
        int next = order[(index + direction + order.length) % order.length];
        View tab = findViewWithTag(getWindow().getDecorView(), "navTab:" + next);
        if (tab != null) {
            tab.performClick();
            return;
        }
        // Nested screens may not render the bottom bar; keep keyboard tab cycling global.
        switch (next) {
            case 0: navigateTab(0, this::showOpenSales); break;
            case 1: navigateTab(1, this::loadFeatureOrders); break;
            case TAB_COLLECT: navigateTab(TAB_COLLECT, this::openLatestCollect); break;
            case 2: navigateTab(2, this::loadFeatureTransactions); break;
            case 3: navigateTab(3, this::showAccount); break;
        }
    }

    private View findViewWithTag(View rootView, String tag) {
        if (rootView == null) return null;
        if (tag.equals(rootView.getTag())) return rootView;
        if (rootView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) rootView;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewWithTag(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void scrollCurrentScreenByPage(int direction) {
        if (productList != null && showingOrderScreen && screenKind == 1) {
            int amount = Math.max(1, productList.getChildCount() - 1) * direction;
            int target = Math.max(0, Math.min(gridRows.size() - 1, productList.getFirstVisiblePosition() + amount));
            productList.setSelection(target);
            return;
        }
        View scrollTarget = scrollView;
        if (scrollTarget instanceof ScrollView) {
            int page = Math.max(dp(120), ((ScrollView) scrollTarget).getHeight() - dp(48));
            scrollTarget.scrollBy(0, page * direction);
        } else if (scrollTarget instanceof HorizontalScrollView) {
            scrollTarget.scrollBy(dp(240) * direction, 0);
        } else if (currentScreen instanceof ViewGroup) {
            scrollCurrentScreenFallback(direction);
        }
    }

    private void scrollCurrentScreenFallback(int direction) {
        View focus = getCurrentFocus();
        ViewParentSearch:
        {
            View node = focus;
            while (node != null) {
                if (node instanceof ScrollView) {
                    ScrollView sv = (ScrollView) node;
                    sv.smoothScrollBy(0, direction * Math.max(dp(120), sv.getHeight() - dp(48)));
                    return;
                }
                android.view.ViewParent p = node.getParent();
                node = p instanceof View ? (View) p : null;
            }
        }
        if (productList != null && showingOrderScreen) {
            int target = direction < 0 ? 0 : Math.max(0, gridRows.size() - 1);
            productList.smoothScrollToPosition(target);
        }
    }

    private void scrollCurrentScreenToEdge(boolean end) {
        // Keys 2/8 are direct jumps, not animated scrolling commands.
        if (productList != null && showingOrderScreen && screenKind == 1) {
            int target = end ? Math.max(0, gridRows.size() - 1) : 0;
            productList.setSelectionFromTop(target, 0);
            return;
        }
        if (scrollView != null) {
            scrollView.post(() -> {
                if (scrollView == null) return;
                View content = scrollView.getChildAt(0);
                int targetY = end && content != null
                        ? Math.max(0, content.getHeight() - scrollView.getHeight())
                        : 0;
                scrollView.scrollTo(0, targetY);
            });
            return;
        }
        scrollCurrentScreenFallback(end ? 1 : -1);
    }

    private void jumpCategoryBy(int direction) {
        if (!showingOrderScreen || productList == null || catHeaders.isEmpty()) return;
        List<Map.Entry<String, Integer>> categories = new ArrayList<>(catHeaders.entrySet());
        int index = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getKey().equals(activeCat)) { index = i; break; }
        }
        int next = (index + direction + categories.size()) % categories.size();
        Map.Entry<String, Integer> target = categories.get(next);
        pendingCategoryJump = target.getKey();
        productList.setSelection(target.getValue());
        activeCat = target.getKey();
        styleCategoryChips();
        TextView chip = catChips.get(activeCat);
        if (chip != null && catStripScroll != null) {
            int x = chip.getLeft() - (catStripScroll.getWidth() - chip.getWidth()) / 2;
            catStripScroll.smoothScrollTo(Math.max(0, x), 0);
        }
        productList.postDelayed(() -> pendingCategoryJump = "", 250);
    }

    /**
     * Route the physical Menu key to the same visible menu the user can open on screen.
     * Order/product and cached-order screens have their own three-dot actions; everywhere
     * else the key activates the header's global settings/actions button.
     */
    private void openKeyboardMenu() {
        boolean onOrderScreen = (showingOrderScreen && screenKind == 1) || screenKind == 2;
        if (onOrderScreen) {
            View orderAnchor = findTaggedView(currentScreen,
                    screenKind == 2 ? "orderSummaryOptions" : "orderToolbarOptions");
            if (orderAnchor == null || !orderAnchor.isShown()) {
                // Be resilient if a screen is being restored and its matching menu is not
                // attached yet; look for either order menu anchor before giving up.
                orderAnchor = findTaggedView(currentScreen, "orderToolbarOptions");
                if (orderAnchor == null || !orderAnchor.isShown()) {
                    orderAnchor = findTaggedView(currentScreen, "orderSummaryOptions");
                }
            }
            if (orderAnchor != null && orderAnchor.isShown()) orderAnchor.performClick();
            return;
        }

        View settingsAnchor = findTaggedView(currentScreen, "globalSettingsMenu");
        if (settingsAnchor != null && settingsAnchor.isShown()) settingsAnchor.performClick();
        else showActionsDialog();
    }

    private void openProductDetailsOrMoreOptions() {
        View focus = getCurrentFocus();
        CardHolder holder = findProductCardHolder(focus);
        if (holder != null && holder.p != null) {
            // Prefer the existing card click action so product details stay consistent with touch.
            holder.card.performClick();
            return;
        }
        showActionsDialog();
    }

    private boolean isImageViewerShowing() {
        return imageViewerDialog != null && imageViewerDialog.isShowing();
    }

    private void dismissImageViewer() {
        if (imageViewerDialog != null && imageViewerDialog.isShowing()) imageViewerDialog.dismiss();
    }

    private int focusDirectionForKey(int keyCode) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return View.FOCUS_FORWARD;
        }
    }

    private boolean isOrderToolbarView(View view) {
        if (view == null) return false;
        View node = view;
        while (node != null) {
            Object tag = node.getTag();
            if ("orderTopToolbar".equals(tag) || "orderCategoryStrip".equals(tag)) return true;
            android.view.ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean moveFocusWithinOrderToolbar(View current, int key) {
        if (current == null) return false;
        int direction;
        switch (key) {
            case android.view.KeyEvent.KEYCODE_DPAD_UP: direction = View.FOCUS_UP; break;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: direction = View.FOCUS_DOWN; break;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: direction = View.FOCUS_LEFT; break;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
            default: return false;
        }
        View target = current.focusSearch(direction);
        if (target == null || target == current || !isOrderToolbarView(target)
                || !target.isShown() || !target.isFocusable() || !target.isEnabled()) {
            target = findNearestOrderToolbarFocus(current, direction);
        }
        if (target == null || target == current) return false;
        target.requestFocus();
        if (target.hasFocus()) {
            if (isDescendantOf(target, catStripRow)) scrollCategoryChipIntoView(target);
            return true;
        }
        return false;
    }

    private View findNearestOrderToolbarFocus(View current, int direction) {
        View decor = getWindow() == null ? null : getWindow().getDecorView();
        if (decor == null || current == null) return null;
        int[] from = new int[2];
        current.getLocationOnScreen(from);
        float cx = from[0] + current.getWidth() / 2f;
        float cy = from[1] + current.getHeight() / 2f;
        View best = null;
        double bestScore = Double.MAX_VALUE;
        ArrayList<View> candidates = new ArrayList<>();
        collectFocusableOrderToolbarViews(decor, candidates, current);
        for (View candidate : candidates) {
            int[] pos = new int[2];
            candidate.getLocationOnScreen(pos);
            float dx = pos[0] + candidate.getWidth() / 2f - cx;
            float dy = pos[1] + candidate.getHeight() / 2f - cy;
            boolean valid;
            double primary, secondary;
            if (direction == View.FOCUS_LEFT) { valid = dx < -dp(2); primary = -dx; secondary = Math.abs(dy); }
            else if (direction == View.FOCUS_RIGHT) { valid = dx > dp(2); primary = dx; secondary = Math.abs(dy); }
            else if (direction == View.FOCUS_UP) { valid = dy < -dp(2); primary = -dy; secondary = Math.abs(dx); }
            else { valid = dy > dp(2); primary = dy; secondary = Math.abs(dx); }
            if (!valid) continue;
            double score = primary + secondary * 1.8;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private void collectFocusableOrderToolbarViews(View view, List<View> out, View current) {
        if (view == null) return;
        if (view != current && isOrderToolbarView(view)
                && view.isFocusable() && view.isShown() && view.isEnabled()) {
            out.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectFocusableOrderToolbarViews(group.getChildAt(i), out, current);
            }
        }
    }

    private boolean isProductNavigationView(View view) {
        if (view == null) return false;
        if (view == orderSummaryButton) return true;
        View node = view;
        while (node != null) {
            Object tag = node.getTag();
            if (tag instanceof String && ((String) tag).startsWith("productCard:")) return true;
            android.view.ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private View firstProductNavigationTarget() {
        if (productList == null) return null;
        int first = productList.getFirstVisiblePosition();
        for (int i = 0; i < productList.getChildCount(); i++) {
            View row = productList.getChildAt(i);
            int pos = first + i;
            if (row == null || pos < 0 || pos >= gridRows.size()
                    || gridRows.get(pos).header != null || !(row.getTag() instanceof RowHolder)) continue;
            RowHolder holder = (RowHolder) row.getTag();
            for (CardHolder card : new CardHolder[]{holder.first, holder.second, holder.third, holder.fourth, holder.fifth}) {
                if (card.card.getVisibility() == View.VISIBLE && card.p != null && card.card.isFocusable()) return card.card;
            }
        }
        return null;
    }

    private boolean moveFocusToAdjacentProductRow(View current, int delta) {
        if (productList == null || current == null) return false;
        View rowView = current;
        while (rowView != null && rowView.getParent() != productList) {
            android.view.ViewParent parent = rowView.getParent();
            rowView = parent instanceof View ? (View) parent : null;
        }
        if (rowView == null) return false;
        int currentPosition = productList.getPositionForView(rowView);
        if (currentPosition < 0) return false;
        String role = navigationRole(current);
        int targetPosition = currentPosition + delta;
        while (targetPosition >= 0 && targetPosition < gridRows.size()) {
            if (gridRows.get(targetPosition).header == null) {
                final int position = targetPosition;
                final String targetRole = role;
                // Jump to the actual adapter row first. Smooth scrolling can leave the
                // target detached when the first posted callback runs, which used to make
                // Down appear stuck on the current card.
                productList.setSelectionFromTop(position, 0);
                focusProductRowWhenAttached(position, targetRole, current, 0);
                return true;
            }
            targetPosition += delta;
        }
        // At the bottom, let Down reach the fixed order-summary action; Up from it returns to products.
        if (delta > 0 && orderSummaryButton != null && orderSummaryButton.isShown()) {
            orderSummaryButton.requestFocus();
            return true;
        }
        if (delta < 0 && current == orderSummaryButton) {
            View last = lastVisibleProductFocusTarget();
            if (last != null) { last.requestFocus(); return true; }
        }
        return false;
    }

    private void focusProductRowWhenAttached(final int position, final String targetRole,
            final View originView, final int attempt) {
        if (productList == null || position < 0 || position >= gridRows.size()) return;
        productList.post(() -> {
            if (productList == null || position >= gridRows.size()) return;
            int childIndex = position - productList.getFirstVisiblePosition();
            View child = childIndex >= 0 && childIndex < productList.getChildCount()
                    ? productList.getChildAt(childIndex) : null;
            if (child == null || !(child.getTag() instanceof RowHolder)) {
                if (attempt < 12) {
                    productList.postDelayed(() ->
                            focusProductRowWhenAttached(position, targetRole, originView, attempt + 1), 35);
                }
                return;
            }
            RowHolder holder = (RowHolder) child.getTag();
            CardHolder best = null;
            int[] origin = new int[2];
            if (originView != null) originView.getLocationOnScreen(origin);
            float cx = originView == null ? 0 : origin[0] + originView.getWidth() / 2f;
            CardHolder[] cards = {holder.first, holder.second, holder.third, holder.fourth, holder.fifth};
            double score = Double.MAX_VALUE;
            for (CardHolder card : cards) {
                if (card.p == null || card.card.getVisibility() != View.VISIBLE) continue;
                View target = navigationTargetForRole(card, targetRole);
                if (!canTakeProductFocus(target)) continue;
                int[] loc = new int[2];
                target.getLocationOnScreen(loc);
                double candidateScore = originView == null ? 0
                        : Math.abs((loc[0] + target.getWidth() / 2f) - cx);
                if (best == null || candidateScore < score) {
                    score = candidateScore;
                    best = card;
                }
            }
            if (best != null) {
                View target = navigationTargetForRole(best, targetRole);
                if (canTakeProductFocus(target)) {
                    target.requestFocus();
                    if (!target.hasFocus()) best.card.requestFocus();
                } else {
                    best.card.requestFocus();
                }
            }
        });
    }

    private View lastVisibleProductFocusTarget() {
        if (productList == null) return null;
        for (int i = productList.getChildCount() - 1; i >= 0; i--) {
            View row = productList.getChildAt(i);
            if (!(row != null && row.getTag() instanceof RowHolder)) continue;
            RowHolder holder = (RowHolder) row.getTag();
            CardHolder[] cards = {holder.fifth, holder.fourth, holder.third, holder.second, holder.first};
            for (CardHolder c : cards) {
                if (c.p != null && c.card.getVisibility() == View.VISIBLE) {
                    View target = navigationTargetForRole(c, "card");
                    if (target != null) return target;
                }
            }
        }
        return null;
    }

    private CardHolder findProductCardHolder(View view) {
        View node = view;
        while (node != null) {
            Object tag = node.getTag();
            if (tag instanceof String && ((String) tag).startsWith("productCard:")) {
                View rowView = node;
                while (rowView != null && !(rowView.getTag() instanceof RowHolder)) {
                    android.view.ViewParent parent = rowView.getParent();
                    rowView = parent instanceof View ? (View) parent : null;
                }
                if (rowView instanceof View && rowView.getTag() instanceof RowHolder) {
                    RowHolder row = (RowHolder) rowView.getTag();
                    for (CardHolder card : new CardHolder[]{row.first, row.second, row.third, row.fourth, row.fifth}) {
                        if (card.card == node) return card;
                    }
                }
                return null;
            }
            android.view.ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private String navigationRole(View view) {
        if (view == orderSummaryButton) return "order";
        Object tag = view == null ? null : view.getTag();
        if ("productPlus".equals(tag) || "autoOrderPlus".equals(tag)) return "plus";
        if ("productMinus".equals(tag) || "autoOrderMinus".equals(tag)) return "minus";
        if ("productQty".equals(tag) || "autoOrderQty".equals(tag)) return "qty";
        if ("productAdd".equals(tag)) return "add";
        if ("productCharge".equals(tag)) return "charge";
        if ("autoOrderSave".equals(tag)) return "autoSave";
        if ("stockAlert".equals(tag)) return "stockAlert";
        if (tag instanceof String && ((String) tag).startsWith("productCard:")) return "card";
        View node = view;
        while (node != null) {
            Object t = node.getTag();
            if ("productPlus".equals(t) || "autoOrderPlus".equals(t)) return "plus";
            if ("productMinus".equals(t) || "autoOrderMinus".equals(t)) return "minus";
            if ("productQty".equals(t) || "autoOrderQty".equals(t)) return "qty";
            if ("productAdd".equals(t)) return "add";
            if ("productCharge".equals(t)) return "charge";
            if ("autoOrderSave".equals(t)) return "autoSave";
            if ("stockAlert".equals(t)) return "stockAlert";
            if (t instanceof String && ((String) t).startsWith("productCard:")) return "card";
            android.view.ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return "card";
    }

    private View navigationTargetForRole(CardHolder card, String role) {
        if (card == null || card.p == null) return null;
        if ("plus".equals(role)) {
            if (canTakeProductFocus(card.st.plus)) return card.st.plus;
            if (canTakeProductFocus(card.autoOrderStepper.plus)) return card.autoOrderStepper.plus;
            if (canTakeProductFocus(card.add)) return card.add;
        }
        if ("minus".equals(role)) {
            if (canTakeProductFocus(card.st.minus)) return card.st.minus;
            if (canTakeProductFocus(card.autoOrderStepper.minus)) return card.autoOrderStepper.minus;
            if (canTakeProductFocus(card.add)) return card.add;
        }
        if ("qty".equals(role)) {
            if (canTakeProductFocus(card.st.qty)) return card.st.qty;
            if (canTakeProductFocus(card.autoOrderStepper.qty)) return card.autoOrderStepper.qty;
            if (canTakeProductFocus(card.add)) return card.add;
        }
        if ("add".equals(role)) {
            if (canTakeProductFocus(card.add)) return card.add;
            if (canTakeProductFocus(card.st.plus)) return card.st.plus;
            if (canTakeProductFocus(card.autoOrderStepper.plus)) return card.autoOrderStepper.plus;
        }
        if ("charge".equals(role) && canTakeProductFocus(card.chargeButton)) return card.chargeButton;
        if ("autoSave".equals(role) && canTakeProductFocus(card.autoOrderSaveButton)) return card.autoOrderSaveButton;
        if ("stockAlert".equals(role) && canTakeProductFocus(card.stockAlertButton)) return card.stockAlertButton;
        if ("order".equals(role)) return orderSummaryButton;
        return card.card;
    }

    private boolean canTakeProductFocus(View view) {
        return view != null && view.isShown() && view.isFocusable() && view.isEnabled();
    }

    private boolean requestProductFocus(View view) {
        if (!canTakeProductFocus(view)) return false;
        view.requestFocus();
        return view.hasFocus();
    }

    /** The card's first available action, independent of Android's native focus heuristics. */
    private View firstFocusableControl(CardHolder card) {
        if (card == null) return null;
        if (canTakeProductFocus(card.add)) return card.add;
        if (card.st.view.isShown()) {
            if (canTakeProductFocus(card.st.plus)) return card.st.plus;
            if (canTakeProductFocus(card.st.qty)) return card.st.qty;
            if (canTakeProductFocus(card.st.minus)) return card.st.minus;
        }
        if (card.autoOrderPanel.isShown()) {
            if (canTakeProductFocus(card.autoOrderStepper.plus)) return card.autoOrderStepper.plus;
            if (canTakeProductFocus(card.autoOrderStepper.qty)) return card.autoOrderStepper.qty;
            if (canTakeProductFocus(card.autoOrderStepper.minus)) return card.autoOrderStepper.minus;
            if (canTakeProductFocus(card.autoOrderSaveButton)) return card.autoOrderSaveButton;
        }
        if (canTakeProductFocus(card.stockAlertButton)) return card.stockAlertButton;
        if (canTakeProductFocus(card.chargeButton)) return card.chargeButton;
        return null;
    }

    /** Left/right moves between the controls inside one stepper; card-to-card movement remains spatial. */
    private boolean moveWithinProductControls(CardHolder card, View current, String role, int key) {
        if (card == null || current == null) return false;
        String[] tags;
        if ("plus".equals(role) || "minus".equals(role) || "qty".equals(role)) {
            boolean stockStepper = current == card.autoOrderStepper.plus || current == card.autoOrderStepper.minus
                    || current == card.autoOrderStepper.qty;
            if (stockStepper) tags = new String[]{"autoOrderMinus", "autoOrderQty", "autoOrderPlus"};
            else tags = new String[]{"productMinus", "productQty", "productPlus"};
        } else {
            return false;
        }
        int index = -1;
        for (int i = 0; i < tags.length; i++) if (tags[i].equals(current.getTag())) index = i;
        if (index < 0) return false;
        int next = index + (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
        if (next < 0 || next >= tags.length) return false;
        View target;
        switch (tags[next]) {
            case "productMinus": target = card.st.minus; break;
            case "productQty": target = card.st.qty; break;
            case "productPlus": target = card.st.plus; break;
            case "autoOrderMinus": target = card.autoOrderStepper.minus; break;
            case "autoOrderQty": target = card.autoOrderStepper.qty; break;
            default: target = card.autoOrderStepper.plus; break;
        }
        return requestProductFocus(target);
    }


    private boolean isStepperRole(String role) {
        return "plus".equals(role) || "minus".equals(role) || "qty".equals(role);
    }

    /** Logical column 0 is the visual rightmost card in the Hebrew product grid. */
    private int[] findCardRowAndColumn(CardHolder targetCard) {
        if (productList == null || targetCard == null) return null;
        int first = productList.getFirstVisiblePosition();
        for (int i = 0; i < productList.getChildCount(); i++) {
            View rowView = productList.getChildAt(i);
            if (rowView == null || !(rowView.getTag() instanceof RowHolder)) continue;
            RowHolder row = (RowHolder) rowView.getTag();
            CardHolder[] cards = {row.first, row.second, row.third, row.fourth, row.fifth};
            for (int column = 0; column < cards.length; column++) {
                if (cards[column] == targetCard && cards[column].p != null
                        && cards[column].card.getVisibility() == View.VISIBLE) return new int[]{first + i, column};
            }
        }
        return null;
    }

    private List<String> verticalRolesForCard(CardHolder card) {
        List<String> roles = new ArrayList<>();
        roles.add("card");
        if (card == null || card.p == null) return roles;
        if (canTakeProductFocus(card.add)) {
            roles.add("add");
        } else if (card.autoOrderPanel.isShown()) {
            if (canTakeProductFocus(card.autoOrderStepper.plus) || canTakeProductFocus(card.autoOrderStepper.qty)
                    || canTakeProductFocus(card.autoOrderStepper.minus)) roles.add("plus");
            if (canTakeProductFocus(card.autoOrderSaveButton)) roles.add("autoSave");
        } else if (card.st.view.isShown()) {
            if (canTakeProductFocus(card.st.plus) || canTakeProductFocus(card.st.qty)
                    || canTakeProductFocus(card.st.minus)) roles.add("plus");
        }
        if (canTakeProductFocus(card.stockAlertButton)) roles.add("stockAlert");
        if (canTakeProductFocus(card.chargeButton)) roles.add("charge");
        return roles;
    }

    private int verticalLayerIndex(List<String> roles, String role) {
        int index = roles.indexOf(isStepperRole(role) ? "plus" : role);
        return Math.max(0, index);
    }

    private String previousVerticalRole(CardHolder card, String role) {
        List<String> roles = verticalRolesForCard(card);
        int index = verticalLayerIndex(roles, role);
        return index > 0 ? roles.get(index - 1) : null;
    }

    private String nextVerticalRole(CardHolder card, String role) {
        List<String> roles = verticalRolesForCard(card);
        int index = verticalLayerIndex(roles, role);
        return index + 1 < roles.size() ? roles.get(index + 1) : null;
    }

    private boolean moveFocusFromOrderSummaryToLastProduct() {
        View last = lastVisibleProductFocusTarget();
        if (last != null) requestProductFocus(last);
        return true;
    }

    private void moveFocusToProductRow(int currentPosition, int preferredColumn, int delta, String targetRole) {
        if (productList == null) return;
        int targetPosition = currentPosition + delta;
        while (targetPosition >= 0 && targetPosition < gridRows.size()) {
            if (gridRows.get(targetPosition).header == null) {
                focusProductRowAtColumn(targetPosition, preferredColumn, targetRole, delta);
                return;
            }
            targetPosition += delta;
        }
        if (delta > 0 && orderSummaryButton != null && orderSummaryButton.isShown()) {
            requestProductFocus(orderSummaryButton);
        } else if (delta < 0) {
            // At the first product row, Up/Right can leave the grid and reach the fixed toolbar.
            focusOrderToolbarFromFirstProduct();
        }
    }

    private void focusOrderToolbarFromFirstProduct() {
        if (catStripRow != null) {
            for (int i = 0; i < catStripRow.getChildCount(); i++) {
                View chip = catStripRow.getChildAt(i);
                if (chip != null && chip.getVisibility() == View.VISIBLE && chip.isFocusable()) {
                    if (requestProductFocus(chip)) {
                        scrollCategoryChipIntoView(chip);
                        return;
                    }
                }
            }
        }
        if (orderSummaryButton != null && orderSummaryButton.isShown()) requestProductFocus(orderSummaryButton);
    }

    private boolean isDescendantOf(View child, ViewGroup ancestor) {
        View node = child;
        while (node != null) {
            if (node == ancestor) return true;
            android.view.ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean moveFocusWithinCategoryStrip(View current, int key) {
        if (catStripRow == null || current == null) return false;
        int currentCenter = current.getLeft() + current.getWidth() / 2;
        View target = null;
        int nearestDistance = Integer.MAX_VALUE;
        boolean toRight = key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        for (int i = 0; i < catStripRow.getChildCount(); i++) {
            View candidate = catStripRow.getChildAt(i);
            if (candidate == null || candidate == current || candidate.getVisibility() != View.VISIBLE
                    || !candidate.isFocusable()) continue;
            int center = candidate.getLeft() + candidate.getWidth() / 2;
            int distance = center - currentCenter;
            if ((toRight && distance > 0 || !toRight && distance < 0)
                    && Math.abs(distance) < nearestDistance) {
                nearestDistance = Math.abs(distance);
                target = candidate;
            }
        }
        if (target == null) {
            // Wrap at the end of the horizontal strip instead of trapping focus.
            View edge = null;
            for (int i = 0; i < catStripRow.getChildCount(); i++) {
                View candidate = catStripRow.getChildAt(i);
                if (candidate == null || candidate.getVisibility() != View.VISIBLE || !candidate.isFocusable()) continue;
                int center = candidate.getLeft() + candidate.getWidth() / 2;
                if (edge == null || (toRight
                        ? center < edge.getLeft() + edge.getWidth() / 2
                        : center > edge.getLeft() + edge.getWidth() / 2)) edge = candidate;
            }
            target = edge;
        }
        if (target != null && requestProductFocus(target)) scrollCategoryChipIntoView(target);
        return true;
    }

    private void scrollCategoryChipIntoView(View chip) {
        if (catStripScroll == null || chip == null) return;
        catStripScroll.post(() -> {
            if (catStripScroll == null || chip.getParent() != catStripRow || !chip.hasFocus()) return;
            int targetX = chip.getLeft() - (catStripScroll.getWidth() - chip.getWidth()) / 2;
            catStripScroll.smoothScrollTo(Math.max(0, targetX), 0);
        });
    }

    private void moveFocusHorizontallyToCard(int rowPosition, int column, int columnDelta, String targetRole) {
        if (productList == null || rowPosition < 0 || rowPosition >= gridRows.size()) return;
        int targetColumn = column + columnDelta;
        if (targetColumn >= 0 && targetColumn < cardColumns()
                && focusCardInRowIfAvailable(rowPosition, targetColumn, targetRole)) return;
        int rowDelta = columnDelta > 0 ? 1 : -1;
        int preferredColumn = columnDelta > 0 ? 0 : Math.max(0, cardColumns() - 1);
        moveFocusToProductRow(rowPosition, preferredColumn, rowDelta, targetRole);
    }

    private boolean focusCardInRowIfAvailable(int position, int column, String role) {
        if (productList == null) return false;
        int childIndex = position - productList.getFirstVisiblePosition();
        if (childIndex < 0 || childIndex >= productList.getChildCount()) return false;
        View rowView = productList.getChildAt(childIndex);
        if (rowView == null || !(rowView.getTag() instanceof RowHolder)) return false;
        RowHolder holder = (RowHolder) rowView.getTag();
        CardHolder[] cards = {holder.first, holder.second, holder.third, holder.fourth, holder.fifth};
        if (column < 0 || column >= cards.length) return false;
        CardHolder card = cards[column];
        return card.p != null && card.card.getVisibility() == View.VISIBLE
                && requestProductFocus(navigationTargetForRole(card, role));
    }

    private void focusProductRowAtColumn(final int position, final int preferredColumn,
            final String targetRole, final int delta) {
        if (productList == null || position < 0 || position >= gridRows.size()) return;
        int first = productList.getFirstVisiblePosition();
        int last = productList.getLastVisiblePosition();
        int childIndex = position - first;
        if (position >= first && position <= last && childIndex >= 0 && childIndex < productList.getChildCount()) {
            View row = productList.getChildAt(childIndex);
            if (row != null && row.getTag() instanceof RowHolder) {
                int listTop = productList.getPaddingTop();
                int listBottom = productList.getHeight() - productList.getPaddingBottom();
                if (row.getTop() >= listTop && row.getBottom() <= listBottom) {
                    applyFocusToRowHolder((RowHolder) row.getTag(), preferredColumn, targetRole);
                    return;
                }
                int desiredTop = delta > 0
                        ? Math.max(listTop, listBottom - row.getHeight() - dp(8)) : listTop + dp(8);
                productList.setSelectionFromTop(position, desiredTop);
                focusProductRowWhenAttached(position, preferredColumn, targetRole, 0);
                return;
            }
        }
        int offset = delta > 0 ? Math.max(dp(16), productList.getHeight() / 2) : dp(16);
        productList.setSelectionFromTop(position, offset);
        focusProductRowWhenAttached(position, preferredColumn, targetRole, 0);
    }

    private void focusProductRowWhenAttached(final int position, final int preferredColumn,
            final String targetRole, final int attempt) {
        if (productList == null || position < 0 || position >= gridRows.size()) return;
        productList.post(() -> {
            if (productList == null || position >= gridRows.size()) return;
            int childIndex = position - productList.getFirstVisiblePosition();
            View child = childIndex >= 0 && childIndex < productList.getChildCount()
                    ? productList.getChildAt(childIndex) : null;
            if (child == null || !(child.getTag() instanceof RowHolder)) {
                if (attempt < 12) productList.postDelayed(
                        () -> focusProductRowWhenAttached(position, preferredColumn, targetRole, attempt + 1), 35);
                return;
            }
            applyFocusToRowHolder((RowHolder) child.getTag(), preferredColumn, targetRole);
        });
    }

    private void applyFocusToRowHolder(RowHolder holder, int preferredColumn, String targetRole) {
        if (holder == null) return;
        CardHolder[] cards = {holder.first, holder.second, holder.third, holder.fourth, holder.fifth};
        CardHolder chosen = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < cards.length; i++) {
            CardHolder candidate = cards[i];
            if (candidate.p == null || candidate.card.getVisibility() != View.VISIBLE) continue;
            int distance = Math.abs(i - preferredColumn);
            if (chosen == null || distance < bestDistance) { chosen = candidate; bestDistance = distance; }
        }
        if (chosen != null) {
            if (!requestProductFocus(navigationTargetForRole(chosen, targetRole))) requestProductFocus(chosen.card);
        }
    }

    /** '#' moves visually right in RTL; '*' moves visually left and opens the destination immediately. */
    /**
     * Makes a SeekBar usable without touch. Android's native thumb movement is retained,
     * while common hardware-key adjustments explicitly commit the value as well.
     */
    @Override protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        Locale hebrew = new Locale("he", "IL");
        config.setLocale(hebrew);
        config.setLayoutDirection(hebrew);
        int percent = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("ui_scale_percent", 100);
        percent = Math.max(80, Math.min(150, percent));
        config.fontScale = config.fontScale * (percent / 100.0f);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN
                && screenKind == 1
                && activeProductSearch != null
                && activeProductSearch.isShown()
                && activeProductSearch.hasFocus()
                && !isTouchInsideView(activeProductSearch, event)
                && !isTouchInsideView(productSearchClearButton, event)) {
            hideKeyboard();
            activeProductSearch.clearFocus();
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isTouchInsideView(View view, android.view.MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isShown()) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0] && x < location[0] + view.getWidth()
                && y >= location[1] && y < location[1] + view.getHeight();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(
                (oldFocus, newFocus) -> showKeyboardFocusHighlight(newFocus));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int savedScalePercent = prefs.getInt("ui_scale_percent", 100);
        savedScalePercent = 70 + Math.round((Math.max(70, Math.min(130, savedScalePercent)) - 70) / 5.0f) * 5;
        uiScale = savedScalePercent / 100.0f;
        prefs.edit().putInt("ui_scale_percent", savedScalePercent).apply();
        uiScaleAtActivityStart = uiScale;
        boolean reopenActionsAfterScale = prefs.getBoolean("reopen_actions_after_scale", false);
        int restoreTabAfterScale = prefs.getInt("actions_restore_tab", -1);
        if (reopenActionsAfterScale) {
            prefs.edit().remove("reopen_actions_after_scale").remove("actions_restore_tab").apply();
        }
        nightMode = prefs.getBoolean("night_mode", false);
        setTheme(nightMode ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        applyPalette();
        clearLegacyUpdateArtifacts();
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false); // one behaviour on every Android 11+ device; insets are handled in beginScreen
        int cacheKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        imageCache = new LruCache<String, Bitmap>(cacheKb) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount() / 1024; }
        };
        getWindow().setStatusBarColor(HEADER_BG);
        getWindow().setNavigationBarColor(SURFACE);
        pruneImageCache();
        SaleMonitor.ensureNotificationChannel(this);
        StockMonitor.scheduleNextCheck(this);
        requestMonitoringPermissions(false);
        if (getIntent() != null && getIntent().hasExtra("stock_sale_id") && getIntent().hasExtra("stock_product_sid")) {
            int stockSaleId = getIntent().getIntExtra("stock_sale_id", -1);
            String stockSid = getIntent().getStringExtra("stock_product_sid");
            if (stockSaleId > 0 && stockSid != null && !stockSid.trim().isEmpty()) {
                prefs.edit().putInt(PREF_PENDING_STOCK_SALE_ID, stockSaleId)
                        .putString(PREF_PENDING_STOCK_SID, stockSid).remove(PREF_PENDING_NOTIFICATION_SALE_ID).apply();
            }
        }
        if (getIntent() != null && getIntent().hasExtra("sale_id")) {
            int notifiedSaleId = getIntent().getIntExtra("sale_id", -1);
            if (notifiedSaleId > 0 && !prefs.contains(PREF_PENDING_STOCK_SALE_ID))
                prefs.edit().putInt(PREF_PENDING_NOTIFICATION_SALE_ID, notifiedSaleId).apply();
        }
        if (hasToken()) {
            SaleMonitor.scheduleNextStart(this);
            int pendingStockSaleId = prefs.getInt(PREF_PENDING_STOCK_SALE_ID, -1);
            String pendingStockSid = prefs.getString(PREF_PENDING_STOCK_SID, "");
            int pendingSaleId = prefs.getInt(PREF_PENDING_NOTIFICATION_SALE_ID, -1);
            if (getIntent() != null && getIntent().getBooleanExtra("open_notification_settings", false)) {
                if (hasToken()) showNotificationSettings(); else showLogin();
            } else if (pendingStockSaleId > 0 && !pendingStockSid.isEmpty()) {
                openSaleFromStockNotification(pendingStockSaleId, pendingStockSid);
            } else if (pendingSaleId > 0) {
                openSaleFromNotification(pendingSaleId);
            } else if (reopenActionsAfterScale && restoreTabAfterScale >= 0) {
                switch (restoreTabAfterScale) {
                    case 1: loadFeatureOrders(); break;
                    case 2: loadFeatureTransactions(); break;
                    case 3: showAccount(); break;
                    default: showOpenSales(); break;
                }
            } else {
                showOpenSales();
            }
        } else {
            showLogin();
        }
        // Check for a newer release once per app launch; the setting defaults to enabled.
        if (root != null) root.post(() -> {
            boolean showedUpdateSuccess = showPendingInAppUpdateSuccess();
            if (!showedUpdateSuccess) checkForUpdatesOnLaunch();
            if (!showedUpdateSuccess && reopenActionsAfterScale && hasToken()) showActionsDialog();
        });
        else if (!showPendingInAppUpdateSuccess()) checkForUpdatesOnLaunch();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_notification_settings", false)) {
            if (hasToken()) showNotificationSettings(); else showLogin();
            return;
        }
        if (intent != null && intent.hasExtra("stock_sale_id") && intent.hasExtra("stock_product_sid")) {
            int stockSaleId = intent.getIntExtra("stock_sale_id", -1);
            String stockSid = intent.getStringExtra("stock_product_sid");
            if (stockSaleId > 0 && stockSid != null && !stockSid.trim().isEmpty()) {
                prefs.edit().putInt(PREF_PENDING_STOCK_SALE_ID, stockSaleId)
                        .putString(PREF_PENDING_STOCK_SID, stockSid).remove(PREF_PENDING_NOTIFICATION_SALE_ID).apply();
                if (hasToken()) openSaleFromStockNotification(stockSaleId, stockSid);
                else showLogin();
            }
            return;
        }
        if (intent != null && intent.hasExtra("sale_id")) {
            int notifiedSaleId = intent.getIntExtra("sale_id", -1);
            if (notifiedSaleId > 0) prefs.edit().putInt(PREF_PENDING_NOTIFICATION_SALE_ID, notifiedSaleId).apply();
            if (hasToken() && notifiedSaleId > 0) openSaleFromNotification(notifiedSaleId);
            else if (!hasToken()) showLogin();
        }
    }

    @Override public void onBackPressed() {
        if (backAction != null) backAction.run();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        imageExecutor.shutdownNow();
        loadExecutor.shutdownNow();
        fetchPool.shutdownNow();
        prefetchExecutor.shutdownNow();
    }

    // ======================================================================== small helpers

    private int dp(float n) {
        return (int) (n * uiScale * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean stale(int token) { return token != screenToken; }

    private Toast lastToast;

    private void toast(String message, boolean longer) {
        if (lastToast != null) lastToast.cancel();
        lastToast = Toast.makeText(this, message, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        lastToast.show();
    }

    /**
     * One visual language for every app dialog: rounded surface, app palette,
     * readable RTL text, and rounded action buttons instead of platform defaults.
     */
    private AlertDialog.Builder appDialog() {
        return new AppDialogBuilder();
    }

    private final class AppTimePickerDialog extends TimePickerDialog {
        AppTimePickerDialog(TimePickerDialog.OnTimeSetListener listener, int hour, int minute, boolean is24HourView) {
            super(MainActivity.this, listener, hour, minute, is24HourView);
        }

        @Override
        public void show() {
            super.show();
            styleAppDialog(this);
        }
    }

    private final class AppDialogBuilder extends AlertDialog.Builder {
        AppDialogBuilder() {
            super(MainActivity.this);
        }

        @Override
        public AlertDialog show() {
            AlertDialog dialog = super.show();
            styleAppDialog(dialog);
            return dialog;
        }
    }

    private void styleAppDialog(AlertDialog dialog) {
        if (dialog == null) return;
        // Match the Settings screen surface and its light-orange action buttons.
        final int panelColor = BG;
        final int textColor = INK;
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(shape(panelColor, 24, LINE));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = Math.min(metrics.widthPixels - dp(32), dp(440));
            window.setLayout(Math.max(dp(280), width), -2);
        }

        TextView title = dialog.findViewById(getResources().getIdentifier("alertTitle", "id", "android"));
        if (title != null) {
            title.setTextColor(textColor);
            title.setTextSize(19);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            title.setPadding(dp(22), dp(18), dp(22), dp(8));
            title.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(textColor);
            message.setTextSize(15);
            message.setLineSpacing(dp(2), 1f);
            message.setPadding(dp(22), dp(8), dp(22), dp(12));
            message.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }

        styleDialogButtons(dialog);
        View buttonPanel = dialog.findViewById(getResources().getIdentifier("buttonPanel", "id", "android"));
        if (buttonPanel instanceof ViewGroup) {
            buttonPanel.setPadding(dp(12), dp(6), dp(12), dp(12));
            if (buttonPanel instanceof LinearLayout) {
                ((LinearLayout) buttonPanel).setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            }
        }
        ListView choices = dialog.getListView();
        if (choices != null) {
            choices.setBackgroundColor(panelColor);
            // Action-list dialogs use the same white panel and spaced orange rounded
            // buttons as Settings, rather than platform-style flat list rows.
            choices.setDivider(new ColorDrawable(Color.TRANSPARENT));
            // Keep action-list buttons narrower, with generous spacing around each one.
            choices.setDividerHeight(dp(18));
            choices.setPadding(dp(24), dp(14), dp(24), dp(14));
            choices.setClipToPadding(false);
            choices.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            for (int i = 0; i < choices.getChildCount(); i++) {
                styleDialogChoice(choices.getChildAt(i));
            }
            choices.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override public void onChildViewAdded(View parent, View child) {
                    styleDialogChoice(child);
                }
                @Override public void onChildViewRemoved(View parent, View child) { }
            });
        }
        styleDialogInputs(dialog.getWindow() == null ? null : dialog.getWindow().getDecorView());
    }


    private void styleDialogButtons(AlertDialog dialog) {
        int[] ids = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL};
        for (int id : ids) {
            android.widget.Button button = dialog.getButton(id);
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setMinHeight(dp(36));
            button.setMinWidth(0);
            button.setPadding(dp(12), dp(5), dp(12), dp(5));
            button.setTextColor(ORANGE_DARK);
            button.setBackground(ripple(shape(ORANGE_LIGHT, ACTION_BUTTON_RADIUS_DP, LINE), ACTION_BUTTON_RADIUS_DP));
            ViewGroup.LayoutParams rawParams = button.getLayoutParams();
            if (rawParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.leftMargin = dp(ACTION_BUTTON_GAP_DP / 2);
                params.rightMargin = dp(ACTION_BUTTON_GAP_DP / 2);
                button.setLayoutParams(params);
            }
        }
    }

    private void styleDialogChoice(View view) {
        if (view instanceof TextView) {
            TextView item = (TextView) view;
            item.setTextColor(ORANGE_DARK);
            item.setTextSize(16);
            item.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setPadding(dp(12), dp(6), dp(12), dp(6));
            item.setMinHeight(dp(38));
            item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            // Use a narrower row for the visible button itself; preserve the original text size.
            android.view.ViewParent parent = item.getParent();
            if (parent instanceof ListView) {
                int availableWidth = ((ListView) parent).getWidth();
                if (availableWidth <= 0) {
                    availableWidth = getResources().getDisplayMetrics().widthPixels - dp(64);
                }
                int buttonWidth = Math.max(dp(220), availableWidth - dp(72));
                ViewGroup.LayoutParams params = item.getLayoutParams();
                if (params != null) {
                    params.width = buttonWidth;
                    item.setLayoutParams(params);
                }
            }
            item.setBackground(ripple(shape(ORANGE_LIGHT, ACTION_BUTTON_RADIUS_DP, LINE), ACTION_BUTTON_RADIUS_DP));
        }
    }

    private void styleDialogInputs(View view) {
        if (view == null) return;
        if (view instanceof EditText) {
            EditText field = (EditText) view;
            field.setTextColor(INK);
            field.setHintTextColor(MUTED);
            field.setPadding(dp(12), dp(10), dp(12), dp(10));
            field.setBackground(shape(SURFACE, 12, LINE));
            field.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleDialogInputs(group.getChildAt(i));
            }
        }
    }

    private static final class AppGradientDrawable extends GradientDrawable {
        int appFillColor;
        int appStrokeColor;
        int appStrokeWidth;
        boolean hasAppStroke;
    }

    private GradientDrawable shape(int fill, float radiusDp, int stroke) {
        AppGradientDrawable g = new AppGradientDrawable();
        g.appFillColor = fill;
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            g.appStrokeColor = stroke;
            g.appStrokeWidth = dp(1);
            g.hasAppStroke = true;
            g.setStroke(g.appStrokeWidth, stroke);
        }
        return g;
    }

    private GradientDrawable shape(int fill, float radiusDp, int stroke, int strokeWidthDp) {
        GradientDrawable drawable = shape(fill, radiusDp, stroke);
        if (stroke != 0) {
            int width = dp(Math.max(1, strokeWidthDp));
            drawable.setStroke(width, stroke);
            if (drawable instanceof AppGradientDrawable) {
                AppGradientDrawable appDrawable = (AppGradientDrawable) drawable;
                appDrawable.appStrokeColor = stroke;
                appDrawable.appStrokeWidth = width;
                appDrawable.hasAppStroke = true;
            }
        }
        return drawable;
    }

    private String nightToggleLabel() {
        // The label describes the mode the button will switch TO.
        return nightMode ? "☀️ יום" : "🌙 לילה";
    }

    private GradientDrawable nightToggleBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { AppPalette.NIGHT_TOGGLE_GRADIENT_TOP, Color.WHITE });
        background.setCornerRadius(dp(18));
        background.setStroke(dp(3), Color.BLACK);
        return background;
    }

    private int scaleDimension(int value, float ratio) {
        return value > 0 ? Math.max(1, Math.round(value * ratio)) : value;
    }

    /** Resize the live view tree in place so changing text size never navigates away from the current page. */
    private void scaleViewTreeInPlace(View view, float ratio) {
        if (view == null || Math.abs(ratio - 1.0f) < 0.001f) return;
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    Math.max(1.0f, textView.getTextSize() * ratio));
        }
        view.setPadding(scaleDimension(view.getPaddingLeft(), ratio),
                scaleDimension(view.getPaddingTop(), ratio),
                scaleDimension(view.getPaddingRight(), ratio),
                scaleDimension(view.getPaddingBottom(), ratio));
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.width = scaleDimension(params.width, ratio);
            params.height = scaleDimension(params.height, ratio);
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                margins.leftMargin = scaleDimension(margins.leftMargin, ratio);
                margins.topMargin = scaleDimension(margins.topMargin, ratio);
                margins.rightMargin = scaleDimension(margins.rightMargin, ratio);
                margins.bottomMargin = scaleDimension(margins.bottomMargin, ratio);
            }
            view.setLayoutParams(params);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scaleViewTreeInPlace(group.getChildAt(i), ratio);
            }
        }
    }

    private void changeUiScale(int requestedPercent) {
        int bounded = Math.max(70, Math.min(130, requestedPercent));
        int next = 70 + Math.round((bounded - 70) / 5.0f) * 5;
        next = Math.max(70, Math.min(130, next));
        int current = prefs.getInt("ui_scale_percent", 100);
        if (actionScaleBar != null) actionScaleBar.setProgress((next - 70) / 5);
        if (actionScaleValue != null) actionScaleValue.setText(next + "%");
        if (next == current) return;
        float ratio = next / (float) current;
        prefs.edit().putInt("ui_scale_percent", next).apply();
        uiScale = next / 100.0f;
        Set<View> scaledRoots = new HashSet<>();
        if (currentScreen != null && scaledRoots.add(currentScreen)) scaleViewTreeInPlace(currentScreen, ratio);
        for (TabScreenState cached : tabScreenCache.values()) {
            if (cached != null && cached.screen != null && scaledRoots.add(cached.screen)) {
                scaleViewTreeInPlace(cached.screen, ratio);
            }
        }
        if (currentScreen != null) currentScreen.requestLayout();
    }

    private void commitCardColumns(int requestedColumns, boolean showConfirmation) {
        int columns = Math.max(1, Math.min(5, requestedColumns));
        int previous = cardColumns();
        if (previous == columns) return;
        prefs.edit().putInt("product_card_columns", columns).apply();
        if (showingOrderScreen && productAdapter != null) rebuildGrid(false);
        if (showConfirmation) toast("פריסת הכרטיסיות נשמרה", false);
    }

    private void showActionsDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(18));
        panel.setBackground(shape(SURFACE, 24, LINE));
        panel.setElevation(dp(12));

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("פעולות", 22, true);
        heading.setTextColor(INK);
        heading.setGravity(Gravity.CENTER);
        headingRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = btn("✕", FLAT);
        close.setContentDescription("סגירת חלון הפעולות");
        close.setMinHeight(dp(40));
        close.setPadding(dp(10), dp(4), dp(10), dp(4));
        headingRow.addView(close, new LinearLayout.LayoutParams(dp(44), dp(40)));
        panel.addView(headingRow, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = muted("פעולות מהירות לעמוד ההזמנה", 13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(3);
        subtitleParams.bottomMargin = dp(14);
        panel.addView(subtitle, subtitleParams);

        TextView scaleLabel = text("Aa  גודל התצוגה", 14, true);
        scaleLabel.setTextColor(INK);
        scaleLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams scaleLabelLp = new LinearLayout.LayoutParams(-1, -2);
        scaleLabelLp.topMargin = dp(12);
        panel.addView(scaleLabel, scaleLabelLp);
        int scaleStart = Math.max(70, Math.min(130, prefs.getInt("ui_scale_percent", 100)));
        TextView scaleValue = muted(scaleStart + "%", 12);
        actionScaleValue = scaleValue;
        scaleValue.setGravity(Gravity.CENTER);
        panel.addView(scaleValue, new LinearLayout.LayoutParams(-1, -2));
        SeekBar scaleBar = new SeekBar(this);
        actionScaleBar = scaleBar;
        scaleBar.setMax(12);
        scaleBar.setProgress((scaleStart - 70) / 5);
        scaleBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (Build.VERSION.SDK_INT >= 21) {
            scaleBar.setProgressTintList(ColorStateList.valueOf(ORANGE));
            scaleBar.setThumbTintList(ColorStateList.valueOf(ORANGE));
            scaleBar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        }
        panel.addView(scaleBar, new LinearLayout.LayoutParams(-1, dp(38)));
        scaleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                scaleValue.setText((70 + progress * 5) + "%");
                if (fromUser) changeUiScale(70 + progress * 5);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                changeUiScale(70 + bar.getProgress() * 5);
            }
        });
        scaleBar.setContentDescription("גודל התצוגה, 70 עד 130 אחוז");

        TextView columnsLabel = text("▦  מספר עמודות בהזמנות", 14, true);
        columnsLabel.setTextColor(INK);
        LinearLayout.LayoutParams columnsLabelLp = new LinearLayout.LayoutParams(-1, -2);
        columnsLabelLp.topMargin = dp(8);
        panel.addView(columnsLabel, columnsLabelLp);
        int columnsStart = cardColumns();
        TextView columnsValue = muted(columnsStart + (columnsStart == 1 ? " עמודה" : " עמודות"), 12);
        columnsValue.setGravity(Gravity.CENTER);
        panel.addView(columnsValue, new LinearLayout.LayoutParams(-1, -2));
        SeekBar columnsBar = new SeekBar(this);
        columnsBar.setMax(4);
        columnsBar.setProgress(columnsStart - 1);
        columnsBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (Build.VERSION.SDK_INT >= 21) {
            columnsBar.setProgressTintList(ColorStateList.valueOf(ORANGE));
            columnsBar.setThumbTintList(ColorStateList.valueOf(ORANGE));
            columnsBar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        }
        panel.addView(columnsBar, new LinearLayout.LayoutParams(-1, dp(38)));
        columnsBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int count = progress + 1;
                columnsValue.setText(count + (count == 1 ? " עמודה" : " עמודות"));
                if (fromUser) commitCardColumns(count, false);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                commitCardColumns(columnsBar.getProgress() + 1, false);
            }
        });
        columnsBar.setContentDescription("מספר עמודות בכרטיסי המוצרים, 1 עד 5");

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        quickActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        quickActions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams quickActionsLp = new LinearLayout.LayoutParams(-1, dp(44));
        quickActionsLp.topMargin = dp(14);
        panel.addView(quickActions, quickActionsLp);

        TextView nightToggle = btn(nightMode ? "🌙 מצב לילה" : "☀️ מצב יום", OUTLINE);
        nightToggle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams quickActionLp = new LinearLayout.LayoutParams(0, -1, 1f);
        quickActionLp.setMarginEnd(dp(5));
        quickActions.addView(nightToggle, quickActionLp);
        nightToggle.setOnClickListener(v -> {
            toggleNightMode();
            nightToggle.setText(nightMode ? "🌙 מצב לילה" : "☀️ מצב יום");
        });

        TextView resetDisplayButton = btn("↺ איפוס תצוגה", OUTLINE);
        resetDisplayButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams resetActionLp = new LinearLayout.LayoutParams(0, -1, 1f);
        resetActionLp.setMarginStart(dp(5));
        quickActions.addView(resetDisplayButton, resetActionLp);
        resetDisplayButton.setContentDescription("איפוס גודל ל־100 אחוז, שלוש עמודות ומצב יום");
        resetDisplayButton.setOnClickListener(v -> {
            scaleBar.setProgress(6);
            scaleValue.setText("100%");
            changeUiScale(100);
            columnsBar.setProgress(2);
            columnsValue.setText("3 עמודות");
            commitCardColumns(3, false);
            if (nightMode) toggleNightMode();
            nightToggle.setText("☀️ מצב יום");
            styleActionButton(nightToggle, ORANGE_LIGHT, ORANGE_DARK, 0);
            toast("התצוגה אופסה לברירת המחדל", false);
        });

        AlertDialog dialog = new AlertDialog.Builder(this,
                nightMode ? android.R.style.Theme_Material_Dialog_Alert
                        : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setView(panel)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams windowParams = dialog.getWindow().getAttributes();
                windowParams.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(440));
                windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                windowParams.gravity = Gravity.CENTER;
                windowParams.dimAmount = 0.48f;
                dialog.getWindow().setAttributes(windowParams);
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(440)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private Drawable ripple(Drawable content, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(AppPalette.RIPPLE_ALPHA, Color.red(ORANGE), Color.green(ORANGE), Color.blue(ORANGE))), content, mask);
    }

    private float liveFontSize(float size) { return size * (uiScale / Math.max(0.01f, uiScaleAtActivityStart)); }

    private TextView text(String value, float size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(liveFontSize(size));
        v.setTextColor(INK);
        v.setGravity(Gravity.START);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(2), dp(3), dp(2), dp(3));
        return v;
    }

    private TextView muted(String value, float size) {
        TextView v = text(value, size, false);
        v.setTextColor(MUTED);
        return v;
    }

    // Shared action-button style: use the same rounded-rectangle geometry and touch feedback
    // throughout screens, menus, cards, and dialogs. Circular controls (e.g. +/-) stay separate.
    private static final int ACTION_BUTTON_RADIUS_DP = 12;
    private static final int ACTION_BUTTON_GAP_DP = 6;

    private void styleActionButton(TextView button, int fill, int foreground, int stroke) {
        button.setTextColor(foreground);
        button.setBackground(ripple(
                shape(fill, ACTION_BUTTON_RADIUS_DP, stroke), ACTION_BUTTON_RADIUS_DP));
        button.setBackground(ripple(shape(fill, ACTION_BUTTON_RADIUS_DP, stroke, dp(1)), ACTION_BUTTON_RADIUS_DP));
        button.setMinHeight(dp(44));
        button.setAllCaps(false);
    }

    private TextView btn(String label, int style) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(liveFontSize(16));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        b.setMinHeight(dp(44));
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        int fill, fg, stroke = 0;
        switch (style) {
            case PRIMARY: fill = ORANGE; fg = ON_ORANGE; break;
            case TONAL: fill = ORANGE_LIGHT; fg = ORANGE_DARK; break;
            case OUTLINE: fill = SURFACE; fg = ORANGE_DARK; stroke = ORANGE; break;
            case DANGER: fill = RED_LIGHT; fg = RED; break;
            default: fill = Color.TRANSPARENT; fg = ORANGE_DARK; break;
        }
        styleActionButton(b, fill, fg, stroke);
        return b;
    }

    private TextView smallChip(String label, int style) {
        TextView b = btn(label, style);
        b.setTextSize(liveFontSize(14));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(34));
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        return b;
    }

    private TextView roundBtn(String label, int sizeDp, int fill, int fg) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(liveFontSize(21));
        b.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        b.setTextColor(fg);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(fill);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(AppPalette.CIRCULAR_BUTTON_RIPPLE_ALPHA, Color.red(AppPalette.DAY.orange), Color.green(AppPalette.DAY.orange), Color.blue(AppPalette.DAY.orange))), oval, mask));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return b;
    }

    private void setEnabledLook(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.45f);
    }

    private LinearLayout card(float paddingDp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        c.setBackground(shape(SURFACE, 14, LINE));
        return c;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTextSize(liveFontSize(15));
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(shape(SURFACE, 12, LINE));
        return e;
    }

    private Stepper stepper(int heightDp) {
        Stepper s = new Stepper();
        s.view = new LinearLayout(this);
        s.view.setOrientation(LinearLayout.HORIZONTAL);
        s.view.setGravity(Gravity.CENTER);
        s.view.setBackground(shape(ORANGE_LIGHT, heightDp, 0));
        s.plus = roundBtn("+", heightDp, ORANGE, ON_ORANGE);
        s.minus = roundBtn("−", heightDp, MINUS_FILL, ORANGE_DARK);
        s.qty = new TextView(this);
        s.qty.setTextSize(16);
        s.qty.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        s.qty.setTextColor(INK);
        s.qty.setGravity(Gravity.CENTER);
        s.view.addView(s.plus);
        s.view.addView(s.qty, new LinearLayout.LayoutParams(dp(36), dp(heightDp)));
        s.view.addView(s.minus);
        return s;
    }

    private void setStatus(String message) { setStatus(message, false); }

    private void setStatus(String message, boolean error) {
        if (statusView == null) return;
        statusView.setText(message == null ? "" : message);
        statusView.setTextColor(error ? RED : MUTED);
        statusView.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (imm != null && focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    // ---- json helpers

    private String first(JSONObject obj, String... keys) {
        if (obj == null) return "";
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            String s;
            if (value instanceof JSONObject) s = ((JSONObject) value).optString("name", "");
            else if (value instanceof JSONArray) continue;
            else s = String.valueOf(value);
            s = s.trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
        }
        return "";
    }

    private String nameOf(JSONObject obj, String key) {
        if (obj == null) return "";
        JSONObject v = obj.optJSONObject(key);
        if (v == null) return "";
        String s = v.optString("name", "").trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private boolean truthy(Object v) {
        if (v == null || v == JSONObject.NULL) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        String s = String.valueOf(v).trim();
        return !s.isEmpty() && !"0".equals(s) && !"false".equalsIgnoreCase(s) && !"null".equalsIgnoreCase(s);
    }

    private double parseMoney(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        try {
            String normalized = raw.replace(",", ".").replaceAll("[^0-9.\\-]", "");
            return normalized.isEmpty() ? 0 : Double.parseDouble(normalized);
        } catch (Exception e) {
            return 0;
        }
    }

    private double num(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) return parseMoney((String) v);
        return 0;
    }

    private String fmtNum(double n) {
        String s = String.format(Locale.US, "%.2f", n);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private String money(double n) { return "₪" + fmtNum(n); }

    private String money2(double n) { return "₪" + String.format(Locale.US, "%.2f", n); }

    private JSONArray arrayFrom(String body) {
        try {
            Object v = new JSONTokener(body).nextValue();
            if (v instanceof JSONArray) return (JSONArray) v;
            if (v instanceof JSONObject) {
                JSONObject o = (JSONObject) v;
                JSONArray a = o.optJSONArray("data");
                if (a == null) a = o.optJSONArray("items");
                if (a == null) a = o.optJSONArray("products");
                if (a != null) return a;
            }
        } catch (Exception ignored) { }
        return new JSONArray();
    }

    private String cleanHtml(String s) {
        if (s == null || s.isEmpty()) return "";
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private String apiError(HttpResult r) {
        String body = r.body == null ? "" : r.body.trim();
        if (body.startsWith("{")) {
            try {
                JSONObject j = new JSONObject(body);
                String msg = cleanHtml(first(j, "message", "title", "error"));
                String detail = "";
                JSONObject errs = j.optJSONObject("errors");
                if (errs != null) {
                    java.util.Iterator<String> keys = errs.keys();
                    while (keys.hasNext() && detail.isEmpty()) {
                        Object v = errs.opt(keys.next());
                        if (v instanceof JSONArray && ((JSONArray) v).length() > 0) detail = labelOf(((JSONArray) v).opt(0));
                        else if (v instanceof String) detail = (String) v;
                    }
                }
                if (!detail.isEmpty()) return detail;                if (!msg.isEmpty()) return msg;
            } catch (Exception ignored) { }
        }
        return httpMessage(r.code);
    }

    private String httpMessage(int code) {
        if (code == 401) return "נדרשת התחברות מחדש";
        if (code == 403) return "אין הרשאה לבצע את הפעולה";
        if (code == 404) return "הפריט לא נמצא בשרת";
        if (code == 419 || code == 422) return "השרת לא קיבל את הנתונים";
        if (code == 429) return "יותר מדי בקשות. נסו שוב בעוד רגע";
        if (code >= 500) return "תקלה זמנית בשרת. נסו שוב";
        return "שגיאה (" + code + ")";
    }

    /** Never shows raw JSON / HTML / stack text to the user. */
    private String errText(Exception e, String fallback) {
        if (e instanceof AuthException) return e.getMessage();
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException) return "אין חיבור לשרת. בדקו את האינטרנט ונסו שוב.";
        if (e instanceof java.net.SocketTimeoutException) return "השרת לא הגיב בזמן. נסו שוב.";
        if (e instanceof java.io.IOException) return "בעיית תקשורת עם השרת. נסו שוב.";
        if (e instanceof org.json.JSONException) return "תשובת השרת לא הייתה במבנה צפוי. נסו לרענן.";
        String m = e.getMessage();
        if (m == null || m.trim().isEmpty()) return fallback;
        String t = m.trim();
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("<") || t.contains("<html") || t.contains("org.json") || t.contains("java.")) return fallback;
        return m;
    }

    private String labelOf(Object v) {
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof JSONObject) return first((JSONObject) v, "name", "title", "label");
        return String.valueOf(v);
    }

    // ---- dates

    private Date parseDt(String raw) {
        if (raw == null) return null;
        String v = raw.replace("T", " ").trim();
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd"};
        int[] lengths = {19, 16, 10};
        for (int i = 0; i < patterns.length; i++) {
            if (v.length() < lengths[i]) continue;
            try {
                return new SimpleDateFormat(patterns[i], Locale.US).parse(v.substring(0, lengths[i]));
            } catch (Exception ignored) { }
        }
        return null;
    }

    private String fmtDay(Date d, boolean withTime) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        String s = "יום " + HEB_DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + " " + new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d);
        return withTime ? s + " " + new SimpleDateFormat("HH:mm", Locale.US).format(d) : s;
    }

    private String fmtWhen(String raw) {
        Date d = parseDt(raw);
        return d == null ? (raw == null ? "" : raw) : new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(d);
    }

    private Date pickupDate(JSONObject sale) {
        Date d = parseDt(first(sale, "supply_begin"));
        if (d != null) return d;
        try {
            JSONObject parent = sale.optJSONObject("parent");
            JSONObject site = sale.optJSONObject("site");
            if (parent == null || site == null) return null;
            int dd = (int) num(site.opt("distribution_day"));
            int shift = (int) num(sale.opt("day_diff"));
            Date ws = parseDt(first(parent, "week_start"));
            if (ws == null || dd <= 0) return null;
            Calendar c = Calendar.getInstance();
            c.setTime(ws);
            c.add(Calendar.DAY_OF_MONTH, dd - 1 + shift);
            return c.getTime();
        } catch (Exception e) {
            return null;
        }
    }

    private String closingText(JSONObject sale) {
        Date end = parseDt(first(sale, "end"));
        if (end == null) return "";
        long left = end.getTime() - System.currentTimeMillis();
        if (left <= 0) return "המכירה נסגרה";
        long minutes = left / 60000L;
        long days = minutes / 1440L, hours = (minutes % 1440L) / 60L, mins = minutes % 60L;
        return "נסגרת בעוד " + (days > 0 ? days + " ימים " : "") + hours + " שעות " + mins + " דקות";
    }

    private String saleTitle(JSONObject sale) {
        if (sale == null) return "";
        String name = first(sale, "custom_name");
        if (name.isEmpty()) name = first(sale.optJSONObject("parent"), "week");
        if (name.isEmpty()) name = first(sale, "name", "title", "sale_name");
        return name;
    }

    // ======================================================================== screens: frame

    private void beginScreen(String heading, int tab, Runnable back) {
        if (showingSalesList && scrollView != null) {
            salesListScrollY = Math.max(0, scrollView.getScrollY());
        }
        if (showingOrderScreen && screenKind == 1 && productList != null) {
            // The products are in a ListView; scrollView is detached on the order screen.
            orderListFirstVisiblePosition = Math.max(0, productList.getFirstVisiblePosition());
            View firstVisibleRow = productList.getChildAt(0);
            orderListTopOffset = firstVisibleRow == null ? 0 : firstVisibleRow.getTop();
            returnToOrderScreenOnSalesTab = true;
        }
        showingSalesList = false;
        showingOrderScreen = false;
        screenToken++;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screenKind = 0;
        backAction = back;

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(HEADER_BG);
        header.setTag("appHeader");
        final int padStart = back != null ? dp(8) : dp(16);
        final int padEnd = dp(16);
        header.setPadding(rtlActive() ? padEnd : padStart, dp(1), rtlActive() ? padStart : padEnd, dp(1));
        header.setElevation(dp(3));
        if (back != null) {
            TextView arrow = text("→", 24, true);
            arrow.setTextColor(HEADER_FG);
            arrow.setTag("appHeaderText");
            arrow.setGravity(Gravity.CENTER);
            arrow.setPadding(0, 0, 0, 0);
            arrow.setOnClickListener(v -> back.run());
            // Keep the arrow typography as large as the 24sp screen title.
            header.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
        // The current screen title is enlarged by 50% and the night-mode toggle is always available.
        TextView head = text(heading, 24, true);
        head.setTextColor(HEADER_FG);
        head.setTag("appHeaderText");
        head.setMaxLines(1);
        head.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(head, new LinearLayout.LayoutParams(0, -2, 1));
        TextView actionsButton = text("⚙️", 16, true);
        actionsButton.setTextColor(nightMode ? INK : ORANGE_DARK);
        actionsButton.setGravity(Gravity.CENTER);
        actionsButton.setSingleLine(true);
        actionsButton.setIncludeFontPadding(false);
        actionsButton.setPadding(0, 0, 0, 0);
        actionsButton.setBackground(ripple(shape(SURFACE, 50, nightMode ? LINE : Color.WHITE), 50));
        actionsButton.setElevation(dp(2));
        actionsButton.setTag("globalSettingsMenu");
        actionsButton.setContentDescription("פתיחת הגדרות ופעולות");
        actionsButton.setOnClickListener(v -> showActionsDialog());
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        actionsParams.leftMargin = dp(2);
        header.addView(actionsButton, actionsParams);
        header.setPadding(rtlActive() ? padEnd : padStart, dp(4), rtlActive() ? padStart : padEnd, dp(4));
        screen.addView(header, new LinearLayout.LayoutParams(-1, -2));
        topHolder = new FrameLayout(this);
        // Keep the category strip above the scrolling product list.
        topHolder.setBackgroundColor(SURFACE);
        topHolder.setElevation(dp(12));
        topHolder.setClipChildren(false);
        screen.addView(topHolder, new LinearLayout.LayoutParams(-1, -2));
        catStripRow = null;
        catStripScroll = null;

        scrollView = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        statusView = muted("", 13);
        statusView.setVisibility(View.GONE);
        root.addView(statusView, lp(-1, -2, 0, 6));

        bottomHolder = new FrameLayout(this);
        screen.addView(bottomHolder, new LinearLayout.LayoutParams(-1, -2));

        View navView = null;
        if (hasToken()) {
            LinearLayout nav = new LinearLayout(this);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setBackgroundColor(SURFACE);
            nav.setElevation(dp(8));
            final String[] labels = {"מכירות פתוחות", "היסטוריה", "זיכויים", "חשבון"};
            final int[] iconTypes = {ICON_CART, ICON_CLOCK, ICON_WALLET, ICON_PERSON};
            Runnable[] actions = {
                    () -> navigateTab(0, this::showOpenSales),
                    () -> navigateTab(1, this::loadFeatureOrders),
                    () -> navigateTab(2, this::loadFeatureTransactions),
                    () -> navigateTab(3, this::showAccount)
            };
            nav.setClipChildren(false);
            screen.setClipChildren(false);
            List<View> navCells = new ArrayList<>(); // listed right to left, the way Hebrew is read
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                boolean active = i == tab;
                int tint = active ? ORANGE : (nightMode ? INK : AppPalette.navInactive(purpleTheme));
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                item.setPadding(dp(2), dp(5), dp(2), dp(5));
                item.setBackground(ripple(new GradientDrawable(), 0));
                item.addView(new IconView(iconTypes[i], tint, 2.8f), new LinearLayout.LayoutParams(dp(28), dp(28)));
                TextView label = text(labels[i], 11, true);
                label.setMaxLines(1);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setGravity(Gravity.CENTER);
                label.setTextColor(active ? ORANGE_DARK : INK);
                item.addView(label, new LinearLayout.LayoutParams(-1, -2));
                View bar = new View(this);
                bar.setBackground(shape(ORANGE, 2, 0));
                bar.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
                LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(24), dp(3));
                barLp.topMargin = dp(2);
                item.addView(bar, barLp);
                item.setTag("navTab:" + index);
                item.setFocusable(true);
                item.setContentDescription("מעבר לכרטיסייה " + labels[i]);
                item.setOnClickListener(v -> actions[index].run());
                item.setLayoutParams(new LinearLayout.LayoutParams(0, dp(64), 1));
                navCells.add(item);
                if (i == 1) navCells.add(collectNavButton(tab == TAB_COLLECT));
            }
            List<View> navWithLines = new ArrayList<>();
            for (int k = 0; k < navCells.size(); k++) {
                if (k > 0) navWithLines.add(navDivider());
                navWithLines.add(navCells.get(k));
            }
            addVisualRtl(nav, navWithLines);
            navView = nav;
            screen.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        }
        final View navFinal = navView;
        final LinearLayout headerFinal = header;
        final LinearLayout screenFinal = screen;
        screen.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT < 30) return insets; // older Android already keeps content inside the system bars
            int[] in = systemInsets(insets);
            headerFinal.setPadding((rtlActive() ? padEnd : padStart) + in[0], dp(1) + in[1], (rtlActive() ? padStart : padEnd) + in[2], dp(1));
            if (navFinal != null) {
                LinearLayout navLayout = (LinearLayout) navFinal;
                navLayout.setPadding(in[0], 0, in[2], 0);
                for (int k = 0; k < navLayout.getChildCount(); k++) {
                    View cell = navLayout.getChildAt(k);
                    if ("navDivider".equals(cell.getTag())) continue;
                    ViewGroup.LayoutParams cellLp = cell.getLayoutParams();
                    cellLp.height = dp(64) + in[3];
                    cell.setLayoutParams(cellLp);
                    if ("navCenter".equals(cell.getTag())) {
                        View dome = ((ViewGroup) cell).getChildAt(0);
                        ViewGroup.LayoutParams domeLp = dome.getLayoutParams();
                        domeLp.height = dp(NAV_FINGER_RISE + NAV_FINGER_INNER) + in[3];
                        dome.setLayoutParams(domeLp);
                        View inner = ((ViewGroup) dome).getChildAt(0); // same row as the other buttons
                        ViewGroup.LayoutParams innerLp = inner.getLayoutParams();
                        innerLp.height = dp(NAV_FINGER_INNER) + in[3];
                        inner.setLayoutParams(innerLp);
                        inner.setPadding(dp(2), dp(2), dp(2), dp(1) + in[3]);
                    } else {
                        cell.setPadding(dp(2), dp(5), dp(2), dp(5) + in[3]);
                    }
                }
                screenFinal.setPadding(0, 0, 0, 0);
            } else {
                screenFinal.setPadding(in[0], 0, in[2], in[3]);
            }
            return insets;
        });
        currentScreen = screen;
        setContentView(screen);
        screen.requestApplyInsets();
        styleSystemBars();
    }

    private TabScreenState captureTabScreenState() {
        TabScreenState state = new TabScreenState();
        state.screen = currentScreen;
        state.root = root;
        state.scrollView = scrollView;
        state.topHolder = topHolder;
        state.bottomHolder = bottomHolder;
        state.statusView = statusView;
        state.barInfo = barInfo;
        state.barTotal = barTotal;
        state.summaryTotal = summaryTotal;
        state.summarySub = summarySub;
        state.progressView = progressView;
        state.histMore = histMore;
        state.activeProductSearch = activeProductSearch;
        state.productSearchClearButton = productSearchClearButton;
        state.productList = productList;
        state.productAdapter = productAdapter;
        state.histList = histList;
        state.summaryList = summaryList;
        state.collectedBox = collectedBox;
        state.collectedList = collectedList;
        state.catStripRow = catStripRow;
        state.catStripScroll = catStripScroll;
        state.backAction = backAction;
        state.screenKind = screenKind;
        state.showingSalesList = showingSalesList;
        state.showingOrderScreen = showingOrderScreen;
        state.salesListScrollY = salesListScrollY;
        state.orderListFirstVisiblePosition = orderListFirstVisiblePosition;
        state.orderListTopOffset = orderListTopOffset;
        state.returnToOrderScreenOnSalesTab = returnToOrderScreenOnSalesTab;
        state.featureSale = featureSale;
        state.featureOrderId = featureOrderId;
        state.currentSaleId = currentSaleId;
        state.featureProducts = new ArrayList<>(featureProducts);
        state.featureQuantities = new LinkedHashMap<>(featureQuantities);
        state.pending = new HashSet<>(pending);
        state.favIds = new HashSet<>(favIds);
        state.saleSnapshots = new LinkedHashMap<>(saleSnapshots);
        state.favQtyCache = favQtyCache;
        state.filterText = filterText;
        state.viewMode = viewMode;
        state.activeCat = activeCat;
        state.histSearch = histSearch;
        state.histPage = histPage;
        state.histLast = histLast;
        state.historyTab = historyTab;
        state.productsLoading = productsLoading;
        state.productsPartial = productsPartial;
        state.emptyMsgShown = emptyMsgShown;
        state.summaryFromHistory = summaryFromHistory;
        state.collectMode = collectMode;
        state.productsLoadedAt = productsLoadedAt;
        state.summary = summary;
        state.gridRows = new ArrayList<>(gridRows);
        state.catHeaders = new LinkedHashMap<>(catHeaders);
        state.catCounts = new LinkedHashMap<>(catCounts);
        state.catChips = new LinkedHashMap<>(catChips);
        return state;
    }

    private void restoreTabScreenState(TabScreenState state) {
        currentScreen = state.screen;
        root = state.root;
        scrollView = state.scrollView;
        topHolder = state.topHolder;
        bottomHolder = state.bottomHolder;
        statusView = state.statusView;
        barInfo = state.barInfo;
        barTotal = state.barTotal;
        summaryTotal = state.summaryTotal;
        summarySub = state.summarySub;
        progressView = state.progressView;
        histMore = state.histMore;
        activeProductSearch = state.activeProductSearch;
        productSearchClearButton = state.productSearchClearButton;
        productList = state.productList;
        productAdapter = state.productAdapter;
        histList = state.histList;
        summaryList = state.summaryList;
        collectedBox = state.collectedBox;
        collectedList = state.collectedList;
        catStripRow = state.catStripRow;
        catStripScroll = state.catStripScroll;
        backAction = state.backAction;
        screenKind = state.screenKind;
        showingSalesList = state.showingSalesList;
        showingOrderScreen = state.showingOrderScreen;
        salesListScrollY = state.salesListScrollY;
        orderListFirstVisiblePosition = state.orderListFirstVisiblePosition;
        orderListTopOffset = state.orderListTopOffset;
        returnToOrderScreenOnSalesTab = state.returnToOrderScreenOnSalesTab;
        featureSale = state.featureSale;
        featureOrderId = state.featureOrderId;
        currentSaleId = state.currentSaleId;
        featureProducts.clear();
        featureProducts.addAll(state.featureProducts);
        featureQuantities.clear();
        featureQuantities.putAll(state.featureQuantities);
        pending.clear();
        pending.addAll(state.pending);
        favIds.clear();
        favIds.addAll(state.favIds);
        saleSnapshots.clear();
        saleSnapshots.putAll(state.saleSnapshots);
        favQtyCache = state.favQtyCache;
        filterText = state.filterText;
        viewMode = state.viewMode;
        activeCat = state.activeCat;
        histSearch = state.histSearch;
        histPage = state.histPage;
        histLast = state.histLast;
        historyTab = state.historyTab;
        productsLoading = state.productsLoading;
        productsPartial = state.productsPartial;
        emptyMsgShown = state.emptyMsgShown;
        summaryFromHistory = state.summaryFromHistory;
        collectMode = state.collectMode;
        productsLoadedAt = state.productsLoadedAt;
        summary = state.summary;
        gridRows.clear();
        gridRows.addAll(state.gridRows);
        catHeaders.clear();
        catHeaders.putAll(state.catHeaders);
        catCounts.clear();
        catCounts.putAll(state.catCounts);
        catChips.clear();
        catChips.putAll(state.catChips);
    }

    /** Switch to a tab by restoring its live view tree when it has already been opened. */
    private void navigateTab(int targetTab, Runnable firstOpenAction) {
        if (targetTab == activeBottomTab && currentScreen != null) {
            // Tapping the selected tab again returns to that tab's main screen.
            firstOpenAction.run();
            return;
        }
        if (currentScreen != null && activeBottomTab >= 0) {
            tabScreenCache.put(activeBottomTab, captureTabScreenState());
        }
        activeBottomTab = targetTab;
        TabScreenState cached = tabScreenCache.get(targetTab);
        if (cached != null && cached.screen != null) {
            // Prevent asynchronous callbacks from a hidden screen mutating the newly active screen.
            screenToken++;
            restoreTabScreenState(cached);
            if (targetTab == 0) returnToOrderScreenOnSalesTab = false;
            // If another screen changed the saved list, rebuild the cached list instead of
            // showing the stale view until the app is restarted.
            if (creditListDirty && cached.screen == creditListScreenView) {
                showCreditList();
                return;
            }
            setContentView(currentScreen);
            currentScreen.requestApplyInsets();
            styleSystemBars();
            return;
        }
        firstOpenAction.run();
    }

    // ======================================================================== login / account

    private void showLogin() {
        beginScreen("התחברות לחשבון", -1, null);
        TextView hello = text("ברוכים הבאים", 22, true);
        hello.setTextColor(ORANGE_DARK);
        root.addView(hello, lp(-1, -2, 24, 2));
        root.addView(muted("התחברו עם החשבון שלכם באתר משנת יוסף", 14), lp(-1, -2, 0, 18));

        emailField = field("כתובת אימייל", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setText(prefs.getString("email", ""));
        root.addView(emailField, lp(-1, dp(52), 0, 10));

        passwordField = field("סיסמה", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        passwordField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        passwordField.setOnEditorActionListener((v, actionId, event) -> { login(); return true; });
        root.addView(passwordField, lp(-1, dp(52), 0, 16));

        loginButton = btn("התחברות", PRIMARY);
        loginButton.setOnClickListener(v -> login());
        root.addView(loginButton, lp(-1, dp(52), 0, 8));
    }

    private void showAccount() {
        beginScreen("חשבון והגדרות", 3, null);
        LinearLayout acc = card(14);
        acc.addView(muted("מחובר כ", 12));
        acc.addView(text(prefs.getString("email", ""), 16, true));
        acc.addView(muted(tokenStatus(), 12));
        root.addView(acc, lp(-1, -2, 0, 10));
        addDisplaySettings();
        addKeyboardShortcutsButton();
        addHomeNotificationSettings();
        addHomeStockSettings();
        addHomeUpdateSettings();
        addHomeShareAppButton();
        addWhatsNewEntry();
        addSafetySettings();
        TextView contactBtn = btn("📞 יצירת קשר", TONAL);
        contactBtn.setOnClickListener(v -> showContactOptions());
        root.addView(contactBtn, lp(-1, dp(46), 0, 8));
        TextView developerMessageBtn = btn("✉️ שלח הודעה למפתחים", TONAL);
        developerMessageBtn.setOnClickListener(v -> {
            try {
                Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:londkirs@gmail.com"));
                mail.putExtra(Intent.EXTRA_SUBJECT, "פנייה למפתחי אפליקציית משנת יוסף");
                mail.putExtra(Intent.EXTRA_TEXT, "\n\nפרטי הפנייה:\n");
                startActivity(Intent.createChooser(mail, "שליחת הודעה למפתחים"));
            } catch (Exception e) {
                toast("לא נמצאה אפליקציית דואר במכשיר", true);
            }
        });
        root.addView(developerMessageBtn, lp(-1, dp(46), 0, 8));
        TextView refreshTok = btn("🔁 חידוש התחברות (בלי סיסמה)", TONAL);
        refreshTok.setOnClickListener(v -> executor.execute(() -> {
            String cur = prefs.getString("token", "");
            String next = refreshToken(cur);
            runOnUiThread(() -> { toast(next.isEmpty() ? "החידוש לא הצליח. ייתכן שנדרשת התחברות מחדש" : "ההתחברות חודשה", true); showAccount(); });
        }));
        root.addView(refreshTok, lp(-1, dp(46), 0, 8));
        TextView logBtn = btn("📡 יומן תקשורת (לאבחון תקלות)", TONAL);
        logBtn.setOnClickListener(v -> showApiLog());
        root.addView(logBtn, lp(-1, dp(46), 0, 8));
        TextView itemDiagBtn = btn("🔎 בדיקת API", TONAL);
        itemDiagBtn.setOnClickListener(v -> diagnoseAllServerJson());
        root.addView(itemDiagBtn, lp(-1, dp(46), 0, 8));
        TextView logout = btn("התנתקות", DANGER);
        logout.setOnClickListener(v -> {
            prefs.edit().remove("token").remove("expires_at").apply();
            showLogin();
        });
        root.addView(logout, lp(-1, dp(48), 16, 8));
        String appVersion = "לא ידועה";
        try {
            String detectedVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (detectedVersion != null && !detectedVersion.trim().isEmpty()) appVersion = detectedVersion;
        } catch (Exception ignored) { }
        TextView versionView = muted("גרסת האפליקציה: " + appVersion, 12);
        versionView.setGravity(Gravity.CENTER);
        root.addView(versionView, lp(-1, -2, 0, 16));
    }

    // Release notes are embedded in this APK at build time. This screen does not
    // fetch its content from GitHub and remains available offline.
    private void addWhatsNewEntry() {
        String latestTitle = getString(R.string.release_notes_title).trim();
        String latestDetails = getString(R.string.release_notes_details).trim();
        if (latestTitle.isEmpty()) latestTitle = "העדכון האחרון";
        if (latestDetails.isEmpty()) latestDetails = latestTitle;

        LinearLayout whatsNewCard = card(14);
        whatsNewCard.addView(sectionTitle("✨ מה חדש"));
        TextView summary = muted(latestDetails.split("\\n\\s*\\n", 2)[0], 13);
        whatsNewCard.addView(summary);
        TextView open = btn("✨ מה חדש באפליקציה", TONAL);
        final String titleForDialog = latestTitle;
        final String detailsForDialog = latestDetails;
        open.setOnClickListener(v -> appDialog()
                .setTitle("✨ מה חדש במשנת יוסף")
                .setMessage(titleForDialog + "\n\n" + detailsForDialog)
                .setPositiveButton("הבנתי", null)
                .show());
        whatsNewCard.addView(open, lp(-1, dp(46), 0, 8));
        root.addView(whatsNewCard, lp(-1, -2, 0, 10));
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        setStatus(message, false);
        if (loginButton != null) setEnabledLook(loginButton, !value);
    }

    private void login() {
        if (busy) return;
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            setStatus("יש למלא אימייל וסיסמה.", true);
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
                    toast("✔ התחברת בהצלחה", false);
                    SaleMonitor.scheduleNextStart(this);
                    int pendingStockSaleId = prefs.getInt(PREF_PENDING_STOCK_SALE_ID, -1);
                    String pendingStockSid = prefs.getString(PREF_PENDING_STOCK_SID, "");
                    int pendingSaleId = prefs.getInt(PREF_PENDING_NOTIFICATION_SALE_ID, -1);
                    if (pendingStockSaleId > 0 && !pendingStockSid.isEmpty())
                        openSaleFromStockNotification(pendingStockSaleId, pendingStockSid);
                    else if (pendingSaleId > 0) openSaleFromNotification(pendingSaleId);
                    else showOpenSales();
                });
            } catch (Exception e) {
                String message = errText(e, "ההתחברות נכשלה.");
                runOnUiThread(() -> { setBusy(false, ""); setStatus(message, true); });
            }
        });
    }

    // ======================================================================== stock alerts and automatic ordering
    private String stockKey(JSONObject p) { return StockMonitor.keyFor(featureSale == null ? -1 : featureSale.optInt("id", -1), sidOf(p)); }
    private JSONObject findStockWatch(JSONArray a, String key) {
        for (int i=0;i<a.length();i++) { JSONObject w=a.optJSONObject(i); if(w!=null && key.equals(w.optString("key",""))) return w; } return null;
    }
    private boolean stockWatchMode(JSONObject p,String mode) {
        if(p==null || featureSale==null)return false; JSONObject w=findStockWatch(StockMonitor.loadWatches(this),stockKey(p));
        return w!=null && mode.equals(w.optString("mode","alert"));
    }
    private int stockDraftQty(JSONObject p) { Integer q=stockDraftQuantities.get(stockKey(p)); return q==null?1:Math.max(1,q); }
    private void adjustStockDraft(JSONObject p,int delta,Stepper st) {
        int q=Math.max(1,Math.min(999,stockDraftQty(p)+delta)); int max=maxPerMan(p); if(max>0)q=Math.min(q,max); stockDraftQuantities.put(stockKey(p),q); st.qty.setText(String.valueOf(q));
    }
    private void saveStockAlert(JSONObject p) {
        if(p==null || featureSale==null)return; String key=stockKey(p); JSONObject old=findStockWatch(StockMonitor.loadWatches(this),key);
        if(old!=null){toast("המעקב כבר קיים. אפשר לנהל אותו בהגדרות.",false);showStockWatchManagement();return;}
        upsertStockWatch(p,"alert",1,false);
    }
    private void saveAutoOrder(JSONObject p,int qty) {
        if(p==null || featureSale==null)return; int max=maxPerMan(p); if(max>0&&qty>max){toast("הכמות חורגת מהמקסימום למוצר: "+max,true);return;} String key=stockKey(p); JSONObject old=findStockWatch(StockMonitor.loadWatches(this),key);
        if(old!=null && "auto".equals(old.optString("mode","alert")) && old.optBoolean("consent",false)) {
            JSONArray a=StockMonitor.loadWatches(this);
            for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w!=null&&key.equals(w.optString("key","")))try{w.put("qty",Math.max(1,qty));}catch(Exception ignored){}}
            StockMonitor.saveWatches(this,a); StockMonitor.appendLog(this,key,productName(p),"עודכנה כמות להזמנה אוטומטית","כמות: "+qty);
            toast("✔ עודכנה הכמות המבוקשת",false);return;
        }
        confirmAutoOrderConsent(productName(p),qty,()->upsertStockWatch(p,"auto",qty,true));
    }
    private void confirmAutoOrderConsent(String name,int qty,Runnable confirmed) {
        appDialog().setTitle("⚠️ אישור הזמנה אוטומטית")
          .setMessage("המוצר: "+name+"\nכמות: "+qty+"\n\nהמערכת תנסה להזמין את המוצר כשהוא יחזור למלאי. הדבר עשוי לגרום לחיוב כספי בלי פעולה נוספת שלך.\n\nהשמירה כעת מקומית בלבד ואינה שולחת הזמנה לשרת. אם תוצאת בקשה אינה ודאית, לא יישלח ניסיון חוזר אוטומטי.")
          .setNegativeButton("ביטול",null).setPositiveButton("אני מאשר/ת", (d,w)->confirmed.run()).show();
    }
    private void upsertStockWatch(JSONObject p,String mode,int qty,boolean consent) {
        int saleId=featureSale==null?-1:featureSale.optInt("id",-1); String sid=sidOf(p);
        if(saleId<=0||sid.isEmpty()){toast("חסר מזהה מוצר או מכירה.",true);return;}
        String key=StockMonitor.keyFor(saleId,sid); JSONArray a=StockMonitor.loadWatches(this); JSONObject w=findStockWatch(a,key);
        boolean fresh=w==null; String oldMode=fresh?"":w.optString("mode","alert"); if(fresh)w=new JSONObject();
        try {
            w.put("key",key).put("saleId",saleId).put("sid",sid).put("name",productName(p)).put("saleName",saleTitle(featureSale))
             .put("mode",mode).put("qty",Math.max(1,qty)).put("consent",consent);
            if(fresh){w.put("createdAt",System.currentTimeMillis()).put("lastAvailability",0).put("state","waiting");a.put(w);}
            else if("auto".equals(mode)&&!"auto".equals(oldMode))w.put("state","waiting").put("lastAvailability",0);
        }catch(Exception e){toast("שמירת המעקב נכשלה.",true);return;}
        StockMonitor.saveWatches(this,a);
        StockMonitor.appendLog(this,key,productName(p),"נוסף למעקב","סוג: "+mode+"; כמות: "+qty);
        toast("auto".equals(mode)?"✔ נשמר להזמנה אוטומטית; לא נשלחה הזמנה לשרת.":"✔ נשמרה התראה לחזרה למלאי.",false);
        productsRefresh.run();
    }
    private View stockMonitorCard() {
        LinearLayout c=card(14); c.addView(sectionTitle("📦 התראות מלאי והזמנות אוטומטיות"));
        c.addView(muted("השמירה מקומית בלבד. הזמנה אוטומטית עשויה לגרום לחיוב כספי.",12));
        c.addView(switchRow("בדיקת מלאי אוטומטית","בדיקה משותפת של כל המכירות הפתוחות והמוצרים הרלוונטיים",prefs.getBoolean(StockMonitor.ENABLED,true),(b,on)->{
            prefs.edit().putBoolean(StockMonitor.ENABLED,on).apply();StockMonitor.scheduleNextCheck(this);toast(on?"בדיקת מלאי הופעלה":"בדיקת מלאי כובתה",false);
        }));
        c.addView(switchRow("התראה גלובלית כשהמוצר אוזל","כל המוצרים הרלוונטיים בכל המכירות הפתוחות",prefs.getBoolean("stock_global_notify_out",false),(b,on)->{
            prefs.edit().putBoolean("stock_global_notify_out",on).apply(); if(on) maybePromptPersistentNotification(); else refreshPersistentNotification();StockMonitor.scheduleNextCheck(this);
        }));
        c.addView(switchRow("התראה גלובלית כשהמוצר חוזר למלאי","כל המוצרים הרלוונטיים; התראה רק בעת שינוי מצב",prefs.getBoolean("stock_global_notify_return",false),(b,on)->{
            prefs.edit().putBoolean("stock_global_notify_return",on).apply(); if(on) maybePromptPersistentNotification(); else refreshPersistentNotification();StockMonitor.scheduleNextCheck(this);
        }));
        c.addView(switchRow("התראה כשהמוצר אוזל במעקב אישי","בנוסף להתראות הגלובליות, למוצרים שנבחרו ידנית",prefs.getBoolean("stock_notify_out",true),(b,on)->{prefs.edit().putBoolean("stock_notify_out",on).apply(); if(on) maybePromptPersistentNotification(); else refreshPersistentNotification();}));
        c.addView(switchRow("התראה כשהמוצר חוזר במעקב אישי","מעקב אישי; הזמנה אוטומטית תציג את תוצאת הניסיון",prefs.getBoolean("stock_notify_return",true),(b,on)->{prefs.edit().putBoolean("stock_notify_return",on).apply(); if(on) maybePromptPersistentNotification(); else refreshPersistentNotification();}));
        c.addView(text("תדירות בדיקה (דקות)",14,true),lp(-1,-2,0,4)); LinearLayout chips=new LinearLayout(this);
        for(int n:new int[]{1,2,5,10,15,30,60}){TextView ch=pickChip(String.valueOf(n));styleChip(ch,prefs.getInt(StockMonitor.INTERVAL,5)==n);
            ch.setOnClickListener(v->{prefs.edit().putInt(StockMonitor.INTERVAL,n).apply();StockMonitor.scheduleNextCheck(this);showStockSettings();});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(42),dp(38));cp.setMarginEnd(dp(4));chips.addView(ch,cp);}
        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.addView(chips);c.addView(hs,lp(-1,-2,0,4));
        return c;
    }
    private String stockStateLabel(JSONObject w) {
        if(!"auto".equals(w.optString("mode","alert")))return "🔔 התראה בלבד";
        String st=w.optString("state","waiting");
        if("attempting".equals(st))return "⏳ מתבצע ניסיון הזמנה";
        if("success".equals(st))return "✔ ההזמנה אומתה";
        if("failed".equals(st))return "✖ ניסיון ההזמנה נכשל";
        if("uncertain".equals(st))return "⚠ תוצאה לא ודאית — אין ניסיון חוזר אוטומטי";
        return "🛒 הזמנה אוטומטית · ממתין למלאי";
    }
    private void showStockWatchManagement() {
        beginScreen("ניהול מעקב מלאי",3,this::showStockSettings);
        screenKind = 5;
        root.addView(muted("שנו כמויות, החליפו סוג מעקב או הסירו מוצרים. הזמנה אוטומטית עשויה לגרום לחיוב.",13),lp(-1,-2,0,8));
        JSONArray a=StockMonitor.loadWatches(this);
        if(a.length()==0)root.addView(text("אין מוצרים במעקב כרגע.",15,true),lp(-1,-2,0,12));
        for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w==null)continue;final String key=w.optString("key",""),mode=w.optString("mode","alert");
            LinearLayout c=card(12);c.addView(text(w.optString("name","מוצר"),16,true));c.addView(muted(w.optString("saleName","מכירה"),12));
            TextView status=text(stockStateLabel(w),13,true);status.setTextColor("success".equals(w.optString("state",""))?GREEN:("failed".equals(w.optString("state",""))||"uncertain".equals(w.optString("state",""))?RED:ORANGE_DARK));c.addView(status,lp(-1,-2,0,5));
            if("auto".equals(mode)){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text("כמות להזמנה:",13,true),new LinearLayout.LayoutParams(0,-2,1));
                Stepper st=stepper(34);st.qty.setText(String.valueOf(Math.max(1,w.optInt("qty",1))));st.plus.setOnClickListener(v->updateStockWatchQuantity(key,1));st.minus.setOnClickListener(v->updateStockWatchQuantity(key,-1));row.addView(st.view);c.addView(row,lp(-1,dp(40),0,4));
                if(!w.optString("lastResult","").isEmpty())c.addView(muted(w.optString("lastResult"),12));}
            TextView change=btn("auto".equals(mode)?"שינוי להתראה בלבד":"הפעלה כהזמנה אוטומטית",TONAL);
            change.setOnClickListener(v->{if("auto".equals(mode))changeStockWatchMode(key,"alert");else confirmAutoOrderConsent(w.optString("name","מוצר"),Math.max(1,w.optInt("qty",1)),()->changeStockWatchMode(key,"auto"));});
            c.addView(change,lp(-1,dp(40),0,6));
            TextView remove=btn("הסרת המוצר מהמעקב",DANGER);remove.setOnClickListener(v->appDialog().setTitle("הסרת מוצר מהמעקב").setMessage("להסיר את "+w.optString("name","המוצר")+"? לא תתבצע עבורו התראה או הזמנה נוספת.").setNegativeButton("ביטול",null).setPositiveButton("הסרה",(d,x)->removeStockWatch(key)).show());
            c.addView(remove,lp(-1,dp(40),0,6));root.addView(c,lp(-1,-2,0,8));}
        TextView log=btn("📋 פתיחת יומן הפעולות",TONAL);log.setOnClickListener(v->showStockMonitorLog());root.addView(log,lp(-1,dp(44),0,8));
    }
    private void updateStockWatchQuantity(String key,int delta) {
        JSONArray a=StockMonitor.loadWatches(this);for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w!=null&&key.equals(w.optString("key",""))&&"auto".equals(w.optString("mode","alert"))){if("attempting".equals(w.optString("state",""))){toast("מתבצע ניסיון הזמנה. אפשר לשנות את הכמות לאחר קבלת התוצאה.",false);return;}int q=Math.max(1,Math.min(999,w.optInt("qty",1)+delta));try{w.put("qty",q);}catch(Exception ignored){}StockMonitor.appendLog(this,key,w.optString("name",""),"עודכנה כמות","כמות: "+q);break;}}
        StockMonitor.saveWatches(this,a);showStockWatchManagement();
    }
    private void changeStockWatchMode(String key,String mode) {
        JSONArray a=StockMonitor.loadWatches(this);for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w==null||!key.equals(w.optString("key","")))continue;
            if("attempting".equals(w.optString("state",""))){toast("מתבצע ניסיון הזמנה; אפשר לשנות את סוג המעקב לאחר קבלת התוצאה.",false);return;}
            try{w.put("mode",mode).put("consent","auto".equals(mode));if("auto".equals(mode))w.put("state","waiting");}catch(Exception ignored){}
            StockMonitor.appendLog(this,key,w.optString("name",""),"שונה סוג המעקב",mode);break;}
        StockMonitor.saveWatches(this,a);showStockWatchManagement();
    }
    private void removeStockWatch(String key) {
        JSONArray a=StockMonitor.loadWatches(this),kept=new JSONArray();String name="";
        for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w!=null&&key.equals(w.optString("key",""))&&"attempting".equals(w.optString("state",""))){toast("כבר מתבצע ניסיון הזמנה. אי אפשר לבטל בקשה שכבר נשלחה; אפשר להסיר לאחר שהתוצאה תתקבל.",true);return;}}
        for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);if(w==null)continue;if(key.equals(w.optString("key","")))name=w.optString("name","");else kept.put(w);}
        StockMonitor.saveWatches(this,kept);StockMonitor.appendLog(this,key,name,"הוסר מהמעקב","המשתמש הסיר את המוצר.");showStockWatchManagement();
    }
    private void showStockMonitorLog() {
        beginScreen("יומן מעקב מלאי",3,this::showStockWatchManagement);
        screenKind = 6;JSONArray a=StockMonitor.loadLog(this);
        if(a.length()==0)root.addView(text("עדיין אין פעולות ביומן.",14,true));
        for(int i=a.length()-1;i>=0;i--){JSONObject w=a.optJSONObject(i);if(w==null)continue;String time=new SimpleDateFormat("dd/MM HH:mm:ss",Locale.US).format(new Date(w.optLong("time",0)));
            LinearLayout c=card(10);c.addView(text(w.optString("event","פעולה"),14,true));if(!w.optString("product","").isEmpty())c.addView(muted(w.optString("product"),12));c.addView(muted(time,11));if(!w.optString("detail","").isEmpty())c.addView(muted(w.optString("detail"),12));root.addView(c,lp(-1,-2,0,6));}
    }

    // ======================================================================== notification settings

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
        if (requestCode == 4101) { requestMonitoringPermissions(false); refreshPersistentNotification(); }
    }

    private static final int[] CAL_DAYS = {Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY};

    private String dayName(int calDay) { return "יום " + HEB_DAYS[indexOf(CAL_DAYS, calDay)]; }

    private String hoursText(int minutes) {
        if (minutes < 60) return minutes + " דקות";
        if (minutes % 60 == 0) return minutes == 60 ? "שעה" : (minutes / 60) + " שעות";
        return fmtNum(minutes / 60.0) + " שעות";
    }

    private String humanDuration(long ms) {
        long minutes = Math.max(0, ms / 60000L);
        long days = minutes / 1440L, hours = (minutes % 1440L) / 60L, mins = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days == 1 ? "יום" : days + " ימים");
        if (hours > 0) { if (sb.length() > 0) sb.append(" ו-"); sb.append(hours == 1 ? "שעה" : hours + " שעות"); }
        if (mins > 0 && days == 0) { if (sb.length() > 0) sb.append(" ו-"); sb.append(mins).append(" דקות"); }
        return sb.length() == 0 ? "פחות מדקה" : sb.toString();
    }

    private String monitorSummary() {
        if (!prefs.getBoolean("sale_monitor_enabled", true)) return "כבוי";
        return "פועלת מ" + dayName(prefs.getInt("monitor_start_day", Calendar.THURSDAY)) + " " + prefs.getString("monitor_start_time", "19:00")
                + " עד " + dayName(prefs.getInt("monitor_end_day", Calendar.SUNDAY)) + " " + prefs.getString("monitor_end_time", "10:00")
                + " · בדיקה כל " + prefs.getInt("monitor_interval_minutes", 5) + " דקות";
    }

    private String notificationStatusText() {
        String closing = prefs.getBoolean("closing_reminder_enabled", false)
                ? hoursText(prefs.getInt("closing_reminder_minutes", 120)) + " לפני הסגירה" : "כבוי";
        return "מכירה חדשה: " + monitorSummary() + "\nתזכורת סגירה: " + closing;
    }

    private void addHomeNotificationSettings() {
        LinearLayout card = card(14);
        card.addView(text("🔔 התראות", 15, true));
        TextView status = muted(notificationStatusText(), 13);
        status.setPadding(dp(2), dp(4), dp(2), dp(10));
        card.addView(status, new LinearLayout.LayoutParams(-1, -2));
        TextView settings = btn("⚙️ הגדרות התראות", TONAL);
        settings.setOnClickListener(v -> showNotificationSettings());
        card.addView(settings, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(card, lp(-1, -2, 0, 10));
    }

    private void addHomeStockSettings() {
        LinearLayout card = card(14);
        card.addView(text("📦 אזל מהמלאי", 15, true));
        card.addView(muted("התראות מלאי, מעקב מוצרים והזמנות אוטומטיות", 13), lp(-1, -2, 0, 8));
        TextView settings = btn("⚙️ ניהול הגדרות", TONAL);
        settings.setOnClickListener(v -> showStockSettings());
        card.addView(settings, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(card, lp(-1, -2, 0, 10));
    }

    private void addHomeUpdateSettings() {
        LinearLayout card = card(14);
        card.addView(text("📲 עדכוני אפליקציה", 15, true));
        card.addView(muted("בדיקת גרסה חדשה, הורדה והתקנה מתוך האפליקציה", 13), lp(-1, -2, 0, 8));
        TextView settings = btn("⬇️ בדוק עדכונים והתקנה", TONAL);
        settings.setOnClickListener(v -> showUpdateSettings());
        card.addView(settings, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(card, lp(-1, -2, 0, 10));
    }

    private void addHomeShareAppButton() {
        LinearLayout card = card(14);
        card.addView(text("📤 שיתוף האפליקציה", 15, true));
        card.addView(muted("שליחת קובץ ההתקנה של משנת יוסף לחבר", 13), lp(-1, -2, 0, 8));
        TextView share = btn("📤 שיתוף קובץ APK", TONAL);
        share.setOnClickListener(v -> shareAppToFriend());
        card.addView(share, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(card, lp(-1, -2, 0, 10));
    }

    private File sharedApkFile() {
        File dir = new File(getCacheDir(), "shared_apk");
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new File(dir, "mishnat-yosef.apk");
    }

    private boolean copyVerifiedApk(InputStream input, File destination, String expectedSha256) throws Exception {
        if (input == null || destination == null) throw new java.io.IOException("קובץ האפליקציה אינו זמין");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        File temp = new File(destination.getParentFile(), "share-download.tmp");
        try (InputStream in = input; OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                out.write(buffer, 0, count);
            }
            out.flush();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b & 0xff));
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-fA-F]{64}")
                || !expectedSha256.equalsIgnoreCase(hex.toString())) {
            temp.delete();
            throw new java.io.IOException("קובץ האפליקציה לא עבר אימות SHA-256");
        }
        if (destination.exists() && !destination.delete()) {
            temp.delete();
            throw new java.io.IOException("לא ניתן להחליף את קובץ השיתוף הקודם");
        }
        if (!temp.renameTo(destination)) {
            try (InputStream in = new java.io.FileInputStream(temp);
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
            temp.delete();
        }
        return destination.isFile() && destination.length() > 0;
    }

    private void shareVerifiedApk(File apk) {
        if (apk == null || !apk.isFile() || apk.length() <= 0) {
            toast("קובץ האפליקציה לא נמצא. נסה שוב.", true);
            return;
        }
        Uri uri = Uri.withAppendedPath(Uri.parse("content://org.mishnatyosef.mobile.jsonreports"), "mishnat-yosef.apk");
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/vnd.android.package-archive");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, "משנת יוסף");
        share.putExtra(Intent.EXTRA_TITLE, "משנת יוסף.apk");
        share.setClipData(ClipData.newUri(getContentResolver(), "משנת יוסף.apk", uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            Intent chooser = Intent.createChooser(share, "שיתוף קובץ APK");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Exception error) {
            String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            toast("לא ניתן לפתוח את חלון השיתוף: " + detail, true);
        }
    }

    private void shareAppToFriend() {
        toast("מכין את קובץ האפליקציה לשיתוף…", false);
        executor.execute(() -> {
            File target = sharedApkFile();
            try {
                if (target == null) throw new java.io.IOException("לא ניתן ליצור קובץ שיתוף");
                long savedId = prefs.getLong("update_download_id", -1L);
                int savedCode = prefs.getInt("update_download_verified_code", -1);
                int knownLatestCode = prefs.getInt("update_latest_code", -1);
                String knownSha = prefs.getString("update_latest_sha256", "");
                boolean savedVerified = prefs.getBoolean("update_download_verified", false);
                String savedPath = prefs.getString("update_download_file", "");
                File savedFile = savedPath == null || savedPath.trim().isEmpty() ? null : new File(savedPath);
                if (savedVerified && savedCode > 0 && savedCode == knownLatestCode
                        && knownSha.matches("[0-9a-fA-F]{64}")) {
                    if (savedFile != null && savedFile.isFile() && savedFile.canRead()) {
                        try (InputStream in = new java.io.FileInputStream(savedFile)) {
                            copyVerifiedApk(in, target, knownSha);
                        }
                        runOnUiThread(() -> shareVerifiedApk(target));
                        return;
                    }
                    if (savedId > 0) {
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        Uri savedUri = dm == null ? null : dm.getUriForDownloadedFile(savedId);
                        if (savedUri != null) {
                            try (InputStream in = getContentResolver().openInputStream(savedUri)) {
                                copyVerifiedApk(in, target, knownSha);
                            }
                            runOnUiThread(() -> shareVerifiedApk(target));
                            return;
                        }
                    }
                }

                JSONObject manifest = fetchUpdateManifest();
                String apkUrl = manifest.optString("apkUrl", UPDATE_APK_URL).trim();
                String sha = manifest.optString("sha256", "").trim();
                boolean expectedUrl = UPDATE_APK_URL.equals(apkUrl)
                        || "https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/mishnat-yosef/app.apk".equals(apkUrl);
                if (!expectedUrl) throw new java.io.IOException("כתובת ה־APK במידע העדכון אינה צפויה");
                if (!sha.matches("[0-9a-fA-F]{64}")) throw new java.io.IOException("מידע האימות של הגרסה חסר או לא תקין");
                HttpURLConnection connection = (HttpURLConnection) new URL(apkUrl).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                try {
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) throw new java.io.IOException("שרת ההורדה החזיר שגיאה " + status);
                    try (InputStream in = connection.getInputStream()) {
                        copyVerifiedApk(in, target, sha);
                    }
                } finally {
                    connection.disconnect();
                }
                prefs.edit().putInt("update_latest_code", manifest.optInt("versionCode", 0))
                        .putString("update_latest_name", manifest.optString("versionName", ""))
                        .putString("update_latest_sha256", sha).apply();
                runOnUiThread(() -> shareVerifiedApk(target));
            } catch (Exception error) {
                String detail = error.getMessage() == null ? "שגיאה לא ידועה" : error.getMessage();
                runOnUiThread(() -> toast("לא ניתן להכין את קובץ השיתוף: " + detail, true));
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (screenKind == 3 && hasToken()) showNotificationSettings(); // permissions may have changed in system settings
        else if (screenKind == 4 && hasToken()) showStockSettings();
        else if (screenKind == 7 && prefs.getBoolean("update_auto_install_pending", false)
                && Build.VERSION.SDK_INT >= 26 && getPackageManager().canRequestPackageInstalls()) {
            installDownloadedUpdate();
        }
    }

    private void showNotificationSettings() {
        beginScreen("הגדרות התראות", 3, this::showAccount);
        screenKind = 3;
        root.addView(permissionsCard(), lp(-1, -2, 0, 10));
        root.addView(persistentNotificationCard(), lp(-1, -2, 0, 10));
        root.addView(notificationBehaviorCard(), lp(-1, -2, 0, 10));
        root.addView(newSaleCard(), lp(-1, -2, 0, 10));
        root.addView(closingCard(), lp(-1, -2, 0, 10));
    }

    private void showStockSettings() {
        beginScreen("הגדרות אזל מהמלאי", 3, this::showAccount);
        screenKind = 4;
        root.addView(stockMonitorCard(), lp(-1, -2, 0, 10));
        TextView orders = btn("🛒 ניהול הזמנות אוטומטיות ומוצרים במעקב", PRIMARY);
        orders.setOnClickListener(v -> showStockWatchManagement());
        root.addView(orders, lp(-1, dp(44), 0, 8));
        TextView log = btn("📋 היסטוריית פעולות מלאי", TONAL);
        log.setOnClickListener(v -> showStockMonitorLog());
        root.addView(log, lp(-1, dp(42), 0, 6));
    }

    /**
     * Shows a success message only when the installed version actually changed after
     * the user started an update from this app. Pending details are stored immediately
     * before opening Android's installer and are cleared on failure/cancellation.
     */
    private boolean showPendingInAppUpdateSuccess() {        if (!prefs.getBoolean("in_app_update_success_pending", false)) return false;

        int oldCode = prefs.getInt("in_app_update_old_code", 0);
        String oldName = prefs.getString("in_app_update_old_name", "לא ידועה");
        int targetCode = prefs.getInt("in_app_update_target_code", 0);
        String targetName = prefs.getString("in_app_update_target_name", "");
        // The newly installed APK contains its own release notes, so the success
        // message does not depend on the remote GitHub manifest being reachable.
        String notes = getString(R.string.release_notes_details).trim();
        if (notes.isEmpty()) notes = getString(R.string.release_notes_title).trim();
        int currentCode = installedVersionCode();
        String currentName = installedVersionName();

        // No version change means installation was cancelled or did not succeed.
        if (oldCode <= 0 || targetCode <= 0 || currentCode == oldCode || currentCode != targetCode) {
            clearPendingInAppUpdateSuccess();
            return false;
        }

        clearPendingInAppUpdateSuccess();
        String message = "מגרסה " + oldName + " (" + oldCode + ") לגרסה "
                + currentName + " (" + currentCode + ")."
                + "\n\nמה חדש?\n"
                + (notes.isEmpty() ? "לא צורף פירוט שינויים לגרסה זו." : notes);
        appDialog()
                .setTitle("✅ האפליקציה עודכנה בהצלחה!")
                .setMessage(message)
                .setPositiveButton("מעולה", null)
                .show();
        return true;
    }

    private void clearPendingInAppUpdateSuccess() {
        prefs.edit()
                .remove("in_app_update_success_pending")
                .remove("in_app_update_old_code")
                .remove("in_app_update_old_name")
                .remove("in_app_update_target_code")
                .remove("in_app_update_target_name")
                .remove("in_app_update_notes")
                .apply();
    }

    private int installedVersionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) info.getLongVersionCode();
            return info.versionCode;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String installedVersionName() {
        try {
            String value = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return value == null || value.trim().isEmpty() ? "לא ידועה" : value.trim();
        } catch (Exception ignored) {
            return "לא ידועה";
        }
    }

    private void checkForUpdatesOnLaunch() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        if (!prefs.getBoolean("update_auto_check_on_launch", true)) return;
        executor.execute(() -> {
            try {
                JSONObject manifest = fetchUpdateManifest();
                int code = manifest.getInt("versionCode");
                String name = manifest.getString("versionName");
                String sha = manifest.getString("sha256");
                String notes = manifest.optString("releaseNotes", "").trim();
                int installed = installedVersionCode();
                prefs.edit().putInt("update_latest_code", code)
                        .putString("update_latest_name", name)
                        .putString("update_latest_sha256", sha)
                        .putString("update_release_notes", notes).apply();
                if (code == installed) return;
                runOnUiThread(() -> {
                    if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                    appDialog()
                            .setTitle("📲 נמצאה גרסה שונה ב־GitHub")
                            .setMessage("נמצאה גרסה שונה ב־GitHub של משנת יוסף: " + name + " (" + code + ")."
                                    + (notes.isEmpty() ? "" : "\n\n" + notes)
                                    + "\n\nרוצה לעבור לעמוד העדכונים ולהתחיל אוטומטית את ההורדה וההתקנה?")
                            .setNegativeButton("לא עכשיו", null)
                            .setPositiveButton("כן, לעדכן", (dialog, which) -> {
                                autoDownloadAfterCheck = true;
                                showUpdateSettings();
                                checkForAppUpdate();
                            })
                            .show();
                });
            } catch (Exception ignored) {
                // Network or manifest failures must never block app startup or show a false update prompt.
            }
        });
    }

    private void showUpdateSettings() {
        beginScreen("עדכוני אפליקציה", 3, this::showAccount);
        screenKind = 7;
        LinearLayout card = card(14);
        card.addView(text("📲 עדכוני אפליקציה", 17, true));
        Switch autoCheck = new Switch(this);
        autoCheck.setText("בדיקה אוטומטית בכל פתיחה");
        autoCheck.setTextSize(14);
        autoCheck.setChecked(prefs.getBoolean("update_auto_check_on_launch", true));
        autoCheck.setOnCheckedChangeListener((button, enabled) ->
                prefs.edit().putBoolean("update_auto_check_on_launch", enabled).apply());
        card.addView(autoCheck, lp(-1, -2, 0, 4));
        card.addView(muted("כאשר האפשרות פעילה, תיבדק גרסה חדשה בכל פתיחה. בדיקה והתקנה ידניות נשארות זמינות גם כשהמתג כבוי.", 12), lp(-1, -2, 0, 6));
        TextView installedVersionView = text("הגרסה המותקנת: " + installedVersionName() + " (" + installedVersionCode() + ")", 16, true);
        installedVersionView.setTextColor(INK);
        card.addView(installedVersionView, lp(-1, -2, 0, 8));
        updateStatusView = text("תוצאות הבדיקה האחרונה: לחץ על „בדוק אם יש עדכון”.", 15, true);
        updateStatusView.setTextColor(MUTED);
        updateStatusView.setPadding(dp(2), dp(10), dp(2), dp(8));
        updateStatusView.setOnClickListener(v -> {
            if (updateLatestCode > installedVersionCode()
                    && updateLatestSha256.matches("[0-9a-f]{64}")) {
                downloadLatestUpdate();
            }
        });
        updateStatusView.setClickable(false);
        card.addView(updateStatusView, new LinearLayout.LayoutParams(-1, -2));
        updateProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        updateProgressBar.setMax(100);
        updateProgressBar.setProgress(0);
        updateProgressBar.setProgressTintList(ColorStateList.valueOf(ORANGE));
        updateProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(ORANGE_LIGHT));
        updateProgressBar.setIndeterminateTintList(ColorStateList.valueOf(ORANGE));
        updateProgressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = lp(-1, dp(10), 0, 4);
        progressLp.leftMargin = dp(2); progressLp.rightMargin = dp(2);
        card.addView(updateProgressBar, progressLp);
        card.addView(text("מה חדש?", 16, true), lp(-1, -2, 0, 6));
        updateNotesView = muted("פרטי השינויים יוצגו לאחר בדיקת הגרסה העדכנית.", 14);
        updateNotesView.setPadding(dp(2), dp(4), dp(2), dp(10));
        card.addView(updateNotesView, new LinearLayout.LayoutParams(-1, -2));
        updateCheckButton = btn("🔄 בדוק אם יש עדכון", PRIMARY);
        updateCheckButton.setOnClickListener(v -> checkForAppUpdate());
        card.addView(updateCheckButton, lp(-1, dp(46), 0, 6));
        updateDownloadButton = btn("⬇️ הורדה ועדכון מיידים", TONAL);
        updateDownloadButton.setEnabled(false);
        updateDownloadButton.setOnClickListener(v -> downloadLatestUpdate());
        card.addView(updateDownloadButton, lp(-1, dp(48), 0, 6));
        updateInstallButton = btn("📦 התקנה ידנית", TONAL);
        updateInstallButton.setEnabled(false);
        updateInstallButton.setVisibility(View.VISIBLE);
        updateInstallButton.setOnClickListener(v -> installDownloadedUpdate());
        card.addView(updateInstallButton, lp(-1, dp(46), 0, 6));
        card.addView(muted("ההתקנה נפתחת דרך מערכת Android ועשויה לדרוש אישור. ההורדה נבדקת מול חתימת SHA-256 שפורסמה עם הגרסה.", 12), lp(-1, -2, 0, 4));
        root.addView(card, lp(-1, -2, 0, 10));

        updateLatestCode = prefs.getInt("update_latest_code", 0);
        updateLatestName = prefs.getString("update_latest_name", "");
        updateLatestSha256 = prefs.getString("update_latest_sha256", "");
        updateReleaseNotes = prefs.getString("update_release_notes", "");
        if (updateLatestCode > 0 && updateLatestCode != installedVersionCode()) {
            updateStatusView.setText("תוצאות הבדיקה האחרונה: נמצאה גרסה " + updateLatestName + " (" + updateLatestCode + "). אפשר לבדוק שוב לפני ההורדה.");
            updateStatusView.setTextColor(ORANGE_DARK);
            updateNotesView.setText(updateReleaseNotes.isEmpty() ? "לא צורף פירוט שינויים לגרסה." : updateReleaseNotes);
            updateDownloadButton.setEnabled(true);
            styleActionButton(updateDownloadButton, ORANGE, ON_ORANGE, 0);
        }
        File savedUpdateFile = savedVerifiedUpdateFile();
        boolean legacyDownloadAvailable = prefs.getLong("update_download_id", -1L) > 0;
        if (updateDownloadInProgress) {
            updateDownloadButton.setEnabled(false);
            updateStatusView.setText("ההורדה מתבצעת ברקע באופן פרטי. אפשר לעקוב אחר ההתקדמות כאן.");
            updateStatusView.setTextColor(ORANGE_DARK);
            if (updateProgressBar != null) { updateProgressBar.setVisibility(View.VISIBLE); updateProgressBar.setIndeterminate(true); }
        }
        if (prefs.getBoolean("update_download_verified", false)
                && prefs.getInt("update_download_verified_code", 0) == updateLatestCode
                && updateLatestCode != installedVersionCode()
                && (savedUpdateFile != null || legacyDownloadAvailable)) {
            updateStatusView.setText("ההורדה הושלמה והקובץ אומת. אפשר להתקין.");
            updateInstallButton.setVisibility(View.VISIBLE);
            updateInstallButton.setEnabled(true);
        } else if (prefs.getBoolean("update_download_verified", false) && !legacyDownloadAvailable) {
            prefs.edit().putBoolean("update_download_verified", false).apply();
        }
        // Always refresh GitHub metadata as soon as this settings screen is opened.
        checkForAppUpdate();
    }

    private JSONObject fetchUpdateManifest() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(UPDATE_MANIFEST_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new java.io.IOException("GitHub החזיר HTTP " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0, count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 131072) throw new java.io.IOException("קובץ מידע הגרסה גדול מדי");
                    output.write(buffer, 0, count);
                }
                JSONObject json = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
                int code = json.optInt("versionCode", 0);
                String name = json.optString("versionName", "").trim();
                String apkUrl = json.optString("apkUrl", "").trim();
                String sha = json.optString("sha256", "").trim().toLowerCase(Locale.US);
                if (code <= 0 || name.isEmpty()) throw new java.io.IOException("מידע הגרסה חסר או לא תקין");
                if (!UPDATE_APK_URL.equals(apkUrl)) throw new java.io.IOException("כתובת קובץ העדכון אינה מוכרת");
                if (!sha.matches("[0-9a-f]{64}")) throw new java.io.IOException("חסרה חתימת SHA-256 תקינה לקובץ העדכון");
                json.put("versionCode", code);
                json.put("versionName", name);
                json.put("sha256", sha);
                return json;
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void checkForAppUpdate() {
        if (updateCheckButton != null) updateCheckButton.setEnabled(false);
        if (updateStatusView != null) {
            updateStatusView.setText("בודק מול GitHub…");
            updateStatusView.setTextColor(MUTED);
            updateStatusView.setTextSize(15);
            updateStatusView.setBackgroundColor(Color.TRANSPARENT);
            updateStatusView.setPadding(dp(2), dp(10), dp(2), dp(8));
        }
        executor.execute(() -> {
            try {
                JSONObject manifest = fetchUpdateManifest();
                int code = manifest.getInt("versionCode");
                String name = manifest.getString("versionName");
                String sha = manifest.getString("sha256");
                String notes = manifest.optString("releaseNotes", "").trim();
                int installed = installedVersionCode();
                runOnUiThread(() -> {
                    if (screenKind != 7 || updateStatusView == null) { autoDownloadAfterCheck = false; return; }
                    updateLatestCode = code;
                    updateLatestName = name;
                    updateLatestSha256 = sha;
                    updateReleaseNotes = notes;
                    prefs.edit().putInt("update_latest_code", code)
                            .putString("update_latest_name", name)
                            .putString("update_latest_sha256", sha)
                            .putString("update_release_notes", notes).apply();
                    if (code > installed) {
                        updateStatusView.setText("🎉 נמצאה גרסה חדשה!\nגרסה " + name + " (" + code + ") זמינה לעדכון");
                        updateStatusView.setTextSize(19);
                        updateStatusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        updateStatusView.setTextColor(GREEN);
                        updateStatusView.setBackground(ripple(shape(AppPalette.WHATS_NEW_BACKGROUND, 14, AppPalette.WHATS_NEW_BORDER), 14));
                        updateStatusView.setPadding(dp(12), dp(12), dp(12), dp(12));
                        updateStatusView.setClickable(true);
                        updateStatusView.setContentDescription("נמצאה גרסה חדשה. לחיצה תתחיל הורדה מאובטחת והתקנה.");
                        updateStatusView.setOnClickListener(v -> downloadLatestUpdate());
                        updateNotesView.setText(notes.isEmpty() ? "לא צורף פירוט שינויים לגרסה." : notes);
                        updateDownloadButton.setEnabled(true);
                        styleActionButton(updateDownloadButton, ORANGE, ON_ORANGE, 0);
                        updateInstallButton.setVisibility(View.VISIBLE);
                        updateInstallButton.setEnabled(false);
                        if (autoDownloadAfterCheck) {
                            autoDownloadAfterCheck = false;
                            downloadLatestUpdate();
                        }
                    } else if (code == installed) {
                        autoDownloadAfterCheck = false;
                        updateStatusView.setText("תוצאות הבדיקה האחרונה: האפליקציה מעודכנת. מותקנת הגרסה האחרונה: " + name + " (" + code + ").");
                        updateStatusView.setTextColor(GREEN);
                        updateNotesView.setText(notes.isEmpty() ? "אין פירוט שינויים נוסף." : notes);
                        updateDownloadButton.setEnabled(false);
                        styleActionButton(updateDownloadButton, ORANGE_LIGHT, ORANGE_DARK, 0);
                        updateInstallButton.setVisibility(View.VISIBLE);
                        updateInstallButton.setEnabled(false);
                    } else {
                        boolean startOlderVersionDownload = autoDownloadAfterCheck;
                        autoDownloadAfterCheck = false;
                        updateStatusView.setText("תוצאות הבדיקה האחרונה: הגרסה שב־GitHub ישנה יותר מהגרסה המותקנת. אפשר להוריד אותה, אך Android עשוי לחסום התקנה לאחור.");
                        updateStatusView.setTextColor(ORANGE_DARK);
                        updateNotesView.setText(notes.isEmpty() ? "לא צורף פירוט שינויים לגרסה." : notes);
                        updateDownloadButton.setEnabled(true);
                        styleActionButton(updateDownloadButton, ORANGE, ON_ORANGE, 0);
                        updateInstallButton.setVisibility(View.VISIBLE);
                        updateInstallButton.setEnabled(false);
                        if (startOlderVersionDownload) downloadLatestUpdate();
                    }
                    if (updateCheckButton != null) updateCheckButton.setEnabled(true);
                });
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
                final String shown = message;
                runOnUiThread(() -> {
                    if (screenKind != 7 || updateStatusView == null) return;
                    updateStatusView.setText("תוצאות הבדיקה האחרונה: בדיקת העדכונים נכשלה: " + shown + ". אפשר לנסות שוב מאוחר יותר.");
                    updateStatusView.setTextColor(ORANGE_DARK);
                    if (updateCheckButton != null) updateCheckButton.setEnabled(true);
                    boolean cachedUpdateAvailable = updateLatestCode > 0
                            && updateLatestCode != installedVersionCode()
                            && updateLatestSha256.matches("[0-9a-f]{64}");
                    updateDownloadButton.setEnabled(cachedUpdateAvailable);
                    styleActionButton(updateDownloadButton,
                            cachedUpdateAvailable ? ORANGE : ORANGE_LIGHT,
                            cachedUpdateAvailable ? ON_ORANGE : ORANGE_DARK, 0);
                });
            }
        });
    }

    private File updateApkFile(int versionCode) {
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) directory = new File(getFilesDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) return null;
        return new File(directory, "mishnat-yosef-update-" + versionCode + ".apk");
    }

    private void pruneOldUpdateApks(File keep) {
        File[] directories = new File[] {
                getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                new File(getFilesDir(), "updates")
        };
        String keepPath = keep == null ? "" : keep.getAbsolutePath();
        for (File directory : directories) {
            if (directory == null || !directory.isDirectory()) continue;
            File[] files = directory.listFiles();
            if (files == null) continue;
            for (File file : files) {
                String name = file.getName();
                if (!name.startsWith("mishnat-yosef-update-")) continue;
                if (!(name.endsWith(".apk") || name.endsWith(".apk.part"))) continue;
                if (file.getAbsolutePath().equals(keepPath)) continue;
                try { file.delete(); } catch (Exception ignored) { }
            }
        }
    }

    private File savedVerifiedUpdateFile() {
        String path = prefs.getString("update_download_file", "");
        if (path == null || path.trim().isEmpty()) return null;
        File file = new File(path);
        return file.isFile() && file.canRead() && file.length() > 0 ? file : null;
    }

    private void downloadLatestUpdate() {
        if (updateLatestCode <= 0 || !updateLatestSha256.matches("[0-9a-f]{64}")) {
            toast("צריך לבצע בדיקת עדכונים מוצלחת לפני ההורדה.", true);
            return;
        }
        if (updateDownloadInProgress) {
            toast("ההורדה כבר מתבצעת. אפשר לעקוב אחר ההתקדמות בעמוד העדכונים.", false);
            return;
        }
        final int targetCode = updateLatestCode;
        final String targetName = updateLatestName;
        final String expectedSha = updateLatestSha256;
        final File target = updateApkFile(targetCode);
        if (target == null) {
            toast("לא ניתן ליצור תיקיית הורדה פרטית לאפליקציה.", true);
            return;
        }
        final File partial = new File(target.getAbsolutePath() + ".part");
        updateDownloadInProgress = true;
        if (updateStatusView != null) updateStatusView.setClickable(false);
        updateDownloadButton.setEnabled(false);
        updateInstallButton.setEnabled(false);
        if (updateProgressBar != null) {
            updateProgressBar.setVisibility(View.VISIBLE);
            updateProgressBar.setIndeterminate(true);
            updateProgressBar.setProgress(0);
        }
        updateStatusView.setText("מתחיל הורדה מאובטחת…");
        prefs.edit().putBoolean("update_download_verified", false)
                .remove("update_download_verified_code").remove("update_download_file").apply();

        executor.execute(() -> {
            HttpURLConnection connection = null;
            boolean verified = false;
            String failure = "";
            try {
                if (partial.exists() && !partial.delete())
                    throw new java.io.IOException("לא ניתן לנקות קובץ הורדה חלקי קודם");
                // Give every published APK a unique URL so GitHub/CDN caches cannot serve bytes from an older build.
                // The manifest keeps the canonical URL; the query parameters only bust intermediary caches.
                String downloadUrl = UPDATE_APK_URL + "?v=" + targetCode + "&sha256=" + expectedSha;
                connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300)
                    throw new java.io.IOException("שרת ההורדה החזיר שגיאה " + status);
                long total = connection.getContentLength();
                long downloaded = 0L;
                long lastUiUpdate = 0L;
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        downloaded += count;
                        if (downloaded > 200L * 1024L * 1024L)
                            throw new java.io.IOException("קובץ העדכון גדול מהגודל המרבי המותר");
                        output.write(buffer, 0, count);
                        long now = System.currentTimeMillis();
                        if (now - lastUiUpdate >= 350L) {
                            lastUiUpdate = now;
                            final long currentBytes = downloaded;
                            final long totalBytes = total;
                            runOnUiThread(() -> {
                                if (screenKind == 7 && updateStatusView != null) {
                                    String progress = totalBytes > 0
                                            ? " (" + Math.min(100, currentBytes * 100L / totalBytes) + "%)"
                                            : " (" + formatBytes(currentBytes) + ")";
                                    updateStatusView.setText("מוריד עדכון באופן פרטי" + progress + "…");
                                    updateStatusView.setTextColor(ORANGE_DARK);
                                    if (updateProgressBar != null) {
                                        updateProgressBar.setVisibility(View.VISIBLE);
                                        if (totalBytes > 0) {
                                            updateProgressBar.setIndeterminate(false);
                                            updateProgressBar.setProgress((int) Math.min(100, currentBytes * 100L / totalBytes));
                                        } else updateProgressBar.setIndeterminate(true);
                                    }
                                }
                            });
                        }
                    }
                    output.flush();
                }
                if (partial.length() <= 0) throw new java.io.IOException("שרת ההורדה החזיר קובץ ריק");
                if (target.exists() && !target.delete())
                    throw new java.io.IOException("לא ניתן להחליף קובץ עדכון קודם");
                if (!partial.renameTo(target))
                    throw new java.io.IOException("לא ניתן לשמור את קובץ העדכון שהורד");
                try (InputStream input = new java.io.FileInputStream(target)) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b & 0xff));
                    verified = expectedSha.equals(hex.toString());
                }
                if (!verified) {
                    target.delete();
                    throw new java.io.IOException("חתימת הקובץ אינה תואמת לגרסה שפורסמה");
                }
                pruneOldUpdateApks(target);
            } catch (Exception error) {
                failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
                if (partial.exists()) partial.delete();
            }
            final boolean success = verified;
            final String problem = failure;
            runOnUiThread(() -> {
                updateDownloadInProgress = false;
                if (success) {
                    prefs.edit().putBoolean("update_download_verified", true)
                            .putInt("update_download_verified_code", targetCode)
                            .putString("update_download_file", target.getAbsolutePath())
                            .putBoolean("update_auto_install_pending", true).apply();
                    if (screenKind == 7 && updateStatusView != null) {
                        updateStatusView.setText("ההורדה הושלמה והקובץ אומת בהצלחה. פותח את מתקין Android…");
                        updateStatusView.setTextColor(GREEN);
                        if (updateProgressBar != null) { updateProgressBar.setVisibility(View.VISIBLE); updateProgressBar.setIndeterminate(false); updateProgressBar.setProgress(100); }
                        updateInstallButton.setVisibility(View.VISIBLE);
                        updateInstallButton.setEnabled(true);
                        updateDownloadButton.setEnabled(true);
                    }
                    installDownloadedUpdate();
                } else {
                    prefs.edit().putBoolean("update_download_verified", false)
                            .remove("update_download_verified_code").remove("update_download_file")
                            .putBoolean("update_auto_install_pending", false).apply();
                    if (screenKind == 7 && updateStatusView != null) {
                        updateStatusView.setText("ההורדה לא הושלמה או שהקובץ לא עבר אימות: " + problem + ". אפשר לנסות שוב.");
                        updateStatusView.setTextColor(ORANGE_DARK);
                        updateStatusView.setClickable(updateLatestCode > installedVersionCode()
                                && updateLatestSha256.matches("[0-9a-f]{64}"));
                        if (updateProgressBar != null) updateProgressBar.setVisibility(View.GONE);
                        updateDownloadButton.setEnabled(updateLatestCode > 0 && updateLatestSha256.matches("[0-9a-f]{64}"));
                    }
                    toast("הורדת העדכון נכשלה: " + problem, true);
                }
            });
        });
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void installDownloadedUpdate() {
        int verifiedCode = prefs.getInt("update_download_verified_code", 0);
        File verifiedFile = savedVerifiedUpdateFile();
        if (!prefs.getBoolean("update_download_verified", false)
                || verifiedCode != updateLatestCode
                || updateLatestCode <= 0) {
            toast("אין קובץ עדכון מאומת להתקנה. בדוק עדכונים והורד מחדש.", true);
            return;
        }
        Uri uri = null;
        if (verifiedFile != null) {
            uri = Uri.parse("content://org.mishnatyosef.mobile.jsonreports/" + Uri.encode(verifiedFile.getName()));
        } else {
            // Compatibility with an update download started by an older app version.
            long oldId = prefs.getLong("update_download_id", -1L);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (oldId > 0 && manager != null) uri = manager.getUriForDownloadedFile(oldId);
        }
        if (uri == null) {
            prefs.edit().putBoolean("update_download_verified", false).apply();
            toast("קובץ העדכון לא נמצא. צריך להוריד אותו מחדש.", true);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            prefs.edit().putBoolean("update_auto_install_pending", true).apply();
            if (updateStatusView != null) updateStatusView.setText("ההורדה אומתה. צריך לאפשר התקנת אפליקציות ממקור זה ב־Android; לאחר החזרה לאפליקציה ההתקנה תיפתח אוטומטית. אפשר גם להשתמש בכפתור ההתקנה הידנית.");
            if (updateInstallButton != null) updateInstallButton.setVisibility(View.VISIBLE);
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception error) {
                toast("פתח את הגדרות Android ואפשר התקנת אפליקציות ממקור זה.", true);
            }
            return;
        }
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            prefs.edit().putBoolean("update_auto_install_pending", false)
                    .putBoolean("in_app_update_success_pending", true)
                    .putInt("in_app_update_old_code", installedVersionCode())
                    .putString("in_app_update_old_name", installedVersionName())
                    .putInt("in_app_update_target_code", updateLatestCode)
                    .putString("in_app_update_target_name", updateLatestName)
                    .putString("in_app_update_notes", prefs.getString("update_release_notes", updateReleaseNotes))
                    .apply();
            startActivity(install);
        } catch (Exception error) {
            prefs.edit().putBoolean("update_auto_install_pending", false).apply();
            clearPendingInAppUpdateSuccess();
            if (updateStatusView != null) updateStatusView.setText("הקובץ אומת, אך מתקין Android לא נפתח. אפשר ללחוץ על התקנה ידנית ולנסות שוב.");
            if (updateInstallButton != null) updateInstallButton.setVisibility(View.VISIBLE);
            toast("לא ניתן לפתוח את מתקין Android. אפשר לנסות שוב בכפתור ההתקנה הידנית.", true);
        }
    }

    private TextView sectionTitle(String title) {
        TextView v = text(title, 16, true);
        v.setTextColor(ORANGE_DARK);
        return v;
    }

    private View switchRow(String title, String subtitle, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15, true));
        if (subtitle != null && !subtitle.isEmpty()) texts.addView(muted(subtitle, 12));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        tintSwitch(sw);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);
        return row;
    }

    private TextView pickChip(String label) {
        TextView c = smallChip(label, TONAL);
        c.setMinHeight(dp(38));
        c.setTextSize(13);
        return c;
    }

    private void styleChip(TextView chip, boolean on) {
        chip.setBackground(ripple(shape(on ? ORANGE : ORANGE_LIGHT, ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
        chip.setTextColor(on ? ON_ORANGE : ORANGE_DARK);
    }

    private boolean hasAnyNotificationFeatureEnabled() {
        return prefs.getBoolean("sale_monitor_enabled", true)
                || prefs.getBoolean("closing_reminder_enabled", false)
                || prefs.getBoolean("stock_global_notify_out", false)
                || prefs.getBoolean("stock_global_notify_return", false)
                || prefs.getBoolean("stock_notify_out", true)
                || prefs.getBoolean("stock_notify_return", true);
    }

    private String persistentNotificationContent() {
        java.util.ArrayList<String> enabled = new java.util.ArrayList<>();
        if (prefs.getBoolean("sale_monitor_enabled", true)) enabled.add("התראות על פתיחת מכירה");
        if (prefs.getBoolean("closing_reminder_enabled", false)) enabled.add("תזכורת לפני סגירת ההזמנה");
        boolean out = prefs.getBoolean("stock_global_notify_out", false) || prefs.getBoolean("stock_notify_out", true);
        boolean back = prefs.getBoolean("stock_global_notify_return", false) || prefs.getBoolean("stock_notify_return", true);
        if (out && back) enabled.add("עדכונים על מוצרים שאזלו וחזרו למלאי");
        else if (out) enabled.add("עדכונים על מוצרים שאזלו מהמלאי");
        else if (back) enabled.add("עדכונים על מוצרים שחזרו למלאי");
        return enabled.isEmpty() ? "אין כרגע סוגי התראות פעילים" : "פעילה לצורך קבלת " + android.text.TextUtils.join(", ", enabled) + " וכו׳";
    }

    private void refreshPersistentNotification() {
        Intent service = new Intent(this, PersistentNotificationService.class);
        if (prefs.getBoolean("persistent_notifications_enabled", false) && hasAnyNotificationFeatureEnabled()) {
            try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service); }
            catch (Exception e) { toast("לא ניתן להפעיל את ההתראה הקבועה. בדקו את הרשאות ההתראות.", true); }
        } else stopService(service);
    }

    private void maybePromptPersistentNotification() {
        if (!hasAnyNotificationFeatureEnabled() || prefs.getBoolean("persistent_notifications_enabled", false)) {
            refreshPersistentNotification();
            return;
        }
        appDialog().setTitle("🔔 כדאי להפעיל התראה קבועה")
                .setMessage("כדי לשפר את הסיכוי לקבלת התראות גם כשהאפליקציה ברקע, מומלץ להפעיל את ההתראה הקבועה «משנת יוסף». היא מציגה אילו התראות פעילות, אך אינה מבטיחה שמערכת Android לא תגביל את האפליקציה.")
                .setNegativeButton("אחר כך", null)
                .setPositiveButton("להגדרות ההתראה הקבועה", (d, w) -> showNotificationSettings()).show();
    }

    private View persistentNotificationCard() {
        LinearLayout card = card(14);
        card.addView(sectionTitle("📌 התראה קבועה — משנת יוסף"));
        card.addView(muted("התראה מתמשכת שמציגה אילו עדכונים פעילים. היא מסייעת להשאיר את פעילות ההתראות גלויה, אך אינה יכולה להבטיח ש־Android או הגדרות חיסכון בסוללה לא יעצרו תהליכים.", 13), lp(-1, -2, 0, 8));
        boolean enabled = prefs.getBoolean("persistent_notifications_enabled", false);
        card.addView(switchRow("הפעלת התראה קבועה", "הצגת התראה מתמשכת עם סוגי ההתראות הפעילים", enabled, (button, checked) -> {
            prefs.edit().putBoolean("persistent_notifications_enabled", checked).apply();
            if (checked) requestMonitoringPermissions(false);
            refreshPersistentNotification();
            showNotificationSettings();
            toast(checked ? "🔔 ההתראה הקבועה הופעלה" : "ההתראה הקבועה כובתה", false);
        }));
        card.addView(muted(enabled ? persistentNotificationContent() : "כבויה — הפעילו כדי להציג התראה מתמשכת.", 12), lp(-1, -2, 0, 6));
        return card;
    }

    private View notificationBehaviorCard() {
        LinearLayout card = card(14);
        card.addView(sectionTitle("🔔 צליל ורטט לכל ההתראות"));
        card.addView(muted("ההגדרות האלה אחידות לכל סוגי ההתראות באפליקציה: פתיחת מכירה, תזכורת סגירה ועדכוני מלאי.", 13), lp(-1, -2, 0, 8));
        card.addView(switchRow("צליל", "השמעת צליל בכל התראה", prefs.getBoolean("notify_sound", true), (b, checked) -> {
            prefs.edit().putBoolean("notify_sound", checked).apply();
            toast(checked ? "🔊 צליל הופעל לכל ההתראות" : "🔕 הצליל כובה לכל ההתראות", false);
        }));
        card.addView(switchRow("רטט", "רטט בכל התראה", prefs.getBoolean("notify_vibrate", true), (b, checked) -> {
            prefs.edit().putBoolean("notify_vibrate", checked).apply();
            toast(checked ? "📳 רטט הופעל לכל ההתראות" : "רטט כובה לכל ההתראות", false);
        }));
        return card;
    }

    private View permissionsCard() {
        boolean notifOk = true, exactOk = true;
        if (Build.VERSION.SDK_INT >= 33) notifOk = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && !nm.areNotificationsEnabled()) notifOk = false;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            exactOk = am == null || am.canScheduleExactAlarms();
        }
        LinearLayout card = card(14);
        if (notifOk && exactOk) {
            TextView ok = text("✔ ההרשאות תקינות, ההתראות יגיעו בזמן", 14, true);
            ok.setTextColor(GREEN);
            card.addView(ok);
            card.setBackground(shape(GREEN_LIGHT, 14, 0));
            return card;
        }
        card.setBackground(shape(RED_LIGHT, 14, 0));
        TextView title = text("⚠️ חסרות הרשאות להתראות", 15, true);
        title.setTextColor(RED);
        card.addView(title);
        if (!notifOk) card.addView(muted("• ההתראות חסומות לאפליקציה", 13));
        if (!exactOk) card.addView(muted("• אין הרשאה להתראות בשעה מדויקת (ההתראה עלולה להתאחר)", 13));
        final boolean fixNotifications = !notifOk;
        TextView fix = btn("תיקון הרשאות", PRIMARY);
        fix.setOnClickListener(v -> {
            try {
                if (fixNotifications) {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(i);
                } else {
                    requestMonitoringPermissions(true);
                }
            } catch (Exception e) {
                toast("לא ניתן לפתוח את ההגדרות", true);
            }
        });
        card.addView(fix, lp(-1, dp(44), 8, 0));
        return card;
    }

    private View windowRow(final String label, final String dayKey, int dayDefault, final String timeKey, final String timeDefault, final Runnable onChange) {
        final int defDay = dayDefault;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 14, true), new LinearLayout.LayoutParams(0, -2, 1));
        final TextView dayChip = pickChip(dayName(prefs.getInt(dayKey, defDay)));
        final TextView timeChip = pickChip(prefs.getString(timeKey, timeDefault));
        dayChip.setOnClickListener(v -> {
            final String[] names = new String[7];
            for (int i = 0; i < 7; i++) names[i] = "יום " + HEB_DAYS[i];
            appDialog().setTitle(label)
                    .setSingleChoiceItems(names, indexOf(CAL_DAYS, prefs.getInt(dayKey, defDay)), (d, which) -> {
                        prefs.edit().putInt(dayKey, CAL_DAYS[which]).apply();
                        dayChip.setText(names[which]);
                        d.dismiss();
                        onChange.run();
                    }).setNegativeButton("ביטול", null).show();
        });
        timeChip.setOnClickListener(v -> {
            String[] parts = prefs.getString(timeKey, timeDefault).split(":");
            int h = 0, m = 0;
            try { h = Integer.parseInt(parts[0]); m = Integer.parseInt(parts[1]); } catch (Exception ignored) { }
            new AppTimePickerDialog( (picker, hh, mm) -> {
                String t = String.format(Locale.US, "%02d:%02d", hh, mm);
                prefs.edit().putString(timeKey, t).apply();
                timeChip.setText(t);
                onChange.run();
            }, h, m, true).show();
        });
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(-2, -2);
        dp1.setMarginEnd(dp(6));
        row.addView(dayChip, dp1);
        row.addView(timeChip);
        return row;
    }

    private void applyMonitorSettings() {
        SaleMonitor.stopMonitoring(this);
        if (prefs.getBoolean("sale_monitor_enabled", true)) {
            requestMonitoringPermissions(false);
            SaleMonitor.scheduleNextStart(this);
        }
    }

    private View newSaleCard() {
        LinearLayout card = card(14);
        card.addView(sectionTitle("🛒 התראה על מכירה חדשה"));
        final LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(prefs.getBoolean("sale_monitor_enabled", true) ? View.VISIBLE : View.GONE);
        final TextView summaryView = muted(monitorSummary(), 12);
        final Runnable onChange = () -> { summaryView.setText(monitorSummary()); applyMonitorSettings(); toast("✔ הגדרות ההתראה נשמרו", false); };

        card.addView(switchRow("הפעלה", "בדיקה אוטומטית של מכירות פתוחות בחלון הזמן שתגדירו", prefs.getBoolean("sale_monitor_enabled", true), (b, checked) -> {
            prefs.edit().putBoolean("sale_monitor_enabled", checked).apply();
            details.setVisibility(checked ? View.VISIBLE : View.GONE);
            onChange.run();
            if (checked) maybePromptPersistentNotification(); else refreshPersistentNotification();
            toast(checked ? "🔔 התראה על מכירה חדשה הופעלה" : "🔕 התראה על מכירה חדשה כובתה", false);
        }));

        details.addView(windowRow("מתחילה", "monitor_start_day", Calendar.THURSDAY, "monitor_start_time", "19:00", onChange), lp(-1, -2, 4, 4));
        details.addView(windowRow("מסתיימת", "monitor_end_day", Calendar.SUNDAY, "monitor_end_time", "10:00", onChange), lp(-1, -2, 4, 8));

        details.addView(text("בדיקה כל כמה דקות", 14, true));
        final int[] intervals = {1, 2, 3, 5, 10, 15, 30, 60};
        final TextView[] chips = new TextView[intervals.length];
        LinearLayout chipRow = new LinearLayout(this);
        final Runnable styleIntervals = () -> {
            int cur = prefs.getInt("monitor_interval_minutes", 5);
            for (int i = 0; i < chips.length; i++) styleChip(chips[i], intervals[i] == cur);
        };
        for (int i = 0; i < intervals.length; i++) {
            final int value = intervals[i];
            chips[i] = pickChip(String.valueOf(value));
            chips[i].setOnClickListener(v -> {
                prefs.edit().putInt("monitor_interval_minutes", value).apply();
                styleIntervals.run();
                onChange.run();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(48), dp(38));
            if (i > 0) cp.setMarginStart(dp(6));
            chipRow.addView(chips[i], cp);
        }
        styleIntervals.run();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chipRow);
        details.addView(hs, lp(-1, -2, 4, 8));

        summaryView.setPadding(dp(2), dp(6), dp(2), dp(6));
        details.addView(summaryView);
        card.addView(details);
        return card;
    }

    private View closingCard() {
        LinearLayout card = card(14);
        card.addView(sectionTitle("⏰ תזכורת לפני סגירת מכירה"));
        final LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(prefs.getBoolean("closing_reminder_enabled", false) ? View.VISIBLE : View.GONE);
        final LinearLayout previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);

        card.addView(switchRow("הפעלה", "התראה כמה זמן לפני שהמכירה נסגרת", prefs.getBoolean("closing_reminder_enabled", false), (b, checked) -> {
            prefs.edit().putBoolean("closing_reminder_enabled", checked).apply();
            details.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked) requestMonitoringPermissions(false);
            SaleMonitor.scheduleClosingFromStore(this);
            renderClosingPreview(previewBox);
            if (checked) maybePromptPersistentNotification(); else refreshPersistentNotification();
            toast(checked ? "⏰ תזכורת לפני סגירה הופעלה" : "תזכורת לפני סגירה כובתה", false);
        }));

        details.addView(text("כמה זמן לפני הסגירה", 14, true));
        final int[] minutes = {Math.max(30, prefs.getInt("closing_reminder_minutes", 120))};
        final Stepper st = stepper(42);
        st.qty.setLayoutParams(new LinearLayout.LayoutParams(dp(120), dp(42)));
        st.qty.setTextSize(15);
        final int[] quick = {60, 120, 180, 360, 480, 600, 720, 1440};
        final TextView[] chips = new TextView[quick.length];
        final Runnable[] commit = new Runnable[1];
        commit[0] = () -> {
            prefs.edit().putInt("closing_reminder_minutes", minutes[0]).apply();
            st.qty.setText(hoursText(minutes[0]));
            for (int i = 0; i < chips.length; i++) styleChip(chips[i], quick[i] == minutes[0]);
            SaleMonitor.scheduleClosingFromStore(this);
            renderClosingPreview(previewBox);
        };
        st.plus.setOnClickListener(v -> { minutes[0] = Math.min(4320, minutes[0] + 30); commit[0].run(); toast("✔ תזכורת: " + hoursText(minutes[0]) + " לפני הסגירה", false); });
        st.minus.setOnClickListener(v -> { minutes[0] = Math.max(30, minutes[0] - 30); commit[0].run(); toast("✔ תזכורת: " + hoursText(minutes[0]) + " לפני הסגירה", false); });
        LinearLayout stepRow = new LinearLayout(this);
        stepRow.setGravity(Gravity.CENTER);
        stepRow.addView(st.view);
        details.addView(stepRow, lp(-1, -2, 6, 8));

        LinearLayout chipRow = new LinearLayout(this);
        for (int i = 0; i < quick.length; i++) {
            final int value = quick[i];
            chips[i] = pickChip(hoursText(value).replace(" שעות", "ש׳").replace("שעה", "1ש׳"));
            chips[i].setOnClickListener(v -> { minutes[0] = value; commit[0].run(); toast("✔ תזכורת: " + hoursText(minutes[0]) + " לפני הסגירה", false); });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(38));
            if (i > 0) cp.setMarginStart(dp(6));
            chipRow.addView(chips[i], cp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chipRow);
        details.addView(hs, lp(-1, -2, 0, 10));

        details.addView(previewBox, lp(-1, -2, 0, 8));
        card.addView(details);

        commit[0].run();
        refreshReminderSales(previewBox);
        return card;
    }

    private void renderClosingPreview(LinearLayout box) {
        box.removeAllViews();
        int minutes = prefs.getInt("closing_reminder_minutes", 120);
        long now = System.currentTimeMillis();
        JSONArray store = SaleMonitor.closingStore(this);
        List<JSONObject> entries = new ArrayList<>();
        for (int i = 0; i < store.length(); i++) {
            JSONObject e = store.optJSONObject(i);
            if (e != null && e.optLong("end", 0L) > now) entries.add(e);
        }
        Collections.sort(entries, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) { return Long.compare(a.optLong("end"), b.optLong("end")); }
        });
        box.addView(text("מתי תגיע ההתראה", 14, true));
        if (entries.isEmpty()) {
            box.addView(muted("אין כרגע מכירה פתוחה עם זמן סגירה. התזכורת תיקבע אוטומטית כשתיפתח מכירה.", 12));
            return;
        }
        for (JSONObject e : entries) {
            long end = e.optLong("end");
            long trigger = end - minutes * 60000L;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12), dp(8), dp(12), dp(8));
            item.setBackground(shape(ORANGE_LIGHT, 12, 0));
            item.addView(text(e.optString("name", "מכירה"), 14, true));
            item.addView(muted("נסגרת: " + fmtDay(new Date(end), true), 12));
            boolean future = trigger > now;
            TextView when = text(future ? "🔔 ההתראה: " + fmtDay(new Date(trigger), true) + " (בעוד " + humanDuration(trigger - now) + ")"
                    : "⏱ הזמן שנבחר כבר עבר, לא תישלח התראה למכירה זו", 13, true);
            when.setTextColor(future ? GREEN : RED);
            item.addView(when);
            box.addView(item, lp(-1, -2, 6, 0));
        }
    }

    /** Refreshes the open sales so the preview and the alarms use current closing times. */
    private void refreshReminderSales(final LinearLayout box) {
        final int t = screenToken;
        executor.execute(() -> {
            try {
                HttpResult r = api("GET", "/api/profile/open_sales", null);
                if (r.code >= 200 && r.code < 300) SaleMonitor.scheduleClosingReminders(getApplicationContext(), arrayFrom(r.body));
                runOnUiThread(() -> { if (!stale(t)) renderClosingPreview(box); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (e instanceof AuthException) showLogin(); });
            }
        });
    }

    private int indexOf(int[] values, int target) { for (int i = 0; i < values.length; i++) if (values[i] == target) return i; return 0; }
    private int indexOf(Integer[] values, int target) { for (int i = 0; i < values.length; i++) if (values[i] == target) return i; return 3; }
    private boolean validTime(String value) { if (value == null || !value.matches("[0-2][0-9]:[0-5][0-9]")) return false; return Integer.parseInt(value.substring(0, 2)) < 24; }

    // ======================================================================== network + auth

    private boolean hasToken() {
        return !prefs.getString("token", "").isEmpty();
    }

    /** One place for all authenticated calls: retries GETs on network/5xx, proactive refresh, new token from headers. */
    private HttpResult api(String method, String path, JSONObject body) throws Exception {
        boolean idempotent = "GET".equals(method);
        int attempts = idempotent ? 3 : 1;
        HttpResult r = null;
        for (int i = 0; i < attempts; i++) {
            try {
                r = apiOnce(method, path, body);
            } catch (AuthException ae) {
                throw ae;
            } catch (java.io.IOException io) {
                if (i == attempts - 1) throw io;
                Thread.sleep(700L * (i + 1));
                continue;
            }
            if (idempotent && (r.code == 429 || r.code == 502 || r.code == 503 || r.code == 504) && i < attempts - 1) {
                Thread.sleep(900L * (i + 1));
                continue;
            }
            break;
        }
        return r;
    }

    private HttpResult apiOnce(String method, String path, JSONObject body) throws Exception {
        String token = prefs.getString("token", "");
        long expiry = prefs.getLong("expires_at", 0L);
        if (!token.isEmpty() && expiry > 0 && System.currentTimeMillis() > expiry - REFRESH_MARGIN_MS) {
            String refreshed = refreshToken(token);
            if (!refreshed.isEmpty()) token = refreshed;
        }
        HttpResult r = request(method, path, token, body);
        if (r.code == 401) {
            String refreshed = refreshToken(token);
            if (!refreshed.isEmpty()) r = request(method, path, refreshed, body);
            if (r.code == 401) {
                prefs.edit().remove("token").remove("expires_at").apply();
                throw new AuthException();
            }
        }
        if (r.code < 400 && r.newToken != null && !r.newToken.isEmpty() && !r.newToken.equals(prefs.getString("token", ""))) {
            saveToken(r.newToken, new JSONObject());
        }
        return r;
    }

    private void saveToken(String token, JSONObject data) {
        prefs.edit().putString("token", token).putLong("expires_at", parseExpiry(data, token)).apply();
    }

    private String refreshToken(String token) {
        if (refreshDead || token == null || token.isEmpty()) return "";
        try {
            JSONObject payload = new JSONObject();
            payload.put("remember", 1);
            HttpResult r = request("POST", "/api/refresh", token, payload);
            if (r.code != 200 && r.code != 401 && r.code != 404 && r.code != 405) r = request("POST", "/api/refresh", token, null);
            if (r.code == 404 || r.code == 405) { refreshDead = true; return ""; }
            if (r.code != 200) return "";
            JSONObject data = new JSONObject();
            String next = "";
            try { data = new JSONObject(r.body); next = pickToken(data); } catch (Exception ignored) { }
            if (next.isEmpty() && r.newToken != null) next = r.newToken;
            if (!next.isEmpty()) {
                saveToken(next, data);
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
        long t0 = System.currentTimeMillis();
        try {
            HttpResult r = requestRaw(method, path, token, body);
            long elapsed = System.currentTimeMillis() - t0;
            logApi(method, path, String.valueOf(r.code), elapsed, r.code >= 400 ? r.body : null);
            recordApiDiscovery(method, path, r.code, r.body, elapsed, null);
            return r;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            logApi(method, path, e.getClass().getSimpleName(), elapsed, e.getMessage());
            recordApiDiscovery(method, path, -1, "", elapsed, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            throw e;
        }
    }

    /*
     * Local API discovery: records only requests made to this app's configured API host.
     * It never stores request bodies, Authorization headers, passwords, or access tokens.
     * The small persistent index helps correlate a response with the screen/feature that
     * caused it, and keeps matches for the known pea item available across app restarts.
     */
    private static final String PREF_API_DISCOVERY = "api_discovery_endpoints_v1";
    private static final String PREF_API_MATCHES = "api_discovery_matches_v1";
    private static final String TARGET_PEA_ID = "39625438";

    private void recordApiDiscovery(String method, String path, int code, String responseBody,
                                    long elapsedMs, String error) {
        try {
            if (prefs == null || path == null || !path.startsWith("/api/")) return;
            String context = apiDiscoveryContext(path);
            String url = API + path;
            JSONArray endpoints;
            try { endpoints = new JSONArray(prefs.getString(PREF_API_DISCOVERY, "[]")); }
            catch (Exception ignored) { endpoints = new JSONArray(); }
            JSONObject found = null;
            for (int i = 0; i < endpoints.length(); i++) {
                JSONObject item = endpoints.optJSONObject(i);
                if (item != null && method.equals(item.optString("method"))
                        && path.equals(item.optString("path"))) { found = item; break; }
            }
            if (found == null) {
                found = new JSONObject();
                found.put("method", method);
                found.put("path", path);
                found.put("url", url);
                found.put("context", context);
                found.put("first_seen", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                endpoints.put(found);
            }
            found.put("last_seen", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            found.put("last_http", code);
            found.put("last_ms", elapsedMs);
            found.put("count", found.optInt("count", 0) + 1);
            if (error != null) found.put("last_error", error.length() > 180 ? error.substring(0, 180) : error);
            else found.remove("last_error");
            while (endpoints.length() > 250) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < endpoints.length(); i++) trimmed.put(endpoints.opt(i));
                endpoints = trimmed;
            }
            prefs.edit().putString(PREF_API_DISCOVERY, endpoints.toString()).apply();

            String body = responseBody == null ? "" : responseBody;
            String lower = body.toLowerCase(Locale.ROOT);
            boolean peaHit = body.contains(TARGET_PEA_ID) || lower.contains("אפונה")
                    || lower.contains("קייטי") || lower.contains("peas");
            if (code >= 200 && code < 300 && peaHit) {
                JSONArray matches;
                try { matches = new JSONArray(prefs.getString(PREF_API_MATCHES, "[]")); }
                catch (Exception ignored) { matches = new JSONArray(); }
                String excerpt;
                if (body.length() <= 14000) excerpt = body;
                else {
                    int at = body.indexOf(TARGET_PEA_ID);
                    if (at < 0) at = Math.max(lower.indexOf("אפונה"), 0);
                    int start = Math.max(0, at - 3500);
                    int end = Math.min(body.length(), at + 10500);
                    excerpt = body.substring(start, end);
                }
                JSONObject match = new JSONObject();
                match.put("url", url);
                match.put("path", path);
                match.put("context", context);
                match.put("http", code);
                match.put("matched_id", body.contains(TARGET_PEA_ID));
                match.put("matched_name", lower.contains("אפונה") || lower.contains("קייטי") || lower.contains("peas"));
                match.put("response_chars", body.length());
                match.put("captured_at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                match.put("response_excerpt_or_body", excerpt);
                JSONArray updated = new JSONArray();
                for (int i = 0; i < matches.length(); i++) {
                    JSONObject old = matches.optJSONObject(i);
                    if (old != null && !url.equals(old.optString("url"))) updated.put(old);
                }
                updated.put(match);
                while (updated.length() > 20) {
                    JSONArray trimmed = new JSONArray();
                    for (int i = 1; i < updated.length(); i++) trimmed.put(updated.opt(i));
                    updated = trimmed;
                }
                prefs.edit().putString(PREF_API_MATCHES, updated.toString()).apply();
            }
        } catch (Exception ignored) { }
    }

    private String apiDiscoveryContext(String path) {
        String screen = screenKind == 1 ? "מסך מוצרים" : screenKind == 2 ? "מסך סיכום הזמנה" : "מסך/רקע כללי";
        if (path.contains("get_items_for_sale")) return screen + " / טעינת מוצרי מכירה";
        if (path.contains("open_sales")) return screen + " / מכירות פתוחות";
        if (path.contains("orders")) return screen + " / הזמנות";
        if (path.contains("transactions")) return screen + " / עסקאות והיסטוריה";
        if (path.contains("favourites") || path.contains("favorites")) return screen + " / מועדפים";
        if (path.contains("payment")) return screen + " / אמצעי תשלום";
        if (path.contains("search")) return screen + " / חיפוש";
        return screen + " / בקשת API שלא סווגה";
    }

    private String apiDiscoverySummary() {
        StringBuilder out = new StringBuilder();
        try {
            JSONArray endpoints = new JSONArray(prefs.getString(PREF_API_DISCOVERY, "[]"));
            JSONArray matches = new JSONArray(prefs.getString(PREF_API_MATCHES, "[]"));
            out.append("\n========== אינדקס API מקומי שנלמד מהשימוש באפליקציה ==========\n")
                    .append("הכתובות נשמרות במכשיר בלבד; נרשמו ").append(endpoints.length()).append(" נתיבים ייחודיים.\n");
            for (int i = 0; i < endpoints.length(); i++) {
                JSONObject e = endpoints.optJSONObject(i);
                if (e == null) continue;
                out.append(e.optString("method", "GET")).append(" ")
                        .append(e.optString("url", e.optString("path", "")))
                        .append(" | HTTP ").append(e.optInt("last_http", -1))
                        .append(" | שימושים ").append(e.optInt("count", 1))
                        .append(" | הקשר: ").append(e.optString("context", "לא ידוע")).append("\n");
            }
            out.append("\n========== תשובות שבהן זוהו אפונה/קייטי/מזהה הפריט ==========\n")
                    .append("נשמרו ").append(matches.length()).append(" התאמות אחרונות.\n");
            for (int i = 0; i < matches.length(); i++) {
                JSONObject m = matches.optJSONObject(i);
                if (m == null) continue;                out.append("\n--- התאמה ").append(i + 1).append(" ---\n")
                        .append("URL: ").append(m.optString("url", "")).append("\n")
                        .append("הקשר: ").append(m.optString("context", "")).append("\n")
                        .append("HTTP: ").append(m.optInt("http", -1))
                        .append(" | מזהה מדויק: ").append(m.optBoolean("matched_id"))
                        .append(" | שם מתאים: ").append(m.optBoolean("matched_name"))
                        .append(" | אורך תשובה: ").append(m.optInt("response_chars")).append("\n")
                        .append(m.optString("response_excerpt_or_body", "")).append("\n");
            }
        } catch (Exception e) {
            out.append("\nלא ניתן לקרוא את אינדקס ה־API המקומי: ").append(e.getMessage()).append("\n");
        }
        return out.toString();
    }

    private String tokenFromHeaders(HttpURLConnection c) {
        String[] names = {"Authorization", "X-Authorization", "X-New-Token", "X-Access-Token"};
        for (String n : names) {
            String v = c.getHeaderField(n);
            if (v == null) continue;
            v = v.trim();
            if (v.toLowerCase(Locale.ROOT).startsWith("bearer ")) v = v.substring(7).trim();
            if (v.length() > 20 && v.split("\\.").length == 3) return v;
        }
        return null;
    }

    private HttpResult requestRaw(String method, String path, String token, JSONObject body) throws Exception {
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
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (stream != null) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stream.read(buf)) > 0) out.write(buf, 0, n);
                stream.close();
            }
            return new HttpResult(code, new String(out.toByteArray(), StandardCharsets.UTF_8), tokenFromHeaders(c));
        } finally {
            c.disconnect();
        }
    }

    private void fail(int token, Exception e, String fallback) {
        runOnUiThread(() -> {
            if (e instanceof AuthException) {
                showLogin();
                toast(e.getMessage(), true);
                return;
            }
            String msg = errText(e, fallback);
            if (stale(token)) toast(msg, true);
            else setStatus(msg, true);
        });
    }

    // ======================================================================== images

    private String imageUrl(JSONObject p, boolean thumb) {
        if (p == null) return "";
        JSONObject prod = p.optJSONObject("product") != null ? p.optJSONObject("product") : p;
        String url = "";
        if (thumb) {
            String t = first(prod, "thumb_featured_image");
            if (t.isEmpty()) t = first(p, "thumb_featured_image");
            if (!t.isEmpty()) url = t;
        }
        if (url.isEmpty()) {
            JSONArray images = prod.optJSONArray("images");
            if (images == null) images = p.optJSONArray("images");
            if (images != null) {
                for (int i = 0; i < images.length() && url.isEmpty(); i++) {
                    JSONObject img = images.optJSONObject(i);
                    if (img != null) {
                        String direct = first(img, "url", "src", "path");
                        String filename = first(img, "filename");
                        if (!direct.isEmpty()) url = direct;
                        else if (!filename.isEmpty()) {
                            String ext = first(img, "extension");
                            if (ext.startsWith(".")) ext = ext.substring(1);
                            url = filename + "." + (ext.isEmpty() ? "jpg" : ext);
                        }
                    } else if (images.opt(i) instanceof String) {
                        url = (String) images.opt(i);
                    }
                }
            }
        }
        if (url.isEmpty()) url = first(prod, "featured_image", "image_url", "imageUrl", "picture_url");
        if (url.isEmpty()) url = first(p, "featured_image", "image_url", "imageUrl", "picture_url");
        if (url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        return IMG_BASE + url.replaceFirst("^/+", "");
    }

    private void loadImage(final ImageView iv, final String url) {
        if (url == null || url.isEmpty()) return;
        iv.setTag(url);
        if (TextUtils.isEmpty(iv.getContentDescription())) iv.setContentDescription(url);
        iv.setOnClickListener(v -> {
            Object full = iv.getContentDescription();
            Object tagged = iv.getTag();
            String previewUrl = full instanceof CharSequence ? full.toString() : "";
            if (previewUrl.isEmpty() && tagged instanceof String) previewUrl = (String) tagged;
            showImagePreview(previewUrl);
        });
        iv.setClickable(true);
        // Product thumbnails open on click but must not become accidental D-pad focus stops.
        iv.setFocusable(false);
        iv.setFocusableInTouchMode(false);
        Bitmap cached = imageCache.get(url);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        try {
            imageExecutor.execute(() -> {
                Bitmap bmp = downloadBitmap(url, 480);
                if (bmp == null) return;
                imageCache.put(url, bmp);
                runOnUiThread(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(bmp); });
            });
        } catch (Exception ignored) { }
    }

    /**
     * Opens the original product image in a full-screen viewer.
     * The source URL is kept separate from the thumbnail URL to load the original image.
     * Any tap anywhere and any key press closes the viewer.
     */
    private void showImagePreview(String url) {
        if (url == null || url.trim().isEmpty() || isFinishing()) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // A plain, full-screen Dialog avoids AlertDialog's internal custom/button panels.
        // Intercept touch/key events at the Window's dispatch boundary so empty margins
        // below or beside the photo dismiss just like a tap on the photo itself.
        final android.app.Dialog dialog = new android.app.Dialog(
                this, android.R.style.Theme_Translucent_NoTitleBar) {
            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event != null
                        && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    dismiss();
                    return true;
                }
                return true;
            }

            @Override
            public boolean dispatchKeyEvent(android.view.KeyEvent event) {
                if (event != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    dismiss();
                    return true;
                }
                return true;
            }
        };

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(AppPalette.IMAGE_OVERLAY);
        overlay.setClickable(true);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setContentDescription("תצוגה מוגדלת של התמונה");
        overlay.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        TextView loading = text("טוען תמונה מלאה…", 14, true);
        loading.setTextColor(Color.WHITE);
        loading.setGravity(Gravity.CENTER);
        loading.setBackground(shape(AppPalette.IMAGE_LOADING_BACKGROUND, 14, 0));
        overlay.addView(loading, new FrameLayout.LayoutParams(-2, dp(48), Gravity.CENTER));

        TextView close = text("×", 27, true);
        close.setTextColor(Color.WHITE);
        close.setGravity(Gravity.CENTER);
        close.setBackground(shape(AppPalette.IMAGE_CLOSE_BACKGROUND, 22, 0));
        close.setElevation(dp(6));
        close.setContentDescription("סגירת התמונה המוגדלת");
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                dp(42), dp(42), Gravity.TOP | Gravity.LEFT);
        closeLp.leftMargin = dp(16);
        closeLp.topMargin = dp(24);
        overlay.addView(close, closeLp);

        Runnable positionCloseOnPhoto = () -> {
            if (preview.getDrawable() == null || preview.getWidth() <= 0 || preview.getHeight() <= 0) return;
            int photoWidth = preview.getDrawable().getIntrinsicWidth();
            int photoHeight = preview.getDrawable().getIntrinsicHeight();
            if (photoWidth <= 0 || photoHeight <= 0) return;

            float scale = Math.min(
                    (float) preview.getWidth() / photoWidth,
                    (float) preview.getHeight() / photoHeight
            );
            int shownWidth = Math.round(photoWidth * scale);
            int shownHeight = Math.round(photoHeight * scale);

            closeLp.leftMargin = Math.max(dp(8),
                    (preview.getWidth() - shownWidth) / 2 + shownWidth - dp(42) - dp(8));
            closeLp.topMargin = Math.max(dp(16),
                    (preview.getHeight() - shownHeight) / 2 + dp(8));
            close.setLayoutParams(closeLp);
        };

        dialog.setContentView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        imageViewerDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (imageViewerDialog == dialog) imageViewerDialog = null;
        });
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.22f;
            window.setAttributes(attrs);
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }

        final int decodeSize = Math.max(screenWidth, screenHeight) * 2;
        try {
            imageExecutor.execute(() -> {
                Bitmap bmp = downloadBitmap(url, decodeSize);
                runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    if (bmp != null) {
                        loading.setVisibility(View.GONE);
                        preview.setImageBitmap(bmp);
                        preview.post(positionCloseOnPhoto);
                    } else {
                        loading.setText("לא ניתן לטעון את התמונה. אפשר לסגור ולנסות שוב.");
                        loading.setVisibility(View.VISIBLE);
                    }
                });
            });
        } catch (Exception e) {
            loading.setText("לא ניתן לטעון את התמונה.");
            loading.setVisibility(View.VISIBLE);
        }
    }

    // ---- images are kept on disk, so they open instantly and also work offline

    private File imageDir() {
        File d = new File(getFilesDir(), "img");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private String imageKey(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : digest) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private File imageFile(String url) { return new File(imageDir(), imageKey(url) + ".img"); }

    private byte[] fetchImageBytes(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Referer", "https://mishnatyosef.org/");
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            c.setRequestProperty("Accept", "image/*,*/*");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 6000000) { in.close(); return null; }
                out.write(buf, 0, n);
            }
            in.close();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void writeImageFile(File f, byte[] data) {
        try {
            File tmp = new File(f.getPath() + ".tmp" + Thread.currentThread().getId());
            try (FileOutputStream os = new FileOutputStream(tmp)) { os.write(data); }
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Exception ignored) { }
    }

    private Bitmap decodeBytes(byte[] data, int maxPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap decodeFile(File f, int maxPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }

    /** Disk first, then the network (and the downloaded bytes are kept for next time). */
    private Bitmap downloadBitmap(String url, int maxPx) {
        File f = imageFile(url);
        if (f.exists() && f.length() > 0) {
            Bitmap cached = decodeFile(f, maxPx);
            if (cached != null) return cached;
            f.delete();
        }
        byte[] data = fetchImageBytes(url);
        if (data == null) return null;
        writeImageFile(f, data);
        return decodeBytes(data, maxPx);
    }

    /** Quietly downloads every product picture in the background so the next opening is instant. */
    private void prefetchImages(final List<JSONObject> products) {
        final boolean thumb = !fullImages();
        try {
            prefetchExecutor.execute(() -> {
                for (JSONObject p : products) {
                    if (Thread.currentThread().isInterrupted()) return;
                    String url = imageUrl(p, thumb);
                    if (url.isEmpty()) continue;
                    File f = imageFile(url);
                    if (f.exists() && f.length() > 0) continue;
                    byte[] data = fetchImageBytes(url);
                    if (data != null) writeImageFile(f, data);
                }
            });
        } catch (Exception ignored) { }
    }

    /** Keeps the picture cache under ~300 MB (oldest files go first). */
    private void pruneImageCache() {
        try {
            prefetchExecutor.execute(() -> {
                File[] files = imageDir().listFiles();
                if (files == null) return;
                long total = 0;
                for (File f : files) total += f.length();
                if (total <= 300L * 1024L * 1024L) return;
                java.util.Arrays.sort(files, new Comparator<File>() {
                    @Override public int compare(File x, File y) { return Long.compare(x.lastModified(), y.lastModified()); }
                });
                for (File f : files) {
                    if (total <= 250L * 1024L * 1024L) break;
                    total -= f.length();
                    f.delete();
                }
            });
        } catch (Exception ignored) { }
    }

    private ImageView imageView(int widthDp, int heightDp, ImageView.ScaleType scale) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(scale);
        iv.setBackgroundColor(SURFACE);
        iv.setLayoutParams(new LinearLayout.LayoutParams(widthDp < 0 ? widthDp : dp(widthDp), dp(heightDp)));
        iv.setOnClickListener(v -> {
            Object full = iv.getContentDescription();
            Object tagged = iv.getTag();
            String previewUrl = full instanceof CharSequence ? full.toString() : "";
            if (previewUrl.isEmpty() && tagged instanceof String) previewUrl = (String) tagged;
            showImagePreview(previewUrl);
        });
        iv.setClickable(true);
        iv.setFocusable(true);
        return iv;
    }

    // ======================================================================== product text helpers (learned from the Python script)

    private JSONObject productOf(JSONObject p) {
        JSONObject prod = p == null ? null : p.optJSONObject("product");
        return prod != null ? prod : p;
    }

    private String sidOf(JSONObject p) {
        String id = first(p, "item_salesID", "item_sales_id", "id", "itemID");
        return id;
    }

    private String productName(JSONObject p) {
        String name = first(p, "name", "full_name", "title");
        if (name.isEmpty()) name = first(p.optJSONObject("product"), "name", "full_name", "title");
        return name.isEmpty() ? "מוצר #" + sidOf(p) : name;
    }

    private double productPrice(JSONObject p) {
        String raw = first(p, "price", "unit_price", "unitPrice", "price_list");
        if (raw.isEmpty()) raw = first(p.optJSONObject("product"), "price", "unit_price");
        return parseMoney(raw);
    }

    private double numericAvailable(JSONObject p) {
        if (!p.has("available") || p.isNull("available")) return -1;
        Object value = p.opt("available");
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).trim()); } catch (Exception ignored) { return -1; }
        }
        return -1;
    }

    private boolean isSoldOut(JSONObject p) { return numericAvailable(p) == 0; }

    /** max_per_man: the most a single customer may order of this item (0 = no limit). */
    private int maxPerMan(JSONObject p) {
        if (p == null) return 0;
        double v = num(p.opt("max_per_man"));
        if (v <= 0) v = num(productOf(p) == null ? null : productOf(p).opt("max_per_man"));
        return v > 0 ? (int) Math.round(v) : 0;
    }

    private boolean showMax() { return prefs.getBoolean("show_max", true); }

    /** "רשת כ-2 ק"ג", "שק 5 ק"ג", "יחידה"... */
    private String packageText(JSONObject p) {
        String kind = nameOf(p, "item_type"), unit = nameOf(p, "item_unit");
        double units = num(p.opt("units"));
        StringBuilder sb = new StringBuilder(kind);
        if (units > 0 && !unit.isEmpty() && !unit.equals("יחידה") && !unit.equals("יח'") && !unit.equals("יח")) {
            if (sb.length() > 0) sb.append(" ");
            if (truthy(p.opt("approx"))) sb.append("כ-");
            sb.append(fmtNum(units)).append(" ").append(unit);
        }
        return sb.toString();
    }

    /** מחיר ליחידת משקל: ₪3.10 לק"ג, ובפחות מקילו ל-100 גרם. */
    private String unitPriceText(JSONObject p) {
        double price = productPrice(p), units = num(p.opt("units"));
        String unit = nameOf(p, "item_unit");
        if (price <= 0 || units <= 0 || unit.isEmpty()) return "";
        if (unit.equals("ק\"ג")) {
            return units < 1 ? money2(price / (units * 10)) + " ל-100 גרם" : money2(price / units) + " לק\"ג";
        }
        if (unit.equals("גרם")) return money2(price / units * 100) + " ל-100 גרם";
        if (unit.equals("ליטר")) return money2(price / units) + " לליטר";
        if (unit.equals("מ\"ל")) return money2(price / units * 100) + " ל-100 מ\"ל";
        return "";
    }

    private String categoryName(JSONObject p) {
        JSONObject prod = productOf(p);
        Object cat = prod.opt("category");
        if (cat == null || cat == JSONObject.NULL) cat = p.opt("category");
        String name = "";
        if (cat instanceof JSONObject) name = ((JSONObject) cat).optString("name", "").trim();
        else if (cat instanceof String) name = ((String) cat).trim();
        return name.isEmpty() || "null".equalsIgnoreCase(name) ? "ללא קטגוריה" : name;
    }

    private int categoryOrder(JSONObject p) {
        // Keep eggs near the requested early position in the category strip.
        if (categoryName(p).contains("ביצים")) return 15;
        JSONObject prod = productOf(p);
        Object cat = prod.opt("category");
        if (!(cat instanceof JSONObject)) cat = p.opt("category");
        if (cat instanceof JSONObject) {
            Object o = ((JSONObject) cat).opt("order");
            if (o instanceof Number) return ((Number) o).intValue();
        }
        return 9999;
    }

    // ======================================================================== sales list

    private boolean autoEnterSingle = true;

    /** From the tab / startup: restore the in-memory order if we just left it for another tab. */
    private void showOpenSales() {
        if (returnToOrderScreenOnSalesTab && featureSale != null) {
            int cachedSaleId = featureSale.optInt("id", -1);
            if (cachedSaleId > 0 && currentSaleId == cachedSaleId
                    && featureOrderId > 0 && !featureProducts.isEmpty() && !productsPartial) {
                // Rebuild only the view from the already-loaded in-memory data; do not refetch sales/products.
                returnToOrderScreenOnSalesTab = false;
                renderFeatureProducts();
                return;
            }
        }
        showOpenSales(true);
    }

    private void showOpenSales(boolean autoEnter) {
        returnToOrderScreenOnSalesTab = false;
        closedOrderReadOnly = false;
        autoEnterSingle = autoEnter;
        beginScreen("מכירות פתוחות", 0, null);
        showingSalesList = true;
        // Draw the local/offline actions immediately; the server request below updates this same tab.
        renderSales(new JSONArray());
        setStatus("ממתין לשרת…");
        loadOpenSales();
    }

    private void loadOpenSales() {
        final int t = screenToken;
        setStatus("טוען מכירות פתוחות…");
        executor.execute(() -> {
            try {
                HttpResult r = api("GET", "/api/profile/open_sales", null);
                if (r.code < 200 || r.code >= 300) {
                    throw new Exception(apiErrorMessage(r.body, "טעינת המכירות נכשלה."));
                }
                JSONArray rows = arrayFrom(r.body);
                try { SaleMonitor.scheduleClosingReminders(getApplicationContext(), rows); } catch (Exception ignored) { }
                runOnUiThread(() -> { if (!stale(t)) renderSales(rows); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (stale(t)) return;
                    // Keep the offline actions visible even when the server cannot be reached.
                    renderSales(new JSONArray());
                    setStatus(errText(e, "טעינת המכירות נכשלה. אפשר לבדוק שוב באמצעות רענון."), true);
                });
            }
        });
    }

    private void renderSales(JSONArray rows) {
        setStatus("");
        if (autoEnterSingle && rows.length() == 1 && rows.optJSONObject(0) != null) {
            autoEnterSingle = false;
            loadFeatureProducts(rows.optJSONObject(0));
            return;
        }
        // Re-render this screen in place: the initial offline actions and the server
        // response both call renderSales(), so remove the previous sales content first.
        // Keep child 0 (statusView), which is owned by setStatus().
        if (root != null) {
            while (root.getChildCount() > 1) {
                root.removeViewAt(1);
            }
        }
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(muted(rows.length() == 0 ? "אין כרגע מכירות פתוחות" : rows.length() + " מכירות פתוחות", 14), new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = smallChip("↻ רענון", TONAL);
        refresh.setOnClickListener(v -> { toast("↻ מרענן את המכירות…", false); showOpenSales(false); });
        top.addView(refresh);
        root.addView(top, lp(-1, -2, 0, 8));

        // Show this shortcut only while at least one of the two sale notifications still needs setup.
        boolean saleOpeningNotificationEnabled = prefs.getBoolean("sale_monitor_enabled", true);
        boolean saleClosingReminderEnabled = prefs.getBoolean("closing_reminder_enabled", false);
        if (!(saleOpeningNotificationEnabled && saleClosingReminderEnabled)) {
            LinearLayout notificationShortcut = card(14);
            notificationShortcut.addView(text(
                    "🔔 רוצים לקבל התראה ברגע שנפתחת מכירה?\n🔔 רוצים לקבל תזכורת לפני סגירת מכירה?",
                    16, true), lp(-1, -2, 0, 4));
            TextView notificationSettingsButton = btn("לחצו כאן", PRIMARY);
            notificationSettingsButton.setTextSize(15);
            notificationSettingsButton.setGravity(Gravity.CENTER);
            notificationSettingsButton.setMinHeight(dp(42));
            notificationSettingsButton.setPadding(dp(12), dp(6), dp(12), dp(6));
            notificationSettingsButton.setOnClickListener(v -> {
                // The shortcut lives on Open Sales, but notification settings belong to Account.
                // Switch the active bottom tab first so the Account tab is selected and Back returns there.
                navigateTab(3, this::showAccount);
                showNotificationSettings();
            });
            notificationShortcut.addView(notificationSettingsButton, lp(-1, -2, 0, 8));
            root.addView(notificationShortcut, lp(-1, -2, 0, 10));
        }

        LinearLayout cachedOrdersBox = new LinearLayout(this);
        cachedOrdersBox.setOrientation(LinearLayout.VERTICAL);
        cachedOrdersBox.setGravity(Gravity.CENTER);
        cachedOrdersBox.setPadding(dp(12), dp(9), dp(12), dp(9));
        cachedOrdersBox.setBackground(ripple(shape(ORANGE_LIGHT, 16, LINE), 16));
        TextView cachedOrders = text("📦 הזמנות מההיסטוריה", 18, true);
        cachedOrders.setTextColor(ORANGE_DARK);
        cachedOrders.setGravity(Gravity.CENTER);
        TextView cachedOrdersNote = muted("מבוסס על זיכרון המכשיר בלבד — לא מידע מהשרת", 12);
        cachedOrdersNote.setGravity(Gravity.CENTER);
        cachedOrdersBox.addView(cachedOrders, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(2);
        cachedOrdersBox.addView(cachedOrdersNote, noteParams);
        cachedOrdersBox.setMinimumHeight(dp(62));
        cachedOrdersBox.setContentDescription("פתיחת הזמנות מההיסטוריה המקומית");
        cachedOrdersBox.setOnClickListener(v -> showCachedOrders());
        root.addView(cachedOrdersBox, lp(-1, -2, 0, 10));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject sale = rows.optJSONObject(i);
            if (sale != null) root.addView(saleCard(sale), lp(-1, -2, 0, 10));
        }
        // Restore only after the sales cards have been added and measured.
        final int restoreY = salesListScrollY;
        if (scrollView != null) scrollView.post(() -> {
            if (showingSalesList && scrollView != null) scrollView.scrollTo(0, restoreY);
        });
    }

    /**
     * Shows only readable local sale snapshots. This screen deliberately never queries
     * the server: the available cache files are the source of truth for this list.
     */
    private void showCachedOrders() {
        beginScreen("הזמנות מההיסטוריה", 0, null);
        showingSalesList = false;
        final int t = screenToken;
        // Render local snapshots immediately; server history only fills missing sale names.
        loadExecutor.execute(() -> {
            final List<Integer> cachedSaleIds = new ArrayList<>();
            final Map<Integer, SaleSnapshot> cachedSnapshots = new LinkedHashMap<>();
            try {
                File dir = new File(getFilesDir(), "cache");
                File[] files = dir.listFiles((d, name) -> name.matches("sale_[0-9]+\\.json"));
                if (files != null) {
                    java.util.Arrays.sort(files, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
                    for (File file : files) {
                        String fileName = file.getName();
                        int saleId;
                        try { saleId = Integer.parseInt(fileName.substring(5, fileName.length() - 5)); }
                        catch (Exception ignored) { continue; }
                        if (saleId <= 0) continue;
                        SaleSnapshot snapshot = readSnapshot(saleId);
                        if (snapshot == null || snapshot.products.isEmpty()) continue;
                        cachedSaleIds.add(saleId);
                        cachedSnapshots.put(saleId, snapshot);
                    }
                }
            } catch (Exception ignored) { }
            final Map<Integer, TextView> titleViews = new LinkedHashMap<>();
            final Map<Integer, JSONObject> cachedOrders = new LinkedHashMap<>();
            runOnUiThread(() -> {
                if (stale(t)) return;
                setStatus("");
                TextView back = btn("‹ חזרה להזמנות פתוחות", TONAL);
                back.setOnClickListener(v -> showOpenSales(false));
                root.addView(back, lp(-1, dp(46), 0, 8));
                if (cachedSaleIds.isEmpty()) {
                    root.addView(muted("לא נמצאו הזמנות עם מטמון מקומי קריא.", 13), lp(-1, -2, 0, 10));
                    return;
                }
                for (int saleId : cachedSaleIds) {
                    SaleSnapshot snapshot = cachedSnapshots.get(saleId);
                    if (snapshot == null) continue;
                    String saleName = snapshot.saleName == null ? "" : snapshot.saleName.trim();
                    if (saleName.isEmpty()) saleName = "מכירה #" + saleId;
                    LinearLayout item = card(16);
                    item.setPadding(dp(16), dp(16), dp(16), dp(16));
                    item.setMinimumHeight(dp(88));
                    item.setClickable(true);
                    item.setFocusable(true);
                    TextView saleTitleView = text(saleName, 18, true);
                    saleTitleView.setTextColor(INK);
                    saleTitleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                    item.addView(saleTitleView, lp(-1, -2, 0, 4));

                    int totalQuantity = 0;
                    double totalAmount = 0d;
                    for (JSONObject product : snapshot.products) {
                        if (product == null) continue;
                        String sid = sidOf(product);
                        int quantity = sid.isEmpty() ? 0 : Math.max(0, snapshot.quantities.containsKey(sid)
                                ? snapshot.quantities.get(sid) : 0);
                        totalQuantity += quantity;
                        totalAmount += productPrice(product) * quantity;
                    }
                    String orderDate = "לא ידוע";
                    if (snapshot.loadedAt > 0) {
                        orderDate = android.text.format.DateFormat.format("dd/MM/yyyy", snapshot.loadedAt).toString();
                    }
                    TextView details = muted(
                            "תאריך: " + orderDate
                                    + "   •   כמות מוצרים: " + totalQuantity
                                    + "   •   סה״כ הזמנה: " + money(totalAmount),
                            14);
                    details.setTextColor(MUTED);
                    details.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    details.setMaxLines(2);
                    item.addView(details, lp(-1, -2, 0, 2));

                    final int snapshotSaleId = saleId;
                    final JSONObject cachedOrder = new JSONObject();
                    try {
                        cachedOrder.put("saleID", saleId);
                        cachedOrder.put("sale_name", saleName);
                        cachedOrder.put("id", snapshot.orderId);
                    } catch (Exception ignored) { }
                    titleViews.put(saleId, saleTitleView);
                    cachedOrders.put(saleId, cachedOrder);
                    item.setContentDescription("פתיחת ההזמנה מההיסטוריה: " + saleName);
                    item.setOnClickListener(v -> openCachedClosedOrder(cachedOrder, snapshotSaleId));
                    saleTitleView.setOnClickListener(v -> openCachedClosedOrder(cachedOrder, snapshotSaleId));
                    root.addView(item, lp(-1, -2, 0, 4));
                }
            });
            Map<Integer, String> saleNames = fetchCachedSaleNames(new HashSet<>(cachedSaleIds));
            for (Map.Entry<Integer, String> entry : saleNames.entrySet()) {
                String resolved = entry.getValue() == null ? "" : entry.getValue().trim();
                SaleSnapshot snapshot = cachedSnapshots.get(entry.getKey());
                if (snapshot != null && !resolved.isEmpty()) {
                    snapshot.saleName = resolved;
                    persistCachedSaleName(entry.getKey(), resolved);
                }
            }
            if (!saleNames.isEmpty()) runOnUiThread(() -> {
                if (stale(t)) return;
                for (Map.Entry<Integer, String> entry : saleNames.entrySet()) {
                    String resolved = entry.getValue() == null ? "" : entry.getValue().trim();
                    if (resolved.isEmpty()) continue;
                    TextView title = titleViews.get(entry.getKey());
                    if (title != null) title.setText(resolved);
                    JSONObject order = cachedOrders.get(entry.getKey());
                    if (order != null) try { order.put("sale_name", resolved); } catch (Exception ignored) { }
                }
            });
        });
    }

    private Map<Integer, String> fetchCachedSaleNames(Set<Integer> targetSaleIds) {
        Map<Integer, String> names = new LinkedHashMap<>();
        if (targetSaleIds == null || targetSaleIds.isEmpty() || !hasToken()) return names;
        int page = 1;
        int lastPage = 1;
        try {
            while (page <= lastPage && page <= 100 && names.size() < targetSaleIds.size()) {
                HttpResult response = api("GET", "/api/orders?page=" + page, null);
                if (response.code < 200 || response.code >= 300) break;
                Object parsed = new JSONTokener(response.body == null ? "" : response.body).nextValue();
                JSONArray rows;
                JSONObject payload = null;
                if (parsed instanceof JSONObject) {
                    payload = (JSONObject) parsed;
                    rows = arrayFrom(response.body);
                    JSONObject meta = payload.optJSONObject("meta");
                    JSONObject dataObject = payload.optJSONObject("data");
                    lastPage = Math.max(1, payload.optInt("last_page",
                            meta != null ? meta.optInt("last_page", 1)
                                    : dataObject != null ? dataObject.optInt("last_page", 1) : 1));
                } else if (parsed instanceof JSONArray) {
                    rows = (JSONArray) parsed;
                    lastPage = 1;
                } else {
                    break;
                }
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) continue;
                    String rawSaleId = first(row, "saleID", "sale_id", "saleId");
                    int saleId;
                    try { saleId = Integer.parseInt(rawSaleId); } catch (Exception ignored) { continue; }
                    if (!targetSaleIds.contains(saleId) || names.containsKey(saleId)) continue;
                    String name = saleTitle(row.optJSONObject("sale"));
                    if (name.isEmpty()) name = saleTitle(row);
                    if (name.isEmpty()) name = saleTitle(row.optJSONObject("parent"));
                    if (!name.trim().isEmpty()) names.put(saleId, name.trim());
                }
                page++;
            }
        } catch (Exception ignored) { }
        return names;
    }

    private void persistCachedSaleName(int saleId, String saleName) {
        if (saleId <= 0 || saleName == null || saleName.trim().isEmpty()) return;
        try {
            File f = snapshotFile(saleId);
            if (!f.exists() || f.length() == 0) return;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (InputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            }
            JSONObject o = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            o.put("sale_name", saleName.trim());
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Exception ignored) { }
    }

    /**
     * Opens cached products in the same grid used by a regular order.
     * This mode is strictly read-only: it never fetches a live order or writes quantities.
     */
    private void openCachedClosedOrder(JSONObject order, int saleId) {
        if (saleId <= 0) {
            toast("אין מזהה מכירה מתאים לקובץ המטמון.", true);
            return;
        }
        toast("טוען את ההזמנה מהמטמון המקומי…", false);
        loadExecutor.execute(() -> {
            SaleSnapshot snap = readSnapshot(saleId);
            runOnUiThread(() -> {
                if (snap == null || snap.products.isEmpty()) {
                    toast("לא נמצא מטמון קריא למכירה הזאת.", true);
                    return;
                }
                JSONObject sale = order == null ? null : order.optJSONObject("sale");
                JSONObject cachedSale = new JSONObject();
                try {
                    if (sale != null) cachedSale = new JSONObject(sale.toString());
                    cachedSale.put("id", saleId);
                    String name = saleTitle(cachedSale);
                    if (name.isEmpty()) name = first(order, "sale_name", "saleName", "name", "title");
                    if (name.isEmpty()) name = "הזמנה סגורה #" + extractOrderId(order);
                    cachedSale.put("custom_name", name);
                } catch (Exception ignored) { }
                closedOrderReadOnly = true;
                featureSale = cachedSale;
                summaryFromHistory = false;
                filterText = "";
                viewMode = "all";
                featureProducts.clear();
                featureProducts.addAll(snap.products);
                featureQuantities.clear();
                // This cached order is only a catalogue for preparing charges.
                // Never seed the temporary selector with quantities from the original order.
                for (JSONObject cachedProduct : snap.products) {
                    if (cachedProduct == null) continue;
                    String cachedSid = sidOf(cachedProduct);
                    if (!cachedSid.isEmpty()) featureQuantities.put(cachedSid, 0);
                }
                favIds.clear();
                favIds.addAll(snap.favs);
                pending.clear();
                featureOrderId = -1;
                currentSaleId = saleId;
                productsLoadedAt = snap.loadedAt;
                productsPartial = false;
                productsLoading = false;
                orderDirty = false;
                renderFeatureProducts();
                setStatus("תצוגת מטמון · הכמויות כאן מיועדות לחיובים בלבד ואינן משנות את ההזמנה");
                if (barInfo != null) updateOrderBar();
            });
        });
    }

    /** Change only the temporary quantity selector used to prepare a charge from a closed order. */
    private void changeCachedChargeQuantity(JSONObject product, int delta) {
        if (!closedOrderReadOnly || product == null) return;
        String sid = sidOf(product);
        if (sid.isEmpty()) {
            toast("לא ניתן לזהות את שורת המוצר במטמון.", true);
            return;
        }
        int current = featureQuantities.containsKey(sid) ? featureQuantities.get(sid) : 0;
        featureQuantities.put(sid, Math.max(0, current + delta));
        lastChangedProductSid = sid;
        View targetCard = findTaggedView(currentScreen, "productCard:" + sid);
        View quantityView = findTaggedView(targetCard, "productQty");
        if (quantityView instanceof TextView) {
            ((TextView) quantityView).setText(String.valueOf(Math.max(0, current + delta)));
        }
        updateOrderBar();
    }

    private String cachedClosedOrderText() {
        StringBuilder details = new StringBuilder(currentTitle()).append("\\nעותק מקומי של הזמנה סגורה — לקריאה בלבד\\n\\n");
        int count = 0;
        double total = 0;
        for (OrderLine line : currentOrderLines()) {
            details.append(line.name).append(" × ").append(fmtNum(line.amount));
            if (line.price > 0) { details.append(" · ").append(money(line.price)).append(" ליחידה"); total += line.price * line.amount; }
            details.append("\\n");
            count++;
        }
        details.append("\\n").append(count).append(" מוצרים עם כמות שמורה");
        if (total > 0) details.append(" · סה״כ משוער ").append(money(total));
        details.append("\\nהנתונים נשמרו במטמון המקומי ועשויים להיות חלקיים או לא עדכניים.");
        return details.toString();
    }

    private void showCachedClosedOrderSummary() {
        StringBuilder details = new StringBuilder("תצוגת מטמון מקומית — לקריאה בלבד\n\n");
        int count = 0;
        for (JSONObject p : featureProducts) {
            if (p == null) continue;
            String sid = sidOf(p);
            int qty = featureQuantities.containsKey(sid) ? featureQuantities.get(sid) : 0;
            if (qty <= 0) continue;
            details.append(productName(p)).append(" × ").append(qty);
            double price = productPrice(p);
            if (price > 0) details.append(" · ").append(money(price)).append(" ליחידה");
            details.append("\n");
            count++;
        }
        details.append("\n").append(count).append(" מוצרים עם כמות שמורה.");
        details.append("\nהנתונים עשויים להיות חלקיים או לא עדכניים.");
        appDialog().setTitle("פריטי ההזמנה הסגורה").setMessage(details.toString())
                .setPositiveButton("סגירה", null).show();
    }

    private View saleCard(final JSONObject sale) {
        String name = saleTitle(sale);
        if (name.isEmpty()) name = "מכירה #" + first(sale, "id");
        JSONObject order = sale.optJSONObject("order");
        boolean hasOrder = extractOrderId(order) > 0;
        boolean closed = truthy(sale.opt("closed_for_orders"));

        LinearLayout card = card(14);
        card.setTag("openSaleCard");
        card.setBackground(ripple(shape(nightMode ? Color.BLACK : SURFACE, 14, nightMode ? Color.BLACK : LINE), 14));
        card.setClickable(true);
        card.setOnClickListener(v -> loadFeatureProducts(sale));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(name, 17, true);
        title.setTag("openSaleTitle");
        if (nightMode) title.setTextColor(Color.WHITE);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView info = smallChip("פרטים", nightMode ? TONAL : FLAT);
        info.setTag("openSaleInfo");
        info.setOnClickListener(v -> showSaleDetails(sale));
        titleRow.addView(info);
        card.addView(titleRow);

        LinearLayout chips = new LinearLayout(this);
        TextView state = text(hasOrder ? "✔ יש הזמנה" : "טרם נפתחה הזמנה", 12, true);
        state.setTextColor(hasOrder ? GREEN : MUTED);
        state.setBackground(shape(hasOrder ? GREEN_LIGHT : NO_ORDER_BG, 8, 0));
        state.setPadding(dp(10), dp(3), dp(10), dp(3));
        chips.addView(state);
        if (closed) {
            TextView lock = text("🔒 סגורה להזמנות", 12, true);
            lock.setTextColor(RED);
            lock.setBackground(shape(RED_LIGHT, 8, 0));
            lock.setPadding(dp(10), dp(3), dp(10), dp(3));
            LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(-2, -2);
            lockLp.setMarginStart(dp(6));
            chips.addView(lock, lockLp);
        }
        card.addView(chips, lp(-2, -2, 6, 6));

        String closing = closingText(sale);
        if (!closing.isEmpty()) card.addView(muted("⏰ " + closing, 13));
        Date pickup = pickupDate(sale);
        if (pickup != null) card.addView(muted("📦 איסוף: " + fmtDay(pickup, false), 13));

        TextView go = btn(hasOrder ? "המשך להזמנה  ‹" : "פתיחת הזמנה  ‹", PRIMARY);
        go.setOnClickListener(v -> loadFeatureProducts(sale));
        card.addView(go, lp(-1, dp(46), 10, 0));
        return card;
    }

    private void showSaleDetails(JSONObject sale) {
        if (sale == null) return;
        List<String> lines = new ArrayList<>();
        JSONObject parent = sale.optJSONObject("parent");
        JSONObject site = sale.optJSONObject("site");
        JSONObject order = sale.optJSONObject("order");
        Date begin = parseDt(first(sale, "begin")), end = parseDt(first(sale, "end"));
        if (begin != null) lines.add((begin.getTime() <= System.currentTimeMillis() ? "🟢 נפתחה: " : "🕒 תיפתח: ") + fmtDay(begin, true));
        if (truthy(sale.opt("closed_for_orders"))) {
            String reason = first(sale, "reason_for_closing");
            lines.add("🔒 המערכת סגורה" + (reason.isEmpty() ? "" : ": " + reason));
        }
        if (end != null) {
            String closing = closingText(sale);
            lines.add("⏰ " + (closing.equals("המכירה נסגרה") ? "המערכת נסגרה" : "המערכת " + closing));
            lines.add("    סגירה: " + fmtDay(end, true));
        }
        Date pick = pickupDate(sale);
        lines.add("📦 יום איסוף: " + (pick == null ? "לא ידוע" : fmtDay(pick, false)));
        String hours = first(order, "times");
        if (hours.isEmpty() && !first(sale, "startHour").isEmpty()) hours = first(sale, "startHour") + "-" + first(sale, "endHour");
        if (!hours.isEmpty()) lines.add("    שעה: " + hours);
        if (site != null) {
            String street = first(site, "street"), city = first(site, "city");            String addr = street.isEmpty() ? city : (city.isEmpty() ? street : street + ", " + city);
            if (!addr.isEmpty()) lines.add("📍 מיקום: " + addr);
            if (!first(site, "name").isEmpty()) lines.add("    תחנה: " + first(site, "name"));
            if (!first(site, "address_description").isEmpty()) lines.add("    " + first(site, "address_description"));
        }
        JSONArray cats = sale.optJSONArray("categories");
        if (cats != null && cats.length() > 0) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < cats.length(); i++) { String lbl = labelOf(cats.opt(i)); if (!lbl.isEmpty()) names.add(lbl); }
            lines.add("🗂️ קטגוריות: " + TextUtils.join(", ", names));
        }
        String msg = cleanHtml(first(sale, "special_msg_txt"));
        if (!msg.isEmpty()) { lines.add(""); lines.add("📢 הודעה:"); lines.add(msg); }
        if (parent != null && first(parent, "week").isEmpty() && lines.isEmpty()) lines.add("אין פרטים נוספים זמינים.");

        String title = saleTitle(sale);
        appDialog()
                .setTitle(title.isEmpty() ? "פרטי המכירה" : title)
                .setMessage(TextUtils.join("\n", lines))
                .setPositiveButton("סגירה", null)
                .setNegativeButton("כניסה להזמנה", (d, w) -> loadFeatureProducts(sale))
                .show();
    }

    private void clearPendingStockNavigation() {
        prefs.edit().remove(PREF_PENDING_STOCK_SALE_ID).remove(PREF_PENDING_STOCK_SID).apply();
    }

    /** Opens the sale linked to a restock notification, then scrolls to its real product card. */
    private void openSaleFromStockNotification(int saleId, String productSid) {
        if (saleId <= 0 || productSid == null || productSid.trim().isEmpty()) {
            clearPendingStockNavigation();
            showOpenSales(false);
            return;
        }
        prefs.edit().putInt(PREF_PENDING_STOCK_SALE_ID, saleId)
                .putString(PREF_PENDING_STOCK_SID, productSid).remove(PREF_PENDING_NOTIFICATION_SALE_ID).apply();
        beginScreen("פתיחת המוצר שחזר למלאי…", 0, null);
        setStatus("טוען את המכירה ואת פרטי המוצר…");
        final int t = screenToken;
        executor.execute(() -> {
            try {
                HttpResult r = api("GET", "/api/profile/open_sales", null);
                if (r.code < 200 || r.code >= 300) throw new Exception("לא ניתן לטעון את המכירה. נסו שוב.");
                JSONArray sales = arrayFrom(r.body);
                JSONObject found = null;
                for (int i = 0; i < sales.length(); i++) {
                    JSONObject sale = sales.optJSONObject(i);
                    if (sale != null && sale.optInt("id", -1) == saleId) { found = sale; break; }
                }
                final JSONObject selected = found;
                runOnUiThread(() -> {
                    if (stale(t)) return;
                    if (selected != null) {
                        loadFeatureProducts(selected);
                    } else {
                        clearPendingStockNavigation();
                        showOpenSales(false);
                        toast("המכירה כבר אינה פתוחה, ולכן לא ניתן לפתוח את כרטיס המוצר. מציג את המכירות הפתוחות.", true);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (e instanceof AuthException) { showLogin(); return; }
                    // Keep the target for a retry; never silently redirect to a different product.
                    showOpenSales(false);
                    toast(errText(e, "טעינת המוצר נכשלה. אפשר לנסות שוב מההתראה הבאה."), true);
                });
            }
        });
    }

    private void openSaleFromNotification(int saleId) {
        if (saleId < 0) { showOpenSales(false); return; }
        beginScreen("פתיחת המכירה החדשה…", 0, null);
        setStatus("טוען את המכירה מההתראה…");
        final int t = screenToken;
        executor.execute(() -> {
            try {
                HttpResult r = api("GET", "/api/profile/open_sales", null);
                if (r.code < 200 || r.code >= 300) throw new Exception("לא ניתן לטעון את המכירה. פתחו את המכירות הפתוחות ורענן.");
                JSONArray sales = arrayFrom(r.body);
                JSONObject found = null;
                for (int i = 0; i < sales.length(); i++) {
                    JSONObject sale = sales.optJSONObject(i);
                    if (sale != null && sale.optInt("id", -1) == saleId) { found = sale; break; }
                }
                final JSONObject selected = found;
                runOnUiThread(() -> {
                    if (selected != null) {
                        prefs.edit().remove(PREF_PENDING_NOTIFICATION_SALE_ID).apply();
                        loadFeatureProducts(selected);
                    } else {
                        prefs.edit().remove(PREF_PENDING_NOTIFICATION_SALE_ID).apply();
                        showOpenSales(false);
                        toast("המכירה כבר לא פתוחה. מציג את המכירות העדכניות.", true);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (e instanceof AuthException) { showLogin(); return; }
                    prefs.edit().remove(PREF_PENDING_NOTIFICATION_SALE_ID).apply();
                    showOpenSales(false);
                    toast(errText(e, "טעינת המכירה נכשלה."), true);
                });
            }
        });
    }

    // ======================================================================== order creation / loading

    private long extractOrderId(JSONObject obj) {
        if (obj == null) return -1;
        long id = obj.optLong("id", obj.optLong("order_id", obj.optLong("orderID", -1)));
        JSONObject nested = obj.optJSONObject("order");
        if (id <= 0 && nested != null) id = nested.optLong("id", nested.optLong("order_id", -1));
        JSONObject data = obj.optJSONObject("data");
        if (id <= 0 && data != null && data != obj) id = extractOrderId(data);
        return id;
    }

    private long findOrderIdForSale(int saleId) {
        try {
            HttpResult r = api("GET", "/api/orders", null);
            if (r.code < 200 || r.code >= 300) return -1;
            JSONArray orders = arrayFrom(r.body);
            for (int i = 0; i < orders.length(); i++) {
                JSONObject o = orders.optJSONObject(i);
                if (o == null) continue;
                int sid = o.optInt("saleID", o.optInt("sale_id", -2));
                JSONObject osale = o.optJSONObject("sale");
                if (sid < 0 && osale != null) sid = osale.optInt("id", -2);
                if (sid == saleId) {
                    long id = extractOrderId(o);
                    if (id > 0) return id;
                }
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private long findOrCreateOrder(JSONObject sale) throws Exception {
        long existingId = extractOrderId(sale.optJSONObject("order"));
        if (existingId > 0) return existingId;
        int saleId = sale.optInt("id", -1);
        if (saleId <= 0) throw new Exception("מזהה המכירה חסר.");

        long mapped = findOrderIdForSale(saleId);
        if (mapped > 0) return mapped;

        HttpResult cardsResponse = api("GET", "/api/payment_methods/cards", null);
        if (cardsResponse.code < 200 || cardsResponse.code >= 300)
            throw new Exception("לא ניתן לטעון את אמצעי התשלום השמורים (" + cardsResponse.code + ").");
        JSONArray cards = arrayFrom(cardsResponse.body);
        long cardId = -1;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject c = cards.optJSONObject(i);
            if (c != null && (truthy(c.opt("is_default")) || truthy(c.opt("default")))) {
                cardId = c.optLong("id", c.optLong("card_id", -1));
                if (cardId > 0) break;
            }
        }
        if (cardId <= 0 && cards.length() > 0) {
            JSONObject c = cards.optJSONObject(0);
            if (c != null) cardId = c.optLong("id", c.optLong("card_id", -1));
        }
        if (cardId <= 0) throw new Exception("לא נמצא כרטיס אשראי שמור. הגדירו כרטיס באתר משנת יוסף ונסו שוב.");

        JSONObject body = new JSONObject();
        body.put("card_id", cardId);
        HttpResult created = api("POST", "/api/create_order/" + saleId, body);
        if (created.code < 200 || created.code >= 300)
            throw new Exception("פתיחת ההזמנה נכשלה: " + apiError(created));
        try {
            long id = extractOrderId(new JSONObject(created.body));
            if (id > 0) return id;
        } catch (Exception ignored) { }
        Thread.sleep(500);
        mapped = findOrderIdForSale(saleId);
        if (mapped > 0) return mapped;
        throw new Exception("השרת אישר פתיחת הזמנה אך לא החזיר מזהה. בדקו בהיסטוריית ההזמנות לפני ניסיון חוזר.");
    }

    private List<JSONObject> toObjects(JSONArray a) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }

    private String itemsPath(long id, int page, int limit) {
        return "/api/get_items_for_sale/" + id + "/null?page=" + page + "&limit=" + limit;
    }

    /** First page on this thread (so the screen can start at once), the remaining pages in parallel. */
    private int fetchPagesFor(final long id, PageCallback cb) throws Exception {
        int limit = PAGE_LIMIT;
        HttpResult first = api("GET", itemsPath(id, 1, limit), null);
        if (first.code < 200 || first.code >= 300) {
            limit = 50;
            first = api("GET", itemsPath(id, 1, limit), null);
        }
        if (first.code < 200 || first.code >= 300) return 0;
        JSONArray firstArray = arrayFrom(first.body);
        if (firstArray.length() == 0) return 0;
        int lastPage = 1, total = 0;
        try {
            JSONObject o = new JSONObject(first.body);
            lastPage = Math.max(1, o.optInt("last_page", 1));
            total = o.optInt("total", 0);
        } catch (Exception ignored) { }
        List<JSONObject> firstItems = toObjects(firstArray);
        cb.onPage(firstItems, total);
        int count = firstItems.size();
        final int lim = limit;
        if (lastPage <= 1 && firstArray.length() >= lim) {
            // no pagination info in the answer: keep reading until a short page
            for (int page = 2; page <= 100; page++) {
                HttpResult r = api("GET", itemsPath(id, page, lim), null);
                if (r.code < 200 || r.code >= 300) throw new Exception("טעינת עמוד " + page + " נכשלה");
                List<JSONObject> items = toObjects(arrayFrom(r.body));
                if (items.isEmpty()) break;
                cb.onPage(items, total);
                count += items.size();
                if (items.size() < lim) break;
            }
            return count;
        }
        if (lastPage > 1) {
            ExecutorCompletionService<List<JSONObject>> ecs = new ExecutorCompletionService<>(fetchPool);
            for (int page = 2; page <= lastPage; page++) {
                final int pg = page;
                ecs.submit(new Callable<List<JSONObject>>() {
                    @Override public List<JSONObject> call() throws Exception {
                        Exception last = null;
                        for (int attempt = 0; attempt < 2; attempt++) {
                            try {
                                HttpResult r = api("GET", itemsPath(id, pg, lim), null);
                                if (r.code >= 200 && r.code < 300) return toObjects(arrayFrom(r.body));
                                last = new Exception(apiError(r));
                            } catch (AuthException ae) {
                                throw ae;
                            } catch (Exception e) {
                                last = e;
                            }
                        }
                        throw last == null ? new Exception("page failed") : last;
                    }
                });
            }
            int failures = 0;
            for (int i = 2; i <= lastPage; i++) {
                try {
                    List<JSONObject> items = ecs.take().get();
                    cb.onPage(items, total);
                    count += items.size();
                } catch (ExecutionException ee) {
                    if (ee.getCause() instanceof AuthException) throw (AuthException) ee.getCause();
                    failures++;
                }
            }
            if (failures > 0) throw new Exception("נטענו " + count + " מוצרים, אבל " + failures + " עמודים לא נטענו");
        }
        return count;
    }

    private void fetchProductPages(long orderId, int saleId, PageCallback cb) throws Exception {
        long[] ids = saleId > 0 && saleId != orderId ? new long[]{orderId, saleId} : new long[]{orderId};
        for (long id : ids) if (fetchPagesFor(id, cb) > 0) return;
        throw new Exception("לא נמצאו מוצרים במכירה.");
    }

    private List<JSONObject> fetchAllProducts(long orderId, int saleId) throws Exception {
        final List<JSONObject> all = new ArrayList<>();
        fetchProductPages(orderId, saleId, new PageCallback() {
            @Override public void onPage(List<JSONObject> items, int total) { all.addAll(items); }
        });
        return all;
    }

    /** GET /api/orders/{id}: order.products, without billing lines, same as the Python script. */
    private OrderData fetchOrder(long orderId) throws Exception {
        HttpResult r = api("GET", "/api/orders/" + orderId, null);
        if (r.code < 200 || r.code >= 300) throw new Exception("טעינת ההזמנה נכשלה (" + r.code + ").");
        JSONObject response = new JSONObject(r.body);
        JSONObject order = response.optJSONObject("order");
        if (order == null) order = response.optJSONObject("data");
        if (order == null) order = response;
        JSONObject sale = response.optJSONObject("sale");
        if (sale == null) sale = order.optJSONObject("sale");

        OrderData od = new OrderData();
        od.id = order.optLong("id", orderId);
        od.status = first(order, "status", "order_status");
        od.when = fmtWhen(first(order, "time"));
        od.saleName = saleTitle(sale);
        od.saleId = order.optLong("saleID", sale == null ? 0 : sale.optLong("id", 0));
        if (od.saleName.isEmpty()) od.saleName = "מכירה " + first(order, "saleID", "sale_id");
        if (order.has("total_list") && !order.isNull("total_list")) od.total = num(order.opt("total_list"));
        od.credits = num(order.opt("trans_list"));

        JSONArray products = orderItemsFrom(order);
        Map<String, OrderLine> bySid = new LinkedHashMap<>();
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            if (truthy(p.opt("billing_product"))) continue;
            // API versions may expose the sale-item identifier with either spelling,
            // and some order responses put the catalog item inside "product".
            JSONObject nestedProduct = p.optJSONObject("product");
            String sid = first(p, "item_salesID", "item_sales_id");
            if (sid.isEmpty()) sid = first(nestedProduct, "item_salesID", "item_sales_id", "itemID", "id");
            if (sid.isEmpty()) continue;
            double amount = num(p.opt("amount"));
            OrderLine line = bySid.get(sid);
            if (line == null) {
                line = new OrderLine();
                line.sid = sid;
                line.raw = p;
                String name = first(p, "full_name", "name", "item_name", "title");
                if (name.isEmpty()) name = first(nestedProduct, "full_name", "name", "item_name", "title");
                line.name = name.isEmpty() ? "(פריט " + sid + ")" : name;
                line.price = productPrice(p);
                bySid.put(sid, line);
            }
            line.amount += amount;
        }
        od.lines.addAll(bySid.values());
        return od;
    }

    private JSONArray orderItemsFrom(JSONObject order) {
        if (order == null) return new JSONArray();
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

    // ---- sale snapshots: memory first, then disk, then network (the screen never waits for all of them)

    private File snapshotFile(int saleId) {
        File d = new File(getFilesDir(), "cache");
        if (!d.exists()) d.mkdirs();
        return new File(d, "sale_" + saleId + ".json");
    }

    private void persistQuantities() {
        if (featureOrderId <= 0) return;
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Integer> e : featureQuantities.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) o.put(e.getKey(), e.getValue());
            }
            prefs.edit().putString("qty_" + featureOrderId, o.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void writeSnapshot(int saleId, long orderId, List<JSONObject> products, Set<String> favs, long savedAt) {
        try {
            JSONObject o = new JSONObject();
            o.put("order_id", orderId);
            o.put("saved_at", savedAt);
            JSONArray arr = new JSONArray();
            for (JSONObject p : products) arr.put(p);
            o.put("products", arr);
            JSONArray fa = new JSONArray();
            for (String id : favs) fa.put(id);
            o.put("favs", fa);
            File f = snapshotFile(saleId);
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) { os.write(o.toString().getBytes(StandardCharsets.UTF_8)); }
            if (!tmp.renameTo(f)) tmp.delete();
        } catch (Exception ignored) { }
    }

    private SaleSnapshot readSnapshot(int saleId) {
        try {
            File f = snapshotFile(saleId);
            if (!f.exists() || f.length() == 0) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (InputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            }
            JSONObject o = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            JSONArray arr = o.optJSONArray("products");
            if (arr == null || arr.length() == 0) return null;
            SaleSnapshot snap = new SaleSnapshot();
            snap.orderId = o.optLong("order_id", -1);
            snap.loadedAt = o.optLong("saved_at", 0L);
            snap.saleName = o.optString("sale_name", "").trim();
            JSONObject qty = null;
            try { qty = new JSONObject(prefs.getString("qty_" + snap.orderId, "")); } catch (Exception ignored) { }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p == null) continue;
                String sid = sidOf(p);
                if (sid.isEmpty()) continue;
                int q = qty != null ? qty.optInt(sid, 0) : Math.max(0, (int) Math.round(num(p.opt("amount"))));
                try { p.put("amount", q); } catch (Exception ignored) { }
                snap.products.add(p);
                snap.quantities.put(sid, q);
            }
            JSONArray fa = o.optJSONArray("favs");
            if (fa != null) for (int i = 0; i < fa.length(); i++) snap.favs.add(String.valueOf(fa.opt(i)));
            return snap.products.isEmpty() ? null : snap;
        } catch (Exception e) {
            return null;
        }
    }

    private void snapshotCurrent() {
        if (currentSaleId <= 0 || featureOrderId <= 0 || featureProducts.isEmpty() || productsPartial) return;
        SaleSnapshot snap = new SaleSnapshot();
        snap.orderId = featureOrderId;
        snap.loadedAt = productsLoadedAt;
        snap.products.addAll(featureProducts);
        snap.quantities.putAll(featureQuantities);
        snap.favs.addAll(favIds);
        saleSnapshots.put(currentSaleId, snap);
    }

    private void applySnapshot(SaleSnapshot snap, int saleId) {
        featureOrderId = snap.orderId;
        currentSaleId = saleId;
        productsLoadedAt = snap.loadedAt;
        productsPartial = false;
        pending.clear();
        featureProducts.clear();
        featureProducts.addAll(snap.products);
        featureQuantities.clear();
        featureQuantities.putAll(snap.quantities);
        favIds.clear();
        favIds.addAll(snap.favs);
    }

    private boolean orderNoticeDialogVisible = false;
    private boolean orderNoticePending = false;

    /** Version-scoped preference: each new app version can show this notice once again. */
    private String orderNoticeVersionKey() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return String.valueOf(info.versionName) + ":" + code;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "unknown";
        }
    }

    /** Show only after the order screen has been laid out; never tie this to product refreshes. */
    private void showOrderEntryNoticeIfNeeded() {
        if (prefs == null || orderNoticeDialogVisible || screenKind != 1 || isFinishing()) return;
        final String versionKey = orderNoticeVersionKey();
        if (versionKey.equals(prefs.getString("skip_order_notice_version", ""))) return;

        CheckBox dontShowAgain = new CheckBox(this);
        dontShowAgain.setText("אל תציג הודעה זו שוב");
        dontShowAgain.setTextSize(14);
        dontShowAgain.setTextColor(INK);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView explanation = text("שימו לב: לחיצה על הפלוס, המינוס או על «הוספה» מבצעת את הפעולה מיד מול השרת. הכמות מתעדכנת וההזמנה נוצרת או משתנה מיד — גם בלי ללחוץ על שמירה ובלי לבצע פעולה נוספת.", 15, false);
        explanation.setSingleLine(false);
        explanation.setLineSpacing(dp(2), 1f);
        panel.addView(explanation, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(-1, -2);
        checkLp.topMargin = dp(10);
        panel.addView(dontShowAgain, checkLp);

        AlertDialog dialog = appDialog()
                .setTitle("עדכון כמויות בהזמנה")
                .setView(panel)
                .setPositiveButton("הבנתי", null)
                .create();
        dialog.setOnDismissListener(d -> {
            if (dontShowAgain.isChecked() && prefs != null) {
                prefs.edit().putString("skip_order_notice_version", versionKey).apply();
            }
            orderNoticeDialogVisible = false;
        });
        orderNoticeDialogVisible = true;
        dialog.show();
        styleAppDialog(dialog);
    }

    private void loadFeatureProducts(final JSONObject sale) {
        closedOrderReadOnly = false;
        orderNoticePending = true;
        final int saleId = sale.optInt("id", -1);
        if (currentSaleId > 0 && currentSaleId != saleId) snapshotCurrent(); // keep the sale we are leaving
        featureSale = sale;
        summaryFromHistory = false;
        filterText = "";
        viewMode = "all";

        boolean inMemory = currentSaleId == saleId && featureOrderId > 0 && !featureProducts.isEmpty() && !productsPartial;
        if (!inMemory) {
            SaleSnapshot snap = saleSnapshots.get(saleId);
            if (snap != null) {
                applySnapshot(snap, saleId);
                inMemory = true;
            } else {
                featureProducts.clear();
                featureQuantities.clear();
                favIds.clear();
                pending.clear();
                featureOrderId = -1;
                currentSaleId = -1;
                productsPartial = false;
            }
        }
        renderFeatureProducts(); // paints at once from memory (empty when nothing is cached yet)
        final int t = screenToken;
        if (inMemory) {
            // Prefer a fresh server result for restock navigation; stale cached data can have a different order or availability.
            if (System.currentTimeMillis() - productsLoadedAt > STALE_MS) {
                refreshProducts(sale, true);
            } else {
                navigateToPendingStockProduct(saleId);
            }
            return;
        }
        productsLoading = true;
        setStatus("טוען מוצרים…");
        loadExecutor.execute(() -> {
            final SaleSnapshot disk = readSnapshot(saleId);
            if (disk != null) {
                runOnUiThread(() -> {
                    if (!stale(t) && featureSale != null && featureSale.optInt("id", -1) == saleId && featureProducts.isEmpty()) {
                        applySnapshot(disk, saleId);
                        productsLoading = false;
                        setStatus("");
                        rebuildGrid();
                        updateOrderBar();
                    }
                    refreshProducts(sale, true);
                });
            } else {
                refreshProducts(sale, false);
            }
        });
    }

    /** Loads the sale from the server. Pages arrive in parallel and appear on screen as they come. */
    private void refreshProducts(final JSONObject sale, final boolean silent) {
        final int t = screenToken;
        final int saleId = sale.optInt("id", -1);
        final boolean hadData = !featureProducts.isEmpty();
        final boolean showPartial = !hadData;
        final long knownOrder = (currentSaleId == saleId && featureOrderId > 0) ? featureOrderId : -1;
        if (!silent || !hadData) {
            productsLoading = true;
            setStatus(hadData ? "מרענן את המוצרים…" : "טוען מוצרים…");
        }
        loadExecutor.execute(() -> {
            try {
                final long orderId = knownOrder > 0 ? knownOrder : findOrCreateOrder(sale);
                Future<OrderData> orderFuture = fetchPool.submit(new Callable<OrderData>() {
                    @Override public OrderData call() throws Exception { return fetchOrder(orderId); }
                });
                Future<Set<String>> favFuture = fetchPool.submit(new Callable<Set<String>>() {
                    @Override public Set<String> call() throws Exception { return fetchFavouriteIds(saleId); }
                });
                final List<JSONObject> all = new ArrayList<>();
                fetchProductPages(orderId, saleId, new PageCallback() {
                    @Override public void onPage(List<JSONObject> items, int total) {
                        all.addAll(items);
                        if (showPartial) {
                            final List<JSONObject> copy = new ArrayList<>(all);
                            final int tot = total;
                            runOnUiThread(() -> showPartialProducts(t, saleId, orderId, copy, tot));
                        }
                    }
                });
                OrderData od = null;
                Set<String> favs = null;
                try { od = orderFuture.get(); } catch (ExecutionException ee) { if (ee.getCause() instanceof AuthException) throw (AuthException) ee.getCause(); }
                try { favs = favFuture.get(); } catch (ExecutionException ee) { if (ee.getCause() instanceof AuthException) throw (AuthException) ee.getCause(); }
                final OrderData fod = od;
                final Set<String> ffavs = favs;
                runOnUiThread(() -> finishProducts(t, saleId, orderId, all, fod, ffavs, silent));
            } catch (Exception e) {
                runOnUiThread(() -> productsLoadFailed(t, e));
            }
        });
    }

    private void showPartialProducts(int t, int saleId, long orderId, List<JSONObject> partial, int total) {
        if (stale(t) || featureSale == null || featureSale.optInt("id", -1) != saleId || summaryFromHistory) return;
        productsPartial = true;
        featureOrderId = orderId;
        featureProducts.clear();
        for (JSONObject p : partial) {
            String sid = sidOf(p);
            if (sid.isEmpty()) continue;
            featureProducts.add(p);
            if (!featureQuantities.containsKey(sid)) featureQuantities.put(sid, Math.max(0, (int) Math.round(num(p.opt("amount")))));
        }
        setStatus("טוען מוצרים… " + featureProducts.size() + (total > 0 ? " מתוך " + total : ""));
        rebuildGrid();
        updateOrderBar();
    }

    private void finishProducts(int t, int saleId, long orderId, List<JSONObject> all, OrderData od, Set<String> favs, boolean silent) {
        Map<String, Integer> serverQty = null;
        if (od != null) {
            serverQty = new LinkedHashMap<>();
            for (OrderLine line : od.lines) serverQty.put(line.sid, (int) Math.round(line.amount));
        }
        List<JSONObject> clean = new ArrayList<>();
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (JSONObject p : all) {
            String sid = sidOf(p);
            if (sid.isEmpty() || productName(p).isEmpty()) continue;
            int amount = serverQty != null ? (serverQty.containsKey(sid) ? serverQty.get(sid) : 0) : Math.max(0, (int) Math.round(num(p.opt("amount"))));
            try { p.put("amount", amount); } catch (Exception ignored) { }
            clean.add(p);
            quantities.put(sid, amount);
        }
        final long now = System.currentTimeMillis();
        final boolean live = featureSale != null && featureSale.optInt("id", -1) == saleId && !summaryFromHistory;
        Set<String> favSet = new HashSet<String>();
        if (favs != null) favSet.addAll(favs);
        else if (live) favSet.addAll(favIds);
        SaleSnapshot snap = new SaleSnapshot();
        snap.orderId = orderId;
        snap.loadedAt = now;
        snap.products.addAll(clean);
        snap.quantities.putAll(quantities);
        snap.favs.addAll(favSet);
        saleSnapshots.put(saleId, snap);
        if (live) {
            Map<String, Integer> keep = new HashMap<>(); // quantities the user is changing right now
            for (String sid : pending) if (featureQuantities.containsKey(sid)) keep.put(sid, featureQuantities.get(sid));
            featureOrderId = orderId;
            currentSaleId = saleId;
            productsLoadedAt = now;
            productsPartial = false;
            featureProducts.clear();
            featureProducts.addAll(clean);
            featureQuantities.clear();
            featureQuantities.putAll(quantities);
            featureQuantities.putAll(keep);
            if (favs != null) { favIds.clear(); favIds.addAll(favs); }
            if (od != null) { summary = od; orderDirty = false; }
            persistQuantities();
            productsLoading = false;
            if (!stale(t) && screenKind == 1) {
                setStatus("");
                rebuildGrid();
                updateOrderBar();
                navigateToPendingStockProduct(saleId);
                if (!silent) toast("✔ נטענו " + featureProducts.size() + " מוצרים", false);
            }
        }
        final List<JSONObject> toSave = clean;
        final Set<String> favCopy = favSet;
        try { loadExecutor.execute(() -> writeSnapshot(saleId, orderId, toSave, favCopy, now)); } catch (Exception ignored) { }
        prefetchImages(clean);
    }

    private void productsLoadFailed(int t, Exception e) {
        productsLoading = false;
        if (e instanceof AuthException) { showLogin(); toast(e.getMessage(), true); return; }
        if (stale(t)) return;
        String msg = errText(e, "פתיחת ההזמנה נכשלה.");
        boolean have = !featureProducts.isEmpty();
        if (have && !productsPartial) {
            String when = productsLoadedAt > 0 ? " (נשמרו " + new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(productsLoadedAt)) + ")" : "";
            setStatus("⚠ " + msg + " · מוצגים המוצרים השמורים" + when, true);
        } else if (have) {
            setStatus("⚠ " + msg + " · לחצו ↻ לניסיון חוזר", true);
        } else {
            setStatus(msg, true);
        }
    }

    // ======================================================================== product grid

    private double[] orderTotals() {
        int lines = 0, units = 0;
        double total = 0;
        for (JSONObject p : featureProducts) {
            Integer q = featureQuantities.get(sidOf(p));
            if (q != null && q > 0) { lines++; units += q; total += productPrice(p) * q; }
        }
        return new double[]{lines, units, total};
    }

    private boolean orderDirty = false;
    private int orderSyncSeq = 0;
    private Runnable orderSyncTask;

    /** The same figures "my order" shows: server lines and server total once known, an estimate while a change is in flight. */
    private double[] orderBarStats() {
        if (closedOrderReadOnly) {
            double[] t = orderTotals();
            return new double[]{t[0], t[1], t[2], 1};
        }
        if (summary != null && summary.id == featureOrderId && !orderDirty && pending.isEmpty()) {
            int lines = 0, units = 0;
            double local = 0;
            for (OrderLine line : summary.lines) {
                int q = lineQty(line);
                if (q > 0) { lines++; units += q; local += line.price * q; }
            }
            boolean server = summary.total >= 0;
            return new double[]{lines, units, server ? summary.total : local, server ? 0 : 1};
        }
        double[] t = orderTotals();
        return new double[]{t[0], t[1], t[2], 1};
    }

    private void updateOrderBar() {
        if (barInfo == null || barTotal == null) return;
        double[] t = orderBarStats();
        String price = (t[3] > 0 ? "≈ " : "") + money(t[2]);
        barInfo.setText((int) t[0] + " פריטים · " + price);
        barTotal.setText("סה״כ " + price);
    }

    /** After a local change the numbers are estimates until the order is read from the server again. */
    private void scheduleOrderSync() {
        orderDirty = true;
        updateOrderBar();
        final int seq = ++orderSyncSeq;
        if (orderSyncTask != null) ui.removeCallbacks(orderSyncTask);
        orderSyncTask = () -> syncOrderNow(seq);
        ui.postDelayed(orderSyncTask, 500);
    }

    private void syncOrderNow(final int seq) {
        final long orderId = featureOrderId;
        if (orderId <= 0) return;
        executor.execute(() -> {
            try {
                final OrderData od = fetchOrder(orderId);
                runOnUiThread(() -> {
                    if (seq != orderSyncSeq || featureOrderId != orderId) return; // a newer change is on its way
                    if (!pending.isEmpty()) { ui.postDelayed(() -> syncOrderNow(seq), 400); return; }
                    syncOrderDataFromServer(od);
                    if (screenKind == 2) { rebuildSummary(); updateSummaryTotals(); }
                    else refreshVisibleProductRows();
                });
            } catch (Exception ignored) { }
        });
    }

    private void renderFeatureProducts() {
        String title = saleTitle(featureSale);
        Runnable productsBack;
        if (closedOrderReadOnly) productsBack = () -> showCachedOrders();
        else if (summaryFromHistory) productsBack = () -> showOrderSummary();
        else productsBack = () -> showOpenSales(false);
        beginScreen(title.isEmpty() ? "הזמנה" : title, summaryFromHistory ? historyTab : 0, productsBack);
        screenKind = 1;
        showingOrderScreen = true;

        // Fixed header: category chips first, then a compact search/order-summary row.
        LinearLayout topBox = new LinearLayout(this);
        topBox.setOrientation(LinearLayout.VERTICAL);

        catStripScroll = new HorizontalScrollView(this);
        catStripScroll.setTag("orderCategoryStrip");
        catStripScroll.setFocusable(false);
        catStripScroll.setHorizontalScrollBarEnabled(false);
        catStripScroll.setBackgroundColor(SURFACE);
        catStripScroll.setPadding(dp(8), dp(5), dp(8), dp(5));
        catStripRow = new LinearLayout(this);
        catStripRow.setGravity(Gravity.CENTER_VERTICAL);
        catStripScroll.addView(catStripRow);
        topBox.addView(catStripScroll, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setTag("orderTopToolbar");
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(5), dp(8), dp(5));

        EditText search = field("חיפוש מוצר…", InputType.TYPE_CLASS_TEXT);
        activeProductSearch = search;
        search.setTag("orderToolbarSearch");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setText(filterText);
        search.setTextSize(14);
        search.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        search.setPadding(dp(9), 0, dp(9), 0);
        search.setHint("חיפוש מוצר…");
        // The search field shares the row with a compact clear button.
        final LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        search.setOnClickListener(v -> {
            search.requestFocus();
            search.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            });
        });
        TextView clearSearch = text("×", 22, true);
        clearSearch.setGravity(Gravity.CENTER);
        clearSearch.setTextColor(MUTED);
        clearSearch.setBackground(ripple(shape(SURFACE, ACTION_BUTTON_RADIUS_DP, LINE), ACTION_BUTTON_RADIUS_DP));
        clearSearch.setContentDescription("מחיקת החיפוש");
        clearSearch.setVisibility(filterText.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams clearSearchLp = new LinearLayout.LayoutParams(dp(34), dp(36));
        clearSearchLp.setMarginStart(dp(3));
        clearSearch.setOnClickListener(v -> {
            search.setText("");
            filterText = "";
            search.requestFocus();
            search.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            });
        });
        clearSearch.setTag("orderToolbarClearSearch");
        clearSearch.setFocusable(true);
        productSearchClearButton = clearSearch;
        final Runnable[] debounce = new Runnable[1];
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                clearSearch.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                if (debounce[0] != null) ui.removeCallbacks(debounce[0]);
                final String q = s.toString().trim().toLowerCase(Locale.ROOT);
                debounce[0] = () -> { filterText = q; rebuildGrid(true); };
                ui.postDelayed(debounce[0], 250);
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> { hideKeyboard(); search.clearFocus(); return true; });
        toolbar.addView(search, searchLp);
        toolbar.addView(clearSearch, clearSearchLp);

        // One wide "my order" button; the order details (items and price) are part of the button itself.
        final TextView orderTitle = text("ההזמנה שלי  ‹", 12, true);
        orderTitle.setTextColor(ON_ORANGE);
        orderTitle.setSingleLine(true);
        orderTitle.setGravity(Gravity.CENTER);
        orderTitle.setPadding(0, 0, 0, 0);
        barInfo = text("", 10.5f, false);
        barInfo.setTextColor(ON_ORANGE);
        barInfo.setSingleLine(true);
        barInfo.setEllipsize(TextUtils.TruncateAt.END);
        barInfo.setGravity(Gravity.CENTER);
        barInfo.setPadding(0, 0, 0, 0);
        barTotal = text("", 10, false); // full "total" text, hidden; kept for accessibility
        barTotal.setVisibility(View.GONE);
        final LinearLayout orderBtn = new LinearLayout(this);
        orderBtn.setOrientation(LinearLayout.VERTICAL);
        orderBtn.setGravity(Gravity.CENTER);
        orderBtn.setClickable(true);
        orderBtn.setFocusable(true);
        orderBtn.setFocusableInTouchMode(false);
        orderSummaryButton = orderBtn;
        orderBtn.setTag("orderToolbarSummary");
        orderBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
        orderBtn.setBackground(ripple(shape(ORANGE, ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
        orderBtn.addView(orderTitle, new LinearLayout.LayoutParams(-1, -2));
        orderBtn.addView(barInfo, new LinearLayout.LayoutParams(-1, -2));
        orderBtn.addView(barTotal, new LinearLayout.LayoutParams(0, 0));
        orderBtn.setOnClickListener(v -> { if (closedOrderReadOnly) showCachedClosedOrderSummary(); else showOrderSummary(); });
        LinearLayout.LayoutParams orderBtnLp = new LinearLayout.LayoutParams(dp(172), dp(44));
        orderBtnLp.setMarginStart(dp(3));
        toolbar.addView(orderBtn, orderBtnLp);

        // While typing in the search field the button steps aside so the field can use the whole row.
        search.setOnFocusChangeListener((v, hasFocus) -> {
            orderBtn.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
            searchLp.width = 0;
            searchLp.weight = 1f;
            search.setLayoutParams(searchLp);
        });

        TextView optionsBtn = roundBtn("⋯", 32, ORANGE_LIGHT, ORANGE_DARK);
        optionsBtn.setTag("orderToolbarOptions");
        optionsBtn.setFocusable(true);
        optionsBtn.setContentDescription("אפשרויות נוספות");
        optionsBtn.setOnClickListener(v -> showOrderQuickMenu(optionsBtn));
        optionsBtn.setContentDescription("אפשרויות נוספות");
        LinearLayout.LayoutParams optionsLp = new LinearLayout.LayoutParams(dp(32), dp(40));
        optionsLp.setMarginStart(dp(3));
        toolbar.addView(optionsBtn, optionsLp);

        statusView = muted("", 12); // loading/offline state stays in the fixed header, below search
        statusView.setPadding(dp(12), dp(1), dp(12), dp(3));
        statusView.setVisibility(View.GONE);
        topBox.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        topBox.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
        topHolder.addView(topBox);

        // the product list: only the rows on screen exist as views, and they are recycled while scrolling
        productList = new ListView(this);
        productList.setItemsCanFocus(true);
        productList.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        productList.setContentDescription("רשימת מוצרי ההזמנה");
        productList.setDivider(null);
        productList.setDividerHeight(0);
        productList.setSelector(new ColorDrawable(Color.TRANSPARENT));
        productList.setCacheColorHint(Color.TRANSPARENT);
        productList.setClipToPadding(false);
        productList.setPadding(dp(11), dp(4), dp(11), dp(12));
        productAdapter = new ProductAdapter();
        productList.setAdapter(productAdapter);
        FrameLayout productArea = new FrameLayout(this);
        final View[] floatingTopButtonRef = new View[1];
        productList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) { }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                updateActiveCategory(first);
                if (floatingTopButtonRef[0] != null) floatingTopButtonRef[0].setVisibility(first > 0 ? View.VISIBLE : View.GONE);
            }
        });
        productArea.addView(productList, new FrameLayout.LayoutParams(-1, -1));
        // Enlarge the entire floating jump-to-top control by 50%, and scale its arrow too.
        View floatingTopButton = new View(this) {
            private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                arrowPaint.setColor(ON_ORANGE);
                arrowPaint.setStyle(Paint.Style.STROKE);
                arrowPaint.setStrokeWidth(dp(4.5f));
                arrowPaint.setStrokeCap(Paint.Cap.ROUND);
                arrowPaint.setStrokeJoin(Paint.Join.ROUND);
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f - dp(1.5f);
                Path arrow = new Path();
                arrow.moveTo(cx, cy + dp(12));
                arrow.lineTo(cx, cy - dp(9));
                arrow.moveTo(cx - dp(10.5f), cy + dp(1.5f));
                arrow.lineTo(cx, cy - dp(10.5f));
                arrow.lineTo(cx + dp(10.5f), cy + dp(1.5f));
                canvas.drawPath(arrow, arrowPaint);
            }
        };
        floatingTopButtonRef[0] = floatingTopButton;
        floatingTopButton.setContentDescription("חזרה מיידית לראש הרשימה");
        floatingTopButton.setBackground(ripple(shape(ORANGE, 30, ORANGE_DARK), 30));
        floatingTopButton.setElevation(dp(8));
        floatingTopButton.setVisibility(View.GONE);
        floatingTopButton.setOnClickListener(v -> {
            hideKeyboard();
            productList.setSelection(0);
            productList.setSelectionFromTop(0, 0);
        });
        // Keep a clear gap from the screen edge and avoid pinning the control in the left corner.
        FrameLayout.LayoutParams floatingTopLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.BOTTOM | Gravity.LEFT);
        floatingTopLp.setMargins(dp(16), 0, 0, dp(18));
        productArea.addView(floatingTopButton, floatingTopLp);
        ViewGroup parent = (ViewGroup) scrollView.getParent();
        int at = parent.indexOfChild(scrollView);
        parent.removeViewAt(at);
        parent.addView(productArea, at, new LinearLayout.LayoutParams(-1, 0, 1));

        // Order summary now lives beside search in the fixed header, so no duplicate bottom bar.
        bottomHolder.setVisibility(View.GONE);

        rebuildGrid(true);
        updateOrderBar();
        if (productsLoading) setStatus("טוען מוצרים…");
        if (orderNoticePending) {
            orderNoticePending = false;
            final int noticeScreenToken = screenToken;
            topHolder.post(() -> {
                if (screenToken != noticeScreenToken || screenKind != 1 || isFinishing()) return;
                if (Build.VERSION.SDK_INT < 17 || !isDestroyed()) showOrderEntryNoticeIfNeeded();
            });
        }
        // Restore the actual product ListView row and its pixel offset, not the detached ScrollView.
        final ListView listToRestore = productList;
        final int restorePosition = orderListFirstVisiblePosition;
        final int restoreTop = orderListTopOffset;
        if (listToRestore != null && (restorePosition > 0 || restoreTop != 0)) {
            listToRestore.post(() -> {
                if (showingOrderScreen && screenKind == 1 && productList == listToRestore) {
                    listToRestore.setSelectionFromTop(restorePosition, restoreTop);
                }
            });
        }
        returnToOrderScreenOnSalesTab = false;

    }

    private void rebuildGrid() { rebuildGrid(false); }

    /** Scrolls to the requested stock-restock product only after its real card is in the adapter. */
    private void navigateToPendingStockProduct(int saleId) {
        if (prefs == null || productList == null || productAdapter == null || featureSale == null) return;
        int targetSaleId = prefs.getInt(PREF_PENDING_STOCK_SALE_ID, -1);
        String targetSid = prefs.getString(PREF_PENDING_STOCK_SID, "");
        if (targetSaleId != saleId || targetSid.isEmpty() || currentSaleId != saleId
                || featureSale.optInt("id", -1) != saleId || productsPartial || productsLoading) return;
        // Ensure the target remains visible even if the user's sold-out filter would otherwise hide it.
        int targetPosition = -1;
        for (int i = 0; i < gridRows.size(); i++) {
            GridRow row = gridRows.get(i);
            if (row.header != null) continue;
            if (rowContainsProduct(row, targetSid)) { targetPosition = i; break; }
        }
        if (targetPosition < 0) {            // A target absent from fresh product data must not send the user to a random card.
            clearPendingStockNavigation();
            toast("המוצר כבר לא מופיע במכירה. הצגתי את ההזמנה כדי שתוכל לבדוק את המוצרים.", true);
            return;
        }
        final int position = targetPosition;
        final String name = productName(findProductBySid(targetSid));
        productList.post(() -> {
            if (productList == null || productAdapter == null || currentSaleId != saleId) return;
            productList.setSelectionFromTop(position, dp(12));
            productList.smoothScrollToPosition(position);
            clearPendingStockNavigation();
            toast("📍 הגעת לכרטיס: " + (name.isEmpty() ? "המוצר שחזר למלאי" : name), false);
        });
    }

    private boolean rowContainsProduct(GridRow row, String sid) {
        return row != null && (sid.equals(sidOf(row.a)) || sid.equals(sidOf(row.b))
                || sid.equals(sidOf(row.c)) || sid.equals(sidOf(row.d)) || sid.equals(sidOf(row.e)));
    }

    private JSONObject findProductBySid(String sid) {
        for (JSONObject product : featureProducts) if (sid.equals(sidOf(product))) return product;
        return null;
    }

    private void rebuildGrid(boolean toTop) {
        if (productAdapter == null) return;
        gridRows.clear();
        catHeaders.clear();
        catCounts.clear();

        Map<String, List<JSONObject>> byCat = new LinkedHashMap<>();
        final Map<String, Integer> catOrder = new LinkedHashMap<>();
        for (JSONObject p : featureProducts) {
            String name = productName(p);
            if (!filterText.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(filterText)) continue;
            boolean isPendingStockTarget = prefs != null
                    && prefs.getInt(PREF_PENDING_STOCK_SALE_ID, -1) == currentSaleId
                    && sidOf(p).equals(prefs.getString(PREF_PENDING_STOCK_SID, ""));
            if (hideSoldOut() && !separateSoldOutCategory() && isSoldOut(p) && !isPendingStockTarget) { Integer inCart = featureQuantities.get(sidOf(p)); if (inCart == null || inCart <= 0) continue; }
            if ("fav".equals(viewMode) && !favIds.contains(pidOf(p))) continue;
            if ("cart".equals(viewMode)) { Integer qq = featureQuantities.get(sidOf(p)); if (qq == null || qq <= 0) continue; }
            String cat = separateSoldOutCategory() && isSoldOut(p) ? "פריטים שאזלו" : categoryName(p);
            int order = "פריטים שאזלו".equals(cat) ? 10000 : categoryOrder(p);
            if (!byCat.containsKey(cat)) { byCat.put(cat, new ArrayList<JSONObject>()); catOrder.put(cat, order); }
            else catOrder.put(cat, Math.min(catOrder.get(cat), order));
            byCat.get(cat).add(p);
        }
        List<String> cats = new ArrayList<>(byCat.keySet());
        Collections.sort(cats, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int c = Integer.compare(catOrder.get(a), catOrder.get(b));
                return c != 0 ? c : a.compareTo(b);
            }
        });
        for (String cat : cats) {
            List<JSONObject> items = byCat.get(cat);
            Collections.sort(items, new Comparator<JSONObject>() {
                @Override public int compare(JSONObject a, JSONObject b) { return productName(a).compareTo(productName(b)); }
            });
            GridRow header = new GridRow();
            header.header = cat + "  (" + items.size() + ")";
            catHeaders.put(cat, gridRows.size());
            catCounts.put(cat, items.size());
            gridRows.add(header);
            int columns = cardColumns();
            for (int i = 0; i < items.size(); i += columns) {
                GridRow row = new GridRow();
                row.a = items.get(i);
                row.b = columns >= 2 && i + 1 < items.size() ? items.get(i + 1) : null;
                row.c = columns >= 3 && i + 2 < items.size() ? items.get(i + 2) : null;
                row.d = columns >= 4 && i + 3 < items.size() ? items.get(i + 3) : null;
                row.e = columns >= 5 && i + 4 < items.size() ? items.get(i + 4) : null;
                gridRows.add(row);
            }
        }
        if (gridRows.isEmpty() && !productsLoading) {
            String emptyMsg = !filterText.isEmpty() ? "לא נמצאו מוצרים התואמים לחיפוש."
                    : ("fav".equals(viewMode) ? "אין מועדפים במכירה הזו. סמנו כוכב על מוצר." : ("cart".equals(viewMode) ? "ההזמנה ריקה." : "אין מוצרים להצגה."));
            setStatus(emptyMsg);
            emptyMsgShown = true;
        } else if (emptyMsgShown && !gridRows.isEmpty()) {
            setStatus("");
            emptyMsgShown = false;
        }
        refillCategoryStrip(cats);
        productAdapter.notifyDataSetChanged();
        if (toTop && productList != null) productList.setSelection(0);
    }

    private void paintButton(TextView b, int style) {
        int fill, fg, stroke = 0;
        switch (style) {
            case PRIMARY: fill = ORANGE; fg = ON_ORANGE; break;
            case TONAL: fill = ORANGE_LIGHT; fg = ORANGE_DARK; break;
            case OUTLINE: fill = SURFACE; fg = ORANGE_DARK; stroke = ORANGE; break;
            case DANGER: fill = RED_LIGHT; fg = RED; break;
            default: fill = Color.TRANSPARENT; fg = ORANGE_DARK; break;
        }
        styleActionButton(b, fill, fg, stroke);
    }

    /** One product card. Created once, then re-bound while the list scrolls. */
    private class CardHolder {
        final LinearLayout card = new LinearLayout(MainActivity.this);
        final ImageView image;
        final FrameLayout imageBox;
        final FrameLayout controls;
        final TextView badge, star, name, pack, price, maxView, savedView, unitView, add;
        final View actionSpacer;
        final Stepper st;
        final TextView chargeButton;
        final LinearLayout autoOrderPanel;
        final Stepper autoOrderStepper;
        final TextView autoOrderSaveButton;
        final TextView stockAlertButton;
        JSONObject p;
        String boundUrl = "";
        private final Map<View, Drawable> normalFocusBackgrounds = new HashMap<>();
        private final Map<TextView, android.content.res.ColorStateList> normalFocusTextColors = new HashMap<>();

        CardHolder() {
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(shape(SURFACE, 14, LINE));
            card.setClickable(true);
            card.setFocusable(true);
            card.setFocusableInTouchMode(false);
            card.setOnFocusChangeListener((view, focused) -> updateFocusVisuals());
            card.setPadding(dp(4), dp(4), dp(4), dp(6));

            imageBox = new FrameLayout(MainActivity.this);
            image = new ImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(SURFACE);
            image.setClickable(true);
            image.setFocusable(false);
            image.setOnClickListener(v -> {
                Object full = image.getContentDescription();
                String previewUrl = full instanceof CharSequence ? full.toString() : "";
                if (previewUrl.isEmpty() && image.getTag() instanceof String) previewUrl = (String) image.getTag();
                showImagePreview(previewUrl);
            });
            imageBox.addView(image, new FrameLayout.LayoutParams(-1, dp(112)));
            badge = text("", 11, true);
            badge.setTextColor(ON_ORANGE);
            badge.setBackground(shape(RED, 8, 0));
            badge.setPadding(dp(8), dp(2), dp(8), dp(2));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2);
            bp.gravity = Gravity.TOP | Gravity.END;
            bp.setMargins(dp(6), dp(6), dp(6), 0);
            imageBox.addView(badge, bp);
            star = text("☆", 20, true);
            star.setGravity(Gravity.CENTER);
            star.setPadding(0, 0, 0, 0);
            star.setTextColor(ORANGE_DARK);
            star.setBackground(shape(STAR_BG, 16, 0));
            FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(dp(32), dp(32));
            starLp.gravity = Gravity.TOP | Gravity.START;
            starLp.setMargins(dp(6), dp(6), 0, 0);
            imageBox.addView(star, starLp);
            card.addView(imageBox, new LinearLayout.LayoutParams(-1, dp(112)));

            name = text("", 12, true);
            // Short names use their actual height instead of reserving blank title lines.
            name.setMinLines(1);
            name.setMaxLines(3);
            name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name, lp(-1, -2, 2, 0));
            pack = muted("", 10);
            pack.setSingleLine(true);
            pack.setEllipsize(TextUtils.TruncateAt.END);
            unitView = muted("", 9);
            unitView.setSingleLine(true);
            unitView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout detailsRow = new LinearLayout(MainActivity.this);
            detailsRow.setOrientation(LinearLayout.HORIZONTAL);
            detailsRow.setGravity(Gravity.CENTER_VERTICAL);
            detailsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            detailsRow.addView(pack, new LinearLayout.LayoutParams(0, -2, 1f));
            detailsRow.addView(unitView, new LinearLayout.LayoutParams(-2, -2));
            card.addView(detailsRow, new LinearLayout.LayoutParams(-1, -2));
            maxView = text("", 10, true);
            maxView.setTextColor(ORANGE_DARK);
            card.addView(maxView);
            savedView = muted("", 9);
            card.addView(savedView);

            // Equalization space stays above the price, grouping price with the bottom order controls.
            actionSpacer = new View(MainActivity.this);
            actionSpacer.setFocusable(false);
            card.addView(actionSpacer, new LinearLayout.LayoutParams(-1, 0, 1f));
            price = text("", 14, true);
            price.setTextColor(ORANGE_DARK);
            price.setGravity(Gravity.CENTER);
            card.addView(price, lp(-1, -2, 0, 0));

            controls = new FrameLayout(MainActivity.this);
            add = btn("הוספה", TONAL);
            add.setTextSize(14);
            add.setMinHeight(dp(34));
            add.setPadding(dp(3), 0, dp(3), 0);
            st = stepper(32);
            st.qty.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(32)));
            controls.addView(add, new FrameLayout.LayoutParams(-1, dp(34)));
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, dp(34));
            sp.gravity = Gravity.CENTER;
            controls.addView(st.view, sp);
            card.addView(controls, lp(-1, dp(34), 6, 0));
            chargeButton = smallChip("הוסף לחיובים", PRIMARY);
            chargeButton.setTextSize(12);
            chargeButton.setOnClickListener(v -> { if (closedOrderReadOnly && p != null) addCachedProductCharge(p); });
            card.addView(chargeButton, lp(-1, dp(34), 5, 0));
            autoOrderPanel = new LinearLayout(MainActivity.this);
            autoOrderPanel.setOrientation(LinearLayout.VERTICAL); autoOrderPanel.setPadding(dp(5),dp(5),dp(5),dp(5));
            autoOrderPanel.setBackground(shape(ORANGE_LIGHT,10,ORANGE)); autoOrderPanel.setVisibility(View.GONE);
            LinearLayout autoRow=new LinearLayout(MainActivity.this);autoRow.setGravity(Gravity.CENTER);
            autoOrderStepper=stepper(32);autoOrderStepper.qty.setLayoutParams(new LinearLayout.LayoutParams(dp(34),dp(32)));
            autoRow.addView(autoOrderStepper.view,new LinearLayout.LayoutParams(-2,dp(34)));
            autoOrderPanel.addView(autoRow,lp(-1,dp(36),0,4));
            autoOrderSaveButton=btn("שמירה להזמנה אוטומטית",PRIMARY);
            autoOrderSaveButton.setTextSize(12);
            autoOrderPanel.addView(autoOrderSaveButton,lp(-1,dp(32),0,4));
            card.addView(autoOrderPanel,lp(-1,-2,5,0));
            stockAlertButton=btn("🔔 התראה כשהמוצר חוזר למלאי",OUTLINE);
            stockAlertButton.setTextSize(11);
            stockAlertButton.setPadding(dp(8),0,dp(8),0);
            stockAlertButton.setMinHeight(dp(28));
            stockAlertButton.setVisibility(View.GONE);card.addView(stockAlertButton,lp(-1,dp(29),5,0));
            autoOrderStepper.plus.setTag("autoOrderPlus");
            autoOrderStepper.minus.setTag("autoOrderMinus");
            autoOrderStepper.qty.setTag("autoOrderQty");
            autoOrderStepper.qty.setFocusable(true);
            autoOrderStepper.qty.setFocusableInTouchMode(false);
            autoOrderStepper.plus.setFocusable(true);
            autoOrderStepper.plus.setFocusableInTouchMode(false);
            autoOrderStepper.minus.setFocusable(true);
            autoOrderStepper.minus.setFocusableInTouchMode(false);
            autoOrderSaveButton.setTag("autoOrderSave");
            stockAlertButton.setTag("stockAlert");
            autoOrderStepper.plus.setOnClickListener(v->{if(p!=null)adjustStockDraft(p,1,autoOrderStepper);});
            autoOrderStepper.minus.setOnClickListener(v->{if(p!=null)adjustStockDraft(p,-1,autoOrderStepper);});
            autoOrderSaveButton.setOnClickListener(v->{if(p!=null)saveAutoOrder(p,stockDraftQty(p));});
            stockAlertButton.setOnClickListener(v->{if(p!=null)saveStockAlert(p);});

            card.setOnClickListener(v -> { if (p != null) showProductDetails(p); });
            imageBox.setOnClickListener(v -> { if (p != null) showProductDetails(p); });
            star.setOnClickListener(v -> { if (p != null) toggleFavourite(p, () -> { if ("fav".equals(viewMode)) rebuildGrid(); else productsRefresh.run(); }); });
            add.setTag("productAdd");
            st.plus.setTag("productPlus");
            st.minus.setTag("productMinus");
            st.qty.setTag("productQty");
            // Explicitly enable hardware-key focus; do not rely on theme/widget defaults.
            card.setFocusable(true);
            card.setFocusableInTouchMode(false);
            add.setFocusable(true);
            add.setFocusableInTouchMode(false);
            st.plus.setFocusable(true);
            st.plus.setFocusableInTouchMode(false);
            st.minus.setFocusable(true);
            st.minus.setFocusableInTouchMode(false);
            st.qty.setFocusable(true);
            st.qty.setFocusableInTouchMode(false);
            chargeButton.setTag("productCharge");
            chargeButton.setFocusable(true);
            chargeButton.setFocusableInTouchMode(false);
            autoOrderSaveButton.setFocusable(true);
            autoOrderSaveButton.setFocusableInTouchMode(false);
            stockAlertButton.setFocusable(true);
            stockAlertButton.setFocusableInTouchMode(false);
            card.setTag("productCard:" + sidOf(p));
            add.setOnClickListener(v -> { if (!closedOrderReadOnly && p != null && !truthy(p.opt("disable")) && !isSoldOut(p)) changeBy(p, 1, productsRefresh, null); });
            st.plus.setOnClickListener(v -> {
                if (p == null) return;
                if (closedOrderReadOnly) changeCachedChargeQuantity(p, 1);
                else changeBy(p, 1, productsRefresh, null);
            });
            st.minus.setOnClickListener(v -> {
                if (p == null) return;
                if (closedOrderReadOnly) changeCachedChargeQuantity(p, -1);
                else changeBy(p, -1, productsRefresh, null);
            });
            st.qty.setOnClickListener(v -> { if (!closedOrderReadOnly && p != null) askQuantity(p, productsRefresh, null); });

            View.OnFocusChangeListener focusListener = (view, focused) -> refreshVisibleProductFocusVisuals();
            for (View focusView : new View[]{card, add, st.plus, st.qty, st.minus, chargeButton,
                    autoOrderStepper.plus, autoOrderStepper.qty, autoOrderStepper.minus,
                    autoOrderSaveButton, stockAlertButton}) focusView.setOnFocusChangeListener(focusListener);
        }

        private void rememberNormalFocusBackgrounds() {
            for (View focusView : new View[]{add, st.plus, st.qty, st.minus, chargeButton,
                    autoOrderStepper.plus, autoOrderStepper.qty, autoOrderStepper.minus,
                    autoOrderSaveButton, stockAlertButton}) {
                normalFocusBackgrounds.put(focusView, focusView.getBackground());
                if (focusView instanceof TextView) {
                    normalFocusTextColors.put((TextView) focusView, ((TextView) focusView).getTextColors());
                }
            }
        }

        private void updateFocusVisuals() {
            card.setBackground(shape(SURFACE, 14, card.hasFocus() ? ORANGE : LINE, card.hasFocus() ? 2 : 1));
            View focusedChild = null;
            for (View focusView : new View[]{add, st.plus, st.qty, st.minus, chargeButton,
                    autoOrderStepper.plus, autoOrderStepper.qty, autoOrderStepper.minus,
                    autoOrderSaveButton, stockAlertButton}) {
                if (focusView.hasFocus()) focusedChild = focusView;
                else {
                    Drawable normal = normalFocusBackgrounds.get(focusView);
                    if (normal != null && focusView.getBackground() != normal) focusView.setBackground(normal);
                    if (focusView instanceof TextView) {
                        android.content.res.ColorStateList normalText = normalFocusTextColors.get((TextView) focusView);
                        if (normalText != null && !((TextView) focusView).getTextColors().equals(normalText)) {
                            ((TextView) focusView).setTextColor(normalText);
                        }
                    }
                    focusView.setScaleX(1f); focusView.setScaleY(1f); focusView.setElevation(0f);
                }
            }
            if (focusedChild != null) {
                int fill = SURFACE, foreground = INK, radius = ACTION_BUTTON_RADIUS_DP;
                if (focusedChild == st.plus || focusedChild == autoOrderStepper.plus) { fill = ORANGE; foreground = ON_ORANGE; radius = 99; }
                else if (focusedChild == st.minus || focusedChild == autoOrderStepper.minus) { fill = MINUS_FILL; foreground = ORANGE_DARK; radius = 99; }
                else if (focusedChild == st.qty || focusedChild == autoOrderStepper.qty) { fill = nightMode ? SURFACE : ORANGE_LIGHT; foreground = INK; radius = 8; }
                else if (focusedChild == add) { fill = ORANGE_LIGHT; foreground = ORANGE_DARK; }
                else if (focusedChild == chargeButton || focusedChild == autoOrderSaveButton) { fill = ORANGE; foreground = ON_ORANGE; }
                else if (focusedChild == stockAlertButton) { fill = SURFACE; foreground = ORANGE_DARK; }
                focusedChild.setBackground(ripple(shape(fill, radius, nightMode ? Color.WHITE : INK, 2), radius));
                if (focusedChild instanceof TextView) ((TextView) focusedChild).setTextColor(foreground);
                focusedChild.setScaleX(1.04f); focusedChild.setScaleY(1.04f); focusedChild.setElevation(dp(3));
            }
        }

        private void setCompactOptionalRow(TextView view, boolean visible) {
            // Preserve assigned width: unitView shares a horizontal row with package text.
            ViewGroup.LayoutParams current = view.getLayoutParams();
            int width = current == null ? -1 : current.width;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    width, visible ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(3));
            view.setLayoutParams(params);
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }

        void adaptCardSize() {
            int columns = cardColumns();
            float scale = columns == 1 ? 1.18f : columns == 2 ? 1.08f : columns == 3 ? 1f : columns == 4 ? 0.84f : 0.72f;
            int imageHeight = columns == 1 ? 176 : columns == 2 ? 144 : columns == 3 ? 112 : columns == 4 ? 84 : 66;
            int controlHeight = columns == 1 ? 42 : columns == 2 ? 38 : columns == 3 ? 34 : columns == 4 ? 30 : 27;
            card.setPadding(dp(columns >= 4 ? 2 : 4), dp(columns >= 4 ? 2 : 4), dp(columns >= 4 ? 2 : 4), dp(columns >= 4 ? 3 : 6));
            imageBox.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(imageHeight)));
            image.setLayoutParams(new FrameLayout.LayoutParams(-1, dp(imageHeight)));
            name.setTextSize(12 * scale);
            int nameLines = columns >= 4 ? 2 : 3;
            name.setMinLines(1);
            name.setMaxLines(nameLines);
            pack.setTextSize(10 * scale);
            price.setTextSize(14 * scale);
            maxView.setTextSize(10 * scale);
            savedView.setTextSize(9 * scale);
            unitView.setTextSize(9 * scale);
            badge.setTextSize(11 * scale);
            badge.setPadding(dp(columns >= 4 ? 2 : 8), dp(1), dp(columns >= 4 ? 2 : 8), dp(1));
            star.setTextSize(20 * scale);
            star.setLayoutParams(new FrameLayout.LayoutParams(dp(columns >= 4 ? 22 : 32), dp(columns >= 4 ? 22 : 32), Gravity.TOP | Gravity.START));
            add.setTextSize(14 * scale);
            add.setMinHeight(dp(controlHeight));
            st.qty.setTextSize(16 * scale);
            st.qty.setLayoutParams(new LinearLayout.LayoutParams(dp(columns >= 4 ? 18 : 28), dp(controlHeight)));
            st.minus.setLayoutParams(new LinearLayout.LayoutParams(dp(controlHeight), dp(controlHeight)));
            st.plus.setLayoutParams(new LinearLayout.LayoutParams(dp(controlHeight), dp(controlHeight)));
            controls.setLayoutParams(lp(-1, dp(controlHeight), columns >= 4 ? 2 : 6, 0));
            add.setLayoutParams(new FrameLayout.LayoutParams(-1, dp(controlHeight)));
            st.view.setLayoutParams(new FrameLayout.LayoutParams(-2, dp(controlHeight), Gravity.CENTER));
        }

        void bind(JSONObject prod) {
            for (Map.Entry<View, Drawable> entry : normalFocusBackgrounds.entrySet()) {
                if (entry.getValue() != null) entry.getKey().setBackground(entry.getValue());
            }
            p = prod;
            card.setTag("productCard:" + sidOf(prod));
            card.setContentDescription(productName(prod) + ". כרטיס מוצר");
            adaptCardSize();
            String sid = sidOf(prod), pid = pidOf(prod);
            boolean disabled = truthy(prod.opt("disable")), soldOut = isSoldOut(prod), canOrder = !disabled && !soldOut;
            String url = imageUrl(prod, !fullImages());
            if (!url.equals(boundUrl)) {
                boundUrl = url;
                image.setImageDrawable(null);
                loadImage(image, url);
            }
            // Keep the full-size URL even when the card itself uses a thumbnail.
            image.setContentDescription(imageUrl(prod, false));
            image.setAlpha(canOrder ? 1f : 0.45f);
            badge.setVisibility(canOrder ? View.GONE : View.VISIBLE);
            badge.setText(disabled ? "לא זמין" : "אזל");
            star.setText(favIds.contains(pid) ? "⭐" : "☆");
            star.setVisibility(View.VISIBLE);
            name.setText(productName(prod));
            String packText = packageText(prod);
            pack.setText(packText);
            pack.setVisibility(packText.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            double pr = productPrice(prod);
            price.setText(pr > 0 ? money(pr) : "—");
            int mx = maxPerMan(prod);
            maxView.setText("מקסימום " + mx);
            setCompactOptionalRow(maxView, showMax() && mx > 0);
            int savedQ = favSavedQty(pid);
            savedView.setText("📌 כמות שמורה ×" + savedQ);
            setCompactOptionalRow(savedView, savedQ > 0 && favIds.contains(pid));
            String up = unitPriceText(prod);
            unitView.setText(up);
            setCompactOptionalRow(unitView, !up.isEmpty());

            add.setText(canOrder ? "הוספה" : (disabled ? "לא זמין" : "אזל מהמלאי"));
            paintButton(add, canOrder ? TONAL : FLAT);
            int q = featureQuantities.containsKey(sid) ? featureQuantities.get(sid) : 0;
            boolean busyNow = pending.contains(sid);
            add.setVisibility(closedOrderReadOnly || q > 0 ? View.GONE : View.VISIBLE);
            st.view.setVisibility(closedOrderReadOnly || q > 0 ? View.VISIBLE : View.GONE);
            st.qty.setText(String.valueOf(q));
            st.plus.setVisibility(View.VISIBLE);
            st.minus.setVisibility(View.VISIBLE);
            st.qty.setClickable(!closedOrderReadOnly);
            // Keep controls enabled during requests; repeated taps are queued in changeBy().
            boolean dimNow = !closedOrderReadOnly && busyNow;
            st.view.setAlpha(dimNow ? 0.6f : 1f);
            st.plus.setEnabled(true);
            st.minus.setEnabled(true);
            st.plus.setAlpha(dimNow ? 0.6f : 1f);
            st.minus.setAlpha(dimNow ? 0.6f : 1f);
            add.setEnabled(!closedOrderReadOnly && canOrder);
            add.setAlpha(add.isEnabled() && !busyNow ? 1f : 0.45f);
            chargeButton.setVisibility(closedOrderReadOnly ? View.VISIBLE : View.GONE);
            setEnabledLook(chargeButton, closedOrderReadOnly);
            boolean localStockAction=!closedOrderReadOnly&&(disabled||soldOut);
            controls.setVisibility(localStockAction?View.GONE:View.VISIBLE);
            autoOrderPanel.setVisibility(localStockAction?View.VISIBLE:View.GONE);
            stockAlertButton.setVisibility(localStockAction?View.VISIBLE:View.GONE);
            if(localStockAction){
                autoOrderStepper.qty.setText(String.valueOf(stockDraftQty(prod)));
                autoOrderSaveButton.setText(stockWatchMode(prod,"auto")?"✓ מנוהל בהזמנה אוטומטית":"שמירה להזמנה אוטומטית");
                stockAlertButton.setText(stockWatchMode(prod,"alert")?"🔔 ההתראה פעילה · ניהול":"🔔 התראה כשהמוצר חוזר למלאי");
                autoOrderPanel.setBackground(shape(disabled&&!soldOut?RED_LIGHT:ORANGE_LIGHT,10,disabled&&!soldOut?RED:ORANGE));
            }
            rememberNormalFocusBackgrounds();
            updateFocusVisuals();
        }
    }

    private class RowHolder {
        final LinearLayout row = new LinearLayout(MainActivity.this);
        final CardHolder first = new CardHolder();
        final CardHolder second = new CardHolder();
        final CardHolder third = new CardHolder();
        final CardHolder fourth = new CardHolder();
        final CardHolder fifth = new CardHolder();
        int boundPosition = -1;
        int boundColumns = -1;
        int bindGeneration = 0;

        void resetCardHeights() {
            for (CardHolder holder : new CardHolder[]{first, second, third, fourth, fifth}) {
                ViewGroup.LayoutParams params = holder.card.getLayoutParams();
                if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    holder.card.setLayoutParams(params);
                }
            }
        }

        void equalizeCardHeights(int expectedPosition, int expectedColumns, int expectedGeneration) {
            if (boundPosition != expectedPosition || bindGeneration != expectedGeneration
                    || cardColumns() != expectedColumns || row.getTag() != this) return;
            CardHolder[] holders = {first, second, third, fourth, fifth};
            int maxHeight = 0;
            for (CardHolder holder : holders) {
                if (holder.card.getVisibility() == View.VISIBLE) {
                    maxHeight = Math.max(maxHeight, holder.card.getMeasuredHeight());
                }
            }
            if (maxHeight <= 0) return;
            for (CardHolder holder : holders) {
                if (holder.card.getVisibility() != View.VISIBLE) continue;
                ViewGroup.LayoutParams params = holder.card.getLayoutParams();
                if (params != null && params.height != maxHeight) {
                    params.height = maxHeight;
                    holder.card.setLayoutParams(params);
                }
            }
        }

        RowHolder() {
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, dp(8));
            List<View> cells = new ArrayList<>();
            for (CardHolder h : new CardHolder[]{first, second, third, fourth, fifth}) {
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
                cp.setMargins(dp(2), 0, dp(2), 0);
                h.card.setLayoutParams(cp);
                cells.add(h.card);
            }
            addVisualRtl(row, cells);
        }
    }

    private class ProductAdapter extends BaseAdapter {
        private final Set<RowHolder> cachedRows = new HashSet<>();

        void rethemeCachedRows(PaletteSnapshot old, Set<View> visited) {
            for (RowHolder holder : cachedRows) rethemeViewTree(holder.row, old, visited);
        }

        @Override public int getCount() { return gridRows.size(); }
        @Override public Object getItem(int i) { return gridRows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return gridRows.get(i).header != null ? 0 : 1; }

        @Override public View getView(int position, View convert, ViewGroup parent) {
            GridRow r = gridRows.get(position);
            if (r.header != null) {
                TextView tv = convert instanceof TextView ? (TextView) convert : null;
                if (tv == null) {
                    tv = text("", 16, true);
                    tv.setTextColor(ORANGE_DARK);
                    tv.setContentDescription("כותרת קטגוריית מוצרים");
                    tv.setPadding(dp(4), dp(12), dp(4), dp(4));
                }
                tv.setText(r.header);
                tv.setTextColor(ORANGE_DARK);
                return tv;
            }
            RowHolder h;
            if (convert != null && convert.getTag() instanceof RowHolder) {
                h = (RowHolder) convert.getTag();
            } else {
                h = new RowHolder();
                h.row.setTag(h);
                cachedRows.add(h);
            }
            int columns = cardColumns();
            boolean rowIdentityChanged = h.boundPosition != position || h.boundColumns != columns;
            h.boundPosition = position;
            h.boundColumns = columns;
            int bindGeneration = ++h.bindGeneration;
            // Reset equalized heights only when a row is recycled or the column count changes.
            if (rowIdentityChanged) h.resetCardHeights();
            h.first.card.setVisibility(View.VISIBLE);
            h.first.bind(r.a);
            CardHolder[] holders = {h.second, h.third, h.fourth, h.fifth};
            JSONObject[] products = {r.b, r.c, r.d, r.e};
            for (int i = 0; i < holders.length; i++) {
                if (i + 1 >= columns) {
                    // These cells are outside the selected grid; remove them from layout width.
                    holders[i].card.setVisibility(View.GONE);
                } else if (products[i] != null) {
                    holders[i].card.setVisibility(View.VISIBLE);
                    holders[i].bind(products[i]);
                } else {
                    // Preserve only unused slots within the selected column count.
                    holders[i].card.setVisibility(View.INVISIBLE);
                }
            }
            // One post is enough to measure the laid-out row and avoids an extra focus-sensitive frame.
            h.row.post(() -> h.equalizeCardHeights(position, columns, bindGeneration));
            return h.row;
        }
    }

    // Repeated taps made while the previous request for the same product is still running (fast +,+,+).
    private final Map<String, Integer> queuedDeltas = new LinkedHashMap<>();

    private void changeBy(JSONObject p, int delta, Runnable refresh, Runnable onSuccess) {
        String sid = sidOf(p);
        if (pending.contains(sid)) {
            Integer queued = queuedDeltas.get(sid);
            queuedDeltas.put(sid, (queued == null ? 0 : queued) + delta);
            return;
        }
        int current = featureQuantities.containsKey(sid) ? featureQuantities.get(sid) : 0;
        int next = Math.max(0, current + delta);
        if (delta > 0) {
            if (truthy(p.opt("disable")) || isSoldOut(p)) { toast("המוצר אינו זמין כרגע", false); return; }
        }
        setItemAmount(p, next, refresh, onSuccess);
    }

    /** Updates the server (add / update / remove) and only that one row in the UI, without reloading the screen. */
    private void setItemAmount(final JSONObject p, final int newQty, final Runnable refresh, final Runnable onSuccess) {
        requireUnlock(() -> doSetItemAmount(p, newQty, refresh, onSuccess));
    }

    private void doSetItemAmount(final JSONObject p, final int newQty, final Runnable refresh, final Runnable onSuccess) {
        final String sid = sidOf(p);
        if (pending.contains(sid) || featureOrderId <= 0) return;
        final int oldQty = featureQuantities.containsKey(sid) ? featureQuantities.get(sid) : 0;
        if (newQty == oldQty) return;
        final int serverAmount = (int) Math.round(num(p.opt("amount")));
        final long orderId = featureOrderId;
        pending.add(sid);
        featureQuantities.put(sid, newQty);
        final String pname = productName(p);
        toast("⏳ " + (newQty == 0 ? "מסיר" : (oldQty == 0 ? "מוסיף" : "מעדכן")) + ": " + pname, false);
        lastChangedProductSid = sid;
        refresh.run();
        lastChangedProductSid = null;
        executor.execute(() -> {
            String error = null;
            boolean needLogin = false;
            try {
                HttpResult result = null;
                if (newQty == 0) {
                    if (serverAmount > 0) result = api("POST", "/api/remove_item/" + orderId + "/" + sid, null);
                } else {
                    JSONObject item = new JSONObject(p.toString());
                    item.put("amount", newQty);
                    if (!item.has("selected_subitems") || item.isNull("selected_subitems")) item.put("selected_subitems", new JSONArray());
                    result = api("POST", (serverAmount > 0 ? "/api/update_item/" : "/api/add_item/") + orderId, item);
                }
                if (result != null && (result.code < 200 || result.code >= 300)) error = apiError(result);
            } catch (AuthException e) {
                needLogin = true;
                error = e.getMessage();
            } catch (Exception e) {
                error = errText(e, "עדכון ההזמנה נכשל");
            }
            final String err = error;
            final boolean login = needLogin;
            runOnUiThread(() -> {
                pending.remove(sid);
                if (err == null) {
                    try { p.put("amount", newQty); } catch (Exception ignored) { }
                    featureQuantities.put(sid, newQty);
                    persistQuantities();
                    scheduleOrderSync();
                    toast(newQty == 0 ? "✔ הוסר: " + pname : (oldQty == 0 ? "✔ נוסף להזמנה: " + pname + " ×" + newQty : "✔ עודכן: " + pname + " ← " + newQty), false);
                } else {
                    featureQuantities.put(sid, oldQty);
                    if (err.contains("אזל")) { try { p.put("available", 0); } catch (Exception ignored) { } }
                    toast(err, true);
                }
                lastChangedProductSid = sid;
                refresh.run();
                lastChangedProductSid = null;
                Integer queuedDelta = queuedDeltas.remove(sid);
                if (err == null && queuedDelta != null && queuedDelta != 0) changeBy(p, queuedDelta, refresh, onSuccess);
                if (err == null && onSuccess != null) onSuccess.run();
                if (login) showLogin();
            });
        });
    }

    /** Builds the same product information shown in the details card for copy/share. */
    private String productShareText(JSONObject p) {
        if (p == null) return "";
        JSONObject prod = productOf(p);
        JSONObject meta = prod.optJSONObject("metadata");
        JSONObject hechsher = prod.optJSONObject("hechsher");
        StringBuilder out = new StringBuilder(productName(p));
        double price = productPrice(p);
        if (price > 0) out.append("\nמחיר: ").append(money(price));
        String pack = packageText(p);
        if (!pack.isEmpty()) out.append("\nאריזה: ").append(pack);
        String unit = unitPriceText(p);
        if (!unit.isEmpty()) out.append("\n").append(unit);
        if (isSoldOut(p)) out.append("\n⛔ אזל מהמלאי");
        if (showMax() && maxPerMan(p) > 0) out.append("\nמקסימום ").append(maxPerMan(p));

        String[][] blocks = {
                {"תיאור", cleanHtml(first(prod, "description"))},
                {"פרטים נוספים", cleanHtml(first(p, "description", "note", "details", "badge"))},
                {"הכשר", cleanHtml(first(hechsher, "name"))},
                {"רכיבים", metaText(meta, "ingredients")},
                {"אלרגנים", metaText(meta, "allergens")},
                {"עלול להכיל", metaText(meta, "precautionary_statement")},
                {"אחסון", metaText(meta, "storage_instructions")}
        };
        Set<String> seen = new HashSet<>();
        for (String[] block : blocks) {
            String value = block[1] == null ? "" : block[1].trim();
            if (value.isEmpty() || !seen.add(value)) continue;
            out.append("\n\n").append(block[0]).append(": ").append(value);
        }
        return out.toString().trim();
    }

    private void showProductDetails(JSONObject p) {
        if (p == null) return;
        JSONObject prod = productOf(p);
        JSONObject meta = prod.optJSONObject("metadata");
        JSONObject hechsher = prod.optJSONObject("hechsher");

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(8), dp(16), dp(4));
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        String url = imageUrl(p, false);
        if (!url.isEmpty()) {
            ImageView iv = imageView(-1, 200, ImageView.ScaleType.FIT_CENTER);
            panel.addView(iv);
            final String full = url;
            iv.setTag(full);
            iv.setContentDescription(full);
            imageExecutor.execute(() -> {
                Bitmap bmp = downloadBitmap(full, 900);
                if (bmp != null) runOnUiThread(() -> { if (full.equals(iv.getTag())) iv.setImageBitmap(bmp); });
            });
        }
        panel.addView(text(productName(p), 17, true), lp(-1, -2, 8, 0));
        double price = productPrice(p);
        String line = (price > 0 ? money(price) : "") + (packageText(p).isEmpty() ? "" : "  ·  " + packageText(p));
        if (!line.trim().isEmpty()) panel.addView(text(line, 14, true));
        if (!unitPriceText(p).isEmpty()) panel.addView(muted(unitPriceText(p), 12));
        if (isSoldOut(p)) panel.addView(text("⛔ אזל מהמלאי", 13, true));
        if (showMax() && maxPerMan(p) > 0) panel.addView(text("מקסימום " + maxPerMan(p), 13, true));

        String[][] blocks = {
                {"", cleanHtml(first(prod, "description"))},
                {"", cleanHtml(first(p, "description", "note", "details", "badge"))},
                {"הכשר", first(hechsher, "name")},
                {"רכיבים", metaText(meta, "ingredients")},
                {"אלרגנים", metaText(meta, "allergens")},
                {"עלול להכיל", metaText(meta, "precautionary_statement")},
                {"אחסון", metaText(meta, "storage_instructions")},
        };
        Set<String> seen = new HashSet<>();
        for (String[] b : blocks) {
            if (b[1].isEmpty() || !seen.add(b[1])) continue;
            panel.addView(b[0].isEmpty() ? text(b[1], 14, false) : text(b[0] + ": " + b[1], 14, false), lp(-1, -2, 6, 0));
        }
        if (seen.isEmpty() && url.isEmpty()) panel.addView(muted("אין פרטים נוספים זמינים.", 13));

        final String pid = pidOf(p);
        final AlertDialog[] dlg = new AlertDialog[1];

        // Keep all product-card actions in this shared details dialog, so the same
        // controls appear consistently wherever a product can be opened.
        if ((closedOrderReadOnly || featureOrderId > 0) && findProduct(sidOf(p)) != null && !pid.isEmpty()) {
            if (!closedOrderReadOnly && featureOrderId > 0) {
                LinearLayout orderControls = new LinearLayout(this);
                orderControls.setOrientation(LinearLayout.HORIZONTAL);
                orderControls.setGravity(Gravity.CENTER_VERTICAL);
                orderControls.setPadding(dp(8), dp(4), dp(8), dp(4));
                orderControls.setBackground(ripple(shape(SURFACE, 12, ORANGE_LIGHT), 12));

                TextView minusBtn = smallChip("−", TONAL);
                minusBtn.setTextSize(20);
                minusBtn.setContentDescription("הפחתת כמות");
                TextView plusBtn = smallChip("+", PRIMARY);
                plusBtn.setTextSize(20);
                plusBtn.setContentDescription("הגדלת כמות");
                TextView currentQty = text(String.valueOf(featureQuantities.containsKey(sidOf(p))
                        ? featureQuantities.get(sidOf(p)) : 0), 17, true);
                currentQty.setGravity(Gravity.CENTER);
                currentQty.setMinWidth(dp(44));
                currentQty.setContentDescription("כמות בהזמנה");
                LinearLayout qtyLabel = new LinearLayout(this);
                qtyLabel.setOrientation(LinearLayout.VERTICAL);
                qtyLabel.setGravity(Gravity.CENTER);
                qtyLabel.addView(currentQty);

                minusBtn.setOnClickListener(v -> changeBy(p, -1, () -> {
                    Integer q = featureQuantities.get(sidOf(p));
                    currentQty.setText(String.valueOf(q == null ? 0 : q));
                    rebuildGrid();
                }, null));
                plusBtn.setOnClickListener(v -> changeBy(p, 1, () -> {
                    Integer q = featureQuantities.get(sidOf(p));
                    currentQty.setText(String.valueOf(q == null ? 0 : q));
                    rebuildGrid();
                }, null));

                orderControls.addView(minusBtn, new LinearLayout.LayoutParams(dp(44), dp(38)));
                LinearLayout.LayoutParams qtyLp = new LinearLayout.LayoutParams(0, dp(38), 1);
                qtyLp.setMargins(dp(8), 0, dp(8), 0);
                orderControls.addView(qtyLabel, qtyLp);
                orderControls.addView(plusBtn, new LinearLayout.LayoutParams(dp(44), dp(38)));
                panel.addView(orderControls, lp(-1, -2, 12, 0));
            }

            LinearLayout savedActionsRow = new LinearLayout(this);
            savedActionsRow.setOrientation(LinearLayout.HORIZONTAL);
            savedActionsRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView favBtn = smallChip(favIds.contains(pid) ? "☆ הסרה ממועדפים" : "⭐ הוספה למועדפים", TONAL);
            favBtn.setTextSize(12);
            favBtn.setOnClickListener(v -> { dlg[0].dismiss(); toggleFavourite(p, () -> rebuildGrid()); });
            int sq = favSavedQty(pid);
            TextView qtyBtn = smallChip(sq > 0 ? "📌 כמות שמורה: " + sq : "📌 שמירת כמות", TONAL);
            qtyBtn.setTextSize(12);
            qtyBtn.setOnClickListener(v -> { dlg[0].dismiss(); askSavedQty(p); });
            savedActionsRow.addView(favBtn, new LinearLayout.LayoutParams(0, dp(38), 1));
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, dp(38), 1);
            qlp.setMarginStart(dp(6));
            savedActionsRow.addView(qtyBtn, qlp);
            panel.addView(savedActionsRow, lp(-1, -2, 8, 0));
        }

        LinearLayout shareActionsRow = new LinearLayout(this);
        shareActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        shareActionsRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView copyBtn = smallChip("📋 העתקה ללוח", TONAL);
        copyBtn.setTextSize(12);
        copyBtn.setOnClickListener(v -> copyText(productShareText(p)));
        TextView shareBtn = smallChip("📤 שיתוף", TONAL);
        shareBtn.setTextSize(12);
        shareBtn.setOnClickListener(v -> shareText(productName(p), productShareText(p)));
        shareActionsRow.addView(copyBtn, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0, dp(38), 1);
        shareLp.setMarginStart(dp(6));
        shareActionsRow.addView(shareBtn, shareLp);
        panel.addView(shareActionsRow, lp(-1, -2, 8, 0));

        ScrollView sv = new ScrollView(this);
        sv.addView(panel);
        dlg[0] = appDialog().setView(sv).setPositiveButton("סגירה", null).create();
        dlg[0].show();
        styleAppDialog(dlg[0]);
    }

    private String metaText(JSONObject meta, String key) {
        if (meta == null) return "";
        Object v = meta.opt(key);
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) { String s = labelOf(a.opt(i)).trim(); if (!s.isEmpty()) parts.add(s); }
            return TextUtils.join(", ", parts);
        }
        return cleanHtml(String.valueOf(v));
    }

    // ======================================================================== order summary (like the order page: images, quantities, remove, collect mode)

    private JSONObject findProduct(String sid) {
        for (JSONObject p : featureProducts) if (sid.equals(sidOf(p))) return p;
        return null;
    }

    private JSONObject productForLine(OrderLine line) {
        JSONObject known = findProduct(line.sid);
        if (known != null) return known;
        JSONObject stub = new JSONObject();
        try {
            stub.put("item_salesID", line.sid);
            stub.put("name", line.name);
            stub.put("amount", (int) Math.round(line.amount));
        } catch (Exception ignored) { }
        return stub;
    }

    private int lineQty(OrderLine line) {
        Integer q = featureQuantities.get(line.sid);
        return q != null ? q : (int) Math.round(line.amount);
    }

    private Set<String> collectedSet() {
        return new HashSet<>(prefs.getStringSet("collected_" + featureOrderId, new HashSet<String>()));
    }

    private void toggleCollected(String sid) {
        Set<String> set = collectedSet();
        if (!set.remove(sid)) set.add(sid);
        prefs.edit().putStringSet("collected_" + featureOrderId, set).apply();
    }

    private void showOrderSummary() {
        if (featureOrderId <= 0) { toast("לא נמצאה הזמנה", false); return; }
        collectMode = false;
        if (summaryFromHistory) beginScreen("הזמנה #" + featureOrderId, historyTab, this::loadFeatureOrders);
        else beginScreen("ההזמנה שלי", 0, this::renderFeatureProducts);
        setStatus("טוען את ההזמנה מהשרת…");
        final int t = screenToken;
        executor.execute(() -> {
            try {
                OrderData od = fetchOrder(featureOrderId);
                runOnUiThread(() -> {
                    if (stale(t)) return;
                    summary = od;
                    syncOrderDataFromServer(od);
                    renderOrderSummary();
                });
            } catch (Exception e) {
                fail(t, e, "טעינת סיכום ההזמנה נכשלה.");
            }
        });
    }

    private void renderOrderSummary() {
        screenKind = 2;
        setStatus("");
        LinearLayout head = card(14);
        head.addView(text(summary.saleName, 17, true));
        String meta = "הזמנה #" + summary.id + (summary.when.isEmpty() ? "" : " · הוזמן: " + summary.when);
        head.addView(muted(meta, 12));
        if (summaryFromHistory && !summary.status.isEmpty()) head.addView(muted("סטטוס: " + summary.status, 12));
        progressView = text("", 13, true);
        progressView.setTextColor(GREEN);
        head.addView(progressView);
        root.addView(head, lp(-1, -2, 0, 10));

        // Mode switch: hide the irrelevant Edit button while collecting.
        LinearLayout modes = new LinearLayout(this);
        final TextView edit = btn("✏️ עריכה", PRIMARY);
        final TextView collect = btn(collectMode ? "✓ סיום איסוף" : "☑ מצב איסוף", collectMode ? PRIMARY : TONAL);
        edit.setVisibility(collectMode ? View.GONE : View.VISIBLE);
        edit.setOnClickListener(v -> { collectMode = false; rebuildSummary(); updateModeButtons(edit, collect); });
        collect.setOnClickListener(v -> { collectMode = !collectMode; if (collectMode) collectionCelebrationShown = false; rebuildSummary(); updateModeButtons(edit, collect); });
        modes.addView(edit, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(44), 1);
        if (!collectMode) cp.setMarginStart(dp(8));
        modes.addView(collect, cp);
        TextView summaryActions = btn("⋯", TONAL);
        summaryActions.setTag("orderSummaryOptions");
        summaryActions.setContentDescription("אפשרויות נוספות להזמנה");
        summaryActions.setOnClickListener(v -> showOrderQuickMenu(summaryActions));
        LinearLayout.LayoutParams sap = new LinearLayout.LayoutParams(dp(52), dp(44));
        sap.setMarginStart(dp(8));
        modes.addView(summaryActions, sap);
        root.addView(modes, lp(-1, -2, 0, 10));

        summaryList = new LinearLayout(this);
        summaryList.setOrientation(LinearLayout.VERTICAL);
        root.addView(summaryList, new LinearLayout.LayoutParams(-1, -2));

        collectedBox = new LinearLayout(this);
        collectedBox.setOrientation(LinearLayout.VERTICAL);
        collectedBox.setBackground(shape(GREEN_LIGHT, 14, GREEN));
        collectedBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout collectedHead = new LinearLayout(this);
        collectedHead.setGravity(Gravity.CENTER_VERTICAL);
        final TextView collectedTitle = text("✔ נאספו", 16, true);
        collectedTitle.setTextColor(GREEN);
        collectedTitle.setTag("collectedTitle");
        collectedHead.addView(collectedTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView reset = smallChip("איפוס", OUTLINE);
        reset.setOnClickListener(v -> {
            prefs.edit().remove("collected_" + featureOrderId).apply();
            rebuildSummary();
        });
        collectedHead.addView(reset);
        collectedBox.addView(collectedHead);
        collectedList = new LinearLayout(this);
        collectedList.setOrientation(LinearLayout.VERTICAL);
        collectedBox.addView(collectedList, lp(-1, -2, 6, 0));
        root.addView(collectedBox, lp(-1, -2, 12, 0));

        // sticky totals
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(SURFACE);
        bar.setElevation(dp(10));
        bar.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        summaryTotal = text("", 17, true);
        summaryTotal.setTextColor(ORANGE_DARK);
        summarySub = muted("", 12);
        texts.addView(summaryTotal);
        texts.addView(summarySub);
        bar.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = btn("+ הוספת מוצרים", TONAL);
        more.setOnClickListener(v -> renderFeatureProducts());
        if (!featureProducts.isEmpty()) bar.addView(more, new LinearLayout.LayoutParams(-2, dp(46)));
        bottomHolder.addView(bar);

        rebuildSummary();
    }

    private void updateModeButtons(TextView edit, TextView collect) {
        edit.setVisibility(collectMode ? View.GONE : View.VISIBLE);
        collect.setText(collectMode ? "✓ סיום איסוף" : "☑ מצב איסוף");
        edit.setBackground(ripple(shape(ORANGE, ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
        edit.setTextColor(ON_ORANGE);
        collect.setBackground(ripple(shape(collectMode ? ORANGE : ORANGE_LIGHT, ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
        collect.setTextColor(collectMode ? ON_ORANGE : ORANGE_DARK);
    }

    private void rebuildSummary() {
        if (summary == null || summaryList == null) return;
        if (collectMode) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final int keepScroll = scrollView == null ? 0 : scrollView.getScrollY();
        summaryList.removeAllViews();
        collectedList.removeAllViews();
        Set<String> collected = collectedSet();
        int shown = 0, done = 0, total = 0;
        for (OrderLine line : summary.lines) {
            int qty = lineQty(line);
            if (qty <= 0) continue;
            total++;
            if (collectMode && collected.contains(line.sid)) {
                done++;
                collectedList.addView(collectRow(line, qty, true), lp(-1, -2, 0, 6));
            } else {
                shown++;
                summaryList.addView(collectMode ? collectRow(line, qty, false) : editRow(line, qty), lp(-1, -2, 0, 8));
            }
        }
        if (total == 0) {
            summaryList.addView(muted("ההזמנה ריקה. חזרו למוצרים כדי להוסיף.", 14), lp(-1, -2, 12, 0));
        }
        collectedBox.setVisibility(collectMode && done > 0 ? View.VISIBLE : View.GONE);
        View title = collectedBox.findViewWithTag("collectedTitle");
        if (title instanceof TextView) ((TextView) title).setText("✔ נאספו (" + done + ")");
        progressView.setVisibility(collectMode ? View.VISIBLE : View.GONE);
        boolean allCollected = collectMode && total > 0 && done == total;
        progressView.setText(allCollected ? "✓ כל הפריטים נאספו! כל הכבוד!" : "נאספו " + done + " מתוך " + total + " פריטים");
        if (allCollected) collectionCelebrationShown = true;
        else collectionCelebrationShown = false;
        updateSummaryTotals();
        if (scrollView != null && keepScroll > 0) scrollView.post(() -> scrollView.scrollTo(0, keepScroll));
    }

    private void updateSummaryTotals() {
        if (summary == null || summaryTotal == null) return;
        int lines = 0, units = 0;
        double local = 0;
        for (OrderLine line : summary.lines) {
            int q = lineQty(line);
            if (q > 0) { lines++; units += q; local += line.price * q; }
        }
        boolean server = summary.total >= 0 && pending.isEmpty();
        summaryTotal.setText("סה״כ " + money(server ? summary.total : local) + (server ? "" : " (משוער)"));
        String sub = lines + " פריטים · " + units + " יחידות";
        if (collectMode) {
            Set<String> col = collectedSet();
            int doneCount = 0;
            for (OrderLine line : summary.lines) if (lineQty(line) > 0 && col.contains(line.sid)) doneCount++;
            sub = "✔ נאספו " + doneCount + " מתוך " + lines + " · " + units + " יחידות";
        }
        if (summary.credits != 0) sub += " · זיכויים " + money(Math.abs(summary.credits));
        summarySub.setText(sub);
    }

    private final Runnable summaryRefresh = new Runnable() {
        @Override public void run() {
            if (summary != null && !pending.isEmpty()) summary.total = -1;
            rebuildSummary();
        }
    };

    /** Treat the server response as authoritative for both the summary list and product-page header. */
    private void syncOrderDataFromServer(OrderData od) {
        if (od == null) return;
        summary = od;
        orderDirty = false;
        featureQuantities.clear();
        for (JSONObject p : featureProducts) {
            try { p.put("amount", 0); } catch (Exception ignored) { }
        }
        for (OrderLine line : od.lines) {
            int qty = Math.max(0, (int) Math.round(line.amount));
            featureQuantities.put(line.sid, qty);
            JSONObject product = findProduct(line.sid);
            if (product != null) {
                try { product.put("amount", qty); } catch (Exception ignored) { }
            }
        }
        persistQuantities();
        updateOrderBar();
    }

    /** Re-fetch all order lines after a successful edit, not just the server total. */
    private void refreshServerTotals() {
        final int t = screenToken;
        final long orderId = featureOrderId;
        executor.execute(() -> {
            try {
                OrderData od = fetchOrder(orderId);
                runOnUiThread(() -> {
                    if (stale(t) || featureOrderId != orderId || summary == null) return;
                    syncOrderDataFromServer(od);
                    if (screenKind == 2) {
                        rebuildSummary();
                        updateSummaryTotals();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!stale(t) && screenKind == 2) {
                        setStatus("⚠ לא ניתן לרענן את ההזמנה מהשרת; הנתונים יוצגו עד לרענון הבא.", true);
                    }
                });
            }
        });
    }

    private View rowShell(OrderLine line, int imageDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(shape(SURFACE, 14, LINE));
        ImageView iv = imageView(imageDp, imageDp, ImageView.ScaleType.FIT_CENTER);
        iv.setBackground(shape(SURFACE, 10, 0));
        iv.setClipToOutline(true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(imageDp), dp(imageDp));        ip.setMarginEnd(dp(10));
        row.addView(iv, ip);
        JSONObject known = findProduct(line.sid);
        JSONObject imageProduct = known != null ? known : line.raw;
        loadImage(iv, imageUrl(imageProduct, !fullImages()));
        iv.setContentDescription(imageUrl(imageProduct, false));
        return row;
    }

    private View editRow(final OrderLine line, final int qty) {
        final JSONObject p = productForLine(line);
        final boolean editable = findProduct(line.sid) != null;
        LinearLayout row = (LinearLayout) rowShell(line, 96);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(line.name, 14, true);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        String pack = packageText(line.raw);
        col.addView(muted((line.price > 0 ? money(line.price) + " ליחידה" : "") + (pack.isEmpty() ? "" : (line.price > 0 ? " · " : "") + pack), 12));
        int mxRow = maxPerMan(p);
        if (showMax() && mxRow > 0) {
            TextView mxView = text("מקסימום " + mxRow, 11, true);
            mxView.setTextColor(ORANGE_DARK);
            col.addView(mxView);
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        if (editable) {
            Stepper st = stepper(36);
            st.qty.setText(String.valueOf(qty));
            boolean busyNow = pending.contains(line.sid);
            setEnabledLook(st.plus, !busyNow);
            setEnabledLook(st.minus, !busyNow && qty > 1);
            st.plus.setOnClickListener(v -> changeBy(p, 1, summaryRefresh, this::refreshServerTotals));
            st.minus.setOnClickListener(v -> { if (qty > 1) changeBy(p, -1, summaryRefresh, this::refreshServerTotals); });
            st.qty.setOnClickListener(v -> askQuantity(p, summaryRefresh, this::refreshServerTotals));
            bottom.addView(st.view);
        } else {
            bottom.addView(text("כמות: " + qty, 14, true));
        }
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView lineTotal = text(line.price > 0 ? money(line.price * qty) : "", 16, true);
        lineTotal.setTextColor(ORANGE_DARK);
        bottom.addView(lineTotal);
        col.addView(bottom, lp(-1, -2, 6, 0));
        if (summaryFromHistory) {
            LinearLayout historyActions = new LinearLayout(this);
            boolean narrowHistoryRow = getResources().getConfiguration().screenWidthDp < 390;
            historyActions.setOrientation(narrowHistoryRow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            TextView copyItem = smallChip("📋 העתקת פריט", TONAL);
            copyItem.setOnClickListener(v -> copyText(historyItemCopyText(line)));
            historyActions.addView(copyItem, lp(-2, dp(34), 0, 0));
            TextView addCredit = smallChip("+ לזיכוי", TONAL);
            addCredit.setOnClickListener(v -> addHistoryItemToCreditList(line));
            LinearLayout.LayoutParams creditLp = new LinearLayout.LayoutParams(-2, dp(34));
            if (narrowHistoryRow) creditLp.topMargin = dp(6);
            else creditLp.setMarginStart(dp(6));
            historyActions.addView(addCredit, creditLp);
            col.addView(historyActions, lp(-1, -2, 0, 6));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView remove = roundBtn("✕", 36, RED_LIGHT, RED);
        remove.setTextSize(15);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(36), dp(36));
        rp.setMarginStart(dp(8));
        remove.setOnClickListener(v -> confirmRemove(line, p));
        row.addView(remove, rp);
        return row;
    }

    /** Format one historical order item for copying, including its sale and unit price. */
    private String historyItemCopyText(OrderLine line) {
        String itemName = line == null || line.name == null ? "" : line.name.trim();
        String saleName = summary == null || summary.saleName == null ? "" : summary.saleName.trim();
        if (saleName.isEmpty()) saleName = "מכירה " + (summary == null ? "" : summary.saleId);
        int quantity = line == null ? 0 : (int) Math.round(line.amount);
        String quantityText = "כמות: " + quantity;
        String totalPrice = line != null && line.price > 0 && quantity > 0
                ? money(line.price * quantity) : "מחיר כולל לא זמין";
        return saleName + " - " + itemName + " - " + quantityText + " - סה״כ: " + totalPrice;
    }

    private void confirmRemove(final OrderLine line, final JSONObject p) {
        appDialog()
                .setTitle("הסרה מההזמנה")
                .setMessage("להסיר את \"" + line.name + "\" מההזמנה?")
                .setNegativeButton("ביטול", null)
                .setPositiveButton("הסרה", (d, w) -> setItemAmount(p, 0, summaryRefresh, this::refreshServerTotals))
                .show();
    }

    private View collectRow(final OrderLine line, final int qty, final boolean isDone) {
        LinearLayout row = (LinearLayout) rowShell(line, isDone ? 60 : 88);
        row.setAlpha(isDone ? 0.8f : 1f);

        TextView check = roundBtn(isDone ? "↩" : "✓", 46, isDone ? SURFACE : GREEN_LIGHT, GREEN);
        LinearLayout.LayoutParams chkLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        chkLp.setMarginEnd(dp(10));
        final View.OnClickListener toggle = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            boolean wasDone = collectedSet().contains(line.sid);
            toggleCollected(line.sid);
            rebuildSummary();
            toast((wasDone ? "↩ הוחזר לרשימה: " : "✔ נאסף: ") + line.name, false);
        };
        check.setOnClickListener(toggle);
        row.setClickable(true);
        row.setOnClickListener(toggle);
        row.addView(check, 0, chkLp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(line.name, isDone ? 14 : 15, true);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        if (isDone) name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        col.addView(name);
        if (!isDone && line.price > 0) col.addView(muted(money(line.price * qty), 12));
        if (summaryFromHistory) {
            LinearLayout historyActions = new LinearLayout(this);
            boolean narrowHistoryRow = getResources().getConfiguration().screenWidthDp < 390;
            historyActions.setOrientation(narrowHistoryRow ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            TextView copyItem = smallChip("📋 העתקת פריט", TONAL);
            copyItem.setOnClickListener(v -> copyText(historyItemCopyText(line)));
            historyActions.addView(copyItem, lp(-2, dp(34), 0, 0));
            TextView addCredit = smallChip("+ לזיכוי", TONAL);
            addCredit.setOnClickListener(v -> addHistoryItemToCreditList(line));
            LinearLayout.LayoutParams creditLp = new LinearLayout.LayoutParams(-2, dp(34));
            if (narrowHistoryRow) creditLp.topMargin = dp(6);
            else creditLp.setMarginStart(dp(6));
            historyActions.addView(addCredit, creditLp);
            col.addView(historyActions, lp(-1, -2, 0, 6));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        TextView q = text(String.valueOf(qty), isDone ? 22 : 30, true);
        q.setGravity(Gravity.CENTER);
        q.setBackground(shape(SURFACE, 10, LINE));
        q.setMinWidth(dp(isDone ? 44 : 58));
        q.setPadding(dp(8), dp(2), dp(8), dp(2));
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-2, -2);
        qp.setMarginStart(dp(8));
        row.addView(q, qp);
        return row;
    }

    // ======================================================================== order history

    private void loadFeatureOrders() {
        // Invalidate any request left behind by a previous history screen.
        histRequestId++;
        histLoading = false;
        beginScreen("היסטוריית הזמנות", 1, null);

        // Search row mirrors the existing clear-X control inside an order.
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText search = field("חיפוש לפי שם מוצר…", InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setSingleLine(true);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        searchLp.setMarginEnd(dp(5));
        searchRow.addView(search, searchLp);

        TextView clearSearch = text("×", 22, true);
        clearSearch.setGravity(Gravity.CENTER);
        clearSearch.setTextColor(MUTED);
        clearSearch.setBackground(ripple(shape(SURFACE, ACTION_BUTTON_RADIUS_DP, LINE), ACTION_BUTTON_RADIUS_DP));
        clearSearch.setContentDescription("מחיקת החיפוש");
        clearSearch.setVisibility(View.GONE);
        searchRow.addView(clearSearch, new LinearLayout.LayoutParams(dp(42), dp(42)));

        search.setOnEditorActionListener((v, actionId, event) -> {
            histSearch = v.getText().toString().trim();
            histRequestId++;
            histLoading = false;
            histPage = 0;
            histList.removeAllViews();
            histMore.setVisibility(View.GONE);
            hideKeyboard();
            loadHistoryPage();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                clearSearch.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
            }
        });
        clearSearch.setOnClickListener(v -> {
            boolean wasFiltering = !histSearch.isEmpty();
            search.setText("");
            if (wasFiltering) {
                histSearch = "";
                histRequestId++;
                histLoading = false;
                histPage = 0;
                histList.removeAllViews();
                histMore.setVisibility(View.GONE);
                loadHistoryPage();
            }
            hideKeyboard();
        });

        root.addView(searchRow, lp(-1, dp(46), 0, 10));
        histList = new LinearLayout(this);
        histList.setOrientation(LinearLayout.VERTICAL);
        root.addView(histList, new LinearLayout.LayoutParams(-1, -2));
        histMore = muted("טוען הזמנות נוספות…", 12);
        histMore.setGravity(Gravity.CENTER);
        histMore.setPadding(dp(12), dp(10), dp(12), dp(10));
        histMore.setBackground(shape(SURFACE, 12, LINE));
        histMore.setVisibility(View.GONE);
        histMore.setContentDescription("טעינה אוטומטית של עמודי היסטוריית הזמנות");
        root.addView(histMore, lp(-1, dp(44), 4, 0));
        scrollView.getViewTreeObserver().addOnScrollChangedListener(this::maybeLoadHistoryAtBottom);
        histSearch = "";
        histPage = 0;
        histLast = 1;
        loadHistoryPage();
    }

    private void maybeLoadHistoryAtBottom() {
        if (histList == null || scrollView == null || histLoading || histPage >= histLast) return;
        if (scrollView.getChildCount() == 0) return;
        View content = scrollView.getChildAt(0);
        int contentBottom = content.getBottom();
        int visibleBottom = scrollView.getScrollY() + scrollView.getHeight();
        // Start the next request slightly before the exact bottom, so loading feels continuous.
        if (contentBottom <= visibleBottom + dp(140)) loadHistoryPage();
    }

    private void loadHistoryPage() {
        if (histLoading || (histPage > 0 && histPage >= histLast)) return;
        histLoading = true;
        final int requestId = ++histRequestId;
        final int t = screenToken;
        final int page = histPage + 1;
        final String q = histSearch;
        setStatus(page == 1 ? "טוען הזמנות…" : "");
        if (page > 1) {
            histMore.setText("טוען הזמנות נוספות…");
            histMore.setVisibility(View.VISIBLE);
        } else {
            histMore.setVisibility(View.GONE);
        }
        executor.execute(() -> {
            try {
                String path = "/api/orders?page=" + page + (q.isEmpty() ? "" : "&search=" + URLEncoder.encode(q, "UTF-8"));
                HttpResult r = api("GET", path, null);
                if (r.code < 200 || r.code >= 300) throw new Exception("שגיאת שרת (" + r.code + ")");
                int last = 1;
                try { last = Math.max(1, new JSONObject(r.body).optInt("last_page", 1)); } catch (Exception ignored) { }
                final int lastPage = last;
                final JSONArray orders = arrayFrom(r.body);
                runOnUiThread(() -> {
                    if (requestId != histRequestId) return;
                    histLoading = false;
                    if (stale(t) || !q.equals(histSearch)) return;
                    histMore.setVisibility(View.GONE);
                    setStatus(page == 1 && orders.length() == 0 ? "לא נמצאו הזמנות." : "");
                    histPage = page;
                    histLast = lastPage;
                    for (int i = 0; i < orders.length(); i++) {
                        JSONObject o = orders.optJSONObject(i);
                        if (o == null) continue;
                        if (!q.trim().isEmpty()) {
                            JSONArray matched = o.optJSONArray("matched_products");
                            List<String> names = new ArrayList<>();
                            if (matched != null) {
                                for (int j = 0; j < matched.length(); j++) {
                                    String name = matched.optString(j, "").trim();
                                    if (!name.isEmpty() && !names.contains(name)) names.add(name);
                                }
                            }
                            if (!names.isEmpty()) {
                                for (String name : names) {
                                    histList.addView(historyProductCard(o, name), lp(-1, -2, 0, 8));
                                }
                            } else {
                                // Defensive fallback if the API returns an order without matched_products.
                                histList.addView(historyCard(o), lp(-1, -2, 0, 8));
                            }
                        } else {
                            histList.addView(historyCard(o), lp(-1, -2, 0, 8));
                        }
                    }
                    // If the first page doesn't fill the screen, keep loading until it does.
                    // This also handles short screens without requiring an artificial swipe.
                    if (orders.length() > 0 && histPage < histLast && scrollView != null) {
                        histList.post(this::maybeLoadHistoryAtBottom);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestId != histRequestId) return;
                    histLoading = false;
                    if (stale(t)) return;
                    histMore.setVisibility(View.GONE);
                    fail(t, e, "טעינת ההיסטוריה נכשלה.");
                });
            }
        });
    }

    private View historyCard(JSONObject o) {
        JSONObject sale = o.optJSONObject("sale");
        String saleName = saleTitle(sale);
        if (saleName.isEmpty()) saleName = "מכירה " + first(o, "saleID", "sale_id");
        final long orderId = o.optLong("id", -1);
        String total = first(o, "total_list", "total", "amount", "sum");

        LinearLayout card = card(12);
        card.setTag("historySaleCard");
        card.setBackground(ripple(shape(nightMode ? Color.BLACK : SURFACE, 14, LINE), 14));
        card.setClickable(true);
        if (orderId > 0) card.setOnClickListener(v -> openHistoryOrder(orderId));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView saleLabel = text(saleName, 16, true);
        saleLabel.setTextColor(ORANGE);
        top.addView(saleLabel, new LinearLayout.LayoutParams(0, -2, 1));
        if (!total.isEmpty()) {
            TextView tv = text(money(parseMoney(total)), 16, true);
            tv.setTextColor(ORANGE_DARK);
            top.addView(tv);
        }
        card.addView(top);
        String when = fmtWhen(first(o, "time"));
        card.addView(muted("הזמנה #" + orderId + (when.isEmpty() ? "" : " · " + when), 12));

        return card;
    }

    /** One product-first result per matching product and order; tapping it opens the original order. */
    private View historyProductCard(JSONObject o, String productName) {
        JSONObject sale = o.optJSONObject("sale");
        String saleName = saleTitle(sale);
        if (saleName.isEmpty()) saleName = "מכירה " + first(o, "saleID", "sale_id");
        String when = fmtWhen(first(o, "time"));
        final long orderId = o.optLong("id", -1);

        LinearLayout card = card(12);
        card.setTag("historyProductCard");
        card.setBackground(ripple(shape(nightMode ? Color.BLACK : SURFACE, 14, LINE), 14));
        card.setClickable(orderId > 0);
        if (orderId > 0) card.setOnClickListener(v -> openHistoryOrder(orderId));

        TextView product = text(productName, 18, true);
        product.setTextColor(INK);
        card.addView(product);

        TextView saleLabel = text("מכירה: " + saleName, 14, true);
        saleLabel.setTextColor(ORANGE);
        saleLabel.setPadding(0, dp(5), 0, 0);
        card.addView(saleLabel);

        if (!when.isEmpty()) {
            card.addView(muted("תאריך ההזמנה: " + when, 12));
        }
        return card;
    }

    /** A history order opens as a full order screen: edit, remove, collect mode and every action menu item. */
    private void openHistoryOrder(final long id) { openHistoryOrder(id, false, 1); }

    private void openHistoryOrder(final long id, final boolean collect, final int tab) {
        historyTab = tab;
        beginScreen("הזמנה #" + id, tab, this::loadFeatureOrders);
        setStatus("טוען את ההזמנה…");
        final int t = screenToken;
        executor.execute(() -> {
            try {
                final OrderData od = fetchOrder(id);
                List<JSONObject> products = new ArrayList<>();
                try { products = fetchAllProducts(id, (int) od.saleId); } catch (AuthException ae) { throw ae; } catch (Exception ignored) { }
                Set<String> favs = null;
                try { favs = fetchFavouriteIds((int) od.saleId); } catch (AuthException ae) { throw ae; } catch (Exception ignored) { }
                final List<JSONObject> prods = products;
                final Set<String> favSet = favs;
                runOnUiThread(() -> {
                    if (stale(t)) return;
                    if (currentSaleId > 0) snapshotCurrent(); // the open sale stays available in memory
                    currentSaleId = -1;
                    productsPartial = false;
                    JSONObject saleStub = new JSONObject();
                    try {
                        saleStub.put("id", od.saleId);
                        saleStub.put("custom_name", od.saleName);
                    } catch (Exception ignored) { }
                    featureSale = saleStub;
                    featureOrderId = id;
                    featureProducts.clear();
                    featureQuantities.clear();
                    pending.clear();
                    Map<String, Integer> saved = new LinkedHashMap<>();
                    for (OrderLine l : od.lines) saved.put(l.sid, (int) Math.round(l.amount));
                    for (JSONObject p : prods) {
                        String sid = sidOf(p);
                        if (sid.isEmpty()) continue;
                        int amount = saved.containsKey(sid) ? saved.get(sid) : 0;
                        try { p.put("amount", amount); } catch (Exception ignored) { }
                        featureProducts.add(p);
                        featureQuantities.put(sid, amount);
                    }
                    for (OrderLine l : od.lines) if (!featureQuantities.containsKey(l.sid)) featureQuantities.put(l.sid, (int) Math.round(l.amount));
                    if (favSet != null) { favIds.clear(); favIds.addAll(favSet); }
                    filterText = "";
                    viewMode = "all";
                    summaryFromHistory = true;
                    summary = od;
                    collectMode = collect || !collectedSet().isEmpty();
                    renderOrderSummary();
                });
            } catch (Exception e) {
                fail(t, e, "טעינת ההזמנה נכשלה.");
            }
        });
    }

    // ======================================================================== persistent credit list

    private static final String CREDIT_LIST_PREF = "history_credit_list_v1";
    // A tab may keep the credit-list screen cached while another tab changes the list.
    private View creditListScreenView;
    private boolean creditListDirty = false;

    private JSONArray readCreditList() {
        try {
            JSONArray items = new JSONArray(prefs.getString(CREDIT_LIST_PREF, "[]"));
            // Backward-compatible migration: rows saved before the type field are credits.
            boolean migrated = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && !item.has("type")) {
                    item.put("type", "credit");
                    migrated = true;
                }
            }
            if (migrated) prefs.edit().putString(CREDIT_LIST_PREF, items.toString()).apply();
            return items;
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void saveCreditList(JSONArray items) {
        prefs.edit().putString(CREDIT_LIST_PREF, items == null ? "[]" : items.toString()).apply();
        creditListDirty = true;
    }

    /** Stable identity: a product may be listed once per sale, regardless of its quantity. */
    private String historyCreditKey(OrderLine line) {
        String saleIdentity = summary == null ? "" : String.valueOf(summary.saleId);
        if (saleIdentity.isEmpty() || "0".equals(saleIdentity)) {
            saleIdentity = summary == null || summary.saleName == null ? "" : summary.saleName.trim();
        }
        String itemIdentity = line == null || line.sid == null ? "" : line.sid.trim();
        if (itemIdentity.isEmpty()) {
            itemIdentity = line == null || line.name == null ? "" : line.name.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ");
        }
        return saleIdentity + "|" + itemIdentity;
    }

    private void addHistoryItemToCreditList(OrderLine line) {
        if (!summaryFromHistory || summary == null || line == null) {
            toast("אפשר להוסיף לרשימת הזיכויים רק פריטים מהיסטוריית ההזמנות.", false);
            return;
        }
        String key = historyCreditKey(line);
        JSONArray items = readCreditList();
        for (int i = 0; i < items.length(); i++) {
            JSONObject existing = items.optJSONObject(i);
            if (existing != null && key.equals(existing.optString("key", ""))) {
                toast("הפריט כבר נמצא ברשימת הזיכויים.", false);
                return;
            }
        }
        try {
            JSONObject entry = new JSONObject();
            entry.put("key", key);
            entry.put("type", "credit");
            entry.put("text", historyItemCopyText(line));
            entry.put("quantity", Math.max(0, (int) Math.round(line.amount)));
            entry.put("unitPrice", line.price);
            entry.put("saleId", summary.saleId);
            entry.put("saleName", summary.saleName == null ? "" : summary.saleName);
            entry.put("itemId", line.sid == null ? "" : line.sid);
            entry.put("itemName", line.name == null ? "" : line.name);
            items.put(entry);
            saveCreditList(items);
            toast("נוסף לרשימת הזיכויים: " + line.name, false);
        } catch (Exception e) {
            toast("לא ניתן לשמור את הפריט ברשימת הזיכויים.", false);
        }
    }

    private String creditListText() {
        JSONArray current = readCreditList();
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            String itemText = item.optString("text", "").trim();
            if (itemText.isEmpty()) continue;
            if (all.length() > 0) all.append("\n");
            all.append("charge".equals(item.optString("type", "credit")) ? "חיוב: " : "זיכוי: ")
                    .append(itemText);
        }
        return all.toString();
    }

    private String creditListTypedText(String type) {
        JSONArray current = readCreditList();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            if (!type.equals(item.optString("type", "credit"))) continue; // old saved rows are credits
            String itemText = item.optString("text", "").trim();
            if (itemText.isEmpty()) continue;
            if (out.length() > 0) out.append("\n");
            out.append(itemText);
        }
        return out.toString();
    }

    /** Sum saved credit/charge entries, including older rows that only stored a formatted total. */
    private String creditListTotalText(String type, String label) {
        JSONArray current = readCreditList();
        double total = 0;
        int missingPrices = 0;
        int countedItems = 0;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null || !type.equals(item.optString("type", "credit"))) continue;
            String itemText = item.optString("text", "").trim();
            if (itemText.isEmpty()) continue;
            countedItems++;
            int quantity = Math.max(0, item.optInt("quantity", 0));
            if (item.has("unitPrice") && quantity > 0) {
                double unitPrice = item.optDouble("unitPrice", -1);
                if (unitPrice >= 0) {
                    total += unitPrice * quantity;
                    continue;
                }
            }
            // Backward compatibility for saved rows created before unitPrice was stored.
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("סה״כ:\\s*₪\\s*([0-9]+(?:[.,][0-9]+)?)")
                    .matcher(itemText);
            if (matcher.find()) {
                try {
                    total += Double.parseDouble(matcher.group(1).replace(',', '.'));
                    continue;
                } catch (Exception ignored) { }
            }
            missingPrices++;
        }
        if (countedItems == 0) return "";
        String result = label + ": " + money(total);
        if (missingPrices > 0) result += " (לא כולל " + missingPrices + " פריטים ללא מחיר זמין)";
        return result;
    }

    /** Stable key for one product row within one sale; never merges charges across sales. */
    private String cachedChargeKey(int saleId, JSONObject product) {
        String itemIdentity = first(product, "item_salesID", "item_sales_id", "id", "itemID");
        if (itemIdentity.isEmpty()) itemIdentity = pidOf(product);
        if (itemIdentity.isEmpty()) itemIdentity = productName(product).trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return "charge|" + saleId + "|" + itemIdentity;
    }

    private void addCachedProductCharge(JSONObject product) {
        if (!closedOrderReadOnly || product == null) return;
        String sid = sidOf(product);
        int quantity = sid.isEmpty() || !featureQuantities.containsKey(sid) ? 0 : featureQuantities.get(sid);
        if (quantity <= 0) {
            toast("בחר כמות גדולה מאפס לפני הוספת החיוב.", false);
            return;
        }
        int saleId = currentSaleId;
        if (saleId <= 0) {
            toast("לא ניתן לזהות את המכירה שממנה נוצר החיוב.", true);
            return;
        }
        String key = cachedChargeKey(saleId, product);
        String saleName = currentTitle();
        String itemName = productName(product);
        double unitPrice = productPrice(product);
        String priceText = unitPrice > 0 ? money(unitPrice * quantity) : "מחיר כולל לא זמין";
        String itemText = saleName + " - " + itemName + " - כמות: " + quantity + " - סה״כ: " + priceText;
        JSONArray items = readCreditList();
        JSONArray updated = new JSONArray();
        boolean replaced = false;
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject existing = items.optJSONObject(i);
                if (existing == null) continue;
                if ("charge".equals(existing.optString("type", "credit"))
                        && key.equals(existing.optString("key", ""))) {
                    if (!replaced) {
                        JSONObject entry = new JSONObject();
                        entry.put("key", key);
                        entry.put("type", "charge");
                        entry.put("text", itemText);
                        entry.put("quantity", quantity);
                        entry.put("saleId", saleId);
                        entry.put("saleName", saleName);
                        entry.put("itemId", sid.isEmpty() ? pidOf(product) : sid);
                        entry.put("itemName", itemName);
                        entry.put("unitPrice", unitPrice);
                        updated.put(entry);
                        replaced = true;
                    }
                    // Collapse legacy duplicates of this charge only.
                } else {
                    updated.put(existing);
                }
            }
            if (!replaced) {
                JSONObject entry = new JSONObject();
                entry.put("key", key);
                entry.put("type", "charge");
                entry.put("text", itemText);
                entry.put("quantity", quantity);
                entry.put("saleId", saleId);
                entry.put("saleName", saleName);
                entry.put("itemId", sid.isEmpty() ? pidOf(product) : sid);
                entry.put("itemName", itemName);
                entry.put("unitPrice", unitPrice);
                updated.put(entry);
            }
            saveCreditList(updated);
            toast((replaced ? "עודכן החיוב: " : "נוסף חיוב: ") + itemName + " × " + quantity, false);
        } catch (Exception e) {
            toast("לא ניתן לשמור את החיוב.", true);
        }
    }

    private void showCreditList() {
        beginScreen("רשימת זיכויים וחיובים", 2, this::loadFeatureTransactions);
        creditListScreenView = currentScreen;
        creditListDirty = false;
        JSONArray items = readCreditList();
        setStatus(items.length() == 0 ? "הרשימה ריקה. אפשר להוסיף זיכויים מההיסטוריה וחיובים מהזמנות סגורות." : "");
        // Keep all four list actions on one compact row, including on narrow phones.
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView clear = smallChip("🗑 מחיקה", DANGER);
        clear.setEnabled(items.length() > 0);
        clear.setAlpha(items.length() > 0 ? 1f : 0.5f);
        clear.setTextSize(12);
        clear.setOnClickListener(v -> appDialog()
                .setTitle("מחיקת רשימת הזיכויים")
                .setMessage("למחוק את כל " + readCreditList().length() + " הפריטים מהרשימה?")
                .setNegativeButton("ביטול", null)
                .setPositiveButton("מחק", (dialog, which) -> {
                    saveCreditList(new JSONArray());
                    showCreditList();
                    toast("רשימת הזיכויים נמחקה.", false);
                }).show());

        TextView copyAll = smallChip("📋 העתקה", TONAL);
        copyAll.setEnabled(items.length() > 0);
        copyAll.setAlpha(items.length() > 0 ? 1f : 0.5f);
        copyAll.setTextSize(12);
        copyAll.setOnClickListener(v -> {
            String all = creditListText();
            if (!all.isEmpty()) copyText(all);
        });

        TextView sendCredit = smallChip("שלח למשנת יוסף", PRIMARY);
        sendCredit.setEnabled(items.length() > 0);
        sendCredit.setAlpha(items.length() > 0 ? 1f : 0.5f);
        sendCredit.setTextSize(12);
        // Sending the credit/charge request is a protected action when child lock is enabled.
        sendCredit.setOnClickListener(v -> requireUnlock(() -> {
            String creditText = creditListTypedText("credit");
            String chargeText = creditListTypedText("charge");
            String creditTotalText = creditListTotalText("credit", "סה״כ לזיכויים");
            String chargeTotalText = creditListTotalText("charge", "סה״כ חיובים");
            if (creditText.isEmpty() && chargeText.isEmpty()) {
                toast("רשימת הזיכויים והחיובים ריקה.", false);
                return;
            }
            try {
                String accountEmail = prefs.getString("email", "").trim();
                String subject;
                String body;
                if (!creditText.isEmpty() && !chargeText.isEmpty()) {
                    subject = "בקשת זיכויים וחיובים על הזמנות משנת יוסף לחשבון " + accountEmail;
                    body = "שלום,\n"
                            + "נשמח לקבל זיכוי על המוצרים הבאים: \n"
                            + creditText
                            + (creditTotalText.isEmpty() ? "" : "\n\n" + creditTotalText)
                            + "\n\nסיבת הזיכוי: חוסרים."
                            + "\n\nולהלן הדברים שאתם צריכים לחייב אותנו עליהם:\n"
                            + chargeText
                            + (chargeTotalText.isEmpty() ? "" : "\n\n" + chargeTotalText);
                } else if (!creditText.isEmpty()) {
                    subject = "בקשת זיכויים על הזמנות משנת יוסף לחשבון " + accountEmail;
                    body = "שלום,\n"
                            + "נשמח לקבל זיכוי על המוצרים הבאים: \n"
                            + creditText
                            + (creditTotalText.isEmpty() ? "" : "\n\n" + creditTotalText)
                            + "\n\nסיבת הזיכוי: חוסרים.";
                } else {
                    subject = "חיובים על הזמנות משנת יוסף לחשבון " + accountEmail;
                    body = "שלום,\n"
                            + "יש לנו גם כמה חיובים שאתם צריכים לחייב אותנו.\n"
                            + "פירוט החיובים:\n"
                            + chargeText
                            + (chargeTotalText.isEmpty() ? "" : "\n\n" + chargeTotalText);
                }
                Uri mailUri = Uri.parse("mailto:A0773168080@gmail.com"
                        + "?subject=" + Uri.encode(subject)
                        + "&body=" + Uri.encode(body));
                Intent mail = new Intent(Intent.ACTION_SENDTO, mailUri);
                startActivity(Intent.createChooser(mail, "שליחת זיכויים וחיובים למשנת יוסף"));
            } catch (Exception e) {
                toast("לא נמצאה אפליקציה לשליחת מייל.", true);
            }
        }));

        TextView shareCredit = smallChip("📤 שיתוף", TONAL);
        shareCredit.setEnabled(items.length() > 0);
        shareCredit.setAlpha(items.length() > 0 ? 1f : 0.5f);
        shareCredit.setTextSize(12);
        shareCredit.setOnClickListener(v -> {
            String body = creditListText();
            if (!body.isEmpty()) shareText("רשימת זיכויים", body);
        });

        TextView[] actionButtons = {clear, copyAll, shareCredit};
        for (int i = 0; i < actionButtons.length; i++) {
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                    0, dp(36), 1f);
            if (i > 0) actionLp.setMarginStart(dp(4));
            actions.addView(actionButtons[i], actionLp);
        }
        root.addView(actions, lp(-1, -2, 0, 8));

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            final String key = item.optString("key", "");
            LinearLayout entryCard = card(12);
            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            String itemType = item.optString("type", "credit");
            TextView details = text(("charge".equals(itemType) ? "חיוב · " : "זיכוי · ")
                    + item.optString("text", ""), 14, true);
            details.setTextIsSelectable(true);
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1f);
            detailsLp.setMarginEnd(dp(8));
            itemRow.addView(details, detailsLp);
            TextView remove = smallChip("✕", DANGER);
            remove.setContentDescription("הסרת הפריט");
            remove.setTextSize(16);
            remove.setMinWidth(dp(36));
            remove.setOnClickListener(v -> {
                JSONArray current = readCreditList();
                JSONArray remaining = new JSONArray();
                for (int j = 0; j < current.length(); j++) {
                    JSONObject candidate = current.optJSONObject(j);
                    if (candidate != null && !key.equals(candidate.optString("key", ""))) {
                        remaining.put(candidate);
                    }
                }
                saveCreditList(remaining);
                showCreditList();
                toast("הפריט הוסר מהרשימה.", false);
            });
            itemRow.addView(remove, new LinearLayout.LayoutParams(dp(36), dp(36)));
            entryCard.addView(itemRow);
            root.addView(entryCard, lp(-1, -2, 0, 8));
        }
        // Keep the primary email action below every item and stretch it across the screen.
        root.addView(sendCredit, lp(-1, 48, 0, 12));
    }

    // ======================================================================== credits / charges

    private void loadFeatureTransactions() {
        beginScreen("זיכויים וחיובים", 2, null);
        setStatus("ממתין לשרת…");
        // Build the complete screen shell immediately; only transaction data waits for the API.
        renderTransactions(Collections.<JSONObject>emptyList(), true);
        final int t = screenToken;
        executor.execute(() -> {
            try {
                LinkedHashMap<String, JSONObject> unique = new LinkedHashMap<>();
                int page = 1, lastPage = 1;
                while (page <= Math.min(lastPage, 60)) {
                    HttpResult response = api("GET", "/api/profile/transactions?page=" + page, null);
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
                final ArrayList<JSONObject> rows = new ArrayList<>(unique.values());
                Collections.sort(rows, new Comparator<JSONObject>() {
                    @Override public int compare(JSONObject a, JSONObject b) { return b.optString("time", "").compareTo(a.optString("time", "")); }
                });
                runOnUiThread(() -> {
                    if (stale(t)) return;
                    // Rebuild the same screen with the completed data, preserving its normal behavior.
                    beginScreen("זיכויים וחיובים", 2, null);
                    renderTransactions(rows);
                });
            } catch (Exception e) {
                if (e instanceof AuthException) {
                    fail(t, e, "טעינת הזיכויים נכשלה.");
                    return;
                }
                runOnUiThread(() -> {
                    if (stale(t)) return;
                    // Keep the controls visible on failure too, and replace the waiting state with an error.
                    beginScreen("זיכויים וחיובים", 2, null);
                    renderTransactions(Collections.<JSONObject>emptyList());
                    fail(screenToken, e, "טעינת הזיכויים נכשלה.");
                });
            }
        });
    }

    private void renderTransactions(final List<JSONObject> rows) {
        renderTransactions(rows, false);
    }

    private void renderTransactions(final List<JSONObject> rows, final boolean waitingForServer) {
        double creditTotal = 0, chargeTotal = 0;
        int creditCount = 0, chargeCount = 0;
        for (JSONObject tx : rows) {
            double amount = tx.optDouble("skum", 0);
            if (tx.optBoolean("canceled", false)) continue;
            if (amount < 0) { creditTotal += -amount; creditCount++; }
            if (amount > 0) { chargeTotal += amount; chargeCount++; }
        }
        setStatus(waitingForServer ? "ממתין לשרת…" : (rows.isEmpty() ? "לא נמצאו תנועות בחשבון." : ""));

        LinearLayout sum = card(14);
        if (waitingForServer) {
            sum.addView(text("ממתין לשרת…", 16, true));
            sum.addView(muted("אפשר להשתמש בתפריטים ובכלי המסך כבר עכשיו.", 12));
        } else {
            TextView c1 = text("🟢 סך הכל זיכויים: " + money2(creditTotal) + " (" + creditCount + ")", 16, true);
            c1.setTextColor(GREEN);
            TextView c2 = text("🔴 סך הכל חיובים: " + money2(chargeTotal) + " (" + chargeCount + ")", 16, true);
            c2.setTextColor(RED);
            sum.addView(c1);
            sum.addView(c2);
            sum.addView(muted("סה״כ תנועות: " + rows.size(), 12));
        }
        root.addView(sum, lp(-1, -2, 0, 10));

        final String[] mode = {"all"};
        LinearLayout filters = new LinearLayout(this);
        final TextView all = btn("הכול", PRIMARY);
        final TextView credits = btn("זיכויים", TONAL);
        final TextView charges = btn("חיובים", TONAL);
        final TextView bySale = smallChip("לפי מכירה", TONAL);
        final TextView shareTx = smallChip("📤 שיתוף", TONAL);
        final TextView[] chips = {all, credits, charges, bySale, shareTx};
        for (int i = 0; i < chips.length; i++) {
            chips[i].setMinHeight(dp(40));
            chips[i].setTextSize(12);
            chips[i].setPadding(dp(2), dp(4), dp(2), dp(4));
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(40), 1);
            if (i > 0) fp.setMarginStart(dp(4));
            filters.addView(chips[i], fp);
        }
        root.addView(filters, lp(-1, -2, 0, 10));

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));

        final Runnable[] render = new Runnable[1];
        final String[] query = {""};
        EditText searchBox = field("חיפוש (מוצר, מכירה, סכום, תאריך)…", InputType.TYPE_CLASS_TEXT);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { query[0] = e.toString().trim().toLowerCase(Locale.ROOT); render[0].run(); }
        });
        root.addView(searchBox, root.indexOfChild(list), lp(-1, dp(46), 0, 8));
        int pendingCreditItems = readCreditList().length();
        String creditListLabel = "🧾 רשימת זיכויים וחיובים" + (pendingCreditItems > 0 ? " (" + pendingCreditItems + ")" : "");
        TextView creditListButton = btn(creditListLabel, TONAL);
        creditListButton.setTextSize(17);
        creditListButton.setTypeface(creditListButton.getTypeface(), Typeface.BOLD);
        creditListButton.setGravity(Gravity.CENTER);
        creditListButton.setMinHeight(dp(48));
        creditListButton.setOnClickListener(v -> showCreditList());
        root.addView(creditListButton, root.indexOfChild(filters), lp(-1, dp(50), 0, 8));
        final boolean[] bySaleMode = {false};
        final Set<String> expandedSales = new HashSet<>();
        bySale.setOnClickListener(v -> {
            bySaleMode[0] = !bySaleMode[0];
            if (bySaleMode[0]) mode[0] = "all";
            render[0].run();
        });
        shareTx.setOnClickListener(v -> shareText("זיכויים וחיובים", creditsText(rows)));
        render[0] = () -> {
            list.removeAllViews();
            String[] keys = {"all", "credit", "charge"};
            for (int i = 0; i < 3; i++) {
                boolean on = keys[i].equals(mode[0]) && !bySaleMode[0];
                chips[i].setBackground(ripple(shape(on ? ORANGE : ORANGE_LIGHT, ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
                chips[i].setTextColor(on ? ON_ORANGE : ORANGE_DARK);
            }
            bySale.setBackground(ripple(shape(bySaleMode[0] ? ORANGE : ORANGE_LIGHT, ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
            bySale.setTextColor(bySaleMode[0] ? ON_ORANGE : ORANGE_DARK);
            shareTx.setBackground(ripple(shape(ORANGE_LIGHT, ACTION_BUTTON_RADIUS_DP, 0), ACTION_BUTTON_RADIUS_DP));
            shareTx.setTextColor(ORANGE_DARK);
            if (bySaleMode[0]) {
                renderTransactionsBySale(rows, list, mode[0], query[0], expandedSales);
                return;
            }
            for (JSONObject tx : rows) {
                double amount = tx.optDouble("skum", 0);
                if ("credit".equals(mode[0]) && amount >= 0) continue;
                if ("charge".equals(mode[0]) && amount <= 0) continue;
                if (!query[0].isEmpty() && !txSearchText(tx).contains(query[0])) continue;
                list.addView(transactionCard(tx, amount), lp(-1, -2, 0, 8));
            }
        };
        all.setOnClickListener(v -> { bySaleMode[0] = false; mode[0] = "all"; render[0].run(); });
        credits.setOnClickListener(v -> { bySaleMode[0] = false; mode[0] = "credit"; render[0].run(); });
        charges.setOnClickListener(v -> { bySaleMode[0] = false; mode[0] = "charge"; render[0].run(); });
        render[0].run();
    }

    private void renderTransactionsBySale(List<JSONObject> rows, LinearLayout list,
                                          String mode, String query, Set<String> expandedSales) {
        // Rebuild the grouped list after each tap so expansion/collapse is visible immediately.
        list.removeAllViews();
        Map<String, List<JSONObject>> groups = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> latest = new LinkedHashMap<>();
        for (JSONObject tx : rows) {
            if (tx.optBoolean("canceled", false)) continue;
            double amount = tx.optDouble("skum", 0);
            if ("credit".equals(mode) && amount >= 0) continue;
            if ("charge".equals(mode) && amount <= 0) continue;
            if (!query.isEmpty() && !txSearchText(tx).contains(query)) continue;
            String[] names = txNames(tx);
            String label = !names[1].isEmpty() ? names[1] : (!names[0].isEmpty() ? names[0] : "ללא מכירה (כללי)");
            String key = label;
            List<JSONObject> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
                labels.put(key, label);
                latest.put(key, "");
            }
            group.add(tx);
            String time = tx.optString("time", "");
            if (time.compareTo(latest.get(key)) > 0) latest.put(key, time);
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        Collections.sort(keys, (a, b) -> latest.get(b).compareTo(latest.get(a)));
        if (keys.isEmpty()) {
            list.addView(muted("לא נמצאו מכירות עם תנועות התואמות לסינון.", 14), lp(-1, -2, 0, 8));
            return;
        }
        for (String key : keys) {
            List<JSONObject> group = groups.get(key);
            double creditsTotal = 0, chargesTotal = 0;
            int creditCount = 0, chargeCount = 0;
            for (JSONObject tx : group) {
                double amount = tx.optDouble("skum", 0);
                if (amount < 0) { creditsTotal += -amount; creditCount++; }
                else if (amount > 0) { chargesTotal += amount; chargeCount++; }
            }
            boolean expanded = expandedSales.contains(key);
            LinearLayout saleCard = card(12);
            saleCard.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout headingRow = new LinearLayout(this);
            headingRow.setOrientation(LinearLayout.HORIZONTAL);
            headingRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView toggle = text((expanded ? "−" : "+") + "  " + labels.get(key), 15, true);
            toggle.setTextColor(INK);
            toggle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            toggle.setMaxLines(2);
            LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(0, -2, 1f);
            toggleLp.setMarginEnd(dp(8));
            headingRow.addView(toggle, toggleLp);
            TextView count = muted(group.size() + " תנועות", 12);
            headingRow.addView(count, new LinearLayout.LayoutParams(-2, -2));
            View.OnClickListener expand = v -> {
                if (expandedSales.contains(key)) expandedSales.remove(key);
                else expandedSales.add(key);
                renderTransactionsBySale(rows, list, mode, query, expandedSales);
            };
            headingRow.setOnClickListener(expand);
            toggle.setOnClickListener(expand);
            saleCard.setOnClickListener(expand);
            saleCard.addView(headingRow);
            TextView summary = muted("🟢 זיכויים: " + money2(creditsTotal) + " (" + creditCount + ")"
                    + "   •   🔴 חיובים: " + money2(chargesTotal) + " (" + chargeCount + ")", 12);
            summary.setPadding(dp(28), dp(4), 0, 0);
            saleCard.addView(summary);
            list.addView(saleCard, lp(-1, -2, 0, 6));
            if (expanded) {
                for (JSONObject tx : group) {
                    list.addView(transactionCard(tx, tx.optDouble("skum", 0)), lp(-1, -2, 0, 4));
                }
            }
        }
    }

    private View transactionCard(JSONObject tx, double amount) {
        boolean canceled = tx.optBoolean("canceled", false);
        JSONObject product = tx.optJSONObject("product");
        if (product == null) product = new JSONObject();
        String name = product.optString("full_name", product.optString("name", ""));
        if ("null".equals(name)) name = "";
        String note = cleanHtml(tx.optString("note", ""));
        if ("null".equals(note)) note = "";
        JSONObject sale = firstObject(tx, "sale", "sale_data", "saleData");
        JSONObject original = firstObject(tx, "original_sale", "originalSale", "original_sale_data", "originalSaleData");
        String saleName = tx.optInt("saleID", tx.optInt("sale_id", -1)) == 1 ? "" : transactionSaleName(tx, sale, false);
        int origId = tx.optInt("original_saleID", tx.optInt("original_sale_id", -1));
        String originalName = origId == 0 || origId == 1 ? "" : transactionSaleName(tx, original, true);
        String description = name.isEmpty() ? note : name;
        if (description.isEmpty()) description = originalName.isEmpty() ? saleName : originalName;
        String date = fmtWhen(tx.optString("time", ""));

        LinearLayout card = card(12);
        if (canceled) card.setAlpha(0.6f);
        int color = amount < 0 ? GREEN : (amount > 0 ? RED : MUTED);
        String kind = amount < 0 ? "🟢 זיכוי" : (amount > 0 ? "🔴 חיוב" : "ללא סכום");
        TextView heading = text(kind + ": " + money2(Math.abs(amount)), 16, true);
        heading.setTextColor(color);
        card.addView(heading);
        if (!description.isEmpty()) card.addView(text(description, 14, false));
        if (tx.has("amount") && !tx.isNull("amount") && !tx.optString("amount").isEmpty()) card.addView(muted("כמות: " + tx.optString("amount"), 12));
        if (!saleName.isEmpty()) card.addView(muted("זוכה במכירה: " + saleName, 12));
        if (!originalName.isEmpty() && !originalName.equals(saleName)) card.addView(muted("מכירה מקורית: " + originalName, 12));
        if (!date.isEmpty()) card.addView(muted(date, 12));
        if (canceled) {
            TextView cancel = text("בוטל — לא נספר בסיכום", 12, true);
            cancel.setTextColor(RED);
            card.addView(cancel);
        }
        return card;
    }

    private JSONObject firstObject(JSONObject tx, String... keys) {
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
        String name = first(tx, directKeys);
        if (!name.isEmpty()) return name;
        if (sale != null) {
            name = first(sale, "custom_name", "name", "sale_name", "title", "week", "label");
            if (name.isEmpty()) {
                JSONObject parent = firstObject(sale, "parent", "sale_parent");
                if (parent != null) name = first(parent, "week", "name", "title", "custom_name");
            }
            if (name.isEmpty()) {
                JSONObject nested = firstObject(sale, "sale", "original_sale", "originalSale");
                if (nested != null && nested != sale) name = transactionSaleName(tx, nested, original);
            }
            if (!name.isEmpty()) return name;
        }
        String id = original
                ? first(tx, "original_sale_id", "originalSaleId", "original_saleID")
                : first(tx, "sale_id", "saleId", "saleID");
        if (id.isEmpty() && sale != null) id = first(sale, "id", "sale_id", "saleID");
        return id.isEmpty() ? "" : "מכירה #" + id;
    }

    private void addTransactionList(Map<String, JSONObject> target, Object value) {
        JSONArray array = null;
        if (value instanceof JSONArray) array = (JSONArray) value;
        else if (value instanceof JSONObject) array = ((JSONObject) value).optJSONArray("data");
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject tx = array.optJSONObject(i);
            if (tx != null) target.putIfAbsent(tx.optString("id", "extra_" + target.size()), tx);
        }
    }

    // ======================================================================== misc helpers (log, token status, share, dialogs)

    private String redact(String text) {
        if (text == null) return "";
        return text.replaceAll("eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]*", "<jwt>")
                .replaceAll("(?i)(\"?(?:password|passwd|סיסמה)\"?\\s*[:=]\\s*)\"?[^\",\\s}]+\"?", "$1***");
    }

    private String apiErrorMessage(String body, String fallback) {
        if (body == null || body.trim().isEmpty()) return fallback;
        try {
            JSONObject error = new JSONObject(body);
            String message = first(error, "message", "error_description", "detail", "error");
            if (message != null && !message.trim().isEmpty()) return message.trim();
        } catch (Exception ignored) { }
        return fallback;    }

    private void logApi(String method, String path, String result, long ms, String errorBody) {
        // The diagnostic log intentionally preserves the HTTP result and the complete response body.
        // redact() still masks sensitive values before anything is displayed or shared.
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + method + " " + path + " → " + result + " (" + ms + "ms)";
        if (errorBody != null && !errorBody.isEmpty()) {
            line += "\n    " + redact(errorBody.replace("\n", " "));
        }
        synchronized (apiLog) {
            apiLog.add(line);
            while (apiLog.size() > 150) apiLog.remove(0);
        }
    }

    /**
     * Diagnostic-only lookup: inspect the authenticated item lists for all currently open sales,
     * then show the complete raw server object(s) whose identifier matches the requested ID.
     */
    private void diagnoseItemFromServer(final String targetId) {
        if (!hasToken()) { toast("צריך להתחבר לפני בדיקת הפריט", true); return; }
        toast("בודק את הפריט מול נתוני המכירות שבשרת…", false);
        executor.execute(() -> {
            final List<String> matches = Collections.synchronizedList(new ArrayList<>());
            final List<String> orderMatches = Collections.synchronizedList(new ArrayList<>());
            final List<String> checkedSales = Collections.synchronizedList(new ArrayList<>());
            final List<String> failures = Collections.synchronizedList(new ArrayList<>());
            String orderCheck = "לא בוצעה בדיקת הזמנה";
            try {
                // Inspect the exact order independently from the open-sale catalogue endpoint.
                try {
                    HttpResult orderResponse = api("GET", "/api/orders/26039956", null);
                    if (orderResponse.code >= 200 && orderResponse.code < 300) {
                        JSONObject response = new JSONObject(orderResponse.body);
                        JSONObject order = response.optJSONObject("order");
                        if (order == null) order = response.optJSONObject("data");
                        if (order == null) order = response;
                        JSONArray orderItems = orderItemsFrom(order);
                        orderCheck = "GET /api/orders/26039956 הצליח; מספר שורות פריטים שנקראו: " + orderItems.length();
                        for (int i = 0; i < orderItems.length(); i++) {
                            JSONObject item = orderItems.optJSONObject(i);
                            if (item != null && (jsonHasMatchingItemId(item, targetId) || jsonContainsItemName(item))) {
                                JSONObject record = new JSONObject();
                                record.put("order_id", 26039956);
                                record.put("matched_by", jsonHasMatchingItemId(item, targetId)
                                        ? "מזהה " + targetId : "שם שעשוי להיות קשור לאפונה/קייטי");
                                record.put("order_item_from_server", item);
                                orderMatches.add(record.toString(2));
                            }
                        }
                    } else {
                        orderCheck = "GET /api/orders/26039956 נכשל: HTTP " + orderResponse.code
                                + "\n" + safeBodyForDiagnostic(orderResponse.body);
                    }
                } catch (Exception orderError) {
                    orderCheck = "בדיקת GET /api/orders/26039956 נכשלה: " + orderError.getMessage();
                }
                HttpResult salesResponse = api("GET", "/api/profile/open_sales", null);
                if (salesResponse.code < 200 || salesResponse.code >= 300) {
                    throw new Exception("טעינת המכירות נכשלה: HTTP " + salesResponse.code
                            + "\n" + safeBodyForDiagnostic(salesResponse.body));
                }
                JSONArray sales = arrayFrom(salesResponse.body);
                for (int i = 0; i < sales.length(); i++) {
                    JSONObject sale = sales.optJSONObject(i);
                    if (sale == null) continue;
                    long saleId = sale.optLong("id", -1);
                    if (saleId <= 0) continue;
                    checkedSales.add("מכירה " + saleId + " — " + saleTitle(sale));
                    try {
                        fetchPagesFor(saleId, (items, total) -> {
                            for (JSONObject item : items) {
                                if (jsonHasMatchingItemId(item, targetId)) {
                                    JSONObject record = new JSONObject();
                                    try {
                                        record.put("sale_id", saleId);
                                        record.put("sale_name", saleTitle(sale));
                                        record.put("item_from_server", item);
                                        matches.add(record.toString(2));
                                    } catch (Exception ignored) { }
                                }
                            }
                        });
                    } catch (Exception e) {
                        failures.add("מכירה " + saleId + ": " + e.getMessage());
                    }
                }
                StringBuilder report = new StringBuilder();
                report.append("מזהה שנבדק: ").append(targetId).append("\n");
                report.append("בדיקת ההזמנה 26039956: ").append(orderCheck).append("\n");
                if (!orderMatches.isEmpty()) {
                    report.append("\nנתוני פריטים מההזמנה 26039956 (התאמה לפי מזהה או שם אפונה/קייטי):\n\n");
                    for (int i = 0; i < orderMatches.size(); i++) {
                        report.append("=== פריט מההזמנה ").append(i + 1).append(" ===\n")
                                .append(orderMatches.get(i)).append("\n\n");
                    }
                } else {
                    report.append("לא נמצאה שורת הזמנה שמכילה את המזהה ").append(targetId)
                            .append(" או שם עם אפונה/קייטי.\n");
                }
                report.append("מקור: GET /api/profile/open_sales ואז GET /api/get_items_for_sale/{sale_id}/null?page=…&limit=…\n");
                report.append("מכירות פתוחות שנבדקו: ").append(checkedSales.size()).append("\n");
                if (!matches.isEmpty()) {
                    report.append("נמצאו ").append(matches.size()).append(" התאמות. כל הנתונים הגולמיים שהשרת החזיר עבורן:\n\n");
                    synchronized (matches) {
                        for (int i = 0; i < matches.size(); i++) {
                            report.append("=== התאמה ").append(i + 1).append(" ===\n")
                                    .append(matches.get(i)).append("\n\n");
                        }
                    }
                } else {
                    report.append("\nלא נמצא פריט עם המזהה הזה ברשימות הפריטים של המכירות הפתוחות שנבדקו. ")
                            .append("זה לא מוכיח שהפריט לא קיים: ייתכן שהוא שייך למכירה סגורה או שהשרת משתמש בנתיב אחר.\n");
                }
                if (!failures.isEmpty()) {
                    report.append("\nשגיאות בזמן בדיקה:\n");
                    synchronized (failures) {
                        for (String failure : failures) report.append("• ").append(failure).append("\n");
                    }
                }
                report.append("\nמכירות שנבדקו:\n");
                synchronized (checkedSales) {
                    for (String checked : checkedSales) report.append("• ").append(checked).append("\n");
                }
                final String result = report.toString();
                runOnUiThread(() -> appDialog()
                        .setTitle("תוצאות אבחון פריט " + targetId)
                        .setMessage(result)
                        .setPositiveButton("סגירה", null)
                        .setNeutralButton("העתקת כל הדוח", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("אבחון פריט " + targetId, result));
                            toast("דוח האבחון הועתק ללוח", false);
                        })
                        .setNegativeButton("שיתוף", (d, w) -> shareText("אבחון פריט " + targetId, result))
                        .show());
            } catch (Exception e) {
                String error = "בדיקת הפריט נכשלה.\n" + e.getMessage();
                runOnUiThread(() -> showTextDialog("שגיאה באבחון פריט " + targetId, error));
            }
        });
    }

    /**
     * Collect full response bodies from the authenticated GET endpoints used by the app.
     * The report stays on-device until the user explicitly copies or shares it.
     */
    private static class JsonEndpointChoice {
        final String label;
        final String path;
        JsonEndpointChoice(String label, String path) { this.label = label; this.path = path; }
    }

    /**
     * The JSON button now opens a safe, GET-only endpoint picker. Responses are never shown
     * on screen or shared automatically: sensitive values are redacted before clipboard copy.
     */
    private void diagnoseAllServerJson() {
        if (!hasToken()) { toast("צריך להתחבר לפני בדיקת כתובת", true); return; }
        final JsonEndpointChoice[] endpoints = new JsonEndpointChoice[] {
                new JsonEndpointChoice("מסך מכירות פתוחות — GET /api/profile/open_sales", "/api/profile/open_sales"),
                new JsonEndpointChoice("מסך ההזמנות — GET /api/orders", "/api/orders"),
                new JsonEndpointChoice("הזמנות שנסגרו / היסטוריית הזמנות — GET /api/orders", "/api/orders"),
                new JsonEndpointChoice("פרטי הזמנה (מזהה לדוגמה 26039956) — מסך הזמנה", "/api/orders/26039956"),
                new JsonEndpointChoice("מסך תנועות החשבון — GET /api/profile/transactions?page=1", "/api/profile/transactions?page=1"),
                new JsonEndpointChoice("פתיחת הזמנה: טעינת אמצעי תשלום — GET /api/payment_methods/cards", "/api/payment_methods/cards"),
                new JsonEndpointChoice("מסך פרטי מכירה: טעינת מוצרים (מזהה מכירה לדוגמה 171584)", "/api/get_items_for_sale/171584/null?page=1&limit=100"),
                new JsonEndpointChoice("מסך מוצרים: המועדפים במכירה לדוגמה 171584", "/api/get_favourites/171584")
        };
        final String[] labels = new String[endpoints.length + 1];
        for (int i = 0; i < endpoints.length; i++) labels[i] = endpoints[i].label;
        labels[endpoints.length] = "✏️ כתובת API מותאמת אישית…";
        appDialog()
                .setTitle("בחר כתובת לבדיקה • התוצאה תועתק ללוח")
                .setItems(labels, (dialog, which) -> {
                    if (which == endpoints.length) promptCustomApiPath();
                    else promptApiGrepFilter(endpoints[which]);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    /**
     * Allows testing authenticated GET routes on this API host only.
     * Restrict input to relative /api/ paths so credentials are never sent to another host.
     */
    private void promptCustomApiPath() {
        String previous = prefs.getString("api_custom_path", "/api/orders?search=");
        final EditText pathInput = field("למשל /api/orders?search=שמן", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pathInput.setSingleLine(true);
        pathInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        pathInput.setText(previous);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(8), dp(4), dp(4));
        content.addView(muted("הזן נתיב יחסי של ה־API, כולל פרמטרים אם צריך. מתחילים ב־/api/ ולא מזינים כתובת אתר מלאה. הנתיב האחרון נשמר לשימוש הבא. הבדיקה היא GET בלבד.", 13));
        content.addView(pathInput, lp(-1, dp(52), 0, 8));
        appDialog().setTitle("כתובת API מותאמת אישית")
                .setView(content)
                .setNegativeButton("ביטול", null)
                .setNeutralButton("בדיקת כתובת", (d, w) -> {
                    String path = pathInput.getText().toString().trim();
                    if (!isValidCustomApiPath(path)) {
                        toast("כתובת לא תקינה. יש להזין נתיב שמתחיל ב־/api/ ללא רווחים או כתובת אתר מלאה.", true);
                        return;
                    }
                    prefs.edit().putString("api_custom_path", path).apply();
                    promptApiGrepFilter(new JsonEndpointChoice("כתובת מותאמת אישית", path));
                })
                .show();
    }

    private boolean isValidCustomApiPath(String path) {
        if (path == null || path.length() < 6 || path.length() > 500) return false;
        if (!path.startsWith("/api/") || path.startsWith("//")) return false;
        if (path.contains("://") || path.contains("#") || path.contains("\\\\") || path.contains(" ")) return false;
        for (int i = 0; i < path.length(); i++) {
            if (Character.isISOControl(path.charAt(i))) return false;
        }
        return true;
    }

    private void promptApiGrepFilter(final JsonEndpointChoice endpoint) {
        final EditText filter = field("מילים לסינון, מופרדות בפסיק (ריק = הכול)", InputType.TYPE_CLASS_TEXT);
        filter.setSingleLine(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(8), dp(4), dp(4));
        content.addView(muted("אפשר להקליד מילה או כמה ביטויים. יועתקו רק שורות שמכילות לפחות אחד מהם, ללא תלות באותיות גדולות/קטנות.", 13));
        content.addView(filter, lp(-1, dp(52), 8, 0));
        appDialog().setTitle("סינון פלט API • " + endpoint.path)
                .setView(content)
                .setNegativeButton("ביטול", null)
                .setNeutralButton("בדיקה ללא סינון", (d, w) -> runSingleJsonEndpoint(endpoint, ""))
                .setPositiveButton("סנן והריץ", (d, w) -> runSingleJsonEndpoint(endpoint, filter.getText().toString().trim()))
                .show();
    }

    private String grepDiagnosticOutput(String safeBody, String filterText) {
        if (filterText == null || filterText.trim().isEmpty()) return safeBody;
        String[] terms = filterText.split("[,\\n\\r]+");
        List<String> matches = new ArrayList<>();
        String[] lines = safeBody.split("\\r?\\n");
        for (String line : lines) {
            String lowerLine = line.toLowerCase(Locale.ROOT);
            for (String rawTerm : terms) {
                String term = rawTerm.trim().toLowerCase(Locale.ROOT);
                if (!term.isEmpty() && lowerLine.contains(term)) {
                    matches.add(line);
                    break;
                }
            }
        }
        if (matches.isEmpty()) return "(grep: לא נמצאו שורות תואמות)";
        return "grep — שורות תואמות בלבד (" + matches.size() + "):\n" + TextUtils.join("\n", matches);
    }

    private void runSingleJsonEndpoint(final JsonEndpointChoice endpoint, final String filterText) {
        if (!hasToken()) { toast("צריך להתחבר לפני בדיקת כתובת", true); return; }
        toast("מריץ: " + endpoint.label.split(" — ")[0], false);
        executor.execute(() -> {
            String clipboardText;
            int status = -1;
            try {
                HttpResult response = api("GET", endpoint.path, null);
                status = response.code;
                String safeBody = redactSensitiveOutput(response.body == null ? "" : response.body);
                String filteredBody = grepDiagnosticOutput(safeBody, filterText);
                clipboardText = "כתובת: " + endpoint.path + "\n"
                        + "שיטה: GET\nHTTP: " + status + "\n"
                        + (filterText == null || filterText.trim().isEmpty()
                            ? "תשובה (לאחר הסתרת מידע רגיש):\n"
                            : "תשובת grep (לאחר הסתרת מידע רגיש; מונחים: " + filterText + "):\n")
                        + filteredBody;
            } catch (Exception e) {
                // Do not put exception messages on screen or clipboard: they may contain request data.
                clipboardText = "כתובת: " + endpoint.path + "\nשיטה: GET\nהבקשה נכשלה (" + e.getClass().getSimpleName() + ").";
            }
            final String result = clipboardText;
            final int httpStatus = status;
            runOnUiThread(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm == null) {
                        toast("הבדיקה הסתיימה, אך הלוח אינו זמין", true);
                        return;
                    }
                    cm.setPrimaryClip(ClipData.newPlainText("תשובת JSON מסוננת", result));
                    toast(httpStatus < 0 ? "פרטי שגיאה בטוחים הועתקו ללוח" :
                            "תשובת הכתובת הועתקה ללוח (HTTP " + httpStatus + ")", httpStatus < 200 || httpStatus >= 300);
                } catch (Exception ignored) {
                    toast("ההעתקה ללוח נכשלה", true);
                }
            });
        });
    }

    /**
     * Recursively removes secret-bearing JSON fields before output. Also filters non-JSON bodies.
     * This is a best-effort safety filter, not a guarantee that every possible secret format is known.
     */
    private String redactSensitiveOutput(String body) {
        if (body == null || body.isEmpty()) return "(תשובה ריקה)";
        try {
            Object parsed = new JSONTokener(body).nextValue();
            if (parsed instanceof JSONObject) return redactJsonObject((JSONObject) parsed).toString(2);
            if (parsed instanceof JSONArray) return redactJsonArray((JSONArray) parsed).toString(2);
        } catch (Exception ignored) { }
        return redactSensitiveText(body);
    }

    private JSONObject redactJsonObject(JSONObject source) {
        JSONObject safe = new JSONObject();
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.opt(key);
            if (isSensitiveFieldName(key)) {
                try { safe.put(key, "[הוסתר]"); } catch (Exception ignored) { }
            } else {
                try {
                    if (value instanceof JSONObject) safe.put(key, redactJsonObject((JSONObject) value));
                    else if (value instanceof JSONArray) safe.put(key, redactJsonArray((JSONArray) value));
                    else if (value instanceof String) safe.put(key, redactSensitiveText((String) value));
                    else safe.put(key, value == null ? JSONObject.NULL : value);
                } catch (Exception ignored) { }
            }
        }
        return safe;
    }

    private JSONArray redactJsonArray(JSONArray source) {
        JSONArray safe = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            Object value = source.opt(i);
            if (value instanceof JSONObject) safe.put(redactJsonObject((JSONObject) value));
            else if (value instanceof JSONArray) safe.put(redactJsonArray((JSONArray) value));
            else if (value instanceof String) safe.put(redactSensitiveText((String) value));
            else safe.put(value == null ? JSONObject.NULL : value);
        }
        return safe;
    }

    private boolean isSensitiveFieldName(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return k.contains("token") || k.contains("authorization") || k.contains("bearer")
                || k.contains("password") || k.contains("passwd") || k.contains("secret")
                || k.contains("cookie") || k.contains("sessionid") || k.contains("apikey")
                || k.contains("creditcard") || k.equals("card") || k.contains("cardnumber")
                || k.contains("cardno") || k.equals("pan") || k.contains("cvv") || k.contains("cvc")
                || k.contains("securitycode") || k.contains("iban") || k.contains("bankaccount")
                || k.contains("email") || k.contains("phone") || k.contains("mobile");
    }

    private String redactSensitiveText(String text) {
        if (text == null || text.isEmpty()) return "";
        String safe = text;
        safe = safe.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "Bearer [הוסתר]");
        safe = safe.replaceAll("eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]*", "[JWT הוסתר]");
        safe = safe.replaceAll("(?i)(\\\"?(?:access_token|refresh_token|token|authorization|password|passwd|secret|api_key|apikey|cvv|cvc|card_number|cardnumber|pan|iban|email|phone|mobile)\\\"?\\s*[:=]\\s*)\\\"?[^\\\",\\s}]+\\\"?", "$1\\\"[הוסתר]\\\"");
        // Mask long payment-card-like digit sequences even when the response is not valid JSON.
        safe = safe.replaceAll("(?<![0-9])[0-9](?:[ -]?[0-9]){12,18}(?![0-9])", "[מספר רגיש הוסתר]");
        return safe;
    }

    private boolean jsonHasMatchingItemId(Object value, String targetId) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (("id".equals(normalized) || "itemid".equals(normalized)
                        || "itemsalesid".equals(normalized) || "productid".equals(normalized)
                        || "catalogitemid".equals(normalized))
                        && child != null && !JSONObject.NULL.equals(child)
                        && targetId.equals(String.valueOf(child).trim())) return true;
                if (jsonHasMatchingItemId(child, targetId)) return true;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                if (jsonHasMatchingItemId(array.opt(i), targetId)) return true;
            }
        }
        return false;
    }

    private boolean jsonContainsItemName(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                Object child = object.opt(keys.next());
                if (child instanceof String) {
                    String text = ((String) child).toLowerCase(Locale.ROOT);
                    if (text.contains("אפונה") || text.contains("קייטי") || text.contains("peas")) return true;
                }
                if (jsonContainsItemName(child)) return true;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                if (jsonContainsItemName(array.opt(i))) return true;
            }
        }
        return false;
    }

    private String safeBodyForDiagnostic(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        // Keep the diagnostic useful without allowing a very large server error body into the dialog.
        return trimmed.length() > 1200 ? trimmed.substring(0, 1200) : trimmed;
    }

    private void showApiLog() {
        String text;
        synchronized (apiLog) { text = apiLog.isEmpty() ? "עדיין אין רישום תקשורת." : TextUtils.join("\n", apiLog); }
        final String shown = text;
        appDialog().setTitle("יומן תקשורת")
                .setMessage(shown)
                .setPositiveButton("סגירה", null)
                .setNeutralButton("שיתוף", (d, w) -> shareText("יומן תקשורת משנת יוסף", shown))
                .show();
    }

    private String tokenStatus() {
        long exp = prefs.getLong("expires_at", 0L);
        if (exp <= 0) return "🔑 תוקף ההתחברות לא ידוע";
        long left = (exp - System.currentTimeMillis()) / 1000L;
        String until = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date(exp));
        if (left <= 0) return "🔑 ההתחברות פגה (" + until + ")";
        long d = left / 86400, h = (left % 86400) / 3600, m = (left % 3600) / 60;
        String rest = d > 0 ? d + " ימים " + h + " שעות" : (h > 0 ? h + " שעות " + m + " דקות" : m + " דקות");
        return "🔑 תקף עד " + until + " (נותרו " + rest + ")";
    }

    private static class ContactOption {
        final String title;
        final String value;
        final String explanation;
        final boolean email;
        final boolean fax;
        ContactOption(String title, String value, String explanation, boolean email, boolean fax) {
            this.title = title;
            this.value = value;
            this.explanation = explanation;
            this.email = email;
            this.fax = fax;
        }
    }

    private void showContactOptions() {
        final ContactOption[] contacts = new ContactOption[] {
                new ContactOption("📞 מוקד אוטומטי להזמנות — 077-316-8080",
                        "077-316-8080", "הזמנות, שינוי תחנה ובדיקת שעות.", false, false),
                new ContactOption("☎️ מענה אנושי — 08-9798574",
                        "08-9798574", "בעיות, בירורים ופניות כלליות.", false, false),
                new ContactOption("📠 פקס — 153-8-9798574",
                        "153-8-9798574", "שליחת טפסים, אישורים ומסמכים בכתב.", false, true),
                new ContactOption("✉️ תמיכה טכנית — support@mishnatyosef.org",
                        "support@mishnatyosef.org", "תמיכה טכנית ובעיות באזור האישי.", true, false),
                new ContactOption("📷 צילומי מוצרים פגומים וחוסרים — A0773168080@gmail.com",
                        "A0773168080@gmail.com", "שליחת צילומי מוצרים פגומים ודיווח על חוסרים לצורך זיכוי.", true, false),
                new ContactOption("🏢 הנהלה וספקים — admin@m-y.org.il",
                        "admin@m-y.org.il", "פניות ניהוליות וספקים.", true, false)
        };

        // Each contact is a non-clickable card. Its action buttons are visible immediately,
        // so the user never needs to open the contact first to reach an action.
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(6), dp(12), dp(8));
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        for (final ContactOption contact : contacts) {
            LinearLayout contactCard = card(10);
            TextView title = text(contact.title, 14, true);
            title.setTextColor(INK);
            title.setGravity(Gravity.START);
            title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            contactCard.addView(title, lp(-1, -2, 0, 3));

            TextView explanation = muted(contact.explanation, 12);
            explanation.setGravity(Gravity.START);
            explanation.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);
            contactCard.addView(explanation, lp(-1, -2, 0, 8));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER);
            actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            if (contact.email) {
                addContactAction(actions, "✉️ מייל", () -> composeContactEmail(contact));
                addContactAction(actions, "📋 העתקה", () -> copyText(contact.value));
                addContactAction(actions, "📤 שיתוף", () -> shareText(contact.title, contact.value));
            } else if (contact.fax) {
                addContactAction(actions, "📋 העתקה", () -> copyText(contact.value));
                addContactAction(actions, "📤 שיתוף", () -> shareText(contact.title, contact.value));
            } else {
                addContactAction(actions, "📞 להתקשר", () -> dialContact(contact.value));
                addContactAction(actions, "📋 העתקה", () -> copyText(contact.value));
                addContactAction(actions, "📤 שיתוף", () -> shareText(contact.title, contact.value));
            }
            contactCard.addView(actions, lp(-1, dp(38), 0, 0));
            list.addView(contactCard, lp(-1, -2, 0, 8));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        scroll.setPadding(dp(4), dp(2), dp(4), 0);
        appDialog().setTitle("יצירת קשר עם משנת יוסף")
                .setView(scroll)
                .setPositiveButton("סגירה", null)
                .show();
    }

    private void addContactAction(LinearLayout row, String label, Runnable action) {
        TextView button = smallChip(label, TONAL);
        button.setTextSize(12);
        button.setMinHeight(dp(34));
        button.setPadding(dp(3), dp(3), dp(3), dp(3));
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
        button.setOnClickListener(v -> action.run());
    }

    private void dialContact(String phone) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.replaceAll("[^0-9+]", ""))));
        } catch (Exception e) {
            toast("לא נמצאה אפליקציה לחיוג במכשיר", true);
        }
    }

    private void smsContact(String phone) {
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone.replaceAll("[^0-9+]", ""))));
        } catch (Exception e) {
            toast("לא נמצאה אפליקציה לשליחת הודעות", true);
        }
    }

    private void composeContactEmail(ContactOption contact) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + contact.value));
            intent.putExtra(Intent.EXTRA_SUBJECT, "פנייה בנושא: " + contact.explanation);
            intent.putExtra(Intent.EXTRA_TEXT, "\n\nפרטי הפנייה:\n");
            startActivity(Intent.createChooser(intent, "שליחת מייל"));
        } catch (Exception e) {
            toast("לא נמצאה אפליקציית דואר במכשיר", true);
        }
    }

    private void showTextDialog(String title, String message) {
        appDialog().setTitle(title).setMessage(message).setPositiveButton("סגירה", null).show();
    }

    private void addHScroll(View content, LinearLayout.LayoutParams params) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(content);
        root.addView(hs, params);
    }

    private void shareText(String subject, String text) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, subject);
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "שיתוף"));
        } catch (Exception e) {
            toast("לא ניתן לפתוח את חלון השיתוף", true);
        }
    }

    private void shareWhatsapp(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                i.setPackage(pkg);
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        i.setPackage(null);
        try { startActivity(Intent.createChooser(i, "שיתוף")); } catch (Exception e) { toast("לא ניתן לשתף", true); }
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("order", text));
            toast("הועתק ללוח", false);
        }
    }

    private void showShareMenu(final String text) {
        String[] labels = {"📋 העתקה ללוח", "🟢 שליחה בווצאפ", "📤 מייל / הודעה / אחר"};
        appDialog().setTitle("שיתוף").setItems(labels, (d, which) -> {
            if (which == 0) copyText(text);
            else if (which == 1) shareWhatsapp(text);
            else shareText("הזמנה משנת יוסף", text);
        }).show();
    }

    private String safeName(String name) {
        String n = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|\\r\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        if (n.length() > 80) n = n.substring(0, 80).trim();
        return n.isEmpty() ? "הזמנה" : n;
    }

    // ======================================================================== order text / export / import

    private String pidOf(JSONObject p) {
        String id = first(p, "id", "itemID");
        if (id.isEmpty()) id = first(p.optJSONObject("product"), "id");
        return id;
    }

    private String currentTitle() {
        String t = saleTitle(featureSale);
        return t.isEmpty() ? "מכירה " + first(featureSale, "id") : t;
    }

    private List<OrderLine> currentOrderLines() {
        List<OrderLine> out = new ArrayList<>();
        for (JSONObject p : featureProducts) {
            Integer q = featureQuantities.get(sidOf(p));
            if (q == null || q <= 0) continue;
            OrderLine l = new OrderLine();
            l.sid = sidOf(p);
            l.name = productName(p);
            l.price = productPrice(p);
            l.amount = q;
            l.raw = p;
            out.add(l);
        }
        return out;
    }

    private String orderText(String title, long orderId, String when, List<OrderLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n").append("הזמנה ").append(orderId).append("\n");
        sb.append(when == null || when.isEmpty() ? "נוצר: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date()) : "הוזמן: " + when).append("\n\n");
        int n = 0;
        double units = 0, total = 0;
        for (OrderLine l : lines) {
            if (l.amount <= 0) continue;
            n++;
            units += l.amount;
            total += l.price * l.amount;
            sb.append(n).append(". ").append(fmtNum(l.amount)).append(" × ").append(l.name);
            if (l.price > 0) sb.append("  (").append(money(l.price * l.amount)).append(")");
            sb.append("\n");
        }
        sb.append("\n------------------------------\n").append(n).append(" פריטים | ").append(fmtNum(units)).append(" יחידות | סה״כ ").append(money(total)).append("\n");
        return sb.toString();
    }

    private String buildExportJson(String saleTitle, long saleId, long orderId, List<OrderLine> lines) {
        try {
            JSONArray items = new JSONArray();
            double units = 0, total = 0;
            for (OrderLine l : lines) {
                if (l.amount <= 0) continue;
                JSONObject it = new JSONObject();
                String pid = pidOf(l.raw);
                if (!pid.isEmpty()) {
                    try { it.put("id", Long.parseLong(pid)); } catch (NumberFormatException nfe) { it.put("id", pid); }
                }
                it.put("name", l.name);
                it.put("amount", l.amount == Math.rint(l.amount) ? (Object) Long.valueOf((long) l.amount) : (Object) Double.valueOf(l.amount));
                it.put("price", l.price > 0 ? (Object) Double.valueOf(l.price) : (Object) JSONObject.NULL);
                items.put(it);
                units += l.amount;
                total += l.price * l.amount;
            }
            JSONObject o = new JSONObject();
            o.put("format", "mishnat-order");
            o.put("version", 2);
            o.put("exported_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
            o.put("sale_id", saleId);
            o.put("sale_title", saleTitle);
            o.put("order_id", orderId);
            o.put("units", units);
            o.put("total", Math.round(total * 100) / 100.0);
            o.put("items", items);
            return o.toString(2);
        } catch (Exception e) {
            return "";
        }
    }

    private void startExport(String name, String json) {
        if (json == null || json.isEmpty()) { toast("אין מה לייצא", false); return; }
        pendingExport = json;
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, safeName(name) + ".json");
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast("לא ניתן לפתוח בחירת קובץ", true);
        }
    }

    private void pickImportFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast("לא ניתן לפתוח בחירת קובץ", true);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("no stream");
                os.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                toast("הקובץ נשמר", false);
            } catch (Exception e) {
                toast("השמירה נכשלה", true);
            }
        } else if (requestCode == REQ_IMPORT) {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new Exception("no stream");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > 2000000) { in.close(); throw new Exception("הקובץ גדול מדי"); }
                    bo.write(buf, 0, n);
                }
                in.close();
                String text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                if (text.contains("\uFFFD")) text = new String(bo.toByteArray(), java.nio.charset.Charset.forName("windows-1255"));
                if (text.startsWith("\uFEFF")) text = text.substring(1);
                startImport(text, "קובץ");
            } catch (Exception e) {
                toast("לא הצלחתי לקרוא את הקובץ: " + errText(e, "קובץ לא תקין"), true);
            }
        }
    }

    private void parseImport(String raw, List<ImportEntry> entries, List<String> bad) throws Exception {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            JSONArray rows;
            if (trimmed.startsWith("{")) {
                rows = new JSONObject(trimmed).optJSONArray("items");
                if (rows == null) throw new Exception("בקובץ אין רשימת פריטים (items)");
            } else {
                rows = new JSONArray(trimmed);
            }
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.optJSONObject(i);
                if (r == null) { bad.add("פריט " + (i + 1) + ": מבנה לא תקין"); continue; }
                String name = first(r, "name"), pid = first(r, "id");
                String label = !name.isEmpty() ? name : (!pid.isEmpty() ? "מזהה " + pid : "פריט " + (i + 1));
                double amt = num(r.opt("amount"));
                if (name.isEmpty() && pid.isEmpty()) bad.add(label + ": אין שם ואין מזהה");
                else if (amt <= 0 || amt > 999) bad.add(label + ": כמות לא תקינה");
                else {
                    ImportEntry ie = new ImportEntry();
                    ie.pid = pid;
                    ie.name = name;
                    ie.amount = Math.round(amt);
                    entries.add(ie);
                }
            }
        } else {
            for (String rawLine : raw.split("\\r?\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int bar = line.lastIndexOf('|');
                String q = line;
                int amt = 1;
                if (bar >= 0) {
                    String tail = line.substring(bar + 1).trim();
                    if (tail.matches("\\d{1,4}")) { q = line.substring(0, bar).trim(); amt = Integer.parseInt(tail); }
                }
                if (amt <= 0 || amt > 999) { bad.add(q + ": כמות לא תקינה"); continue; }
                ImportEntry ie = new ImportEntry();
                ie.query = q;
                ie.amount = amt;
                entries.add(ie);
            }
        }
    }

    private void startImport(String raw, String label) {
        if (featureOrderId <= 0 || featureProducts.isEmpty()) { toast("פתחו קודם הזמנה של מכירה", true); return; }
        List<ImportEntry> entries = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        try {
            parseImport(raw, entries, bad);
        } catch (Exception e) {
            toast("לא הצלחתי לקרוא את הקובץ: " + errText(e, "מבנה לא תקין"), true);
            return;
        }
        startImportEntries(entries, bad, label);
    }

    private List<JSONObject> matchProducts(ImportEntry e) {
        List<JSONObject> hits = new ArrayList<>();
        if (e.query != null) {
            String q = e.query.trim();
            if (q.matches("\\d+")) for (JSONObject p : featureProducts) if (q.equals(pidOf(p))) hits.add(p);
            if (hits.isEmpty()) for (JSONObject p : featureProducts) if (q.equals(productName(p).trim())) hits.add(p);
            if (hits.isEmpty()) for (JSONObject p : featureProducts) if (productName(p).contains(q)) hits.add(p);
        } else {
            if (!e.pid.isEmpty()) for (JSONObject p : featureProducts) if (e.pid.equals(pidOf(p))) hits.add(p);
            if (hits.size() > 1 && !e.name.isEmpty()) {
                List<JSONObject> narrowed = new ArrayList<>();
                for (JSONObject p : hits) if (e.name.equals(productName(p).trim())) narrowed.add(p);
                if (!narrowed.isEmpty()) hits = narrowed;
            }
            if (hits.isEmpty() && !e.name.isEmpty()) for (JSONObject p : featureProducts) if (e.name.equals(productName(p).trim())) hits.add(p);
        }
        return hits;
    }

    private void appendList(StringBuilder sb, String title, List<String> list) {
        if (list.isEmpty()) return;
        sb.append("\n").append(title).append(" (").append(list.size()).append("):\n");
        for (int i = 0; i < Math.min(8, list.size()); i++) sb.append("  - ").append(list.get(i)).append("\n");
        if (list.size() > 8) sb.append("  …ועוד ").append(list.size() - 8).append("\n");
    }

    private void startImportEntries(List<ImportEntry> entries, List<String> bad, String label) {
        if (entries.isEmpty()) {
            showTextDialog("ייבוא", "לא נמצאו פריטים תקינים לייבוא." + (bad.isEmpty() ? "" : "\n\n" + TextUtils.join("\n", bad)));
            return;
        }
        final List<PlanItem> plan = new ArrayList<>();
        List<String> notFound = new ArrayList<>(), ambiguous = new ArrayList<>(), blocked = new ArrayList<>(), dups = new ArrayList<>();
        int same = 0;
        Set<String> seen = new HashSet<>();
        for (ImportEntry entry : entries) {
            String lab = !entry.name.isEmpty() ? entry.name : (entry.query != null ? entry.query : "מזהה " + entry.pid);
            List<JSONObject> hits = matchProducts(entry);
            if (hits.isEmpty()) { notFound.add(lab); continue; }
            if (hits.size() > 1) { ambiguous.add(lab); continue; }
            JSONObject p = hits.get(0);
            String sid = sidOf(p), name = productName(p);
            if (!seen.add(sid)) { dups.add(name); continue; }
            int amount = (int) entry.amount;
            int cur = featureQuantities.containsKey(sid) ? featureQuantities.get(sid) : 0;
            if (cur > 0 && cur == amount) { same++; continue; }
            if (!(cur > 0 && amount < cur)) {
                if (truthy(p.opt("disable"))) { blocked.add(name + ": לא זמין"); continue; }
                if (isSoldOut(p)) { blocked.add(name + ": אזל"); continue; }
            }
            PlanItem pi = new PlanItem();
            pi.p = p;
            pi.name = name;
            pi.amount = amount;
            pi.cur = cur;
            plan.add(pi);
        }
        StringBuilder sb = new StringBuilder();
        if (!plan.isEmpty()) {
            sb.append("יתבצעו ").append(plan.size()).append(" שינויים:\n");
            for (int i = 0; i < Math.min(25, plan.size()); i++) {
                PlanItem pi = plan.get(i);
                sb.append(pi.cur > 0 ? "✏️ " + pi.name + ": " + pi.cur + " ← " + pi.amount : "➕ " + pi.amount + " × " + pi.name).append("\n");
            }
            if (plan.size() > 25) sb.append("…ועוד ").append(plan.size() - 25).append("\n");
        } else {
            sb.append("אין מה לשנות בהזמנה.\n");
        }
        if (same > 0) sb.append("\n➖ ללא שינוי (כבר בהזמנה באותה כמות): ").append(same).append("\n");
        appendList(sb, "⚠️ לא נמצאו במכירה", notFound);
        appendList(sb, "⚠️ מתאימים ליותר מפריט אחד", ambiguous);
        appendList(sb, "⛔ דולגו", blocked);
        appendList(sb, "🔁 כפולים", dups);
        appendList(sb, "❓ שורות לא תקינות", bad);
        if (plan.isEmpty()) { showTextDialog("ייבוא: " + label, sb.toString()); return; }
        appDialog().setTitle("ייבוא: " + label).setMessage(sb.toString())
                .setNegativeButton("ביטול", null)
                .setPositiveButton("החלה", (d, w) -> applyPlan(plan))
                .show();
    }

    /** Runs the plan on the server one by one (a short pause between calls, like the Python script). */
    private void applyPlan(final List<PlanItem> plan) {
        requireUnlock(() -> applyPlanNow(plan));
    }

    private void applyPlanNow(final List<PlanItem> plan) {
        final TextView msg = text("מתחיל…", 15, false);
        msg.setPadding(dp(22), dp(16), dp(22), dp(8));
        msg.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final AlertDialog dlg = appDialog().setTitle("מעדכן הזמנה").setView(msg).setCancelable(false).create();
        dlg.show();
        styleAppDialog(dlg);
        toast("⏳ מעדכן " + plan.size() + " פריטים…", false);
        final long orderId = featureOrderId;
        executor.execute(() -> {
            final List<PlanItem> succeeded = new ArrayList<>();
            final List<String> failed = new ArrayList<>();
            boolean authLost = false;
            for (int i = 0; i < plan.size(); i++) {
                final PlanItem pi = plan.get(i);
                final int idx = i;
                runOnUiThread(() -> msg.setText("מעדכן " + (idx + 1) + " מתוך " + plan.size() + "…\n" + pi.name));
                String err = syncSetAmount(pi.p, pi.amount, orderId);
                if (err == null) succeeded.add(pi);
                else {
                    failed.add(pi.name + ": " + err);
                    if (err.equals(new AuthException().getMessage())) { authLost = true; break; }
                }
                try { Thread.sleep(350); } catch (InterruptedException ie) { break; }
            }
            final boolean needLogin = authLost;
            runOnUiThread(() -> {
                for (PlanItem pi : succeeded) featureQuantities.put(sidOf(pi.p), pi.amount);
                persistQuantities();
                scheduleOrderSync();
                dlg.dismiss();
                StringBuilder res = new StringBuilder("✅ עודכנו: " + succeeded.size() + " מתוך " + plan.size());
                if (!failed.isEmpty()) {
                    res.append("\n\n❌ נכשלו (").append(failed.size()).append("):\n");
                    for (int i = 0; i < Math.min(8, failed.size()); i++) res.append("  - ").append(failed.get(i)).append("\n");
                }
                toast("✔ עודכנו " + succeeded.size() + " מתוך " + plan.size(), false);
                showTextDialog("סיכום", res.toString());
                refreshCurrentOrderScreen();
                if (needLogin) showLogin();
            });
        });
    }

    private String syncSetAmount(JSONObject p, int newQty, long orderId) {
        String sid = sidOf(p);
        int serverAmount = (int) Math.round(num(p.opt("amount")));
        try {
            HttpResult result = null;
            if (newQty == 0) {
                if (serverAmount > 0) result = api("POST", "/api/remove_item/" + orderId + "/" + sid, null);
            } else {
                JSONObject item = new JSONObject(p.toString());
                item.put("amount", newQty);
                if (!item.has("selected_subitems") || item.isNull("selected_subitems")) item.put("selected_subitems", new JSONArray());
                result = api("POST", (serverAmount > 0 ? "/api/update_item/" : "/api/add_item/") + orderId, item);
            }
            if (result != null && (result.code < 200 || result.code >= 300)) {
                String msg = apiError(result);
                if (msg.contains("אזל")) { try { p.put("available", 0); } catch (Exception ignored) { } }
                return msg;
            }
            p.put("amount", newQty);
            return null;
        } catch (Exception e) {
            return errText(e, "העדכון נכשל");
        }
    }

    private void refreshCurrentOrderScreen() {
        if (screenKind == 2) showOrderSummary();
        else if (screenKind == 1) renderFeatureProducts();
    }

    // ======================================================================== order actions menu

    private void showOrderQuickMenu(View anchor) {
        final List<String> labels = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        labels.add(nightMode ? "☀️ מעבר למצב יום" : "🌙 הפעלת מצב לילה"); actions.add(11);
        labels.add((prefs.getBoolean("experimental_skip_cached_quantity_grid_rebuild", false) ? "✓ " : "") + "ביטול רענון עמוד בהזמנות");
        actions.add(14);
        if (closedOrderReadOnly) {
            labels.add("פרטי המכירה"); actions.add(0);
            labels.add("📐 שינוי גודל כרטיסי המוצרים"); actions.add(10);
            labels.add("📤 שיתוף סיכום ההזמנה השמורה"); actions.add(12);
            labels.add("📦 ייצוא ההזמנה השמורה לקובץ"); actions.add(13);
        } else {
            if (!summaryFromHistory) {
                labels.add("פרטי המכירה"); actions.add(0);
                labels.add("רענון מוצרים"); actions.add(1);
                labels.add("📐 שינוי גודל כרטיסי המוצרים"); actions.add(10);
            }
            if (featureOrderId > 0) {
                labels.add("📤 שיתוף סיכום ההזמנה"); actions.add(2);
                labels.add("📦 ייצוא הזמנה לקובץ"); actions.add(3);
                labels.add("📥 ייבוא הזמנה מקובץ"); actions.add(4);
                labels.add("📝 הדבקת רשימת קניות"); actions.add(5);
                labels.add("⭐ טעינת מועדפים להזמנה"); actions.add(6);
                labels.add("💾 שמירת כמויות למועדפים"); actions.add(7);
                labels.add("🧹 ניקוי כמויות שמורות"); actions.add(8);
                labels.add("🗑 מחיקת ההזמנה"); actions.add(9);
            }
        }

        // A compact Apple-inspired popover, anchored only to the order's three-dot button.
        LinearLayout popupPanel = new LinearLayout(this);
        popupPanel.setOrientation(LinearLayout.VERTICAL);
        popupPanel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        popupPanel.setPadding(dp(12), dp(10), dp(12), dp(12));
        popupPanel.setBackground(shape(SURFACE, 20, LINE));

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView heading = text("אפשרויות נוספות", 17, true);
        heading.setTextColor(INK);
        heading.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        headingRow.addView(heading, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView close = btn("✕", FLAT);
        close.setContentDescription("סגירת אפשרויות ההזמנה");
        close.setMinHeight(dp(36));
        close.setPadding(dp(8), dp(2), dp(8), dp(2));
        headingRow.addView(close, new LinearLayout.LayoutParams(dp(38), dp(36)));
        popupPanel.addView(headingRow, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.topMargin = dp(3);
        dividerLp.bottomMargin = dp(7);
        popupPanel.addView(divider, dividerLp);

        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setFillViewport(false);
        menuScroll.setVerticalScrollBarEnabled(labels.size() > 7);
        LinearLayout menuRows = new LinearLayout(this);
        menuRows.setOrientation(LinearLayout.VERTICAL);
        menuRows.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        final PopupWindow[] popupRef = new PopupWindow[1];
        for (int i = 0; i < labels.size(); i++) {
            final int action = actions.get(i);
            TextView item = text(labels.get(i), 14, true);
            item.setTextColor(INK);
            item.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            item.setMinHeight(dp(42));
            item.setPadding(dp(12), dp(7), dp(12), dp(7));
            item.setBackground(ripple(shape(SURFACE, 12, LINE), 12));
            item.setFocusable(true);
            item.setContentDescription(labels.get(i));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-1, -2);
            itemLp.bottomMargin = dp(4);
            menuRows.addView(item, itemLp);
            item.setOnClickListener(v -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                runOrderQuickMenuAction(action);
            });
        }
        menuScroll.addView(menuRows, new ScrollView.LayoutParams(-1, -2));
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        int desiredRowsHeight = dp(labels.size() * 46);
        int maxRowsHeight = Math.min(dp(460), displayHeight * 58 / 100);
        int rowsHeight = Math.max(dp(46), Math.min(desiredRowsHeight, maxRowsHeight));
        popupPanel.addView(menuScroll, new LinearLayout.LayoutParams(-1, rowsHeight));

        LinearLayout popupContent = new LinearLayout(this);
        popupContent.setOrientation(LinearLayout.VERTICAL);
        popupContent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        popupContent.setPadding(0, 0, 0, 0);
        View pointer = new View(this) {
            private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float centerX = getWidth() - dp(24);
                float tipY = getHeight();
                Path triangle = new Path();
                triangle.moveTo(centerX, 0);
                triangle.lineTo(centerX - dp(8), tipY);
                triangle.lineTo(centerX + dp(8), tipY);
                triangle.close();
                pointerPaint.setColor(SURFACE);
                pointerPaint.setStyle(Paint.Style.FILL);
                canvas.drawPath(triangle, pointerPaint);
                pointerPaint.setColor(LINE);
                pointerPaint.setStyle(Paint.Style.STROKE);
                pointerPaint.setStrokeWidth(dp(1));
                canvas.drawLine(centerX - dp(8), tipY, centerX, 0, pointerPaint);
                canvas.drawLine(centerX, 0, centerX + dp(8), tipY, pointerPaint);
            }
        };
        popupContent.addView(pointer, new LinearLayout.LayoutParams(-1, dp(9)));
        popupPanel.setElevation(0);
        popupContent.addView(popupPanel, new LinearLayout.LayoutParams(-1, -2));

        int popupWidth = Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(360));
        PopupWindow popup = new PopupWindow(popupContent, popupWidth, -2, true);
        popupRef[0] = popup;
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setTouchable(true);
        popup.setElevation(dp(12));
        popup.setClippingEnabled(true);
        popup.setOnDismissListener(() -> anchor.setSelected(false));
        close.setOnClickListener(v -> popup.dismiss());
        anchor.setSelected(true);
        popup.showAsDropDown(anchor, 0, -dp(7), Gravity.START);
    }

    private void runOrderQuickMenuAction(int action) {
        switch (action) {
            case 0: showSaleDetails(featureSale); break;
            case 1: refreshProducts(featureSale, false); break;
            case 2: showShareMenu(orderText(currentTitle(), featureOrderId, "", currentOrderLines())); break;
            case 3: startExport("הזמנה " + currentTitle(), buildExportJson(currentTitle(), featureSale == null ? 0 : featureSale.optLong("id", 0), featureOrderId, currentOrderLines())); break;
            case 4: pickImportFile(); break;
            case 5: showPasteWishlist(); break;
            case 6: orderFavourites(); break;
            case 7: saveFavQuantities(); break;
            case 8: clearFavQuantities(); break;
            case 9: confirmDeleteOrder(); break;
            case 10: showCardSizeDialog(); break;
            case 11: toggleNightMode(); break;
            case 12: showShareMenu(cachedClosedOrderText()); break;
            case 13: startExport("הזמנה שמורה " + currentTitle(), buildExportJson(currentTitle(), featureSale == null ? 0 : featureSale.optLong("id", 0), 0, currentOrderLines())); break;
            case 14:
                boolean skipPageRefresh = !prefs.getBoolean("experimental_skip_cached_quantity_grid_rebuild", false);
                prefs.edit().putBoolean("experimental_skip_cached_quantity_grid_rebuild", skipPageRefresh).apply();
                toast(skipPageRefresh ? "ביטול רענון עמוד הופעל בהזמנות" : "רענון עמוד ההזמנות הוחזר", false);
                break;
        }
    }

    private int cardColumns() {
        return Math.max(1, Math.min(5, prefs.getInt("product_card_columns", 3)));
    }

    private String cardSizeLabel(int columns) {
        switch (columns) {
            case 1: return "כרטיסים ענקיים — עמודה אחת";
            case 2: return "כרטיסים גדולים — 2 עמודות";
            case 3: return "כרטיסים בינוניים — 3 עמודות";
            case 4: return "כרטיסים קטנים — 4 עמודות";
            default: return "כרטיסים קומפקטיים — 5 עמודות";
        }
    }

    private void showCardSizeDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(18));
        panel.setBackground(shape(SURFACE, 24, LINE));
        panel.setElevation(dp(12));

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("▦  פריסת כרטיסי מוצרים", 20, true);
        heading.setTextColor(INK);
        heading.setGravity(Gravity.CENTER);
        headingRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = btn("✕", FLAT);
        close.setContentDescription("סגירת הגדרות מספר העמודות");
        close.setMinHeight(dp(40));
        close.setPadding(dp(10), dp(4), dp(10), dp(4));
        headingRow.addView(close, new LinearLayout.LayoutParams(dp(44), dp(40)));
        panel.addView(headingRow, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = muted("בחרו כמה כרטיסי מוצרים יוצגו בכל שורה", 13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(3);
        subtitleParams.bottomMargin = dp(14);
        panel.addView(subtitle, subtitleParams);

        int initialColumns = cardColumns();
        TextView count = text(initialColumns + (initialColumns == 1 ? " עמודה" : " עמודות"), 22, true);
        count.setGravity(Gravity.CENTER);
        count.setTextColor(ORANGE_DARK);
        count.setBackground(ripple(shape(ORANGE_LIGHT, 14, LINE), 14));
        count.setPadding(dp(18), dp(7), dp(18), dp(7));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, dp(46));
        countParams.topMargin = dp(2);
        countParams.bottomMargin = dp(5);
        panel.addView(count, countParams);

        TextView sizeLabel = muted(cardSizeLabel(initialColumns), 13);
        sizeLabel.setGravity(Gravity.CENTER);
        panel.addView(sizeLabel, new LinearLayout.LayoutParams(-1, -2));

        SeekBar slider = new SeekBar(this);
        slider.setMax(4);
        slider.setProgress(initialColumns - 1);
        slider.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        slider.setContentDescription("מספר עמודות בכרטיסי המוצרים, בין עמודה אחת לחמש");
        if (Build.VERSION.SDK_INT >= 21) {
            slider.setProgressTintList(ColorStateList.valueOf(ORANGE));
            slider.setThumbTintList(ColorStateList.valueOf(ORANGE));
            slider.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        }
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(-1, dp(48));
        sliderParams.topMargin = dp(8);
        panel.addView(slider, sliderParams);

        LinearLayout marks = new LinearLayout(this);
        marks.setOrientation(LinearLayout.HORIZONTAL);
        marks.setGravity(Gravity.CENTER);
        marks.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int columns = 5; columns >= 1; columns--) {
            TextView mark = muted(String.valueOf(columns), 12);
            mark.setGravity(Gravity.CENTER);
            marks.addView(mark, new LinearLayout.LayoutParams(0, -2, 1));
        }
        panel.addView(marks, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = muted("פחות עמודות מציגות כרטיסים גדולים יותר. ההגדרה נשמרת ומשמשת בעמודי ההזמנה.", 12);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(10);
        panel.addView(hint, hintParams);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int columns = progress + 1;
                count.setText(columns + (columns == 1 ? " עמודה" : " עמודות"));
                sizeLabel.setText(cardSizeLabel(columns));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        slider.setContentDescription("מספר עמודות בכרטיסי המוצרים, 1 עד 5");

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = dp(16);
        panel.addView(buttons, buttonsParams);

        TextView save = btn("✓  שמירת הבחירה", PRIMARY);
        TextView cancel = btn("ביטול", OUTLINE);
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        cancelParams.setMargins(dp(8), 0, 0, 0);
        buttons.addView(cancel, cancelParams);

        AlertDialog dialog = new AlertDialog.Builder(this,
                nightMode ? android.R.style.Theme_Material_Dialog_Alert
                        : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setView(panel)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            prefs.edit().putInt("product_card_columns", slider.getProgress() + 1).apply();
            rebuildGrid(false);
            toast("מספר העמודות נשמר", false);
            dialog.dismiss();
        });
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams windowParams = dialog.getWindow().getAttributes();
                windowParams.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(440));
                windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                windowParams.gravity = Gravity.CENTER;
                windowParams.dimAmount = 0.48f;
                dialog.getWindow().setAttributes(windowParams);
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(440)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private void showPasteWishlist() {
        final EditText input = new EditText(this);
        input.setHint("שורה לכל פריט: שם או מזהה | כמות\n# הערה");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        appDialog().setTitle("רשימת קניות").setView(input)
                .setNegativeButton("ביטול", null)
                .setPositiveButton("המשך", (d, w) -> startImport(input.getText().toString(), "רשימה מודבקת"))
                .show();
    }

    private void confirmDeleteOrder() {
        appDialog().setTitle("מחיקת ההזמנה")
                .setMessage("למחוק את כל ההזמנה? הפעולה בלתי הפיכה.")
                .setNegativeButton("ביטול", null)
                .setPositiveButton("מחיקה", (d, w) -> requireUnlock(() -> deleteOrder()))
                .show();
    }

    private void deleteOrder() {
        final long id = featureOrderId;
        toast("⏳ מוחק את ההזמנה…", false);
        final int t = screenToken;
        executor.execute(() -> {
            try {
                HttpResult r = api("POST", "/api/orders/" + id + "/delete_order", null);
                boolean ok = r.code >= 200 && r.code < 300;
                if (ok) {
                    try {
                        JSONObject j = new JSONObject(r.body);
                        if (j.has("status")) ok = "success".equals(j.optString("status"));
                    } catch (Exception ignored) { }
                }
                final boolean done = ok;
                final String err = ok ? "" : apiError(r);
                runOnUiThread(() -> {
                    if (done) {
                        if (currentSaleId > 0) {
                            saleSnapshots.remove(currentSaleId);
                            try { snapshotFile(currentSaleId).delete(); } catch (Exception ignored) { }
                            currentSaleId = -1;
                        }
                        featureOrderId = -1;
                        featureProducts.clear();
                        featureQuantities.clear();
                        toast("ההזמנה נמחקה", false);
                        if (summaryFromHistory) loadFeatureOrders();
                        else showOpenSales(false);
                    } else {
                        toast("המחיקה נכשלה: " + err, true);
                    }
                });
            } catch (Exception e) {
                fail(t, e, "המחיקה נכשלה.");
            }
        });
    }

    private void askQuantity(final JSONObject p, final Runnable refresh, final Runnable onSuccess) {
        final String sid = sidOf(p);
        final int cur = featureQuantities.containsKey(sid) ? featureQuantities.get(sid) : 0;
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setText(String.valueOf(cur));
        in.selectAll();
        in.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        appDialog().setTitle(productName(p)).setMessage("כמות בהזמנה (0 = הסרה)").setView(in)
                .setNegativeButton("ביטול", null)
                .setPositiveButton("קביעה", (d, w) -> {
                    int n;
                    try { n = Integer.parseInt(in.getText().toString().trim()); } catch (Exception ex) { toast("מספר לא תקין", false); return; }
                    if (n < 0 || n > 999) { toast("כמות בין 0 ל-999", false); return; }
                    if (n > cur) {
                        if (truthy(p.opt("disable")) || isSoldOut(p)) { toast("המוצר אינו זמין כרגע", false); return; }
                    }
                    setItemAmount(p, n, refresh, onSuccess);
                }).show();
    }

    // ======================================================================== favourites

    private void styleModeChips(TextView[] chips, String[] keys) {
        for (int i = 0; i < chips.length; i++) {
            boolean on = keys[i].equals(viewMode);
            chips[i].setBackground(ripple(shape(on ? ORANGE : ORANGE_LIGHT, 12, 0), 12));
            chips[i].setTextColor(on ? ON_ORANGE : ORANGE_DARK);
        }
    }

    private Set<String> fetchFavouriteIds(int saleId) throws Exception {
        if (saleId <= 0) return null;
        HttpResult r = api("GET", "/api/get_favourites/" + saleId, null);
        if (r.code < 200 || r.code >= 300) return null;
        Set<String> out = new HashSet<>();
        Object v = new JSONTokener(r.body).nextValue();
        JSONArray a = null;
        if (v instanceof JSONArray) a = (JSONArray) v;
        else if (v instanceof JSONObject) {
            a = ((JSONObject) v).optJSONArray("data");
            if (a == null) a = ((JSONObject) v).optJSONArray("favourites");
        }
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            Object o = a.opt(i);
            String id = o instanceof JSONObject ? first((JSONObject) o, "id") : String.valueOf(o).trim();
            if (!id.isEmpty()) out.add(id);
        }
        return out;
    }

    private boolean containsId(JSONArray a, String id) {
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (id.equals(String.valueOf(a.opt(i)).trim())) return true;
        return false;
    }

    private void toggleFavourite(final JSONObject p, final Runnable done) {
        final String pid = pidOf(p);
        if (pid.isEmpty()) { toast("אין מזהה למוצר", false); return; }
        executor.execute(() -> {
            try {
                HttpResult r = api("GET", "/api/toggle_favourites/" + pid, null);
                if (r.code < 200 || r.code >= 300) throw new Exception(apiError(r));
                JSONObject d = new JSONObject(r.body);
                final boolean attached = containsId(d.optJSONArray("attached"), pid);
                final boolean detached = containsId(d.optJSONArray("detached"), pid);
                runOnUiThread(() -> {
                    if (attached) favIds.add(pid);
                    else if (detached) favIds.remove(pid);
                    else { toast("תשובה לא צפויה מהשרת", true); return; }
                    toast(attached ? "⭐ נוסף למועדפים" : "☆ הוסר מהמועדפים", false);
                    if (done != null) done.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (e instanceof AuthException) showLogin();
                    else toast(errText(e, "העדכון נכשל"), true);
                });
            }
        });
    }

    private int favSavedQty(String pid) {
        if (pid == null || pid.isEmpty()) return 0;
        try {
            if (favQtyCache == null) favQtyCache = new JSONObject(prefs.getString("fav_quantities", "{}"));
            JSONObject e = favQtyCache.optJSONObject(pid);
            return e == null ? 0 : Math.max(0, e.optInt("qty", 0));
        } catch (Exception ex) {
            return 0;
        }
    }

    private void setFavSavedQty(String pid, String name, int qty) {
        try {
            JSONObject all = new JSONObject(prefs.getString("fav_quantities", "{}"));
            if (qty > 0) {
                JSONObject e = new JSONObject();
                e.put("qty", qty);
                e.put("name", name);
                all.put(pid, e);
            } else {
                all.remove(pid);
            }
            favQtyCache = all;
            prefs.edit().putString("fav_quantities", all.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void askSavedQty(final JSONObject p) {
        final String pid = pidOf(p);
        int start = favSavedQty(pid);
        if (start == 0) { Integer q = featureQuantities.get(sidOf(p)); start = q == null || q <= 0 ? 1 : q; }
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setText(String.valueOf(start));
        in.selectAll();
        in.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        appDialog().setTitle(productName(p)).setMessage("כמות שמורה להזמנה אוטומטית של מועדפים (0 = ניקוי)").setView(in)
                .setNegativeButton("ביטול", null)
                .setPositiveButton("שמירה", (d, w) -> {
                    int n;
                    try { n = Integer.parseInt(in.getText().toString().trim()); } catch (Exception ex) { toast("מספר לא תקין", false); return; }
                    if (n < 0 || n > 999) { toast("כמות בין 0 ל-999", false); return; }
                    setFavSavedQty(pid, productName(p), n);
                    toast(n > 0 ? "הכמות נשמרה" : "הכמות השמורה נוקתה", false);
                    rebuildGrid();
                }).show();
    }

    /** Adds every available favourite that is not in the order yet, with its saved quantity (default 1). */
    private void orderFavourites() {
        final List<PlanItem> plan = new ArrayList<>();
        int already = 0, savedCount = 0, units = 0;
        for (JSONObject p : featureProducts) {
            String pid = pidOf(p);
            if (!favIds.contains(pid)) continue;
            Integer cur = featureQuantities.get(sidOf(p));
            if (cur != null && cur > 0) { already++; continue; }
            if (truthy(p.opt("disable")) || isSoldOut(p)) continue;
            int q = favSavedQty(pid);
            if (q > 0) savedCount++; else q = 1;
            PlanItem pi = new PlanItem();
            pi.p = p;
            pi.name = productName(p);
            pi.amount = q;
            pi.cur = 0;
            plan.add(pi);
            units += q;
        }
        if (plan.isEmpty()) {
            toast(already > 0 ? "כל המועדפים (" + already + ") כבר בהזמנה" : "אין מועדפים זמינים להוספה (סמנו כוכב על מוצרים)", true);
            return;
        }
        String detail = units + " יחידות: " + savedCount + " לפי כמות שמורה, " + (plan.size() - savedCount) + " בכמות 1"
                + (already > 0 ? "\n(" + already + " כבר בהזמנה ולא ישתנו)" : "");
        appDialog().setTitle("טעינת מועדפים")
                .setMessage("להוסיף " + plan.size() + " מועדפים להזמנה?\n" + detail)
                .setNegativeButton("ביטול", null)
                .setPositiveButton("הוספה", (d, w) -> applyPlan(plan))
                .show();
    }

    private void storeFavQuantities(List<String[]> items) {
        for (String[] it : items) setFavSavedQty(it[0], it[1], Integer.parseInt(it[2]));
        toast("נשמרו כמויות ל-" + items.size() + " מועדפים", false);
        rebuildGrid();
    }

    private void saveFavQuantities() {
        final List<String[]> news = new ArrayList<>(), conflicts = new ArrayList<>();
        int same = 0;
        for (JSONObject p : featureProducts) {
            String pid = pidOf(p);
            if (pid.isEmpty() || !favIds.contains(pid)) continue;
            Integer q = featureQuantities.get(sidOf(p));
            if (q == null || q <= 0) continue;
            int old = favSavedQty(pid);
            if (old == 0) news.add(new String[]{pid, productName(p), String.valueOf(q)});
            else if (old != q) conflicts.add(new String[]{pid, productName(p), String.valueOf(q), String.valueOf(old)});
            else same++;
        }
        if (news.isEmpty() && conflicts.isEmpty()) {
            toast(same > 0 ? "הכמויות של " + same + " המועדפים שבהזמנה כבר שמורות" : "אין מועדפים בהזמנה הנוכחית", true);
            return;
        }
        if (conflicts.isEmpty()) {
            appDialog().setTitle("שמירת כמויות")
                    .setMessage("לשמור כמויות ל-" + news.size() + " מועדפים מההזמנה הנוכחית?")
                    .setNegativeButton("ביטול", null)
                    .setPositiveButton("שמירה", (d, w) -> storeFavQuantities(news))
                    .show();
            return;
        }
        StringBuilder sb = new StringBuilder("יש " + conflicts.size() + " מועדפים עם כמות שמורה שונה מההזמנה:\n");
        for (int i = 0; i < Math.min(10, conflicts.size()); i++) {
            String[] c = conflicts.get(i);
            sb.append(c[1]).append(": ×").append(c[3]).append(" ← ×").append(c[2]).append("\n");
        }
        if (conflicts.size() > 10) sb.append("…ועוד ").append(conflicts.size() - 10).append("\n");
        if (!news.isEmpty()) sb.append("\n").append(news.size()).append(" חדשים יישמרו.");
        AlertDialog.Builder b = appDialog().setTitle("שמירת כמויות").setMessage(sb.toString())
                .setNegativeButton("ביטול", null)
                .setPositiveButton("דרוס את כולם", (d, w) -> {
                    List<String[]> all = new ArrayList<>(news);
                    all.addAll(conflicts);
                    storeFavQuantities(all);
                });
        if (!news.isEmpty()) b.setNeutralButton("רק חדשים", (d, w) -> storeFavQuantities(news));
        b.show();
    }

    private void clearFavQuantities() {
        final List<JSONObject> withQty = new ArrayList<>();
        for (JSONObject p : featureProducts) if (favIds.contains(pidOf(p)) && favSavedQty(pidOf(p)) > 0) withQty.add(p);
        if (withQty.isEmpty()) { toast("אין כמויות שמורות", false); return; }
        appDialog().setTitle("ניקוי כמויות שמורות")
                .setMessage("לנקות כמויות שמורות של " + withQty.size() + " מועדפים? הם יחזרו לכמות 1.")
                .setNegativeButton("ביטול", null)
                .setPositiveButton("ניקוי", (d, w) -> {
                    for (JSONObject p : withQty) setFavSavedQty(pidOf(p), productName(p), 0);
                    toast("הכמויות נוקו", false);
                    rebuildGrid();
                })
                .show();
    }

    // ======================================================================== credits: search / by sale / share

    private String[] txNames(JSONObject tx) {
        JSONObject sale = firstObject(tx, "sale", "sale_data", "saleData");
        JSONObject original = firstObject(tx, "original_sale", "originalSale", "original_sale_data", "originalSaleData");
        String saleName = tx.optInt("saleID", tx.optInt("sale_id", -1)) == 1 ? "" : transactionSaleName(tx, sale, false);
        int origId = tx.optInt("original_saleID", tx.optInt("original_sale_id", -1));
        String originalName = origId == 0 || origId == 1 ? "" : transactionSaleName(tx, original, true);
        return new String[]{saleName, originalName};
    }

    private String txDescription(JSONObject tx) {
        JSONObject product = tx.optJSONObject("product");
        String name = product == null ? "" : first(product, "full_name", "name");
        if (!name.isEmpty()) return name;
        return cleanHtml(first(tx, "note"));
    }

    private String txSearchText(JSONObject tx) {
        String[] names = txNames(tx);
        return (txDescription(tx) + " " + names[0] + " " + names[1] + " " + fmtWhen(tx.optString("time", "")) + " "
                + fmtNum(Math.abs(tx.optDouble("skum", 0)))).toLowerCase(Locale.ROOT);
    }

    private void showCreditsBySale(List<JSONObject> rows) {
        Map<String, double[]> groups = new LinkedHashMap<>();
        final Map<String, String> latest = new LinkedHashMap<>();
        double creditTotal = 0, chargeTotal = 0;
        for (JSONObject tx : rows) {
            if (tx.optBoolean("canceled", false)) continue;
            double amount = tx.optDouble("skum", 0);
            String[] names = txNames(tx);
            String key = !names[1].isEmpty() ? names[1] : (!names[0].isEmpty() ? names[0] : "ללא מכירה (כללי)");
            double[] g = groups.get(key);
            if (g == null) { g = new double[4]; groups.put(key, g); latest.put(key, ""); }
            if (amount < 0) { g[0] += -amount; g[2]++; creditTotal += -amount; }
            else if (amount > 0) { g[1] += amount; g[3]++; chargeTotal += amount; }
            String time = tx.optString("time", "");
            if (time.compareTo(latest.get(key)) > 0) latest.put(key, time);
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String a, String b) { return latest.get(b).compareTo(latest.get(a)); }
        });
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            double[] g = groups.get(key);
            StringBuilder bits = new StringBuilder();
            if (g[2] > 0) bits.append("🟢 ").append(money2(g[0])).append(" (").append((int) g[2]).append(")");
            if (g[3] > 0) bits.append(bits.length() > 0 ? " | " : "").append("🔴 ").append(money2(g[1])).append(" (").append((int) g[3]).append(")");
            if (bits.length() > 0) sb.append(key).append(": ").append(bits).append("\n\n");
        }
        sb.append("סך הכל זיכויים: ").append(money2(creditTotal)).append("\nסך הכל חיובים: ").append(money2(chargeTotal));
        showTextDialog("📊 זיכויים וחיובים לפי מכירה", sb.toString());
    }

    private String creditsText(List<JSONObject> rows) {
        StringBuilder sb = new StringBuilder("זיכויים וחיובים בחשבון\nנוצר: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date()) + "\n\n");
        int n = 0;
        double cr = 0, ch = 0;
        for (JSONObject tx : rows) {
            n++;
            double a = tx.optDouble("skum", 0);
            boolean canceled = tx.optBoolean("canceled", false);
            if (!canceled) { if (a < 0) cr += -a; else if (a > 0) ch += a; }
            String[] names = txNames(tx);
            String sale = !names[1].isEmpty() ? names[1] : names[0];
            sb.append(n).append(". ").append(a < 0 ? "זיכוי " : (a > 0 ? "חיוב " : "")).append(money2(Math.abs(a)));
            String desc = txDescription(tx);
            if (!desc.isEmpty()) sb.append(" | ").append(desc);
            if (!sale.isEmpty()) sb.append(" | ").append(sale);
            String date = fmtWhen(tx.optString("time", ""));
            if (!date.isEmpty()) sb.append(" | ").append(date);
            if (canceled) sb.append(" | בוטל");
            sb.append("\n");
        }
        sb.append("\n------------------------------\nסך הכל זיכויים ").append(money2(cr)).append(" | חיובים ").append(money2(ch)).append("\n");
        return sb.toString();
    }

    // ======================================================================== centre "collect" button

    private static final int NAV_FINGER_RISE = 10; // centre button rises only 10dp above neighbouring buttons
    private static final int NAV_FINGER_INNER = 72; // height of the icon + label area inside the centre button (dp)

    private View navDivider() {
        View d = new View(this);
        d.setTag("navDivider");
        d.setBackgroundColor(Color.TRANSPARENT);
        d.setLayoutParams(new LinearLayout.LayoutParams(dp(1), -1));
        return d;
    }

    /** The centre "collect" button: a fingertip that rises a little above the bar and is cut flat at the bottom. */
    private View collectNavButton(boolean active) {
        FrameLayout slot = new FrameLayout(this);
        slot.setTag("navCenter");
        slot.setFocusable(false);
        slot.setClipChildren(false);

        FrameLayout finger = new FrameLayout(this);
        float r = dp(33);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? ORANGE_DARK : ORANGE);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        finger.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(AppPalette.WHITE_RIPPLE_ALPHA, 255, 255, 255)), bg, mask));
        finger.setElevation(dp(8));
        finger.setClickable(true);
        finger.setFocusable(true);
        finger.setTag("navTab:4");
        finger.setContentDescription("מעבר מיידי לכרטיסיית איסוף");

        // Same structure as the other buttons (icon, label, bar placeholder), so the icon sits at the same height and size.
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        inner.setPadding(dp(2), dp(2), dp(2), dp(1));
        inner.addView(new IconView(ICON_CHECK, ON_ORANGE, 3.8f), new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView label = text("איסוף", 11, true);
        label.setTextColor(ON_ORANGE);
        label.setMaxLines(1);
        label.setGravity(Gravity.CENTER);
        inner.addView(label, new LinearLayout.LayoutParams(-1, -2));
        View barSpace = new View(this);
        barSpace.setBackground(shape(ON_ORANGE, 2, 0));
        barSpace.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(24), dp(3));
        barLp.topMargin = dp(2);
        inner.addView(barSpace, barLp);
        // Extra height prevents the label under the enlarged icon from being clipped.
        FrameLayout.LayoutParams innerLp = new FrameLayout.LayoutParams(-1, dp(NAV_FINGER_INNER));
        innerLp.gravity = Gravity.BOTTOM;
        finger.addView(inner, innerLp);

        FrameLayout.LayoutParams fingerLp = new FrameLayout.LayoutParams(-1, dp(NAV_FINGER_RISE + NAV_FINGER_INNER));
        fingerLp.gravity = Gravity.BOTTOM;
        fingerLp.topMargin = dp(-12);
        slot.addView(finger, fingerLp);
        finger.setOnClickListener(v -> navigateTab(TAB_COLLECT, this::openLatestCollect));
        slot.setLayoutParams(new LinearLayout.LayoutParams(0, dp(64), 1.1f));
        return slot;
    }

    /** Opens the newest order in the history straight in collect mode. Collected items are kept per order. */
    private void openLatestCollect() {
        beginScreen("איסוף הזמנה", TAB_COLLECT, null);
        setStatus("מחפש את ההזמנה האחרונה…");
        final int t = screenToken;
        executor.execute(() -> {
            long found = -1;
            Exception failure = null;
            try {
                HttpResult r = api("GET", "/api/orders?page=1", null);
                if (r.code >= 200 && r.code < 300) {
                    JSONArray orders = arrayFrom(r.body);
                    String bestTime = "";
                    for (int i = 0; i < orders.length(); i++) {
                        JSONObject o = orders.optJSONObject(i);
                        if (o == null) continue;
                        long oid = o.optLong("id", -1);
                        if (oid <= 0) continue;
                        String time = first(o, "time");
                        if (found < 0 || time.compareTo(bestTime) > 0 || (time.equals(bestTime) && oid > found)) { found = oid; bestTime = time; }
                    }
                    if (found > 0) prefs.edit().putLong("last_collect_order_id", found).apply();
                } else {
                    failure = new Exception(apiError(r));
                }
            } catch (Exception e) {
                failure = e;
            }
            if (found <= 0 && !(failure instanceof AuthException)) found = prefs.getLong("last_collect_order_id", -1); // offline fallback
            final long orderId = found;
            final Exception err = failure;
            if (orderId <= 0) {
                fail(t, err != null ? err : new Exception("לא נמצאו הזמנות בהיסטוריה."), "לא ניתן לפתוח את ההזמנה האחרונה.");
                return;
            }
            runOnUiThread(() -> { if (!stale(t)) openHistoryOrder(orderId, true, TAB_COLLECT); });
        });
    }

    // ======================================================================== system bars (works on every device)

    /** Insets for the status bar, navigation bar, cutout and on-screen input keyboard. */

    private int[] systemInsets(WindowInsets w) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = w.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int bottom = Math.max(bars.bottom, w.getInsets(WindowInsets.Type.ime()).bottom);
            return new int[]{bars.left, bars.top, bars.right, bottom};
        }
        return new int[]{w.getSystemWindowInsetLeft(), w.getSystemWindowInsetTop(), w.getSystemWindowInsetRight(), w.getSystemWindowInsetBottom()};
    }

    /** Dark icons on the white navigation bar, so the system buttons stay visible. */
    private void styleSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    int light = nightMode ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    c.setSystemBarsAppearance(light, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            } else if (Build.VERSION.SDK_INT >= 26) {
                View d = getWindow().getDecorView();
                int flags = d.getSystemUiVisibility();
                if (nightMode) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                d.setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) { }
    }

    // ======================================================================== child lock (product picture first, maths fallback)

    private long unlockExpiry = 0L;
    private long mathLockoutUntil = 0L;
    private int mathFailures = 0;
    private static final long UNLOCK_WINDOW_MS = 5L * 60L * 1000L;
    private static final int CHILD_LOCK_EXCLUDED_TRAILING_CATEGORIES = 3;

    private static final class ChildLockProduct {
        final JSONObject raw;
        final String name;
        final String imageUrl;
        final String key;
        ChildLockProduct(JSONObject raw, String name, String imageUrl, String key) {
            this.raw = raw;
            this.name = name;
            this.imageUrl = imageUrl;
            this.key = key;
        }
    }

    private boolean childLockOn() { return prefs.getBoolean("child_lock_enabled", true); }

    private boolean hideSoldOut() { return prefs.getBoolean("hide_sold_out", false); }
    private boolean separateSoldOutCategory() { return prefs.getBoolean("separate_sold_out_category", false); }

    private void tintSwitch(Switch sw) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        int trackOn = Color.argb(AppPalette.SWITCH_TRACK_ON_ALPHA, Color.red(ORANGE), Color.green(ORANGE), Color.blue(ORANGE));
        sw.setThumbTintList(new ColorStateList(states, new int[]{ORANGE, SWITCH_OFF_THUMB}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{trackOn, SWITCH_OFF_TRACK}));
    }

    private boolean fullImages() { return prefs.getBoolean("full_images", false); }

    @Override protected void onStop() {
        super.onStop();
        unlockExpiry = 0L; // the lock closes whenever the app leaves the screen
    }

    /** Runs the action at once when the lock is off or recently solved; otherwise asks for the challenge first. */
    private void requireUnlock(final Runnable action) {
        long now = System.currentTimeMillis();
        if (!childLockOn() || now < unlockExpiry) {
            if (childLockOn()) unlockExpiry = now + UNLOCK_WINDOW_MS;
            action.run();
            return;
        }
        showChildLockChallenge(action, null);
    }

    /**
     * Selects a simple product-picture challenge from the active order first, then local sale
     * snapshots. Products from the last three categories are excluded when there are enough
     * categories to do so, while retaining at least one category as a usable source.
     */
    private List<ChildLockProduct> childLockProductsFrom(List<JSONObject> products) {
        List<ChildLockProduct> out = new ArrayList<>();
        if (products == null || products.isEmpty()) return out;

        final Map<String, Integer> categoryRanks = new LinkedHashMap<>();
        final Map<String, Integer> categoryFirstPosition = new LinkedHashMap<>();
        for (int i = 0; i < products.size(); i++) {
            JSONObject p = products.get(i);
            if (p == null) continue;
            String category = categoryName(p);
            if (!categoryFirstPosition.containsKey(category)) {
                categoryFirstPosition.put(category, i);
                categoryRanks.put(category, categoryOrder(p));
            } else {
                Integer oldRank = categoryRanks.get(category);
                int rank = categoryOrder(p);
                if (oldRank == null || rank < oldRank) categoryRanks.put(category, rank);
            }
        }

        List<String> orderedCategories = new ArrayList<>(categoryFirstPosition.keySet());
        Collections.sort(orderedCategories, (a, b) -> {
            int ar = categoryRanks.containsKey(a) ? categoryRanks.get(a) : 9999;
            int br = categoryRanks.containsKey(b) ? categoryRanks.get(b) : 9999;
            if (ar != br) return Integer.compare(ar, br);
            return Integer.compare(categoryFirstPosition.get(a), categoryFirstPosition.get(b));
        });
        Set<String> excludedCategories = new HashSet<>();
        int excludedCount = Math.min(CHILD_LOCK_EXCLUDED_TRAILING_CATEGORIES,
                Math.max(0, orderedCategories.size() - 1));
        for (int i = orderedCategories.size() - excludedCount; i < orderedCategories.size(); i++) {
            if (i >= 0 && i < orderedCategories.size()) excludedCategories.add(orderedCategories.get(i));
        }

        Set<String> uniqueKeys = new HashSet<>();
        for (JSONObject p : products) {
            if (p == null || excludedCategories.contains(categoryName(p))) continue;
            String name = productName(p);
            if (name == null) continue;
            name = name.trim();
            if (name.isEmpty() || name.matches("מוצר\\s*#.*")) continue;
            String url = imageUrl(p, !fullImages());
            if (url == null || url.trim().isEmpty()) continue;
            String key = sidOf(p);
            if (key == null || key.trim().isEmpty()) key = name + "|" + url;
            else key = key.trim() + "|" + url;
            if (!uniqueKeys.add(key)) continue;
            if (normalizedProductWords(name).isEmpty()) continue;
            out.add(new ChildLockProduct(p, name, url, key));
        }
        return out;
    }

    private List<ChildLockProduct> childLockProductsFromOpenOrder() {
        if (featureSale == null || featureSale.optInt("id", -1) <= 0
                || featureOrderId <= 0 || closedOrderReadOnly || featureProducts.isEmpty()) {
            return new ArrayList<>();
        }
        return childLockProductsFrom(new ArrayList<>(featureProducts));
    }

    /** Cached order snapshots are the offline product source; no network call is made here. */
    private List<ChildLockProduct> childLockProductsFromCache() {
        List<ChildLockProduct> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (SaleSnapshot snapshot : saleSnapshots.values()) {
            if (snapshot == null || snapshot.products == null || snapshot.products.isEmpty()) continue;
            for (ChildLockProduct candidate : childLockProductsFrom(snapshot.products)) {
                if (seen.add(candidate.key)) out.add(candidate);
            }
        }

        try {
            File dir = new File(getFilesDir(), "cache");
            File[] files = dir.listFiles((d, name) -> name.matches("sale_[0-9]+\\.json"));
            if (files != null) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File file : files) {
                    int saleId;
                    try {
                        String fileName = file.getName();
                        saleId = Integer.parseInt(fileName.substring(5, fileName.length() - 5));
                    } catch (Exception ignored) { continue; }
                    if (saleId <= 0) continue;
                    SaleSnapshot snapshot = readSnapshot(saleId);
                    if (snapshot == null || snapshot.products.isEmpty()) continue;
                    for (ChildLockProduct candidate : childLockProductsFrom(snapshot.products)) {
                        if (seen.add(candidate.key)) out.add(candidate);
                    }
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Removes the Hebrew letters ו, ה and י before token comparison on both sides. */
    private Set<String> normalizedProductWords(String value) {
        Set<String> words = new HashSet<>();
        if (value == null || value.trim().isEmpty()) return words;
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("ו", "")
                .replace("ה", "")
                .replace("י", "");
        String[] parts = normalized.split("[^\\p{L}\\p{N}]+");
        for (String part : parts) {
            String word = part.trim();
            // Ignore tokens made empty (or meaningless) by the requested letter removal.
            if (word.length() >= 2) words.add(word);
        }
        return words;
    }

    private boolean childLockAnswerMatches(String productName, String answer) {
        Set<String> expected = normalizedProductWords(productName);
        Set<String> actual = normalizedProductWords(answer);
        if (expected.isEmpty() || actual.isEmpty()) return false;
        for (String word : actual) if (expected.contains(word)) return true;
        return false;
    }

    private ChildLockProduct pickNextChildLockProduct(List<ChildLockProduct> products,
            Set<String> seenKeys, Set<String> failedUrls, String currentKey) {
        if (products == null || products.isEmpty()) return null;
        List<ChildLockProduct> unseen = new ArrayList<>();
        List<ChildLockProduct> alternatives = new ArrayList<>();
        for (ChildLockProduct product : products) {
            if (product == null || product.imageUrl.isEmpty() || failedUrls.contains(product.imageUrl)
                    || product.key.equals(currentKey)) continue;
            alternatives.add(product);
            if (!seenKeys.contains(product.key)) unseen.add(product);
        }
        List<ChildLockProduct> choices = unseen.isEmpty() ? alternatives : unseen;
        if (choices.isEmpty()) return null;
        ChildLockProduct selected = choices.get(new Random().nextInt(choices.size()));
        seenKeys.add(selected.key);
        return selected;
    }

    private void showChildLockChallenge(final Runnable onSuccess, final Runnable onCancel) {
        long wait = mathLockoutUntil - System.currentTimeMillis();
        if (wait > 0) {
            toast("יותר מדי טעויות. נסו שוב בעוד " + (wait / 1000 + 1) + " שניות", true);
            if (onCancel != null) onCancel.run();
            return;
        }

        List<ChildLockProduct> candidates = childLockProductsFromOpenOrder();
        if (candidates.isEmpty()) candidates = childLockProductsFromCache();
        if (candidates.isEmpty()) {
            showMathChallenge(onSuccess, onCancel);
            return;
        }

        final List<ChildLockProduct> products = candidates;
        final Set<String> seenKeys = new HashSet<>();
        final Set<String> failedUrls = new HashSet<>();
        final ChildLockProduct[] current = new ChildLockProduct[1];
        final AlertDialog[] ref = new AlertDialog[1];

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(8));
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView hint = muted("זהו את המוצר שבתמונה והקלידו מילה משמו", 14);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, lp(-1, -2, 0, 8));

        FrameLayout imageFrame = new FrameLayout(this);
        imageFrame.setBackground(ripple(shape(SURFACE, 14, LINE), 14));
        imageFrame.setPadding(dp(8), dp(8), dp(8), dp(8));
        ImageView productImage = new ImageView(this);
        productImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        productImage.setContentDescription("תמונת המוצר לזיהוי — לחצו להגדלה");
        productImage.setFocusable(false);
        // Reuse the app-wide full-screen image viewer, including its original-resolution loading.
        // Only open it after the current product image has actually loaded.
        productImage.setClickable(true);
        productImage.setOnClickListener(v -> {
            ChildLockProduct selected = current[0];
            if (selected != null && productImage.getDrawable() != null) {
                showImagePreview(selected.imageUrl);
            }
        });
        imageFrame.addView(productImage, new FrameLayout.LayoutParams(-1, dp(190), Gravity.CENTER));
        TextView imageStatus = muted("טוען תמונה…", 13);
        imageStatus.setGravity(Gravity.CENTER);
        imageFrame.addView(imageStatus, new FrameLayout.LayoutParams(-1, dp(190), Gravity.CENTER));
        panel.addView(imageFrame, lp(-1, dp(206), 0, 10));

        TextView changeImage = btn("🔄  תמונה אחרת", TONAL);
        changeImage.setContentDescription("החלפת תמונת המוצר לחידה אחרת");
        panel.addView(changeImage, lp(-1, dp(42), 0, 8));

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextSize(20);
        input.setGravity(Gravity.CENTER);
        input.setHint("שם המוצר");
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackground(shape(SURFACE, 12, ORANGE));
        panel.addView(input, lp(-1, dp(54), 0, 4));

        final Runnable showMathFallback = () -> {
            if (ref[0] != null && ref[0].isShowing()) ref[0].dismiss();
            showMathChallenge(onSuccess, onCancel);
        };

        final Runnable[] next = new Runnable[1];
        next[0] = () -> {
            ChildLockProduct selected = pickNextChildLockProduct(products, seenKeys, failedUrls,
                    current[0] == null ? "" : current[0].key);
            if (selected == null) {
                showMathFallback.run();
                return;
            }
            current[0] = selected;
            input.setText("");
            productImage.setImageDrawable(null);
            imageStatus.setText("טוען תמונה…");
            imageStatus.setVisibility(View.VISIBLE);
            try {
                imageExecutor.execute(() -> {
                    Bitmap bitmap = imageCache == null ? null : imageCache.get(selected.imageUrl);
                    if (bitmap == null) bitmap = downloadBitmap(selected.imageUrl, 900);
                    final Bitmap loaded = bitmap;
                    if (loaded != null && imageCache != null) imageCache.put(selected.imageUrl, loaded);
                    runOnUiThread(() -> {
                        if (ref[0] == null || !ref[0].isShowing() || current[0] != selected) return;
                        if (loaded == null) {
                            failedUrls.add(selected.imageUrl);
                            next[0].run();
                        } else {
                            productImage.setImageBitmap(loaded);
                            imageStatus.setVisibility(View.GONE);
                        }
                    });
                });
            } catch (Exception ignored) {
                failedUrls.add(selected.imageUrl);
                next[0].run();
            }
        };
        changeImage.setOnClickListener(v -> next[0].run());

        final Runnable check = () -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) {
                toast("הקלידו מילה משם המוצר", false);
                return;
            }
            if (current[0] != null && childLockAnswerMatches(current[0].name, value)) {
                mathFailures = 0;
                unlockExpiry = System.currentTimeMillis() + UNLOCK_WINDOW_MS;
                ref[0].dismiss();
                onSuccess.run();
                return;
            }

            mathFailures++;
            if (mathFailures >= 3) {
                mathFailures = 0;
                mathLockoutUntil = System.currentTimeMillis() + 30000L;
                ref[0].dismiss();
                toast("3 טעויות. אפשר לנסות שוב בעוד 30 שניות", true);
                if (onCancel != null) onCancel.run();
            } else {
                toast("תשובה שגויה. נסו לזהות מוצר אחר", false);
                next[0].run();
            }
        };

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                check.run();
                return true;
            }
            return false;
        });
        ref[0] = appDialog().setTitle("🔒 נעילת ילדים").setView(panel)
                .setPositiveButton("אישור", null)
                .setNegativeButton("ביטול", (d, w) -> { if (onCancel != null) onCancel.run(); })
                .setOnCancelListener(d -> { if (onCancel != null) onCancel.run(); })
                .create();
        ref[0].setOnShowListener(x -> {
            ref[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> check.run());
            input.requestFocus();
            if (ref[0].getWindow() != null) ref[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            styleAppDialog(ref[0]);
            next[0].run();
        });
        ref[0].show();
    }

    /** One easy operation. Numbers are 10..100 (in × and ÷ one of them may be 2..9). The answer is always 10..100. */
    private String[] newMathExercise() {
        Random r = new Random();
        switch (r.nextInt(4)) {
            case 0: {
                int a = 10 + r.nextInt(81);
                int b = 10 + r.nextInt(100 - a - 10 + 1);
                return new String[]{a + " + " + b, String.valueOf(a + b)};
            }
            case 1: {
                int a = 20 + r.nextInt(81);
                int b = 10 + r.nextInt(a - 10 - 10 + 1);
                return new String[]{a + " − " + b, String.valueOf(a - b)};
            }
            case 2: {
                int small = 2 + r.nextInt(8);
                int big = 10 + r.nextInt(100 / small - 10 + 1);
                return r.nextBoolean()
                        ? new String[]{small + " × " + big, String.valueOf(small * big)}
                        : new String[]{big + " × " + small, String.valueOf(small * big)};
            }
            default: {
                int divisor = 2 + r.nextInt(8);
                int result = 10 + r.nextInt(100 / divisor - 10 + 1);
                int dividend = divisor * result;
                return new String[]{dividend + " ÷ " + divisor, String.valueOf(result)};
            }
        }
    }

    /** Mathematics remains the fallback when no suitable product image is available. */
    private void showMathChallenge(final Runnable onSuccess, final Runnable onCancel) {
        long wait = mathLockoutUntil - System.currentTimeMillis();
        if (wait > 0) {
            toast("יותר מדי טעויות. נסו שוב בעוד " + (wait / 1000 + 1) + " שניות", true);
            if (onCancel != null) onCancel.run();
            return;
        }
        final int[] answer = new int[1];
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(10), dp(22), dp(6));
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView hint = muted("פתרו את התרגיל כדי להמשיך", 14);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint);
        final TextView question = new TextView(this);
        question.setTextSize(34);
        question.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        question.setTextColor(ORANGE_DARK);
        question.setGravity(Gravity.CENTER);
        question.setTextDirection(View.TEXT_DIRECTION_LTR);
        panel.addView(question, lp(-1, -2, 8, 8));
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(24);
        input.setGravity(Gravity.CENTER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackground(shape(SURFACE, 12, ORANGE));
        panel.addView(input, lp(-1, dp(56), 0, 4));
        final Runnable next = () -> {
            String[] ex = newMathExercise();
            question.setText(ex[0] + " = ?");
            answer[0] = Integer.parseInt(ex[1]);
            input.setText("");
        };
        next.run();
        final AlertDialog[] ref = new AlertDialog[1];
        final Runnable check = () -> {
            int val;
            try { val = Integer.parseInt(input.getText().toString().trim()); }
            catch (Exception e) { toast("הקלידו מספר", false); return; }
            long now = System.currentTimeMillis();
            if (val == answer[0]) {
                mathFailures = 0;
                unlockExpiry = now + UNLOCK_WINDOW_MS;
                ref[0].dismiss();
                onSuccess.run();
            } else {
                mathFailures++;
                if (mathFailures >= 3) {
                    mathFailures = 0;
                    mathLockoutUntil = now + 30000L;
                    ref[0].dismiss();
                    toast("3 טעויות. אפשר לנסות שוב בעוד 30 שניות", true);
                    if (onCancel != null) onCancel.run();
                } else {
                    toast("תשובה שגויה. תרגיל חדש", false);
                    next.run();
                }
            }
        };
        input.setOnEditorActionListener((v, actionId, event) -> { check.run(); return true; });
        ref[0] = appDialog().setTitle("🔒 נעילת ילדים").setView(panel)
                .setPositiveButton("אישור", null)
                .setNegativeButton("ביטול", (d, w) -> { if (onCancel != null) onCancel.run(); })
                .setOnCancelListener(d -> { if (onCancel != null) onCancel.run(); })
                .create();
        ref[0].setOnShowListener(x -> {
            ref[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> check.run());
            input.requestFocus();
            if (ref[0].getWindow() != null) ref[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            styleAppDialog(ref[0]);
        });
        ref[0].show();
    }


    /**
     * One-time compatibility cleanup for installations upgrading from builds that had
     * the removed in-app updater. This only cancels old jobs and notifications; it
     * does not check, download, or install application updates.
     */
    private void clearLegacyUpdateArtifacts() {
        try {
            stopService(new Intent().setClassName(this, getPackageName() + ".UpdateService"));
        } catch (Exception ignored) { }

        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(7332);
                nm.cancel(7333);
                if (Build.VERSION.SDK_INT >= 26) nm.deleteNotificationChannel("app_update_monitor_v2");
            }
        } catch (Exception ignored) { }

        try {
            Intent watchdogIntent = new Intent()
                    .setClassName(this, getPackageName() + ".UpdateBootReceiver")
                    .setAction("org.mishnatyosef.mobile.UPDATE_SERVICE_WATCHDOG");
            android.app.PendingIntent watchdog = android.app.PendingIntent.getBroadcast(
                    this, 7334, watchdogIntent,
                    android.app.PendingIntent.FLAG_NO_CREATE | android.app.PendingIntent.FLAG_IMMUTABLE);
            if (watchdog != null) {
                AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarms != null) alarms.cancel(watchdog);
                watchdog.cancel();
            }
        } catch (Exception ignored) { }

        prefs.edit()
                .remove("app_update_auto")
                .remove("app_update_notification")
                .remove("app_update_interval_seconds")
                .remove("app_update_downloaded_version")
                .remove("app_update_latest_version")
                .remove("app_update_status")
                .remove("app_update_last_check_time")
                .remove("app_update_last_install_attempt")
                .apply();
    }

    private View displaySettingTile(String icon, String title, String subtitle, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tile.setPadding(dp(12), dp(10), dp(12), dp(10));
        tile.setBackground(ripple(shape(SURFACE, 14, LINE), 14));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setMinimumHeight(dp(72));

        TextView iconView = text(icon, icon.equals("Aa") ? 20 : 25, true);
        iconView.setTextColor(ORANGE_DARK);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(shape(ORANGE_LIGHT, 13, LINE));
        tile.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelsParams.setMargins(dp(12), 0, dp(10), 0);
        tile.addView(labels, labelsParams);

        TextView titleView = text(title, 15, true);
        titleView.setMaxLines(2);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        labels.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitleView = muted(subtitle, 12);
        subtitleView.setMaxLines(2);
        subtitleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(2);
        labels.addView(subtitleView, subtitleParams);

        TextView arrow = text("‹", 28, false);
        arrow.setTextColor(MUTED);
        arrow.setGravity(Gravity.CENTER);
        tile.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));
        tile.setContentDescription(title + ". " + subtitle);
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    private void addSafetySettings() {
        LinearLayout card = card(14);
        card.addView(sectionTitle("🛡️ בטיחות"));
        card.addView(switchRow("נעילת ילדים", "זיהוי מוצר לפי תמונה לפני פעולות בהזמנה ושליחת זיכויים וחיובים. אם אין תמונה מתאימה, יוצג תרגיל חשבון. אחרי פתרון הנעילה פתוחה 5 דקות", childLockOn(), (b, checked) -> {
            if (checked) {
                prefs.edit().putBoolean("child_lock_enabled", true).apply();
                unlockExpiry = 0L;
                toast("🔒 נעילת ילדים הופעלה", false);
            } else {
                showChildLockChallenge(() -> { prefs.edit().putBoolean("child_lock_enabled", false).apply(); toast("🔓 נעילת ילדים כובתה", false); }, () -> showAccount());
            }
        }));
        root.addView(card, lp(-1, -2, 0, 10));
    }

    private void addKeyboardShortcutsButton() {
        LinearLayout shortcutsCard = card(14);
        shortcutsCard.addView(text("⌨️ קיצורי מקשים", 15, true));
        shortcutsCard.addView(muted("מדריך אינטראקטיבי לקיצורי המקשים", 13), lp(-1, -2, 0, 8));
        TextView shortcutsButton = btn("קיצורי מקשים", TONAL);
        shortcutsButton.setContentDescription("פתיחת מדריך קיצורי מקשים");
        shortcutsButton.setOnClickListener(v -> showKeyboardShortcuts());
        shortcutsCard.addView(shortcutsButton, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(shortcutsCard, lp(-1, -2, 0, 10));
    }

    /**
     * Shows the keyboard-shortcuts HTML inside the app. The native header and close button
     * stay visible even while the page scrolls; Android Back also dismisses the viewer.
     */
    private void showKeyboardShortcuts() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        page.setBackground(shape(SURFACE, 18, LINE));
        page.setClipToOutline(true);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(8), dp(12), dp(8));
        toolbar.setBackgroundColor(SURFACE);

        TextView title = text("⌨️ קיצורי מקשים", 18, true);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView closeButton = btn("✕  סגירה", TONAL);
        closeButton.setContentDescription("סגירת מדריך קיצורי המקשים");
        closeButton.setMinHeight(dp(42));
        closeButton.setOnClickListener(v -> dialog.dismiss());
        toolbar.addView(closeButton, new LinearLayout.LayoutParams(-2, dp(42)));
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        page.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        WebView webView = new WebView(this);
        webView.setBackgroundColor(BG);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        // This WebView displays only the trusted HTML bundled in app/src/main/assets.
        // Keep cross-file and universal access disabled to prevent local-file origin access.
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setAllowContentAccess(false);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 21) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host != null && (host.equals("dropbox.com") || host.equals("www.dropbox.com")
                        || host.equals("dropboxusercontent.com") || host.endsWith(".dropboxusercontent.com"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    toast("לא ניתן לפתוח את הקישור", true);
                }
                return true;
            }
        });
        page.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));

        dialog.setContentView(page);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(AppPalette.DIALOG_DIM_AMOUNT);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        webView.loadUrl("file:///android_asset/keypad_phone_app.html");
    }

    private void addDisplaySettings() {
        LinearLayout card = card(14);
        card.addView(text("🎨 הגדרות תצוגה", 15, true));
        card.addView(muted("התאמת גודל הטקסט, התצוגה ומספר העמודות בכרטיסי המוצרים", 13), lp(-1, -2, 0, 8));
        TextView settings = btn("⚙️ פתיחת הגדרות תצוגה", TONAL);
        settings.setOnClickListener(v -> showDisplaySettingsPage());
        card.addView(settings, new LinearLayout.LayoutParams(-1, dp(46)));
        root.addView(card, lp(-1, -2, 0, 10));
    }

    private void showDisplaySettingsPage() {
        beginScreen("הגדרות תצוגה", 3, () -> showAccount());
        buildDisplaySettingsPage();
    }

    private void buildDisplaySettingsPage() {
        LinearLayout card = card(14);
        card.addView(sectionTitle("🎨 תצוגה"));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(12));
        controls.setBackground(shape(SURFACE, 16, LINE));
        controls.setVisibility(View.VISIBLE);

        TextView themeTitle = text("🎨  ערכת צבעים", 15, true);
        themeTitle.setTextColor(INK);
        themeTitle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        controls.addView(themeTitle);
        controls.addView(muted("לחיצה על צבע מחליפה את העיצוב מיד ושומרת את הבחירה", 12));
        LinearLayout themeOptions = new LinearLayout(this);
        themeOptions.setOrientation(LinearLayout.HORIZONTAL);
        themeOptions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams themeOptionsLp = new LinearLayout.LayoutParams(-1, -2);
        themeOptionsLp.topMargin = dp(8);
        themeOptionsLp.bottomMargin = dp(6);
        TextView orangeThemeOption = text("", 14, true);
        orangeThemeOption.setTag("colorThemeOrangeOption");
        styleColorThemeOption(orangeThemeOption, false);
        orangeThemeOption.setOnClickListener(v -> changeColorTheme(false));
        TextView purpleThemeOption = text("", 14, true);
        purpleThemeOption.setTag("colorThemePurpleOption");
        styleColorThemeOption(purpleThemeOption, true);
        purpleThemeOption.setOnClickListener(v -> changeColorTheme(true));
        LinearLayout.LayoutParams themeOptionLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        themeOptionLp.setMarginStart(dp(5));
        themeOptions.addView(orangeThemeOption, themeOptionLp);
        LinearLayout.LayoutParams purpleOptionLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        purpleOptionLp.setMarginStart(dp(5));
        themeOptions.addView(purpleThemeOption, purpleOptionLp);
        controls.addView(themeOptions, themeOptionsLp);

        View themeDivider = new View(this);
        themeDivider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams themeDividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        themeDividerLp.topMargin = dp(8);
        themeDividerLp.bottomMargin = dp(8);
        controls.addView(themeDivider, themeDividerLp);

        TextView scaleTitle = text("Aa  גודל טקסט ותצוגה", 15, true);
        scaleTitle.setTextColor(INK);
        scaleTitle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams scaleTitleLp = new LinearLayout.LayoutParams(-1, -2);
        scaleTitleLp.topMargin = dp(12);
        controls.addView(scaleTitle, scaleTitleLp);
        controls.addView(muted("הטקסט, הכפתורים והמרווחים משתנים יחד", 12));

        int initialScale = Math.max(70, Math.min(130, prefs.getInt("ui_scale_percent", 100)));
        TextView scaleValue = text(initialScale + "%", 20, true);
        scaleValue.setTextColor(ORANGE_DARK);
        scaleValue.setGravity(Gravity.CENTER);
        scaleValue.setBackground(shape(ORANGE_LIGHT, 12, LINE));
        scaleValue.setPadding(dp(14), dp(5), dp(14), dp(5));
        LinearLayout.LayoutParams scaleValueLp = new LinearLayout.LayoutParams(-2, dp(40));
        scaleValueLp.gravity = Gravity.CENTER_HORIZONTAL;
        scaleValueLp.topMargin = dp(8);
        controls.addView(scaleValue, scaleValueLp);

        SeekBar scaleSlider = new SeekBar(this);
        scaleSlider.setMax(12);
        scaleSlider.setProgress((initialScale - 70) / 5);
        scaleSlider.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scaleSlider.setContentDescription("גודל התצוגה, מ־70 עד 130 אחוז בקפיצות של 5");
        if (Build.VERSION.SDK_INT >= 21) {
            scaleSlider.setProgressTintList(ColorStateList.valueOf(ORANGE));
            scaleSlider.setThumbTintList(ColorStateList.valueOf(ORANGE));
            scaleSlider.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        }
        controls.addView(scaleSlider, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout scaleMarks = new LinearLayout(this);
        scaleMarks.setOrientation(LinearLayout.HORIZONTAL);        scaleMarks.setGravity(Gravity.CENTER);
        scaleMarks.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int mark : new int[]{130, 120, 110, 100, 90, 80, 70}) {
            TextView label = muted(String.valueOf(mark), 10);
            label.setGravity(Gravity.CENTER);
            scaleMarks.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        }
        controls.addView(scaleMarks, new LinearLayout.LayoutParams(-1, -2));
        scaleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                scaleValue.setText((70 + progress * 5) + "%");
                if (fromUser) changeUiScale(70 + progress * 5);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                changeUiScale(70 + seekBar.getProgress() * 5);
            }
        });
        scaleSlider.setContentDescription("גודל התצוגה, מ־70 עד 130 אחוז בקפיצות של 5");

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.topMargin = dp(14);
        dividerLp.bottomMargin = dp(12);
        controls.addView(divider, dividerLp);

        TextView columnsTitle = text("▦  מספר עמודות בעמוד ההזמנה", 15, true);
        columnsTitle.setTextColor(INK);
        controls.addView(columnsTitle, new LinearLayout.LayoutParams(-1, -2));
        controls.addView(muted("בחירת גודל כרטיסי המוצרים", 12));
        int initialColumns = cardColumns();
        TextView columnsValue = text(initialColumns + (initialColumns == 1 ? " עמודה" : " עמודות"), 20, true);
        columnsValue.setTextColor(ORANGE_DARK);
        columnsValue.setGravity(Gravity.CENTER);
        columnsValue.setBackground(shape(ORANGE_LIGHT, 12, LINE));
        columnsValue.setPadding(dp(14), dp(5), dp(14), dp(5));
        LinearLayout.LayoutParams columnsValueLp = new LinearLayout.LayoutParams(-2, dp(40));
        columnsValueLp.gravity = Gravity.CENTER_HORIZONTAL;
        columnsValueLp.topMargin = dp(8);
        controls.addView(columnsValue, columnsValueLp);
        TextView columnsSize = muted(cardSizeLabel(initialColumns), 12);
        columnsSize.setGravity(Gravity.CENTER);
        controls.addView(columnsSize, new LinearLayout.LayoutParams(-1, -2));

        SeekBar columnsSlider = new SeekBar(this);
        columnsSlider.setMax(4);
        columnsSlider.setProgress(initialColumns - 1);
        columnsSlider.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        columnsSlider.setContentDescription("מספר עמודות בכרטיסי המוצרים, בין עמודה אחת לחמש");
        if (Build.VERSION.SDK_INT >= 21) {
            columnsSlider.setProgressTintList(ColorStateList.valueOf(ORANGE));
            columnsSlider.setThumbTintList(ColorStateList.valueOf(ORANGE));
            columnsSlider.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        }
        LinearLayout.LayoutParams columnsSliderLp = new LinearLayout.LayoutParams(-1, dp(42));
        columnsSliderLp.topMargin = dp(4);
        controls.addView(columnsSlider, columnsSliderLp);
        LinearLayout columnMarks = new LinearLayout(this);
        columnMarks.setOrientation(LinearLayout.HORIZONTAL);
        columnMarks.setGravity(Gravity.CENTER);
        columnMarks.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int columns = 5; columns >= 1; columns--) {
            TextView mark = muted(String.valueOf(columns), 11);
            mark.setGravity(Gravity.CENTER);
            columnMarks.addView(mark, new LinearLayout.LayoutParams(0, -2, 1));
        }
        controls.addView(columnMarks, new LinearLayout.LayoutParams(-1, -2));
        columnsSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int columns = progress + 1;
                columnsValue.setText(columns + (columns == 1 ? " עמודה" : " עמודות"));
                columnsSize.setText(cardSizeLabel(columns));
                if (fromUser) commitCardColumns(columns, false);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                commitCardColumns(columnsSlider.getProgress() + 1, false);
            }
        });
        columnsSlider.setContentDescription("מספר עמודות בכרטיסי המוצרים, בין עמודה אחת לחמש");

        TextView resetDisplayButton = btn("↺  איפוס תצוגה", OUTLINE);
        resetDisplayButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams resetDisplayLp = new LinearLayout.LayoutParams(-1, dp(44));
        resetDisplayLp.topMargin = dp(14);
        resetDisplayLp.bottomMargin = dp(8);
        controls.addView(resetDisplayButton, resetDisplayLp);
        resetDisplayButton.setContentDescription("איפוס גודל ל־100 אחוז, שלוש עמודות ומצב יום");
        resetDisplayButton.setOnClickListener(v -> {
            scaleSlider.setProgress(6);
            scaleValue.setText("100%");
            changeUiScale(100);
            columnsSlider.setProgress(2);
            columnsValue.setText("3 עמודות");
            columnsSize.setText(cardSizeLabel(3));
            commitCardColumns(3, false);
            if (nightMode) toggleNightMode();
            View dayModeSwitch = findViewWithTag(currentScreen, "displayDayModeSwitch");
            if (dayModeSwitch instanceof Switch) ((Switch) dayModeSwitch).setChecked(true);
            if (actionScaleBar != null) actionScaleBar.setProgress(6);
            if (actionScaleValue != null) actionScaleValue.setText("100%");
            toast("התצוגה אופסה לברירת המחדל", false);
        });

        View displayToggleDivider = new View(this);
        displayToggleDivider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams displayToggleDividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        displayToggleDividerLp.topMargin = dp(4);
        displayToggleDividerLp.bottomMargin = dp(4);
        controls.addView(displayToggleDivider, displayToggleDividerLp);

        View dayModeRow = switchRow("מצב יום", "כיבוי המתג מפעיל מצב לילה", !nightMode, (button, checked) -> {
            if (nightMode == checked) toggleNightMode();
        });
        if (dayModeRow instanceof ViewGroup && ((ViewGroup) dayModeRow).getChildCount() > 1) {
            ((ViewGroup) dayModeRow).getChildAt(1).setTag("displayDayModeSwitch");
        }
        controls.addView(dayModeRow);
        controls.addView(switchRow("ניווט לפי פעולה", "ניווט בחצים בין פעולות הכרטיס במקום ניווט פשוט", !simpleKeyboardNavigation(), (button, checked) -> {
            prefs.edit().putBoolean("simple_keyboard_navigation", !checked).apply();
        }));

        TextView soldOutHeading = text("📦 הצגת פריטים שאזלו", 15, true);
        soldOutHeading.setTextColor(INK);
        LinearLayout.LayoutParams soldOutHeadingLp = new LinearLayout.LayoutParams(-1, -2);
        soldOutHeadingLp.topMargin = dp(18);
        soldOutHeadingLp.bottomMargin = dp(6);
        controls.addView(soldOutHeading, soldOutHeadingLp);
        controls.addView(switchRow("הפרדת פריטים שאזלו", "העברת פריטים שאזלו לקטגוריה נפרדת; כשחוזרים למלאי הם חוזרים לקטגוריה המקורית.", separateSoldOutCategory(), (b, checked) -> {
            SharedPreferences.Editor edit = prefs.edit().putBoolean("separate_sold_out_category", checked);
            if (checked) edit.putBoolean("hide_sold_out", false);
            edit.apply();
            if (productAdapter != null && !featureProducts.isEmpty()) rebuildGrid();
            toast(checked ? "פריטים שאזלו הועברו לקטגוריה נפרדת" : "החלוקה הרגילה לפי קטגוריות הוחזרה", false);
        }));
        controls.addView(switchRow("הסתר פריטים שאזלו", "פריטים שאזלו מהמלאי לא יוצגו ברשימה. פריט שכבר בהזמנה ממשיך להופיע.", hideSoldOut(), (b, checked) -> {
            prefs.edit().putBoolean("hide_sold_out", checked).apply();
            if (productAdapter != null && !featureProducts.isEmpty()) rebuildGrid();
            toast(checked ? "🙈 פריטים שאזלו מוסתרים" : "פריטים שאזלו מוצגים", false);
        }));
        View stockDisplayDivider = new View(this);
        stockDisplayDivider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams stockDisplayDividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        stockDisplayDividerLp.topMargin = dp(12);
        stockDisplayDividerLp.bottomMargin = dp(8);
        controls.addView(stockDisplayDivider, stockDisplayDividerLp);
        card.addView(controls, lp(-1, -2, 0, 0));
        root.addView(card, lp(-1, -2, 0, 10));
    }

    // ======================================================================== layout helpers

    /** The manifest has no supportsRtl, so Android lays everything out left-to-right. This keeps Hebrew order either way. */
    private boolean rtlActive() { return (getApplicationInfo().flags & ApplicationInfo.FLAG_SUPPORTS_RTL) != 0; }

    /** Adds the cells so that the first one ends up on the visual right (cells need their LayoutParams already). */
    private void addVisualRtl(LinearLayout row, List<View> cells) {
        if (rtlActive()) for (View v : cells) row.addView(v);
        else for (int i = cells.size() - 1; i >= 0; i--) row.addView(cells.get(i));
    }

    /** Thick-line vector icons, drawn in code so no image files are needed. */
    private class IconView extends View {
        private final int type;
        private final float strokePx;
        private int iconColor;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconView(int type, int color, float strokeDp) {
            super(MainActivity.this);
            this.type = type;
            this.iconColor = color;
            this.strokePx = strokeDp * getResources().getDisplayMetrics().density;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(color);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
        }

        int getIconColor() { return iconColor; }

        void setIconColor(int color) {
            iconColor = color;
            stroke.setColor(color);
            fill.setColor(color);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            if (s <= 0) return;
            c.save();
            c.translate((getWidth() - 24f * s) / 2f, (getHeight() - 24f * s) / 2f);
            c.scale(s, s);
            stroke.setStrokeWidth(strokePx / s);
            Path path = new Path();
            switch (type) {
                case ICON_CART:
                    path.moveTo(2.5f, 3.5f); path.lineTo(5.2f, 3.5f); path.lineTo(7.6f, 15f);
                    path.lineTo(19f, 15f); path.lineTo(21f, 6.5f); path.lineTo(6f, 6.5f);
                    c.drawPath(path, stroke);
                    c.drawCircle(9.5f, 19.6f, 1.6f, fill);
                    c.drawCircle(17f, 19.6f, 1.6f, fill);
                    break;
                case ICON_CLOCK:
                    c.drawCircle(12f, 12f, 9f, stroke);
                    path.moveTo(12f, 6.5f); path.lineTo(12f, 12f); path.lineTo(16f, 14.5f);
                    c.drawPath(path, stroke);
                    break;
                case ICON_WALLET:
                    c.drawRoundRect(new RectF(3f, 5.5f, 21f, 19.5f), 3.2f, 3.2f, stroke);
                    path.moveTo(3f, 9.5f); path.lineTo(21f, 9.5f);
                    c.drawPath(path, stroke);
                    c.drawCircle(16.8f, 14.5f, 1.5f, fill);
                    break;
                case ICON_PERSON:
                    c.drawCircle(12f, 8f, 4f, stroke);
                    path.moveTo(4.5f, 20.5f);
                    path.cubicTo(4.5f, 15.5f, 8f, 13.8f, 12f, 13.8f);
                    path.cubicTo(16f, 13.8f, 19.5f, 15.5f, 19.5f, 20.5f);
                    c.drawPath(path, stroke);
                    break;
                default:
                    c.drawRoundRect(new RectF(3.5f, 3.5f, 20.5f, 20.5f), 4f, 4f, stroke);
                    path.moveTo(7.6f, 12.4f); path.lineTo(10.8f, 15.6f); path.lineTo(16.6f, 8.6f);
                    c.drawPath(path, stroke);
                    break;
            }
            c.restore();
        }
    }

    // ======================================================================== category menu (jump straight to a category)

    private void refillCategoryStrip(List<String> cats) {
        if (catStripRow == null) return;
        catStripRow.removeAllViews();
        catChips.clear();
        if (activeCat.isEmpty() || !cats.contains(activeCat)) activeCat = cats.isEmpty() ? "" : cats.get(0);
        if (!pendingCategoryJump.isEmpty() && !cats.contains(pendingCategoryJump)) pendingCategoryJump = "";
        List<View> cells = new ArrayList<>();
        TextView menu = pickChip("☰ קטגוריות");
        menu.setTag("categoryToolbarMenu");
        menu.setFocusable(true);
        menu.setFocusableInTouchMode(false);
        menu.setOnFocusChangeListener((v, hasFocus) -> {
            v.setElevation(hasFocus ? dp(3) : 0);
            applyCategoryToolbarChipStyle((TextView) v, hasFocus);
            if (hasFocus) scrollCategoryChipIntoView(v);
        });
        styleChip(menu, true);
        menu.setOnClickListener(v -> showCategoryMenu());
        menu.setLayoutParams(chipLp());
        cells.add(menu);
        final String[] modeKeys = {"all", "fav", "cart"};
        String[] modeLabels = {"הכול", "⭐ מועדפים", "🛒 בהזמנה"};
        for (int i = 0; i < 3; i++) {
            final String key = modeKeys[i];
            TextView mc = pickChip(modeLabels[i]);
            mc.setTag("categoryToolbarMode:" + key);
            mc.setFocusable(true);
            mc.setFocusableInTouchMode(false);
            mc.setOnFocusChangeListener((v, hasFocus) -> {
                v.setElevation(hasFocus ? dp(3) : 0);
                applyCategoryToolbarChipStyle((TextView) v, hasFocus);
                if (hasFocus) scrollCategoryChipIntoView(v);
            });
            styleChip(mc, key.equals(viewMode));
            mc.setOnClickListener(v -> { viewMode = key; rebuildGrid(true); });
            mc.setLayoutParams(chipLp());
            cells.add(mc);
        }
        for (final String cat : cats) {
            Integer count = catCounts.get(cat);
            TextView chip = pickChip(cat + (count == null ? "" : " " + count));
            chip.setTag("categoryToolbarCategory:" + cat);
            chip.setFocusable(true);
            chip.setFocusableInTouchMode(false);
            chip.setOnFocusChangeListener((v, hasFocus) -> {
                v.setElevation(hasFocus ? dp(3) : 0);
                applyCategoryToolbarChipStyle((TextView) v, hasFocus);
                if (hasFocus) scrollCategoryChipIntoView(v);
            });
            chip.setOnClickListener(v -> jumpToCategory(cat));
            chip.setLayoutParams(chipLp());
            catChips.put(cat, chip);
            cells.add(chip);
        }
        addVisualRtl(catStripRow, cells);
        styleCategoryChips();
        if (catStripScroll != null && !rtlActive()) catStripScroll.post(() -> catStripScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(36));
        cp.setMargins(dp(3), 0, dp(3), 0);
        return cp;
    }

    /** The active category is drawn with an orange outline so it differs from the filled "mode" chips. */
    private void styleCategoryChips() {
        for (Map.Entry<String, TextView> e : catChips.entrySet()) {
            boolean on = e.getKey().equals(activeCat);
            TextView chip = e.getValue();
            chip.setTextColor(ORANGE_DARK);
            applyCategoryToolbarChipStyle(chip, chip.hasFocus());
        }
        if (catStripRow != null) {
            for (int i = 0; i < catStripRow.getChildCount(); i++) {
                View view = catStripRow.getChildAt(i);
                if (!(view instanceof TextView)) continue;
                TextView chip = (TextView) view;
                if (String.valueOf(chip.getTag()).startsWith("categoryToolbarMode:")) {
                    String key = String.valueOf(chip.getTag()).substring("categoryToolbarMode:".length());
                    applyCategoryToolbarChipStyle(chip, chip.hasFocus());
                } else if ("categoryToolbarMenu".equals(chip.getTag())) {
                    applyCategoryToolbarChipStyle(chip, chip.hasFocus());
                }
            }
        }
    }

    private void applyCategoryToolbarChipStyle(TextView chip, boolean focused) {
        if (chip == null) return;
        Object tag = chip.getTag();
        boolean activeCategory = tag instanceof String
                && ((String) tag).startsWith("categoryToolbarCategory:")
                && ((String) tag).substring("categoryToolbarCategory:".length()).equals(activeCat);
        boolean activeMode = tag instanceof String
                && ((String) tag).startsWith("categoryToolbarMode:")
                && ((String) tag).substring("categoryToolbarMode:".length()).equals(viewMode);
        boolean activeMenu = "categoryToolbarMenu".equals(tag);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(activeCategory ? SURFACE : (activeMode || activeMenu ? ORANGE : ORANGE_LIGHT));
        if (focused) bg.setStroke(dp(2), AppPalette.categoryFocusBorder(purpleTheme));
        else if (activeCategory) bg.setStroke(dp(2), ORANGE);
        chip.setBackground(ripple(bg, 12));
        chip.setTextColor(activeMode || activeMenu ? ON_ORANGE : ORANGE_DARK);
    }

    private void jumpToCategory(String cat) {
        Integer index = catHeaders.get(cat);
        if (index == null || productList == null) return;
        hideKeyboard();
        final int position = index;
        // Set the target before posting the ListView jump. Rapid 7/9 presses must advance
        // from the last requested category, not an intermediate scroll position.
        pendingCategoryJump = cat;
        activeCat = cat;
        styleCategoryChips();
        // Keep the selected category button visible in the horizontal chip row as well.
        if (catStripScroll != null) catStripScroll.post(() -> {
            if (catStripScroll == null || !cat.equals(activeCat)) return;
            TextView selectedChip = catChips.get(cat);
            if (selectedChip == null) return;
            int horizontalTarget = selectedChip.getLeft()
                    - (catStripScroll.getWidth() - selectedChip.getWidth()) / 2;
            catStripScroll.smoothScrollTo(Math.max(0, horizontalTarget), 0);
        });
        productList.post(() -> {
            if (productList == null || !showingOrderScreen || !catHeaders.containsKey(cat)) {
                if (cat.equals(pendingCategoryJump)) pendingCategoryJump = "";
                return;
            }
            productList.setSelectionFromTop(position, 0);
            // Let ListView finish its selection/scroll callbacks before releasing the guard.
            productList.post(() -> {
                if (productList == null || !cat.equals(pendingCategoryJump)) return;
                pendingCategoryJump = "";
                activeCat = cat;
                        styleCategoryChips();
            });
        });
    }

    private void showCategoryMenu() {
        if (catHeaders.isEmpty()) { toast("אין קטגוריות להצגה", false); return; }
        final List<String> names = new ArrayList<>(catHeaders.keySet());
        String[] labels = new String[names.size() + 1];
        labels[0] = "⬆ לראש הרשימה";
        for (int i = 0; i < names.size(); i++) {
            Integer c = catCounts.get(names.get(i));
            labels[i + 1] = names.get(i) + (c == null ? "" : "  (" + c + ")");
        }
        appDialog().setTitle("קפיצה לקטגוריה").setItems(labels, (d, which) -> {
            if (which == 0) { if (productList != null) productList.setSelection(0); }
            else jumpToCategory(names.get(which - 1));
        }).show();
    }

    /** Highlights the category at the top of the list and keeps its chip in view. */
    private void updateActiveCategory(int firstVisible) {
        if (catHeaders.isEmpty()) return;
        // A posted category jump is in progress. Ignore intermediate scroll callbacks so
        // they cannot replace the target before the next 7/9 press.
        if (!pendingCategoryJump.isEmpty()) return;
        String found = "";
        for (Map.Entry<String, Integer> e : catHeaders.entrySet()) {
            if (found.isEmpty()) found = e.getKey();
            if (e.getValue() <= firstVisible) found = e.getKey();
            else break;
        }
        if (found.isEmpty() || found.equals(activeCat)) return;
        activeCat = found;
        styleCategoryChips();
        TextView chip = catChips.get(found);
        if (chip != null && catStripScroll != null) {
            int target = chip.getLeft() - (catStripScroll.getWidth() - chip.getWidth()) / 2;
            catStripScroll.smoothScrollTo(Math.max(0, target), 0);
        }
    }
}

