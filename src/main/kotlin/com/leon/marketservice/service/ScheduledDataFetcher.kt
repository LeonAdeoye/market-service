package com.leon.marketservice.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ScheduledDataFetcher( private val marketDataService: MarketDataService, private val alphaVantageService: AlphaVantageService,
    private val gaussianRandomDataService: GaussianRandomDataService, private val ampsPublisherService: AmpsPublisherService)
{
    private val logger = LoggerFactory.getLogger(ScheduledDataFetcher::class.java)
    private var alphaVantageBatchIndex = 0
    private val alphaVantageBatchSize = 5

    @Scheduled(fixedRateString = "\${market.data.scheduled.fetch.interval.seconds:30}000") // Configurable interval
    fun fetchMarketDataScheduled() 
    {
        try 
        {
            val subscriptions = marketDataService.getActiveSubscriptions()
            @Suppress("UNCHECKED_CAST")
            val subscriptionList = subscriptions["subscriptions"] as? List<Map<String, Any>> ?: emptyList()
            if (subscriptionList.isEmpty())
                return
            
            val alphaVantageRics = mutableListOf<String>()
            val gaussianRandomRics = mutableListOf<String>()
            
            for (subscription in subscriptionList)
            {
                val ric = subscription["ric"] as String
                val dataSource = subscription["dataSource"] as? String ?: "alpha-vantage"
                
                when (dataSource)
                {
                    "alpha-vantage" -> alphaVantageRics.add(ric)
                    "gaussian-random" -> gaussianRandomRics.add(ric)
                    else -> 
                    {
                        logger.warn("Unknown data source '$dataSource' for RIC $ric, defaulting to Alpha Vantage")
                        alphaVantageRics.add(ric)
                    }
                }
            }
            
            if (alphaVantageRics.isNotEmpty())
                fetchAlphaVantageBatch(alphaVantageRics)
            
            if (gaussianRandomRics.isNotEmpty())
                fetchGaussianRandomBatch(gaussianRandomRics)
            
        } 
        catch (e: Exception) 
        {
            logger.error("Error in scheduled market data fetch", e)
        }
    }

    private fun fetchAlphaVantageBatch(allRics: List<String>) 
    {
        val startIndex = alphaVantageBatchIndex % allRics.size
        val endIndex = minOf(startIndex + alphaVantageBatchSize, allRics.size)
        val currentBatch = if (startIndex < endIndex) allRics.subList(startIndex, endIndex) else allRics.take(alphaVantageBatchSize)
        logger.debug("Fetching Alpha Vantage batch ${alphaVantageBatchIndex + 1}: ${currentBatch.size} RICs (${currentBatch.joinToString()})")

        alphaVantageService.fetchMarketDataForSymbols(currentBatch).subscribe(
        {
            marketData -> ampsPublisherService.publishMarketData(marketData)
            logger.debug("Published Alpha Vantage data for ${marketData.ric}")
        },
        {
            error -> logger.error("Error fetching Alpha Vantage batch", error)
        })

        alphaVantageBatchIndex = (alphaVantageBatchIndex + alphaVantageBatchSize) % allRics.size
    }
    
    private fun fetchGaussianRandomBatch(allRics: List<String>) 
    {
        logger.debug("Fetching Gaussian random batch: ${allRics.size} RICs (${allRics.joinToString()})")

        gaussianRandomDataService.generateMarketDataForSymbols(allRics).subscribe(
        {
            marketData -> ampsPublisherService.publishMarketData(marketData)
            logger.debug("Published Gaussian random data for ${marketData.ric}")
        },
        {
            error -> logger.error("Error fetching Gaussian random batch", error)
        })
    }

    fun updateFetchInterval(intervalSeconds: Long) 
    {
        if (intervalSeconds < 1)
            throw IllegalArgumentException("Fetch interval must be at least 1 second")
        
        logger.info("Updated fetch interval to $intervalSeconds seconds")
    }
}