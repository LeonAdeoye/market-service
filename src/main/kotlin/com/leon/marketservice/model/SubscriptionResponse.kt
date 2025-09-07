package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Response model for subscription operations
 * Provides feedback on the success or failure of subscription requests
 * 
 * @param success Indicates whether the subscription was successful
 * @param message Human-readable message describing the result
 * @param subscriptionId Unique identifier for the subscription
 * @param timestamp When the subscription was processed
 * @param rics List of RICs that were successfully subscribed to
 */
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