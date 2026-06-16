package br.com.investlog.server.stockholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class StockLotRepository(private val dsl: DSLContext) {

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

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_LOTS)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .and(STOCK_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
