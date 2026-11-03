package br.com.investlog.server.fundholdings.repositories

import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import br.com.investlog.server.jooq.finances.tables.references.FUND_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_TYPES
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.SEGMENTS
import br.com.investlog.server.jooq.finances.tables.references.STRATEGIES
import br.com.investlog.server.jooq.finances.tables.references.STRATEGY_CLASSES
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.results.rest.payloads.WithdrawalResponse
import br.com.investlog.server.shared.utils.pagedModelOf
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class FundHoldingRepository(
    private val dsl: DSLContext
) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<FundHoldingResponse> {
        val wallets = WALLETS.`as`("wallets")
        val holdingTypes = HOLDING_TYPES.`as`("holding_types")
        val segments = SEGMENTS.`as`("segments")
        val fundHoldings = FUND_HOLDINGS.`as`("fund_holdings")
        val strategyClasses = STRATEGY_CLASSES.`as`("strategy_classes")
        val strategies = STRATEGIES.`as`("strategies")

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

        val withdrawalsField = DSL.multiset(
            DSL.selectFrom(RESULTS)
                .where(RESULTS.FUND_HOLDING_ID.eq(fundHoldings.ID))
                .orderBy(RESULTS.RESULT_DATE)
        ).`as`("withdrawals").convertFrom { r ->
            r.map { rec ->
                WithdrawalResponse(
                    id = rec.get(RESULTS.EXTERNAL_ID)!!,
                    resultType = rec.get(RESULTS.RESULT_TYPE)!!.literal,
                    resultDate = rec.get(RESULTS.RESULT_DATE)!!,
                    quantity = rec.get(RESULTS.QUANTITY),
                    grossAmount = rec.get(RESULTS.GROSS_AMOUNT)!!,
                    fees = rec.get(RESULTS.FEES)!!,
                    taxes = rec.get(RESULTS.TAXES)!!,
                    costBasis = rec.get(RESULTS.COST_BASIS)!!,
                    netAmount = rec.get(RESULTS.NET_AMOUNT)!!,
                    profit = rec.get(RESULTS.PROFIT)!!,
                )
            }
        }

        val content = dsl.select(
            fundHoldings.EXTERNAL_ID,
            wallets.EXTERNAL_ID,
            holdingTypes.EXTERNAL_ID,
            holdingTypes.NAME,
            segments.EXTERNAL_ID,
            segments.NAME,
            fundHoldings.NAME,
            fundHoldings.CURRENT_VALUE,
            fundHoldings.ADMINISTRATION_FEE_RATE,
            fundHoldings.PERFORMANCE_FEE_RATE,
            fundHoldings.FROZEN,
            strategies.EXTERNAL_ID,
            strategies.NAME,
            strategyClasses.EXTERNAL_ID,
            strategyClasses.NAME,
            contributionsField,
            withdrawalsField
        )
            .from(fundHoldings)
            .join(wallets).on(wallets.ID.eq(fundHoldings.WALLET_ID))
            .join(holdingTypes).on(holdingTypes.ID.eq(fundHoldings.HOLDING_TYPE_ID))
            .leftJoin(segments).on(segments.ID.eq(fundHoldings.SEGMENT_ID))
            .join(strategyClasses).on(strategyClasses.ID.eq(fundHoldings.STRATEGY_CLASS_ID))
            .join(strategies).on(strategies.ID.eq(strategyClasses.STRATEGY_ID))
            .where(fundHoldings.WALLET_ID.eq(walletInternalId))
            .orderBy(fundHoldings.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { rec ->
                FundHoldingResponse(
                    id = rec.get(fundHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    typeId = rec.get(holdingTypes.EXTERNAL_ID)!!,
                    typeName = rec.get(holdingTypes.NAME)!!,
                    segmentId = rec.get(segments.EXTERNAL_ID),
                    segmentName = rec.get(segments.NAME),
                    name = rec.get(fundHoldings.NAME)!!,
                    currentValue = rec.get(fundHoldings.CURRENT_VALUE),
                    administrationFeeRate = rec.get(fundHoldings.ADMINISTRATION_FEE_RATE),
                    performanceFeeRate = rec.get(fundHoldings.PERFORMANCE_FEE_RATE),
                    frozen = rec.get(fundHoldings.FROZEN)!!,
                    strategyId = rec.get(strategies.EXTERNAL_ID)!!,
                    strategyName = rec.get(strategies.NAME)!!,
                    strategyClassId = rec.get(strategyClasses.EXTERNAL_ID)!!,
                    strategyClassName = rec.get(strategyClasses.NAME)!!,
                    contributions = rec.get(contributionsField),
                    withdrawals = rec.get(withdrawalsField),
                )
            }

        val total = dsl.fetchCount(
            dsl.selectFrom(FUND_HOLDINGS).where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(
        walletInternalId: Long,
        typeInternalId: Long,
        segmentInternalId: Long?,
        strategyClassInternalId: Long?,
        name: String,
        currentValue: BigDecimal?,
        administrationFeeRate: BigDecimal?,
        performanceFeeRate: BigDecimal?,
        contribution: ContributionCreateRequest
    ): FundHoldingResponse {
        val holdingId = dsl.insertInto(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.WALLET_ID, walletInternalId)
            .set(FUND_HOLDINGS.HOLDING_TYPE_ID, typeInternalId)
            .set(FUND_HOLDINGS.SEGMENT_ID, segmentInternalId)
            .set(FUND_HOLDINGS.STRATEGY_CLASS_ID, strategyClassInternalId)
            .set(FUND_HOLDINGS.NAME, name)
            .set(FUND_HOLDINGS.CURRENT_VALUE, currentValue)
            .set(FUND_HOLDINGS.ADMINISTRATION_FEE_RATE, administrationFeeRate)
            .set(FUND_HOLDINGS.PERFORMANCE_FEE_RATE, performanceFeeRate)
            .returning(FUND_HOLDINGS.ID)
            .fetchSingle(FUND_HOLDINGS.ID)!!

        dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holdingId)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, contribution.contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, contribution.amount)
            .execute()

        return findByInternalId(holdingId, walletInternalId)!!
    }

    fun updateSegment(walletInternalId: Long, externalId: UUID, segmentInternalId: Long?): FundHoldingResponse? {
        val holdingId = dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.SEGMENT_ID, segmentInternalId)
            .set(FUND_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .returning(FUND_HOLDINGS.ID)
            .fetchOne(FUND_HOLDINGS.ID) ?: return null

        return findByInternalId(holdingId, walletInternalId)
    }

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

    fun isFrozen(internalId: Long): Boolean =
        dsl.select(FUND_HOLDINGS.FROZEN)
            .from(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.ID.eq(internalId))
            .fetchSingle(FUND_HOLDINGS.FROZEN)!!

    fun update(
        walletInternalId: Long,
        externalId: UUID,
        typeInternalId: Long?,
        name: String?,
        currentValue: BigDecimal?,
        administrationFeeRate: BigDecimal?,
        performanceFeeRate: BigDecimal?,
        frozen: Boolean?,
    ): FundHoldingResponse? {
        val existing = dsl.selectFrom(FUND_HOLDINGS)
            .where(FUND_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(FUND_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        dsl.update(FUND_HOLDINGS)
            .set(FUND_HOLDINGS.HOLDING_TYPE_ID, typeInternalId ?: existing.holdingTypeId!!)
            .set(FUND_HOLDINGS.NAME, name ?: existing.name!!)
            .set(FUND_HOLDINGS.CURRENT_VALUE, currentValue ?: existing.currentValue)
            .set(FUND_HOLDINGS.ADMINISTRATION_FEE_RATE, administrationFeeRate ?: existing.administrationFeeRate)
            .set(FUND_HOLDINGS.PERFORMANCE_FEE_RATE, performanceFeeRate ?: existing.performanceFeeRate)
            .set(FUND_HOLDINGS.FROZEN, frozen ?: existing.frozen!!)
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
        val holdingTypes = HOLDING_TYPES.`as`("holding_types")
        val segments = SEGMENTS.`as`("segments")
        val fundHoldings = FUND_HOLDINGS.`as`("fund_holdings")
        val strategyClasses = STRATEGY_CLASSES.`as`("strategy_classes")
        val strategies = STRATEGIES.`as`("strategies")

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

        val withdrawalsField = DSL.multiset(
            DSL.selectFrom(RESULTS)
                .where(RESULTS.FUND_HOLDING_ID.eq(fundHoldings.ID))
                .orderBy(RESULTS.RESULT_DATE)
        ).`as`("withdrawals").convertFrom { r ->
            r.map { rec ->
                WithdrawalResponse(
                    id = rec.get(RESULTS.EXTERNAL_ID)!!,
                    resultType = rec.get(RESULTS.RESULT_TYPE)!!.literal,
                    resultDate = rec.get(RESULTS.RESULT_DATE)!!,
                    quantity = rec.get(RESULTS.QUANTITY),
                    grossAmount = rec.get(RESULTS.GROSS_AMOUNT)!!,
                    fees = rec.get(RESULTS.FEES)!!,
                    taxes = rec.get(RESULTS.TAXES)!!,
                    costBasis = rec.get(RESULTS.COST_BASIS)!!,
                    netAmount = rec.get(RESULTS.NET_AMOUNT)!!,
                    profit = rec.get(RESULTS.PROFIT)!!,
                )
            }
        }

        return dsl.select(
            fundHoldings.EXTERNAL_ID,
            wallets.EXTERNAL_ID,
            holdingTypes.EXTERNAL_ID,
            holdingTypes.NAME,
            segments.EXTERNAL_ID,
            segments.NAME,
            fundHoldings.NAME,
            fundHoldings.CURRENT_VALUE,
            fundHoldings.ADMINISTRATION_FEE_RATE,
            fundHoldings.PERFORMANCE_FEE_RATE,
            fundHoldings.FROZEN,
            strategies.EXTERNAL_ID,
            strategies.NAME,
            strategyClasses.EXTERNAL_ID,
            strategyClasses.NAME,
            contributionsField,
            withdrawalsField
        )
            .from(fundHoldings)
            .join(wallets).on(wallets.ID.eq(fundHoldings.WALLET_ID))
            .join(holdingTypes).on(holdingTypes.ID.eq(fundHoldings.HOLDING_TYPE_ID))
            .leftJoin(segments).on(segments.ID.eq(fundHoldings.SEGMENT_ID))
            .join(strategyClasses).on(strategyClasses.ID.eq(fundHoldings.STRATEGY_CLASS_ID))
            .join(strategies).on(strategies.ID.eq(strategyClasses.STRATEGY_ID))
            .where(fundHoldings.ID.eq(internalId))
            .and(fundHoldings.WALLET_ID.eq(walletInternalId))
            .fetchOne { rec ->
                FundHoldingResponse(
                    id = rec.get(fundHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    typeId = rec.get(holdingTypes.EXTERNAL_ID)!!,
                    typeName = rec.get(holdingTypes.NAME)!!,
                    segmentId = rec.get(segments.EXTERNAL_ID),
                    segmentName = rec.get(segments.NAME),
                    name = rec.get(fundHoldings.NAME)!!,
                    currentValue = rec.get(fundHoldings.CURRENT_VALUE),
                    administrationFeeRate = rec.get(fundHoldings.ADMINISTRATION_FEE_RATE),
                    performanceFeeRate = rec.get(fundHoldings.PERFORMANCE_FEE_RATE),
                    frozen = rec.get(fundHoldings.FROZEN)!!,
                    strategyId = rec.get(strategies.EXTERNAL_ID)!!,
                    strategyName = rec.get(strategies.NAME)!!,
                    strategyClassId = rec.get(strategyClasses.EXTERNAL_ID)!!,
                    strategyClassName = rec.get(strategyClasses.NAME)!!,
                    contributions = rec.get(contributionsField),
                    withdrawals = rec.get(withdrawalsField),
                )
            }
    }
}
