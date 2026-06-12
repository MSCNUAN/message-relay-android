package io.github.messagerelay

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RelayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = runBlocking(Dispatchers.IO) {
            val settings = AppSettingsRepository(context).current()
            val dao = RelayDatabase.get(context).relayDao()
            Triple(settings.paused, dao.queuedCount().first(), dao.records().first().firstOrNull())
        }
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.relay_widget)
            views.setTextViewText(R.id.widget_status, if (snapshot.first) "消息接力已暂停" else "消息接力运行中")
            views.setTextViewText(R.id.widget_recent, "待发送：${snapshot.second} 条\n最近：${snapshot.third?.let { "${it.app} · ${it.status}" } ?: "暂无记录"}")
            views.setTextViewText(R.id.widget_toggle, if (snapshot.first) "恢复" else "暂停")
            views.setOnClickPendingIntent(R.id.widget_toggle, PendingIntent.getBroadcast(context, 0, Intent(context, RelayWidget::class.java).setAction("toggle"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "toggle") runBlocking { val repo = AppSettingsRepository(context); repo.setPaused(!repo.current().paused) }
        super.onReceive(context, intent)
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RelayWidget::class.java))
            if (ids.isNotEmpty()) RelayWidget().onUpdate(context, manager, ids)
        }
    }
}
