package io.github.messagerelay

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.settingsDataStore by preferencesDataStore("relay_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val paused: Boolean = false,
    val persistentNotification: Boolean = true,
    val quietEnabled: Boolean = false,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
    val urgentKeywords: String = "验证码\n来电\n未接来电",
    val templateTitle: String = "[{{app}}] {{title}}",
    val templateBody: String = "{{body}}\n{{time}}",
    val themeMode: String = "system",
    val lockScreenOnly: Boolean = false,
    val primaryChannelId: String = "",
    val advancedAcknowledged: Boolean = false,
    val selectedTemplatePreset: String = TemplateCatalog.STANDARD_ID,
    val historyRetention: String = "30",
    val privacyDisplayMode: String = "full",
    val multiChannelSend: Boolean = false,
    val retryEnabled: Boolean = true,
    val maxRetryCount: Int = 3,
    val deliveryDelaySeconds: Int = 0,
    val mergeNotifications: Boolean = false,
    val mergeWindowSeconds: Int = 2,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckAt: Long = 0L
) {
    fun quietHours() = QuietHours(
        quietEnabled,
        runCatching { LocalTime.parse(quietStart) }.getOrDefault(LocalTime.of(22, 0)),
        runCatching { LocalTime.parse(quietEnd) }.getOrDefault(LocalTime.of(7, 0)),
        urgentKeywords.lines().filter(String::isNotBlank)
    )
}

class AppSettingsRepository(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val paused = booleanPreferencesKey("paused")
        val persistent = booleanPreferencesKey("persistent_notification")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = stringPreferencesKey("quiet_start")
        val quietEnd = stringPreferencesKey("quiet_end")
        val urgent = stringPreferencesKey("urgent_keywords")
        val templateTitle = stringPreferencesKey("template_title")
        val templateBody = stringPreferencesKey("template_body")
        val themeMode = stringPreferencesKey("theme_mode")
        val lockScreenOnly = booleanPreferencesKey("lock_screen_only")
        val primaryChannelId = stringPreferencesKey("primary_channel_id")
        val advancedAcknowledged = booleanPreferencesKey("advanced_acknowledged")
        val selectedTemplatePreset = stringPreferencesKey("selected_template_preset")
        val historyRetention = stringPreferencesKey("history_retention")
        val privacyDisplayMode = stringPreferencesKey("privacy_display_mode")
        val multiChannelSend = booleanPreferencesKey("multi_channel_send")
        val retryEnabled = booleanPreferencesKey("retry_enabled")
        val maxRetryCount = intPreferencesKey("max_retry_count")
        val deliveryDelaySeconds = intPreferencesKey("delivery_delay_seconds")
        val mergeNotifications = booleanPreferencesKey("merge_notifications")
        val mergeWindowSeconds = intPreferencesKey("merge_window_seconds")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map {
        AppSettings(
            it[Keys.onboarding] ?: false,
            it[Keys.paused] ?: false,
            it[Keys.persistent] ?: true,
            it[Keys.quietEnabled] ?: false,
            it[Keys.quietStart] ?: "22:00",
            it[Keys.quietEnd] ?: "07:00",
            it[Keys.urgent] ?: "验证码\n来电\n未接来电",
            it[Keys.templateTitle] ?: "[{{app}}] {{title}}",
            it[Keys.templateBody] ?: "{{body}}\n{{time}}",
            it[Keys.themeMode] ?: "system",
            it[Keys.lockScreenOnly] ?: false,
            it[Keys.primaryChannelId] ?: "",
            it[Keys.advancedAcknowledged] ?: false,
            it[Keys.selectedTemplatePreset] ?: TemplateCatalog.STANDARD_ID,
            it[Keys.historyRetention] ?: "30",
            it[Keys.privacyDisplayMode] ?: "full",
            it[Keys.multiChannelSend] ?: false,
            it[Keys.retryEnabled] ?: true,
            it[Keys.maxRetryCount] ?: 3,
            it[Keys.deliveryDelaySeconds] ?: 0,
            it[Keys.mergeNotifications] ?: false,
            it[Keys.mergeWindowSeconds] ?: 2,
            it[Keys.autoCheckUpdates] ?: true,
            it[Keys.lastUpdateCheckAt] ?: 0L
        )
    }

    suspend fun current() = settings.first()
    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboarding] = value }
    suspend fun setPaused(value: Boolean) = edit { it[Keys.paused] = value }
    suspend fun setPersistentNotification(value: Boolean) = edit { it[Keys.persistent] = value }
    suspend fun setQuiet(enabled: Boolean, start: String, end: String, urgent: String) = edit {
        it[Keys.quietEnabled] = enabled
        it[Keys.quietStart] = start
        it[Keys.quietEnd] = end
        it[Keys.urgent] = urgent
    }
    suspend fun setTemplate(title: String, body: String) = edit { it[Keys.templateTitle] = title; it[Keys.templateBody] = body }
    suspend fun setThemeMode(value: String) = edit { it[Keys.themeMode] = if (value in setOf("system", "light", "dark")) value else "system" }
    suspend fun setLockScreenOnly(value: Boolean) = edit { it[Keys.lockScreenOnly] = value }
    suspend fun setPrimaryChannelId(value: String) = edit { it[Keys.primaryChannelId] = value }
    suspend fun setAdvancedAcknowledged(value: Boolean) = edit { it[Keys.advancedAcknowledged] = value }
    suspend fun setSelectedTemplatePreset(value: String) = edit { it[Keys.selectedTemplatePreset] = value.ifBlank { TemplateCatalog.STANDARD_ID } }
    suspend fun setHistoryRetention(value: String) = edit { it[Keys.historyRetention] = if (value in setOf("7", "30", "90", "forever", "status_only")) value else "30" }
    suspend fun setPrivacyDisplayMode(value: String) = edit { it[Keys.privacyDisplayMode] = if (value in setOf("full", "masked", "hidden")) value else "full" }
    suspend fun setMultiChannelSend(value: Boolean) = edit { it[Keys.multiChannelSend] = value }
    suspend fun setRetryPolicy(enabled: Boolean, maxRetryCount: Int) = edit {
        it[Keys.retryEnabled] = enabled
        it[Keys.maxRetryCount] = maxRetryCount.coerceIn(0, 10)
    }
    suspend fun setNotificationTiming(merge: Boolean, mergeWindowSeconds: Int, delaySeconds: Int) = edit {
        it[Keys.mergeNotifications] = merge
        it[Keys.mergeWindowSeconds] = mergeWindowSeconds.coerceIn(1, 60)
        it[Keys.deliveryDelaySeconds] = delaySeconds.coerceIn(0, 300)
    }
    suspend fun setAutoCheckUpdates(value: Boolean) = edit { it[Keys.autoCheckUpdates] = value }
    suspend fun setLastUpdateCheckAt(value: Long) = edit { it[Keys.lastUpdateCheckAt] = value }
    private suspend fun edit(block: (MutablePreferences) -> Unit) { context.settingsDataStore.edit(block) }
}
