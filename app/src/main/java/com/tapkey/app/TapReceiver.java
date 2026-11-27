package com.tapkey.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
 * <p>Delivery itself is not trusted, so the window is enforced three redundant ways. The handler
 * timer resolves the held digit when the window elapses. Every incoming tap first checks the age
 * of the pending digit and resolves it as a single when the window and a short grace have both
 * passed. And an alarm set at arm time outlives the process: the held digit is persisted, so a
 * process that is killed mid-window is rebuilt by the alarm it left behind instead of swallowing
 * the tap. The grace matters because aggressive battery management can freeze the process
 * mid-window: a second tap whose delivery lagged past the window is still a pair, and a
 * resolution that woke up late holds briefly longer for a second digit that may already be in
 * flight.
 *
 * <p>Taps inside a short debounce are ignored: one physical tap can arrive as two broadcasts
 * when a launcher double-fires the tap during a transition, and a duplicate must not complete a
 * pair the finger never typed.
 */
public class TapReceiver extends BroadcastReceiver {

    public static final String ACTION_TAP = "com.tapkey.app.action.TAP";
    public static final String ACTION_EXPIRE = "com.tapkey.app.action.EXPIRE";
    public static final String EXTRA_DIGIT = "com.tapkey.app.extra.DIGIT";
    public static final String EXTRA_SEQUENCE = "com.tapkey.app.extra.SEQUENCE";
    public static final String EXTRA_SINCE = "com.tapkey.app.extra.SINCE";

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
    /** A digit persisted by an earlier process this far past its window is discarded, not launched. */
    private static final long RESURRECT_LIMIT_MS = 5000;
    private static final int EXPIRY_REQUEST = 1;

    /** First digit of the sequence in progress, when it was recorded, and the timer that resolves it on its own. */
    private static String sPendingDigit;
    private static long sPendingSince;
    private static Runnable sPendingExpiry;
    private static boolean sPendingExtended;
    private static long sLastTapAt;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        Context app = context.getApplicationContext();
        String action = intent.getAction();
        if (ACTION_TAP.equals(action)) {
            int digit = intent.getIntExtra(EXTRA_DIGIT, 0);
            if (digit == 1 || digit == 2) {
                onTap(app, digit);
            }
        } else if (ACTION_EXPIRE.equals(action)) {
            String sequence = intent.getStringExtra(EXTRA_SEQUENCE);
            long since = intent.getLongExtra(EXTRA_SINCE, 0L);
            adoptIfLost(app, sequence, since);
            expire(app, sequence, since, Prefs.getTapTimeoutMs(app));
        }
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
            // This process may be younger than the widget: a digit the previous process armed
            // and died holding resolves here instead of vanishing, then the fresh arm starts.
            String orphaned = takeOrphaned(context, timeout, now);
            if (orphaned != null) {
                SequenceLauncher.launch(context, orphaned);
            }
            arm(context, digit, timeout, now);
            return;
        }
        if (now - sPendingSince >= timeout + STALE_GRACE_MS) {
            // The window and its grace expired without a resolution having gone through (the
            // host can be slow to deliver a tap); resolve the stale digit as a single before
            // starting a fresh sequence with the new tap.
            clearPending(context);
            SequenceLauncher.launch(context, pending);
            arm(context, digit, timeout, now);
            return;
        }
        clearPending(context);
        SequenceLauncher.launch(context, pending + digit);
    }

    /** Holds a digit for the window and schedules its resolution with the timer and the alarm. */
    private static void arm(Context context, int digit, int timeout, long now) {
        String sequence = String.valueOf(digit);
        sPendingDigit = sequence;
        sPendingSince = now;
        sPendingExtended = false;
        Prefs.setPendingSequence(context, sequence, now);
        scheduleHandlerExpiry(context, sequence, now, timeout);
        scheduleExpiryAlarm(context, sequence, now, timeout);
    }

    /** Runs on the main thread when the single-tap window has passed, by timer or by alarm. */
    private static void expire(Context context, String sequence, long since, int timeout) {
        if (!sequence.equals(sPendingDigit) || since != sPendingSince) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - since;
        if (!sPendingExtended && elapsed > timeout + OVERDUE_MS) {
            // This resolution woke up late: the process was suspended mid-window or restarted.
            // Hold a little longer so a second digit already in flight can still complete the
            // pair before the lone digit resolves on its own.
            sPendingExtended = true;
            scheduleHandlerExpiry(context, sequence, since, timeout);
            return;
        }
        clearPending(context);
        SequenceLauncher.launch(context, sequence);
    }

    /**
     * Adopts a digit persisted by an earlier process, when this process is younger than the
     * window it was armed in. Called before the alarm's expiry resolves it.
     */
    private static void adoptIfLost(Context context, String sequence, long since) {
        if (sPendingDigit != null || sequence == null
                || !sequence.equals(Prefs.getPendingSequence(context))
                || since != Prefs.getPendingSince(context)) {
            return;
        }
        sPendingDigit = sequence;
        sPendingSince = since;
        // Nothing was in flight or the tap would have restarted the process already: no grace.
        sPendingExtended = true;
    }

    /** Returns the digit an earlier process armed and died holding, if it is still fresh enough. */
    private static String takeOrphaned(Context context, int timeout, long now) {
        String sequence = Prefs.getPendingSequence(context);
        if (sequence == null) {
            return null;
        }
        Prefs.clearPendingSequence(context);
        if (now - Prefs.getPendingSince(context) > timeout + STALE_GRACE_MS + RESURRECT_LIMIT_MS) {
            return null;
        }
        return sequence;
    }

    /** Posts the handler timer that resolves the held digit at its deadline. */
    private static void scheduleHandlerExpiry(Context context, String sequence, long since, int timeout) {
        long deadline = since + timeout + (sPendingExtended ? STALE_GRACE_MS : 0);
        long delay = Math.max(0L, deadline - SystemClock.elapsedRealtime());
        Runnable expiry = () -> expire(context, sequence, since, Prefs.getTapTimeoutMs(context));
        sPendingExpiry = expiry;
        HANDLER.postDelayed(expiry, delay);
    }

    /** One alarm per held digit; it fires even when the process is dead, so no tap is lost. */
    private static void scheduleExpiryAlarm(Context context, String sequence, long since, int timeout) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) {
            return;
        }
        long triggerAt = since + timeout;
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, expiryOperation(context, sequence, since));
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, expiryOperation(context, sequence, since));
        }
    }

    private static void cancelExpiryAlarm(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) {
            return;
        }
        // Matching is on action, component and request code; the extras are irrelevant here.
        alarms.cancel(expiryOperation(context, null, 0L));
    }

    private static PendingIntent expiryOperation(Context context, String sequence, long since) {
        Intent intent = new Intent(context, TapReceiver.class)
                .setAction(ACTION_EXPIRE)
                .putExtra(EXTRA_SEQUENCE, sequence)
                .putExtra(EXTRA_SINCE, since);
        return PendingIntent.getBroadcast(context, EXPIRY_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void clearPending(Context context) {
        if (sPendingExpiry != null) {
            HANDLER.removeCallbacks(sPendingExpiry);
            sPendingExpiry = null;
        }
        cancelExpiryAlarm(context);
        Prefs.clearPendingSequence(context);
        sPendingDigit = null;
        sPendingSince = 0L;
        sPendingExtended = false;
    }
}
