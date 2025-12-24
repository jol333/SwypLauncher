package com.joyal.swyplauncher.domain.model

data class AppUsageInfo(
    val packageName: String,
    val usageCount: Int = 0,
    val lastUsedTimestamp: Long = 0L
)
