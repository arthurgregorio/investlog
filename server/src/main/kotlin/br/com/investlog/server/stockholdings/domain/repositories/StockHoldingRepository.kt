package br.com.investlog.server.stockholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_TYPES
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
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
class StockHoldingRepository(private val dsl: DSLContext) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<StockHoldingResponse> {
        val w = WALLETS.`as`("w")
        val st = STOCK_TYPES.`as`("st")
        val sh = STOCK_HOLDINGS.`as`("sh")

        val lotsField = DSL.multiset(
            DSL.selectFrom(STOCK_LOTS)
                .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(sh.ID))
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

        val content = dsl.select(sh.EXTERNAL_ID, w.EXTERNAL_ID, st.EXTERNAL_ID, sh.TICKER, sh.NAME, sh.CURRENT_PRICE, lotsField)
            .from(sh)
            .join(w).on(w.ID.eq(sh.WALLET_ID))
            .join(st).on(st.ID.eq(sh.STOCK_TYPE_ID))
            .where(sh.WALLET_ID.eq(walletInternalId))
            .orderBy(sh.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { rec ->
                StockHoldingResponse(
                    id = rec.get(sh.EXTERNAL_ID)!!,
                    walletId = rec.get(w.EXTERNAL_ID)!!,
                    stockTypeId = rec.get(st.EXTERNAL_ID)!!,
                    ticker = rec.get(sh.TICKER)!!,
                    name = rec.get(sh.NAME)!!,
                    currentPrice = rec.get(sh.CURRENT_PRICE),
                    lots = rec.get(lotsField),
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
        ticker: String,
        name: String,
        currentPrice: BigDecimal?,
        lot: LotCreateRequest,
    ): StockHoldingResponse {
        val holding = dsl.insertInto(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.WALLET_ID, walletInternalId)
            .set(STOCK_HOLDINGS.STOCK_TYPE_ID, stockTypeInternalId)
            .set(STOCK_HOLDINGS.TICKER, ticker.uppercase())
            .set(STOCK_HOLDINGS.NAME, name)
            .set(STOCK_HOLDINGS.CURRENT_PRICE, currentPrice)
            .returning()
            .fetchSingle()

        val lotRec = dsl.insertInto(STOCK_LOTS)
            .set(STOCK_LOTS.STOCK_HOLDING_ID, holding.id)
            .set(STOCK_LOTS.LOT_DATE, lot.lotDate)
            .set(STOCK_LOTS.QUANTITY, lot.quantity)
            .set(STOCK_LOTS.PRICE, lot.price)
            .returning()
            .fetchSingle()

        val walletExternalId = dsl.select(WALLETS.EXTERNAL_ID).from(WALLETS)
            .where(WALLETS.ID.eq(walletInternalId)).fetchSingle(WALLETS.EXTERNAL_ID)!!

        val stockTypeExternalId = dsl.select(STOCK_TYPES.EXTERNAL_ID).from(STOCK_TYPES)
            .where(STOCK_TYPES.ID.eq(stockTypeInternalId)).fetchSingle(STOCK_TYPES.EXTERNAL_ID)!!

        return StockHoldingResponse(
            id = holding.externalId!!,
            walletId = walletExternalId,
            stockTypeId = stockTypeExternalId,
            ticker = holding.ticker!!,
            name = holding.name!!,
            currentPrice = holding.currentPrice,
            lots = listOf(
                LotResponse(
                    id = lotRec.externalId!!,
                    lotDate = lotRec.lotDate!!,
                    quantity = lotRec.quantity!!,
                    price = lotRec.price!!,
                )
            ),
        )
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

    fun findStockTypeInternalId(externalId: UUID): Long? =
        dsl.select(STOCK_TYPES.ID).from(STOCK_TYPES)
            .where(STOCK_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchOne(STOCK_TYPES.ID)

    fun update(
        walletInternalId: Long,
        externalId: UUID,
        stockTypeInternalId: Long?,
        ticker: String?,
        name: String?,
        currentPrice: BigDecimal?,
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
        val w = WALLETS.`as`("w")
        val st = STOCK_TYPES.`as`("st")
        val sh = STOCK_HOLDINGS.`as`("sh")

        val lotsField = DSL.multiset(
            DSL.selectFrom(STOCK_LOTS)
                .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(sh.ID))
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

        return dsl.select(sh.EXTERNAL_ID, w.EXTERNAL_ID, st.EXTERNAL_ID, sh.TICKER, sh.NAME, sh.CURRENT_PRICE, lotsField)
            .from(sh)
            .join(w).on(w.ID.eq(sh.WALLET_ID))
            .join(st).on(st.ID.eq(sh.STOCK_TYPE_ID))
            .where(sh.ID.eq(internalId))
            .and(sh.WALLET_ID.eq(walletInternalId))
            .fetchOne { rec ->
                StockHoldingResponse(
                    id = rec.get(sh.EXTERNAL_ID)!!,
                    walletId = rec.get(w.EXTERNAL_ID)!!,
                    stockTypeId = rec.get(st.EXTERNAL_ID)!!,
                    ticker = rec.get(sh.TICKER)!!,
                    name = rec.get(sh.NAME)!!,
                    currentPrice = rec.get(sh.CURRENT_PRICE),
                    lots = rec.get(lotsField),
                )
            }
    }
}
