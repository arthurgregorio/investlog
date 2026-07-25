package br.com.investlog.server.profile.rest.payloads

import jakarta.validation.constraints.NotBlank

data class PasswordChangeRequest(
    @field:NotBlank(message = "currentPassword must not be blank")
    val currentPassword: String,
    @field:NotBlank(message = "newPassword must not be blank")
    val newPassword: String,
)
