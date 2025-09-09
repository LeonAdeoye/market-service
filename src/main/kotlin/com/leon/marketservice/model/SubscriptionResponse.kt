package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class SubscriptionResponse(
    @field:JsonProperty("success")
    val success: Boolean,
    
    @field:JsonProperty("message")
    val message: String,
    
    @field:JsonProperty("subscriptionId")
    val subscriptionId: String? = null,
    
    @field:JsonProperty("timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now(),
    
    @field:JsonProperty("rics")
    val rics: List<String> = emptyList()
)