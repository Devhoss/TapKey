package com.tapkey.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Turns widget taps into launches and decides when one digit is a single and when it is a pair.
 *
 * <p>The widget sends a broadcast rather than starting an activity, because two taps of the same
 * digit would otherwise carry identical intents. The platform merges such a launch into the
 * activity that is already on top and, while the process is still starting, drops it outright:
 * the second tap of {@code 1 + 1} produced {@code START_DELIVERED_TO_TOP} into a waiting surface
 * that was finishing, so the pair silently degraded to a single. Broadcasts are never merged, so
 * every tap reaches the app.
 *
 * <p>Nothing is started per tap. The first tap of a sequence holds its digit here and counts down
 * {@link Prefs#getTapTimeoutMs}; a second tap inside that window resolves the pair at once, and
 * otherwise the held digit resolves on its own.
 *
 * <p>The receive is finished before the digit's window ends. Broadcasts to one receiver are
 * serialized, so a dispatch held open for the window would block the second tap until the window
 * had already expired and the single had already launched.
 */
public class TapReceiver extends BroadcastReceiver {

    public static final String ACTION_TAP = "com.tapkey.app.action.TAP";
    public static final String EXTRA_DIGIT = "com.tapkey.app.extra.DIGIT";

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    /** First digit of the sequence in progress and the timer that resolves it on its own. */
    private static String sPendingDigit;
    private static Runnable sPendingExpiry;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TAP.equals(intent.getAction())) {
            return;
        }
        int digit = intent.getIntExtra(EXTRA_DIGIT, 0);
        if (digit != 1 && digit != 2) {
            return;
        }
        onTap(context.getApplicationContext(), digit);
    }

    /** Runs on the main thread, one tap at a time. */
    private static void onTap(Context context, int digit) {
        String pending = sPendingDigit;
        if (pending == null) {
            String sequence = String.valueOf(digit);
            sPendingDigit = sequence;
            Runnable expiry = new Runnable() {
                @Override
                public void run() {
                    expire(context, sequence);
                }
            };
            sPendingExpiry = expiry;
            HANDLER.postDelayed(expiry, Prefs.getTapTimeoutMs(context));
            return;
        }
        clearPending();
        SequenceLauncher.launch(context, pending + digit);
    }

    /** Runs on the main thread once the single-tap window has passed. */
    private static void expire(Context context, String sequence) {
        if (!sequence.equals(sPendingDigit)) {
            return;
        }
        clearPending();
        SequenceLauncher.launch(context, sequence);
    }

    private static void clearPending() {
        if (sPendingExpiry != null) {
            HANDLER.removeCallbacks(sPendingExpiry);
            sPendingExpiry = null;
        }
        sPendingDigit = null;
    }
}
