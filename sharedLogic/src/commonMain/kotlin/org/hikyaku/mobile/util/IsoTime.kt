package org.hikyaku.mobile.util

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** YYYY-MM-DD slice of the UTC date for [epochMillis]. Not for display — see [epochMillisToDisplayDate]. */
fun epochMillisToIsoDate(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date.toString()

private val MONTH_NAMES = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** Formats [date] for user-facing display, e.g. "24 July 2026". */
fun formatDisplayDate(date: LocalDate): String = "${date.day} ${MONTH_NAMES[date.month.ordinal]} ${date.year}"

/** The UTC date for [epochMillis], formatted for user-facing display, e.g. "24 July 2026". */
fun epochMillisToDisplayDate(epochMillis: Long): String =
    formatDisplayDate(Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date)

/** Formats the date portion of an ISO-8601 [isoDateTime] for user-facing display, e.g. "24 July 2026". */
fun formatIsoAsDisplayDate(isoDateTime: String): String =
    runCatching { formatDisplayDate(LocalDate.parse(isoDateTime.take(10))) }.getOrDefault(isoDateTime)

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

/** Formats an ISO-8601 [isoDateTime] for user-facing display, e.g. "24 July 2026 14:30", or the raw string if unparseable. */
fun formatIsoAsDateTime(isoDateTime: String): String =
    runCatching {
        val dateTime = Instant.parse(isoDateTime).toLocalDateTime(TimeZone.UTC)
        "${formatDisplayDate(dateTime.date)} ${dateTime.time.toString().take(5)}"
    }.getOrDefault(isoDateTime)

/** Zero-padded `HH:MM` for a wall-clock hour/minute, e.g. as read from a Material3 TimePicker. */
fun formatHourMinute(hour: Int, minute: Int): String = LocalTime(hour, minute).toString()

/** The (hour, minute) wall-clock components of an ISO-8601 [isoDateTime] string, or null if unparseable. */
fun isoDateTimeToHourMinute(isoDateTime: String): Pair<Int, Int>? =
    runCatching { Instant.parse(isoDateTime).toLocalDateTime(TimeZone.UTC) }
        .getOrNull()
        ?.let { it.hour to it.minute }
