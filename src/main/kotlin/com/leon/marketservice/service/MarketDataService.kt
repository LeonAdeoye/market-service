package com.leon.marketservice.service

import com.leon.marketservice.model.*
import com.leon.marketservice.model.SubscriptionDetails
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct
import com.fasterxml.jackson.annotation.JsonProperty

@Component
@ConfigurationProperties(prefix = "market.data")
@EnableConfigurationProperties
data class MarketDataConfig(var alphaVantageRicEndings: List<String> = emptyList(), var allTickRicEndings: List<String> = emptyList())
{
    private val logger = LoggerFactory.getLogger(MarketDataConfig::class.java)
    
    fun isAlphaVantageEnabled(): Boolean = alphaVantageRicEndings.isNotEmpty()
    fun isAllTickEnabled(): Boolean = allTickRicEndings.isNotEmpty()
    
    @PostConstruct
    fun logConfiguration()
    {
        logger.info("MarketDataConfig initialized with:")
        logger.info("  AllTick RIC endings: $allTickRicEndings")
        logger.info("  Alpha Vantage RIC endings: $alphaVantageRicEndings")
    }
    
    fun determineDataSourceFromRicEnding(ric: String): String?
    {
        logger.debug("Checking RIC '$ric' against AllTick endings: $allTickRicEndings")
        for (ending in allTickRicEndings) 
        {
            if (ric.endsWith(ending))
            {
                logger.debug("RIC '$ric' matches AllTick ending '$ending'")
                return "ALL_TICK"
            }
        }

        logger.debug("Checking RIC '$ric' against Alpha Vantage endings: $alphaVantageRicEndings")
        for (ending in alphaVantageRicEndings) 
        {
            if (ric.endsWith(ending))
            {
                logger.debug("RIC '$ric' matches Alpha Vantage ending '$ending'")
                return "ALPHA_VANTAGE"
            }
        }

        logger.debug("RIC '$ric' does not match any configured endings")
        return null
    }
    
    @Bean
    fun webClient(): WebClient 
    {
        return WebClient.builder().build()
    }
}

@Service
class MarketDataService(private val alphaVantageService: AlphaVantageService, private val allTickService: AllTickService,
    private val ampsPublisherService: AmpsPublisherService, private val marketDataConfig: MarketDataConfig)
{
    private val logger = LoggerFactory.getLogger(MarketDataService::class.java)
    private val subscriptions = ConcurrentHashMap<String, SubscriptionDetails>()

    fun subscribe(request: SubscriptionRequest): SubscriptionResponse 
    {
        logger.info("Processing subscription request for ${request.rics.size} stocks")
        logger.debug("AllTick RIC endings: ${marketDataConfig.allTickRicEndings}")
        logger.debug("Alpha Vantage RIC endings: ${marketDataConfig.alphaVantageRicEndings}")
        val subscriptionId = generateSubscriptionId()
        val successfulRics = mutableListOf<String>()
        
        for (ric in request.rics) 
        {
            try 
            {
                logger.debug("Processing RIC: $ric")
                val dataSource = determineDataSourceByRic(ric)
                val subscriptionDetails = SubscriptionDetails(ric = ric, subscriptionId = subscriptionId)
                subscriptions[ric] = subscriptionDetails
                successfulRics.add(ric)
                logger.info("Successfully subscribed to $ric using $dataSource")
            } 
            catch (e: Exception) 
            {
                logger.error("Failed to subscribe to $ric", e)
            }
        }
        
        return SubscriptionResponse(success = successfulRics.isNotEmpty(),
            message = if (successfulRics.isNotEmpty()) "Successfully subscribed to ${successfulRics.size} stocks" else "Failed to subscribe to any stocks",
            subscriptionId = subscriptionId, rics = successfulRics)
    }

    fun unsubscribe(ric: String) 
    {
        logger.info("Unsubscribing from $ric")
        val subscription = subscriptions.remove(ric)

        if (subscription != null)
            logger.info("Successfully unsubscribed from $ric")
        else
            logger.warn("No active subscription found for $ric")
    }

    fun getActiveSubscriptions(): Map<String, Any> 
    {
        return mapOf(
            "count" to subscriptions.size,
            "subscriptions" to subscriptions.values.map { subscription ->
                mapOf("ric" to subscription.ric, "subscriptionId" to subscription.subscriptionId)
            }
        )
    }

    fun getSubscriptionStatus(): Map<String, Any> 
    {
        val allTickRics = subscriptions.values.filter {
            try
            {
                determineDataSourceByRic(it.ric) == DataSource.ALL_TICK
            }
            catch (e: Exception)
            {
                false
            }
        }
        val alphaVantageRics = subscriptions.values.filter { 
            try
            {
                determineDataSourceByRic(it.ric) == DataSource.ALPHA_VANTAGE
            }
            catch (e: Exception)
            {
                false
            }
        }
        
        return mapOf("totalSubscriptions" to subscriptions.size,
            "allTickRics" to mapOf("count" to allTickRics.size, "rics" to allTickRics.map { it.ric }),
            "alphaVantageRics" to mapOf("count" to alphaVantageRics.size, "rics" to alphaVantageRics.map { it.ric }))
    }

    private fun determineDataSourceByRic(ric: String): DataSource 
    {
        return when (val dataSource = marketDataConfig.determineDataSourceFromRicEnding(ric))
        {
            "ALL_TICK" -> DataSource.ALL_TICK
            "ALPHA_VANTAGE" -> DataSource.ALPHA_VANTAGE
            null -> 
            {
                val availableEndings = marketDataConfig.alphaVantageRicEndings + marketDataConfig.allTickRicEndings
                throw IllegalArgumentException("RIC '$ric' does not match any configured endings. Available endings: ${availableEndings.joinToString()}")
            }
            else -> throw IllegalArgumentException("Unknown data source: $dataSource")
        }
    }

    fun getConfiguration(): Map<String, Any> 
    {
        return mapOf(
            "alphaVantageRicEndings" to marketDataConfig.alphaVantageRicEndings,
            "allTickRicEndings" to marketDataConfig.allTickRicEndings,
            "alphaVantageEnabled" to marketDataConfig.isAlphaVantageEnabled(),
            "allTickEnabled" to marketDataConfig.isAllTickEnabled()
        )
    }

    private fun generateSubscriptionId(): String 
    {
        return java.util.UUID.randomUUID().toString()
    }
}
