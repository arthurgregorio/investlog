package br.com.investlog.server.walletmoves.services

import br.com.investlog.server.jooq.finances.enums.HoldingStatus
import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.shared.exceptions.InvalidWalletMoveException
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.walletmoves.repositories.HoldingReference
import br.com.investlog.server.walletmoves.repositories.MovableHolding
import br.com.investlog.server.walletmoves.repositories.MovableHoldingRepository
import br.com.investlog.server.walletmoves.repositories.MoveWallet
import br.com.investlog.server.walletmoves.repositories.WalletMoveRepository
import br.com.investlog.server.walletmoves.rest.payloads.WalletMoveItemRequest
import br.com.investlog.server.walletmoves.rest.payloads.WalletMoveRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional(readOnly = true)
class WalletMoveService(
    private val walletMoveRepository: WalletMoveRepository,
    private val movableHoldingRepository: MovableHoldingRepository,
) {

    companion object {
        private const val SCALE = 20
        private val MOVE_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    }

    @Transactional
    fun move(userId: Long, originWalletId: UUID, request: WalletMoveRequest) {
        val destinationWalletId = request.destinationWalletId!!
        val items = request.items!!

        if (originWalletId == destinationWalletId) {
            throw InvalidWalletMoveException("A carteira de destino deve ser diferente da carteira de origem")
        }

        val origin = walletMoveRepository.findWallet(userId, originWalletId)
            ?: throw NotFoundException("Carteira não encontrada")
        val destination = walletMoveRepository.findWallet(userId, destinationWalletId)
            ?: throw NotFoundException("Carteira de destino não encontrada")

        if (origin.kind != destination.kind) {
            throw InvalidWalletMoveException("As carteiras de origem e destino devem ser do mesmo tipo")
        }

        if (origin.currency != destination.currency) {
            throw InvalidWalletMoveException("As carteiras de origem e destino devem ter a mesma moeda")
        }

        if (items.map { item -> item.holdingId }.distinct().size != items.size) {
            throw InvalidWalletMoveException("Um investimento não pode ser listado mais de uma vez")
        }

        val movedAt = LocalDate.now(MOVE_ZONE)

        items.forEach { item -> moveHolding(origin, destination, item, movedAt) }
    }

    private fun moveHolding(origin: MoveWallet, destination: MoveWallet, item: WalletMoveItemRequest, movedAt: LocalDate) {
        val holding = movableHoldingRepository.findHolding(origin.id, origin.kind, item.holdingId!!)
            ?: throw NotFoundException("Investimento não encontrado")

        if (holding.status == HoldingStatus.COMPLETED) {
            throw InvalidWalletMoveException("${holding.name} já foi totalmente resgatado e não pode ser movido")
        }

        if (holding.kind == WalletKind.FUNDS && item.quantity != null) {
            throw InvalidWalletMoveException("Fundos são movidos por inteiro, sem informar quantidade")
        }

        val remainingQuantity = holding.quantity ?: BigDecimal.ZERO

        if (item.quantity != null && item.quantity > remainingQuantity) {
            throw InvalidWalletMoveException(
                "A quantidade movida de ${holding.name} não pode ser maior que a quantidade restante de $remainingQuantity"
            )
        }

        val partialQuantity = item.quantity?.takeIf { quantity -> quantity.compareTo(remainingQuantity) != 0 }
        val match = movableHoldingRepository.findMatchingActiveHolding(destination.id, holding)

        val destinationHolding = if (partialQuantity == null) {
            moveWhole(holding, match, destination, movedAt)
        } else {
            val target = match ?: movableHoldingRepository.createCopy(holding, destination.id)
            splitLots(holding, partialQuantity, target, movedAt)
            target
        }

        walletMoveRepository.insert(origin, destination, holding, destinationHolding, partialQuantity, movedAt)
    }

    private fun moveWhole(
        holding: MovableHolding,
        match: HoldingReference?,
        destination: MoveWallet,
        movedAt: LocalDate,
    ): HoldingReference {
        if (match == null) {
            movableHoldingRepository.reassign(holding, destination.id)
            return holding.reference
        }

        if (!holding.hasResults) {
            movableHoldingRepository.reattachChildren(holding, match)
            if (holding.kind == WalletKind.FUNDS) {
                movableHoldingRepository.transferFundCurrentValue(holding, match)
            }
            movableHoldingRepository.delete(holding)
            return match
        }

        if (holding.kind == WalletKind.FUNDS) {
            splitContributions(holding, match, movedAt)
            movableHoldingRepository.transferFundCurrentValue(holding, match)
        } else {
            splitLots(holding, holding.quantity!!, match, movedAt)
        }
        movableHoldingRepository.markCompleted(holding)

        return match
    }

    private fun splitLots(holding: MovableHolding, movedQuantity: BigDecimal, target: HoldingReference, movedAt: LocalDate) {
        val lots = movableHoldingRepository.findLots(holding)
        val remainingQuantity = holding.quantity!!

        val movedCost = holding.costBasis.multiply(movedQuantity).divide(remainingQuantity, SCALE, RoundingMode.HALF_UP)
        val lotQuantity = lots.sumOf { lot -> lot.quantity }
        val lotCost = lots.sumOf { lot -> lot.quantity.multiply(lot.price) }

        val keptQuantities = distribute(lots.map { lot -> lot.quantity }, lotQuantity.subtract(movedQuantity))
        val keptCostAtOriginalPrices = lots.zip(keptQuantities)
            .sumOf { (lot, keptQuantity) -> keptQuantity.multiply(lot.price) }

        val priceFactor = if (holding.hasResults && keptCostAtOriginalPrices.signum() > 0) {
            lotCost.subtract(movedCost).divide(keptCostAtOriginalPrices, SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ONE
        }

        lots.zip(keptQuantities).forEach { (lot, keptQuantity) ->
            movableHoldingRepository.updateLot(
                holding.kind,
                lot.id,
                keptQuantity.normalized(),
                lot.price.multiply(priceFactor).normalized(),
            )
        }

        movableHoldingRepository.insertLot(
            holding.kind,
            target,
            movedAt,
            movedQuantity,
            movedCost.divide(movedQuantity, SCALE, RoundingMode.HALF_UP).normalized(),
        )
    }

    private fun splitContributions(holding: MovableHolding, target: HoldingReference, movedAt: LocalDate) {
        val contributions = movableHoldingRepository.findContributions(holding)
        val contributedAmount = contributions.sumOf { contribution -> contribution.amount }
        val withdrawnCost = contributedAmount.subtract(holding.costBasis)

        if (withdrawnCost.signum() > 0) {
            val keptAmounts = distribute(contributions.map { contribution -> contribution.amount }, withdrawnCost)
            contributions.zip(keptAmounts).forEach { (contribution, keptAmount) ->
                movableHoldingRepository.updateContributionAmount(contribution.id, keptAmount.normalized())
            }
        } else {
            movableHoldingRepository.deleteContributions(holding)
        }

        if (holding.costBasis.signum() > 0) {
            movableHoldingRepository.insertContribution(target, movedAt, holding.costBasis)
        }
    }

    private fun distribute(parts: List<BigDecimal>, newTotal: BigDecimal): List<BigDecimal> {
        val total = parts.sumOf { part -> part }
        val scaledParts = parts.dropLast(1).map { part ->
            part.multiply(newTotal).divide(total, SCALE, RoundingMode.HALF_UP)
        }
        return scaledParts + newTotal.subtract(scaledParts.sumOf { part -> part })
    }

    private fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros().let { value ->
        if (value.scale() < 0) value.setScale(0) else value
    }
}
