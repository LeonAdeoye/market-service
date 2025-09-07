package com.leon.marketservice.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@CrossOrigin
@RestController
class HealthController()
{
    @GetMapping("/health")
    fun health(): ResponseEntity<String>
    {
        return ResponseEntity.ok("Up")
    }
}
