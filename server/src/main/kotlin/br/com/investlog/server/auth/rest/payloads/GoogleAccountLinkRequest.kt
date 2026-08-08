package br.com.investlog.server.auth.rest.payloads

import jakarta.validation.constraints.NotBlank

data class GoogleAccountLinkRequest(
    @field:NotBlank(message = "linkToken não pode estar em branco")
    val linkToken: String,
    @field:NotBlank(message = "password não pode estar em branco")
    val password: String,
)
