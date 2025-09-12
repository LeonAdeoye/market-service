package com.leon.marketservice.controller

import com.leon.marketservice.service.MarketDataService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@CrossOrigin
@RestController
class HealthController(private val marketDataService: MarketDataService) 
{
    @GetMapping("/health")
    fun health(): ResponseEntity<String>
    {
        return ResponseEntity.ok("Up")
    }
    
    @GetMapping("/health/datasources")
    fun healthDataSources(): ResponseEntity<Map<String, Any>>
    {
        return try
        {
            val config = marketDataService.getConfiguration()
            val status = marketDataService.getSubscriptionStatus()
            
            val healthInfo = mapOf<String, Any>(
                "status" to "Up",
                "dataSources" to (config["dataSources"] ?: emptyMap<String, Any>()),
                "subscriptions" to status
            )
            
            ResponseEntity.ok(healthInfo)
        }
        catch (e: Exception)
        {
            ResponseEntity.status(500).body(mapOf<String, Any>(
                "status" to "Error",
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
}
