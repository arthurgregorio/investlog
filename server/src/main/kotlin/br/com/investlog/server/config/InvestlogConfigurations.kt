package br.com.investlog.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "investlog")
data class InvestlogConfigurations(
    val demoMode: DemoMode,
    val security: Security,
    val googleAuth: GoogleAuth,
    val brApi: BrApi,
    val coinGecko: CoinGecko,
    val awesomeApi: AwesomeApi
) {
    data class DemoMode(
        val enabled: Boolean
    )

    data class Security(
        val adminDefaultPassword: String,
        val totp: Totp
    ) {
        data class Totp(
            val enabled: Boolean,
            val lockoutMaxAttempts: Int,
            val lockoutBaseDuration: Duration
        )
    }

    data class GoogleAuth(
        val enabled: Boolean,
        val clientId: String,
        val clientSecret: String,
        val clientBaseUrl: String
    )

    data class BrApi(
        val baseUrl: String,
        val token: String
    )

    data class CoinGecko(
        val baseUrl: String,
        val apiKey: String,
        val apiKeyHeader: String
    ) {
        fun containsApiKey(): Boolean = apiKey.isNotBlank()
    }

    data class AwesomeApi(
        val baseUrl: String
    )
}
