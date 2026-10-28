package br.com.investlog.server.typelists.rest.payloads

import br.com.investlog.server.wallets.rest.payloads.WalletKind
import java.util.UUID

data class HoldingTypeResponse(
    val id: UUID,
    val name: String,
    val kinds: List<WalletKind>,
    val usageCount: Int,
)
