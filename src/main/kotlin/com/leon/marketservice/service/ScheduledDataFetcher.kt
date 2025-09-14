package com.leon.marketservice.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ScheduledDataFetcher( private val marketDataService: MarketDataService, private val alphaVantageService: AlphaVantageService,
    private val gaussianRandomDataService: GaussianRandomDataService, private val ampsPublisherService: AmpsPublisherService)
{
    private val logger = LoggerFactory.getLogger(ScheduledDataFetcher::class.java)
    private var alphaVantageBatchIndex = 0
    private val alphaVantageBatchSize = 5
    
    @Value("\${alpha.vantage.fallback.to.gaussian:true}")
    private var fallbackToGaussian: Boolean = true

    @Scheduled(fixedRateString = "\${market.data.scheduled.fetch.interval.seconds:20}000")
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
                when (val dataSource = subscription["dataSource"] as? String ?: "alpha-vantage")
                {
                    "alpha-vantage" -> alphaVantageRics.add(ric)
                    "gaussian-random" -> gaussianRandomRics.add(ric)
                    else -> 
                    {
                        logger.warn("Unknown data source '$dataSource' for RIC $ric, defaulting to Gaussian random.")
                        gaussianRandomRics.add(ric)
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
            marketData -> 
            if (marketData.price == 0.0 && fallbackToGaussian)
            {
                logger.info("Alpha Vantage failed or returned price 0 for ${marketData.ric}, falling back to Gaussian random data")
                // Fallback to Gaussian random data
                gaussianRandomDataService.generateMarketDataForSymbols(listOf(marketData.ric)).subscribe(
                {
                    fallbackData -> ampsPublisherService.publishMarketData(fallbackData)
                },
                {
                    error -> logger.error("Error generating fallback data for ${marketData.ric}", error)
                })
            }
            else
            {
                if (marketData.price == 0.0)
                    logger.warn("Alpha Vantage returned price 0 for ${marketData.ric}, but fallback is disabled")
                ampsPublisherService.publishMarketData(marketData)
                logger.debug("Published Alpha Vantage data for ${marketData.ric}")
            }
        },
        {
            error -> logger.error("Unexpected error in Alpha Vantage batch processing", error)
        })

        alphaVantageBatchIndex = (alphaVantageBatchIndex + alphaVantageBatchSize) % allRics.size
    }
    
    private fun fetchGaussianRandomBatch(allRics: List<String>) 
    {
        logger.info("Fetching Gaussian random batch: ${allRics.size} RICs (${allRics.joinToString()})")

        gaussianRandomDataService.generateMarketDataForSymbols(allRics).subscribe(
        {
            marketData -> ampsPublisherService.publishMarketData(marketData)
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