package com.leon.marketservice.controller

import com.leon.marketservice.service.ScheduledDataFetcher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for managing the market data scheduler
 * Provides endpoints to configure and monitor the scheduled data fetching
 */
@RestController
@RequestMapping("/api/scheduler")
@CrossOrigin(origins = ["*"])
class SchedulerController(
    private val scheduledDataFetcher: ScheduledDataFetcher
) 
{

    /**
     * Get current scheduler configuration
     * Returns the current fetch interval and status
     * 
     * @return ResponseEntity containing scheduler status
     */
    @GetMapping("/status")
    fun getSchedulerStatus(): ResponseEntity<Map<String, Any>> 
    {
        val status = scheduledDataFetcher.getFetchingStatus()
        return ResponseEntity.ok(status)
    }

    /**
     * Update the fetch interval
     * Changes how often the scheduler fetches market data
     * 
     * @param intervalSeconds New interval in seconds (minimum 1)
     * @return ResponseEntity with success/error message
     */
    @PostMapping("/interval")
    fun updateFetchInterval(@RequestParam("intervalSeconds") intervalSeconds: Long): ResponseEntity<Map<String, String>> 
    {
        return try 
        {
            scheduledDataFetcher.updateFetchInterval(intervalSeconds)
            ResponseEntity.ok(mapOf(
                "message" to "Fetch interval updated to ${intervalSeconds} seconds",
                "intervalSeconds" to intervalSeconds.toString()
            ))
        } 
        catch (e: IllegalArgumentException) 
        {
            ResponseEntity.badRequest().body(mapOf(
                "error" to (e.message ?: "Invalid interval")
            ))
        } 
        catch (e: Exception) 
        {
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Failed to update interval: ${e.message ?: "Unknown error"}"
            ))
        }
    }

    /**
     * Get current fetch interval
     * Returns just the current interval value
     * 
     * @return ResponseEntity containing the current interval
     */
    @GetMapping("/interval")
    fun getFetchInterval(): ResponseEntity<Map<String, Long>> 
    {
        val interval = scheduledDataFetcher.getFetchInterval()
        return ResponseEntity.ok(mapOf("intervalSeconds" to interval))
    }
}
