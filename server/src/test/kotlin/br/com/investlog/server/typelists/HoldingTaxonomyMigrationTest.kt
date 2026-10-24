package br.com.investlog.server.typelists

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_REPORT_ROWS
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_TYPES
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoldingTaxonomyMigrationTest : BaseIntegrationTest() {

    companion object {
        private const val CHANGELOG = "db/changelog/db.changelog-master.xml"
        private const val TAXONOMY_CHANGESETS = 5
        private const val WALLET_PREFIX = "Migração"
    }

    @Autowired
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var dsl: DSLContext

    private data class OverviewRow(val externalId: UUID, val kind: WalletKind, val typeLabel: String?, val segmentLabel: String?)

    @Test
    @Order(1)
    fun `rolls back to the kind-specific type lists, then migrates them into one list without regrouping any holding`() {
        runLiquibase { liquibase -> liquibase.rollback(TAXONOMY_CHANGESETS, "") }

        val stockTypeExternalId = UUID.randomUUID()
        val fundTypeExternalId = UUID.randomUUID()
        val plainFundTypeExternalId = UUID.randomUUID()
        seedLegacyPortfolio(stockTypeExternalId, fundTypeExternalId, plainFundTypeExternalId)

        val overviewBefore = overviewRows()
        val reportBefore = reportRows()

        runLiquibase { liquibase -> liquibase.update("") }

        assertEquals("Colisão", typeNameOf(stockTypeExternalId))
        assertEquals("Colisão (fundo)", typeNameOf(fundTypeExternalId))
        assertEquals("Fundo Exclusivo", typeNameOf(plainFundTypeExternalId))
        assertEquals(listOf("STOCKS"), kindsOf(stockTypeExternalId))
        assertEquals(listOf("FUNDS"), kindsOf(fundTypeExternalId))

        val expectedOverview = overviewBefore.map { row ->
            when {
                row.kind == WalletKind.CRYPTO -> row.copy(typeLabel = "Cripto")
                row.kind == WalletKind.FUNDS && row.typeLabel == "Colisão" -> row.copy(typeLabel = "Colisão (fundo)")
                else -> row
            }
        }
        assertEquals(expectedOverview.toSet(), overviewRows().toSet())

        val expectedReport = reportBefore.mapKeys { (kindAndLabel, _) ->
                val (kind, label) = kindAndLabel
                when {
                    kind == WalletKind.CRYPTO -> kind to "Cripto"
                    kind == WalletKind.FUNDS && label == "Colisão" -> kind to "Colisão (fundo)"
                    else -> kindAndLabel
                }
            }
        assertEquals(expectedReport, reportRows())

        assertEquals(setOf("Bancos"), overviewRows().filter { row -> row.kind == WalletKind.STOCKS }.map { row -> row.segmentLabel }.toSet())
    }

    private fun seedLegacyPortfolio(stockTypeExternalId: UUID, fundTypeExternalId: UUID, plainFundTypeExternalId: UUID) {
        dsl.execute("INSERT INTO finances.stock_types (external_id, name) VALUES ('$stockTypeExternalId', 'Colisão')")
        dsl.execute("INSERT INTO finances.fund_types (external_id, name) VALUES ('$fundTypeExternalId', 'Colisão')")
        dsl.execute("INSERT INTO finances.fund_types (external_id, name) VALUES ('$plainFundTypeExternalId', 'Fundo Exclusivo')")
        dsl.execute("INSERT INTO finances.stock_segments (name) VALUES ('Bancos')")

        dsl.execute(
            """
            INSERT INTO finances.wallets (user_id, name, kind, currency)
            SELECT (SELECT MIN(id) FROM system.users), wallet.name, wallet.kind::finances.wallet_kind, 'BRL'
            FROM (VALUES ('$WALLET_PREFIX Ações', 'STOCKS'), ('$WALLET_PREFIX Fundos', 'FUNDS'), ('$WALLET_PREFIX Cripto', 'CRYPTO'))
                AS wallet(name, kind)
            """.trimIndent()
        )

        dsl.execute(
            """
            INSERT INTO finances.stock_holdings (wallet_id, stock_type_id, stock_segment_id, ticker, name, current_price)
            SELECT wallets.id, stock_types.id, stock_segments.id, holding.ticker, holding.ticker, 10
            FROM (VALUES ('ITUB4'), ('BBDC4')) AS holding(ticker)
            JOIN finances.wallets ON wallets.name = '$WALLET_PREFIX Ações'
            JOIN finances.stock_types ON stock_types.name = 'Colisão'
            JOIN finances.stock_segments ON stock_segments.name = 'Bancos'
            """.trimIndent()
        )
        dsl.execute(
            """
            INSERT INTO finances.stock_lots (stock_holding_id, lot_date, quantity, price)
            SELECT stock_holdings.id, DATE '2024-01-15', 10, 8
            FROM finances.stock_holdings
            JOIN finances.wallets ON wallets.id = stock_holdings.wallet_id AND wallets.name = '$WALLET_PREFIX Ações'
            """.trimIndent()
        )

        dsl.execute(
            """
            INSERT INTO finances.fund_holdings (wallet_id, fund_type_id, name, current_value)
            SELECT wallets.id, fund_types.id, holding.name, 1000
            FROM (VALUES ('Fundo Colidido', 'Colisão'), ('Fundo Próprio', 'Fundo Exclusivo')) AS holding(name, type_name)
            JOIN finances.wallets ON wallets.name = '$WALLET_PREFIX Fundos'
            JOIN finances.fund_types ON fund_types.name = holding.type_name
            """.trimIndent()
        )
        dsl.execute(
            """
            INSERT INTO finances.fund_contributions (fund_holding_id, contribution_date, amount)
            SELECT fund_holdings.id, DATE '2024-01-10', 900
            FROM finances.fund_holdings
            JOIN finances.wallets ON wallets.id = fund_holdings.wallet_id AND wallets.name = '$WALLET_PREFIX Fundos'
            """.trimIndent()
        )

        dsl.execute(
            """
            INSERT INTO finances.crypto_holdings (wallet_id, ticker, name, current_price)
            SELECT wallets.id, 'BTC', 'Bitcoin', 100
            FROM finances.wallets
            WHERE wallets.name = '$WALLET_PREFIX Cripto'
            """.trimIndent()
        )
        dsl.execute(
            """
            INSERT INTO finances.crypto_lots (crypto_holding_id, lot_date, quantity, price)
            SELECT crypto_holdings.id, DATE '2024-01-15', 1, 90
            FROM finances.crypto_holdings
            JOIN finances.wallets ON wallets.id = crypto_holdings.wallet_id AND wallets.name = '$WALLET_PREFIX Cripto'
            """.trimIndent()
        )
    }

    private fun overviewRows(): List<OverviewRow> =
        dsl.select(HOLDINGS_OVERVIEW.EXTERNAL_ID, HOLDINGS_OVERVIEW.KIND, HOLDINGS_OVERVIEW.TYPE_LABEL, HOLDINGS_OVERVIEW.SEGMENT_LABEL)
            .from(HOLDINGS_OVERVIEW)
            .join(WALLETS).on(WALLETS.ID.eq(HOLDINGS_OVERVIEW.WALLET_ID))
            .where(WALLETS.NAME.startsWith(WALLET_PREFIX))
            .fetch { record ->
                OverviewRow(
                    externalId = record.get(HOLDINGS_OVERVIEW.EXTERNAL_ID)!!,
                    kind = record.get(HOLDINGS_OVERVIEW.KIND)!!,
                    typeLabel = record.get(HOLDINGS_OVERVIEW.TYPE_LABEL),
                    segmentLabel = record.get(HOLDINGS_OVERVIEW.SEGMENT_LABEL),
                )
            }

    private fun reportRows(): Map<Pair<WalletKind, String?>, Set<String>> =
        dsl.select(HOLDINGS_REPORT_ROWS.KIND, HOLDINGS_REPORT_ROWS.TYPE_LABEL, HOLDINGS_REPORT_ROWS.NAME)
            .from(HOLDINGS_REPORT_ROWS)
            .where(HOLDINGS_REPORT_ROWS.WALLET_NAME.startsWith(WALLET_PREFIX))
            .fetch()
            .groupBy(
                { record -> record.get(HOLDINGS_REPORT_ROWS.KIND)!! to record.get(HOLDINGS_REPORT_ROWS.TYPE_LABEL) },
                { record -> record.get(HOLDINGS_REPORT_ROWS.NAME)!! },
            )
            .mapValues { (_, names) -> names.toSet() }

    private fun typeNameOf(externalId: UUID): String =
        dsl.select(HOLDING_TYPES.NAME)
            .from(HOLDING_TYPES)
            .where(HOLDING_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchSingle(HOLDING_TYPES.NAME)!!

    private fun kindsOf(externalId: UUID): List<String> =
        dsl.select(HOLDING_TYPES.KINDS)
            .from(HOLDING_TYPES)
            .where(HOLDING_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchSingle(HOLDING_TYPES.KINDS)!!
            .map { kind -> kind!!.literal }

    private fun runLiquibase(action: (Liquibase) -> Unit) {
        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            database.databaseChangeLogTableName = "database_changelog"
            database.databaseChangeLogLockTableName = "database_changelog_lock"
            action(Liquibase(CHANGELOG, ClassLoaderResourceAccessor(), database))
        }
    }
}
