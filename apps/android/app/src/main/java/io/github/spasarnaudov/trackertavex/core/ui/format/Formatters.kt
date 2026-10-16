package io.github.spasarnaudov.trackertavex.core.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formats money for display with exactly two decimals; never mutates the source value. */
fun BigDecimal.formatMoney(locale: Locale = Locale.getDefault()): String {
    val scaled = setScale(2, RoundingMode.HALF_UP)
    return String.format(locale, "%,.2f", scaled)
}

fun BigDecimal?.formatMoneyOrDash(locale: Locale = Locale.getDefault()): String =
    this?.formatMoney(locale) ?: "—"

private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun LocalDateTime.formatDisplay(): String = format(dateTimeFormatter)
