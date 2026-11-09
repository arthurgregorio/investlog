package br.com.investlog.server.mail.services

import br.com.investlog.server.mail.repositories.MailSettings
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import jakarta.mail.AuthenticationFailedException
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MailSenderSmtpPropertiesTest {

    @Test
    fun `a server without security sends in plain text and without auth when there is no username`() {
        val properties = MailSender.smtpPropertiesFor(settings(security = MailSecurity.NONE, username = null))

        assertEquals("false", properties.getProperty("mail.smtp.auth"))
        assertEquals("false", properties.getProperty("mail.smtp.starttls.enable"))
        assertEquals("false", properties.getProperty("mail.smtp.starttls.required"))
        assertEquals("false", properties.getProperty("mail.smtp.ssl.enable"))
    }

    @Test
    fun `STARTTLS is enabled and required, with auth when there is a username`() {
        val properties = MailSender.smtpPropertiesFor(settings(security = MailSecurity.STARTTLS, username = "smtp-user"))

        assertEquals("true", properties.getProperty("mail.smtp.auth"))
        assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"))
        assertEquals("true", properties.getProperty("mail.smtp.starttls.required"))
        assertEquals("false", properties.getProperty("mail.smtp.ssl.enable"))
    }

    @Test
    fun `SSL_TLS connects over SSL from the start`() {
        val properties = MailSender.smtpPropertiesFor(settings(security = MailSecurity.SSL_TLS, username = "smtp-user"))

        assertEquals("true", properties.getProperty("mail.smtp.ssl.enable"))
        assertEquals("false", properties.getProperty("mail.smtp.starttls.enable"))
    }

    @Test
    fun `every connection has finite timeouts`() {
        val properties = MailSender.smtpPropertiesFor(settings())

        listOf("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout").forEach { name ->
            assertEquals("15000", properties.getProperty(name))
        }
    }

    @Test
    fun `the sender is built from the saved settings`() {
        val javaMailSender = MailSender.javaMailSenderFor(settings(username = "smtp-user"), "segredo")

        assertEquals("smtp.example.com", javaMailSender.host)
        assertEquals(587, javaMailSender.port)
        assertEquals("smtp-user", javaMailSender.username)
        assertEquals("segredo", javaMailSender.password)
        assertEquals("UTF-8", javaMailSender.defaultEncoding)
        assertEquals("true", javaMailSender.javaMailProperties.getProperty("mail.smtp.auth"))
    }

    @Test
    fun `a sender without a username carries no credentials`() {
        val javaMailSender = MailSender.javaMailSenderFor(settings(username = null), null)

        assertNull(javaMailSender.username)
        assertNull(javaMailSender.password)
    }

    @Test
    fun `describes a failure by the SMTP server's own message`() {
        val authenticationFailure = MailAuthenticationException(AuthenticationFailedException("535 5.7.8 Authentication failed"))

        assertEquals("535 5.7.8 Authentication failed", MailSender.describe(authenticationFailure))
    }

    @Test
    fun `falls back to the deepest message, then to the exception name`() {
        val sendFailure = MailSendException("Falha ao enviar", IllegalStateException("Conexão recusada"))

        assertEquals("Conexão recusada", MailSender.describe(sendFailure))
        assertEquals("Falha ao enviar", MailSender.describe(MailSendException("Falha ao enviar")))
        assertEquals("IllegalStateException", MailSender.describe(IllegalStateException()))
    }

    @Test
    fun `settings are configured only with host, port, sender address and public URL`() {
        assertTrue(settings().isConfigured())
        assertFalse(settings().copy(host = null).isConfigured())
        assertFalse(settings().copy(port = null).isConfigured())
        assertFalse(settings().copy(fromAddress = null).isConfigured())
        assertFalse(settings().copy(publicUrl = null).isConfigured())
    }

    private fun settings(security: MailSecurity = MailSecurity.STARTTLS, username: String? = null) = MailSettings(
        enabled = true,
        host = "smtp.example.com",
        port = 587,
        security = security,
        username = username,
        passwordCiphertext = null,
        fromAddress = "investlog@example.com",
        fromName = "InvestLog",
        publicUrl = "https://investlog.example.com",
        updatedAt = OffsetDateTime.now(),
    )
}
