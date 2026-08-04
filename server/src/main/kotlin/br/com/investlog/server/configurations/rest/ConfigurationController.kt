package br.com.investlog.server.configurations.rest

import br.com.investlog.server.configurations.rest.payloads.ConfigurationResponse
import br.com.investlog.server.configurations.rest.payloads.ConfigurationUpdateRequest
import br.com.investlog.server.configurations.services.ConfigurationService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/configurations")
class ConfigurationController(
    private val configurationService: ConfigurationService
) {

    @GetMapping
    fun findAll(pageable: Pageable): ResponseEntity<PagedModel<ConfigurationResponse>> =
        ResponseEntity.ok(configurationService.findAll(pageable))

    @PatchMapping("/{key}")
    fun update(
        @PathVariable key: String, @Valid @RequestBody request: ConfigurationUpdateRequest,
    ): ResponseEntity<ConfigurationResponse> =
        ResponseEntity.ok(configurationService.update(key, request.value))
}
