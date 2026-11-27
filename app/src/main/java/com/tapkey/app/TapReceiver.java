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
 * arrives after the window already passed (the host can be slow to deliver a tap, or the process
 * was frozen mid-window by battery management), the digit is still a hair inside a short grace
 * before it is treated as stale, and a resolution that itself woke up late holds briefly longer
 * for a second digit that may already be in flight. A process killed mid-window simply drops the
 * tap: the held digit dies with the process, and nothing is launched late.
 *
 * <p>Taps inside a short debounce are ignored: one physical tap can arrive as two broadcasts
 * when a launcher double-fires the tap during a transition, and a duplicate must not complete a
 * pair the finger never typed.
 */
public class TapReceiver extends BroadcastReceiver {

    public static final String ACTION_TAP = "com.tapkey.app.action.TAP";
    public static final String EXTRA_DIGIT = "com.tapkey.app.extra.DIGIT";

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    /** One physical tap cannot repeat this fast; launcher double-fires and duplicates can. */
    private static final long DEBOUNCE_MS = 70;
    /**
     * Extra delay a second tap may lag its window by and still complete the pair, and extra
     * time a late resolution holds before the lone digit resolves on its own.
     */
    private static final long STALE_GRACE_MS = 200;
    /** A resolution later than this was suspended mid-window (frozen process) and gets the grace. */
    private static final long OVERDUE_MS = 50;

    /** First digit of the sequence in progress, when it was recorded, and the timer that resolves it on its own. */
    private static String sPendingDigit;
    private static long sPendingSince;
    private static Runnable sPendingExpiry;
    private static boolean sPendingExtended;
    private static long sLastTapAt;

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
        long now = SystemClock.elapsedRealtime();
        if (now - sLastTapAt < DEBOUNCE_MS) {
            return;
        }
        sLastTapAt = now;
        int timeout = Prefs.getTapTimeoutMs(context);
        String pending = sPendingDigit;
        if (pending == null) {
            arm(context, digit, timeout, now);
            return;
        }
        if (now - sPendingSince >= timeout + STALE_GRACE_MS) {
            // The window and its grace expired without a resolution having gone through (the
            // host can be slow to deliver a tap); resolve the stale digit as a single before
            // starting a fresh sequence with the new tap.
            clearPending();
            SequenceLauncher.launch(context, pending);
            arm(context, digit, timeout, now);
            return;
        }
        clearPending();
        SequenceLauncher.launch(context, pending + digit);
    }

    /** Holds a digit for the window and schedules its resolution. */
    private static void arm(Context context, int digit, int timeout, long now) {
        String sequence = String.valueOf(digit);
        sPendingDigit = sequence;
        sPendingSince = now;
        sPendingExtended = false;
        scheduleHandlerExpiry(context, sequence, now, timeout);
    }

    /** Runs on the main thread once the single-tap window has passed. */
    private static void expire(Context context, String sequence, long since, int timeout) {
        if (!sequence.equals(sPendingDigit) || since != sPendingSince) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - since;
        if (!sPendingExtended && elapsed > timeout + OVERDUE_MS) {
            // This resolution woke up late: the process was suspended mid-window. Hold a
            // little longer so a second digit already in flight can still complete the pair
            // before the lone digit resolves on its own.
            sPendingExtended = true;
            scheduleHandlerExpiry(context, sequence, since, timeout);
            return;
        }
        clearPending();
        SequenceLauncher.launch(context, sequence);
    }

    /** Posts the handler timer that resolves the held digit at its deadline. */
    private static void scheduleHandlerExpiry(Context context, String sequence, long since, int timeout) {
        long deadline = since + timeout + (sPendingExtended ? STALE_GRACE_MS : 0);
        long delay = Math.max(0L, deadline - SystemClock.elapsedRealtime());
        Runnable expiry = () -> expire(context, sequence, since, Prefs.getTapTimeoutMs(context));
        sPendingExpiry = expiry;
        HANDLER.postDelayed(expiry, delay);
    }

    private static void clearPending() {
        if (sPendingExpiry != null) {
            HANDLER.removeCallbacks(sPendingExpiry);
            sPendingExpiry = null;
        }
        sPendingDigit = null;
        sPendingSince = 0L;
        sPendingExtended = false;
    }
}
