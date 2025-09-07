package com.leon.marketservice.exception

/**
 * Custom exception for market data related errors
 * Provides more specific error handling for market data operations
 * 
 * @param message Human-readable error message
 * @param cause The underlying cause of this exception
 */
open class MarketDataException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exception thrown when a data source is unavailable
 * Indicates that the requested data source cannot be reached
 */
class DataSourceUnavailableException(dataSource: String,cause: Throwable? = null) : MarketDataException("Data source $dataSource is currently unavailable", cause)

/**
 * Exception thrown when rate limits are exceeded
 * Indicates that too many requests have been made to a data source
 */
class RateLimitExceededException(dataSource: String,retryAfterSeconds: Long) : MarketDataException("Rate limit exceeded for $dataSource. Retry after $retryAfterSeconds seconds")

/**
 * Exception thrown when invalid RIC codes are provided
 * Indicates that one or more RIC codes in the request are invalid
 */
class InvalidRicException(invalidRics: List<String>) : MarketDataException("Invalid RIC codes: ${invalidRics.joinToString(", ")}")