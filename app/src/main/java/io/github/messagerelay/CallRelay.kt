package io.github.messagerelay

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class CallEventType(val title: String) {
    MISSED_CALL("未接来电"),
    INCOMING_RINGING("来电提醒"),
    CALL_ANSWERED("来电已接通")
}

object CallEventTypes {
    val default: Set<CallEventType> = setOf(CallEventType.MISSED_CALL, CallEventType.INCOMING_RINGING)

    fun serialize(types: Set<CallEventType>): String = types.joinToString(",") { it.name }

    fun parse(value: String?): Set<CallEventType> {
        val parsed = value.orEmpty()
            .split(',', '\n')
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { raw -> CallEventType.entries.firstOrNull { it.name == raw } }
            .toSet()
        return parsed.ifEmpty { default }
    }

    fun isValid(value: String?): Boolean = parse(value).isNotEmpty()
}

data class SimInfoModel(
    val subscriptionId: Int,
    val fingerprint: String,
    val displayName: String,
    val systemDisplayName: String,
    val carrierName: String,
    val slotIndex: Int,
    val countryIso: String,
    val listening: Boolean = false,
    val status: String = ""
)

data class CallMonitorStatus(
    val phoneStateGranted: Boolean,
    val callLogGranted: Boolean,
    val contactsGranted: Boolean,
    val activeSimCount: Int,
    val registeredListenerCount: Int,
    val lastStateText: String,
    val recentRingingText: String = "",
    val recentOffhookText: String = "",
    val recentIdleText: String = "",
    val lastNumberSource: String = "",
    val lastCallLogResult: String = "",
    val lastCallLogError: String = "",
    val lastError: String
) {
    val phoneAvailable: Boolean get() = phoneStateGranted && registeredListenerCount > 0
}

data class PhoneNumberInfo(
    val rawNumber: String,
    val displayNumber: String,
    val contactName: String = "",
    val location: String = "",
    val carrier: String = ""
)

data class CallStateDecision(
    val eventType: CallEventType,
    val title: String,
    val body: String,
    val subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID
)

data class PendingCallEvent(
    val eventType: CallEventType,
    val title: String,
    val number: String,
    val subscriptionId: Int,
    val ringingStartedAt: Long
)

class CallSessionTracker(private val subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
    private var hasRung = false
    private var hasAnswered = false
    private var ringingNotified = false
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var number: String = ""
    private var ringingStartedAt: Long = 0L

    fun onStateChanged(state: Int, incomingNumber: String? = null, now: Long = System.currentTimeMillis()): PendingCallEvent? {
        if (state == lastState && state != TelephonyManager.CALL_STATE_RINGING) return null
        val safeNumber = incomingNumber?.takeIf(String::isNotBlank) ?: number
        if (safeNumber.isNotBlank()) number = safeNumber
        return when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                hasRung = true
                hasAnswered = false
                lastState = state
                if (ringingStartedAt == 0L) ringingStartedAt = now
                if (ringingNotified) null else {
                    ringingNotified = true
                    PendingCallEvent(CallEventType.INCOMING_RINGING, "来电提醒", number, subscriptionId, ringingStartedAt)
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                lastState = state
                if (hasRung && !hasAnswered) {
                    hasAnswered = true
                    PendingCallEvent(CallEventType.CALL_ANSWERED, "来电已接通", number, subscriptionId, ringingStartedAt.takeIf { it > 0 } ?: now)
                } else {
                    null
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                lastState = state
                val missed = hasRung && !hasAnswered
                val event = if (missed) PendingCallEvent(CallEventType.MISSED_CALL, "未接来电", number, subscriptionId, ringingStartedAt.takeIf { it > 0 } ?: now) else null
                reset()
                event
            }
            else -> {
                lastState = state
                null
            }
        }
    }

    private fun reset() {
        hasRung = false
        hasAnswered = false
        ringingNotified = false
        number = ""
        ringingStartedAt = 0L
    }
}

