package br.com.investlog.server.auth.rest.payloads

import br.com.investlog.server.shared.security.UserRole

data class SessionResponse(
    val name: String,
    val email: String,
    val role: UserRole,
)
