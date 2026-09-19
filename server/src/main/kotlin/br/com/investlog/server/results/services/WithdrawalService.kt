package br.com.investlog.server.results.services

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.ResultType
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.results.repositories.HoldingPosition
import br.com.investlog.server.results.repositories.ResultRepository
import br.com.investlog.server.results.rest.payloads.FundWithdrawalRequest
import br.com.investlog.server.results.rest.payloads.HoldingWithdrawalRequest
import br.com.investlog.server.shared.exceptions.InvalidWithdrawalException
import br.com.investlog.server.shared.exceptions.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private const val COST_BASIS_SCALE = 10

@Service
@Transactional(readOnly = true)
class WithdrawalService(private val resultRepository: ResultRepository) {

    @Transactional
    fun withdrawFromHolding(
        userId: Long,
        walletId: UUID,
        holdingId: UUID,
        kind: WalletKind,
        request: HoldingWithdrawalRequest,
    ) {
        val position = activePosition(userId, walletId, holdingId, kind)

        val quantity = request.quantity!!
        val remainingQuantity = position.quantity ?: BigDecimal.ZERO

        if (quantity > remainingQuantity) {
            throw InvalidWithdrawalException(
                "A quantidade resgatada não pode ser maior que a quantidade restante de $remainingQuantity"
            )
        }

        val grossAmount = quantity.multiply(request.unitPrice!!)
        val costBasis = position.costBasis
            .divide(remainingQuantity, COST_BASIS_SCALE, RoundingMode.HALF_UP)
            .multiply(quantity)

        resultRepository.insert(
            position = position,
            resultType = ResultType.WITHDRAWAL,
            resultDate = request.resultDate!!,
            quantity = quantity,
            grossAmount = grossAmount,
            fees = request.fees ?: BigDecimal.ZERO,
            taxes = request.taxes ?: BigDecimal.ZERO,
            costBasis = costBasis,
        )

        if (quantity.compareTo(remainingQuantity) == 0) {
            resultRepository.markCompleted(position)
        }
    }

    @Transactional
    fun withdrawFromFund(
        userId: Long,
        walletId: UUID,
        holdingId: UUID,
        request: FundWithdrawalRequest,
    ) {
        val position = activePosition(userId, walletId, holdingId, WalletKind.FUNDS)

        val amount = request.amount!!
        val currentValue = position.currentValue ?: BigDecimal.ZERO

        if (amount > currentValue) {
            throw InvalidWithdrawalException(
                "O valor resgatado não pode ser maior que o valor atual do fundo, de $currentValue"
            )
        }

        val costBasis = position.costBasis
            .multiply(amount)
            .divide(currentValue, COST_BASIS_SCALE, RoundingMode.HALF_UP)

        resultRepository.insert(
            position = position,
            resultType = ResultType.WITHDRAWAL,
            resultDate = request.resultDate!!,
            quantity = null,
            grossAmount = amount,
            fees = request.fees ?: BigDecimal.ZERO,
            taxes = request.taxes ?: BigDecimal.ZERO,
            costBasis = costBasis,
        )

        resultRepository.reduceFundCurrentValue(position.holdingId, amount)

        if (amount.compareTo(currentValue) == 0) {
            resultRepository.markCompleted(position)
        }
    }

    private fun activePosition(
        userId: Long,
        walletId: UUID,
        holdingId: UUID,
        kind: WalletKind,
    ): HoldingPosition {
        val position = resultRepository.findPosition(userId, walletId, holdingId)
            ?: throw NotFoundException("Investimento não encontrado")

        if (position.kind != kind) {
            throw NotFoundException("Investimento não encontrado")
        }

        if (position.status == HoldingStatus.COMPLETED) {
            throw InvalidWithdrawalException("Este investimento já foi totalmente resgatado")
        }

        return position
    }
}
