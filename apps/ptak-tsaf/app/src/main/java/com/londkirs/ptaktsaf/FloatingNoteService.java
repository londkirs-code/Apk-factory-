package com.londkirs.ptaktsaf;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

public class FloatingNoteService extends Service {
    private static final String PREFS_NAME = "floating_note";
    private static final String NOTE_KEY = "note_text";
    private WindowManager windowManager;
    private View noteView;
    private WindowManager.LayoutParams windowParams;
    private EditText noteText;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (noteView != null) {
            closeNote();
            stopSelf();
        } else {
            showNote();
        }
        return START_NOT_STICKY;
    }

    private void showNote() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        noteView = LayoutInflater.from(this).inflate(R.layout.floating_note, null);
        noteText = noteView.findViewById(R.id.noteText);
        TextView closeButton = noteView.findViewById(R.id.btnClose);
        View dragHandle = noteView.findViewById(R.id.dragHandle);

        noteText.setText(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(NOTE_KEY, ""));
        noteText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(NOTE_KEY, s.toString()).apply();
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        createWindowParams();
        windowManager.addView(noteView, windowParams);
        closeButton.setOnClickListener(v -> {
            closeNote();
            stopSelf();
        });
        installDragGesture(dragHandle);
        noteText.requestFocus();
        noteText.post(() -> {
            if (noteText != null && noteView != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(noteText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void createWindowParams() {
        float d = getResources().getDisplayMetrics().density;
        windowParams = new WindowManager.LayoutParams(
                (int) (300 * d),
                (int) (240 * d),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = (int) (24 * d);
        windowParams.y = (int) (160 * d);
        windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    }

    private void installDragGesture(View dragHandle) {
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX, downRawY;
            private int startX, startY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX(); downRawY = event.getRawY();
                        startX = windowParams.x; startY = windowParams.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        windowParams.x = startX + Math.round(event.getRawX() - downRawX);
                        windowParams.y = startY + Math.round(event.getRawY() - downRawY);
                        if (noteView != null && noteView.isAttachedToWindow()) windowManager.updateViewLayout(noteView, windowParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                    default:
                        return true;
                }
            }
        });
    }

    private void closeNote() {
        if (noteView != null) {
            try { windowManager.removeView(noteView); }
            catch (IllegalArgumentException ignored) { }
            noteView = null;
            noteText = null;
        }
    }

    @Override public void onDestroy() {
        closeNote();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
