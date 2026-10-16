package io.github.spasarnaudov.trackertavex.core.data

import io.github.spasarnaudov.trackertavex.core.model.ManualItem
import java.math.BigDecimal
import java.time.LocalDate

/** Pure, unit-testable validation applied before a `PUT /portfolio` request is built. */
object PortfolioValidator {
    fun validate(manualItems: List<ManualItem>): List<String> {
        val errors = mutableListOf<String>()

        for (item in manualItems) {
            if (item.markedForDeletion) continue
            if (item.name.isBlank()) {
                errors += "Manual item name cannot be empty."
            }
            if (item.quantity.signum() < 0) {
                errors += "Quantity for \"${item.name}\" cannot be negative."
            }
            val unitPrice = item.unitPrice
            if (unitPrice != null && unitPrice.signum() < 0) {
                errors += "Unit price for \"${item.name}\" cannot be negative."
            }
        }

        return errors
    }
}

/** Pure, unit-testable validation applied before adding or editing a purchase lot. */
object PurchaseValidator {
    fun validate(quantity: BigDecimal?, purchasePrice: BigDecimal?, purchaseDate: LocalDate?): List<String> {
        val errors = mutableListOf<String>()

        if (quantity == null || quantity.signum() <= 0) {
            errors += "Quantity must be greater than zero."
        } else if (quantity.stripTrailingZeros().scale() > 0) {
            errors += "Quantity must be a whole number."
        }
        if (purchasePrice == null || purchasePrice.signum() < 0) {
            errors += "Price paid must be zero or greater."
        }
        if (purchaseDate == null) {
            errors += "Purchase date is required."
        } else if (purchaseDate.isAfter(LocalDate.now())) {
            errors += "Purchase date cannot be in the future."
        }

        return errors
    }
}
