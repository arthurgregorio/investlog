package br.com.investlog.server.mail.services

import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettings
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSettingsRequest
import br.com.investlog.server.mail.rest.payloads.MailSettingsResponse
import br.com.investlog.server.shared.exceptions.InvalidMailSettingsException
import br.com.investlog.server.shared.security.CurrentUserProvider
import br.com.investlog.server.shared.security.DemoModeGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MailSettingsService(
    private val mailSettingsRepository: MailSettingsRepository,
    private val mailPasswordCipher: MailPasswordCipher,
    private val mailSender: MailSender,
    private val currentUserProvider: CurrentUserProvider,
    private val demoModeGuard: DemoModeGuard,
) {

    fun find(): MailSettingsResponse {
        currentUserProvider.getCurrentUser()
        return mailSettingsRepository.find().toResponse()
    }

    @Transactional
    fun update(request: MailSettingsRequest): MailSettingsResponse {
        currentUserProvider.getCurrentUser()
        demoModeGuard.assertNotDemoMode()

        val update = MailServerUpdate(
            host = request.host.trim(),
            port = request.port,
            security = request.security,
            username = request.username?.trim()?.ifEmpty { null },
            passwordCiphertext = request.password?.ifBlank { null }?.let(mailPasswordCipher::encrypt),
            fromAddress = request.fromAddress.trim(),
            fromName = request.fromName?.trim()?.ifEmpty { null },
            publicUrl = request.publicUrl.trim().trimEnd('/'),
        )

        return mailSettingsRepository.updateServer(update).toResponse()
    }

    @Transactional
    fun updateEnabled(enabled: Boolean): MailSettingsResponse {
        currentUserProvider.getCurrentUser()
        demoModeGuard.assertNotDemoMode()

        if (enabled && !mailSettingsRepository.find().isConfigured()) {
            throw InvalidMailSettingsException("Salve o servidor SMTP antes de ativar o envio")
        }

        return mailSettingsRepository.updateEnabled(enabled).toResponse()
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun sendTest(recipient: String) {
        currentUserProvider.getCurrentUser()
        mailSender.sendOrThrow(MailMessage(to = listOf(recipient), subject = TEST_SUBJECT, templateName = TEST_TEMPLATE))
    }

    private fun MailSettings.toResponse() = MailSettingsResponse(
        enabled = enabled,
        configured = isConfigured(),
        host = host,
        port = port,
        security = security,
        username = username,
        passwordSet = passwordCiphertext != null,
        fromAddress = fromAddress,
        fromName = fromName,
        publicUrl = publicUrl,
        updatedAt = updatedAt,
    )

    companion object {
        private const val TEST_SUBJECT = "Teste de envio do InvestLog"
        private const val TEST_TEMPLATE = "test"
    }
}
