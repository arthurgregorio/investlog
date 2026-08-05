package br.com.investlog.server.config.core

import org.springframework.context.annotation.Configuration
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@EnableSpringDataWebSupport
@Configuration(proxyBeanMethods = false)
class WebMvcConfig : WebMvcConfigurer {

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(
            "/private/{version}",
            HandlerTypePredicate.forAnnotation(RestController::class.java),
        )
    }

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer.usePathSegment(1)
            .addSupportedVersions("v1")
            .setDefaultVersion("v1")
    }
}
