package io.github.messagerelay

import org.json.JSONArray
import org.json.JSONObject

data class ParsedChannelResult(
    val channel: String,
    val success: Boolean?,
    val httpStatus: Int?,
    val retryable: Boolean?,
    val reason: String
) {
    val statusText: String
        get() = when (success) {
            true -> "发送成功"
            false -> "发送失败"
            null -> if (reason.isNotBlank()) "已过滤" else "未知"
        }
}

object ChannelResultParser {
    fun parse(raw: String): List<ParsedChannelResult>? {
        val value = raw.trim()
        if (value.isBlank() || value == "[]") return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.opt(index)
                    val obj = item as? JSONObject ?: continue
                    add(
                        ParsedChannelResult(
                            channel = obj.optString("channel").ifBlank { obj.optString("name").ifBlank { "未知渠道" } },
                            success = when {
                                obj.has("success") -> obj.optBoolean("success")
                                else -> null
                            },
                            httpStatus = obj.optIntOrNull("httpStatus"),
                            retryable = if (obj.has("retryable")) obj.optBoolean("retryable") else null,
                            reason = obj.optString("error").ifBlank { obj.optString("reason") }
                        )
                    )
                }
            }
        }.getOrNull()
    }

    fun channelSummary(raw: String): String {
        val parsed = parse(raw) ?: return "发送结果暂时无法解析"
        if (parsed.isEmpty()) return ""
        val names = parsed.map { it.channel }.filter { it.isNotBlank() }.distinct()
        return if (names.isEmpty()) "" else names.joinToString("、")
    }

    fun detailText(raw: String): String {
        val parsed = parse(raw) ?: return "发送结果暂时无法解析"
        if (parsed.isEmpty()) return ""
        return parsed.joinToString("\n\n") { result ->
            buildList {
                add("渠道：${result.channel}")
                add("状态：${result.statusText}")
                result.httpStatus?.let { add("HTTP 状态：$it") }
                if (result.reason.isNotBlank()) add("原因：${redactResultText(result.reason)}")
                result.retryable?.let { add("可以重试：${if (it) "是" else "否"}") }
            }.joinToString("\n")
        }
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun redactResultText(value: String): String =
    value
        .replace(Regex("https?://[^\\s]+"), "https://***")
        .replace(Regex("([A-Za-z0-9_-]{16,})"), "***")
        .replace(Regex("(\\d{3})\\d{4}(\\d{4})"), "$1****$2")
