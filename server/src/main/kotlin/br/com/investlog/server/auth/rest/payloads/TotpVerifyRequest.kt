package br.com.investlog.server.auth.rest.payloads

data class TotpVerifyRequest(
    val email: String,
    val password: String,
    val code: String,
)
