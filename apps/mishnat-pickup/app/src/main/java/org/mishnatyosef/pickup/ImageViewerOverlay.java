package org.mishnatyosef.pickup;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;

/** Full-screen local image viewer. Large bitmaps are owned only for the overlay lifetime. */
final class ImageViewerOverlay {
    private final Activity activity;
    private final ExecutorService decoder;
    private FrameLayout overlay;
    private ImageView image;
    private Bitmap largeBitmap;
    private int generation;
    private int swallowUpKeyCode = KeyEvent.KEYCODE_UNKNOWN;

    ImageViewerOverlay(Activity activity, ExecutorService decoder) {
        this.activity = activity;
        this.decoder = decoder;
    }

    void show(String name, String amount, Bitmap preview, File localFile) {
        reset();
        final int token = ++generation;
        FrameLayout layer = new FrameLayout(activity);
        layer.setBackgroundColor(Ui.SCRIM);
        layer.setClickable(true);
        layer.setFocusable(false);
        layer.setOnClickListener(v -> dismiss());
        ImageView photo = new ImageView(activity);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setFocusable(false);
        photo.setFocusableInTouchMode(false);
        photo.setClickable(true);
        photo.setOnClickListener(v -> dismiss());
        if (preview != null && !preview.isRecycled()) photo.setImageBitmap(preview);
        FrameLayout.LayoutParams photoLp = new FrameLayout.LayoutParams(-1, -1);
        photoLp.setMargins(Ui.dp(activity, Ui.S4), Ui.dp(activity, Ui.S4),
                Ui.dp(activity, Ui.S4), Ui.dp(activity, Ui.S8));
        layer.addView(photo, photoLp);
        LinearLayout details = Ui.column(activity);
        details.setGravity(Gravity.CENTER);
        details.setBackground(Ui.shape(activity, Ui.P900, Ui.R_DIALOG, 1, Ui.P700));
        details.setPadding(Ui.dp(activity, Ui.S5), Ui.dp(activity, Ui.S3),
                Ui.dp(activity, Ui.S5), Ui.dp(activity, Ui.S3));
        TextView title = Ui.text(activity, name == null ? "" : name, Ui.T_BODY, Ui.P25, true);
        title.setGravity(Gravity.CENTER);
        TextView quantity = Ui.text(activity, "כמות: " + (amount == null ? "" : amount),
                Ui.T_NOTE, Ui.P100, true);
        quantity.setGravity(Gravity.CENTER);
        details.addView(title);
        details.addView(quantity, Ui.row(activity, Ui.S1, 0));
        FrameLayout.LayoutParams detailsLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        detailsLp.setMargins(Ui.dp(activity, Ui.S5), 0, Ui.dp(activity, Ui.S5), Ui.dp(activity, Ui.S5));
        layer.addView(details, detailsLp);
        overlay = layer;
        image = photo;
        activity.addContentView(layer, new ViewGroup.LayoutParams(-1, -1));
        if (localFile != null && localFile.isFile()) decodeLarge(localFile, token);
    }

    private void decodeLarge(File file, int token) {
        decoder.execute(() -> {
            Bitmap decoded = decodeForScreen(file);
            if (decoded == null) return;
            activity.runOnUiThread(() -> {
                if (token != generation || overlay == null || image == null) {
                    decoded.recycle();
                    return;
                }
                Bitmap previous = largeBitmap;
                largeBitmap = decoded;
                image.setImageBitmap(decoded);
                if (previous != null && !previous.isRecycled()) previous.recycle();
            });
        });
    }

    private Bitmap decodeForScreen(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int targetWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int targetHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidth
                && bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    boolean onKey(KeyEvent event) {
        if (swallowUpKeyCode != KeyEvent.KEYCODE_UNKNOWN
                && event.getAction() == KeyEvent.ACTION_UP
                && event.getKeyCode() == swallowUpKeyCode) {
            swallowUpKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (overlay == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int key = event.getKeyCode();
        boolean consume = key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        dismiss();
        if (consume) swallowUpKeyCode = key;
        return consume;
    }

    boolean isOpen() { return overlay != null; }

    void reset() {
        swallowUpKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        dismiss();
    }

    private void dismiss() {
        generation++;
        FrameLayout old = overlay;
        overlay = null;
        if (image != null) image.setImageDrawable(null);
        image = null;
        Bitmap bitmap = largeBitmap;
        largeBitmap = null;
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        if (old != null && old.getParent() instanceof ViewGroup)
            ((ViewGroup) old.getParent()).removeView(old);
    }
}
