package com.leon.marketservice.service

import com.leon.marketservice.config.MarketDataConfig
import com.leon.marketservice.model.*
import com.leon.marketservice.model.SubscriptionDetails
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class MarketDataService(private val alphaVantageService: AlphaVantageService, private val allTickService: AllTickService,
    private val ampsPublisherService: AmpsPublisherService, private val marketDataConfig: MarketDataConfig)
{
    private val logger = LoggerFactory.getLogger(MarketDataService::class.java)
    private val subscriptions = ConcurrentHashMap<String, SubscriptionDetails>()

    fun subscribe(request: SubscriptionRequest): SubscriptionResponse 
    {
        logger.info("Processing subscription request for ${request.rics.size} stocks")
        val subscriptionId = generateSubscriptionId()
        val successfulRics = mutableListOf<String>()
        
        for (ric in request.rics) 
        {
            try 
            {
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
                mapOf(
                    "ric" to subscription.ric,
                    "subscriptionId" to subscription.subscriptionId
                )
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

    private fun generateSubscriptionId(): String 
    {
        return java.util.UUID.randomUUID().toString()
    }
}
