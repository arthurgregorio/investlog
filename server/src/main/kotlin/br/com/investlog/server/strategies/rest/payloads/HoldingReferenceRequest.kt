package br.com.investlog.server.strategies.rest.payloads

import br.com.investlog.server.wallets.rest.payloads.WalletKind
import java.util.UUID

data class HoldingReferenceRequest(
    val kind: WalletKind,
    val id: UUID,
)
