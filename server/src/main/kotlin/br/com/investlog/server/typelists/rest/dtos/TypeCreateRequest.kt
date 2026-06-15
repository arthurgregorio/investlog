package br.com.investlog.server.typelists.rest.dtos

import jakarta.validation.constraints.NotBlank

data class TypeCreateRequest(
    @field:NotBlank(message = "name must not be blank")
    val name: String,
)
