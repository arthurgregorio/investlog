package br.com.investlog.server.mail

import org.thymeleaf.context.Context
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailTemplateConfigurationTest {

    private val mailTemplateEngine = MailTemplateConfiguration().mailTemplateEngine()

    @Test
    fun `a template passes an empty footer to the layout with an empty fragment`() {
        val context = Context(Locale.forLanguageTag("pt-BR")).apply {
            setVariable("subject", "Sem rodapé")
            setVariable("publicUrl", "https://investlog.example.com")
            setVariable("greeting", "Olá, Maria")
        }

        val html = mailTemplateEngine.process("layout-without-footer", context)

        assertTrue(html.contains("<title>Sem rodapé</title>"))
        assertTrue(html.contains("<p>Olá, Maria</p>"))
        assertTrue(html.contains("InvestLog · <a href=\"https://investlog.example.com\""))
        assertFalse(html.contains("th:replace"))
        assertFalse(html.contains("th:text"))
    }
}
