package br.com.investlog.server.usersadmin.rest.payloads

import jakarta.validation.constraints.NotBlank

data class PasswordResetRequest(
    @field:NotBlank(message = "newPassword must not be blank")
    val newPassword: String,
)
