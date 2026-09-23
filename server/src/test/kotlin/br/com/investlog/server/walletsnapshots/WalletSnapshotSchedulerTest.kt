package br.com.investlog.server.walletsnapshots

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.jooq.finances.tables.references.WALLET_DAILY_SNAPSHOTS
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import br.com.investlog.server.walletsnapshots.scheduler.WalletSnapshotScheduler
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletSnapshotSchedulerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var walletSnapshotScheduler: WalletSnapshotScheduler

    @Autowired
    lateinit var dsl: DSLContext

    @BeforeAll
    fun setup() {
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Scheduled Wallet","kind":"stocks","currency":"BRL"}""")
            .exchange()
            .returnResult<WalletResponse>()
            .responseBody!!
    }

    private fun toggleSnapshotJob(enabled: Boolean) {
        restTestClient.patch()
            .uri("/private/v1/configurations/wallet_snapshot_enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"value":"$enabled"}""")
            .exchange()
            .expectStatus().isOk()
    }

    private fun countSnapshots(): Int = dsl.fetchCount(dsl.selectFrom(WALLET_DAILY_SNAPSHOTS))

    @Test
    @Order(1)
    fun `scheduler skips the capture run when wallet_snapshot_enabled is false`() {

        toggleSnapshotJob(false)

        walletSnapshotScheduler.captureSnapshots()

        assertEquals(0, countSnapshots())
    }

    @Test
    @Order(2)
    fun `scheduler captures snapshots when wallet_snapshot_enabled is true`() {

        toggleSnapshotJob(true)

        walletSnapshotScheduler.captureSnapshots()

        assertEquals(1, countSnapshots())
    }
}
