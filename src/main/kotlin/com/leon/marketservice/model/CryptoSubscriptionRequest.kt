package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotEmpty

data class CryptoSubscriptionRequest(
    @field:NotEmpty(message = "Instrument codes list cannot be empty")
    @field:JsonProperty("instrumentCodes")
    val instrumentCodes: List<String>
)
