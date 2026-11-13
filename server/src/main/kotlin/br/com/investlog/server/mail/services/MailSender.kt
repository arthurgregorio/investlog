package br.com.investlog.server.mail.services

import br.com.investlog.server.mail.repositories.MailSettings
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.shared.exceptions.InvalidMailSettingsException
import br.com.investlog.server.shared.exceptions.MailDeliveryException
import br.com.investlog.server.shared.security.DemoModeGuard
import br.com.investlog.server.shared.utils.sanitizedForLog
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.MessagingException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.util.Locale
import java.util.Properties

private val logger = KotlinLogging.logger {}

@Component
class MailSender(
    private val mailSettingsRepository: MailSettingsRepository,
    private val mailPasswordCipher: MailPasswordCipher,
    private val demoModeGuard: DemoModeGuard,
    @Qualifier("mailTemplateEngine")
    private val mailTemplateEngine: TemplateEngine,
) {

    fun send(message: MailMessage) {
        try {
            val settings = mailSettingsRepository.find()

            when {
                demoModeGuard.isDemoMode() -> logger.info { "Demo mode is on, not sending \"${message.subject.sanitizedForLog()}\"" }
                !settings.enabled -> logger.info { "Email sending is disabled, not sending \"${message.subject.sanitizedForLog()}\"" }
                !settings.isConfigured() -> logger.info { "SMTP settings are incomplete, not sending \"${message.subject.sanitizedForLog()}\"" }
                else -> deliver(settings, message)
            }
        } catch (exception: Exception) {
            logger.warn(exception) { "Could not send \"${message.subject.sanitizedForLog()}\" to ${message.to.map { it.sanitizedForLog() }}" }
        }
    }

    fun sendOrThrow(message: MailMessage) {
        demoModeGuard.assertNotDemoMode()

        val settings = mailSettingsRepository.find()

        if (!settings.isConfigured()) {
            throw InvalidMailSettingsException("Salve o servidor SMTP antes de enviar um teste")
        }

        deliver(settings, message)
    }

    private fun deliver(settings: MailSettings, message: MailMessage) {
        try {
            val javaMailSender = javaMailSenderFor(settings, passwordOf(settings))
            val html = render(settings, message)

            val mimeMessage = javaMailSender.createMimeMessage()

            MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, ENCODING).apply {
                setFrom(settings.fromAddress!!, settings.fromName ?: DEFAULT_FROM_NAME)
                setTo(message.to.toTypedArray())
                setSubject(message.subject)
                setText(MailPlainText.from(html), html)
            }

            javaMailSender.send(mimeMessage)
        } catch (exception: Exception) {
            throw MailDeliveryException(describe(exception), exception)
        }
    }

    private fun passwordOf(settings: MailSettings): String? =
        settings.passwordCiphertext
            ?.takeIf { settings.username != null && mailPasswordCipher.isAvailable() }
            ?.let(mailPasswordCipher::decrypt)

    private fun render(settings: MailSettings, message: MailMessage): String {
        val context = Context(PORTUGUESE_BRAZIL).apply {
            setVariables(message.variables)
            setVariable("subject", message.subject)
            setVariable("publicUrl", settings.publicUrl)
        }

        return mailTemplateEngine.process(message.templateName, context)
    }

    companion object {
        private const val ENCODING = "UTF-8"
        private const val DEFAULT_FROM_NAME = "InvestLog"
        private const val TIMEOUT_MILLISECONDS = "15000"
        private val PORTUGUESE_BRAZIL: Locale = Locale.forLanguageTag("pt-BR")

        fun javaMailSenderFor(settings: MailSettings, password: String?): JavaMailSenderImpl =
            JavaMailSenderImpl().apply {
                host = settings.host
                port = settings.port!!
                username = settings.username
                this.password = password
                defaultEncoding = ENCODING
                javaMailProperties = smtpPropertiesFor(settings)
            }

        fun smtpPropertiesFor(settings: MailSettings): Properties = Properties().apply {
            setProperty("mail.smtp.auth", (settings.username != null).toString())
            setProperty("mail.smtp.connectiontimeout", TIMEOUT_MILLISECONDS)
            setProperty("mail.smtp.timeout", TIMEOUT_MILLISECONDS)
            setProperty("mail.smtp.writetimeout", TIMEOUT_MILLISECONDS)
            setProperty("mail.smtp.starttls.enable", (settings.security == MailSecurity.STARTTLS).toString())
            setProperty("mail.smtp.starttls.required", (settings.security == MailSecurity.STARTTLS).toString())
            setProperty("mail.smtp.ssl.enable", (settings.security == MailSecurity.SSL_TLS).toString())
        }

        fun describe(exception: Throwable): String {
            val causes = generateSequence(exception) { it.cause }.toList()
            val messagingException = causes.firstOrNull { it is MessagingException && !it.message.isNullOrBlank() }

            return messagingException?.message
                ?: causes.lastOrNull { !it.message.isNullOrBlank() }?.message
                ?: exception.javaClass.simpleName
        }
    }
}
