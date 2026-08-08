package br.com.investlog.server.auth.rest.payloads

import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank(message = "name não pode estar em branco")
    val name: String,
    @field:NotBlank(message = "email não pode estar em branco")
    val email: String,
    @field:NotBlank(message = "password não pode estar em branco")
    val password: String,
)
