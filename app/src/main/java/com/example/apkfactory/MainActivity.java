package com.example.apkfactory;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("האפליקציה הראשונה שלך מוכנה!");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 45, 75));
        title.setGravity(Gravity.CENTER);

        TextView description = new TextView(this);
        description.setText("נבנתה אוטומטית ב־GitHub Actions.\nערוך את MainActivity.java כדי להתחיל ליצור.");
        description.setTextSize(16);
        description.setTextColor(Color.DKGRAY);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 24, 0, 0);

        layout.addView(title);
        layout.addView(description);
        setContentView(layout);
    }
}
