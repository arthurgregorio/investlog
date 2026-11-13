package br.com.investlog.server.shared

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class BoundedDecimalRequestBodyTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    private val walletId = UUID.randomUUID()
    private val holdingId = UUID.randomUUID()
    private val otherId = UUID.randomUUID()

    private data class DecimalField(
        val description: String,
        val method: HttpMethod,
        val uri: String,
        val body: (String) -> String,
    )

    private val decimalFields: List<DecimalField> = listOf(
        reinvestmentField("quantity") { """"quantity":$it""" },
        reinvestmentField("unitPrice") { """"unitPrice":$it""" },
        reinvestmentField("amount") { """"amount":$it""" },
        reinvestmentField("fees") { """"quantity":1,"unitPrice":1,"fees":$it""" },
        reinvestmentField("taxes") { """"quantity":1,"unitPrice":1,"taxes":$it""" },

        DecimalField("holding withdrawal quantity", HttpMethod.POST, "/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals") {
            """{"resultDate":"2026-01-10","quantity":$it,"unitPrice":10}"""
        },
        DecimalField("holding withdrawal unitPrice", HttpMethod.POST, "/private/v1/wallets/$walletId/crypto-holdings/$holdingId/withdrawals") {
            """{"resultDate":"2026-01-10","quantity":1,"unitPrice":$it}"""
        },
        DecimalField("holding withdrawal fees", HttpMethod.POST, "/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals") {
            """{"resultDate":"2026-01-10","quantity":1,"unitPrice":10,"fees":$it}"""
        },
        DecimalField("holding withdrawal taxes", HttpMethod.POST, "/private/v1/wallets/$walletId/stock-holdings/$holdingId/withdrawals") {
            """{"resultDate":"2026-01-10","quantity":1,"unitPrice":10,"taxes":$it}"""
        },
        DecimalField("fund withdrawal amount", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings/$holdingId/withdrawals") {
            """{"resultDate":"2026-01-10","amount":$it}"""
        },
        DecimalField("fund withdrawal fees", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings/$holdingId/withdrawals") {
            """{"resultDate":"2026-01-10","amount":10,"fees":$it}"""
        },
        DecimalField("fund withdrawal taxes", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings/$holdingId/withdrawals") {
            """{"resultDate":"2026-01-10","amount":10,"taxes":$it}"""
        },

        DecimalField("wallet move item quantity", HttpMethod.POST, "/private/v1/wallets/$walletId/moves") {
            """{"destinationWalletId":"$otherId","items":[{"holdingId":"$holdingId","quantity":$it}]}"""
        },

        DecimalField("strategy class target", HttpMethod.PUT, "/private/v1/strategies/$holdingId/classes") {
            """{"classes":[{"name":"Ações","targetPercentage":$it}]}"""
        },
        DecimalField("holding target", HttpMethod.PUT, "/private/v1/strategy-classes/$holdingId/targets") {
            """{"targets":[{"kind":"STOCKS","holdingId":"$otherId","targetPercentage":$it}]}"""
        },

        DecimalField("fund contribution amount", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings/$holdingId/contributions") {
            """{"contributionDate":"2026-01-10","amount":$it}"""
        },
        DecimalField("fund contribution update amount", HttpMethod.PATCH, "/private/v1/wallets/$walletId/fund-holdings/$holdingId/contributions/$otherId") {
            """{"contributionDate":"2026-01-10","amount":$it}"""
        },
        DecimalField("fund holding currentValue", HttpMethod.PATCH, "/private/v1/wallets/$walletId/fund-holdings/$holdingId") {
            """{"currentValue":$it}"""
        },
        DecimalField("fund holding administrationFeeRate", HttpMethod.PATCH, "/private/v1/wallets/$walletId/fund-holdings/$holdingId") {
            """{"administrationFeeRate":$it}"""
        },
        DecimalField("fund holding performanceFeeRate", HttpMethod.PATCH, "/private/v1/wallets/$walletId/fund-holdings/$holdingId") {
            """{"performanceFeeRate":$it}"""
        },
        DecimalField("fund holding create currentValue", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings") {
            """{"typeId":"$otherId","name":"Fundo","contribution":{"contributionDate":"2026-01-10","amount":10},"currentValue":$it}"""
        },
        DecimalField("fund holding create administrationFeeRate", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings") {
            """{"typeId":"$otherId","name":"Fundo","contribution":{"contributionDate":"2026-01-10","amount":10},"administrationFeeRate":$it}"""
        },
        DecimalField("fund holding create performanceFeeRate", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings") {
            """{"typeId":"$otherId","name":"Fundo","contribution":{"contributionDate":"2026-01-10","amount":10},"performanceFeeRate":$it}"""
        },
        DecimalField("fund holding create contribution amount", HttpMethod.POST, "/private/v1/wallets/$walletId/fund-holdings") {
            """{"typeId":"$otherId","name":"Fundo","contribution":{"contributionDate":"2026-01-10","amount":$it}}"""
        },

        DecimalField("stock lot quantity", HttpMethod.POST, "/private/v1/wallets/$walletId/stock-holdings/$holdingId/lots") {
            """{"lotDate":"2026-01-10","quantity":$it,"price":10}"""
        },
        DecimalField("stock lot price", HttpMethod.POST, "/private/v1/wallets/$walletId/stock-holdings/$holdingId/lots") {
            """{"lotDate":"2026-01-10","quantity":1,"price":$it}"""
        },
        DecimalField("stock lot update quantity", HttpMethod.PATCH, "/private/v1/wallets/$walletId/stock-holdings/$holdingId/lots/$otherId") {
            """{"lotDate":"2026-01-10","quantity":$it}"""
        },
        DecimalField("stock lot update price", HttpMethod.PATCH, "/private/v1/wallets/$walletId/stock-holdings/$holdingId/lots/$otherId") {
            """{"lotDate":"2026-01-10","price":$it}"""
        },
        DecimalField("stock holding currentPrice", HttpMethod.PATCH, "/private/v1/wallets/$walletId/stock-holdings/$holdingId") {
            """{"currentPrice":$it}"""
        },
        DecimalField("stock holding create currentPrice", HttpMethod.POST, "/private/v1/wallets/$walletId/stock-holdings") {
            """{"typeId":"$otherId","ticker":"PETR4","currentPrice":$it,"lot":{"lotDate":"2026-01-10","quantity":1,"price":10}}"""
        },
        DecimalField("stock holding create lot quantity", HttpMethod.POST, "/private/v1/wallets/$walletId/stock-holdings") {
            """{"typeId":"$otherId","ticker":"PETR4","lot":{"lotDate":"2026-01-10","quantity":$it,"price":10}}"""
        },

        DecimalField("crypto lot quantity", HttpMethod.POST, "/private/v1/wallets/$walletId/crypto-holdings/$holdingId/lots") {
            """{"lotDate":"2026-01-10","quantity":$it,"price":10}"""
        },
        DecimalField("crypto lot update price", HttpMethod.PATCH, "/private/v1/wallets/$walletId/crypto-holdings/$holdingId/lots/$otherId") {
            """{"lotDate":"2026-01-10","price":$it}"""
        },
        DecimalField("crypto holding currentPrice", HttpMethod.PATCH, "/private/v1/wallets/$walletId/crypto-holdings/$holdingId") {
            """{"currentPrice":$it}"""
        },
        DecimalField("crypto holding create currentPrice", HttpMethod.POST, "/private/v1/wallets/$walletId/crypto-holdings") {
            """{"typeId":"$otherId","ticker":"BTC","currentPrice":$it,"lot":{"lotDate":"2026-01-10","quantity":1,"price":10}}"""
        },

        DecimalField("cash transfer amount", HttpMethod.POST, "/private/v1/wallets/$walletId/cash-transfers") {
            """{"destinationWalletId":"$otherId","transferDate":"2026-01-10","amount":$it}"""
        },
        DecimalField("cash transfer rate", HttpMethod.POST, "/private/v1/wallets/$walletId/cash-transfers") {
            """{"destinationWalletId":"$otherId","transferDate":"2026-01-10","amount":10,"rate":$it}"""
        },
        DecimalField("cash withdrawal amount", HttpMethod.POST, "/private/v1/wallets/$walletId/cash-withdrawals") {
            """{"withdrawalDate":"2026-01-10","amount":$it}"""
        },

        DecimalField("currency rate", HttpMethod.PUT, "/private/v1/currency-rates/USD") {
            """{"rate":$it}"""
        },
        DecimalField("notification minimum gain", HttpMethod.PUT, "/private/v1/profile/notifications") {
            """{"periodicReportFrequency":"NONE","monthEndReportEnabled":false,"frozenAlertEnabled":true,"frozenAlertMinimumGainPercentage":$it}"""
        },
    )

    private fun reinvestmentField(name: String, fragment: (String) -> String) = DecimalField(
        "reinvestment $name",
        HttpMethod.POST,
        "/private/v1/reinvestments",
    ) {
        """{"sourceKind":"STOCKS","sourceHoldingId":"$holdingId","destinationKind":"STOCKS","destinationHoldingId":"$otherId","reinvestmentDate":"2026-01-10",${fragment(it)}}"""
    }

    private fun statusOf(field: DecimalField, value: String): Int = restTestClient.method(field.method)
        .uri(field.uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(field.body(value))
        .exchange()
        .returnResult(String::class.java)
        .status
        .value()

    @Test
    fun `rejects an exponent-heavy number in every decimal field of a request body`() {
        val hostileValues = listOf("1E50000000", "1E-50000000", "-1E40000000", "\"1E50000000\"", "0.${"0".repeat(5000)}1")
        val failures = mutableListOf<String>()

        decimalFields.forEach { field ->
            hostileValues.forEach { value ->
                val startedAt = System.nanoTime()
                val status = statusOf(field, value)
                val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
                if (status != 400) failures += "${field.description} accepted ${value.take(20)} with status $status"
                if (elapsedMillis > 3_000) failures += "${field.description} took ${elapsedMillis}ms for ${value.take(20)}"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `rejects numbers beyond the supported integer digits or decimal places`() {
        val failures = mutableListOf<String>()

        decimalFields.forEach { field ->
            listOf("1000000000000000", "1.123456789012345678901").forEach { value ->
                val status = statusOf(field, value)
                if (status != 400) failures += "${field.description} accepted $value with status $status"
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `allows more decimal places for quantities and unit prices than for amounts`() {
        val quantity = decimalFields.single { it.description == "stock lot quantity" }
        val amount = decimalFields.single { it.description == "cash transfer amount" }

        assertTrue(statusOf(quantity, "0.123456789012345678") != 400)
        assertTrue(statusOf(quantity, "0.1234567890123456789") == 400)
        assertTrue(statusOf(amount, "0.12345678") != 400)
        assertTrue(statusOf(amount, "0.123456789") == 400)
    }

    @Test
    fun `keeps accepting amounts within the supported precision`() {
        val failures = mutableListOf<String>()

        decimalFields.forEach { field ->
            listOf("1", "10.5", "99.99", "999999999999999.99999999").forEach { value ->
                val status = statusOf(field, value)
                if (status == 400 && !field.description.startsWith("strategy") && !field.description.startsWith("holding target") && !field.description.startsWith("notification")) {
                    failures += "${field.description} rejected $value with status $status"
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
