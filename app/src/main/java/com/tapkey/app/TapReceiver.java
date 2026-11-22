package com.tapkey.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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
 *
 * <p>The window is enforced twice. The handler timer resolves the held digit when the window
 * elapses, and every incoming tap first checks the age of the pending digit: when a broadcast
 * arrives after the window already passed (the host can be slow to deliver a tap), the stale,
 * expired digit is resolved as a single before the fresh sequence starts.
 */
public class TapReceiver extends BroadcastReceiver {

    public static final String ACTION_TAP = "com.tapkey.app.action.TAP";
    public static final String EXTRA_DIGIT = "com.tapkey.app.extra.DIGIT";

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    /** First digit of the sequence in progress, when it was recorded, and the timer that resolves it on its own. */
    private static String sPendingDigit;
    private static long sPendingSince;
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
        int timeout = Prefs.getTapTimeoutMs(context);
        String pending = sPendingDigit;
        if (pending == null) {
            arm(context, digit, timeout);
            return;
        }
        if (SystemClock.elapsedRealtime() - sPendingSince >= timeout) {
            // The window expired without the timer having fired; resolve the stale digit as a
            // single before starting a fresh sequence with the new tap.
            clearPending();
            SequenceLauncher.launch(context, pending);
            arm(context, digit, timeout);
            return;
        }
        clearPending();
        SequenceLauncher.launch(context, pending + digit);
    }

    /** Holds a digit for the window and schedules its resolution. */
    private static void arm(Context context, int digit, int timeout) {
        String sequence = String.valueOf(digit);
        sPendingDigit = sequence;
        sPendingSince = SystemClock.elapsedRealtime();
        long since = sPendingSince;
        Runnable expiry = new Runnable() {
            @Override
            public void run() {
                expire(context, sequence, since);
            }
        };
        sPendingExpiry = expiry;
        HANDLER.postDelayed(expiry, timeout);
    }

    /** Runs on the main thread once the single-tap window has passed. */
    private static void expire(Context context, String sequence, long since) {
        if (!sequence.equals(sPendingDigit) || since != sPendingSince) {
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
        sPendingSince = 0L;
    }
}
