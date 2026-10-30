package br.com.investlog.server.stockholdings.repositories

import br.com.investlog.server.jooq.finances.tables.references.HOLDING_TYPES
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.SEGMENTS
import br.com.investlog.server.jooq.finances.tables.references.STRATEGIES
import br.com.investlog.server.jooq.finances.tables.references.STRATEGY_CLASSES
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.results.rest.payloads.WithdrawalResponse
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class StockHoldingRepository(
    private val dsl: DSLContext
) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<StockHoldingResponse> {
        val wallets = WALLETS.`as`("wallets")
        val holdingTypes = HOLDING_TYPES.`as`("holding_types")
        val segments = SEGMENTS.`as`("segments")
        val stockHoldings = STOCK_HOLDINGS.`as`("stock_holdings")
        val strategyClasses = STRATEGY_CLASSES.`as`("strategy_classes")
        val strategies = STRATEGIES.`as`("strategies")

        val lotsField = DSL.multiset(
            DSL.selectFrom(STOCK_LOTS)
                .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(stockHoldings.ID))
                .orderBy(STOCK_LOTS.LOT_DATE)
        ).`as`("lots").convertFrom { r ->
            r.map { rec ->
                LotResponse(
                    id = rec.get(STOCK_LOTS.EXTERNAL_ID)!!,
                    lotDate = rec.get(STOCK_LOTS.LOT_DATE)!!,
                    quantity = rec.get(STOCK_LOTS.QUANTITY)!!,
                    price = rec.get(STOCK_LOTS.PRICE)!!,
                )
            }
        }

        val withdrawalsField = DSL.multiset(
            DSL.selectFrom(RESULTS)
                .where(RESULTS.STOCK_HOLDING_ID.eq(stockHoldings.ID))
                .orderBy(RESULTS.RESULT_DATE)
        ).`as`("withdrawals").convertFrom { r ->
            r.map { rec ->
                WithdrawalResponse(
                    id = rec.get(RESULTS.EXTERNAL_ID)!!,
                    resultType = rec.get(RESULTS.RESULT_TYPE)!!.literal,
                    resultDate = rec.get(RESULTS.RESULT_DATE)!!,
                    quantity = rec.get(RESULTS.QUANTITY),
                    grossAmount = rec.get(RESULTS.GROSS_AMOUNT)!!,
                    fees = rec.get(RESULTS.FEES)!!,
                    taxes = rec.get(RESULTS.TAXES)!!,
                    costBasis = rec.get(RESULTS.COST_BASIS)!!,
                    netAmount = rec.get(RESULTS.NET_AMOUNT)!!,
                    profit = rec.get(RESULTS.PROFIT)!!,
                )
            }
        }

        val content = dsl.select(
            stockHoldings.EXTERNAL_ID,
            wallets.EXTERNAL_ID,
            holdingTypes.EXTERNAL_ID,
            holdingTypes.NAME,
            segments.EXTERNAL_ID,
            segments.NAME,
            stockHoldings.TICKER,
            stockHoldings.NAME,
            stockHoldings.CURRENT_PRICE,
            stockHoldings.FROZEN,
            strategies.EXTERNAL_ID,
            strategies.NAME,
            strategyClasses.EXTERNAL_ID,
            strategyClasses.NAME,
            lotsField,
            withdrawalsField
        )
            .from(stockHoldings)
            .join(wallets).on(wallets.ID.eq(stockHoldings.WALLET_ID))
            .join(holdingTypes).on(holdingTypes.ID.eq(stockHoldings.HOLDING_TYPE_ID))
            .leftJoin(segments).on(segments.ID.eq(stockHoldings.SEGMENT_ID))
            .join(strategyClasses).on(strategyClasses.ID.eq(stockHoldings.STRATEGY_CLASS_ID))
            .join(strategies).on(strategies.ID.eq(strategyClasses.STRATEGY_ID))
            .where(stockHoldings.WALLET_ID.eq(walletInternalId))
            .orderBy(stockHoldings.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { rec ->
                StockHoldingResponse(
                    id = rec.get(stockHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    typeId = rec.get(holdingTypes.EXTERNAL_ID)!!,
                    typeName = rec.get(holdingTypes.NAME)!!,
                    segmentId = rec.get(segments.EXTERNAL_ID),
                    segmentName = rec.get(segments.NAME),
                    ticker = rec.get(stockHoldings.TICKER)!!,
                    name = rec.get(stockHoldings.NAME)!!,
                    currentPrice = rec.get(stockHoldings.CURRENT_PRICE),
                    frozen = rec.get(stockHoldings.FROZEN)!!,
                    strategyId = rec.get(strategies.EXTERNAL_ID)!!,
                    strategyName = rec.get(strategies.NAME)!!,
                    strategyClassId = rec.get(strategyClasses.EXTERNAL_ID)!!,
                    strategyClassName = rec.get(strategyClasses.NAME)!!,
                    lots = rec.get(lotsField),
                    withdrawals = rec.get(withdrawalsField),
                )
            }

        val total = dsl.fetchCount(
            dsl.selectFrom(STOCK_HOLDINGS).where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(
        walletInternalId: Long,
        typeInternalId: Long,
        segmentInternalId: Long?,
        strategyClassInternalId: Long?,
        ticker: String,
        name: String,
        currentPrice: BigDecimal?,
        lot: LotCreateRequest,
    ): StockHoldingResponse {
        val holdingId = dsl.insertInto(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.WALLET_ID, walletInternalId)
            .set(STOCK_HOLDINGS.HOLDING_TYPE_ID, typeInternalId)
            .set(STOCK_HOLDINGS.SEGMENT_ID, segmentInternalId)
            .set(STOCK_HOLDINGS.STRATEGY_CLASS_ID, strategyClassInternalId)
            .set(STOCK_HOLDINGS.TICKER, ticker.uppercase())
            .set(STOCK_HOLDINGS.NAME, name)
            .set(STOCK_HOLDINGS.CURRENT_PRICE, currentPrice)
            .returning(STOCK_HOLDINGS.ID)
            .fetchSingle(STOCK_HOLDINGS.ID)!!

        dsl.insertInto(STOCK_LOTS)
            .set(STOCK_LOTS.STOCK_HOLDING_ID, holdingId)
            .set(STOCK_LOTS.LOT_DATE, lot.lotDate)
            .set(STOCK_LOTS.QUANTITY, lot.quantity)
            .set(STOCK_LOTS.PRICE, lot.price)
            .execute()

        return findByInternalId(holdingId, walletInternalId)!!
    }

    fun findByExternalId(walletInternalId: Long, externalId: UUID): StockHoldingResponse? {
        val internalId = findInternalId(walletInternalId, externalId) ?: return null
        return findByInternalId(internalId, walletInternalId)
    }

    fun findInternalId(walletInternalId: Long, externalId: UUID): Long? =
        dsl.select(STOCK_HOLDINGS.ID)
            .from(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne(STOCK_HOLDINGS.ID)

    fun isFrozen(internalId: Long): Boolean =
        dsl.select(STOCK_HOLDINGS.FROZEN)
            .from(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.ID.eq(internalId))
            .fetchSingle(STOCK_HOLDINGS.FROZEN)!!

    fun updateSegment(walletInternalId: Long, externalId: UUID, segmentInternalId: Long?): StockHoldingResponse? {
        val holdingId = dsl.update(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.SEGMENT_ID, segmentInternalId)
            .set(STOCK_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .returning(STOCK_HOLDINGS.ID)
            .fetchOne(STOCK_HOLDINGS.ID) ?: return null

        return findByInternalId(holdingId, walletInternalId)
    }

    fun update(
        walletInternalId: Long,
        externalId: UUID,
        typeInternalId: Long?,
        ticker: String?,
        name: String?,
        currentPrice: BigDecimal?,
        frozen: Boolean?,
    ): StockHoldingResponse? {
        val existing = dsl.selectFrom(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        dsl.update(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.HOLDING_TYPE_ID, typeInternalId ?: existing.holdingTypeId!!)
            .set(STOCK_HOLDINGS.TICKER, (ticker ?: existing.ticker!!).uppercase())
            .set(STOCK_HOLDINGS.NAME, name ?: existing.name!!)
            .set(STOCK_HOLDINGS.CURRENT_PRICE, currentPrice ?: existing.currentPrice)
            .set(STOCK_HOLDINGS.FROZEN, frozen ?: existing.frozen!!)
            .set(STOCK_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(STOCK_HOLDINGS.ID.eq(existing.id))
            .execute()

        return findByInternalId(existing.id!!, walletInternalId)
    }

    fun deleteByExternalId(walletInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_HOLDINGS)
            .where(STOCK_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(STOCK_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun findByInternalId(internalId: Long, walletInternalId: Long): StockHoldingResponse? {
        val wallets = WALLETS.`as`("wallets")
        val holdingTypes = HOLDING_TYPES.`as`("holding_types")
        val segments = SEGMENTS.`as`("segments")
        val stockHoldings = STOCK_HOLDINGS.`as`("stock_holdings")
        val strategyClasses = STRATEGY_CLASSES.`as`("strategy_classes")
        val strategies = STRATEGIES.`as`("strategies")

        val lotsField = DSL.multiset(
            DSL.selectFrom(STOCK_LOTS)
                .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(stockHoldings.ID))
                .orderBy(STOCK_LOTS.LOT_DATE)
        ).`as`("lots").convertFrom { r ->
            r.map { rec ->
                LotResponse(
                    id = rec.get(STOCK_LOTS.EXTERNAL_ID)!!,
                    lotDate = rec.get(STOCK_LOTS.LOT_DATE)!!,
                    quantity = rec.get(STOCK_LOTS.QUANTITY)!!,
                    price = rec.get(STOCK_LOTS.PRICE)!!,
                )
            }
        }

        val withdrawalsField = DSL.multiset(
            DSL.selectFrom(RESULTS)
                .where(RESULTS.STOCK_HOLDING_ID.eq(stockHoldings.ID))
                .orderBy(RESULTS.RESULT_DATE)
        ).`as`("withdrawals").convertFrom { r ->
            r.map { rec ->
                WithdrawalResponse(
                    id = rec.get(RESULTS.EXTERNAL_ID)!!,
                    resultType = rec.get(RESULTS.RESULT_TYPE)!!.literal,
                    resultDate = rec.get(RESULTS.RESULT_DATE)!!,
                    quantity = rec.get(RESULTS.QUANTITY),
                    grossAmount = rec.get(RESULTS.GROSS_AMOUNT)!!,
                    fees = rec.get(RESULTS.FEES)!!,
                    taxes = rec.get(RESULTS.TAXES)!!,
                    costBasis = rec.get(RESULTS.COST_BASIS)!!,
                    netAmount = rec.get(RESULTS.NET_AMOUNT)!!,
                    profit = rec.get(RESULTS.PROFIT)!!,
                )
            }
        }

        return dsl.select(
            stockHoldings.EXTERNAL_ID,
            wallets.EXTERNAL_ID,
            holdingTypes.EXTERNAL_ID,
            holdingTypes.NAME,
            segments.EXTERNAL_ID,
            segments.NAME,
            stockHoldings.TICKER,
            stockHoldings.NAME,
            stockHoldings.CURRENT_PRICE,
            stockHoldings.FROZEN,
            strategies.EXTERNAL_ID,
            strategies.NAME,
            strategyClasses.EXTERNAL_ID,
            strategyClasses.NAME,
            lotsField,
            withdrawalsField
        )
            .from(stockHoldings)
            .join(wallets).on(wallets.ID.eq(stockHoldings.WALLET_ID))
            .join(holdingTypes).on(holdingTypes.ID.eq(stockHoldings.HOLDING_TYPE_ID))
            .leftJoin(segments).on(segments.ID.eq(stockHoldings.SEGMENT_ID))
            .join(strategyClasses).on(strategyClasses.ID.eq(stockHoldings.STRATEGY_CLASS_ID))
            .join(strategies).on(strategies.ID.eq(strategyClasses.STRATEGY_ID))
            .where(stockHoldings.ID.eq(internalId))
            .and(stockHoldings.WALLET_ID.eq(walletInternalId))
            .fetchOne { rec ->
                StockHoldingResponse(
                    id = rec.get(stockHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    typeId = rec.get(holdingTypes.EXTERNAL_ID)!!,
                    typeName = rec.get(holdingTypes.NAME)!!,
                    segmentId = rec.get(segments.EXTERNAL_ID),
                    segmentName = rec.get(segments.NAME),
                    ticker = rec.get(stockHoldings.TICKER)!!,
                    name = rec.get(stockHoldings.NAME)!!,
                    currentPrice = rec.get(stockHoldings.CURRENT_PRICE),
                    frozen = rec.get(stockHoldings.FROZEN)!!,
                    strategyId = rec.get(strategies.EXTERNAL_ID)!!,
                    strategyName = rec.get(strategies.NAME)!!,
                    strategyClassId = rec.get(strategyClasses.EXTERNAL_ID)!!,
                    strategyClassName = rec.get(strategyClasses.NAME)!!,
                    lots = rec.get(lotsField),
                    withdrawals = rec.get(withdrawalsField),
                )
            }
    }
}
