package io.github.messagerelay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ConfigBackup {
    suspend fun export(context: Context, includeSensitive: Boolean, password: CharArray? = null): String {
        val settings = AppSettingsRepository(context).current()
        val dao = RelayDatabase.get(context).relayDao()
        dao.ensureTemplates(settings)
        val rules = dao.allRules()
        val templates = dao.allTemplates()
        val simAliases = dao.simAliases()
        val json = JSONObject()
            .put("version", 2)
            .put(
                "settings",
                JSONObject()
                    .put("paused", settings.paused)
                    .put("persistentNotification", settings.persistentNotification)
                    .put("quietEnabled", settings.quietEnabled)
                    .put("quietStart", settings.quietStart)
                    .put("quietEnd", settings.quietEnd)
                    .put("urgentKeywords", settings.urgentKeywords)
                    .put("templateTitle", settings.templateTitle)
                    .put("templateBody", settings.templateBody)
                    .put("lockScreenOnly", settings.lockScreenOnly)
                    .put("primaryChannelId", settings.primaryChannelId)
                    .put("selectedTemplatePreset", settings.selectedTemplatePreset)
                    .put("historyRetention", settings.historyRetention)
                    .put("privacyDisplayMode", settings.privacyDisplayMode)
                    .put("multiChannelSend", settings.multiChannelSend)
                    .put("retryEnabled", settings.retryEnabled)
                    .put("maxRetryCount", settings.maxRetryCount)
                    .put("deliveryDelaySeconds", settings.deliveryDelaySeconds)
                    .put("mergeNotifications", settings.mergeNotifications)
                    .put("mergeWindowSeconds", settings.mergeWindowSeconds)
            )
            .put(
                "rules",
                JSONArray().apply {
                    rules.forEach {
                        put(
                            JSONObject()
                                .put("packageName", it.packageName)
                                .put("appName", it.appName)
                                .put("includes", it.includes)
                                .put("excludes", it.excludes)
                                .put("enabled", it.enabled)
                                .put("templateId", it.templateId)
                                .put("screenOffOnly", it.screenOffOnly)
                                .put("enabledCallEventTypes", it.enabledCallEventTypes)
                        )
                    }
                }
            )
            .put(
                "templates",
                JSONArray().apply {
                    templates.filterNot(TemplateEntity::builtIn).forEach {
                        put(JSONObject().put("id", it.id).put("name", it.name).put("title", it.title).put("body", it.body))
                    }
                }
            )
            .put(
                "simAliases",
                JSONArray().apply {
                    simAliases.forEach {
                        put(JSONObject().put("simFingerprint", it.simFingerprint).put("alias", it.alias).put("systemLabel", it.systemLabel).put("carrierName", it.carrierName).put("slotIndex", it.slotIndex))
                    }
                }
            )
            .put("channels", SecureStore(context).get("channels").orEmpty())
        if (includeSensitive) {
            json.put("relayUrl", SecureStore(context).get("relay_url").orEmpty())
            json.put("relayToken", SecureStore(context).get("relay_token").orEmpty())
        }
        return if (includeSensitive) BackupCodec.encrypt(json.toString(), requireNotNull(password) { "完整备份需要密码" }) else json.toString(2)
    }

    suspend fun import(context: Context, input: String, password: CharArray? = null) {
        val raw = if (input.startsWith("message-relay-backup-v1.")) BackupCodec.decrypt(input, requireNotNull(password) { "加密备份需要密码" }) else input
        val json = JSONObject(raw)
        val version = json.getInt("version")
        require(version in 1..2) { "备份版本不兼容" }
        val settings = json.getJSONObject("settings")
        val parsedChannels = if (json.has("channels")) {
            json.optString("channels").takeIf(String::isNotBlank)?.let(ChannelSender::parse).orEmpty().also { channels ->
                require(channels.all(ChannelValidation::isValid)) { "备份中的渠道配置无效" }
            }
        } else {
            null
        }
        val parsedTemplates = mutableListOf<TemplateEntity>()
        if (json.has("templates")) {
            val templates = json.getJSONArray("templates")
            for (index in 0 until templates.length()) templates.getJSONObject(index).run {
                val entity = TemplateEntity(getString("id"), getString("name"), getString("title"), getString("body"))
                entity.definition().template().renderTitle(RelayMessage("test", "微信", "张三", "明天 10 点开会", 0))
                entity.definition().template().renderBody(RelayMessage("test", "微信", "张三", "明天 10 点开会", 0))
                parsedTemplates.add(entity)
            }
        }
        val parsedRules = mutableListOf<RuleEntity>()
        val rules = json.getJSONArray("rules")
        for (index in 0 until rules.length()) rules.getJSONObject(index).run {
            val packageName = getString("packageName")
            val appName = getString("appName")
            require(packageName.isNotBlank() && appName.isNotBlank()) { "备份中的来源应用规则无效" }
            val callTypes = optString("enabledCallEventTypes", CallEventTypes.serialize(CallEventTypes.default))
            require(CallEventTypes.isValid(callTypes)) { "电话通知类型配置无效" }
            val iconMode = PerAppRouteResolver.parseIconMode(optString("barkIconMode", BarkIconMode.NONE.name))
            val iconUrl = optString("barkIconUrl")
            require(PerAppRouteResolver.validateIcon(iconMode, iconUrl).isBlank()) { "Bark 图标 URL 无效" }
            parsedRules.add(
                RuleEntity(
                    packageName,
                    appName,
                    optString("includes"),
                    optString("excludes"),
                    optBoolean("enabled", true),
                    optString("templateId", TemplateCatalog.GENERAL_ID),
                    "",
                    optBoolean("screenOffOnly", false),
                    CallEventTypes.serialize(CallEventTypes.parse(callTypes)),
                    false,
                    iconMode.name,
                    iconUrl,
                    optString("barkSound"),
                    optString("barkGroup"),
                    optString("barkLevel"),
                    optBoolean("barkCall", false)
                )
            )
        }
        val parsedSimAliases = mutableListOf<SimAliasEntity>()
        if (json.has("simAliases")) {
            val aliases = json.getJSONArray("simAliases")
            for (index in 0 until aliases.length()) aliases.getJSONObject(index).run {
                val fingerprint = optString("simFingerprint")
                val alias = optString("alias").trim().take(20)
                require(fingerprint.isNotBlank()) { "SIM 别名配置无效" }
                parsedSimAliases.add(SimAliasEntity(fingerprint, alias, optString("systemLabel"), optString("carrierName"), optInt("slotIndex", -1)))
            }
        }
        val repository = AppSettingsRepository(context)
        repository.setPaused(settings.optBoolean("paused"))
        repository.setPersistentNotification(settings.optBoolean("persistentNotification", true))
        repository.setLockScreenOnly(settings.optBoolean("lockScreenOnly", false))
        repository.setQuiet(settings.optBoolean("quietEnabled"), settings.optString("quietStart", "22:00"), settings.optString("quietEnd", "07:00"), settings.optString("urgentKeywords", "验证码\n来电\n未接来电"))
        repository.setTemplate(settings.optString("templateTitle", "[{{app}}] {{title}}"), settings.optString("templateBody", "{{body}}\n{{time}}"))
        repository.setPrimaryChannelId(settings.optString("primaryChannelId"))
        repository.setSelectedTemplatePreset(settings.optString("selectedTemplatePreset", TemplateCatalog.STANDARD_ID))
        repository.setHistoryRetention(settings.optString("historyRetention", "30"))
        repository.setPrivacyDisplayMode(settings.optString("privacyDisplayMode", "full"))
        repository.setMultiChannelSend(settings.optBoolean("multiChannelSend", false))
        repository.setRetryPolicy(settings.optBoolean("retryEnabled", true), settings.optInt("maxRetryCount", 3))
        repository.setNotificationTiming(settings.optBoolean("mergeNotifications", false), settings.optInt("mergeWindowSeconds", 2), settings.optInt("deliveryDelaySeconds", 0))
        val dao = RelayDatabase.get(context).relayDao()
        dao.ensureTemplates(repository.current())
        parsedTemplates.forEach { dao.saveTemplate(it) }
        parsedRules.forEach { dao.saveRule(it) }
        parsedSimAliases.forEach { dao.saveSimAlias(it) }
        parsedChannels?.let { SecureStore(context).put("channels", ChannelSender.serialize(it)) }
        if (json.has("relayUrl")) SecureStore(context).put("relay_url", json.getString("relayUrl"))
        if (json.has("relayToken")) SecureStore(context).put("relay_token", json.getString("relayToken"))
    }
}
