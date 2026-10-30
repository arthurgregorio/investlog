package br.com.investlog.server.strategies

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.strategies.rest.payloads.ClassHoldingResponse
import br.com.investlog.server.strategies.rest.payloads.StrategyResponse
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import dev.samstevens.totp.code.DefaultCodeGenerator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StrategyControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    private lateinit var stockWalletId: UUID
    private lateinit var cryptoWalletId: UUID
    private lateinit var fundWalletId: UUID
    private lateinit var stockTypeId: UUID
    private lateinit var fundTypeId: UUID

    private lateinit var longTermId: UUID
    private lateinit var brazilClassId: UUID
    private lateinit var abroadClassId: UUID
    private lateinit var petr4: StockHoldingResponse
    private lateinit var vale3: StockHoldingResponse
    private lateinit var itub4: StockHoldingResponse

    @BeforeAll
    fun setup() {
        stockWalletId = createWallet("Ações Estratégia", "stocks")
        cryptoWalletId = createWallet("Cripto Estratégia", "crypto")
        fundWalletId = createWallet("Fundos Estratégia", "funds")
        stockTypeId = createType("Ação Estratégia", "STOCKS")
        fundTypeId = createType("Fundo Estratégia", "FUNDS")
    }

    @Test
    @Order(1)
    fun `every user starts with Sem estrategia and its Nao definida class`() {
        val strategies = findStrategies()

        val default = strategies.single()
        assertEquals("Sem estratégia", default.name)
        assertTrue(default.default)
        assertEquals(listOf("Não definida"), default.classes.map { strategyClass -> strategyClass.name })
        assertNull(default.classes.single().targetPercentage)
        assertTrue(default.classes.single().default)
    }

    @Test
    @Order(2)
    fun `a new holding lands in Nao definida`() {
        petr4 = createStock("PETR4")

        assertEquals("Sem estratégia", petr4.strategyName)
        assertEquals("Não definida", petr4.strategyClassName)
        assertEquals(1, findStrategies().single { strategy -> strategy.default }.holdingCount)
    }

    @Test
    @Order(3)
    fun `creates a strategy with no classes and refuses a duplicate name`() {
        val created = restTestClient.post()
            .uri("/private/v1/strategies")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"  Longo prazo  "}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StrategyResponse>()
            .responseBody!!

        assertEquals("Longo prazo", created.name)
        assertEquals(false, created.default)
        assertTrue(created.classes.isEmpty())
        longTermId = created.id

        restTestClient.post()
            .uri("/private/v1/strategies")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Longo prazo"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)

        restTestClient.post()
            .uri("/private/v1/strategies")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":""}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(4)
    fun `the default strategy cannot be renamed, deleted or given classes`() {
        val defaultId = findStrategies().single { strategy -> strategy.default }.id

        restTestClient.patch()
            .uri("/private/v1/strategies/$defaultId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Outra"}""")
            .exchange()
            .expectStatus().isBadRequest()

        restTestClient.delete()
            .uri("/private/v1/strategies/$defaultId")
            .exchange()
            .expectStatus().isBadRequest()

        putClasses(defaultId, """[{"name":"Ações","targetPercentage":100}]""").expectStatus().isBadRequest()
    }

    @Test
    @Order(5)
    fun `refuses class targets that do not add up to 100, repeated names and unknown classes`() {
        putClasses(longTermId, """[{"name":"Ações Brasil","targetPercentage":55},{"name":"Exterior","targetPercentage":40}]""")
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("As metas das classes precisam somar 100% (soma: 95%)")

        putClasses(longTermId, """[{"name":"Exterior","targetPercentage":50},{"name":"exterior","targetPercentage":50}]""")
            .expectStatus().isBadRequest()

        putClasses(longTermId, """[{"id":"${UUID.randomUUID()}","name":"Exterior","targetPercentage":100}]""")
            .expectStatus().isNotFound()

        putClasses(longTermId, "[]").expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `saves classes whose targets add up to 100 in the order given`() {
        val strategy = putClasses(longTermId, """[{"name":"Ações Brasil","targetPercentage":60},{"name":"Exterior","targetPercentage":40}]""")
            .expectStatus().isOk()
            .returnResult<StrategyResponse>()
            .responseBody!!

        assertEquals(listOf("Ações Brasil", "Exterior"), strategy.classes.map { strategyClass -> strategyClass.name })
        assertEquals(listOf(0, 1), strategy.classes.map { strategyClass -> strategyClass.position })
        assertEquals(0, BigDecimal("60").compareTo(strategy.classes[0].targetPercentage))
        brazilClassId = strategy.classes[0].id
        abroadClassId = strategy.classes[1].id
    }

    @Test
    @Order(7)
    fun `a holding can be created straight into a class of any kind`() {
        vale3 = createStock("VALE3", brazilClassId)
        assertEquals("Longo prazo", vale3.strategyName)
        assertEquals("Ações Brasil", vale3.strategyClassName)

        val bitcoin = restTestClient.post()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"ticker":"BTC","strategyClassId":"$abroadClassId","lot":{"lotDate":"2024-01-01","quantity":0.5,"price":42000.00}}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<CryptoHoldingResponse>()
            .responseBody!!
        assertEquals("Exterior", bitcoin.strategyClassName)

        val fund = restTestClient.post()
            .uri("/private/v1/wallets/$fundWalletId/fund-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"typeId":"$fundTypeId","name":"Tesouro IPCA+","strategyClassId":"$abroadClassId","contribution":{"contributionDate":"2024-01-10","amount":5000.00}}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<FundHoldingResponse>()
            .responseBody!!
        assertEquals("Exterior", fund.strategyClassName)

        restTestClient.post()
            .uri("/private/v1/wallets/$stockWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(stockBody("WEGE3", UUID.randomUUID()))
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(8)
    fun `moves one holding through its own class endpoint, for every kind`() {
        petr4 = restTestClient.put()
            .uri("/private/v1/wallets/$stockWalletId/stock-holdings/${petr4.id}/class")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"strategyClassId":"$brazilClassId"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<StockHoldingResponse>()
            .responseBody!!
        assertEquals("Ações Brasil", petr4.strategyClassName)

        val bitcoinId = classHoldings(abroadClassId).single { holding -> holding.kind == "CRYPTO" }.id
        restTestClient.put()
            .uri("/private/v1/wallets/$cryptoWalletId/crypto-holdings/$bitcoinId/class")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"strategyClassId":"$brazilClassId"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.strategyClassName").isEqualTo("Ações Brasil")

        val fundId = classHoldings(abroadClassId).single { holding -> holding.kind == "FUNDS" }.id
        restTestClient.put()
            .uri("/private/v1/wallets/$fundWalletId/fund-holdings/$fundId/class")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"strategyClassId":"$brazilClassId"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.strategyClassName").isEqualTo("Ações Brasil")

        listOf("stock", "crypto", "fund").forEach { kind ->
            val walletId = mapOf("stock" to stockWalletId, "crypto" to cryptoWalletId, "fund" to fundWalletId).getValue(kind)
            restTestClient.put()
                .uri("/private/v1/wallets/$walletId/$kind-holdings/${UUID.randomUUID()}/class")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"strategyClassId":"$brazilClassId"}""")
                .exchange()
                .expectStatus().isNotFound()
        }
    }

    @Test
    @Order(9)
    fun `moves many holdings at once and lists a class's holdings`() {
        itub4 = createStock("ITUB4")
        val crypto = classHoldings(brazilClassId).single { holding -> holding.kind == "CRYPTO" }
        val fund = classHoldings(brazilClassId).single { holding -> holding.kind == "FUNDS" }

        restTestClient.post()
            .uri("/private/v1/strategy-classes/$abroadClassId/holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"holdings":[{"kind":"STOCKS","id":"${itub4.id}"},{"kind":"CRYPTO","id":"${crypto.id}"},{"kind":"FUNDS","id":"${fund.id}"}]}""")
            .exchange()
            .expectStatus().isNoContent()

        assertEquals(setOf("ITUB4", "BTC", "Tesouro IPCA+"), classHoldings(abroadClassId).map { holding -> holding.ticker ?: holding.name }.toSet())
        assertEquals(setOf("PETR4", "VALE3"), classHoldings(brazilClassId).map { holding -> holding.ticker }.toSet())

        restTestClient.post()
            .uri("/private/v1/strategy-classes/$abroadClassId/holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"holdings":[{"kind":"STOCKS","id":"${UUID.randomUUID()}"}]}""")
            .exchange()
            .expectStatus().isNotFound()

        restTestClient.post()
            .uri("/private/v1/strategy-classes/$abroadClassId/holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"holdings":[]}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(10)
    fun `typed holding targets leave the rest to the blanks`() {
        val holdings = putTargets(brazilClassId, """[{"kind":"STOCKS","holdingId":"${petr4.id}","targetPercentage":30}]""")
            .expectStatus().isOk()
            .returnResult<List<ClassHoldingResponse>>()
            .responseBody!!

        val petr4Target = holdings.single { holding -> holding.ticker == "PETR4" }
        val vale3Target = holdings.single { holding -> holding.ticker == "VALE3" }
        assertEquals(0, BigDecimal("30").compareTo(petr4Target.targetPercentage))
        assertNull(vale3Target.targetPercentage)
        assertEquals(0, BigDecimal("70").compareTo(vale3Target.effectiveTargetPercentage))

        val brazil = findStrategies().single { strategy -> strategy.id == longTermId }.classes.single { strategyClass -> strategyClass.id == brazilClassId }
        assertEquals(2, brazil.holdingCount)
        assertEquals(1, brazil.typedCount)
        assertEquals(0, BigDecimal("30").compareTo(brazil.typedPercentageSum))
        assertEquals(0, BigDecimal("70").compareTo(brazil.blankSharePercentage))
    }

    @Test
    @Order(11)
    fun `refuses holding targets over 100, a fully typed class off 100, Nao definida and holdings outside the class`() {
        putTargets(brazilClassId, """[{"kind":"STOCKS","holdingId":"${petr4.id}","targetPercentage":60},{"kind":"STOCKS","holdingId":"${vale3.id}","targetPercentage":60}]""")
            .expectStatus().isBadRequest()

        putTargets(brazilClassId, """[{"kind":"STOCKS","holdingId":"${petr4.id}","targetPercentage":30},{"kind":"STOCKS","holdingId":"${vale3.id}","targetPercentage":30}]""")
            .expectStatus().isBadRequest()

        putTargets(brazilClassId, """[{"kind":"STOCKS","holdingId":"${itub4.id}","targetPercentage":30}]""")
            .expectStatus().isNotFound()

        val undefinedClassId = findStrategies().single { strategy -> strategy.default }.classes.single().id
        putTargets(undefinedClassId, "[]").expectStatus().isBadRequest()

        putTargets(brazilClassId, """[{"kind":"STOCKS","holdingId":"${petr4.id}","targetPercentage":101}]""")
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(12)
    fun `the holdings list carries each holding's strategy and class`() {
        restTestClient.get()
            .uri("/private/v1/holdings?kind=STOCKS&size=50")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.ticker == 'VALE3')].strategyName").isEqualTo("Longo prazo")
            .jsonPath("$.content[?(@.ticker == 'VALE3')].strategyClassName").isEqualTo("Ações Brasil")
            .jsonPath("$.content[?(@.ticker == 'VALE3')].strategyClassId").isEqualTo(brazilClassId.toString())
    }

    @Test
    @Order(13)
    fun `removing a class sends its holdings back to Nao definida while renamed classes keep theirs`() {
        val strategy = putClasses(longTermId, """[{"id":"$brazilClassId","name":"Exterior","targetPercentage":100}]""")
            .expectStatus().isOk()
            .returnResult<StrategyResponse>()
            .responseBody!!

        assertEquals(listOf("Exterior"), strategy.classes.map { strategyClass -> strategyClass.name })
        assertEquals(brazilClassId, strategy.classes.single().id)
        assertEquals(2, strategy.classes.single().holdingCount)

        val undefined = findStrategies().single { strategy -> strategy.default }.classes.single()
        assertEquals(3, undefined.holdingCount)
        assertEquals(setOf("ITUB4", "BTC", "Tesouro IPCA+"), classHoldings(undefined.id).map { holding -> holding.ticker ?: holding.name }.toSet())
    }

    @Test
    @Order(14)
    fun `renames a strategy and deleting it returns its holdings to Nao definida without targets`() {
        restTestClient.patch()
            .uri("/private/v1/strategies/$longTermId")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Aposentadoria"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Aposentadoria")

        restTestClient.delete()
            .uri("/private/v1/strategies/$longTermId")
            .exchange()
            .expectStatus().isNoContent()

        val strategies = findStrategies()
        assertEquals(listOf("Sem estratégia"), strategies.map { strategy -> strategy.name })
        val undefinedHoldings = classHoldings(strategies.single().classes.single().id)
        assertEquals(5, undefinedHoldings.size)
        assertTrue(undefinedHoldings.all { holding -> holding.targetPercentage == null })

        restTestClient.delete()
            .uri("/private/v1/strategies/$longTermId")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(15)
    fun `another user gets their own default strategy and cannot reach this user's classes`() {
        val cookie = registerApproveAndLogin("strategies-reader@example.com", "Senha123")
        val undefinedClassId = findStrategies().single().classes.single().id

        val otherStrategies = restTestClient.get()
            .uri("/private/v1/strategies")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isOk()
            .returnResult<List<StrategyResponse>>()
            .responseBody!!
        assertEquals(listOf("Sem estratégia"), otherStrategies.map { strategy -> strategy.name })
        assertTrue(otherStrategies.single().classes.single().id != undefinedClassId)

        restTestClient.get()
            .uri("/private/v1/strategy-classes/$undefinedClassId/holdings")
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isNotFound()

        restTestClient.post()
            .uri("/private/v1/strategy-classes/${otherStrategies.single().classes.single().id}/holdings")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"holdings":[{"kind":"STOCKS","id":"${petr4.id}"}]}""")
            .exchange()
            .expectStatus().isNotFound()
    }

    private fun findStrategies(): List<StrategyResponse> =
        restTestClient.get()
            .uri("/private/v1/strategies")
            .exchange()
            .expectStatus().isOk()
            .returnResult<List<StrategyResponse>>()
            .responseBody!!

    private fun classHoldings(classId: UUID): List<ClassHoldingResponse> =
        restTestClient.get()
            .uri("/private/v1/strategy-classes/$classId/holdings")
            .exchange()
            .expectStatus().isOk()
            .returnResult<List<ClassHoldingResponse>>()
            .responseBody!!

    private fun putClasses(strategyId: UUID, classes: String) =
        restTestClient.put()
            .uri("/private/v1/strategies/$strategyId/classes")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"classes":$classes}""")
            .exchange()

    private fun putTargets(classId: UUID, targets: String) =
        restTestClient.put()
            .uri("/private/v1/strategy-classes/$classId/targets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"targets":$targets}""")
            .exchange()

    private fun stockBody(ticker: String, strategyClassId: UUID?): String {
        val strategyClass = strategyClassId?.let { classId -> ""","strategyClassId":"$classId"""" } ?: ""
        return """{"typeId":"$stockTypeId","ticker":"$ticker","currentPrice":38.50$strategyClass,"lot":{"lotDate":"2024-01-15","quantity":100,"price":35.00}}"""
    }

    private fun createStock(ticker: String, strategyClassId: UUID? = null): StockHoldingResponse =
        restTestClient.post()
            .uri("/private/v1/wallets/$stockWalletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(stockBody(ticker, strategyClassId))
            .exchange()
            .expectStatus().isCreated()
            .returnResult<StockHoldingResponse>()
            .responseBody!!

    private fun createWallet(name: String, kind: String): UUID =
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kind":"$kind","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
            .id

    private fun createType(name: String, kind: String): UUID =
        restTestClient.post()
            .uri("/private/v1/holding-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","kinds":["$kind"]}""")
            .exchange()
            .returnResult<TypeResponse>()
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
