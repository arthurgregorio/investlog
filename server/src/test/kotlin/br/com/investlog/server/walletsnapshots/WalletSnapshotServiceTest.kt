package br.com.investlog.server.walletsnapshots

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_DAILY_SNAPSHOTS
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import br.com.investlog.server.walletsnapshots.services.WalletSnapshotService
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletSnapshotServiceTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var walletSnapshotService: WalletSnapshotService

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var investedWalletId: UUID
    lateinit var emptyWalletId: UUID
    lateinit var stockHoldingId: UUID

    private val snapshotDate: LocalDate = LocalDate.of(2026, 9, 19)

    @BeforeAll
    fun setup() {
        investedWalletId = createWallet("Invested Wallet")
        emptyWalletId = createWallet("Empty Wallet")

        val stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação ON"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        stockHoldingId = restTestClient.post()
            .uri("/private/v1/wallets/$investedWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"PETR4",
                  "name":"Petrobras",
                  "currentPrice":38.50,
                  "lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id
    }

    private fun createWallet(name: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"stocks","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun findWallet(walletId: UUID): WalletResponse = restTestClient.get()
        .uri("/private/v1/wallets/$walletId")
        .exchange()
        .expectStatus().isOk()
        .returnResult<WalletResponse>()
        .responseBody!!

    private fun countSnapshots(walletId: UUID): Int = dsl.fetchCount(
        dsl.selectFrom(WALLET_DAILY_SNAPSHOTS)
            .where(
                WALLET_DAILY_SNAPSHOTS.WALLET_ID.eq(
                    dsl.select(WALLETS.ID).from(WALLETS).where(WALLETS.EXTERNAL_ID.eq(walletId))
                )
            )
    )

    private fun findSnapshot(walletId: UUID, date: LocalDate) = dsl.selectFrom(WALLET_DAILY_SNAPSHOTS)
        .where(
            WALLET_DAILY_SNAPSHOTS.WALLET_ID.eq(
                dsl.select(WALLETS.ID).from(WALLETS).where(WALLETS.EXTERNAL_ID.eq(walletId))
            )
        )
        .and(WALLET_DAILY_SNAPSHOTS.SNAPSHOT_DATE.eq(date))
        .fetchOne()

    @Test
    @Order(1)
    fun `captures exactly one snapshot per wallet for the run date`() {

        walletSnapshotService.captureSnapshots(snapshotDate)

        assertEquals(1, countSnapshots(investedWalletId))
        assertEquals(1, countSnapshots(emptyWalletId))
    }

    @Test
    @Order(2)
    fun `snapshot figures match what the live wallet endpoint reports`() {

        val wallet = findWallet(investedWalletId)
        val snapshot = assertNotNull(findSnapshot(investedWalletId, snapshotDate))

        assertEquals(0, wallet.currentValue!!.compareTo(snapshot.currentValue))
        assertEquals(0, wallet.totalInvested.compareTo(snapshot.totalInvested))
        assertEquals(0, wallet.gain!!.compareTo(snapshot.gain))
        assertEquals(0, wallet.gainPct!!.compareTo(snapshot.gainPct))
    }

    @Test
    @Order(3)
    fun `a wallet with no holdings is snapshotted with zeroed figures`() {

        val snapshot = assertNotNull(findSnapshot(emptyWalletId, snapshotDate))

        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.currentValue))
        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.totalInvested))
        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.gain))
        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.gainPct))
    }

    @Test
    @Order(4)
    fun `re-running for a date that already has a snapshot updates it instead of duplicating`() {

        restTestClient.patch()
            .uri("/private/v1/wallets/$investedWalletId/stock-holdings/$stockHoldingId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"currentPrice":40.00}""")
            .exchange()
            .expectStatus().isOk()

        walletSnapshotService.captureSnapshots(snapshotDate)

        assertEquals(1, countSnapshots(investedWalletId))

        val snapshot = assertNotNull(findSnapshot(investedWalletId, snapshotDate))
        assertEquals(0, BigDecimal("4000.00").compareTo(snapshot.currentValue))
        assertEquals(0, BigDecimal("3500.00").compareTo(snapshot.totalInvested))
        assertEquals(0, BigDecimal("500.00").compareTo(snapshot.gain))
    }

    @Test
    @Order(5)
    fun `a later run date adds a second snapshot rather than replacing the first`() {

        walletSnapshotService.captureSnapshots(snapshotDate.plusDays(1))

        assertEquals(2, countSnapshots(investedWalletId))
        assertNotNull(findSnapshot(investedWalletId, snapshotDate))
        assertNotNull(findSnapshot(investedWalletId, snapshotDate.plusDays(1)))
    }
}
