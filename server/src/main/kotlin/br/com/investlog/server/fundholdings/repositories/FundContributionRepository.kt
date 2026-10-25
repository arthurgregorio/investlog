package br.com.investlog.server.fundholdings.repositories

import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.ContributionUpdateRequest
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FundContributionRepository(
    private val dsl: DSLContext
) {

    fun addContribution(holdingInternalId: Long, request: ContributionCreateRequest): ContributionResponse {
        val rec = dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holdingInternalId)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, request.contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, request.amount)
            .returning()
            .fetchSingle()
        return ContributionResponse(
            id = rec.externalId!!,
            contributionDate = rec.contributionDate!!,
            amount = rec.amount!!,
        )
    }

    fun updateContribution(
        holdingInternalId: Long,
        externalId: UUID,
        request: ContributionUpdateRequest,
    ): ContributionResponse? {
        val changes = buildMap<Field<*>, Any> {
            put(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, request.contributionDate)
            request.amount?.let { amount ->
                put(FUND_CONTRIBUTIONS.AMOUNT, amount)
                put(
                    FUND_CONTRIBUTIONS.PURCHASE_AMOUNT,
                    FUND_CONTRIBUTIONS.PURCHASE_AMOUNT.plus(DSL.`val`(amount).minus(FUND_CONTRIBUTIONS.AMOUNT)),
                )
            }
        }
        val rec = dsl.update(FUND_CONTRIBUTIONS)
            .set(changes)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holdingInternalId))
            .and(FUND_CONTRIBUTIONS.EXTERNAL_ID.eq(externalId))
            .returning()
            .fetchOne() ?: return null
        return ContributionResponse(
            id = rec.externalId!!,
            contributionDate = rec.contributionDate!!,
            amount = rec.amount!!,
        )
    }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_CONTRIBUTIONS)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holdingInternalId))
            .and(FUND_CONTRIBUTIONS.EXTERNAL_ID.eq(externalId))
            .execute()
}
