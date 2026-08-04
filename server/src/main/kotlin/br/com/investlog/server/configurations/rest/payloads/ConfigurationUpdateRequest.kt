package br.com.investlog.server.configurations.rest.payloads

import jakarta.validation.constraints.NotBlank

data class ConfigurationUpdateRequest(
    @field:NotBlank(message = "value must not be blank")
    val value: String,
)
