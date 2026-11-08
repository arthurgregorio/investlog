package br.com.investlog.server.notifications

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.notifications.repositories.NotificationDeliveryKind
import br.com.investlog.server.notifications.repositories.NotificationDeliveryRepository
import br.com.investlog.server.notifications.repositories.NotificationPreferences
import br.com.investlog.server.notifications.repositories.NotificationPreferencesRepository
import br.com.investlog.server.notifications.repositories.NotificationRecipientRepository
import br.com.investlog.server.notifications.rest.payloads.PeriodicReportFrequency
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.UserRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationRecipientAndDeliveryRepositoryTest : BaseIntegrationTest() {

    @Autowired
    lateinit var dsl: DSLContext

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var notificationPreferencesRepository: NotificationPreferencesRepository

    @Autowired
    lateinit var notificationRecipientRepository: NotificationRecipientRepository

    @Autowired
    lateinit var notificationDeliveryRepository: NotificationDeliveryRepository

    @Test
    @Order(1)
    fun `only approved users are recipients, each with their saved preferences or the defaults`() {

        val approved = createUser("aprovado@example.com", CurrentUser.Status.APPROVED)
        createUser("pendente@example.com", CurrentUser.Status.PENDING)
        val blocked = createUser("bloqueado@example.com", CurrentUser.Status.BLOCKED)

        val savedPreferences = NotificationPreferences(
            periodicReportFrequency = PeriodicReportFrequency.WEEKLY,
            monthEndReportEnabled = false,
            frozenAlertEnabled = true,
            frozenAlertMinimumGainPercentage = BigDecimal("5.25"),
        )
        notificationPreferencesRepository.save(approved, savedPreferences)
        notificationPreferencesRepository.save(blocked, savedPreferences)

        val recipients = notificationRecipientRepository.findAll().associateBy { recipient -> recipient.email }

        assertEquals(setOf(ADMIN_EMAIL, "aprovado@example.com"), recipients.keys)

        val administrator = recipients.getValue(ADMIN_EMAIL)
        assertEquals("Administrador", administrator.name)
        assertEquals("BRL", administrator.preferredCurrency)
        assertEquals(NotificationPreferences.DEFAULTS, administrator.preferences)

        val approvedRecipient = recipients.getValue("aprovado@example.com")
        assertEquals(approved, approvedRecipient.id)
        assertEquals("Aprovado", approvedRecipient.name)
        assertEquals(savedPreferences, approvedRecipient.preferences)
    }

    @Test
    @Order(2)
    fun `claim succeeds once per user, kind and period end`() {

        val userId = userRepository.findByEmail(ADMIN_EMAIL)!!.id
        val periodEnd = LocalDate.of(2026, 10, 31)

        assertTrue(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.MONTH_END_REPORT, periodEnd))
        assertFalse(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.MONTH_END_REPORT, periodEnd))
        assertFalse(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.MONTH_END_REPORT, periodEnd))

        assertTrue(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.BIWEEKLY_REPORT, periodEnd))
        assertTrue(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.MONTH_END_REPORT, periodEnd.minusMonths(1)))

        val otherUserId = userRepository.findByEmail("aprovado@example.com")!!.id
        assertTrue(notificationDeliveryRepository.claim(otherUserId, NotificationDeliveryKind.MONTH_END_REPORT, periodEnd))
    }

    @Test
    @Order(3)
    fun `a released claim can be claimed again`() {

        val userId = userRepository.findByEmail(ADMIN_EMAIL)!!.id
        val periodEnd = LocalDate.of(2026, 11, 6)

        assertTrue(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.DAILY_REPORT, periodEnd))
        assertTrue(notificationDeliveryRepository.release(userId, NotificationDeliveryKind.DAILY_REPORT, periodEnd))
        assertFalse(notificationDeliveryRepository.release(userId, NotificationDeliveryKind.DAILY_REPORT, periodEnd))
        assertTrue(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.DAILY_REPORT, periodEnd))
        assertFalse(notificationDeliveryRepository.claim(userId, NotificationDeliveryKind.DAILY_REPORT, periodEnd))
    }

    @Test
    @Order(4)
    fun `every delivery kind can be claimed`() {

        val userId = userRepository.findByEmail("aprovado@example.com")!!.id
        val periodEnd = LocalDate.of(2026, 11, 8)

        NotificationDeliveryKind.entries.forEach { kind ->
            assertTrue(notificationDeliveryRepository.claim(userId, kind, periodEnd))
        }
    }

    private fun createUser(email: String, status: CurrentUser.Status): Long {

        val userId = userRepository.createLocalUser(
            name = email.substringBefore("@").replaceFirstChar { character -> character.uppercase() },
            email = email,
            passwordHash = "hash",
        ).id

        dsl.update(USERS)
            .set(USERS.STATUS, status.name)
            .where(USERS.ID.eq(userId))
            .execute()

        return userId
    }

    companion object {
        private const val ADMIN_EMAIL = "admin@admin.com"
    }
}
