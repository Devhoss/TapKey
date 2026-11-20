package com.tapkey.app;

import android.content.Context;

/**
 * System / Light / Dark for the TapKey screens only. The home-screen widget keeps its own
 * ten themes, which are independent of this setting.
 */
final class Appearance {

    static final int SYSTEM = 0;
    static final int LIGHT = 1;
    static final int DARK = 2;

    private Appearance() {
    }

    /** Theme resource that paints the next activity: System follows values-night. */
    static int themeResource(Context context) {
        switch (Prefs.getAppearance(context)) {
            case LIGHT:
                return R.style.Theme_TapKey_Light;
            case DARK:
                return R.style.Theme_TapKey_Dark;
            default:
                return R.style.Theme_TapKey;
        }
    }
}
