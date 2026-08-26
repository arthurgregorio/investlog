package br.com.investlog.server.auth.rest.payloads

import java.time.OffsetDateTime
import java.util.UUID

data class TrustedDeviceResponse(
    val id: UUID,
    val label: String,
    val lastUsedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
)
