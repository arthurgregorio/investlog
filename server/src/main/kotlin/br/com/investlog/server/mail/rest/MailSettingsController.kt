package br.com.investlog.server.mail.rest

import br.com.investlog.server.mail.rest.payloads.MailEnabledRequest
import br.com.investlog.server.mail.rest.payloads.MailSettingsRequest
import br.com.investlog.server.mail.rest.payloads.MailSettingsResponse
import br.com.investlog.server.mail.rest.payloads.MailTestRequest
import br.com.investlog.server.mail.services.MailSettingsService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mail-settings")
class MailSettingsController(private val mailSettingsService: MailSettingsService) {

    @GetMapping
    fun find(): ResponseEntity<MailSettingsResponse> = ResponseEntity.ok(mailSettingsService.find())

    @PutMapping
    fun update(@Valid @RequestBody request: MailSettingsRequest): ResponseEntity<MailSettingsResponse> =
        ResponseEntity.ok(mailSettingsService.update(request))

    @PatchMapping("/enabled")
    fun updateEnabled(@RequestBody request: MailEnabledRequest): ResponseEntity<MailSettingsResponse> =
        ResponseEntity.ok(mailSettingsService.updateEnabled(request.enabled))

    @PostMapping("/test")
    fun sendTest(@Valid @RequestBody request: MailTestRequest): ResponseEntity<Void> {
        mailSettingsService.sendTest(request.recipient)
        return ResponseEntity.noContent().build()
    }
}
