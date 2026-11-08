package br.com.investlog.server.notifications

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.notifications.rest.payloads.NotificationPreferencesResponse
import br.com.investlog.server.notifications.rest.payloads.PeriodicReportFrequency
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPreferencesControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var mailSettingsRepository: MailSettingsRepository

    @Test
    @Order(1)
    fun `a user without saved preferences reads the defaults`() {

        val response = findPreferences()

        assertEquals(PeriodicReportFrequency.DISABLED, response.periodicReportFrequency)
        assertTrue(response.monthEndReportEnabled)
        assertTrue(response.frozenAlertEnabled)
        assertEquals("1.00", response.frozenAlertMinimumGainPercentage.toPlainString())
        assertFalse(response.emailConfigured)
    }

    @Test
    @Order(2)
    fun `saving the preferences returns and persists them`() {

        val saved = savePreferences(
            """{"periodicReportFrequency":"WEEKLY","monthEndReportEnabled":false,"frozenAlertEnabled":false,"frozenAlertMinimumGainPercentage":5}"""
        )

        assertEquals(PeriodicReportFrequency.WEEKLY, saved.periodicReportFrequency)
        assertFalse(saved.monthEndReportEnabled)
        assertFalse(saved.frozenAlertEnabled)
        assertEquals("5.00", saved.frozenAlertMinimumGainPercentage.toPlainString())
        assertEquals(saved, findPreferences())
    }

    @Test
    @Order(3)
    fun `saving again replaces the previous preferences`() {

        savePreferences(
            """{"periodicReportFrequency":"DAILY","monthEndReportEnabled":true,"frozenAlertEnabled":true,"frozenAlertMinimumGainPercentage":0}"""
        )

        val response = findPreferences()

        assertEquals(PeriodicReportFrequency.DAILY, response.periodicReportFrequency)
        assertTrue(response.monthEndReportEnabled)
        assertTrue(response.frozenAlertEnabled)
        assertEquals(0, response.frozenAlertMinimumGainPercentage.compareTo(BigDecimal.ZERO))
    }

    @Test
    @Order(4)
    fun `the minimum gain accepts both ends of the range with two decimals`() {

        val highest = savePreferences(
            """{"periodicReportFrequency":"BIWEEKLY","monthEndReportEnabled":true,"frozenAlertEnabled":true,"frozenAlertMinimumGainPercentage":100.00}"""
        )

        assertEquals("100.00", highest.frozenAlertMinimumGainPercentage.toPlainString())

        val withTwoDecimals = savePreferences(
            """{"periodicReportFrequency":"BIWEEKLY","monthEndReportEnabled":true,"frozenAlertEnabled":true,"frozenAlertMinimumGainPercentage":2.35}"""
        )

        assertEquals("2.35", withTwoDecimals.frozenAlertMinimumGainPercentage.toPlainString())
    }

    @Test
    @Order(5)
    fun `a minimum gain below zero is rejected`() {
        rejectMinimumGain("-0.01", "frozenAlertMinimumGainPercentage deve estar entre 0 e 100")
    }

    @Test
    @Order(6)
    fun `a minimum gain above one hundred is rejected`() {
        rejectMinimumGain("100.01", "frozenAlertMinimumGainPercentage deve estar entre 0 e 100")
    }

    @Test
    @Order(7)
    fun `a minimum gain with three decimals is rejected`() {
        rejectMinimumGain("1.234", "frozenAlertMinimumGainPercentage deve ter no máximo duas casas decimais")
    }

    @Test
    @Order(8)
    fun `an unknown frequency is rejected`() {
        restTestClient.put()
            .uri(PREFERENCES_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"periodicReportFrequency":"HOURLY","monthEndReportEnabled":true,"frozenAlertEnabled":true,"frozenAlertMinimumGainPercentage":1}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(9)
    fun `a rejected request leaves the saved preferences untouched`() {

        val response = findPreferences()

        assertEquals(PeriodicReportFrequency.BIWEEKLY, response.periodicReportFrequency)
        assertEquals("2.35", response.frozenAlertMinimumGainPercentage.toPlainString())
    }

    @Test
    @Order(10)
    fun `a non-admin user reads and saves their own preferences without touching anyone else's`() {

        val cookie = registerApproveAndLogin("notificacoes@example.com", "Senha123")

        restTestClient.get()
            .uri(PREFERENCES_URI)
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.periodicReportFrequency").isEqualTo("DISABLED")

        restTestClient.put()
            .uri(PREFERENCES_URI)
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"periodicReportFrequency":"DAILY","monthEndReportEnabled":false,"frozenAlertEnabled":true,"frozenAlertMinimumGainPercentage":3.5}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.periodicReportFrequency").isEqualTo("DAILY")
            .jsonPath("$.monthEndReportEnabled").isEqualTo(false)

        val adminPreferences = findPreferences()

        assertEquals(PeriodicReportFrequency.BIWEEKLY, adminPreferences.periodicReportFrequency)
        assertTrue(adminPreferences.monthEndReportEnabled)
        assertEquals("2.35", adminPreferences.frozenAlertMinimumGainPercentage.toPlainString())
    }

    @Test
    @Order(11)
    fun `emailConfigured follows the mail settings`() {

        mailSettingsRepository.updateServer(
            MailServerUpdate(
                host = "localhost",
                port = 2525,
                security = MailSecurity.NONE,
                username = null,
                passwordCiphertext = null,
                fromAddress = "investlog@example.com",
                fromName = null,
                publicUrl = "https://investlog.example.com",
            )
        )

        assertFalse(findPreferences().emailConfigured)

        mailSettingsRepository.updateEnabled(true)

        assertTrue(findPreferences().emailConfigured)

        mailSettingsRepository.updateEnabled(false)

        assertFalse(findPreferences().emailConfigured)
    }

    private fun findPreferences(): NotificationPreferencesResponse =
        restTestClient.get()
            .uri(PREFERENCES_URI)
            .exchange()
            .expectStatus().isOk()
            .returnResult<NotificationPreferencesResponse>()
            .responseBody!!

    private fun savePreferences(body: String): NotificationPreferencesResponse =
        restTestClient.put()
            .uri(PREFERENCES_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk()
            .returnResult<NotificationPreferencesResponse>()
            .responseBody!!

    private fun rejectMinimumGain(minimumGain: String, message: String) {
        val response = restTestClient.put()
            .uri(PREFERENCES_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"periodicReportFrequency":"DAILY","monthEndReportEnabled":true,"frozenAlertEnabled":true,"frozenAlertMinimumGainPercentage":$minimumGain}""")
            .exchange()
            .expectStatus().isBadRequest()
            .returnResult<Map<String, Any?>>()
            .responseBody

        @Suppress("UNCHECKED_CAST")
        val errors = response?.get("errors") as List<String>
        assertTrue(errors.any { error -> error.contains(message) })
    }

    private fun registerApproveAndLogin(email: String, password: String): String {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isCreated()

        val targetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { user -> user as Map<*, *> }
            .single { user -> user["email"] == email }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        return restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")
    }

    companion object {
        private const val PREFERENCES_URI = "/private/v1/profile/notifications"
    }
}
