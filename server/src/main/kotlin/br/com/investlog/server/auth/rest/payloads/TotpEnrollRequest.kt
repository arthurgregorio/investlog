package br.com.investlog.server.auth.rest.payloads

data class TotpEnrollRequest(
    val email: String,
    val password: String,
)
