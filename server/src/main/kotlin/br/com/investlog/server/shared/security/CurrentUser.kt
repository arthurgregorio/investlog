package br.com.investlog.server.shared.security

import java.util.UUID

data class CurrentUser(
    val id: Long,
    val externalId: UUID,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val accentColor: String,
    val preferredCurrency: String,
)
