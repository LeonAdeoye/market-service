package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class CryptoPriceData(
    @field:JsonProperty("symbol")
    val symbol: String,
    
    @field:JsonProperty("price")
    val price: Double? = null,

    @field:JsonProperty("vol_24h")
    val vol24h: Double? = null,
    
    @field:JsonProperty("timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now()
)
