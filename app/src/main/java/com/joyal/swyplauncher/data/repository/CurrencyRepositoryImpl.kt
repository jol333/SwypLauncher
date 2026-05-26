package com.joyal.swyplauncher.data.repository

import android.content.Context
import com.joyal.swyplauncher.domain.repository.CurrencyRepository
import com.joyal.swyplauncher.domain.repository.CurrencyRepository.RatesResult
import com.joyal.swyplauncher.domain.repository.PreferencesRepository
import com.joyal.swyplauncher.util.CurrencyUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository
) : CurrencyRepository {

    private val nativeCode: String by lazy { CurrencyUtil.detectNativeCurrency(context) }

    override fun getNativeCurrencyCode(): String = nativeCode

    override fun hasRatesInMemory(): Boolean = memCache != null

    private data class Cached(
        val base: String,
        val rates: Map<String, Double>,
        val timestamp: Long,
        val fromCache: Boolean
    )

    private val mutex = Mutex()

    @Volatile private var memCache: Cached? = null

    override suspend fun getRates(): RatesResult {
        // Fast path: memory cache (any age within session)
        memCache?.let { return it.toResult() }

        return mutex.withLock {
            // Re-check after lock
            memCache?.let { return@withLock it.toResult() }

            val persisted = loadPersisted()

            // If persisted is fresh enough, use it without API call (still marked fromCache=true for timestamp display)
            if (persisted != null && (System.currentTimeMillis() - persisted.timestamp) < FRESH_MS) {
                memCache = persisted
                return@withLock persisted.toResult()
            }

            // Try to fetch online (primary then backup)
            val fetched = withContext(Dispatchers.IO) { tryFetch() }
            if (fetched != null) {
                memCache = fetched
                persist(fetched)
                return@withLock fetched.toResult()
            }

            // Online failed — fall back to persisted (even if stale)
            if (persisted != null) {
                memCache = persisted
                return@withLock persisted.toResult()
            }

            RatesResult.Error("No internet connection. Please check your network & try again.")
        }
    }

    private fun Cached.toResult(): RatesResult.Success =
        RatesResult.Success(base, rates, timestamp, fromCache)

    private fun loadPersisted(): Cached? {
        val base = prefs.getCurrencyRatesBase() ?: return null
        val json = prefs.getCurrencyRatesJson() ?: return null
        val ts = prefs.getCurrencyRatesTimestamp().takeIf { it > 0 } ?: return null
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Double>()
            obj.keys().forEach { k -> map[k] = obj.optDouble(k, Double.NaN).takeIf { !it.isNaN() } ?: return@forEach }
            Cached(base, map, ts, fromCache = true)
        } catch (e: Exception) {
            null
        }
    }

    private fun persist(c: Cached) {
        val obj = JSONObject()
        for ((k, v) in c.rates) obj.put(k, v)
        prefs.setCurrencyRates(c.base, obj.toString(), c.timestamp)
    }

    private fun tryFetch(): Cached? {
        val primary = fetchPrimary()
        if (primary != null) return primary
        return fetchBackup()
    }

    // https://api.fxratesapi.com/latest — returns { base, date, rates: {...} }
    private fun fetchPrimary(): Cached? {
        return try {
            val body = httpGet("https://api.fxratesapi.com/latest")
            val obj = JSONObject(body)
            val base = obj.optString("base", "USD")
            val ratesObj = obj.optJSONObject("rates") ?: return null
            val rates = ratesObj.toRateMap()
            if (rates.isEmpty()) return null
            Cached(base, rates, System.currentTimeMillis(), fromCache = false)
        } catch (e: Exception) {
            null
        }
    }

    // https://open.er-api.com/v6/latest/USD — returns { result, base_code, rates: {...} }
    private fun fetchBackup(): Cached? {
        return try {
            val body = httpGet("https://open.er-api.com/v6/latest/USD")
            val obj = JSONObject(body)
            if (obj.optString("result") != "success") return null
            val base = obj.optString("base_code", "USD")
            val ratesObj = obj.optJSONObject("rates") ?: return null
            val rates = ratesObj.toRateMap()
            if (rates.isEmpty()) return null
            Cached(base, rates, System.currentTimeMillis(), fromCache = false)
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.toRateMap(): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        keys().forEach { k ->
            val v = optDouble(k, Double.NaN)
            if (!v.isNaN()) map[k] = v
        }
        return map
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 5000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        // Treat persisted rates as fresh for 6 hours; older → try refresh
        private const val FRESH_MS = 6L * 60L * 60L * 1000L
    }
}
