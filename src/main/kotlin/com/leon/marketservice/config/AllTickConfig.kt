package com.leon.marketservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for AllTick API
 * Contains all necessary settings for connecting to and using the AllTick service
 * 
 * @param apiKey The API key for AllTick authentication
 * @param baseUrl The base URL for AllTick REST API endpoints
 * @param realtimeUrl The real-time data URL for streaming
 */
@Configuration
@ConfigurationProperties(prefix = "alltick")
data class AllTickConfig(
    var apiKey: String = "",
    var baseUrl: String = "https://api.alltick.co",
    var realtimeUrl: String = "wss://api.alltick.co/ws"
)
