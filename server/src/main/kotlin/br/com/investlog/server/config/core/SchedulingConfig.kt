package br.com.investlog.server.config.core

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@Profile("!test")
@Configuration(proxyBeanMethods = false)
class SchedulingConfig
