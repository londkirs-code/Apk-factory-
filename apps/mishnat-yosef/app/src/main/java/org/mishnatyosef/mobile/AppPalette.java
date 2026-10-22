package org.mishnatyosef.mobile;

import android.graphics.Color;

/**
 * Central semantic color tokens for the native Mishnat Yosef UI.
 *
 * Keep day/night values here so future theme choices can be added without scattering
 * color literals through screens. Effect opacity and fixed-purpose colors are tokens too.
 * Image pixels, transparent masks, and other content colors remain intentionally independent.
 */
final class AppPalette {
    private AppPalette() {}

    // Keep alpha values named so opacity can be tuned independently from RGB.
    static final int STAR_BACKGROUND_ALPHA = 220;

    static final Theme DAY = new Theme(
            Color.rgb(230, 94, 20), Color.rgb(191, 70, 12), Color.rgb(255, 239, 224),
            Color.rgb(255, 249, 244), Color.WHITE, Color.rgb(34, 49, 46),
            Color.rgb(120, 110, 104), Color.rgb(238, 220, 205),
            Color.rgb(20, 130, 70), Color.rgb(228, 245, 234),
            Color.rgb(175, 45, 45), Color.rgb(253, 236, 234),
            Color.WHITE, Color.rgb(230, 94, 20), Color.WHITE,
            Color.rgb(255, 218, 190), Color.argb(STAR_BACKGROUND_ALPHA, 255, 255, 255),
            Color.rgb(242, 238, 234), Color.rgb(190, 180, 172), Color.rgb(225, 218, 212));

    static final Theme NIGHT = new Theme(
            Color.rgb(255, 145, 75), Color.rgb(255, 166, 105), Color.rgb(75, 48, 34),
            Color.rgb(18, 20, 22), Color.rgb(30, 33, 36), Color.rgb(240, 240, 238),
            Color.rgb(177, 180, 181), Color.rgb(65, 69, 72),
            Color.rgb(100, 205, 140), Color.rgb(35, 65, 48),
            Color.rgb(255, 120, 120), Color.rgb(75, 40, 40),
            Color.rgb(36, 22, 12), Color.rgb(34, 38, 42), Color.rgb(255, 166, 105),
            Color.rgb(110, 68, 40), Color.argb(STAR_BACKGROUND_ALPHA, 30, 33, 36),
            Color.rgb(52, 55, 58), Color.rgb(150, 155, 160), Color.rgb(70, 74, 78));

    static Theme forNightMode(boolean nightMode) {
        return nightMode ? NIGHT : DAY;
    }

    /** Semantic roles retain their meaning even when their actual color changes. */
    static final class Theme {
        final int orange;
        final int orangeDark;
        final int orangeLight;
        final int background;
        final int surface;
        final int textPrimary;
        final int textSecondary;
        final int border;
        final int success;
        final int successSurface;
        final int error;
        final int errorSurface;
        final int textOnAccent;
        final int headerBackground;
        final int headerForeground;
        final int minusFill;
        final int starBackground;
        final int noOrderBackground;
        final int switchThumbOff;
        final int switchTrackOff;

        Theme(int orange, int orangeDark, int orangeLight, int background, int surface,
              int textPrimary, int textSecondary, int border, int success, int successSurface,
              int error, int errorSurface, int textOnAccent, int headerBackground,
              int headerForeground, int minusFill, int starBackground, int noOrderBackground,
              int switchThumbOff, int switchTrackOff) {
            this.orange = orange;
            this.orangeDark = orangeDark;
            this.orangeLight = orangeLight;
            this.background = background;
            this.surface = surface;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.border = border;
            this.success = success;
            this.successSurface = successSurface;
            this.error = error;
            this.errorSurface = errorSurface;
            this.textOnAccent = textOnAccent;
            this.headerBackground = headerBackground;
            this.headerForeground = headerForeground;
            this.minusFill = minusFill;
            this.starBackground = starBackground;
            this.noOrderBackground = noOrderBackground;
            this.switchThumbOff = switchThumbOff;
            this.switchTrackOff = switchTrackOff;
        }
    }

    // Fixed-purpose UI colors: centralized, but deliberately not changed with day/night mode.
    static final int NAV_INACTIVE_DAY = Color.rgb(70, 62, 58);
    static final int FOCUS_BORDER = Color.rgb(142, 142, 147);
    static final int NIGHT_TOGGLE_GRADIENT_TOP = Color.rgb(255, 255, 250);
    static final int CATEGORY_FOCUS_BORDER = Color.rgb(33, 150, 243);
    static final int WHATS_NEW_BACKGROUND = 0xFFE8F7EC;
    static final int WHATS_NEW_BORDER = 0xFFB7E4C7;

    // Effects keep their original alpha values; alpha is intentionally separate from RGB.
    static final int RIPPLE_ALPHA = 40;
    static final int CIRCULAR_BUTTON_RIPPLE_ALPHA = 50;
    static final int FOCUS_FILL_ALPHA = 44;
    static final int WHITE_RIPPLE_ALPHA = 80;
    static final int SWITCH_TRACK_ON_ALPHA = 120;
    static final int IMAGE_OVERLAY = Color.argb(180, 0, 0, 0);
    static final int IMAGE_LOADING_BACKGROUND = Color.argb(185, 30, 33, 36);
    static final int IMAGE_CLOSE_BACKGROUND = Color.argb(190, 24, 27, 30);
    static final float DIALOG_DIM_AMOUNT = 0.25f;
}
