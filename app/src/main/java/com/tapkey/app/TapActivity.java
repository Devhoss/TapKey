package com.tapkey.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Receives one widget tap and resolves it into a shortcut.
 *
 * <p>A single digit cannot be launched immediately because it may be the first half of
 * {@code 11}, {@code 12}, {@code 22} or {@code 21}. The first tap is therefore held for
 * {@link Prefs#getTapTimeoutMs} (350 ms by default, adjustable in Settings); a second tap inside
 * that window resolves the pair at once, and otherwise the held digit resolves on its own.
 *
 * <p>The activity must not keep a window on screen while a digit waits. Any window above the home
 * screen blocks touches to it — the platform puts an input sink over the activity record — so the
 * second tap could never reach the widget that turns it into the next PendingIntent. The activity
 * therefore resolves what it can and finishes during {@code onCreate}, before the system adds its
 * window, leaving the home screen fully tappable for the rest of the window. The second tap then
 * starts this activity again through its own PendingIntent and the pair resolves immediately.
 *
 * <p>The widget tap is a PendingIntent sent by the launcher, which is in the foreground, so both
 * the immediate pair launch and the delayed single-digit launch stay inside Android's
 * background-activity-start rules.
 */
public class TapActivity extends Activity {

    public static final String EXTRA_DIGIT = "com.tapkey.app.extra.DIGIT";

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    /** First digit of the sequence in progress, plus the context that resolves it. */
    private static String sPendingDigit;
    private static Runnable sPendingResolver;
    private static Context sAppContext;

    private boolean resolved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readDigit(getIntent());
    }

    private void readDigit(Intent intent) {
        int digit = intent == null ? 0 : intent.getIntExtra(EXTRA_DIGIT, 0);
        if (digit != 1 && digit != 2) {
            finishQuietly();
            return;
        }
        onDigit(digit);
    }

    private void onDigit(int digit) {
        String pending = sPendingDigit;
        if (pending == null) {
            sPendingDigit = String.valueOf(digit);
            sAppContext = getApplicationContext();
            Resolve resolve = new Resolve(sPendingDigit);
            sPendingResolver = resolve;
            HANDLER.postDelayed(resolve, Prefs.getTapTimeoutMs(this));
            // Finish now: the window is never added, so the home screen stays tappable and the
            // second tap arrives as a fresh PendingIntent instead of being swallowed by this one.
            finishQuietly();
        } else {
            String sequence = pending + digit;
            clearPending();
            launch(sequence);
        }
    }

    private void launch(String sequence) {
        if (resolved) {
            return;
        }
        resolved = true;
        SequenceLauncher.launch(this, sequence);
        finishQuietly();
    }

    private void finishQuietly() {
        finish();
        overridePendingTransition(0, 0);
    }

    private static synchronized void clearPending() {
        if (sPendingResolver != null) {
            HANDLER.removeCallbacks(sPendingResolver);
        }
        sPendingResolver = null;
        sPendingDigit = null;
    }

    /** Runs on the main thread once the single-tap window has passed. */
    private static final class Resolve implements Runnable {

        private final String sequence;

        Resolve(String sequence) {
            this.sequence = sequence;
        }

        @Override
        public void run() {
            if (sPendingResolver != this) {
                return;
            }
            clearPending();
            Context context = sAppContext;
            if (context == null) {
                return;
            }
            SequenceLauncher.launch(context, sequence);
        }
    }
}
