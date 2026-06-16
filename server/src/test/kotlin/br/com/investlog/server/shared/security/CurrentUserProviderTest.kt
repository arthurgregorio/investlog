package br.com.investlog.server.shared.security

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentUserProviderTest : BaseIntegrationTest() {

    @Autowired
    lateinit var currentUserProvider: CurrentUserProvider

    @Test
    fun `resolves the seeded dev user`() {
        val user = currentUserProvider.getCurrentUser()

        assertEquals("arthurshakal@gmail.com", user.email)
        assertEquals("Arthur Gregorio", user.name)
        assertEquals("teal", user.accentColor)
        assertEquals("BRL", user.preferredCurrency)
    }
}
