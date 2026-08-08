package br.com.investlog.server.typelists.rest.payloads

import jakarta.validation.constraints.NotBlank

data class TypeCreateRequest(
    @field:NotBlank(message = "name não pode estar em branco")
    val name: String,
)
