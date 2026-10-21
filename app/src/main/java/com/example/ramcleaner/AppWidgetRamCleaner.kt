package com.example.ramcleaner

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class AppWidgetRamCleaner : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_CLEAN = "com.example.ramcleaner.ACTION_WIDGET_CLEAN"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, AppWidgetRamCleaner::class.java)
            )
            for (id in ids) {
                updateWidgetView(context, manager, id)
            }
        }

        fun updateWidgetView(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_ram_cleaner)
            views.setTextViewText(R.id.widgetStatus, PrefsHelper.getWidgetStatus(context))

            val clickIntent = Intent(context, AppWidgetRamCleaner::class.java).apply {
                action = ACTION_WIDGET_CLEAN
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetIcon, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetStatus, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidgetView(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLEAN) {
            // Un Service normal (no en primer plano) arrancado por un tap del
            // usuario está exento de las restricciones de inicio en segundo
            // plano, así que esto funciona igual en Android reciente.
            context.startService(Intent(context, WidgetActionService::class.java))
        }
    }
}
