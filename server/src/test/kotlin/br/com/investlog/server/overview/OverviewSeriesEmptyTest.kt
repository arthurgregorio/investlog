package br.com.investlog.server.overview

import br.com.investlog.server.BaseIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewSeriesEmptyTest : BaseIntegrationTest() {

    @Autowired
    lateinit var restTestClient: RestTestClient

    @Test
    fun `a user with no purchases gets an empty series`() {
        val body = restTestClient.get()
            .uri("/private/v1/overview/series")
            .exchange()
            .expectStatus().isOk()
            .returnResult<String>()
            .responseBody!!

        assertEquals("[]", body)
    }
}
