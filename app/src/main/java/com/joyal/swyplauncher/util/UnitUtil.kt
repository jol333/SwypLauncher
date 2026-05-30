package com.joyal.swyplauncher.util

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

object UnitUtil {

    data class Region(val imperial: Boolean, val indian: Boolean, val country: String, val locale: Locale)
    data class Parsed(val amount: Double, val from: String, val to: String, val category: UnitData.Category)

    private val amountRegex = Regex("[+-]?(\\d{1,3}(?:[,\\s]\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)")
    private val splitRegex = Regex("\\b(into|to|in)\\b", RegexOption.IGNORE_CASE)

    // Countries that primarily use imperial units
    private val imperialCountries = setOf("US", "LR", "MM")

    // (token, unitId) pairs, longest first so the most specific alias wins.
    // Built in category-priority order so collisions resolve to the more common category.
    private val tokenIndex: List<Pair<String, String>> by lazy {
        val seen = HashSet<String>()
        buildList {
            for (spec in UnitData.parsePriorityOrder) {
                val tokens = buildList {
                    add(spec.id.replace('_', ' '))
                    val sym = spec.symbol.lowercase()
                    if (sym.length > 1 || sym.any { !it.isLetter() }) add(sym)
                    addAll(spec.aliases.map { it.lowercase() })
                }
                for (t in tokens) if (t.isNotBlank() && seen.add(t)) add(t to spec.id)
            }
        }.sortedByDescending { it.first.length }
    }

    // Detect measurement system + Indian numbering from SIM/network country (no GPS).
    fun detectRegion(context: Context): Region {
        val country = systemCountry(context)
        // Find the native, default language for this country using ICU
        val uLocale = android.icu.util.ULocale.addLikelySubtags(android.icu.util.ULocale("und", country))
        return Region(
            imperial = country in imperialCountries,
            indian = country == "IN",
            country = country,
            locale = uLocale.toLocale()
        )
    }

    private fun systemCountry(context: Context): String {
        val candidates = buildList {
            runCatching {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
                    as? android.telephony.TelephonyManager
                tm?.simCountryIso?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                tm?.networkCountryIso?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            }
            runCatching {
                val sys = android.content.res.Resources.getSystem().configuration.locales
                for (i in 0 until sys.size()) sys.get(i)?.country?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            }
            Locale.getDefault().country?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        }
        return candidates.firstOrNull() ?: "US"
    }

    // Parse input like "34 fahrenheit", "25°F", "1 lightyear to miles", "5 km in m"
    fun tryParse(input: String, region: Region): Parsed? {
        val raw = input.trim()
        if (raw.isBlank()) return null
        val cleaned = raw.replace(Regex("(?i)^\\s*convert\\s+"), "").trim()
        if (cleaned.isBlank()) return null

        val (fromPart, toPart) = splitFromTo(cleaned)

        val fromId = findUnit(fromPart) ?: return null
        val fromSpec = UnitData.byId(fromId) ?: return null
        val amount = findAmount(fromPart, fromSpec) ?: return null

        val toId = if (toPart != null) {
            val t = findUnit(toPart) ?: return null
            if (UnitData.byId(t)?.category != fromSpec.category) return null
            t
        } else {
            defaultTarget(fromId, region) ?: return null
        }

        if (fromId == toId) return null
        return Parsed(amount, fromId, toId, fromSpec.category)
    }

    private fun splitFromTo(text: String): Pair<String, String?> {
        val match = splitRegex.find(text) ?: return text to null
        val before = text.substring(0, match.range.first).trim()
        val after = text.substring(match.range.last + 1).trim()
        if (before.isBlank() || after.isBlank()) return text to null
        return before to after
    }

    private fun findUnit(part: String): String? {
        val lower = part.lowercase()
        for ((token, id) in tokenIndex) if (lower.containsToken(token)) return id
        return null
    }

