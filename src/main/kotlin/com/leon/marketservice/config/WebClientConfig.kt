package com.leon.marketservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Minimal WebClient configuration
 * Provides a default WebClient bean for HTTP operations
 */
@Configuration
class WebClientConfig 
{
    /**
     * WebClient bean for HTTP operations
     * Uses Spring Boot's default configuration
     * 
     * @return WebClient instance
     */
    @Bean
    fun webClient(): WebClient 
    {
        return WebClient.builder().build()
    }
}
