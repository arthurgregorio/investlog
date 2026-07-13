package br.com.investlog.server.usersadmin.rest.payloads

import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser.Status
import br.com.investlog.server.shared.security.UserRole
import java.util.UUID

data class UserAdminResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val role: UserRole,
    val status: Status,
    val authProvider: AuthProvider,
    val totpEnabled: Boolean,
)