    private fun findAmount(part: String, spec: UnitData.Spec): Double? {
        // Numeral systems: value is digits in the unit's radix (e.g. "ff", "1010", "0xff")
        if (spec.radix != null) {
            val radix = spec.radix
            val tok = Regex("[0-9a-fA-F]+").findAll(part)
                .map { it.value }
                .filter { v -> v.all { Character.digit(it, radix) >= 0 } }
                .maxByOrNull { it.length }  // longest valid run (handles "0xff" -> "ff")
                ?: return null
            return tok.toLongOrNull(radix)?.toDouble()
        }
        val match = amountRegex.find(part) ?: return null
        return match.value.replace(",", "").replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    // Most popular target for a source unit; falls back to region-preferred primary.
    fun defaultTarget(fromId: String, region: Region): String? {
        val spec = UnitData.byId(fromId) ?: return null
        val meta = UnitData.meta[spec.category] ?: return null
        meta.popularPairs[fromId]?.let { if (UnitData.byId(it) != null && it != fromId) return it }
        val primary = if (region.imperial) meta.imperialPrimary else meta.metricPrimary
        if (primary != fromId && UnitData.byId(primary) != null) return primary
        if (meta.secondary != fromId && UnitData.byId(meta.secondary) != null) return meta.secondary
        return UnitData.unitsIn(spec.category).map { it.id }.firstOrNull { it != fromId }
    }

    // Affine for ordinary units; special handling for fuel (inverse) and numeral (radix repr).
    fun convert(value: Double, fromId: String, toId: String): Double? {
        val f = UnitData.byId(fromId) ?: return null
        val t = UnitData.byId(toId) ?: return null
        if (f.category != t.category) return null
        return when {
            // Numeral: stored value is already decimal; display layer renders the radix
            f.radix != null && t.radix != null -> value
            // Fuel consumption: convert via L/100km base
            f.fuelKind != null && t.fuelKind != null && f.fuelK != null && t.fuelK != null -> {
                val base = if (f.fuelKind == "dist_per_vol") f.fuelK / value else value * f.fuelK
                if (base == 0.0) return null
                if (t.fuelKind == "dist_per_vol") t.fuelK / base else base / t.fuelK
            }
            else -> {
                val base = value * f.factor + f.offset
                val result = (base - t.offset) / t.factor
                // Shoe sizes: reject unrealistic foot length or sub-minimum (avoids negative sizes)
                if (f.category == UnitData.Category.SHOE_SIZE &&
                    (base < SHOE_FOOT_MIN_MM || base > SHOE_FOOT_MAX_MM || result < 0.5)) {
                    return null
                }
                result
            }
        }
    }

    // Plausible foot-length range (mm) for real shoes — newborn to large adult
    private const val SHOE_FOOT_MIN_MM = 110.0
    private const val SHOE_FOOT_MAX_MM = 340.0

    // contains() with word-boundary semantics for letter tokens
    private fun String.containsToken(token: String): Boolean {
        if (token.isEmpty()) return false
        // Bare single letter (e.g. "m","f"): must stand alone — avoids hijacking "3d", "5k"
        if (token.length == 1 && token[0].isLetter()) {
            return Regex("(?<![\\p{L}\\d])${Regex.escape(token)}(?![\\p{L}\\d])").containsMatchIn(this)
        }
        if (token.all { it.isLetter() || it.isWhitespace() }) {
            return Regex("(?<![\\p{L}])${Regex.escape(token)}(?![\\p{L}])").containsMatchIn(this)
        }
        return this.contains(token)
    }

    // International large-number scale words
    private val intlScales = listOf(
        1e15 to "quadrillion", 1e12 to "trillion", 1e9 to "billion", 1e6 to "million"
    )

    // Approximate human-readable form for very large results, null if in normal range.
    fun humanReadable(value: Double, indian: Boolean): String? {
        if (!value.isFinite()) return null
        if (abs(value) < 1e6) return null
        return if (indian) indianReadable(value) else intlReadable(value)
    }

    private fun intlReadable(value: Double): String {
        val a = abs(value)
        for ((threshold, word) in intlScales) {
            if (a >= threshold) return "~${trimNum(value / threshold)} $word"
        }
        return "~${trimNum(value / 1e3)} thousand"
    }

    // Indian numbering: lakh (1e5), crore (1e7), then chained "lakh crore" (1e12), "crore crore" (1e14)
    private fun indianReadable(value: Double): String {
        val a = abs(value)
        return when {
            a >= 1e14 -> "~${trimNum(value / 1e14)} crore crore"
            a >= 1e12 -> "~${trimNum(value / 1e12)} lakh crore"
            a >= 1e7 -> "~${trimNum(value / 1e7)} crore"
            a >= 1e5 -> "~${trimNum(value / 1e5)} lakh"
            else -> "~${trimNum(value / 1e3)} thousand"
        }
    }

    private fun trimNum(v: Double): String {
        val bd = v.toBigDecimal().setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    // Pretty value formatting for the converter (radix-aware for numeral systems).
    fun formatValue(value: Double, unitId: String): String {
        val spec = UnitData.byId(unitId)
        if (spec?.radix != null) {
            return value.toLong().toString(spec.radix).uppercase()
        }
        // Snap to display increment (e.g. shoe sizes to nearest 0.5 / 5mm)
        spec?.displayStep?.let { step ->
            return trimNum(Math.round(value / step) * step)
        }
        val a = abs(value)
        val scale = when {
            a == 0.0 -> 0
            a >= 100 -> 2
            a >= 1 -> 4
            a >= 0.0001 -> 6
            else -> 10
        }
        val rounded = value.toBigDecimal().setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros()
        return rounded.toPlainString()
    }
}
