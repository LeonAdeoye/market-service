package com.leon.marketservice.model

/**
 * Enumeration of available market data sources
 * Each source has different characteristics:
 * - ALPHA_VANTAGE: Delayed data, good for historical analysis
 * - ALL_TICK: Real-time data, higher frequency updates
 */
enum class DataSource 
{
    ALPHA_VANTAGE,
    ALL_TICK
}