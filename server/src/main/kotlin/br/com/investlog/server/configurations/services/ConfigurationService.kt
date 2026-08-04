package br.com.investlog.server.configurations.services

import br.com.investlog.server.configurations.ConfigurationKey
import br.com.investlog.server.configurations.repositories.ConfigurationRepository
import br.com.investlog.server.configurations.rest.payloads.ConfigurationResponse
import br.com.investlog.server.shared.exceptions.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
@Transactional(readOnly = true)
class ConfigurationService(
    private val configurationRepository: ConfigurationRepository,
) {

    private val cache = ConcurrentHashMap<String, String>()

    @PostConstruct
    fun loadCache() {
        configurationRepository.findAll().forEach { cache[it.key] = it.value }
        logger.info { "Loaded ${cache.size} configuration(s) into memory" }
    }

    fun findAll(pageable: Pageable): PagedModel<ConfigurationResponse> = configurationRepository.findAll(pageable)

    fun isEnabled(key: ConfigurationKey): Boolean = cache[key.key].toBoolean()

    @Transactional
    fun update(key: String, value: String): ConfigurationResponse {
        val configurationKey = ConfigurationKey.entries.find { it.key == key }
            ?: throw NotFoundException("Configuration $key not found")

        val response = configurationRepository.update(configurationKey.key, value)
            ?: throw NotFoundException("Configuration $key not found")

        cache[configurationKey.key] = value

        return response
    }
}
