package br.com.investlog.server.reinvestments.services

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.ResultType
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.reinvestments.repositories.ReinvestmentHolding
import br.com.investlog.server.reinvestments.repositories.ReinvestmentRepository
import br.com.investlog.server.reinvestments.rest.payloads.ReinvestmentRequest
import br.com.investlog.server.results.repositories.RecordedResult
import br.com.investlog.server.results.services.PositionExitService
import br.com.investlog.server.shared.exceptions.InvalidReinvestmentException
import br.com.investlog.server.shared.exceptions.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ReinvestmentService(
    private val reinvestmentRepository: ReinvestmentRepository,
    private val positionExitService: PositionExitService,
) {

    companion object {
        private const val QUANTITY_SCALE = 20
    }

    @Transactional
    fun reinvest(userId: Long, request: ReinvestmentRequest) {
        val sourceKind = request.sourceKind!!
        val destinationKind = request.destinationKind!!

        if (sourceKind == destinationKind && request.sourceHoldingId == request.destinationHoldingId) {
            throw InvalidReinvestmentException("O investimento de destino deve ser diferente do investimento de origem")
        }

        val source = reinvestmentRepository.findHolding(userId, sourceKind, request.sourceHoldingId!!)
            ?: throw NotFoundException("Investimento de origem não encontrado")
        val destination = reinvestmentRepository.findHolding(userId, destinationKind, request.destinationHoldingId!!)
            ?: throw NotFoundException("Investimento de destino não encontrado")

        validate(source, destination)

        val destinationPrice = destinationPriceOf(destination)
        val fees = request.fees ?: BigDecimal.ZERO
        val taxes = request.taxes ?: BigDecimal.ZERO
        val reinvestmentDate = request.reinvestmentDate!!

        val result = if (sourceKind == WalletKind.FUNDS) {
            exitFund(source, request, reinvestmentDate, fees, taxes)
        } else {
            exitHolding(source, request, reinvestmentDate, fees, taxes)
        }

        if (destinationPrice == null) {
            reinvestmentRepository.addContribution(destination.position.holdingId, reinvestmentDate, result.netAmount)
        } else {
            reinvestmentRepository.addLot(
                kind = destinationKind,
                holdingId = destination.position.holdingId,
                lotDate = reinvestmentDate,
                quantity = result.netAmount.divide(destinationPrice, QUANTITY_SCALE, RoundingMode.HALF_UP).normalized(),
                price = destinationPrice,
            )
        }

        reinvestmentRepository.insert(result.id, destination.position, result.netAmount, reinvestmentDate)
    }

    private fun validate(source: ReinvestmentHolding, destination: ReinvestmentHolding) {
        if (source.position.status == HoldingStatus.COMPLETED) {
            throw InvalidReinvestmentException("${source.name} já foi totalmente resgatado e não pode ser reinvestido")
        }

        if (destination.position.status == HoldingStatus.COMPLETED) {
            throw InvalidReinvestmentException("${destination.name} está encerrado e não pode receber um reinvestimento")
        }

        if (source.walletCurrency != destination.walletCurrency) {
            throw InvalidReinvestmentException(
                "Os investimentos de origem e destino devem estar em carteiras com a mesma moeda"
            )
        }
    }

    private fun destinationPriceOf(destination: ReinvestmentHolding): BigDecimal? {
        if (destination.position.kind == WalletKind.FUNDS) {
            return null
        }

        return destination.currentPrice?.takeIf { price -> price.signum() > 0 }
            ?: throw InvalidReinvestmentException(
                "${destination.name} não tem preço atual. Defina o preço atual do investimento de destino antes de reinvestir nele"
            )
    }

    private fun exitHolding(
        source: ReinvestmentHolding,
        request: ReinvestmentRequest,
        reinvestmentDate: LocalDate,
        fees: BigDecimal,
        taxes: BigDecimal,
    ): RecordedResult {
        val quantity = request.quantity
            ?: throw InvalidReinvestmentException("Informe a quantidade a reinvestir")
        val unitPrice = request.unitPrice
            ?: throw InvalidReinvestmentException("Informe o preço unitário de venda")
        val remainingQuantity = source.position.quantity ?: BigDecimal.ZERO

        if (quantity > remainingQuantity) {
            throw InvalidReinvestmentException(
                "A quantidade reinvestida não pode ser maior que a quantidade restante de $remainingQuantity"
            )
        }

        requirePositiveNet(quantity.multiply(unitPrice), fees, taxes)

        return positionExitService.exitHolding(
            position = source.position,
            resultType = ResultType.REINVESTMENT,
            resultDate = reinvestmentDate,
            quantity = quantity,
            unitPrice = unitPrice,
            fees = fees,
            taxes = taxes,
        )
    }

    private fun exitFund(
        source: ReinvestmentHolding,
        request: ReinvestmentRequest,
        reinvestmentDate: LocalDate,
        fees: BigDecimal,
        taxes: BigDecimal,
    ): RecordedResult {
        val amount = request.amount
            ?: throw InvalidReinvestmentException("Informe o valor a reinvestir")
        val currentValue = source.position.currentValue ?: BigDecimal.ZERO

        if (amount > currentValue) {
            throw InvalidReinvestmentException(
                "O valor reinvestido não pode ser maior que o valor atual do fundo, de $currentValue"
            )
        }

        requirePositiveNet(amount, fees, taxes)

        return positionExitService.exitFund(
            position = source.position,
            resultType = ResultType.REINVESTMENT,
            resultDate = reinvestmentDate,
            amount = amount,
            fees = fees,
            taxes = taxes,
        )
    }

    private fun requirePositiveNet(grossAmount: BigDecimal, fees: BigDecimal, taxes: BigDecimal) {
        if (grossAmount.subtract(fees).subtract(taxes).signum() <= 0) {
            throw InvalidReinvestmentException("Taxas e impostos não podem consumir todo o valor reinvestido")
        }
    }

    private fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros().let { value ->
        if (value.scale() < 0) value.setScale(0) else value
    }
}
