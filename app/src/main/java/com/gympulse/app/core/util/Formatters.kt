package com.gympulse.app.core.util

import com.gympulse.app.domain.model.WeightUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Formatters {
    val locale: Locale = Locale.FRANCE
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val symbols = DecimalFormatSymbols(locale).apply { groupingSeparator = '\u202F' }
    private val oneDecimal = DecimalFormat("#,##0.#", symbols)
    private val twoDecimals = DecimalFormat("#,##0.##", symbols)
    private val integer = DecimalFormat("#,##0", symbols)

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val fullDateYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", locale)
    private val shortDay = DateTimeFormatter.ofPattern("EEE d", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)

    /** "05:07" or "1:05:07". */
    fun clock(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0) / 1000)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }

    /** Countdown rounded up, so that "00:01" is shown until the very end. */
    fun countdown(remainingMs: Long): String = clock(((remainingMs + 999) / 1000) * 1000)

    /** "45 s", "2 min", "1 min 30". */
    fun seconds(totalSeconds: Int): String {
        if (totalSeconds < 60) return "$totalSeconds s"
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (seconds == 0) "$minutes min" else "$minutes min ${"%02d".format(seconds)}"
    }

    /** "48 min", "1 h 12". */
    fun duration(millis: Long): String {
        val totalMinutes = (millis / 60_000.0).roundToInt()
        if (totalMinutes < 60) return "$totalMinutes min"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (minutes == 0) "$hours h" else "$hours h ${"%02d".format(minutes)}"
    }

    /** Total hours with one decimal, for large aggregates. */
    fun hours(millis: Long): String = "${oneDecimal.format(millis / 3_600_000.0)} h"

    fun number(value: Double): String = oneDecimal.format(value)

    fun integer(value: Number): String = integer.format(value)

    fun weightValue(kg: Double, unit: WeightUnit): String = twoDecimals.format(roundForDisplay(unit.fromKg(kg)))

    fun weight(kg: Double, unit: WeightUnit): String = "${weightValue(kg, unit)} ${unit.symbol}"

    private fun roundForDisplay(value: Double): Double = (value * 100).roundToInt() / 100.0

    fun volume(kg: Double, unit: WeightUnit): String {
        val value = unit.fromKg(kg)
        return when {
            unit == WeightUnit.KG && value >= 10_000 -> "${oneDecimal.format(value / 1000)} t"
            unit == WeightUnit.LB && value >= 10_000 -> "${oneDecimal.format(value / 1000)} k lb"
            else -> "${integer.format(value)} ${unit.symbol}"
        }
    }

    /** Compact volume for chart axes: tonnes (k lb) from 1 000 so that ticks share one unit. */
    fun volumeCompact(kg: Double, unit: WeightUnit): String {
        val value = unit.fromKg(kg)
        return if (value >= 1000) {
            "${oneDecimal.format(value / 1000)} ${if (unit == WeightUnit.KG) "t" else "k lb"}"
        } else {
            "${integer.format(value)} ${unit.symbol}"
        }
    }

    /** "+12 %" / "−3 %". */
    fun percentChange(ratio: Double): String {
        val percent = (ratio * 100).roundToInt()
        val sign = if (percent > 0) "+" else if (percent < 0) "−" else ""
        return "$sign${abs(percent)} %"
    }

    fun signed(value: Double, suffix: String = ""): String {
        val sign = if (value > 0) "+" else if (value < 0) "−" else ""
        return "$sign${oneDecimal.format(abs(value))}$suffix"
    }

    private fun date(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun dayMonth(epochMillis: Long): String = dayMonth.format(date(epochMillis)).trimEnd('.')

    fun dayMonthYear(epochMillis: Long): String = dayMonthYear.format(date(epochMillis))

    fun fullDate(epochMillis: Long): String {
        val date = date(epochMillis)
        val formatter = if (date.year == LocalDate.now(zone).year) fullDate else fullDateYear
        return formatter.format(date).replaceFirstChar { it.titlecase(locale) }
    }

    fun monthYear(epochMillis: Long): String = monthYear.format(date(epochMillis)).replaceFirstChar { it.titlecase(locale) }

    fun shortMonth(epochMillis: Long): String = shortMonth.format(date(epochMillis)).trimEnd('.').replaceFirstChar { it.titlecase(locale) }

    fun shortDay(epochMillis: Long): String = shortDay.format(date(epochMillis)).replaceFirstChar { it.titlecase(locale) }

    fun time(epochMillis: Long): String = time.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** "Aujourd'hui", "Hier", "Il y a 3 jours", or a date. */
    fun relativeDay(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val days = ChronoUnit.DAYS.between(date(epochMillis), date(now))
        return when {
            days <= 0L -> "Aujourd'hui"
            days == 1L -> "Hier"
            days < 7L -> "Il y a $days jours"
            else -> dayMonth(epochMillis)
        }
    }

    fun greeting(now: Long = System.currentTimeMillis()): String {
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        return when (hour) {
            in 5..11 -> "Bonjour"
            in 12..17 -> "Bon après-midi"
            else -> "Bonsoir"
        }
    }

    fun distance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m" else "${oneDecimal.format(meters / 1000)} km"

    /** Parses user input accepting both "," and "." as decimal separators. */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()
}