class SimRepository(private val context: Context) {
    fun activeSims(aliases: List<SimAliasEntity> = emptyList(), listeningIds: Set<Int> = emptySet()): List<SimInfoModel> {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return emptyList()
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val infos = runCatching {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                manager.activeSubscriptionInfoList.orEmpty()
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
        val aliasByFingerprint = aliases.associateBy { it.simFingerprint }
        return infos.sortedBy { it.simSlotIndex }.map { info ->
            val fingerprint = simFingerprint(info)
            val systemName = info.displayName?.toString().orEmpty().ifBlank { info.carrierName?.toString().orEmpty() }
            val carrier = info.carrierName?.toString().orEmpty()
            val fallback = systemName.ifBlank { carrier }.ifBlank { "SIM ${info.simSlotIndex + 1}" }
            val alias = aliasByFingerprint[fingerprint]?.alias.orEmpty().trim()
            SimInfoModel(
                subscriptionId = info.subscriptionId,
                fingerprint = fingerprint,
                displayName = alias.ifBlank { fallback },
                systemDisplayName = systemName.ifBlank { "SIM ${info.simSlotIndex + 1}" },
                carrierName = carrier,
                slotIndex = info.simSlotIndex,
                countryIso = info.countryIso.orEmpty().uppercase(Locale.ROOT),
                listening = info.subscriptionId in listeningIds,
                status = if (info.subscriptionId in listeningIds) "正常监听" else "未监听"
            )
        }
    }

    companion object {
        fun simFingerprint(info: SubscriptionInfo): String {
            val raw = listOf(
                "sub:${info.subscriptionId}",
                "slot:${info.simSlotIndex}",
                "carrier:${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.carrierId else 0}",
                "country:${info.countryIso}",
                "name:${info.displayName}",
                "carrierName:${info.carrierName}"
            ).joinToString("|")
            return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

class LegacyPhoneNumberEnricher(private val context: Context) {
    fun buildDecision(event: PendingCallEvent, sim: SimInfoModel?): CallStateDecision {
        val candidate = event.number.ifBlank { latestCallLogNumber(event).orEmpty() }
        val numberInfo = enrich(candidate, sim?.countryIso.orEmpty())
        val body = buildString {
            numberInfo.contactName.takeIf(String::isNotBlank)?.let { appendLine("联系人：$it") }
            appendLine("号码：${numberInfo.displayNumber}")
            numberInfo.location.takeIf(String::isNotBlank)?.let { appendLine("归属地：$it") }
            numberInfo.carrier.takeIf(String::isNotBlank)?.let { appendLine("号码段运营商：$it") }
            appendLine("SIM：${sim?.displayName ?: "未知 SIM"}")
            appendLine("${if (event.eventType == CallEventType.CALL_ANSWERED) "接通时间" else "来电时间"}：${TimeFormatter.formatRecordDetailTime(System.currentTimeMillis())}")
        }.trim()
        return CallStateDecision(event.eventType, event.title, body, event.subscriptionId)
    }

    fun enrich(number: String, countryIso: String): PhoneNumberInfo {
        if (number.isBlank()) return PhoneNumberInfo("", "未知号码")
        val displayNumber = number.trim()
        val contact = lookupContact(displayNumber)
        val parsed = runCatching {
            val region = countryIso.ifBlank { Locale.getDefault().country.ifBlank { "CN" } }
            PhoneNumberUtil.getInstance().parse(displayNumber, region)
        }.getOrNull()
        val formatted = parsed?.let {
            runCatching { PhoneNumberUtil.getInstance().format(it, PhoneNumberUtil.PhoneNumberFormat.NATIONAL) }.getOrNull()
        }.orEmpty().ifBlank { displayNumber }
        val location = parsed?.let {
            runCatching { PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(it, Locale.CHINA) }.getOrNull()
        }.orEmpty()
        val carrier = parsed?.let {
            runCatching { PhoneNumberToCarrierMapper.getInstance().getNameForNumber(it, Locale.CHINA) }.getOrNull()
        }.orEmpty()
        return PhoneNumberInfo(displayNumber, formatted, contact, location, carrier)
    }

    private fun lookupContact(number: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return ""
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null).useFirstString()
        }.getOrDefault("")
    }

    private fun latestCallLogNumber(event: PendingCallEvent): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return ""
        return runCatching {
            val minTime = (event.ringingStartedAt - 30_000).coerceAtLeast(0)
            val types = if (event.eventType == CallEventType.MISSED_CALL) {
                "${CallLog.Calls.MISSED_TYPE}"
            } else {
                "${CallLog.Calls.INCOMING_TYPE}"
            }
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.TYPE} IN ($types)",
                arrayOf(minTime.toString()),
                "${CallLog.Calls.DATE} DESC"
            ).useFirstString()
        }.getOrDefault("")
    }
}

