package br.com.investlog.server.currencyrates.domain.repositories

import br.com.investlog.server.currencyrates.rest.dtos.CurrencyRateResponse
import br.com.investlog.server.jooq.finances.tables.records.CurrencyRatesRecord
import br.com.investlog.server.jooq.finances.tables.references.CURRENCY_RATES
import br.com.investlog.server.shared.persistence.pagedModelOf
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CurrencyRateRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<CurrencyRateResponse> {
        val content = dsl.selectFrom(CURRENCY_RATES)
            .where(CURRENCY_RATES.USER_ID.eq(userId))
            .orderBy(CURRENCY_RATES.CURRENCY_CODE)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(
            dsl.selectFrom(CURRENCY_RATES).where(CURRENCY_RATES.USER_ID.eq(userId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    @Transactional
    fun upsert(userId: Long, currencyCode: String, rate: BigDecimal, isBase: Boolean): CurrencyRateResponse {
        if (isBase) {
            dsl.update(CURRENCY_RATES)
                .set(CURRENCY_RATES.IS_BASE, false)
                .where(CURRENCY_RATES.USER_ID.eq(userId))
                .and(CURRENCY_RATES.IS_BASE.isTrue())
                .and(CURRENCY_RATES.CURRENCY_CODE.ne(currencyCode))
                .execute()
        }

        return dsl.insertInto(CURRENCY_RATES)
            .set(CURRENCY_RATES.USER_ID, userId)
            .set(CURRENCY_RATES.CURRENCY_CODE, currencyCode)
            .set(CURRENCY_RATES.RATE, rate)
            .set(CURRENCY_RATES.IS_BASE, isBase)
            .onConflict(CURRENCY_RATES.USER_ID, CURRENCY_RATES.CURRENCY_CODE)
            .doUpdate()
            .set(CURRENCY_RATES.RATE, rate)
            .set(CURRENCY_RATES.IS_BASE, isBase)
            .set(CURRENCY_RATES.UPDATED_AT, OffsetDateTime.now())
            .returning()
            .fetchSingle()
            .toResponse()
    }

    private fun CurrencyRatesRecord.toResponse() = CurrencyRateResponse(
        currencyCode = currencyCode!!,
        rate = rate!!,
        isBase = isBase!!,
    )
}
