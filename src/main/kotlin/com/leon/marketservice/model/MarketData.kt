package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Standardized market data model
 * Represents market data in a consistent format regardless of the source
 * 
 * @param ric Reuters Instrument Code for the security
 * @param symbol Standardized symbol for the security
 * @param price Current price of the security
 * @param open Opening price for the period
 * @param high Highest price for the period
 * @param low Lowest price for the period
 * @param volume Trading volume for the period
 * @param timestamp When this data was recorded
 * @param dataSource Which data source provided this information
 * @param interval Time interval for this data point (e.g., "1min", "daily")
 */
data class MarketData(
    @field:JsonProperty("ric")
    val ric: String,
    
    @field:JsonProperty("symbol")
    val symbol: String,
    
    @field:JsonProperty("price")
    val price: BigDecimal,
    
    @field:JsonProperty("open")
    val open: BigDecimal? = null,
    
    @field:JsonProperty("high")
    val high: BigDecimal? = null,
    
    @field:JsonProperty("low")
    val low: BigDecimal? = null,
    
    @field:JsonProperty("volume")
    val volume: Long? = null,
    
    @field:JsonProperty("timestamp")
    val timestamp: LocalDateTime,
    
    @field:JsonProperty("dataSource")
    val dataSource: DataSource,
    
    @field:JsonProperty("interval")
    val interval: String
)