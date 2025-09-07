package com.leon.marketservice.service

import com.leon.marketservice.model.DataSource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for comprehensive health checking and monitoring
 * Provides detailed health status for all system components
 * 
 * This service monitors the health of all system components including
 * external APIs, AMPS connection, and internal services.
 */
@Service
class HealthCheckService(
    private val alphaVantageService: AlphaVantageService,
    private val allTickService: AllTickService,
    private val ampsPublisherService: AmpsPublisherService,
    private val retryService: RetryService
) 
{
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(HealthCheckService::class.java)
    
    // Health check results cache
    private val healthCheckCache = ConcurrentHashMap<String, HealthCheckResult>()
    private val cacheTimeoutMs = 30000L // 30 seconds

    /**
     * Get comprehensive health status
     * Returns the health status of all system components
     * 
     * @return Map containing health status for all components
     */
    fun getOverallHealth(): Map<String, Any> 
    {
        val components = mapOf(
            "alphaVantage" to checkAlphaVantageHealth(),
            "allTick" to checkAllTickHealth(),
            "amps" to checkAmpsHealth(),
            "retryService" to checkRetryServiceHealth()
        )
        
        val overallStatus = determineOverallStatus(components)
        
        return mapOf(
            "status" to overallStatus,
            "timestamp" to LocalDateTime.now(),
            "components" to components,
            "summary" to createHealthSummary(components, overallStatus)
        )
    }

    /**
     * Get health status for a specific component
     * Returns detailed health status for a specific component
     * 
     * @param componentName The name of the component
     * @return Map containing component health status
     */
    fun getComponentHealth(componentName: String): Map<String, Any> 
    {
        return when (componentName.lowercase()) 
        {
            "alphavantage" -> checkAlphaVantageHealth()
            "alltick" -> checkAllTickHealth()
            "amps" -> checkAmpsHealth()
            "retry" -> checkRetryServiceHealth()
            else -> mapOf(
                "status" to "UNKNOWN",
                "message" to "Unknown component: $componentName",
                "timestamp" to LocalDateTime.now()
            )
        }
    }

    /**
     * Check Alpha Vantage health
     * Verifies Alpha Vantage service availability and performance
     * 
     * @return Health check result for Alpha Vantage
     */
    private fun checkAlphaVantageHealth(): Map<String, Any> 
    {
        val cacheKey = "alphaVantage"
        val cached = getCachedHealthCheck(cacheKey)
        if (cached != null) 
        {
            return cached
        }
        
        val startTime = System.currentTimeMillis()
        val isAvailable = alphaVantageService.isAvailable()
        val responseTime = System.currentTimeMillis() - startTime
        
        val status = if (isAvailable) 
        {
            if (responseTime > 5000) 
            {
                "DEGRADED"
            } 
            else 
            {
                "HEALTHY"
            }
        } 
        else 
        {
            "UNHEALTHY"
        }
        
        val result = mapOf(
            "status" to status,
            "available" to isAvailable,
            "responseTimeMs" to responseTime,
            "message" to if (isAvailable) "Alpha Vantage is available" else "Alpha Vantage is unavailable",
            "timestamp" to LocalDateTime.now()
        )
        
        cacheHealthCheck(cacheKey, result)
        return result
    }

    /**
     * Check AllTick health
     * Verifies AllTick service availability and performance
     * 
     * @return Health check result for AllTick
     */
    private fun checkAllTickHealth(): Map<String, Any> 
    {
        val cacheKey = "allTick"
        val cached = getCachedHealthCheck(cacheKey)
        if (cached != null) 
        {
            return cached
        }
        
        val startTime = System.currentTimeMillis()
        val isAvailable = allTickService.isAvailable()
        val responseTime = System.currentTimeMillis() - startTime
        
        val status = if (isAvailable) 
        {
            if (responseTime > 3000) 
            {
                "DEGRADED"
            } 
            else 
            {
                "HEALTHY"
            }
        } 
        else 
        {
            "UNHEALTHY"
        }
        
        val result = mapOf(
            "status" to status,
            "available" to isAvailable,
            "responseTimeMs" to responseTime,
            "message" to if (isAvailable) "AllTick is available" else "AllTick is unavailable",
            "timestamp" to LocalDateTime.now()
        )
        
        cacheHealthCheck(cacheKey, result)
        return result
    }

    /**
     * Check AMPS health
     * Verifies AMPS connection and publishing capability
     * 
     * @return Health check result for AMPS
     */
    private fun checkAmpsHealth(): Map<String, Any> 
    {
        val cacheKey = "amps"
        val cached = getCachedHealthCheck(cacheKey)
        if (cached != null) 
        {
            return cached
        }
        
        val isConnected = ampsPublisherService.isConnected()
        val connectionStatus = ampsPublisherService.getConnectionStatus()
        
        val status = if (isConnected) 
        {
            "HEALTHY"
        } 
        else 
        {
            "UNHEALTHY"
        }
        
        val result = mapOf(
            "status" to status,
            "connected" to isConnected,
            "connectionDetails" to connectionStatus,
            "message" to if (isConnected) "AMPS is connected" else "AMPS is disconnected",
            "timestamp" to LocalDateTime.now()
        )
        
        cacheHealthCheck(cacheKey, result)
        return result
    }


    /**
     * Check retry service health
     * Verifies retry service and circuit breaker status
     * 
     * @return Health check result for retry service
     */
    private fun checkRetryServiceHealth(): Map<String, Any> 
    {
        val circuitBreakerStatus = retryService.getCircuitBreakerStatus()
        val openCircuits = circuitBreakerStatus.values.count() 
        { 
            @Suppress("UNCHECKED_CAST")
            (it as? Map<String, Any>)?.get("state") == "OPEN" 
        }
        
        val status = when 
        {
            openCircuits == 0 -> "HEALTHY"
            openCircuits < circuitBreakerStatus.size / 2 -> "DEGRADED"
            else -> "UNHEALTHY"
        }
        
        return mapOf(
            "status" to status,
            "circuitBreakers" to circuitBreakerStatus,
            "openCircuits" to openCircuits,
            "message" to "Retry service is running with $openCircuits open circuits",
            "timestamp" to LocalDateTime.now()
        )
    }

    /**
     * Determine overall system status
     * Calculates the overall system health based on component statuses
     * 
     * @param components Map of component health statuses
     * @return Overall system status
     */
    private fun determineOverallStatus(components: Map<String, Map<String, Any>>): String 
    {
        val statuses = components.values.mapNotNull { it["status"] as? String }
        
        return when 
        {
            statuses.any { it == "UNHEALTHY" } -> "UNHEALTHY"
            statuses.any { it == "DEGRADED" } -> "DEGRADED"
            statuses.all { it == "HEALTHY" } -> "HEALTHY"
            else -> "UNKNOWN"
        }
    }

    /**
     * Create health summary
     * Creates a summary of the health status
     * 
     * @param components Map of component health statuses
     * @param overallStatus Overall system status
     * @return Health summary
     */
    private fun createHealthSummary(components: Map<String, Map<String, Any>>, overallStatus: String): Map<String, Any> 
    {
        val healthyCount = components.values.count { it["status"] == "HEALTHY" }
        val degradedCount = components.values.count { it["status"] == "DEGRADED" }
        val unhealthyCount = components.values.count { it["status"] == "UNHEALTHY" }
        
        return mapOf(
            "totalComponents" to components.size,
            "healthyComponents" to healthyCount,
            "degradedComponents" to degradedCount,
            "unhealthyComponents" to unhealthyCount,
            "overallStatus" to overallStatus,
            "recommendation" to getHealthRecommendation(overallStatus, unhealthyCount, degradedCount)
        )
    }

    /**
     * Get health recommendation
     * Provides recommendations based on health status
     * 
     * @param overallStatus Overall system status
     * @param unhealthyCount Number of unhealthy components
     * @param degradedCount Number of degraded components
     * @return Health recommendation
     */
    private fun getHealthRecommendation(overallStatus: String, unhealthyCount: Int, degradedCount: Int): String 
    {
        return when (overallStatus) 
        {
            "HEALTHY" -> "System is operating normally"
            "DEGRADED" -> "System is experiencing minor issues with $degradedCount degraded components. Monitor closely"
            "UNHEALTHY" -> "System has critical issues with $unhealthyCount unhealthy components. Immediate attention required"
            else -> "System status is unknown. Check logs for details"
        }
    }

    /**
     * Get cached health check result
     * Retrieves cached health check result if still valid
     * 
     * @param cacheKey The cache key
     * @return Cached result or null if expired
     */
    private fun getCachedHealthCheck(cacheKey: String): Map<String, Any>? 
    {
        val cached = healthCheckCache[cacheKey]
        return if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheTimeoutMs) 
        {
            cached.result
        } 
        else 
        {
            null
        }
    }

    /**
     * Cache health check result
     * Stores health check result in cache
     * 
     * @param cacheKey The cache key
     * @param result The health check result
     */
    private fun cacheHealthCheck(cacheKey: String, result: Map<String, Any>) 
    {
        healthCheckCache[cacheKey] = HealthCheckResult(
            result = result,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Data class for health check cache
     * Contains cached health check result and timestamp
     */
    private data class HealthCheckResult(
        val result: Map<String, Any>,
        val timestamp: Long
    )
}