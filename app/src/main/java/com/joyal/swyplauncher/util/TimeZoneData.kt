package com.joyal.swyplauncher.util

/**
 * Static catalog for the time-zone conversion feature.
 *
 * Holds the set of countries we recognise, each with:
 *  - its primary IANA zone(s) (a country like the US has several),
 *  - the clock format that is popular there (12h vs 24h),
 *  - and the names / short-forms a user might type for it.
 *
 * Plus a table of standalone zone tokens (abbreviations like AEST/EST/IST and
 * city names) that resolve directly to a single IANA zone.
 *
 * All IANA ids here were validated against the Android/JVM TZDB. DST is handled
 * at conversion time by [java.time.ZoneId] rules, never hard-coded here.
 */
object TimeZoneData {

    /** Clock format popular in a country. Mixed countries (e.g. India) bucket as [H12]. */
    enum class ClockFormat { H12, H24 }

    /** A single IANA zone with a human-friendly city label. */
    data class Zone(val zoneId: String, val city: String)

    data class Country(
        val iso: String,                 // ISO 3166-1 alpha-2, e.g. "US"
        val name: String,                // display name, e.g. "United States"
        val format: ClockFormat,
        val zones: List<Zone>,           // primary zones, most-populous first
        val aliases: List<String>        // lowercased names / short forms to match
    )

