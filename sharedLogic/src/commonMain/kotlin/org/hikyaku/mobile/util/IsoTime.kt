package org.hikyaku.mobile.util

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** YYYY-MM-DD slice of the UTC date for [epochMillis], for compact display. */
fun epochMillisToIsoDate(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date.toString()

/**
 * Combines a UTC date (epoch millis at UTC midnight, the form a Material3 DatePicker returns) with a
 * wall-clock hour/minute into a full ISO-8601 instant string, e.g. for scheduling a shift start or
 * package arrival.
 */
fun combineDateAndTimeToIsoUtc(dateMillisAtUtcMidnight: Long, hour: Int, minute: Int): String {
    val date = Instant.fromEpochMilliseconds(dateMillisAtUtcMidnight).toLocalDateTime(TimeZone.UTC).date
    return date.atTime(hour, minute).toInstant(TimeZone.UTC).toString()
}

/** UTC-midnight epoch millis for the date portion of an ISO-8601 [isoDateTime] string, or null if unparseable. */
fun isoDateToEpochMillisUtc(isoDateTime: String): Long? =
    runCatching { LocalDate.parse(isoDateTime.take(10)).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
        .getOrNull()

/** Formats an ISO-8601 [isoDateTime] as a compact `YYYY-MM-DD HH:MM`, or the raw string if unparseable. */
fun formatIsoAsDateTime(isoDateTime: String): String =
    runCatching {
        val dateTime = Instant.parse(isoDateTime).toLocalDateTime(TimeZone.UTC)
        "${dateTime.date} ${dateTime.time.toString().take(5)}"
    }.getOrDefault(isoDateTime)

/** Zero-padded `HH:MM` for a wall-clock hour/minute, e.g. as read from a Material3 TimePicker. */
fun formatHourMinute(hour: Int, minute: Int): String = LocalTime(hour, minute).toString()

/** The (hour, minute) wall-clock components of an ISO-8601 [isoDateTime] string, or null if unparseable. */
fun isoDateTimeToHourMinute(isoDateTime: String): Pair<Int, Int>? =
    runCatching { Instant.parse(isoDateTime).toLocalDateTime(TimeZone.UTC) }
        .getOrNull()
        ?.let { it.hour to it.minute }
