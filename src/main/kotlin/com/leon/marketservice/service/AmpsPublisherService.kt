package com.leon.marketservice.service

import com.leon.marketservice.config.AmpsConfig
import com.leon.marketservice.model.MarketData
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for publishing market data to AMPS (Advanced Message Processing System)
 * Handles all AMPS operations including connection management, message publishing,
 * and topic management
 * 
 * This service provides a reliable way to publish market data to AMPS topics,
 * with automatic reconnection and error handling.
 * 
 * Note: This is a simplified implementation for demonstration purposes.
 * In a production environment, you would integrate with the actual AMPS client library.
 */
@Service
class AmpsPublisherService(
    private val config: AmpsConfig,
    private val objectMapper: ObjectMapper
) 
{
    
    private val logger = LoggerFactory.getLogger(AmpsPublisherService::class.java)
    
    private var isConnected = false
    
    private val topicCache = ConcurrentHashMap<String, String>()

    /**
     * Initialize AMPS connection
     * Establishes connection to the AMPS server
     * 
     * @throws Exception if connection fails
     */
    fun initialize() 
    {
        try 
        {
            logger.info("Initializing AMPS connection to ${config.serverUrl}")
            
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

    /**
     * Publish market data to AMPS
     * Sends market data to the appropriate AMPS topic
     * 
     * @param marketData The market data to publish
     * @throws Exception if publishing fails
     */
    fun publishMarketData(marketData: MarketData) 
    {
        if (!isConnected) 
        {
            logger.warn("AMPS not connected, attempting to reconnect")
            initialize()
        }
        
        try {
            val topic = getTopicForRic(marketData.ric)
            
            logger.debug("Publishing market data for ${marketData.ric} to topic $topic")
            
            logger.info("Simulated AMPS publish: ${marketData.ric} -> $topic")
            
            logger.debug("Successfully published market data for ${marketData.ric}")
            
        } catch (e: Exception) {
            logger.error("Failed to publish market data for ${marketData.ric}", e)
            throw e
        }
    }

    /**
     * Publish multiple market data items in batch
     * Efficiently publishes multiple market data items
     * 
     * @param marketDataList List of market data items to publish
     * @throws Exception if batch publishing fails
     */
    fun publishMarketDataBatch(marketDataList: List<MarketData>) 
    {
        if (!isConnected) 
        {
            logger.warn("AMPS not connected, attempting to reconnect")
            initialize()
        }
        
        try {
            logger.debug("Publishing batch of ${marketDataList.size} market data items")
            
            for (marketData in marketDataList) {
                publishMarketData(marketData)
            }
            
            logger.info("Successfully published batch of ${marketDataList.size} market data items")
            
        } catch (e: Exception) {
            logger.error("Failed to publish market data batch", e)
            throw e
        }
    }

    /**
     * Get the AMPS topic for a specific RIC
     * Creates or retrieves the appropriate topic name for the RIC
     * 
     * @param ric The RIC code
     * @return The AMPS topic name
     */
    private fun getTopicForRic(ric: String): String 
    {
        return topicCache.computeIfAbsent(ric) { 
            "${config.topicPrefix}.${ric.replace(".", "_")}"
        }
    }

    /**
     * Create message data from market data
     * Converts MarketData object to message format
     * 
     * @param marketData The market data to convert
     * @return Map containing message data
     */
    private fun createMessageData(marketData: MarketData): Map<String, Any> 
    {
        val messageData = mutableMapOf<String, Any>()
        
        messageData["ric"] = marketData.ric
        messageData["symbol"] = marketData.symbol
        messageData["price"] = marketData.price.toString()
        messageData["dataSource"] = marketData.dataSource.name
        messageData["timestamp"] = marketData.timestamp.toString()
        messageData["interval"] = marketData.interval
        
        marketData.open?.let { messageData["open"] = it.toString() }
        marketData.high?.let { messageData["high"] = it.toString() }
        marketData.low?.let { messageData["low"] = it.toString() }
        marketData.volume?.let { messageData["volume"] = it.toString() }
        
        val jsonPayload = objectMapper.writeValueAsString(marketData)
        messageData["jsonPayload"] = jsonPayload
        
        return messageData
    }

    /**
     * Check if AMPS is connected
     * Verifies the connection status
     * 
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean 
    {
        return isConnected
    }

    /**
     * Disconnect from AMPS
     * Closes the connection to the AMPS server
     */
    fun disconnect() 
    {
        try 
        {
            isConnected = false
            logger.info("Disconnected from AMPS server (simulated)")
        } 
        catch (e: Exception) 
        {
            logger.error("Error disconnecting from AMPS server", e)
        }
    }

    /**
     * Get connection status information
     * Returns detailed information about the AMPS connection
     * 
     * @return Map containing connection status details
     */
    fun getConnectionStatus(): Map<String, Any> 
    {
        return mapOf(
            "connected" to isConnected,
            "serverUrl" to config.serverUrl,
            "topicPrefix" to config.topicPrefix,
            "cachedTopics" to topicCache.size
        )
    }
}
