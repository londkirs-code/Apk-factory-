package org.mishnatyosef.pickup;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * הצגת רשימות כקודי QR בטלפון, לסריקה בטלפון אחר (בלי אינטרנט). אותו פורמט כמו בתוסף.
 * כמה קודים: "הקודם/הבא", או החלפה אוטומטית - הסורק אוסף אותם בכל סדר.
 * מקשים: 4/6 וחצים - מעבר, אישור או 5 - החלפה אוטומטית, חזרה - סגירה.
 * המסך נשאר דלוק ובבהירות מלאה כל עוד הקוד מוצג.
 */
final class QrDisplay {
    private static final long AUTO_MS = 2500;

    private final Activity activity;
    private final List<String> chunks;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Dialog dialog;
    private ImageView image;
    private TextView counter;
    private Button prev, next, auto;
    private int index;
    private boolean autoPlay;
    private Bitmap current;

    QrDisplay(Activity activity, List<String> chunks) {
        this.activity = activity;
        this.chunks = chunks;
        this.autoPlay = chunks.size() > 1; // כמה קודים: החלפה אוטומטית מההתחלה
    }

    void show() {
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE); // תמיד לבן: הכי קל לסריקה, גם במצב לילה
        int pad = Ui.dp(activity, Ui.S3);
        root.setPadding(pad, pad, pad, pad);

        counter = Ui.text(activity, "", Ui.T_NAME, 0xFF2C1A4D, true);
        counter.setGravity(Gravity.CENTER);
        root.addView(counter, new LinearLayout.LayoutParams(-1, -2));

        image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("קוד QR של הרשימה");
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        imageLp.setMargins(0, pad, 0, pad);
        root.addView(image, imageLp);

        TextView help = Ui.text(activity, "סורקים בטלפון השני: \"רשימה חדשה\" ← \"סריקת קוד QR\". אפשר לסרוק בכל סדר.",
                Ui.T_NOTE, 0xFF4B4459, false);
        help.setGravity(Gravity.CENTER);
        root.addView(help, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        prev = button("→ הקודם", v -> { stopAuto(); move(-1); });
        auto = button("", v -> toggleAuto());
        next = button("הבא ←", v -> { stopAuto(); move(1); });
        Button close = button("סגירה", v -> dialog.dismiss());
        for (Button b : new Button[]{prev, auto, next}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(Ui.dp(activity, Ui.S1), pad, Ui.dp(activity, Ui.S1), 0);
            buttons.addView(b, lp);
        }
        if (chunks.size() > 1) root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, -2);
        closeLp.setMargins(Ui.dp(activity, Ui.S1), pad, Ui.dp(activity, Ui.S1), 0);
        root.addView(close, closeLp);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
            attrs.screenBrightness = 1f; // בהירות מלאה לסריקה
            dialog.getWindow().setAttributes(attrs);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        dialog.setOnKeyListener((d, keyCode, event) -> onKey(keyCode, event));
        dialog.setOnDismissListener(d -> {
            main.removeCallbacks(tick);
            image.setImageDrawable(null);
            if (current != null) current.recycle();
            current = null;
        });
        dialog.show();
        image.post(this::render);
        if (autoPlay) main.postDelayed(tick, AUTO_MS);
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(Ui.T_BUTTON * Ui.textScale(activity));
        b.setTextColor(Color.WHITE);
        b.setBackground(Ui.shape(activity, 0xFF56348F, 12, 0, 0));
        b.setFocusable(false);
        b.setOnClickListener(click);
        return b;
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_DPAD_LEFT: stopAuto(); move(1); return true;
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_DPAD_RIGHT: stopAuto(); move(-1); return true;
            case KeyEvent.KEYCODE_5: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) toggleAuto();
                return true;
            default: return true;
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!autoPlay || dialog == null || !dialog.isShowing()) return;
            index = (index + 1) % chunks.size();
            render();
            main.postDelayed(this, AUTO_MS);
        }
    };

    private void toggleAuto() {
        if (chunks.size() < 2) return;
        autoPlay = !autoPlay;
        main.removeCallbacks(tick);
        if (autoPlay) main.postDelayed(tick, AUTO_MS);
        updateButtons();
    }

    private void stopAuto() {
        autoPlay = false;
        main.removeCallbacks(tick);
    }

    private void move(int delta) {
        int target = index + delta;
        if (target < 0 || target >= chunks.size()) { updateButtons(); return; }
        index = target;
        render();
    }

    private void updateButtons() {
        counter.setText(chunks.size() > 1 ? "קוד " + (index + 1) + " מתוך " + chunks.size() : "קוד QR");
        prev.setEnabled(index > 0);
        next.setEnabled(index < chunks.size() - 1);
        auto.setText(autoPlay ? "⏸ עצירה" : "▶ אוטומטי");
    }

    private void render() {
        int size = Math.max(200, Math.min(image.getWidth(), image.getHeight()));
        if (size <= 200) size = Math.min(activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels) * 4 / 5;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new QRCodeWriter().encode(chunks.get(index), BarcodeFormat.QR_CODE, size, size, hints);
            int w = matrix.getWidth(), h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) pixels[y * w + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
            image.setImageBitmap(bitmap);
            if (current != null) current.recycle();
            current = bitmap;
        } catch (Exception e) {
            counter.setText("יצירת הקוד נכשלה");
        }
        updateButtons();
    }
}
