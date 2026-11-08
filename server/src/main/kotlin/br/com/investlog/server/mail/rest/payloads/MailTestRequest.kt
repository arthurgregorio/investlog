package br.com.investlog.server.mail.rest.payloads

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class MailTestRequest(
    @field:NotBlank(message = "recipient não pode estar em branco")
    @field:Email(message = "recipient deve ser um e-mail válido")
    val recipient: String,
)
