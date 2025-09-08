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
class AllTickService(private val config: AllTickConfig, private val webClient: WebClient)
{
    
    private val logger = LoggerFactory.getLogger(AllTickService::class.java)

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
            val symbol = convertRicToSymbol(ric)
            val url = buildApiUrl(symbol)
            logger.debug("Making API request to AllTick: $url")
            val response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer ${config.apiKey}")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
            
            val marketData = if (response != null) {
                parseResponse(response, ric, interval)
            }
            else
            {
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
    private fun parseResponse(response: Map<*, *>, ric: String, interval: String): MarketData 
    {
        val symbol = convertRicToSymbol(ric)

        
        if (response.containsKey("error"))
            throw Exception("AllTick API Error: ${response["error"]}")
        
        if (response.containsKey("message"))
            throw Exception("AllTick API Message: ${response["message"]}")
        
        val marketData = when
        {
            response.containsKey("symbol") && response.containsKey("price") -> parseQuoteObject(response, ric, symbol, interval)
            response.containsKey("quotes") -> parseQuotesArray(response, ric, symbol, interval)
            response.containsKey("data") -> parseDataObject(response, ric, symbol, interval)
            response.containsKey("last") || response.containsKey("close") || response.containsKey("current") -> parseDirectFields(response, ric, symbol, interval)
            else -> throw Exception("Unknown AllTick response format for $ric: ${response.keys}")
        }
        
        return marketData
    }
    
    /**
     * Parse single quote object format
     */
    private fun parseQuoteObject(response: Map<*, *>, ric: String, symbol: String, interval: String): MarketData
    {
        val currentTime = LocalDateTime.now()
        
        val price = parseBigDecimal(response["price"] as? String) ?: parseBigDecimal(response["last"] as? String) ?: parseBigDecimal(response["close"] as? String) ?: BigDecimal.ZERO
        if (price == BigDecimal.ZERO)
            logger.warn("No price data found for $ric, using default value 0")
        
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            open = parseBigDecimal(response["open"] as? String),
            high = parseBigDecimal(response["high"] as? String),
            low = parseBigDecimal(response["low"] as? String),
            volume = parseLong(response["volume"] as? String),
            timestamp = currentTime,
            dataSource = DataSource.ALL_TICK,
            interval = interval
        )
    }
    
    /**
     * Parse quotes array format
     */
    private fun parseQuotesArray(response: Map<*, *>, ric: String, symbol: String, interval: String): MarketData
    {
        val quotes = response["quotes"] as? List<*>
            ?: throw Exception("Invalid quotes array format for $ric")
        
        val firstQuote = quotes.firstOrNull() as? Map<*, *>
            ?: throw Exception("No quotes found for $ric")
        
        val currentTime = LocalDateTime.now()
        
        val price = parseBigDecimal(firstQuote["price"] as? String) ?: parseBigDecimal(firstQuote["last"] as? String) ?: BigDecimal.ZERO
        if (price == BigDecimal.ZERO)
            logger.warn("No price data found for $ric, using default value 0")
        
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            open = parseBigDecimal(firstQuote["open"] as? String),
            high = parseBigDecimal(firstQuote["high"] as? String),
            low = parseBigDecimal(firstQuote["low"] as? String),
            volume = parseLong(firstQuote["volume"] as? String),
            timestamp = currentTime,
            dataSource = DataSource.ALL_TICK,
            interval = interval
        )
    }
    
    /**
     * Parse data object format
     */
    private fun parseDataObject(response: Map<*, *>, ric: String, symbol: String, interval: String): MarketData
    {
        val data = response["data"] as? Map<*, *>
            ?: throw Exception("Invalid data object format for $ric")
        
        val currentTime = LocalDateTime.now()
        
        val price = parseBigDecimal(data["price"] as? String) ?: parseBigDecimal(data["last"] as? String) ?: BigDecimal.ZERO
        if (price == BigDecimal.ZERO)
            logger.warn("No price data found for $ric, using default value 0")
        
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            open = parseBigDecimal(data["open"] as? String),
            high = parseBigDecimal(data["high"] as? String),
            low = parseBigDecimal(data["low"] as? String),
            volume = parseLong(data["volume"] as? String),
            timestamp = currentTime,
            dataSource = DataSource.ALL_TICK,
            interval = interval
        )
    }
    
    /**
     * Parse direct fields format
     */
    private fun parseDirectFields(response: Map<*, *>, ric: String, symbol: String, interval: String): MarketData
    {
        val currentTime = LocalDateTime.now()
        
        val price = parseBigDecimal(response["last"] as? String) ?: parseBigDecimal(response["close"] as? String) ?: parseBigDecimal(response["current"] as? String) ?: BigDecimal.ZERO
        if (price == BigDecimal.ZERO)
            logger.warn("No price data found for $ric, using default value 0")
        
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            open = parseBigDecimal(response["open"] as? String),
            high = parseBigDecimal(response["high"] as? String),
            low = parseBigDecimal(response["low"] as? String),
            volume = parseLong(response["volume"] as? String),
            timestamp = currentTime,
            dataSource = DataSource.ALL_TICK,
            interval = interval
        )
    }
    
    /**
     * Safely parse BigDecimal from string
     */
    private fun parseBigDecimal(value: String?): BigDecimal?
    {
        return try
        {
            value?.let { BigDecimal(it) }
        }
        catch (e: Exception)
        {
            null
        }
    }
    
    /**
     * Safely parse Long from string
     */
    private fun parseLong(value: String?): Long?
    {
        return try
        {
            value?.let { it.toLong() }
        }
        catch (e: Exception)
        {
            null
        }
    }
}
