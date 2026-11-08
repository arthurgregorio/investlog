package br.com.investlog.server.mail

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.mail.services.MailMessage
import br.com.investlog.server.mail.services.MailSender
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test
import kotlin.test.assertTrue

@TestPropertySource(properties = ["investlog.demo-mode.enabled=true"])
class MailDemoModeTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var mailSender: MailSender

    @Autowired
    lateinit var mailSettingsRepository: MailSettingsRepository

    @Test
    @Order(1)
    fun `the settings stay readable in demo mode`() {
        restTestClient.get()
            .uri(SETTINGS_URI)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
    }

    @Test
    @Order(2)
    fun `saving the server is refused in demo mode`() {
        restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"host":"localhost","port":${greenMail.smtp.port},"security":"NONE","fromAddress":"investlog@example.com","publicUrl":"https://investlog.example.com"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Indisponível no modo demonstração.")
            .jsonPath("$.error").isEqualTo("demo_mode")
    }

    @Test
    @Order(3)
    fun `toggling sending is refused in demo mode`() {
        restTestClient.patch()
            .uri("$SETTINGS_URI/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"enabled":false}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(4)
    fun `the test send is refused in demo mode`() {
        restTestClient.post()
            .uri("$SETTINGS_URI/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"recipient":"destino@example.com"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    @Test
    @Order(5)
    fun `the mail sender sends nothing in demo mode, even with complete and enabled settings`() {
        mailSettingsRepository.updateServer(
            MailServerUpdate(
                host = "localhost",
                port = greenMail.smtp.port,
                security = MailSecurity.NONE,
                username = null,
                passwordCiphertext = null,
                fromAddress = "investlog@example.com",
                fromName = null,
                publicUrl = "https://investlog.example.com",
            )
        )
        mailSettingsRepository.updateEnabled(true)

        mailSender.send(MailMessage(to = listOf("destino@example.com"), subject = "Modo demonstração", templateName = "test"))

        assertTrue(mailSettingsRepository.isConfiguredAndEnabled())
        assertTrue(greenMail.receivedMessages.isEmpty())
    }

    companion object {
        private const val SETTINGS_URI = "/private/v1/mail-settings"

        private val greenMail = GreenMail(ServerSetupTest.SMTP.dynamicPort()).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopGreenMail() {
            greenMail.stop()
        }
    }
}
