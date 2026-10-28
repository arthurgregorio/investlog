package br.com.investlog.server.cash.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind
import java.util.UUID

data class CashWallet(
    val id: Long,
    val externalId: UUID,
    val name: String,
    val kind: WalletKind,
    val currency: String,
    val cashReconciled: Boolean,
)
