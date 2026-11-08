package br.com.investlog.server.mail.services

import br.com.investlog.server.config.InvestlogConfigurations
import br.com.investlog.server.shared.exceptions.InvalidMailSettingsException
import java.time.Duration
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MailPasswordCipherTest {

    @Test
    fun `encrypts and decrypts a password round trip`() {
        val cipher = cipherWithKey(FIRST_KEY)

        val ciphertext = cipher.encrypt("segredo çãé")

        assertTrue(cipher.isAvailable())
        assertFalse(ciphertext.contains("segredo"))
        assertEquals("segredo çãé", cipher.decrypt(ciphertext))
    }

    @Test
    fun `prepends a random initialization vector so the same password never encrypts twice the same way`() {
        val cipher = cipherWithKey(FIRST_KEY)

        val firstCiphertext = cipher.encrypt("segredo")
        val secondCiphertext = cipher.encrypt("segredo")

        assertNotEquals(firstCiphertext, secondCiphertext)
        assertEquals(12 + "segredo".length + 16, Base64.getDecoder().decode(firstCiphertext).size)
    }

    @Test
    fun `refuses a tampered ciphertext`() {
        val cipher = cipherWithKey(FIRST_KEY)
        val bytes = Base64.getDecoder().decode(cipher.encrypt("segredo"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()

        assertFailsWith<AEADBadTagException> { cipher.decrypt(Base64.getEncoder().encodeToString(bytes)) }
    }

    @Test
    fun `refuses a ciphertext written with another key`() {
        val ciphertext = cipherWithKey(FIRST_KEY).encrypt("segredo")

        assertFailsWith<AEADBadTagException> { cipherWithKey(SECOND_KEY).decrypt(ciphertext) }
    }

    @Test
    fun `without a key it is unavailable and refuses to encrypt or decrypt`() {
        val cipher = cipherWithKey("  ")

        assertFalse(cipherWithKey("").isAvailable())
        assertFalse(cipher.isAvailable())
        val encryptFailure = assertFailsWith<InvalidMailSettingsException> { cipher.encrypt("segredo") }
        assertFailsWith<InvalidMailSettingsException> { cipher.decrypt(cipherWithKey(FIRST_KEY).encrypt("segredo")) }
        assertEquals("Defina MAIL_ENCRYPTION_KEY para salvar a senha do SMTP", encryptFailure.message)
    }

    @Test
    fun `fails fast on a key that is not base64`() {
        val failure = assertFailsWith<IllegalStateException> { cipherWithKey("não é base64!") }

        assertTrue(failure.message!!.contains("not valid base64"))
    }

    @Test
    fun `fails fast on a key that is not 32 bytes long`() {
        val failure = assertFailsWith<IllegalStateException> { cipherWithKey(Base64.getEncoder().encodeToString(ByteArray(16))) }

        assertTrue(failure.message!!.contains("must decode to 32 bytes, not 16"))
    }

    private fun cipherWithKey(encryptionKey: String) = MailPasswordCipher(
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
            mail = InvestlogConfigurations.Mail(encryptionKey = encryptionKey),
        ),
    )

    companion object {
        private val FIRST_KEY = Base64.getEncoder().encodeToString(ByteArray(32) { index -> index.toByte() })
        private val SECOND_KEY = Base64.getEncoder().encodeToString(ByteArray(32) { index -> (index + 1).toByte() })
    }
}
