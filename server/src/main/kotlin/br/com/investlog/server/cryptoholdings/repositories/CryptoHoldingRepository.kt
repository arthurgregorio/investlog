package br.com.investlog.server.cryptoholdings.repositories

import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.HOLDING_TYPES
import br.com.investlog.server.jooq.finances.tables.references.RESULTS
import br.com.investlog.server.jooq.finances.tables.references.SEGMENTS
import br.com.investlog.server.jooq.finances.tables.references.STRATEGIES
import br.com.investlog.server.jooq.finances.tables.references.STRATEGY_CLASSES
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.results.rest.payloads.WithdrawalResponse
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class CryptoHoldingRepository(
    private val dsl: DSLContext
) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<CryptoHoldingResponse> {
        val wallets = WALLETS.`as`("wallets")
        val holdingTypes = HOLDING_TYPES.`as`("holding_types")
        val segments = SEGMENTS.`as`("segments")
        val cryptoHoldings = CRYPTO_HOLDINGS.`as`("crypto_holdings")
        val strategyClasses = STRATEGY_CLASSES.`as`("strategy_classes")
        val strategies = STRATEGIES.`as`("strategies")

        val lotsField = DSL.multiset(
            DSL.selectFrom(CRYPTO_LOTS)
                .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(cryptoHoldings.ID))
                .orderBy(CRYPTO_LOTS.LOT_DATE)
        ).`as`("lots").convertFrom { r ->
            r.map { rec ->
                LotResponse(
                    id = rec.get(CRYPTO_LOTS.EXTERNAL_ID)!!,
                    lotDate = rec.get(CRYPTO_LOTS.LOT_DATE)!!,
                    quantity = rec.get(CRYPTO_LOTS.QUANTITY)!!,
                    price = rec.get(CRYPTO_LOTS.PRICE)!!,
                )
            }
        }

        val withdrawalsField = DSL.multiset(
            DSL.selectFrom(RESULTS)
                .where(RESULTS.CRYPTO_HOLDING_ID.eq(cryptoHoldings.ID))
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
            cryptoHoldings.EXTERNAL_ID,
            wallets.EXTERNAL_ID,
            holdingTypes.EXTERNAL_ID,
            holdingTypes.NAME,
            segments.EXTERNAL_ID,
            segments.NAME,
            cryptoHoldings.TICKER,
            cryptoHoldings.NAME,
            cryptoHoldings.CURRENT_PRICE,
            cryptoHoldings.FROZEN,
            strategies.EXTERNAL_ID,
            strategies.NAME,
            strategyClasses.EXTERNAL_ID,
            strategyClasses.NAME,
            lotsField,
            withdrawalsField
        )
            .from(cryptoHoldings)
            .join(wallets).on(wallets.ID.eq(cryptoHoldings.WALLET_ID))
            .leftJoin(holdingTypes).on(holdingTypes.ID.eq(cryptoHoldings.HOLDING_TYPE_ID))
            .leftJoin(segments).on(segments.ID.eq(cryptoHoldings.SEGMENT_ID))
            .join(strategyClasses).on(strategyClasses.ID.eq(cryptoHoldings.STRATEGY_CLASS_ID))
            .join(strategies).on(strategies.ID.eq(strategyClasses.STRATEGY_ID))
            .where(cryptoHoldings.WALLET_ID.eq(walletInternalId))
            .orderBy(cryptoHoldings.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { rec ->
                CryptoHoldingResponse(
                    id = rec.get(cryptoHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    typeId = rec.get(holdingTypes.EXTERNAL_ID),
                    typeName = rec.get(holdingTypes.NAME),
                    segmentId = rec.get(segments.EXTERNAL_ID),
                    segmentName = rec.get(segments.NAME),
                    ticker = rec.get(cryptoHoldings.TICKER)!!,
                    name = rec.get(cryptoHoldings.NAME)!!,
                    currentPrice = rec.get(cryptoHoldings.CURRENT_PRICE),
                    frozen = rec.get(cryptoHoldings.FROZEN)!!,
                    strategyId = rec.get(strategies.EXTERNAL_ID)!!,
                    strategyName = rec.get(strategies.NAME)!!,
                    strategyClassId = rec.get(strategyClasses.EXTERNAL_ID)!!,
                    strategyClassName = rec.get(strategyClasses.NAME)!!,
                    lots = rec.get(lotsField),
                    withdrawals = rec.get(withdrawalsField),
                )
            }

        val total = dsl.fetchCount(
            dsl.selectFrom(CRYPTO_HOLDINGS).where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(
        walletInternalId: Long,
        typeInternalId: Long?,
        segmentInternalId: Long?,
        strategyClassInternalId: Long?,
        ticker: String,
        name: String,
        currentPrice: BigDecimal?,
        lot: LotCreateRequest
    ): CryptoHoldingResponse {
        val holdingId = dsl.insertInto(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.WALLET_ID, walletInternalId)
            .set(CRYPTO_HOLDINGS.HOLDING_TYPE_ID, typeInternalId)
            .set(CRYPTO_HOLDINGS.SEGMENT_ID, segmentInternalId)
            .set(CRYPTO_HOLDINGS.STRATEGY_CLASS_ID, strategyClassInternalId)
            .set(CRYPTO_HOLDINGS.TICKER, ticker.uppercase())
            .set(CRYPTO_HOLDINGS.NAME, name)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, currentPrice)
            .returning(CRYPTO_HOLDINGS.ID)
            .fetchSingle(CRYPTO_HOLDINGS.ID)!!

        dsl.insertInto(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holdingId)
            .set(CRYPTO_LOTS.LOT_DATE, lot.lotDate)
            .set(CRYPTO_LOTS.QUANTITY, lot.quantity)
            .set(CRYPTO_LOTS.PRICE, lot.price)
            .execute()

        return findByInternalId(holdingId, walletInternalId)!!
    }

    fun updateSegment(walletInternalId: Long, externalId: UUID, segmentInternalId: Long?): CryptoHoldingResponse? {
        val holdingId = dsl.update(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.SEGMENT_ID, segmentInternalId)
            .set(CRYPTO_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .returning(CRYPTO_HOLDINGS.ID)
            .fetchOne(CRYPTO_HOLDINGS.ID) ?: return null

        return findByInternalId(holdingId, walletInternalId)
    }

    fun findByExternalId(walletInternalId: Long, externalId: UUID): CryptoHoldingResponse? {
        val internalId = findInternalId(walletInternalId, externalId) ?: return null
        return findByInternalId(internalId, walletInternalId)
    }

    fun findInternalId(walletInternalId: Long, externalId: UUID): Long? =
        dsl.select(CRYPTO_HOLDINGS.ID)
            .from(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne(CRYPTO_HOLDINGS.ID)

    fun isFrozen(internalId: Long): Boolean =
        dsl.select(CRYPTO_HOLDINGS.FROZEN)
            .from(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.ID.eq(internalId))
            .fetchSingle(CRYPTO_HOLDINGS.FROZEN)!!

    fun update(
        walletInternalId: Long,
        externalId: UUID,
        typeInternalId: Long?,
        ticker: String?,
        name: String?,
        currentPrice: BigDecimal?,
        frozen: Boolean?,
    ): CryptoHoldingResponse? {
        val existing = dsl.selectFrom(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        dsl.update(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.HOLDING_TYPE_ID, typeInternalId ?: existing.holdingTypeId)
            .set(CRYPTO_HOLDINGS.TICKER, (ticker ?: existing.ticker!!).uppercase())
            .set(CRYPTO_HOLDINGS.NAME, name ?: existing.name!!)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, currentPrice ?: existing.currentPrice)
            .set(CRYPTO_HOLDINGS.FROZEN, frozen ?: existing.frozen!!)
            .set(CRYPTO_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(CRYPTO_HOLDINGS.ID.eq(existing.id))
            .execute()

        return findByInternalId(existing.id!!, walletInternalId)
    }

    fun deleteByExternalId(walletInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun findByInternalId(internalId: Long, walletInternalId: Long): CryptoHoldingResponse? {
        val wallets = WALLETS.`as`("wallets")
        val holdingTypes = HOLDING_TYPES.`as`("holding_types")
        val segments = SEGMENTS.`as`("segments")
        val cryptoHoldings = CRYPTO_HOLDINGS.`as`("crypto_holdings")
        val strategyClasses = STRATEGY_CLASSES.`as`("strategy_classes")
        val strategies = STRATEGIES.`as`("strategies")

        val lotsField = DSL.multiset(
            DSL.selectFrom(CRYPTO_LOTS)
                .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(cryptoHoldings.ID))
                .orderBy(CRYPTO_LOTS.LOT_DATE)
        ).`as`("lots").convertFrom { r ->
            r.map { rec ->
                LotResponse(
                    id = rec.get(CRYPTO_LOTS.EXTERNAL_ID)!!,
                    lotDate = rec.get(CRYPTO_LOTS.LOT_DATE)!!,
                    quantity = rec.get(CRYPTO_LOTS.QUANTITY)!!,
                    price = rec.get(CRYPTO_LOTS.PRICE)!!,
                )
            }
        }

        val withdrawalsField = DSL.multiset(
            DSL.selectFrom(RESULTS)
                .where(RESULTS.CRYPTO_HOLDING_ID.eq(cryptoHoldings.ID))
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
            cryptoHoldings.EXTERNAL_ID,
            wallets.EXTERNAL_ID,
            holdingTypes.EXTERNAL_ID,
            holdingTypes.NAME,
            segments.EXTERNAL_ID,
            segments.NAME,
            cryptoHoldings.TICKER,
            cryptoHoldings.NAME,
            cryptoHoldings.CURRENT_PRICE,
            cryptoHoldings.FROZEN,
            strategies.EXTERNAL_ID,
            strategies.NAME,
            strategyClasses.EXTERNAL_ID,
            strategyClasses.NAME,
            lotsField,
            withdrawalsField
        )
            .from(cryptoHoldings)
            .join(wallets).on(wallets.ID.eq(cryptoHoldings.WALLET_ID))
            .leftJoin(holdingTypes).on(holdingTypes.ID.eq(cryptoHoldings.HOLDING_TYPE_ID))
            .leftJoin(segments).on(segments.ID.eq(cryptoHoldings.SEGMENT_ID))
            .join(strategyClasses).on(strategyClasses.ID.eq(cryptoHoldings.STRATEGY_CLASS_ID))
            .join(strategies).on(strategies.ID.eq(strategyClasses.STRATEGY_ID))
            .where(cryptoHoldings.ID.eq(internalId))
            .and(cryptoHoldings.WALLET_ID.eq(walletInternalId))
            .fetchOne { rec ->
                CryptoHoldingResponse(
                    id = rec.get(cryptoHoldings.EXTERNAL_ID)!!,
                    walletId = rec.get(wallets.EXTERNAL_ID)!!,
                    typeId = rec.get(holdingTypes.EXTERNAL_ID),
                    typeName = rec.get(holdingTypes.NAME),
                    segmentId = rec.get(segments.EXTERNAL_ID),
                    segmentName = rec.get(segments.NAME),
                    ticker = rec.get(cryptoHoldings.TICKER)!!,
                    name = rec.get(cryptoHoldings.NAME)!!,
                    currentPrice = rec.get(cryptoHoldings.CURRENT_PRICE),
                    frozen = rec.get(cryptoHoldings.FROZEN)!!,
                    strategyId = rec.get(strategies.EXTERNAL_ID)!!,
                    strategyName = rec.get(strategies.NAME)!!,
                    strategyClassId = rec.get(strategyClasses.EXTERNAL_ID)!!,
                    strategyClassName = rec.get(strategyClasses.NAME)!!,
                    lots = rec.get(lotsField),
                    withdrawals = rec.get(withdrawalsField),
                )
            }
    }
}
