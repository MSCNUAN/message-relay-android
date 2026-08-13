package io.github.messagerelay

import java.time.LocalTime

data class RelayMessage(val packageName: String, val app: String, val title: String, val body: String, val time: Long)

data class RelayRule(val allowedPackages: Set<String> = emptySet(), val include: List<String> = emptyList(), val exclude: List<String> = emptyList()) {
    fun matches(message: RelayMessage): Boolean {
        val content = "${message.title}\n${message.body}"
        return message.packageName in allowedPackages &&
            (include.isEmpty() || include.any(content::contains)) &&
            exclude.none(content::contains)
    }
}

data class QuietHours(val enabled: Boolean = false, val start: LocalTime = LocalTime.of(22, 0), val end: LocalTime = LocalTime.of(7, 0), val urgent: List<String> = emptyList()) {
    fun shouldQueue(message: RelayMessage, now: LocalTime): Boolean {
        if (!enabled || urgent.any { message.title.contains(it) || message.body.contains(it) }) return false
        return if (start <= end) now >= start && now < end else now >= start || now < end
    }
}

object RecordRetentionPolicy {
    fun cutoffMillis(retention: String, now: Long): Long? = when (retention) {
        "7", "30", "90" -> now - retention.toLong() * 24 * 60 * 60 * 1000
        else -> null
    }

    fun shouldKeepBody(retention: String): Boolean = retention != "status_only"
}

data class MessageTemplate(
    val title: String = "{{appName}}",
    val body: String = "📝：内容：{{notificationBody}}\n\n🕒：接收时间：{{receivedLocalTime}}"
) {
    fun renderTitle(message: RelayMessage) = render(title, message)
    fun renderBody(message: RelayMessage) = render(body, message)

    private fun render(value: String, message: RelayMessage): String {
        val data = templateData(message)
        val rendered = "\\{\\{(\\w+)\\}\\}".toRegex().replace(value) {
            val key = it.groupValues[1]
            require(key in data) { "不支持的模板变量：$key" }
            data[key].orEmpty()
        }
        return rendered.lines()
            .map { it.trimEnd() }
            .filterNot { line ->
                val text = line.trim()
                text.equals("null", ignoreCase = true) ||
                    text.contains("：null", ignoreCase = true) ||
                    text.contains(":null", ignoreCase = true) ||
                    text.endsWith("：") ||
                    text.endsWith(":")
            }
            .joinToString("\n")
            .trim()
    }

    private fun templateData(message: RelayMessage): Map<String, String> {
        val fields = parseStructuredBody(message.body)
        val localTime = TimeFormatter.formatRecordDetailTime(message.time)
        val notificationBody = cleanText(message.body).ifBlank { "该通知未提供正文" }
        val phoneNumber = fields["号码"].orEmpty()
            .ifBlank { phoneCandidate(message.title) }
            .ifBlank { if (message.title in setOf("隐藏号码", "公用电话")) message.title else "" }
            .ifBlank { "未知号码" }
        val contactName = fields["联系人"].orEmpty()
        val fromLabel = when {
            contactName.isNotBlank() -> contactName
            phoneNumber == "隐藏号码" -> "隐藏号码"
            phoneNumber == "未知号码" -> "未知"
            else -> phoneNumber
        }
        val sim = fields["卡槽"].orEmpty().ifBlank { fields["SIM"].orEmpty() }.ifBlank { "未知 SIM" }
        val location = finalLocation(fields["归属地"].orEmpty(), fields["号码段运营商"].orEmpty())
        val callEvent = fields["提醒"].orEmpty().ifBlank { callEventLabel(message.title) }
        val smsBody = fields["短信正文"].orEmpty().ifBlank { notificationBody }.ifBlank { "该短信未提供正文" }
        return mapOf(
            "app" to message.app,
            "title" to message.title,
            "body" to message.body,
            "time" to localTime,
            "appName" to cleanText(message.app).ifBlank { "未知应用" },
            "notificationTitle" to cleanText(message.title),
            "notificationBody" to notificationBody,
            "smsBody" to smsBody,
            "contactName" to contactName,
            "fromLabel" to fromLabel,
            "phoneNumber" to phoneNumber,
            "smsNumber" to phoneNumber,
            "phoneLocation" to location,
            "simDisplayName" to sim,
            "receivedLocalTime" to localTime,
            "callEventLabel" to callEvent
        )
    }

    private fun parseStructuredBody(body: String): Map<String, String> =
        body.lines().mapNotNull { raw ->
            val line = raw.trim()
            val index = listOf(line.indexOf('：'), line.indexOf(':')).filter { it > 0 }.minOrNull() ?: return@mapNotNull null
            val key = line.substring(0, index)
                .trim()
                .replace(Regex("^[👤📓📍📲🔔🕒📩📝]+"), "")
                .trim()
            key to cleanText(line.substring(index + 1).trim())
        }.toMap()

    private fun finalLocation(region: String, carrier: String): String {
        val cleanRegion = simplifyRegion(region)
        val cleanCarrier = simplifyCarrier(carrier)
        return when {
            cleanRegion.isNotBlank() && cleanCarrier.isNotBlank() -> "$cleanRegion · $cleanCarrier"
            cleanRegion.isNotBlank() -> cleanRegion
            cleanCarrier.isNotBlank() -> cleanCarrier
            else -> "无法识别"
        }
    }

    private fun simplifyRegion(value: String): String {
        val cleaned = cleanText(value)
            .replace("中国", "")
            .replace("省", "")
            .replace("市", "")
            .trim()
        return when {
            cleaned.length >= 4 && cleaned.endsWith("地区") -> cleaned.removeSuffix("地区")
            cleaned.length >= 4 && cleaned.contains("深圳") -> "深圳"
            cleaned.length >= 4 && cleaned.contains("广州") -> "广州"
            cleaned.length >= 4 && cleaned.contains("上海") -> "上海"
            cleaned.length >= 4 && cleaned.contains("北京") -> "北京"
            else -> cleaned
        }
    }

    private fun simplifyCarrier(value: String): String =
        cleanText(value)
            .replace("中国", "")
            .replace("移动通信", "移动")
            .replace("联通", "联通")
            .replace("电信", "电信")
            .replace("广电", "广电")
            .trim()

    private fun callEventLabel(value: String): String = when {
        value.contains("未接") || value == "MISSED_CALL" -> "未接来电"
        value.contains("接通") || value == "CALL_ANSWERED" -> "来电已接通"
        else -> "来电提醒"
    }

    private fun cleanText(value: String): String =
        value.trim().takeUnless {
            it.equals("null", ignoreCase = true) ||
                it.startsWith("Bundle", ignoreCase = true) ||
                it.startsWith("[L", ignoreCase = true)
        }.orEmpty()

    private fun phoneCandidate(value: String): String =
        Regex("""(?<!\d)(\+?\d[\d\s-]{5,}\d)(?!\d)""").find(value)?.value?.replace(" ", "")?.replace("-", "").orEmpty()
}

class DedupeWindow {
    private val seen = mutableMapOf<String, Long>()
    fun accept(message: RelayMessage, now: Long): Boolean {
        val key = "${message.packageName}|${message.title}|${message.body}"
        val last = seen[key]
        if (last != null && now - last <= 60_000) return false
        seen[key] = now
        seen.entries.removeIf { now - it.value > 60_000 }
        return true
    }
}
