package br.com.investlog.server.cash

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.CAPITAL_TIMELINE
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_CASH
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashWholeCentsTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    lateinit var sourceWalletId: UUID

    @BeforeAll
    fun setup() {
        val stockTypeId = restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Centavos Ação","kinds":["STOCKS"]}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        sourceWalletId = createWallet("Centavos Origem", "STOCKS")
        val holdingId = restTestClient.post()
            .uri("/private/v1/wallets/$sourceWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "typeId":"$stockTypeId",
                  "ticker":"WEGE3",
                  "currentPrice":50,
                  "lot":{"lotDate":"2025-01-10","quantity":100,"price":100}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
            .id

        post(
            "/private/v1/wallets/$sourceWalletId/stock-holdings/$holdingId/withdrawals",
            """{"resultDate":"2025-02-01","quantity":100,"unitPrice":100}""",
        )
    }

    private fun createWallet(name: String, kind: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
        .exchange()
        .returnResult<WalletResponse>()
        .responseBody!!
        .id

    private fun createBitcoin(walletId: UUID, lotDate: String, quantity: String, price: String): UUID = restTestClient.post()
        .uri("/private/v1/wallets/$walletId/crypto-holdings")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "ticker":"BTC",
              "name":"Bitcoin",
              "currentPrice":$price,
              "lot":{"lotDate":"$lotDate","quantity":$quantity,"price":$price}
            }
            """.trimIndent()
        )
        .exchange()
        .expectStatus().isCreated()
        .returnResult<CryptoHoldingResponse>()
        .responseBody!!
        .id

    private fun post(uri: String, body: String) {
        restTestClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isCreated()
    }

    private fun transfer(originId: UUID, destinationId: UUID, transferDate: String, amount: String) = restTestClient.post()
        .uri("/private/v1/wallets/$originId/cash-transfers")
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"destinationWalletId":"$destinationId","transferDate":"$transferDate","amount":$amount}""")
        .exchange()

    private fun walletCash(walletId: UUID): BigDecimal = restTestClient.get()
        .uri("/private/v1/wallets/$walletId")
        .exchange()
        .expectStatus().isOk()
        .returnResult<WalletResponse>()
        .responseBody!!
        .cash

    private fun internalId(walletId: UUID): Long =
        dsl.select(WALLETS.ID)
            .from(WALLETS)
            .where(WALLETS.EXTERNAL_ID.eq(walletId))
            .fetchSingle(WALLETS.ID)!!

    private fun replayedCash(walletId: UUID): BigDecimal =
        dsl.select(WALLET_CASH.CASH)
            .from(WALLET_CASH)
            .where(WALLET_CASH.WALLET_ID.eq(internalId(walletId)))
            .fetchSingle(WALLET_CASH.CASH)!!

    private fun purchaseAmount(holdingId: UUID): BigDecimal =
        dsl.select(CRYPTO_LOTS.PURCHASE_AMOUNT)
            .from(CRYPTO_LOTS)
            .join(CRYPTO_HOLDINGS).on(CRYPTO_HOLDINGS.ID.eq(CRYPTO_LOTS.CRYPTO_HOLDING_ID))
            .where(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(holdingId))
            .orderBy(CRYPTO_LOTS.LOT_DATE)
            .limit(1)
            .fetchSingle(CRYPTO_LOTS.PURCHASE_AMOUNT)!!

    private fun reconciliationWalletIds(): List<String> = restTestClient.get()
        .uri("/private/v1/cash-reconciliation")
        .exchange()
        .expectStatus().isOk()
        .returnResult<List<Map<String, Any?>>>()
        .responseBody!!
        .map { entry -> entry["walletId"].toString() }

    private fun isWholeCents(value: BigDecimal): Boolean = value.compareTo(value.setScale(2, RoundingMode.HALF_UP)) == 0

    private fun assertZero(value: BigDecimal) = assertEquals(0, value.compareTo(BigDecimal.ZERO), "expected zero, got $value")

    @Test
    fun `a purchase that spends a transfer to a fraction of a cent leaves the wallet with no cash`() {
        val walletId = createWallet("Centavos Cripto", "CRYPTO")
        transfer(sourceWalletId, walletId, "2025-03-01", "1524.00").expectStatus().isCreated()
        val holdingId = createBitcoin(walletId, "2025-03-01", "0.00366469", "415860.55")

        val lotAmount = purchaseAmount(holdingId)
        assertEquals(BigDecimal("1523.9999989795"), lotAmount)
        assertFalse(isWholeCents(lotAmount))

        assertZero(walletCash(walletId))
        assertZero(replayedCash(walletId))

        dsl.update(WALLETS)
            .set(WALLETS.CASH_RECONCILED, false)
            .where(WALLETS.EXTERNAL_ID.eq(walletId))
            .execute()

        assertFalse(walletId.toString() in reconciliationWalletIds())
    }

    @Test
    fun `a transfer of the whole displayed cash is accepted when a purchase left it a fraction of a cent short`() {
        val walletId = createWallet("Centavos Cripto Espelho", "CRYPTO")
        transfer(sourceWalletId, walletId, "2025-03-01", "2000.00").expectStatus().isCreated()
        val holdingId = createBitcoin(walletId, "2025-03-01", "0.00366469", "415860.56")

        val lotAmount = purchaseAmount(holdingId)
        assertFalse(isWholeCents(lotAmount))
        assertTrue(lotAmount > BigDecimal("1524.00"))
        assertEquals(0, BigDecimal("476.00").compareTo(walletCash(walletId)))

        transfer(walletId, sourceWalletId, "2025-03-02", "476.00").expectStatus().isCreated()

        assertZero(walletCash(walletId))
        assertZero(replayedCash(walletId))
    }

    @Test
    fun `the capital timeline carries no fraction of a cent once a later purchase spends the leftover balance`() {
        val walletId = createWallet("Centavos Cripto Série", "CRYPTO")
        transfer(sourceWalletId, walletId, "2025-03-01", "1524.00").expectStatus().isCreated()
        val holdingId = createBitcoin(walletId, "2025-03-01", "0.00366469", "415860.55")
        post(
            "/private/v1/wallets/$walletId/crypto-holdings/$holdingId/lots",
            """{"lotDate":"2025-04-01","quantity":1,"price":100.00}""",
        )

        val rows = dsl.select(CAPITAL_TIMELINE.OWN_MONEY_AMOUNT, CAPITAL_TIMELINE.REINVESTED_PROFIT_AMOUNT)
            .from(CAPITAL_TIMELINE)
            .where(CAPITAL_TIMELINE.WALLET_ID.eq(internalId(walletId)))
            .fetch()

        assertNotEquals(0, rows.size)
        rows.forEach { row ->
            val ownMoney = row.get(CAPITAL_TIMELINE.OWN_MONEY_AMOUNT)!!
            val reinvestedProfit = row.get(CAPITAL_TIMELINE.REINVESTED_PROFIT_AMOUNT)!!
            assertTrue(isWholeCents(ownMoney), "own money $ownMoney carries a fraction of a cent")
            assertTrue(isWholeCents(reinvestedProfit), "reinvested profit $reinvestedProfit carries a fraction of a cent")
        }
        assertEquals(0, BigDecimal("1624.00").compareTo(rows.sumOf { row -> row.get(CAPITAL_TIMELINE.OWN_MONEY_AMOUNT)!! }))
        assertZero(rows.sumOf { row -> row.get(CAPITAL_TIMELINE.REINVESTED_PROFIT_AMOUNT)!! })
    }
}
