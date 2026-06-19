package br.com.investlog.server.shared.rest.payloads

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class CurrencyCode(@JsonValue val text: String) {

    BRL("BRL"),
    USD("USD");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromText(text: String?): CurrencyCode =
            CurrencyCode.entries.find { it.text.equals(text, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid currency code [$text]")
    }
}