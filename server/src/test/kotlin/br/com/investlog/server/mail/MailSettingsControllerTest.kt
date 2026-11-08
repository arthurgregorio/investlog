package br.com.investlog.server.mail

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.rest.payloads.SessionResponse
import br.com.investlog.server.auth.rest.payloads.TotpEnrollResponse
import br.com.investlog.server.jooq.system.tables.references.MAIL_SETTINGS
import br.com.investlog.server.mail.services.MailPasswordCipher
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetupTest
import dev.samstevens.totp.code.DefaultCodeGenerator
import jakarta.mail.internet.MimeMultipart
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestPropertySource(properties = ["investlog.mail.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="])
class MailSettingsControllerTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var dsl: DSLContext

    @Autowired
    lateinit var mailPasswordCipher: MailPasswordCipher

    @BeforeEach
    fun purgeMailboxes() {
        greenMail.purgeEmailFromAllMailboxes()
    }

    @Test
    @Order(1)
    fun `starts disabled and not configured`() {
        restTestClient.get()
            .uri(SETTINGS_URI)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
            .jsonPath("$.configured").isEqualTo(false)
            .jsonPath("$.passwordSet").isEqualTo(false)
            .jsonPath("$.security").isEqualTo("STARTTLS")
            .jsonPath("$.host").doesNotExist()
            .jsonPath("$.updatedAt").exists()
    }

    @Test
    @Order(2)
    fun `refuses to enable sending before the server is saved`() {
        restTestClient.patch()
            .uri("$SETTINGS_URI/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"enabled":true}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Salve o servidor SMTP antes de ativar o envio")
    }

    @Test
    @Order(3)
    fun `refuses a test send before the server is saved`() {
        restTestClient.post()
            .uri("$SETTINGS_URI/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"recipient":"destino@example.com"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Salve o servidor SMTP antes de enviar um teste")
    }

    @Test
    @Order(4)
    fun `rejects settings without the required fields`() {
        restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"host":" ","port":0,"security":"NONE","fromAddress":"not-an-email","publicUrl":"ftp://investlog.example.com"}""")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors.length()").isEqualTo(4)
    }

    @Test
    @Order(5)
    fun `rejects a port above the valid range and an unknown security mode`() {
        restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body(settingsBody(port = 65536))
            .exchange()
            .expectStatus().isBadRequest()

        restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body(settingsBody(security = "TLS"))
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(6)
    fun `saves the server and stores the password encrypted`() {
        val body = restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body(settingsBody(password = SMTP_PASSWORD, publicUrl = "https://investlog.example.com/"))
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody

        assertNotNull(body)
        assertFalse(body.contains(SMTP_PASSWORD))
        assertFalse(body.contains("ciphertext", ignoreCase = true))

        restTestClient.get()
            .uri(SETTINGS_URI)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.host").isEqualTo("localhost")
            .jsonPath("$.port").isEqualTo(greenMail.smtp.port)
            .jsonPath("$.security").isEqualTo("NONE")
            .jsonPath("$.username").isEqualTo(SMTP_USERNAME)
            .jsonPath("$.passwordSet").isEqualTo(true)
            .jsonPath("$.password").doesNotExist()
            .jsonPath("$.passwordCiphertext").doesNotExist()
            .jsonPath("$.fromAddress").isEqualTo("investlog@example.com")
            .jsonPath("$.fromName").isEqualTo("InvestLog")
            .jsonPath("$.publicUrl").isEqualTo("https://investlog.example.com")

        val storedCiphertext = storedPasswordCiphertext()

        assertNotNull(storedCiphertext)
        assertNotEquals(SMTP_PASSWORD, storedCiphertext)
        assertFalse(storedCiphertext.contains(SMTP_PASSWORD))
        assertEquals(SMTP_PASSWORD, mailPasswordCipher.decrypt(storedCiphertext))
    }

    @Test
    @Order(7)
    fun `saving with a blank password keeps the stored one`() {
        val ciphertextBefore = storedPasswordCiphertext()

        restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body(settingsBody(password = "  ", fromName = "Equipe InvestLog"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.username").isEqualTo(SMTP_USERNAME)
            .jsonPath("$.passwordSet").isEqualTo(true)
            .jsonPath("$.fromName").isEqualTo("Equipe InvestLog")

        restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body(settingsBody(password = null))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.passwordSet").isEqualTo(true)

        assertEquals(ciphertextBefore, storedPasswordCiphertext())
    }

    @Test
    @Order(8)
    fun `sends the test email to the typed recipient`() {
        restTestClient.post()
            .uri("$SETTINGS_URI/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"recipient":"destino@example.com"}""")
            .exchange()
            .expectStatus().isNoContent()

        val message = greenMail.receivedMessages.single()
        val content = message.content as MimeMultipart
        val rawMessage = GreenMailUtil.getWholeMessage(message)

        assertEquals("Teste de envio do InvestLog", message.subject)
        assertEquals("destino@example.com", message.allRecipients.single().toString())
        assertTrue(message.from.single().toString().contains("investlog@example.com"))
        assertTrue(rawMessage.contains("text/plain"))
        assertTrue(rawMessage.contains("text/html"))
        assertTrue(content.count > 0)
    }

    @Test
    @Order(9)
    fun `toggles sending on and off once the server is saved`() {
        restTestClient.patch()
            .uri("$SETTINGS_URI/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"enabled":true}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(true)

        restTestClient.patch()
            .uri("$SETTINGS_URI/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"enabled":false}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
    }

    @Test
    @Order(10)
    fun `rejects a test send to an invalid recipient`() {
        restTestClient.post()
            .uri("$SETTINGS_URI/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"recipient":"not-an-email"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(11)
    fun `reports the SMTP error when the server cannot be reached`() {
        val closedPort = ServerSocket(0).use { socket -> socket.localPort }

        restTestClient.put()
            .uri(SETTINGS_URI)
            .contentType(MediaType.APPLICATION_JSON)
            .body(settingsBody(port = closedPort))
            .exchange()
            .expectStatus().isOk()

        restTestClient.post()
            .uri("$SETTINGS_URI/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"recipient":"destino@example.com"}""")
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.detail").value<String> { detail -> assertTrue(detail.contains("connect", ignoreCase = true)) }

        assertTrue(greenMail.receivedMessages.isEmpty())
    }

    @Test
    @Order(12)
    fun `a non-admin is forbidden from every mail settings endpoint`() {
        val cookie = registerApproveAndLogin("mail-settings-user@example.com", "Senha123")

        restTestClient.get()
            .uri(SETTINGS_URI)
            .header("Cookie", cookie)
            .exchange()
            .expectStatus().isEqualTo(403)

        restTestClient.put()
            .uri(SETTINGS_URI)
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(settingsBody())
            .exchange()
            .expectStatus().isEqualTo(403)

        restTestClient.patch()
            .uri("$SETTINGS_URI/enabled")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"enabled":true}""")
            .exchange()
            .expectStatus().isEqualTo(403)

        restTestClient.post()
            .uri("$SETTINGS_URI/test")
            .header("Cookie", cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"recipient":"destino@example.com"}""")
            .exchange()
            .expectStatus().isEqualTo(403)
    }

    private fun storedPasswordCiphertext(): String? =
        dsl.select(MAIL_SETTINGS.PASSWORD_CIPHERTEXT).from(MAIL_SETTINGS).fetchSingle().value1()

    private fun settingsBody(
        port: Int = greenMail.smtp.port,
        security: String = "NONE",
        password: String? = null,
        fromName: String = "InvestLog",
        publicUrl: String = "https://investlog.example.com",
    ): String {
        val passwordField = password?.let { """"password":"$it",""" } ?: ""

        return """{"host":"localhost","port":$port,"security":"$security","username":"$SMTP_USERNAME",$passwordField"fromAddress":"investlog@example.com","fromName":"$fromName","publicUrl":"$publicUrl"}"""
    }

    private fun registerApproveAndLogin(email: String, password: String): String {
        restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Teste","email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isCreated()

        val targetId = (
            restTestClient.get()
                .uri("/private/v1/users?size=200")
                .exchange()
                .expectStatus().isOk()
                .returnResult<Map<String, Any?>>()
                .responseBody
                ?.get("content") as List<*>
            )
            .map { it as Map<*, *> }
            .single { it["email"] == email }["id"] as String

        restTestClient.patch()
            .uri("/private/v1/users/$targetId/approve")
            .exchange()
            .expectStatus().isOk()

        val secret = restTestClient.post()
            .uri("/private/v1/auth/totp/enroll")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<TotpEnrollResponse>()
            .responseBody
            ?.secretKey
            ?: error("Enroll did not return a secret")

        val code = DefaultCodeGenerator().generate(secret, System.currentTimeMillis() / 1000L / 30L)

        return restTestClient.post()
            .uri("/private/v1/auth/totp/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"email":"$email","password":"$password","code":"$code"}""")
            .exchange()
            .expectStatus().isOk()
            .returnResult<SessionResponse>()
            .responseHeaders
            .getFirst("Set-Cookie")
            ?.substringBefore(";")
            ?: error("Verify did not set a session cookie")
    }

    companion object {
        private const val SETTINGS_URI = "/private/v1/mail-settings"
        private const val SMTP_USERNAME = "smtp-user"
        private const val SMTP_PASSWORD = "segredo-do-smtp"

        private val greenMail = GreenMail(ServerSetupTest.SMTP.dynamicPort()).apply {
            setUser(SMTP_USERNAME, SMTP_PASSWORD)
            start()
        }

        @JvmStatic
        @AfterAll
        fun stopGreenMail() {
            greenMail.stop()
        }
    }
}
