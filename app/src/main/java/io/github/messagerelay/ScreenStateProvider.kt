package io.github.messagerelay

import android.app.KeyguardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display

enum class LockDecision { ALLOW, FILTER }

data class LockStateSnapshot(
    val isKeyguardLocked: Boolean?,
    val isDeviceLocked: Boolean?,
    val displayState: Int?,
    val isInteractive: Boolean?,
    val capturedAt: Long,
    val decision: LockDecision,
    val reason: String
)

fun interface LockStateProvider {
    fun snapshot(): LockStateSnapshot
}

class AndroidLockStateProvider(private val context: Context) : LockStateProvider {
    override fun snapshot(): LockStateSnapshot {
        val capturedAt = System.currentTimeMillis()
        val keyguard = runCatching { context.getSystemService(KeyguardManager::class.java) }.getOrNull()
        val power = runCatching { context.getSystemService(PowerManager::class.java) }.getOrNull()
        val display = runCatching {
            val manager = context.getSystemService(DisplayManager::class.java)
            manager?.getDisplay(Display.DEFAULT_DISPLAY) ?: manager?.displays?.firstOrNull()
        }.getOrNull()
        val keyguardLocked = runCatching { keyguard?.isKeyguardLocked }.getOrNull()
        val deviceLocked = runCatching { keyguard?.isDeviceLocked }.getOrNull()
        val displayState = runCatching { display?.state }.getOrNull()
        val interactive = runCatching { power?.isInteractive }.getOrNull()
        val decision = decide(keyguardLocked, deviceLocked, displayState)
        return LockStateSnapshot(
            isKeyguardLocked = keyguardLocked,
            isDeviceLocked = deviceLocked,
            displayState = displayState,
            isInteractive = interactive,
            capturedAt = capturedAt,
            decision = decision.first,
            reason = decision.second
        )
    }

    private fun decide(keyguardLocked: Boolean?, deviceLocked: Boolean?, displayState: Int?): Pair<LockDecision, String> = when {
        keyguardLocked == true -> LockDecision.ALLOW to "锁屏界面已显示，允许仅息屏推送"
        displayState in setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND) ->
            LockDecision.ALLOW to "屏幕处于息屏或 AOD 状态，允许仅息屏推送"
        keyguardLocked == false && deviceLocked == false && displayState in setOf(Display.STATE_ON, Display.STATE_ON_SUSPEND, Display.STATE_VR) ->
            LockDecision.FILTER to "手机当前已解锁，该应用设置为仅息屏时推送"
        else -> LockDecision.ALLOW to "锁屏状态无法可靠判断，默认允许推送以避免漏发"
    }
}

sealed class ScreenOffOnlyDecision {
    data class Allow(val state: LockStateSnapshot? = null) : ScreenOffOnlyDecision()
    data class Filter(val state: LockStateSnapshot, val reason: String) : ScreenOffOnlyDecision()
}

object ScreenOffOnlyPolicy {
    @Volatile var lastState: LockStateSnapshot? = null
        private set
    @Volatile var lastFilterReason: String = ""
        private set

    fun decide(screenOffOnly: Boolean, provider: LockStateProvider): ScreenOffOnlyDecision {
        if (!screenOffOnly) return ScreenOffOnlyDecision.Allow()
        val state = runCatching { provider.snapshot() }.getOrElse {
            LockStateSnapshot(null, null, null, null, System.currentTimeMillis(), LockDecision.ALLOW, "锁屏状态读取异常，默认允许推送以避免漏发")
        }
        lastState = state
        return if (state.decision == LockDecision.ALLOW) {
            ScreenOffOnlyDecision.Allow(state)
        } else {
            lastFilterReason = state.reason
            ScreenOffOnlyDecision.Filter(state, state.reason)
        }
    }
}

fun displayStateName(value: Int?): String = when (value) {
    Display.STATE_OFF -> "STATE_OFF"
    Display.STATE_ON -> "STATE_ON"
    Display.STATE_DOZE -> "STATE_DOZE"
    Display.STATE_DOZE_SUSPEND -> "STATE_DOZE_SUSPEND"
    Display.STATE_ON_SUSPEND -> "STATE_ON_SUSPEND"
    Display.STATE_VR -> "STATE_VR"
    Display.STATE_UNKNOWN -> "STATE_UNKNOWN"
    null -> "UNKNOWN"
    else -> "STATE_$value"
}
