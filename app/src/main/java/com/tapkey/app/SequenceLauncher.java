package com.tapkey.app;

import android.content.Context;
import android.content.Intent;

/** Turns a tap sequence into an app launch, or into the assignment screen when it cannot launch. */
public final class SequenceLauncher {

    private SequenceLauncher() {
    }

    /** Launch intent for the assigned app, or null when the shortcut is empty or its app is gone. */
    public static Intent launchIntent(Context context, String sequence) {
        String packageName = Prefs.getPackage(context, sequence);
        if (packageName == null) {
            return null;
        }
        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }

    /** Launches the assigned app. Returns false when the assignment screen was opened instead. */
    public static boolean launch(Context context, String sequence) {
        Intent target = launchIntent(context, sequence);
        if (target == null) {
            openAssignment(context, sequence);
            return false;
        }
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(target);
            return true;
        } catch (RuntimeException e) {
            openAssignment(context, sequence);
            return false;
        }
    }

    /** Opens TapKey straight on the picker for this sequence (used for empty and uninstalled shortcuts). */
    public static void openAssignment(Context context, String sequence) {
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_PICKER_SEQUENCE, sequence)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(intent);
        } catch (RuntimeException ignored) {
            // Nothing sensible left to do: the app cannot open its own screen.
        }
    }
}
