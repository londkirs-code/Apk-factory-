package com.londkirs.quickfloat;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

public class FloatAccessibilityService extends AccessibilityService {
    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int startX, startY;

    @Override public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Retry after returning from system settings if overlay permission was just granted.
        if (bubble == null) showBubble();
    }
    @Override public void onInterrupt() {}

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        showBubble();
    }

    @Override public void onDestroy() {
        removeBubble();
        super.onDestroy();
    }

    private void showBubble() {
        if (bubble != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "יש לאשר הצגה מעל אפליקציות בהגדרות QuickFloat", Toast.LENGTH_LONG).show();
            return;
        }
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        TextView b = new TextView(this);
        b.setText("Q");
        b.setTextColor(Color.WHITE);
        b.setTextSize(21);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.rgb(7, 94, 84));
        shape.setShape(GradientDrawable.OVAL);
        shape.setStroke(dp(2), Color.WHITE);
        b.setBackground(shape);
        b.setElevation(dp(8));
        bubble = b;
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(58), dp(58), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(180);
        b.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = params.x; startY = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX + (int)(e.getRawX() - downX);
                    params.y = startY + (int)(e.getRawY() - downY);
                    try { wm.updateViewLayout(bubble, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(e.getRawX()-downX) < dp(8) && Math.abs(e.getRawY()-downY) < dp(8)) showActions();
                    return true;
            }
            return false;
        });
        try { wm.addView(bubble, params); } catch (Exception e) { bubble = null; }
    }

    private void showActions() {
        final String[] keys = {"back", "home", "recents", "notifications", "quick_settings", "lock"};
        final String[] labels = {"חזרה", "מסך הבית", "אפליקציות אחרונות", "פתיחת התראות", "הגדרות מהירות", "נעילת המסך"};
        android.content.SharedPreferences prefs = getSharedPreferences("quickfloat", MODE_PRIVATE);
        java.util.ArrayList<String> shown = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> indexes = new java.util.ArrayList<>();
        for (int i=0;i<keys.length;i++) if (prefs.getBoolean(keys[i], true)) { shown.add(labels[i]); indexes.add(i); }
        if (shown.isEmpty()) { Toast.makeText(this, "בחרו לפחות פעולה אחת בהגדרות QuickFloat", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
            .setTitle("QuickFloat")
            .setItems(shown.toArray(new String[0]), (dialog, which) -> runAction(keys[indexes.get(which)]))
            .setNegativeButton("סגירה", null)
            .show()
            .getWindow().setType(Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE);
    }

    private void runAction(String key) {
        boolean ok = true;
        switch (key) {
            case "back": ok = performGlobalAction(GLOBAL_ACTION_BACK); break;
            case "home": ok = performGlobalAction(GLOBAL_ACTION_HOME); break;
            case "recents": ok = performGlobalAction(GLOBAL_ACTION_RECENTS); break;
            case "notifications": ok = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break;
            case "quick_settings": ok = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); break;
            case "lock":
                if (Build.VERSION.SDK_INT >= 28) ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                else { ok = false; Toast.makeText(this, "נעילת מסך דורשת Android 9 ומעלה", Toast.LENGTH_LONG).show(); }
                break;
        }
        if (!ok && !key.equals("lock")) Toast.makeText(this, "הפעולה אינה נתמכת במכשיר זה", Toast.LENGTH_SHORT).show();
    }

    private void removeBubble() {
        if (wm != null && bubble != null) try { wm.removeView(bubble); } catch (Exception ignored) {}
        bubble = null;
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
