package br.com.investlog.server.auth.rest.payloads

import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser.Status
import br.com.investlog.server.shared.security.UserRole

data class SessionResponse(
    val name: String,
    val email: String,
    val role: UserRole,
    val status: Status,
    val authProvider: AuthProvider,
    val demoModeEnabled: Boolean,
)
