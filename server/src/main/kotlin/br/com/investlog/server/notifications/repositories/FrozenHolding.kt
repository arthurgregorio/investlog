package br.com.investlog.server.notifications.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind
import java.math.BigDecimal
import java.util.UUID

data class FrozenHolding(
    val kind: WalletKind,
    val externalId: UUID,
    val ticker: String?,
    val name: String,
    val walletName: String,
    val walletCurrency: String,
    val currentValue: BigDecimal?,
    val gain: BigDecimal?,
    val gainPercentage: BigDecimal?,
    val alert: FrozenHoldingAlert?,
) {

    fun isAboveMinimumGain(minimumGainPercentage: BigDecimal): Boolean =
        currentValue != null &&
            gain != null &&
            gainPercentage != null &&
            gain.signum() > 0 &&
            gainPercentage > minimumGainPercentage
}
