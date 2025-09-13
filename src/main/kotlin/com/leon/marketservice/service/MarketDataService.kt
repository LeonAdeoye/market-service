package com.leon.marketservice.service

import com.leon.marketservice.model.*
import com.leon.marketservice.model.SubscriptionDetails
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.ConcurrentHashMap

@Component
class WebClientConfig
{
    @Bean
    fun webClient(): WebClient 
    {
        return WebClient.builder().build()
    }
}

@Service
class MarketDataService(private val alphaVantageService: AlphaVantageService,
    private val gaussianRandomDataService: GaussianRandomDataService,
    private val ampsPublisherService: AmpsPublisherService)
{
    private val logger = LoggerFactory.getLogger(MarketDataService::class.java)
    private val subscriptions = ConcurrentHashMap<String, SubscriptionDetails>()
    private val dataSourceType = ConcurrentHashMap<String, String>()

    fun subscribe(request: SubscriptionRequest): SubscriptionResponse 
    {
        val dataSource = request.dataSource ?: "gaussian-random"
        logger.info("Processing subscription request for ${request.rics.size} stocks using $dataSource")
        val subscriptionId = generateSubscriptionId()
        val successfulRics = mutableListOf<String>()
        
        for (ric in request.rics) 
        {
            try 
            {
                logger.debug("Processing RIC: $ric with data source: $dataSource")
                val subscriptionDetails = SubscriptionDetails(ric = ric, subscriptionId = subscriptionId)
                subscriptions[ric] = subscriptionDetails
                dataSourceType[ric] = dataSource
                successfulRics.add(ric)
                logger.info("Successfully subscribed to $ric using $dataSource")
            } 
            catch (e: Exception) 
            {
                logger.error("Failed to subscribe to $ric", e)
            }
        }
        
        return SubscriptionResponse(success = successfulRics.isNotEmpty(),
            message = if (successfulRics.isNotEmpty()) "Successfully subscribed to ${successfulRics.size} stocks using $dataSource" else "Failed to subscribe to any stocks",
            subscriptionId = subscriptionId, rics = successfulRics)
    }

    fun unsubscribe(ric: String) 
    {
        logger.info("Unsubscribing from $ric")
        val subscription = subscriptions.remove(ric)
        val dataSource = dataSourceType.remove(ric)

        if (subscription != null)
        {
            logger.info("Successfully unsubscribed from $ric (was using $dataSource)")
            if (dataSource == "gaussian-random")
                gaussianRandomDataService.resetPrice(ric)
        }
        else
            logger.warn("No active subscription found for $ric")
    }

    fun getActiveSubscriptions(): Map<String, Any> 
    {
        return mapOf(
            "count" to subscriptions.size,
            "subscriptions" to subscriptions.values.map { subscription ->
                mapOf<String, Any>(
                    "ric" to subscription.ric, 
                    "subscriptionId" to subscription.subscriptionId,
                    "dataSource" to (dataSourceType[subscription.ric] ?: "unknown")
                )
            }
        )
    }

    fun getSubscriptionStatus(): Map<String, Any> 
    {
        val alphaVantageRics = subscriptions.values.filter { dataSourceType[it.ric] == "alpha-vantage" }
        val gaussianRandomRics = subscriptions.values.filter { dataSourceType[it.ric] == "gaussian-random" }
        
        return mapOf(
            "totalSubscriptions" to subscriptions.size,
            "alphaVantageRics" to mapOf("count" to alphaVantageRics.size, "rics" to alphaVantageRics.map { it.ric }),
            "gaussianRandomRics" to mapOf("count" to gaussianRandomRics.size, "rics" to gaussianRandomRics.map { it.ric })
        )
    }

    fun getConfiguration(): Map<String, Any> 
    {
        return mapOf(
            "dataSources" to mapOf(
                "alphaVantage" to alphaVantageService.getConfiguration(),
                "gaussianRandom" to gaussianRandomDataService.getConfiguration()
            ),
            "enabled" to true
        )
    }

    private fun generateSubscriptionId(): String 
    {
        return java.util.UUID.randomUUID().toString()
    }
}