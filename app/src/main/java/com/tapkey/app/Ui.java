package com.tapkey.app;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.View;

/** Window-inset handling for edge-to-edge Android 15+ without any support library. */
final class Ui {

    private Ui() {
    }

    /** Resolves a palette attribute to its ARGB value for the activity's current theme. */
    static int themeColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    @SuppressWarnings("deprecation")
    static void applySystemBarInsets(Activity activity, int rootId) {
        View root = activity.findViewById(rootId);
        if (root == null) {
            return;
        }
        final int startLeft = root.getPaddingLeft();
        final int startTop = root.getPaddingTop();
        final int startRight = root.getPaddingRight();
        final int startBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    startLeft + insets.getSystemWindowInsetLeft(),
                    startTop + insets.getSystemWindowInsetTop(),
                    startRight + insets.getSystemWindowInsetRight(),
                    startBottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }
}
