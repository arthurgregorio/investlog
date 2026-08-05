package br.com.investlog.server.currencyrates.repositories

import br.com.investlog.server.currencyrates.rest.payloads.CurrencyRateResponse
import br.com.investlog.server.jooq.finances.tables.records.CurrencyRatesRecord
import br.com.investlog.server.jooq.finances.tables.references.CURRENCY_RATES
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.currencyrates.rest.payloads.CurrencyCode
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository

@Repository
class CurrencyRateRepository(private val dsl: DSLContext) {

    fun findAll(pageable: Pageable): PagedModel<CurrencyRateResponse> {
        val content = dsl.selectFrom(CURRENCY_RATES)
            .orderBy(CURRENCY_RATES.CURRENCY_CODE)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(CURRENCY_RATES)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun upsert(currencyCode: String, rate: BigDecimal, isBase: Boolean): CurrencyRateResponse {

        if (isBase) {
            dsl.update(CURRENCY_RATES)
                .set(CURRENCY_RATES.IS_BASE, false)
                .where(CURRENCY_RATES.IS_BASE.isTrue())
                .and(CURRENCY_RATES.CURRENCY_CODE.ne(currencyCode))
                .execute()
        }

        return dsl.insertInto(CURRENCY_RATES)
            .set(CURRENCY_RATES.CURRENCY_CODE, currencyCode)
            .set(CURRENCY_RATES.RATE, rate)
            .set(CURRENCY_RATES.IS_BASE, isBase)
            .onConflict(CURRENCY_RATES.CURRENCY_CODE)
            .doUpdate()
            .set(CURRENCY_RATES.RATE, rate)
            .set(CURRENCY_RATES.IS_BASE, isBase)
            .set(CURRENCY_RATES.UPDATED_AT, OffsetDateTime.now())
            .returning()
            .fetchSingle()
            .toResponse()
    }

    private fun CurrencyRatesRecord.toResponse() = CurrencyRateResponse(
        currencyCode = CurrencyCode.fromText(currencyCode),
        rate = rate!!,
        isBase = isBase!!,
    )
}
