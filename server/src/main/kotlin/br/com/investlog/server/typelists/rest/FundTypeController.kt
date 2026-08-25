package br.com.investlog.server.typelists.rest

import br.com.investlog.server.typelists.services.FundTypeService
import br.com.investlog.server.typelists.rest.payloads.TypeCreateRequest
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.typelists.rest.payloads.TypeUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/fund-types")
class FundTypeController(
    private val fundTypeService: FundTypeService
) {

    @GetMapping
    fun findAll(pageable: Pageable): ResponseEntity<PagedModel<TypeResponse>> =
        ResponseEntity.ok(fundTypeService.findAll(pageable))

    @PostMapping
    fun create(@Valid @RequestBody request: TypeCreateRequest): ResponseEntity<TypeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(fundTypeService.create(request.name))

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: TypeUpdateRequest): ResponseEntity<TypeResponse> =
        ResponseEntity.ok(fundTypeService.update(id, request.name))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        fundTypeService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
