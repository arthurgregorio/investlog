package br.com.investlog.server.walletmoves.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Table
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class MovableHoldingRepository(private val dsl: DSLContext) {

    private class HoldingColumns(
        val table: Table<*>,
        val id: Field<Long?>,
        val externalId: Field<UUID?>,
        val walletId: Field<Long?>,
        val status: Field<HoldingStatus?>,
        val updatedAt: Field<OffsetDateTime?>,
    )

    private class LotColumns(
        val table: Table<*>,
        val id: Field<Long?>,
        val holdingId: Field<Long?>,
        val lotDate: Field<LocalDate?>,
        val quantity: Field<BigDecimal?>,
        val price: Field<BigDecimal?>,
    )

    fun findHolding(walletInternalId: Long, kind: WalletKind, externalId: UUID): MovableHolding? {
        val overview = HOLDINGS_OVERVIEW.`as`("overview")

        val position = dsl.select(
            overview.HOLDING_ID,
            overview.STATUS,
            overview.NAME,
            overview.TICKER,
            overview.CURRENT_PRICE,
            overview.CURRENT_VALUE,
            overview.QUANTITY,
            overview.COST_BASIS,
        )
            .from(overview)
            .where(overview.WALLET_ID.eq(walletInternalId))
            .and(overview.KIND.eq(kind))
            .and(overview.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        val holdingId = position.get(overview.HOLDING_ID)!!

        return MovableHolding(
            id = holdingId,
            externalId = externalId,
            kind = kind,
            status = position.get(overview.STATUS)!!,
            name = position.get(overview.NAME)!!,
            ticker = position.get(overview.TICKER),
            typeId = findTypeId(kind, holdingId),
            currentPrice = position.get(overview.CURRENT_PRICE),
            currentValue = position.get(overview.CURRENT_VALUE),
            quantity = position.get(overview.QUANTITY),
            costBasis = position.get(overview.COST_BASIS) ?: BigDecimal.ZERO,
            hasResults = dsl.fetchExists(
                dsl.selectOne().from(RESULTS).where(resultHoldingColumnOf(kind).eq(holdingId))
            ),
        )
    }

    fun findMatchingActiveHolding(walletInternalId: Long, holding: MovableHolding): HoldingReference? {
        val columns = holdingColumnsOf(holding.kind)

        val sameAsset = when (holding.kind) {
            WalletKind.STOCKS -> DSL.upper(STOCK_HOLDINGS.TICKER).eq(holding.ticker!!.uppercase())
            WalletKind.CRYPTO -> DSL.upper(CRYPTO_HOLDINGS.TICKER).eq(holding.ticker!!.uppercase())
            WalletKind.FUNDS -> FUND_HOLDINGS.NAME.eq(holding.name).and(FUND_HOLDINGS.FUND_TYPE_ID.eq(holding.typeId))
        }

        return dsl.select(columns.id, columns.externalId)
            .from(columns.table)
            .where(columns.walletId.eq(walletInternalId))
            .and(columns.status.eq(HoldingStatus.ACTIVE))
            .and(sameAsset)
            .orderBy(columns.id)
            .limit(1)
            .fetchOne { record -> HoldingReference(record.get(columns.id)!!, record.get(columns.externalId)!!) }
    }

    fun reassign(holding: MovableHolding, walletInternalId: Long) {
        val columns = holdingColumnsOf(holding.kind)

        dsl.update(columns.table)
            .set(columns.walletId, walletInternalId)
            .set(columns.updatedAt, OffsetDateTime.now())
            .where(columns.id.eq(holding.id))
            .execute()
    }

    fun createCopy(holding: MovableHolding, walletInternalId: Long): HoldingReference = when (holding.kind) {
        WalletKind.STOCKS -> dsl.insertInto(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.WALLET_ID, walletInternalId)
            .set(STOCK_HOLDINGS.STOCK_TYPE_ID, holding.typeId)
            .set(STOCK_HOLDINGS.TICKER, holding.ticker)
            .set(STOCK_HOLDINGS.NAME, holding.name)
            .set(STOCK_HOLDINGS.CURRENT_PRICE, holding.currentPrice)
            .returning(STOCK_HOLDINGS.ID, STOCK_HOLDINGS.EXTERNAL_ID)
            .fetchSingle { record -> HoldingReference(record.id!!, record.externalId!!) }

        WalletKind.CRYPTO -> dsl.insertInto(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.WALLET_ID, walletInternalId)
            .set(CRYPTO_HOLDINGS.TICKER, holding.ticker)
            .set(CRYPTO_HOLDINGS.NAME, holding.name)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, holding.currentPrice)
            .returning(CRYPTO_HOLDINGS.ID, CRYPTO_HOLDINGS.EXTERNAL_ID)
            .fetchSingle { record -> HoldingReference(record.id!!, record.externalId!!) }

        WalletKind.FUNDS -> throw IllegalArgumentException("Fund holdings only move whole")
    }

    fun reattachChildren(holding: MovableHolding, target: HoldingReference) {
        when (holding.kind) {
            WalletKind.FUNDS -> dsl.update(FUND_CONTRIBUTIONS)
                .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, target.id)
                .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holding.id))
                .execute()

            else -> {
                val lots = lotColumnsOf(holding.kind)
                dsl.update(lots.table)
                    .set(lots.holdingId, target.id)
                    .where(lots.holdingId.eq(holding.id))
                    .execute()
            }
        }
    }

    fun delete(holding: MovableHolding) {
        val columns = holdingColumnsOf(holding.kind)

        dsl.deleteFrom(columns.table)
            .where(columns.id.eq(holding.id))
            .execute()
    }

    fun markCompleted(holding: MovableHolding) {
        val columns = holdingColumnsOf(holding.kind)

        dsl.update(columns.table)
            .set(columns.status, HoldingStatus.COMPLETED)
            .set(columns.updatedAt, OffsetDateTime.now())
            .where(columns.id.eq(holding.id))
            .execute()
    }

    fun findLots(holding: MovableHolding): List<HoldingLot> {
        val lots = lotColumnsOf(holding.kind)

        return dsl.select(lots.id, lots.quantity, lots.price)
            .from(lots.table)
            .where(lots.holdingId.eq(holding.id))
            .orderBy(lots.lotDate, lots.id)
            .fetch { record ->
                HoldingLot(
                    id = record.get(lots.id)!!,
                    quantity = record.get(lots.quantity)!!,
                    price = record.get(lots.price)!!,
                )
            }
    }

    fun updateLot(kind: WalletKind, lotId: Long, quantity: BigDecimal, price: BigDecimal) {
        val lots = lotColumnsOf(kind)

        dsl.update(lots.table)
            .set(lots.quantity, quantity)
            .set(lots.price, price)
            .where(lots.id.eq(lotId))
            .execute()
    }

    fun insertLot(kind: WalletKind, holding: HoldingReference, lotDate: LocalDate, quantity: BigDecimal, price: BigDecimal) {
        val lots = lotColumnsOf(kind)

        dsl.insertInto(lots.table)
            .set(lots.holdingId, holding.id)
            .set(lots.lotDate, lotDate)
            .set(lots.quantity, quantity)
            .set(lots.price, price)
            .execute()
    }

    fun findContributions(holding: MovableHolding): List<HoldingContribution> =
        dsl.select(FUND_CONTRIBUTIONS.ID, FUND_CONTRIBUTIONS.AMOUNT)
            .from(FUND_CONTRIBUTIONS)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holding.id))
            .orderBy(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, FUND_CONTRIBUTIONS.ID)
            .fetch { record ->
                HoldingContribution(
                    id = record.get(FUND_CONTRIBUTIONS.ID)!!,
                    amount = record.get(FUND_CONTRIBUTIONS.AMOUNT)!!,
                )
            }

    fun updateContributionAmount(contributionId: Long, amount: BigDecimal) {
        dsl.update(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.AMOUNT, amount)
            .where(FUND_CONTRIBUTIONS.ID.eq(contributionId))
            .execute()
    }

    fun deleteContributions(holding: MovableHolding) {
        dsl.deleteFrom(FUND_CONTRIBUTIONS)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holding.id))
            .execute()
    }

    fun insertContribution(holding: HoldingReference, contributionDate: LocalDate, amount: BigDecimal) {
        dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holding.id)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, amount)
            .execute()
    }

    fun transferFundCurrentValue(holding: MovableHolding, target: HoldingReference) {
        val currentValue = holding.currentValue ?: return

        dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.CURRENT_VALUE, DSL.coalesce(FUND_HOLDINGS.CURRENT_VALUE, BigDecimal.ZERO).plus(currentValue))
            .set(FUND_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(FUND_HOLDINGS.ID.eq(target.id))
            .execute()

        dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.CURRENT_VALUE, BigDecimal.ZERO)
            .set(FUND_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(FUND_HOLDINGS.ID.eq(holding.id))
            .execute()
    }

    private fun findTypeId(kind: WalletKind, holdingId: Long): Long? = when (kind) {
        WalletKind.STOCKS -> dsl.select(STOCK_HOLDINGS.STOCK_TYPE_ID)
            .from(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.ID.eq(holdingId))
            .fetchOne(STOCK_HOLDINGS.STOCK_TYPE_ID)

        WalletKind.FUNDS -> dsl.select(FUND_HOLDINGS.FUND_TYPE_ID)
            .from(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.ID.eq(holdingId))
            .fetchOne(FUND_HOLDINGS.FUND_TYPE_ID)

        WalletKind.CRYPTO -> null
    }

    private fun holdingColumnsOf(kind: WalletKind) = when (kind) {
        WalletKind.STOCKS -> HoldingColumns(
            STOCK_HOLDINGS, STOCK_HOLDINGS.ID, STOCK_HOLDINGS.EXTERNAL_ID, STOCK_HOLDINGS.WALLET_ID,
            STOCK_HOLDINGS.STATUS, STOCK_HOLDINGS.UPDATED_AT,
        )

        WalletKind.CRYPTO -> HoldingColumns(
            CRYPTO_HOLDINGS, CRYPTO_HOLDINGS.ID, CRYPTO_HOLDINGS.EXTERNAL_ID, CRYPTO_HOLDINGS.WALLET_ID,
            CRYPTO_HOLDINGS.STATUS, CRYPTO_HOLDINGS.UPDATED_AT,
        )

        WalletKind.FUNDS -> HoldingColumns(
            FUND_HOLDINGS, FUND_HOLDINGS.ID, FUND_HOLDINGS.EXTERNAL_ID, FUND_HOLDINGS.WALLET_ID,
            FUND_HOLDINGS.STATUS, FUND_HOLDINGS.UPDATED_AT,
        )
    }

    private fun lotColumnsOf(kind: WalletKind) = when (kind) {
        WalletKind.STOCKS -> LotColumns(
            STOCK_LOTS, STOCK_LOTS.ID, STOCK_LOTS.STOCK_HOLDING_ID, STOCK_LOTS.LOT_DATE,
            STOCK_LOTS.QUANTITY, STOCK_LOTS.PRICE,
        )

        WalletKind.CRYPTO -> LotColumns(
            CRYPTO_LOTS, CRYPTO_LOTS.ID, CRYPTO_LOTS.CRYPTO_HOLDING_ID, CRYPTO_LOTS.LOT_DATE,
            CRYPTO_LOTS.QUANTITY, CRYPTO_LOTS.PRICE,
        )

        WalletKind.FUNDS -> throw IllegalArgumentException("Fund holdings have contributions, not lots")
    }

    private fun resultHoldingColumnOf(kind: WalletKind) = when (kind) {
        WalletKind.STOCKS -> RESULTS.STOCK_HOLDING_ID
        WalletKind.CRYPTO -> RESULTS.CRYPTO_HOLDING_ID
        WalletKind.FUNDS -> RESULTS.FUND_HOLDING_ID
    }
}
