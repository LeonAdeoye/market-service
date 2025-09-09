package com.leon.marketservice.service

import com.leon.marketservice.model.DataSource
import com.leon.marketservice.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import java.time.LocalDateTime

@Service
class AllTickService(private val webClient: WebClient)
{
    private val logger = LoggerFactory.getLogger(AllTickService::class.java)
    
    @Value("\${alltick.api.key}")
    private lateinit var apiKey: String
    
    @Value("\${alltick.base.url}")
    private lateinit var baseUrl: String

    fun fetchMarketData(ric: String): MarketData 
    {
        logger.debug("Fetching market data for $ric from AllTick")
        
        try 
        {
            val symbol = convertRicToSymbol(ric)
            val url = buildApiUrl(symbol)
            logger.debug("Making API request to AllTick: $url")
            val response = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer $apiKey")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()
            
            val marketData = if (response != null)
                parseResponse(response, ric)
            else
                throw Exception("Received null response from AllTick API")
            
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


    fun fetchMarketDataForSymbols(rics: List<String>): Flux<MarketData> 
    {
        logger.debug("Fetching market data for ${rics.size} symbols from AllTick")
        
        return Flux.fromIterable(rics)
            .flatMap { ric -> 
                try 
                {
                    Flux.just(fetchMarketData(ric))
                } 
                catch (e: Exception) 
                {
                    logger.error("Error fetching data for $ric", e)
                    Flux.empty()
                }
            }
            .doOnComplete { logger.info("Completed fetching current prices for ${rics.size} symbols") }
    }

    private fun convertRicToSymbol(ric: String): String 
    {
        return when 
        {
            ric.endsWith(".HK") -> ric.replace(".HK", ".HKEX")
            ric.endsWith(".T") -> ric.replace(".T", ".TSE")
            else -> ric
        }
    }

    private fun buildApiUrl(symbol: String): String 
    {
        return "$baseUrl/v1/quote?symbol=$symbol"
    }

    private fun parseResponse(response: Map<*, *>, ric: String): MarketData {
        val symbol = convertRicToSymbol(ric)

        if (response.containsKey("error"))
            throw Exception("AllTick API Error: ${response["error"]}")

        if (response.containsKey("message"))
            throw Exception("AllTick API Message: ${response["message"]}")

        val currentTime = LocalDateTime.now()

        val price = parseDouble(response["price"] as? String)
            ?: parseDouble(response["last"] as? String)
            ?: 0.0

        if (price == 0.0)
            logger.warn("No price data found for $ric, using default value 0")

        return MarketData(
            ric = ric,
            symbol = symbol,
            price = price,
            timestamp = currentTime,
            dataSource = DataSource.ALL_TICK
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
