package com.tapkey.app;

import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

/**
 * A theme is only three values: a rounded background, a digit colour and a divider colour.
 * The same values drive the real widget ({@link RemoteViews}) and the in-app preview
 * ({@link #applyToView}), so the preview cannot drift from the home screen.
 */
public final class ThemeStyle {

    public static final int COUNT = 10;
    public static final int DEFAULT = 1; // Black

    /** Clear is the only theme with shadowed digits, so it needs its own copy of the layout. */
    private static final int CLEAR = 3;

    private static final int[] BACKGROUND = {
            R.drawable.widget_bg_gold,
            R.drawable.widget_bg_black,
            R.drawable.widget_bg_ivory,
            R.drawable.widget_bg_clear,
            R.drawable.widget_bg_sapphire,
            R.drawable.widget_bg_invisible,
            R.drawable.widget_bg_slate,
            R.drawable.widget_bg_coral,
            R.drawable.widget_bg_ruby,
            R.drawable.widget_bg_champagne,
    };

    private static final int[] DIGIT_COLOR = {
            R.color.on_gold,
            R.color.on_black,
            R.color.on_ivory,
            R.color.on_clear,
            R.color.on_sapphire,
            R.color.on_invisible,
            R.color.on_slate,
            R.color.on_coral,
            R.color.on_ruby,
            R.color.on_champagne,
    };

    private static final int[] DIVIDER_COLOR = {
            R.color.div_gold,
            R.color.div_black,
            R.color.div_ivory,
            R.color.div_clear,
            R.color.div_sapphire,
            R.color.div_invisible,
            R.color.div_slate,
            R.color.div_coral,
            R.color.div_ruby,
            R.color.div_champagne,
    };

    private ThemeStyle() {
    }

    private static int clamp(int theme) {
        return theme >= 0 && theme < COUNT ? theme : DEFAULT;
    }

    public static String name(Context context, int theme) {
        return context.getResources().getStringArray(R.array.theme_names)[clamp(theme)];
    }

    public static int background(int theme) {
        return BACKGROUND[clamp(theme)];
    }

    /** The layout to inflate for a theme; only Clear differs. */
    public static int layout(int theme) {
        return clamp(theme) == CLEAR ? R.layout.widget_tapkey_clear : R.layout.widget_tapkey;
    }

    public static int digitColor(Context context, int theme) {
        return context.getColor(DIGIT_COLOR[clamp(theme)]);
    }

    public static int dividerColor(Context context, int theme) {
        return context.getColor(DIVIDER_COLOR[clamp(theme)]);
    }

    /** Applies a theme to an inflated copy of {@code widget_tapkey.xml}. */
    public static void applyToView(Context context, View root, int theme) {
        root.setBackgroundResource(background(theme));
        TextView one = root.findViewById(R.id.tap_one);
        TextView two = root.findViewById(R.id.tap_two);
        int digit = digitColor(context, theme);
        one.setTextColor(digit);
        two.setTextColor(digit);
        root.findViewById(R.id.widget_divider).setBackgroundColor(dividerColor(context, theme));
    }

    /** Applies the same theme to the real home-screen widget. */
    public static void applyToWidget(Context context, RemoteViews views, int theme) {
        views.setInt(R.id.widget_root, "setBackgroundResource", background(theme));
        int digit = digitColor(context, theme);
        views.setTextColor(R.id.tap_one, digit);
        views.setTextColor(R.id.tap_two, digit);
        views.setInt(R.id.widget_divider, "setBackgroundColor", dividerColor(context, theme));
    }
}
