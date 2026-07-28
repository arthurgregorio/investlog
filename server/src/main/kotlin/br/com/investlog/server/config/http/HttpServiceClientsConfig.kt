package br.com.investlog.server.config.http

import br.com.investlog.server.cryptopricesync.http.CoinGeckoClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices
import java.time.Duration

@ImportHttpServices(group = "coingecko", types = [CoinGeckoClient::class])
@Configuration(proxyBeanMethods = false)
class HttpServiceClientsConfig {

    @Bean
    fun coinGeckoHttpServiceGroupConfigurer(
        @Value("\${spring.http.serviceclient.coingecko.base-url}") baseUrl: String,
        @Value("\${spring.http.serviceclient.coingecko.connect-timeout}") connectTimeout: Duration,
        @Value("\${spring.http.serviceclient.coingecko.read-timeout}") readTimeout: Duration,
    ): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.filterByName("coingecko").forEachClient { _, clientBuilder ->
                val requestFactory = SimpleClientHttpRequestFactory()
                requestFactory.setConnectTimeout(connectTimeout)
                requestFactory.setReadTimeout(readTimeout)
                clientBuilder.baseUrl(baseUrl).requestFactory(requestFactory)
            }
        }
}
