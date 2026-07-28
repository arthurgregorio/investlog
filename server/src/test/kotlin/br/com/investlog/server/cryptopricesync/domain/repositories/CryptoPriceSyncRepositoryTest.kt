package br.com.investlog.server.cryptopricesync.domain.repositories

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptoPriceSyncRepositoryTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var cryptoPriceSyncRepository: CryptoPriceSyncRepository

    private fun createWallet(currency: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Crypto Wallet","kind":"CRYPTO","currency":"$currency"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createHolding(walletId: UUID, ticker: String): CryptoHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {
                  "ticker":"$ticker",
                  "name":"$ticker",
                  "currentPrice":100.00,
                  "lot":{"lotDate":"2024-01-01","quantity":1.0,"price":100.00}
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

    @Test
    fun `finds distinct tickers across all crypto holdings`() {
        val walletId = createWallet("USD")
        createHolding(walletId, "MATIC")
        createHolding(walletId, "MATIC")
        createHolding(walletId, "LINK")

        val tickers = cryptoPriceSyncRepository.findDistinctTickers()

        assertTrue(tickers.contains("MATIC"))
        assertTrue(tickers.contains("LINK"))
        assertEquals(tickers.size, tickers.toSet().size)
    }

    @Test
    fun `finds distinct currencies among crypto wallets`() {
        createWallet("USD")
        createWallet("BRL")
        createWallet("BRL")

        val currencies = cryptoPriceSyncRepository.findDistinctCryptoWalletCurrencies()

        assertTrue(currencies.contains("USD"))
        assertTrue(currencies.contains("BRL"))
        assertEquals(currencies.size, currencies.toSet().size)
    }

    @Test
    fun `updates current price only for matching ticker and currency`() {
        val usdWalletId = createWallet("USD")
        val brlWalletId = createWallet("BRL")
        val uniqueTicker = "DOT" + UUID.randomUUID().toString().take(6).uppercase()
        createHolding(usdWalletId, uniqueTicker)
        createHolding(brlWalletId, uniqueTicker)

        val updatedRows = cryptoPriceSyncRepository.updatePrice(uniqueTicker, "USD", BigDecimal("7.25"))

        assertEquals(1, updatedRows)

        restTestClient.get()
            .uri("/private/v1/wallets/$usdWalletId/crypto-holdings")
            .exchange()
            .expectBody()
            .jsonPath("$.content[0].currentPrice").isEqualTo(7.25)

        restTestClient.get()
            .uri("/private/v1/wallets/$brlWalletId/crypto-holdings")
            .exchange()
            .expectBody()
            .jsonPath("$.content[0].currentPrice").isEqualTo(100.00)
    }

    @Test
    fun `does not update anything for an unknown ticker`() {
        val walletId = createWallet("USD")
        createHolding(walletId, "SHIB")

        val updatedRows = cryptoPriceSyncRepository.updatePrice("NONEXISTENT", "USD", BigDecimal("1.00"))

        assertEquals(0, updatedRows)
    }
}
