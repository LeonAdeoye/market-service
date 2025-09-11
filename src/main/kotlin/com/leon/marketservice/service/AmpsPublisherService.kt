package com.leon.marketservice.service

import com.leon.marketservice.model.MarketData
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class AmpsPublisherService(private val objectMapper: ObjectMapper) 
{
    private val logger = LoggerFactory.getLogger(AmpsPublisherService::class.java)
    @Value("\${amps.server.url}")
    private lateinit var serverUrl: String
    @Value("\${amps.topic.prefix}")
    private lateinit var topicPrefix: String
    private var isConnected = false
    private val topicCache = ConcurrentHashMap<String, String>()

    fun initialize() 
    {
        try 
        {
            logger.info("Initializing AMPS connection to $serverUrl")
            isConnected = true
            logger.info("Successfully connected to AMPS server (simulated)")
        } 
        catch (e: Exception) 
        {
            logger.error("Failed to connect to AMPS server", e)
            isConnected = false
            throw e
        }
    }

    fun publishMarketData(marketData: MarketData) 
    {
        if (!isConnected) 
        {
            logger.warn("AMPS not connected, attempting to reconnect")
            initialize()
        }
        
        try
        {
            val topic = getTopicForRic(marketData.ric)
            logger.debug("Publishing market data for ${marketData.ric} to topic $topic")
            logger.info("Simulated AMPS publish: ${marketData.ric} -> $topic")
            logger.debug("Successfully published market data for ${marketData.ric}")
        }
        catch (e: Exception)
        {
            logger.error("Failed to publish market data for ${marketData.ric}", e)
            throw e
        }
    }

    private fun getTopicForRic(ric: String): String 
    {
        return topicCache.computeIfAbsent(ric) {
            "$topicPrefix.${ric.replace(".", "_")}"
        }
    }

    private fun createMessageData(marketData: MarketData): Map<String, Any> 
    {
        val messageData = mutableMapOf<String, Any>()
        messageData["ric"] = marketData.ric
        messageData["symbol"] = marketData.symbol
        messageData["price"] = marketData.price.toString()
        messageData["dataSource"] = "Alpha Vantage"
        messageData["timestamp"] = marketData.timestamp.toString()
        val jsonPayload = objectMapper.writeValueAsString(marketData)
        messageData["jsonPayload"] = jsonPayload
        return messageData
    }

}
