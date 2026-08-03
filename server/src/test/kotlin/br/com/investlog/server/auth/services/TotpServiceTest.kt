package br.com.investlog.server.auth.services

import dev.samstevens.totp.code.DefaultCodeGenerator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TotpServiceTest {

    private val totpService = TotpService()

    @Test
    fun `accepts a code generated for the current time from the same secret`() {
        val secret = totpService.generateSecret()
        val validCode = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        assertTrue(totpService.isCodeValid(secret, validCode))
    }

    @Test
    fun `rejects a code that does not match the secret`() {
        val secret = totpService.generateSecret()

        assertFalse(totpService.isCodeValid(secret, "000000"))
    }

    @Test
    fun `builds a data URI for the QR code image`() {
        val secret = totpService.generateSecret()

        val dataUri = totpService.qrCodeDataUri("user@example.com", secret)

        assertTrue(dataUri.startsWith("data:image/png;base64,"))
    }
}
