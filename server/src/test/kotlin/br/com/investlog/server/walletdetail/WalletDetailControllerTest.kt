package br.com.investlog.server.walletdetail

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import br.com.investlog.server.walletsnapshots.services.WalletSnapshotService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletDetailControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var walletSnapshotService: WalletSnapshotService

    lateinit var walletId: UUID
    lateinit var emptyWalletId: UUID
    lateinit var stockTypeId: UUID

    @BeforeAll
    fun setup() {
        walletId = createWallet("Detail Wallet")
        emptyWalletId = createWallet("Empty Wallet")

        stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação ON"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        // PETR4 gains: 100 × 35 invested, worth 100 × 38.50 → +10%.
        createStockHolding("PETR4", "100", "35.00", "38.50", "2024-01-15")
        // VALE3 loses: 50 × 20 invested, worth 50 × 18 → -10%.
        createStockHolding("VALE3", "50", "20.00", "18.00", "2024-03-20")
    }

    private fun createWallet(name: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"stocks","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createStockHolding(
        ticker: String,
        quantity: String,
        price: String,
        currentPrice: String,
        lotDate: String,
    ) {
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "stockTypeId":"$stockTypeId",
                  "ticker":"$ticker",
                  "name":"Holding $ticker",
                  "currentPrice":$currentPrice,
                  "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
    }

    @Test
    @Order(1)
    fun `returns every section in one response for a wallet that has holdings`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Detail Wallet")
            .jsonPath("$.kind").isEqualTo("STOCKS")
            .jsonPath("$.currency").isEqualTo("BRL")
            .jsonPath("$.totalInvested").isEqualTo(4500.0)
            .jsonPath("$.currentValue").isEqualTo(4750.0)
            .jsonPath("$.gain").isEqualTo(250.0)
            .jsonPath("$.bestPerformer.ticker").isEqualTo("PETR4")
            .jsonPath("$.worstPerformer.ticker").isEqualTo("VALE3")
            .jsonPath("$.largestHoldingName").isEqualTo("Holding PETR4")
            .jsonPath("$.activity.transactionCount").isEqualTo(2)
            .jsonPath("$.activity.investmentCount").isEqualTo(2)
    }

    @Test
    @Order(2)
    fun `header figures match what the wallets endpoint reports for the same wallet`() {
        val wallet = restTestClient.get()
            .uri("/private/v1/wallets/$walletId")
            .exchange()
            .expectStatus().isOk()
            .returnResult<WalletResponse>()
            .responseBody!!

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalInvested").isEqualTo(wallet.totalInvested.toDouble())
            .jsonPath("$.currentValue").isEqualTo(wallet.currentValue!!.toDouble())
            .jsonPath("$.gain").isEqualTo(wallet.gain!!.toDouble())
    }

    @Test
    @Order(3)
    fun `reports the largest holding's share of the wallet so the client can flag concentration`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            // PETR4 is 3850 of 4750 — above the 50% the client warns on.
            .jsonPath("$.largestHoldingShare").value<Double> { share ->
                assert(share > 50.0) { "expected PETR4 to exceed 50% of the wallet, got $share" }
            }
    }

    @Test
    @Order(4)
    fun `reports the most recent transaction in the activity section`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.activity.lastTransactionDate").isEqualTo("2024-03-20")
            .jsonPath("$.activity.lastTransactionName").isEqualTo("Holding VALE3")
            .jsonPath("$.activity.lastTransactionAmount").isEqualTo(1000.0)
    }

    @Test
    @Order(5)
    fun `a wallet with no snapshots yet returns an empty series and null deltas`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.series").isEmpty()
            .jsonPath("$.dayChange").doesNotExist()
            .jsonPath("$.weekChange").doesNotExist()
            .jsonPath("$.monthChange").doesNotExist()
    }

    @Test
    @Order(6)
    fun `returns the snapshot series once the capture job has run`() {
        walletSnapshotService.captureSnapshots(LocalDate.of(2026, 9, 19))

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.series.length()").isEqualTo(1)
            .jsonPath("$.series[0].snapshotDate").isEqualTo("2026-09-19")
            .jsonPath("$.series[0].currentValue").isEqualTo(4750.0)
    }

    @Test
    @Order(7)
    fun `computes the day change as the difference against the snapshot a day back`() {
        walletSnapshotService.captureSnapshots(LocalDate.of(2026, 9, 20))

        restTestClient.get()
            .uri("/private/v1/wallets/$walletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.series.length()").isEqualTo(2)
            .jsonPath("$.dayChange").isEqualTo(0.0)
    }

    @Test
    @Order(8)
    fun `a wallet with no holdings returns zeroed figures rather than failing`() {
        restTestClient.get()
            .uri("/private/v1/wallets/$emptyWalletId/detail")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalInvested").isEqualTo(0)
            .jsonPath("$.currentValue").isEqualTo(0)
            .jsonPath("$.gain").isEqualTo(0)
            .jsonPath("$.gainPct").doesNotExist()
            .jsonPath("$.bestPerformer").doesNotExist()
            .jsonPath("$.worstPerformer").doesNotExist()
            .jsonPath("$.largestHoldingName").doesNotExist()
            .jsonPath("$.activity.transactionCount").isEqualTo(0)
            .jsonPath("$.activity.investmentCount").isEqualTo(0)
            .jsonPath("$.activity.walletAgeInDays").doesNotExist()
    }

    @Test
    @Order(9)
    fun `an unknown wallet returns 404`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/detail")
            .exchange()
            .expectStatus().isNotFound()
    }
}
