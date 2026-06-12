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
        val json = JSONObject()
            .put("version", 1)
            .put("settings", JSONObject()
                .put("paused", settings.paused)
                .put("persistentNotification", settings.persistentNotification)
                .put("quietEnabled", settings.quietEnabled)
                .put("quietStart", settings.quietStart)
                .put("quietEnd", settings.quietEnd)
                .put("urgentKeywords", settings.urgentKeywords)
                .put("templateTitle", settings.templateTitle)
                .put("templateBody", settings.templateBody))
            .put("rules", JSONArray().apply { rules.forEach { put(JSONObject().put("packageName", it.packageName).put("appName", it.appName).put("includes", it.includes).put("excludes", it.excludes).put("enabled", it.enabled).put("templateId", it.templateId)) } })
            .put("templates", JSONArray().apply { templates.filterNot(TemplateEntity::builtIn).forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("title", it.title).put("body", it.body)) } })
        if (includeSensitive) {
            json.put("channels", SecureStore(context).get("channels").orEmpty())
            json.put("relayUrl", SecureStore(context).get("relay_url").orEmpty())
            json.put("relayToken", SecureStore(context).get("relay_token").orEmpty())
        }
        return if (includeSensitive) BackupCodec.encrypt(json.toString(), requireNotNull(password) { "完整备份需要密码" }) else json.toString(2)
    }

    suspend fun import(context: Context, input: String, password: CharArray? = null) {
        val raw = if (input.startsWith("message-relay-backup-v1.")) BackupCodec.decrypt(input, requireNotNull(password) { "加密备份需要密码" }) else input
        val json = JSONObject(raw)
        require(json.getInt("version") == 1) { "备份版本不兼容" }
        val settings = json.getJSONObject("settings")
        val repository = AppSettingsRepository(context)
        repository.setPaused(settings.optBoolean("paused"))
        repository.setPersistentNotification(settings.optBoolean("persistentNotification", true))
        repository.setQuiet(settings.optBoolean("quietEnabled"), settings.optString("quietStart", "22:00"), settings.optString("quietEnd", "07:00"), settings.optString("urgentKeywords", "紧急"))
        repository.setTemplate(settings.optString("templateTitle", "[{{app}}] {{title}}"), settings.optString("templateBody", "{{body}}\n{{time}}"))
        val dao = RelayDatabase.get(context).relayDao()
        dao.ensureTemplates(repository.current())
        if (json.has("templates")) {
            val templates = json.getJSONArray("templates")
            for (index in 0 until templates.length()) templates.getJSONObject(index).run {
                dao.saveTemplate(TemplateEntity(getString("id"), getString("name"), getString("title"), getString("body")))
            }
        }
        val rules = json.getJSONArray("rules")
        for (index in 0 until rules.length()) rules.getJSONObject(index).run {
            dao.saveRule(RuleEntity(getString("packageName"), getString("appName"), optString("includes"), optString("excludes"), optBoolean("enabled", true), optString("templateId", TemplateCatalog.GENERAL_ID)))
        }
        if (json.has("channels")) {
            val channels = runCatching { ChannelSender.parse(json.getString("channels")) }.getOrDefault(emptyList())
            SecureStore(context).put("channels", ChannelSender.serialize(ChannelSelection.singleEnabled(channels)))
        }
        if (json.has("relayUrl")) SecureStore(context).put("relay_url", json.getString("relayUrl"))
        if (json.has("relayToken")) SecureStore(context).put("relay_token", json.getString("relayToken"))
    }
}
