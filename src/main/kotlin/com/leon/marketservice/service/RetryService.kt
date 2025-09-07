package com.leon.marketservice.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * Service for implementing retry mechanisms and circuit breaker pattern
 * Provides resilient data fetching with exponential backoff and circuit breaker
 * 
 * This service implements retry logic with exponential backoff and circuit breaker
 * pattern to handle failures gracefully and prevent cascading failures.
 */
@Service
class RetryService {
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(RetryService::class.java)
    
    // Circuit breaker states
    private enum class CircuitState {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, requests are blocked
        HALF_OPEN  // Testing if service is back to normal
    }
    
    // Circuit breaker configuration for each service
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreakerConfig>()
    
    // Retry configuration
    private val maxRetries = 3
    private val baseDelayMs = 1000L
    private val maxDelayMs = 30000L
    
    // Scheduled executor for circuit breaker recovery
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(5)

    /**
     * Execute operation with retry logic
     * Retries the operation with exponential backoff if it fails
     * 
     * @param operation The operation to execute
     * @param serviceName The name of the service (for circuit breaker)
     * @return The result of the operation
     * @throws Exception if all retries fail
     */
    fun <T> executeWithRetry(operation: () -> T, serviceName: String): T {
        val circuitBreaker = getOrCreateCircuitBreaker(serviceName)
        
        // Check circuit breaker state
        if (circuitBreaker.state == CircuitState.OPEN) {
            throw Exception("Circuit breaker is OPEN for service $serviceName")
        }
        
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                val result = operation()
                
                // Success - reset circuit breaker
                onSuccess(circuitBreaker)
                return result
                
            } catch (e: Exception) {
                lastException = e
                logger.warn("Attempt $attempt failed for service $serviceName: ${e.message}")
                
                // Record failure
                onFailure(circuitBreaker)
                
                // If this is not the last attempt, wait before retrying
                if (attempt < maxRetries) {
                    val delay = calculateDelay(attempt)
                    logger.debug("Waiting ${delay}ms before retry $attempt for service $serviceName")
                    Thread.sleep(delay)
                }
            }
        }
        
        // All retries failed
        logger.error("All $maxRetries retries failed for service $serviceName")
        throw lastException ?: Exception("Operation failed after $maxRetries retries")
    }

    /**
     * Execute operation with circuit breaker
     * Executes the operation with circuit breaker protection
     * 
     * @param operation The operation to execute
     * @param serviceName The name of the service
     * @return The result of the operation
     * @throws Exception if circuit breaker is open or operation fails
     */
    fun <T> executeWithCircuitBreaker(operation: () -> T, serviceName: String): T {
        val circuitBreaker = getOrCreateCircuitBreaker(serviceName)
        
        // Check circuit breaker state
        when (circuitBreaker.state) {
            CircuitState.OPEN -> {
                if (System.currentTimeMillis() - circuitBreaker.lastFailureTime > circuitBreaker.timeoutMs) {
                    // Timeout has passed, transition to HALF_OPEN
                    circuitBreaker.state = CircuitState.HALF_OPEN
                    logger.info("Circuit breaker for $serviceName transitioned to HALF_OPEN")
                } else {
                    throw Exception("Circuit breaker is OPEN for service $serviceName")
                }
            }
            CircuitState.HALF_OPEN -> {
                // Allow one request to test if service is back to normal
                logger.debug("Circuit breaker for $serviceName is HALF_OPEN, allowing test request")
            }
            CircuitState.CLOSED -> {
                // Normal operation
            }
        }
        
        try {
            val result = operation()
            onSuccess(circuitBreaker)
            return result
            
        } catch (e: Exception) {
            onFailure(circuitBreaker)
            throw e
        }
    }

    /**
     * Get circuit breaker status for all services
     * Returns the status of all circuit breakers
     * 
     * @return Map containing circuit breaker statuses
     */
    fun getCircuitBreakerStatus(): Map<String, Any> {
        return circuitBreakers.mapValues { (_, config) ->
            mapOf(
                "state" to config.state.name,
                "failureCount" to config.failureCount,
                "successCount" to config.successCount,
                "lastFailureTime" to config.lastFailureTime,
                "isHealthy" to (config.state == CircuitState.CLOSED)
            )
        }
    }

    /**
     * Reset circuit breaker for a service
     * Resets the circuit breaker to CLOSED state
     * 
     * @param serviceName The name of the service
     */
    fun resetCircuitBreaker(serviceName: String) {
        val circuitBreaker = circuitBreakers[serviceName]
        if (circuitBreaker != null) {
            circuitBreaker.state = CircuitState.CLOSED
            circuitBreaker.failureCount = 0
            circuitBreaker.successCount = 0
            circuitBreaker.lastFailureTime = 0L
            logger.info("Circuit breaker reset for service $serviceName")
        }
    }

    /**
     * Get or create circuit breaker configuration
     * Creates a new circuit breaker if one doesn't exist
     * 
     * @param serviceName The name of the service
     * @return Circuit breaker configuration
     */
    private fun getOrCreateCircuitBreaker(serviceName: String): CircuitBreakerConfig {
        return circuitBreakers.computeIfAbsent(serviceName) {
            CircuitBreakerConfig(
                serviceName = serviceName,
                state = CircuitState.CLOSED,
                failureCount = 0,
                successCount = 0,
                lastFailureTime = 0L,
                timeoutMs = 60000L, // 1 minute timeout
                failureThreshold = 5 // Open circuit after 5 failures
            )
        }
    }

    /**
     * Handle successful operation
     * Updates circuit breaker state on success
     * 
     * @param circuitBreaker The circuit breaker configuration
     */
    private fun onSuccess(circuitBreaker: CircuitBreakerConfig) {
        circuitBreaker.successCount++
        circuitBreaker.failureCount = 0
        
        if (circuitBreaker.state == CircuitState.HALF_OPEN) {
            circuitBreaker.state = CircuitState.CLOSED
            logger.info("Circuit breaker for ${circuitBreaker.serviceName} transitioned to CLOSED")
        }
    }

    /**
     * Handle failed operation
     * Updates circuit breaker state on failure
     * 
     * @param circuitBreaker The circuit breaker configuration
     */
    private fun onFailure(circuitBreaker: CircuitBreakerConfig) {
        circuitBreaker.failureCount++
        circuitBreaker.lastFailureTime = System.currentTimeMillis()
        
        if (circuitBreaker.failureCount >= circuitBreaker.failureThreshold) {
            circuitBreaker.state = CircuitState.OPEN
            logger.warn("Circuit breaker for ${circuitBreaker.serviceName} transitioned to OPEN")
            
            // Schedule recovery attempt
            scheduler.schedule({
                if (circuitBreaker.state == CircuitState.OPEN) {
                    circuitBreaker.state = CircuitState.HALF_OPEN
                    logger.info("Circuit breaker for ${circuitBreaker.serviceName} transitioned to HALF_OPEN")
                }
            }, circuitBreaker.timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Calculate delay for exponential backoff
     * Calculates the delay time for the next retry attempt
     * 
     * @param attempt The current attempt number
     * @return Delay in milliseconds
     */
    private fun calculateDelay(attempt: Int): Long {
        val delay = baseDelayMs * (2.0.pow(attempt - 1)).toLong()
        return min(delay, maxDelayMs)
    }

    /**
     * Shutdown the retry service
     * Cleans up resources
     */
    fun shutdown() {
        logger.info("Shutting down RetryService")
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
     * Data class for circuit breaker configuration
     * Contains all necessary information for circuit breaker operation
     */
    private data class CircuitBreakerConfig(
        val serviceName: String,
        var state: CircuitState,
        var failureCount: Int,
        var successCount: Int,
        var lastFailureTime: Long,
        val timeoutMs: Long,
        val failureThreshold: Int
    )
}
