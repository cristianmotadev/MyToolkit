package com.mtp.mytoolsproject;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Widget de atalho na tela inicial: acesso rápido ao app e a duas das
 * ferramentas mais usadas, sem precisar abrir o app primeiro.
 */
public class MyToolkitWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_my_toolkit);

            views.setOnClickPendingIntent(R.id.widgetBtnAbrirApp, criarPendingIntent(context, MainActivity.class, 0));
            views.setOnClickPendingIntent(R.id.widgetBtnRadarDispositivos, criarPendingIntent(context, NetworkScannerActivity.class, 1));
            views.setOnClickPendingIntent(R.id.widgetBtnRadarWifi, criarPendingIntent(context, WifiScannerActivity.class, 2));

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    private PendingIntent criarPendingIntent(Context context, Class<?> activity, int requestCode) {
        Intent intent = new Intent(context, activity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }
}
