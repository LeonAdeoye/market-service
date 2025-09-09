package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class MarketData(
    @field:JsonProperty("ric")
    val ric: String,
    
    @field:JsonProperty("symbol")
    val symbol: String,
    
    @field:JsonProperty("price")
    val price: Double,
    
    @field:JsonProperty("timestamp")
    val timestamp: LocalDateTime,
    
    @field:JsonProperty("dataSource")
    val dataSource: DataSource
)