    /**
     * A standalone token that maps to exactly one zone: timezone abbreviations
     * (AEST, EST, IST…) and well-known city names. [country] is the owning
     * country ISO (for format bucketing / labels); may be null for UTC/GMT.
     */
    data class ZoneToken(
        val token: String,               // lowercased match token
        val zoneId: String,
        val abbrev: String,              // display abbreviation, e.g. "AEST"
        val country: String?,            // owning country ISO, null for UTC/GMT
        val cityOverride: String? = null // label when token is a city, else null
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Countries
    // ─────────────────────────────────────────────────────────────────────────

    val countries: List<Country> = listOf(
        Country(
            "IN", "India", ClockFormat.H12,
            listOf(Zone("Asia/Kolkata", "India")),
            // note: "in"/"ind" omitted — collide with the preposition "in" / "industry" etc.
            listOf("india", "indian", "bharat")
        ),
        Country(
            "US", "United States", ClockFormat.H12,
            listOf(
                Zone("America/New_York", "New York (Eastern)"),
                Zone("America/Chicago", "Chicago (Central)"),
                Zone("America/Denver", "Denver (Mountain)"),
                Zone("America/Los_Angeles", "Los Angeles (Pacific)"),
                Zone("America/Anchorage", "Anchorage (Alaska)"),
                Zone("Pacific/Honolulu", "Honolulu (Hawaii)")
            ),
            listOf(
                "united states", "united states of america", "us", "u.s.", "u.s.a.",
                "usa", "america", "american", "the states", "stateside"
            )
        ),
        Country(
            "GB", "United Kingdom", ClockFormat.H12,
            listOf(Zone("Europe/London", "London")),
            listOf(
                "united kingdom", "uk", "u.k.", "britain", "great britain", "british",
                "england", "english", "scotland", "wales", "gb"
            )
        ),
        Country(
            "AU", "Australia", ClockFormat.H12,
            listOf(
                Zone("Australia/Sydney", "Sydney (Eastern)"),
                Zone("Australia/Adelaide", "Adelaide (Central)"),
                Zone("Australia/Brisbane", "Brisbane (Eastern, no DST)"),
                Zone("Australia/Perth", "Perth (Western)"),
                Zone("Australia/Darwin", "Darwin (Central, no DST)")
            ),
            listOf("australia", "australian", "aussie", "oz", "au", "aus")
        ),
        Country(
            "CA", "Canada", ClockFormat.H12,
            listOf(
                Zone("America/Toronto", "Toronto (Eastern)"),
                Zone("America/Winnipeg", "Winnipeg (Central)"),
                Zone("America/Edmonton", "Edmonton (Mountain)"),
                Zone("America/Vancouver", "Vancouver (Pacific)"),
                Zone("America/Halifax", "Halifax (Atlantic)"),
                Zone("America/St_Johns", "St. John's (Newfoundland)")
            ),
            // "ca" omitted — collides with California / Chartered Accountant ("CA exam time")
            listOf("canada", "canadian")
        ),
        Country(
            "AE", "United Arab Emirates", ClockFormat.H12,
            listOf(Zone("Asia/Dubai", "Dubai")),
            listOf(
                "united arab emirates", "uae", "u.a.e.", "emirates", "emirati",
                "dubai", "abu dhabi", "abudhabi"
            )
        ),
        Country(
            "LK", "Sri Lanka", ClockFormat.H12,
            listOf(Zone("Asia/Colombo", "Colombo")),
            listOf("sri lanka", "srilanka", "sri lankan", "lk", "ceylon", "colombo")
        ),
        Country(
            "MX", "Mexico", ClockFormat.H12,
            listOf(
                Zone("America/Mexico_City", "Mexico City (Central)"),
                Zone("America/Tijuana", "Tijuana (Pacific)"),
                Zone("America/Hermosillo", "Hermosillo (Mountain, no DST)"),
                Zone("America/Cancun", "Cancún (Eastern)")
            ),
            listOf("mexico", "mexican", "mx", "mex")
        ),
        Country(
            // Mainland China predominantly uses 24-hour notation in official/digital contexts.
            "CN", "China", ClockFormat.H24,
            listOf(Zone("Asia/Shanghai", "China")),
            listOf("china", "chinese", "cn", "prc", "beijing", "shanghai")
        ),
        Country(
            "JP", "Japan", ClockFormat.H12,
            listOf(Zone("Asia/Tokyo", "Tokyo")),
            listOf("japan", "japanese", "jp", "jpn", "tokyo", "nippon")
        ),
        Country(
            "KR", "South Korea", ClockFormat.H12,
            listOf(Zone("Asia/Seoul", "Seoul")),
            listOf("south korea", "korea", "korean", "kr", "republic of korea", "seoul")
        ),
        Country(
            "SG", "Singapore", ClockFormat.H12,
            listOf(Zone("Asia/Singapore", "Singapore")),
            listOf("singapore", "singaporean", "sg", "sgp")
        ),
        Country(
            "PK", "Pakistan", ClockFormat.H12,
            listOf(Zone("Asia/Karachi", "Karachi")),
            listOf("pakistan", "pakistani", "pk", "karachi", "islamabad")
        ),
        Country(
            "BD", "Bangladesh", ClockFormat.H12,
            listOf(Zone("Asia/Dhaka", "Dhaka")),
            listOf("bangladesh", "bangladeshi", "bd", "dhaka")
        ),
        Country(
            "NP", "Nepal", ClockFormat.H12,
            listOf(Zone("Asia/Kathmandu", "Kathmandu")),
            listOf("nepal", "nepali", "np", "kathmandu")
        ),
        Country(
            "PH", "Philippines", ClockFormat.H12,
            listOf(Zone("Asia/Manila", "Manila")),
            listOf("philippines", "philippine", "filipino", "ph", "phl", "manila")
        ),
        Country(
            "MY", "Malaysia", ClockFormat.H12,
            listOf(Zone("Asia/Kuala_Lumpur", "Kuala Lumpur")),
            listOf("malaysia", "malaysian", "kuala lumpur", "kl")
        ),
        Country(
            "ID", "Indonesia", ClockFormat.H24,
            listOf(
                Zone("Asia/Jakarta", "Jakarta (Western)"),
                Zone("Asia/Makassar", "Makassar (Central)"),
                Zone("Asia/Jayapura", "Jayapura (Eastern)")
            ),
            // "id" omitted — collides with "ID card", "id" etc.
            listOf("indonesia", "indonesian", "idn", "jakarta")
        ),
        Country(
            "TH", "Thailand", ClockFormat.H24,
            listOf(Zone("Asia/Bangkok", "Bangkok")),
            listOf("thailand", "thai", "th", "bangkok")
        ),
        Country(
            "VN", "Vietnam", ClockFormat.H24,
            listOf(Zone("Asia/Ho_Chi_Minh", "Ho Chi Minh City")),
            listOf("vietnam", "vietnamese", "vn", "hanoi", "ho chi minh")
        ),
        Country(
            "SA", "Saudi Arabia", ClockFormat.H12,
            listOf(Zone("Asia/Riyadh", "Riyadh")),
            listOf("saudi arabia", "saudi", "ksa", "riyadh")
        ),
        Country(
            "IL", "Israel", ClockFormat.H24,
            listOf(Zone("Asia/Jerusalem", "Jerusalem")),
            listOf("israel", "israeli", "il", "jerusalem", "tel aviv")
        ),
        Country(
            "IR", "Iran", ClockFormat.H24,
            listOf(Zone("Asia/Tehran", "Tehran")),
            listOf("iran", "iranian", "ir", "tehran", "persia")
        ),
        Country(
            "TR", "Turkey", ClockFormat.H24,
            listOf(Zone("Europe/Istanbul", "Istanbul")),
            listOf("turkey", "turkish", "turkiye", "türkiye", "tr", "istanbul", "ankara")
        ),
        Country(
            "RU", "Russia", ClockFormat.H24,
            listOf(
                Zone("Europe/Moscow", "Moscow"),
                Zone("Europe/Kaliningrad", "Kaliningrad"),
                Zone("Asia/Yekaterinburg", "Yekaterinburg"),
                Zone("Asia/Novosibirsk", "Novosibirsk"),
                Zone("Asia/Krasnoyarsk", "Krasnoyarsk"),
                Zone("Asia/Vladivostok", "Vladivostok")
            ),
            listOf("russia", "russian", "ru", "rus", "moscow")
        ),
        Country(
            "DE", "Germany", ClockFormat.H24,
            listOf(Zone("Europe/Berlin", "Berlin")),
            listOf("germany", "german", "de", "deu", "berlin", "deutschland")
        ),
        Country(
            "FR", "France", ClockFormat.H24,
            listOf(Zone("Europe/Paris", "Paris")),
            listOf("france", "french", "fr", "fra", "paris")
        ),
        Country(
            "IT", "Italy", ClockFormat.H24,
            listOf(Zone("Europe/Rome", "Rome")),
            listOf("italy", "italian", "ita", "rome", "italia")
        ),
        Country(
            "ES", "Spain", ClockFormat.H24,
            listOf(Zone("Europe/Madrid", "Madrid")),
            listOf("spain", "spanish", "es", "esp", "madrid", "españa")
        ),
        Country(
            "PT", "Portugal", ClockFormat.H24,
            listOf(Zone("Europe/Lisbon", "Lisbon")),
            listOf("portugal", "portuguese", "pt", "prt", "lisbon")
        ),
        Country(
            "NL", "Netherlands", ClockFormat.H24,
            listOf(Zone("Europe/Amsterdam", "Amsterdam")),
            listOf("netherlands", "holland", "dutch", "nl", "nld", "amsterdam")
        ),
        Country(
            "CH", "Switzerland", ClockFormat.H24,
            listOf(Zone("Europe/Zurich", "Zurich")),
            listOf("switzerland", "swiss", "ch", "che", "zurich", "geneva")
        ),
        Country(
            "PL", "Poland", ClockFormat.H24,
            listOf(Zone("Europe/Warsaw", "Warsaw")),
            listOf("poland", "polish", "pl", "pol", "warsaw")
        ),
        Country(
            "SE", "Sweden", ClockFormat.H24,
            listOf(Zone("Europe/Stockholm", "Stockholm")),
            listOf("sweden", "swedish", "se", "swe", "stockholm")
        ),
        Country(
            "NO", "Norway", ClockFormat.H24,
            listOf(Zone("Europe/Oslo", "Oslo")),
            listOf("norway", "norwegian", "nor", "oslo")
        ),
        Country(
            "FI", "Finland", ClockFormat.H24,
            listOf(Zone("Europe/Helsinki", "Helsinki")),
            listOf("finland", "finnish", "fi", "fin", "helsinki")
        ),
        Country(
            "GR", "Greece", ClockFormat.H24,
            listOf(Zone("Europe/Athens", "Athens")),
            listOf("greece", "greek", "gr", "grc", "athens")
        ),
        Country(
            "IE", "Ireland", ClockFormat.H12,
            listOf(Zone("Europe/Dublin", "Dublin")),
            listOf("ireland", "irish", "ie", "irl", "dublin")
        ),
        Country(
            "UA", "Ukraine", ClockFormat.H24,
            listOf(Zone("Europe/Kyiv", "Kyiv")),
            listOf("ukraine", "ukrainian", "ua", "ukr", "kyiv", "kiev")
        ),
        Country(
            "RO", "Romania", ClockFormat.H24,
            listOf(Zone("Europe/Bucharest", "Bucharest")),
            listOf("romania", "romanian", "ro", "rou", "bucharest")
        ),
        Country(
            "BR", "Brazil", ClockFormat.H24,
            listOf(
                Zone("America/Sao_Paulo", "São Paulo (Brasília)"),
                Zone("America/Manaus", "Manaus (Amazon)"),
                Zone("America/Rio_Branco", "Rio Branco (Acre)"),
                Zone("America/Noronha", "Fernando de Noronha")
            ),
            listOf("brazil", "brazilian", "br", "bra", "brasil", "sao paulo")
        ),
        Country(
            "AR", "Argentina", ClockFormat.H24,
            listOf(Zone("America/Argentina/Buenos_Aires", "Buenos Aires")),
            listOf("argentina", "argentine", "argentinian", "ar", "arg", "buenos aires")
        ),
        Country(
            "CL", "Chile", ClockFormat.H24,
            listOf(Zone("America/Santiago", "Santiago")),
            listOf("chile", "chilean", "cl", "chl", "santiago")
        ),
        Country(
            "CO", "Colombia", ClockFormat.H12,
            listOf(Zone("America/Bogota", "Bogotá")),
            // "co" omitted — collides with "co-", "company" etc.
            listOf("colombia", "colombian", "col", "bogota", "bogotá")
        ),
        Country(
            "PE", "Peru", ClockFormat.H12,
            listOf(Zone("America/Lima", "Lima")),
            // "per" omitted — collides with the English word "per"
            listOf("peru", "peruvian", "pe", "lima")
        ),
        Country(
            "VE", "Venezuela", ClockFormat.H12,
            listOf(Zone("America/Caracas", "Caracas")),
            listOf("venezuela", "venezuelan", "ve", "ven", "caracas")
        ),
        Country(
            "EG", "Egypt", ClockFormat.H12,
            listOf(Zone("Africa/Cairo", "Cairo")),
            listOf("egypt", "egyptian", "eg", "egy", "cairo")
        ),
        Country(
            "NG", "Nigeria", ClockFormat.H12,
            listOf(Zone("Africa/Lagos", "Lagos")),
            listOf("nigeria", "nigerian", "ng", "nga", "lagos", "abuja")
        ),
        Country(
            "KE", "Kenya", ClockFormat.H12,
            listOf(Zone("Africa/Nairobi", "Nairobi")),
            listOf("kenya", "kenyan", "ke", "ken", "nairobi")
        ),
        Country(
            "ZA", "South Africa", ClockFormat.H12,
            listOf(Zone("Africa/Johannesburg", "Johannesburg")),
            listOf("south africa", "south african", "za", "zaf", "johannesburg", "cape town")
        ),
        Country(
            "MA", "Morocco", ClockFormat.H24,
            listOf(Zone("Africa/Casablanca", "Casablanca")),
            listOf("morocco", "moroccan", "ma", "mar", "casablanca", "rabat")
        ),
        Country(
            "NZ", "New Zealand", ClockFormat.H12,
            listOf(
                Zone("Pacific/Auckland", "Auckland"),
                Zone("Pacific/Chatham", "Chatham Islands")
            ),
            listOf("new zealand", "newzealand", "nz", "nzl", "kiwi", "auckland")
        ),
        Country(
            "KZ", "Kazakhstan", ClockFormat.H24,
            listOf(Zone("Asia/Almaty", "Almaty")),
            listOf("kazakhstan", "kazakh", "kz", "kaz", "almaty", "astana")
        ),
        Country(
            "CU", "Cuba", ClockFormat.H12,
            listOf(Zone("America/Havana", "Havana")),
            listOf("cuba", "cuban", "cu", "cub", "havana")
        ),
        Country(
            "GH", "Ghana", ClockFormat.H12,
            listOf(Zone("Africa/Accra", "Accra")),
            listOf("ghana", "ghanaian", "gh", "gha", "accra")
        ),
        Country(
            "FJ", "Fiji", ClockFormat.H12,
            listOf(Zone("Pacific/Fiji", "Suva")),
            listOf("fiji", "fijian", "fj", "fji", "suva")
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Standalone zone tokens: abbreviations + UTC/GMT.
    //
    // Abbreviations are inherently ambiguous (CST = US Central / China / Cuba;
    // IST = India / Israel / Ireland). We resolve to the most commonly intended
    // meaning. Both standard and daylight forms point at the same IANA region,
    // since the region's rules give the correct live offset for any instant.
    // ─────────────────────────────────────────────────────────────────────────

    val zoneTokens: List<ZoneToken> = listOf(
        // Indian / South Asian
        ZoneToken("ist", "Asia/Kolkata", "IST", "IN"),       // India (most common IST)
        ZoneToken("pkt", "Asia/Karachi", "PKT", "PK"),
        ZoneToken("npt", "Asia/Kathmandu", "NPT", "NP"),
        ZoneToken("slst", "Asia/Colombo", "SLST", "LK"),
        // North American
        ZoneToken("est", "America/New_York", "EST", "US"),
        ZoneToken("edt", "America/New_York", "EDT", "US"),
        ZoneToken("cst", "America/Chicago", "CST", "US"),     // US Central (most common CST)
        ZoneToken("cdt", "America/Chicago", "CDT", "US"),
        ZoneToken("mst", "America/Denver", "MST", "US"),
        ZoneToken("mdt", "America/Denver", "MDT", "US"),
        ZoneToken("pst", "America/Los_Angeles", "PST", "US"),
        ZoneToken("pdt", "America/Los_Angeles", "PDT", "US"),
        ZoneToken("akst", "America/Anchorage", "AKST", "US"),
        ZoneToken("akdt", "America/Anchorage", "AKDT", "US"),
        ZoneToken("hst", "Pacific/Honolulu", "HST", "US"),
        ZoneToken("ast", "America/Halifax", "AST", "CA"),     // Atlantic
        ZoneToken("adt", "America/Halifax", "ADT", "CA"),
        ZoneToken("nst", "America/St_Johns", "NST", "CA"),
        ZoneToken("ndt", "America/St_Johns", "NDT", "CA"),
        // Australian / Pacific
        ZoneToken("aest", "Australia/Sydney", "AEST", "AU"),
        ZoneToken("aedt", "Australia/Sydney", "AEDT", "AU"),
        ZoneToken("acst", "Australia/Adelaide", "ACST", "AU"),
        ZoneToken("acdt", "Australia/Adelaide", "ACDT", "AU"),
        ZoneToken("awst", "Australia/Perth", "AWST", "AU"),
        ZoneToken("nzst", "Pacific/Auckland", "NZST", "NZ"),
        ZoneToken("nzdt", "Pacific/Auckland", "NZDT", "NZ"),
        // European
        ZoneToken("gmt", "Etc/GMT", "GMT", null),
        ZoneToken("utc", "Etc/UTC", "UTC", null),
        ZoneToken("bst", "Europe/London", "BST", "GB"),       // British Summer Time
        ZoneToken("cet", "Europe/Paris", "CET", null),
        ZoneToken("cest", "Europe/Paris", "CEST", null),
        ZoneToken("eet", "Europe/Athens", "EET", null),
        ZoneToken("eest", "Europe/Athens", "EEST", null),
        ZoneToken("msk", "Europe/Moscow", "MSK", "RU"),
        ZoneToken("trt", "Europe/Istanbul", "TRT", "TR"),
        // Middle Eastern / African
        // "gst" is the spec-required Gulf token but also means GST (tax) in India, so it
        // is kept as a reference yet excluded from the "this is a time query" signal.
        ZoneToken("gst", "Asia/Dubai", "GST", "AE"),
        ZoneToken("irst", "Asia/Tehran", "IRST", "IR"),
        ZoneToken("idt", "Asia/Jerusalem", "IDT", "IL"),
        ZoneToken("sast", "Africa/Johannesburg", "SAST", "ZA"),
        // East Asian / SE Asian
        ZoneToken("jst", "Asia/Tokyo", "JST", "JP"),
        ZoneToken("kst", "Asia/Seoul", "KST", "KR"),
        ZoneToken("sgt", "Asia/Singapore", "SGT", "SG"),
        ZoneToken("myt", "Asia/Kuala_Lumpur", "MYT", "MY"),
        ZoneToken("wib", "Asia/Jakarta", "WIB", "ID"),
        ZoneToken("pht", "Asia/Manila", "PHT", "PH"),
        ZoneToken("hkt", "Asia/Hong_Kong", "HKT", null),
        // South American
        ZoneToken("brt", "America/Sao_Paulo", "BRT", "BR"),
        ZoneToken("clt", "America/Santiago", "CLT", "CL"),
        // Note: ART/CAT/EAT/WET/ICT abbreviations are intentionally omitted — they are
        // everyday English words; the countries are reachable by name (Argentina, etc.).

        // Major cities (multi-word allowed; resolve to a single zone)
        ZoneToken("new york", "America/New_York", "EST", "US", "New York"),
        ZoneToken("nyc", "America/New_York", "EST", "US", "New York"),
        ZoneToken("washington", "America/New_York", "EST", "US", "Washington, D.C."),
        ZoneToken("boston", "America/New_York", "EST", "US", "Boston"),
        ZoneToken("miami", "America/New_York", "EST", "US", "Miami"),
        ZoneToken("chicago", "America/Chicago", "CST", "US", "Chicago"),
        ZoneToken("houston", "America/Chicago", "CST", "US", "Houston"),
        ZoneToken("dallas", "America/Chicago", "CST", "US", "Dallas"),
        ZoneToken("denver", "America/Denver", "MST", "US", "Denver"),
        ZoneToken("los angeles", "America/Los_Angeles", "PST", "US", "Los Angeles"),
        ZoneToken("san francisco", "America/Los_Angeles", "PST", "US", "San Francisco"),
        ZoneToken("seattle", "America/Los_Angeles", "PST", "US", "Seattle"),
        ZoneToken("london", "Europe/London", "GMT", "GB", "London"),
        ZoneToken("paris", "Europe/Paris", "CET", "FR", "Paris"),
        ZoneToken("munich", "Europe/Berlin", "CET", "DE", "Munich"),
        ZoneToken("barcelona", "Europe/Madrid", "CET", "ES", "Barcelona"),
        ZoneToken("milan", "Europe/Rome", "CET", "IT", "Milan"),
        ZoneToken("amsterdam", "Europe/Amsterdam", "CET", "NL", "Amsterdam"),
        ZoneToken("toronto", "America/Toronto", "EST", "CA", "Toronto"),
        ZoneToken("vancouver", "America/Vancouver", "PST", "CA", "Vancouver"),
        ZoneToken("hong kong", "Asia/Hong_Kong", "HKT", null, "Hong Kong"),
        ZoneToken("hongkong", "Asia/Hong_Kong", "HKT", null, "Hong Kong"),
        ZoneToken("melbourne", "Australia/Sydney", "AEST", "AU", "Melbourne"),
        ZoneToken("new south wales", "Australia/Sydney", "AEST", "AU", "New South Wales"),
        ZoneToken("mexico city", "America/Mexico_City", "CST", "MX", "Mexico City"),

        // Full spelled-out zone names (the spec calls out e.g. "Gulf Standard Time").
        // Multi-word, so they never self-trigger as bare abbreviations; "...time" carries intent.
        ZoneToken("gulf standard time", "Asia/Dubai", "GST", "AE", "Gulf"),
        ZoneToken("india standard time", "Asia/Kolkata", "IST", "IN", "India"),
        ZoneToken("greenwich mean time", "Etc/GMT", "GMT", null, "GMT"),
        ZoneToken("coordinated universal time", "Etc/UTC", "UTC", null, "UTC"),
        ZoneToken("eastern standard time", "America/New_York", "EST", "US", "Eastern"),
        ZoneToken("eastern time", "America/New_York", "EST", "US", "Eastern"),
        ZoneToken("central standard time", "America/Chicago", "CST", "US", "Central"),
        ZoneToken("central time", "America/Chicago", "CST", "US", "Central"),
        ZoneToken("mountain standard time", "America/Denver", "MST", "US", "Mountain"),
        ZoneToken("mountain time", "America/Denver", "MST", "US", "Mountain"),
        ZoneToken("pacific standard time", "America/Los_Angeles", "PST", "US", "Pacific"),
        ZoneToken("pacific time", "America/Los_Angeles", "PST", "US", "Pacific"),
        ZoneToken("japan standard time", "Asia/Tokyo", "JST", "JP", "Japan"),
        ZoneToken("australian eastern standard time", "Australia/Sydney", "AEST", "AU", "Sydney")
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Lookups
    // ─────────────────────────────────────────────────────────────────────────

    private val byIso: Map<String, Country> = countries.associateBy { it.iso }

    /** zoneId -> owning country, for labels / format bucketing of a bare zone. */
    private val zoneToCountry: Map<String, Country> = buildMap {
        for (c in countries) for (z in c.zones) putIfAbsent(z.zoneId, c)
    }

    fun country(iso: String): Country? = byIso[iso.uppercase()]

    fun countryForZone(zoneId: String): Country? = zoneToCountry[zoneId]

    /** City label for a zone, preferring the catalog's label, else the IANA tail. */
    fun cityLabel(zoneId: String): String {
        for (c in countries) for (z in c.zones) if (z.zoneId == zoneId) return z.city
        return zoneId.substringAfterLast('/').replace('_', ' ')
    }

    /** Clock format for a zone: its country's bucket, defaulting to 24h (UTC/GMT). */
    fun formatForZone(zoneId: String): ClockFormat =
        zoneToCountry[zoneId]?.format ?: ClockFormat.H24

    // ─────────────────────────────────────────────────────────────────────────
    // Curated abbreviations (DST-aware), so rows show e.g. "SLST"/"GST"/"EDT"
    // instead of the JVM's ambiguous ("IST" for Colombo) or numeric ("GMT+4") names.
    // ─────────────────────────────────────────────────────────────────────────

    private fun isDaylightAbbrev(a: String): Boolean =
        a.endsWith("DT") || a in setOf("BST", "CEST", "EEST", "WEST")

    private val stdAbbrev: Map<String, String> = buildMap {
        for (t in zoneTokens) if (t.cityOverride == null && !isDaylightAbbrev(t.abbrev)) putIfAbsent(t.zoneId, t.abbrev)
        // Standard (winter) / display-only forms for zones whose only token is daylight or absent.
        putIfAbsent("Europe/London", "GMT")
        putIfAbsent("Asia/Shanghai", "CST")
        putIfAbsent("Asia/Kolkata", "IST")
    }

    private val dstAbbrev: Map<String, String> = buildMap {
        for (t in zoneTokens) if (t.cityOverride == null && isDaylightAbbrev(t.abbrev)) putIfAbsent(t.zoneId, t.abbrev)
    }

    /** Curated abbreviation for a zone at the given DST state, or null if none known. */
    fun abbrevFor(zoneId: String, isDst: Boolean): String? {
        if (isDst) dstAbbrev[zoneId]?.let { return it }
        return stdAbbrev[zoneId] ?: dstAbbrev[zoneId]
    }
}
