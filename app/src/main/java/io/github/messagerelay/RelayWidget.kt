package io.github.messagerelay

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class RelayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("relay", Context.MODE_PRIVATE)
        ids.forEach { id ->
            val paused = prefs.getBoolean("paused", false)
            val views = RemoteViews(context.packageName, R.layout.relay_widget)
            views.setTextViewText(R.id.widget_status, if (paused) "消息接力已暂停" else "消息接力运行中")
            views.setTextViewText(R.id.widget_recent, prefs.getString("widget_recent", "最近：暂无记录"))
            views.setTextViewText(R.id.widget_toggle, if (paused) "恢复" else "暂停")
            views.setOnClickPendingIntent(R.id.widget_toggle, PendingIntent.getBroadcast(context, 0, Intent(context, RelayWidget::class.java).setAction("toggle"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            manager.updateAppWidget(id, views)
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "toggle") {
            val prefs = context.getSharedPreferences("relay", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("paused", !prefs.getBoolean("paused", false)).apply()
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(android.content.ComponentName(context, RelayWidget::class.java)))
        }
        super.onReceive(context, intent)
    }
}
