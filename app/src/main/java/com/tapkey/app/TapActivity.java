package com.tapkey.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Invisible activity that receives one widget tap and resolves it into a shortcut.
 *
 * <p>A single digit cannot be launched immediately because it may be the first half of
 * {@code 11}, {@code 12}, {@code 22} or {@code 21}. The first tap is therefore held for
 * {@link #SEQUENCE_TIMEOUT_MS}; a second tap inside that window resolves the pair at once,
 * and otherwise the held digit resolves on its own.
 *
 * <p>Timing happens while this activity is resumed rather than inside a broadcast receiver,
 * so the follow-up launch is an ordinary foreground app launch and stays clear of Android's
 * background activity start restrictions.
 */
public class TapActivity extends Activity {

    public static final String EXTRA_DIGIT = "com.tapkey.app.extra.DIGIT";

    /** How long a single digit waits for a possible second tap. */
    public static final long SEQUENCE_TIMEOUT_MS = 350L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    /** First digit of the sequence in progress, shared so process/activity recreation cannot lose it. */
    private static String sPendingDigit;
    private static Runnable sPendingResolver;

    private boolean resolved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readDigit(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readDigit(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!resolved) {
            resolveOnDestroy();
        }
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
            Resolve resolve = new Resolve(sPendingDigit, this);
            sPendingResolver = resolve;
            HANDLER.postDelayed(resolve, SEQUENCE_TIMEOUT_MS);
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

    /**
     * Safety net: if the tap surface goes away while a digit is still held, resolve that digit
     * rather than swallowing the tap.
     */
    private void resolveOnDestroy() {
        if (!(sPendingResolver instanceof Resolve)) {
            return;
        }
        Resolve current = (Resolve) sPendingResolver;
        if (current.owner != this) {
            return;
        }
        String sequence = sPendingDigit;
        clearPending();
        if (sequence == null) {
            return;
        }
        resolved = true;
        Intent target = SequenceLauncher.launchIntent(getApplicationContext(), sequence);
        if (target == null) {
            return;
        }
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            getApplication().startActivity(target);
        } catch (RuntimeException ignored) {
            // The app was destroyed mid-gesture and the system refused the launch.
        }
    }

    /** Runs on the main thread once the single-tap window has passed. */
    private static final class Resolve implements Runnable {

        private final String sequence;
        private final TapActivity owner;

        Resolve(String sequence, TapActivity owner) {
            this.sequence = sequence;
            this.owner = owner;
        }

        @Override
        public void run() {
            if (sPendingResolver != this) {
                return;
            }
            clearPending();
            owner.launch(sequence);
        }
    }
}
