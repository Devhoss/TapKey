package com.tapkey.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything TapKey stores: six package names, the theme, the appearance and the tap window. */
public final class Prefs {

    private static final String FILE = "tapkey_settings";
    private static final String KEY_THEME = "widget_theme";
    private static final String KEY_APPEARANCE = "appearance";
    private static final String KEY_TAP_TIMEOUT = "tap_timeout_ms";
    private static final String KEY_PENDING_SEQUENCE = "pending_sequence";
    private static final String KEY_PENDING_SINCE = "pending_since";

    /** Bounds of the single-tap window, in milliseconds. */
    public static final int TAP_TIMEOUT_MIN = 100;
    public static final int TAP_TIMEOUT_MAX = 2000;
    public static final int TAP_TIMEOUT_DEFAULT = 350;

    private Prefs() {
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Package assigned to a sequence, or null when the shortcut is empty. */
    public static String getPackage(Context context, String sequence) {
        return store(context).getString(Shortcuts.prefKey(sequence), null);
    }

    public static void setPackage(Context context, String sequence, String packageName) {
        SharedPreferences.Editor editor = store(context).edit();
        if (packageName == null) {
            editor.remove(Shortcuts.prefKey(sequence));
        } else {
            editor.putString(Shortcuts.prefKey(sequence), packageName);
        }
        editor.apply();
    }

    public static int getTheme(Context context) {
        return store(context).getInt(KEY_THEME, ThemeStyle.DEFAULT);
    }

    public static void setTheme(Context context, int theme) {
        store(context).edit().putInt(KEY_THEME, theme).apply();
    }

    /** System / Light / Dark for the app screens; {@link Appearance#SYSTEM} by default. */
    public static int getAppearance(Context context) {
        return store(context).getInt(KEY_APPEARANCE, Appearance.SYSTEM);
    }

    public static void setAppearance(Context context, int appearance) {
        store(context).edit().putInt(KEY_APPEARANCE, appearance).apply();
    }

    /** How long a single digit waits for a possible second tap, in milliseconds. */
    public static int getTapTimeoutMs(Context context) {
        return clampTimeout(store(context).getInt(KEY_TAP_TIMEOUT, TAP_TIMEOUT_DEFAULT));
    }

    public static void setTapTimeoutMs(Context context, int milliseconds) {
        store(context).edit().putInt(KEY_TAP_TIMEOUT, clampTimeout(milliseconds)).apply();
    }

    /**
     * The digit currently held for its window, persisted so a restarted process can resolve it.
     * These writes are synchronous on purpose: they are tiny and once per tap, and an async one
     * is lost exactly when it matters — when the system kills the freshly restarted process.
     */
    static void setPendingSequence(Context context, String sequence, long since) {
        store(context).edit()
                .putString(KEY_PENDING_SEQUENCE, sequence)
                .putLong(KEY_PENDING_SINCE, since)
                .commit();
    }

    static String getPendingSequence(Context context) {
        return store(context).getString(KEY_PENDING_SEQUENCE, null);
    }

    static long getPendingSince(Context context) {
        return store(context).getLong(KEY_PENDING_SINCE, 0L);
    }

    static void clearPendingSequence(Context context) {
        store(context).edit()
                .remove(KEY_PENDING_SEQUENCE)
                .remove(KEY_PENDING_SINCE)
                .commit();
    }

    private static int clampTimeout(int milliseconds) {
        return Math.max(TAP_TIMEOUT_MIN, Math.min(TAP_TIMEOUT_MAX, milliseconds));
    }
}
