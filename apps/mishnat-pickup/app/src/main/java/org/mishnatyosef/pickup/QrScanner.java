package org.mishnatyosef.pickup;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * סורק QR בתוך האפליקציה, בלי אינטרנט: מצלמה (Camera API הישן, שעובד מאנדרואיד 4.4) ופענוח עם ZXing.
 * קוד שמתחיל ב-"#משנת אוסף 2/5" הוא חלק מסדרה: נאספים כל החלקים (בכל סדר), ואז הטקסט המלא
 * עובר לייבוא הרגיל. קוד אחר (טקסט או JSON) עובר מיד.
 * מקשים: חזרה - ביטול, אישור או 5 - סיום עם מה שנסרק עד עכשיו.
 */
final class QrScanner implements SurfaceHolder.Callback, Camera.PreviewCallback {
    interface Listener { void onScanned(String text); }

    private static final Pattern PART = Pattern.compile("^#משנת אוסף (\\d+)/(\\d+)\\s*\\n?");

    private final Activity activity;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final QRCodeReader reader = new QRCodeReader();
    private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    private final TreeMap<Integer, String> parts = new TreeMap<>();
    private int total = 0;

    private Dialog dialog;
    private Camera camera;
    private int previewWidth, previewHeight;
    private boolean decoding, closed, autoFocusLoop;
    private TextView status, progress;
    private Button finish;

