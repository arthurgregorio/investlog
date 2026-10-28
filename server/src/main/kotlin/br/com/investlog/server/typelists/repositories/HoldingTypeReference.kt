package br.com.investlog.server.typelists.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind

data class HoldingTypeReference(
    val id: Long,
    val kinds: Set<WalletKind>,
)
