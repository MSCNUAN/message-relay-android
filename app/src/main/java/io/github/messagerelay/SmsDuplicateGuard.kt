package io.github.messagerelay

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object SmsDuplicateGuard {
    private const val TTL_MS = 20_000L
    private val fingerprints = ConcurrentHashMap<String, Long>()
    @Volatile var smsBroadcastCount: Int = 0
    @Volatile var smsNotificationCount: Int = 0
    @Volatile var duplicateSuppressedCount: Int = 0

    fun registerBroadcast(sender: String, body: String, receivedAt: Long = System.currentTimeMillis()) {
        smsBroadcastCount += 1
        cleanup(receivedAt)
        fingerprints[fingerprint(sender, body, bucket(receivedAt))] = receivedAt
    }

    fun shouldSuppressNotification(packageName: String, title: String, body: String, postTime: Long = System.currentTimeMillis()): Boolean {
        if (!looksLikeSmsPackage(packageName)) return false
        smsNotificationCount += 1
        cleanup(postTime)
        val candidates = listOf(bucket(postTime), bucket(postTime - 10_000), bucket(postTime + 10_000))
        val matched = candidates.any { fingerprints.containsKey(fingerprint(title, body, it)) }
        if (matched) duplicateSuppressedCount += 1
        return matched
    }

    fun fingerprintForTest(sender: String, body: String, receivedAt: Long): String =
        fingerprint(sender, body, bucket(receivedAt))

    private fun cleanup(now: Long) {
        fingerprints.entries.removeIf { now - it.value > TTL_MS }
    }

    private fun bucket(time: Long): Long = time / 10_000L

    private fun fingerprint(sender: String, body: String, bucket: Long): String {
        val normalized = normalizeSender(sender) + "|" + sha256(normalizeBody(body)) + "|" + bucket
        return sha256(normalized)
    }

    private fun looksLikeSmsPackage(packageName: String): Boolean {
        val value = packageName.lowercase()
        return "sms" in value || "mms" in value || "messaging" in value || "telephony" in value
    }

    private fun normalizeSender(value: String): String =
        value.filter { it.isLetterOrDigit() || it == '+' }.lowercase()

    private fun normalizeBody(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
