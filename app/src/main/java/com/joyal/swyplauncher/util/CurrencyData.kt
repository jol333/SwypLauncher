package com.joyal.swyplauncher.util

object CurrencyData {

    // Currency code, primary symbol, and recognizable aliases
    data class Spec(val code: String, val symbol: String, val aliases: List<String>)

    val all: List<Spec> = listOf(
        Spec("USD", "$", listOf("usd", "us dollar", "us dollars", "american dollar", "dollar", "dollars", "buck", "bucks")),
        Spec("EUR", "€", listOf("eur", "euro", "euros")),
        Spec("GBP", "£", listOf("gbp", "british pound", "british pounds", "pound sterling", "pound", "pounds", "sterling", "quid")),
        Spec("INR", "₹", listOf("inr", "indian rupee", "indian rupees", "rupee", "rupees", "rs", "Rs.")),
        Spec("JPY", "¥", listOf("jpy", "japanese yen", "yen")),
        Spec("CNY", "CN¥", listOf("cny", "rmb", "renminbi", "chinese yuan", "yuan", "cn¥")),
        Spec("AUD", "A$", listOf("aud", "australian dollar", "australian dollars", "aussie dollar", "a$")),
        Spec("CAD", "C$", listOf("cad", "canadian dollar", "canadian dollars", "c$")),
        Spec("CHF", "Fr", listOf("chf", "swiss franc", "swiss francs", "franc", "francs")),
        Spec("SGD", "S$", listOf("sgd", "singapore dollar", "singapore dollars", "s$")),
        Spec("HKD", "HK$", listOf("hkd", "hong kong dollar", "hong kong dollars", "hk$")),
        Spec("NZD", "NZ$", listOf("nzd", "new zealand dollar", "new zealand dollars", "nz$")),
        Spec("KRW", "₩", listOf("krw", "korean won", "south korean won", "won")),
        Spec("MXN", "Mex$", listOf("mxn", "mexican peso", "mexican pesos")),
        Spec("BRL", "R$", listOf("brl", "brazilian real", "brazilian reals", "real", "reais", "r$")),
        Spec("ZAR", "R", listOf("zar", "south african rand", "rand")),
        Spec("RUB", "₽", listOf("rub", "russian ruble", "russian rouble", "ruble", "rouble", "rubles")),
        Spec("AED", "AED", listOf("aed", "dirham", "uae dirham", "emirati dirham")),
        Spec("SAR", "SAR", listOf("sar", "saudi riyal", "riyal")),
        Spec("TRY", "₺", listOf("try", "turkish lira", "lira")),
        Spec("THB", "฿", listOf("thb", "thai baht", "baht")),
        Spec("IDR", "Rp", listOf("idr", "indonesian rupiah", "rupiah")),
        Spec("MYR", "RM", listOf("myr", "malaysian ringgit", "ringgit")),
        Spec("PHP", "₱", listOf("php", "philippine peso", "philippine pesos")),
        Spec("VND", "₫", listOf("vnd", "vietnamese dong", "dong")),
        Spec("PLN", "zł", listOf("pln", "polish zloty", "zloty", "złoty")),
        Spec("SEK", "kr", listOf("sek", "swedish krona", "swedish kronor", "krona")),
        Spec("NOK", "kr", listOf("nok", "norwegian krone", "norwegian kroner", "krone")),
        Spec("DKK", "kr", listOf("dkk", "danish krone", "danish kroner")),
        Spec("ILS", "₪", listOf("ils", "israeli shekel", "shekel", "shekels")),
        Spec("EGP", "E£", listOf("egp", "egyptian pound")),
        Spec("PKR", "PKR", listOf("pkr", "pakistani rupee")),
        Spec("BDT", "৳", listOf("bdt", "bangladeshi taka", "taka")),
        Spec("LKR", "LKR", listOf("lkr", "sri lankan rupee")),
        Spec("UAH", "₴", listOf("uah", "ukrainian hryvnia", "hryvnia")),
        Spec("NGN", "₦", listOf("ngn", "nigerian naira", "naira")),
        Spec("KES", "KSh", listOf("kes", "kenyan shilling", "shilling")),
        Spec("ARS", "ARS", listOf("ars", "argentine peso")),
        Spec("CLP", "CLP", listOf("clp", "chilean peso")),
        Spec("COP", "COP", listOf("cop", "colombian peso")),
        // Cryptocurrencies (supported by primary API)
        Spec("BTC", "₿", listOf("btc", "bitcoin", "bitcoins")),
        Spec("ETH", "ETH", listOf("eth", "ether", "ethereum")),
        Spec("BNB", "BNB", listOf("bnb", "binance coin", "binance")),
        Spec("ADA", "₳", listOf("ada", "cardano")),
        Spec("SOL", "SOL", listOf("sol", "solana")),
        Spec("XRP", "XRP", listOf("xrp", "ripple")),
        Spec("DOT", "DOT", listOf("dot", "polkadot")),
        Spec("LTC", "Ł", listOf("ltc", "litecoin"))
    )

    private val byCode: Map<String, Spec> = all.associateBy { it.code }

    fun getByCode(code: String): Spec? = byCode[code.uppercase()]

    // Symbol-only mapping (resolves ambiguous symbols like $ and ¥ to most common)
    val ambiguousSymbolDefaults: Map<String, String> = mapOf(
        "$" to "USD",
        "¥" to "JPY",
        "kr" to "SEK",
        "R" to "ZAR"
    )

    // Locale-to-currency overrides for ambiguous symbols (e.g., $ in Mexico → MXN)
    fun resolveAmbiguousSymbol(symbol: String, localeCurrency: String?): String? {
        if (localeCurrency != null) {
            // Prefer local currency if its symbol matches
            val spec = byCode[localeCurrency]
            if (spec != null && spec.symbol.equals(symbol, ignoreCase = true)) return localeCurrency
        }
        return ambiguousSymbolDefaults[symbol]
    }
}
