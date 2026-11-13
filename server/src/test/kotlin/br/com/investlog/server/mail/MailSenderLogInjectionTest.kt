package br.com.investlog.server.mail

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.mail.services.MailMessage
import br.com.investlog.server.mail.services.MailSender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailSenderLogInjectionTest : BaseIntegrationTest() {

    @Autowired
    lateinit var mailSender: MailSender

    @Autowired
    lateinit var mailSettingsRepository: MailSettingsRepository

    private val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun captureLogs() {
        appender.start()
        rootLogger.addAppender(appender)
    }

    @AfterEach
    fun releaseLogs() {
        rootLogger.detachAppender(appender)
        appender.stop()
    }

    @Test
    @Order(1)
    fun `a stored name with line breaks cannot split the record logged while sending is disabled`() {
        mailSender.send(forgedMessage())

        val records = recordsMentioning("Email sending is disabled")

        assertEquals(1, records.size)
        assertFalse(records.single().formattedMessage.contains(LINE_BREAK))
        assertFalse(records.single().formattedMessage.contains(CARRIAGE_RETURN))
        assertTrue(records.single().formattedMessage.contains("Nome"))
        assertTrue(records.single().formattedMessage.contains("Forjado"))
    }

    @Test
    @Order(2)
    fun `a stored name or recipient with line breaks cannot split the record logged when delivery fails`() {
        mailSettingsRepository.updateServer(unreachableServer())
        mailSettingsRepository.updateEnabled(true)

        mailSender.send(forgedMessage())

        val records = recordsMentioning("Could not send")

        assertEquals(1, records.size)
        assertEquals(Level.WARN, records.single().level)
        assertFalse(records.single().formattedMessage.contains(LINE_BREAK))
        assertFalse(records.single().formattedMessage.contains(CARRIAGE_RETURN))
    }

    private fun forgedMessage() = MailMessage(
        to = listOf("forjado$LINE_BREAK${FORGED_RECORD}@example.com"),
        subject = "Novo usuário aguardando aprovação: Nome$LINE_BREAK${FORGED_RECORD}${CARRIAGE_RETURN}Forjado",
        templateName = "test",
    )

    private fun recordsMentioning(text: String): List<ILoggingEvent> =
        appender.list.filter { event -> event.loggerName.contains("MailSender") && event.formattedMessage.contains(text) }

    private fun unreachableServer() = MailServerUpdate(
        host = "localhost",
        port = ServerSocket(0).use { socket -> socket.localPort },
        security = MailSecurity.NONE,
        username = null,
        passwordCiphertext = null,
        fromAddress = "investlog@example.com",
        fromName = null,
        publicUrl = "https://investlog.example.com",
    )

    companion object {
        private const val LINE_BREAK = "\n"
        private const val CARRIAGE_RETURN = "\r"
        private const val FORGED_RECORD = "2026-01-01 00:00:00 ERROR forged record"
    }
}
