package br.com.investlog.server.notifications

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.currencyrates.repositories.CurrencyRateRepository
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.system.tables.references.NOTIFICATION_DELIVERIES
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.mail.services.MailSender
import br.com.investlog.server.notifications.repositories.NotificationDeliveryRepository
import br.com.investlog.server.notifications.repositories.NotificationPreferences
import br.com.investlog.server.notifications.repositories.NotificationPreferencesRepository
import br.com.investlog.server.notifications.repositories.NotificationRecipient
import br.com.investlog.server.notifications.repositories.NotificationRecipientRepository
import br.com.investlog.server.notifications.repositories.PerformanceReportRepository
import br.com.investlog.server.notifications.rest.payloads.PeriodicReportFrequency
import br.com.investlog.server.notifications.scheduler.PerformanceReportScheduler
import br.com.investlog.server.notifications.services.PerformanceReport
import br.com.investlog.server.notifications.services.PerformanceReportService
import br.com.investlog.server.notifications.services.PerformanceReportView
import br.com.investlog.server.notifications.services.ReportPeriod
import br.com.investlog.server.overview.rest.payloads.MonthSummaryResponse
import br.com.investlog.server.overview.rest.payloads.PortfolioSummaryResponse
import br.com.investlog.server.overview.services.OverviewService
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.MutableClock
import br.com.investlog.server.shared.security.UserRepository
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import br.com.investlog.server.walletsnapshots.services.WalletSnapshotService
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceReportSchedulerTest : BaseIntegrationTest() {

    @TestConfiguration
    class FixedClockConfiguration {

        @Bean
        @Primary
        fun fixedClock(): MutableClock = MutableClock(Instant.parse("2026-10-31T15:00:00Z"))
    }

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var performanceReportScheduler: PerformanceReportScheduler

    @Autowired
    lateinit var walletSnapshotService: WalletSnapshotService

    @Autowired
    lateinit var mailSettingsRepository: MailSettingsRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var notificationPreferencesRepository: NotificationPreferencesRepository

    @Autowired
    lateinit var notificationRecipientRepository: NotificationRecipientRepository

    @Autowired
    lateinit var notificationDeliveryRepository: NotificationDeliveryRepository

    @Autowired
    lateinit var performanceReportRepository: PerformanceReportRepository

    @Autowired
    lateinit var currencyRateRepository: CurrencyRateRepository

    @Autowired
    lateinit var overviewService: OverviewService

    @Autowired
    lateinit var mailSender: MailSender

    lateinit var stocksWalletId: UUID
    lateinit var risingStockId: UUID
    lateinit var fallingStockId: UUID
    var withoutWalletsUserId = 0L

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
                publicUrl = "https://investlog.example.com",
            )
        )
        mailSettingsRepository.updateEnabled(true)

        val stockTypeId = createType("Report Stocks", "STOCKS")
        val fundTypeId = createType("Report Pension", "FUNDS")

        stocksWalletId = createWallet("Ações Brasil", "STOCKS")
        val cryptoWalletId = createWallet("Cripto", "CRYPTO")
        risingStockId = createStock(stockTypeId, "RISE3")
        fallingStockId = createStock(stockTypeId, "FALL3")
        val cryptoHoldingId = post(
            "/private/v1/wallets/$cryptoWalletId/crypto-holdings",
            """{"ticker":"MBTC","name":"Moeda","currentPrice":100,"lot":{"lotDate":"2026-09-15","quantity":1,"price":100}}""",
        ).returnResult<CryptoHoldingResponse>().responseBody!!.id

        withoutWalletsUserId = createUser("sem-carteira@example.com", CurrentUser.Status.APPROVED, PeriodicReportFrequency.DAILY, monthEndReportEnabled = true)
        createWalletFor(createUser("desligado@example.com", CurrentUser.Status.APPROVED, PeriodicReportFrequency.DISABLED, monthEndReportEnabled = false))
        createWalletFor(createUser("bloqueado@example.com", CurrentUser.Status.BLOCKED, PeriodicReportFrequency.DAILY, monthEndReportEnabled = true))
        createWalletFor(createUser(DAILY_EMAIL, CurrentUser.Status.APPROVED, PeriodicReportFrequency.DAILY, monthEndReportEnabled = false))

        walletSnapshotService.captureSnapshots(LocalDate.parse("2026-09-30"))

        val fundsWalletId = createWallet("Previdência", "funds")
        val fundHoldingId = post(
            "/private/v1/wallets/$fundsWalletId/fund-holdings",
            """{"typeId":"$fundTypeId","name":"PGBL","currentValue":300,"contribution":{"contributionDate":"2026-10-14","amount":300}}""",
        ).returnResult<FundHoldingResponse>().responseBody!!.id

        walletSnapshotService.captureSnapshots(LocalDate.parse("2026-10-14"))

        post("/private/v1/wallets/$cryptoWalletId/crypto-holdings/$cryptoHoldingId/lots", """{"lotDate":"2026-10-05","quantity":0.5,"price":110}""")
        setStockPrice(risingStockId, "12")
        setStockPrice(fallingStockId, "8")
        dsl.update(CRYPTO_HOLDINGS).set(CRYPTO_HOLDINGS.CURRENT_PRICE, BigDecimal("115")).where(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(cryptoHoldingId)).execute()
        dsl.update(FUND_HOLDINGS).set(FUND_HOLDINGS.CURRENT_VALUE, BigDecimal("310")).where(FUND_HOLDINGS.EXTERNAL_ID.eq(fundHoldingId)).execute()
        withdraw(risingStockId, "2026-10-20", quantity = 2, unitPrice = 13)
        post("/private/v1/wallets/$stocksWalletId/stock-holdings/$fallingStockId/lots", """{"lotDate":"2026-10-25","quantity":1,"price":8}""")
    }

    @BeforeEach
    fun purgeMailboxes() {
        greenMail.purgeEmailFromAllMailboxes()
    }

    @Test
    @Order(1)
    fun `the month-end report shows what the Overview showed on the month's last day`() {
        val overviewMonth = get<MonthSummaryResponse>("/private/v1/overview/month")
        val overviewSummary = get<PortfolioSummaryResponse>("/private/v1/overview")

        walletSnapshotService.captureSnapshots(LocalDate.parse("2026-10-31"))
        clock.advance(Duration.ofDays(1))

        performanceReportScheduler.sendReports()

        val message = greenMail.receivedMessages.single()
        val html = htmlOf(message)

        assertEquals(ADMIN_EMAIL, message.allRecipients.single().toString())
        assertEquals("Relatório de outubro de 2026", message.subject)

        assertEquals("2026-10", overviewMonth.month)
        assertEquals("R$ 355,00", PerformanceReportView.money(overviewMonth.contributions, "BRL"))
        assertEquals("+R$ 6,00", PerformanceReportView.signedMoney(overviewMonth.realizedProfit, "BRL"))
        assertEquals("+R$ 23,50", PerformanceReportView.signedMoney(overviewMonth.currentResultChange!!, "BRL"))
        assertEquals("RISE3", overviewMonth.bestMover!!.ticker)
        assertEquals("+20,00%", PerformanceReportView.signedPercentage(overviewMonth.bestMover!!.changePct))
        assertEquals("FALL3", overviewMonth.worstMover!!.ticker)
        assertEquals("−20,00%", PerformanceReportView.signedPercentage(overviewMonth.worstMover!!.changePct))
        assertEquals("R$ 684,50", PerformanceReportView.money(overviewSummary.totalCurrentValue, "BRL"))

        assertContainsInOrder(
            html,
            "Relatório de fim de mês",
            "Outubro de 2026",
            "Fechamento de 31/10/2026 comparado com o de 30/09/2026.",
            "Resultado no mês", "+R$ 29,50",
            "Valor total", "R$ 684,50",
            "Resultado atual", "R$ 23,50",
            "No mês", "01/10 a 31/10/2026",
            "Aportes no mês", "R$ 355,00",
            "Lucro realizado no mês", "+R$ 6,00",
            "Variação do resultado atual", "+R$ 23,50",
            "Maior alta", "RISE3", "+20,00%",
            "Maior queda", "FALL3", "−20,00%",
            "Ações Brasil", "R$ 202,00", "−R$ 4,00", "+R$ 2,00",
            "Cripto", "R$ 172,50", "R$ 17,50", "+R$ 17,50",
            "Previdência", "nova, desde 14/10", "R$ 310,00", "R$ 10,00", "+R$ 10,00",
            "Total", "na sua moeda (BRL)", "R$ 684,50", "R$ 23,50", "+R$ 29,50", "3,65%",
        )

        assertEquals(1, deliveriesOf(userIdOf(ADMIN_EMAIL)))
        assertEquals(0, deliveriesOf(withoutWalletsUserId))
    }

    @Test
    @Order(2)
    fun `a second run for the same period sends nothing`() {
        performanceReportScheduler.sendReports()

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertEquals(1, deliveriesOf(userIdOf(ADMIN_EMAIL)))
    }

    @Test
    @Order(3)
    fun `a missing end snapshot sends and records nothing, and exits count by their date`() {
        notificationPreferencesRepository.save(
            userIdOf(ADMIN_EMAIL),
            NotificationPreferences.DEFAULTS.copy(periodicReportFrequency = PeriodicReportFrequency.DAILY),
        )
        clock.advance(Duration.ofDays(2))

        performanceReportScheduler.sendReports()

        assertTrue(greenMail.receivedMessages.isEmpty())
        assertEquals(1, deliveriesOf(userIdOf(ADMIN_EMAIL)))
        assertEquals(0, deliveriesOf(userIdOf(DAILY_EMAIL)))

        walletSnapshotService.captureSnapshots(LocalDate.parse("2026-10-30"))
        withdraw(risingStockId, "2026-10-30", quantity = 1, unitPrice = 12)
        withdraw(risingStockId, "2026-11-02", quantity = 1, unitPrice = 12)
        walletSnapshotService.captureSnapshots(LocalDate.parse("2026-11-02"))

        performanceReportScheduler.sendReports()

        val messages = greenMail.receivedMessages.associateBy { message -> message.allRecipients.single().toString() }
        assertEquals(setOf(ADMIN_EMAIL, DAILY_EMAIL), messages.keys)

        val adminMessage = messages.getValue(ADMIN_EMAIL)
        assertEquals("Relatório diário · 02/11", adminMessage.subject)
        assertContainsInOrder(
            htmlOf(adminMessage),
            "Segunda-feira, 02/11/2026",
            "Comparado com o fechamento de sexta-feira, 30/10/2026.",
            "Resultado no período", "−R$ 2,00",
            "No período",
            "Ações Brasil", "R$ 202,00", "−R$ 8,00", "−R$ 2,00",
            "Cripto", "R$ 172,50", "R$ 17,50", "R$ 0,00",
            "Previdência", "R$ 310,00", "R$ 10,00", "R$ 0,00",
            "Você recebe este relatório de terça a sábado porque escolheu a frequência diária.",
        )
        assertEquals("Relatório diário · 02/11", messages.getValue(DAILY_EMAIL).subject)
        assertEquals(0, deliveriesOf(withoutWalletsUserId))
    }

    @Test
    @Order(4)
    fun `one recipient's failure is logged and never stops the others`() {
        clock.advance(Duration.ofDays(1))
        walletSnapshotService.captureSnapshots(LocalDate.parse("2026-11-03"))

        val failingForAdmin = object : PerformanceReportService(performanceReportRepository, currencyRateRepository, overviewService) {
            override fun build(recipient: NotificationRecipient, period: ReportPeriod): PerformanceReport? =
                if (recipient.email == ADMIN_EMAIL) error("Falha simulada") else super.build(recipient, period)
        }

        PerformanceReportScheduler(notificationRecipientRepository, notificationDeliveryRepository, failingForAdmin, mailSender, clock)
            .sendReports()

        assertEquals(listOf(DAILY_EMAIL), greenMail.receivedMessages.map { message -> message.allRecipients.single().toString() })
        assertEquals(2, deliveriesOf(userIdOf(ADMIN_EMAIL)))

        greenMail.purgeEmailFromAllMailboxes()
        performanceReportScheduler.sendReports()

        val retried = greenMail.receivedMessages.single()
        assertEquals(ADMIN_EMAIL, retried.allRecipients.single().toString())
        assertEquals("Relatório diário · 03/11", retried.subject)
    }

    private fun createType(name: String, kind: String): UUID =
        post("/private/v1/holding-types", """{"name":"$name","kinds":["$kind"]}""").returnResult<TypeResponse>().responseBody!!.id

    private fun createWallet(name: String, kind: String): UUID =
        post("/private/v1/wallets", """{"name":"$name","kind":"$kind","currency":"BRL"}""").returnResult<WalletResponse>().responseBody!!.id

    private fun createStock(typeId: UUID, ticker: String): UUID =
        post(
            "/private/v1/wallets/$stocksWalletId/stock-holdings",
            """{"typeId":"$typeId","ticker":"$ticker","currentPrice":10,"lot":{"lotDate":"2026-09-10","quantity":10,"price":10}}""",
        ).returnResult<StockHoldingResponse>().responseBody!!.id

    private fun withdraw(holdingId: UUID, date: String, quantity: Int, unitPrice: Int) {
        post(
            "/private/v1/wallets/$stocksWalletId/stock-holdings/$holdingId/withdrawals",
            """{"resultDate":"$date","quantity":$quantity,"unitPrice":$unitPrice}""",
        )
    }

    private fun setStockPrice(holdingId: UUID, price: String) {
        dsl.update(STOCK_HOLDINGS).set(STOCK_HOLDINGS.CURRENT_PRICE, BigDecimal(price)).where(STOCK_HOLDINGS.EXTERNAL_ID.eq(holdingId)).execute()
    }

    private fun post(uri: String, body: String) = restTestClient.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus().is2xxSuccessful()

    private inline fun <reified T : Any> get(uri: String): T = restTestClient.get()
        .uri(uri)
        .exchange()
        .expectStatus().isOk()
        .returnResult<T>()
        .responseBody!!

    private fun createUser(
        email: String,
        status: CurrentUser.Status,
        frequency: PeriodicReportFrequency,
        monthEndReportEnabled: Boolean,
    ): Long {
        val userId = userRepository.createLocalUser(name = email.substringBefore("@"), email = email, passwordHash = "hash").id

        dsl.update(USERS).set(USERS.STATUS, status.name).where(USERS.ID.eq(userId)).execute()
        notificationPreferencesRepository.save(
            userId,
            NotificationPreferences.DEFAULTS.copy(periodicReportFrequency = frequency, monthEndReportEnabled = monthEndReportEnabled),
        )

        return userId
    }

    private fun createWalletFor(userId: Long) {
        dsl.insertInto(WALLETS)
            .set(WALLETS.USER_ID, userId)
            .set(WALLETS.NAME, "Carteira")
            .set(WALLETS.KIND, WalletKind.STOCKS)
            .set(WALLETS.CURRENCY, "BRL")
            .execute()
    }

    private fun userIdOf(email: String): Long = userRepository.findByEmail(email)!!.id

    private fun deliveriesOf(userId: Long): Int =
        dsl.fetchCount(NOTIFICATION_DELIVERIES, NOTIFICATION_DELIVERIES.USER_ID.eq(userId))

    private fun htmlOf(message: MimeMessage): String =
        generateSequence(listOf(message.content)) { contents ->
            contents.filterIsInstance<MimeMultipart>()
                .flatMap { multipart -> (0 until multipart.count).map { index -> multipart.getBodyPart(index).content } }
                .ifEmpty { null }
        }.flatten().filterIsInstance<String>().first { content -> content.contains("<html") }

    private fun assertContainsInOrder(html: String, vararg fragments: String) {
        fragments.fold(0) { position, fragment ->
            val found = html.indexOf(fragment, position)
            assertTrue(found >= 0, "\"$fragment\" not found after position $position")
            found + fragment.length
        }
    }

    companion object {
        private const val ADMIN_EMAIL = "admin@admin.com"
        private const val DAILY_EMAIL = "diario@example.com"

        private val greenMail = GreenMail(ServerSetupTest.SMTP.dynamicPort()).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopGreenMail() {
            greenMail.stop()
        }
    }
}
