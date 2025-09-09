package com.leon.marketservice.service

import com.leon.marketservice.model.DataSource
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ScheduledDataFetcher( private val marketDataService: MarketDataService, private val alphaVantageService: AlphaVantageService,
    private val allTickService: AllTickService,  private val ampsPublisherService: AmpsPublisherService, private val marketDataConfig: MarketDataConfig)
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
            val allTickRics = mutableListOf<String>()
            
            for (subscription in subscriptionList)
            {
                val ric = subscription["ric"] as String
                val dataSource = subscription["dataSource"] as? String ?: "ALPHA_VANTAGE"
                when (DataSource.valueOf(dataSource))
                {
                    DataSource.ALPHA_VANTAGE -> alphaVantageRics.add(ric)
                    DataSource.ALL_TICK -> allTickRics.add(ric)
                }
            }
            
            if (allTickRics.isNotEmpty() && marketDataConfig.isAllTickEnabled())
                fetchAllTickData(allTickRics)
            else if (allTickRics.isNotEmpty() && !marketDataConfig.isAllTickEnabled())
                logger.warn("AllTick RICs found but AllTick is disabled (no RIC endings configured)")
            
            if (alphaVantageRics.isNotEmpty() && marketDataConfig.isAlphaVantageEnabled())
                fetchAlphaVantageBatch(alphaVantageRics)
            else if (alphaVantageRics.isNotEmpty() && !marketDataConfig.isAlphaVantageEnabled())
                logger.warn("Alpha Vantage RICs found but Alpha Vantage is disabled (no RIC endings configured)")
            
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

    private fun fetchAllTickData(rics: List<String>) 
    {
        logger.debug("Fetching data for ${rics.size} RICs using AllTick")
        allTickService.fetchMarketDataForSymbols(rics).subscribe(
        {
            marketData -> ampsPublisherService.publishMarketData(marketData)
            logger.debug("Published AllTick data for ${marketData.ric}")
        },
        {
            error -> logger.error("Error fetching AllTick data", error)
        })
    }

    fun updateFetchInterval(intervalSeconds: Long) 
    {
        if (intervalSeconds < 1)
            throw IllegalArgumentException("Fetch interval must be at least 1 second")
        
        logger.info("Updated fetch interval to $intervalSeconds seconds")
    }
}

