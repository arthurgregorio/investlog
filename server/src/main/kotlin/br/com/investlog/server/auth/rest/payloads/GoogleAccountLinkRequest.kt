package br.com.investlog.server.auth.rest.payloads

import jakarta.validation.constraints.NotBlank

data class GoogleAccountLinkRequest(
    @field:NotBlank(message = "linkToken must not be blank")
    val linkToken: String,
    @field:NotBlank(message = "password must not be blank")
    val password: String,
)
