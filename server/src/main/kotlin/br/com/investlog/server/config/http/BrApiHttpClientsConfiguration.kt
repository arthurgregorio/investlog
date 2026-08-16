package br.com.investlog.server.config.http

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.config.http.BrApiHttpClientsConfiguration.Companion.CLIENT_GROUP_NAME
import br.com.investlog.server.config.http.BrApiHttpClientsConfiguration.Companion.PACKAGE_TO_SEARCH
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer
import org.springframework.web.service.registry.ImportHttpServices

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = CLIENT_GROUP_NAME, basePackages = [PACKAGE_TO_SEARCH])
class BrApiHttpClientsConfiguration(
    private val investlogConfigurations: InvestlogConfigurations
) {

    @Bean
    fun brapiGroupConfigurer(): RestClientHttpServiceGroupConfigurer =
        RestClientHttpServiceGroupConfigurer { groups ->

            val brapiConfigs = investlogConfigurations.brApi

            groups.filterByName(CLIENT_GROUP_NAME)
                .forEachClient { _, builder ->
                    builder
                        .baseUrl(brapiConfigs.baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${brapiConfigs.token}")
                }
        }

    companion object {
        private const val CLIENT_GROUP_NAME = "brapi"
        private const val PACKAGE_TO_SEARCH = "br.com.investlog.server.shared.http.brapi"
    }
}
