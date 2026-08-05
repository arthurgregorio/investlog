package br.com.investlog.server.cryptopricesync.repositories

import br.com.investlog.server.jooq.finances.enums.WalletKind
import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_HOLDINGS
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime

@Repository
class CryptoPriceSyncRepository(
    private val dsl: DSLContext
) {

    fun findDistinctTickers(): List<String> =
        dsl.selectDistinct(CRYPTO_HOLDINGS.TICKER)
            .from(CRYPTO_HOLDINGS)
            .fetch(CRYPTO_HOLDINGS.TICKER)
            .filterNotNull()

    fun findDistinctCryptoWalletCurrencies(): List<String> =
        dsl.selectDistinct(WALLETS.CURRENCY)
            .from(WALLETS)
            .where(WALLETS.KIND.eq(WalletKind.CRYPTO))
            .orderBy(WALLETS.CURRENCY)
            .fetch(WALLETS.CURRENCY)
            .filterNotNull()

    fun updatePrice(ticker: String, currency: String, price: BigDecimal): Int =
        dsl.update(CRYPTO_HOLDINGS)
            .set(CRYPTO_HOLDINGS.CURRENT_PRICE, price)
            .set(CRYPTO_HOLDINGS.UPDATED_AT, OffsetDateTime.now())
            .where(CRYPTO_HOLDINGS.TICKER.eq(ticker.uppercase()))
            .and(
                CRYPTO_HOLDINGS.WALLET_ID.`in`(
                    dsl.select(WALLETS.ID)
                        .from(WALLETS)
                        .where(WALLETS.KIND.eq(WalletKind.CRYPTO))
                        .and(WALLETS.CURRENCY.eq(currency))
                )
            )
            .execute()
}
