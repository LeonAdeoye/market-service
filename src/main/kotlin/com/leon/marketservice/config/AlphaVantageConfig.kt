package com.leon.marketservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for Alpha Vantage API
 * Contains all necessary settings for connecting to and using the Alpha Vantage service
 * 
 * @param apiKey The API key for Alpha Vantage authentication
 * @param baseUrl The base URL for Alpha Vantage API endpoints
 * @param rateLimitCalls Maximum number of calls allowed per rate limit period
 * @param rateLimitPeriod Time period in seconds for rate limiting
 */
@Configuration
@ConfigurationProperties(prefix = "alpha.vantage")
data class AlphaVantageConfig(
    var apiKey: String = "",
    var baseUrl: String = "https://www.alphavantage.co/query",
    var rateLimitCalls: Int = 5,
    var rateLimitPeriod: Int = 60
)
