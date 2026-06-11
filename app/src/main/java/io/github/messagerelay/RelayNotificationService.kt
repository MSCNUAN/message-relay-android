package io.github.messagerelay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.*

class RelayNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onListenerConnected() {
        super.onListenerConnected()
        if (getSharedPreferences("relay", MODE_PRIVATE).getBoolean("persistent_notification", true)) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("relay_status", "消息接力运行状态", NotificationManager.IMPORTANCE_LOW))
            val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            startForeground(1001, NotificationCompat.Builder(this, "relay_status").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("消息接力运行中").setContentText("重要通知将自动接力到 iPhone").setContentIntent(intent).setOngoing(true).build())
        }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val body = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        scope.launch { RelayEngine.process(applicationContext, RelayMessage(sbn.packageName, app, title, body, sbn.postTime)) }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
