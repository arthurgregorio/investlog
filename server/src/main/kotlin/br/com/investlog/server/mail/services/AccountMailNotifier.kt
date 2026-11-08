package br.com.investlog.server.mail.services

import br.com.investlog.server.shared.security.AuthProvider
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Component
class AccountMailNotifier(
    private val userRepository: UserRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {

    fun notifyPendingApproval(user: CurrentUser) {
        val administrators = userRepository.findAdministratorsToNotify()

        if (administrators.isEmpty()) {
            logger.info { "No approved administrator to notify about a new user awaiting approval" }
            return
        }

        val createdAt = CREATED_AT_FORMATTER.format(clock.instant().atZone(SAO_PAULO))

        administrators.forEach { administrator ->
            publish(
                MailMessage(
                    to = listOf(administrator.email),
                    subject = "$PENDING_APPROVAL_SUBJECT: ${user.name}",
                    templateName = PENDING_APPROVAL_TEMPLATE,
                    variables = mapOf(
                        "recipientName" to administrator.name,
                        "userName" to user.name,
                        "userEmail" to user.email,
                        "signUpMethod" to signUpMethodOf(user.authProvider),
                        "createdAt" to createdAt,
                    ),
                )
            )
        }
    }

    fun notifyAccountApproved(name: String, email: String) {
        publish(
            MailMessage(
                to = listOf(email),
                subject = ACCOUNT_APPROVED_SUBJECT,
                templateName = ACCOUNT_APPROVED_TEMPLATE,
                variables = mapOf(
                    "userName" to name,
                    "userEmail" to email,
                ),
            )
        )
    }

    private fun publish(message: MailMessage) {
        applicationEventPublisher.publishEvent(MailRequested(message))
    }

    private fun signUpMethodOf(authProvider: AuthProvider): String =
        when (authProvider) {
            AuthProvider.LOCAL -> "E-mail e senha"
            AuthProvider.GOOGLE -> "Conta Google"
        }

    companion object {
        private const val PENDING_APPROVAL_SUBJECT = "Novo usuário aguardando aprovação"
        private const val PENDING_APPROVAL_TEMPLATE = "user-pending-approval"
        private const val ACCOUNT_APPROVED_SUBJECT = "Sua conta no InvestLog foi aprovada"
        private const val ACCOUNT_APPROVED_TEMPLATE = "account-approved"
        private val SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")
        private val CREATED_AT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
    }
}
