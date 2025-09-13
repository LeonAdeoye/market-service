package com.leon.marketservice.service

import com.leon.marketservice.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import kotlin.random.Random

@Service
class GaussianRandomDataService
{
    private val logger = LoggerFactory.getLogger(GaussianRandomDataService::class.java)
    
    @Value("\${gaussian.random.base.price:100.0}")
    private var basePrice: Double = 100.0
    
    @Value("\${gaussian.random.volatility:0.02}")
    private var volatility: Double = 0.02
    
    @Value("\${gaussian.random.drift:0.0001}")
    private var drift: Double = 0.0001
    
    private val random = Random.Default
    private val stockPrices = mutableMapOf<String, Double>()
    private var hasSpare = false
    private var spare = 0.0
    
    fun generateMarketData(ric: String): Mono<MarketData>
    {
        logger.debug("Generating random price for $ric using Gaussian distribution")
        
        val currentPrice = generateRandomPrice(ric)
        val currentTime = LocalDateTime.now()
        
        val marketData = MarketData(
            ric = ric,
            symbol = ric,
            price = currentPrice,
            timestamp = currentTime
        )
        
        logger.debug("Generated price $currentPrice for $ric")
        return Mono.just(marketData)
    }
    
    fun generateMarketDataForSymbols(rics: List<String>): Flux<MarketData>
    {
        logger.debug("Generating random prices for ${rics.size} symbols using Gaussian distribution")
        return Flux.fromIterable(rics)
            .flatMap { ric -> generateMarketData(ric) }
            .doOnComplete { logger.info("Completed generating random prices for ${rics.size} symbols") }
    }
    
    private fun generateRandomPrice(ric: String): Double
    {
        val previousPrice = stockPrices[ric] ?: basePrice
        val gaussianRandom = generateGaussianRandom()
        val priceChange: Double = previousPrice * (drift + volatility * gaussianRandom)
        val newPrice: Double = previousPrice + priceChange
        val finalPrice = maxOf(newPrice, 0.01)
        stockPrices[ric] = finalPrice
        return finalPrice
    }
    
    private fun generateGaussianRandom(): Double
    {
        if (hasSpare)
        {
            hasSpare = false
            return spare
        }
        
        hasSpare = true
        val u = random.nextDouble()
        val v = random.nextDouble()
        val mag = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u))
        spare = v * mag
        return u * mag
    }
    
    fun resetPrice(ric: String)
    {
        stockPrices.remove(ric)
        logger.debug("Reset price history for $ric")
    }
    
    fun resetAllPrices()
    {
        stockPrices.clear()
        logger.info("Reset all price histories")
    }
    
    fun getCurrentPrice(ric: String): Double?
    {
        return stockPrices[ric]
    }
    
    fun getAllCurrentPrices(): Map<String, Double>
    {
        return stockPrices.toMap()
    }
    
    fun updateBasePrice(newBasePrice: Double)
    {
        if (newBasePrice <= 0)
            throw IllegalArgumentException("Base price must be positive")
        
        basePrice = newBasePrice
        logger.info("Updated base price to $basePrice")
    }
    
    fun updateVolatility(newVolatility: Double)
    {
        if (newVolatility < 0)
            throw IllegalArgumentException("Volatility must be non-negative")
        
        volatility = newVolatility
        logger.info("Updated volatility to $volatility")
    }
    
    fun updateDrift(newDrift: Double)
    {
        drift = newDrift
        logger.info("Updated drift to $drift")
    }
    
    fun getConfiguration(): Map<String, Any>
    {
        return mapOf(
            "dataSource" to "Gaussian Random Generator",
            "basePrice" to basePrice,
            "volatility" to volatility,
            "drift" to drift,
            "enabled" to true
        )
    }
}
