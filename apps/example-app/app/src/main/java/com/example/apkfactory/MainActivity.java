package com.example.apkfactory;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView title = new TextView(this);
        title.setText("APK Factory — Example App");
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        setContentView(title);
    }
}
