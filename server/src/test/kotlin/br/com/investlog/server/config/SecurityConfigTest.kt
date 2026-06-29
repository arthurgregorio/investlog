package br.com.investlog.server.config

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityConfigTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `authenticated requests reach private endpoints`() {
        restTestClient.get()
            .uri("/private/v1/profile")
            .exchange()
            .expectStatus().isOk()
    }

    @Test
    fun `password encoder hashes and verifies a raw password`() {
        val hash = passwordEncoder.encode("admin")

        assertNotEquals("admin", hash)
        assertTrue(passwordEncoder.matches("admin", hash))
    }
}
