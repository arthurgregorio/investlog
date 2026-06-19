package br.com.investlog.server.wallets.rest.payloads

import com.fasterxml.jackson.annotation.JsonCreator

enum class WalletKind(val text: String) {

    STOCKS("STOCKS"),
    CRYPTO("CRYPTO"),
    FUNDS("FUNDS");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromText(text: String?): WalletKind =
            WalletKind.entries.find { it.text.equals(text, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid wallet kind [$text]")
    }
}
