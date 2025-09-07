package com.leon.marketservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull

/**
 * Request model for switching data sources
 * Allows switching between Alpha Vantage and AllTick for all stocks or specific stocks
 * 
 * @param dataSource The data source to switch to
 * @param rics Optional list of specific RICs to switch (if null, applies to all stocks)
 * @param applyToAll Whether to apply this change to all stocks
 */
data class DataSourceSwitchRequest(
    @field:NotNull(message = "Data source is required")
    @field:JsonProperty("dataSource")
    val dataSource: DataSource,
    
    @field:JsonProperty("rics")
    val rics: List<String>? = null,
    
    @field:JsonProperty("applyToAll")
    val applyToAll: Boolean = false
)