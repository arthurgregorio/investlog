package br.com.investlog.server.cryptoholdings.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
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
class CryptoLotRepository(private val dsl: DSLContext) {

    fun addLot(holdingInternalId: Long, request: LotCreateRequest): LotResponse {
        val rec = dsl.insertInto(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holdingInternalId)
            .set(CRYPTO_LOTS.LOT_DATE, request.lotDate)
            .set(CRYPTO_LOTS.QUANTITY, request.quantity)
            .set(CRYPTO_LOTS.PRICE, request.price)
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
            put(CRYPTO_LOTS.LOT_DATE, request.lotDate)
            request.quantity?.let { put(CRYPTO_LOTS.QUANTITY, it) }
            request.price?.let { put(CRYPTO_LOTS.PRICE, it) }
            val newQuantity = request.quantity?.let { DSL.`val`(it) } ?: CRYPTO_LOTS.QUANTITY
            val newPrice = request.price?.let { DSL.`val`(it) } ?: CRYPTO_LOTS.PRICE
            put(
                CRYPTO_LOTS.PURCHASE_AMOUNT,
                CRYPTO_LOTS.PURCHASE_AMOUNT.plus(newQuantity.mul(newPrice).minus(CRYPTO_LOTS.QUANTITY.mul(CRYPTO_LOTS.PRICE))),
            )
        }
        val rec = dsl.update(CRYPTO_LOTS)
            .set(changes)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .and(CRYPTO_LOTS.EXTERNAL_ID.eq(externalId))
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
        dsl.select(DSL.coalesce(DSL.sum(CRYPTO_LOTS.QUANTITY), BigDecimal.ZERO))
            .from(CRYPTO_LOTS)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .fetchSingle()
            .value1()

    fun sumExitedQuantity(holdingInternalId: Long): BigDecimal =
        dsl.select(DSL.coalesce(DSL.sum(RESULTS.QUANTITY), BigDecimal.ZERO))
            .from(RESULTS)
            .where(RESULTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .fetchSingle()
            .value1()

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(CRYPTO_LOTS)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .and(CRYPTO_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
