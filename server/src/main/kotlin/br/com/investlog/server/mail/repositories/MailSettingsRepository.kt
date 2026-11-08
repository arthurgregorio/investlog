package br.com.investlog.server.mail.repositories

import br.com.investlog.server.jooq.system.tables.records.MailSettingsRecord
import br.com.investlog.server.jooq.system.tables.references.MAIL_SETTINGS
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class MailSettingsRepository(private val dsl: DSLContext) {

    fun find(): MailSettings = fetchRecord().toMailSettings()

    fun isConfiguredAndEnabled(): Boolean = find().let { settings -> settings.enabled && settings.isConfigured() }

    fun updateServer(update: MailServerUpdate): MailSettings {
        val record = fetchRecord().apply {
            host = update.host
            port = update.port
            security = update.security.name
            username = update.username
            fromAddress = update.fromAddress
            fromName = update.fromName
            publicUrl = update.publicUrl
            updatedAt = OffsetDateTime.now()
        }

        update.passwordCiphertext?.let { passwordCiphertext -> record.passwordCiphertext = passwordCiphertext }
        record.store()

        return record.toMailSettings()
    }

    fun updateEnabled(enabled: Boolean): MailSettings {
        val record = fetchRecord().apply {
            this.enabled = enabled
            updatedAt = OffsetDateTime.now()
        }

        record.store()

        return record.toMailSettings()
    }

    private fun fetchRecord(): MailSettingsRecord = dsl.fetchSingle(MAIL_SETTINGS, MAIL_SETTINGS.ID.eq(SETTINGS_ID))

    private fun MailSettingsRecord.toMailSettings() = MailSettings(
        enabled = enabled!!,
        host = host,
        port = port,
        security = MailSecurity.valueOf(security!!),
        username = username,
        passwordCiphertext = passwordCiphertext,
        fromAddress = fromAddress,
        fromName = fromName,
        publicUrl = publicUrl,
        updatedAt = updatedAt!!,
    )

    companion object {
        private const val SETTINGS_ID: Short = 1
    }
}
