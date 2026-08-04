package br.com.investlog.server.configurations.repositories

import br.com.investlog.server.configurations.rest.payloads.ConfigurationResponse
import br.com.investlog.server.jooq.system.tables.records.ConfigurationsRecord
import br.com.investlog.server.jooq.system.tables.references.CONFIGURATIONS
import br.com.investlog.server.shared.utils.pagedModelOf
import org.jooq.DSLContext
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class ConfigurationRepository(private val dsl: DSLContext) {

    fun findAll(): List<ConfigurationResponse> =
        dsl.selectFrom(CONFIGURATIONS)
            .orderBy(CONFIGURATIONS.KEY)
            .fetch()
            .map { it.toResponse() }

    fun findAll(pageable: Pageable): PagedModel<ConfigurationResponse> {
        val content = dsl.selectFrom(CONFIGURATIONS)
            .orderBy(CONFIGURATIONS.KEY)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch()
            .map { it.toResponse() }

        val total = dsl.fetchCount(CONFIGURATIONS)

        return pagedModelOf(content, pageable, total.toLong())
    }

    fun update(key: String, value: String): ConfigurationResponse? =
        dsl.update(CONFIGURATIONS)
            .set(CONFIGURATIONS.VALUE, value)
            .set(CONFIGURATIONS.UPDATED_AT, OffsetDateTime.now())
            .where(CONFIGURATIONS.KEY.eq(key))
            .returning()
            .fetchOne()
            ?.toResponse()

    private fun ConfigurationsRecord.toResponse() = ConfigurationResponse(
        key = key!!,
        value = value!!,
        updatedAt = updatedAt!!,
    )
}
