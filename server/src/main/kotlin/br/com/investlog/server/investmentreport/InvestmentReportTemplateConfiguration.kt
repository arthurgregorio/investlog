package br.com.investlog.server.investmentreport

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.nio.charset.StandardCharsets

@Configuration
class InvestmentReportTemplateConfiguration {

    @Bean
    fun reportTemplateEngine(): TemplateEngine {
        val templateResolver = ClassLoaderTemplateResolver().apply {
            prefix = "templates/reports/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
            characterEncoding = StandardCharsets.UTF_8.name()
        }

        return TemplateEngine().apply { setTemplateResolver(templateResolver) }
    }
}
