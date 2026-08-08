package br.com.investlog.server.shared.security

import br.com.investlog.server.shared.exceptions.DemoModeProtectedAccountException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DemoModeGuard(
    @Value($$"${investlog.demo-mode.enabled:false}")
    private val demoModeEnabled: Boolean,
) {

    fun assertNotProtectedAdminAccount(email: String) {
        if (demoModeEnabled && email == AdminAccount.EMAIL) {
            throw DemoModeProtectedAccountException("This action is disabled for the protected demo admin account.")
        }
    }
}
