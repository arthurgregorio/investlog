package br.com.investlog.server.walletledger.repositories

data class WalletLedgerPage(
    val rows: List<WalletLedgerRow>,
    val total: Long,
)
