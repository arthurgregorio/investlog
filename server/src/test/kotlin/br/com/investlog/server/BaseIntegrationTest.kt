package br.com.investlog.server

import br.com.investlog.server.BaseIntegrationTest.Companion.ACTIVE_PROFILE
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@AutoConfigureRestTestClient
@ActiveProfiles(ACTIVE_PROFILE)
@Import(value = [TestcontainersConfiguration::class])
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BaseIntegrationTest internal constructor() {

    companion object {
        const val ACTIVE_PROFILE = "test"
    }
}