package br.com.investlog.server.profile.rest.dtos

data class ProfileResponse(
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val accentColor: String,
    val preferredCurrency: String,
)
