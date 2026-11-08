package br.com.investlog.server.mail.rest.payloads

import java.time.OffsetDateTime

data class MailSettingsResponse(
    val enabled: Boolean,
    val configured: Boolean,
    val host: String?,
    val port: Int?,
    val security: MailSecurity,
    val username: String?,
    val passwordSet: Boolean,
    val fromAddress: String?,
    val fromName: String?,
    val publicUrl: String?,
    val updatedAt: OffsetDateTime,
)
