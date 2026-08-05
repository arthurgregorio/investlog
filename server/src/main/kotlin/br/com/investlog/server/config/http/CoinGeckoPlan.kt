package br.com.investlog.server.config.http

enum class CoinGeckoPlan(val apiKeyHeader: String, val defaultBaseUrl: String) {

    DEMO("x-cg-demo-api-key", "https://api.coingecko.com/api/v3"),
    PRO("x-cg-pro-api-key", "https://pro-api.coingecko.com/api/v3");

    companion object {
        fun fromText(text: String?): CoinGeckoPlan =
            entries.find { it.name.equals(text, ignoreCase = true) } ?: DEMO
    }
}
