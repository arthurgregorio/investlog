package br.com.investlog.server.usersadmin.rest.payloads

import jakarta.validation.constraints.NotBlank

data class PasswordResetRequest(
    @field:NotBlank(message = "newPassword não pode estar em branco")
    val newPassword: String,
)
