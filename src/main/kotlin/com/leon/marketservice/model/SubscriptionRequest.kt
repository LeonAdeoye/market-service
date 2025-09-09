package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotEmpty

data class SubscriptionRequest(
    @field:NotEmpty(message = "RICs list cannot be empty")
    @field:JsonProperty("rics")
    val rics: List<String>
)