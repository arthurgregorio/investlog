package br.com.investlog.server.wallets.rest.payloads

import com.fasterxml.jackson.annotation.JsonCreator
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

enum class WalletKind(val text: String, val jooqWalletKind: JooqWalletKind) {

    STOCKS("STOCKS", JooqWalletKind.STOCKS),
    CRYPTO("CRYPTO", JooqWalletKind.CRYPTO),
    FUNDS("FUNDS", JooqWalletKind.FUNDS);

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromText(text: String?): WalletKind =
            WalletKind.entries.find { it.text.equals(text, ignoreCase = true) }
                ?: throw IllegalArgumentException("Tipo de carteira inválido [$text]")
    }
}