data class PhoneNumberResolution(
    val number: String,
    val source: String,
    val callLogResult: String = "",
    val error: String = ""
)

class PhoneNumberResolver(private val context: Context) {
    fun resolve(event: PendingCallEvent): PhoneNumberResolution {
        RealtimeIncomingCallStore.recent()?.let { realtime ->
            return PhoneNumberResolution(realtime.phoneNumber, realtime.source)
        }
        val callbackNumber = event.number.trim()
        if (callbackNumber.isNotBlank()) return PhoneNumberResolution(callbackNumber, "PHONE_STATE_BROADCAST_VALID_NUMBER")
        if (event.eventType == CallEventType.INCOMING_RINGING) {
            return PhoneNumberResolution("", "来电提醒阶段系统未提供号码")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return PhoneNumberResolution("", "未授权 READ_CALL_LOG", error = "无法从通话记录补全号码")
        }

        val delays = longArrayOf(0L, 350L, 900L, 1_500L)
        var latest = PhoneNumberResolution("", "通话记录", callLogResult = "未命中")
        delays.forEachIndexed { index, delay ->
            if (delay > 0) Thread.sleep(delay)
            latest = queryLatestCallLog(event)
            if (latest.number.isNotBlank()) return latest.copy(source = "通话记录重试 ${index + 1}")
        }
        return latest
    }

    private fun queryLatestCallLog(event: PendingCallEvent): PhoneNumberResolution = runCatching {
        val minTime = (event.ringingStartedAt - 45_000).coerceAtLeast(0)
        val types = if (event.eventType == CallEventType.MISSED_CALL) {
            "${CallLog.Calls.MISSED_TYPE}"
        } else {
            "${CallLog.Calls.INCOMING_TYPE}"
        }
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.NUMBER_PRESENTATION
        )
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.TYPE} IN ($types)",
            arrayOf(minTime.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                PhoneNumberResolution("", "通话记录", callLogResult = "没有匹配记录")
            } else {
                val presentation = cursor.getIntOrDefault(CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.PRESENTATION_ALLOWED)
                val rawNumber = cursor.getStringOrNull(CallLog.Calls.NUMBER)
                val number = when (presentation) {
                    CallLog.Calls.PRESENTATION_RESTRICTED -> "隐藏号码"
                    CallLog.Calls.PRESENTATION_PAYPHONE -> "公用电话"
                    CallLog.Calls.PRESENTATION_UNKNOWN -> ""
                    else -> rawNumber.orEmpty()
                }
                PhoneNumberResolution(
                    number = number,
                    source = "通话记录",
                    callLogResult = "命中 ${TimeFormatter.formatRecordDetailTime(cursor.getLongOrDefault(CallLog.Calls.DATE, 0L))} / ${maskPhoneNumber(number)}"
                )
            }
        } ?: PhoneNumberResolution("", "通话记录", callLogResult = "查询结果为空")
    }.getOrElse {
        PhoneNumberResolution("", "通话记录", error = "${it.javaClass.simpleName}: ${it.message.orEmpty()}")
    }
}

class PhoneNumberEnricher(private val context: Context) {
    fun buildDecision(event: PendingCallEvent, sim: SimInfoModel?): CallStateDecision {
        val resolution = PhoneNumberResolver(context).resolve(event)
        CallMonitorManager.noteNumberResolution(resolution)
        val numberInfo = enrich(resolution.number, sim?.countryIso.orEmpty())
        val contactName = numberInfo.contactName.ifBlank {
            if (numberInfo.rawNumber.isBlank()) "未知联系人" else numberInfo.displayNumber
        }
        val body = buildString {
            appendLine("联系人：$contactName")
            appendLine("号码：${numberInfo.displayNumber}")
            appendLine("归属地：${numberInfo.location.ifBlank { "无法识别" }}")
            numberInfo.carrier.takeIf(String::isNotBlank)?.let { appendLine("号码段运营商：$it") }
            appendLine("SIM：${sim?.displayName ?: "未知 SIM"}")
            appendLine("提醒：${event.title}")
            appendLine("接收时间：${TimeFormatter.formatRecordDetailTime(System.currentTimeMillis())}")
        }.trim()
        return CallStateDecision(event.eventType, event.title, body, event.subscriptionId)
    }

