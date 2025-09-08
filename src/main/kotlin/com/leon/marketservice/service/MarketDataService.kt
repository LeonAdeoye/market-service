package com.leon.marketservice.service

import com.leon.marketservice.config.MarketDataConfig
import com.leon.marketservice.model.*
import com.leon.marketservice.model.SubscriptionDetails
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Main market data service that orchestrates data fetching and publishing
 * This service acts as the central coordinator for all market data operations,
 * managing subscriptions and coordinating between data sources
 * 
 * The service maintains a registry of active subscriptions and automatically
 * routes HK stocks to AllTick and other stocks to Alpha Vantage
 */
@Service
class MarketDataService(
    private val alphaVantageService: AlphaVantageService,
    private val allTickService: AllTickService,
    private val ampsPublisherService: AmpsPublisherService,
    private val marketDataConfig: MarketDataConfig
) 
{
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(MarketDataService::class.java)
    
    // Registry of active subscriptions - maps RIC to subscription details
    private val subscriptions = ConcurrentHashMap<String, SubscriptionDetails>()

    /**
     * Subscribe to market data for specified stocks
     * Creates subscriptions with automatic data source selection based on RIC
     * 
     * @param request The subscription request containing all necessary parameters
     * @return SubscriptionResponse indicating success or failure
     */
    fun subscribe(request: SubscriptionRequest): SubscriptionResponse 
    {
        logger.info("Processing subscription request for ${request.rics.size} stocks")
        
        val subscriptionId = generateSubscriptionId()
        val successfulRics = mutableListOf<String>()
        
        // Process each RIC in the request
        for (ric in request.rics) 
        {
            try 
            {
                // Automatically determine data source based on RIC
                val dataSource = determineDataSourceByRic(ric)
                
                // Create subscription details
                val subscriptionDetails = SubscriptionDetails(
                    ric = ric,
                    subscriptionId = subscriptionId,
                    throttleTimeSeconds = request.throttleTimeSeconds,
                    dataSource = dataSource,
                    intervals = request.intervals
                )
                
                // Register the subscription
                subscriptions[ric] = subscriptionDetails
                
                successfulRics.add(ric)
                logger.info("Successfully subscribed to $ric using $dataSource")
                
            } catch (e: Exception) {
                logger.error("Failed to subscribe to $ric", e)
                // Continue with other RICs even if one fails
            }
        }
        
        return SubscriptionResponse(
            success = successfulRics.isNotEmpty(),
            message = if (successfulRics.isNotEmpty()) "Successfully subscribed to ${successfulRics.size} stocks" else "Failed to subscribe to any stocks",
            subscriptionId = subscriptionId,
            rics = successfulRics
        )
    }

    /**
     * Unsubscribe from market data for a specific stock
     * Removes the subscription and stops data fetching
     * 
     * @param ric The RIC code to unsubscribe from
     */
    fun unsubscribe(ric: String) 
    {
        logger.info("Unsubscribing from $ric")
        
        val subscription = subscriptions.remove(ric)
        if (subscription != null) 
        {
            logger.info("Successfully unsubscribed from $ric")
        } 
        else 
        {
            logger.warn("No active subscription found for $ric")
        }
    }

    /**
     * Get list of active subscriptions
     * Returns information about all currently active subscriptions
     * 
     * @return Map containing subscription information
     */
    fun getActiveSubscriptions(): Map<String, Any> 
    {
        return mapOf(
            "count" to subscriptions.size,
            "subscriptions" to subscriptions.values.map { subscription ->
                mapOf(
                    "ric" to subscription.ric,
                    "subscriptionId" to subscription.subscriptionId,
                    "dataSource" to subscription.dataSource,
                    "throttleTimeSeconds" to subscription.throttleTimeSeconds,
                    "intervals" to subscription.intervals
                )
            }
        )
    }

    /**
     * Get current subscription status
     * Returns information about active subscriptions and their data sources
     * 
     * @return Map containing subscription status
     */
    fun getSubscriptionStatus(): Map<String, Any> 
    {
        val hkRics = subscriptions.values.filter { it.dataSource == DataSource.ALL_TICK }
        val otherRics = subscriptions.values.filter { it.dataSource == DataSource.ALPHA_VANTAGE }
        
        return mapOf(
            "totalSubscriptions" to subscriptions.size,
            "hongKongStocks" to mapOf(
                "count" to hkRics.size,
                "rics" to hkRics.map { it.ric }
            ),
            "otherMarkets" to mapOf(
                "count" to otherRics.size,
                "rics" to otherRics.map { it.ric }
            )
        )
    }

    /**
     * Automatically determine data source based on RIC code
     * Uses configurable RIC endings to determine the appropriate data source
     * 
     * @param ric The RIC code
     * @return The appropriate data source, or throws exception if no match
     * @throws IllegalArgumentException if RIC doesn't match any configured endings
     */
    private fun determineDataSourceByRic(ric: String): DataSource 
    {
        val dataSource = marketDataConfig.determineDataSource(ric)
        
        return when (dataSource) 
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

    /**
     * Generate a unique subscription ID
     * Creates a unique identifier for each subscription
     * 
     * @return A unique subscription ID
     */
    private fun generateSubscriptionId(): String 
    {
        return "sub_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

}
