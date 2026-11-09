package br.com.investlog.server.notifications

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.FROZEN_HOLDING_ALERTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.notifications.repositories.NotificationDeliveryKind
import br.com.investlog.server.notifications.repositories.NotificationDeliveryRepository
import br.com.investlog.server.notifications.repositories.NotificationPreferences
import br.com.investlog.server.notifications.repositories.NotificationPreferencesRepository
import br.com.investlog.server.notifications.scheduler.FrozenHoldingAlertScheduler
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.MutableClock
import br.com.investlog.server.shared.security.UserRepository
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FrozenHoldingAlertSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    @Autowired
    lateinit var frozenHoldingAlertScheduler: FrozenHoldingAlertScheduler

    @Autowired
    lateinit var mailSettingsRepository: MailSettingsRepository

    @Autowired
    lateinit var notificationPreferencesRepository: NotificationPreferencesRepository

    @Autowired
    lateinit var notificationDeliveryRepository: NotificationDeliveryRepository

    @Autowired
    lateinit var userRepository: UserRepository

    var administratorId: Long = 0
    lateinit var stocksWalletId: UUID
    lateinit var fundsWalletId: UUID
    lateinit var stockTypeId: UUID
    lateinit var fundTypeId: UUID
    lateinit var magazineLuiza: StockHoldingResponse
    lateinit var petrobras: StockHoldingResponse
    lateinit var logistics: FundHoldingResponse

    @BeforeAll
    fun setup() {
        mailSettingsRepository.updateServer(
            MailServerUpdate(
                host = "localhost",
                port = greenMail.smtp.port,
                security = MailSecurity.NONE,
                username = null,
                passwordCiphertext = null,
                fromAddress = "investlog@example.com",
                fromName = null,
                publicUrl = PUBLIC_URL,
            ),
        )
        mailSettingsRepository.updateEnabled(true)

        administratorId = userRepository.findByEmail(ADMIN_EMAIL)!!.id
        stocksWalletId = createWallet("Ações Brasil", "stocks")
        fundsWalletId = createWallet("Fundos Imobiliários", "funds")
        stockTypeId = createHoldingType("Ação Alerta", "STOCKS")
        fundTypeId = createHoldingType("Fundo Alerta", "FUNDS")
    }

    @BeforeEach
    fun purgeMailboxes() {
        greenMail.purgeEmailFromAllMailboxes()
    }

    @Test
    @Order(1)
    fun `a holding that passes the minimum gain overnight is emailed on the first run from 08,00 with every holding above it`() {

        magazineLuiza = createStockHolding("MGLU3", "Magazine Luiza", currentPrice = "10.12")
        petrobras = createStockHolding("PETR4", "Petrobras", currentPrice = "10.08")
        createStockHolding("VALE3", "Vale", currentPrice = "10.50")
        logistics = createFundHolding("CSHG Logística", currentValue = "1012.00")
        freezeStock(magazineLuiza.id)
        freezeStock(petrobras.id)
        freezeFund(logistics.id)

        runAt("2026-11-09T02:00")

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertEquals(mapOf(magazineLuiza.id to true, petrobras.id to false, logistics.id to true), greenStates())
        assertTrue(alertedAt(magazineLuiza.id) == null && alertedAt(logistics.id) == null)

        runAt("2026-11-09T08:00")

        val message = greenMail.receivedMessages.single()
        val html = htmlOf(message)

        assertEquals(listOf(ADMIN_EMAIL), message.allRecipients.map { recipient -> recipient.toString() })
        assertEquals("2 investimentos congelados passaram do ganho mínimo", message.subject)
        assertTrue(html.contains("Eles passaram do ganho mínimo de"))
        assertTrue(html.contains("1,00%"))
        assertTrue(html.contains("MGLU3"))
        assertTrue(html.contains("Ações Brasil · Magazine Luiza"))
        assertTrue(html.contains("R$ 1.012,00"))
        assertTrue(html.contains("+R$ 12,00"))
        assertTrue(html.contains("+1,20%"))
        assertTrue(html.contains("CSHG Logística"))
        assertTrue(html.contains("Fundos Imobiliários · Fundo"))
        assertFalse(html.contains("PETR4"))
        assertFalse(html.contains("VALE3"))
        assertEquals(2, newPillsIn(html))
        assertFalse(html.contains("avisado em"))
        assertTrue(html.contains("href=\"$PUBLIC_URL/investments\""))
        assertTrue(html.contains("href=\"$PUBLIC_URL/notifications\""))
        assertTrue(html.contains("No máximo um aviso por dia, entre 08:00 e 22:00."))
        assertNotNull(alertedAt(magazineLuiza.id))
        assertNotNull(alertedAt(logistics.id))
    }

    @Test
    @Order(2)
    fun `re-running the same day sends nothing more`() {

        runAt("2026-11-09T10:00")

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertFalse(notificationDeliveryRepository.claim(administratorId, NotificationDeliveryKind.FROZEN_ALERT, LocalDate.of(2026, 11, 9)))
    }

    @Test
    @Order(3)
    fun `a second holding turning green the same day waits for the next day, which marks it new against the earlier ones`() {

        patchStock(petrobras.id, """{"currentPrice":10.15}""")

        runAt("2026-11-09T12:00")

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertEquals(true, greenStates()[petrobras.id])
        assertNull(alertedAt(petrobras.id))

        runAt("2026-11-10T06:00")

        assertTrue(greenMail.receivedMessages.isEmpty())

        runAt("2026-11-10T08:00")

        val message = greenMail.receivedMessages.single()
        val html = htmlOf(message)

        assertEquals("1 investimento congelado passou do ganho mínimo", message.subject)
        assertTrue(html.contains("Ele passou do ganho mínimo de"))
        assertTrue(html.contains("já pode ser vendido com lucro."))
        assertEquals(1, newPillsIn(html))
        assertTrue(html.contains("Ações Brasil · Petrobras</span>"))
        assertTrue(html.contains("Ações Brasil · Magazine Luiza · avisado em 09/11"))
        assertTrue(html.contains("Fundos Imobiliários · Fundo · avisado em 09/11"))
        assertTrue(html.indexOf("PETR4") < html.indexOf("MGLU3"))
    }

    @Test
    @Order(4)
    fun `a holding that falls to the minimum gain and passes it again is new again`() {

        patchStock(magazineLuiza.id, """{"currentPrice":10.10}""")

        runAt("2026-11-11T08:00")

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertEquals(false, greenStates()[magazineLuiza.id])

        patchStock(magazineLuiza.id, """{"currentPrice":10.20}""")

        runAt("2026-11-11T10:00")

        val html = htmlOf(greenMail.receivedMessages.single())

        assertEquals(1, newPillsIn(html))
        assertTrue(html.contains("Ações Brasil · Magazine Luiza</span>"))
        assertTrue(html.contains("Ações Brasil · Petrobras · avisado em 10/11"))
    }

    @Test
    @Order(5)
    fun `unfreezing or deleting a holding removes its state`() {

        patchStock(petrobras.id, """{"frozen":false}""")
        restTestClient.delete()
            .uri("/private/v1/wallets/$fundsWalletId/fund-holdings/${logistics.id}")
            .exchange()
            .expectStatus().isNoContent()

        runAt("2026-11-11T12:00")

        assertEquals(mapOf(magazineLuiza.id to true), greenStates())

        patchStock(magazineLuiza.id, """{"frozen":false}""")

        runAt("2026-11-11T14:00")

        assertTrue(greenStates().isEmpty())
        assertTrue(greenMail.receivedMessages.isEmpty())
    }

    @Test
    @Order(6)
    fun `with a minimum gain of 0 any positive gain is alerted`() {

        notificationPreferencesRepository.save(administratorId, NotificationPreferences.DEFAULTS.copy(frozenAlertMinimumGainPercentage = BigDecimal("0.00")))
        val almostFlat = createFundHolding("Fundo Quase Zero", currentValue = "1001.00")
        freezeFund(almostFlat.id)

        runAt("2026-11-12T08:00")

        val html = htmlOf(greenMail.receivedMessages.single())

        assertTrue(html.contains("0,00%"))
        assertTrue(html.contains("Fundo Quase Zero"))
        assertTrue(html.contains("+0,10%"))
    }

    @Test
    @Order(7)
    fun `with a minimum gain of 5 percent, 3,20 percent is ignored and 5,40 percent is alerted`() {

        notificationPreferencesRepository.save(administratorId, NotificationPreferences.DEFAULTS.copy(frozenAlertMinimumGainPercentage = BigDecimal("5.00")))
        val belowMinimum = createFundHolding("Fundo Três Vírgula Dois", currentValue = "1032.00")
        val aboveMinimum = createFundHolding("Fundo Cinco Vírgula Quatro", currentValue = "1054.00")
        freezeFund(belowMinimum.id)
        freezeFund(aboveMinimum.id)

        runAt("2026-11-13T08:00")

        val message = greenMail.receivedMessages.single()
        val html = htmlOf(message)

        assertEquals("1 investimento congelado passou do ganho mínimo", message.subject)
        assertTrue(html.contains("5,00%"))
        assertTrue(html.contains("Fundo Cinco Vírgula Quatro"))
        assertTrue(html.contains("+5,40%"))
        assertFalse(html.contains("Fundo Três Vírgula Dois"))
        assertFalse(html.contains("Fundo Quase Zero"))
        assertEquals(false, greenStates()[belowMinimum.id])
    }

    @Test
    @Order(8)
    fun `nothing is claimed or marked alerted while email sending is off`() {

        notificationPreferencesRepository.save(administratorId, NotificationPreferences.DEFAULTS)
        val waiting = createFundHolding("Fundo Em Espera", currentValue = "1100.00")
        freezeFund(waiting.id)
        mailSettingsRepository.updateEnabled(false)

        runAt("2026-11-14T08:00")

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertNull(alertedAt(waiting.id))

        mailSettingsRepository.updateEnabled(true)

        runAt("2026-11-14T10:00")

        assertTrue(htmlOf(greenMail.receivedMessages.single()).contains("Fundo Em Espera"))
        assertNotNull(alertedAt(waiting.id))
    }

    @Test
    @Order(9)
    fun `users with the alert off, pending users and blocked users receive nothing`() {

        val alertOff = createUser("sem-alerta@example.com", CurrentUser.Status.APPROVED)
        notificationPreferencesRepository.save(alertOff, NotificationPreferences.DEFAULTS.copy(frozenAlertEnabled = false))
        val pending = createUser("pendente@example.com", CurrentUser.Status.PENDING)
        val blocked = createUser("bloqueado@example.com", CurrentUser.Status.BLOCKED)

        for (userId in listOf(alertOff, pending, blocked)) {
            val walletId = createWallet("Carteira $userId", "funds")
            freezeFund(createFundHolding("Fundo do usuário $userId", currentValue = "1200.00", walletId = walletId).id, walletId)
            dsl.update(WALLETS).set(WALLETS.USER_ID, userId).where(WALLETS.EXTERNAL_ID.eq(walletId)).execute()
        }

        runAt("2026-11-15T08:00")

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertEquals(0, dsl.fetchCount(FROZEN_HOLDING_ALERTS, FROZEN_HOLDING_ALERTS.USER_ID.`in`(alertOff, pending, blocked)))
    }

    private fun runAt(saoPauloTime: String) {
        val target = LocalDateTime.parse(saoPauloTime).atZone(SAO_PAULO).toInstant()
        clock.advance(Duration.between(clock.instant(), target))
        frozenHoldingAlertScheduler.checkFrozenHoldings()
    }

    private fun greenStates(): Map<UUID, Boolean> =
        dsl.selectFrom(FROZEN_HOLDING_ALERTS)
            .where(FROZEN_HOLDING_ALERTS.USER_ID.eq(administratorId))
            .fetch()
            .associate { record -> record.holdingExternalId!! to record.green!! }

    private fun alertedAt(externalId: UUID) =
        dsl.selectFrom(FROZEN_HOLDING_ALERTS)
            .where(FROZEN_HOLDING_ALERTS.HOLDING_EXTERNAL_ID.eq(externalId))
            .fetchSingle()
            .alertedAt

    private fun newPillsIn(html: String): Int = Regex(">Novo</span>").findAll(html).count()

    private fun createUser(email: String, status: CurrentUser.Status): Long {
        val userId = userRepository.createLocalUser(name = email.substringBefore("@"), email = email, passwordHash = "hash").id

        dsl.update(USERS).set(USERS.STATUS, status.name).where(USERS.ID.eq(userId)).execute()

        return userId
    }

    private fun createWallet(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createHoldingType(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/holding-types")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kinds":["$kind"]}""")
        .exchange()
        .returnResult<TypeResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(ticker: String, name: String, currentPrice: String): StockHoldingResponse = restTestClient.post()
        .uri("/private/v1/wallets/$stocksWalletId/stock-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "typeId":"$stockTypeId",
              "ticker":"$ticker",
              "name":"$name",
              "currentPrice":$currentPrice,
              "lot":{"lotDate":"2026-01-15","quantity":100,"price":10.00}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<StockHoldingResponse>()
        .responseBody!!

    private fun createFundHolding(name: String, currentValue: String, walletId: UUID = fundsWalletId): FundHoldingResponse = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/fund-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "typeId":"$fundTypeId",
              "name":"$name",
              "currentValue":$currentValue,
              "contribution":{"contributionDate":"2026-01-10","amount":1000.00}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<FundHoldingResponse>()
        .responseBody!!

    private fun patchStock(holdingId: UUID, body: String) {
        restTestClient.patch()
            .uri("/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk()
    }

    private fun freezeStock(holdingId: UUID) = patchStock(holdingId, """{"frozen":true}""")

    private fun freezeFund(holdingId: UUID, walletId: UUID = fundsWalletId) {
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/$holdingId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"frozen":true}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun htmlOf(message: MimeMessage): String =
        generateSequence(listOf(message.content)) { contents ->
            contents.filterIsInstance<MimeMultipart>()
                .flatMap { multipart -> (0 until multipart.count).map { index -> multipart.getBodyPart(index) } }
                .map { bodyPart -> bodyPart.content }
                .ifEmpty { null }
        }.flatten().filterIsInstance<String>().first { content -> content.contains("<html") }

    @TestConfiguration
    class FixedClockConfiguration {

        @Bean
        @Primary
        fun frozenHoldingAlertTestClock(): Clock = clock
    }

    companion object {
        private const val ADMIN_EMAIL = "admin@admin.com"
        private const val PUBLIC_URL = "https://investlog.example.com"
        private val SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")

        private val clock = MutableClock(LocalDateTime.parse("2026-11-09T00:00").atZone(SAO_PAULO).toInstant())

        private val greenMail = GreenMail(ServerSetupTest.SMTP.dynamicPort()).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopGreenMail() {
            greenMail.stop()
        }
    }
}
