package org.mishnatyosef.pickup;

import android.app.Activity;
import android.app.Dialog;
import android.view.KeyEvent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.content.res.AssetFileDescriptor;
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
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.view.HapticFeedbackConstants;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.lang.ref.WeakReference;
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
 * Authentication uses the Mishnat Yosef JSON login API. The password is used only for
 * the login request and is never persisted; only the access token is encrypted locally.
 */
public class MainActivity extends Activity {
    private static final String API = "https://v2.mishnatyosef.org";
    private static final String PREFS = "mishnat_pickup_preferences";
    private static final String TOKEN_KEY = "encrypted_access_token";
    private static final String PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY = "allow_offline_cache_after_auth_expiry";
    private static final String KEY_ALIAS = "mishnat_pickup_auth_key_v1";
    // כל צבעי הממשק מרוכזים ב-Ui.java.

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService imageLoader = Executors.newFixedThreadPool(3);
    // Profile names are fetched independently so they can never delay product-list rendering.
    private final ExecutorService profileLoader = Executors.newSingleThreadExecutor();
    // Offline image downloads must never block displaying the order.
    private final ExecutorService cacheWorker = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    // Persistent URL-keyed image cache: rendering never waits for image network requests.
    private final Object imageTargetsLock = new Object();
    private final Set<String> imageDownloads = new HashSet<>();
    private final Map<String, List<WeakReference<ImageView>>> imageTargets = new HashMap<>();

    private SharedPreferences preferences;
    private SharedPreferences uiPreferences;
    private SavedListStore savedListStore;
    private SecretStore secretStore;
    private SourceRegistry sourceRegistry;
    private String activeSourceId = "";
    private File activeSourceDirectory;
    private volatile String accessToken = "";
    private String activeOwner = "";
    private boolean cacheTrusted = false;
    private Button retryButton;
    private JSONObject orderData;
    private String currentOrderId = "";
    /** שם המכירה של ההזמנה המוצגת (למשל שם הפרשה), או ריק כשאין. */
    private String currentOrderTitle = "";
    private TextView homeTitleText;
    /** מידע האיסוף של ההזמנה המוצגת (מקום, שעות, הערות). בתצוגה המאוחדת - לכל חשבון. */
    private JSONObject currentPickupInfo = null;
    private final List<JSONObject> combinedPickupInfos = new ArrayList<>();
    private final List<ProductRow> products = new ArrayList<>();
    private final Set<String> collected = new HashSet<>();
    private final Map<String, Double> partialCollected = new HashMap<>();
    private EditText searchInput;
    private TextView searchEmptyText;
    private String searchQuery = "";
    private int displayMode = 3, lastRenderedDisplayMode = -1;
    private boolean keyFiveLongHandled = false;
    private boolean keyFivePending = false;
    private ScrollView pageScroll;
    private LinearLayout page;
    private ViewGroup pendingList;
    private ViewGroup collectedList;
    private LinearLayout collectedSection;
    private TextView progressText;
    private TextView progressPercent;
    private ProgressBar progressBar;
    private Button resetButton;
    private Button logoutButton;
    private final java.util.Map<String, ProductCard> productCards = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, ProductCard> partialCards = new java.util.LinkedHashMap<>();
    private TextView messageText;
    private EditText loginEmailInput, loginPasswordInput;
    private Button loginButton;
    private ProgressBar loginProgress;
    private TextView loginErrorText;
    private boolean authHandled = false;
    private boolean loginBusy = false;
    private boolean accountsScreenVisible = false;
    private static final int REQUEST_IMPORT_FILE = 6101;
    private static final int REQUEST_EXPORT_FILE = 6102;
    private String pendingExportListId = "";
    private final List<String> syncQueue = new ArrayList<>();
    private int syncQueueIndex = 0;
    private int syncQueueTotal = 0;
    private String syncReturnSource = "", syncReturnMode = "COMBINED_BY_SOURCE";
    private boolean syncAllRunning = false, syncAwaitingResult = false;
    private boolean showingSavedList = false;
    private boolean savedListsScreenVisible = false;
    private boolean savedListsReturnToLogin = false;
    private boolean sourceMergeInProgress = false;
    private int historyPage=0, historyLastPage=1;
    private boolean historyLoading=false;
    private final List<JSONObject> historyOrders=new ArrayList<>();
    private LinearLayout historyContent;
    private Button historyMoreButton;
    private Button viewModeButton;
    private SeekBar displaySizeSlider;
    private String focusRestoreKey;
    private boolean combinedView = false;
    private final java.util.Map<String, java.util.List<SourcePart>> combinedParts = new java.util.LinkedHashMap<>();
    private boolean loading = false;
    private volatile int authGeneration = 0;
    private long lastSyncAttemptAt = 0;
    private boolean wasComplete;
    private View celebration;
    private ImageViewerOverlay imageViewer;
    private boolean imageMode;
    private ProductCard imageFocusCard;
    private TextView imageModeHint;
    private ViewTreeObserver.OnGlobalFocusChangeListener imageFocusListener;
    private final Runnable dismissCelebrationTask = this::dismissCelebration;

    /** המסך המוצג כרגע. מקור אמת יחיד לניווט, לקיצורי המקשים ולמקש חזרה. */
    private enum Screen { NONE, LOGIN, HOME, SETTINGS, SAVED_LISTS, HISTORY }
    private Screen screen = Screen.NONE;
    /** לאן חוזרים ממסך רשימות שמורות, מהיסטוריית ההזמנות וממסך ההתחברות. */
    private Screen savedListsReturn = Screen.HOME, historyReturn = Screen.SAVED_LISTS;
    private boolean loginReturnToSettings = false;
    /** הפריט שהיה בפוקוס לפני שעזבנו את מסך הרשימה, כדי לחזור אליו ולא לראש הרשימה. */
    private String homeFocusKey = null;
    /** כפתורי הכותרת במסך הרשימה, לפי סדר הקריאה מימין לשמאל. */
    private final List<View> headerButtons = new ArrayList<>();
    private int homeScrollY = 0;

    /** מחליף את תוכן המסך, מעדכן את מצב הניווט ומוסיף מעבר רך. */
    private void presentScreen(View root, Screen next) {
        if (screen == Screen.HOME) rememberHomePosition();
        if (next != Screen.HOME) {
            // רכיבי מסך הרשימה הקודם כבר אינם מוצגים; אסור להמשיך לכתוב אליהם הודעות.
            messageText = null;
            searchEmptyText = null;
            retryButton = null;
            searchInput = null;
        }
        screen = next;
        setContentView(root);
        Ui.systemBars(this);
        Ui.fadeIn(root);
    }

    private void rememberHomePosition() {
        ProductCard focused = findProductCard(getCurrentFocus());
        homeFocusKey = focused == null || focused.row.getTag() == null ? null : String.valueOf(focused.row.getTag());
        homeScrollY = pageScroll == null ? 0 : pageScroll.getScrollY();
    }

    @Override public void onContentChanged() {
        super.onContentChanged();
        getWindow().getDecorView().post(() -> installActionToasts(getWindow().getDecorView()));
    }

