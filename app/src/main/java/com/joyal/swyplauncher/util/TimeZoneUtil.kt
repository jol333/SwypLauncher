package com.joyal.swyplauncher.util

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

/**
 * Parses natural-language time-zone queries and converts between zones locally
 * using [java.time] (DST handled by each zone's [java.time.zone.ZoneRules] — no
 * 3rd-party library, no network).
 *
 * Supported shapes (non-exhaustive):
 *   "US time now"                                  home → all US zones
 *   "time in japan" / "japan time"                 home → Japan
 *   "2:40 AEST to EST"                             AEST → EST (2:40 anchored to AEST)
 *   "convert 3:30 PM in CST to australia time"     CST → all Australia zones
 *   "if 15:24 in Sri Lanka now, what will be the time in Mexico"  LK → all Mexico zones
 *
 * Format rules (per spec):
 *   - input value ≥ 13:00 (e.g. 15:24)  → force 24h everywhere
 *   - input has am/pm                    → force 12h everywhere
 *   - no explicit time (or ambiguous)    → every row uses the user's *home* country's
 *                                          popular format (so a single conversion never
 *                                          mixes 12h and 24h across its rows)
 */
object TimeZoneUtil {

    /** A side of the conversion: a whole country (multiple zones) or one zone. */
    sealed interface Ref {
        data class CountryRef(val iso: String) : Ref
        data class ZoneRef(val zoneId: String, val countryIso: String?) : Ref
    }

    enum class FormatPref { FORCE_24, FORCE_12, COUNTRY_DEFAULT }

    data class Parsed(
        val primary: Ref,
        val secondary: Ref,
        val epochMillis: Long,
        val formatPref: FormatPref
    )

    /** One render-ready zone line. */
    data class Row(
        val zoneId: String,
        val city: String,
        val abbrev: String,        // live abbreviation (e.g. "EDT"), may be empty
        val offsetLabel: String,   // e.g. "UTC+5:30"
        val timeText: String,      // "9:30 PM" or "21:30"
        val dayDelta: Int,         // day difference vs the primary zone (-1 / 0 / +1)
        val use24h: Boolean
    )

    data class Built(val primaryRows: List<Row>, val secondaryRows: List<Row>)

    // ─────────────────────────────────────────────────────────────────────────
    // Home detection (no GPS — SIM/network/locale + system zone)
    // ─────────────────────────────────────────────────────────────────────────

    fun detectHome(context: Context): Ref.ZoneRef {
        val sysZone = runCatching { ZoneId.systemDefault().id }.getOrNull() ?: "Etc/UTC"
        val iso = TimeZoneData.countryForZone(sysZone)?.iso ?: systemCountry(context)
        return Ref.ZoneRef(sysZone, iso)
    }

