package br.com.investlog.server.mail.services

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MailRequestedListener(private val mailSender: MailSender) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onMailRequested(event: MailRequested) {
        mailSender.send(event.message)
    }
}
