package io.github.messagerelay

import java.time.Instant
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

data class MessageTemplate(val title: String = "[{{app}}] {{title}}", val body: String = "{{body}}\n{{time}}") {
    private val allowed = setOf("app", "title", "body", "time")
    fun renderTitle(message: RelayMessage) = render(title, message)
    fun renderBody(message: RelayMessage) = render(body, message)
    private fun render(value: String, message: RelayMessage): String {
        val data = mapOf("app" to message.app, "title" to message.title, "body" to message.body, "time" to Instant.ofEpochMilli(message.time).toString())
        return "\\{\\{(\\w+)}}".toRegex().replace(value) {
            require(it.groupValues[1] in allowed) { "不支持的模板变量：${it.groupValues[1]}" }
            data[it.groupValues[1]].orEmpty()
        }
    }
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
