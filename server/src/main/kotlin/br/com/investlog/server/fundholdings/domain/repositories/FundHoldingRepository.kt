package br.com.investlog.server.fundholdings.domain.repositories

import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.FUND_TYPES
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class FundHoldingRepository(private val dsl: DSLContext) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<FundHoldingResponse> {
        val wallets = WALLETS.`as`("wallets")
        val fundTypes = FUND_TYPES.`as`("fund_types")
        val fundHoldings = FUND_HOLDINGS.`as`("fund_holdings")

        val contributionsField = DSL.multiset(
            DSL.selectFrom(FUND_CONTRIBUTIONS)
                .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(fundHoldings.ID))
                .orderBy(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE)
        ).`as`("contributions").convertFrom { r ->
            r.map { rec ->
                ContributionResponse(
                    id = rec.get(FUND_CONTRIBUTIONS.EXTERNAL_ID)!!,
                    contributionDate = rec.get(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE)!!,
                    amount = rec.get(FUND_CONTRIBUTIONS.AMOUNT)!!,
                )
            }
        }

        val content = dsl.select(fundHoldings.EXTERNAL_ID, wallets.EXTERNAL_ID, fundTypes.EXTERNAL_ID, fundHoldings.NAME, fundHoldings.CURRENT_VALUE, contributionsField)
            .from(fundHoldings)
            .join(wallets).on(wallets.ID.eq(fundHoldings.WALLET_ID))
            .join(fundTypes).on(fundTypes.ID.eq(fundHoldings.FUND_TYPE_ID))
            .where(fundHoldings.WALLET_ID.eq(walletInternalId))
            .orderBy(fundHoldings.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { rec ->
                FundHoldingResponse(
                    id = rec.get(fundHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    fundTypeId = rec.get(fundTypes.EXTERNAL_ID)!!,
                    name = rec.get(fundHoldings.NAME)!!,
                    currentValue = rec.get(fundHoldings.CURRENT_VALUE),
                    contributions = rec.get(contributionsField),
                )
            }

        val total = dsl.fetchCount(
            dsl.selectFrom(FUND_HOLDINGS).where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(walletInternalId: Long, fundTypeInternalId: Long, name: String, currentValue: BigDecimal?, contribution: ContributionCreateRequest): FundHoldingResponse {
        val holding = dsl.insertInto(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.WALLET_ID, walletInternalId)
            .set(FUND_HOLDINGS.FUND_TYPE_ID, fundTypeInternalId)
            .set(FUND_HOLDINGS.NAME, name)
            .set(FUND_HOLDINGS.CURRENT_VALUE, currentValue)
            .returning()
            .fetchSingle()

        val contribRec = dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holding.id)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, contribution.contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, contribution.amount)
            .returning()
            .fetchSingle()

        val walletExternalId = dsl.select(WALLETS.EXTERNAL_ID).from(WALLETS)
            .where(WALLETS.ID.eq(walletInternalId)).fetchSingle(WALLETS.EXTERNAL_ID)!!

        val fundTypeExternalId = dsl.select(FUND_TYPES.EXTERNAL_ID).from(FUND_TYPES)
            .where(FUND_TYPES.ID.eq(fundTypeInternalId)).fetchSingle(FUND_TYPES.EXTERNAL_ID)!!

        return FundHoldingResponse(
            id = holding.externalId!!,
            walletId = walletExternalId,
            fundTypeId = fundTypeExternalId,
            name = holding.name!!,
            currentValue = holding.currentValue,
            contributions = listOf(
                ContributionResponse(
                    id = contribRec.externalId!!,
                    contributionDate = contribRec.contributionDate!!,
                    amount = contribRec.amount!!,
                )
            ),
        )
    }

    fun findFundTypeInternalId(externalId: UUID): Long? =
        dsl.select(FUND_TYPES.ID).from(FUND_TYPES)
            .where(FUND_TYPES.EXTERNAL_ID.eq(externalId))
            .fetchOne(FUND_TYPES.ID)

    fun findByExternalId(walletInternalId: Long, externalId: UUID): FundHoldingResponse? {
        val internalId = findInternalId(walletInternalId, externalId) ?: return null
        return findByInternalId(internalId, walletInternalId)
    }

    fun findInternalId(walletInternalId: Long, externalId: UUID): Long? =
        dsl.select(FUND_HOLDINGS.ID)
            .from(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne(FUND_HOLDINGS.ID)

    fun update(walletInternalId: Long, externalId: UUID, fundTypeInternalId: Long?, name: String?, currentValue: BigDecimal?): FundHoldingResponse? {
        val existing = dsl.selectFrom(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.FUND_TYPE_ID, fundTypeInternalId ?: existing.fundTypeId!!)
            .set(FUND_HOLDINGS.NAME, name ?: existing.name!!)
            .set(FUND_HOLDINGS.CURRENT_VALUE, currentValue ?: existing.currentValue)
            .set(FUND_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(FUND_HOLDINGS.ID.eq(existing.id))
            .execute()

        return findByInternalId(existing.id!!, walletInternalId)
    }

    fun deleteByExternalId(walletInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun findByInternalId(internalId: Long, walletInternalId: Long): FundHoldingResponse? {
        val wallets = WALLETS.`as`("wallets")
        val fundTypes = FUND_TYPES.`as`("fund_types")
        val fundHoldings = FUND_HOLDINGS.`as`("fund_holdings")

        val contributionsField = DSL.multiset(
            DSL.selectFrom(FUND_CONTRIBUTIONS)
                .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(fundHoldings.ID))
                .orderBy(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE)
        ).`as`("contributions").convertFrom { r ->
            r.map { rec ->
                ContributionResponse(
                    id = rec.get(FUND_CONTRIBUTIONS.EXTERNAL_ID)!!,
                    contributionDate = rec.get(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE)!!,
                    amount = rec.get(FUND_CONTRIBUTIONS.AMOUNT)!!,
                )
            }
        }

        return dsl.select(fundHoldings.EXTERNAL_ID, wallets.EXTERNAL_ID, fundTypes.EXTERNAL_ID, fundHoldings.NAME, fundHoldings.CURRENT_VALUE, contributionsField)
            .from(fundHoldings)
            .join(wallets).on(wallets.ID.eq(fundHoldings.WALLET_ID))
            .join(fundTypes).on(fundTypes.ID.eq(fundHoldings.FUND_TYPE_ID))
            .where(fundHoldings.ID.eq(internalId))
            .and(fundHoldings.WALLET_ID.eq(walletInternalId))
            .fetchOne { rec ->
                FundHoldingResponse(
                    id = rec.get(fundHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    fundTypeId = rec.get(fundTypes.EXTERNAL_ID)!!,
                    name = rec.get(fundHoldings.NAME)!!,
                    currentValue = rec.get(fundHoldings.CURRENT_VALUE),
                    contributions = rec.get(contributionsField),
                )
            }
    }
}
