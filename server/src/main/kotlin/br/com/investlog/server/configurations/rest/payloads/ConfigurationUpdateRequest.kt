package br.com.investlog.server.configurations.rest.payloads

import jakarta.validation.constraints.NotBlank

data class ConfigurationUpdateRequest(
    @field:NotBlank(message = "value não pode estar em branco")
    val value: String,
)
