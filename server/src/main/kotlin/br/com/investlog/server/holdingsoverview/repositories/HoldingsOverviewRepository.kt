package br.com.investlog.server.holdingsoverview.repositories

import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_REPORT_ROWS
import br.com.investlog.server.shared.utils.pagedModelOf
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SortField
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
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
        val overview = HOLDINGS_OVERVIEW.`as`("overview")

        val baseCondition = overview.USER_ID.eq(userId).and(overview.STATUS.eq(HoldingStatus.ACTIVE))
        val kindCondition = if (kind != null) overview.KIND.eq(kind) else DSL.noCondition()
        val typeLabelCondition = if (typeLabel != null) overview.TYPE_LABEL.eq(typeLabel) else DSL.noCondition()
        val walletIdCondition = if (walletId != null) overview.WALLET_EXTERNAL_ID.eq(walletId) else DSL.noCondition()
        val searchCondition = if (!search.isNullOrBlank()) {
            overview.NAME.likeIgnoreCase("%$search%").or(overview.TICKER.likeIgnoreCase("%$search%"))
        } else {
            DSL.noCondition()
        }

        val sortFields: List<SortField<*>> = pageable.sort.mapNotNull { order ->
            val field: Field<*>? = when (order.property) {
                "wallet" -> overview.WALLET_NAME
                "price" -> overview.CURRENT_PRICE
                "invested" -> overview.COST_BASIS
                "current" -> overview.CURRENT_VALUE
                "gain" -> overview.GAIN
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
            overview.WALLET_EXTERNAL_ID,
            overview.WALLET_NAME,
            overview.WALLET_CURRENCY,
            overview.QUANTITY,
            overview.COST_BASIS,
            overview.CURRENT_PRICE,
            overview.CURRENT_VALUE,
            overview.FROZEN,
            overview.GAIN,
            overview.GAIN_PCT,
            overview.SEGMENT_LABEL,
        )
            .from(overview)
            .where(baseCondition).and(kindCondition).and(typeLabelCondition).and(walletIdCondition).and(searchCondition)
            .orderBy(sortFields)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                HoldingRowResponse(
                    id = record.get(overview.EXTERNAL_ID)!!,
                    kind = record.get(overview.KIND)!!.literal,
                    name = record.get(overview.NAME)!!,
                    ticker = record.get(overview.TICKER),
                    typeLabel = record.get(overview.TYPE_LABEL),
                    segmentLabel = record.get(overview.SEGMENT_LABEL),
                    walletId = record.get(overview.WALLET_EXTERNAL_ID)!!,
                    walletName = record.get(overview.WALLET_NAME)!!,
                    walletCurrency = record.get(overview.WALLET_CURRENCY)!!,
                    quantity = record.get(overview.QUANTITY),
                    costBasis = record.get(overview.COST_BASIS) ?: BigDecimal.ZERO,
                    currentPrice = record.get(overview.CURRENT_PRICE),
                    frozen = record.get(overview.FROZEN)!!,
                    currentValue = record.get(overview.CURRENT_VALUE),
                    gain = record.get(overview.GAIN),
                    gainPct = record.get(overview.GAIN_PCT),
                )
            }

        val total = dsl.fetchCount(
            dsl.select(DSL.one())
                .from(overview)
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
        val reportRows = HOLDINGS_REPORT_ROWS.`as`("report_rows")

        val baseCondition = reportRows.USER_ID.eq(userId)
        val kindCondition = if (kind != null) reportRows.KIND.eq(kind) else DSL.noCondition()
        val typeLabelCondition = if (typeLabel != null) reportRows.TYPE_LABEL.eq(typeLabel) else DSL.noCondition()
        val walletIdCondition = if (walletId != null) reportRows.WALLET_EXTERNAL_ID.eq(walletId) else DSL.noCondition()
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
            reportRows.WALLET_EXTERNAL_ID,
            reportRows.WALLET_NAME,
            reportRows.WALLET_CURRENCY,
            reportRows.QUANTITY,
            reportRows.COST_BASIS,
            reportRows.CURRENT_PRICE,
            reportRows.CURRENT_VALUE,
            reportRows.FROZEN,
            reportRows.GAIN,
            reportRows.GAIN_PCT,
            reportRows.SEGMENT_LABEL,
        )
            .from(reportRows)
            .where(baseCondition).and(kindCondition).and(typeLabelCondition).and(walletIdCondition).and(searchCondition)
            .orderBy(reportRows.WALLET_NAME, reportRows.COST_BASIS.desc().nullsLast())
            .fetch { record ->
                HoldingRowResponse(
                    id = record.get(reportRows.EXTERNAL_ID)!!,
                    kind = record.get(reportRows.KIND)!!.literal,
                    name = record.get(reportRows.NAME)!!,
                    ticker = record.get(reportRows.TICKER),
                    typeLabel = record.get(reportRows.TYPE_LABEL),
                    segmentLabel = record.get(reportRows.SEGMENT_LABEL),
                    walletId = record.get(reportRows.WALLET_EXTERNAL_ID)!!,
                    walletName = record.get(reportRows.WALLET_NAME)!!,
                    walletCurrency = record.get(reportRows.WALLET_CURRENCY)!!,
                    quantity = record.get(reportRows.QUANTITY),
                    costBasis = record.get(reportRows.COST_BASIS) ?: BigDecimal.ZERO,
                    currentPrice = record.get(reportRows.CURRENT_PRICE),
                    frozen = record.get(reportRows.FROZEN)!!,
                    currentValue = record.get(reportRows.CURRENT_VALUE),
                    gain = record.get(reportRows.GAIN),
                    gainPct = record.get(reportRows.GAIN_PCT),
                )
            }
    }
}
