package com.leon.marketservice.service

import com.leon.marketservice.model.CryptoPriceData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class CoinMarketCapService(private val webClient: WebClient)
{
    private val logger = LoggerFactory.getLogger(CoinMarketCapService::class.java)
    @Value("\${coinmarketcap.api.key}")
    private lateinit var apiKey: String
    @Value("\${coinmarketcap.base.url}")
    private lateinit var baseUrl: String

    fun fetchCryptoPriceData(symbol: String): Mono<CryptoPriceData> 
    {
        val url = buildApiUrl(symbol)
        return webClient.get()
            .uri(url)
            .header("X-CMC_PRO_API_KEY", apiKey)
            .header("Accept", "application/json")
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response -> parseResponse(response, symbol) }
            .onErrorMap(WebClientResponseException::class.java)
            {
                e ->
                    logger.warn("CoinMarketCap API error for $symbol: ${e.statusCode} - ${e.responseBodyAsString}")
                    Exception("Failed to fetch data from CoinMarketCap: ${e.message}")
            }
    }

    fun fetchCryptoPriceDataForSymbols(symbols: List<String>): Flux<CryptoPriceData> 
    {
        return Flux.fromIterable(symbols)
            .flatMap { symbol -> fetchCryptoPriceData(symbol)
                    .onErrorResume {
                        Mono.just(CryptoPriceData(symbol = symbol))
                    }
            }
    }

    private fun buildApiUrl(symbol: String): String = "$baseUrl/v1/cryptocurrency/quotes/latest?symbol=$symbol"

    private fun parseResponse(response: Map<*, *>, symbol: String): CryptoPriceData 
    {
        val status = response["status"] as? Map<*, *>
        if (status?.get("error_code") != null)
        {
            val errorMessage = status["error_message"] as? String ?: "Unknown error"
            throw Exception("CoinMarketCap API Error: $errorMessage")
        }
        
        val data = response["data"] as? Map<*, *> ?: throw Exception("Expected data response for $symbol")
        val cryptoData = data[symbol] as? Map<*, *> ?: throw Exception("No data found for symbol $symbol")
        val quote = cryptoData["quote"] as? Map<*, *>
        val usdQuote = quote?.get("USD") as? Map<*, *> ?: throw Exception("No USD quote found for $symbol")
        val currentTime = LocalDateTime.now()
        
        return CryptoPriceData(
            symbol = symbol,
            price = parseDouble(usdQuote["price"] as? Double),
            vol24h = parseDouble(usdQuote["volume_24h"] as? Double),
            timestamp = currentTime
        )
    }

    private fun parseDouble(value: Double?): Double? = value?.takeIf { it > 0.0 }
}
