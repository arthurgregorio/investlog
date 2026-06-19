package br.com.investlog.server.typelists.rest.payloads

import java.util.UUID

data class TypeResponse(
    val id: UUID,
    val name: String
)
