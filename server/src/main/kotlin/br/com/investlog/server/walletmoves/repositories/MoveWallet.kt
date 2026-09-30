package br.com.investlog.server.walletmoves.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind

data class MoveWallet(
    val id: Long,
    val kind: WalletKind,
    val currency: String,
)
