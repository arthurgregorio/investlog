package br.com.investlog.server.notifications.repositories

data class NotificationRecipient(
    val id: Long,
    val name: String,
    val email: String,
    val preferredCurrency: String,
    val preferences: NotificationPreferences,
)
