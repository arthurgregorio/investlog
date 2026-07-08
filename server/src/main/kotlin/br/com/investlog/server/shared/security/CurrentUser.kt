package br.com.investlog.server.shared.security

import br.com.investlog.server.profile.rest.payloads.AccentColor
import java.util.UUID

data class CurrentUser(
    val id: Long,
    val externalId: UUID,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val accentColor: AccentColor,
    val preferredCurrency: String,
    val role: UserRole,
    val status: UserStatus,
    val authProvider: AuthProvider,
    val totpEnabled: Boolean,
)
