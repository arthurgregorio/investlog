package br.com.investlog.server.notifications.services

import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.services.MailSender
import br.com.investlog.server.notifications.repositories.FrozenHolding
import br.com.investlog.server.notifications.repositories.FrozenHoldingAlert
import br.com.investlog.server.notifications.repositories.FrozenHoldingAlertRepository
import br.com.investlog.server.notifications.repositories.NotificationDeliveryKind
import br.com.investlog.server.notifications.repositories.NotificationDeliveryRepository
import br.com.investlog.server.notifications.repositories.NotificationRecipient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class FrozenHoldingAlertService(
    private val frozenHoldingAlertRepository: FrozenHoldingAlertRepository,
    private val notificationDeliveryRepository: NotificationDeliveryRepository,
    private val mailSettingsRepository: MailSettingsRepository,
    private val mailSender: MailSender,
) {

    @Transactional
    fun check(recipient: NotificationRecipient, now: ZonedDateTime) {
        val checkedAt = now.toOffsetDateTime()
        val greenHoldings = refresh(recipient, now)
        val newHoldings = greenHoldings.filter { holding -> holding.alert!!.isNew }

        val shouldSend = isWithinSendingWindow(now.toLocalTime()) &&
            newHoldings.isNotEmpty() &&
            mailSettingsRepository.isConfiguredAndEnabled() &&
            notificationDeliveryRepository.claim(recipient.id, NotificationDeliveryKind.FROZEN_ALERT, now.toLocalDate())

        if (!shouldSend) {
            return
        }

        mailSender.send(FrozenHoldingAlertMail.messageFor(recipient, greenHoldings))

        newHoldings.forEach { holding ->
            frozenHoldingAlertRepository.save(recipient.id, holding, holding.alert!!.alerted(checkedAt))
        }
    }

    private fun refresh(recipient: NotificationRecipient, now: ZonedDateTime): List<FrozenHolding> {
        val minimumGainPercentage = recipient.preferences.frozenAlertMinimumGainPercentage
        val holdings = frozenHoldingAlertRepository.findFrozenHoldings(recipient.id)

        frozenHoldingAlertRepository.deleteAllExcept(recipient.id, holdings)

        return holdings
            .map { holding ->
                val alert = FrozenHoldingAlert.after(holding.alert, holding.isAboveMinimumGain(minimumGainPercentage), now.toOffsetDateTime())

                if (alert != holding.alert) {
                    frozenHoldingAlertRepository.save(recipient.id, holding, alert)
                }

                holding.copy(alert = alert)
            }
            .filter { holding -> holding.alert!!.green }
    }

    companion object {
        private val SENDING_WINDOW = LocalTime.of(8, 0)..LocalTime.of(22, 0)

        fun isWithinSendingWindow(time: LocalTime): Boolean = time.truncatedTo(ChronoUnit.MINUTES) in SENDING_WINDOW
    }
}
