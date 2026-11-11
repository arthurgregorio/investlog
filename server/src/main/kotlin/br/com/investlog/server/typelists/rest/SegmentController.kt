package br.com.investlog.server.typelists.rest

import br.com.investlog.server.typelists.services.SegmentService
import br.com.investlog.server.typelists.rest.payloads.TypeCreateRequest
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.typelists.rest.payloads.TypeUpdateRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
@RequestMapping("/segments")
class SegmentController(
    private val segmentService: SegmentService
) {

    @GetMapping
    fun findAll(pageable: Pageable): ResponseEntity<PagedModel<TypeResponse>> =
        ResponseEntity.ok(segmentService.findAll(pageable))

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    fun create(@Valid @RequestBody request: TypeCreateRequest): ResponseEntity<TypeResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(segmentService.create(request.name))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    fun update(@PathVariable id: UUID, @Valid @RequestBody request: TypeUpdateRequest): ResponseEntity<TypeResponse> =
        ResponseEntity.ok(segmentService.update(id, request.name))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        segmentService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
