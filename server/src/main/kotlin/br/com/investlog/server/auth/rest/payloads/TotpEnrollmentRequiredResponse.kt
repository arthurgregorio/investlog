package br.com.investlog.server.auth.rest.payloads

data class TotpEnrollmentRequiredResponse(
    val status: String = "needs_enrollment",
)
