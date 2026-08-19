package br.com.investlog.server.auth.rest.payloads

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "name não pode estar em branco")
    val name: String,
    @field:NotBlank(message = "email não pode estar em branco")
    val email: String,
    @field:NotBlank(message = "password não pode estar em branco")
    @field:Size(min = 8, max = 128, message = "password deve ter entre 8 e 128 caracteres")
    val password: String,
)
