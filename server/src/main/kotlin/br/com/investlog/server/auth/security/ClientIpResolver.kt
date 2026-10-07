package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class ClientIpResolver(investlogConfigurations: InvestlogConfigurations) {

    private val trustedProxyCount = investlogConfigurations.security.clientIp.trustedProxyCount

    fun resolve(servletRequest: HttpServletRequest): String {
        val forwardedChain = servletRequest.getHeader(FORWARDED_FOR_HEADER)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val addressChain = forwardedChain + servletRequest.remoteAddr

        return addressChain.getOrNull(addressChain.size - 1 - trustedProxyCount) ?: addressChain.first()
    }

    private companion object {
        const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
    }
}
