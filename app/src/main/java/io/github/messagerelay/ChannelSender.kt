package io.github.messagerelay

import org.json.JSONArray
import org.json.JSONObject
import java.lang.Integer.toUnsignedString
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ChannelConfig(
    val type: String,
    val url: String,
    val secret: String = "",
    val enabled: Boolean = true,
    val id: String = "",
    val name: String = "",
    val sound: String = "",
    val icon: String = "",
    val group: String = "",
    val level: String = "",
    val call: Boolean = false,
    val boundAppPackages: String = ""
) {
    fun normalized(index: Int = 0): ChannelConfig {
        val normalizedId = id.ifBlank { stableChannelId(type, url, index) }
        val normalizedName = name.ifBlank {
            when (type) {
                "dingtalk" -> "钉钉"
                "feishu" -> "飞书"
                "bark" -> "Bark ${index + 1}"
                else -> type
            }
        }
        return copy(id = normalizedId, name = normalizedName)
    }

    fun boundPackages(): Set<String> =
        boundAppPackages.lines().map(String::trim).filter(String::isNotBlank).toSet()
}

data class DeliveryResult(
    val channel: String,
    val success: Boolean,
    val httpStatus: Int? = null,
    val retryable: Boolean = false,
    val error: String? = null
)

object DeliveryPolicy {
    fun shouldRetry(status: Int?, networkError: Boolean = false): Boolean =
        networkError || status == 429 || (status != null && status >= 500)
}

object ChannelValidation {
    fun isValid(channel: ChannelConfig): Boolean = runCatching {
        val uri = URI(channel.url)
        val iconValid = channel.icon.isBlank() || URI(channel.icon).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() }
        uri.scheme == "https" && !uri.host.isNullOrBlank() && channel.type in setOf("dingtalk", "feishu", "bark") && iconValid
    }.getOrDefault(false)
}

object ChannelSignatures {
    fun hmacSha256Base64(key: ByteArray, value: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        return Base64.getEncoder().encodeToString(mac.doFinal(value))
    }

    fun dingtalk(secret: String, timestampMillis: Long): String =
        hmacSha256Base64(secret.toByteArray(), "$timestampMillis\n$secret".toByteArray())

    fun feishu(secret: String, timestampSeconds: Long): String =
        hmacSha256Base64("$timestampSeconds\n$secret".toByteArray(), ByteArray(0))
}

