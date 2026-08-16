package br.com.investlog.server.config.core

import br.com.investlog.server.config.InvestlogConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(InvestlogConfigurations::class)
class CommonsConfiguration
