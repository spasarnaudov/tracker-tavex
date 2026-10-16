package io.github.spasarnaudov.trackertavex.core.data

import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import java.time.LocalDate

/** Pure, unit-testable validation for the `custom` chart range's start/end dates. */
object DateRangeValidator {
    /** Returns a human-readable error, or null when the range is valid (or not applicable). */
    fun validateCustomRange(range: ChartRange, startDate: LocalDate?, endDate: LocalDate?): String? {
        if (range != ChartRange.CUSTOM) return null
        if (startDate == null || endDate == null) return "Start and end date are required for a custom range."
        if (startDate.isAfter(endDate)) return "Start date must be before the end date."
        if (endDate.isAfter(LocalDate.now())) return "End date cannot be in the future."
        return null
    }
}
