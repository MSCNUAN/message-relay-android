package io.github.messagerelay

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ChannelConfig(val type: String, val url: String, val secret: String = "", val enabled: Boolean = true)

object ChannelSender {
    fun parse(raw: String): List<ChannelConfig> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { index -> array.getJSONObject(index).run { ChannelConfig(getString("type"), getString("url"), optString("secret"), optBoolean("enabled", true)) } }
    }

    fun send(channel: ChannelConfig, title: String, body: String): Boolean {
        val (url, payload) = target(channel, title, body)
        if (url.isBlank()) return false
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = if (channel.type == "bark") "GET" else "POST"
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        if (channel.type != "bark") {
            connection.doOutput = true; connection.setRequestProperty("content-type", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        }
        return connection.responseCode in 200..299
    }

    fun sendRelay(relayUrl: String, token: String, channels: List<ChannelConfig>, message: RelayMessage, title: String, body: String): Boolean {
        val targets = JSONArray()
        channels.forEach { channel ->
            val (url, payload) = target(channel, title, body)
            targets.put(JSONObject().put("type", channel.type).put("url", url).put("payload", payload))
        }
        val request = JSONObject().put("message", JSONObject().put("app", message.app).put("title", message.title).put("body", message.body).put("time", message.time.toString())).put("targets", targets)
        val connection = URL(relayUrl.trimEnd('/') + "/v1/relay").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"; connection.doOutput = true; connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        connection.setRequestProperty("content-type", "application/json"); connection.setRequestProperty("authorization", "Bearer $token")
        connection.outputStream.use { it.write(request.toString().toByteArray()) }
        return connection.responseCode in 200..299
    }

    private fun target(channel: ChannelConfig, title: String, body: String): Pair<String, JSONObject> =
        when (channel.type) {
            "dingtalk" -> signedDingTalk(channel) to JSONObject().put("msgtype", "text").put("text", JSONObject().put("content", "$title\n$body"))
            "feishu" -> channel.url to JSONObject().put("msg_type", "text").put("content", JSONObject().put("text", "$title\n$body"))
            "bark" -> channel.url.trimEnd('/') + "/" + URLEncoder.encode(title, "UTF-8") + "/" + URLEncoder.encode(body, "UTF-8") to JSONObject()
            else -> "" to JSONObject()
        }

    private fun signedDingTalk(channel: ChannelConfig): String {
        if (channel.secret.isBlank()) return channel.url
        val timestamp = System.currentTimeMillis()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(channel.secret.toByteArray(), "HmacSHA256")) }
        val sign = URLEncoder.encode(Base64.encodeToString(mac.doFinal("$timestamp\n${channel.secret}".toByteArray()), Base64.NO_WRAP), "UTF-8")
        return "${channel.url}&timestamp=$timestamp&sign=$sign"
    }
}
