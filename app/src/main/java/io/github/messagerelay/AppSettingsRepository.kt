package io.github.messagerelay

import android.content.Context
import androidx.datastore.preferences.core.*
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
    val urgentKeywords: String = "紧急",
    val templateTitle: String = "[{{app}}] {{title}}",
    val templateBody: String = "{{body}}\n{{time}}"
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
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map {
        AppSettings(
            it[Keys.onboarding] ?: false,
            it[Keys.paused] ?: false,
            it[Keys.persistent] ?: true,
            it[Keys.quietEnabled] ?: false,
            it[Keys.quietStart] ?: "22:00",
            it[Keys.quietEnd] ?: "07:00",
            it[Keys.urgent] ?: "紧急",
            it[Keys.templateTitle] ?: "[{{app}}] {{title}}",
            it[Keys.templateBody] ?: "{{body}}\n{{time}}"
        )
    }

    suspend fun current() = settings.first()
    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboarding] = value }
    suspend fun setPaused(value: Boolean) = edit { it[Keys.paused] = value }
    suspend fun setPersistentNotification(value: Boolean) = edit { it[Keys.persistent] = value }
    suspend fun setQuiet(enabled: Boolean, start: String, end: String, urgent: String) = edit {
        it[Keys.quietEnabled] = enabled; it[Keys.quietStart] = start; it[Keys.quietEnd] = end; it[Keys.urgent] = urgent
    }
    suspend fun setTemplate(title: String, body: String) = edit { it[Keys.templateTitle] = title; it[Keys.templateBody] = body }
    private suspend fun edit(block: (MutablePreferences) -> Unit) { context.settingsDataStore.edit(block) }
}
