package br.com.investlog.server.shared.security

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class AdminBootstrapRunner(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value($$"${investlog.security.admin-default-password:admin}")
    private val adminDefaultPassword: String,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val admin = userRepository.findByEmail(ADMIN_EMAIL) ?: return
        val existingHash = userRepository.findPasswordHashByEmail(ADMIN_EMAIL)

        if (existingHash != null) return

        logger.warn { "Setting the seeded admin's password from investlog.security.admin-default-password — change it after first login." }
        userRepository.updatePasswordHash(admin.id, passwordEncoder.encode(adminDefaultPassword)!!)
    }

    companion object {
        private const val ADMIN_EMAIL = "admin@admin.com"
    }
}