    private fun systemCountry(context: Context): String? {
        val candidates = buildList {
            runCatching {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
                    as? android.telephony.TelephonyManager
                tm?.simCountryIso?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                tm?.networkCountryIso?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            }
            runCatching {
                val sys = android.content.res.Resources.getSystem().configuration.locales
                for (i in 0 until sys.size()) sys.get(i)?.country?.takeIf { it.isNotBlank() }
                    ?.let { add(it.uppercase()) }
            }
            Locale.getDefault().country?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        }
        return candidates.firstOrNull { TimeZoneData.country(it) != null } ?: candidates.firstOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    private val IC = setOf(RegexOption.IGNORE_CASE)
    // "15:24", "3:30 pm" — colon separator, meridiem optional
    private val colonRegex = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})\\s*(a\\.?m\\.?|p\\.?m\\.?)?(?!\\d)", IC)
    // "2.40pm" — dot separator ONLY with a meridiem, so plain decimals like "2.40" aren't times
    private val dotMeridiemRegex = Regex("(?<!\\d)(\\d{1,2})\\.(\\d{2})\\s*(a\\.?m\\.?|p\\.?m\\.?)(?!\\d)", IC)
    // "5pm", "9 am", "11 p.m."
    private val hRegex = Regex("(?<![\\d.:])(\\d{1,2})\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![\\d\\p{L}])", IC)
    // "1500 hrs", "0930 h" — military time needs the explicit hr(s)/h suffix to be unambiguous
    private val militaryRegex = Regex("(?<!\\d)([01]\\d|2[0-3])([0-5]\\d)\\s*(?:h|hrs?|hours)\\b", IC)
    private val noonRegex = Regex("\\bnoon\\b", IC)
    private val midnightRegex = Regex("\\bmidnight\\b", IC)
    private val nowRegex = Regex("\\b(now|right now|currently|current time|at the moment|atm)\\b", IC)
    private val splitRegex = Regex("\\b(to|into)\\b", IC)
    private val fromRegex = Regex("\\bfrom\\b", IC)
    // Word-bounded so "showtime"/"overtime"/"sometime"/"timeline" do NOT count as time-intent.
    private val timeWordRegex = Regex("\\b(time|timezone|o'?clock)\\b", IC)
    // Abbreviation tokens that are also everyday words / mean something else (GST = tax in India):
    // kept as references but NOT a standalone "this is a time query" signal.
    private val intentDenylist = setOf("gst")

    private data class RefMatch(val ref: Ref, val start: Int, val end: Int)
    private data class TimeMatch(val hour: Int, val minute: Int, val start: Int, val pref: FormatPref)

    /**
     * Attempts to parse [input] as a time-zone query. [home] is the auto-detected
     * home zone (used as the default side the user didn't specify).
     * [nowMillis] is the reference "now" and defaults to the system clock.
     */
    fun tryParse(
        input: String,
        home: Ref.ZoneRef,
        nowMillis: Long = System.currentTimeMillis()
    ): Parsed? {
        val text = normalizeSpoken(input.trim().lowercase(Locale.ROOT))
        if (text.isBlank()) return null

        val refs = findRefs(text)
        if (refs.isEmpty()) return null

        val time = findTime(text)
        val hasAbbrev = abbrevRegexes.any { it.containsMatchIn(text) }
        val hasTimeIntent = time != null ||
            nowRegex.containsMatchIn(text) ||
            timeWordRegex.containsMatchIn(text) ||
            hasAbbrev
        if (!hasTimeIntent) return null

        val (primary, secondary) = resolveSides(text, refs, time, home)

        // Anchor a specified clock time to the *primary* (source) zone; else use now.
        val epochMillis = if (time == null) {
            nowMillis
        } else {
            val z = ZoneId.of(representativeZone(primary))
            Instant.ofEpochMilli(nowMillis).atZone(z)
                .withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
                .toInstant().toEpochMilli()
        }

        // No explicit time (or an ambiguous one like "midnight") → render everything in the
        // home country's popular format, rather than letting each row follow its own country.
        val formatPref = when (val p = time?.pref) {
            null, FormatPref.COUNTRY_DEFAULT -> homeFormatPref(home)
            else -> p
        }
        return Parsed(primary, secondary, epochMillis, formatPref)
    }

    // Pure-alpha abbreviation tokens (IST/EST/GMT…) that imply a time query — excluding
    // homographs in [intentDenylist] (e.g. "gst") which still resolve as references.
    private val abbrevRegexes: List<Regex> by lazy {
        TimeZoneData.zoneTokens
            .filter { it.cityOverride == null && it.token.all { c -> c.isLetter() } && it.token !in intentDenylist }
            .mapNotNull { compileToken(it.token) }
    }

    /** The home country's popular clock format, as a concrete force-preference. */
    private fun homeFormatPref(home: Ref.ZoneRef): FormatPref =
        if (clockFormatFor(home.zoneId, home.countryIso) == TimeZoneData.ClockFormat.H24)
            FormatPref.FORCE_24 else FormatPref.FORCE_12

    /** Resolve which ref is the source (primary, on top) and which is the target. */
    private fun resolveSides(
        text: String,
        refs: List<RefMatch>,
        time: TimeMatch?,
        home: Ref.ZoneRef
    ): Pair<Ref, Ref> {
        val sorted = refs.sortedBy { it.start }

        // Explicit "from <src>" marks the source; target is the ref after a "to"/"into"
        // split if present, otherwise the other ref. ("9am in london from tokyo")
        fromRegex.find(text)?.let { fm ->
            val src = sorted.firstOrNull { it.start > fm.range.last }
            if (src != null) {
                var tgt: RefMatch? = null
                for (m in splitRegex.findAll(text)) {
                    val after = sorted.firstOrNull { it.start > m.range.first && it !== src }
                    if (after != null) { tgt = after; break }
                }
                if (tgt == null) tgt = sorted.firstOrNull { it !== src }
                if (tgt != null) return dedupe(src.ref, tgt.ref, home)
            }
        }

        // "<src> to/into <dst>"
        for (m in splitRegex.findAll(text)) {
            val p = m.range.first
            val before = sorted.lastOrNull { it.end < p }
            val after = sorted.firstOrNull { it.start > p }
            if (before != null && after != null) {
                return dedupe(before.ref, after.ref, home)
            }
        }

        if (sorted.size == 1) {
            val only = sorted[0].ref
            return if (time != null) dedupe(only, home, home)   // "2:40 AEST" → AEST → home
            else dedupe(home, only, home)                       // "US time now" → home → US
        }

        // ≥2 refs, no explicit split
        return if (time != null) {
            val source = sorted.minByOrNull { abs(it.start - time.start) }!!
            val target = sorted.filter { it !== source }.maxByOrNull { it.start } ?: sorted.first { it !== source }
            dedupe(source.ref, target.ref, home)
        } else {
            dedupe(sorted[0].ref, sorted[1].ref, home)
        }
    }

    /** Avoid showing the same zone on both sides. */
    private fun dedupe(primary: Ref, secondary: Ref, home: Ref.ZoneRef): Pair<Ref, Ref> {
        if (!sameZones(primary, secondary)) return primary to secondary
        val alt: Ref = if (!sameZones(primary, home)) home else Ref.ZoneRef("Etc/UTC", null)
        return primary to alt
    }

    /** Whether two refs resolve to the same set of zones (used for interactive dedupe). */
    fun sameZones(a: Ref, b: Ref): Boolean = zoneIds(a) == zoneIds(b)

    private fun zoneIds(ref: Ref): Set<String> = zonesOf(ref).map { it.zoneId }.toSet()

    /** Find country + zone-token references with positions, longest-match, no overlap. */
    private fun findRefs(text: String): List<RefMatch> {
        val matches = ArrayList<RefMatch>()
        val consumed = BooleanArray(text.length)

        for (tok in compiledIndex) {
            for (m in tok.regex.findAll(text)) {
                val s = m.range.first
                val e = m.range.last
                var clash = false
                for (i in s..e) if (consumed[i]) { clash = true; break }
                if (clash) continue
                for (i in s..e) consumed[i] = true
                matches.add(RefMatch(tok.ref, s, e))
            }
        }
        // Keep first occurrence of each distinct resolved zone-set, in reading order.
        return matches.sortedBy { it.start }
            .distinctBy { zoneIds(it.ref) }
    }

    private class Tok(val regex: Regex, val ref: Ref)

    // token -> Ref, longest first so "united states of america" wins over "america".
    // Precompiled (immutable) so concurrent mode parsers can share it safely.
    private val compiledIndex: List<Tok> by lazy {
        val list = ArrayList<Pair<String, Ref>>()
        for (c in TimeZoneData.countries) {
            for (a in c.aliases) list.add(a to Ref.CountryRef(c.iso))
        }
        for (z in TimeZoneData.zoneTokens) {
            list.add(z.token to Ref.ZoneRef(z.zoneId, z.country))
        }
        list.sortedByDescending { it.first.length }
            .mapNotNull { (t, r) -> compileToken(t)?.let { Tok(it, r) } }
    }

    // Word-edge match; allows trailing punctuation in tokens like "u.s.".
    private fun compileToken(token: String): Regex? =
        if (token.isBlank()) null
        else Regex("(?<![\\p{L}])${Regex.escape(token)}(?![\\p{L}])")

    // Spoken-time normalization for voice mode: "three pm" → "3 pm", "five thirty" → "5:30",
    // "quarter to six" → "5:45". Only fires next to an explicit time cue, so ordinary words
    // ("someone", "for one hour") are untouched.
    private val wordToNum = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12
    )
    private val numAlt = wordToNum.keys.joinToString("|")
    private val reOclock = Regex("\\b($numAlt)\\s+o'?clock\\b", IC)
    private val reHalfPast = Regex("\\bhalf\\s+past\\s+($numAlt)\\b", IC)
    private val reQuarterPast = Regex("\\bquarter\\s+past\\s+($numAlt)\\b", IC)
    private val reQuarterTo = Regex("\\bquarter\\s+to\\s+($numAlt)\\b", IC)
    private val reThirty = Regex("\\b($numAlt)\\s+thirty\\b", IC)
    private val reFifteen = Regex("\\b($numAlt)\\s+fifteen\\b", IC)
    private val reFortyFive = Regex("\\b($numAlt)\\s+forty[\\s-]?five\\b", IC)
    private val reMeridiemWord = Regex("\\b($numAlt)\\s+(a\\.?m\\.?|p\\.?m\\.?)\\b", IC)

    private fun numOf(w: String): Int = wordToNum[w.lowercase(Locale.ROOT)] ?: 0

    private fun normalizeSpoken(input: String): String {
        if (input.none { it.isLetter() }) return input
        var t = input
        t = reOclock.replace(t) { "${numOf(it.groupValues[1])}:00" }
        t = reHalfPast.replace(t) { "${numOf(it.groupValues[1])}:30" }
        t = reQuarterPast.replace(t) { "${numOf(it.groupValues[1])}:15" }
        t = reQuarterTo.replace(t) { val h = numOf(it.groupValues[1]) - 1; "${if (h <= 0) 12 else h}:45" }
        t = reThirty.replace(t) { "${numOf(it.groupValues[1])}:30" }
        t = reFifteen.replace(t) { "${numOf(it.groupValues[1])}:15" }
        t = reFortyFive.replace(t) { "${numOf(it.groupValues[1])}:45" }
        t = reMeridiemWord.replace(t) { "${numOf(it.groupValues[1])} ${it.groupValues[2]}" }
        return t
    }

    // Collects every clock candidate then returns the earliest one — so a leading
    // "3 pm" isn't lost to a later "18:00", and the time-vs-source anchoring stays stable.
    private fun findTime(text: String): TimeMatch? {
        val cands = ArrayList<TimeMatch>()
        for (m in colonRegex.findAll(text)) {
            val h = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].toIntOrNull() ?: continue
            toTimeMatch(h, min, m.groupValues[3].lowercase(), m.range.first)?.let { cands.add(it) }
        }
        for (m in dotMeridiemRegex.findAll(text)) {
            val h = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].toIntOrNull() ?: continue
            toTimeMatch(h, min, m.groupValues[3].lowercase(), m.range.first)?.let { cands.add(it) }
        }
        for (m in hRegex.findAll(text)) {
            val h = m.groupValues[1].toIntOrNull() ?: continue
            toTimeMatch(h, 0, m.groupValues[2].lowercase(), m.range.first)?.let { cands.add(it) }
        }
        for (m in militaryRegex.findAll(text)) {
            val h = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].toIntOrNull() ?: continue
            if (h in 0..23 && min in 0..59) cands.add(TimeMatch(h, min, m.range.first, FormatPref.FORCE_24))
        }
        noonRegex.find(text)?.let { cands.add(TimeMatch(12, 0, it.range.first, FormatPref.FORCE_12)) }
        midnightRegex.find(text)?.let { cands.add(TimeMatch(0, 0, it.range.first, FormatPref.COUNTRY_DEFAULT)) }
        return cands.minByOrNull { it.start }
    }

    private fun toTimeMatch(hourIn: Int, minute: Int, meridiem: String, start: Int): TimeMatch? {
        if (minute !in 0..59) return null
        var hour = hourIn
        val pref: FormatPref
        when {
            meridiem.startsWith("p") -> { // PM
                if (hour !in 1..12) return null
                if (hour < 12) hour += 12
                pref = FormatPref.FORCE_12
            }
            meridiem.startsWith("a") -> { // AM
                if (hour !in 1..12) return null
                if (hour == 12) hour = 0
                pref = FormatPref.FORCE_12
            }
            else -> {
                if (hour !in 0..23) return null
                pref = if (hour >= 13) FormatPref.FORCE_24 else FormatPref.COUNTRY_DEFAULT
            }
        }
        return TimeMatch(hour, minute, start, pref)
    }

    /**
     * Result of editing a row's clock. [pref] carries the format the user signalled by
     * what they typed — [FormatPref.FORCE_24] for a 24h value (≥13:00), [FormatPref.FORCE_12]
     * for an am/pm value — or null when ambiguous (caller keeps the current format).
     */
    data class EditedTime(val hour: Int, val minute: Int, val pref: FormatPref?)

    /** Lenient clock parse for the editable UI fields. */
    fun parseEditedTime(text: String): EditedTime? {
        val t = normalizeSpoken(text.trim().lowercase(Locale.ROOT))
        if (t.isBlank()) return null
        findTime(t)?.let { tm ->
            return EditedTime(tm.hour, tm.minute, tm.pref.takeIf { it != FormatPref.COUNTRY_DEFAULT })
        }
        // bare hour like "14" (24h intent) or "9" (ambiguous)
        t.toIntOrNull()?.let {
            if (it in 0..23) return EditedTime(it, 0, if (it >= 13) FormatPref.FORCE_24 else null)
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conversion / building rows
    // ─────────────────────────────────────────────────────────────────────────

    fun build(primary: Ref, secondary: Ref, epochMillis: Long, formatPref: FormatPref): Built {
        val instant = Instant.ofEpochMilli(epochMillis)
        val anchorDate = instant.atZone(ZoneId.of(representativeZone(primary))).toLocalDate()
        return Built(
            primaryRows = rowsFor(primary, instant, formatPref, anchorDate),
            secondaryRows = rowsFor(secondary, instant, formatPref, anchorDate)
        )
    }

    /** New instant when the user edits a row's clock, keeping that zone's date. */
    fun applyEditedTime(zoneId: String, hour: Int, minute: Int, currentEpochMillis: Long): Long =
        Instant.ofEpochMilli(currentEpochMillis).atZone(ZoneId.of(zoneId))
            .withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59))
            .withSecond(0).withNano(0)
            .toInstant().toEpochMilli()

    fun representativeZone(ref: Ref): String = when (ref) {
        is Ref.CountryRef -> TimeZoneData.country(ref.iso)?.zones?.firstOrNull()?.zoneId ?: "Etc/UTC"
        is Ref.ZoneRef -> ref.zoneId
    }

    private fun zonesOf(ref: Ref): List<TimeZoneData.Zone> = when (ref) {
        is Ref.CountryRef -> TimeZoneData.country(ref.iso)?.zones
            ?: listOf(TimeZoneData.Zone("Etc/UTC", "UTC"))
        is Ref.ZoneRef -> listOf(TimeZoneData.Zone(ref.zoneId, TimeZoneData.cityLabel(ref.zoneId)))
    }

    private fun countryHint(ref: Ref): String? = when (ref) {
        is Ref.CountryRef -> ref.iso
        is Ref.ZoneRef -> ref.countryIso
    }

    private fun rowsFor(
        ref: Ref,
        instant: Instant,
        formatPref: FormatPref,
        anchorDate: java.time.LocalDate
    ): List<Row> {
        val hint = countryHint(ref)
        return zonesOf(ref).map { z -> toRow(z.zoneId, z.city, hint, instant, formatPref, anchorDate) }
    }

    private fun toRow(
        zoneId: String,
        city: String,
        countryHint: String?,
        instant: Instant,
        formatPref: FormatPref,
        anchorDate: java.time.LocalDate
    ): Row {
        val zid = ZoneId.of(zoneId)
        val zdt = instant.atZone(zid)
        val use24h = when (formatPref) {
            FormatPref.FORCE_24 -> true
            FormatPref.FORCE_12 -> false
            FormatPref.COUNTRY_DEFAULT -> clockFormatFor(zoneId, countryHint) == TimeZoneData.ClockFormat.H24
        }
        return Row(
            zoneId = zoneId,
            city = city,
            abbrev = liveAbbrev(zoneId, instant),
            offsetLabel = offsetLabel(zdt),
            timeText = formatClock(zdt, use24h),
            dayDelta = (zdt.toLocalDate().toEpochDay() - anchorDate.toEpochDay()).toInt(),
            use24h = use24h
        )
    }

    private fun clockFormatFor(zoneId: String, countryHint: String?): TimeZoneData.ClockFormat {
        countryHint?.let { TimeZoneData.country(it)?.let { c -> return c.format } }
        return TimeZoneData.formatForZone(zoneId)
    }

    fun formatClock(zdt: ZonedDateTime, use24h: Boolean): String {
        val h = zdt.hour
        val m = zdt.minute
        return if (use24h) {
            String.format(Locale.US, "%02d:%02d", h, m)
        } else {
            val ampm = if (h < 12) "AM" else "PM"
            var hh = h % 12
            if (hh == 0) hh = 12
            String.format(Locale.US, "%d:%02d %s", hh, m, ampm)
        }
    }

    private fun offsetLabel(zdt: ZonedDateTime): String {
        val id = zdt.offset.id // "+05:30" or "Z"
        return "UTC" + if (id == "Z") "+00:00" else id
    }

    /**
     * DST-aware short name (e.g. "EDT"). Prefers the curated catalog abbrev (so Colombo
     * shows "SLST" not the JVM's ambiguous "IST", and Dubai shows "GST" not "GMT+4"),
     * falling back to the live JVM name, then to empty (UI then shows the offset only).
     */
    private fun liveAbbrev(zoneId: String, instant: Instant): String {
        return runCatching {
            val inDst = ZoneId.of(zoneId).rules.isDaylightSavings(instant)
            TimeZoneData.abbrevFor(zoneId, inDst)?.let { return it }
            val tz = java.util.TimeZone.getTimeZone(zoneId)
            val name = tz.getDisplayName(inDst, java.util.TimeZone.SHORT, Locale.US) ?: ""
            if (name.isEmpty() || name.startsWith("GMT") || name.startsWith("+") || name.startsWith("-")) "" else name
        }.getOrDefault("")
    }
}
