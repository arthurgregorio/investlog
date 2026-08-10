package br.com.investlog.server.config.http

import br.com.investlog.server.config.http.AwesomeApiHttpClientsConfig.Companion.CLIENT_GROUP_NAME
import br.com.investlog.server.config.http.AwesomeApiHttpClientsConfig.Companion.PACKAGE_TO_SEARCH
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CLIENT_GROUP_NAME, basePackages = [PACKAGE_TO_SEARCH])
class AwesomeApiHttpClientsConfig(
    @Value($$"${investlog.awesomeapi.base-url}")
    private val awesomeApiBaseUrl: String,
) {

    @Bean
    fun awesomeApiGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->
            groups.filterByName(CLIENT_GROUP_NAME)
                .forEachClient { _, builder ->
                    builder.baseUrl(awesomeApiBaseUrl)
                }
        }

    companion object {
        private const val CLIENT_GROUP_NAME = "awesomeapi"
        private const val PACKAGE_TO_SEARCH = "br.com.investlog.server.shared.http.awesomeapi"
    }
}
