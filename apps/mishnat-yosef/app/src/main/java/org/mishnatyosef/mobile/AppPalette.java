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

    static final Theme PURPLE_DAY = new Theme(
            Color.rgb(104, 65, 184), Color.rgb(81, 48, 143), Color.rgb(240, 233, 250),
            Color.rgb(251, 249, 254), Color.WHITE, Color.rgb(45, 38, 56),
            Color.rgb(117, 109, 128), Color.rgb(230, 223, 240),
            Color.rgb(20, 130, 70), Color.rgb(228, 245, 234),
            Color.rgb(175, 45, 45), Color.rgb(253, 236, 234),
            Color.WHITE, Color.rgb(111, 76, 166), Color.WHITE,
            Color.rgb(231, 215, 248), Color.argb(STAR_BACKGROUND_ALPHA, 255, 255, 255),
            Color.rgb(244, 240, 248), Color.rgb(199, 190, 207), Color.rgb(229, 221, 235));

    static final Theme PURPLE_NIGHT = new Theme(
            Color.rgb(182, 154, 239), Color.rgb(155, 123, 214), Color.rgb(57, 43, 75),
            Color.rgb(23, 19, 31), Color.rgb(36, 28, 48), Color.rgb(244, 239, 251),
            Color.rgb(185, 173, 201), Color.rgb(67, 54, 79),
            Color.rgb(100, 205, 140), Color.rgb(35, 65, 48),
            Color.rgb(255, 120, 120), Color.rgb(75, 40, 40),
            Color.rgb(36, 28, 48), Color.rgb(36, 28, 48), Color.rgb(220, 203, 255),
            Color.rgb(75, 53, 95), Color.argb(STAR_BACKGROUND_ALPHA, 36, 28, 48),
            Color.rgb(45, 36, 56), Color.rgb(141, 130, 153), Color.rgb(75, 65, 85));

    // Forest-green palette inspired by the new Mishnat Yosef launcher artwork.
    static final Theme GREEN_DAY = new Theme(
            Color.rgb(39, 133, 75), Color.rgb(24, 91, 51), Color.rgb(226, 244, 232),
            Color.rgb(247, 251, 248), Color.WHITE, Color.rgb(30, 49, 38),
            Color.rgb(105, 124, 111), Color.rgb(211, 229, 216),
            Color.rgb(20, 130, 70), Color.rgb(228, 245, 234),
            Color.rgb(175, 45, 45), Color.rgb(253, 236, 234),
            Color.WHITE, Color.rgb(31, 111, 62), Color.WHITE,
            Color.rgb(204, 232, 212), Color.argb(STAR_BACKGROUND_ALPHA, 255, 255, 255),
            Color.rgb(239, 246, 241), Color.rgb(184, 198, 188), Color.rgb(220, 231, 223));

    static final Theme GREEN_NIGHT = new Theme(
            Color.rgb(116, 213, 151), Color.rgb(143, 226, 171), Color.rgb(38, 66, 49),
            Color.rgb(17, 23, 19), Color.rgb(27, 35, 30), Color.rgb(237, 245, 239),
            Color.rgb(174, 190, 178), Color.rgb(57, 72, 61),
            Color.rgb(116, 213, 151), Color.rgb(34, 65, 45),
            Color.rgb(255, 130, 130), Color.rgb(75, 40, 40),
            Color.rgb(18, 43, 27), Color.rgb(31, 62, 42), Color.rgb(232, 247, 237),
            Color.rgb(57, 82, 64), Color.argb(STAR_BACKGROUND_ALPHA, 27, 35, 30),
            Color.rgb(44, 53, 46), Color.rgb(124, 143, 129), Color.rgb(61, 75, 64));

    static Theme forNightMode(boolean nightMode) {
        return nightMode ? NIGHT : DAY;
    }

    static Theme forTheme(boolean nightMode, boolean purpleTheme) {
        return forTheme(nightMode, purpleTheme, false);
    }

    static Theme forTheme(boolean nightMode, boolean purpleTheme, boolean greenTheme) {
        if (greenTheme) return nightMode ? GREEN_NIGHT : GREEN_DAY;
        if (purpleTheme) return nightMode ? PURPLE_NIGHT : PURPLE_DAY;
        return forNightMode(nightMode);
    }

    /**
     * System status bar color is derived from the explicit selected theme, not header text color.
     * This keeps orange and purple themes distinct and makes future themes easy to extend.
     */
    static int systemBar(boolean nightMode, boolean purpleTheme) {
        return systemBar(nightMode, purpleTheme, false);
    }

    static int systemBar(boolean nightMode, boolean purpleTheme, boolean greenTheme) {
        Theme selected = forTheme(nightMode, purpleTheme, greenTheme);
        return nightMode ? selected.background : selected.orangeDark;
    }

    static int navInactive(boolean purpleTheme) {
        return navInactive(purpleTheme, false);
    }

    static int navInactive(boolean purpleTheme, boolean greenTheme) {
        if (greenTheme) return NAV_INACTIVE_GREEN;
        return purpleTheme ? NAV_INACTIVE_PURPLE : NAV_INACTIVE_DAY;
    }

    static int categoryFocusBorder(boolean purpleTheme) {
        return categoryFocusBorder(purpleTheme, false);
    }

    static int categoryFocusBorder(boolean purpleTheme, boolean greenTheme) {
        if (greenTheme) return CATEGORY_FOCUS_BORDER_GREEN;
        return purpleTheme ? CATEGORY_FOCUS_BORDER_PURPLE : CATEGORY_FOCUS_BORDER;
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
    static final int NAV_INACTIVE_PURPLE = Color.rgb(78, 70, 90);
    static final int ORANGE_SWATCH = Color.rgb(191, 70, 12);
    static final int PURPLE_SWATCH = Color.rgb(81, 48, 143);
    static final int GREEN_SWATCH = Color.rgb(39, 133, 75);
    static final int NAV_INACTIVE_GREEN = Color.rgb(44, 75, 53);
    static final int CATEGORY_FOCUS_BORDER_GREEN = Color.rgb(39, 133, 75);
    static final int FOCUS_BORDER = Color.rgb(142, 142, 147);
    static final int NIGHT_TOGGLE_GRADIENT_TOP = Color.rgb(255, 255, 250);
    static final int CATEGORY_FOCUS_BORDER = Color.rgb(33, 150, 243);
    static final int CATEGORY_FOCUS_BORDER_PURPLE = Color.rgb(134, 102, 213);
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
