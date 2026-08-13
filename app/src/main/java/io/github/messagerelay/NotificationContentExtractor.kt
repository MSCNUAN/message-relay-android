package io.github.messagerelay

import android.app.Notification
import android.os.Bundle
import android.os.Build
import android.service.notification.StatusBarNotification

data class ExtractedNotificationContent(
    val title: String,
    val body: String
)

object NotificationContentExtractor {
    private const val MAX_BODY_CHARS = 4000

    fun extract(sbn: StatusBarNotification): ExtractedNotificationContent {
        val extras = sbn.notification.extras ?: Bundle.EMPTY
        val title = firstNonBlank(
            extras.safeText(Notification.EXTRA_TITLE),
            extras.safeText(Notification.EXTRA_CONVERSATION_TITLE),
            latestMessageSender(extras)
        )
        val body = firstNonBlank(
            extras.safeText(Notification.EXTRA_TEXT),
            extras.safeText(Notification.EXTRA_BIG_TEXT),
            latestMessageText(extras),
            latestTextLine(extras)
        ).let { normalizeBody(it, title) }

        return ExtractedNotificationContent(
            title = title,
            body = body.ifBlank { "该通知未提供正文" }
        )
    }

    private fun normalizeBody(value: String, title: String): String {
        val cleaned = clean(value)
        val deduped = if (title.isNotBlank() && cleaned == title) "" else cleaned
        return if (deduped.length > MAX_BODY_CHARS) deduped.take(MAX_BODY_CHARS) + "…" else deduped
    }

    private fun firstNonBlank(vararg values: String): String =
        values.map(::clean).firstOrNull(String::isNotBlank).orEmpty()

    private fun Bundle.safeText(key: String): String =
        when (val value = get(key)) {
            is CharSequence -> value.toString()
            else -> ""
        }

    private fun latestTextLine(extras: Bundle): String {
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
        return lines.map { it?.toString().orEmpty() }.lastOrNull { clean(it).isNotBlank() }.orEmpty()
    }

    private fun latestMessageText(extras: Bundle): String =
        messages(extras)
            .orEmpty()
            .lastOrNull { clean(it.text?.toString().orEmpty()).isNotBlank() }
            ?.text
            ?.toString()
            .orEmpty()

    private fun latestMessageSender(extras: Bundle): String =
        messages(extras)
            .orEmpty()
            .map(::messageSender)
            .lastOrNull { clean(it).isNotBlank() }
            .orEmpty()

    private fun messages(extras: Bundle): List<Notification.MessagingStyle.Message>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(extras.getParcelableArray(Notification.EXTRA_MESSAGES))
        } else {
            null
        }

    private fun messageSender(message: Notification.MessagingStyle.Message): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            message.senderPerson?.name?.toString().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            message.sender?.toString().orEmpty()
        }

    private fun clean(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
    }
}
