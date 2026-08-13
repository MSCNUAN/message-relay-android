package io.github.messagerelay

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object TimeFormatter {
    private val locale: Locale = Locale.CHINA
    private val listSameYear = DateTimeFormatter.ofPattern("MM月dd日 HH:mm", locale)
    private val listCrossYear = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm", locale)
    private val detail = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", locale)
    private val log = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", locale)

    fun parseTimestampSafely(value: Any?): Instant? = when (value) {
        null -> null
        is Instant -> value
        is Long -> Instant.ofEpochMilli(value)
        is Int -> Instant.ofEpochMilli(value.toLong())
        is Number -> Instant.ofEpochMilli(value.toLong())
        is String -> parseString(value)
        else -> null
    }

    fun formatRecordListTime(value: Any?, nowMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): String {
        val instant = parseTimestampSafely(value) ?: return "时间未知"
        val dateTime = LocalDateTime.ofInstant(instant, zoneId)
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val date = dateTime.toLocalDate()
        val today = now.toLocalDate()
        return when {
            date == today -> "今天 ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm", locale))}"
            date == today.minusDays(1) -> "昨天 ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm", locale))}"
            date.year == today.year -> dateTime.format(listSameYear)
            else -> dateTime.format(listCrossYear)
        }
    }

    fun formatRecordDetailTime(value: Any?, zoneId: ZoneId = ZoneId.systemDefault()): String =
        parseTimestampSafely(value)?.let { LocalDateTime.ofInstant(it, zoneId).format(detail) } ?: "时间未知"

    fun formatLogTime(value: Any?, zoneId: ZoneId = ZoneId.systemDefault()): String =
        parseTimestampSafely(value)?.atZone(zoneId)?.format(log) ?: "时间未知"

    private fun parseString(value: String): Instant? {
        val text = value.trim()
        if (text.isBlank()) return null
        text.toLongOrNull()?.let { return Instant.ofEpochMilli(it) }
        runCatching { return Instant.parse(text) }
        runCatching { return LocalDateTime.parse(text, detail).atZone(ZoneId.systemDefault()).toInstant() }
        runCatching { return LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant() }
        return null
    }
}
