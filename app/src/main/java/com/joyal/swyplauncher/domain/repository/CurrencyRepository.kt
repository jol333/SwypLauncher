package com.joyal.swyplauncher.domain.repository

interface CurrencyRepository {

    sealed interface RatesResult {
        data class Success(
            val base: String,
            val rates: Map<String, Double>,
            val timestamp: Long,
            val fromCache: Boolean
        ) : RatesResult

        data class Error(val message: String) : RatesResult
    }

    suspend fun getRates(): RatesResult

    // Native currency code derived from device locale (computed once, cached)
    fun getNativeCurrencyCode(): String

    // Whether rates are already in memory — used to skip loader on instant cache hits
    fun hasRatesInMemory(): Boolean
}
