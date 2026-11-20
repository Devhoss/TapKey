package com.tapkey.app;

/** The six tap sequences supported by the widget, in display order. */
public final class Shortcuts {

    public static final String[] ALL = {"1", "2", "11", "22", "12", "21"};

    private static final String PREF_PREFIX = "shortcut_";

    private Shortcuts() {
    }

    public static String prefKey(String sequence) {
        return PREF_PREFIX + sequence;
    }

    public static boolean isValid(String sequence) {
        if (sequence == null) {
            return false;
        }
        for (String candidate : ALL) {
            if (candidate.equals(sequence)) {
                return true;
            }
        }
        return false;
    }
}
