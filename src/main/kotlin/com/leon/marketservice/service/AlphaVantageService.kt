package com.leon.marketservice.service

import com.leon.marketservice.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class AlphaVantageService(private val webClient: WebClient)
{
    private val logger = LoggerFactory.getLogger(AlphaVantageService::class.java)
    
    @Value("\${alpha.vantage.api.key}")
    private lateinit var apiKey: String
    
    @Value("\${alpha.vantage.base.url}")
    private lateinit var baseUrl: String

    fun fetchMarketData(ric: String): Mono<MarketData> 
    {
        logger.debug("Fetching current price for $ric from Alpha Vantage")
        
        val symbol = convertRicToSymbol(ric)
        val url = buildApiUrl(symbol)
        
        logger.debug("Making API request to Alpha Vantage: $url")
        
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response -> parseResponse(response, ric) }
            .doOnSuccess { logger.info("Successfully fetched current price for $ric") }
            .doOnError { e -> logger.error("Error fetching current price for $ric from Alpha Vantage", e) }
            .onErrorMap(WebClientResponseException::class.java)
            { e -> logger.error("Alpha Vantage API error for $ric: ${e.statusCode} - ${e.responseBodyAsString}")
                Exception("Failed to fetch data from Alpha Vantage: ${e.message}")
            }
    }

    fun fetchMarketDataForSymbols(rics: List<String>): Flux<MarketData> 
    {
        logger.debug("Fetching current prices for ${rics.size} symbols from Alpha Vantage")
        return Flux.fromIterable(rics)
            .flatMap { ric -> fetchMarketData(ric) }
            .doOnComplete { logger.info("Completed fetching current prices for ${rics.size} symbols") }
    }

    private fun convertRicToSymbol(ric: String): String 
    {
        return when 
        {
            ric.endsWith(".HK") -> ric.replace(".HK", ".HKG")
            ric.endsWith(".T") -> ric.replace(".T", ".TYO")
            else -> ric
        }
    }


    private fun buildApiUrl(symbol: String): String 
    {
        return "$baseUrl?function=GLOBAL_QUOTE&symbol=$symbol&apikey=$apiKey"
    }

    private fun parseResponse(response: Map<*, *>, ric: String): MarketData 
    {
        val symbol = convertRicToSymbol(ric)
        
        if (response.containsKey("Error Message"))
            throw Exception("Alpha Vantage API Error: ${response["Error Message"]}")
        
        if (response.containsKey("Note"))
            throw Exception("Alpha Vantage API Note: ${response["Note"]}")
        
        if (!response.containsKey("Global Quote"))
            throw Exception("Expected Global Quote response for $ric")
        
        return parseGlobalQuote(response, ric, symbol)
    }

    private fun parseGlobalQuote(response: Map<*, *>, ric: String, symbol: String): MarketData
    {
        val globalQuote = response["Global Quote"] as? Map<*, *>
            ?: throw Exception("Invalid Global Quote format for $ric")
        
        val currentTime = LocalDateTime.now()
        
        val price = parseDouble(globalQuote["05. price"] as? String) ?: 0.0
        if (price == 0.0)
            logger.warn("No price data found for $ric, using default value 0")
        
        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            timestamp = currentTime
        )
    }


    private fun parseDouble(value: String?): Double?
    {
        return try
        {
            value?.let { it.toDouble() }
        }
        catch (e: Exception)
        {
            null
        }
    }

}
