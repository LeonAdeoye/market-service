package com.leon.marketservice.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Service for managing client-controlled throttling
 * Implements throttling mechanism to control the frequency of data updates
 * based on client-specified throttle times
 * 
 * This service ensures that data is only fetched and published after
 * the specified throttle time has passed for each stock.
 */
@Service
class ThrottleService {
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(ThrottleService::class.java)
    
    // Throttle configuration for each RIC
    private val throttleConfigs = ConcurrentHashMap<String, ThrottleConfig>()
    
    // Last update times for each RIC
    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()
    
    // Scheduled executor for throttle management
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(10)
    
    // Minimum throttle time in seconds (from configuration)
    private val minThrottleSeconds = 1L
    private val maxThrottleSeconds = 3600L // 1 hour

    /**
     * Setup throttling for a specific RIC
     * Configures throttling parameters for the given stock
     * 
     * @param ric The RIC code for the stock
     * @param throttleTimeSeconds The throttle time in seconds
     * @throws IllegalArgumentException if throttle time is invalid
     */
    fun setupThrottling(ric: String, throttleTimeSeconds: Long) {
        logger.debug("Setting up throttling for $ric with ${throttleTimeSeconds}s interval")
        
        // Validate throttle time
        if (throttleTimeSeconds < minThrottleSeconds) {
            throw IllegalArgumentException("Throttle time must be at least $minThrottleSeconds seconds")
        }
        
        if (throttleTimeSeconds > maxThrottleSeconds) {
            throw IllegalArgumentException("Throttle time cannot exceed $maxThrottleSeconds seconds")
        }
        
        // Create throttle configuration
        val config = ThrottleConfig(
            ric = ric,
            throttleTimeSeconds = throttleTimeSeconds,
            lastUpdateTime = 0L,
            isActive = true
        )
        
        // Store the configuration
        throttleConfigs[ric] = config
        lastUpdateTimes[ric] = 0L
        
        logger.info("Throttling configured for $ric: ${throttleTimeSeconds}s interval")
    }

    /**
     * Check if enough time has passed for a RIC to be updated
     * Determines whether the throttle time has elapsed for the given stock
     * 
     * @param ric The RIC code to check
     * @return true if enough time has passed, false otherwise
     */
    fun canUpdate(ric: String): Boolean {
        val config = throttleConfigs[ric] ?: return false
        val lastUpdate = lastUpdateTimes[ric] ?: 0L
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastUpdate
        val throttleTimeMs = config.throttleTimeSeconds * 1000
        
        val canUpdate = timeSinceLastUpdate >= throttleTimeMs
        
        if (!canUpdate) {
            val remainingTime = (throttleTimeMs - timeSinceLastUpdate) / 1000
            logger.debug("Throttle active for $ric, ${remainingTime}s remaining")
        }
        
        return canUpdate
    }

    /**
     * Record that an update has been performed for a RIC
     * Updates the last update time for the given stock
     * 
     * @param ric The RIC code that was updated
     */
    fun recordUpdate(ric: String) {
        val currentTime = System.currentTimeMillis()
        lastUpdateTimes[ric] = currentTime
        
        // Update the throttle config
        throttleConfigs[ric]?.let { config ->
            throttleConfigs[ric] = config.copy(lastUpdateTime = currentTime)
        }
        
        logger.debug("Recorded update for $ric at $currentTime")
    }

    /**
     * Remove throttling for a specific RIC
     * Cleans up throttling configuration for the given stock
     * 
     * @param ric The RIC code to remove throttling for
     */
    fun removeThrottling(ric: String) {
        logger.debug("Removing throttling for $ric")
        
        throttleConfigs.remove(ric)
        lastUpdateTimes.remove(ric)
        
        logger.info("Throttling removed for $ric")
    }

    /**
     * Get remaining time until next update is allowed
     * Calculates how long to wait before the next update for a RIC
     * 
     * @param ric The RIC code to check
     * @return Remaining time in seconds, or 0 if update is allowed
     */
    fun getRemainingTime(ric: String): Long {
        val config = throttleConfigs[ric] ?: return 0L
        val lastUpdate = lastUpdateTimes[ric] ?: 0L
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastUpdate
        val throttleTimeMs = config.throttleTimeSeconds * 1000
        
        return maxOf(0, (throttleTimeMs - timeSinceLastUpdate) / 1000)
    }

    /**
     * Get throttling status for all active RICs
     * Returns information about all configured throttling
     * 
     * @return Map containing throttling status for all RICs
     */
    fun getThrottlingStatus(): Map<String, Any> {
        val status = mutableMapOf<String, Any>()
        
        for ((ric, config) in throttleConfigs) {
            val remainingTime = getRemainingTime(ric)
            val canUpdate = canUpdate(ric)
            
            status[ric] = mapOf(
                "throttleTimeSeconds" to config.throttleTimeSeconds,
                "remainingTimeSeconds" to remainingTime,
                "canUpdate" to canUpdate,
                "isActive" to config.isActive
            )
        }
        
        return mapOf(
            "totalRics" to throttleConfigs.size,
            "throttleConfigs" to status
        )
    }

    /**
     * Update throttle time for an existing RIC
     * Modifies the throttle time for a stock that already has throttling configured
     * 
     * @param ric The RIC code to update
     * @param newThrottleTimeSeconds The new throttle time in seconds
     * @throws IllegalArgumentException if throttle time is invalid
     */
    fun updateThrottleTime(ric: String, newThrottleTimeSeconds: Long) {
        logger.info("Updating throttle time for $ric to ${newThrottleTimeSeconds}s")
        
        val config = throttleConfigs[ric]
        if (config == null) {
            throw IllegalArgumentException("No throttling configured for $ric")
        }
        
        setupThrottling(ric, newThrottleTimeSeconds)
    }

    /**
     * Cleanup resources
     * Shuts down the scheduler and cleans up resources
     */
    fun shutdown() {
        logger.info("Shutting down ThrottleService")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Data class to hold throttle configuration
     * Contains all necessary information for throttling a specific RIC
     */
    private data class ThrottleConfig(
        val ric: String,
        val throttleTimeSeconds: Long,
        val lastUpdateTime: Long,
        val isActive: Boolean
    )
}
