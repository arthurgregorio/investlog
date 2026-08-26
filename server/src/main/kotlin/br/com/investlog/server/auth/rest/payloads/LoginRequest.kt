package br.com.investlog.server.auth.rest.payloads

data class LoginRequest(
    val email: String,
    val password: String,
    val totpCode: String? = null,
    val trustDevice: Boolean = false,
)
