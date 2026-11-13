package br.com.investlog.server.wallets.rest.payloads

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class WalletCreateRequest(
    @field:NotBlank
    val name: String,
    @field:NotNull
    val kind: WalletKind,
    @field:NotBlank
    @field:Size(max = 5, message = "moeda deve ter no máximo 5 letras")
    @field:Pattern(regexp = "^[A-Za-z]{3,5}$", message = "moeda deve conter de 3 a 5 letras")
    val currency: String
)
