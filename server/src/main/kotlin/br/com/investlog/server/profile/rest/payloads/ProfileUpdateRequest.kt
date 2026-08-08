package br.com.investlog.server.profile.rest.payloads

import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class ProfileUpdateRequest(
    val accentColor: AccentColor?,
    val preferredCurrency: CurrencyCode?
)

enum class AccentColor(@JsonValue val text: String) {

    BLUE("blue"),
    INDIGO("indigo"),
    TEAL("teal"),
    GREEN("green");

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromText(text: String?): AccentColor = entries.find { it.text.equals(text, ignoreCase = true) }
            ?: throw IllegalArgumentException("Cor de destaque inválida [$text]")
    }
}
