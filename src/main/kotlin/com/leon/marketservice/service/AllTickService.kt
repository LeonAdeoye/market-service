package com.leon.marketservice.service

import com.leon.marketservice.config.AllTickConfig
import com.leon.marketservice.model.DataSource
import com.leon.marketservice.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Service for fetching market data from AllTick API
 * Handles all interactions with the AllTick service including
 * real-time data streaming and data transformation
 * 
 * AllTick provides real-time tick data with higher frequency updates
 * compared to Alpha Vantage. This service provides a consistent interface 
 * for real-time market data access.
 */
@Service
class AllTickService(
    private val config: AllTickConfig,
    private val webClient: WebClient
) 
{
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(AllTickService::class.java)
    
    // Real-time data connection management would go here
    // For now, we'll implement REST API calls

    /**
     * Fetch market data for a specific stock
     * Retrieves the latest market data from AllTick API
     * 
     * @param ric The RIC code for the stock
     * @param interval The time interval for the data (not used for real-time data)
     * @return MarketData object containing the market information
     * @throws Exception if the API call fails
     */
    fun fetchMarketData(ric: String, interval: String = "realtime"): MarketData 
    {
        logger.debug("Fetching market data for $ric from AllTick")
        
        try 
        {
            // Convert RIC to AllTick symbol format
            val symbol = convertRicToSymbol(ric)
            
            // Build the API request URL
            val url = buildApiUrl(symbol)
            
            logger.debug("Making API request to AllTick: $url")
            
            // Make the API call
            val response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer ${config.apiKey}")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
            
            // Parse the response and convert to MarketData
            val marketData = if (response != null) {
                parseResponse(response, ric, interval)
            } else {
                throw Exception("Received null response from AllTick API")
            }
            
            logger.info("Successfully fetched market data for $ric")
            return marketData
            
        } 
        catch (e: WebClientResponseException) 
        {
            logger.error("AllTick API error for $ric: ${e.statusCode} - ${e.responseBodyAsString}")
            throw Exception("Failed to fetch data from AllTick: ${e.message}")
        } 
        catch (e: Exception) 
        {
            logger.error("Error fetching market data for $ric from AllTick", e)
            throw e
        }
    }

    /**
     * Check if the service is available
     * Verifies that AllTick API is accessible
     * 
     * @return true if the service is available, false otherwise
     */
    fun isAvailable(): Boolean 
    {
        return try 
        {
            // Simple health check - try to fetch a well-known stock
            fetchMarketData("AAPL")
            true
        } 
        catch (e: Exception) 
        {
            logger.warn("AllTick service is not available", e)
            false
        }
    }

    /**
     * Fetch market data for multiple stocks (reactive)
     * Efficiently fetches data for multiple RICs in parallel
     * 
     * @param rics List of RIC codes
     * @param interval The time interval for the data
     * @return Flux<MarketData> containing market data for all RICs
     */
    fun fetchMarketDataForSymbols(rics: List<String>, interval: String = "realtime"): Flux<MarketData> 
    {
        logger.debug("Fetching market data for ${rics.size} symbols from AllTick")
        
        return Flux.fromIterable(rics)
            .flatMap { ric -> 
                try 
                {
                    Flux.just(fetchMarketData(ric, interval))
                } 
                catch (e: Exception) 
                {
                    logger.error("Error fetching data for $ric", e)
                    Flux.empty()
                }
            }
            .doOnComplete { logger.info("Completed fetching market data for ${rics.size} symbols") }
    }

    /**
     * Convert RIC code to AllTick symbol format
     * Maps RIC codes to the format expected by AllTick API
     * 
     * @param ric The RIC code (e.g., "0700.HK", "7203.T")
     * @return The AllTick symbol format
     */
    private fun convertRicToSymbol(ric: String): String 
    {
        // AllTick might use different symbol formats
        // This is a placeholder - adjust based on actual AllTick API requirements
        return when 
        {
            ric.endsWith(".HK") -> ric.replace(".HK", ".HKEX")
            ric.endsWith(".T") -> ric.replace(".T", ".TSE")
            else -> ric
        }
    }

    /**
     * Build the API URL for AllTick
     * Constructs the complete URL with all necessary parameters
     * 
     * @param symbol The stock symbol
     * @return The complete API URL
     */
    private fun buildApiUrl(symbol: String): String 
    {
        val baseUrl = config.baseUrl
        return "$baseUrl/v1/quote?symbol=$symbol"
    }

    /**
     * Parse AllTick API response and convert to MarketData
     * Transforms the JSON response into our standardized MarketData format
     * 
     * @param response The raw API response
     * @param ric The original RIC code
     * @param interval The time interval
     * @return MarketData object
     */
    @Suppress("UNUSED_PARAMETER")
    private fun parseResponse(response: Map<*, *>, ric: String, interval: String): MarketData 
    {
        // This is a simplified parser - in a real implementation,
        // you would need to handle the actual AllTick response structure
        
        val symbol = convertRicToSymbol(ric)
        val currentTime = LocalDateTime.now()
        
        // For demo purposes, return mock data
        // In reality, you would parse the actual response structure
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = BigDecimal("101.25"), // This would come from the actual response
            open = BigDecimal("100.50"),
            high = BigDecimal("102.00"),
            low = BigDecimal("100.00"),
            volume = 1500000L,
            timestamp = currentTime,
            dataSource = DataSource.ALL_TICK,
            interval = interval
        )
    }
}
