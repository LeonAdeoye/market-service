package com.leon.marketservice.controller

import com.leon.marketservice.model.*
import com.leon.marketservice.service.CryptoMarketDataService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/crypto")
@CrossOrigin
class CryptoPriceController(private val cryptoMarketDataService: CryptoMarketDataService)
{
    private val logger = LoggerFactory.getLogger(CryptoPriceController::class.java)

    @PostMapping("/subscribe")
    fun subscribe(@RequestBody request: CryptoSubscriptionRequest): ResponseEntity<CryptoSubscriptionResponse>
    {
        logger.info("Received crypto subscription request for instruments: ${request.instrumentCodes}")
        
        return try
        {
            val response = cryptoMarketDataService.subscribe(request)
            if (response.success)
            {
                logger.info("Successfully processed crypto subscription request: ${response.message}")
                ResponseEntity.ok(response)
            }
            else
            {
                logger.warn("Failed to process crypto subscription request: ${response.message}")
                ResponseEntity.badRequest().body(response)
            }
        }
        catch (e: Exception)
        {
            logger.error("Error processing crypto subscription request", e)
            val errorResponse = CryptoSubscriptionResponse(
                success = false,
                message = "Internal server error: ${e.message}"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    @DeleteMapping("/unsubscribe/{instrumentCode}")
    fun unsubscribe(@PathVariable instrumentCode: String): ResponseEntity<Map<String, String>>
    {
        logger.info("Received crypto unsubscribe request for instrument: $instrumentCode")
        
        return try
        {
            cryptoMarketDataService.unsubscribe(instrumentCode)
            val response = mapOf(
                "message" to "Successfully unsubscribed from crypto instrument $instrumentCode",
                "instrumentCode" to instrumentCode
            )
            ResponseEntity.ok(response)
        }
        catch (e: Exception)
        {
            logger.error("Error processing crypto unsubscribe request for $instrumentCode", e)
            val errorResponse = mapOf(
                "error" to "Failed to unsubscribe from crypto instrument $instrumentCode: ${e.message}",
                "instrumentCode" to instrumentCode
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }

    @GetMapping("/subscriptions")
    fun getActiveSubscriptions(): ResponseEntity<Map<String, Any>>
    {
        logger.info("Retrieving active crypto subscriptions")
        
        return try
        {
            val subscriptions = cryptoMarketDataService.getActiveSubscriptions()
            ResponseEntity.ok(subscriptions)
        }
        catch (e: Exception)
        {
            logger.error("Error retrieving crypto subscriptions", e)
            val errorResponse = mapOf(
                "error" to "Failed to retrieve crypto subscriptions: ${e.message}"
            )
            ResponseEntity.internalServerError().body(errorResponse)
        }
    }
}
