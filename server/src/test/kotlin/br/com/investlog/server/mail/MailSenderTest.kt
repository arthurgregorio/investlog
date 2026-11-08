package br.com.investlog.server.mail

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.mail.services.MailMessage
import br.com.investlog.server.mail.services.MailRequested
import br.com.investlog.server.mail.services.MailSender
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.transaction.support.TransactionTemplate
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailSenderTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mailSender: MailSender

    @Autowired
    lateinit var mailSettingsRepository: MailSettingsRepository

    @Autowired
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var restTestClient: RestTestClient

    @BeforeEach
    fun purgeMailboxes() {
        greenMail.purgeEmailFromAllMailboxes()
    }

    @Test
    @Order(1)
    fun `sends nothing while the settings are incomplete, even when enabled`() {
        mailSettingsRepository.updateEnabled(true)

        mailSender.send(testMessage())

        assertFalse(mailSettingsRepository.isConfiguredAndEnabled())
        assertTrue(greenMail.receivedMessages.isEmpty())
    }

    @Test
    @Order(2)
    fun `sends nothing while sending is disabled`() {
        mailSettingsRepository.updateServer(serverUpdate())
        mailSettingsRepository.updateEnabled(false)

        mailSender.send(testMessage())

        assertFalse(mailSettingsRepository.isConfiguredAndEnabled())
        assertTrue(greenMail.receivedMessages.isEmpty())
    }

    @Test
    @Order(3)
    fun `sends HTML with a plain-text alternative rendered from the shared layout`() {
        mailSettingsRepository.updateEnabled(true)

        mailSender.send(testMessage(to = listOf("primeiro@example.com", "segundo@example.com")))

        assertTrue(mailSettingsRepository.isConfiguredAndEnabled())
        assertEquals(2, greenMail.receivedMessages.size)

        val message = greenMail.receivedMessages.first()
        val (plainText, html) = partsOf(message)

        assertEquals("Teste do remetente", message.subject)
        assertEquals(listOf("primeiro@example.com", "segundo@example.com"), message.allRecipients.map { it.toString() })
        assertTrue(message.from.single().toString().contains("InvestLog"))
        assertTrue(html.contains("<title>Teste do remetente</title>"))
        assertTrue(html.contains("href=\"https://investlog.example.com\""))
        assertTrue(html.contains("#087f5b"))
        assertTrue(plainText.contains("O envio de e-mails está funcionando"))
        assertTrue(plainText.contains("Abrir o InvestLog: https://investlog.example.com"))
        assertTrue(plainText.contains("Você recebeu este e-mail porque um administrador do InvestLog enviou um teste para este endereço."))
        assertFalse(plainText.contains("<"))
    }

    @Test
    @Order(4)
    fun `never decrypts a stored password while no encryption key is configured`() {
        mailSettingsRepository.updateServer(serverUpdate(username = "smtp-user", passwordCiphertext = "sem-chave-para-decifrar"))

        mailSender.send(testMessage())

        assertTrue(greenMail.receivedMessages.isEmpty())

        mailSettingsRepository.updateServer(serverUpdate())

        mailSender.send(testMessage())

        assertEquals(1, greenMail.receivedMessages.size)
    }

    @Test
    @Order(5)
    fun `an SMTP failure is logged and never reaches the caller`() {
        val closedPort = ServerSocket(0).use { socket -> socket.localPort }
        mailSettingsRepository.updateServer(serverUpdate(port = closedPort))

        mailSender.send(testMessage())

        assertTrue(greenMail.receivedMessages.isEmpty())

        mailSettingsRepository.updateServer(serverUpdate())
    }

    @Test
    @Order(6)
    fun `a MailRequested event is sent asynchronously after the transaction commits`() {
        transactionTemplate.executeWithoutResult {
            applicationEventPublisher.publishEvent(MailRequested(testMessage()))
            assertTrue(greenMail.receivedMessages.isEmpty())
        }

        assertTrue(greenMail.waitForIncomingEmail(WAIT_MILLISECONDS, 1))
        assertEquals("Teste do remetente", greenMail.receivedMessages.single().subject)
    }

    @Test
    @Order(7)
    fun `a MailRequested event from a rolled-back transaction is never sent`() {
        transactionTemplate.executeWithoutResult { status ->
            applicationEventPublisher.publishEvent(MailRequested(testMessage()))
            status.setRollbackOnly()
        }

        applicationEventPublisher.publishEvent(MailRequested(testMessage(to = listOf("fora-de-transacao@example.com"))))

        assertTrue(greenMail.waitForIncomingEmail(WAIT_MILLISECONDS, 1))
        assertEquals(listOf("fora-de-transacao@example.com"), greenMail.receivedMessages.map { it.allRecipients.single().toString() })
    }

    @Test
    @Order(8)
    fun `refuses to save a password without an encryption key`() {
        restTestClient.put()
            .uri("/private/v1/mail-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"host":"localhost","port":${greenMail.smtp.port},"security":"NONE","username":"smtp-user","password":"segredo","fromAddress":"investlog@example.com","publicUrl":"https://investlog.example.com"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Defina MAIL_ENCRYPTION_KEY para salvar a senha do SMTP")

        restTestClient.put()
            .uri("/private/v1/mail-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"host":"localhost","port":${greenMail.smtp.port},"security":"NONE","username":null,"fromAddress":"investlog@example.com","fromName":"","publicUrl":"https://investlog.example.com"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.username").doesNotExist()
            .jsonPath("$.fromName").doesNotExist()
    }

    private fun partsOf(message: MimeMessage): Pair<String, String> {
        val bodyParts = generateSequence(listOf(message.content)) { contents ->
            contents.filterIsInstance<MimeMultipart>()
                .flatMap { multipart -> (0 until multipart.count).map { index -> multipart.getBodyPart(index) } }
                .map { bodyPart -> bodyPart.content }
                .ifEmpty { null }
        }.flatten().filterIsInstance<String>().toList()

        return bodyParts.first { !it.contains("<html") } to bodyParts.first { it.contains("<html") }
    }

    private fun testMessage(to: List<String> = listOf("primeiro@example.com")) =
        MailMessage(to = to, subject = "Teste do remetente", templateName = "test")

    private fun serverUpdate(
        port: Int = greenMail.smtp.port,
        username: String? = null,
        passwordCiphertext: String? = null,
    ) = MailServerUpdate(
        host = "localhost",
        port = port,
        security = MailSecurity.NONE,
        username = username,
        passwordCiphertext = passwordCiphertext,
        fromAddress = "investlog@example.com",
        fromName = null,
        publicUrl = "https://investlog.example.com",
    )

    companion object {
        private const val WAIT_MILLISECONDS = 10_000L

        private val greenMail = GreenMail(ServerSetupTest.SMTP.dynamicPort()).apply {
            setUser("smtp-user", "a-senha-real")
            start()
        }

        @JvmStatic
        @AfterAll
        fun stopGreenMail() {
            greenMail.stop()
        }
    }
}
