package com.leon.marketservice.service

import com.leon.marketservice.model.DataSource
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Scheduled service for fetching market data at regular intervals
 * Manages background data fetching based on active subscriptions and throttling
 * 
 * This service runs scheduled tasks to fetch market data for all active subscriptions,
 * respecting throttle times and data source preferences.
 */
@Service
class ScheduledDataFetcher(
    private val marketDataService: MarketDataService,
    private val alphaVantageService: AlphaVantageService,
    private val allTickService: AllTickService,
    private val ampsPublisherService: AmpsPublisherService,
    private val throttleService: ThrottleService
) {
    
    // Logger for this service
    private val logger = LoggerFactory.getLogger(ScheduledDataFetcher::class.java)
    
    // Track active subscriptions for scheduled fetching
    private val activeSubscriptions = ConcurrentHashMap<String, SubscriptionInfo>()
    
    // Statistics tracking
    private var totalFetches = 0L
    private var successfulFetches = 0L
    private var failedFetches = 0L

    /**
     * Scheduled task that runs every 30 seconds
     * Fetches market data for all active subscriptions that are ready for update
     */
    @Scheduled(fixedRate = 30000) // Run every 30 seconds
    fun fetchMarketDataScheduled() {
        logger.debug("Running scheduled market data fetch")
        
        try {
            // Get all active subscriptions
            val subscriptions = marketDataService.getActiveSubscriptions()
            val subscriptionList = subscriptions["subscriptions"] as? List<Map<String, Any>> ?: emptyList()
            
            for (subscription in subscriptionList) {
                val ric = subscription["ric"] as String
                
                // Check if this RIC is ready for update (throttle check)
                if (throttleService.canUpdate(ric)) {
                    fetchDataForRic(ric, subscription)
                } else {
                    logger.debug("Skipping $ric - throttle active")
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error in scheduled market data fetch", e)
        }
    }

    /**
     * Fetch market data for a specific RIC
     * Handles the actual data fetching and publishing
     * 
     * @param ric The RIC code to fetch data for
     * @param subscription The subscription information
     */
    private fun fetchDataForRic(ric: String, subscription: Map<String, Any>) {
        try {
            val dataSource = subscription["dataSource"] as? String ?: "ALPHA_VANTAGE"
            val intervals = subscription["intervals"] as? List<String> ?: listOf("1min")
            
            logger.debug("Fetching data for $ric using $dataSource")
            
            // Fetch data based on the configured data source
            val marketData = when (DataSource.valueOf(dataSource)) {
                DataSource.ALPHA_VANTAGE -> {
                    if (alphaVantageService.isAvailable()) {
                        alphaVantageService.fetchMarketData(ric, intervals.first())
                    } else {
                        logger.warn("Alpha Vantage unavailable, skipping $ric")
                        return
                    }
                }
                DataSource.ALL_TICK -> {
                    if (allTickService.isAvailable()) {
                        allTickService.fetchMarketData(ric, intervals.first())
                    } else {
                        logger.warn("AllTick unavailable, skipping $ric")
                        return
                    }
                }
            }
            
            // Publish the market data to AMPS
            ampsPublisherService.publishMarketData(marketData)
            
            // Record the update time for throttling
            throttleService.recordUpdate(ric)
            
            // Update statistics
            totalFetches++
            successfulFetches++
            
            logger.info("Successfully fetched and published data for $ric")
            
        } catch (e: Exception) {
            logger.error("Failed to fetch data for $ric", e)
            totalFetches++
            failedFetches++
        }
    }

    /**
     * Add a subscription to the scheduled fetching
     * Registers a new subscription for background data fetching
     * 
     * @param ric The RIC code
     * @param dataSource The data source to use
     * @param intervals The time intervals for data
     */
    fun addSubscription(ric: String, dataSource: DataSource, intervals: List<String>) {
        val subscriptionInfo = SubscriptionInfo(
            ric = ric,
            dataSource = dataSource,
            intervals = intervals,
            isActive = true
        )
        
        activeSubscriptions[ric] = subscriptionInfo
        logger.info("Added subscription for $ric to scheduled fetching")
    }

    /**
     * Remove a subscription from scheduled fetching
     * Unregisters a subscription from background data fetching
     * 
     * @param ric The RIC code to remove
     */
    fun removeSubscription(ric: String) {
        activeSubscriptions.remove(ric)
        logger.info("Removed subscription for $ric from scheduled fetching")
    }

    /**
     * Get status of scheduled fetching
     * Returns information about active subscriptions and fetching status
     * 
     * @return Map containing fetching status information
     */
    fun getFetchingStatus(): Map<String, Any> {
        return mapOf(
            "activeSubscriptions" to activeSubscriptions.size,
            "subscriptions" to activeSubscriptions.values.map { subscription ->
                mapOf(
                    "ric" to subscription.ric,
                    "dataSource" to subscription.dataSource,
                    "intervals" to subscription.intervals,
                    "isActive" to subscription.isActive
                )
            },
            "statistics" to mapOf(
                "totalFetches" to totalFetches,
                "successfulFetches" to successfulFetches,
                "failedFetches" to failedFetches,
                "successRate" to if (totalFetches > 0) (successfulFetches.toDouble() / totalFetches * 100) else 0.0
            )
        )
    }

    /**
     * Reset statistics
     * Clears all fetching statistics
     */
    fun resetStatistics() {
        totalFetches = 0L
        successfulFetches = 0L
        failedFetches = 0L
        logger.info("Statistics reset")
    }

    /**
     * Data class to hold subscription information for scheduled fetching
     * Contains all necessary information for background data fetching
     */
    private data class SubscriptionInfo(
        val ric: String,
        val dataSource: DataSource,
        val intervals: List<String>,
        val isActive: Boolean
    )
}
