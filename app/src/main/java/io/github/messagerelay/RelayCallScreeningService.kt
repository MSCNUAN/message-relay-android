package io.github.messagerelay

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

data class RealtimeIncomingCall(
    val phoneNumber: String,
    val createdAt: Long,
    val source: String = "CALL_SCREENING"
)

object RealtimeIncomingCallStore {
    private const val TTL_MS = 5_000L
    @Volatile private var latest: RealtimeIncomingCall? = null

    fun put(number: String, now: Long = System.currentTimeMillis()) {
        if (number.isNotBlank()) latest = RealtimeIncomingCall(number, now)
    }

    fun recent(now: Long = System.currentTimeMillis()): RealtimeIncomingCall? =
        latest?.takeIf { now - it.createdAt <= TTL_MS && it.phoneNumber.isNotBlank() }
}

class RelayCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allowResponse())
            return
        }
        val number = details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        RealtimeIncomingCallStore.put(number)
        respondToCall(details, allowResponse())
    }

    private fun allowResponse(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setSilenceCall(false)
            }
            .build()
}
