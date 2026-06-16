package br.com.investlog.server.wallets.rest.payloads

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class WalletCreateRequest(
    @field:NotBlank val name: String,
    @field:NotNull val kind: WalletKind,
    @field:NotBlank val currency: String,
)
