package io.github.messagerelay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
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
        val content = if (sbn.packageName == "com.tencent.mm") {
            WeChatNotificationParser.parse(sbn)
        } else {
            NotificationContentExtractor.extract(sbn)
        }
        if (content.title.isBlank() && content.body.isBlank()) return
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        if (SmsDuplicateGuard.shouldSuppressNotification(sbn.packageName, content.title, content.body, sbn.postTime)) return
        val relayApp = if (sbn.packageName == "com.tencent.mm" && content.title.startsWith("微信 · ")) content.title else app
        scope.launch { RelayEngine.process(applicationContext, RelayMessage(sbn.packageName, relayApp, content.title, content.body, sbn.postTime)) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
