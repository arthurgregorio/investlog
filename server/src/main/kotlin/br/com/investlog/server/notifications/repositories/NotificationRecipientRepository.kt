package br.com.investlog.server.notifications.repositories

import br.com.investlog.server.jooq.system.tables.references.NOTIFICATION_PREFERENCES
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.shared.security.CurrentUser
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class NotificationRecipientRepository(private val dsl: DSLContext) {

    fun findAll(): List<NotificationRecipient> =
        dsl.select(listOf(USERS.ID, USERS.NAME, USERS.EMAIL, USERS.PREFERRED_CURRENCY) + NOTIFICATION_PREFERENCES.fields())
            .from(USERS)
            .leftJoin(NOTIFICATION_PREFERENCES).on(NOTIFICATION_PREFERENCES.USER_ID.eq(USERS.ID))
            .where(USERS.STATUS.eq(CurrentUser.Status.APPROVED.name))
            .orderBy(USERS.ID)
            .fetch { record ->
                NotificationRecipient(
                    id = record.get(USERS.ID)!!,
                    name = record.get(USERS.NAME)!!,
                    email = record.get(USERS.EMAIL)!!,
                    preferredCurrency = record.get(USERS.PREFERRED_CURRENCY)!!,
                    preferences = NotificationPreferences.from(record.into(NOTIFICATION_PREFERENCES)),
                )
            }
}
