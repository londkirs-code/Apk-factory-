package com.londkirs.ptaktsaf;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "floating_note";
    private static final String NOTE_KEY = "note_text";
    private static final String MODE_KEY = "checklist_mode";

    private SharedPreferences preferences;
    private EditText freeText;
    private ScrollView checklistScroll;
    private LinearLayout checklistRows;
    private TextView checklistButton;
    private boolean checklistMode;
    private boolean rebuilding;
    private final List<NoteItem> items = new ArrayList<>();

    private static class NoteItem {
        String text;
        boolean checked;

        NoteItem(String text, boolean checked) {
            this.text = text;
            this.checked = checked;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        setFinishOnTouchOutside(false);

        setContentView(R.layout.floating_note);

        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h);
        window.setGravity(Gravity.BOTTOM);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        freeText = findViewById(R.id.noteText);
        checklistScroll = findViewById(R.id.checklistScroll);
        checklistRows = findViewById(R.id.checklistRows);
        checklistButton = findViewById(R.id.btnChecklist);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnCopy).setOnClickListener(v -> copyNote());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareNote());
        checklistButton.setOnClickListener(v -> toggleChecklist());

        checklistMode = preferences.getBoolean(MODE_KEY, false);
        String saved = preferences.getString(NOTE_KEY, "");
        if (checklistMode) {
            parseItems(saved);
            showChecklist();
        } else {
            freeText.setText(saved);
            showFreeText();
        }

        freeText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!rebuilding && !checklistMode) {
                    preferences.edit().putString(NOTE_KEY, s.toString()).apply();
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void toggleChecklist() {
        if (!checklistMode) {
            parseItems(freeText.getText().toString());
            checklistMode = true;
            preferences.edit().putBoolean(MODE_KEY, true).putString(NOTE_KEY, serializeItems()).apply();
            showChecklist();
            focusLastChecklistRow();
        } else {
            String serialized = serializeItems();
            checklistMode = false;
            preferences.edit().putBoolean(MODE_KEY, false).putString(NOTE_KEY, serialized).apply();
            rebuilding = true;
            freeText.setText(serialized);
            rebuilding = false;
            showFreeText();
            freeText.requestFocus();
            freeText.setSelection(freeText.length());
        }
    }

    private void showFreeText() {
        freeText.setVisibility(View.VISIBLE);
        checklistScroll.setVisibility(View.GONE);
        checklistButton.setText("☑");
        checklistButton.setContentDescription("מעבר לצ'קליסט");
    }

    private void showChecklist() {
        freeText.setVisibility(View.GONE);
        checklistScroll.setVisibility(View.VISIBLE);
        checklistButton.setText("☰");
        checklistButton.setContentDescription("מעבר לטקסט חופשי");
        renderChecklist();
    }

    private void parseItems(String text) {
        items.clear();
        String[] lines = text.split("\n", -1);
        if (text.isEmpty()) {
            items.add(new NoteItem("", false));
            return;
        }
        for (String line : lines) {
            if (line.startsWith("[x]") || line.startsWith("[X]")) {
                items.add(new NoteItem(stripMarkerText(line), true));
            } else if (line.startsWith("[ ]")) {
                items.add(new NoteItem(stripMarkerText(line), false));
            } else {
                items.add(new NoteItem(line, false));
            }
        }
        if (items.isEmpty()) items.add(new NoteItem("", false));
    }

    private String stripMarkerText(String line) {
        if (line.length() <= 3) return "";
        return line.substring(3).startsWith(" ") ? line.substring(4) : line.substring(3);
    }

    private String serializeItems() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) result.append('\n');
            NoteItem item = items.get(i);
            result.append(item.checked ? "[x] " : "[ ] ").append(item.text);
        }
        return result.toString();
    }

    private void saveChecklist() {
        if (!rebuilding) {
            preferences.edit().putString(NOTE_KEY, serializeItems()).apply();
        }
    }

    private void renderChecklist() {
        rebuilding = true;
        checklistRows.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            addChecklistRow(i);
        }
        rebuilding = false;
    }

    private void addChecklistRow(int index) {
        NoteItem item = items.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(2), dp(12), dp(2));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF8B7738));
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(40), dp(48)));

        EditText lineText = new EditText(this);
        lineText.setSingleLine(true);
        lineText.setTextDirection(View.TEXT_DIRECTION_RTL);
        lineText.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        lineText.setTextSize(16);
        lineText.setTextColor(item.checked ? 0xFF999999 : 0xFF2B2615);
        lineText.setHint("כתוב כאן...");
        lineText.setHintTextColor(0xFFA89B6A);
        lineText.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        lineText.setPadding(dp(4), dp(8), dp(4), dp(8));
        lineText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        lineText.setText(item.text);
        if (item.checked) lineText.setPaintFlags(lineText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        row.addView(lineText, textParams);

        checkBox.setChecked(item.checked);
        checkBox.setOnCheckedChangeListener((button, checked) -> {
            item.checked = checked;
            applyCheckedStyle(lineText, checked);
            saveChecklist();
        });

        lineText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                item.text = s.toString();
                saveChecklist();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        lineText.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = actionId == EditorInfo.IME_ACTION_NEXT
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (enterPressed) {
                insertChecklistRow(index + 1);
                return true;
            }
            return false;
        });

        lineText.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                insertChecklistRow(index + 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL && lineText.getText().length() == 0 && items.size() > 1) {
                removeChecklistRow(index);
                return true;
            }
            return false;
        });

        checklistRows.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void applyCheckedStyle(EditText editText, boolean checked) {
        if (checked) {
            editText.setPaintFlags(editText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            editText.setTextColor(0xFF999999);
        } else {
            editText.setPaintFlags(editText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            editText.setTextColor(0xFF2B2615);
        }
    }

    private void insertChecklistRow(int index) {
        items.add(index, new NoteItem("", false));
        saveChecklist();
        renderChecklist();
        focusChecklistRow(index);
    }

    private void removeChecklistRow(int index) {
        items.remove(index);
        saveChecklist();
        int focusIndex = Math.max(0, index - 1);
        renderChecklist();
        focusChecklistRow(focusIndex);
    }

    private void focusChecklistRow(int index) {
        if (index < 0 || index >= checklistRows.getChildCount()) return;
        View row = checklistRows.getChildAt(index);
        if (row instanceof LinearLayout && ((LinearLayout) row).getChildCount() > 1) {
            View text = ((LinearLayout) row).getChildAt(1);
            text.requestFocus();
            if (text instanceof EditText) ((EditText) text).setSelection(((EditText) text).length());
        }
    }

    private void focusLastChecklistRow() {
        focusChecklistRow(items.size() - 1);
    }

    private String getNoteForTransfer() {
        if (!checklistMode) return freeText.getText().toString();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) result.append('\n');
            NoteItem item = items.get(i);
            result.append(item.checked ? "☑ " : "☐ ").append(item.text);
        }
        return result.toString();
    }

    private void copyNote() {
        String text = getNoteForTransfer();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("פתק", text));
        Toast.makeText(this, "הועתק", Toast.LENGTH_SHORT).show();
    }

    private void shareNote() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getNoteForTransfer());
        startActivity(Intent.createChooser(send, "שיתוף פתק"));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
