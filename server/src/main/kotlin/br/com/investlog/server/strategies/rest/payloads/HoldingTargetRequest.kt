package br.com.investlog.server.strategies.rest.payloads

import br.com.investlog.server.wallets.rest.payloads.WalletKind
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal
import java.util.UUID

data class HoldingTargetRequest(
    val kind: WalletKind,
    val holdingId: UUID,
    @field:DecimalMin("0") @field:DecimalMax("100") val targetPercentage: BigDecimal? = null,
)
