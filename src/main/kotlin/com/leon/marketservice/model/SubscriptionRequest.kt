package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

/**
 * Request model for subscribing to market data
 * Contains all necessary information to start receiving market data for specific stocks
 * 
 * @param rics List of RIC (Reuters Instrument Code) identifiers for stocks to subscribe to
 * @param throttleTimeSeconds Minimum time interval between data updates in seconds
 * @param dataSource Optional specific data source override (if null, uses global default)
 * @param intervals List of time intervals for data (e.g., "1min", "5min", "daily")
 */
data class SubscriptionRequest(
    @field:NotEmpty(message = "RICs list cannot be empty")
    @field:JsonProperty("rics")
    val rics: List<String>,
    
    @field:NotNull(message = "Throttle time is required")
    @field:Min(value = 1, message = "Throttle time must be at least 1 second")
    @field:JsonProperty("throttleTimeSeconds")
    val throttleTimeSeconds: Long,
    
    @field:JsonProperty("dataSource")
    val dataSource: DataSource? = null,
    
    @field:JsonProperty("intervals")
    val intervals: List<String> = listOf("1min", "5min", "15min", "30min", "60min")
)