package br.com.investlog.server.shared.persistence

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.data.domain.PageRequest

class PagingTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `wraps content and pageable into a PagedModel`() {
        val model = pagedModelOf(listOf("a", "b"), PageRequest.of(0, 20), 2L)
        val json = objectMapper.valueToTree<JsonNode>(model)

        assertEquals(listOf("a", "b"), model.content.toList())
        assertEquals(0, json["page"]["number"].asInt())
        assertEquals(20, json["page"]["size"].asInt())
        assertEquals(2, json["page"]["totalElements"].asInt())
        assertEquals(1, json["page"]["totalPages"].asInt())
    }
}
