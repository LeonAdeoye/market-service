package com.leon.marketservice.service

import com.leon.marketservice.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class CryptoMarketDataService(private val cryptoDataScheduler: CryptoDataScheduler)
{
    private val logger = LoggerFactory.getLogger(CryptoMarketDataService::class.java)
    private val subscriptions = ConcurrentHashMap<String, CryptoSubscriptionDetails>()

    fun subscribe(request: CryptoSubscriptionRequest): CryptoSubscriptionResponse 
    {
        val subscriptionId = generateSubscriptionId()
        val successfulInstrumentCodes = mutableListOf<String>()
        
        for (instrumentCode in request.instrumentCodes) 
        {
            try 
            {
                val subscriptionDetails = CryptoSubscriptionDetails(instrumentCode = instrumentCode, subscriptionId = subscriptionId)
                subscriptions[instrumentCode] = subscriptionDetails
                cryptoDataScheduler.addSubscription(instrumentCode, subscriptionId)
                successfulInstrumentCodes.add(instrumentCode)
                logger.info("Successfully subscribed to crypto $instrumentCode")
            } 
            catch (e: Exception) 
            {
                logger.error("Failed to subscribe to crypto $instrumentCode", e)
            }
        }
        
        return CryptoSubscriptionResponse(
            success = successfulInstrumentCodes.isNotEmpty(),
            message = if (successfulInstrumentCodes.isNotEmpty()) 
                "Successfully subscribed to ${successfulInstrumentCodes.size} crypto instruments" 
            else 
                "Failed to subscribe to any crypto instruments",
            subscriptionId = subscriptionId, 
            instrumentCodes = successfulInstrumentCodes
        )
    }

    fun unsubscribe(instrumentCode: String) 
    {
        logger.info("Unsubscribing from crypto $instrumentCode")
        val subscription = subscriptions.remove(instrumentCode)
        if (subscription != null)
        {
            cryptoDataScheduler.removeSubscription(instrumentCode)
            logger.info("Successfully unsubscribed from crypto $instrumentCode")
        }
        else
            logger.warn("No active subscription found for crypto $instrumentCode")
    }

    fun getActiveSubscriptions(): Map<String, Any> 
    {
        return mapOf(
            "count" to subscriptions.size,
            "subscriptions" to subscriptions.values.map { subscription ->
                mapOf<String, Any>(
                    "instrumentCode" to subscription.instrumentCode, 
                    "subscriptionId" to subscription.subscriptionId
                )
            }
        )
    }

    private fun generateSubscriptionId(): String = java.util.UUID.randomUUID().toString()
}
