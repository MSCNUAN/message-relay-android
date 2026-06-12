package io.github.messagerelay

import android.content.Context
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object RelayEngine {
    val dedupe = DedupeWindow()

    suspend fun process(context: Context, message: RelayMessage) {
        val dao = RelayDatabase.get(context).relayDao()
        val rule = dao.rule(message.packageName) ?: return
        val relayRule = RelayRule(setOf(rule.packageName), rule.includes.lines().filter(String::isNotBlank), rule.excludes.lines().filter(String::isNotBlank))
        if (!relayRule.matches(message) || !dedupe.accept(message, System.currentTimeMillis())) return
        val settings = AppSettingsRepository(context).current()
        if (settings.paused) return
        if (settings.quietHours().shouldQueue(message, LocalTime.now())) {
            dao.queue(QueuedMessage(packageName = message.packageName, app = message.app, title = message.title, body = message.body, createdAt = message.time))
            dao.trimQueue()
            scheduleQueueFlush(context, settings)
            return
        }
        enqueue(context, message, templateId = rule.templateId)
    }

    fun enqueue(context: Context, message: RelayMessage, delayed: Boolean = false, templateId: String = "") {
        val data = workDataOf("package" to message.packageName, "app" to message.app, "title" to message.title, "body" to message.body, "time" to message.time, "delayed" to delayed, "templateId" to templateId)
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<RelayWorker>().setInputData(data).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
        )
    }

    private fun scheduleQueueFlush(context: Context, settings: AppSettings) {
        val now = LocalTime.now()
        var minutes = java.time.Duration.between(now, LocalTime.parse(settings.quietEnd)).toMinutes()
        if (minutes <= 0) minutes += 24 * 60
        WorkManager.getInstance(context).enqueueUniqueWork(
            "quiet-queue-flush", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<QueueFlushWorker>().setInitialDelay(minutes, TimeUnit.MINUTES).build()
        )
    }
}

class RelayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val message = RelayMessage(inputData.getString("package").orEmpty(), inputData.getString("app").orEmpty(), inputData.getString("title").orEmpty(), inputData.getString("body").orEmpty(), inputData.getLong("time", System.currentTimeMillis()))
        val dao = RelayDatabase.get(applicationContext).relayDao()
        val channels = ChannelSelection.singleEnabled(SecureStore(applicationContext).get("channels")?.let(ChannelSender::parse).orEmpty())
        val settings = AppSettingsRepository(applicationContext).current()
        dao.ensureTemplates(settings)
        val requestedTemplate = inputData.getString("templateId").orEmpty().ifBlank { dao.rule(message.packageName)?.templateId ?: TemplateCatalog.GENERAL_ID }
        val template = (dao.template(requestedTemplate)?.definition() ?: TemplateCatalog.byId(TemplateCatalog.GENERAL_ID)).template()
        val secure = SecureStore(applicationContext)
        val relayUrl = secure.get("relay_url").orEmpty()
        val results = if (relayUrl.isNotBlank()) ChannelSender.sendRelay(relayUrl, secure.get("relay_token").orEmpty(), channels, message, template.renderTitle(message), template.renderBody(message))
        else channels.map { ChannelSender.send(it, template.renderTitle(message), template.renderBody(message)) }
        val successes = results.count(DeliveryResult::success)
        val status = when { channels.isEmpty() -> "未配置渠道"; successes == channels.size -> "成功"; successes > 0 -> "部分成功"; else -> "发送失败" }
        val resultJson = JSONArray().apply { results.forEach { put(JSONObject().put("channel", it.channel).put("success", it.success).put("httpStatus", it.httpStatus).put("retryable", it.retryable).put("error", it.error)) } }.toString()
        dao.addRecord(DeliveryRecord(packageName = message.packageName, app = message.app, title = message.title, body = message.body, status = status, channelResults = resultJson, createdAt = System.currentTimeMillis(), delayed = inputData.getBoolean("delayed", false)))
        dao.trimRecords()
        RelayWidget.refresh(applicationContext)
        return when {
            results.isNotEmpty() && results.all(DeliveryResult::success) -> Result.success()
            results.any(DeliveryResult::retryable) && runAttemptCount < 2 -> Result.retry()
            else -> Result.failure()
        }
    }
}

class QueueFlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = RelayDatabase.get(applicationContext).relayDao()
        dao.queued().forEach {
            val templateId = dao.rule(it.packageName)?.templateId ?: TemplateCatalog.GENERAL_ID
            RelayEngine.enqueue(applicationContext, RelayMessage(it.packageName, it.app, it.title, it.body, it.createdAt), delayed = true, templateId = templateId)
            dao.removeQueued(it.id)
        }
        return Result.success()
    }
}
