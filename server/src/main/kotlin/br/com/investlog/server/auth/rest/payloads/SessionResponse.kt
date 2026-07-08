package br.com.investlog.server.auth.rest.payloads

import br.com.investlog.server.shared.security.UserRole
import br.com.investlog.server.shared.security.UserStatus

data class SessionResponse(
    val name: String,
    val email: String,
    val role: UserRole,
    val status: UserStatus,
)