    private void installActionToasts(View view) {
        if (view instanceof Button && !(view instanceof CheckBox)) {
            Button button = (Button) view;
            button.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP && button.isEnabled()) {
                    showButtonActionToast(button);
                }
                return false;
            });
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) installActionToasts(group.getChildAt(i));
        }
    }

    private void showButtonActionToast(Button button) {
        if (button == null) return;
        CharSequence description = button.getContentDescription();
        String label = button.getText() == null ? "" : button.getText().toString().trim();
        String message;
        String action = description == null ? "" : description.toString().trim();
        if ("הגדרות".equals(action)) message = "פתיחת מסך ההגדרות";
        else if ("איפוס ליקוט".equals(action)) return; // הפעולה עצמה מציגה אישור או הודעה מתאימה
        else if ("רשימה מאוחדת".equals(action) || "רשימה לפי חשבון".equals(action)) return; // ההחלפה מציגה הודעה משלה
        else if ("חזרה".equals(action)) message = "חזרה למסך הקודם";
        else if ("סגירת ההגדרות".equals(action)) message = "סגירת מסך ההגדרות";
        else if (!action.isEmpty()) message = action;
        else if (!label.isEmpty()) message = "פעולה: " + label;
        else message = "הפעולה הופעלה";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showActionToast(String message) {
        if (message != null && !message.trim().isEmpty())
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        imageViewer = new ImageViewerOverlay(this, imageLoader);
        SharedPreferences legacyPreferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        sourceRegistry = new SourceRegistry(this);
        secretStore = new SecretStore(this);
        savedListStore = new SavedListStore(this);
        uiPreferences = getSharedPreferences("mishnat_pickup_ui", MODE_PRIVATE);
        displayMode = Math.max(0, Math.min(5, uiPreferences.getInt("display_mode", 3)));
        sourceRegistry.migrateLegacy(legacyPreferences);
        archiveExistingSnapshots();
        SourceRegistry.Source initialSource = sourceRegistry.active();
        if (initialSource != null) {
            activeSourceId = initialSource.id;
            activeSourceDirectory = sourceRegistry.sourceDir(activeSourceId);
            preferences = sourceRegistry.prefs(activeSourceId);
        } else {
            preferences = legacyPreferences;
            activeSourceDirectory = super.getFilesDir();
        }
        configureSystemBars();
        installImageFocusListener();
        accessToken = readEncryptedToken();
        initializeCacheOwnership();
        String connectedSource = !accessToken.isEmpty() ? activeSourceId : firstConnectedAccountId();
        if (!connectedSource.isEmpty()) {
            if (!connectedSource.equals(activeSourceId)) {
                switchSource(connectedSource, () -> {
                    showHomeScreen(false);
                    syncAllAccounts();
                });
            } else {
                showHomeScreen(false);
                syncAllAccounts();
            }
        } else if (!sourceRegistry.all().isEmpty()) {
            // Imported and signed-out sources remain available offline.
            showHomeScreen(false);
        } else {
            showLoginScreen("");
        }
        handleIncomingImport(getIntent());
    }

    private String firstConnectedAccountId() {
        for (SourceRegistry.Source source : sourceRegistry.all()) {
            if (SourceRegistry.KIND_ACCOUNT.equals(source.kind)) {
                String token = secretStore.get(source.id);
                if (token != null && !token.isEmpty()) return source.id;
            }
        }
        return "";
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingImport(intent);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importFromUri(uri);
        } else if (requestCode == REQUEST_EXPORT_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri=data.getData();String id=pendingExportListId;pendingExportListId="";
            if(uri!=null&&!id.isEmpty())exportSavedListToUri(id,uri);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!showingSavedList && pendingList != null && !loading && !syncAllRunning
                && System.currentTimeMillis() - lastSyncAttemptAt > 5 * 60 * 1000L) {
            if (combinedView || sourceRegistry != null && sourceRegistry.all().size() > 1) syncAllAccounts();
            else if (!accessToken.isEmpty()) loadLatestOrder();
        }
    }

    @Override protected void onPause() {
        if (imageViewer != null) imageViewer.reset();
        setImageMode(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (imageViewer != null) imageViewer.reset();
        setImageMode(false);
        removeImageFocusListener();
        dismissCelebration();
        wasComplete = false;
        super.onDestroy();
        network.shutdownNow();
        imageLoader.shutdownNow();
        profileLoader.shutdown();
        // Let queued collection/cache commits finish instead of dropping the last user action.
        cacheWorker.shutdown();
    }

    private File activeFilesDir() {
        return activeSourceDirectory == null ? super.getFilesDir() : activeSourceDirectory;
    }

    private void configureSystemBars() {
        Ui.systemBars(this);
    }

    // ---------------------------------------------------------------------
    // Native login using the Mishnat Yosef JSON API.
    // ---------------------------------------------------------------------

    private void showLoginScreen(String notice) { showLoginScreen(notice, false); }

    private void showLoginScreen(String notice, boolean returnToSettings) {
        loginReturnToSettings = returnToSettings;
        setImageMode(false);
        if (imageViewer != null) imageViewer.reset();
        dismissCelebration();
        wasComplete = false;
        authHandled = false;
        loginBusy = false;
        loginEmailInput = null;
        loginPasswordInput = null;
        loginButton = null;
        loginProgress = null;
        loginErrorText = null;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.screen(this);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        root.addView(Ui.title(this, "משנת אוסף"));
        root.addView(Ui.text(this, "התחברות מאובטחת לחשבון משנת יוסף",
                Ui.T_BODY, Ui.MUTED, false), Ui.row(this, Ui.S1, Ui.S3));
        root.addView(buildNativeLoginForm());
        if (hasCachedOrder()) root.addView(buildCachedListButton(), Ui.row(this, Ui.S2, Ui.S2));
        Button savedLists = Ui.button(this, "רשימות שמורות", Ui.Kind.PRIMARY);
        savedLists.setOnClickListener(v -> showSavedListsScreen(true));
        root.addView(savedLists, Ui.row(this, Ui.S2, Ui.S2));
        Button importOffline = Ui.button(this, "ייבוא רשימה מקובץ", Ui.Kind.SOFT);
        importOffline.setOnClickListener(v -> openImportPicker());
        root.addView(importOffline, Ui.row(this, Ui.S2, Ui.S2));
        if (sourceRegistry != null && !sourceRegistry.all().isEmpty()) {
            Button connections = Ui.button(this, returnToSettings ? "חזרה להגדרות" : "ניהול חיבורים", Ui.Kind.SOFT);
            connections.setOnClickListener(v -> { loginReturnToSettings = false; showAccountsScreen(); });
            root.addView(connections, Ui.row(this, Ui.S2, Ui.S2));
        }
        // ההודעה מוצגת מעל הטופס, כדי שתיראה בלי גלילה.
        if (notice != null && !notice.isEmpty()) root.addView(Ui.banner(this, notice), 2);
        presentScreen(scroll, Screen.LOGIN);
        if (loginEmailInput != null) loginEmailInput.requestFocus();
    }

    private LinearLayout buildNativeLoginForm() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.shape(this, Ui.SURFACE, Ui.R_CARD, 1, Ui.P200));
        card.setPadding(Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S4),
                Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S4));
        card.addView(Ui.text(this, "כניסה לחשבון", Ui.T_SECTION, Ui.P800, true));
        card.addView(Ui.text(this, "כתובת אימייל", Ui.T_NOTE, Ui.MUTED, true),
                Ui.row(this, Ui.S3, Ui.S1));
        loginEmailInput = createLoginInput("name@example.com", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        loginEmailInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        card.addView(loginEmailInput, Ui.row(this, 0, Ui.S2));

        card.addView(Ui.text(this, "סיסמה", Ui.T_NOTE, Ui.MUTED, true),
                Ui.row(this, Ui.S2, Ui.S1));
        loginPasswordInput = createLoginInput("הקלידו את הסיסמה", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        loginPasswordInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        card.addView(loginPasswordInput, Ui.row(this, 0, Ui.S3));
        // Enter במקלדת: מהאימייל עוברים לסיסמה, ומהסיסמה שולחים את הטופס.
        loginEmailInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT || isEnterKey(event)) {
                if (loginPasswordInput != null) loginPasswordInput.requestFocus();
                return true;
            }
            return false;
        });
        loginPasswordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || isEnterKey(event)) {
                hideKeyboard(v);
                submitLogin();
                return true;
            }
            return false;
        });

        loginErrorText = Ui.banner(this, "");
        loginErrorText.setVisibility(View.GONE);
        card.addView(loginErrorText, Ui.row(this, 0, Ui.S2));

        loginProgress = new ProgressBar(this);
        loginProgress.setIndeterminate(true);
        loginProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 32), Ui.dp(this, 32));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.bottomMargin = Ui.dp(this, Ui.S2);
        card.addView(loginProgress, progressParams);

        loginButton = Ui.button(this, "התחברות", Ui.Kind.PRIMARY);
        loginButton.setOnClickListener(v -> submitLogin());
        card.addView(loginButton, Ui.row(this, 0, 0));
        return card;
    }

    private EditText createLoginInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Ui.T_BODY);
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.MUTED);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setPadding(Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3),
                Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3));
        input.setBackground(Ui.inputBackground(this));
        input.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setSelectAllOnFocus(false);
        input.setMinHeight(Ui.dp(this, 54));
        return input;
    }

    private Button buildCachedListButton() {
        Button button = Ui.button(this, "הצגת הרשימה השמורה", Ui.Kind.SOFT);
        button.setOnClickListener(v -> openCachedListFromLogin());
        return button;
    }

    private void openCachedListFromLogin() {
        if (!cacheTrusted || !hasCachedOrder()) return;
        showHomeScreen(false);
    }

    private void submitLogin() {
        if (loginBusy || authHandled || loginEmailInput == null || loginPasswordInput == null) return;
        final String email = loginEmailInput.getText().toString().trim();        final String password = loginPasswordInput.getText().toString();        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showLoginError("כתובת האימייל חסרה או אינה תקינה. בדקו את הכתובת ונסו שוב.");
            loginEmailInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showLoginError("יש להזין סיסמה.");
            loginPasswordInput.requestFocus();
            return;
        }
        loginBusy = true;
        if (loginButton != null) loginButton.setEnabled(false);
        if (loginProgress != null) loginProgress.setVisibility(View.VISIBLE);
        if (loginErrorText != null) loginErrorText.setVisibility(View.GONE);
        final int requestGeneration = authGeneration;
        network.execute(() -> {
            String token = "";
            String failure = "";
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("password", password);
                payload.put("remember", true);
                HttpResult response = rawRequest("POST", "/api/login", "", payload);
                if (response.code < 200 || response.code >= 300) {
                    failure = formatLoginError(response);
                } else {
                    token = extractToken(response.body);
                    if (token.isEmpty()) {
                        failure = "השרת אישר את הבקשה אך לא החזיר access token מוכר. "
                                + "לא נשמרה התחברות. נא לבדוק את מבנה תשובת השרת.";
                    }
                }
            } catch (Exception error) {
                failure = safeError(error);
            }
            final String resultToken = token;
            final String resultError = failure;
            mainHandler.post(() -> {
                if (isFinishing() || requestGeneration != authGeneration) return;
                loginBusy = false;
                if (loginButton != null) loginButton.setEnabled(true);
                if (loginProgress != null) loginProgress.setVisibility(View.GONE);
                if (!resultToken.isEmpty()) {
                    authHandled = false;
                    acceptLoginToken(resultToken);
                } else {
                    authHandled = false;
                    showLoginError(resultError.isEmpty() ? "ההתחברות נכשלה. נסו שוב." : resultError);
                }
            });
        });
    }

    private void showLoginError(String message) {
        if (loginErrorText == null) return;
        loginErrorText.setText(message);
        loginErrorText.setVisibility(View.VISIBLE);
    }

    private String formatLoginError(HttpResult response) {
        String serverMessage = "";
        StringBuilder details = new StringBuilder();
        try {
            JSONObject body = new JSONObject(response.body);
            serverMessage = first(body, "message", "error_description", "detail", "title", "error");
            JSONObject errors = body.optJSONObject("errors");
            if (errors == null) errors = body.optJSONObject("validation_errors");
            if (errors != null) {
                java.util.Iterator<String> keys = errors.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = errors.opt(key);
                    String label = "email".equalsIgnoreCase(key) ? "אימייל"
                            : ("password".equalsIgnoreCase(key) ? "סיסמה" : key);
                    String detail = value instanceof JSONArray ? joinJsonArray((JSONArray) value)
                            : String.valueOf(value == null ? "" : value);
                    if (!detail.isEmpty()) {
                        if (details.length() > 0) details.append("\n");
                        details.append(label).append(": ").append(detail);
                    }
                }
            }
        } catch (Exception ignored) {
            if (response.body != null && !response.body.trim().isEmpty()
                    && response.body.length() < 240 && !response.body.trim().startsWith("<")) {
                serverMessage = response.body.trim();
            }
        }
        StringBuilder result = new StringBuilder();
        if (response.code == 401 || response.code == 403) {
            result.append("פרטי ההתחברות לא אושרו. בדקו את האימייל והסיסמה.");
        } else if (response.code == 422) {
            result.append("הפרטים שנשלחו אינם תקינים. בדקו את השדות ונסו שוב.");
        } else if (response.code == 429) {
            result.append("נשלחו יותר מדי ניסיונות התחברות. המתינו מעט ונסו שוב.");
        } else if (response.code >= 500) {
            result.append("יש כרגע תקלה בשרת משנת יוסף. נסו שוב בעוד כמה דקות.");
        } else {
            result.append("ההתחברות נכשלה (קוד שרת ").append(response.code).append(").");
        }
        if (!serverMessage.isEmpty() && !serverMessage.equalsIgnoreCase("unauthorized")
                && !serverMessage.equalsIgnoreCase("validation error")) {
            result.append("\nהודעת השרת: ").append(serverMessage);
        }
        if (details.length() > 0) result.append("\n").append(details);
        return result.toString();
    }

    private String joinJsonArray(JSONArray values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            String value = String.valueOf(values.opt(i));
            if (value.isEmpty() || "null".equals(value)) continue;
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private void acceptLoginToken(String token) {
        if (authHandled || isFinishing()) return;
        authHandled = true;
        final int generation = ++authGeneration;
        loading = false;
        final String tokenValue = token;
        final String newOwner = DataGuard.ownerFromToken(tokenValue);
        final String tokenFingerprint = DataGuard.tokenFingerprint(tokenValue);
        cacheWorker.execute(() -> mainHandler.post(() -> completeLoginToken(tokenValue, generation, newOwner, tokenFingerprint)));
    }

    private void completeLoginToken(String token, int generation, String newOwner, String tokenFingerprint) {
        if (isFinishing() || generation != authGeneration) return;
        try {
            SourceRegistry.Source target = sourceRegistry.findByOwner(newOwner);
            if (target == null && newOwner.isEmpty()) {
                for (SourceRegistry.Source candidate : sourceRegistry.all()) {
                    String saved = sourceRegistry.prefs(candidate.id).getString(DataGuard.K_TOKEN_FINGERPRINT, "");
                    if (!saved.isEmpty() && saved.equals(tokenFingerprint)) { target = candidate; break; }
                }
            }
            if (target == null) {
                String email = emailFromToken(token);
                target = sourceRegistry.create(SourceRegistry.KIND_ACCOUNT,
                        email.isEmpty() ? "חיבור חדש" : email, newOwner);
            }
            if (SourceRegistry.KIND_IMPORTED.equals(target.kind)) sourceRegistry.promoteToAccount(target.id);
            sourceRegistry.setActive(target.id);
            activeSourceId = target.id;
            activeSourceDirectory = sourceRegistry.sourceDir(activeSourceId);
            preferences = sourceRegistry.prefs(activeSourceId);
            final String oldOwner = preferences.getString(DataGuard.K_OWNER, "");
            final String oldFingerprint = preferences.getString(DataGuard.K_TOKEN_FINGERPRINT, "");
            boolean sameAccount = (!newOwner.isEmpty() && newOwner.equals(oldOwner))
                    || (newOwner.isEmpty() && !oldFingerprint.isEmpty() && oldFingerprint.equals(tokenFingerprint));
            saveEncryptedToken(token);
            preferences.edit().putString(DataGuard.K_TOKEN_FINGERPRINT, tokenFingerprint)
                    .remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY).apply();
            accessToken = token;
            activeOwner = !newOwner.isEmpty() ? newOwner : (sameAccount ? oldOwner : target.ownerKey);
            sourceRegistry.updateOwner(target.id, activeOwner);
            sourceRegistry.rename(target.id, "חיבור ללא שם");
            preferences.edit().remove("profile_name_loaded")
                    .remove("profile_lname_loaded_v2").apply();
            cacheTrusted = sameAccount && isCacheManifestValid(activeOwner);
            if (!sameAccount) {
                invalidateSavedOrderPreferences(newOwner);
                queueOrderDataWipe(generation);
            }
            showHomeScreen(true);
        } catch (Exception e) {
            authHandled = false;
            showLoginScreen("לא ניתן לשמור את ההתחברות בצורה מאובטחת: " + safeError(e));
        }
    }
    private String emailFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            byte[] payload = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP);
            return new JSONObject(new String(payload, "UTF-8")).optString("email", "").trim();
        } catch (Exception ignored) { return ""; }
    }

    private void invalidateSavedOrderPreferences(String owner) {
        // Archive the previous order and its progress before invalidating account-scoped metadata.
        archiveCurrentSnapshot(activeSourceId);
        SharedPreferences.Editor edit = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(DataGuard.COLLECTED_PREFIX) || key.startsWith("partial_")) edit.remove(key);
        }
        edit.remove(DataGuard.K_ORDER).remove(DataGuard.K_PRINT)
                .remove(DataGuard.K_TOKEN_FINGERPRINT)
                .remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY)
                .putString(DataGuard.K_OWNER, owner == null ? "" : owner).commit();
        collected.clear();
        partialCollected.clear();
        // Product images are public, URL-keyed shared cache entries; account/order
        // changes must not evict images that are still valid for other lists.
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
            if (new File(activeFilesDir(), name).exists()) return true;
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
            setImageMode(false);
            if (imageViewer != null) imageViewer.reset();
            dismissCelebration();
            wasComplete = false;
        };
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run();
        else mainHandler.post(cleanup);
        DataGuard.wipeOrderData(activeFilesDir(), preferences);
    }

    private boolean isCacheManifestValid(String owner) {
        File manifest = new File(new File(activeFilesDir(), "pickup_order_cache"), "order.json");
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
        for (ProductRow row : products) if (collectedQuantity(row) + 0.000001 < parseQuantity(row.amount)) return false;
        return true;
    }

    private double collectedQuantity(ProductRow row) {
        if (row == null) return 0;
        if (collected.contains(row.key)) return parseQuantity(row.amount);
        Double value = partialCollected.get(row.key);
        return value == null ? 0 : Math.max(0, value);
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
        performFeedback(true, true);
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
        if (layer.getParent() instanceof ViewGroup) {            ((ViewGroup) layer.getParent()).removeView(layer);
        }    }

    /** Grid mode has card-only navigation; images and text children never receive DPAD focus. */
    private boolean handleGridNavigationKey(KeyEvent event) {
        if (displayMode != 5 || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT
                && key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;

        ProductCard focused = findProductCard(getCurrentFocus());
        if (focused == null || focused.row == null) return false;
        if (!(focused.row.getParent() instanceof ViewGroup)) return true;

        ViewGroup container = (ViewGroup) focused.row.getParent();
        int index = container.indexOfChild(focused.row);
        int count = container.getChildCount();
        if (index < 0 || count == 0) return true;

        int column = index % 3;
        int target = -1;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            // In RTL, the next item in row order is visually to the left.
            if (column < 2 && index + 1 < count) target = index + 1;
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (column > 0) target = index - 1;
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (index + 3 < count) target = index + 3;
            // שורה אחרונה חלקית: ממשיכים לכרטיס האחרון בה במקום להיתקע.
            else if (index / 3 < (count - 1) / 3) target = count - 1;
            else if (container == pendingList && collectedList != null
                    && collectedList.getChildCount() > 0) {
                return focusGridCard(collectedList, Math.min(column, collectedList.getChildCount() - 1));
            }
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (index >= 3) target = index - 3;
            else if (container == collectedList && pendingList != null
                    && pendingList.getChildCount() > 0) {
                int lastRowStart = ((pendingList.getChildCount() - 1) / 3) * 3;
                return focusGridCard(pendingList,
                        Math.min(lastRowStart + column, pendingList.getChildCount() - 1));
            }
        }
        if (target >= 0) return focusGridCard(container, target);
        // Every card in the first grid row can move focus to the fixed header
        // (also when every item is already collected and the pending grid is empty).
        if (key == KeyEvent.KEYCODE_DPAD_UP && index < 3 && (container == pendingList
                || (container == collectedList && (pendingList == null || pendingList.getChildCount() == 0))))
            return moveFocusToHomeTop();
        // Do not let focus escape from the card grid to images, labels or other controls.
        return true;
    }

    private boolean moveFocusToHomeTop() {
        if (pageScroll != null) pageScroll.post(() -> pageScroll.smoothScrollTo(0, 0));
        // למעלה מהפריט הראשון מגיעים לשדה החיפוש, וממנו - לכפתורי הכותרת.
        if (searchInput != null && searchInput.getParent() != null) {
            EditText target = searchInput;
            target.post(target::requestFocus);
            return true;
        }
        View topButton = getWindow().getDecorView().findViewWithTag("home_first_header_button");
        if (topButton != null) topButton.post(() -> topButton.requestFocus());
        return true;
    }

    private boolean handleFirstListItemUp(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP) return false;
        if (displayMode == 5) return false; // במצב רשת הטיפול נעשה בניווט הרשת
        ProductCard focused = findProductCard(getCurrentFocus());
        if (focused == null || focused.row == null || focused.row != firstCard()) return false;
        return moveFocusToHomeTop();
    }

    private boolean focusGridCard(ViewGroup container, int index) {
        if (container == null || index < 0 || index >= container.getChildCount()) return true;
        View target = container.getChildAt(index);
        ProductCard card = findProductCard(target);
        if (card != null) card.row.requestFocus();
        else target.requestFocus();
        return true;
    }

    private boolean isHomeShortcutScreen() {
        // קיצורי הספרות פעילים רק במסך הרשימה עצמו. בעבר הם נשארו פעילים גם במסך
        // ההתחברות ובהיסטוריה (למשל # היה מקפיץ מההתחברות לרשימה).
        return screen == Screen.HOME && pageScroll != null;
    }

    private static boolean isEnterKey(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int key = event.getKeyCode();
        return key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER
                || key == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    private void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private boolean isTextEntryFocused() {
        return getCurrentFocus() instanceof EditText;
    }

    private int navigationKeyForShortcut(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_2: case KeyEvent.KEYCODE_NUMPAD_2: return KeyEvent.KEYCODE_DPAD_UP;
            case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_NUMPAD_4: return KeyEvent.KEYCODE_DPAD_LEFT;
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_NUMPAD_6: return KeyEvent.KEYCODE_DPAD_RIGHT;
            case KeyEvent.KEYCODE_8: case KeyEvent.KEYCODE_NUMPAD_8: return KeyEvent.KEYCODE_DPAD_DOWN;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    private boolean handleHomeShortcut(KeyEvent event) {
        int key = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (key == KeyEvent.KEYCODE_MENU) {
            if (down && event.getRepeatCount() == 0) {
                showSettingsScreen();
                showActionToast("פתיחת מסך ההגדרות");
            }
            return true;
        }
        if (isTextEntryFocused()) return false;
        if (key == KeyEvent.KEYCODE_5 || key == KeyEvent.KEYCODE_NUMPAD_5) {
            if (down) {
                if (event.getRepeatCount() == 0) {
                    keyFivePending = true;
                    keyFiveLongHandled = false;
                } else if (keyFivePending && !keyFiveLongHandled) {
                    ProductCard focused = findProductCard(getCurrentFocus());
                    if (focused != null) {
                        keyFiveLongHandled = true;
                        if (remainingQuantity(focused.product) > 0.000001)
                            showPartialCollectionDialog(focused.product.key);
                    }
                }
            } else if (keyFivePending) {
                keyFivePending = false;
                if (keyFiveLongHandled) {
                    keyFiveLongHandled = false;
                } else {
                    performFocusedSelection();
                }
            }
            return true;
        }
        if (event.getRepeatCount() > 0 && down) return isShortcutKey(key);
        if (!down) return isShortcutKey(key);
        switch (key) {
            case KeyEvent.KEYCODE_1: case KeyEvent.KEYCODE_NUMPAD_1:
                cycleDisplayMode(); return true;
            case KeyEvent.KEYCODE_3: case KeyEvent.KEYCODE_NUMPAD_3:
                focusSearch(); return true;
            case KeyEvent.KEYCODE_0: case KeyEvent.KEYCODE_NUMPAD_0:
                if (confirmResetCollection()) showActionToast("נפתחה בקשת אישור לאיפוס הליקוט");
                else showActionToast("אין סימוני איסוף לאיפוס");
                return true;
            case KeyEvent.KEYCODE_7: case KeyEvent.KEYCODE_NUMPAD_7:
                // גם הפוקוס עובר לראש הרשימה; אחרת המקש הבא היה מקפיץ חזרה לפריט הקודם.
                if (pageScroll != null) pageScroll.scrollTo(0, 0);
                focusFirstPending();
                showActionToast("הועברת לראש רשימת האיסוף");
                return true;
            case KeyEvent.KEYCODE_9: case KeyEvent.KEYCODE_NUMPAD_9:
                if (pageScroll != null && page != null)
                    pageScroll.scrollTo(0, Math.max(0, page.getHeight() - pageScroll.getHeight()));
                focusLastCard();
                showActionToast("הועברת לסוף רשימת האיסוף");
                return true;
            case KeyEvent.KEYCODE_STAR: case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
                ProductCard focused = findProductCard(getCurrentFocus());
                if (focused != null) {
                    openProductImage(focused);
                    showActionToast("הצגת תמונת הפריט: " + focused.product.name);
                } else showActionToast("יש לבחור פריט כדי להציג את תמונתו");
                return true;
            case KeyEvent.KEYCODE_POUND:
                toggleListViewMode();
                return true;
            default:
                return false;
        }
    }

    private boolean isShortcutKey(int key) {
        return key == KeyEvent.KEYCODE_MENU
                || key == KeyEvent.KEYCODE_1 || key == KeyEvent.KEYCODE_3
                || key == KeyEvent.KEYCODE_5 || key == KeyEvent.KEYCODE_7
                || key == KeyEvent.KEYCODE_9 || key == KeyEvent.KEYCODE_0
                || key == KeyEvent.KEYCODE_STAR || key == KeyEvent.KEYCODE_POUND
                || key == KeyEvent.KEYCODE_NUMPAD_0 || key == KeyEvent.KEYCODE_NUMPAD_1
                || key == KeyEvent.KEYCODE_NUMPAD_3 || key == KeyEvent.KEYCODE_NUMPAD_5
                || key == KeyEvent.KEYCODE_NUMPAD_7 || key == KeyEvent.KEYCODE_NUMPAD_9
                || key == KeyEvent.KEYCODE_NUMPAD_MULTIPLY;
    }

    private void performFocusedSelection() {
        View focused = getCurrentFocus();
        ProductCard card = findProductCard(focused);
        if (card != null) {
            card.row.performClick();
            return;
        }
        if (focused instanceof Button && !(focused instanceof CheckBox)) {
            Button button = (Button) focused;
            if (button.isEnabled()) {
                showButtonActionToast(button);
                button.performClick();
            }
        } else if (focused != null) focused.performClick();
    }

    private void setDisplayModeFromSlider(int mode) {
        int normalized = Math.max(0, Math.min(5, mode));
        // displayMode is the single source of truth for the shortcut and Settings slider.
        displayMode = normalized;
        if (displaySizeSlider != null && displaySizeSlider.getProgress() != normalized)
            displaySizeSlider.setProgress(normalized);
        uiPreferences.edit().putInt("display_mode", normalized).apply();
    }

    private void cycleDisplayMode() {
        final String[] labels = {"צ׳ק ליסט צפוף", "70%", "85%", "100%", "115%", "רשת · 3 כרטיסים בשורה"};
        // Keep keyboard focus while replacing the actual list containers. The previous
        // implementation changed card styling but left the old LinearLayout/GridLayout
        // containers in place, so shortcut mode 5 could never match Settings mode 5.
        View focused = getCurrentFocus();
        Object tag = focused == null ? null : focused.getTag();
        focusRestoreKey = tag instanceof String ? (String) tag : null;
        int next = (displayMode + 1) % labels.length;
        setDisplayModeFromSlider(next);
        rebuildProductContainersForDisplayMode();
        renderProducts(false);
        wireFocusOrder();
        showActionToast("גודל התצוגה: " + labels[next]);
    }

    private void rebuildProductContainersForDisplayMode() {
        if (page == null || collectedSection == null || pendingList == null || collectedList == null)
            return;
        if (pendingList.getParent() == page) {
            int index = page.indexOfChild(pendingList);
            page.removeViewAt(index);
            pendingList = createProductContainer();
            page.addView(pendingList, index, new LinearLayout.LayoutParams(-1, -2));
        }
        if (collectedList.getParent() == collectedSection) {
            int index = collectedSection.indexOfChild(collectedList);
            collectedSection.removeViewAt(index);
            collectedList = createProductContainer();
            collectedSection.addView(collectedList, index, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void focusSearch() {
        if (searchInput == null) {
            showActionToast("שדה החיפוש אינו זמין במסך הנוכחי");
            return;
        }
        searchInput.requestFocus();
        searchInput.setSelection(searchInput.getText().length());
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        showActionToast("שדה חיפוש הפריטים נפתח");
    }

    private void toggleListViewMode() {
        if (sourceRegistry == null) return;
        if (syncAllRunning) { showActionToast("אפשר להחליף תצוגה בסיום הסנכרון"); return; }
        String next = "COMBINED_SUM".equals(sourceRegistry.viewMode())
                ? "COMBINED_BY_SOURCE" : "COMBINED_SUM";
        sourceRegistry.setViewMode(next);
        showingSavedList = false;
        showHomeScreen(false);
        showActionToast("מצב הרשימה שונה ל־" +
                ("COMBINED_SUM".equals(next) ? "רשימה מאוחדת מכל החשבונות" : "רשימות נפרדות לפי חשבון"));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (imageViewer != null && imageViewer.onKey(event)) return true;
        // במסכי ההגדרות, הרשימות השמורות, ההיסטוריה וההתחברות: 2/4/6/8 מנווטים ו-5 בוחר,
        // בדיוק כמו במסך הרשימה, כל עוד הפוקוס אינו בשדה טקסט.
        if (screen != Screen.HOME && screen != Screen.NONE && !isTextEntryFocused()) {
            int code = event.getKeyCode();
            int mapped = navigationKeyForShortcut(code);
            if (code == KeyEvent.KEYCODE_5 || code == KeyEvent.KEYCODE_NUMPAD_5) mapped = KeyEvent.KEYCODE_DPAD_CENTER;
            if (mapped != KeyEvent.KEYCODE_UNKNOWN) {
                return dispatchKeyEvent(new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), mapped, event.getRepeatCount(), event.getMetaState()));
            }
        }
        if (isHomeShortcutScreen() && event.getKeyCode() != KeyEvent.KEYCODE_MENU) {
            int navigationKey = isTextEntryFocused() ? KeyEvent.KEYCODE_UNKNOWN
                    : navigationKeyForShortcut(event.getKeyCode());
            if (navigationKey != KeyEvent.KEYCODE_UNKNOWN) {
                KeyEvent mapped = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), navigationKey, event.getRepeatCount(), event.getMetaState());
                return dispatchKeyEvent(mapped);
            }
            if (handleHomeShortcut(event)) return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            // Menu פותח את ההגדרות מהרשימה, וסוגר אותן כשהן פתוחות. בשאר המסכים אין לו פעולה.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (screen == Screen.HOME) { showSettingsScreen(); showActionToast("פתיחת מסך ההגדרות"); }
                else if (screen == Screen.SETTINGS) { closeSettings(); showActionToast("סגירת מסך ההגדרות"); }
            }
            return true;
        }
        if (handleFirstListItemUp(event)) return true;
        if (handleGridNavigationKey(event)) return true;
        if (handleImageModeKey(event)) return true;
        if (celebration != null && event.getAction() == KeyEvent.ACTION_DOWN) dismissCelebration();
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            View focused = getCurrentFocus();
            if (focused instanceof Button && !(focused instanceof CheckBox))
                showButtonActionToast((Button) focused);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (imageViewer != null && imageViewer.isOpen()) { imageViewer.reset(); return; }
        if (celebration != null) { dismissCelebration(); return; }
        switch (screen) {
            case SETTINGS: closeSettings(); return;
            case SAVED_LISTS: leaveSavedListsScreen(); return;
            case HISTORY: leaveHistoryScreen(); return;
            case LOGIN:
                // מסך התחברות שנפתח מההגדרות (הוספת חשבון / התחברות מחדש) חוזר להגדרות.
                if (loginReturnToSettings && sourceRegistry != null && !sourceRegistry.all().isEmpty()) {
                    loginReturnToSettings = false;
                    showSettingsScreen();
                    return;
                }
                break;
            case HOME:
                // חזרה מהחיפוש: קודם מנקים את החיפוש ומחזירים את הפוקוס לרשימה.
                if (searchInput != null && (searchInput.hasFocus() || searchQuery.trim().length() > 0)) {
                    boolean hadText = searchQuery.trim().length() > 0;
                    hideKeyboard(searchInput);
                    if (hadText) searchInput.setText("");
                    searchInput.clearFocus();
                    focusFirstPending();
                    if (hadText) showActionToast("החיפוש נוקה");
                    return;
                }
                break;
            default: break;
        }
        super.onBackPressed();
    }

    private void closeSettings() {
        accountsScreenVisible = false;
        showHomeScreen(false);
    }

    private void leaveHistoryScreen() {
        if (historyReturn == Screen.SETTINGS) showSettingsScreen();
        else showSavedListsScreen(savedListsReturn);
    }

    // ---------------------------------------------------------------------
    // Main pickup checklist screen
    // ---------------------------------------------------------------------

    private void showHomeScreen(boolean loadData) {
        accountsScreenVisible = false;
        savedListsScreenVisible = false;
        savedListsReturnToLogin = false;
        logoutButton = null;
        resetButton = null;
        retryButton = null;
        loginReturnToSettings = false;
        // הפריט שהיה בפוקוס נשמר לפני בניית המסך מחדש (גם במעבר מרשימה לרשימה).
        if (screen == Screen.HOME) rememberHomePosition();
        final String restoreKey = homeFocusKey;
        final int restoreScroll = homeScrollY;
        homeFocusKey = null;
        homeScrollY = 0;
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout header = buildHomeHeader();
        pageScroll = buildProductScroll();
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        screen = Screen.NONE; // presentScreen לא ידרוס את המיקום שנשמר כבר
        presentScreen(root, Screen.HOME);
        combinedView = !showingSavedList && sourceRegistry != null && !sourceRegistry.all().isEmpty() && !syncAllRunning;
        if (combinedView) loadCombinedSnapshot();
        else if (cacheTrusted && hasCachedOrder()) loadCachedOrder();
        if (products.isEmpty() && !loadData && (messageText == null || messageText.getVisibility() != View.VISIBLE))
            showSyncMessage(accessToken.isEmpty()
                    ? "אין עדיין רשימה להצגה. אפשר להתחבר לחשבון או לייבא רשימה דרך ההגדרות."
                    : "אין עדיין רשימה להצגה. אפשר לסנכרן דרך ההגדרות.", false);
        renderProducts(false);
        wireFocusOrder();
        pageScroll.post(() -> restoreHomePosition(restoreKey, restoreScroll));
        if (loadData && !accessToken.isEmpty()) loadLatestOrder();
    }

    /** מחזיר את הפוקוס לפריט שהיה בחור לפני היציאה מהרשימה, או לפריט הראשון. */
    private void restoreHomePosition(String key, int scrollY) {
        if (screen != Screen.HOME || pageScroll == null) return;
        ProductCard card = null;
        if (key != null) card = key.startsWith("partial:")
                ? partialCards.get(key.substring(8)) : productCards.get(key);
        if (card != null && card.row.getParent() != null && card.row.requestFocus()) return;
        if (scrollY > 0) pageScroll.scrollTo(0, scrollY);
        else focusFirstPending();
    }

    private void openImportPicker() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.setType("*/*");
        open.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json","text/plain","application/zip","application/octet-stream"});
        try { startActivityForResult(open, REQUEST_IMPORT_FILE); }
        catch (android.content.ActivityNotFoundException e) {
            Intent get = new Intent(Intent.ACTION_GET_CONTENT);
            get.addCategory(Intent.CATEGORY_OPENABLE); get.setType("*/*");
            try { startActivityForResult(get, REQUEST_IMPORT_FILE); }
            catch (android.content.ActivityNotFoundException ignored) {
                showSyncMessage("לא נמצא בורר קבצים. אפשר להעתיק קובץ לתיקייה המוצגת במסך ניהול החיבורים.", false);
            }
        }
    }

    private void showImportFolder() {
        File dir=getExternalFilesDir("import");
        if(dir==null){showSyncMessage("תיקיית הייבוא אינה זמינה במכשיר.",false);return;}
        if(!dir.exists())dir.mkdirs();
        File[] files=dir.listFiles();
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=Ui.dialogPanel(this);
        panel.addView(Ui.text(this,"קבצים בתיקיית import",Ui.T_NAME,Ui.P900,true));
        panel.addView(Ui.text(this,dir.getAbsolutePath(),Ui.T_NOTE,Ui.MUTED,false),Ui.row(this,Ui.S2,Ui.S2));
        int count=0;
        if(files!=null)for(File file:files)if(file.isFile()&&isImportFile(file.getName())){
            count++;Button b=Ui.button(this,file.getName(),Ui.Kind.SOFT);
            b.setOnClickListener(v->{dialog.dismiss();importFromFile(file);});
            panel.addView(b,Ui.row(this,Ui.S2,0));
        }
        if(count==0)panel.addView(Ui.text(this,"לא נמצאו קובצי JSON, TXT או ZIP.",Ui.T_BODY,Ui.MUTED,false),Ui.row(this,Ui.S2,Ui.S2));
        Button close=Ui.button(this,"סגירה",Ui.Kind.SOFT);close.setOnClickListener(v->dialog.dismiss());panel.addView(close,Ui.row(this,Ui.S3,0));
        dialog.setContentView(panel);dialog.show();styleConfirmWindow(dialog);
    }

    private boolean isImportFile(String name) {
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        return n.endsWith(".json")||n.endsWith(".txt")||n.endsWith(".zip");
    }

    private void importFromFile(File file) {
        try { importInput(new FileInputStream(file)); }
        catch(Exception e){showSyncMessage("לא ניתן לפתוח את הקובץ: "+safeError(e),false);}
    }

    private void importFromUri(Uri uri) {
        try {
            InputStream in=getContentResolver().openInputStream(uri);
            if(in==null)throw new Exception("לא ניתן לפתוח את הקובץ שנבחר.");
            importInput(in);
        } catch(Exception e) { showSyncMessage("ייבוא הקובץ נכשל: "+safeError(e),false); }
    }

    private void importInput(InputStream input) {
        final File work=new File(getCacheDir(),"pickup_import_"+System.currentTimeMillis());
        showSyncMessage("בודק את קובץ הייבוא…",false);
        cacheWorker.execute(() -> {
            try {
                ImportParser.Bundle bundle=ImportParser.parse(input,work);
                mainHandler.post(() -> showImportSummary(bundle));
            } catch(Exception e) {
                final String message=e.getMessage()==null?"קובץ לא תקין.":e.getMessage();
                mainHandler.post(() -> showSyncMessage("הייבוא לא בוצע: "+message,false));
            }
        });
    }

    private void handleIncomingImport(Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();Uri uri=null;
        if(Intent.ACTION_VIEW.equals(action))uri=intent.getData();
        else if(Intent.ACTION_SEND.equals(action)) {
            Object stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(stream instanceof Uri)uri=(Uri)stream;
            if(uri==null&&intent.getClipData()!=null&&intent.getClipData().getItemCount()>0)
                uri=intent.getClipData().getItemAt(0).getUri();
        }
        if(uri!=null)importFromUri(uri);
    }

    private void showImportSummary(ImportParser.Bundle bundle) {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=Ui.dialogPanel(this);
        panel.addView(Ui.text(this,"נמצאו "+bundle.lists.size()+" רשימות",Ui.T_NAME,Ui.P900,true));
        boolean replacesExisting = false;
        for(ImportParser.ImportedList list:bundle.lists) {
            String name=list.accountName.isEmpty()?"רשימה מיובאת":list.accountName;
            String owner=DataGuard.sha256(list.accountId.toLowerCase(Locale.ROOT));
            replacesExisting |= sourceRegistry.findByOwner(owner)!=null;
            panel.addView(Ui.text(this,name+" · "+(list.orderName.isEmpty()?"הזמנה "+list.orderId:list.orderName)
                    +" · "+list.items.size()+" מוצרים",Ui.T_NOTE,Ui.INK,false),Ui.row(this,Ui.S2,0));
        }
        if(replacesExisting) panel.addView(Ui.banner(this,"נמצא מקור קיים. בדקו שתאריך העדכון בקובץ אינו ישן יותר; הייבוא עשוי להחליף את הרשימה המקומית."),Ui.row(this,Ui.S2,0));
        Button cancel=Ui.button(this,"ביטול",Ui.Kind.SOFT);
        Button apply=Ui.button(this,"ייבא רשימות",Ui.Kind.PRIMARY);
        cancel.setOnClickListener(v->{dialog.dismiss();ImportParser.delete(bundle.workDir);});
        apply.setOnClickListener(v->{
            dialog.dismiss();
            if(hasConnectedImportConflict(bundle))showImportConflict(bundle);
            else commitImport(bundle,false);
        });
        Button saveSeparate=Ui.button(this,"שמור כרשימות עצמאיות",Ui.Kind.SOFT);
        saveSeparate.setOnClickListener(v->{dialog.dismiss();archiveImportedBundle(bundle,false);});
        Button addCurrent=Ui.button(this,"הוסף לרשימה הנוכחית",Ui.Kind.SOFT);
        addCurrent.setEnabled(!activeSourceId.isEmpty()&&!currentOrderId.isEmpty());
        addCurrent.setOnClickListener(v->{dialog.dismiss();archiveImportedBundle(bundle,true);});
        panel.addView(saveSeparate,Ui.row(this,Ui.S3,Ui.S1));
        panel.addView(addCurrent,Ui.row(this,0,Ui.S1));
        panel.addView(apply,Ui.row(this,Ui.S3,Ui.S1));panel.addView(cancel);
        // סגירה במקש חזרה או בנגיעה מחוץ לחלון מנקה את קובצי הייבוא הזמניים.
        dialog.setOnCancelListener(d->ImportParser.delete(bundle.workDir));
        dialog.setContentView(panel);dialog.show();styleConfirmWindow(dialog);
        saveSeparate.requestFocus();
    }

    private void archiveImportedBundle(ImportParser.Bundle bundle,boolean mergeCurrent) {
        if(bundle==null||bundle.lists.isEmpty())return;
        SourceRegistry.Source source=sourceRegistry.find(activeSourceId);
        if(source==null||activeOwner.isEmpty()) {
            Toast.makeText(this,"כדי לשמור קובץ כרשימה עצמאית צריך להתחבר לחשבון.",Toast.LENGTH_LONG).show();
            ImportParser.delete(bundle.workDir);return;
        }
        String currentEntryId="";
        if(mergeCurrent) {
            archiveCurrentSnapshot(activeSourceId);
            for(SavedListStore.Entry entry:savedListStore.list())
                if(entry.sourceId.equals(activeSourceId)&&entry.owner.equals(activeOwner)&&entry.orderId.equals(currentOrderId)){currentEntryId=entry.id;break;}
            if(currentEntryId.isEmpty()) {
                Toast.makeText(this,"לא נמצאה הרשימה הנוכחית בארכיון. הקובץ לא מוזג.",Toast.LENGTH_LONG).show();
                ImportParser.delete(bundle.workDir);return;
            }
        }
        final String targetId=currentEntryId;
        cacheWorker.execute(()->{
            List<String> archived=new ArrayList<>();
            try {
                int index=0;
                for(ImportParser.ImportedList list:bundle.lists) {
                    JSONArray products=new JSONArray();
                    for(ImportParser.Item item:list.items) {
                        String key=item.id==null||item.id.trim().isEmpty()
                                ? "import-"+DataGuard.sha256(item.name).substring(0,16) : item.id;
                        String note=item.note==null?"":item.note;
                        if(item.unit!=null&&!item.unit.trim().isEmpty())note=note.isEmpty()?item.unit:note+" · "+item.unit;
                        if(item.category!=null&&!item.category.trim().isEmpty())note=note.isEmpty()?item.category:note+" · "+item.category;
                        String imagePath="";
                        File image=bundle.imageFile(item.image);
                        if(image!=null&&image.isFile())imagePath=image.getAbsolutePath();
                        products.put(new JSONObject().put("key",key).put("name",item.name)
                                .put("amount",item.quantity==null||item.quantity.isEmpty()?"1":item.quantity)
                                .put("note",note).put("category",item.category==null?"":item.category).put("image_url",item.imageUrl==null?"":item.imageUrl)
                                .put("image_file",imagePath));
                    }
                    long syntheticId=System.currentTimeMillis()+index++;
                    String title=list.orderName==null||list.orderName.trim().isEmpty()
                            ? (list.accountName==null||list.accountName.trim().isEmpty()?"רשימה מיובאת":list.accountName)
                            : list.orderName;
                    JSONObject snapshot=new JSONObject().put("order_id",syntheticId).put("owner",activeOwner)
                            .put("order_name",title).put("updated_at",list.updatedAt==null?"":list.updatedAt)
                            .put("imported",true).put("products",products);
                    if(!savedListStore.archiveSnapshot(source.id,source.label,snapshot,sourceRegistry.prefs(source.id)))
                        throw new Exception("לא ניתן לשמור את הרשימה '"+title+"'.");
                    String ownerKey=activeOwner.substring(0,Math.min(12,activeOwner.length()));
                    archived.add(source.id+"_"+ownerKey+"_"+syntheticId);
                }
                if(mergeCurrent)for(String id:archived)
                    if(!savedListStore.mergeEntries(targetId,id))throw new Exception("אחד הקבצים לא מוזג לרשימה הנוכחית; העותקים המקוריים נשארו בארכיון.");
                mainHandler.post(()->{
                    ImportParser.delete(bundle.workDir);showSavedListsScreen(false);
                    Toast.makeText(this,mergeCurrent?"הקבצים נוספו לרשימה הנוכחית ונשמרו גם כעותקים נפרדים.":"הקבצים נשמרו כרשימות עצמאיות.",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                String error=safeError(e);mainHandler.post(()->{
                    ImportParser.delete(bundle.workDir);showSavedListsScreen(false);
                    Toast.makeText(this,"שמירת הקבצים נכשלה: "+error,Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean hasConnectedImportConflict(ImportParser.Bundle bundle) {
        for(ImportParser.ImportedList list:bundle.lists) {
            String owner=DataGuard.sha256(list.accountId.toLowerCase(Locale.ROOT));
            SourceRegistry.Source existing=sourceRegistry.findByOwner(owner);
            if(existing!=null&&SourceRegistry.KIND_ACCOUNT.equals(existing.kind))return true;
        }
        return false;
    }

    private void showImportConflict(ImportParser.Bundle bundle) {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=Ui.dialogPanel(this);
        panel.addView(Ui.text(this,"נמצא חשבון מחובר עם אותו מזהה",Ui.T_NAME,Ui.P900,true));
        panel.addView(Ui.text(this,"כדי למנוע כפילות בתצוגה המאוחדת, בחר כיצד לייבא.",Ui.T_BODY,Ui.MUTED,false),Ui.row(this,Ui.S2,Ui.S3));
        Button update=Ui.button(this,"עדכן את החשבון המחובר",Ui.Kind.PRIMARY);
        Button separate=Ui.button(this,"הוסף כמקור נפרד",Ui.Kind.SOFT);
        Button cancel=Ui.button(this,"ביטול",Ui.Kind.SOFT);
        update.setOnClickListener(v->{dialog.dismiss();commitImport(bundle,false);});
        separate.setOnClickListener(v->{dialog.dismiss();commitImport(bundle,true);});
        cancel.setOnClickListener(v->{dialog.dismiss();ImportParser.delete(bundle.workDir);});
        panel.addView(update);panel.addView(separate,Ui.row(this,Ui.S2,0));panel.addView(cancel,Ui.row(this,Ui.S2,0));
        dialog.setOnCancelListener(d->ImportParser.delete(bundle.workDir));
        dialog.setContentView(panel);dialog.show();styleConfirmWindow(dialog);
    }

    private void commitImport(ImportParser.Bundle bundle,boolean separateConnected) {
        showSyncMessage("שומר את הרשימות המיובאות…",false);
        cacheWorker.execute(() -> {
            List<String> created=new ArrayList<>();
            List<File[]> committed=new ArrayList<>();
            List<String> targetIds=new ArrayList<>();
            try {
                if(bundle.lists.size()>SourceRegistry.MAX_SOURCES-sourceRegistry.all().size()) {
                    int newCount=0;
                    for(ImportParser.ImportedList l:bundle.lists) {
                        String owner=DataGuard.sha256(l.accountId.toLowerCase(Locale.ROOT));
                        SourceRegistry.Source ex=sourceRegistry.findByOwner(owner);
                        if(ex==null||(separateConnected&&SourceRegistry.KIND_ACCOUNT.equals(ex.kind)))newCount++;
                    }
                    if(newCount>SourceRegistry.MAX_SOURCES-sourceRegistry.all().size())
                        throw new Exception("אין מספיק מקום. אפשר לשמור עד 10 מקורות.");
                }
                List<PreparedImport> prepared=new ArrayList<>();
                for(ImportParser.ImportedList list:bundle.lists) {
                    String owner=DataGuard.sha256(list.accountId.toLowerCase(Locale.ROOT));
                    SourceRegistry.Source source=sourceRegistry.findByOwner(owner);
                    if(source!=null&&SourceRegistry.KIND_ACCOUNT.equals(source.kind)&&separateConnected) {
                        String separateOwner=owner+"#imported";
                        source=sourceRegistry.findByOwner(separateOwner);
                        if(source==null){source=sourceRegistry.create(SourceRegistry.KIND_IMPORTED,
                                list.accountName.isEmpty()?"רשימה מיובאת":list.accountName,separateOwner);created.add(source.id);}
                    } else if(source==null) {
                        source=sourceRegistry.create(SourceRegistry.KIND_IMPORTED,
                                list.accountName.isEmpty()?"רשימה מיובאת":list.accountName,owner);created.add(source.id);
                    }
                    if(SourceRegistry.KIND_IMPORTED.equals(source.kind)&&!list.accountName.isEmpty())
                        sourceRegistry.rename(source.id,list.accountName);
                    PreparedImport item=prepareImportedList(bundle,list,source,owner);
                    prepared.add(item);targetIds.add(source.id);
                }
                for(PreparedImport item:prepared) {
                    archiveCurrentSnapshot(item.source.id);
                    File root=new File(sourceRegistry.sourceDir(item.source.id),"pickup_order_cache");
                    File temp=new File(sourceRegistry.sourceDir(item.source.id),"pickup_order_cache_tmp");
                    File backup=new File(sourceRegistry.sourceDir(item.source.id),"pickup_order_cache_backup");
                    ImportParser.delete(temp);ImportParser.delete(backup);
                    if(!copyImportTree(item.ready,temp))throw new Exception("לא ניתן להכין רשימה לשמירה.");
                    boolean had=root.exists();
                    if(had&&!root.renameTo(backup))throw new Exception("לא ניתן לגבות רשימה קיימת.");
                    if(!temp.renameTo(root)){if(had)backup.renameTo(root);throw new Exception("שמירת רשימה נכשלה.");}
                    committed.add(new File[]{root,backup});
                }
                for(PreparedImport item:prepared) {
                    SharedPreferences prefs=sourceRegistry.prefs(item.source.id);
                    SharedPreferences.Editor edit=prefs.edit();
                    for(String key:prefs.getAll().keySet())
                        if(key.startsWith(DataGuard.COLLECTED_PREFIX)&&!key.equals(DataGuard.COLLECTED_PREFIX+item.orderId))edit.remove(key);
                    edit.putString(DataGuard.COLLECTED_PREFIX+item.orderId,item.ticks.toString());
                    if(!edit.commit())throw new Exception("לא ניתן לשמור סימוני ליקוט.");
                    DataGuard.saveMeta(prefs,item.owner,item.orderIdLong,item.fingerprint,"");
                    if(SourceRegistry.KIND_IMPORTED.equals(item.source.kind))sourceRegistry.markImported(item.source.id);
                    archiveCurrentSnapshot(item.source.id);
                }
                for(File[] pair:committed)ImportParser.delete(pair[1]);
                ImportParser.delete(bundle.workDir);
                String last=targetIds.isEmpty()?"":targetIds.get(targetIds.size()-1);
                mainHandler.post(() -> {
                    try {
                        if(!last.isEmpty()) {
                            sourceRegistry.setActive(last);activeSourceId=last;
                            activeSourceDirectory=sourceRegistry.sourceDir(last);preferences=sourceRegistry.prefs(last);
                            accessToken=readEncryptedToken();activeOwner=preferences.getString(DataGuard.K_OWNER,"");
                            cacheTrusted=isCacheManifestValid(activeOwner);clearOrderMemory();
                        }
                        showHomeScreen(false);showSyncMessage("הייבוא הושלם בהצלחה.",false);
                    } catch(Exception e){showSyncMessage("הרשימות נשמרו, אך לא ניתן לפתוח אותן כרגע.",false);}
                });
            } catch(Exception e) {
                for(File[] pair:committed) {
                    ImportParser.delete(pair[0]);
                    if(pair[1].exists())pair[1].renameTo(pair[0]);
                }
                for(String id:created)sourceRegistry.remove(id);
                final String message=e.getMessage()==null?"שגיאה בשמירת הייבוא.":e.getMessage();
                mainHandler.post(() -> showSyncMessage("הייבוא לא הושלם: "+message,false));
                ImportParser.delete(bundle.workDir);
            }
        });
    }

    private PreparedImport prepareImportedList(ImportParser.Bundle bundle,ImportParser.ImportedList list,
                                                SourceRegistry.Source source,String owner)throws Exception {
        File ready=new File(bundle.workDir,"ready_"+source.id);ImportParser.delete(ready);
        if(!ready.mkdirs())throw new Exception("לא ניתן להכין תיקיית רשימה זמנית.");
        JSONArray savedRows=new JSONArray();List<ProductRow> rows=new ArrayList<>();
        for(int i=0;i<list.items.size();i++) {
            ImportParser.Item item=list.items.get(i);String imageFile="";
            File image=bundle.imageFile(item.image);
            if(image!=null&&image.isFile()&&image.length()>0) {
                imageFile=savedListStore.cacheImage(image);
            }
            String note=item.note;
            if(!item.unit.isEmpty())note=(note.isEmpty()?"":note+" · ")+"יחידה: "+item.unit;
            if(!item.category.isEmpty())note=(note.isEmpty()?"":note+" · ")+item.category;
            ProductRow row=new ProductRow(item.id,item.name,item.imageUrl,item.quantity,note,"",item.category);
            rows.add(row);
            JSONObject saved=new JSONObject().put("key",item.id).put("name",item.name)
                    .put("image_url",item.imageUrl).put("amount",item.quantity)
                    .put("note",note).put("category",item.category==null?"":item.category).put("image_file",imageFile);
            savedRows.put(saved);
        }
        long orderId;
        try{orderId=Long.parseLong(list.orderId);if(orderId<=0)throw new NumberFormatException();}
        catch(Exception e){orderId=Long.parseLong(DataGuard.sha256(list.orderId).substring(0,15),16);}
        String orderKey=String.valueOf(orderId);
        String fingerprint=DataGuard.fingerprint(rows);
        JSONObject snapshot=new JSONObject().put("order_id",orderId).put("order_id_text",list.orderId)
                .put("owner",owner).put("fingerprint",fingerprint).put("fingerprint_version",2)
                .put("saved_at",System.currentTimeMillis()).put("products",savedRows)
                .put("imported",true).put("order_name",list.orderName).put("pickup_date",list.pickupDate)
                .put("updated_at",list.updatedAt);
        try(FileOutputStream out=new FileOutputStream(new File(ready,"order.json"))) {
            out.write(snapshot.toString().getBytes("UTF-8"));out.getFD().sync();
        }
        JSONArray retained=new JSONArray();
        File oldManifest=new File(new File(sourceRegistry.sourceDir(source.id),"pickup_order_cache"),"order.json");
        if(oldManifest.isFile()) {
            try {
                JSONObject old=new JSONObject(readImportText(oldManifest));
                if(old.optLong("order_id",-1)==orderId) {
                    Set<String> valid=new HashSet<>();for(ProductRow row:rows)valid.add(DataGuard.tick(row));
                    JSONArray ticks=new JSONArray(sourceRegistry.prefs(source.id).getString(DataGuard.COLLECTED_PREFIX+orderKey,"[]"));
                    for(int i=0;i<ticks.length();i++){String tick=ticks.optString(i,"");if(valid.contains(tick))retained.put(tick);}
                }
            }catch(Exception ignored){}
        }
        return new PreparedImport(source,ready,owner,orderId,fingerprint,retained);
    }

    private String readImportText(File file)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        return new String(out.toByteArray(),"UTF-8");
    }
    private boolean copyImportFile(File from,File to) {
        try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();return true;
        }catch(Exception e){return false;}
    }
    private boolean copyImportTree(File from,File to) {
        if(from.isDirectory()){if(!to.exists()&&!to.mkdirs())return false;File[] files=from.listFiles();if(files==null)return true;
            for(File f:files)if(!copyImportTree(f,new File(to,f.getName())))return false;return true;}
        return copyImportFile(from,to);
    }
    private static final class PreparedImport {
        final SourceRegistry.Source source;final File ready;final String owner,fingerprint;final long orderIdLong;final String orderId;final JSONArray ticks;
        PreparedImport(SourceRegistry.Source s,File r,String o,long id,String f,JSONArray t){source=s;ready=r;owner=o;orderIdLong=id;orderId=String.valueOf(id);fingerprint=f;ticks=t;}
    }

    /** מסך ההגדרות מרכז את כל פעולות הניהול, החשבונות וההתנתקות. */
    private void showAccountsScreen() { showSettingsScreen(); }
    private static final String WHATS_NEW =
            "• שם המכירה במרכז הכותרת, והכפתורים בצדדים.\n"
            + "• המוצרים ממוינים לפי מחלקות (קטגוריות), לפי סדר המחלקות של המכירה.\n"
            + "• מידע איסוף (מקום, שעות, הערות, מפה וחיוג) ליד כל הזמנה במסך ההזמנות הקודמות.\n"
            + "• מסך הגדרות מסודר בקבוצות, בלי כפתורים מיותרים.\n"
            + "• אפשר להגיע לחיפוש במקשים: למעלה מהפריט הראשון.\n"
            + "• בחירת החשבון הפעיל (בהגדרות ובמסך ההזמנות הקודמות), ולכל חשבון היסטוריית הזמנות משלו.\n"
            + "• כפתורי התצוגה (מאוחדת / לפי חשבון) חזרו לכותרת.";

    /** כרטיס קבוצה במסך ההגדרות: כותרת קטנה ותוכן על רקע בהיר. */
    private LinearLayout settingsCard(LinearLayout content, String title) {
        content.addView(Ui.section(this, title));
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.shape(this, Ui.SURFACE, Ui.R_CARD, 1, Ui.P200));
        card.setPadding(Ui.dp(this, Ui.S3), Ui.dp(this, Ui.S2), Ui.dp(this, Ui.S3), Ui.dp(this, Ui.S2));
        content.addView(card, Ui.row(this, 0, Ui.S1));
        return card;
    }

    /** שני כפתורים זה לצד זה, ברוחב שווה. */
    private LinearLayout buttonPair(Button right, Button left) {
        LinearLayout pair = new LinearLayout(this);
        pair.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1f);
        a.setMarginEnd(Ui.dp(this, Ui.S1));
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -2, 1f);
        b.setMarginStart(Ui.dp(this, Ui.S1));
        pair.addView(right, a);
        pair.addView(left, b);
        return pair;
    }

    private void showSettingsScreen() {
        if (sourceMergeInProgress) { showSyncMessage("המקור מתאחד עם מקור קיים. המתינו רגע.", false); return; }
        if (syncAllRunning) { showSyncMessage("הסנכרון הסדרתי עדיין פועל. המתינו לסיומו.", false); return; }
        accountsScreenVisible = true;
        logoutButton = null;
        LinearLayout content = Ui.screen(this);
        LinearLayout settingsHeader = new LinearLayout(this);
        settingsHeader.setGravity(Gravity.CENTER_VERTICAL);
        settingsHeader.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView settingsTitle = Ui.text(this, "הגדרות", Ui.T_TITLE, Ui.P900, true);
        settingsTitle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Button closeSettings = Ui.iconButton(this, Ui.IC_CLOSE, "סגירת ההגדרות", false);
        closeSettings.setOnClickListener(v -> closeSettings());
        settingsHeader.addView(closeSettings);
        settingsHeader.addView(settingsTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(settingsHeader, Ui.row(this, 0, 0));

        // 1. רשימות: הפעולות היומיומיות.
        LinearLayout lists = settingsCard(content, "רשימות");
        Button savedLists = Ui.button(this, "רשימות שמורות", Ui.Kind.SOFT);
        savedLists.setOnClickListener(v -> showSavedListsScreen(Screen.SETTINGS));
        Button history = Ui.button(this, "הזמנות קודמות", Ui.Kind.SOFT);
        history.setEnabled(!connectedAccounts().isEmpty());
        history.setOnClickListener(v -> showOrderHistoryScreen(Screen.SETTINGS));
        lists.addView(buttonPair(savedLists, history), Ui.row(this, Ui.S1, Ui.S1));
        Button sync = Ui.button(this, "סנכרון עם האתר", Ui.Kind.PRIMARY);
        // הסנכרון מתבצע מול מסך הרשימה, כדי שההתקדמות וההודעות יוצגו למשתמש.
        sync.setOnClickListener(v -> { closeSettings(); syncAllAccounts(); });
        lists.addView(sync, Ui.row(this, Ui.S1, Ui.S1));

        // 2. חשבונות: כל חשבון הוא כפתור אחד שפותח את פעולותיו (התחברות מחדש, ניתוק, הסרה).
        LinearLayout accounts = settingsCard(content, "חשבונות");
        for (SourceRegistry.Source source : sourceRegistry.all()) {
            boolean imported = SourceRegistry.KIND_IMPORTED.equals(source.kind);
            boolean connected = !imported && secretStore.get(source.id) != null;
            String status = imported ? "מיובאת" : (connected ? (source.id.equals(activeSourceId) ? "פעיל" : "מחובר") : "מנותק");
            String label = source.label == null || source.label.trim().isEmpty()
                    ? "חיבור ללא שם" : source.label;
            Button item = Ui.button(this, label + "  ·  " + status, Ui.Kind.SOFT);
            item.setOnClickListener(v -> showSourceActions(source));
            accounts.addView(item, Ui.row(this, Ui.S1, Ui.S1));
        }
        Button add = Ui.button(this, sourceRegistry.all().isEmpty() ? "התחברות לחשבון" : "הוספת חשבון",
                Ui.Kind.PRIMARY);
        add.setOnClickListener(v -> beginAddAccount());
        accounts.addView(add, Ui.row(this, Ui.S1, Ui.S1));

        // 3. תצוגה ומשוב.
        LinearLayout display = settingsCard(content, "תצוגה ומשוב");
        addDisplaySettings(display);
        addFeedbackSettings(display);

        // 4. ייבוא ונתונים.
        LinearLayout data = settingsCard(content, "ייבוא ונתונים");
        Button importFile = Ui.button(this, "ייבוא קובץ", Ui.Kind.SOFT);
        importFile.setOnClickListener(v -> openImportPicker());
        Button importFolder = Ui.button(this, "מתיקיית import", Ui.Kind.SOFT);
        importFolder.setOnClickListener(v -> showImportFolder());
        data.addView(buttonPair(importFile, importFolder), Ui.row(this, Ui.S1, Ui.S1));
        Button clear = Ui.button(this, "מחיקת כל הנתונים", Ui.Kind.STRONG);
        clear.setOnClickListener(v -> confirmClearSavedData());
        data.addView(clear, Ui.row(this, Ui.S1, Ui.S1));

        Button whatsNew = Ui.button(this, "מה חדש · גרסה " + appVersionName(), Ui.Kind.SOFT);
        whatsNew.setOnClickListener(v -> showInfoDialog("מה חדש בגרסה " + appVersionName(), WHATS_NEW));
        content.addView(whatsNew, Ui.row(this, Ui.S5, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        presentScreen(scroll, Screen.SETTINGS);
        // מקלדת: הפוקוס מתחיל בפעולה הראשונה, ולמעלה ממנה נמצא כפתור הסגירה.
        savedLists.requestFocus();
    }

    /** חלון טקסט פשוט עם כפתור סגירה. */
    private void showInfoDialog(String title, String message) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, title, Ui.T_NAME, Ui.P900, true));
        panel.addView(Ui.text(this, message, Ui.T_BODY, Ui.INK, false), Ui.row(this, Ui.S3, Ui.S3));
        Button close = Ui.button(this, "סגירה", Ui.Kind.PRIMARY);
        close.setOnClickListener(v -> dialog.dismiss());
        panel.addView(close);
        dialog.setContentView(panel);
        dialog.show();
        styleConfirmWindow(dialog);
        close.requestFocus();
    }

    private void showSourceActions(SourceRegistry.Source source) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, source.label, Ui.T_NAME, Ui.P900, true));
        if (SourceRegistry.KIND_ACCOUNT.equals(source.kind)) {
            boolean connected = secretStore.get(source.id) != null;
            if (connected && !source.id.equals(activeSourceId))
                addSourceAction(panel, dialog, "הפוך לחשבון הפעיל", () -> makeActiveAccount(source.id, this::showSettingsScreen));
            addSourceAction(panel, dialog, "התחבר מחדש / עדכן טוקן", () -> beginReconnectSource(source.id));
            if (connected) addSourceAction(panel, dialog, "התנתק (שמור את הרשימה במכשיר)",
                    () -> showModernConfirm("ניתוק הטוקן", "הטוקן יימחק מהמכשיר. הרשימה וסימוני האיסוף יישארו זמינים ללא אינטרנט.", "התנתק", () -> disconnectSource(source.id)));
        }
        addSourceAction(panel, dialog, SourceRegistry.KIND_IMPORTED.equals(source.kind)
                        ? "הסר רשימה מהמכשיר" : "הסר חיבור ורשימה מהמכשיר",
                () -> showModernConfirm("הסרת מקור", "החיבור והעותק הפעיל יוסרו מהמכשיר. רשימות שכבר נשמרו בארכיון יישארו במסך רשימות שמורות עד למחיקה ידנית.", "הסר", () -> removeSource(source.id)));
        Button cancel = Ui.button(this, "סגירה", Ui.Kind.SOFT);
        cancel.setOnClickListener(v -> dialog.dismiss());
        panel.addView(cancel, Ui.row(this, Ui.S2, 0));
        dialog.setContentView(panel);
        dialog.show();
        styleConfirmWindow(dialog);
    }

    private void addSourceAction(LinearLayout panel, Dialog dialog, String label, Runnable action) {
        Button b = Ui.button(this, label, Ui.Kind.SOFT);
        b.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
        panel.addView(b, Ui.row(this, Ui.S2, 0));
    }

    private void beginAddAccount() {
        if (!sourceRegistry.canAdd()) {
            showSyncMessage("אפשר לשמור עד 10 חיבורים או רשימות.", false);
            accountsScreenVisible=false; showHomeScreen(false); return;
        }
        beginReconnectSource("");
    }

    private void beginReconnectSource(String sourceId) {
        accountsScreenVisible = false;
        // מקש חזרה ממסך ההתחברות הזה מחזיר להגדרות ולא סוגר את האפליקציה.
        showLoginScreen(sourceId == null || sourceId.isEmpty()
                ? "התחברו לחיבור נוסף. החיבורים הקיימים והרשימות השמורות יישארו ללא שינוי."
                : "התחברו מחדש. הטוקן החדש יעודכן בחיבור הקיים לפי מזהה הלקוח; הרשימה וסימוני האיסוף יישמרו.", true);
    }

    private void disconnectSource(String id) {
        SourceRegistry.Source source = sourceRegistry.find(id);
        if (source == null || !SourceRegistry.KIND_ACCOUNT.equals(source.kind)) return;
        if (id.equals(activeSourceId)) { logout(); return; }
        secretStore.delete(id);
        sourceRegistry.markNeedsLogin(id);
        showAccountsScreen();
    }

    private void switchSourceThen(String id, Runnable action) {
        switchSource(id, action);
    }

    private void switchSource(String id) { switchSource(id, null); }

    private void switchSource(String id, Runnable afterSwitch) {
        if (id.equals(activeSourceId)) {
            accountsScreenVisible=false;
            if (afterSwitch != null) afterSwitch.run(); else showHomeScreen(false);
            return;
        }
        // Drain already queued writes before changing the active source directory/preferences.
        final int generation = ++authGeneration;
        loading = false;
        cacheWorker.execute(() -> mainHandler.post(() -> {
            try {
                SourceRegistry.Source next = sourceRegistry.find(id);
                if (next == null || isFinishing() || generation != authGeneration) return;
                activeSourceId = id;
                sourceRegistry.setActive(id);
                activeSourceDirectory = sourceRegistry.sourceDir(id);
                preferences = sourceRegistry.prefs(id);
                accessToken = readEncryptedToken();
                activeOwner = preferences.getString(DataGuard.K_OWNER, next.ownerKey);
                cacheTrusted = isCacheManifestValid(activeOwner);
                clearOrderMemory();
                initializeCacheOwnership();
                accountsScreenVisible=false;
                if (afterSwitch != null) afterSwitch.run();
                else showHomeScreen(!accessToken.isEmpty());
                if (syncAllRunning && accessToken.isEmpty()) advanceSyncQueue();
            } catch (Exception e) {
                showAccountsScreen();
            }
        }));
    }

    private void renameSource(SourceRegistry.Source source) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(source.label);
        input.setSelectAllOnFocus(true);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, "שינוי שם החשבון", Ui.T_NAME, Ui.P900, true));
        panel.addView(input, Ui.row(this, Ui.S2, Ui.S3));
        Button save = Ui.button(this, "שמירה", Ui.Kind.PRIMARY);
        Button cancel = Ui.button(this, "ביטול", Ui.Kind.SOFT);
        save.setOnClickListener(v -> { sourceRegistry.rename(source.id, input.getText().toString()); dialog.dismiss(); showAccountsScreen(); });
        cancel.setOnClickListener(v -> dialog.dismiss());
        panel.addView(save); panel.addView(cancel, Ui.row(this, Ui.S2, 0));
        dialog.setContentView(panel); dialog.show(); styleConfirmWindow(dialog);
        input.requestFocus();
    }

    private void removeSource(String id) {
        final boolean removingActive = id.equals(activeSourceId);
        final int removeGeneration = ++authGeneration;
        loading = false;
        cacheWorker.execute(() -> {
            archiveCurrentSnapshot(id);
            boolean removed = sourceRegistry.remove(id);
            mainHandler.post(() -> {
                if (!removed) { showSyncMessage("לא ניתן היה לשמור את הסרת המקור. הנתונים נשארו במכשיר.", false); return; }
                if (isFinishing()) return;
                if (removingActive) {
                    SourceRegistry.Source next=sourceRegistry.active();
                    if (next == null) {
                        activeSourceId=""; activeSourceDirectory=super.getFilesDir();
                        preferences=getSharedPreferences(PREFS,MODE_PRIVATE);
                        accessToken=""; activeOwner=""; cacheTrusted=false; clearOrderMemory();
                        showLoginScreen("המקור הוסר מהמכשיר.");
                    } else switchSource(next.id);
                } else showAccountsScreen();
            });
        });
    }

    private ViewGroup createProductContainer() {
        if (displayMode == 5) {
            GridLayout grid = new GridLayout(this); grid.setColumnCount(3);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS); grid.setUseDefaultMargins(false);
            // Physical LTR columns are mapped explicitly to RTL card order.
            grid.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); return grid;
        }
        return Ui.column(this);
    }
    private ViewGroup.LayoutParams productLayoutParams(ViewGroup parent, int index) {
        if (parent instanceof GridLayout) {
            // Explicit physical columns: first card right, second middle, third left.
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(
                    GridLayout.spec(index / 3), GridLayout.spec(2 - (index % 3), 1f));
            lp.width=0; lp.height=Ui.dp(this,176);
            lp.setMargins(Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,2));return lp;
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=Ui.dp(this,displayMode==0?Ui.S1:Ui.S3);return lp;
    }
    private boolean matchesSearch(ProductRow row) {
        if(row==null||searchQuery==null||searchQuery.trim().isEmpty())return true;
        String q=searchQuery.trim().toLowerCase(Locale.ROOT);
        return row.name.toLowerCase(Locale.ROOT).contains(q)||row.note.toLowerCase(Locale.ROOT).contains(q)
                ||row.key.toLowerCase(Locale.ROOT).contains(q)||row.amount.toLowerCase(Locale.ROOT).contains(q);
    }
    private LinearLayout buildSearchRow() {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        EditText input=new EditText(this);searchInput=input;input.setId(View.generateViewId());input.setSingleLine(true);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,Ui.T_BODY);input.setTextColor(Ui.INK);input.setHintTextColor(Ui.MUTED);
        input.setHint("חיפוש פריט ברשימה…");input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (Build.VERSION.SDK_INT >= 24)
            input.setImeHintLocales(new android.os.LocaleList(new Locale("he")));
        input.setPadding(Ui.dp(this,Ui.S3),Ui.dp(this,Ui.S2),Ui.dp(this,Ui.S3),Ui.dp(this,Ui.S2));
        input.setBackground(Ui.inputBackground(this));input.setText(searchQuery);
        input.setSelection(input.getText().length());
        Drawable searchIcon=new Ui.IconDrawable(this,Ui.IC_SEARCH,Ui.P600,20,1f);
        searchIcon.setBounds(0,0,Ui.dp(this,20),Ui.dp(this,20));
        input.setCompoundDrawablesRelative(searchIcon,null,null,null);
        input.setCompoundDrawablePadding(Ui.dp(this,Ui.S2));
        input.setContentDescription("חיפוש פריט ברשימה (מקש 3)");
        // אישור החיפוש במקלדת סוגר אותה ומעביר את הבחירה לתוצאה הראשונה.
        input.setOnEditorActionListener((v,actionId,event)->{
            if(actionId==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH||isEnterKey(event)){
                hideKeyboard(v);v.clearFocus();focusFirstPending();return true;}
            return false;
        });
        row.addView(input,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f));
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){searchQuery=s==null?"":s.toString();renderProducts(false);}
            @Override public void afterTextChanged(Editable e){}
        });return row;
    }
    private void addDisplaySettings(LinearLayout content) {
        content.addView(Ui.text(this,"גודל התצוגה",Ui.T_NOTE,Ui.P600,true),Ui.row(this,Ui.S1,0));
        final String[] labels={"צ׳ק ליסט צפוף","70%","85%","100%","115%","רשת · 3 כרטיסים בשורה"};
        final TextView current=Ui.text(this,labels[displayMode],Ui.T_BODY,Ui.P800,true);current.setGravity(Gravity.CENTER);
        SeekBar slider=new SeekBar(this);slider.setMax(5);slider.setProgress(displayMode);
        displaySizeSlider = slider;
        // מסגרת פוקוס גלויה כשהמחוון נבחר במקלדת.
        slider.setBackground(Ui.focusFrame(this));
        slider.setPadding(Ui.dp(this,Ui.S5),Ui.dp(this,Ui.S3),Ui.dp(this,Ui.S5),Ui.dp(this,Ui.S3));
        slider.setContentDescription("גודל התצוגה");
        if(Build.VERSION.SDK_INT>=21){slider.setProgressTintList(Ui.tint(Ui.P600));slider.setThumbTintList(Ui.tint(Ui.P700));slider.setProgressBackgroundTintList(Ui.tint(Ui.P200));}
        // Persist only the selected setting while the slider is moving. Rebuilding the home
        // screen here used to destroy the settings view, steal focus and jump to the top.
        Runnable apply=()->setDisplayModeFromSlider(slider.getProgress());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){current.setText(labels[progress]);}
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){apply.run();Toast.makeText(MainActivity.this, "גודל התצוגה: "+labels[displayMode], Toast.LENGTH_SHORT).show();}
        });
        slider.setOnKeyListener((v,keyCode,event)->{
            if(event.getAction()==KeyEvent.ACTION_DOWN&&(keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT
                    ||keyCode==KeyEvent.KEYCODE_DPAD_UP||keyCode==KeyEvent.KEYCODE_DPAD_DOWN))slider.post(apply);
            return false;
        });
        content.addView(current,Ui.row(this,Ui.S1,Ui.S1));content.addView(slider,Ui.row(this,0,Ui.S2));
        LinearLayout ends=new LinearLayout(this);ends.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ends.addView(Ui.text(this,"קטן",Ui.T_NOTE,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1f));
        TextView large=Ui.text(this,"גדול",Ui.T_NOTE,Ui.MUTED,false);large.setGravity(Gravity.RIGHT);
        ends.addView(large,new LinearLayout.LayoutParams(0,-2,1f));content.addView(ends);
    }
    private void addFeedbackSettings(LinearLayout content) {
        content.addView(Ui.text(this, "צליל ורטט", Ui.T_NOTE, Ui.P600, true), Ui.row(this, Ui.S3, 0));
        boolean legacySound = uiPreferences.getBoolean("feedback_sound", false);
        boolean legacyVibration = uiPreferences.getBoolean("feedback_vibration", false);
        addFeedbackCheckbox(content, "feedback_item_vibration", "רטט באיסוף פריט",
                legacyVibration, Ui.S1, 0);
        addFeedbackCheckbox(content, "feedback_item_sound", "צליל באיסוף פריט",
                legacySound, 0, 0);
        addFeedbackCheckbox(content, "feedback_completion_vibration", "רטט בסיום הרשימה",
                true, 0, 0);
        addFeedbackCheckbox(content, "feedback_completion_sound", "מנגינה בסיום הרשימה",
                legacySound, 0, Ui.S1);
    }

    private void addFeedbackCheckbox(LinearLayout content, String key, String label,
                                     boolean legacyDefault, int top, int bottom) {
        CheckBox option = new CheckBox(this);
        option.setText(label);
        option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Ui.T_BODY);
        option.setTextColor(Ui.INK);
        option.setChecked(uiPreferences.getBoolean(key, legacyDefault));
        if (Build.VERSION.SDK_INT >= 21) option.setButtonTintList(Ui.tint(Ui.P600));
        // מסגרת פוקוס גלויה ומרווח לחיצה נוח, כמו בשאר רכיבי המסך.
        option.setBackground(Ui.focusFrame(this));
        option.setPadding(Ui.dp(this, Ui.S2), Ui.dp(this, Ui.S2), Ui.dp(this, Ui.S2), Ui.dp(this, Ui.S2));
        option.setMinHeight(Ui.dp(this, 48));
        option.setFocusable(true);
        option.setOnCheckedChangeListener((button, checked) -> {
            uiPreferences.edit().putBoolean(key, checked).apply();
            Toast.makeText(this, label + (checked ? " הופעל" : " כובה"), Toast.LENGTH_SHORT).show();
        });
        content.addView(option, Ui.row(this, top, bottom));
    }

    private void performFeedback(boolean completed, boolean added) {
        if (uiPreferences == null) return;
        String vibrationKey = completed ? "feedback_completion_vibration" : "feedback_item_vibration";
        String soundKey = completed ? "feedback_completion_sound" : "feedback_item_sound";
        boolean legacyVibration = uiPreferences.getBoolean("feedback_vibration", false);
        boolean legacySound = uiPreferences.getBoolean("feedback_sound", false);
        boolean vibrationEnabled = uiPreferences.getBoolean(vibrationKey, completed || legacyVibration);

        if (vibrationEnabled) {
            try {
                Vibrator vibrator = null;
                if (Build.VERSION.SDK_INT >= 31) {
                    android.os.VibratorManager manager =
                            (android.os.VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                    if (manager != null) vibrator = manager.getDefaultVibrator();
                }
                if (vibrator == null)
                    vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (completed) {
                        // One continuous pulse is more reliable than a multi-segment waveform.
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                                    1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(1000);
                        }
                    } else if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                                125, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(125);
                    }
                } else {
                    getWindow().getDecorView().performHapticFeedback(
                            HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                }
            } catch (Exception error) {
                android.util.Log.e("MishnatPickup", "Vibration feedback failed", error);
                getWindow().getDecorView().performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
        }

        if (!uiPreferences.getBoolean(soundKey, legacySound)) return;
        if (completed) {
            try {
                playVictorySound();
            } catch (Exception error) {
                android.util.Log.e("MishnatPickup", "Victory sound failed", error);
                playTone(ToneGenerator.TONE_PROP_ACK, 700);
            }
        } else {
            playTone(added ? ToneGenerator.TONE_PROP_BEEP2 : ToneGenerator.TONE_PROP_NACK,
                    added ? 180 : 230);
        }
    }

    private void playVictorySound() throws Exception {
        final MediaPlayer player = new MediaPlayer();
        AssetFileDescriptor sound = null;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
            } else {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            sound = getResources().openRawResourceFd(R.raw.victory_fanfare);
            if (sound == null) throw new IllegalStateException("Victory sound resource unavailable");
            player.setDataSource(sound.getFileDescriptor(), sound.getStartOffset(), sound.getLength());
            sound.close();
            sound = null;
            player.setOnPreparedListener(mp -> {
                mp.setVolume(1f, 1f);
                mp.start();
            });
            player.setOnCompletionListener(MediaPlayer::release);
            player.setOnErrorListener((mp, what, extra) -> {
                android.util.Log.e("MishnatPickup", "Victory player error: " + what + "/" + extra);
                mp.release();
                return true;
            });
            player.prepareAsync();
        } catch (Exception error) {
            if (sound != null) try { sound.close(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            throw error;
        }
    }

    private void playTone(int toneType, int duration) {
        ToneGenerator tone = null;
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            if (!tone.startTone(toneType, duration)) {
                tone.release();
                tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                tone.startTone(toneType, duration);
            }
            final ToneGenerator active = tone;
            mainHandler.postDelayed(active::release, duration + 180L);
        } catch (Exception error) {
            if (tone != null) try { tone.release(); } catch (Exception ignored) { }
            android.util.Log.e("MishnatPickup", "Tone feedback failed", error);
        }
    }

    private double remainingQuantity(ProductRow product) {
        if(product==null||collected.contains(product.key))return 0;
        return Math.max(0,parseQuantity(product.amount)-collectedQuantity(product));
    }

    private void showPartialCollectionDialog(String key) {
        ProductRow product=findProduct(key);if(product==null)return;
        final int remainingUnits=(int)Math.floor(remainingQuantity(product)+0.000001);
        if(remainingUnits<=0)return;
        if(remainingUnits==1){applyPartialCollection(key,1);return;}
        final TextView selected=Ui.text(this,"",Ui.T_TITLE,Ui.P800,true);
        selected.setGravity(Gravity.CENTER);
        SeekBar slider=new SeekBar(this);slider.setMax(remainingUnits-1);
        if(Build.VERSION.SDK_INT>=21){slider.setProgressTintList(Ui.tint(Ui.P600));slider.setThumbTintList(Ui.tint(Ui.P700));slider.setProgressBackgroundTintList(Ui.tint(Ui.P200));}
        slider.setProgress(0);selected.setText("1 מתוך "+remainingUnits);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){selected.setText((progress+1)+" מתוך "+remainingUnits);}
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=Ui.dialogPanel(this);
        panel.addView(Ui.text(this,"איסוף כמות חלקית",Ui.T_NAME,Ui.P900,true));
        panel.addView(Ui.text(this,product.name,Ui.T_BODY,Ui.INK,true),Ui.row(this,Ui.S2,Ui.S1));
        panel.addView(Ui.text(this,"בחרו כמה יחידות נוספות לאסוף (נותרו "+remainingUnits+")",Ui.T_NOTE,Ui.MUTED,false),Ui.row(this,0,Ui.S2));
        panel.addView(selected,Ui.row(this,Ui.S1,Ui.S1));panel.addView(slider,Ui.row(this,0,Ui.S4));
        Button save=Ui.button(this,"אישור הכמות",Ui.Kind.PRIMARY),cancel=Ui.button(this,"ביטול",Ui.Kind.SOFT);
        save.setOnClickListener(v->{dialog.dismiss();applyPartialCollection(key,slider.getProgress()+1);});
        slider.setOnKeyListener((v,keyCode,event)->{
            if(event.getAction()==KeyEvent.ACTION_DOWN&&(keyCode==KeyEvent.KEYCODE_ENTER||keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER||keyCode==KeyEvent.KEYCODE_DPAD_CENTER)){save.performClick();return true;}
            return false;
        });
        cancel.setOnClickListener(v->dialog.dismiss());panel.addView(save);panel.addView(cancel,Ui.row(this,Ui.S2,0));
        dialog.setContentView(panel);dialog.show();styleConfirmWindow(dialog);
        slider.setFocusable(true);slider.setFocusableInTouchMode(true);slider.requestFocus();
    }

    private void applyPartialCollection(String key,double quantity) {
        ProductRow product=findProduct(key);if(product==null)return;double total=parseQuantity(product.amount);
        ProductCard partialCardBefore=partialCards.get(key);
        boolean hadPartialFocus=partialCardBefore!=null&&partialCardBefore.row.hasFocus();
        double alreadyCollected=collected.contains(key)?total:Math.min(total,collectedQuantity(product));
        // Zero keeps the existing undo behavior; positive values add units in this collection action.
        double updated=quantity<=0?0:alreadyCollected+Math.min(Math.max(0,quantity),Math.max(0,total-alreadyCollected));
        if(combinedView)updateCombinedPartial(key,updated);
        else{collected.remove(key);if(updated>=total-0.000001){collected.add(key);partialCollected.remove(key);}
            else if(updated>0)partialCollected.put(key,updated);else partialCollected.remove(key);saveCollectedState();}
        renderProducts(false);
        if(hadPartialFocus){ProductCard mainCard=productCards.get(key);if(mainCard!=null)mainCard.row.requestFocus();}
        performFeedback(false, quantity > 0);checkCelebration();
    }
    private void showSavedListsScreen() { showSavedListsScreen(savedListsReturn); }
    private void showSavedListsScreen(boolean fromLogin) { showSavedListsScreen(fromLogin ? Screen.LOGIN : Screen.HOME); }
    /** רענון מסך הרשימות השמורות בלי לשנות את המסך שאליו חוזרים ממנו. */
    private void refreshSavedListsScreen() { showSavedListsScreen(savedListsReturn); }

    /** כותרת אחידה למסכי משנה: כפתור חזרה (חץ ימינה) ושם המסך. */
    private LinearLayout buildSubScreenHeader(String titleText, Runnable onBack) {
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Button back=Ui.iconButton(this,Ui.IC_BACK,"חזרה",false);
        back.setTag("screen_back_button");
        back.setOnClickListener(v->onBack.run());header.addView(back);
        TextView title=Ui.text(this,titleText,Ui.T_TITLE,Ui.P900,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1f);titleLp.setMarginStart(Ui.dp(this,Ui.S3));
        header.addView(title,titleLp);
        return header;
    }

    private void showSavedListsScreen(Screen returnTo) {
        savedListsScreenVisible=true;
        savedListsReturn=returnTo==null?Screen.HOME:returnTo;
        savedListsReturnToLogin=savedListsReturn==Screen.LOGIN;
        LinearLayout content=Ui.screen(this);content.setGravity(Gravity.TOP);
        content.addView(buildSubScreenHeader("רשימות שמורות",this::leaveSavedListsScreen),Ui.row(this,0,Ui.S2));
        Button history=Ui.button(this,"הזמנות קודמות מהאתר",Ui.Kind.PRIMARY);
        history.setEnabled(!connectedAccounts().isEmpty());
        history.setOnClickListener(v->showOrderHistoryScreen(Screen.SAVED_LISTS));
        content.addView(history,Ui.row(this,0,Ui.S2));
        Button create=Ui.button(this,"יצירת רשימה חדשה",Ui.Kind.SOFT);
        create.setOnClickListener(v->createNewSavedList());
        content.addView(create,Ui.row(this,0,Ui.S2));
        List<SavedListStore.Entry> entries=savedListStore.list();Collections.sort(entries,(a,b)->Long.compare(b.archivedAt,a.archivedAt));
        if(entries.isEmpty())content.addView(Ui.banner(this,"עדיין לא נשמרו רשימות במכשיר."));
        else content.addView(Ui.section(this,entries.size()+" רשימות במכשיר"));
        Button firstOpen=null;
        for(SavedListStore.Entry entry:entries){LinearLayout card=Ui.column(this);
            card.setBackground(Ui.shape(this,Ui.SURFACE,Ui.R_CARD,1,Ui.P200));card.setPadding(Ui.dp(this,Ui.S3),Ui.dp(this,Ui.S3),Ui.dp(this,Ui.S3),Ui.dp(this,Ui.S3));
            card.addView(Ui.text(this,entry.title,Ui.T_NAME,Ui.P900,true));
            card.addView(Ui.text(this,entry.sourceLabel+" · "+entry.itemCount+" פריטים"+(entry.updatedAt.isEmpty()?"":" · "+entry.updatedAt),Ui.T_NOTE,Ui.MUTED,false),Ui.row(this,Ui.S1,Ui.S2));
            // שני כפתורים בשורה במקום ארבעה כפתורים זה מתחת לזה: פחות גלילה ופחות לחיצות מקלדת.
            LinearLayout actions=new LinearLayout(this);actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            Button open=Ui.button(this,"פתיחה",Ui.Kind.PRIMARY);open.setOnClickListener(v->openSavedList(entry));
            Button more=Ui.button(this,"פעולות נוספות",Ui.Kind.SOFT);more.setOnClickListener(v->showSavedListActions(entry));
            LinearLayout.LayoutParams openLp=new LinearLayout.LayoutParams(0,-2,1f);openLp.setMarginEnd(Ui.dp(this,Ui.S1));
            LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(0,-2,1f);moreLp.setMarginStart(Ui.dp(this,Ui.S1));
            actions.addView(open,openLp);actions.addView(more,moreLp);
            card.addView(actions,Ui.row(this,0,0));
            content.addView(card,Ui.row(this,0,Ui.S2));
            if(firstOpen==null)firstOpen=open;
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content,new ScrollView.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT));
        presentScreen(scroll,Screen.SAVED_LISTS);
        // מקלדת: הפוקוס מתחיל ברשימה הראשונה, ואם אין רשימות - בכפתור יצירת רשימה.
        if(firstOpen!=null)firstOpen.requestFocus();else if(history.isEnabled())history.requestFocus();else create.requestFocus();
    }

    private void showSavedListActions(SavedListStore.Entry entry) {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=Ui.dialogPanel(this);
        panel.addView(Ui.text(this,entry.title,Ui.T_NAME,Ui.P900,true));
        panel.addView(Ui.text(this,entry.itemCount+" פריטים",Ui.T_NOTE,Ui.MUTED,false),Ui.row(this,Ui.S1,Ui.S2));
        addSourceAction(panel,dialog,"מיזוג לתוך רשימה אחרת",()->showMergeTargetDialog(entry));
        addSourceAction(panel,dialog,"ייצוא לקובץ TXT",()->exportSavedList(entry));
        addSourceAction(panel,dialog,"מחיקה מהרשימות השמורות",()->showModernConfirm("מחיקת רשימה שמורה","הרשימה והעותק המקומי שלה יימחקו מהמכשיר בלבד. נתוני החשבון המקוון לא יושפעו.","מחק רשימה",()->deleteSavedListEntry(entry)));
        Button close=Ui.button(this,"סגירה",Ui.Kind.SOFT);close.setOnClickListener(v->dialog.dismiss());
        panel.addView(close,Ui.row(this,Ui.S3,0));
        dialog.setContentView(panel);dialog.show();styleConfirmWindow(dialog);
    }

    private void showOrderHistoryScreen() { showOrderHistoryScreen(Screen.SAVED_LISTS); }

    /** כל החשבונות שיש להם התחברות פעילה (טוקן שמור). */
    private List<SourceRegistry.Source> connectedAccounts() {
        List<SourceRegistry.Source> result = new ArrayList<>();
        if (sourceRegistry == null) return result;
        for (SourceRegistry.Source source : sourceRegistry.all()) {
            if (!SourceRegistry.KIND_ACCOUNT.equals(source.kind)) continue;
            String token = secretStore.get(source.id);
            if (token != null && !token.isEmpty()) result.add(source);
        }
        return result;
    }

    private String accountLabel(SourceRegistry.Source source) {
        return source == null || source.label == null || source.label.trim().isEmpty()
                ? "חשבון ללא שם" : source.label.trim();
    }

    /** הופך חשבון לחשבון הפעיל: ההזמנה העדכנית, ההיסטוריה והשמירה מתבצעות מולו. */
    private void makeActiveAccount(String sourceId, Runnable after) {
        SourceRegistry.Source source = sourceRegistry.find(sourceId);
        if (source == null) return;
        switchSource(sourceId, () -> {
            showActionToast("החשבון הפעיל: " + accountLabel(source));
            if (after != null) after.run(); else showHomeScreen(false);
        });
    }

    /** בחירת החשבון הפעיל מתוך החשבונות המחוברים. */
    private void showAccountPicker(Runnable after) {
        List<SourceRegistry.Source> accounts = connectedAccounts();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.dialogPanel(this);
        panel.addView(Ui.text(this, "בחירת חשבון", Ui.T_NAME, Ui.P900, true));
        Button focus = null;
        for (SourceRegistry.Source account : accounts) {
            boolean active = account.id.equals(activeSourceId);
            Button choose = Ui.button(this, accountLabel(account) + (active ? "  ·  פעיל" : ""),
                    active ? Ui.Kind.PRIMARY : Ui.Kind.SOFT);
            choose.setOnClickListener(v -> {
                dialog.dismiss();
                if (account.id.equals(activeSourceId)) return;
                makeActiveAccount(account.id, after);
            });
            panel.addView(choose, Ui.row(this, Ui.S2, 0));
            if (active) focus = choose;
        }
        if (accounts.isEmpty())
            panel.addView(Ui.text(this, "אין חשבונות מחוברים.", Ui.T_BODY, Ui.MUTED, false), Ui.row(this, Ui.S2, 0));
        Button cancel = Ui.button(this, "ביטול", Ui.Kind.SOFT);
        cancel.setOnClickListener(v -> dialog.dismiss());
        panel.addView(cancel, Ui.row(this, Ui.S3, 0));
        dialog.setContentView(panel);
        dialog.show();
        styleConfirmWindow(dialog);
        if (focus != null) focus.requestFocus();
    }

    private void showOrderHistoryScreen(Screen returnTo) {
        if(accessToken.isEmpty()){
            // החשבון הפעיל מנותק: עוברים לחשבון מחובר אחר במקום להיתקע.
            String other=firstConnectedAccountId();
            if(!other.isEmpty()&&!other.equals(activeSourceId)){makeActiveAccount(other,()->showOrderHistoryScreen(returnTo));return;}
            Toast.makeText(this,"יש להתחבר כדי לטעון הזמנות קודמות.",Toast.LENGTH_LONG).show();return;}
        historyReturn=returnTo==Screen.SETTINGS?Screen.SETTINGS:Screen.SAVED_LISTS;
        savedListsScreenVisible=false;accountsScreenVisible=false;
        historyPage=0;historyLastPage=1;historyOrders.clear();historyLoading=false;
        LinearLayout content=Ui.screen(this);
        content.addView(buildSubScreenHeader("הזמנות קודמות",this::leaveHistoryScreen),Ui.row(this,0,Ui.S2));
        // לכל חשבון היסטוריה משלו. השורה מציגה איזה חשבון מוצג, ומאפשרת להחליף.
        LinearLayout intro=Ui.column(this);
        SourceRegistry.Source current=sourceRegistry.find(activeSourceId);
        LinearLayout accountRow=new LinearLayout(this);accountRow.setGravity(Gravity.CENTER_VERTICAL);accountRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        accountRow.setBackground(Ui.shape(this,Ui.P100,Ui.R_BUTTON,1,Ui.P300));
        accountRow.setPadding(Ui.dp(this,Ui.S3),Ui.dp(this,Ui.S1),Ui.dp(this,Ui.S1),Ui.dp(this,Ui.S1));
        accountRow.addView(Ui.text(this,"חשבון: "+accountLabel(current),Ui.T_BODY,Ui.P800,true),new LinearLayout.LayoutParams(0,-2,1f));
        if(connectedAccounts().size()>1){
            Button change=Ui.button(this,"החלפה",Ui.Kind.PRIMARY);
            change.setContentDescription("החלפת החשבון המוצג");
            change.setOnClickListener(v->showAccountPicker(()->showOrderHistoryScreen(historyReturn)));
            LinearLayout.LayoutParams changeLp=new LinearLayout.LayoutParams(-2,-2);
            accountRow.addView(change,changeLp);
        }
        intro.addView(accountRow,Ui.row(this,0,Ui.S2));
        intro.addView(Ui.text(this,"בחר הזמנה כדי לשמור אותה כרשימה עצמאית של החשבון הזה. ההזמנה הפעילה לא תוחלף.",Ui.T_NOTE,Ui.MUTED,false));
        content.addView(intro,Ui.row(this,0,Ui.S2));
        historyContent=content;historyMoreButton=null;
        content.addView(Ui.banner(this,"טוען הזמנות קודמות…"));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        presentScreen(scroll,Screen.HISTORY);
        View back=content.findViewWithTag("screen_back_button");if(back!=null)back.requestFocus();
        loadOrderHistoryPage(1);
    }
    private void loadOrderHistoryPage(int requestedPage) {
        if(historyLoading||accessToken.isEmpty())return;
        historyLoading=true;final RequestSession session=new RequestSession(authGeneration,accessToken);
        if(historyMoreButton!=null){historyMoreButton.setEnabled(false);historyMoreButton.setText("טוען…");}
        network.execute(()->{
            try {
                HttpResult response=authorizedRequest(session,"GET","/api/orders?page="+requestedPage,null);ensureSuccess(response);
                OrderHistoryPage parsed=OrderHistoryPage.parse(response.body,requestedPage);
                List<JSONObject> rows=new ArrayList<>();
                for(int i=0;i<parsed.orders.length();i++){JSONObject row=parsed.orders.optJSONObject(i);if(row!=null&&!OrderHistoryPage.isCancelled(row))rows.add(row);}
                mainHandler.post(()->{
                    historyLoading=false;
                    if(!session.isCurrent(this)||screen!=Screen.HISTORY)return;
                    Set<String> seen=new HashSet<>();for(JSONObject old:historyOrders)seen.add(String.valueOf(extractOrderId(old)));
                    for(JSONObject row:rows)if(seen.add(String.valueOf(extractOrderId(row))))historyOrders.add(row);
                    historyPage=parsed.currentPage;historyLastPage=parsed.lastPage;
                    boolean firstPage=requestedPage<=1;
                    renderOrderHistory();
                    if(firstPage)focusFirstHistoryItem();
                });
            } catch(Exception e) {
                String error=safeError(e);
                mainHandler.post(()->{
                    historyLoading=false;
                    if(screen!=Screen.HISTORY||historyContent==null)return;
                    while(historyContent.getChildCount()>2)historyContent.removeViewAt(2);
                    historyContent.addView(Ui.banner(this,"טעינת ההיסטוריה נכשלה: "+error),Ui.row(this,0,Ui.S2));
                    // תמיד יש דרך לנסות שוב, גם כשהעמוד הראשון נכשל.
                    Button retry=Ui.button(this,"לנסות שוב",Ui.Kind.PRIMARY);
                    retry.setOnClickListener(v->{
                        while(historyContent.getChildCount()>2)historyContent.removeViewAt(2);
                        if(historyOrders.isEmpty()){historyContent.addView(Ui.banner(this,"טוען הזמנות קודמות…"));loadOrderHistoryPage(1);}
                        else{renderOrderHistory();loadOrderHistoryPage(historyPage+1);}
                    });
                    historyContent.addView(retry,Ui.row(this,0,Ui.S2));
                    retry.requestFocus();
                });
            }
        });
    }
    private void focusFirstHistoryItem() {
        if(historyContent==null)return;
        for(int i=2;i<historyContent.getChildCount();i++){View child=historyContent.getChildAt(i);
            if((child instanceof Button||child instanceof LinearLayout)&&child.requestFocus())return;}
    }
    private void renderOrderHistory() {
        if(historyContent==null)return;
        View focused=getCurrentFocus();boolean moreHadFocus=focused!=null&&focused==historyMoreButton;
        while(historyContent.getChildCount()>2)historyContent.removeViewAt(2);
        if(historyOrders.isEmpty())historyContent.addView(Ui.banner(this,"לא נמצאו הזמנות קודמות פעילות."));
        for(JSONObject row:historyOrders) {
            long id=extractOrderId(row);if(id<=0)continue;String title=OrderHistoryPage.title(row);
            // בהיסטוריה מוצג שם המכירה בלבד, ולידו כפתור מידע איסוף להזמנה.
            Button item=Ui.button(this,title,Ui.Kind.SOFT);
            item.setContentDescription("שמירת ההזמנה "+title+" כרשימה");
            item.setOnClickListener(v->saveHistoricalOrder(id,title,row));
            Button info=Ui.iconButton(this,Ui.IC_INFO,"מידע איסוף",false);
            info.setOnClickListener(v->{
                JSONObject pickup=PickupInfo.extract(row,null);
                List<JSONObject> list=new ArrayList<>();if(pickup!=null)list.add(pickup);
                showPickupInfo(list);
            });
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            line.addView(item,new LinearLayout.LayoutParams(0,-2,1f));
            int infoSize=Ui.dp(this,Ui.iconButtonDp(this));
            LinearLayout.LayoutParams infoLp=new LinearLayout.LayoutParams(infoSize,infoSize);infoLp.setMarginStart(Ui.dp(this,Ui.S2));
            line.addView(info,infoLp);
            historyContent.addView(line,Ui.row(this,0,Ui.S2));
        }
        if(historyPage<historyLastPage){historyMoreButton=Ui.button(this,"טען הזמנות ישנות יותר",Ui.Kind.PRIMARY);historyMoreButton.setOnClickListener(v->loadOrderHistoryPage(historyPage+1));historyContent.addView(historyMoreButton,Ui.row(this,0,Ui.S3));}else historyMoreButton=null;
        // אחרי "טען עוד" הפוקוס נשאר במקום הגיוני: על כפתור טען עוד החדש או על הפריט האחרון.
        if(moreHadFocus){
            if(historyMoreButton!=null)historyMoreButton.requestFocus();
            else if(historyContent.getChildCount()>2)historyContent.getChildAt(historyContent.getChildCount()-1).requestFocus();
        }
    }
    private void saveHistoricalOrder(long id,String title,JSONObject historyRow) {
        if(accessToken.isEmpty()||activeSourceId.isEmpty()){Toast.makeText(this,"אין חיבור פעיל לשמירת ההזמנה.",Toast.LENGTH_LONG).show();return;}
        final RequestSession session=new RequestSession(authGeneration,accessToken);
        Toast.makeText(this,"טוען את פרטי ההזמנה…",Toast.LENGTH_SHORT).show();
        network.execute(()->{
            try {
                JSONObject order=unwrapOrder(apiGetObject("/api/orders/"+id,session));List<ProductRow> rows=parseProducts(order);
                if(rows.isEmpty())throw new Exception("בהזמנה שנבחרה לא נמצאו פריטים.");
                String owner=DataGuard.ownerFromOrder(order);if(owner.isEmpty())owner=activeOwner;if(owner.isEmpty())throw new Exception("לא ניתן לאמת את בעלות ההזמנה.");
                JSONArray items=new JSONArray();
                JSONObject pickupForSort=PickupInfo.extract(historyRow,order);sortByCategory(rows,pickupForSort);
                for(ProductRow p:rows)items.put(new JSONObject().put("key",p.key).put("name",p.name).put("image_url",p.imageUrl).put("amount",p.amount).put("note",p.note).put("category",p.category).put("category_order",p.categoryOrder).put("image_file",""));
                JSONObject snapshot=new JSONObject().put("order_id",id).put("owner",owner).put("order_name",title).put("updated_at",first(order,"created_at","updated_at","date")).put("imported",true).put("products",items);
                JSONObject pickup=PickupInfo.extract(historyRow,order);if(pickup!=null)snapshot.put("pickup_info",pickup);
                SourceRegistry.Source source=sourceRegistry.find(activeSourceId);if(source==null)throw new Exception("החיבור הפעיל אינו קיים.");
                if(!savedListStore.archiveSnapshot(source.id,source.label,snapshot,sourceRegistry.prefs(source.id)))throw new Exception("שמירת ההזמנה נכשלה.");
                if(!session.isCurrent(this))return;
                mainHandler.post(()->{if(screen==Screen.HISTORY)leaveHistoryScreen();Toast.makeText(this,"ההזמנה נשמרה כרשימה עצמאית במסך רשימות שמורות.",Toast.LENGTH_LONG).show();});
            } catch(Exception e) {String error=safeError(e);mainHandler.post(()->Toast.makeText(this,"שמירת ההזמנה נכשלה: "+error,Toast.LENGTH_LONG).show());}
        });
    }
    private void createNewSavedList() {
        if(activeSourceId.isEmpty()||activeOwner.isEmpty()){Toast.makeText(this,"כדי ליצור רשימה עצמאית צריך להתחבר לחשבון.",Toast.LENGTH_LONG).show();return;}
        SourceRegistry.Source source=sourceRegistry.find(activeSourceId);if(source==null)return;
        try {
            long id=System.currentTimeMillis();
            JSONObject snapshot=new JSONObject().put("order_id",id).put("owner",activeOwner).put("order_name","רשימה חדשה").put("imported",true).put("products",new JSONArray());
            if(!savedListStore.archiveSnapshot(source.id,source.label,snapshot,sourceRegistry.prefs(source.id)))throw new Exception("שמירת הרשימה נכשלה.");
            refreshSavedListsScreen();Toast.makeText(this,"נוצרה רשימה חדשה.",Toast.LENGTH_LONG).show();
        } catch(Exception e){Toast.makeText(this,safeError(e),Toast.LENGTH_LONG).show();}
    }
    private void exportSavedList(SavedListStore.Entry entry) {
        try {
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/plain");
            String name=(entry.title==null||entry.title.trim().isEmpty()?"רשימת איסוף":entry.title).replaceAll("[/:*?<>|]","_").replace("\\\\","_").replace("\"","_");
            intent.putExtra(Intent.EXTRA_TITLE,name+".txt");pendingExportListId=entry.id;
            startActivityForResult(intent,REQUEST_EXPORT_FILE);
        } catch(Exception e) {pendingExportListId="";Toast.makeText(this,"לא ניתן לפתוח שמירת קובץ.",Toast.LENGTH_LONG).show();}
    }
    private void exportSavedListToUri(String id,Uri uri) {
        cacheWorker.execute(()->{
            try {
                JSONObject data=savedListStore.readEntry(id);JSONArray rows=data.optJSONArray("products");
                StringBuilder text=new StringBuilder("# משנת אוסף — ").append(data.optString("order_name","רשימה")).append("\n");
                if(rows!=null)for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                    String name=row.optString("name","").replace("|","/");
                    String amount=row.optString("amount","1").replace("|","/");
                    String note=row.optString("note","").replace("|","/");
                    text.append(name).append(" | ").append(amount).append(" | | ").append(note).append("\n");
                }
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new Exception("לא ניתן לפתוח את קובץ היעד.");
                    out.write(text.toString().getBytes(StandardCharsets.UTF_8));out.flush();
                }
                mainHandler.post(()->Toast.makeText(this,"הרשימה יוצאה לקובץ TXT.",Toast.LENGTH_LONG).show());
            }catch(Exception e){String error=safeError(e);mainHandler.post(()->Toast.makeText(this,"ייצוא הרשימה נכשל: "+error,Toast.LENGTH_LONG).show());}
        });
    }

    private void showMergeTargetDialog(SavedListStore.Entry source) {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);LinearLayout panel=Ui.dialogPanel(this);
        panel.addView(Ui.text(this,"בחר רשימת יעד למיזוג",Ui.T_NAME,Ui.P900,true));int choices=0;
        for(SavedListStore.Entry target:savedListStore.list()) {
            if(target.id.equals(source.id))continue;choices++;
            Button choose=Ui.button(this,target.title+" · "+target.itemCount+" פריטים",Ui.Kind.SOFT);
            choose.setOnClickListener(v->{dialog.dismiss();showModernConfirm("אישור מיזוג","הפריטים מ '"+source.title+"' יצורפו ל '"+target.title+"'. כמויות של פריטים זהים יסוכמו; רשימת המקור תישאר שמורה.","מזג רשימות",()->{boolean ok=savedListStore.mergeEntries(target.id,source.id);refreshSavedListsScreen();Toast.makeText(this,ok?"הרשימות מוזגו בהצלחה.":"המיזוג נכשל; הרשימות המקוריות נשמרו.",Toast.LENGTH_LONG).show();});});
            panel.addView(choose,Ui.row(this,Ui.S2,0));
        }
        if(choices==0)panel.addView(Ui.text(this,"צריך לפחות שתי רשימות שמורות כדי למזג.",Ui.T_BODY,Ui.MUTED,false));
        Button cancel=Ui.button(this,"ביטול",Ui.Kind.SOFT);cancel.setOnClickListener(v->dialog.dismiss());panel.addView(cancel,Ui.row(this,Ui.S2,0));
        dialog.setContentView(panel);dialog.show();styleConfirmWindow(dialog);
    }

    private void leaveSavedListsScreen() {
        Screen target=savedListsReturn;
        savedListsScreenVisible=false;savedListsReturnToLogin=false;savedListsReturn=Screen.HOME;
        if(target==Screen.LOGIN)showLoginScreen("");
        else if(target==Screen.SETTINGS)showSettingsScreen();
        else showHomeScreen(false);
    }

    private void deleteSavedListEntry(SavedListStore.Entry entry) {
        final Screen returnTo=savedListsReturn;
        SourceRegistry.Source source=sourceRegistry.find(entry.sourceId);
        if(source!=null){
            File manifest=new File(new File(sourceRegistry.sourceDir(entry.sourceId),"pickup_order_cache"),"order.json");
            boolean same=false;
            try{if(manifest.isFile()){JSONObject current=new JSONObject(readImportText(manifest));
                same=String.valueOf(current.optLong("order_id",-1)).equals(entry.orderId)
                        && current.optString("owner","").equals(entry.owner);}}catch(Exception ignored){}
            if(same){
                Runnable clear=()->cacheWorker.execute(()->{
                    DataGuard.wipeOrderData(sourceRegistry.sourceDir(entry.sourceId),sourceRegistry.prefs(entry.sourceId));
                    savedListStore.delete(entry.id);
                    mainHandler.post(()->{
                        if(entry.sourceId.equals(activeSourceId)){cacheTrusted=false;activeOwner="";clearOrderMemory();}
                        showSavedListsScreen(returnTo);
                    });
                });
                if(entry.sourceId.equals(activeSourceId))clear.run();
                else cacheWorker.execute(()->{
                    DataGuard.wipeOrderData(sourceRegistry.sourceDir(entry.sourceId),sourceRegistry.prefs(entry.sourceId));
                    savedListStore.delete(entry.id);
                    mainHandler.post(()->showSavedListsScreen(returnTo));
                });
                return;
            }
        }
        savedListStore.delete(entry.id);
        showSavedListsScreen(returnTo);
    }

    private void openSavedList(SavedListStore.Entry entry) {
        SourceRegistry.Source source=sourceRegistry.find(entry.sourceId);
        String archiveId=entry.id;
        try{
            if(source!=null&&!entry.owner.isEmpty()&&!source.ownerKey.equals(entry.owner)){
                source=sourceRegistry.findByOwner(entry.owner);
                if(source==null)source=sourceRegistry.create(SourceRegistry.KIND_IMPORTED,entry.sourceLabel,entry.owner);
                archiveId=savedListStore.rebindSource(entry.id,source.id,source.label);
            }else if(source==null){
                source=!entry.owner.isEmpty()?sourceRegistry.findByOwner(entry.owner):null;
                if(source==null)source=sourceRegistry.create(SourceRegistry.KIND_IMPORTED,entry.sourceLabel,entry.owner);
                archiveId=savedListStore.rebindSource(entry.id,source.id,source.label);
            }else if(source.ownerKey.isEmpty()&&!entry.owner.isEmpty()){
                sourceRegistry.updateOwner(source.id,entry.owner);
                archiveId=savedListStore.rebindSource(entry.id,source.id,source.label);
            }
            if(archiveId.isEmpty()){showSyncMessage("לא ניתן לשייך את הרשימה לחיבור המקומי.",false);return;}
        }catch(Exception e){showSyncMessage(e.getMessage(),false);return;}
        final String sourceId=source.id;
        final String savedArchiveId=archiveId;
        Runnable activate=()->cacheWorker.execute(()->{
            boolean installed=savedListStore.install(savedArchiveId,new File(sourceRegistry.sourceDir(sourceId),"pickup_order_cache"));
            boolean restored=installed&&savedListStore.restoreProgress(savedArchiveId,sourceRegistry.prefs(sourceId));
            JSONObject snapshot=null;try{snapshot=savedListStore.readEntry(savedArchiveId);}catch(Exception ignored){}
            final JSONObject saved=snapshot;
            mainHandler.post(()->{if(!installed||!restored||saved==null){showSyncMessage("לא ניתן לפתוח את הרשימה השמורה.",false);return;}
                activeOwner=saved.optString("owner",entry.owner);String print=savedListFingerprint(saved);long orderId=saved.optLong("order_id",-1);
                DataGuard.saveMeta(preferences,activeOwner,orderId,print,preferences.getString(DataGuard.K_TOKEN_FINGERPRINT,""));
                cacheTrusted=isCacheManifestValid(activeOwner);showingSavedList=true;clearOrderMemory();showHomeScreen(false);
                showSyncMessage("הרשימה השמורה נפתחה. היא תישאר במכשיר עד למחיקה ידנית.",false);});
        });
        if(!sourceId.equals(activeSourceId))switchSource(sourceId,activate);else activate.run();
    }
    private String savedListFingerprint(JSONObject saved) {        String fingerprint=saved.optString("fingerprint","");
        if(!fingerprint.isEmpty())return fingerprint;
        JSONArray rows=saved.optJSONArray("products");
        if(rows==null)return "";
        List<ProductRow> restored=new ArrayList<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)return "";
            restored.add(new ProductRow(first(row,"key"),first(row,"name"),first(row,"image_url"),
                    first(row,"amount"),first(row,"note")));
        }
        return saved.optInt("fingerprint_version",1)>=2
                ?DataGuard.fingerprint(restored):DataGuard.legacyFingerprint(restored);
    }

    private void archiveExistingSnapshots() {if(savedListStore!=null&&sourceRegistry!=null)for(SourceRegistry.Source s:sourceRegistry.all())archiveCurrentSnapshot(s.id);}
    private void archiveCurrentSnapshot(String sourceId) {
        if(savedListStore==null||sourceRegistry==null||sourceId==null||sourceId.isEmpty())return;
        SourceRegistry.Source source=sourceRegistry.find(sourceId);if(source==null)return;
        File file=new File(new File(sourceRegistry.sourceDir(sourceId),"pickup_order_cache"),"order.json");
        savedListStore.archive(sourceId,source.label,file,sourceRegistry.prefs(sourceId));
    }

    private LinearLayout buildHomeHeader() {
        // כותרת ומד ההתקדמות נשארים קבועים; פעולות הניהול נמצאות בהגדרות.
        LinearLayout header = Ui.column(this);
        header.setPadding(Ui.dp(this, Ui.S5), Ui.dp(this, Ui.S4),
                Ui.dp(this, Ui.S5), Ui.dp(this, Ui.S2));
        header.addView(buildHomeTitleRow());
        imageModeHint = null;
        header.addView(buildProgressCard(), Ui.row(this, Ui.S2, 0));
        return header;
    }

    private LinearLayout buildHomeTitleRow() {
        // שם המכירה במרכז, והכפתורים בשני הצדדים: מימין הגדרות ואיפוס, משמאל שתי התצוגות.
        // לשני הצדדים אותו רוחב, כדי שהשם יהיה באמצע המסך בדיוק.
        // במסך צר השם יורד לשורה משלו (במרכז), מתחת לכפתורים.
        boolean wide = Ui.wideHeader(this);
        int size = Ui.dp(this, Ui.iconButtonDp(this));
        int gap = Ui.dp(this, Ui.S1);
        headerButtons.clear();

        Button settings = Ui.iconButton(this, Ui.IC_SETTINGS, "הגדרות", false);
        settings.setTag("home_first_header_button");
        settings.setOnClickListener(v -> showSettingsScreen());
        resetButton = Ui.iconButton(this, Ui.IC_RESET, "איפוס ליקוט", false);
        resetButton.setOnClickListener(v -> {
            if (!confirmResetCollection()) showActionToast("אין סימוני איסוף לאיפוס");
        });
        // שני כפתורי התצוגה: רשימה מאוחדת ורשימה לפי חשבון. הכפתור של התצוגה הפעילה מודגש.
        boolean unifiedMode = sourceRegistry != null && "COMBINED_SUM".equals(sourceRegistry.viewMode());
        Button unified = buildViewModeButton("COMBINED_SUM", Ui.IC_LIST_SINGLE, "רשימה מאוחדת", unifiedMode);
        Button byAccount = buildViewModeButton("COMBINED_BY_SOURCE", Ui.IC_LIST_STACK, "רשימה לפי חשבון", !unifiedMode);

        LinearLayout rightSide = new LinearLayout(this);
        rightSide.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        rightSide.addView(settings, new LinearLayout.LayoutParams(size, size));
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(size, size);
        resetLp.setMarginStart(gap);
        rightSide.addView(resetButton, resetLp);

        LinearLayout leftSide = new LinearLayout(this);
        leftSide.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        leftSide.addView(unified, new LinearLayout.LayoutParams(size, size));
        LinearLayout.LayoutParams byAccountLp = new LinearLayout.LayoutParams(size, size);
        byAccountLp.setMarginStart(gap);
        leftSide.addView(byAccount, byAccountLp);

        TextView title = Ui.text(this, currentOrderTitle.isEmpty() ? "רשימת איסוף" : currentOrderTitle,
                wide ? Ui.T_TITLE : Ui.T_NAME + 2, Ui.P900, true);
        homeTitleText = title;
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        headerButtons.add(settings); headerButtons.add(resetButton);
        headerButtons.add(unified); headerButtons.add(byAccount);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int sideWidth = size * 2 + gap;
        row.addView(rightSide, new LinearLayout.LayoutParams(sideWidth, size));
        if (wide) {
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
            titleLp.setMargins(Ui.dp(this, Ui.S2), 0, Ui.dp(this, Ui.S2), 0);
            row.addView(title, titleLp);
        } else {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        row.addView(leftSide, new LinearLayout.LayoutParams(sideWidth, size));
        if (wide) return row;

        LinearLayout stacked = Ui.column(this);
        stacked.addView(row, new LinearLayout.LayoutParams(-1, -2));
        stacked.addView(title, Ui.row(this, Ui.S2, 0));
        return stacked;
    }

    private Button buildViewModeButton(String mode, int icon, String description, boolean active) {
        Button button = Ui.iconButton(this, icon, description, active);
        button.setEnabled(!syncAllRunning);
        button.setOnClickListener(v -> {
            if (syncAllRunning || active) return;
            sourceRegistry.setViewMode(mode);
            showingSavedList = false;
            showHomeScreen(false);
            showActionToast("COMBINED_SUM".equals(mode) ? "רשימה מאוחדת מכל החשבונות" : "רשימות נפרדות לפי חשבון");
        });
        return button;
    }

    private String appVersionName() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "לא ידועה" : info.versionName;
        } catch (Exception ignored) { return "לא ידועה"; }
    }

    private Button buildRetryButton() {
        retryButton = Ui.button(this, "לנסות לסנכרן שוב", Ui.Kind.SOFT);
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> {
            if (combinedView || sourceRegistry != null && sourceRegistry.all().size() > 1)
                syncAllAccounts();
            else loadLatestOrder();
        });
        return retryButton;
    }

    private LinearLayout buildProgressCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.shape(this, Ui.SURFACE, Ui.R_CARD, 1, Ui.P200));
        card.setPadding(Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3),
                Ui.dp(this, Ui.S4), Ui.dp(this, Ui.S3));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        progressPercent = Ui.text(this, "0%", Ui.T_TITLE, Ui.P800, true);
        progressPercent.setGravity(Gravity.CENTER);
        progressPercent.setMinWidth(Ui.dp(this, Ui.PERCENT_WIDTH));
        LinearLayout.LayoutParams percent = new LinearLayout.LayoutParams(
                Ui.dp(this, Ui.PERCENT_WIDTH), -2);
        percent.leftMargin = Ui.dp(this, Ui.S3);
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

        // החיפוש נע בתוך אזור הגלילה יחד עם הרשימה; הכותרת וההתקדמות נשארות נגישות.
        page.addView(buildSearchRow(), Ui.row(this, 0, Ui.S2));
        messageText = Ui.banner(this, "");
        messageText.setVisibility(View.GONE);
        page.addView(messageText);
        // כפתור "לנסות שוב" מוצג רק אחרי כשל סנכרון. בעבר הוא נוצר אך מעולם לא נוסף למסך.
        page.addView(buildRetryButton(), Ui.row(this, 0, Ui.S2));
        searchEmptyText = Ui.banner(this, "לא נמצאו פריטים שמתאימים לחיפוש.");
        searchEmptyText.setVisibility(View.GONE);
        page.addView(searchEmptyText);

        pendingList = createProductContainer();
        collectedList = createProductContainer();
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
        String text = value == null ? "" : value.trim();
        if (screen != Screen.HOME || messageText == null) {
            // מחוץ למסך הרשימה אין שורת הודעות; הודעה חשובה מוצגת כ-Toast במקום להיעלם.
            if (!text.isEmpty()) Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            return;
        }
        messageText.setText(text);
        messageText.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (retryButton != null) {
            retryButton.setText(combinedView || (sourceRegistry != null && sourceRegistry.all().size() > 1)
                    ? "סנכרן את כל החיבורים שוב" : "לנסות לסנכרן שוב");
            retryButton.setVisibility(canRetry && !syncAllRunning ? View.VISIBLE : View.GONE);
        }
    }

    /** פותח אישור איפוס. מחזיר false כשאין מה לאפס, כדי שהמסך יציג הודעה במקום לשתוק. */
    private boolean confirmResetCollection() {
        boolean hasTicks = !collected.isEmpty() || !partialCollected.isEmpty();
        if (combinedView) {
            for (java.util.List<SourcePart> parts : combinedParts.values())
                for (SourcePart part : parts) if (part.collected || part.partialQuantity > 0) hasTicks = true;
        }
        if (!hasTicks) return false;
        showModernConfirm("איפוס האיסוף", "להחזיר את כל המוצרים לרשימת הממתינים?", "איפוס", () -> {
            if (combinedView) {
                for (String key : new ArrayList<>(combinedParts.keySet())) {
                    updateCombinedParts(key, false);
                    ProductCard card = productCards.get(key);
                    if (card != null) {
                        card.views.note.setText(combinedNote(combinedParts.get(key)));
                        card.views.note.setVisibility(card.views.note.getText().length() == 0 ? View.GONE : View.VISIBLE);
                    }
                }
            } else {
                collected.clear();
                partialCollected.clear();
                saveCollectedState();
            }
            collected.clear();
            partialCollected.clear();
            syncCompletionState();
            renderProducts(false);
            pageScroll.post(this::focusFirstPending);
            showActionToast("כל המוצרים הוחזרו לרשימת הממתינים");
        });
        return true;
    }

    private void confirmLogout() {
        showModernConfirm("התנתקות מהחשבון",
                "למחוק את פרטי ההתחברות? הרשימה, התמונות וסימוני הליקוט יישארו שמורים לצפייה ללא אינטרנט.",
                "התנתקות", this::logout);
    }

    private void confirmClearSavedData() {
        showModernConfirm("מחיקת כל הנתונים השמורים", "למחוק את המטמון הפעיל, התמונות וסימוני האיסוף? הרשימות שבארכיון יישארו עד למחיקה ידנית במסך רשימות שמורות.", "מחיקה", this::clearAllSavedData);
    }

    private void clearAllSavedData() {
        if (sourceMergeInProgress) { showSyncMessage("החשבון מתאחד עם מקור קיים. המתינו רגע.", false); return; }
        if (syncAllRunning) { showSyncMessage("המתינו לסיום הסנכרון לפני מחיקת נתונים.", false); return; }
        dismissCelebration();
        wasComplete = false;
        final int generation = ++authGeneration;
        loading = false;
        accessToken = "";
        activeOwner = "";
        cacheTrusted = false;
        preferences.edit().remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY).commit();
        clearOrderMemory();
        cacheWorker.execute(() -> {
            if (generation != authGeneration) return;
            wipeOrderData();
            clearToken();
            mainHandler.post(() -> {
                if (generation == authGeneration && !isFinishing())
                    showLoginScreen("כל הנתונים המקומיים נמחקו, כולל פרטי ההתחברות. אפשר להתחבר מחדש.");
            });
        });
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
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1f);        first.setMargins(0, 0, Ui.dp(this, Ui.S2), 0);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1f);
        second.setMargins(Ui.dp(this, Ui.S2), 0, 0, 0);
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
        dialog.getWindow().getDecorView().post(() ->
                installActionToasts(dialog.getWindow().getDecorView()));
    }

    private void syncAllAccounts() {
        if (syncAllRunning) return;
        showingSavedList=false;
        syncQueue.clear();
        for (SourceRegistry.Source source : sourceRegistry.all()) {
            if (SourceRegistry.KIND_ACCOUNT.equals(source.kind) && secretStore.get(source.id) != null)
                syncQueue.add(source.id);
        }
        if (syncQueue.isEmpty()) {
            showSyncMessage("אין חשבונות מחוברים לסנכרון. אפשר להתחבר מחדש דרך ניהול החיבורים.", false);
            return;
        }
        syncReturnSource=activeSourceId;syncReturnMode=sourceRegistry.viewMode();
        syncQueueIndex=0;syncQueueTotal=syncQueue.size();syncAllRunning=true;syncAwaitingResult=false;
        combinedView=false;
        if(viewModeButton!=null)viewModeButton.setEnabled(false);
        showSyncMessage("מתחיל סנכרון של כל "+syncQueueTotal+" הטוקנים הפעילים…",false);
        syncNextAccount();
    }

    private void syncNextAccount() {
        if(!syncAllRunning)return;
        if(syncQueueIndex>=syncQueue.size()){finishSyncAll();return;}
        String id=syncQueue.get(syncQueueIndex++);
        if(sourceRegistry.find(id)==null){syncNextAccount();return;}
        syncAwaitingResult=true;
        if(id.equals(activeSourceId)) {
            if(accessToken.isEmpty()) advanceSyncQueue(); else loadLatestOrder();
        } else switchSource(id);
    }

    private void advanceSyncQueue() {
        if(!syncAllRunning||!syncAwaitingResult)return;
        syncAwaitingResult=false;
        cacheWorker.execute(() -> mainHandler.post(this::syncNextAccount));
    }

    private void finishSyncAll() {
        if(!syncAllRunning)return;
        syncAllRunning=false;syncAwaitingResult=false;syncQueue.clear();
        final String restoreId=syncReturnSource,restoreMode=syncReturnMode;
        Runnable finish=()->{sourceRegistry.setViewMode(restoreMode);showHomeScreen(false);
            showSyncMessage("הסנכרון הסתיים עבור "+syncQueueTotal+" טוקנים.",false);};
        if(!restoreId.isEmpty()&&!restoreId.equals(activeSourceId)&&sourceRegistry.find(restoreId)!=null)
            switchSource(restoreId,finish);
        else finish.run();
    }

    private void loadLatestOrder() {        if (combinedView && !syncAllRunning) { syncAllAccounts(); return; }
        if (loading || accessToken.isEmpty()) return;
        final RequestSession session = new RequestSession(authGeneration, accessToken);
        final String requestSourceId = activeSourceId;
        loading = true;
        lastSyncAttemptAt = System.currentTimeMillis();
        showSyncMessage("מתחבר לשרת וטוען הזמנה אחרונה…", false);
        network.execute(() -> {
            try {
                JSONArray orders = apiGetArray("/api/orders", session);
                String owner = DataGuard.ownerFromToken(session.token);
                if (owner.isEmpty()) owner = DataGuard.ownerFromOrders(orders);
                                JSONObject latest = chooseLatestPickupOrder(orders);
                if (latest == null) throw new Exception("לא נמצאה הזמנה בחשבון.");
                long id = extractOrderId(latest);
                if (id <= 0) throw new Exception("לא נמצא מזהה להזמנה האחרונה.");
                JSONObject order = unwrapOrder(apiGetObject("/api/orders/" + id, session));
                if (owner.isEmpty()) owner = DataGuard.ownerFromOrder(order);
                if (!session.isCurrent(this)) return;
                if (owner.isEmpty() && DataGuard.tokenFingerprint(session.token).equals(
                        preferences.getString(DataGuard.K_TOKEN_FINGERPRINT, "")))
                    owner = preferences.getString(DataGuard.K_OWNER, "");
                List<ProductRow> parsed = parseProducts(order);
                if (parsed.isEmpty()) throw new Exception("ההזמנה נמצאה, אבל לא נמצאו בה פריטים להצגה.");
                // שם המכירה נלקח מרשומת ההזמנה ברשימת ההזמנות (נתוני ההיסטוריה), ורק אם חסר שם - מפרטי ההזמנה.
                String sale = OrderHistoryPage.saleName(latest);
                if (sale.isEmpty()) sale = OrderHistoryPage.saleName(order);
                final String orderTitle = sale.isEmpty() ? "הזמנה " + id : sale;
                final JSONObject pickupInfo = PickupInfo.extract(latest, order);
                sortByCategory(parsed, pickupInfo);
                if (!session.isCurrent(this)) return;
                // This request is deliberately detached from the order flow: product data
                // is rendered as soon as it arrives, while the account name loads quietly.
                fetchAccountProfileName(requestSourceId, session.token);
                final String resolvedOwner = owner;
                final String resolvedToken = session.token;
                mainHandler.post(() -> onFreshOrder(session.generation, resolvedToken,
                        resolvedOwner, id, order, parsed, orderTitle, pickupInfo));
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
            if (sourceRegistry != null && !activeSourceId.isEmpty()) sourceRegistry.markNeedsLogin(activeSourceId);
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
            advanceSyncQueue();
            return;
        }
        if (products.isEmpty() && cacheTrusted) loadCachedOrder();
        renderProducts(false);
        showSyncMessage(products.isEmpty() ? message
                : "מוצגת ההזמנה השמורה מהמכשיר. הסנכרון נכשל: " + message,
                !accessToken.isEmpty());
        advanceSyncQueue();
    }

    private void loadCachedOrder() {
        if (!cacheTrusted || !hasCachedOrder()) return;
        File root = new File(activeFilesDir(), "pickup_order_cache");
        File manifest = new File(root, "order.json");
        try (FileInputStream in = new FileInputStream(manifest);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            JSONObject saved = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            long id = saved.optLong("order_id", -1);
            JSONArray rows = saved.optJSONArray("products");
            if (id <= 0 || rows == null || (rows.length() == 0 && !saved.optBoolean("imported", false))) throw new Exception("invalid cache");
            List<ProductRow> restored = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) throw new Exception("invalid item");
                String imageFile = first(row, "image_file");
                String imagePath = "";
                if (!imageFile.isEmpty()) {
                    File image = new File(imageFile);
                    if (!image.isAbsolute()) image = new File(root, imageFile);
                    if (image.isFile() && image.length() > 0) imagePath = image.getAbsolutePath();
                    // Images are optional. A missing/uncached image must never block list loading.
                }
                restored.add(new ProductRow(first(row, "key"), first(row, "name"),
                        first(row, "image_url"), first(row, "amount"), first(row, "note"), imagePath,
                        first(row, "category"), row.optInt("category_order", 0)));
            }
            // Version 1 caches predate image URLs being included in the content digest.
            // Keep them readable offline; the next online sync upgrades their fingerprint to v2.
            int fingerprintVersion = saved.optInt("fingerprint_version", 1);
            if (fingerprintVersion >= 2
                    && !DataGuard.fingerprint(restored).equals(preferences.getString(DataGuard.K_PRINT, "")))
                throw new Exception("cache fingerprint mismatch");
            currentOrderId = String.valueOf(id);
            setCurrentOrderTitle(saved.optString("order_name", ""));
            currentPickupInfo = saved.optJSONObject("pickup_info");
            sortByCategory(restored, currentPickupInfo);
            products.clear();
            products.addAll(restored);
            loadCollectedState();
            syncCompletionState();
        } catch (Exception error) {
            cacheTrusted = false;
            cacheWorker.execute(() -> DataGuard.wipeOrderData(activeFilesDir(), preferences));
        }
    }

    private boolean hasCachedOrder() {
        return cacheTrusted && isCacheManifestValid(activeOwner);
    }

    private void onFreshOrder(int generation, String requestToken, String owner,
                              long orderId, JSONObject order, List<ProductRow> rows, String orderTitle,
                              JSONObject pickupInfo) {
        if (generation != authGeneration || !requestToken.equals(accessToken) || isFinishing()) return;
        loading = false;
        String stableOwner = owner == null ? "" : owner;
        if (stableOwner.isEmpty()) {
            cacheTrusted = false;
            activeOwner = "";
            clearOrderMemory();
            currentOrderId = String.valueOf(orderId);
            setCurrentOrderTitle(orderTitle);
            currentPickupInfo = pickupInfo;
            products.addAll(rows);
            syncCompletionState();
            renderProducts(false);
            showSyncMessage("לא ניתן לאמת את זהות החשבון; הרשימה מוצגת אך לא תישמר במטמון.", false);
            cacheWorker.execute(() -> {
                if (generation == authGeneration) wipeOrderData();
                mainHandler.post(this::advanceSyncQueue);
            });
            return;
        }

        SourceRegistry.Source existingOwnerSource = sourceRegistry.findByOwner(stableOwner);
        String currentSavedOwner = preferences.getString(DataGuard.K_OWNER, "");
        if (existingOwnerSource != null && !existingOwnerSource.id.equals(activeSourceId)
                && !stableOwner.equals(currentSavedOwner)) {
            final String existingId = existingOwnerSource.id;
            final String temporaryId = activeSourceId;
            sourceMergeInProgress = true;
            loading = true;
            showSyncMessage("נמצא מקור קיים לאותו מזהה לקוח. מחבר אליו את החשבון…", false);
            // switchSource drains all queued writes from the temporary source before moving
            // the active preferences and cache directory to the existing source.
            switchSource(existingId, () -> {
                try {
                    secretStore.put(existingId, requestToken);
                    sourceRegistry.promoteToAccount(existingId);
                    String email = emailFromToken(requestToken);
                    if (!email.isEmpty()) {
                        sourceRegistry.rename(existingId, email);
                        sourceRegistry.prefs(existingId).edit().remove("profile_name_loaded").apply();
                    }
                    sourceRegistry.updateOwner(existingId, stableOwner);
                    accessToken = requestToken;
                    activeOwner = stableOwner;
                    cacheTrusted = isCacheManifestValid(stableOwner);
                    cacheWorker.execute(() -> {
                        boolean removed = sourceRegistry.remove(temporaryId);
                        mainHandler.post(() -> {
                            sourceMergeInProgress = false;
                            showHomeScreen(true);
                            if (!removed) showSyncMessage("החשבון חובר לרשימה הקיימת, אך המקור הזמני לא הוסר. ניתן להסירו מניהול החיבורים.", false);
                            else showSyncMessage("החשבון חובר לרשימה הקיימת ללא יצירת כפילות.", false);
                        });
                    });
                } catch (Exception mergeError) {
                    sourceMergeInProgress = false;
                    loading = false;
                    android.util.Log.w("MishnatPickup", "Could not reconnect existing source", mergeError);
                    showHomeScreen(false);
                    showSyncMessage("לא ניתן היה לחבר אוטומטית את המקור הקיים. החשבון הזמני נשאר שמור.", false);
                }
            });
            return;
        }

        String fingerprint = DataGuard.fingerprint(rows);
        DataGuard.Verdict verdict = DataGuard.judge(preferences, stableOwner, orderId, fingerprint);
        Set<String> retainedTicks = new HashSet<>();
        Map<String,Double> retainedPartials = new HashMap<>();
        if (verdict != DataGuard.Verdict.SAME) {
            setImageMode(false);
            if (imageViewer != null) imageViewer.reset();
        }
        if (verdict == DataGuard.Verdict.CONTENT_CHANGED) {
            retainedTicks.addAll(readCollectedTicks(String.valueOf(orderId)));
            // A just-tapped item may still be queued on cacheWorker; include the live in-memory
            // state so a server edit cannot discard the latest user action.
            for (ProductRow previous : products) {
                if (collected.contains(previous.key)) retainedTicks.add(DataGuard.tick(previous));
                Double partial = partialCollected.get(previous.key);
                if (partial != null && partial > 0) {
                    for (ProductRow current : rows) if (current.key.equals(previous.key)
                            && current.amount.equals(previous.amount)) retainedPartials.put(current.key, partial);
                }
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
        sourceRegistry.updateOwner(activeSourceId, stableOwner);
        sourceRegistry.markSynced(activeSourceId);
        cacheTrusted = true;
        orderData = order;
        currentOrderId = String.valueOf(orderId);
        setCurrentOrderTitle(orderTitle);
        currentPickupInfo = pickupInfo;
        products.clear();
        products.addAll(rows);
        collected.clear(); partialCollected.clear();
        if (verdict == DataGuard.Verdict.SAME) {
            loadCollectedState();
        } else if (verdict == DataGuard.Verdict.CONTENT_CHANGED) {
            for (ProductRow row : rows) {
                if (retainedTicks.contains(DataGuard.tick(row))) collected.add(row.key);
                else if (retainedPartials.containsKey(row.key)) partialCollected.put(row.key, retainedPartials.get(row.key));
            }
        }
        syncCompletionState();
        renderProducts(false);
        wireFocusOrder();
        showSyncMessage("", false);

        boolean rewrite = verdict != DataGuard.Verdict.SAME || !isCacheManifestValid(stableOwner);
        if (!rewrite) {
            saveCollectedState();
            final String sourceForArchive = activeSourceId;
            cacheWorker.execute(() -> {
                if (generation == authGeneration && requestToken.equals(accessToken)) {
                    DataGuard.saveMeta(preferences, stableOwner, orderId, fingerprint,
                            DataGuard.tokenFingerprint(requestToken));
                    // רשימות שנשמרו לפני שהתווסף שם המכירה מקבלות אותו בסנכרון הבא.
                    updateCachedOrderName(orderTitle, pickupInfo);
                    archiveCurrentSnapshot(sourceForArchive);
                }
                mainHandler.post(this::advanceSyncQueue);
            });
            return;
        }

        final String tokenFingerprint = DataGuard.tokenFingerprint(requestToken);
        final String orderKey = String.valueOf(orderId);
        final JSONArray ticks = collectedTicksJson();
        final String partialState = partialQuantitiesJson().toString();
        final String sourceForArchive = activeSourceId;
        final DataGuard.Verdict decision = verdict;
        cacheWorker.execute(() -> {
            if (generation != authGeneration || !requestToken.equals(accessToken)) return;
            if (decision != DataGuard.Verdict.SAME) {
                archiveCurrentSnapshot(sourceForArchive);
                wipeOrderData();
            }
            if (generation != authGeneration || !requestToken.equals(accessToken)) return;
            writeCollectedState(orderKey, ticks);
            preferences.edit().putString("partial_" + orderKey, partialState).commit();
            try {
                cacheOrderIfComplete(generation, requestToken, stableOwner, orderId,
                        order, rows, fingerprint, orderTitle, pickupInfo);
                if (generation != authGeneration || !requestToken.equals(accessToken)) return;
                DataGuard.saveMeta(preferences, stableOwner, orderId, fingerprint, tokenFingerprint);
                archiveCurrentSnapshot(sourceForArchive);
            } catch (Exception cacheError) {
                android.util.Log.w("MishnatPickup",
                        "Offline cache update failed; stale snapshot was not retained", cacheError);
                if (generation == authGeneration && requestToken.equals(accessToken)) {
                    DataGuard.wipeCacheFiles(activeFilesDir());
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
            mainHandler.post(this::advanceSyncQueue);
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

    /** חלון מידע האיסוף: מועד ושעות, מקום, איש קשר, הערות ופרטי ההזמנה. */
    private void showPickupInfo(List<JSONObject> infos) {
        if (infos == null || infos.isEmpty()) {
            Toast.makeText(this, "אין מידע איסוף להזמנה זו.", Toast.LENGTH_LONG).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = Ui.dialogPanel(this);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title = Ui.text(this, "מידע איסוף", Ui.T_NAME, Ui.P900, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button close = Ui.iconButton(this, Ui.IC_CLOSE, "סגירת מידע האיסוף", false);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close);
        panel.addView(head, Ui.row(this, 0, Ui.S2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        // המקלדת גוללת את המידע עצמו (חצים / 2 ו-8), ובסוף עוברת לכפתורי הפעולה.
        scroll.setFocusable(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scroll.setBackground(Ui.focusFrame(this));
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dp(this, Ui.S2), 0, Ui.dp(this, Ui.S2), Ui.dp(this, Ui.S2));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        for (int i = 0; i < infos.size(); i++) {
            JSONObject info = infos.get(i);
            if (infos.size() > 1) {
                String label = info.optString("_source_label", "").trim();
                String heading = PickupInfo.title(info);
                TextView source = Ui.text(this, (label.isEmpty() ? "" : label + " · ") + heading,
                        Ui.T_BODY, Ui.P800, true);
                source.setBackground(Ui.shape(this, Ui.P100, Ui.R_BUTTON, 0, 0));
                source.setPadding(Ui.dp(this, Ui.S3), Ui.dp(this, Ui.S2), Ui.dp(this, Ui.S3), Ui.dp(this, Ui.S2));
                body.addView(source, Ui.row(this, i == 0 ? 0 : Ui.S5, Ui.S1));
            }
            for (PickupInfo.Line line : PickupInfo.describe(info)) {
                if (line.isSection()) {
                    body.addView(Ui.section(this, line.label));
                    continue;
                }
                LinearLayout row = Ui.column(this);
                row.addView(Ui.text(this, line.label, Ui.T_NOTE, Ui.P600, true));
                TextView value = Ui.text(this, line.value, Ui.T_BODY, Ui.INK, false);
                row.addView(value, Ui.row(this, 2, 0));
                body.addView(row, Ui.row(this, Ui.S1, Ui.S2));
            }
            addPickupActions(body, info, dialog);
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        dialog.setContentView(panel);
        dialog.show();
        styleConfirmWindow(dialog);
        if (dialog.getWindow() != null) {
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.86f);
            dialog.getWindow().setLayout(dialog.getWindow().getAttributes().width, height);
        }
        scroll.requestFocus();
    }

    /** כפתורי ניווט למפה וחיוג לאחראי הנקודה, כשיש מיקום או טלפון. */
    private void addPickupActions(LinearLayout body, JSONObject info, Dialog dialog) {
        String map = PickupInfo.mapUri(info);
        String phone = PickupInfo.phone(info);
        if (!map.isEmpty()) {
            Button open = Ui.button(this, "פתיחת המיקום במפה", Ui.Kind.PRIMARY);
            open.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(map))); }
                catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, "לא נמצאה במכשיר אפליקציית מפות.", Toast.LENGTH_LONG).show();
                }
            });
            body.addView(open, Ui.row(this, Ui.S3, Ui.S1));
        }
        if (!phone.isEmpty()) {
            Button call = Ui.button(this, "חיוג לאחראי הנקודה", Ui.Kind.SOFT);
            call.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.replaceAll("[^0-9+]", "")))); }
                catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, "לא ניתן לפתוח חייגן במכשיר.", Toast.LENGTH_LONG).show();
                }
            });
            body.addView(call, Ui.row(this, Ui.S1, Ui.S1));
        }
    }

    /** מעדכן את שם המכירה בעותק המקומי של ההזמנה הפעילה, בלי לגעת במוצרים ובסימונים. */
    private void updateCachedOrderName(String orderTitle, JSONObject pickupInfo) {
        if ((orderTitle == null || orderTitle.isEmpty()) && pickupInfo == null) return;
        File manifest = new File(new File(activeFilesDir(), "pickup_order_cache"), "order.json");
        if (!manifest.isFile()) return;
        try {
            JSONObject saved = new JSONObject(readImportText(manifest));
            boolean sameName = orderTitle == null || orderTitle.isEmpty()
                    || orderTitle.equals(saved.optString("order_name", ""));
            JSONObject oldInfo = saved.optJSONObject("pickup_info");
            boolean sameInfo = pickupInfo == null || (oldInfo != null && oldInfo.toString().equals(pickupInfo.toString()));
            if (sameName && sameInfo) return;
            if (!sameName) saved.put("order_name", orderTitle);
            if (!sameInfo) saved.put("pickup_info", pickupInfo);
            File temp = new File(manifest.getParentFile(), "order.json.tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(saved.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (!temp.renameTo(manifest)) temp.delete();
        } catch (Exception ignored) { }
    }

    /** שם ההזמנה המוצג בכותרת המסך: שם המכירה בלבד. */
    private void setCurrentOrderTitle(String title) {
        currentOrderTitle = title == null ? "" : title.trim();
        if (homeTitleText != null)
            homeTitleText.setText(currentOrderTitle.isEmpty() ? "רשימת איסוף" : currentOrderTitle);
    }

    private void cacheOrderIfComplete(int generation, String requestToken, String owner,
                                      long orderId, JSONObject order, List<ProductRow> rows,
                                      String fingerprint, String orderTitle, JSONObject pickupInfo) throws Exception {
        if (generation != authGeneration || !requestToken.equals(accessToken)) return;
        File root = new File(activeFilesDir(), "pickup_order_cache");
        File temp = new File(activeFilesDir(), "pickup_order_cache_tmp");
        File backup = new File(activeFilesDir(), "pickup_order_cache_backup");
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
            // Store list data immediately. Image downloads are independent background work.
            // The URL-keyed image cache persists across orders and account switches.
            imageFile = "";
            JSONObject item = new JSONObject();            item.put("key", row.key);
            item.put("name", row.name);
            item.put("image_url", row.imageUrl);
            item.put("amount", row.amount);
            item.put("note", row.note);
            item.put("category", row.category);
            item.put("category_order", row.categoryOrder);
            item.put("image_file", imageFile);
            savedRows.put(item);
        }
        JSONObject snapshot = new JSONObject();
        snapshot.put("order_id", orderId);
        snapshot.put("owner", owner);
        snapshot.put("order_name", orderTitle == null ? "" : orderTitle);
        if (pickupInfo != null) snapshot.put("pickup_info", pickupInfo);
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
            connection = openConnection(new URL(url));
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
            if (bitmap == null) return false;            bitmap.recycle();
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
        collected.clear(); partialCollected.clear();
        if (currentOrderId.isEmpty()) { syncCompletionState(); return; }
        Set<String> saved = readCollectedTicks(currentOrderId);
        JSONObject partial = new JSONObject();
        try { partial = new JSONObject(preferences.getString("partial_" + currentOrderId, "{}")); }
        catch (Exception ignored) { }
        for (ProductRow row : products) {
            if (saved.contains(DataGuard.tick(row))) collected.add(row.key);
            else {
                double value = partial.optDouble(row.key, 0);
                if (value > 0) {
                    if (value >= parseQuantity(row.amount) - 0.000001) collected.add(row.key);
                    else partialCollected.put(row.key, value);
                }
            }
        }
        saveCollectedState();
        syncCompletionState();
    }

    private JSONObject partialQuantitiesJson() {
        JSONObject saved = new JSONObject();
        try { for (Map.Entry<String,Double> entry : partialCollected.entrySet())
            if (entry.getValue() != null && entry.getValue() > 0) saved.put(entry.getKey(), entry.getValue()); }
        catch (Exception ignored) { }
        return saved;
    }

    private void saveCollectedState() {
        if (currentOrderId.isEmpty()) return;
        final String orderId = currentOrderId;
        final String sourceId = activeSourceId;
        final int generation = authGeneration;
        final JSONArray ticks = collectedTicksJson();
        final String partial = partialQuantitiesJson().toString();
        cacheWorker.execute(() -> {
            if (generation == authGeneration) {
                writeCollectedState(orderId, ticks);
                preferences.edit().putString("partial_" + orderId, partial).commit();
                archiveCurrentSnapshot(sourceId);
            }
        });
    }

    private void renderProducts(boolean ignoredScrollFlag) {
        if (pendingList == null || collectedList == null) return;
        View focused = getCurrentFocus();
        Object focusedTag = focused == null ? null : focused.getTag();
        String focusedKey = focusRestoreKey != null ? focusRestoreKey
                : (focusedTag instanceof String ? (String) focusedTag : null);
        focusRestoreKey = null;
        boolean sameKeys = productCards.size() == products.size() && lastRenderedDisplayMode == displayMode;
        if (sameKeys) for (ProductRow product : products) {
            ProductCard existing = productCards.get(product.key);
            if (existing == null || !sameProduct(existing.product, product)) { sameKeys = false; break; }
        }
        if (!sameKeys) {
            pendingList.removeAllViews();
            collectedList.removeAllViews();
            productCards.clear();partialCards.clear();
            for(ProductRow product:products)productCards.put(product.key,createProductCard(product));
        }
        for (ProductRow product : products) {
            ProductCard card = productCards.get(product.key);
            if (card == null) continue;
            if (!matchesSearch(product)) { ViewGroup oldParent=(ViewGroup)card.row.getParent(); if(oldParent!=null)oldParent.removeView(card.row);
                ProductCard hiddenPartial=partialCards.get(product.key);if(hiddenPartial!=null&&hiddenPartial.row.getParent() instanceof ViewGroup)((ViewGroup)hiddenPartial.row.getParent()).removeView(hiddenPartial.row);continue; }
            boolean done=collected.contains(product.key);
            double partial=done?0:collectedQuantity(product),total=parseQuantity(product.amount);
            double remaining=Math.max(0,total-partial);
            if(done)bindProductCard(card,product,true);else bindProductCard(card,product,false,formatQuantity(remaining),partial>0);
            ViewGroup target=done?collectedList:pendingList;
            ViewGroup parent=(ViewGroup)card.row.getParent();
            if(parent!=target){if(parent!=null)parent.removeView(card.row);target.addView(card.row,insertionIndex(target,product,done),productLayoutParams(target,insertionIndex(target,product,done)));}
            else{int wanted=insertionIndex(target,product,done),current=target.indexOfChild(card.row);
                if(current!=wanted){target.removeView(card.row);target.addView(card.row,Math.min(wanted,target.getChildCount()),productLayoutParams(target,Math.min(wanted,target.getChildCount())));}}
            ProductCard partialCard=partialCards.get(product.key);
            if(!done&&partial>0&&partialCard==null){partialCard=createPartialProductCard(product);partialCards.put(product.key,partialCard);}
            if(partialCard!=null){
                if(!done&&partial>0){bindPartialProductCard(partialCard,product,partial);
                    ViewGroup pp=(ViewGroup)partialCard.row.getParent();
                    if(pp!=collectedList){if(pp!=null)pp.removeView(partialCard.row);collectedList.addView(partialCard.row,partialInsertionIndex(product),productLayoutParams(collectedList,partialInsertionIndex(product)));}
                    else{int wanted=partialInsertionIndex(product),current=collectedList.indexOfChild(partialCard.row);
                        if(current!=wanted){collectedList.removeView(partialCard.row);collectedList.addView(partialCard.row,Math.min(wanted,collectedList.getChildCount()),productLayoutParams(collectedList,Math.min(wanted,collectedList.getChildCount())));}}
                }else if(partialCard.row.getParent() instanceof ViewGroup)((ViewGroup)partialCard.row.getParent()).removeView(partialCard.row);
            }
        }
        // Grid positions are rebuilt from the original product order after every state
        // change. This keeps both sections stable when cards move between pending and
        // collected, and explicitly leaves any incomplete row's empty cells on the left.
        reflowGridListsInProductOrder();
        lastRenderedDisplayMode = displayMode;
        int visibleCount = 0; for(ProductRow p:products) if(matchesSearch(p)) visibleCount++;
        if (searchEmptyText != null) searchEmptyText.setVisibility(!searchQuery.trim().isEmpty() && visibleCount==0 ? View.VISIBLE : View.GONE);
        updateProgress();
        wireFocusOrder();
        if (!sameKeys) {
            pageScroll.post(() -> {
                ProductCard restore = null;
                if (focusedKey != null) {
                    restore = focusedKey.startsWith("partial:")
                            ? partialCards.get(focusedKey.substring(8))
                            : productCards.get(focusedKey);
                }
                if (restore == null || !restore.row.requestFocus()) focusFirstPending();
            });
        }
    }

    private void reflowGridListsInProductOrder() {
        if (displayMode != 5 || !(pendingList instanceof GridLayout)
                || !(collectedList instanceof GridLayout)) return;

        ArrayList<ProductCard> pendingOrder = new ArrayList<>();
        ArrayList<ProductCard> collectedOrder = new ArrayList<>();
        for (ProductRow product : products) {
            if (!matchesSearch(product)) continue;
            ProductCard full = productCards.get(product.key);
            if (full != null) {
                if (full.row.getParent() == pendingList) pendingOrder.add(full);
                else if (full.row.getParent() == collectedList) collectedOrder.add(full);
            }
            ProductCard partial = partialCards.get(product.key);
            if (partial != null && partial.row.getParent() == collectedList)
                collectedOrder.add(partial);
        }

        pendingList.removeAllViews();
        collectedList.removeAllViews();
        for (int i = 0; i < pendingOrder.size(); i++)
            pendingList.addView(pendingOrder.get(i).row, productLayoutParams(pendingList, i));
        for (int i = 0; i < collectedOrder.size(); i++)
            collectedList.addView(collectedOrder.get(i).row, productLayoutParams(collectedList, i));
    }

    private int insertionIndex(ViewGroup target, ProductRow product, boolean done) {
        int index = 0;
        for (ProductRow candidate : products) {
            if (candidate.key.equals(product.key)) break;
            if (matchesSearch(candidate) && (done ? (collected.contains(candidate.key) || collectedQuantity(candidate) > 0) : !collected.contains(candidate.key))) index++;
        }
        return Math.min(index, target.getChildCount());
    }

    private ProductCard createProductCard(ProductRow product) {
        Ui.CardViews views = Ui.productCard(this, displayMode);
        ProductCard card = new ProductCard(views, product);
        views.root.setTag(product.key);
        views.root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (displayMode != 0) {
            if (!product.imagePath.isEmpty()) loadLegacyProductImage(product, views.image);
            else if (!product.imageUrl.isEmpty()) loadProductImage(product.imageUrl, views.image);
            else views.image.setImageDrawable(Ui.photoPlaceholder(this));
        }
        // Every text/amount touch target explicitly routes taps to collection and long presses
        // to partial collection. This avoids child long-click listeners swallowing short taps.
        views.image.setOnClickListener(v -> openProductImage(card));
        View.OnClickListener collectClick=v -> toggleProduct(product.key);
        View.OnLongClickListener partialLongPress=v -> {
            ProductRow current=findProduct(product.key);
            if(current!=null&&remainingQuantity(current)>0.000001)
                showPartialCollectionDialog(product.key);
            return true;
        };
        views.image.setOnLongClickListener(partialLongPress);
        views.name.setOnClickListener(collectClick);
        views.name.setOnLongClickListener(partialLongPress);
        views.note.setOnClickListener(collectClick);
        views.note.setOnLongClickListener(partialLongPress);
        views.amount.setOnClickListener(collectClick);
        views.amount.setOnLongClickListener(partialLongPress);
        views.image.setFocusable(false);
        views.image.setFocusableInTouchMode(false);
        views.root.setOnClickListener(collectClick);
        views.root.setOnLongClickListener(partialLongPress);
        if (displayMode == 0) views.note.setVisibility(View.GONE);
        return card;
    }

    private void installImageFocusListener() {
        imageFocusListener = (oldFocus, newFocus) -> onImageFocusChanged(newFocus);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(imageFocusListener);
    }

    private void removeImageFocusListener() {
        if (imageFocusListener == null) return;
        View decor = getWindow().getDecorView();
        if (decor.getViewTreeObserver().isAlive())
            decor.getViewTreeObserver().removeOnGlobalFocusChangeListener(imageFocusListener);
        imageFocusListener = null;
    }

    private void onImageFocusChanged(View focused) {
        if (!imageMode) return;
        ProductCard next = findProductCard(focused);
        if (next == null) { setImageMode(false); return; }
        if (imageFocusCard != next) {
            if (imageFocusCard != null) Ui.setImageFocus(this, imageFocusCard.views, false);
            imageFocusCard = next;
        }
        Ui.setImageFocus(this, next.views, true);
    }

    private ProductCard findProductCard(View view) {
        View current = view;
        while (current != null) {
            Object tag = current.getTag();
            if (tag instanceof String) {
                String key=(String)tag;
                ProductCard card=key.startsWith("partial:")?partialCards.get(key.substring(8)):productCards.get(key);
                if(card!=null&&card.row==current)return card;
            }
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        return null;
    }

    private boolean imageIsOnRight(ProductCard card) {
        if (card == null || card.views.root.getWidth() <= 0 || card.views.image.getWidth() <= 0)
            return true;
        int[] root = new int[2], image = new int[2];
        card.views.root.getLocationOnScreen(root);
        card.views.image.getLocationOnScreen(image);
        return image[0] + card.views.image.getWidth() / 2f
                > root[0] + card.views.root.getWidth() / 2f;
    }

    private String imageHintText(boolean fallback) {
        ProductCard card = null;
        if (pendingList != null && pendingList.getChildCount() > 0)
            card = findProductCard(pendingList.getChildAt(0));
        if (card == null && collectedList != null && collectedList.getChildCount() > 0)
            card = findProductCard(collectedList.getChildAt(0));
        boolean right = card == null ? fallback : imageIsOnRight(card);
        return "לתמונה: " + (right ? "→" : "←") + "   הגדלה: OK / Enter   חזרה: " + (right ? "←" : "→");
    }

    private void updateImageModeHint() {
        if (imageModeHint != null) imageModeHint.setText(imageHintText(true));
    }

    private void setImageMode(boolean enabled) {
        imageMode = enabled;
        if (!enabled) {
            if (imageFocusCard != null) Ui.setImageFocus(this, imageFocusCard.views, false);
            imageFocusCard = null;
            return;
        }
        ProductCard focused = findProductCard(getCurrentFocus());
        if (focused == null) { imageMode = false; return; }
        imageFocusCard = focused;
        Ui.setImageFocus(this, focused.views, true);
    }

    private boolean handleImageModeKey(KeyEvent event) {
        if (displayMode == 0 || displayMode == 5 || event.getAction() != KeyEvent.ACTION_DOWN) {
            if (displayMode == 5 && imageMode) setImageMode(false);
            return false;
        }
        ProductCard focused = findProductCard(getCurrentFocus());
        int key = event.getKeyCode();
        if (!imageMode) {
            if (focused != null && key == (imageIsOnRight(focused)
                    ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT)) {
                setImageMode(true);
                return true;
            }
            return false;
        }
        if (focused == null) { setImageMode(false); return false; }
        int enter = imageIsOnRight(focused) ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int leave = imageIsOnRight(focused) ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        if (key == enter) return true;
        if (key == leave || key == KeyEvent.KEYCODE_BACK) { setImageMode(false); return true; }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            openProductImage(focused);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) return false;
        setImageMode(false);
        return false;
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
        File persistentImage = imageCacheFile(product.imageUrl);
        if (persistentImage != null && persistentImage.isFile() && persistentImage.length() > 0)
            return persistentImage;
        File root = new File(activeFilesDir(), "pickup_order_cache");
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
                File image = new File(imageFile);
                if (!image.isAbsolute()) image = new File(root, imageFile);
                return image.isFile() && image.length() > 0 ? image : null;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void bindProductCard(ProductCard card,ProductRow product,boolean done){bindProductCard(card,product,done,product.amount,false);}
    private void bindProductCard(ProductCard card,ProductRow product,boolean done,String shownAmount,boolean partial){
        card.product=product;String detail=product.note==null?"":product.note;
        if(partial)detail=(detail.isEmpty()?"":detail+" · ")+"נותרו "+shownAmount+" מתוך "+product.amount;
        Ui.bindProduct(card.views,product.name,detail,shownAmount,done);
        if(displayMode==0)card.views.note.setVisibility(View.GONE);
        if(imageMode&&findProductCard(getCurrentFocus())==card)Ui.setImageFocus(this,card.views,true);
    }
    private ProductCard createPartialProductCard(ProductRow product){
        Ui.CardViews views=Ui.productCard(this,displayMode);ProductCard card=new ProductCard(views,product);
        views.root.setFocusable(true);views.root.setFocusableInTouchMode(false);views.root.setTag("partial:"+product.key);
        views.root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if(displayMode!=0){if(!product.imagePath.isEmpty())loadLegacyProductImage(product,views.image);
            else if(!product.imageUrl.isEmpty())loadProductImage(product.imageUrl,views.image);else views.image.setImageDrawable(Ui.photoPlaceholder(this));}
        View.OnClickListener undo=v->applyPartialCollection(product.key,0);
        views.root.setOnClickListener(undo);views.name.setOnClickListener(undo);views.note.setOnClickListener(undo);views.amount.setOnClickListener(undo);
        View.OnLongClickListener edit=v->{showPartialCollectionDialog(product.key);return true;};
        views.root.setOnLongClickListener(edit);views.name.setOnLongClickListener(edit);views.note.setOnLongClickListener(edit);views.amount.setOnLongClickListener(edit);
        views.image.setOnClickListener(v->openProductImage(card));views.image.setOnLongClickListener(edit);views.image.setFocusable(false);
        return card;
    }
    private void bindPartialProductCard(ProductCard card,ProductRow product,double quantity){
        card.product=product;String detail=(product.note==null||product.note.isEmpty()?"":product.note+" · ")+"נאספו חלקית · הקשה להחזרה";
        Ui.bindProduct(card.views,product.name,detail,formatQuantity(quantity),true);
        card.views.name.setPaintFlags(card.views.name.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);
        card.views.name.setTextColor(Ui.INK);card.views.amount.setTextColor(Ui.P800);card.views.image.setAlpha(1f);    }
    private int partialInsertionIndex(ProductRow product){
        int index=0;for(ProductRow candidate:products){if(candidate.key.equals(product.key))break;
            if(matchesSearch(candidate)&&(collected.contains(candidate.key)||collectedQuantity(candidate)>0))index++;}
        return Math.min(index,collectedList.getChildCount());
    }

    private void loadCombinedSnapshot() {
        products.clear(); collected.clear(); partialCollected.clear(); combinedParts.clear();
        java.util.LinkedHashMap<String, java.util.List<SourcePart>> grouped = new java.util.LinkedHashMap<>();
        Set<String> orderNames = new java.util.LinkedHashSet<>();
        combinedPickupInfos.clear();
        currentPickupInfo = null;
        for (SourceRegistry.Source source : sourceRegistry.all()) {
            File root = new File(sourceRegistry.sourceDir(source.id), "pickup_order_cache");
            File manifest = new File(root, "order.json");
            if (!manifest.isFile()) continue;
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = new FileInputStream(manifest)) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n=in.read(buffer))!=-1) bytes.write(buffer,0,n);
                }
                JSONObject snapshot = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                String orderId = String.valueOf(snapshot.optLong("order_id", -1));
                JSONArray rows = snapshot.optJSONArray("products");
                if (rows == null || "-1".equals(orderId)) continue;
                String savedName = snapshot.optString("order_name", "").trim();
                if (!savedName.isEmpty()) orderNames.add(savedName);
                JSONObject savedInfo = snapshot.optJSONObject("pickup_info");
                if (savedInfo != null) {
                    // שם החשבון מצורף כדי להבחין בין נקודות איסוף של חשבונות שונים.
                    JSONObject labeled = new JSONObject(savedInfo.toString());
                    labeled.put("_source_label", source.label == null ? "" : source.label);
                    combinedPickupInfos.add(labeled);
                }
                Set<String> ticks = new HashSet<>();
                JSONObject partials = new JSONObject();
                try {
                    SharedPreferences sourcePrefs=sourceRegistry.prefs(source.id);
                    JSONArray saved = new JSONArray(sourcePrefs.getString(DataGuard.COLLECTED_PREFIX + orderId, "[]"));
                    for (int i=0;i<saved.length();i++) ticks.add(saved.optString(i,""));
                    partials = new JSONObject(sourcePrefs.getString("partial_" + orderId, "{}"));
                } catch (Exception ignored) { }
                for (int i=0;i<rows.length();i++) {
                    JSONObject o=rows.optJSONObject(i); if(o==null) continue;
                    String imageFile=first(o,"image_file");
                    String imagePath="";
                    if(!imageFile.isEmpty()) {
                        File image=new File(imageFile);if(!image.isAbsolute())image=new File(root,imageFile);
                        if(image.isFile()&&image.length()>0) imagePath=image.getAbsolutePath();
                    }
                    ProductRow original=new ProductRow(first(o,"key"),first(o,"name"),
                            first(o,"image_url"),first(o,"amount"),first(o,"note"),imagePath,first(o,"category"),o.optInt("category_order",0));
                    if(original.key.isEmpty()||original.name.isEmpty())continue;
                    boolean done=ticks.contains(DataGuard.tick(original));
                    double partial=done?parseQuantity(original.amount):Math.max(0,partials.optDouble(original.key,0));
                    SourcePart part=new SourcePart(source.id,orderId,original,done,partial,source.label);
                    String displayKey="COMBINED_SUM".equals(sourceRegistry.viewMode())
                            ? original.key : source.id+"__"+original.key;
                    java.util.List<SourcePart> list=grouped.get(displayKey);
                    if(list==null){list=new ArrayList<>();grouped.put(displayKey,list);}
                    list.add(part);
                }
            } catch(Exception ignored) { }
        }
        for (java.util.Map.Entry<String, java.util.List<SourcePart>> entry : grouped.entrySet()) {
            java.util.List<SourcePart> parts=entry.getValue();
            if(parts.isEmpty())continue;
            SourcePart first=parts.get(0);
            String amount;
            String note=combinedNote(parts);
            if("COMBINED_SUM".equals(sourceRegistry.viewMode())) {
                double total=0;
                for(SourcePart part:parts) total+=parseQuantity(part.product.amount);
                amount=formatQuantity(total);
            } else amount=first.product.amount;
            ProductRow base=first.product;
            products.add(new ProductRow(entry.getKey(),base.name,base.imageUrl,amount,note,base.imagePath,base.category,base.categoryOrder));
            combinedParts.put(entry.getKey(),parts);
            boolean all=true;double collectedAmount=0;
            for(SourcePart part:parts){double q=parseQuantity(part.product.amount);
                if(!part.collected)all=false;
                collectedAmount+=part.collected?q:Math.min(q,part.partialQuantity);}
            if(all)collected.add(entry.getKey());
            else if(collectedAmount>0)partialCollected.put(entry.getKey(),collectedAmount);        }
        // מיון לפי מחלקות, לפי סדר המחלקות של המכירה הראשונה שיש לה מידע.
        sortByCategory(products, combinedPickupInfos.isEmpty() ? null : combinedPickupInfos.get(0));
        // כשכל החשבונות מציגים את אותה מכירה, שמה מוצג בכותרת; אחרת כל השמות יחד.
        setCurrentOrderTitle(orderNames.size() <= 2 ? android.text.TextUtils.join(" · ", orderNames) : "");
        syncCompletionState();
        if(products.isEmpty()) showSyncMessage("אין רשימות שמורות להצגה בתצוגה המאוחדת.", false);
        else showSyncMessage("", false);
    }

    private String combinedNote(java.util.List<SourcePart> parts) {
        if(parts==null||parts.isEmpty())return "";
        boolean any=false,all=true;
        StringBuilder detail=new StringBuilder();
        for(SourcePart part:parts) {
            any|=part.collected || part.partialQuantity>0; all&=part.collected;
            if(detail.length()>0)detail.append(" · ");
            detail.append(part.sourceLabel).append(" ").append(part.product.amount);
            if(part.product.note!=null&&!part.product.note.isEmpty())detail.append(" (").append(part.product.note).append(")");
        }
        if(any&&!all)return "נאסף חלקית · "+detail;
        return detail.toString();
    }

    private void updateCombinedParts(String key, boolean done) {
        java.util.List<SourcePart> parts=combinedParts.get(key);
        if(parts==null)return;
        for(SourcePart part:parts){part.collected=done;part.partialQuantity=done?parseQuantity(part.product.amount):0;}
        partialCollected.remove(key);
        final java.util.List<SourcePart> copy=new ArrayList<>(parts);
        cacheWorker.execute(() -> {
            for(SourcePart part:copy) {
                if(sourceRegistry.find(part.sourceId)==null)continue;
                SharedPreferences prefs=sourceRegistry.prefs(part.sourceId);
                Set<String> values=new HashSet<>();JSONObject partials=new JSONObject();
                try {
                    JSONArray a=new JSONArray(prefs.getString(DataGuard.COLLECTED_PREFIX+part.orderId,"[]"));
                    for(int i=0;i<a.length();i++)values.add(a.optString(i,""));
                    partials=new JSONObject(prefs.getString("partial_"+part.orderId,"{}"));
                } catch(Exception ignored) { }
                String tick=DataGuard.tick(part.product);
                if(done){values.add(tick);partials.remove(part.product.key);}
                else{values.remove(tick);partials.remove(part.product.key);}
                JSONArray saved=new JSONArray();for(String value:values)saved.put(value);
                prefs.edit().putString(DataGuard.COLLECTED_PREFIX+part.orderId,saved.toString())
                        .putString("partial_"+part.orderId,partials.toString()).commit();
                archiveCurrentSnapshot(part.sourceId);
            }
        });
    }

    private void updateCombinedPartial(String key,double quantity) {
        java.util.List<SourcePart> parts=combinedParts.get(key);if(parts==null)return;
        double remaining=quantity,total=0;
        for(SourcePart part:parts){double partTotal=parseQuantity(part.product.amount);total+=partTotal;
            part.partialQuantity=Math.min(partTotal,Math.max(0,remaining));remaining-=part.partialQuantity;
            part.collected=part.partialQuantity>=partTotal-0.000001;}
        if(quantity>=total-0.000001){collected.add(key);partialCollected.remove(key);}
        else{collected.remove(key);if(quantity>0)partialCollected.put(key,quantity);else partialCollected.remove(key);}
        final java.util.List<SourcePart> copy=new ArrayList<>(parts);
        cacheWorker.execute(()->{
            for(SourcePart part:copy){if(sourceRegistry.find(part.sourceId)==null)continue;
                SharedPreferences prefs=sourceRegistry.prefs(part.sourceId);Set<String> ticks=new HashSet<>();JSONObject partials=new JSONObject();
                try{JSONArray a=new JSONArray(prefs.getString(DataGuard.COLLECTED_PREFIX+part.orderId,"[]"));
                    for(int i=0;i<a.length();i++)ticks.add(a.optString(i,""));
                    partials=new JSONObject(prefs.getString("partial_"+part.orderId,"{}"));
                }catch(Exception ignored){}
                String tick=DataGuard.tick(part.product);
                if(part.collected){ticks.add(tick);partials.remove(part.product.key);}
                else{ticks.remove(tick);if(part.partialQuantity>0)try{partials.put(part.product.key,part.partialQuantity);}catch(Exception ignored){}
                    else partials.remove(part.product.key);}
                JSONArray saved=new JSONArray();for(String value:ticks)saved.put(value);
                prefs.edit().putString(DataGuard.COLLECTED_PREFIX+part.orderId,saved.toString())
                        .putString("partial_"+part.orderId,partials.toString()).commit();
                archiveCurrentSnapshot(part.sourceId);
            }
        });
    }

    private double parseQuantity(String value) {
        try { return Math.max(0,Double.parseDouble(value.replace(',','.'))); }
        catch(Exception ignored) { return 0; }
    }

    private String formatQuantity(double value) {
        if(Math.abs(value-Math.rint(value))<0.000001)return String.valueOf((long)Math.rint(value));
        String s=String.format(Locale.US,"%.3f",value);
        while(s.contains(".")&&s.endsWith("0"))s=s.substring(0,s.length()-1);
        if(s.endsWith("."))s=s.substring(0,s.length()-1);
        return s;
    }

    private void toggleProduct(String key) {
        if (sourceMergeInProgress) return;
        ProductCard moving = productCards.get(key);
        if (moving == null) return;
        boolean wasCollected = collected.contains(key);
        ViewGroup source = wasCollected ? collectedList : pendingList;
        ProductRow product = findProduct(key);
        if (product == null) return;
        int oldIndex = source.indexOfChild(moving.row);
        boolean hadKeyboardFocus = moving.row.hasFocus();
        View neighbor = null;
        if (oldIndex >= 0) {
            if (oldIndex + 1 < source.getChildCount()) neighbor = source.getChildAt(oldIndex + 1);
            else if (oldIndex > 0) neighbor = source.getChildAt(oldIndex - 1);
        }
        if (hadKeyboardFocus && neighbor != null) neighbor.requestFocus();
        if (wasCollected) { collected.remove(key); partialCollected.remove(key); }
        else { collected.add(key); partialCollected.remove(key); }
        ProductCard stalePartial=partialCards.get(key);
        if(stalePartial!=null&&stalePartial.row.getParent() instanceof ViewGroup)
            ((ViewGroup)stalePartial.row.getParent()).removeView(stalePartial.row);
        if (combinedView) {
            updateCombinedParts(key, !wasCollected);
            moving.views.note.setText(combinedNote(combinedParts.get(key)));
            moving.views.note.setVisibility(moving.views.note.getText().length() == 0 ? View.GONE : View.VISIBLE);
        } else saveCollectedState();
        ViewGroup target = wasCollected ? pendingList : collectedList;
        ViewGroup parent = (ViewGroup) moving.row.getParent();
        if (parent != null) parent.removeView(moving.row);
        bindProductCard(moving, product, !wasCollected);
        int targetIndex = insertionIndex(target, product, !wasCollected);
        target.addView(moving.row, targetIndex, productLayoutParams(target, targetIndex));
        // GridLayout children keep their old explicit row/column specs when a card is
        // moved. Reflow BOTH sections after every full collection/undo so no holes remain.
        reflowGridListsInProductOrder();
        updateProgress();
        wireFocusOrder();
        if (hadKeyboardFocus && neighbor == null) moving.row.requestFocus();
        performFeedback(false, !wasCollected);
        checkCelebration();
    }

    private boolean sameProduct(ProductRow a, ProductRow b) {
        return a != null && b != null && a.key.equals(b.key) && a.name.equals(b.name)
                && a.imageUrl.equals(b.imageUrl) && a.amount.equals(b.amount) && a.note.equals(b.note)
                && a.imagePath.equals(b.imagePath) && a.category.equals(b.category);
    }

    private ProductRow findProduct(String key) {
        for (ProductRow p : products) if (p.key.equals(key)) return p;
        return null;
    }

    private void updateProgress() {
        double total = 0, done = 0;
        for (ProductRow product : products) { double q=parseQuantity(product.amount); total += q; done += Math.min(q,collectedQuantity(product)); }
        int percent = total <= 0 ? 0 : (int) Math.round(done * 100.0 / total);
        if (progressText != null) progressText.setText("נאספו " + formatQuantity(done) + " מתוך " + formatQuantity(total) + " יחידות");
        if (progressPercent != null) progressPercent.setText(percent + "%");
        if (progressBar != null) progressBar.setProgress(percent);
    }

    private void wireFocusOrder() {
        if (pendingList == null || collectedList == null) return;
        View first = firstCard();
        int firstId = first == null ? View.NO_ID : first.getId();
        // סדר הניווט במקשים: כפתורי הכותרת ← שדה החיפוש ← הפריט הראשון, ולהפך.
        // (מקש 3 ממשיך לפתוח את החיפוש ישירות.)
        int searchId = searchInput == null ? firstId : searchInput.getId();
        for (View button : headerButtons) button.setNextFocusDownId(searchId);
        if (searchInput != null) {
            searchInput.setNextFocusDownId(firstId);
            View top = headerButtons.isEmpty() ? null : headerButtons.get(0);
            if (top != null) searchInput.setNextFocusUpId(top.getId());
        }
        if (retryButton != null && retryButton.getVisibility() == View.VISIBLE) retryButton.setNextFocusDownId(firstId);
        if (first != null) first.setNextFocusUpId(searchId);
    }

    private View firstCard() {
        if (pendingList != null && pendingList.getChildCount() > 0) return pendingList.getChildAt(0);
        if (collectedList != null && collectedList.getChildCount() > 0) return collectedList.getChildAt(0);
        return null;
    }

    private void focusFirstPending() {
        View first = firstCard();
        if (first != null) first.requestFocus();
        else if (!headerButtons.isEmpty()) headerButtons.get(0).requestFocus();
    }

    private void focusLastCard() {
        if (collectedList != null && collectedList.getChildCount() > 0)
            collectedList.getChildAt(collectedList.getChildCount() - 1).requestFocus();
        else if (pendingList != null && pendingList.getChildCount() > 0)
            pendingList.getChildAt(pendingList.getChildCount() - 1).requestFocus();
    }

    private void loadLegacyProductImage(ProductRow product, ImageView target) {
        final String imageUrl = product.imageUrl == null ? "" : product.imageUrl;
        final String viewTag = imageUrl.isEmpty() ? "legacy:" + product.imagePath : imageUrl;
        // A URL-keyed cache entry always wins over the old per-order image file.
        // Previously the legacy branch decoded that file again on every screen rebuild.
        if (!imageUrl.isEmpty()) {
            Bitmap memory = imageCache.get(imageUrl);
            if (memory != null) {
                target.setTag(imageUrl);
                target.setImageBitmap(memory);
                return;
            }
            File persistent = imageCacheFile(imageUrl);
            if (persistent != null && persistent.isFile() && persistent.length() > 0) {
                loadProductImage(imageUrl, target);
                return;
            }
        }
        target.setImageDrawable(Ui.photoPlaceholder(this));
        target.setTag(viewTag);
        imageLoader.execute(() -> {
            File source = new File(product.imagePath);
            Bitmap bitmap = decodeProductImage(source);
            if (bitmap == null) {
                if (!imageUrl.isEmpty()) mainHandler.post(() -> {
                    if (viewTag.equals(target.getTag())) loadProductImage(imageUrl, target);
                });
                return;
            }
            if (!imageUrl.isEmpty()) {
                File cached = imageCacheFile(imageUrl);
                if (cached != null && !cached.isFile()) {
                    File dir = cached.getParentFile();
                    File temp = new File(dir, cached.getName() + ".tmp");
                    try {
                        if (!dir.exists()) dir.mkdirs();
                        try (FileInputStream in = new FileInputStream(source);
                             FileOutputStream out = new FileOutputStream(temp)) {
                            byte[] buffer = new byte[8192]; int count; long total = 0;
                            while ((count = in.read(buffer)) != -1) {
                                total += count;
                                if (total > 8L * 1024L * 1024L) throw new Exception("image too large");
                                out.write(buffer, 0, count);
                            }
                            out.getFD().sync();
                        }
                        if (temp.renameTo(cached)) imageCache.put(imageUrl, bitmap);
                        else temp.delete();
                    } catch (Exception ignored) { temp.delete(); }
                } else imageCache.put(imageUrl, bitmap);
            }
            mainHandler.post(() -> {
                if (viewTag.equals(target.getTag())) target.setImageBitmap(bitmap);
            });
        });
    }

    private File imageCacheFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        File dir = new File(getFilesDir(), "product_image_cache");
        return new File(dir, DataGuard.sha256(imageUrl) + ".img");
    }

    private Bitmap decodeProductImage(File file) {
        if (file == null || !file.isFile() || file.length() <= 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int targetPixels = Math.max(120, Ui.imageSize(this));
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetPixels
                && bounds.outHeight / (sample * 2) >= targetPixels) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void loadProductImage(String imageUrl, ImageView target) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            target.setImageDrawable(Ui.photoPlaceholder(this));
            return;
        }
        target.setTag(imageUrl);
        Bitmap cached = imageCache.get(imageUrl);
        if (cached != null) { target.setImageBitmap(cached); return; }
        target.setImageDrawable(Ui.photoPlaceholder(this));

        // Deduplicate both disk decoding and network downloads by URL. Repeated
        // renders attach their ImageView to the same in-flight cache operation.
        synchronized (imageTargetsLock) {
            List<WeakReference<ImageView>> targets = imageTargets.get(imageUrl);
            if (targets == null) { targets = new ArrayList<>(); imageTargets.put(imageUrl, targets); }
            boolean alreadyAttached = false;
            for (WeakReference<ImageView> reference : targets) {
                if (reference.get() == target) { alreadyAttached = true; break; }
            }
            if (!alreadyAttached) targets.add(new WeakReference<>(target));
            if (!imageDownloads.add(imageUrl)) return;
        }

        imageLoader.execute(() -> {
            HttpURLConnection connection = null;
            Bitmap bitmap = null;
            File temp = null;
            File diskFile = imageCacheFile(imageUrl);
            try {
                // First try the permanent URL cache. Only a missing or corrupt entry
                // is allowed to trigger a network request.
                if (diskFile != null && diskFile.isFile() && diskFile.length() > 0) {
                    bitmap = decodeProductImage(diskFile);
                    if (bitmap == null) diskFile.delete();
                }
                if (bitmap == null) {
                    File dir = diskFile.getParentFile();
                    if (!dir.exists() && !dir.mkdirs())
                        throw new Exception("image cache directory unavailable");
                    temp = new File(dir, diskFile.getName() + ".tmp");
                    connection = openConnection(new URL(imageUrl));
                    connection.setConnectTimeout(7000);
                    connection.setReadTimeout(9000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setInstanceFollowRedirects(true);
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) throw new Exception("image HTTP " + code);
                    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                        byte[] buffer = new byte[8192]; int read; long total = 0;
                        while ((read = in.read(buffer)) != -1) {
                            total += read;
                            if (total > 6L * 1024L * 1024L) throw new Exception("image too large");
                            out.write(buffer, 0, read);
                        }
                        out.getFD().sync();
                    }
                    bitmap = decodeProductImage(temp);
                    if (bitmap == null) throw new Exception("invalid image");
                    if (diskFile.exists()) diskFile.delete();
                    if (!temp.renameTo(diskFile)) throw new Exception("cannot publish image cache");
                    temp = null;
                }
                imageCache.put(imageUrl, bitmap);
            } catch (Exception ignored) {
                if (bitmap != null && !imageCache.snapshot().containsKey(imageUrl)) bitmap.recycle();
                if (temp != null) temp.delete();
                bitmap = null;
            } finally {
                if (connection != null) connection.disconnect();
                List<WeakReference<ImageView>> targets;
                synchronized (imageTargetsLock) {
                    imageDownloads.remove(imageUrl);
                    targets = imageTargets.remove(imageUrl);
                }
                final Bitmap result = bitmap;
                if (result != null && targets != null) mainHandler.post(() -> {
                    for (WeakReference<ImageView> reference : targets) {
                        ImageView view = reference.get();
                        if (view != null && imageUrl.equals(view.getTag())) view.setImageBitmap(result);
                    }
                });
            }
        });
    }
    private void logout() {
        if (sourceMergeInProgress) { showSyncMessage("החשבון מתאחד עם מקור קיים. המתינו רגע.", false); return; }
        if (syncAllRunning) { showSyncMessage("המתינו לסיום הסנכרון לפני התנתקות.", false); return; }
        setImageMode(false);
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
        if (sourceRegistry != null && !activeSourceId.isEmpty()) sourceRegistry.markNeedsLogin(activeSourceId);
        activeOwner = preserveOfflineList ? trustedOwner : "";
        cacheTrusted = preserveOfflineList;
        preferences.edit().remove(PREF_OFFLINE_CACHE_AFTER_AUTH_EXPIRY).commit();
        clearOrderMemory();
        mainHandler.post(() -> {
            if (generation != authGeneration || isFinishing()) return;
            // If another token is still connected, continue with it automatically.
            // No connection is preferred; this is only the internal request context.
            String nextTokenSource = firstConnectedAccountId();
            if (!nextTokenSource.isEmpty()) {
                switchSource(nextTokenSource, () -> {
                    showHomeScreen(false);
                    syncAllAccounts();
                });
            } else showLoginScreen("");
        });
    }

    private void clearOrderMemory() {
        dismissCelebration();
        wasComplete = false;
        products.clear();
        collected.clear();
        partialCollected.clear();
        productCards.clear();partialCards.clear();
        orderData = null;
        currentOrderId = "";
        setCurrentOrderTitle("");
        currentPickupInfo = null;
        combinedPickupInfos.clear();
        // Keep the URL-keyed bitmap cache across account switches and list rebuilds.
        // Evicting it here caused every already-loaded product image to be decoded again.
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
            return array;        }
        throw new Exception("השרת לא החזיר רשימת הזמנות תקינה.");
    }

    private JSONObject apiGetObject(String path, RequestSession session) throws Exception {
        HttpResult response = authorizedRequest(session, "GET", path, null);
        ensureSuccess(response);
        Object parsed = new JSONTokener(response.body).nextValue();
        if (!(parsed instanceof JSONObject)) throw new Exception("פרטי ההזמנה חזרו במבנה לא תקין.");
        return (JSONObject) parsed;
    }

    private void fetchAccountProfileName(String sourceId, String token) {
        if (sourceId == null || sourceId.isEmpty() || token == null || token.isEmpty()) return;
        SharedPreferences sourcePrefs = sourceRegistry.prefs(sourceId);
        if (sourcePrefs.getBoolean("profile_lname_loaded_v2", false)) return;
        profileLoader.execute(() -> {
            try {
                HttpResult response = rawRequest("GET", "/api/profile", token, null);
                if (response.code < 200 || response.code >= 300) return;
                JSONObject profileResponse = new JSONObject(response.body);
                JSONObject profile = profileResponse.optJSONObject("data");
                if (profile == null) profile = profileResponse.optJSONObject("profile");
                if (profile == null) profile = profileResponse;
                JSONObject nested = profile.optJSONObject("profile");
                if (nested != null) profile = nested;
                String label = profileDisplayName(profile);
                if (sourceRegistry.find(sourceId) == null) return;
                sourceRegistry.rename(sourceId, label.isEmpty() ? "חיבור ללא שם" : label);
                sourcePrefs.edit().putBoolean("profile_lname_loaded_v2", true).apply();
            } catch (Exception ignored) {
                // Profile is supplementary metadata. Never fail or delay product loading.
            }
        });
    }

    private String profileDisplayName(JSONObject profile) {
        if (profile == null) return "";
        return first(profile, "lname").trim();
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

    private HttpURLConnection openConnection(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (Build.VERSION.SDK_INT < 21 && connection instanceof javax.net.ssl.HttpsURLConnection) {
            javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
            context.init(null, null, null);
            ((javax.net.ssl.HttpsURLConnection) connection).setSSLSocketFactory(
                    new Tls12SocketFactory(context.getSocketFactory()));
        }
        return connection;
    }

    private HttpResult rawRequest(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = openConnection(new URL(API + path));
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
            if (OrderHistoryPage.isCancelled(order)) continue;
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
            // The catalog product ID is stable across orders and accounts. Line IDs
            // (item_salesID / order-item id) are not suitable for cross-account aggregation.
            String catalogProductId = first(saleProduct, "id", "product_id", "productID");
            if (catalogProductId.isEmpty()) {
                JSONObject itemSale = item == null ? null : item.optJSONObject("item_sale");
                catalogProductId = first(itemSale, "product_id", "productID");
            }
            String lineId = first(row, "item_salesID", "item_sales_id", "itemID", "id");
            if (lineId.isEmpty() && data != null) lineId = first(data, "item_salesID", "item_sales_id", "itemID", "id");
            String key = !catalogProductId.isEmpty() ? catalogProductId
                    : (lineId.isEmpty() ? "line" : lineId) + "_" + i;
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
            // מבנה השרת: items[].item_sale.product.category = {name, order, main_category}.
            String category = findCategory(saleProduct, data, row,
                    item == null ? null : item.optJSONObject("item_sale"), item);
            JSONObject categoryObject = saleProduct == null ? null : saleProduct.optJSONObject("category");
            int categoryOrder = categoryObject == null ? 0 : categoryObject.optInt("order", 0);
            result.add(new ProductRow(key, name, image, amount, note, "", category, categoryOrder));
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
        if (activeSourceId.isEmpty()) throw new Exception("לא נבחר מקור לחשבון.");
        secretStore.put(activeSourceId, token);
        preferences.edit().remove(TOKEN_KEY).apply();
    }

    private String readEncryptedToken() {
        if (activeSourceId.isEmpty()) return "";
        String token = secretStore.get(activeSourceId);
        if (token != null && !token.isEmpty()) return token;
        String stored = preferences.getString(TOKEN_KEY, "");
        if (stored.isEmpty() || Build.VERSION.SDK_INT < 23) return "";
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) throw new Exception("Invalid legacy token storage");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            token = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            secretStore.put(activeSourceId, token);
            preferences.edit().remove(TOKEN_KEY).commit();
            return token;
        } catch (Exception e) { return ""; }
    }

    @android.annotation.TargetApi(23)
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
        if (secretStore != null && !activeSourceId.isEmpty()) secretStore.delete(activeSourceId);
        preferences.edit().remove(TOKEN_KEY).apply();
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
        return message;    }

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

    private static final class SourcePart {
        final String sourceId, orderId, sourceLabel;
        final ProductRow product;
        boolean collected;
        double partialQuantity;
        SourcePart(String sourceId,String orderId,ProductRow product,boolean collected,double partialQuantity,String sourceLabel) {
            this.sourceId=sourceId;this.orderId=orderId;this.product=product;
            this.collected=collected;this.partialQuantity=partialQuantity;
            this.sourceLabel=sourceLabel==null?"חשבון":sourceLabel;
        }
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
        /** מחלקת המוצר (למשל "ירקות"), לצורך מיון הרשימה. אינה חלק מטביעת התוכן. */
        final String category;
        /** סדר המחלקה באתר (category.order); 0 כשאינו ידוע. */
        final int categoryOrder;
        ProductRow(String key, String name, String imageUrl, String amount, String note) {
            this(key, name, imageUrl, amount, note, "", "");
        }
        ProductRow(String key, String name, String imageUrl, String amount, String note, String imagePath) {
            this(key, name, imageUrl, amount, note, imagePath, "");
        }
        ProductRow(String key, String name, String imageUrl, String amount, String note, String imagePath,
                   String category) {
            this(key, name, imageUrl, amount, note, imagePath, category, 0);
        }
        ProductRow(String key, String name, String imageUrl, String amount, String note, String imagePath,
                   String category, int categoryOrder) {
            this.key = key; this.name = name; this.imageUrl = imageUrl; this.amount = amount;
            this.note = note; this.imagePath = imagePath == null ? "" : imagePath;
            this.category = category == null ? "" : category.trim();
            this.categoryOrder = Math.max(0, categoryOrder);
        }
    }

    // ---------------------------------------------------------------------
    // מיון לפי מחלקות
    // ---------------------------------------------------------------------

    private static final String[] CATEGORY_KEYS = {"category", "category_name", "categoryName", "cat_name",
            "catName", "category_title", "department", "department_name", "dept", "group_name"};

    /** מחלקת מוצר מתוך נתוני השורה, הפריט והמוצר. בודק גם שדות מקוננים (למשל category.name). */
    private static String findCategory(JSONObject... sources) {
        for (JSONObject source : sources) {
            String found = findCategory(source, 0);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    private static String findCategory(JSONObject source, int depth) {
        if (source == null || depth > 2) return "";
        for (String key : CATEGORY_KEYS) {
            Object value = source.opt(key);
            if (value instanceof JSONObject) {
                String nested = first((JSONObject) value, "name", "title", "category_name", "week");
                if (!nested.isEmpty()) return nested;
            } else if (value instanceof String) {
                String text = ((String) value).trim();
                // מזהה מספרי אינו שם מחלקה.
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text) && !text.matches("\\d+")) return text;
            }
        }
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            Object value = source.opt(keys.next());
            if (value instanceof JSONObject) {
                String nested = findCategory((JSONObject) value, depth + 1);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    /**
     * ממיין לפי מחלקות. סדר המחלקות לפי category.order שמגיע מהשרת; כשאין, לפי רשימת המחלקות
     * של המכירה (sale.categories), ואחרת לפי א״ב. מוצרים בלי מחלקה בסוף. בתוך מחלקה נשמר הסדר המקורי.
     */
    private static void sortByCategory(List<ProductRow> rows, JSONObject pickupInfo) {
        if (rows == null || rows.size() < 2) return;
        final List<String> order = new ArrayList<>();
        JSONObject sale = pickupInfo == null ? null : pickupInfo.optJSONObject("sale");
        JSONArray listed = sale == null ? null : sale.optJSONArray("categories");
        if (listed != null) for (int i = 0; i < listed.length(); i++) order.add(listed.optString(i, "").trim());
        final java.text.Collator collator = java.text.Collator.getInstance(new Locale("he"));
        Collections.sort(rows, (a, b) -> {
            boolean aEmpty = a.category.isEmpty(), bEmpty = b.category.isEmpty();
            if (aEmpty || bEmpty) return aEmpty == bEmpty ? 0 : (aEmpty ? 1 : -1);
            // סדר המחלקות כפי שהוא באתר משנת יוסף (category.order).
            if (a.categoryOrder > 0 && b.categoryOrder > 0 && a.categoryOrder != b.categoryOrder)
                return a.categoryOrder < b.categoryOrder ? -1 : 1;
            if (a.category.equals(b.category)) return 0;
            int ra = order.indexOf(a.category), rb = order.indexOf(b.category);
            if (ra < 0) ra = Integer.MAX_VALUE;
            if (rb < 0) rb = Integer.MAX_VALUE;
            if (ra != rb) return ra < rb ? -1 : 1;
            return ra == Integer.MAX_VALUE ? collator.compare(a.category, b.category) : 0;
        });
    }
}
