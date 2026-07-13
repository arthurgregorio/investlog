package br.com.investlog.server.auth.rest.payloads

data class TotpEnrollResponse(
    val secretKey: String,
    val qrCodeDataUri: String,
)
