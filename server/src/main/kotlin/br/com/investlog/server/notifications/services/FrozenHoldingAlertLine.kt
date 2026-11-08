package br.com.investlog.server.notifications.services

data class FrozenHoldingAlertLine(
    val title: String,
    val subtitle: String,
    val currentValue: String,
    val gain: String,
    val gainPercentage: String,
    val newSinceLastAlert: Boolean,
)
