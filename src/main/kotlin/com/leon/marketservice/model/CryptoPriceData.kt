package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class CryptoPriceData(
    @field:JsonProperty("symbol")
    val symbol: String,
    
    @field:JsonProperty("best_ask")
    val bestAsk: Double? = null,
    
    @field:JsonProperty("best_bid")
    val bestBid: Double? = null,
    
    @field:JsonProperty("vwap_today")
    val vwapToday: Double? = null,
    
    @field:JsonProperty("vwap_24h")
    val vwap24h: Double? = null,
    
    @field:JsonProperty("low")
    val low: Double? = null,
    
    @field:JsonProperty("high")
    val high: Double? = null,
    
    @field:JsonProperty("open")
    val open: Double? = null,
    
    @field:JsonProperty("close")
    val close: Double? = null,
    
    @field:JsonProperty("vol_today")
    val volToday: Double? = null,
    
    @field:JsonProperty("vol_24h")
    val vol24h: Double? = null,
    
    @field:JsonProperty("num_trades")
    val numTrades: Long? = null,
    
    @field:JsonProperty("num_trades_24h")
    val numTrades24h: Long? = null,
    
    @field:JsonProperty("timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now()
)
