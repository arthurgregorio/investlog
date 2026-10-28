package br.com.investlog.server.walletledger.rest.payloads

enum class WalletLedgerEventType {
    PURCHASE,
    CONTRIBUTION,
    WITHDRAWAL,
    REINVESTMENT,
    MOVE_IN,
    MOVE_OUT,
    TRANSFER_IN,
    TRANSFER_OUT,
    CASH_WITHDRAWAL,
}
