package com.leon.marketservice.service

import com.leon.marketservice.model.MarketData
import com.fasterxml.jackson.databind.ObjectMapper
import com.crankuptheamps.client.Client
import jakarta.annotation.PreDestroy
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AmpsPublisherService(private val objectMapper: ObjectMapper) 
{
    private val logger = LoggerFactory.getLogger(AmpsPublisherService::class.java)
    @Value("\${amps.server.url}")
    private lateinit var serverUrl: String
    @Value("\${amps.topic.name}")
    private lateinit var topicName: String
    @Value("\${amps.client.name:MarketDataPublisher}")
    private lateinit var clientName: String
    @Value("\${amps.enabled:true}")
    private var ampsEnabled: Boolean = true
    private var isConnected = false
    private var ampsClient: Client? = null

    @PostConstruct
    fun initialize() 
    {
        if (!ampsEnabled)
        {
            logger.info("AMPS publishing is disabled via configuration")
            return
        }
        
        try 
        {
            logger.info("Initializing AMPS connection to $serverUrl")
            ampsClient = Client(clientName)
            ampsClient?.connect(serverUrl)
            ampsClient?.logon()
            isConnected = true
            logger.info("Successfully connected to AMPS server at $serverUrl")
        } 
        catch (e: Exception) 
        {
            logger.warn("Failed to connect to AMPS server at $serverUrl. AMPS publishing will be disabled. Error: ${e.message}")
            isConnected = false
            ampsClient = null
        }
    }

    fun publishMarketData(marketData: MarketData) 
    {
        if (!ampsEnabled)
        {
            logger.debug("AMPS publishing is disabled, skipping publish for ${marketData.ric}")
            return
        }
        
        if (!isConnected || ampsClient == null) 
        {
            logger.debug("AMPS not connected, attempting to reconnect")
            try
            {
                initialize()
            }
            catch (e: Exception)
            {
                logger.debug("Failed to reconnect to AMPS, skipping publish for ${marketData.ric}")
                return
            }
        }
        
        try
        {
            val topic = topicName
            val jsonPayload = objectMapper.writeValueAsString(marketData)
            ampsClient?.publish(topic, jsonPayload)
            logger.info("Published market data for ${marketData.ric} to topic $topic: price=${marketData.price}")
        }
        catch (e: Exception)
        {
            logger.error("Failed to publish market data for ${marketData.ric}", e)
            isConnected = false
            ampsClient = null
        }
    }
    
    @PreDestroy
    fun shutdown() 
    {
        try 
        {
            if (ampsClient != null)
            {
                ampsClient?.disconnect()
                logger.info("Disconnected from AMPS server")
            }
        } 
        catch (e: Exception) 
        {
            logger.error("Error disconnecting from AMPS server", e)
        }
    }
}
