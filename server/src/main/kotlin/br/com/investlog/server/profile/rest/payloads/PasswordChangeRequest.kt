package br.com.investlog.server.profile.rest.payloads

import jakarta.validation.constraints.NotBlank

data class PasswordChangeRequest(
    @field:NotBlank(message = "currentPassword não pode estar em branco")
    val currentPassword: String,
    @field:NotBlank(message = "newPassword não pode estar em branco")
    val newPassword: String,
)
