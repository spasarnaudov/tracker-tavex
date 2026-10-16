package io.github.spasarnaudov.trackertavex.core.model

import java.time.LocalDateTime

data class AdminUserSummary(
    val id: Long,
    val username: String,
    val role: String,
    val active: Boolean?,
    val createdAt: LocalDateTime?,
)

/** Schema is unknown (ASSUMED endpoint) — kept as an ordered label/value list to render generically. */
data class LoginStatEntry(val label: String, val value: String)

data class LoginHistoryEntry(val fields: List<LoginStatEntry>)

data class LogFile(
    val name: String,
    val sizeBytes: Long?,
    val modifiedAt: LocalDateTime?,
)

data class LogContent(
    val name: String,
    val lines: List<String>,
)
