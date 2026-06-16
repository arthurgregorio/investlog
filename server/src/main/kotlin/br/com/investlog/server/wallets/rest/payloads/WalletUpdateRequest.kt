package br.com.investlog.server.wallets.rest.payloads

import jakarta.validation.constraints.NotBlank

data class WalletUpdateRequest(
    @field:NotBlank val name: String,
)
