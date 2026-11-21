package com.tapkey.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Everything that is not a shortcut assignment: the app appearance, the widget theme,
 * the single-tap window and sharing.
 */
public class SettingsActivity extends Activity {

    private static final int[] APPEARANCE_MODES = {
            Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK
    };

    private final View[] themeCells = new View[ThemeStyle.COUNT];
    private final TextView[] appearancePills = new TextView[APPEARANCE_MODES.length];

    private TextView timeoutValue;
    private int appliedThemeResource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must happen before the content is inflated: the palette lives in the theme.
        appliedThemeResource = Appearance.themeResource(this);
        setTheme(appliedThemeResource);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.applySystemBarInsets(this, R.id.settings_root);

        findViewById(R.id.back_action).setOnClickListener(view -> finish());
        findViewById(R.id.share_action).setOnClickListener(view -> share());

        buildAppearanceRow();
        buildThemeGrid();

        timeoutValue = findViewById(R.id.timeout_value);
        findViewById(R.id.timeout_row).setOnClickListener(view -> editTapTimeout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The appearance may have been changed on another screen; repaint this one.
        if (appliedThemeResource != Appearance.themeResource(this)) {
            recreate();
            return;
        }
        bindAppearanceSelection();
        bindThemeSelection();
        bindTimeout();
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
            View preview = inflater.inflate(ThemeStyle.layout(theme), box, false);
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
            int ring;
            if (theme == ThemeStyle.CLEAR) {
                ring = isSelected ? R.drawable.theme_ring_clear_selected : R.drawable.theme_ring_clear;
            } else {
                ring = isSelected ? R.drawable.theme_ring_selected : R.drawable.theme_ring_idle;
            }
            cell.findViewById(R.id.theme_preview_box).setBackgroundResource(ring);
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

    // ---------------------------------------------------------------- tap timing

    private void bindTimeout() {
        timeoutValue.setText(getString(R.string.tap_timeout_value, Prefs.getTapTimeoutMs(this)));
    }

    private void editTapTimeout() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(Prefs.getTapTimeoutMs(this)));
        input.setSelection(input.getText().length());
        input.setGravity(Gravity.CENTER);
        input.setTextSize(18);

        FrameLayout container = new FrameLayout(this);
        int padding = dp(20);
        container.setPadding(padding, dp(4), padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.tap_timeout_title)
                .setMessage(getString(R.string.tap_timeout_message,
                        Prefs.TAP_TIMEOUT_MIN, Prefs.TAP_TIMEOUT_MAX))
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.tap_timeout_save,
                        (dialog, which) -> saveTapTimeout(input))
                .show();
    }

    private void saveTapTimeout(EditText input) {
        int milliseconds;
        try {
            milliseconds = Integer.parseInt(input.getText().toString().trim());
        } catch (NumberFormatException e) {
            milliseconds = -1;
        }
        if (milliseconds < Prefs.TAP_TIMEOUT_MIN || milliseconds > Prefs.TAP_TIMEOUT_MAX) {
            Toast.makeText(this, getString(R.string.tap_timeout_invalid,
                    Prefs.TAP_TIMEOUT_MIN, Prefs.TAP_TIMEOUT_MAX), Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.setTapTimeoutMs(this, milliseconds);
        bindTimeout();
    }

    // ---------------------------------------------------------------- share

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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
