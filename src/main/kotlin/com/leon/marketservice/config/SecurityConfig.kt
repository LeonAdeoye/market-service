package com.leon.marketservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Security configuration for the market data service
 * Provides basic security settings and CORS configuration
 * 
 * This configuration sets up basic security for the application
 * with appropriate CORS settings for API access.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /**
     * Security filter chain
     * Configures HTTP security settings
     * 
     * @param http HttpSecurity configuration
     * @return SecurityFilterChain
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() } // Disable CSRF for API endpoints
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health/**").permitAll()
                    .requestMatchers("/api/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/metrics/**").permitAll()
                    .anyRequest().authenticated()
            }
            .headers { headers ->
                headers
                    .frameOptions().deny()
                    .contentTypeOptions().and()
                    .httpStrictTransportSecurity { hstsConfig ->
                        hstsConfig
                            .maxAgeInSeconds(31536000)
                            .includeSubDomains(true)
                    }
            }
            .build()
    }
}
