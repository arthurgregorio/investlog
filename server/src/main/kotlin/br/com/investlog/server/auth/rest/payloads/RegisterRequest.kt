package br.com.investlog.server.auth.rest.payloads

import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank(message = "name must not be blank")
    val name: String,
    @field:NotBlank(message = "email must not be blank")
    val email: String,
    @field:NotBlank(message = "password must not be blank")
    val password: String,
)
