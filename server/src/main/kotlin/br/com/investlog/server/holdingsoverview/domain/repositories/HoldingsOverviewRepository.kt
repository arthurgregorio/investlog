package br.com.investlog.server.holdingsoverview.domain.repositories

import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class HoldingsOverviewRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, kind: JooqWalletKind?, pageable: Pageable): PagedModel<HoldingRowResponse> {
        val wallets = WALLETS.`as`("wallets")
        val overview = HOLDINGS_OVERVIEW.`as`("overview")

        val baseCondition = wallets.USER_ID.eq(userId)
        val kindCondition = if (kind != null) overview.KIND.eq(kind) else DSL.noCondition()

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
            overview.CURRENT_VALUE,
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(baseCondition).and(kindCondition)
            .orderBy(overview.COST_BASIS.desc())
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
                    currentValue = currentValue,
                    gain = gain,
                    gainPct = gainPct,
                )
            }

        val total = dsl.fetchCount(
            dsl.select(DSL.one())
                .from(overview)
                .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
                .where(baseCondition).and(kindCondition)
        )

        return pagedModelOf(content, pageable, total.toLong())
    }
}
