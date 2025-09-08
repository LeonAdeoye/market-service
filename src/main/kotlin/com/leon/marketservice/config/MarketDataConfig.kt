package com.leon.marketservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "market.data")
data class MarketDataConfig(var alphaVantageRicEndings: List<String> = emptyList(), var allTickRicEndings: List<String> = emptyList())
{
    fun isAlphaVantageEnabled(): Boolean = alphaVantageRicEndings.isNotEmpty()
    
    fun isAllTickEnabled(): Boolean = allTickRicEndings.isNotEmpty()
    
    fun determineDataSource(ric: String): String? 
    {
        for (ending in allTickRicEndings) 
        {
            if (ric.endsWith(ending))
                return "ALL_TICK"
        }

        for (ending in alphaVantageRicEndings) 
        {
            if (ric.endsWith(ending))
                return "ALPHA_VANTAGE"
        }

        return null
    }
}
