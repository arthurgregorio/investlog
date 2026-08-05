package br.com.investlog.server.config.http

import br.com.investlog.server.config.http.CoinGeckoHttpClientsConfig.Companion.CLIENT_GROUP_NAME
import br.com.investlog.server.config.http.CoinGeckoHttpClientsConfig.Companion.PACKAGE_TO_SEARCH
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CLIENT_GROUP_NAME, basePackages = [PACKAGE_TO_SEARCH])
class CoinGeckoHttpClientsConfig(
    @Value($$"${investlog.coingecko.base-url}")
    private val coinGeckoBaseUrl: String,
    @Value($$"${investlog.coingecko.api-key:}")
    private val coinGeckoApiKey: String,
    @Value($$"${investlog.coingecko.api-key-header}")
    private val coinGeckoApiKeyHeader: String,
) {

    @Bean
    fun coinGeckoGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.filterByName(CLIENT_GROUP_NAME)
                .forEachClient { _, builder ->
                    builder.baseUrl(coinGeckoBaseUrl)
                    if (coinGeckoApiKey.isNotBlank()) {
                        builder.defaultHeader(coinGeckoApiKeyHeader, coinGeckoApiKey)
                    }
                }
        }

    companion object {
        private const val CLIENT_GROUP_NAME = "coingecko"
        private const val PACKAGE_TO_SEARCH = "br.com.investlog.server.cryptopricesync.http"
    }
}