object ChannelSender {
    fun parse(raw: String): List<ChannelConfig> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            array.getJSONObject(index).run {
                ChannelConfig(
                    type = getString("type"),
                    url = getString("url"),
                    secret = optString("secret"),
                    enabled = optBoolean("enabled", true),
                    id = optString("id"),
                    name = optString("name"),
                    sound = optString("sound"),
                    icon = optString("icon"),
                    group = optString("group"),
                    level = optString("level"),
                    call = optBoolean("call", false),
                    boundAppPackages = optString("boundAppPackages")
                ).normalized(index)
            }
        }
    }

    fun serialize(channels: List<ChannelConfig>): String = JSONArray().apply {
        channels.mapIndexed { index, channel -> channel.normalized(index) }
            .filter { it.type in setOf("dingtalk", "feishu", "bark") && it.url.isNotBlank() }
            .forEach { channel ->
                put(
                    JSONObject()
                        .put("type", channel.type)
                        .put("url", channel.url)
                        .put("secret", channel.secret)
                        .put("enabled", channel.enabled)
                        .put("id", channel.id)
                        .put("name", channel.name)
                        .put("sound", channel.sound)
                        .put("icon", channel.icon)
                        .put("group", channel.group)
                        .put("level", channel.level)
                        .put("call", channel.call)
                        .put("boundAppPackages", channel.boundAppPackages)
                )
            }
    }.toString()

    fun send(channel: ChannelConfig, title: String, body: String): DeliveryResult {
        val channelLabel = channel.name.ifBlank { channel.type }
        if (!ChannelValidation.isValid(channel)) {
            return DeliveryResult(channelLabel, false, error = "渠道地址必须是有效的 HTTPS 地址")
        }
        return runCatching {
            val (url, payload) = target(channel, title, body)
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = if (channel.type == "bark") "GET" else "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (channel.type != "bark") {
                connection.doOutput = true
                connection.setRequestProperty("content-type", "application/json")
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            }
            val status = connection.responseCode
            val responseText = runCatching {
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            val appError = if (status in 200..299) applicationLayerError(channel.type, responseText) else null
            DeliveryResult(
                channelLabel,
                status in 200..299 && appError.isNullOrBlank(),
                status,
                DeliveryPolicy.shouldRetry(status),
                when {
                    status !in 200..299 -> "HTTP $status"
                    !appError.isNullOrBlank() -> appError
                    else -> null
                }
            )
        }.getOrElse { DeliveryResult(channelLabel, false, retryable = true, error = "网络连接失败") }
    }

    fun sendRelay(relayUrl: String, token: String, channels: List<ChannelConfig>, message: RelayMessage, title: String, body: String): List<DeliveryResult> {
        if (!relayUrl.startsWith("https://")) {
            return channels.map { DeliveryResult(channelLabel(it), false, error = "中转地址必须使用 HTTPS") }
        }
        return runCatching {
            val targets = JSONArray()
            channels.forEach { channel ->
                val (url, payload) = target(channel, title, body)
                targets.put(JSONObject().put("type", channel.type).put("id", channel.id).put("name", channel.name).put("url", url).put("payload", payload))
            }
            val request = JSONObject()
                .put("message", JSONObject().put("app", message.app).put("title", message.title).put("body", message.body).put("time", message.time.toString()))
                .put("targets", targets)
            val connection = URL(relayUrl.trimEnd('/') + "/v1/relay").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("content-type", "application/json")
            connection.setRequestProperty("authorization", "Bearer $token")
            connection.outputStream.use { it.write(request.toString().toByteArray()) }
            val status = connection.responseCode
            if (status in 200..299) {
                parseRelayResults(connection.inputStream.bufferedReader().use { it.readText() }, channels)
            } else {
                channels.map { DeliveryResult(channelLabel(it), false, status, DeliveryPolicy.shouldRetry(status), "中转 HTTP $status") }
            }
        }.getOrElse { channels.map { DeliveryResult(channelLabel(it), false, retryable = true, error = "中转网络连接失败") } }
    }

    fun parseRelayResults(raw: String, channels: List<ChannelConfig>): List<DeliveryResult> {
        val values = JSONObject(raw).getJSONArray("results")
        val resultObjects = (0 until values.length()).map { values.getJSONObject(it) }
        val byId = resultObjects.filter { it.optString("id").isNotBlank() }.associateBy { it.optString("id") }
        val byType = resultObjects.associateBy { it.getString("type") }
        return channels.map { channel ->
            val label = channelLabel(channel)
            val value = byId[channel.id] ?: byType[channel.type] ?: return@map DeliveryResult(label, false, error = "中转未返回渠道结果")
            val status = value.optInt("status").takeIf { it > 0 }
            val success = value.optBoolean("ok")
            DeliveryResult(label, success, status, DeliveryPolicy.shouldRetry(status, status == null), if (success) null else if (status == null) "中转渠道网络失败" else "HTTP $status")
        }
    }

    fun target(channel: ChannelConfig, title: String, body: String): Pair<String, JSONObject> = when (channel.type) {
        "dingtalk" -> signedDingTalk(channel) to JSONObject().put("msgtype", "text").put("text", JSONObject().put("content", "$title\n$body"))
        "feishu" -> channel.url to JSONObject().apply {
            put("msg_type", "text")
            put("content", JSONObject().put("text", "$title\n$body"))
            if (channel.secret.isNotBlank()) {
                val timestamp = System.currentTimeMillis() / 1000
                put("timestamp", timestamp.toString())
                put("sign", ChannelSignatures.feishu(channel.secret, timestamp))
            }
        }
        "bark" -> barkUrl(channel, title, body) to JSONObject()
        else -> "" to JSONObject()
    }

    private fun barkUrl(channel: ChannelConfig, title: String, body: String): String {
        val base = channel.url.trimEnd('/') + "/" + encodePathSegment(title) + "/" + encodePathSegment(body)
        val params = listOfNotNull(
            channel.sound.takeIf(String::isNotBlank)?.let { "sound=${encodeQueryValue(it)}" },
            channel.icon.takeIf(String::isNotBlank)?.let { "icon=${encodeQueryValue(it)}" },
            channel.group.takeIf(String::isNotBlank)?.let { "group=${encodeQueryValue(it)}" },
            channel.level.takeIf(String::isNotBlank)?.let { "level=${encodeQueryValue(it)}" },
            channel.call.takeIf { it }?.let { "call=1" }
        )
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    private fun signedDingTalk(channel: ChannelConfig): String {
        if (channel.secret.isBlank()) return channel.url
        val timestamp = System.currentTimeMillis()
        val sign = URLEncoder.encode(ChannelSignatures.dingtalk(channel.secret, timestamp), "UTF-8")
        return "${channel.url}${if ('?' in channel.url) '&' else '?'}timestamp=$timestamp&sign=$sign"
    }

    private fun applicationLayerError(type: String, raw: String): String? {
        if (raw.isBlank()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return when (type) {
            "bark" -> {
                val code = json.optInt("code", 200)
                if (code == 200) null else "Bark 返回错误：code=$code ${json.optString("message").ifBlank { json.optString("msg") }}"
            }
            "feishu" -> {
                val code = json.optInt("code", 0)
                if (code == 0) null else "飞书返回错误：code=$code ${json.optString("msg").ifBlank { json.optString("message") }}"
            }
            "dingtalk" -> {
                val code = json.optInt("errcode", 0)
                if (code == 0) null else "钉钉返回错误：errcode=$code ${json.optString("errmsg")}"
            }
            else -> null
        }
    }
}

private fun stableChannelId(type: String, url: String, index: Int): String {
    val hash = toUnsignedString("$type|$url|$index".hashCode(), 36)
    return "${type}_${hash}"
}

private fun channelLabel(channel: ChannelConfig): String = channel.name.ifBlank { channel.type }

private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, "UTF-8")
