package io.github.messagerelay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class RelayNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch {
            if (AppSettingsRepository(applicationContext).current().persistentNotification) showStatusNotification()
        }
    }

    private fun showStatusNotification() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("relay_status", "消息接力运行状态", NotificationManager.IMPORTANCE_LOW))
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.notify(1001, NotificationCompat.Builder(this, "relay_status")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("消息接力运行中")
            .setContentText("重要通知将自动接力到 iPhone")
            .setContentIntent(intent)
            .setOngoing(true)
            .build())
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

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
