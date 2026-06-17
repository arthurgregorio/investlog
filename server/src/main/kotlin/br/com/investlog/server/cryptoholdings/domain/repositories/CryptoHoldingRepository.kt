package br.com.investlog.server.cryptoholdings.domain.repositories

import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
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
class CryptoHoldingRepository(private val dsl: DSLContext) {

    fun findAll(walletInternalId: Long, pageable: Pageable): PagedModel<CryptoHoldingResponse> {
        val w = WALLETS.`as`("w")
        val ch = CRYPTO_HOLDINGS.`as`("ch")

        val lotsField = DSL.multiset(
            DSL.selectFrom(CRYPTO_LOTS)
                .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(ch.ID))
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

        val content = dsl.select(ch.EXTERNAL_ID, w.EXTERNAL_ID, ch.TICKER, ch.NAME, ch.CURRENT_PRICE, lotsField)
            .from(ch)
            .join(w).on(w.ID.eq(ch.WALLET_ID))
            .where(ch.WALLET_ID.eq(walletInternalId))
            .orderBy(ch.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { rec ->
                CryptoHoldingResponse(
                    id = rec.get(ch.EXTERNAL_ID)!!,
                    walletId = rec.get(w.EXTERNAL_ID)!!,
                    ticker = rec.get(ch.TICKER)!!,
                    name = rec.get(ch.NAME)!!,
                    currentPrice = rec.get(ch.CURRENT_PRICE),
                    lots = rec.get(lotsField),
                )
            }

        val total = dsl.fetchCount(
            dsl.selectFrom(CRYPTO_HOLDINGS).where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
        )

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(walletInternalId: Long, ticker: String, name: String, currentPrice: BigDecimal?, lot: LotCreateRequest): CryptoHoldingResponse {
        val holding = dsl.insertInto(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.WALLET_ID, walletInternalId)
            .set(CRYPTO_HOLDINGS.TICKER, ticker.uppercase())
            .set(CRYPTO_HOLDINGS.NAME, name)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, currentPrice)
            .returning()
            .fetchSingle()

        val lotRec = dsl.insertInto(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holding.id)
            .set(CRYPTO_LOTS.LOT_DATE, lot.lotDate)
            .set(CRYPTO_LOTS.QUANTITY, lot.quantity)
            .set(CRYPTO_LOTS.PRICE, lot.price)
            .returning()
            .fetchSingle()

        val walletExternalId = dsl.select(WALLETS.EXTERNAL_ID).from(WALLETS)
            .where(WALLETS.ID.eq(walletInternalId)).fetchSingle(WALLETS.EXTERNAL_ID)!!

        return CryptoHoldingResponse(
            id = holding.externalId!!,
            walletId = walletExternalId,
            ticker = holding.ticker!!,
            name = holding.name!!,
            currentPrice = holding.currentPrice,
            lots = listOf(
                LotResponse(
                    id = lotRec.externalId!!,
                    lotDate = lotRec.lotDate!!,
                    quantity = lotRec.quantity!!,
                    price = lotRec.price!!,
                )
            ),
        )
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

    fun update(walletInternalId: Long, externalId: UUID, ticker: String?, name: String?, currentPrice: BigDecimal?): CryptoHoldingResponse? {
        val existing = dsl.selectFrom(CRYPTO_HOLDINGS)
            .where(CRYPTO_HOLDINGS.WALLET_ID.eq(walletInternalId))
            .and(CRYPTO_HOLDINGS.EXTERNAL_ID.eq(externalId))
            .fetchOne() ?: return null

        dsl.update(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.TICKER, (ticker ?: existing.ticker!!).uppercase())
            .set(CRYPTO_HOLDINGS.NAME, name ?: existing.name!!)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, currentPrice ?: existing.currentPrice)
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
        val w = WALLETS.`as`("w")
        val ch = CRYPTO_HOLDINGS.`as`("ch")

        val lotsField = DSL.multiset(
            DSL.selectFrom(CRYPTO_LOTS)
                .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(ch.ID))
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

        return dsl.select(ch.EXTERNAL_ID, w.EXTERNAL_ID, ch.TICKER, ch.NAME, ch.CURRENT_PRICE, lotsField)
            .from(ch)
            .join(w).on(w.ID.eq(ch.WALLET_ID))
            .where(ch.ID.eq(internalId))
            .and(ch.WALLET_ID.eq(walletInternalId))
            .fetchOne { rec ->
                CryptoHoldingResponse(
                    id = rec.get(ch.EXTERNAL_ID)!!,
                    walletId = rec.get(w.EXTERNAL_ID)!!,
                    ticker = rec.get(ch.TICKER)!!,
                    name = rec.get(ch.NAME)!!,
                    currentPrice = rec.get(ch.CURRENT_PRICE),
                    lots = rec.get(lotsField),
                )
            }
    }
}
