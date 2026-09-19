package br.com.investlog.server.results.repositories

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.ResultType
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.results.rest.payloads.ResultResponse
import br.com.investlog.server.shared.utils.pagedModelOf
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
class ResultRepository(private val dsl: DSLContext) {

    fun findPosition(userId: Long, walletId: UUID, holdingId: UUID): HoldingPosition? {
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val wallets = WALLETS.`as`("wallets")

        return dsl.select(
            overview.HOLDING_ID,
            overview.KIND,
            overview.STATUS,
            overview.QUANTITY,
            overview.COST_BASIS,
            overview.CURRENT_VALUE,
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(wallets.USER_ID.eq(userId))
            .and(wallets.EXTERNAL_ID.eq(walletId))
            .and(overview.EXTERNAL_ID.eq(holdingId))
            .fetchOne { record ->
                HoldingPosition(
                    holdingId = record.get(overview.HOLDING_ID)!!,
                    kind = record.get(overview.KIND)!!,
                    status = record.get(overview.STATUS)!!,
                    quantity = record.get(overview.QUANTITY),
                    costBasis = record.get(overview.COST_BASIS) ?: BigDecimal.ZERO,
                    currentValue = record.get(overview.CURRENT_VALUE),
                )
            }
    }

    fun insert(
        position: HoldingPosition,
        resultType: ResultType,
        resultDate: LocalDate,
        quantity: BigDecimal?,
        grossAmount: BigDecimal,
        fees: BigDecimal,
        taxes: BigDecimal,
        costBasis: BigDecimal,
    ) {
        dsl.insertInto(RESULTS)
            .set(holdingColumnOf(position), position.holdingId)
            .set(RESULTS.RESULT_TYPE, resultType)
            .set(RESULTS.RESULT_DATE, resultDate)
            .set(RESULTS.QUANTITY, quantity)
            .set(RESULTS.GROSS_AMOUNT, grossAmount)
            .set(RESULTS.FEES, fees)
            .set(RESULTS.TAXES, taxes)
            .set(RESULTS.COST_BASIS, costBasis)
            .execute()
    }

    fun markCompleted(position: HoldingPosition) {
        when (position.kind) {
            WalletKind.STOCKS -> dsl.update(STOCK_HOLDINGS)
                .set(STOCK_HOLDINGS.STATUS, HoldingStatus.COMPLETED)
                .where(STOCK_HOLDINGS.ID.eq(position.holdingId))
                .execute()

            WalletKind.CRYPTO -> dsl.update(CRYPTO_HOLDINGS)
                .set(CRYPTO_HOLDINGS.STATUS, HoldingStatus.COMPLETED)
                .where(CRYPTO_HOLDINGS.ID.eq(position.holdingId))
                .execute()

            WalletKind.FUNDS -> dsl.update(FUND_HOLDINGS)
                .set(FUND_HOLDINGS.STATUS, HoldingStatus.COMPLETED)
                .where(FUND_HOLDINGS.ID.eq(position.holdingId))
                .execute()
        }
    }

    fun reduceFundCurrentValue(holdingId: Long, amount: BigDecimal) {
        dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.CURRENT_VALUE, FUND_HOLDINGS.CURRENT_VALUE.minus(amount))
            .where(FUND_HOLDINGS.ID.eq(holdingId))
            .execute()
    }

    fun findAll(userId: Long, pageable: Pageable): PagedModel<ResultResponse> {
        val results = RESULTS.`as`("results")
        val stockHoldings = STOCK_HOLDINGS.`as`("stock_holdings")
        val cryptoHoldings = CRYPTO_HOLDINGS.`as`("crypto_holdings")
        val fundHoldings = FUND_HOLDINGS.`as`("fund_holdings")
        val wallets = WALLETS.`as`("wallets")

        val walletId = DSL.coalesce(stockHoldings.WALLET_ID, cryptoHoldings.WALLET_ID, fundHoldings.WALLET_ID)
        val holdingName = DSL.coalesce(stockHoldings.NAME, cryptoHoldings.NAME, fundHoldings.NAME)
        val ticker = DSL.coalesce(stockHoldings.TICKER, cryptoHoldings.TICKER)
        val kind = DSL.case_()
            .`when`(results.STOCK_HOLDING_ID.isNotNull, WalletKind.STOCKS.literal)
            .`when`(results.CRYPTO_HOLDING_ID.isNotNull, WalletKind.CRYPTO.literal)
            .else_(WalletKind.FUNDS.literal)

        val content = dsl.select(
            results.EXTERNAL_ID,
            results.RESULT_TYPE,
            results.RESULT_DATE,
            results.QUANTITY,
            results.GROSS_AMOUNT,
            results.FEES,
            results.TAXES,
            results.COST_BASIS,
            results.NET_AMOUNT,
            results.PROFIT,
            kind.`as`("kind"),
            holdingName.`as`("holding_name"),
            ticker.`as`("ticker"),
            wallets.EXTERNAL_ID,
            wallets.NAME,
            wallets.CURRENCY,
        )
            .from(results)
            .leftJoin(stockHoldings).on(stockHoldings.ID.eq(results.STOCK_HOLDING_ID))
            .leftJoin(cryptoHoldings).on(cryptoHoldings.ID.eq(results.CRYPTO_HOLDING_ID))
            .leftJoin(fundHoldings).on(fundHoldings.ID.eq(results.FUND_HOLDING_ID))
            .join(wallets).on(wallets.ID.eq(walletId))
            .where(wallets.USER_ID.eq(userId))
            .orderBy(results.RESULT_DATE.desc(), results.ID.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                ResultResponse(
                    id = record.get(results.EXTERNAL_ID)!!,
                    kind = record.get("kind", String::class.java)!!,
                    resultType = record.get(results.RESULT_TYPE)!!.literal,
                    holdingName = record.get("holding_name", String::class.java)!!,
                    ticker = record.get("ticker", String::class.java),
                    walletId = record.get(wallets.EXTERNAL_ID)!!,
                    walletName = record.get(wallets.NAME)!!,
                    walletCurrency = record.get(wallets.CURRENCY)!!,
                    resultDate = record.get(results.RESULT_DATE)!!,
                    quantity = record.get(results.QUANTITY),
                    grossAmount = record.get(results.GROSS_AMOUNT)!!,
                    fees = record.get(results.FEES)!!,
                    taxes = record.get(results.TAXES)!!,
                    costBasis = record.get(results.COST_BASIS)!!,
                    netAmount = record.get(results.NET_AMOUNT)!!,
                    profit = record.get(results.PROFIT)!!,
                )
            }

        val total = dsl.fetchCount(
            dsl.select(DSL.one())
                .from(results)
                .leftJoin(stockHoldings).on(stockHoldings.ID.eq(results.STOCK_HOLDING_ID))
                .leftJoin(cryptoHoldings).on(cryptoHoldings.ID.eq(results.CRYPTO_HOLDING_ID))
                .leftJoin(fundHoldings).on(fundHoldings.ID.eq(results.FUND_HOLDING_ID))
                .join(wallets).on(wallets.ID.eq(walletId))
                .where(wallets.USER_ID.eq(userId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    private fun holdingColumnOf(position: HoldingPosition) = when (position.kind) {
        WalletKind.STOCKS -> RESULTS.STOCK_HOLDING_ID
        WalletKind.CRYPTO -> RESULTS.CRYPTO_HOLDING_ID
        WalletKind.FUNDS -> RESULTS.FUND_HOLDING_ID
    }
}
