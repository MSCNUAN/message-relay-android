package io.github.messagerelay

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification

enum class WeChatFieldSource {
    MESSAGING_PERSON,
    CONVERSATION_TITLE,
    EXTRA_TITLE,
    EXTRA_TITLE_BIG,
    EXTRA_SUB_TEXT,
    BODY_PREFIX,
    UNKNOWN
}

data class WeChatMessageInfo(
    val senderName: String?,
    val conversationName: String?,
    val body: String?,
    val isGroupConversation: Boolean?,
    val senderSource: WeChatFieldSource,
    val conversationSource: WeChatFieldSource
)

data class WeChatStructureSnapshot(
    val messagingStyle: Boolean,
    val messageCount: Int,
    val person: Boolean,
    val personName: Boolean,
    val conversationTitle: Boolean,
    val extraTitle: Boolean,
    val extraTitleBig: Boolean,
    val extraText: Boolean,
    val extraBigText: Boolean,
    val extraTextLines: Boolean,
    val extraSubText: Boolean,
    val extraSummaryText: Boolean,
    val extraConversationTitle: Boolean,
    val extraTemplate: Boolean,
    val prefixParserUsed: Boolean
)

object WeChatDiagnostics {
    @Volatile var lastPrivateSnapshot: WeChatStructureSnapshot? = null
    @Volatile var lastGroupSnapshot: WeChatStructureSnapshot? = null
    @Volatile var lastPrivateSenderSource: WeChatFieldSource = WeChatFieldSource.UNKNOWN
    @Volatile var lastGroupSenderSource: WeChatFieldSource = WeChatFieldSource.UNKNOWN
    @Volatile var lastGroupConversationSource: WeChatFieldSource = WeChatFieldSource.UNKNOWN

    fun note(info: WeChatMessageInfo, snapshot: WeChatStructureSnapshot) {
        if (info.isGroupConversation == true) {
            lastGroupSnapshot = snapshot
            lastGroupSenderSource = info.senderSource
            lastGroupConversationSource = info.conversationSource
        } else {
            lastPrivateSnapshot = snapshot
            lastPrivateSenderSource = info.senderSource
        }
    }
}

object WeChatNotificationParser {
    fun parse(sbn: StatusBarNotification): ExtractedNotificationContent {
        val extras = sbn.notification.extras ?: Bundle.EMPTY
        val info = info(extras)
        val snapshot = snapshot(extras, info)
        WeChatDiagnostics.note(info, snapshot)
        val displayName = when {
            info.conversationName.isNullOrBlank().not() -> info.conversationName
            info.senderName.isNullOrBlank().not() -> info.senderName
            else -> null
        }
        val title = displayName?.let { "微信 · $it" } ?: "微信"
        val body = info.body?.ifBlank { null } ?: "该通知未提供正文"
        return ExtractedNotificationContent(title, body)
    }

    fun info(extras: Bundle): WeChatMessageInfo {
        val messages = messages(extras).orEmpty()
        val latestMessage = messages.lastOrNull()
        val latestPersonName = latestMessage?.let(::messageSender)?.cleanWechatText()
        val conversation = messagingConversationTitle(extras).cleanWechatText()
            .ifBlank { extras.safeText(Notification.EXTRA_CONVERSATION_TITLE).cleanWechatText() }
            .ifBlank { extras.safeText(Notification.EXTRA_TITLE_BIG).cleanWechatText() }
            .ifBlank { extras.safeText(Notification.EXTRA_SUB_TEXT).cleanWechatText() }
        val group = isGroupConversation(extras, conversation)
        val rawBody = firstNonBlank(
            latestMessage?.text?.toString().orEmpty(),
            extras.safeText(Notification.EXTRA_TEXT),
            extras.safeText(Notification.EXTRA_BIG_TEXT),
            latestTextLine(extras)
        )
        val prefix = if (group && latestPersonName.isNullOrBlank()) splitSenderPrefix(rawBody) else null
        val sender = firstNonBlank(
            latestPersonName.orEmpty(),
            prefix?.first.orEmpty(),
            if (!group) extras.safeText(Notification.EXTRA_TITLE) else ""
        )
        val senderSource = when {
            !latestPersonName.isNullOrBlank() -> WeChatFieldSource.MESSAGING_PERSON
            prefix != null -> WeChatFieldSource.BODY_PREFIX
            sender.isNotBlank() -> WeChatFieldSource.EXTRA_TITLE
            else -> WeChatFieldSource.UNKNOWN
        }
        val conversationSource = when {
            conversation.isNotBlank() && messagingConversationTitle(extras).cleanWechatText().isNotBlank() -> WeChatFieldSource.CONVERSATION_TITLE
            conversation.isNotBlank() && extras.safeText(Notification.EXTRA_CONVERSATION_TITLE).cleanWechatText().isNotBlank() -> WeChatFieldSource.CONVERSATION_TITLE
            conversation.isNotBlank() && extras.safeText(Notification.EXTRA_TITLE_BIG).cleanWechatText().isNotBlank() -> WeChatFieldSource.EXTRA_TITLE_BIG
            conversation.isNotBlank() && extras.safeText(Notification.EXTRA_SUB_TEXT).cleanWechatText().isNotBlank() -> WeChatFieldSource.EXTRA_SUB_TEXT
            else -> WeChatFieldSource.UNKNOWN
        }
        val body = (prefix?.let { "${it.first}：${it.second}" } ?: rawBody).cleanWechatText()
        return WeChatMessageInfo(
            senderName = sender.takeIf(String::isNotBlank),
            conversationName = conversation.takeIf(String::isNotBlank),
            body = body.takeIf(String::isNotBlank),
            isGroupConversation = group,
            senderSource = senderSource,
            conversationSource = conversationSource
        )
    }

