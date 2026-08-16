package br.com.investlog.server.config.http

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.config.http.CoinGeckoHttpClientsConfiguration.Companion.CLIENT_GROUP_NAME
import br.com.investlog.server.config.http.CoinGeckoHttpClientsConfiguration.Companion.PACKAGE_TO_SEARCH
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CLIENT_GROUP_NAME, basePackages = [PACKAGE_TO_SEARCH])
class CoinGeckoHttpClientsConfiguration(
    private val investlogConfigurations: InvestlogConfigurations
) {

    @Bean
    fun coinGeckoGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->

            val coinGeckoConfigs = investlogConfigurations.coinGecko

            groups.filterByName(CLIENT_GROUP_NAME)
                .forEachClient { _, builder ->
                    builder.baseUrl(coinGeckoConfigs.baseUrl)
                    if (coinGeckoConfigs.containsApiKey()) {
                        builder.defaultHeader(coinGeckoConfigs.apiKeyHeader, coinGeckoConfigs.apiKey)
                    }
                }
        }

    companion object {
        private const val CLIENT_GROUP_NAME = "coingecko"
        private const val PACKAGE_TO_SEARCH = "br.com.investlog.server.shared.http.coingecko"
    }
}
