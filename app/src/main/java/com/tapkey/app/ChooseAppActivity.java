package com.tapkey.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Application picker: lists installed launcher apps and assigns one to a tap sequence. */
public class ChooseAppActivity extends Activity {

    public static final String EXTRA_SEQUENCE = "com.tapkey.app.extra.SEQUENCE";

    /** Widget taps that stray onto this screen land in its first moments on top; ignore them. */
    private static final long STRAY_TOUCH_GUARD_MS = 800;

    private String sequence;
    private EditText searchField;
    private ListView listView;
    private View progressView;
    private View emptyView;
    private AppAdapter adapter;
    private long openedAt;

    private final List<AppEntry> allApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Match the appearance chosen on the main screen.
        setTheme(Appearance.themeResource(this));
        super.onCreate(savedInstanceState);
        String requested = getIntent().getStringExtra(EXTRA_SEQUENCE);
        sequence = Shortcuts.isValid(requested) ? requested : Shortcuts.ALL[0];
        openedAt = SystemClock.elapsedRealtime();
        setContentView(R.layout.activity_choose_app);
        Ui.applySystemBarInsets(this, R.id.picker_root);

        importViews();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // A second digit of a combo whose screen this picker took over would otherwise land on
        // the list and silently assign whichever app it hit.
        if (SystemClock.elapsedRealtime() - openedAt < STRAY_TOUCH_GUARD_MS) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void importViews() {
        ((ImageButton) findViewById(R.id.back_action)).setOnClickListener(view -> finish());

        TextView target = findViewById(R.id.picker_target);
        String packageName = Prefs.getPackage(this, sequence);
        CharSequence label = packageName == null ? null : labelOf(packageName);
        String current;
        if (packageName == null) {
            current = getString(R.string.picker_none);
        } else if (label == null) {
            current = getString(R.string.picker_missing);
        } else {
            current = getString(R.string.picker_current, label);
        }
        target.setText(getString(R.string.picker_target, sequence) + " \u00B7 " + current);

        progressView = findViewById(R.id.picker_progress);
        emptyView = findViewById(R.id.picker_empty);
        listView = findViewById(R.id.picker_list);
        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> assign(adapter.getItem(position)));

        searchField = findViewById(R.id.picker_search);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                applyFilter(text == null ? "" : text.toString());
            }
        });

        loadApps();
    }

    private CharSequence labelOf(String packageName) {
        try {
            CharSequence label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0));
            return label == null || label.length() == 0 ? packageName : label;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private void loadApps() {
        final PackageManager packageManager = getPackageManager();
        new Thread(() -> {
            final List<AppEntry> entries = queryLaunchableApps(packageManager);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                allApps.clear();
                allApps.addAll(entries);
                progressView.setVisibility(View.GONE);
                applyFilter(searchField.getText().toString());
            });
        }, "tapkey-app-scan").start();
    }

    private List<AppEntry> queryLaunchableApps(PackageManager packageManager) {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(launcher, 0);
        List<AppEntry> entries = new ArrayList<>(resolved.size());
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null) {
                continue;
            }
            String packageName = activity.packageName;
            if (packageName == null || !seen.add(packageName)) {
                continue;
            }
            CharSequence rawLabel = info.loadLabel(packageManager);
            String label = rawLabel == null || rawLabel.length() == 0
                    ? packageName : rawLabel.toString();
            Drawable icon;
            try {
                icon = info.loadIcon(packageManager);
            } catch (RuntimeException e) {
                icon = null;
            }
            entries.add(new AppEntry(packageName, label, icon));
        }
        Collections.sort(entries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry first, AppEntry second) {
                int byLabel = first.label.compareToIgnoreCase(second.label);
                return byLabel != 0 ? byLabel : first.packageName.compareTo(second.packageName);
            }
        });
        return entries;
    }

    private void applyFilter(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.getDefault());
        List<AppEntry> matches = new ArrayList<>(allApps.size());
        for (AppEntry entry : allApps) {
            if (query.length() == 0
                    || entry.label.toLowerCase(Locale.getDefault()).contains(query)
                    || entry.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                matches.add(entry);
            }
        }
        adapter.setItems(matches);
        boolean scanning = progressView.getVisibility() == View.VISIBLE;
        emptyView.setVisibility(!scanning && matches.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(matches.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void assign(AppEntry entry) {
        Prefs.setPackage(this, sequence, entry.packageName);
        TapKeyWidgetProvider.refreshAll(this);
        finish();
    }

    /** One installed application. */
    private static final class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private final class AppAdapter extends BaseAdapter {

        private final List<AppEntry> items = new ArrayList<>();

        void setItems(List<AppEntry> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_app, parent, false);
            }
            AppEntry entry = items.get(position);
            ((TextView) view.findViewById(R.id.app_name)).setText(entry.label);
            ((TextView) view.findViewById(R.id.app_package)).setText(entry.packageName);
            ImageView iconView = view.findViewById(R.id.app_icon);
            if (entry.icon != null) {
                iconView.setImageDrawable(entry.icon);
            } else {
                iconView.setImageResource(R.drawable.ic_add);
            }
            return view;
        }
    }
}
