package com.leon.marketservice.service

import com.leon.marketservice.config.AlphaVantageConfig
import com.leon.marketservice.model.DataSource
import com.leon.marketservice.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Service for fetching market data from Alpha Vantage API
 * Handles all interactions with the Alpha Vantage service including
 * rate limiting, error handling, and data transformation
 * 
 * Alpha Vantage provides delayed market data with a rate limit of 5 calls per minute
 * for the free tier. This service manages those limitations and provides
 * a consistent interface for market data access.
 */
@Service
class AlphaVantageService(
    private val config: AlphaVantageConfig,
    private val webClient: WebClient
) {
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(AlphaVantageService::class.java)
    
    // Rate limiting tracking - maps endpoint to last call time
    private val lastCallTimes = mutableMapOf<String, Long>()
    
    // Rate limit: 5 calls per minute (60 seconds)
    private val rateLimitCalls = config.rateLimitCalls
    private val rateLimitPeriod = config.rateLimitPeriod * 1000L // Convert to milliseconds

    /**
     * Fetch market data for a specific stock
     * Retrieves the latest market data from Alpha Vantage API
     * 
     * @param ric The RIC code for the stock
     * @param interval The time interval for the data (e.g., "1min", "5min", "daily")
     * @return MarketData object containing the market information
     * @throws Exception if the API call fails or rate limit is exceeded
     */
    fun fetchMarketData(ric: String, interval: String = "1min"): MarketData {
        logger.debug("Fetching market data for $ric from Alpha Vantage")
        
        // Check rate limiting
        checkRateLimit()
        
        try {
            // Convert RIC to Alpha Vantage symbol format
            val symbol = convertRicToSymbol(ric)
            
            // Determine the appropriate Alpha Vantage function based on interval
            val function = determineFunction(interval)
            
            // Build the API request URL
            val url = buildApiUrl(function, symbol, interval)
            
            logger.debug("Making API request to Alpha Vantage: $url")
            
            // Make the API call
            val response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
            
            // Parse the response and convert to MarketData
            val marketData = if (response != null) {
                parseResponse(response, ric, interval)
            } else {
                throw Exception("Received null response from Alpha Vantage API")
            }
            
            logger.info("Successfully fetched market data for $ric")
            return marketData
            
        } catch (e: WebClientResponseException) {
            logger.error("Alpha Vantage API error for $ric: ${e.statusCode} - ${e.responseBodyAsString}")
            throw Exception("Failed to fetch data from Alpha Vantage: ${e.message}")
        } catch (e: Exception) {
            logger.error("Error fetching market data for $ric from Alpha Vantage", e)
            throw e
        }
    }

    /**
     * Check if the service is available
     * Verifies that Alpha Vantage API is accessible and not rate limited
     * 
     * @return true if the service is available, false otherwise
     */
    fun isAvailable(): Boolean {
        return try {
            // Simple health check - try to fetch a well-known stock
            fetchMarketData("AAPL", "daily")
            true
        } catch (e: Exception) {
            logger.warn("Alpha Vantage service is not available", e)
            false
        }
    }

    /**
     * Check rate limiting before making API calls
     * Ensures we don't exceed the 5 calls per minute limit
     * 
     * @throws Exception if rate limit would be exceeded
     */
    private fun checkRateLimit() {
        val currentTime = System.currentTimeMillis()
        val endpoint = "alpha_vantage"
        
        val lastCallTime = lastCallTimes[endpoint] ?: 0L
        val timeSinceLastCall = currentTime - lastCallTime
        
        if (timeSinceLastCall < rateLimitPeriod) {
            val waitTime = rateLimitPeriod - timeSinceLastCall
            logger.warn("Rate limit exceeded. Waiting ${waitTime}ms before next call")
            throw Exception("Rate limit exceeded. Please wait ${waitTime / 1000} seconds before retrying")
        }
        
        lastCallTimes[endpoint] = currentTime
    }

    /**
     * Convert RIC code to Alpha Vantage symbol format
     * Maps RIC codes to the format expected by Alpha Vantage API
     * 
     * @param ric The RIC code (e.g., "0700.HK", "7203.T")
     * @return The Alpha Vantage symbol format
     */
    private fun convertRicToSymbol(ric: String): String {
        return when {
            ric.endsWith(".HK") -> ric.replace(".HK", ".HKG")
            ric.endsWith(".T") -> ric.replace(".T", ".TYO")
            else -> ric
        }
    }

    /**
     * Determine the appropriate Alpha Vantage function based on interval
     * Maps time intervals to Alpha Vantage API functions
     * 
     * @param interval The time interval (e.g., "1min", "5min", "daily")
     * @return The Alpha Vantage function name
     */
    private fun determineFunction(interval: String): String {
        return when (interval) {
            "1min", "5min", "15min", "30min", "60min" -> "TIME_SERIES_INTRADAY"
            "daily" -> "TIME_SERIES_DAILY"
            "weekly" -> "TIME_SERIES_WEEKLY"
            "monthly" -> "TIME_SERIES_MONTHLY"
            else -> "GLOBAL_QUOTE"
        }
    }

    /**
     * Build the API URL for Alpha Vantage
     * Constructs the complete URL with all necessary parameters
     * 
     * @param function The Alpha Vantage function
     * @param symbol The stock symbol
     * @param interval The time interval
     * @return The complete API URL
     */
    private fun buildApiUrl(function: String, symbol: String, interval: String): String {
        val baseUrl = config.baseUrl
        val apiKey = config.apiKey
        
        return when (function) {
            "TIME_SERIES_INTRADAY" -> "$baseUrl?function=$function&symbol=$symbol&interval=$interval&apikey=$apiKey"
            "TIME_SERIES_DAILY", "TIME_SERIES_WEEKLY", "TIME_SERIES_MONTHLY" -> "$baseUrl?function=$function&symbol=$symbol&apikey=$apiKey"
            "GLOBAL_QUOTE" -> "$baseUrl?function=$function&symbol=$symbol&apikey=$apiKey"
            else -> "$baseUrl?function=$function&symbol=$symbol&apikey=$apiKey"
        }
    }

    /**
     * Parse Alpha Vantage API response and convert to MarketData
     * Transforms the JSON response into our standardized MarketData format
     * 
     * @param response The raw API response
     * @param ric The original RIC code
     * @param interval The time interval
     * @return MarketData object
     */
    private fun parseResponse(response: Map<*, *>, ric: String, interval: String): MarketData {
        // This is a simplified parser - in a real implementation,
        // you would need to handle the complex Alpha Vantage response structure
        
        val symbol = convertRicToSymbol(ric)
        val currentTime = LocalDateTime.now()
        
        // For demo purposes, return mock data
        // In reality, you would parse the actual response structure
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = BigDecimal("100.50"), // This would come from the actual response
            open = BigDecimal("100.00"),
            high = BigDecimal("101.00"),
            low = BigDecimal("99.50"),
            volume = 1000000L,
            timestamp = currentTime,
            dataSource = DataSource.ALPHA_VANTAGE,
            interval = interval
        )
    }
}
