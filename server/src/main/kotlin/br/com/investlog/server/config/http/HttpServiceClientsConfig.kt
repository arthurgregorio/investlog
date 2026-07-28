package br.com.investlog.server.config.http

import br.com.investlog.server.cryptopricesync.http.CoinGeckoClient
import org.springframework.context.annotation.Configuration
import org.springframework.web.service.registry.ImportHttpServices

@ImportHttpServices(group = "coingecko", types = [CoinGeckoClient::class])
@Configuration(proxyBeanMethods = false)
class HttpServiceClientsConfig
