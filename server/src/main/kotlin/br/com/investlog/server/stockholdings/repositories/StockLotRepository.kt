package br.com.investlog.server.stockholdings.repositories

import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID

@Repository
class StockLotRepository(
    private val dsl: DSLContext
) {

    fun addLot(holdingInternalId: Long, request: LotCreateRequest): LotResponse {
        val rec = dsl.insertInto(STOCK_LOTS)
            .set(STOCK_LOTS.STOCK_HOLDING_ID, holdingInternalId)
            .set(STOCK_LOTS.LOT_DATE, request.lotDate)
            .set(STOCK_LOTS.QUANTITY, request.quantity)
            .set(STOCK_LOTS.PRICE, request.price)
            .returning()
            .fetchSingle()
        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun updateLot(holdingInternalId: Long, externalId: UUID, request: LotUpdateRequest): LotResponse? {
        val changes = buildMap<Field<*>, Any> {
            put(STOCK_LOTS.LOT_DATE, request.lotDate)
            request.quantity?.let { put(STOCK_LOTS.QUANTITY, it) }
            request.price?.let { put(STOCK_LOTS.PRICE, it) }
        }
        val rec = dsl.update(STOCK_LOTS)
            .set(changes)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .and(STOCK_LOTS.EXTERNAL_ID.eq(externalId))
            .returning()
            .fetchOne() ?: return null
        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun sumQuantity(holdingInternalId: Long): BigDecimal =
        dsl.select(DSL.coalesce(DSL.sum(STOCK_LOTS.QUANTITY), BigDecimal.ZERO))
            .from(STOCK_LOTS)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .fetchSingle()
            .value1()

    fun sumExitedQuantity(holdingInternalId: Long): BigDecimal =
        dsl.select(DSL.coalesce(DSL.sum(RESULTS.QUANTITY), BigDecimal.ZERO))
            .from(RESULTS)
            .where(RESULTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .fetchSingle()
            .value1()

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_LOTS)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .and(STOCK_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
