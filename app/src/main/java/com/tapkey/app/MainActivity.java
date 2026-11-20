package com.tapkey.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Main screen: the six shortcuts, the theme gallery and the short introduction. */
public class MainActivity extends Activity {

    public static final String EXTRA_PICKER_SEQUENCE = "com.tapkey.app.extra.PICKER_SEQUENCE";

    private static final int[] APPEARANCE_MODES = {
            Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK
    };

    private final View[] shortcutRows = new View[Shortcuts.ALL.length];
    private final View[] themeCells = new View[ThemeStyle.COUNT];
    private final TextView[] appearancePills = new TextView[APPEARANCE_MODES.length];

    private PackageManager packageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must happen before the content is inflated: the palette lives in the theme.
        setTheme(Appearance.themeResource(this));
        super.onCreate(savedInstanceState);
        packageManager = getPackageManager();
        setContentView(R.layout.activity_main);
        Ui.applySystemBarInsets(this, R.id.main_root);
        buildAppearanceRow();
        buildShortcutRows();
        buildThemeGrid();
        findViewById(R.id.share_action).setOnClickListener(view -> share());
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
        for (int index = 0; index < shortcutRows.length; index++) {
            bindShortcutRow(index);
        }
        bindAppearanceSelection();
        bindThemeSelection();
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

    // ---------------------------------------------------------------- appearance

    private void buildAppearanceRow() {
        LinearLayout row = findViewById(R.id.appearance_row);
        int[] labels = {
                R.string.appearance_system, R.string.appearance_light, R.string.appearance_dark
        };
        for (int index = 0; index < APPEARANCE_MODES.length; index++) {
            TextView pill = (TextView) getLayoutInflater()
                    .inflate(R.layout.item_appearance, row, false);
            pill.setText(labels[index]);
            final int mode = APPEARANCE_MODES[index];
            pill.setOnClickListener(view -> selectAppearance(mode));
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (index > 0) {
                params.setMarginStart(dp(8));
            }
            row.addView(pill, params);
            appearancePills[index] = pill;
        }
    }

    private void bindAppearanceSelection() {
        int current = Prefs.getAppearance(this);
        for (int index = 0; index < appearancePills.length; index++) {
            boolean selected = APPEARANCE_MODES[index] == current;
            appearancePills[index].setBackgroundResource(
                    selected ? R.drawable.pill_selected : R.drawable.pill_idle);
            appearancePills[index].setTextColor(selected
                    ? getColor(R.color.on_accent)
                    : Ui.themeColor(this, R.attr.paletteTextSecondary));
        }
    }

    private void selectAppearance(int mode) {
        if (Prefs.getAppearance(this) == mode) {
            return;
        }
        Prefs.setAppearance(this, mode);
        recreate();
    }

    // ---------------------------------------------------------------- themes

    private void buildThemeGrid() {
        GridLayout grid = findViewById(R.id.theme_grid);
        LayoutInflater inflater = getLayoutInflater();
        int margin = dp(4);
        for (int theme = 0; theme < ThemeStyle.COUNT; theme++) {
            View cell = inflater.inflate(R.layout.item_theme, grid, false);
            FrameLayout box = cell.findViewById(R.id.theme_preview_box);
            View preview = inflater.inflate(R.layout.widget_tapkey, box, false);
            ThemeStyle.applyToView(this, preview, theme);
            box.addView(preview);
            ((TextView) cell.findViewById(R.id.theme_name)).setText(ThemeStyle.name(this, theme));

            GridLayout.LayoutParams params = (GridLayout.LayoutParams) cell.getLayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(theme % 2, 1, 1f);
            params.setMargins(margin, margin, margin, dp(8));
            grid.addView(cell, params);

            final int themeIndex = theme;
            cell.setOnClickListener(view -> selectTheme(themeIndex));
            themeCells[theme] = cell;
        }
    }

    private void bindThemeSelection() {
        int selected = Prefs.getTheme(this);
        for (int theme = 0; theme < themeCells.length; theme++) {
            View cell = themeCells[theme];
            boolean isSelected = theme == selected;
            cell.findViewById(R.id.theme_preview_box).setBackgroundResource(
                    isSelected ? R.drawable.theme_ring_selected : R.drawable.theme_ring_idle);
            TextView name = cell.findViewById(R.id.theme_name);
            name.setTextColor(getColor(isSelected ? R.color.accent : R.color.text_secondary));
            name.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void selectTheme(int theme) {
        if (Prefs.getTheme(this) == theme) {
            return;
        }
        Prefs.setTheme(this, theme);
        bindThemeSelection();
        TapKeyWidgetProvider.refreshAll(this);
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

    /**
     * Promotes the app: plain text only, no stream, no file URI, no FileProvider.
     * The public link is appended once {@code share_link} is filled in.
     */
    private void share() {
        String text = getString(R.string.share_text);
        String link = getString(R.string.share_link).trim();
        if (!link.isEmpty()) {
            text = text + "\n\n" + link;
        }
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                .putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
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
