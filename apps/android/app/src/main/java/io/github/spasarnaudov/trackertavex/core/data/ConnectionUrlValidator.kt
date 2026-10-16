package io.github.spasarnaudov.trackertavex.core.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Pure, unit-testable validation for the Connection Settings base URL field. */
object ConnectionUrlValidator {
    fun validate(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "Enter a server address."
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "The address must start with http:// or https://."
        }
        if (trimmed.toHttpUrlOrNull() == null) return "This doesn't look like a valid URL."
        return null
    }
}
