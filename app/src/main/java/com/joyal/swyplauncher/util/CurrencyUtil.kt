package com.joyal.swyplauncher.util

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

object CurrencyUtil {

    data class Parsed(val amount: Double, val from: String, val to: String)

    private val amountRegex = Regex("[+-]?(\\d{1,3}(?:[,\\s]\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)")
    private val splitRegex = Regex("\\b(into|to|in)\\b", RegexOption.IGNORE_CASE)
    private val supportedCodes = CurrencyData.all.map { it.code }.toSet()

    // All (token, code) pairs sorted by length descending so longest match wins.
    // Skip single-letter symbols to avoid spurious matches (e.g., ZAR "R").
    private val tokenIndex: List<Pair<String, String>> by lazy {
        buildList {
            for (spec in CurrencyData.all) {
                add(spec.code.lowercase() to spec.code)
                val sym = spec.symbol.lowercase()
                if (sym.length > 1 || sym.any { !it.isLetter() }) add(sym to spec.code)
                for (alias in spec.aliases) add(alias.lowercase() to spec.code)
            }
        }.distinct().sortedByDescending { it.first.length }
    }

    // Returns native currency code from device locale, fallback USD.
    // Must source country from system (not app-overridden) — AppCompatDelegate per-app language
    // can strip country (e.g. "en-IN" → "en"), which then makes Currency.getInstance() fail.
    fun detectNativeCurrency(context: Context): String {
        val candidates = buildList {
            // SIM/network country first — language locale country (e.g. en_US) reflects
            runCatching {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
                    as? android.telephony.TelephonyManager
                tm?.simCountryIso?.takeIf { it.isNotBlank() }
                    ?.let { add(Locale("", it.uppercase())) }
                tm?.networkCountryIso?.takeIf { it.isNotBlank() }
                    ?.let { add(Locale("", it.uppercase())) }
            }
            runCatching {
                val sys = android.content.res.Resources.getSystem().configuration.locales
                for (i in 0 until sys.size()) sys.get(i)?.let { add(it) }
            }
            val ctxLocales = context.resources.configuration.locales
            for (i in 0 until ctxLocales.size()) ctxLocales.get(i)?.let { add(it) }
            add(Locale.getDefault())
        }
        for (locale in candidates) {
            if (locale.country.isNullOrBlank()) continue
            val code = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: continue
            if (code in supportedCodes) return code
        }
        return "USD"
    }

    // Attempts to parse input as a currency conversion expression
    fun tryParse(input: String, nativeCode: String): Parsed? {
        val raw = input.trim()
        if (raw.isBlank()) return null
        // Remove leading "convert"
        val cleaned = raw.replace(Regex("(?i)^\\s*convert\\s+"), "").trim()
        if (cleaned.isBlank()) return null

        // Try splitting around to/in/into
        val split = splitFromTo(cleaned)
        val fromPart = split.first
        val toPart = split.second

        val fromCurrency = findCurrency(fromPart, nativeCode) ?: return null
        val amount = findAmount(fromPart) ?: return null

        val toCurrency = if (toPart != null) {
            findCurrency(toPart, nativeCode) ?: return null
        } else {
            defaultTarget(fromCurrency, nativeCode)
        }

        if (fromCurrency == toCurrency) return null
        return Parsed(amount, fromCurrency, toCurrency)
    }

    private fun defaultTarget(from: String, native: String): String {
        return if (from == native) {
            if (native == "USD") "EUR" else "USD"
        } else native
    }

    private fun splitFromTo(text: String): Pair<String, String?> {
        val match = splitRegex.find(text) ?: return text to null
        val before = text.substring(0, match.range.first).trim()
        val after = text.substring(match.range.last + 1).trim()
        if (before.isBlank() || after.isBlank()) return text to null
        return before to after
    }

    private fun findCurrency(part: String, nativeCode: String): String? {
        val lower = part.lowercase()
        for ((token, code) in tokenIndex) {
            if (lower.containsToken(token)) {
                // Resolve ambiguous bare symbols with locale hint
                if (token == "$" || token == "¥" || token == "kr" || token == "r") {
                    return CurrencyData.resolveAmbiguousSymbol(token, nativeCode) ?: code
                }
                return code
            }
        }
        return null
    }

    private fun findAmount(part: String): Double? {
        val match = amountRegex.find(part) ?: return null
        val numStr = match.value.replace(",", "").replace(" ", "")
        return numStr.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    // contains() with word-boundary semantics for letter-only tokens
    private fun String.containsToken(token: String): Boolean {
        if (token.isEmpty()) return false
        // Letter-only tokens: require word boundaries
        if (token.all { it.isLetter() || it.isWhitespace() }) {
            return Regex("(?<![\\p{L}])${Regex.escape(token)}(?![\\p{L}])").containsMatchIn(this)
        }
        // Symbols / mixed: direct substring
        return this.contains(token)
    }

    // Convert amount from->to using a base->rate map (base currency typically USD or EUR)
    fun convert(amount: Double, from: String, to: String, base: String, rates: Map<String, Double>): Double? {
        val fromRate = if (from == base) 1.0 else rates[from] ?: return null
        val toRate = if (to == base) 1.0 else rates[to] ?: return null
        return amount / fromRate * toRate
    }

    // Pretty format currency value with symbol. Uses more decimals for small (crypto) values.
    fun formatAmount(value: Double, code: String): String {
        val spec = CurrencyData.getByCode(code)
        val symbol = spec?.symbol ?: code
        val absVal = abs(value)
        val scale = when {
            absVal == 0.0 -> 2
            absVal >= 1.0 -> 2
            absVal >= 0.01 -> 4
            absVal >= 0.0001 -> 6
            else -> 8
        }
        val rounded = BigDecimal(value).setScale(scale, RoundingMode.HALF_UP)
        val plain = if (rounded.signum() == 0 || rounded.stripTrailingZeros().scale() <= 0) {
            rounded.setScale(0, RoundingMode.DOWN).toPlainString()
        } else {
            rounded.stripTrailingZeros().toPlainString()
        }
        val withGrouping = addThousands(plain)
        // Add space between symbol and number when symbol is letters (e.g. "BNB 12.5")
        val sep = if (symbol.all { it.isLetter() }) " " else ""
        return "$symbol$sep$withGrouping"
    }

    private fun addThousands(plain: String): String {
        val sign = if (plain.startsWith("-")) "-" else ""
        val unsigned = if (sign.isNotEmpty()) plain.substring(1) else plain
        val dot = unsigned.indexOf('.')
        val intPart = if (dot == -1) unsigned else unsigned.substring(0, dot)
        val decPart = if (dot == -1) "" else unsigned.substring(dot)
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        return "$sign$grouped$decPart"
    }

}
