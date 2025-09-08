package com.leon.marketservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Configuration properties for market data source mapping
 * Defines which RIC endings should use which data sources
 */
@Component
@ConfigurationProperties(prefix = "market.data")
data class MarketDataConfig(
    /**
     * RIC endings that should use Alpha Vantage
     * Comma-separated list of suffixes (e.g., ".T,.KS,.SI")
     */
    var alphaVantageRicEndings: List<String> = emptyList(),
    
    /**
     * RIC endings that should use AllTick
     * Comma-separated list of suffixes (e.g., ".HK")
     */
    var allTickRicEndings: List<String> = emptyList()
) {
    /**
     * Check if Alpha Vantage is enabled (has RIC endings configured)
     */
    fun isAlphaVantageEnabled(): Boolean = alphaVantageRicEndings.isNotEmpty()
    
    /**
     * Check if AllTick is enabled (has RIC endings configured)
     */
    fun isAllTickEnabled(): Boolean = allTickRicEndings.isNotEmpty()
    
    /**
     * Determine which data source to use for a given RIC
     * 
     * @param ric The RIC code to check
     * @return DataSource.ALPHA_VANTAGE, DataSource.ALL_TICK, or null if no match
     */
    fun determineDataSource(ric: String): String? 
    {
        // Check AllTick endings first (more specific)
        for (ending in allTickRicEndings) 
        {
            if (ric.endsWith(ending)) 
            {
                return "ALL_TICK"
            }
        }
        
        // Check Alpha Vantage endings
        for (ending in alphaVantageRicEndings) 
        {
            if (ric.endsWith(ending)) 
            {
                return "ALPHA_VANTAGE"
            }
        }
        
        // No match found
        return null
    }
}
