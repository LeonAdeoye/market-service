package com.leon.marketservice.model

/**
 * Data class to hold subscription details
 * Contains all information needed for a market data subscription
 * 
 * @param ric Reuters Instrument Code for the security
 * @param subscriptionId Unique identifier for this subscription
 * @param throttleTimeSeconds Throttle time in seconds for this subscription
 * @param dataSource The data source to use for this subscription
 * @param intervals List of time intervals for data fetching
 */
data class SubscriptionDetails(
    val ric: String,
    val subscriptionId: String,
    val throttleTimeSeconds: Long,
    val dataSource: DataSource,
    val intervals: List<String>
)
