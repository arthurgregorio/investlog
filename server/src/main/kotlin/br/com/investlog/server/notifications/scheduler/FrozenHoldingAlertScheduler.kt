package br.com.investlog.server.notifications.scheduler

import br.com.investlog.server.notifications.repositories.NotificationRecipientRepository
import br.com.investlog.server.notifications.services.FrozenHoldingAlertService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Component
class FrozenHoldingAlertScheduler(
    private val notificationRecipientRepository: NotificationRecipientRepository,
    private val frozenHoldingAlertService: FrozenHoldingAlertService,
    private val clock: Clock,
) {

    @Scheduled(cron = "0 0 */2 * * *", zone = SCHEDULING_ZONE)
    fun checkFrozenHoldings() {
        val now = clock.instant().atZone(ZoneId.of(SCHEDULING_ZONE))

        notificationRecipientRepository.findAll()
            .filter { recipient -> recipient.preferences.frozenAlertEnabled }
            .forEach { recipient ->
                runCatching { frozenHoldingAlertService.check(recipient, now) }
                    .onFailure { exception -> logger.error(exception) { "Frozen-holding alert failed for user ${recipient.id}" } }
            }
    }

    companion object {
        private const val SCHEDULING_ZONE = "America/Sao_Paulo"
    }
}
