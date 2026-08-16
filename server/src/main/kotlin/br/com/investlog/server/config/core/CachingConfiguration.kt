package br.com.investlog.server.config.core

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@EnableCaching
@Configuration(proxyBeanMethods = false)
class CachingConfiguration {

    @Bean
    fun cacheManager(): CacheManager =
        CaffeineCacheManager(CRYPTO_TICKER_RESOLUTION_CACHE)
            .apply { setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10))) }

    companion object {
        const val CRYPTO_TICKER_RESOLUTION_CACHE = "cryptoTickerResolution"
    }
}
