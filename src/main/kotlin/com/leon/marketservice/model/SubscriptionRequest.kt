package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotEmpty

data class SubscriptionRequest(
    @field:NotEmpty(message = "RICs list cannot be empty")
    @field:JsonProperty("rics")
    val rics: List<String>,
    
    @field:JsonProperty("intervals")
    val intervals: List<String> = listOf("1min", "5min", "15min", "30min", "60min")
)