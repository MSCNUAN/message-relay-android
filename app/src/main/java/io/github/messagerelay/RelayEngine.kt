package io.github.messagerelay

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object RelayEngine {
    val dedupe = DedupeWindow()
    suspend fun process(context: Context, message: RelayMessage) {
        val dao = RelayDatabase.get(context).relayDao()
        val rules = dao.rules().map { RelayRule(setOf(it.packageName), it.includes.lines().filter(String::isNotBlank), it.excludes.lines().filter(String::isNotBlank)) }
        if (rules.none { it.matches(message) } || !dedupe.accept(message, System.currentTimeMillis())) return
        val prefs = context.getSharedPreferences("relay", Context.MODE_PRIVATE)
        val paused = prefs.getBoolean("paused", false)
        if (paused) return
        val quiet = QuietHours(prefs.getBoolean("quiet_enabled", false))
        if (quiet.shouldQueue(message, java.time.LocalTime.now())) {
            dao.queue(QueuedMessage(packageName = message.packageName, app = message.app, title = message.title, body = message.body, createdAt = message.time))
            dao.trimQueue()
            return
        }
        val data = workDataOf("app" to message.app, "title" to message.title, "body" to message.body, "time" to message.time)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RelayWorker>().setInputData(data).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
    }
}

class RelayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = inputData.getString("app").orEmpty()
        val title = inputData.getString("title").orEmpty()
        val body = inputData.getString("body").orEmpty()
        val dao = RelayDatabase.get(applicationContext).relayDao()
        val channels = SecureStore(applicationContext).get("channels")?.let(ChannelSender::parse).orEmpty().filter(ChannelConfig::enabled)
        val template = MessageTemplate()
        val message = RelayMessage("", app, title, body, inputData.getLong("time", System.currentTimeMillis()))
        val secure = SecureStore(applicationContext)
        val relayUrl = secure.get("relay_url").orEmpty()
        val relayToken = secure.get("relay_token").orEmpty()
        val successes = if (relayUrl.isNotBlank()) {
            if (runCatching { ChannelSender.sendRelay(relayUrl, relayToken, channels, message, template.renderTitle(message), template.renderBody(message)) }.getOrDefault(false)) channels.size else 0
        } else channels.count { runCatching { ChannelSender.send(it, template.renderTitle(message), template.renderBody(message)) }.getOrDefault(false) }
        val status = when { channels.isEmpty() -> "未配置渠道"; successes == channels.size -> "成功"; successes > 0 -> "部分成功"; else -> "发送失败" }
        dao.addRecord(DeliveryRecord(app = app, title = title, status = status, createdAt = System.currentTimeMillis()))
        dao.trimRecords()
        return if (successes == channels.size && channels.isNotEmpty()) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
