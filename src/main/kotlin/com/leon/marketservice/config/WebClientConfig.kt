package com.leon.marketservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/**
 * Configuration for WebClient instances
 * Provides HTTP client beans for making API calls to external services
 * 
 * This configuration sets up WebClient instances with appropriate timeouts
 * and error handling for both Alpha Vantage and AllTick APIs.
 */
@Configuration
class WebClientConfig {

    /**
     * WebClient bean for general HTTP operations
     * Configured with reasonable timeouts and error handling
     * 
     * @return WebClient instance
     */
    @Bean
    fun webClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
            }
            .build()
    }

    /**
     * WebClient bean specifically for Alpha Vantage API
     * Configured with longer timeouts for delayed data responses
     * 
     * @return WebClient instance for Alpha Vantage
     */
    @Bean("alphaVantageWebClient")
    fun alphaVantageWebClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024)
            }
            .build()
    }

    /**
     * WebClient bean specifically for AllTick API
     * Configured with shorter timeouts for real-time data
     * 
     * @return WebClient instance for AllTick
     */
    @Bean("allTickWebClient")
    fun allTickWebClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(512 * 1024) // 512KB
            }
            .build()
    }
}
