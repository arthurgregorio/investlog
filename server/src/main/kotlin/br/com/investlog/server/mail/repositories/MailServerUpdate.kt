package br.com.investlog.server.mail.repositories

import br.com.investlog.server.mail.rest.payloads.MailSecurity

data class MailServerUpdate(
    val host: String,
    val port: Int,
    val security: MailSecurity,
    val username: String?,
    val passwordCiphertext: String?,
    val fromAddress: String,
    val fromName: String?,
    val publicUrl: String,
)
