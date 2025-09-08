package com.leon.marketservice.service

import com.leon.marketservice.config.MarketDataConfig
import com.leon.marketservice.model.DataSource
import com.leon.marketservice.model.SubscriptionInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    private val marketDataConfig: MarketDataConfig
) 
{
    
    private val logger = LoggerFactory.getLogger(ScheduledDataFetcher::class.java)
    
    @Value("\${market.data.scheduled.fetch.interval.seconds:30}")
    private var fetchIntervalSeconds: Long = 30
    
    private val activeSubscriptions = ConcurrentHashMap<String, SubscriptionInfo>()
    
    private var alphaVantageBatchIndex = 0
    private val alphaVantageBatchSize = 5

    /**
     * Scheduled task that runs at configurable intervals
     * Fetches market data with proper batching for Alpha Vantage rate limits
     */
    @Scheduled(fixedRateString = "\${market.data.scheduled.fetch.interval.seconds:30}000") // Configurable interval
    fun fetchMarketDataScheduled() 
    {
        logger.debug("Running scheduled market data fetch (interval: ${fetchIntervalSeconds}s)")
        
        try 
        {
            val subscriptions = marketDataService.getActiveSubscriptions()
            @Suppress("UNCHECKED_CAST")
            val subscriptionList = subscriptions["subscriptions"] as? List<Map<String, Any>> ?: emptyList()
            
            if (subscriptionList.isEmpty()) 
            {
                logger.debug("No active subscriptions to fetch")
                return
            }
            
            val alphaVantageRics = mutableListOf<String>()
            val allTickRics = mutableListOf<String>()
            
            for (subscription in subscriptionList) {
                val ric = subscription["ric"] as String
                val dataSource = subscription["dataSource"] as? String ?: "ALPHA_VANTAGE"
                
                when (DataSource.valueOf(dataSource)) {
                    DataSource.ALPHA_VANTAGE -> alphaVantageRics.add(ric)
                    DataSource.ALL_TICK -> allTickRics.add(ric)
                }
            }
            
            if (allTickRics.isNotEmpty() && marketDataConfig.isAllTickEnabled()) {
                fetchAndPublishData(allTickRics, DataSource.ALL_TICK)
            } else if (allTickRics.isNotEmpty() && !marketDataConfig.isAllTickEnabled()) {
                logger.warn("AllTick RICs found but AllTick is disabled (no RIC endings configured)")
            }
            
            if (alphaVantageRics.isNotEmpty() && marketDataConfig.isAlphaVantageEnabled()) {
                fetchAlphaVantageBatch(alphaVantageRics)
            } else if (alphaVantageRics.isNotEmpty() && !marketDataConfig.isAlphaVantageEnabled()) {
                logger.warn("Alpha Vantage RICs found but Alpha Vantage is disabled (no RIC endings configured)")
            }
            
        } 
        catch (e: Exception) 
        {
            logger.error("Error in scheduled market data fetch", e)
        }
    }

    /**
     * Fetch Alpha Vantage data in batches to respect rate limits
     * Cycles through RICs in batches of 5 every 30 seconds
     * 
     * @param allRics All Alpha Vantage RICs to cycle through
     */
    private fun fetchAlphaVantageBatch(allRics: List<String>) 
    {
        if (allRics.isEmpty()) 
            return
        
        val startIndex = alphaVantageBatchIndex % allRics.size
        val endIndex = minOf(startIndex + alphaVantageBatchSize, allRics.size)
        
        val currentBatch = if (startIndex < endIndex) {
            allRics.subList(startIndex, endIndex)
        } else {
            allRics.take(alphaVantageBatchSize)
        }
        
        logger.debug("Fetching Alpha Vantage batch ${alphaVantageBatchIndex + 1}: ${currentBatch.size} RICs (${currentBatch.joinToString()})")
        
        if (alphaVantageService.isAvailable()) {
            alphaVantageService.fetchMarketDataForSymbols(currentBatch)
                .subscribe(
                    { marketData -> 
                        ampsPublisherService.publishMarketData(marketData)
                        logger.debug("Published Alpha Vantage data for ${marketData.ric}")
                    },
                    { error -> 
                        logger.error("Error fetching Alpha Vantage batch", error)
                    }
                )
        } else {
            logger.warn("Alpha Vantage unavailable, skipping batch of ${currentBatch.size} RICs")
        }
        
        alphaVantageBatchIndex = (alphaVantageBatchIndex + alphaVantageBatchSize) % allRics.size
    }

    /**
     * Fetch and publish market data for AllTick (no rate limits)
     * Fetches all RICs concurrently
     * 
     * @param rics List of RIC codes to fetch
     */
    private fun fetchAndPublishData(rics: List<String>, dataSource: DataSource) 
    {
        logger.debug("Fetching data for ${rics.size} RICs using $dataSource")
        
        when (dataSource) 
        {
            DataSource.ALL_TICK -> 
            {
                if (allTickService.isAvailable()) 
                {
                    allTickService.fetchMarketDataForSymbols(rics)
                        .subscribe(
                            { marketData -> 
                                ampsPublisherService.publishMarketData(marketData)
                                logger.debug("Published AllTick data for ${marketData.ric}")
                            },
                            { error -> 
                                logger.error("Error fetching AllTick data", error)
                            }
                        )
                } else {
                    logger.warn("AllTick unavailable, skipping ${rics.size} RICs")
                }
            }
            else -> {
                logger.warn("Unexpected data source: $dataSource")
            }
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
    fun addSubscription(ric: String, dataSource: DataSource, intervals: List<String>) 
    {
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
    fun removeSubscription(ric: String) 
    {
        activeSubscriptions.remove(ric)
        logger.info("Removed subscription for $ric from scheduled fetching")
    }

    /**
     * Update the fetch interval dynamically
     * Allows changing the scheduler interval at runtime
     * 
     * @param intervalSeconds New interval in seconds
     */
    fun updateFetchInterval(intervalSeconds: Long) 
    {
        if (intervalSeconds < 1) 
        {
            throw IllegalArgumentException("Fetch interval must be at least 1 second")
        }
        
        fetchIntervalSeconds = intervalSeconds
        logger.info("Updated fetch interval to ${intervalSeconds} seconds")
    }

    /**
     * Get current fetch interval
     * 
     * @return Current interval in seconds
     */
    fun getFetchInterval(): Long 
    {
        return fetchIntervalSeconds
    }

    /**
     * Get status of scheduled fetching
     * Returns information about active subscriptions and fetching status
     * 
     * @return Map containing fetching status information
     */
    fun getFetchingStatus(): Map<String, Any> 
    {
        val subscriptions = marketDataService.getActiveSubscriptions()
        @Suppress("UNCHECKED_CAST")
        val subscriptionList = subscriptions["subscriptions"] as? List<Map<String, Any>> ?: emptyList()
        
        val alphaVantageRics = subscriptionList.filter { 
            (it["dataSource"] as? String ?: "ALPHA_VANTAGE") == "ALPHA_VANTAGE" 
        }.map { it["ric"] as String }
        
        val allTickRics = subscriptionList.filter { 
            (it["dataSource"] as? String ?: "ALPHA_VANTAGE") == "ALL_TICK" 
        }.map { it["ric"] as String }
        
        return mapOf(
            "fetchIntervalSeconds" to fetchIntervalSeconds,
            "configuration" to mapOf(
                "alphaVantageEnabled" to marketDataConfig.isAlphaVantageEnabled(),
                "allTickEnabled" to marketDataConfig.isAllTickEnabled(),
                "alphaVantageRicEndings" to marketDataConfig.alphaVantageRicEndings,
                "allTickRicEndings" to marketDataConfig.allTickRicEndings
            ),
            "alphaVantageBatching" to mapOf(
                "batchSize" to alphaVantageBatchSize,
                "currentBatchIndex" to alphaVantageBatchIndex,
                "totalRics" to alphaVantageRics.size,
                "estimatedCyclesPerHour" to if (alphaVantageRics.isNotEmpty() && marketDataConfig.isAlphaVantageEnabled()) {
                    (3600 / fetchIntervalSeconds) * alphaVantageBatchSize / alphaVantageRics.size
                } else 0
            ),
            "dataSources" to mapOf(
                "allTick" to mapOf(
                    "enabled" to marketDataConfig.isAllTickEnabled(),
                    "ricCount" to allTickRics.size
                ),
                "alphaVantage" to mapOf(
                    "enabled" to marketDataConfig.isAlphaVantageEnabled(),
                    "ricCount" to alphaVantageRics.size
                )
            ),
            "totalSubscriptions" to subscriptionList.size
        )
    }

}

