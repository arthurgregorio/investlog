package br.com.investlog.server.config.http

import br.com.investlog.server.stockpricesync.http.BrapiClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices
import java.net.http.HttpClient
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "brapi", types = [BrapiClient::class])
class HttpServiceClientsConfig {

    @Bean
    fun brapiGroupConfigurer(
        @Value("\${investlog.brapi.base-url:https://brapi.dev/api}") brapiBaseUrl: String,
        @Value("\${investlog.brapi.token:}") brapiToken: String,
    ): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.filterByName("brapi").forEachClient { _, builder ->
                val requestFactory = JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()
                ).apply { setReadTimeout(REQUEST_TIMEOUT) }

                builder
                    .baseUrl(brapiBaseUrl)
                    .requestFactory(requestFactory)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $brapiToken")
            }
        }

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
