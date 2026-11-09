package br.com.investlog.server.mail.rest.payloads

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class MailSettingsRequest(
    @field:NotBlank(message = "host não pode estar em branco")
    val host: String,
    @field:Min(value = 1, message = "port deve estar entre 1 e 65535")
    @field:Max(value = 65535, message = "port deve estar entre 1 e 65535")
    val port: Int,
    val security: MailSecurity,
    val username: String? = null,
    val password: String? = null,
    @field:NotBlank(message = "fromAddress não pode estar em branco")
    @field:Email(message = "fromAddress deve ser um e-mail válido")
    val fromAddress: String,
    val fromName: String? = null,
    @field:NotBlank(message = "publicUrl não pode estar em branco")
    @field:Pattern(regexp = "^https?://[^\\s/?#]+[^\\s]*$", message = "publicUrl deve ser um endereço http ou https absoluto")
    val publicUrl: String,
)
