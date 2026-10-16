package io.github.spasarnaudov.trackertavex.core.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PurchaseValidatorTest {

    @Test
    fun `valid purchase produces no errors`() {
        val errors = PurchaseValidator.validate(BigDecimal("2"), BigDecimal("50"), LocalDate.now())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `fractional quantity is rejected`() {
        val errors = PurchaseValidator.validate(BigDecimal("2.5"), BigDecimal("50"), LocalDate.now())
        assertTrue(errors.any { it.contains("whole number") })
    }

    @Test
    fun `zero quantity is rejected`() {
        val errors = PurchaseValidator.validate(BigDecimal.ZERO, BigDecimal("50"), LocalDate.now())
        assertTrue(errors.any { it.contains("Quantity") })
    }

    @Test
    fun `negative quantity is rejected`() {
        val errors = PurchaseValidator.validate(BigDecimal("-1"), BigDecimal("50"), LocalDate.now())
        assertTrue(errors.any { it.contains("Quantity") })
    }

    @Test
    fun `null quantity is rejected`() {
        val errors = PurchaseValidator.validate(null, BigDecimal("50"), LocalDate.now())
        assertTrue(errors.any { it.contains("Quantity") })
    }

    @Test
    fun `negative price is rejected`() {
        val errors = PurchaseValidator.validate(BigDecimal("1"), BigDecimal("-1"), LocalDate.now())
        assertTrue(errors.any { it.contains("Price") })
    }

    @Test
    fun `zero price is valid`() {
        val errors = PurchaseValidator.validate(BigDecimal("1"), BigDecimal.ZERO, LocalDate.now())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `missing date is rejected`() {
        val errors = PurchaseValidator.validate(BigDecimal("1"), BigDecimal("50"), null)
        assertTrue(errors.any { it.contains("date") })
    }

    @Test
    fun `future date is rejected`() {
        val errors = PurchaseValidator.validate(BigDecimal("1"), BigDecimal("50"), LocalDate.now().plusDays(1))
        assertTrue(errors.any { it.contains("future") })
    }
}
