package br.com.investlog.server.mail

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.nio.charset.StandardCharsets

@Configuration
class MailTemplateConfiguration {

    @Bean
    fun mailTemplateEngine(): TemplateEngine {
        val templateResolver = ClassLoaderTemplateResolver().apply {
            prefix = "templates/mail/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
            characterEncoding = StandardCharsets.UTF_8.name()
        }

        return TemplateEngine().apply { setTemplateResolver(templateResolver) }
    }
}
