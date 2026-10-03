package br.com.investlog.server.reinvestments.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.REINVESTMENTS
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.reinvestments.rest.payloads.ReinvestmentResponse
import br.com.investlog.server.reinvestments.rest.payloads.ReinvestmentSideResponse
import br.com.investlog.server.results.repositories.HoldingPosition
import br.com.investlog.server.shared.utils.pagedModelOf
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class ReinvestmentRepository(private val dsl: DSLContext) {

    private class SideColumns(
        val holdingId: Field<UUID?>,
        val kind: Field<String?>,
        val name: Field<String?>,
        val ticker: Field<String?>,
        val walletId: Field<UUID?>,
        val walletName: Field<String?>,
    )

    fun findHolding(userId: Long, kind: WalletKind, externalId: UUID): ReinvestmentHolding? {
        val overview = HOLDINGS_OVERVIEW.`as`("overview")
        val wallets = WALLETS.`as`("wallets")

        return dsl.select(
            overview.HOLDING_ID,
            overview.KIND,
            overview.STATUS,
            overview.NAME,
            overview.QUANTITY,
            overview.COST_BASIS,
            overview.CURRENT_PRICE,
            overview.CURRENT_VALUE,
            overview.FROZEN,
            wallets.CURRENCY,
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(wallets.USER_ID.eq(userId))
            .and(overview.KIND.eq(kind))
            .and(overview.EXTERNAL_ID.eq(externalId))
            .fetchOne { record ->
                ReinvestmentHolding(
                    position = HoldingPosition(
                        holdingId = record.get(overview.HOLDING_ID)!!,
                        kind = record.get(overview.KIND)!!,
                        status = record.get(overview.STATUS)!!,
                        quantity = record.get(overview.QUANTITY),
                        costBasis = record.get(overview.COST_BASIS) ?: BigDecimal.ZERO,
                        currentValue = record.get(overview.CURRENT_VALUE),
                    ),
                    name = record.get(overview.NAME)!!,
                    walletCurrency = record.get(wallets.CURRENCY)!!,
                    currentPrice = record.get(overview.CURRENT_PRICE),
                    frozen = record.get(overview.FROZEN)!!,
                )
            }
    }

    fun addLot(kind: WalletKind, holdingId: Long, lotDate: LocalDate, quantity: BigDecimal, price: BigDecimal) {
        when (kind) {
            WalletKind.STOCKS -> dsl.insertInto(STOCK_LOTS)
                .set(STOCK_LOTS.STOCK_HOLDING_ID, holdingId)
                .set(STOCK_LOTS.LOT_DATE, lotDate)
                .set(STOCK_LOTS.QUANTITY, quantity)
                .set(STOCK_LOTS.PRICE, price)
                .execute()

            WalletKind.CRYPTO -> dsl.insertInto(CRYPTO_LOTS)
                .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holdingId)
                .set(CRYPTO_LOTS.LOT_DATE, lotDate)
                .set(CRYPTO_LOTS.QUANTITY, quantity)
                .set(CRYPTO_LOTS.PRICE, price)
                .execute()

            WalletKind.FUNDS -> throw IllegalArgumentException("Fund holdings receive contributions, not lots")
        }
    }

    fun addContribution(holdingId: Long, contributionDate: LocalDate, amount: BigDecimal) {
        dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holdingId)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, amount)
            .execute()

        dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.CURRENT_VALUE, DSL.coalesce(FUND_HOLDINGS.CURRENT_VALUE, BigDecimal.ZERO).plus(amount))
            .set(FUND_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(FUND_HOLDINGS.ID.eq(holdingId))
            .execute()
    }

    fun insert(resultId: Long, destination: HoldingPosition, amount: BigDecimal, reinvestmentDate: LocalDate) {
        dsl.insertInto(REINVESTMENTS)
            .set(REINVESTMENTS.RESULT_ID, resultId)
            .set(destinationColumnOf(destination.kind), destination.holdingId)
            .set(REINVESTMENTS.AMOUNT, amount)
            .set(REINVESTMENTS.REINVESTMENT_DATE, reinvestmentDate)
            .execute()
    }

    fun findAll(userId: Long, pageable: Pageable): PagedModel<ReinvestmentResponse> {
        val reinvestments = REINVESTMENTS.`as`("reinvestments")
        val results = RESULTS.`as`("results")
        val sourceStocks = STOCK_HOLDINGS.`as`("source_stocks")
        val sourceCryptos = CRYPTO_HOLDINGS.`as`("source_cryptos")
        val sourceFunds = FUND_HOLDINGS.`as`("source_funds")
        val sourceWallets = WALLETS.`as`("source_wallets")
        val destinationStocks = STOCK_HOLDINGS.`as`("destination_stocks")
        val destinationCryptos = CRYPTO_HOLDINGS.`as`("destination_cryptos")
        val destinationFunds = FUND_HOLDINGS.`as`("destination_funds")
        val destinationWallets = WALLETS.`as`("destination_wallets")

        val source = SideColumns(
            holdingId = DSL.coalesce(sourceStocks.EXTERNAL_ID, sourceCryptos.EXTERNAL_ID, sourceFunds.EXTERNAL_ID)
                .`as`("source_holding_id"),
            kind = DSL.case_()
                .`when`(results.STOCK_HOLDING_ID.isNotNull, WalletKind.STOCKS.literal)
                .`when`(results.CRYPTO_HOLDING_ID.isNotNull, WalletKind.CRYPTO.literal)
                .else_(WalletKind.FUNDS.literal)
                .`as`("source_kind"),
            name = DSL.coalesce(sourceStocks.NAME, sourceCryptos.NAME, sourceFunds.NAME).`as`("source_name"),
            ticker = DSL.coalesce(sourceStocks.TICKER, sourceCryptos.TICKER).`as`("source_ticker"),
            walletId = sourceWallets.EXTERNAL_ID.`as`("source_wallet_id"),
            walletName = sourceWallets.NAME.`as`("source_wallet_name"),
        )

        val destination = SideColumns(
            holdingId = DSL.coalesce(
                destinationStocks.EXTERNAL_ID,
                destinationCryptos.EXTERNAL_ID,
                destinationFunds.EXTERNAL_ID,
            ).`as`("destination_holding_id"),
            kind = DSL.case_()
                .`when`(reinvestments.DESTINATION_STOCK_HOLDING_ID.isNotNull, WalletKind.STOCKS.literal)
                .`when`(reinvestments.DESTINATION_CRYPTO_HOLDING_ID.isNotNull, WalletKind.CRYPTO.literal)
                .else_(WalletKind.FUNDS.literal)
                .`as`("destination_kind"),
            name = DSL.coalesce(destinationStocks.NAME, destinationCryptos.NAME, destinationFunds.NAME)
                .`as`("destination_name"),
            ticker = DSL.coalesce(destinationStocks.TICKER, destinationCryptos.TICKER).`as`("destination_ticker"),
            walletId = destinationWallets.EXTERNAL_ID.`as`("destination_wallet_id"),
            walletName = destinationWallets.NAME.`as`("destination_wallet_name"),
        )

        val sourceWalletId = DSL.coalesce(sourceStocks.WALLET_ID, sourceCryptos.WALLET_ID, sourceFunds.WALLET_ID)
        val destinationWalletId = DSL.coalesce(
            destinationStocks.WALLET_ID,
            destinationCryptos.WALLET_ID,
            destinationFunds.WALLET_ID,
        )

        val joined = reinvestments
            .join(results).on(results.ID.eq(reinvestments.RESULT_ID))
            .leftJoin(sourceStocks).on(sourceStocks.ID.eq(results.STOCK_HOLDING_ID))
            .leftJoin(sourceCryptos).on(sourceCryptos.ID.eq(results.CRYPTO_HOLDING_ID))
            .leftJoin(sourceFunds).on(sourceFunds.ID.eq(results.FUND_HOLDING_ID))
            .join(sourceWallets).on(sourceWallets.ID.eq(sourceWalletId))
            .leftJoin(destinationStocks).on(destinationStocks.ID.eq(reinvestments.DESTINATION_STOCK_HOLDING_ID))
            .leftJoin(destinationCryptos).on(destinationCryptos.ID.eq(reinvestments.DESTINATION_CRYPTO_HOLDING_ID))
            .leftJoin(destinationFunds).on(destinationFunds.ID.eq(reinvestments.DESTINATION_FUND_HOLDING_ID))
            .join(destinationWallets).on(destinationWallets.ID.eq(destinationWalletId))

        val content = dsl.select(
            reinvestments.EXTERNAL_ID,
            reinvestments.REINVESTMENT_DATE,
            reinvestments.AMOUNT,
            results.QUANTITY,
            results.GROSS_AMOUNT,
            results.FEES,
            results.TAXES,
            results.PROFIT,
            sourceWallets.CURRENCY,
            source.holdingId,
            source.kind,
            source.name,
            source.ticker,
            source.walletId,
            source.walletName,
            destination.holdingId,
            destination.kind,
            destination.name,
            destination.ticker,
            destination.walletId,
            destination.walletName,
        )
            .from(joined)
            .where(sourceWallets.USER_ID.eq(userId))
            .orderBy(reinvestments.REINVESTMENT_DATE.desc(), reinvestments.ID.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                ReinvestmentResponse(
                    id = record.get(reinvestments.EXTERNAL_ID)!!,
                    reinvestmentDate = record.get(reinvestments.REINVESTMENT_DATE)!!,
                    currency = record.get(sourceWallets.CURRENCY)!!,
                    source = sideOf(record, source),
                    destination = sideOf(record, destination),
                    quantity = record.get(results.QUANTITY),
                    grossAmount = record.get(results.GROSS_AMOUNT)!!,
                    fees = record.get(results.FEES)!!,
                    taxes = record.get(results.TAXES)!!,
                    amount = record.get(reinvestments.AMOUNT)!!,
                    profit = record.get(results.PROFIT)!!,
                )
            }

        val total = dsl.fetchCount(
            dsl.select(DSL.one())
                .from(joined)
                .where(sourceWallets.USER_ID.eq(userId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    private fun sideOf(record: Record, side: SideColumns) = ReinvestmentSideResponse(
        holdingId = record.get(side.holdingId)!!,
        kind = record.get(side.kind)!!,
        name = record.get(side.name)!!,
        ticker = record.get(side.ticker),
        walletId = record.get(side.walletId)!!,
        walletName = record.get(side.walletName)!!,
    )

    private fun destinationColumnOf(kind: WalletKind) = when (kind) {
        WalletKind.STOCKS -> REINVESTMENTS.DESTINATION_STOCK_HOLDING_ID
        WalletKind.CRYPTO -> REINVESTMENTS.DESTINATION_CRYPTO_HOLDING_ID
        WalletKind.FUNDS -> REINVESTMENTS.DESTINATION_FUND_HOLDING_ID
    }
}
