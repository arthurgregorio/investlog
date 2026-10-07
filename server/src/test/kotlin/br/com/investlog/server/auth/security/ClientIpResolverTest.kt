package br.com.investlog.server.auth.security

import br.com.investlog.server.config.InvestlogConfigurations
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientIpResolverTest {

    private fun resolverTrusting(trustedProxyCount: Int) = ClientIpResolver(
        InvestlogConfigurations(
            demoMode = InvestlogConfigurations.DemoMode(enabled = false),
            security = InvestlogConfigurations.Security(
                adminDefaultPassword = "admin",
                totp = InvestlogConfigurations.Security.Totp(
                    enabled = true,
                    lockoutMaxAttempts = 5,
                    lockoutBaseDuration = Duration.ofMinutes(1),
                ),
                login = InvestlogConfigurations.Security.Login(
                    lockoutMaxAttempts = 5,
                    lockoutBaseDuration = Duration.ofMinutes(1),
                ),
                trustedDevice = InvestlogConfigurations.Security.TrustedDevice(expiry = Duration.ofDays(30)),
                clientIp = InvestlogConfigurations.Security.ClientIp(trustedProxyCount = trustedProxyCount),
            ),
            googleAuth = InvestlogConfigurations.GoogleAuth(
                enabled = false,
                clientId = "",
                clientSecret = "",
                clientBaseUrl = "",
            ),
            brApi = InvestlogConfigurations.BrApi(baseUrl = "", token = ""),
            coinGecko = InvestlogConfigurations.CoinGecko(baseUrl = "", apiKey = "", apiKeyHeader = ""),
            awesomeApi = InvestlogConfigurations.AwesomeApi(baseUrl = ""),
        ),
    )

    private fun requestFrom(remoteAddress: String, forwardedFor: String? = null) =
        MockHttpServletRequest().apply {
            remoteAddr = remoteAddress
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
        }

    @Test
    fun `uses the peer address when there is no forwarded header`() {
        assertEquals("203.0.113.9", resolverTrusting(0).resolve(requestFrom("203.0.113.9")))
    }

    @Test
    fun `with no trusted proxy the peer address wins over anything the client forwards`() {
        val request = requestFrom("203.0.113.9", forwardedFor = "10.0.0.1, 10.0.0.2")

        assertEquals("203.0.113.9", resolverTrusting(0).resolve(request))
    }

    @Test
    fun `skips as many trailing addresses as there are trusted proxies`() {
        val request = requestFrom("198.51.100.1", forwardedFor = "203.0.113.9")

        assertEquals("203.0.113.9", resolverTrusting(1).resolve(request))
    }

    @Test
    fun `a spoofed leftmost entry never becomes the client address`() {
        val request = requestFrom("198.51.100.1", forwardedFor = "10.9.9.9, 203.0.113.9")

        assertEquals("203.0.113.9", resolverTrusting(1).resolve(request))
    }

    @Test
    fun `falls back to the leftmost known address when the chain is shorter than the trusted count`() {
        val request = requestFrom("198.51.100.1")

        assertEquals("198.51.100.1", resolverTrusting(2).resolve(request))
    }

    @Test
    fun `ignores blank entries in the forwarded header`() {
        val request = requestFrom("198.51.100.1", forwardedFor = " , 203.0.113.9 ,")

        assertEquals("203.0.113.9", resolverTrusting(1).resolve(request))
    }
}
