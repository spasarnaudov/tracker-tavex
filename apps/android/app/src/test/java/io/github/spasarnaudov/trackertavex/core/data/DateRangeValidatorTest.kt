package io.github.spasarnaudov.trackertavex.core.data

import io.github.spasarnaudov.trackertavex.core.model.ChartRange
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateRangeValidatorTest {

    @Test
    fun `non-custom ranges skip validation entirely`() {
        assertNull(DateRangeValidator.validateCustomRange(ChartRange.ONE_MONTH, null, null))
    }

    @Test
    fun `custom range requires both dates`() {
        assertNotNull(DateRangeValidator.validateCustomRange(ChartRange.CUSTOM, null, LocalDate.now()))
        assertNotNull(DateRangeValidator.validateCustomRange(ChartRange.CUSTOM, LocalDate.now(), null))
    }

    @Test
    fun `start date after end date is rejected`() {
        val error = DateRangeValidator.validateCustomRange(
            ChartRange.CUSTOM,
            LocalDate.now(),
            LocalDate.now().minusDays(1),
        )
        assertNotNull(error)
    }

    @Test
    fun `end date in the future is rejected`() {
        val error = DateRangeValidator.validateCustomRange(
            ChartRange.CUSTOM,
            LocalDate.now().minusDays(5),
            LocalDate.now().plusDays(1),
        )
        assertNotNull(error)
    }

    @Test
    fun `a valid past range is accepted`() {
        val error = DateRangeValidator.validateCustomRange(
            ChartRange.CUSTOM,
            LocalDate.now().minusDays(10),
            LocalDate.now().minusDays(1),
        )
        assertNull(error)
    }

    @Test
    fun `same start and end date is accepted`() {
        val today = LocalDate.now()
        assertNull(DateRangeValidator.validateCustomRange(ChartRange.CUSTOM, today, today))
    }
}
