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
    val builtIns = listOf(
        TemplateDefinition(GENERAL_ID, "通用", "[{{app}}] {{title}}"),
        TemplateDefinition("phone", "电话", "来电提醒：{{title}}"),
        TemplateDefinition("sms", "短信", "短信：{{title}}"),
        TemplateDefinition("wechat", "微信", "{{app}} · {{title}}"),
        TemplateDefinition("work_wechat", "企业微信", "{{app}} · {{title}}"),
        TemplateDefinition("qq", "QQ", "{{app}} · {{title}}"),
        TemplateDefinition("dingtalk", "钉钉", "{{app}} · {{title}}"),
        TemplateDefinition("feishu", "飞书", "{{app}} · {{title}}")
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
    fun singleEnabled(channels: List<ChannelConfig>): List<ChannelConfig> =
        channels.firstOrNull(ChannelConfig::enabled)?.let { listOf(it.copy(enabled = true)) }.orEmpty()
}
