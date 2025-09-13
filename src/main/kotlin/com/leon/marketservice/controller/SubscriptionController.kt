package com.leon.marketservice.controller

import com.leon.marketservice.model.SubscriptionRequest
import com.leon.marketservice.model.SubscriptionResponse
import com.leon.marketservice.service.MarketDataService
import com.leon.marketservice.service.ScheduledDataFetcher
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@CrossOrigin
class SubscriptionController(private val marketDataService: MarketDataService, private val scheduledDataFetcher: ScheduledDataFetcher)
{
    private val logger = LoggerFactory.getLogger(SubscriptionController::class.java)

    @PostMapping("/subscribe")
    fun subscribe(@Valid @RequestBody request: SubscriptionRequest): ResponseEntity<SubscriptionResponse> 
    {
        logger.info("Received subscription request for RICs: ${request.rics}")
        return try 
        {
            val response = marketDataService.subscribe(request)
            logger.info("Successfully created subscription: ${response.subscriptionId}")
            ResponseEntity.ok(response)
        }
        catch (e: Exception)
        {
            logger.error("Failed to create subscription for RICs: ${request.rics}", e)
            val errorResponse = SubscriptionResponse(success = false, message = "Failed to create subscription: ${e.message}")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
        }
    }

    @DeleteMapping("/unsubscribe/{ric}")
    fun unsubscribe(@PathVariable ric: String): ResponseEntity<Map<String, Any>> 
    {
        logger.info("Received unsubscribe request for RIC: $ric")
        return try
        {
            marketDataService.unsubscribe(ric)
            logger.info("Successfully unsubscribed from RIC: $ric")
            ResponseEntity.ok(mapOf("success" to true, "message" to "Successfully unsubscribed from $ric"))
        }
        catch (e: Exception)
        {
            logger.error("Failed to unsubscribe from RIC: $ric", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("success" to false, "message" to "Failed to unsubscribe: ${e.message}"))
        }
    }

    @GetMapping("/subscriptions")
    fun getSubscriptions(): ResponseEntity<Map<String, Any>> 
    {
        logger.info("Received request for all subscriptions")
        return try
        {
            val subscriptions = marketDataService.getActiveSubscriptions()
            logger.info("Retrieved active subscriptions")
            ResponseEntity.ok(subscriptions)
        }
        catch (e: Exception)
        {
            logger.error("Failed to retrieve subscriptions", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "Failed to retrieve subscriptions: ${e.message}"))
        }
    }

    @PostMapping("/interval")
    fun updateFetchInterval(@RequestParam("intervalSeconds") intervalSeconds: Long): ResponseEntity<String> 
    {
        return try
        {
            scheduledDataFetcher.updateFetchInterval(intervalSeconds)
            ResponseEntity.ok("Updated to $intervalSeconds seconds")
        }
        catch (e: IllegalArgumentException)
        {
            ResponseEntity.badRequest().body("Invalid interval: ${e.message}")
        }
    }

    @GetMapping("/config")
    fun getConfiguration(): ResponseEntity<Map<String, Any>> 
    {
        logger.info("Received request for configuration")
        return try
        {
            val config = marketDataService.getConfiguration()
            ResponseEntity.ok(config)
        }
        catch (e: Exception)
        {
            logger.error("Failed to retrieve configuration", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "Failed to retrieve configuration: ${e.message}"))
        }
    }
}