    QrScanner(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    void show() {
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView preview = new SurfaceView(activity);
        preview.getHolder().addCallback(this);
        if (android.os.Build.VERSION.SDK_INT < 11) preview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        // ריבוע כיוון במרכז.
        View guide = new View(activity);
        guide.setBackground(Ui.shape(activity, Color.TRANSPARENT, 16, 3, 0xFFC9B8F0));
        int side = Math.round(Math.min(activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels) * 0.7f);
        root.addView(guide, new FrameLayout.LayoutParams(side, side, Gravity.CENTER));

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xE62C1A4D);
        int pad = Ui.dp(activity, Ui.S3);
        bar.setPadding(pad, pad, pad, pad);
        status = Ui.text(activity, "מכוונים את המצלמה לקוד ה-QR", Ui.T_BODY, Color.WHITE, true);
        status.setGravity(Gravity.CENTER);
        progress = Ui.text(activity, "", Ui.T_NOTE, 0xFFD9CFFA, false);
        progress.setGravity(Gravity.CENTER);
        bar.addView(status);
        bar.addView(progress);
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        finish = barButton("סיום (אישור)", v -> finishPartial());
        finish.setVisibility(View.GONE);
        Button cancel = barButton("ביטול (חזרה)", v -> close());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(Ui.dp(activity, Ui.S1), Ui.dp(activity, Ui.S2), Ui.dp(activity, Ui.S1), 0);
        buttons.addView(finish, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        lp2.setMargins(Ui.dp(activity, Ui.S1), Ui.dp(activity, Ui.S2), Ui.dp(activity, Ui.S1), 0);
        buttons.addView(cancel, lp2);
        bar.addView(buttons);
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        dialog.setContentView(root);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP) return keyCode != KeyEvent.KEYCODE_BACK;
            if (keyCode == KeyEvent.KEYCODE_BACK) { close(); return true; }
            if (keyCode == KeyEvent.KEYCODE_5 || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                finishPartial();
                return true;
            }
            return true;
        });
        dialog.setOnDismissListener(d -> release());
        dialog.show();
    }

    private Button barButton(String label, View.OnClickListener click) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(Ui.shape(activity, 0xFF56348F, 12, 0, 0));
        b.setFocusable(false);
        b.setOnClickListener(click);
        return b;
    }

    // ---- מצלמה ----

    @Override public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = openBackCamera();
            if (camera == null) { fail("לא נמצאה מצלמה במכשיר."); return; }
            Camera.Parameters params = camera.getParameters();
            Camera.Size size = choosePreviewSize(params);
            if (size != null) params.setPreviewSize(size.width, size.height);
            java.util.List<String> modes = params.getSupportedFocusModes();
            if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            else if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                autoFocusLoop = true;
            }
            camera.setParameters(params);
            Camera.Size actual = camera.getParameters().getPreviewSize();
            previewWidth = actual.width;
            previewHeight = actual.height;
            camera.setDisplayOrientation(displayOrientation());
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            requestFrame();
            if (autoFocusLoop) main.postDelayed(focusTick, 600);
        } catch (Exception e) {
            fail("לא ניתן לפתוח את המצלמה. ייתכן שהיא בשימוש באפליקציה אחרת.");
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) { release(); }

    private Camera openBackCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { cameraId = i; return Camera.open(i); }
        }
        if (Camera.getNumberOfCameras() > 0) { cameraId = 0; return Camera.open(0); }
        return null;
    }

    private int cameraId = 0;

    private int displayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return (360 - (info.orientation + degrees) % 360) % 360;
        return (info.orientation - degrees + 360) % 360;
    }

    /** תצוגה מקדימה בינונית (כ-800x600): חדה מספיק לקוד, וקלה למעבד חלש. */
    private static Camera.Size choosePreviewSize(Camera.Parameters params) {
        Camera.Size best = null;
        long target = 800L * 600L, bestDiff = Long.MAX_VALUE;
        for (Camera.Size s : params.getSupportedPreviewSizes()) {
            long diff = Math.abs((long) s.width * s.height - target);
            if (diff < bestDiff) { bestDiff = diff; best = s; }
        }
        return best;
    }

    private final Runnable focusTick = new Runnable() {
        @Override public void run() {
            if (closed || camera == null) return;
            try { camera.autoFocus((success, cam) -> { }); } catch (Exception ignored) { }
            main.postDelayed(this, 2500);
        }
    };

    private void requestFrame() {
        if (closed || camera == null) return;
        try { camera.setOneShotPreviewCallback(this); } catch (Exception ignored) { }
    }

    @Override public void onPreviewFrame(byte[] data, Camera cam) {
        if (closed || decoding || data == null) { main.postDelayed(this::requestFrame, 120); return; }
        decoding = true;
        final int w = previewWidth, h = previewHeight;
        decoder.execute(() -> {
            String text = decode(data, w, h);
            main.post(() -> {
                decoding = false;
                if (closed) return;
                if (text != null) handle(text);
                main.postDelayed(this::requestFrame, text != null ? 600 : 80);
            });
        });
    }

    private String decode(byte[] data, int width, int height) {
        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
            Result result = reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints);
            return result == null ? null : result.getText();
        } catch (Exception e) {
            return null; // לא נמצא קוד בתמונה הזו - ממשיכים לנסות
        } finally {
            reader.reset();
        }
    }

    // ---- איסוף חלקים ----

    private void handle(String text) {
        Matcher m = PART.matcher(text);
        if (!m.find()) { done(text); return; }
        int index = Integer.parseInt(m.group(1)), count = Integer.parseInt(m.group(2));
        if (count <= 1) { done(text); return; }
        if (total != 0 && total != count) {
            status.setText("הקוד הזה שייך לסדרה אחרת");
            return;
        }
        total = count;
        boolean isNew = !parts.containsKey(index);
        parts.put(index, text);
        if (isNew) feedback();
        if (parts.size() >= total) { done(joined()); return; }
        StringBuilder missing = new StringBuilder();
        for (int i = 1; i <= total; i++) if (!parts.containsKey(i)) missing.append(missing.length() == 0 ? "" : ", ").append(i);
        status.setText("נסרקו " + parts.size() + " מתוך " + total + " קודים");
        progress.setText("חסרים: " + missing + " · אפשר לסרוק בכל סדר");
        finish.setVisibility(View.VISIBLE);
    }

    private String joined() {
        StringBuilder all = new StringBuilder();
        for (String part : parts.values()) all.append(part).append('\n');
        return all.toString();
    }

    private void finishPartial() {
        if (parts.isEmpty()) return;
        done(joined());
    }

    private void feedback() {
        try {
            android.os.Vibrator v = (android.os.Vibrator) activity.getSystemService(Activity.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(60);
        } catch (Exception ignored) { }
    }

    private void done(String text) {
        feedback();
        close();
        listener.onScanned(text);
    }

    private void fail(String message) {
        close();
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
    }

    private void close() {
        closed = true;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        release();
    }

    private void release() {
        closed = true;
        main.removeCallbacks(focusTick);
        if (camera != null) {
            try { camera.setOneShotPreviewCallback(null); camera.stopPreview(); } catch (Exception ignored) { }
            try { camera.release(); } catch (Exception ignored) { }
            camera = null;
        }
        decoder.shutdownNow();
    }
}
