package br.com.investlog.server.cryptoholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
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

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(CRYPTO_LOTS)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .and(CRYPTO_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
