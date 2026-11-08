package br.com.investlog.server.notifications.rest

import br.com.investlog.server.notifications.rest.payloads.NotificationPreferencesRequest
import br.com.investlog.server.notifications.rest.payloads.NotificationPreferencesResponse
import br.com.investlog.server.notifications.services.NotificationPreferencesService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/profile/notifications")
class NotificationPreferencesController(private val notificationPreferencesService: NotificationPreferencesService) {

    @GetMapping
    fun find(): ResponseEntity<NotificationPreferencesResponse> = ResponseEntity.ok(notificationPreferencesService.find())

    @PutMapping
    fun update(@Valid @RequestBody request: NotificationPreferencesRequest): ResponseEntity<NotificationPreferencesResponse> =
        ResponseEntity.ok(notificationPreferencesService.update(request))
}
