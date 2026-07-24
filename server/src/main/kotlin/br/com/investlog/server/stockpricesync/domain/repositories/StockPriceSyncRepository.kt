package br.com.investlog.server.stockpricesync.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime

@Repository
class StockPriceSyncRepository(
    private val dsl: DSLContext
) {

    fun findDistinctTickers(): List<String> =
        dsl.selectDistinct(STOCK_HOLDINGS.TICKER)
            .from(STOCK_HOLDINGS)
            .fetch(STOCK_HOLDINGS.TICKER)
            .filterNotNull()

    fun updatePrice(ticker: String, price: BigDecimal): Int =
        dsl.update(STOCK_HOLDINGS)
            .set(STOCK_HOLDINGS.CURRENT_PRICE, price)
            .set(STOCK_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(STOCK_HOLDINGS.TICKER.eq(ticker.uppercase()))
            .execute()
}
