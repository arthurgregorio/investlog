package br.com.investlog.server.config.core

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.module.SimpleModule
import java.math.BigDecimal

@Configuration(proxyBeanMethods = false)
class JacksonConfiguration {

    @Bean
    fun boundedDecimalCustomizer(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.addModule(
            SimpleModule("BoundedDecimalModule")
                .addDeserializer(BigDecimal::class.java, BoundedBigDecimalDeserializer()),
        )
    }
}
