package br.com.investlog.server.shared.security

import br.com.investlog.server.shared.exceptions.DemoModeProtectedAccountException
import br.com.investlog.server.shared.exceptions.DemoModeRestrictedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DemoModeGuard(
    @Value($$"${investlog.demo-mode.enabled:false}")
    private val demoModeEnabled: Boolean,
) {

    fun isDemoMode(): Boolean = demoModeEnabled

    fun assertNotDemoMode() {
        if (demoModeEnabled) {
            throw DemoModeRestrictedException("Indisponível no modo demonstração.")
        }
    }

    fun assertNotProtectedAdminAccount(email: String) {
        if (demoModeEnabled && email == AdminAccount.EMAIL) {
            throw DemoModeProtectedAccountException("Esta ação está desabilitada para a conta protegida do administrador de demonstração.")
        }
    }
}
