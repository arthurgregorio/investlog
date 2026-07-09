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
        assertEquals(CurrentUser.Status.APPROVED, user.status)
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

    @Test
    fun `stores and retrieves a totp secret`() {
        val user = userRepository.findByEmail("admin@admin.com")!!
        userRepository.updateTotpSecret(user.id, "JBSWY3DPEHPK3PXP")

        assertEquals("JBSWY3DPEHPK3PXP", userRepository.findTotpSecretByEmail("admin@admin.com"))
    }

    @Test
    fun `enabling totp sets both the secret and the enabled flag`() {
        val user = userRepository.findByEmail("admin@admin.com")!!
        userRepository.enableTotp(user.id, "KRSXG5CTMVRXEZLU")

        val updated = userRepository.findByEmail("admin@admin.com")!!
        assertEquals(true, updated.totpEnabled)
        assertEquals("KRSXG5CTMVRXEZLU", userRepository.findTotpSecretByEmail("admin@admin.com"))
    }
}
