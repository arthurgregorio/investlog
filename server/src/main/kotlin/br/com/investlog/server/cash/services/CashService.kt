package br.com.investlog.server.cash.services

import br.com.investlog.server.cash.repositories.CashReplayEvent
import br.com.investlog.server.cash.repositories.CashRepository
import br.com.investlog.server.cash.repositories.CashSource
import br.com.investlog.server.cash.repositories.CashTransferRow
import br.com.investlog.server.cash.repositories.CashWallet
import br.com.investlog.server.cash.rest.payloads.CashReconciliationResponse
import br.com.investlog.server.cash.rest.payloads.CashSourceResponse
import br.com.investlog.server.cash.rest.payloads.CashSuggestionResponse
import br.com.investlog.server.cash.rest.payloads.CashTransferRequest
import br.com.investlog.server.cash.rest.payloads.CashTransferResponse
import br.com.investlog.server.cash.rest.payloads.CashWithdrawalRequest
import br.com.investlog.server.cash.rest.payloads.CashWithdrawalResponse
import br.com.investlog.server.currencyrates.repositories.CurrencyRateRepository
import br.com.investlog.server.shared.exceptions.CashNotCoveredException
import br.com.investlog.server.shared.exceptions.InvalidCashOperationException
import br.com.investlog.server.shared.exceptions.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CashService(
    private val cashRepository: CashRepository,
    private val currencyRateRepository: CurrencyRateRepository,
) {

    @Transactional
    fun transfer(userId: Long, walletId: UUID, request: CashTransferRequest): CashTransferResponse {
        val origin = requireWallet(userId, walletId)
        val destination = cashRepository.findWallet(userId, request.destinationWalletId!!)
            ?: throw NotFoundException("Carteira de destino não encontrada")

        if (origin.id == destination.id) {
            throw InvalidCashOperationException("A carteira de destino deve ser diferente da carteira de origem")
        }

        val transfer = cashRepository.insertTransfer(
            originWalletId = origin.id,
            destinationWalletId = destination.id,
            transferDate = request.transferDate!!,
            amount = request.amount!!,
            rate = rateFor(origin, destination, request.rate),
        )

        shortfallOf(origin.id)?.let { shortfall -> throw InvalidCashOperationException(messageOf(shortfall)) }

        return transfer.toResponse(origin.externalId, destination.externalId)
    }

    @Transactional
    fun deleteTransfer(userId: Long, walletId: UUID, transferId: UUID) {
        val wallet = requireWallet(userId, walletId)
        val transfer = cashRepository.findTransferTouching(wallet.id, transferId)
            ?: throw NotFoundException("Transferência não encontrada")

        cashRepository.deleteTransfer(transfer.id)

        shortfallOf(transfer.destinationWalletId)?.let { shortfall ->
            throw CashNotCoveredException(
                "Essa transferência já foi usada na carteira de destino: ${messageOf(shortfall).replaceFirstChar { it.lowercase() }}"
            )
        }
    }

    @Transactional
    fun withdrawToBank(userId: Long, walletId: UUID, request: CashWithdrawalRequest): CashWithdrawalResponse {
        val wallet = requireWallet(userId, walletId)
        val withdrawal = cashRepository.insertWithdrawal(
            walletId = wallet.id,
            withdrawalDate = request.withdrawalDate!!,
            amount = request.amount!!,
            bankName = request.bankName?.trim()?.takeIf { name -> name.isNotEmpty() },
        )

        shortfallOf(wallet.id)?.let { shortfall -> throw InvalidCashOperationException(messageOf(shortfall)) }

        return CashWithdrawalResponse(
            id = withdrawal.externalId,
            walletId = wallet.externalId,
            withdrawalDate = withdrawal.withdrawalDate,
            amount = withdrawal.amount,
            bankName = withdrawal.bankName,
        )
    }

    @Transactional
    fun deleteBankWithdrawal(userId: Long, walletId: UUID, withdrawalId: UUID) {
        val wallet = requireWallet(userId, walletId)
        val internalId = cashRepository.findWithdrawalId(wallet.id, withdrawalId)
            ?: throw NotFoundException("Saque não encontrado")

        cashRepository.deleteWithdrawal(internalId)
    }

    @Transactional
    fun markReconciled(userId: Long, walletId: UUID) {
        cashRepository.markReconciled(requireWallet(userId, walletId).id)
    }

    fun findReconciliation(userId: Long): List<CashReconciliationResponse> {
        val wallets = cashRepository.findWallets(userId)
        val replays = wallets.associate { wallet -> wallet.id to cashRepository.replay(wallet.id) }
        val rates = mutableMapOf<String, BigDecimal>()
        val rateOf = { currency: String -> rates.getOrPut(currency) { currencyRateRepository.findRateOrAnchor(currency) } }

        return wallets.filterNot { wallet -> wallet.cashReconciled }.mapNotNull { wallet ->
            val events = replays.getValue(wallet.id)
            val cash = events.lastOrNull()?.balance ?: BigDecimal.ZERO

            if (cash.signum() <= 0) return@mapNotNull null

            val lastEmptyIndex = events.indexOfLast { event -> event.balance.signum() <= 0 }
            val sourceIds = events.drop(lastEmptyIndex + 1)
                .filter { event -> event.eventType == CashReplayEvent.WITHDRAWAL }
                .map { event -> event.eventId }
            val sources = cashRepository.findSources(sourceIds)

            CashReconciliationResponse(
                walletId = wallet.externalId,
                walletName = wallet.name,
                kind = wallet.kind.literal,
                currency = wallet.currency,
                cash = cash,
                sources = sources.map { source -> source.toResponse() },
                suggestion = suggestionFor(wallet, cash, sources, wallets, replays, rateOf),
            )
        }
    }

    fun requireCoveredAfterUndo(userId: Long, walletId: UUID) {
        val wallet = requireWallet(userId, walletId)

        shortfallOf(wallet.id)?.let { shortfall ->
            throw CashNotCoveredException(
                "O caixa deste resgate já saiu da carteira em ${shortfall.date.format(DATE_FORMAT)}. " +
                    "Exclua a transferência ou o saque antes de desfazer o resgate."
            )
        }
    }

    fun shortfallOf(walletId: Long): CashShortfall? {
        val overdrawn = cashRepository.replay(walletId)
            .firstOrNull { event -> event.isFixedDebit && event.balance.signum() < 0 }
            ?: return null

        return CashShortfall(
            date = overdrawn.eventDate,
            available = overdrawn.balance + overdrawn.amount,
            currency = cashRepository.findWalletById(walletId).currency,
        )
    }

    fun messageOf(shortfall: CashShortfall): String =
        "Maior que o caixa disponível em ${shortfall.date.format(DATE_FORMAT)} (${moneyOf(shortfall.available, shortfall.currency)})"

    private fun suggestionFor(
        wallet: CashWallet,
        cash: BigDecimal,
        sources: List<CashSource>,
        wallets: List<CashWallet>,
        replays: Map<Long, List<CashReplayEvent>>,
        rateOf: (String) -> BigDecimal,
    ): CashSuggestionResponse? {
        val firstSourceDate = sources.minOfOrNull { source -> source.resultDate } ?: return null
        val lastCandidateDate = firstSourceDate.plusDays(SUGGESTION_WINDOW_DAYS)
        val tolerance = cash * SUGGESTION_TOLERANCE

        val best = wallets.filter { candidate -> candidate.id != wallet.id }
            .flatMap { candidate ->
                replays.getValue(candidate.id)
                    .filter { event -> (event.newMoney ?: BigDecimal.ZERO).signum() > 0 }
                    .filter { event -> !event.eventDate.isBefore(firstSourceDate) && !event.eventDate.isAfter(lastCandidateDate) }
                    .map { event ->
                        val converted = event.newMoney!! * rateOf(candidate.currency)
                            .divide(rateOf(wallet.currency), RATE_SCALE, RoundingMode.HALF_UP)
                        Triple(candidate, event, (converted - cash).abs())
                    }
            }
            .filter { (_, _, distance) -> distance <= tolerance }
            .minByOrNull { (_, _, distance) -> distance }
            ?: return null

        val (candidate, event) = best
        val purchase = cashRepository.findPurchases(listOf(event.eventId)).getValue(event.eventId)

        return CashSuggestionResponse(
            walletId = candidate.externalId,
            walletName = candidate.name,
            walletCurrency = candidate.currency,
            holdingName = purchase.holdingName,
            ticker = purchase.ticker,
            purchaseDate = purchase.purchaseDate,
            newMoney = event.newMoney!!,
            rate = rateOf(wallet.currency).divide(rateOf(candidate.currency), RATE_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros(),
        )
    }

    private fun rateFor(origin: CashWallet, destination: CashWallet, rate: BigDecimal?): BigDecimal {
        if (origin.currency == destination.currency) {
            if (rate != null && rate.compareTo(BigDecimal.ONE) != 0) {
                throw InvalidCashOperationException("Carteiras na mesma moeda não usam câmbio")
            }
            return BigDecimal.ONE
        }

        return rate ?: throw InvalidCashOperationException(
            "Informe o câmbio de ${origin.currency} para ${destination.currency}"
        )
    }

    private fun requireWallet(userId: Long, walletId: UUID): CashWallet =
        cashRepository.findWallet(userId, walletId) ?: throw NotFoundException("Carteira não encontrada")

    private fun moneyOf(amount: BigDecimal, currency: String): String {
        val format = NumberFormat.getCurrencyInstance(BRAZILIAN_LOCALE)
        format.currency = Currency.getInstance(currency)
        return format.format(amount).replace(' ', ' ')
    }

    private fun CashTransferRow.toResponse(originExternalId: UUID, destinationExternalId: UUID) = CashTransferResponse(
        id = externalId,
        originWalletId = originExternalId,
        destinationWalletId = destinationExternalId,
        transferDate = transferDate,
        amount = amount,
        rate = rate,
        destinationAmount = destinationAmount,
    )

    private fun CashSource.toResponse() = CashSourceResponse(
        resultId = resultId,
        date = resultDate,
        holdingName = holdingName,
        ticker = ticker,
        amount = amount,
    )

    companion object {
        private const val RATE_SCALE = 10
        private const val SUGGESTION_WINDOW_DAYS = 30L
        private val SUGGESTION_TOLERANCE = BigDecimal("0.25")
        private val BRAZILIAN_LOCALE: Locale = Locale.of("pt", "BR")
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
