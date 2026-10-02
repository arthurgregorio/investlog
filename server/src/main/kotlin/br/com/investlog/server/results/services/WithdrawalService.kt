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
import br.com.investlog.server.shared.exceptions.WithdrawalNotDeletableException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional(readOnly = true)
class WithdrawalService(
    private val resultRepository: ResultRepository,
    private val positionExitService: PositionExitService,
) {

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

        positionExitService.exitHolding(
            position = position,
            resultType = ResultType.WITHDRAWAL,
            resultDate = request.resultDate!!,
            quantity = quantity,
            unitPrice = request.unitPrice!!,
            fees = request.fees ?: BigDecimal.ZERO,
            taxes = request.taxes ?: BigDecimal.ZERO,
        )
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

        positionExitService.exitFund(
            position = position,
            resultType = ResultType.WITHDRAWAL,
            resultDate = request.resultDate!!,
            amount = amount,
            fees = request.fees ?: BigDecimal.ZERO,
            taxes = request.taxes ?: BigDecimal.ZERO,
        )
    }

    @Transactional
    fun deleteWithdrawal(
        userId: Long,
        walletId: UUID,
        holdingId: UUID,
        kind: WalletKind,
        resultId: UUID,
    ) {
        val position = resultRepository.findPosition(userId, walletId, holdingId)
            ?: throw NotFoundException("Investimento não encontrado")

        if (position.kind != kind) {
            throw NotFoundException("Investimento não encontrado")
        }

        val result = resultRepository.findResult(position, resultId)
            ?: throw NotFoundException("Resgate não encontrado")

        if (result.resultType == ResultType.REINVESTMENT) {
            throw WithdrawalNotDeletableException("Um reinvestimento não pode ser desfeito.")
        }

        val mostRecentResultId = resultRepository.findMostRecentResultId(position)
        if (mostRecentResultId != result.id) {
            throw WithdrawalNotDeletableException("Apenas o resgate mais recente pode ser desfeito.")
        }

        resultRepository.delete(result.id)

        if (position.status == HoldingStatus.COMPLETED) {
            resultRepository.markActive(position)
        }

        if (position.kind == WalletKind.FUNDS) {
            resultRepository.increaseFundCurrentValue(position.holdingId, result.grossAmount)
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