    fun enrich(number: String, countryIso: String): PhoneNumberInfo {
        if (number.isBlank()) return PhoneNumberInfo("", "未知号码")
        val displayNumber = number.trim()
        if (displayNumber in setOf("隐藏号码", "公用电话")) return PhoneNumberInfo("", displayNumber)
        val contact = lookupContact(displayNumber)
        val parsed = runCatching {
            val region = countryIso.ifBlank { Locale.getDefault().country.ifBlank { "CN" } }
            PhoneNumberUtil.getInstance().parse(displayNumber, region)
        }.getOrNull()
        val formatted = parsed?.let {
            runCatching { PhoneNumberUtil.getInstance().format(it, PhoneNumberUtil.PhoneNumberFormat.NATIONAL) }.getOrNull()
        }.orEmpty().ifBlank { displayNumber }
        val location = parsed?.let {
            runCatching { PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(it, Locale.CHINA) }.getOrNull()
        }.orEmpty()
        val carrier = parsed?.let {
            runCatching { PhoneNumberToCarrierMapper.getInstance().getNameForNumber(it, Locale.CHINA) }.getOrNull()
        }.orEmpty()
        return PhoneNumberInfo(displayNumber, formatted, contact, location, carrier)
    }

    private fun lookupContact(number: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return ""
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null).useFirstString()
        }.getOrDefault("")
    }
}

private fun Cursor?.useFirstString(): String =
    this?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }.orEmpty()

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.getIntOrDefault(columnName: String, defaultValue: Int): Int {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getInt(index) else defaultValue
}

private fun Cursor.getLongOrDefault(columnName: String, defaultValue: Long): Long {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else defaultValue
}

object CallMonitorManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val trackers = ConcurrentHashMap<Int, CallSessionTracker>()
    private val legacyTracker = CallSessionTracker()
    private val phoneListeners = ConcurrentHashMap<Int, PhoneStateListener>()
    private val callbacks = ConcurrentHashMap<Int, TelephonyCallback>()
    private var subscriptionListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    @Volatile private var lastStateText: String = ""
    @Volatile private var recentRingingText: String = ""
    @Volatile private var recentOffhookText: String = ""
    @Volatile private var recentIdleText: String = ""
    @Volatile private var lastNumberSource: String = ""
    @Volatile private var lastCallLogResult: String = ""
    @Volatile private var lastCallLogError: String = ""
    @Volatile private var lastError: String = ""

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            unregister(appContext)
            lastError = "READ_PHONE_STATE 未授权，电话功能不可用"
            return
        }
        runCatching {
            registerSubscriptionListener(appContext)
            val sims = SimRepository(appContext).activeSims()
            val activeIds = sims.map { it.subscriptionId }.toSet()
            (phoneListeners.keys + callbacks.keys).filterNot { it in activeIds }.forEach { unregisterOne(appContext, it) }
            sims.forEach { sim -> registerOne(appContext, sim.subscriptionId) }
            lastError = ""
        }.onFailure { lastError = "电话监听注册失败：${it.javaClass.simpleName}" }
    }

    fun status(context: Context): CallMonitorStatus {
        val activeCount = runCatching { SimRepository(context).activeSims().size }.getOrDefault(0)
        return CallMonitorStatus(
            phoneStateGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
            callLogGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED,
            contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
            activeSimCount = activeCount,
            registeredListenerCount = phoneListeners.size + callbacks.size,
            lastStateText = lastStateText,
            recentRingingText = recentRingingText,
            recentOffhookText = recentOffhookText,
            recentIdleText = recentIdleText,
            lastNumberSource = lastNumberSource,
            lastCallLogResult = lastCallLogResult,
            lastCallLogError = lastCallLogError,
            lastError = lastError
        )
    }

    fun registeredSubscriptionIds(): Set<Int> = phoneListeners.keys + callbacks.keys

    fun noteNumberResolution(resolution: PhoneNumberResolution) {
        lastNumberSource = "${resolution.source} / ${TimeFormatter.formatLogTime(System.currentTimeMillis())}"
        lastCallLogResult = resolution.callLogResult.ifBlank { "无" }
        lastCallLogError = resolution.error.ifBlank { "无" }
    }

    fun handleLegacyBroadcast(context: Context, state: Int, number: String?) {
        noteState(SubscriptionManager.INVALID_SUBSCRIPTION_ID, state, number)
        val event = legacyTracker.onStateChanged(state, number) ?: return
        dispatch(context.applicationContext, event)
    }

    private fun registerSubscriptionListener(context: Context) {
        if (subscriptionListener != null) return
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return
        subscriptionListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                refresh(context)
            }
        }.also { listener ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    manager.addOnSubscriptionsChangedListener(ContextCompat.getMainExecutor(context), listener)
                } else {
                    @Suppress("DEPRECATION")
                    manager.addOnSubscriptionsChangedListener(listener)
                }
            }
                .onFailure { lastError = "SIM 变化监听注册失败：${it.javaClass.simpleName}" }
        }
    }

    private fun registerOne(context: Context, subscriptionId: Int) {
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || phoneListeners.containsKey(subscriptionId) || callbacks.containsKey(subscriptionId)) return
        val telephony = context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(subscriptionId) ?: return
        trackers.putIfAbsent(subscriptionId, CallSessionTracker(subscriptionId))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerCallback(context, telephony, subscriptionId)
        } else {
            registerPhoneStateListener(context, telephony, subscriptionId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerCallback(context: Context, telephony: TelephonyManager, subscriptionId: Int) {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                onState(context, subscriptionId, state, null)
            }
        }
        telephony.registerTelephonyCallback(context.mainExecutor, callback)
        callbacks[subscriptionId] = callback
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener(context: Context, telephony: TelephonyManager, subscriptionId: Int) {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Android")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                onState(context.applicationContext, subscriptionId, state, phoneNumber)
            }
        }
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        phoneListeners[subscriptionId] = listener
    }

    private fun onState(context: Context, subscriptionId: Int, state: Int, number: String?) {
        noteState(subscriptionId, state, number)
        val event = trackers.getOrPut(subscriptionId) { CallSessionTracker(subscriptionId) }.onStateChanged(state, number) ?: return
        dispatch(context.applicationContext, event)
    }

    private fun noteState(subscriptionId: Int, state: Int, number: String?) {
        val text = "subscriptionId=$subscriptionId state=${callStateName(state)} number=${maskPhoneNumber(number.orEmpty()).ifBlank { "空" }} time=${TimeFormatter.formatLogTime(System.currentTimeMillis())}"
        lastStateText = text
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> recentRingingText = text
            TelephonyManager.CALL_STATE_OFFHOOK -> recentOffhookText = text
            TelephonyManager.CALL_STATE_IDLE -> recentIdleText = text
        }
    }

    private fun dispatch(context: Context, event: PendingCallEvent) {
        scope.launch {
            if (event.eventType == CallEventType.INCOMING_RINGING && event.number.isBlank()) {
                kotlinx.coroutines.delay(300)
            }
            val aliases = runCatching { RelayDatabase.get(context).relayDao().simAliases() }.getOrDefault(emptyList())
            val sim = SimRepository(context).activeSims(aliases, registeredSubscriptionIds()).firstOrNull { it.subscriptionId == event.subscriptionId }
            val decision = PhoneNumberEnricher(context).buildDecision(event, sim)
            RelayEngine.processCallEvent(context, decision)
        }
    }

    private fun unregister(context: Context) {
        (phoneListeners.keys + callbacks.keys).toList().forEach { unregisterOne(context, it) }
    }

    @Suppress("DEPRECATION")
    private fun unregisterOne(context: Context, subscriptionId: Int) {
        val telephony = context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(subscriptionId)
        phoneListeners.remove(subscriptionId)?.let { runCatching { telephony?.listen(it, PhoneStateListener.LISTEN_NONE) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callbacks.remove(subscriptionId)?.let { callback ->
                runCatching { telephony?.unregisterTelephonyCallback(callback) }
            }
        }
        trackers.remove(subscriptionId)
    }
}

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val hadRegisteredListener = CallMonitorManager.registeredSubscriptionIds().isNotEmpty()
        CallMonitorManager.refresh(context)
        if (hadRegisteredListener) return
        val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        CallMonitorManager.handleLegacyBroadcast(context, state, number)
    }
}

fun maskPhoneNumber(value: String): String =
    value.replace(Regex("(\\d{3})\\d{4}(\\d{4})"), "$1****$2")

private fun callStateName(state: Int): String = when (state) {
    TelephonyManager.CALL_STATE_RINGING -> "RINGING"
    TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
    TelephonyManager.CALL_STATE_IDLE -> "IDLE"
    else -> state.toString()
}
