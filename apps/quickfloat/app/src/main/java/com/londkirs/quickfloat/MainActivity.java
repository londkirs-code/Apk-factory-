package com.londkirs.quickfloat;

import org.apkfactory.theme.ThemeWorkbench;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

public class MainActivity extends Activity {
    private final String[] keys = {"back", "home", "recents", "notifications", "quick_settings", "lock"};
    private final String[] labels = {"חזרה", "מסך הבית", "אפליקציות אחרונות", "פתיחת התראות", "הגדרות מהירות", "נעילת המסך"};
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("quickfloat", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(7, 94, 84));
        getWindow().setNavigationBarColor(Color.rgb(7, 94, 84));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(24));
        page.setBackgroundColor(Color.rgb(245, 247, 248));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("QuickFloat");
        title.setTextSize(30);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(7, 94, 84));
        page.addView(title);

        TextView intro = new TextView(this);
        intro.setText("כפתור צף לפעולות מערכת\nבחרו אילו פעולות יופיעו בתפריט הכפתור.");
        intro.setTextSize(16);
        intro.setTextColor(Color.DKGRAY);
        intro.setPadding(0, dp(8), 0, dp(18));
        page.addView(intro);

        Button overlay = new Button(this);
        overlay.setText("1. אישור הצגה מעל אפליקציות");
        overlay.setAllCaps(false);
        overlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        });
        page.addView(overlay, matchWrap());

        Button accessibility = new Button(this);
        accessibility.setText("2. הפעלת שירות הנגישות");
        accessibility.setAllCaps(false);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        page.addView(accessibility, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("פעולות בתפריט");
        sub.setTextSize(19);
        sub.setTypeface(null, Typeface.BOLD);
        sub.setTextColor(Color.BLACK);
        sub.setPadding(0, dp(22), 0, dp(8));
        page.addView(sub);

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            CheckBox check = new CheckBox(this);
            check.setText(labels[i]);
            check.setTextSize(16);
            check.setTextColor(Color.DKGRAY);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(7, 94, 84)));
            check.setChecked(prefs.getBoolean(key, true));
            check.setPadding(0, dp(5), 0, dp(5));
            check.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
            page.addView(check, matchWrap());
        }

        TextView note = new TextView(this);
        note.setText("\nלאחר מתן שתי ההרשאות, הכפתור הצף יופיע. אפשר לגרור אותו למיקום נוח. פעולות מסוימות תלויות בגרסת Android ובמכשיר.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        page.addView(note);
        setContentView(scroll);
        ThemeWorkbench.install(this);
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
