package br.com.investlog.server.usersadmin.rest.payloads

import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.UserRole
import br.com.investlog.server.shared.security.UserStatus
import java.util.UUID

data class UserAdminResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val role: UserRole,
    val status: UserStatus,
    val authProvider: AuthProvider,
    val totpEnabled: Boolean,
)
