package br.com.investlog.server.usersadmin.rest.payloads

import br.com.investlog.server.shared.security.UserRole

data class RoleUpdateRequest(
    val role: UserRole,
)
