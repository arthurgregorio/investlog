package br.com.investlog.server.configurations.rest.payloads

import java.time.OffsetDateTime

data class ConfigurationResponse(
    val key: String,
    val value: String,
    val updatedAt: OffsetDateTime,
)
