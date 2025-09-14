package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty

data class CryptoSubscriptionDetails(
    @field:JsonProperty("instrumentCode")
    val instrumentCode: String,
    
    @field:JsonProperty("subscriptionId")
    val subscriptionId: String
)
