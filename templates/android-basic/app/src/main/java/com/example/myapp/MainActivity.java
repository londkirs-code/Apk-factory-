package com.example.myapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("My Android app is ready!");
        view.setTextSize(22);
        view.setGravity(Gravity.CENTER);
        setContentView(view);
    }
}
