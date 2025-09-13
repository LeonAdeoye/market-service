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
        val symbol = convertRicToSymbol(ric)
        val url = buildApiUrl(symbol)
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response -> parseResponse(response, ric) }
            .onErrorMap(WebClientResponseException::class.java)
            { e -> 
                logger.warn("Alpha Vantage API error for $ric: ${e.statusCode} - ${e.responseBodyAsString}")
                Exception("Failed to fetch data from Alpha Vantage: ${e.message}")
            }
    }

    fun fetchMarketDataForSymbols(rics: List<String>): Flux<MarketData> 
    {
        return Flux.fromIterable(rics)
            .flatMap { ric -> fetchMarketData(ric)
                    .onErrorResume {
                        Mono.just(MarketData(ric = ric, price = 0.0, timestamp = java.time.LocalDateTime.now()))
                    }
            }
//            .doOnComplete { logger.info("Completed fetching current prices for ${rics.size} symbols") }
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
        if (response.containsKey("Error Message"))
            throw Exception("Alpha Vantage API Error: ${response["Error Message"]}")
        
        if (response.containsKey("Note"))
            throw Exception("Alpha Vantage API Note: ${response["Note"]}")
        
        if (!response.containsKey("Global Quote"))
            throw Exception("Expected Global Quote response for $ric")
        
        return parseGlobalQuote(response, ric)
    }

    private fun parseGlobalQuote(response: Map<*, *>, ric: String): MarketData
    {
        val globalQuote = response["Global Quote"] as? Map<*, *>
            ?: throw Exception("Invalid Global Quote format for $ric")
        
        val currentTime = LocalDateTime.now()
        
        val price = parseDouble(globalQuote["05. price"] as? String) ?: 0.0
        if (price == 0.0)
            logger.warn("No price data found for $ric, using default value 0")
        
        return MarketData(
            ric = ric,
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
    
    fun getConfiguration(): Map<String, Any>
    {
        return mapOf(
            "dataSource" to "Alpha Vantage",
            "baseUrl" to baseUrl,
            "enabled" to true
        )
    }

}
