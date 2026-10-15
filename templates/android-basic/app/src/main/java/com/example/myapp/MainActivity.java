package com.example.myapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * APK Factory default starter screen.
 * New apps generated from android-basic inherit this clean, bright visual system.
 * Replace the sample content with the app's real functionality while retaining
 * the reusable colors, cards, typography, spacing and button styles.
 */
public class MainActivity extends Activity {
    private final int ink = Color.rgb(18, 34, 63);
    private final int secondary = Color.rgb(91, 108, 136);
    private final int blue = Color.rgb(20, 112, 255);
    private final int paleBlue = Color.rgb(235, 244, 255);
    private final int border = Color.rgb(222, 231, 243);
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(248, 250, 255));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248, 250, 255));

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(22), dp(22), dp(28));
        scroll.addView(page);

        TextView eyebrow = text("YOUR NEXT IDEA, MADE SIMPLE", 11, secondary, true);
        eyebrow.setLetterSpacing(0.08f);
        page.addView(eyebrow);

        String appTitle = getApplicationInfo().loadLabel(getPackageManager()).toString();
        TextView title = text(appTitle, 32, ink, true);
        LinearLayout.LayoutParams titleLp = params(-1, -2);
        titleLp.topMargin = dp(8);
        page.addView(title, titleLp);

        TextView subtitle = text("A fresh start for something great.", 15, secondary, false);
        LinearLayout.LayoutParams subtitleLp = params(-1, -2);
        subtitleLp.topMargin = dp(6);
        page.addView(subtitle, subtitleLp);

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(26), dp(20), dp(26));
        hero.setBackground(shape(Color.WHITE, dp(26), border));
        LinearLayout.LayoutParams heroLp = params(-1, -2);
        heroLp.topMargin = dp(26);
        page.addView(hero, heroLp);

        TextView icon = text("✦", 38, blue, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(paleBlue, dp(24), Color.TRANSPARENT));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(82), dp(82));
        hero.addView(icon, iconLp);

        TextView heroTitle = text("Made for your ideas", 21, ink, true);
        heroTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams heroTitleLp = params(-1, -2);
        heroTitleLp.topMargin = dp(18);
        hero.addView(heroTitle, heroTitleLp);

        TextView heroBody = text("This app starts with APK Factory’s bright, clean design system.", 14, secondary, false);
        heroBody.setGravity(Gravity.CENTER);
        heroBody.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams heroBodyLp = params(-1, -2);
        heroBodyLp.topMargin = dp(8);
        hero.addView(heroBody, heroBodyLp);

        Button primary = new Button(this);
        primary.setText("Get Started  →");
        primary.setAllCaps(false);
        primary.setTextSize(16);
        primary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        primary.setTextColor(Color.WHITE);
        primary.setBackground(shape(blue, dp(18), Color.TRANSPARENT));
        primary.setElevation(dp(3));
        LinearLayout.LayoutParams primaryLp = params(-1, dp(54));
        primaryLp.topMargin = dp(24);
        hero.addView(primary, primaryLp);
        primary.setOnClickListener(v -> showMessage("Your app is ready to customize."));

        TextView section = text("DESIGNED TO FEEL SIMPLE", 12, secondary, true);
        section.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams sectionLp = params(-1, -2);
        sectionLp.topMargin = dp(28);
        page.addView(section, sectionLp);

        addInfoCard("Thoughtful by default", "Generous spacing, clear typography and rounded cards.", "01");
        addInfoCard("Ready to make yours", "Keep this visual foundation and replace the sample features.", "02");

        TextView footer = text("Built with APK Factory", 12, secondary, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = params(-1, -2);
        footerLp.topMargin = dp(28);
        page.addView(footer, footerLp);

        setContentView(scroll);
    }

    private void addInfoCard(String heading, String description, String number) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(shape(Color.WHITE, dp(20), border));
        LinearLayout.LayoutParams cardLp = params(-1, -2);
        cardLp.topMargin = dp(12);
        page.addView(card, cardLp);

        TextView badge = text(number, 13, blue, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(paleBlue, dp(12), Color.TRANSPARENT));
        card.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLp.leftMargin = dp(14);
        card.addView(copy, copyLp);

        copy.addView(text(heading, 16, ink, true));
        TextView body = text(description, 13, secondary, false);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyLp = params(-1, -2);
        bodyLp.topMargin = dp(5);
        copy.addView(body, bodyLp);
    }

    private void showMessage(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable shape(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeColor != Color.TRANSPARENT) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
