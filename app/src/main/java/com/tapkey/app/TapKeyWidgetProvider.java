package com.tapkey.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * The widget itself. There is no periodic work: the widget is drawn on update, on install,
 * on theme change and on assignment change, and every tap is a plain PendingIntent.
 */
public class TapKeyWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        // All sizes share one weighted layout, so a redraw keeps the digits and divider aligned.
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    /** Redraws every placed widget, for example after an assignment or theme change. */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TapKeyWidgetProvider.class));
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_tapkey);
        ThemeStyle.applyToWidget(context, views, Prefs.getTheme(context));
        views.setOnClickPendingIntent(R.id.tap_one, digitPendingIntent(context, 1));
        views.setOnClickPendingIntent(R.id.tap_two, digitPendingIntent(context, 2));
        manager.updateAppWidget(appWidgetId, views);
    }

    private static PendingIntent digitPendingIntent(Context context, int digit) {
        Intent intent = new Intent(context, TapActivity.class)
                .setData(Uri.parse("tapkey://tap/" + digit))
                .putExtra(TapActivity.EXTRA_DIGIT, digit);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, digit, intent, flags);
    }
}
