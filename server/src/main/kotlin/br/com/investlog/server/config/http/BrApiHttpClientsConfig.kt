package br.com.investlog.server.config.http

import br.com.investlog.server.config.http.BrApiHttpClientsConfig.Companion.CLIENT_GROUP_NAME
import br.com.investlog.server.config.http.BrApiHttpClientsConfig.Companion.PACKAGE_TO_SEARCH
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
@ImportHttpServices(group = CLIENT_GROUP_NAME, basePackages = [PACKAGE_TO_SEARCH])
class BrApiHttpClientsConfig(
    @Value($$"${investlog.brapi.base-url:https://brapi.dev/api}")
    private val brapiBaseUrl: String,
    @Value($$"${investlog.brapi.token:}")
    private val brapiToken: String
) {

    @Bean
    fun brapiGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.filterByName(CLIENT_GROUP_NAME)
                .forEachClient { _, builder ->
                    builder
                        .baseUrl(brapiBaseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $brapiToken")
                }
        }

    companion object {
        private const val CLIENT_GROUP_NAME = "brapi"
        private const val PACKAGE_TO_SEARCH = "br.com.investlog.server.stockpricesync.http"
    }
}
