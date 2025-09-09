package com.leon.marketservice.model

data class SubscriptionInfo(
    val ric: String,
    val dataSource: DataSource,
    val isActive: Boolean
)
