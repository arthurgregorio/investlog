package br.com.investlog.server.walletledger.services

import br.com.investlog.server.cash.repositories.CashReplayEvent
import br.com.investlog.server.cash.repositories.CashRepository
import br.com.investlog.server.shared.utils.pagedModelOf
import br.com.investlog.server.walletledger.repositories.WalletLedgerRepository
import br.com.investlog.server.walletledger.repositories.WalletLedgerRow
import br.com.investlog.server.walletledger.rest.payloads.WalletLedgerEntryResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class WalletLedgerService(
    private val walletLedgerRepository: WalletLedgerRepository,
    private val cashRepository: CashRepository,
) {

    fun findByWallet(walletId: Long, pageable: Pageable): PagedModel<WalletLedgerEntryResponse> {
        val page = walletLedgerRepository.findByWallet(walletId, pageable)
        val replay = cashRepository.replay(walletId)
        val replayById = replay.associateBy { event -> event.eventId }

        val content = page.rows.map { row ->
            val cashEvent = replayById[row.id]
            row.toResponse(
                cashEvent = cashEvent,
                cashBalance = cashEvent?.balance ?: balanceAt(replay, row),
            )
        }

        return pagedModelOf(content, pageable, page.total)
    }

    private fun balanceAt(replay: List<CashReplayEvent>, row: WalletLedgerRow): BigDecimal =
        replay.lastOrNull { event ->
            event.eventDate < row.date || (event.eventDate == row.date && event.createdAt <= row.createdAt)
        }?.balance ?: BigDecimal.ZERO

    private fun WalletLedgerRow.toResponse(cashEvent: CashReplayEvent?, cashBalance: BigDecimal) =
        WalletLedgerEntryResponse(
            id = id,
            date = date,
            type = type,
            kind = kind,
            holdingId = holdingId,
            holdingName = holdingName,
            ticker = ticker,
            quantity = quantity,
            amount = amount,
            profit = profit,
            counterpartName = counterpartName,
            originalAmount = originalAmount,
            originalCurrency = originalCurrency,
            rate = rate,
            cashUsed = cashEvent?.cashUsed,
            newMoney = cashEvent?.newMoney,
            cashBalance = cashBalance,
        )
}
