package br.com.investlog.server.mail

import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.auth.services.AuthService
import br.com.investlog.server.jooq.system.tables.references.USERS
import br.com.investlog.server.mail.repositories.MailServerUpdate
import br.com.investlog.server.mail.repositories.MailSettingsRepository
import br.com.investlog.server.mail.rest.payloads.MailSecurity
import br.com.investlog.server.mail.services.MailMessage
import br.com.investlog.server.mail.services.MailRequested
import br.com.investlog.server.shared.security.CurrentUser
import br.com.investlog.server.shared.security.UserRepository
import br.com.investlog.server.shared.security.UserRole
import br.com.investlog.server.usersadmin.rest.payloads.UserAdminResponse
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccountMailNotifierTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var mailSettingsRepository: MailSettingsRepository

    @Autowired
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    lateinit var dsl: DSLContext

    @BeforeEach
    fun purgeMailboxes() {
        greenMail.purgeEmailFromAllMailboxes()
    }

    @Test
    @Order(1)
    fun `a registration sends nothing while the default admin is the only admin`() {
        mailSettingsRepository.updateServer(serverUpdate())
        mailSettingsRepository.updateEnabled(true)

        register("Primeiro Cadastro", "primeiro-cadastro@example.com")

        assertOnlySentinelArrives()
    }

    @Test
    @Order(2)
    fun `a local registration emails every approved admin except the default admin, one message each`() {
        createUser("Ana Admin", "ana-admin@example.com", UserRole.ADMIN, CurrentUser.Status.APPROVED)
        createUser("Bruno Admin", "bruno-admin@example.com", UserRole.ADMIN, CurrentUser.Status.APPROVED)
        createUser("Carla Pendente", "carla-pendente@example.com", UserRole.ADMIN, CurrentUser.Status.PENDING)
        createUser("Diego Bloqueado", "diego-bloqueado@example.com", UserRole.ADMIN, CurrentUser.Status.BLOCKED)
        createUser("Elisa Usuária", "elisa-usuaria@example.com", UserRole.USER, CurrentUser.Status.APPROVED)

        register("Maria Souza", MARIA_EMAIL)

        val messages = awaitMessages(2)

        assertEquals(setOf(ANA_EMAIL, BRUNO_EMAIL), messages.map(::singleRecipientOf).toSet())

        messages.forEach { message ->
            val (plainText, html) = partsOf(message)
            val recipientName = if (singleRecipientOf(message) == ANA_EMAIL) "Ana Admin" else "Bruno Admin"

            assertEquals("Novo usuário aguardando aprovação: Maria Souza", message.subject)
            assertTrue(plainText.contains("Olá, $recipientName."), plainText)
            assertTrue(plainText.contains("Aguardando aprovação"))
            assertTrue(plainText.contains("Nome Maria Souza"), plainText)
            assertTrue(plainText.contains("E-mail $MARIA_EMAIL"), plainText)
            assertTrue(plainText.contains("Cadastro por E-mail e senha"), plainText)
            assertTrue(CREATED_AT.containsMatchIn(plainText), plainText)
            assertTrue(plainText.contains("Revisar cadastros pendentes: $PUBLIC_URL/settings/users"), plainText)
            assertTrue(plainText.contains("Você recebe este e-mail porque é administrador do InvestLog."))
            assertTrue(html.contains("href=\"$PUBLIC_URL/settings/users\""))
        }
    }

    @Test
    @Order(3)
    fun `a duplicate registration sends nothing and answers exactly like a new one`() {
        val newRegistration = register("Registro Novo", "registro-duplicado@example.com")
        awaitMessages(2)
        greenMail.purgeEmailFromAllMailboxes()

        val duplicateRegistration = register("Outro Nome", "registro-duplicado@example.com")

        assertEquals(newRegistration, duplicateRegistration)
        assertOnlySentinelArrives()
    }

    @Test
    @Order(4)
    fun `a first Google sign-in emails every approved admin, and a later sign-in sends nothing`() {
        googleSignIn()

        val messages = awaitMessages(2)

        assertEquals(setOf(ANA_EMAIL, BRUNO_EMAIL), messages.map(::singleRecipientOf).toSet())
        messages.forEach { message ->
            val (plainText, _) = partsOf(message)

            assertEquals("Novo usuário aguardando aprovação: Gabriel Google", message.subject)
            assertTrue(plainText.contains("E-mail $GOOGLE_EMAIL"), plainText)
            assertTrue(plainText.contains("Cadastro por Conta Google"), plainText)
        }

        greenMail.purgeEmailFromAllMailboxes()

        googleSignIn()

        assertOnlySentinelArrives()
    }

    @Test
    @Order(5)
    fun `approving a pending user emails them the login link`() {
        val response = approve(userIdOf(MARIA_EMAIL))
            .expectStatus().isOk()
            .returnResult<UserAdminResponse>()
            .responseBody

        assertEquals(CurrentUser.Status.APPROVED, response?.status)

        val message = awaitMessages(1).single()
        val (plainText, html) = partsOf(message)

        assertEquals(MARIA_EMAIL, singleRecipientOf(message))
        assertEquals("Sua conta no InvestLog foi aprovada", message.subject)
        assertTrue(plainText.contains("Conta aprovada"))
        assertTrue(plainText.contains("Olá, Maria Souza."), plainText)
        assertTrue(plainText.contains("Você já pode entrar com o e-mail $MARIA_EMAIL"), plainText)
        assertTrue(plainText.contains("Entrar no InvestLog: $PUBLIC_URL/login"), plainText)
        assertTrue(plainText.contains("Você recebe este e-mail porque criou uma conta no InvestLog."))
        assertTrue(html.contains("href=\"$PUBLIC_URL/login\""))
    }

    @Test
    @Order(6)
    fun `approving a user who is not pending is refused and sends nothing`() {
        approve(userIdOf(MARIA_EMAIL))
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Não é possível aprovar um usuário cujo status atual não é PENDING")

        approve(userIdOf("diego-bloqueado@example.com"))
            .expectStatus().isEqualTo(409)

        assertOnlySentinelArrives()
    }

    @Test
    @Order(7)
    fun `unblocking a user sends nothing`() {
        restTestClient.patch()
            .uri("/private/v1/users/${userIdOf(MARIA_EMAIL)}/block")
            .exchange()
            .expectStatus().isOk()

        restTestClient.patch()
            .uri("/private/v1/users/${userIdOf(MARIA_EMAIL)}/unblock")
            .exchange()
            .expectStatus().isOk()

        assertOnlySentinelArrives()
    }

    @Test
    @Order(8)
    fun `registration and approval answer the same while sending is disabled or failing`() {
        val referenceRegistration = register("Referência", "referencia@example.com")
        awaitMessages(2)

        mailSettingsRepository.updateEnabled(false)

        assertEquals(referenceRegistration, register("Envio Desligado", "envio-desligado@example.com"))
        approve(userIdOf("envio-desligado@example.com")).expectStatus().isOk()

        mailSettingsRepository.updateServer(serverUpdate(port = ServerSocket(0).use { socket -> socket.localPort }))
        mailSettingsRepository.updateEnabled(true)

        assertEquals(referenceRegistration, register("Envio Falhando", "envio-falhando@example.com"))
        approve(userIdOf("envio-falhando@example.com")).expectStatus().isOk()

        assertEquals(CurrentUser.Status.APPROVED, userRepository.findByEmail("envio-desligado@example.com")?.status)
        assertEquals(CurrentUser.Status.APPROVED, userRepository.findByEmail("envio-falhando@example.com")?.status)
        assertEquals(2, greenMail.receivedMessages.size)
    }

    private fun register(name: String, email: String): RegistrationResponse {
        val result = restTestClient.post()
            .uri("/private/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"$name","email":"$email","password":"Senha123"}""")
            .exchange()
            .expectStatus().isCreated()
            .returnResult<String>()

        return RegistrationResponse(
            status = result.status.value(),
            contentType = result.responseHeaders.contentType?.toString(),
            contentLength = result.responseHeaders.contentLength,
            body = result.responseBody,
        )
    }

    private fun approve(userId: String) =
        restTestClient.patch()
            .uri("/private/v1/users/$userId/approve")
            .exchange()

    private fun googleSignIn() {
        authService.handleGoogleLogin(
            googleSub = "google-sub-account-mail",
            email = GOOGLE_EMAIL,
            name = "Gabriel Google",
            avatarUrl = null,
            servletRequest = MockHttpServletRequest(),
            servletResponse = MockHttpServletResponse(),
        )
    }

    private fun createUser(name: String, email: String, role: UserRole, status: CurrentUser.Status) {
        val user = userRepository.createLocalUser(name, email, "not-a-real-hash")

        dsl.update(USERS)
            .set(USERS.ROLE, role.name)
            .set(USERS.STATUS, status.name)
            .where(USERS.ID.eq(user.id))
            .execute()
    }

    private fun userIdOf(email: String): String =
        assertNotNull(userRepository.findByEmail(email)).externalId.toString()

    private fun awaitMessages(count: Int): List<MimeMessage> {
        assertTrue(greenMail.waitForIncomingEmail(WAIT_MILLISECONDS, count))
        return greenMail.receivedMessages.toList()
    }

    private fun assertOnlySentinelArrives() {
        applicationEventPublisher.publishEvent(
            MailRequested(MailMessage(to = listOf(SENTINEL_EMAIL), subject = "Sentinela", templateName = "test"))
        )

        assertTrue(greenMail.waitForIncomingEmail(WAIT_MILLISECONDS, 1))
        assertEquals(listOf(SENTINEL_EMAIL), greenMail.receivedMessages.map(::singleRecipientOf))
    }

    private fun singleRecipientOf(message: MimeMessage): String = message.allRecipients.single().toString()

    private fun partsOf(message: MimeMessage): Pair<String, String> {
        val bodyParts = generateSequence(listOf(message.content)) { contents ->
            contents.filterIsInstance<MimeMultipart>()
                .flatMap { multipart -> (0 until multipart.count).map { index -> multipart.getBodyPart(index) } }
                .map { bodyPart -> bodyPart.content }
                .ifEmpty { null }
        }.flatten().filterIsInstance<String>().toList()

        val html = bodyParts.first { it.contains("<html") }

        assertFalse(html.contains("xmlns:th"))
        assertFalse(html.contains("th:text"))

        val plainText = bodyParts.first { !it.contains("<html") }.replace(WHITESPACE, " ")

        return plainText to html
    }

    private fun serverUpdate(port: Int = greenMail.smtp.port) = MailServerUpdate(
        host = "localhost",
        port = port,
        security = MailSecurity.NONE,
        username = null,
        passwordCiphertext = null,
        fromAddress = "investlog@example.com",
        fromName = null,
        publicUrl = PUBLIC_URL,
    )

    private data class RegistrationResponse(
        val status: Int,
        val contentType: String?,
        val contentLength: Long,
        val body: String?,
    )

    companion object {
        private const val WAIT_MILLISECONDS = 10_000L
        private const val PUBLIC_URL = "https://investlog.example.com"
        private const val SENTINEL_EMAIL = "sentinela@example.com"
        private const val ANA_EMAIL = "ana-admin@example.com"
        private const val BRUNO_EMAIL = "bruno-admin@example.com"
        private const val MARIA_EMAIL = "maria.souza@example.com"
        private const val GOOGLE_EMAIL = "gabriel.google@example.com"
        private val WHITESPACE = Regex("\\s+")
        private val CREATED_AT = Regex("Criada em \\d{2}/\\d{2}/\\d{4} às \\d{2}:\\d{2}")

        private val greenMail = GreenMail(ServerSetupTest.SMTP.dynamicPort()).apply { start() }

        @JvmStatic
        @AfterAll
        fun stopGreenMail() {
            greenMail.stop()
        }
    }
}
