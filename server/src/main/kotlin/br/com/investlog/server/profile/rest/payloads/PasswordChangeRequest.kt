package br.com.investlog.server.profile.rest.payloads

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class PasswordChangeRequest(
    @field:NotBlank(message = "currentPassword não pode estar em branco")
    val currentPassword: String,
    @field:NotBlank(message = "newPassword não pode estar em branco")
    @field:Size(min = 8, max = 128, message = "newPassword deve ter entre 8 e 128 caracteres")
    @field:Pattern(regexp = ".*[A-Z].*", message = "newPassword deve conter ao menos uma letra maiúscula")
    @field:Pattern(regexp = ".*[0-9].*", message = "newPassword deve conter ao menos um número")
    val newPassword: String,
)