    private fun snapshot(extras: Bundle, info: WeChatMessageInfo): WeChatStructureSnapshot {
        val messages = messages(extras).orEmpty()
        val person = messages.lastOrNull()?.let(::messageSender).orEmpty()
        return WeChatStructureSnapshot(
            messagingStyle = messages.isNotEmpty() || messagingConversationTitle(extras).isNotBlank(),
            messageCount = messages.size,
            person = messages.lastOrNull() != null,
            personName = person.cleanWechatText().isNotBlank(),
            conversationTitle = messagingConversationTitle(extras).cleanWechatText().isNotBlank(),
            extraTitle = extras.safeText(Notification.EXTRA_TITLE).cleanWechatText().isNotBlank(),
            extraTitleBig = extras.safeText(Notification.EXTRA_TITLE_BIG).cleanWechatText().isNotBlank(),
            extraText = extras.safeText(Notification.EXTRA_TEXT).cleanWechatText().isNotBlank(),
            extraBigText = extras.safeText(Notification.EXTRA_BIG_TEXT).cleanWechatText().isNotBlank(),
            extraTextLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().isNotEmpty(),
            extraSubText = extras.safeText(Notification.EXTRA_SUB_TEXT).cleanWechatText().isNotBlank(),
            extraSummaryText = extras.safeText(Notification.EXTRA_SUMMARY_TEXT).cleanWechatText().isNotBlank(),
            extraConversationTitle = extras.safeText(Notification.EXTRA_CONVERSATION_TITLE).cleanWechatText().isNotBlank(),
            extraTemplate = extras.safeText(Notification.EXTRA_TEMPLATE).cleanWechatText().isNotBlank(),
            prefixParserUsed = info.senderSource == WeChatFieldSource.BODY_PREFIX
        )
    }

    private fun splitSenderPrefix(value: String): Pair<String, String>? {
        val text = value.cleanWechatText()
        val index = listOf(text.indexOf('：'), text.indexOf(':')).filter { it in 1..20 }.minOrNull() ?: return null
        val sender = text.substring(0, index).cleanWechatText()
        val body = text.substring(index + 1).cleanWechatText()
        val forbidden = setOf("验证码", "校验码", "内容", "通知", "提醒")
        if (sender.isBlank() || body.isBlank() || sender in forbidden || sender.length > 20) return null
        return sender to body
    }

    private fun firstNonBlank(vararg values: String): String =
        values.map { it.cleanWechatText() }.firstOrNull(String::isNotBlank).orEmpty()

    private fun latestTextLine(extras: Bundle): String =
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it?.toString().orEmpty() }.lastOrNull { it.cleanWechatText().isNotBlank() }.orEmpty()

    private fun Bundle.safeText(key: String): String =
        (get(key) as? CharSequence)?.toString().orEmpty()

    private fun messages(extras: Bundle): List<Notification.MessagingStyle.Message>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(extras.getParcelableArray(Notification.EXTRA_MESSAGES))
        } else {
            null
        }

    private fun messagingConversationTitle(extras: Bundle): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            messages(extras)?.lastOrNull()?.let { "" }.orEmpty()
            extras.safeText(Notification.EXTRA_CONVERSATION_TITLE)
        } else {
            ""
        }

    private fun isGroupConversation(extras: Bundle, conversation: String): Boolean {
        val template = extras.safeText(Notification.EXTRA_TEMPLATE)
        val hasConversation = conversation.isNotBlank()
        val title = extras.safeText(Notification.EXTRA_TITLE).cleanWechatText()
        val text = extras.safeText(Notification.EXTRA_TEXT).cleanWechatText()
        return hasConversation || ("MessagingStyle" in template && title.isNotBlank() && text.contains("："))
    }

    private fun messageSender(message: Notification.MessagingStyle.Message): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            message.senderPerson?.name?.toString().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            message.sender?.toString().orEmpty()
        }

    private fun String.cleanWechatText(): String {
        val value = trim()
        return if (value.equals("null", ignoreCase = true)) "" else value
    }
}
