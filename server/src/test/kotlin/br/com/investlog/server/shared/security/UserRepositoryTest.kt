package br.com.investlog.server.shared.security

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserRepositoryTest : BaseIntegrationTest() {

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `finds the seeded admin by email`() {
        val user = userRepository.findByEmail("admin@admin.com")

        assertNotNull(user)
        assertEquals("Administrador", user.name)
        assertEquals(UserRole.ADMIN, user.role)
        assertEquals(UserStatus.APPROVED, user.status)
        assertEquals(AuthProvider.LOCAL, user.authProvider)
    }

    @Test
    fun `returns null for an unknown email`() {
        assertNull(userRepository.findByEmail("nobody@example.com"))
    }

    @Test
    fun `the admin bootstrap runner sets a non-null password hash`() {
        assertNotNull(userRepository.findPasswordHashByEmail("admin@admin.com"))
    }
}
