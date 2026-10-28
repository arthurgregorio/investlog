package br.com.investlog.server.typelists.rest.payloads

import br.com.investlog.server.wallets.rest.payloads.WalletKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class HoldingTypeCreateRequest(
    @field:NotBlank(message = "name não pode estar em branco")
    val name: String,
    @field:NotEmpty(message = "kinds deve ter ao menos um tipo de carteira")
    val kinds: Set<WalletKind> = emptySet(),
)
