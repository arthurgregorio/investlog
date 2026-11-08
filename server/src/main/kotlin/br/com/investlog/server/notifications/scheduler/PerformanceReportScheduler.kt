package br.com.investlog.server.notifications.scheduler

import br.com.investlog.server.mail.services.MailMessage
import br.com.investlog.server.mail.services.MailSender
import br.com.investlog.server.notifications.repositories.NotificationDeliveryRepository
import br.com.investlog.server.notifications.repositories.NotificationRecipient
import br.com.investlog.server.notifications.repositories.NotificationRecipientRepository
import br.com.investlog.server.notifications.services.PerformanceReportService
import br.com.investlog.server.notifications.services.PerformanceReportView
import br.com.investlog.server.notifications.services.ReportPeriod
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Component
class PerformanceReportScheduler(
    private val notificationRecipientRepository: NotificationRecipientRepository,
    private val notificationDeliveryRepository: NotificationDeliveryRepository,
    private val performanceReportService: PerformanceReportService,
    private val mailSender: MailSender,
    private val clock: Clock,
) {

    @Scheduled(cron = "0 0 7 * * *", zone = SCHEDULING_ZONE)
    fun sendReports() {
        val periods = ReportPeriod.dueOn(LocalDate.now(clock.withZone(ZoneId.of(SCHEDULING_ZONE))))
        val recipients = notificationRecipientRepository.findAll()

        for (period in periods) {
            recipients
                .filter { recipient -> period.kind.isWantedBy(recipient.preferences) }
                .forEach { recipient -> sendReport(recipient, period) }
        }
    }

    private fun sendReport(recipient: NotificationRecipient, period: ReportPeriod) {
        val deliveryKind = period.kind.deliveryKind

        if (!notificationDeliveryRepository.claim(recipient.id, deliveryKind, period.endDate)) {
            return
        }

        try {
            val report = performanceReportService.build(recipient, period)

            if (report == null) {
                notificationDeliveryRepository.release(recipient.id, deliveryKind, period.endDate)
                logger.info { "Skipped $deliveryKind for user ${recipient.id}: no wallet snapshot on ${period.endDate}" }
                return
            }

            val view = PerformanceReportView.of(report)

            mailSender.send(
                MailMessage(
                    to = listOf(recipient.email),
                    subject = view.subject,
                    templateName = TEMPLATE_NAME,
                    variables = mapOf("report" to view),
                )
            )
        } catch (exception: Exception) {
            notificationDeliveryRepository.release(recipient.id, deliveryKind, period.endDate)
            logger.error(exception) { "Could not send $deliveryKind for user ${recipient.id} and period ending ${period.endDate}" }
        }
    }

    companion object {
        private const val SCHEDULING_ZONE = "America/Sao_Paulo"
        private const val TEMPLATE_NAME = "performance-report"
    }
}
