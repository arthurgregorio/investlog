package br.com.investlog.server.mail.repositories

import br.com.investlog.server.mail.rest.payloads.MailSecurity
import java.time.OffsetDateTime

data class MailSettings(
    val enabled: Boolean,
    val host: String?,
    val port: Int?,
    val security: MailSecurity,
    val username: String?,
    val passwordCiphertext: String?,
    val fromAddress: String?,
    val fromName: String?,
    val publicUrl: String?,
    val updatedAt: OffsetDateTime,
) {

    fun isConfigured(): Boolean = host != null && port != null && fromAddress != null && publicUrl != null
}
