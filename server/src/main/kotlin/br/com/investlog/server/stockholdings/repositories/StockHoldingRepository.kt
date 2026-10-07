package br.com.investlog.server.stockholdings.repositories

import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_SEGMENTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_TYPES
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
        val stockTypes = STOCK_TYPES.`as`("stock_types")
        val stockSegments = STOCK_SEGMENTS.`as`("stock_segments")
        val stockHoldings = STOCK_HOLDINGS.`as`("stock_holdings")

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
            stockTypes.EXTERNAL_ID,
            stockSegments.EXTERNAL_ID,
            stockSegments.NAME,
            stockHoldings.TICKER,
            stockHoldings.NAME,
            stockHoldings.CURRENT_PRICE,
            stockHoldings.FROZEN,
            lotsField,
            withdrawalsField
        )
            .from(stockHoldings)
            .join(wallets).on(wallets.ID.eq(stockHoldings.WALLET_ID))
            .join(stockTypes).on(stockTypes.ID.eq(stockHoldings.STOCK_TYPE_ID))
            .leftJoin(stockSegments).on(stockSegments.ID.eq(stockHoldings.STOCK_SEGMENT_ID))
            .where(stockHoldings.WALLET_ID.eq(walletInternalId))
            .orderBy(stockHoldings.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { rec ->
                StockHoldingResponse(
                    id = rec.get(stockHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    stockTypeId = rec.get(stockTypes.EXTERNAL_ID)!!,
                    stockSegmentId = rec.get(stockSegments.EXTERNAL_ID),
                    stockSegmentName = rec.get(stockSegments.NAME),
                    ticker = rec.get(stockHoldings.TICKER)!!,
                    name = rec.get(stockHoldings.NAME)!!,
                    currentPrice = rec.get(stockHoldings.CURRENT_PRICE),
                    frozen = rec.get(stockHoldings.FROZEN)!!,
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
        stockTypeInternalId: Long,
        stockSegmentInternalId: Long?,
        ticker: String,
        name: String,
        currentPrice: BigDecimal?,
        lot: LotCreateRequest,
    ): StockHoldingResponse {
        val holdingId = dsl.insertInto(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.WALLET_ID, walletInternalId)
            .set(STOCK_HOLDINGS.STOCK_TYPE_ID, stockTypeInternalId)
            .set(STOCK_HOLDINGS.STOCK_SEGMENT_ID, stockSegmentInternalId)
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

    fun findStockTypeInternalId(externalId: UUID): Long? =
        dsl.select(STOCK_TYPES.ID).from(STOCK_TYPES)
            .where(STOCK_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchOne(STOCK_TYPES.ID)

    fun findStockSegmentInternalId(externalId: UUID): Long? =
        dsl.select(STOCK_SEGMENTS.ID).from(STOCK_SEGMENTS)
            .where(STOCK_SEGMENTS.EXTERNAL_ID.eq(externalId))
            .fetchOne(STOCK_SEGMENTS.ID)

    fun updateSegment(walletInternalId: Long, externalId: UUID, stockSegmentInternalId: Long?): StockHoldingResponse? {
        val holdingId = dsl.update(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.STOCK_SEGMENT_ID, stockSegmentInternalId)
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
        stockTypeInternalId: Long?,
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
            .set(STOCK_HOLDINGS.STOCK_TYPE_ID, stockTypeInternalId ?: existing.stockTypeId!!)
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
        val stockTypes = STOCK_TYPES.`as`("stock_types")
        val stockSegments = STOCK_SEGMENTS.`as`("stock_segments")
        val stockHoldings = STOCK_HOLDINGS.`as`("stock_holdings")

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
            stockTypes.EXTERNAL_ID,
            stockSegments.EXTERNAL_ID,
            stockSegments.NAME,
            stockHoldings.TICKER,
            stockHoldings.NAME,
            stockHoldings.CURRENT_PRICE,
            stockHoldings.FROZEN,
            lotsField,
            withdrawalsField
        )
            .from(stockHoldings)
            .join(wallets).on(wallets.ID.eq(stockHoldings.WALLET_ID))
            .join(stockTypes).on(stockTypes.ID.eq(stockHoldings.STOCK_TYPE_ID))
            .leftJoin(stockSegments).on(stockSegments.ID.eq(stockHoldings.STOCK_SEGMENT_ID))
            .where(stockHoldings.ID.eq(internalId))
            .and(stockHoldings.WALLET_ID.eq(walletInternalId))
            .fetchOne { rec ->
                StockHoldingResponse(
                    id = rec.get(stockHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    stockTypeId = rec.get(stockTypes.EXTERNAL_ID)!!,
                    stockSegmentId = rec.get(stockSegments.EXTERNAL_ID),
                    stockSegmentName = rec.get(stockSegments.NAME),
                    ticker = rec.get(stockHoldings.TICKER)!!,
                    name = rec.get(stockHoldings.NAME)!!,
                    currentPrice = rec.get(stockHoldings.CURRENT_PRICE),
                    frozen = rec.get(stockHoldings.FROZEN)!!,
                    lots = rec.get(lotsField),
                    withdrawals = rec.get(withdrawalsField),
                )
            }
    }
}
