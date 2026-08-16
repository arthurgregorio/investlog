package br.com.investlog.server.holdingsoverview.repositories

import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_REPORT_ROWS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.utils.pagedModelOf
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SortField
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class HoldingsOverviewRepository(private val dsl: DSLContext) {

    fun findAll(
        userId: Long,
        kind: JooqWalletKind?,
        typeLabel: String?,
        walletId: UUID?,
        search: String?,
        pageable: Pageable,
    ): PagedModel<HoldingRowResponse> {
        val wallets = WALLETS.`as`("wallets")
        val overview = HOLDINGS_OVERVIEW.`as`("overview")

        val baseCondition = wallets.USER_ID.eq(userId)
        val kindCondition = if (kind != null) overview.KIND.eq(kind) else DSL.noCondition()
        val typeLabelCondition = if (typeLabel != null) overview.TYPE_LABEL.eq(typeLabel) else DSL.noCondition()
        val walletIdCondition = if (walletId != null) wallets.EXTERNAL_ID.eq(walletId) else DSL.noCondition()
        val searchCondition = if (!search.isNullOrBlank()) {
            overview.NAME.likeIgnoreCase("%$search%").or(overview.TICKER.likeIgnoreCase("%$search%"))
        } else {
            DSL.noCondition()
        }

        val sortFields: List<SortField<*>> = pageable.sort.mapNotNull { order ->
            val field: Field<*>? = when (order.property) {
                "wallet" -> wallets.NAME
                "price" -> overview.CURRENT_PRICE
                "invested" -> overview.COST_BASIS
                "current" -> overview.CURRENT_VALUE
                "gain" -> overview.CURRENT_VALUE.sub(overview.COST_BASIS)
                else -> null
            }
            field?.let { if (order.isAscending) it.asc().nullsLast() else it.desc().nullsLast() }
        }.ifEmpty { listOf(overview.COST_BASIS.desc().nullsLast()) }

        val content = dsl.select(
            overview.EXTERNAL_ID,
            overview.KIND,
            overview.NAME,
            overview.TICKER,
            overview.TYPE_LABEL,
            wallets.EXTERNAL_ID,
            wallets.NAME,
            wallets.CURRENCY,
            overview.QUANTITY,
            overview.COST_BASIS,
            overview.CURRENT_PRICE,
            overview.CURRENT_VALUE,
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(baseCondition).and(kindCondition).and(typeLabelCondition).and(walletIdCondition).and(searchCondition)
            .orderBy(sortFields)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                val costBasis = record.get(overview.COST_BASIS) ?: BigDecimal.ZERO
                val currentValue = record.get(overview.CURRENT_VALUE)
                val gain = currentValue?.let { it - costBasis }
                val gainPct = if (gain != null && costBasis.signum() != 0) {
                    gain.divide(costBasis, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                } else null

                HoldingRowResponse(
                    id = record.get(overview.EXTERNAL_ID)!!,
                    kind = record.get(overview.KIND)!!.literal,
                    name = record.get(overview.NAME)!!,
                    ticker = record.get(overview.TICKER),
                    typeLabel = record.get(overview.TYPE_LABEL),
                    walletId = record.get(wallets.EXTERNAL_ID)!!,
                    walletName = record.get(wallets.NAME)!!,
                    walletCurrency = record.get(wallets.CURRENCY)!!,
                    quantity = record.get(overview.QUANTITY),
                    costBasis = costBasis,
                    currentPrice = record.get(overview.CURRENT_PRICE),
                    currentValue = currentValue,
                    gain = gain,
                    gainPct = gainPct,
                )
            }

        val total = dsl.fetchCount(
            dsl.select(DSL.one())
                .from(overview)
                .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
                .where(baseCondition).and(kindCondition).and(typeLabelCondition).and(walletIdCondition).and(searchCondition)
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun findAllForReport(
        userId: Long,
        kind: JooqWalletKind?,
        typeLabel: String?,
        walletId: UUID?,
        search: String?,
    ): List<HoldingRowResponse> {
        val wallets = WALLETS.`as`("wallets")
        val reportRows = HOLDINGS_REPORT_ROWS.`as`("report_rows")

        val baseCondition = wallets.USER_ID.eq(userId)
        val kindCondition = if (kind != null) reportRows.KIND.eq(kind) else DSL.noCondition()
        val typeLabelCondition = if (typeLabel != null) reportRows.TYPE_LABEL.eq(typeLabel) else DSL.noCondition()
        val walletIdCondition = if (walletId != null) wallets.EXTERNAL_ID.eq(walletId) else DSL.noCondition()
        val searchCondition = if (!search.isNullOrBlank()) {
            reportRows.NAME.likeIgnoreCase("%$search%").or(reportRows.TICKER.likeIgnoreCase("%$search%"))
        } else {
            DSL.noCondition()
        }

        return dsl.select(
            reportRows.EXTERNAL_ID,
            reportRows.KIND,
            reportRows.NAME,
            reportRows.TICKER,
            reportRows.TYPE_LABEL,
            wallets.EXTERNAL_ID,
            wallets.NAME,
            wallets.CURRENCY,
            reportRows.QUANTITY,
            reportRows.COST_BASIS,
            reportRows.CURRENT_PRICE,
            reportRows.CURRENT_VALUE,
        )
            .from(reportRows)
            .join(wallets).on(wallets.ID.eq(reportRows.WALLET_ID))
            .where(baseCondition).and(kindCondition).and(typeLabelCondition).and(walletIdCondition).and(searchCondition)
            .orderBy(wallets.NAME, reportRows.COST_BASIS.desc().nullsLast())
            .fetch { record ->
                val costBasis = record.get(reportRows.COST_BASIS) ?: BigDecimal.ZERO
                val currentValue = record.get(reportRows.CURRENT_VALUE)
                val gain = currentValue?.let { it - costBasis }
                val gainPct = if (gain != null && costBasis.signum() != 0) {
                    gain.divide(costBasis, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                } else null

                HoldingRowResponse(
                    id = record.get(reportRows.EXTERNAL_ID)!!,
                    kind = record.get(reportRows.KIND)!!.literal,
                    name = record.get(reportRows.NAME)!!,
                    ticker = record.get(reportRows.TICKER),
                    typeLabel = record.get(reportRows.TYPE_LABEL),
                    walletId = record.get(wallets.EXTERNAL_ID)!!,
                    walletName = record.get(wallets.NAME)!!,
                    walletCurrency = record.get(wallets.CURRENCY)!!,
                    quantity = record.get(reportRows.QUANTITY),
                    costBasis = costBasis,
                    currentPrice = record.get(reportRows.CURRENT_PRICE),
                    currentValue = currentValue,
                    gain = gain,
                    gainPct = gainPct,
                )
            }
    }
}
