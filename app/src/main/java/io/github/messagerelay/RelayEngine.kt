package io.github.messagerelay

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
        if (!relayRule.matches(message)) {
            addFilteredRecord(context, dao, message, "规则未命中或命中排除关键词")
            return
        }
        if (!dedupe.accept(message, System.currentTimeMillis())) return
        val settings = AppSettingsRepository(context).current()
        if (settings.paused) return
        when (val screenDecision = ScreenOffOnlyPolicy.decide(rule.screenOffOnly, AndroidLockStateProvider(context))) {
            is ScreenOffOnlyDecision.Allow -> Unit
            is ScreenOffOnlyDecision.Filter -> {
                addFilteredRecord(context, dao, message, screenDecision.reason)
                return
            }
        }
        if (settings.quietHours().shouldQueue(message, LocalTime.now())) {
            addFilteredRecord(context, dao, message, "免打扰时段已跳过，结束后不会补发旧消息")
            dao.queue(QueuedMessage(packageName = message.packageName, app = message.app, title = message.title, body = message.body, createdAt = message.time))
            dao.trimQueue()
            scheduleQueueFlush(context, settings)
            return
        }
        val timingDelay = settings.deliveryDelaySeconds.toLong().coerceAtLeast(0) * 1000
        val mergeDelay = if (settings.mergeNotifications) settings.mergeWindowSeconds.toLong().coerceAtLeast(1) * 1000 else 0
        enqueue(
            context,
            message,
            templateId = rule.templateId,
            delayMillis = maxOf(timingDelay, mergeDelay),
            uniqueName = if (settings.mergeNotifications) "merge-${message.packageName}" else null
        )
    }

    suspend fun processCallEvent(context: Context, decision: CallStateDecision) {
        val dao = RelayDatabase.get(context).relayDao()
        val rule = dao.allRules().firstOrNull {
            it.enabled && TemplateCatalog.recommend(it.appName, it.packageName) == "phone"
        } ?: return
        if (decision.eventType !in CallEventTypes.parse(rule.enabledCallEventTypes)) return
        val message = RelayMessage(rule.packageName, rule.appName, decision.title, decision.body, System.currentTimeMillis())
        val settings = AppSettingsRepository(context).current()
        if (settings.paused) return
        when (val screenDecision = ScreenOffOnlyPolicy.decide(rule.screenOffOnly, AndroidLockStateProvider(context))) {
            is ScreenOffOnlyDecision.Allow -> Unit
            is ScreenOffOnlyDecision.Filter -> {
                addFilteredRecord(context, dao, message, "${screenDecision.reason}；应用规则：仅息屏时推送；处理结果：未发送，且不会延迟补发")
                return
            }
        }
        enqueue(context, message, templateId = rule.templateId)
    }

    suspend fun processSmsEvent(context: Context, decision: SmsStateDecision) {
        val dao = RelayDatabase.get(context).relayDao()
        val rule = dao.allRules().firstOrNull {
            it.enabled && TemplateCatalog.recommend(it.appName, it.packageName) == "sms"
        } ?: return
        val message = RelayMessage(rule.packageName, rule.appName, decision.title, decision.body, System.currentTimeMillis())
        val relayRule = RelayRule(setOf(rule.packageName), rule.includes.lines().filter(String::isNotBlank), rule.excludes.lines().filter(String::isNotBlank))
        if (!relayRule.matches(message)) {
            addFilteredRecord(context, dao, message, "规则未命中或命中排除关键词")
            return
        }
        val settings = AppSettingsRepository(context).current()
        if (settings.paused) return
        when (val screenDecision = ScreenOffOnlyPolicy.decide(rule.screenOffOnly, AndroidLockStateProvider(context))) {
            is ScreenOffOnlyDecision.Allow -> Unit
            is ScreenOffOnlyDecision.Filter -> {
                addFilteredRecord(context, dao, message, screenDecision.reason)
                return
            }
        }
        if (settings.quietHours().shouldQueue(message, LocalTime.now())) {
            addFilteredRecord(context, dao, message, "免打扰时段已跳过，结束后不会补发旧消息")
            return
        }
        enqueue(context, message, templateId = rule.templateId)
    }

    private suspend fun addFilteredRecord(context: Context, dao: RelayDao, message: RelayMessage, reason: String) {
        val settings = AppSettingsRepository(context).current()
        dao.addRecord(
            DeliveryRecord(
                packageName = message.packageName,
                app = message.app,
                title = message.title,
                body = if (RecordRetentionPolicy.shouldKeepBody(settings.historyRetention)) message.body else "",
                status = "已过滤",
                channelResults = JSONArray().put(JSONObject().put("reason", reason)).toString(),
                createdAt = System.currentTimeMillis()
            )
        )
        RecordRetentionPolicy.cutoffMillis(settings.historyRetention, System.currentTimeMillis())?.let { dao.deleteRecordsOlderThan(it) }
        dao.trimRecords()
        RelayWidget.refresh(context)
    }

    suspend fun addFailedRecord(context: Context, dao: RelayDao, message: RelayMessage, reason: String) {
        val settings = AppSettingsRepository(context).current()
        dao.addRecord(
            DeliveryRecord(
                packageName = message.packageName,
                app = message.app,
                title = message.title,
                body = if (RecordRetentionPolicy.shouldKeepBody(settings.historyRetention)) message.body else "",
                status = "发送失败",
                channelResults = JSONArray().put(JSONObject().put("reason", reason).put("success", false)).toString(),
                createdAt = System.currentTimeMillis()
            )
        )
        RecordRetentionPolicy.cutoffMillis(settings.historyRetention, System.currentTimeMillis())?.let { dao.deleteRecordsOlderThan(it) }
        dao.trimRecords()
        RelayWidget.refresh(context)
    }

    fun enqueue(context: Context, message: RelayMessage, delayed: Boolean = false, templateId: String = "", delayMillis: Long = 0, uniqueName: String? = null, manualOrTest: Boolean = false) {
        val data = workDataOf("package" to message.packageName, "app" to message.app, "title" to message.title, "body" to message.body, "time" to message.time, "delayed" to delayed, "templateId" to templateId, "manualOrTest" to manualOrTest)
        val request = OneTimeWorkRequestBuilder<RelayWorker>()
            .setInputData(data)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        val workManager = WorkManager.getInstance(context)
        if (uniqueName.isNullOrBlank()) workManager.enqueue(request)
        else workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
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
        val rawChannels = SecureStore(applicationContext).get("channels")?.let { runCatching { ChannelSender.parse(it) }.getOrDefault(emptyList()) }.orEmpty()
        val settings = AppSettingsRepository(applicationContext).current()
        dao.ensureTemplates(settings)
        val rule = dao.rule(message.packageName)
        val requestedTemplate = inputData.getString("templateId").orEmpty().ifBlank { rule?.templateId ?: TemplateCatalog.GENERAL_ID }
        val channels = when (val route = PerAppRouteResolver.resolve(rawChannels, settings, rule, manualOrTest = inputData.getBoolean("manualOrTest", false))) {
            is RouteResolution.Targets -> route.channels
            is RouteResolution.Failure -> {
                RelayEngine.addFailedRecord(applicationContext, dao, message, route.message)
                return Result.failure()
            }
        }
        val template = (dao.template(requestedTemplate)?.definition() ?: TemplateCatalog.byId(TemplateCatalog.STANDARD_ID)).template()
        val secure = SecureStore(applicationContext)
        val relayUrl = secure.get("relay_url").orEmpty()
        val results = if (relayUrl.isNotBlank()) ChannelSender.sendRelay(relayUrl, secure.get("relay_token").orEmpty(), channels, message, template.renderTitle(message), template.renderBody(message))
        else channels.map { ChannelSender.send(it, template.renderTitle(message), template.renderBody(message)) }
        val successes = results.count(DeliveryResult::success)
        val status = when {
            channels.isEmpty() -> "未配置渠道"
            successes == channels.size -> "成功"
            successes > 0 -> "部分成功"
            else -> "发送失败"
        }
        val resultJson = JSONArray().apply { results.forEach { put(JSONObject().put("channel", it.channel).put("success", it.success).put("httpStatus", it.httpStatus).put("retryable", it.retryable).put("error", it.error)) } }.toString()
        dao.addRecord(DeliveryRecord(packageName = message.packageName, app = message.app, title = message.title, body = if (RecordRetentionPolicy.shouldKeepBody(settings.historyRetention)) message.body else "", status = status, channelResults = resultJson, createdAt = System.currentTimeMillis(), delayed = inputData.getBoolean("delayed", false)))
        RecordRetentionPolicy.cutoffMillis(settings.historyRetention, System.currentTimeMillis())?.let { dao.deleteRecordsOlderThan(it) }
        dao.trimRecords()
        RelayWidget.refresh(applicationContext)
        return when {
            results.isNotEmpty() && results.all(DeliveryResult::success) -> Result.success()
            settings.retryEnabled && results.any(DeliveryResult::retryable) && runAttemptCount < settings.maxRetryCount -> Result.retry()
            else -> Result.failure()
        }
    }
}

class QueueFlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = RelayDatabase.get(applicationContext).relayDao()
        dao.queued().forEach {
            // Quiet hours intentionally drop old queued notifications instead of batch-sending stale messages.
            dao.removeQueued(it.id)
        }
        return Result.success()
    }
}
