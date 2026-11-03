package br.com.investlog.server.strategies

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.strategies.rest.payloads.ContributionPlanResponse
import br.com.investlog.server.strategies.rest.payloads.LeftOutReason
import br.com.investlog.server.strategies.rest.payloads.OverviewAllocationResponse
import br.com.investlog.server.strategies.rest.payloads.StrategyAllocationResponse
import br.com.investlog.server.strategies.rest.payloads.StrategyResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StrategyAllocationControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    private lateinit var stockTypeId: UUID
    private lateinit var brazilWalletId: UUID
    private lateinit var usdWalletId: UUID
    private lateinit var cryptoWalletId: UUID

    private lateinit var strategy: StrategyResponse
    private lateinit var emptyStrategy: StrategyResponse
    private lateinit var defaultStrategyId: UUID
    private lateinit var sharesClassId: UUID
    private lateinit var abroadClassId: UUID
    private lateinit var cryptoClassId: UUID

    private lateinit var aaa3: StockHoldingResponse
    private lateinit var bbb3: StockHoldingResponse
    private lateinit var ccc3: StockHoldingResponse
    private lateinit var ddd: StockHoldingResponse
    private lateinit var btc: CryptoHoldingResponse

    @BeforeAll
    fun setup() {
        restTestClient.put()
            .uri("/private/v1/currency-rates/USD")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"rate":5.00}""")
            .exchange()
            .expectStatus().isOk()

        stockTypeId = restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Ação Alocação","kinds":["STOCKS"]}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id
        val banksSegmentId = restTestClient.post()
            .uri("/private/v1/segments")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Bancos"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        brazilWalletId = createWallet("Ações Alocação", "stocks", "BRL")
        usdWalletId = createWallet("Exterior Alocação", "stocks", "USD")
        cryptoWalletId = createWallet("Cripto Alocação", "crypto", "BRL")

        strategy = createStrategy("Alocação Teste")
        emptyStrategy = createStrategy("Sem classes")
        defaultStrategyId = findStrategies().single { candidate -> candidate.default }.id
        val classes = restTestClient.put()
            .uri("/private/v1/strategies/${strategy.id}/classes")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"classes":[{"name":"Ações","targetPercentage":60},{"name":"Exterior","targetPercentage":30},
                   {"name":"Cripto","targetPercentage":10}]}"""
            )
            .exchange()
            .expectStatus().isOk()
            .returnResult<StrategyResponse>()
            .responseBody!!
            .classes
        sharesClassId = classes[0].id
        abroadClassId = classes[1].id
        cryptoClassId = classes[2].id

        aaa3 = createStock(brazilWalletId, "AAA3", "10.00", 30, sharesClassId, banksSegmentId)
        bbb3 = createStock(brazilWalletId, "BBB3", "10.00", 10, sharesClassId, null)
        ccc3 = createStock(brazilWalletId, "CCC3", "10.00", 10, sharesClassId, banksSegmentId)
        ddd = createStock(usdWalletId, "DDD", "20.00", 4, abroadClassId, null)
        createStock(brazilWalletId, "EEE3", "10.00", 5, null, null)
        btc = restTestClient.post()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"ticker":"BTC","name":"Bitcoin","currentPrice":100.00,"strategyClassId":"$cryptoClassId",
                   "lot":{"lotDate":"2025-01-10","quantity":1,"price":90.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!

        freeze("/private/v1/wallets/$brazilWalletId/stock-holdings/${ccc3.id}")
        freeze("/private/v1/wallets/$cryptoWalletId/crypto-holdings/${btc.id}")

        restTestClient.put()
            .uri("/private/v1/strategy-classes/$sharesClassId/targets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"targets":[{"kind":"STOCKS","holdingId":"${aaa3.id}","targetPercentage":30}]}""")
            .exchange()
            .expectStatus().isOk()
    }

    @Test
    fun `the allocation matches the hand-computed figures for classes, holdings and segments`() {
        val allocation = restTestClient.get()
            .uri("/private/v1/strategies/${strategy.id}/allocation")
            .exchange()
            .expectStatus().isOk()
            .returnResult<StrategyAllocationResponse>()
            .responseBody!!

        assertEquals("BRL", allocation.displayCurrency)
        assertAmount("1000.00", allocation.totalValue)
        assertEquals(1, allocation.unassignedHoldingCount)
        assertAmount("50.00", allocation.unassignedValue)
        assertEquals(listOf("Ações", "Exterior", "Cripto"), allocation.classes.map { strategyClass -> strategyClass.name })

        val shares = allocation.classes[0]
        assertAmount("60", shares.targetPercentage)
        assertAmount("50.00", shares.actualPercentage)
        assertAmount("-10.00", shares.driftPercentagePoints)
        assertAmount("-100.00", shares.driftValue)
        assertAmount("500.00", shares.value)
        assertEquals(listOf(aaa3.id, bbb3.id, ccc3.id), shares.holdings.map { holding -> holding.id })

        val (first, second, third) = shares.holdings
        assertAmount("30", first.targetPercentage)
        assertTrue(first.typed)
        assertAmount("60.00", first.actualPercentage)
        assertAmount("30.00", first.driftPercentagePoints)
        assertAmount("150.00", first.driftValue)
        assertAmount("35", second.targetPercentage)
        assertEquals(false, second.typed)
        assertAmount("20.00", second.actualPercentage)
        assertAmount("-15.00", second.driftPercentagePoints)
        assertAmount("-75.00", second.driftValue)
        assertTrue(third.frozen)
        assertAmount("-75.00", third.driftValue)

        assertEquals(listOf("Bancos", "Sem segmento"), shares.segments.map { segment -> segment.label })
        assertAmount("400.00", shares.segments[0].value)
        assertAmount("80.00", shares.segments[0].classPercentage)
        assertAmount("40.00", shares.segments[0].strategyPercentage)
        assertAmount("20.00", shares.segments[1].classPercentage)
        assertAmount("10.00", shares.segments[1].strategyPercentage)

        val abroad = allocation.classes[1]
        assertAmount("400.00", abroad.value)
        assertAmount("40.00", abroad.actualPercentage)
        assertAmount("10.00", abroad.driftPercentagePoints)
        assertAmount("100.00", abroad.driftValue)
        assertAmount("100", abroad.holdings.single().targetPercentage)
        assertAmount("0.00", abroad.holdings.single().driftValue)

        val crypto = allocation.classes[2]
        assertAmount("10.00", crypto.actualPercentage)
        assertAmount("0.00", crypto.driftPercentagePoints)
    }

    @Test
    fun `a strategy without classes or holdings has an empty allocation`() {
        val allocation = restTestClient.get()
            .uri("/private/v1/strategies/${emptyStrategy.id}/allocation")
            .exchange()
            .expectStatus().isOk()
            .returnResult<StrategyAllocationResponse>()
            .responseBody!!

        assertAmount("0.00", allocation.totalValue)
        assertTrue(allocation.classes.isEmpty())
    }

    @Test
    fun `the plan buys only below-target unfrozen holdings and says why the rest are left out`() {
        val plan = planContribution(strategy.id, "300")

        assertAmount("300.00", plan.amount)
        assertAmount("1000.00", plan.totalValueBefore)
        assertAmount("1300.00", plan.totalValueAfter)

        val receiving = plan.classes.single()
        assertEquals("Ações", receiving.name)
        assertAmount("300.00", receiving.amount)
        assertAmount("50.00", receiving.percentageBefore)
        assertAmount("61.54", receiving.percentageAfter)
        assertEquals(bbb3.id, receiving.holdings.single().id)
        assertAmount("300.00", receiving.holdings.single().amount)

        assertEquals(
            mapOf(
                aaa3.id to LeftOutReason.ABOVE_TARGET_IN_CLASS,
                ccc3.id to LeftOutReason.FROZEN,
                ddd.id to LeftOutReason.CLASS_ABOVE_TARGET,
                btc.id to LeftOutReason.FROZEN,
            ),
            plan.leftOut.associate { holding -> holding.id to holding.reason },
        )
        assertEquals("Exterior", plan.leftOut.single { holding -> holding.id == ddd.id }.className)
    }

    @Test
    fun `a plan with uneven gaps still adds up to the cent`() {
        val plan = planContribution(strategy.id, "1000.005")

        assertAmount("1000.00", plan.amount)
        assertEquals(listOf("Ações", "Exterior"), plan.classes.map { strategyClass -> strategyClass.name })
        assertAmount("1000.00", plan.classes.fold(BigDecimal.ZERO) { total, strategyClass -> total + strategyClass.amount })
        plan.classes.forEach { strategyClass ->
            assertAmount(strategyClass.amount.toPlainString(), strategyClass.holdings.fold(BigDecimal.ZERO) { total, holding -> total + holding.amount })
        }
        assertEquals(LeftOutReason.FROZEN, plan.leftOut.single { holding -> holding.id == btc.id }.reason)
    }

    @Test
    fun `refuses a non-positive or missing amount, the default strategy and a strategy that cannot receive`() {
        listOf("0", "-5", "0.001").forEach { amount ->
            restTestClient.get()
                .uri("/private/v1/strategies/${strategy.id}/contribution-plan?amount=$amount")
                .exchange()
                .expectStatus().isBadRequest()
        }
        restTestClient.get()
            .uri("/private/v1/strategies/${strategy.id}/contribution-plan")
            .exchange()
            .expectStatus().isBadRequest()
        restTestClient.get()
            .uri("/private/v1/strategies/${emptyStrategy.id}/contribution-plan?amount=100")
            .exchange()
            .expectStatus().isBadRequest()
        restTestClient.get()
            .uri("/private/v1/strategies/$defaultStrategyId/contribution-plan?amount=100")
            .exchange()
            .expectStatus().isBadRequest()
        restTestClient.get()
            .uri("/private/v1/strategies/$defaultStrategyId/allocation")
            .exchange()
            .expectStatus().isBadRequest()
        restTestClient.get()
            .uri("/private/v1/strategies/${UUID.randomUUID()}/allocation")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    fun `the overview groups current value by strategy, class and type`() {
        val byStrategy = overviewAllocation("STRATEGY")
        assertAmount("1050.00", byStrategy.totalValue)
        assertEquals(listOf("Alocação Teste", "Sem estratégia"), byStrategy.groups.map { group -> group.label })
        assertEquals(strategy.id, byStrategy.groups[0].id)
        assertEquals(5, byStrategy.groups[0].holdingCount)
        assertAmount("95.24", byStrategy.groups[0].percentage)
        assertAmount("50.00", byStrategy.groups[1].value)

        val byClass = overviewAllocation("CLASS&strategyId=${strategy.id}")
        assertAmount("1000.00", byClass.totalValue)
        assertEquals(listOf("Ações", "Exterior", "Cripto"), byClass.groups.map { group -> group.label })
        assertEquals(listOf(sharesClassId, abroadClassId, cryptoClassId), byClass.groups.map { group -> group.id })
        assertAmount("40.00", byClass.groups[1].percentage)

        val byType = overviewAllocation("TYPE")
        assertEquals(listOf("Ação Alocação", "Sem tipo"), byType.groups.map { group -> group.label })
        assertAmount("950.00", byType.groups[0].value)
        assertAmount("9.52", byType.groups[1].percentage)
    }

    @Test
    fun `the overview refuses a class grouping without a strategy and an unknown grouping`() {
        restTestClient.get()
            .uri("/private/v1/overview/allocation?groupBy=CLASS")
            .exchange()
            .expectStatus().isBadRequest()
        restTestClient.get()
            .uri("/private/v1/overview/allocation?groupBy=WALLET")
            .exchange()
            .expectStatus().isBadRequest()
        restTestClient.get()
            .uri("/private/v1/overview/allocation?groupBy=CLASS&strategyId=${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    fun `another user cannot read this user's strategy`() {
        val cookie = registerApproveAndLogin("allocation-reader@example.com", "Senha123")

        listOf(
            "/private/v1/strategies/${strategy.id}/allocation",
            "/private/v1/strategies/${strategy.id}/contribution-plan?amount=100",
            "/private/v1/overview/allocation?groupBy=CLASS&strategyId=${strategy.id}",
        ).forEach { uri ->
            restTestClient.get()
                .uri(uri)
                .header("Cookie", cookie)
                .exchange()
                .expectStatus().isNotFound()
        }

        val ownGroups = restTestClient.get()
            .uri("/private/v1/overview/allocation?groupBy=STRATEGY")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()
            .returnResult<OverviewAllocationResponse>()
            .responseBody!!
        assertTrue(ownGroups.groups.isEmpty())
        assertAmount("0.00", ownGroups.totalValue)
    }

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")

    private fun planContribution(strategyId: UUID, amount: String): ContributionPlanResponse =
        restTestClient.get()
            .uri("/private/v1/strategies/$strategyId/contribution-plan?amount=$amount")
            .exchange()
            .expectStatus().isOk()
            .returnResult<ContributionPlanResponse>()
            .responseBody!!

    private fun overviewAllocation(query: String): OverviewAllocationResponse =
        restTestClient.get()
            .uri("/private/v1/overview/allocation?groupBy=$query")
            .exchange()
            .expectStatus().isOk()
            .returnResult<OverviewAllocationResponse>()
            .responseBody!!

    private fun findStrategies(): List<StrategyResponse> =
        restTestClient.get()
            .uri("/private/v1/strategies")
            .exchange()
            .expectStatus().isOk()
            .returnResult<List<StrategyResponse>>()
            .responseBody!!

    private fun createStrategy(name: String): StrategyResponse =
        restTestClient.post()
            .uri("/private/v1/strategies")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StrategyResponse>()
            .responseBody!!

    private fun createStock(
        walletId: UUID,
        ticker: String,
        currentPrice: String,
        quantity: Int,
        strategyClassId: UUID?,
        segmentId: UUID?,
    ): StockHoldingResponse {
        val strategyClass = strategyClassId?.let { classId -> ""","strategyClassId":"$classId"""" } ?: ""
        val segment = segmentId?.let { id -> ""","segmentId":"$id"""" } ?: ""
        return restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"typeId":"$stockTypeId","ticker":"$ticker","currentPrice":$currentPrice$strategyClass$segment,
                   "lot":{"lotDate":"2025-01-10","quantity":$quantity,"price":8.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
    }

    private fun freeze(holdingUri: String) {
        restTestClient.patch()
            .uri(holdingUri)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"frozen":true}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun createWallet(name: String, kind: String, currency: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"$kind","currency":"$currency"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

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
}
