package io.github.spasarnaudov.trackertavex.core.data

import io.github.spasarnaudov.trackertavex.core.model.ManualItem
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PortfolioValidatorTest {

    private fun manualItem(
        name: String = "Gold ring",
        quantity: String = "1",
        unitPrice: String? = "70",
        markedForDeletion: Boolean = false,
    ) = ManualItem(
        id = null,
        name = name,
        quantity = BigDecimal(quantity),
        unitPrice = unitPrice?.let { BigDecimal(it) },
        priceAssetId = null,
        includeInChart = true,
        value = null,
        markedForDeletion = markedForDeletion,
    )

    @Test
    fun `valid manual items produce no errors`() {
        val errors = PortfolioValidator.validate(listOf(manualItem()))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `negative manual item quantity is rejected`() {
        val errors = PortfolioValidator.validate(listOf(manualItem(quantity = "-5")))
        assertTrue(errors.any { it.contains("Gold ring") })
    }

    @Test
    fun `negative manual item unit price is rejected`() {
        val errors = PortfolioValidator.validate(listOf(manualItem(unitPrice = "-10")))
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `blank manual item name is rejected`() {
        val errors = PortfolioValidator.validate(listOf(manualItem(name = "  ")))
        assertTrue(errors.any { it.contains("name") })
    }

    @Test
    fun `manual items marked for deletion are not validated`() {
        val errors = PortfolioValidator.validate(
            listOf(manualItem(name = "", quantity = "-1", unitPrice = "-1", markedForDeletion = true)),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `null manual unit price (manual pricing left blank) is not an error`() {
        val errors = PortfolioValidator.validate(listOf(manualItem(unitPrice = null)))
        assertTrue(errors.isEmpty())
    }
}
