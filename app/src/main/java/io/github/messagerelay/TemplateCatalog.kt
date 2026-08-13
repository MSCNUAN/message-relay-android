package io.github.messagerelay

data class TemplateDefinition(
    val id: String,
    val name: String,
    val title: String,
    val body: String = "{{body}}\n{{time}}",
    val builtIn: Boolean = true
) {
    fun template() = MessageTemplate(title, body)
}

object TemplateCatalog {
    const val GENERAL_ID = "general"
    const val STANDARD_ID = "standard"

    private const val APP_TITLE = "{{appName}}"
    private const val APP_BODY = "📝：内容：{{notificationBody}}\n\n🕒：接收时间：{{receivedLocalTime}}"
    private const val PHONE_TITLE = "{{phoneNumber}}"
    private const val PHONE_BODY = "来自：{{fromLabel}}\n\n📍 归属地：{{phoneLocation}}\n\n📲 卡槽：{{simDisplayName}}\n\n🔔 提醒：{{callEventLabel}}\n\n🕒 接收时间：{{receivedLocalTime}}"
    private const val SMS_TITLE = "{{smsNumber}}"
    private const val SMS_BODY = "{{smsBody}}\n\n📩 来自：{{smsNumber}}\n\n📲 卡槽：{{simDisplayName}}\n\n🕒 接收时间：{{receivedLocalTime}}"

    val builtIns = listOf(
        TemplateDefinition(GENERAL_ID, "通用模板", APP_TITLE, APP_BODY),
        TemplateDefinition("simple", "简洁模板", APP_TITLE, APP_BODY),
        TemplateDefinition(STANDARD_ID, "标准模板", APP_TITLE, APP_BODY),
        TemplateDefinition("privacy", "隐私模板", APP_TITLE, APP_BODY),
        TemplateDefinition("raw", "原始通知模板", APP_TITLE, APP_BODY),
        TemplateDefinition("phone", "电话", PHONE_TITLE, PHONE_BODY),
        TemplateDefinition("sms", "短信", SMS_TITLE, SMS_BODY),
        TemplateDefinition("wechat", "微信", APP_TITLE, APP_BODY),
        TemplateDefinition("work_wechat", "企业微信", APP_TITLE, APP_BODY),
        TemplateDefinition("qq", "App 通知", APP_TITLE, APP_BODY),
        TemplateDefinition("dingtalk", "App 通知", APP_TITLE, APP_BODY),
        TemplateDefinition("feishu", "App 通知", APP_TITLE, APP_BODY)
    )

    fun byId(id: String) = builtIns.firstOrNull { it.id == id } ?: builtIns.first()

    fun recommend(appName: String, packageName: String): String {
        val value = "$appName $packageName".lowercase()
        return when {
            "企业微信" in value || "wework" in value -> "work_wechat"
            "微信" in value || "com.tencent.mm" in value -> "wechat"
            "钉钉" in value || "dingtalk" in value -> "dingtalk"
            "飞书" in value || "feishu" in value || "lark" in value -> "feishu"
            "qq" in value -> "qq"
            "短信" in value || "信息" in value || "messaging" in value || "mms" in value -> "sms"
            "电话" in value || "拨号" in value || "dialer" in value || "incallui" in value -> "phone"
            else -> GENERAL_ID
        }
    }
}

data class SourceSelection(val appName: String, val packageName: String, val templateId: String)

object SelectedSources {
    fun add(sources: List<SourceSelection>, source: SourceSelection): List<SourceSelection> =
        if (sources.any { it.packageName == source.packageName }) sources else sources + source
}

object ChannelSelection {
    const val NO_BARK_TARGETS = "__none__"

    fun normalized(channels: List<ChannelConfig>): List<ChannelConfig> {
        val normalized = channels.mapIndexed { index, channel -> channel.normalized(index) }
        val bark = normalized.filter { it.type == "bark" && it.url.isNotBlank() }
        val dingtalk = normalized.firstOrNull { it.type == "dingtalk" && it.url.isNotBlank() }?.let(::listOf).orEmpty()
        val feishu = normalized.firstOrNull { it.type == "feishu" && it.url.isNotBlank() }?.let(::listOf).orEmpty()
        return dingtalk + feishu + bark
    }

    fun enabled(channels: List<ChannelConfig>, barkTargetIds: String = ""): List<ChannelConfig> {
        val active = normalized(channels).filter(ChannelConfig::enabled)
        val selectedBarkIds = barkTargetIds.lines().map(String::trim).filter(String::isNotBlank).toSet()
        val noBarkTargets = NO_BARK_TARGETS in selectedBarkIds
        return active.filter { channel ->
            channel.type != "bark" || (!noBarkTargets && (selectedBarkIds.isEmpty() || channel.id in selectedBarkIds))
        }
    }

    fun primaryEnabled(channels: List<ChannelConfig>, primaryChannelId: String, barkTargetIds: String = ""): List<ChannelConfig> {
        val candidates = enabled(channels, barkTargetIds)
        val primary = candidates.firstOrNull { it.id == primaryChannelId } ?: candidates.firstOrNull()
        return primary?.let { listOf(it.copy(enabled = true)) }.orEmpty()
    }

    fun singleEnabled(channels: List<ChannelConfig>): List<ChannelConfig> =
        enabled(channels).firstOrNull()?.let { listOf(it.copy(enabled = true)) }.orEmpty()
}
