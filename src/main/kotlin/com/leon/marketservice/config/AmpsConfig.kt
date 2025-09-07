package com.leon.marketservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for AMPS (Advanced Message Processing System)
 * Contains all necessary settings for connecting to and publishing to AMPS
 * 
 * @param serverUrl The URL of the AMPS server
 * @param topicPrefix The prefix to use for all AMPS topics
 */
@Configuration
@ConfigurationProperties(prefix = "amps")
data class AmpsConfig(
    var serverUrl: String = "tcp://localhost:9007",
    var topicPrefix: String = "market.data"
)
