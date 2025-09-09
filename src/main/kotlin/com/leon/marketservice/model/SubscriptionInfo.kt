package com.leon.marketservice.model

/**
 * Data class to hold subscription information for scheduled fetching
 * Contains all necessary information for background data fetching
 * 
 * @param ric Reuters Instrument Code for the security
 * @param dataSource The data source to use for fetching
 * @param isActive Whether this subscription is currently active
 */
data class SubscriptionInfo(
    val ric: String,
    val dataSource: DataSource,
    val isActive: Boolean
)
