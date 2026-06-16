package br.com.investlog.server.typelists.rest.controllers

import br.com.investlog.server.typelists.domain.services.FundTypeService
import br.com.investlog.server.typelists.rest.payloads.TypeCreateRequest
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/fund-types")
class FundTypeController(
    private val fundTypeService: FundTypeService
) {

    @GetMapping
    fun findAll(pageable: Pageable): PagedModel<TypeResponse> = fundTypeService.findAll(pageable)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TypeCreateRequest): TypeResponse = fundTypeService.create(request.name)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        fundTypeService.delete(id)
    }
}
