package com.leon.marketservice.service

import com.leon.marketservice.model.DataSource
import com.leon.marketservice.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
class AlphaVantageService(private val webClient: WebClient)
{
    private val logger = LoggerFactory.getLogger(AlphaVantageService::class.java)
    
    @Value("\${alpha.vantage.api.key}")
    private lateinit var apiKey: String
    
    @Value("\${alpha.vantage.base.url}")
    private lateinit var baseUrl: String

    /**
     * Fetch market data for a specific stock (reactive)
     * Retrieves the latest market data from Alpha Vantage API
     * 
     * @param ric The RIC code for the stock
     * @param interval The time interval for the data (e.g., "1min", "5min", "daily")
     * @return Mono<MarketData> containing the market information
     */
    fun fetchMarketData(ric: String, interval: String = "1min"): Mono<MarketData> 
    {
        logger.debug("Fetching market data for $ric from Alpha Vantage")
        
        val symbol = convertRicToSymbol(ric)
        
        val function = determineFunction(interval)
        
        val url = buildApiUrl(function, symbol, interval)
        
        logger.debug("Making API request to Alpha Vantage: $url")
        
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response -> parseResponse(response, ric, interval) }
            .doOnSuccess { logger.info("Successfully fetched market data for $ric") }
            .doOnError { e -> logger.error("Error fetching market data for $ric from Alpha Vantage", e) }
            .onErrorMap(WebClientResponseException::class.java) { e ->
                logger.error("Alpha Vantage API error for $ric: ${e.statusCode} - ${e.responseBodyAsString}")
                Exception("Failed to fetch data from Alpha Vantage: ${e.message}")
            }
    }

    /**
     * Fetch market data for multiple stocks concurrently (reactive)
     * Efficiently fetches data for multiple RICs in parallel
     * 
     * @param rics List of RIC codes
     * @param interval The time interval for the data
     * @return Flux<MarketData> containing market data for all RICs
     */
    fun fetchMarketDataForSymbols(rics: List<String>, interval: String = "1min"): Flux<MarketData> 
    {
        logger.debug("Fetching market data for ${rics.size} symbols from Alpha Vantage")
        
        return Flux.fromIterable(rics)
            .flatMap { ric -> fetchMarketData(ric, interval) }
            .doOnComplete { logger.info("Completed fetching market data for ${rics.size} symbols") }
    }

    /**
     * Check if the service is available
     * Verifies that Alpha Vantage API is accessible and not rate limited
     * 
     * @return true if the service is available, false otherwise
     */
    fun isAvailable(): Boolean 
    {
        return try 
        {
            fetchMarketData("AAPL", "daily").block()
            true
        } 
        catch (e: Exception) 
        {
            logger.warn("Alpha Vantage service is not available", e)
            false
        }
    }


    /**
     * Convert RIC code to Alpha Vantage symbol format
     * Maps RIC codes to the format expected by Alpha Vantage API
     * 
     * @param ric The RIC code (e.g., "0700.HK", "7203.T")
     * @return The Alpha Vantage symbol format
     */
    private fun convertRicToSymbol(ric: String): String 
    {
        return when 
        {
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
    private fun determineFunction(interval: String): String 
    {
        return when (interval) 
        {
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
    private fun buildApiUrl(function: String, symbol: String, interval: String): String 
    {
        return when (function) 
        {
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
    private fun parseResponse(response: Map<*, *>, ric: String, interval: String): MarketData 
    {
        val symbol = convertRicToSymbol(ric)
        
        if (response.containsKey("Error Message"))
            throw Exception("Alpha Vantage API Error: ${response["Error Message"]}")
        
        if (response.containsKey("Note"))
            throw Exception("Alpha Vantage API Note: ${response["Note"]}")
        
        val marketData = when
        {
            response.containsKey("Global Quote") -> parseGlobalQuote(response, ric, symbol, interval)
            response.containsKey("Time Series (${interval})") -> parseTimeSeries(response, ric, symbol, interval, "Time Series (${interval})")
            response.containsKey("Time Series (Daily)") -> parseTimeSeries(response, ric, symbol, interval, "Time Series (Daily)")
            response.containsKey("Time Series (Weekly)") -> parseTimeSeries(response, ric, symbol, interval, "Time Series (Weekly)")
            response.containsKey("Time Series (Monthly)") -> parseTimeSeries(response, ric, symbol, interval, "Time Series (Monthly)")
            response.containsKey("Meta Data") -> parseTimeSeries(response, ric, symbol, interval, "Time Series (${interval})")
            else -> throw Exception("Unknown Alpha Vantage response format for $ric")
        }
        
        return marketData
    }

    private fun parseGlobalQuote(response: Map<*, *>, ric: String, symbol: String, interval: String): MarketData
    {
        val globalQuote = response["Global Quote"] as? Map<*, *>
            ?: throw Exception("Invalid Global Quote format for $ric")
        
        val currentTime = LocalDateTime.now()
        
        val price = parseBigDecimal(globalQuote["05. price"] as? String) ?: BigDecimal.ZERO
        if (price == BigDecimal.ZERO)
            logger.warn("No price data found for $ric, using default value 0")
        
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            open = parseBigDecimal(globalQuote["02. open"] as? String),
            high = parseBigDecimal(globalQuote["03. high"] as? String),
            low = parseBigDecimal(globalQuote["04. low"] as? String),
            volume = parseLong(globalQuote["06. volume"] as? String),
            timestamp = currentTime,
            dataSource = DataSource.ALPHA_VANTAGE,
            interval = interval
        )
    }

    private fun parseTimeSeries(response: Map<*, *>, ric: String, symbol: String, interval: String, timeSeriesKey: String): MarketData
    {
        val timeSeries = response[timeSeriesKey] as? Map<*, *>
            ?: throw Exception("Invalid Time Series format for $ric")
        
        val mostRecentEntry = timeSeries.entries.firstOrNull()
            ?: throw Exception("No time series data found for $ric")
        
        val timestamp = mostRecentEntry.key as String
        val data = mostRecentEntry.value as? Map<*, *>
            ?: throw Exception("Invalid time series data format for $ric")
        
        val parsedTimestamp = try
        {
            LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }
        catch (e: Exception)
        {
            try
            {
                LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
            catch (e2: Exception)
            {
                LocalDateTime.now()
            }
        }
        
        val price = parseBigDecimal(data["4. close"] as? String) ?: parseBigDecimal(data["5. adjusted close"] as? String) ?: BigDecimal.ZERO
        if (price == BigDecimal.ZERO)
        {
            logger.warn("No price data found for $ric, using default value 0")
        }
        
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            open = parseBigDecimal(data["1. open"] as? String),
            high = parseBigDecimal(data["2. high"] as? String),
            low = parseBigDecimal(data["3. low"] as? String),
            volume = parseLong(data["5. volume"] as? String) ?: parseLong(data["6. volume"] as? String),
            timestamp = parsedTimestamp,
            dataSource = DataSource.ALPHA_VANTAGE,
            interval = interval
        )
    }

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
