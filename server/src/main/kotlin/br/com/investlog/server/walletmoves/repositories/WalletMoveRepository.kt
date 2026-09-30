package br.com.investlog.server.walletmoves.repositories

import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.jooq.finances.tables.references.WALLET_MOVES
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.walletmoves.rest.payloads.WalletMoveDirection
import br.com.investlog.server.walletmoves.rest.payloads.WalletMoveResponse
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
class WalletMoveRepository(private val dsl: DSLContext) {

    fun findWallet(userId: Long, externalId: UUID): MoveWallet? =
        dsl.select(WALLETS.ID, WALLETS.KIND, WALLETS.CURRENCY)
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne { record ->
                MoveWallet(
                    id = record.get(WALLETS.ID)!!,
                    kind = record.get(WALLETS.KIND)!!,
                    currency = record.get(WALLETS.CURRENCY)!!,
                )
            }

    fun insert(
        origin: MoveWallet,
        destination: MoveWallet,
        holding: MovableHolding,
        destinationHolding: HoldingReference,
        quantity: BigDecimal?,
        movedAt: LocalDate,
    ) {
        dsl.insertInto(WALLET_MOVES)
            .set(WALLET_MOVES.ORIGIN_WALLET_ID, origin.id)
            .set(WALLET_MOVES.DESTINATION_WALLET_ID, destination.id)
            .set(WALLET_MOVES.KIND, holding.kind)
            .set(WALLET_MOVES.ORIGIN_HOLDING_EXTERNAL_ID, holding.externalId)
            .set(WALLET_MOVES.DESTINATION_HOLDING_EXTERNAL_ID, destinationHolding.externalId)
            .set(WALLET_MOVES.HOLDING_NAME, holding.name)
            .set(WALLET_MOVES.TICKER, holding.ticker)
            .set(WALLET_MOVES.QUANTITY, quantity)
            .set(WALLET_MOVES.MOVED_AT, movedAt)
            .execute()
    }

    fun findByWallet(wallet: MoveWallet, pageable: Pageable): PagedModel<WalletMoveResponse> {
        val walletMoves = WALLET_MOVES.`as`("wallet_moves")
        val originWallets = WALLETS.`as`("origin_wallets")
        val destinationWallets = WALLETS.`as`("destination_wallets")

        val touchesWallet = walletMoves.ORIGIN_WALLET_ID.eq(wallet.id)
            .or(walletMoves.DESTINATION_WALLET_ID.eq(wallet.id))

        val content = dsl.select(
            walletMoves.EXTERNAL_ID,
            walletMoves.MOVED_AT,
            walletMoves.ORIGIN_WALLET_ID,
            walletMoves.KIND,
            walletMoves.HOLDING_NAME,
            walletMoves.TICKER,
            walletMoves.QUANTITY,
            originWallets.EXTERNAL_ID,
            originWallets.NAME,
            destinationWallets.EXTERNAL_ID,
            destinationWallets.NAME,
        )
            .from(walletMoves)
            .leftJoin(originWallets).on(originWallets.ID.eq(walletMoves.ORIGIN_WALLET_ID))
            .leftJoin(destinationWallets).on(destinationWallets.ID.eq(walletMoves.DESTINATION_WALLET_ID))
            .where(touchesWallet)
            .orderBy(walletMoves.MOVED_AT.desc(), walletMoves.ID.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                WalletMoveResponse(
                    id = record.get(walletMoves.EXTERNAL_ID)!!,
                    movedAt = record.get(walletMoves.MOVED_AT)!!,
                    direction = if (record.get(walletMoves.ORIGIN_WALLET_ID) == wallet.id) {
                        WalletMoveDirection.OUT
                    } else {
                        WalletMoveDirection.IN
                    },
                    kind = record.get(walletMoves.KIND)!!.literal,
                    holdingName = record.get(walletMoves.HOLDING_NAME)!!,
                    ticker = record.get(walletMoves.TICKER),
                    quantity = record.get(walletMoves.QUANTITY),
                    originWalletId = record.get(originWallets.EXTERNAL_ID),
                    originWalletName = record.get(originWallets.NAME),
                    destinationWalletId = record.get(destinationWallets.EXTERNAL_ID),
                    destinationWalletName = record.get(destinationWallets.NAME),
                )
            }

        val total = dsl.fetchCount(dsl.selectFrom(walletMoves).where(touchesWallet))

        return pagedModelOf(content, pageable, total.toLong())
    }
}
