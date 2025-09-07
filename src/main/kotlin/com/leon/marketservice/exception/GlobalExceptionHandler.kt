package com.leon.marketservice.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

/**
 * Global exception handler for the market data service
 * Provides centralized error handling and consistent error responses
 * 
 * This handler catches all exceptions thrown by controllers and services,
 * logging them appropriately and returning user-friendly error responses.
 */
@RestControllerAdvice
class GlobalExceptionHandler 
{
    
    // Logger for this handler
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handle MarketDataException
     * Handles custom market data related exceptions
     * 
     * @param ex The MarketDataException
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(MarketDataException::class)
    fun handleMarketDataException(ex: MarketDataException): ResponseEntity<ErrorResponse> 
    {
        logger.error("Market data error: ${ex.message}", ex)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Market Data Error",
            message = ex.message ?: "Unknown market data error",
            path = "/api"
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handle DataSourceUnavailableException
     * Handles data source unavailability errors
     * 
     * @param ex The DataSourceUnavailableException
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(DataSourceUnavailableException::class)
    fun handleDataSourceUnavailableException(ex: DataSourceUnavailableException): ResponseEntity<ErrorResponse> 
    {
        logger.error("Data source unavailable: ${ex.message}", ex)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.SERVICE_UNAVAILABLE.value(),
            error = "Data Source Unavailable",
            message = ex.message ?: "Data source is currently unavailable",
            path = "/api"
        )
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse)
    }

    /**
     * Handle RateLimitExceededException
     * Handles rate limit exceeded errors
     * 
     * @param ex The RateLimitExceededException
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceededException(ex: RateLimitExceededException): ResponseEntity<ErrorResponse> 
    {
        logger.warn("Rate limit exceeded: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.TOO_MANY_REQUESTS.value(),
            error = "Rate Limit Exceeded",
            message = ex.message ?: "Rate limit exceeded",
            path = "/api"
        )
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse)
    }

    /**
     * Handle InvalidRicException
     * Handles invalid RIC code errors
     * 
     * @param ex The InvalidRicException
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(InvalidRicException::class)
    fun handleInvalidRicException(ex: InvalidRicException): ResponseEntity<ErrorResponse> 
    {
        logger.warn("Invalid RIC codes: ${ex.message}")
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Invalid RIC Codes",
            message = ex.message ?: "Invalid RIC codes provided",
            path = "/api"
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handle validation errors
     * Handles method argument validation failures
     * 
     * @param ex The MethodArgumentNotValidException
     * @return ResponseEntity with validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> 
    {
        logger.warn("Validation error: ${ex.message}")
        
        val errors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
            .filterValues { it != null }
            .mapValues { it.value!! }
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = "Request validation failed",
            path = "/api",
            details = errors
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handle generic exceptions
     * Handles all other unhandled exceptions
     * 
     * @param ex The Exception
     * @return ResponseEntity with generic error details
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> 
    {
        logger.error("Unexpected error occurred", ex)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "An unexpected error occurred",
            path = "/api"
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * Error response data class
     * Standardized error response format
     * 
     * @param timestamp When the error occurred
     * @param status HTTP status code
     * @param error Error type
     * @param message Human-readable error message
     * @param path Request path that caused the error
     * @param details Additional error details (optional)
     */
    data class ErrorResponse(
        val timestamp: LocalDateTime,
        val status: Int,
        val error: String,
        val message: String,
        val path: String,
        val details: Map<String, String>? = null
    )
}