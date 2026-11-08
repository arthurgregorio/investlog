package br.com.investlog.server.mail.services

data class MailMessage(
    val to: List<String>,
    val subject: String,
    val templateName: String,
    val variables: Map<String, Any?> = emptyMap(),
)
