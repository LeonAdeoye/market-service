package com.leon.marketservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

/**
 * Configuration for validation
 * Sets up validation framework for request validation
 * 
 * This configuration enables validation for request models and
 * provides custom validation rules for market data operations.
 */
@Configuration
class ValidationConfig {

    /**
     * Local validator factory bean
     * Provides validation capabilities for the application
     * 
     * @return LocalValidatorFactoryBean instance
     */
    @Bean
    fun validator(): LocalValidatorFactoryBean {
        return LocalValidatorFactoryBean()
    }
}
