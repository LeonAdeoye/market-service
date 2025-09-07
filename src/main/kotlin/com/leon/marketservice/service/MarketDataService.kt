package com.leon.marketservice.service

import com.leon.marketservice.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Main market data service that orchestrates data fetching and publishing
 * This service acts as the central coordinator for all market data operations,
 * managing subscriptions, data source switching, and throttling
 * 
 * The service maintains a registry of active subscriptions and their configurations,
 * and coordinates between different data sources (Alpha Vantage and AllTick)
 */
@Service
class MarketDataService(
    private val alphaVantageService: AlphaVantageService,
    private val allTickService: AllTickService,
    private val ampsPublisherService: AmpsPublisherService,
    private val throttleService: ThrottleService
) {
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(MarketDataService::class.java)
    
    // Global default data source - can be overridden per stock
    @Value("\${market.data.default.source:ALPHA_VANTAGE}")
    private lateinit var defaultDataSource: String
    
    // Registry of active subscriptions - maps RIC to subscription details
    private val subscriptions = ConcurrentHashMap<String, SubscriptionDetails>()
    
    // Per-stock data source overrides - maps RIC to specific data source
    private val stockDataSourceOverrides = ConcurrentHashMap<String, DataSource>()

    /**
     * Subscribe to market data for specified stocks
     * Creates subscriptions with the given parameters and starts data fetching
     * 
     * @param request The subscription request containing all necessary parameters
     * @return SubscriptionResponse indicating success or failure
     */
    fun subscribe(request: SubscriptionRequest): SubscriptionResponse {
        logger.info("Processing subscription request for ${request.rics.size} stocks")
        
        val subscriptionId = generateSubscriptionId()
        val successfulRics = mutableListOf<String>()
        
        // Process each RIC in the request
        for (ric in request.rics) {
            try {
                // Determine which data source to use for this RIC
                val dataSource = determineDataSource(ric, request.dataSource)
                
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
                
                // Set up throttling for this subscription
                throttleService.setupThrottling(ric, request.throttleTimeSeconds)
                
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
    fun unsubscribe(ric: String) {
        logger.info("Unsubscribing from $ric")
        
        val subscription = subscriptions.remove(ric)
        if (subscription != null) {
            throttleService.removeThrottling(ric)
            logger.info("Successfully unsubscribed from $ric")
        } else {
            logger.warn("No active subscription found for $ric")
        }
    }

    /**
     * Get list of active subscriptions
     * Returns information about all currently active subscriptions
     * 
     * @return Map containing subscription information
     */
    fun getActiveSubscriptions(): Map<String, Any> {
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
     * Switch the global default data source
     * Changes the default data source for all new subscriptions
     * 
     * @param dataSource The new default data source
     */
    fun switchGlobalDataSource(dataSource: DataSource) {
        logger.info("Switching global data source to $dataSource")
        defaultDataSource = dataSource.name
    }

    /**
     * Switch data source for specific stocks
     * Changes the data source for the specified RICs
     * 
     * @param rics List of RIC codes to switch
     * @param dataSource The new data source for these stocks
     */
    fun switchStockDataSource(rics: List<String>, dataSource: DataSource) {
        logger.info("Switching data source for ${rics.size} stocks to $dataSource")
        
        for (ric in rics) {
            stockDataSourceOverrides[ric] = dataSource
            
            // If there's an active subscription, restart it with the new data source
            val subscription = subscriptions[ric]
            if (subscription != null) {
                val updatedSubscription = subscription.copy(dataSource = dataSource)
                subscriptions[ric] = updatedSubscription
            }
        }
    }

    /**
     * Get current data source configuration
     * Returns information about global and per-stock data source settings
     * 
     * @return Map containing data source configuration
     */
    fun getDataSourceStatus(): Map<String, Any> {
        return mapOf(
            "globalDataSource" to defaultDataSource,
            "stockOverrides" to stockDataSourceOverrides,
            "activeSubscriptions" to subscriptions.size,
            "throttlingStatus" to throttleService.getThrottlingStatus()
        )
    }

    /**
     * Determine which data source to use for a given RIC
     * Checks for per-stock overrides first, then falls back to global default
     * 
     * @param ric The RIC code
     * @param requestDataSource Optional data source from the request
     * @return The data source to use
     */
    private fun determineDataSource(ric: String, requestDataSource: DataSource?): DataSource {
        return requestDataSource 
            ?: stockDataSourceOverrides[ric] 
            ?: DataSource.valueOf(defaultDataSource)
    }

    /**
     * Generate a unique subscription ID
     * Creates a unique identifier for each subscription
     * 
     * @return A unique subscription ID
     */
    private fun generateSubscriptionId(): String {
        return "sub_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }

    /**
     * Data class to hold subscription details
     * Contains all information needed for a market data subscription
     */
    private data class SubscriptionDetails(
        val ric: String,
        val subscriptionId: String,
        val throttleTimeSeconds: Long,
        val dataSource: DataSource,
        val intervals: List<String>
    )
}
