package com.tapkey.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main screen: the six shortcut assignments and the short introduction.
 * Appearance, widget themes, tap timing and sharing live in {@link SettingsActivity}.
 */
public class MainActivity extends Activity {

    public static final String EXTRA_PICKER_SEQUENCE = "com.tapkey.app.extra.PICKER_SEQUENCE";

    private final View[] shortcutRows = new View[Shortcuts.ALL.length];

    private PackageManager packageManager;
    private int appliedThemeResource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must happen before the content is inflated: the palette lives in the theme.
        appliedThemeResource = Appearance.themeResource(this);
        setTheme(appliedThemeResource);
        super.onCreate(savedInstanceState);
        packageManager = getPackageManager();
        setContentView(R.layout.activity_main);
        Ui.applySystemBarInsets(this, R.id.main_root);
        buildShortcutRows();
        findViewById(R.id.settings_action).setOnClickListener(
                view -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.how_it_works_button).setOnClickListener(view -> showTutorial());
        handlePickerRequest(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePickerRequest(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The appearance is changed in Settings; repaint this screen when it comes back.
        if (appliedThemeResource != Appearance.themeResource(this)) {
            recreate();
            return;
        }
        for (int index = 0; index < shortcutRows.length; index++) {
            bindShortcutRow(index);
        }
    }

    // ---------------------------------------------------------------- shortcuts

    private void buildShortcutRows() {
        LinearLayout list = findViewById(R.id.shortcut_list);
        LayoutInflater inflater = getLayoutInflater();
        int gap = dp(10);
        for (int index = 0; index < Shortcuts.ALL.length; index++) {
            View row = inflater.inflate(R.layout.item_shortcut, list, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = gap;
            list.addView(row, params);
            shortcutRows[index] = row;

            final int rowIndex = index;
            final String sequence = Shortcuts.ALL[index];
            row.setOnClickListener(view -> openPicker(sequence));
            row.setOnLongClickListener(view -> {
                if (Prefs.getPackage(MainActivity.this, sequence) == null) {
                    return false;
                }
                Prefs.setPackage(MainActivity.this, sequence, null);
                bindShortcutRow(rowIndex);
                TapKeyWidgetProvider.refreshAll(MainActivity.this);
                Toast.makeText(MainActivity.this,
                        getString(R.string.shortcut_cleared, sequence), Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    private void bindShortcutRow(int index) {
        String sequence = Shortcuts.ALL[index];
        View row = shortcutRows[index];
        TextView name = row.findViewById(R.id.shortcut_name);
        TextView note = row.findViewById(R.id.shortcut_note);
        ImageView icon = row.findViewById(R.id.shortcut_icon);
        ((TextView) row.findViewById(R.id.shortcut_badge)).setText(sequence);

        String packageName = Prefs.getPackage(this, sequence);
        if (packageName == null) {
            name.setText(R.string.shortcut_empty_title);
            note.setText(getString(R.string.shortcut_note, sequence));
            icon.setImageResource(R.drawable.ic_add);
            return;
        }

        CharSequence label = appLabel(packageName);
        Drawable appIcon = appIcon(packageName);
        if (label == null || appIcon == null) {
            name.setText(R.string.shortcut_unavailable_title);
            note.setText(getString(R.string.shortcut_note_replace, sequence));
            icon.setImageResource(R.drawable.ic_add);
            return;
        }

        name.setText(label);
        note.setText(getString(R.string.shortcut_note, sequence));
        icon.setImageDrawable(appIcon);
    }

    private CharSequence appLabel(String packageName) {
        try {
            CharSequence label = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0));
            return label == null || label.length() == 0 ? packageName : label;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private Drawable appIcon(String packageName) {
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- actions

    private void openPicker(String sequence) {
        startActivity(new Intent(this, ChooseAppActivity.class)
                .putExtra(ChooseAppActivity.EXTRA_SEQUENCE, sequence));
    }

    private void handlePickerRequest(Intent intent) {
        if (intent == null) {
            return;
        }
        String sequence = intent.getStringExtra(EXTRA_PICKER_SEQUENCE);
        if (sequence == null) {
            return;
        }
        intent.removeExtra(EXTRA_PICKER_SEQUENCE);
        openPicker(Shortcuts.isValid(sequence) ? sequence : Shortcuts.ALL[0]);
    }

    private void showTutorial() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tutorial_title)
                .setMessage(R.string.tutorial_body)
                .setPositiveButton(R.string.tutorial_close, null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
