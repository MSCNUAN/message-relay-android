package io.github.messagerelay

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ChannelConfig(val type: String, val url: String, val secret: String = "", val enabled: Boolean = true)

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
        uri.scheme == "https" && !uri.host.isNullOrBlank() && channel.type in setOf("dingtalk", "feishu", "bark")
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
                ChannelConfig(getString("type"), getString("url"), optString("secret"), optBoolean("enabled", true))
            }
        }
    }

    fun serialize(channels: List<ChannelConfig>): String = JSONArray().apply {
        channels.forEach { put(JSONObject().put("type", it.type).put("url", it.url).put("secret", it.secret).put("enabled", it.enabled)) }
    }.toString()

    fun send(channel: ChannelConfig, title: String, body: String): DeliveryResult {
        if (!ChannelValidation.isValid(channel)) return DeliveryResult(channel.type, false, error = "渠道地址必须是有效的 HTTPS 地址")
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
            DeliveryResult(channel.type, status in 200..299, status, DeliveryPolicy.shouldRetry(status), if (status in 200..299) null else "HTTP $status")
        }.getOrElse { DeliveryResult(channel.type, false, retryable = true, error = "网络连接失败") }
    }

    fun sendRelay(relayUrl: String, token: String, channels: List<ChannelConfig>, message: RelayMessage, title: String, body: String): List<DeliveryResult> {
        if (!relayUrl.startsWith("https://")) return channels.map { DeliveryResult(it.type, false, error = "中转地址必须使用 HTTPS") }
        return runCatching {
            val targets = JSONArray()
            channels.forEach { channel ->
                val (url, payload) = target(channel, title, body)
                targets.put(JSONObject().put("type", channel.type).put("url", url).put("payload", payload))
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
                channels.map { DeliveryResult(it.type, false, status, DeliveryPolicy.shouldRetry(status), "中转 HTTP $status") }
            }
        }.getOrElse { channels.map { DeliveryResult(it.type, false, retryable = true, error = "中转网络连接失败") } }
    }

    fun parseRelayResults(raw: String, channels: List<ChannelConfig>): List<DeliveryResult> {
        val values = JSONObject(raw).getJSONArray("results")
        val byType = (0 until values.length()).associate { index -> values.getJSONObject(index).let { it.getString("type") to it } }
        return channels.map { channel ->
            val value = byType[channel.type] ?: return@map DeliveryResult(channel.type, false, error = "中转未返回渠道结果")
            val status = value.optInt("status").takeIf { it > 0 }
            val success = value.optBoolean("ok")
            DeliveryResult(channel.type, success, status, DeliveryPolicy.shouldRetry(status, status == null), if (success) null else if (status == null) "中转渠道网络失败" else "HTTP $status")
        }
    }

    private fun target(channel: ChannelConfig, title: String, body: String): Pair<String, JSONObject> = when (channel.type) {
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
        "bark" -> channel.url.trimEnd('/') + "/" + URLEncoder.encode(title, "UTF-8") + "/" + URLEncoder.encode(body, "UTF-8") to JSONObject()
        else -> "" to JSONObject()
    }

    private fun signedDingTalk(channel: ChannelConfig): String {
        if (channel.secret.isBlank()) return channel.url
        val timestamp = System.currentTimeMillis()
        val sign = URLEncoder.encode(ChannelSignatures.dingtalk(channel.secret, timestamp), "UTF-8")
        return "${channel.url}${if ('?' in channel.url) '&' else '?'}timestamp=$timestamp&sign=$sign"
    }
}
