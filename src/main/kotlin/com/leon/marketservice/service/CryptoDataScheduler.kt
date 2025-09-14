package com.leon.marketservice.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class CryptoDataScheduler(private val coinMarketCapService: CoinMarketCapService, private val ampsPublisherService: AmpsPublisherService)
{
    private val logger = LoggerFactory.getLogger(CryptoDataScheduler::class.java)
    private val subscriptions = ConcurrentHashMap<String, String>()

    fun addSubscription(instrumentCode: String, subscriptionId: String)
    {
        subscriptions[instrumentCode] = subscriptionId
    }

    fun removeSubscription(instrumentCode: String)
    {
        subscriptions.remove(instrumentCode)
    }

    @Scheduled(fixedDelayString = "\${market.data.scheduled.fetch.interval.seconds:20}000")
    fun fetchAndPublishCryptoData()
    {
        try
        {
            if (subscriptions.isEmpty())
            {
                logger.debug("No active crypto subscriptions to fetch data for")
                return
            }

            val symbols = subscriptions.keys.toList()
            logger.debug("Scheduled crypto data fetch starting for ${symbols.size} instruments")
            coinMarketCapService.fetchCryptoPriceDataForSymbols(symbols)
                .subscribe(
                    {
                        cryptoData ->
                            logger.debug("Publishing crypto data for ${cryptoData.symbol}")
                            ampsPublisherService.publishCryptoData(cryptoData)
                    },
                    {
                        error -> logger.error("Error fetching crypto data", error)
                    }
                )
        }
        catch (e: Exception)
        {
            logger.error("Error in scheduled crypto data fetch", e)
        }
    }
}
