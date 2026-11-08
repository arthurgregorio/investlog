package br.com.investlog.server.notifications.services

import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.notifications.repositories.NotificationPreferences
import br.com.investlog.server.notifications.repositories.NotificationPreferencesRepository
import br.com.investlog.server.notifications.rest.payloads.NotificationPreferencesRequest
import br.com.investlog.server.notifications.rest.payloads.NotificationPreferencesResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationPreferencesService(
    private val notificationPreferencesRepository: NotificationPreferencesRepository,
    private val mailSettingsRepository: MailSettingsRepository,
    private val currentUserProvider: CurrentUserProvider,
) {

    fun find(): NotificationPreferencesResponse {

        val user = currentUserProvider.getCurrentUser()

        return notificationPreferencesRepository.find(user.id).toResponse()
    }

    @Transactional
    fun update(request: NotificationPreferencesRequest): NotificationPreferencesResponse {

        val user = currentUserProvider.getCurrentUser()

        val preferences = NotificationPreferences(
            periodicReportFrequency = request.periodicReportFrequency,
            monthEndReportEnabled = request.monthEndReportEnabled,
            frozenAlertEnabled = request.frozenAlertEnabled,
            frozenAlertMinimumGainPercentage = request.frozenAlertMinimumGainPercentage,
        )

        return notificationPreferencesRepository.save(user.id, preferences).toResponse()
    }

    private fun NotificationPreferences.toResponse() = NotificationPreferencesResponse(
        periodicReportFrequency = periodicReportFrequency,
        monthEndReportEnabled = monthEndReportEnabled,
        frozenAlertEnabled = frozenAlertEnabled,
        frozenAlertMinimumGainPercentage = frozenAlertMinimumGainPercentage,
        emailConfigured = mailSettingsRepository.isConfiguredAndEnabled(),
    )
}
