package io.github.messagerelay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SmsStateDecision(
    val title: String,
    val body: String,
    val subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID
)

object SmsDiagnostics {
    @Volatile var lastSource: String = "UNKNOWN"
    @Volatile var lastNumberStatus: String = "未获取"
    @Volatile var lastBodyStatus: String = "未获取"
    @Volatile var lastSimStatus: String = "未获取"

    fun note(source: String, number: String, body: String, subscriptionId: Int) {
        lastSource = source
        lastNumberStatus = if (number.isBlank()) "未获取" else "已获取"
        lastBodyStatus = if (body.isBlank()) "未获取" else "已获取"
        lastSimStatus = if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) "未获取" else "已获取"
    }
}

object SmsMessageParser {
    fun fromIntent(context: Context, intent: Intent): SmsStateDecision? {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return null
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (messages.isEmpty()) {
            SmsDiagnostics.note("SMS_RECEIVED", "", "", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            return null
        }
        val number = messages.firstNotNullOfOrNull { message ->
            message.displayOriginatingAddress?.takeIf(String::isNotBlank)
                ?: message.originatingAddress?.takeIf(String::isNotBlank)
        }.orEmpty()
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty().ifBlank { it.messageBody.orEmpty() } }.trim()
        val subscriptionId = intent.subscriptionId()
        val simName = resolveSimName(context, subscriptionId)
        SmsDiagnostics.note("SMS_RECEIVED", number, body, subscriptionId)
        SmsDuplicateGuard.registerBroadcast(number, body)
        val safeNumber = number.ifBlank { "未知号码" }
        val structuredBody = buildString {
            appendLine("短信正文：${body.ifBlank { "该短信未提供正文" }}")
            appendLine("号码：$safeNumber")
            appendLine("SIM：$simName")
            appendLine("接收时间：${TimeFormatter.formatRecordDetailTime(System.currentTimeMillis())}")
        }.trim()
        return SmsStateDecision(safeNumber, structuredBody, subscriptionId)
    }

    private fun Intent.subscriptionId(): Int {
        val keys = listOf(
            "subscription",
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "android.telephony.extra.SUBSCRIPTION_ID",
            "simSubscription",
            "slot"
        )
        return keys.firstNotNullOfOrNull { key ->
            if (hasExtra(key)) getIntExtra(key, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            else null
        } ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    private fun resolveSimName(context: Context, subscriptionId: Int): String {
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return "未知 SIM"
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "未知 SIM"
        }
        return runCatching {
            val dao = RelayDatabase.get(context).relayDao()
            val aliases = kotlinx.coroutines.runBlocking { dao.simAliases() }
            SimRepository(context).activeSims(aliases, CallMonitorManager.registeredSubscriptionIds())
                .firstOrNull { it.subscriptionId == subscriptionId }
                ?.displayName
                .orEmpty()
        }.getOrDefault("").ifBlank { "SIM $subscriptionId" }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            SmsDiagnostics.note("SMS_RECEIVED", "", "", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            return
        }
        val decision = SmsMessageParser.fromIntent(context.applicationContext, intent) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            RelayEngine.processSmsEvent(context.applicationContext, decision)
        }
    }
}
