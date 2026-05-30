package com.joyal.swyplauncher.util

// AUTO-GENERATED unit catalog. Affine model: baseValue = amount * factor + offset.
// Fuel uses fuelKind/fuelK (inverse); numeral uses radix. See UnitUtil for conversion.
object UnitData {

    enum class Category(val label: String) { LENGTH("Length"), AREA("Area"), DENSITY("Density"), VOLUME("Volume"), TIME("Time"), TEMPERATURE("Temperature"), SPEED("Speed"), MASS("Mass"), PRESSURE("Pressure"), ENERGY("Energy"), ANGLE("Angle"), SHOE_SIZE("Shoe Size"), POWER("Power"), FORCE("Force"), TORQUE("Torque"), FUEL("Fuel"), NUMERAL("Numeral"), DATA("Data") }

    data class Spec(
        val id: String,
        val category: Category,
        val symbol: String,
        val factor: Double,
        val offset: Double,
        val aliases: List<String>,
        val metric: Boolean,
        val imperial: Boolean,
        val radix: Int? = null,
        val fuelKind: String? = null,
        val fuelK: Double? = null,
        val displayStep: Double? = null  // round display to this increment (e.g. 0.5 shoe sizes)
    )

    data class CatMeta(
        val base: String,
        val metricPrimary: String,
        val imperialPrimary: String,
        val secondary: String,
        val special: String?,
        val popularPairs: Map<String, String>
    )

    val all: List<Spec> = listOf(
        Spec(id = "meter", category = Category.LENGTH, symbol = "m", factor = 1.0, offset = 0.0, aliases = listOf("meter", "meters", "metre", "metres", "m"), metric = true, imperial = false),
        Spec(id = "kilometer", category = Category.LENGTH, symbol = "km", factor = 1000.0, offset = 0.0, aliases = listOf("kilometer", "kilometers", "kilometre", "kilometres", "km", "kms"), metric = true, imperial = false),
        Spec(id = "centimeter", category = Category.LENGTH, symbol = "cm", factor = 0.01, offset = 0.0, aliases = listOf("centimeter", "centimeters", "centimetre", "centimetres", "cm"), metric = true, imperial = false),
        Spec(id = "millimeter", category = Category.LENGTH, symbol = "mm", factor = 0.001, offset = 0.0, aliases = listOf("millimeter", "millimeters", "millimetre", "millimetres", "mm"), metric = true, imperial = false),
        Spec(id = "micrometer", category = Category.LENGTH, symbol = "µm", factor = 1E-06, offset = 0.0, aliases = listOf("micrometer", "micrometers", "micrometre", "micrometres", "micron", "microns", "µm", "um"), metric = true, imperial = false),
        Spec(id = "nanometer", category = Category.LENGTH, symbol = "nm", factor = 1E-09, offset = 0.0, aliases = listOf("nanometer", "nanometers", "nanometre", "nanometres", "nm"), metric = true, imperial = false),
        Spec(id = "picometer", category = Category.LENGTH, symbol = "pm", factor = 1E-12, offset = 0.0, aliases = listOf("picometer", "picometers", "picometre", "picometres", "pm"), metric = true, imperial = false),
        Spec(id = "angstrom", category = Category.LENGTH, symbol = "Å", factor = 1E-10, offset = 0.0, aliases = listOf("angstrom", "angstroms", "ångström", "ångströms", "Å", "ang"), metric = true, imperial = false),
        Spec(id = "inch", category = Category.LENGTH, symbol = "in", factor = 0.0254, offset = 0.0, aliases = listOf("inch", "inches", "in", "\""), metric = false, imperial = true),
        Spec(id = "foot", category = Category.LENGTH, symbol = "ft", factor = 0.3048, offset = 0.0, aliases = listOf("foot", "feet", "ft", "'"), metric = false, imperial = true),
        Spec(id = "yard", category = Category.LENGTH, symbol = "yd", factor = 0.9144, offset = 0.0, aliases = listOf("yard", "yards", "yd", "yds"), metric = false, imperial = true),
        Spec(id = "mile", category = Category.LENGTH, symbol = "mi", factor = 1609.344, offset = 0.0, aliases = listOf("mile", "miles", "mi"), metric = false, imperial = true),
        Spec(id = "mil", category = Category.LENGTH, symbol = "mil", factor = 2.54E-05, offset = 0.0, aliases = listOf("mil", "mils", "thou", "thous"), metric = false, imperial = true),
        Spec(id = "nautical_mile", category = Category.LENGTH, symbol = "nmi", factor = 1852.0, offset = 0.0, aliases = listOf("nautical mile", "nautical miles", "nmi", "naut mi", "nm (nautical)"), metric = false, imperial = false),
        Spec(id = "astronomical_unit", category = Category.LENGTH, symbol = "AU", factor = 149597870700.0, offset = 0.0, aliases = listOf("astronomical unit", "astronomical units", "au", "ua"), metric = false, imperial = false),
        Spec(id = "light_year", category = Category.LENGTH, symbol = "ly", factor = 9460730472580800.0, offset = 0.0, aliases = listOf("light-year", "light-years", "light year", "light years", "lightyear", "lightyears", "ly"), metric = false, imperial = false),
        Spec(id = "parsec", category = Category.LENGTH, symbol = "pc", factor = 3.085677581491367E16, offset = 0.0, aliases = listOf("parsec", "parsecs", "pc"), metric = false, imperial = false),
        Spec(id = "square_meter", category = Category.AREA, symbol = "m²", factor = 1.0, offset = 0.0, aliases = listOf("square meter", "square meters", "square metre", "square metres", "sq m", "sqm", "m2", "m^2", "m²"), metric = true, imperial = false),
        Spec(id = "square_centimeter", category = Category.AREA, symbol = "cm²", factor = 0.0001, offset = 0.0, aliases = listOf("square centimeter", "square centimeters", "square centimetre", "square centimetres", "sq cm", "sqcm", "cm2", "cm^2", "cm²"), metric = true, imperial = false),
        Spec(id = "square_millimeter", category = Category.AREA, symbol = "mm²", factor = 1E-06, offset = 0.0, aliases = listOf("square millimeter", "square millimeters", "square millimetre", "square millimetres", "sq mm", "sqmm", "mm2", "mm^2", "mm²"), metric = true, imperial = false),
        Spec(id = "square_kilometer", category = Category.AREA, symbol = "km²", factor = 1000000.0, offset = 0.0, aliases = listOf("square kilometer", "square kilometers", "square kilometre", "square kilometres", "sq km", "sqkm", "km2", "km^2", "km²"), metric = true, imperial = false),
        Spec(id = "square_inch", category = Category.AREA, symbol = "in²", factor = 0.00064516, offset = 0.0, aliases = listOf("square inch", "square inches", "sq in", "sqin", "in2", "in^2", "in²"), metric = false, imperial = true),
        Spec(id = "square_foot", category = Category.AREA, symbol = "ft²", factor = 0.09290304, offset = 0.0, aliases = listOf("square foot", "square feet", "sq ft", "sqft", "ft2", "ft^2", "ft²"), metric = false, imperial = true),
        Spec(id = "square_yard", category = Category.AREA, symbol = "yd²", factor = 0.83612736, offset = 0.0, aliases = listOf("square yard", "square yards", "sq yd", "sqyd", "yd2", "yd^2", "yd²"), metric = false, imperial = true),
        Spec(id = "square_mile", category = Category.AREA, symbol = "mi²", factor = 2589988.110336, offset = 0.0, aliases = listOf("square mile", "square miles", "sq mi", "sqmi", "mi2", "mi^2", "mi²"), metric = false, imperial = true),
        Spec(id = "hectare", category = Category.AREA, symbol = "ha", factor = 10000.0, offset = 0.0, aliases = listOf("hectare", "hectares", "ha"), metric = true, imperial = false),
        Spec(id = "acre", category = Category.AREA, symbol = "ac", factor = 4046.8564224, offset = 0.0, aliases = listOf("acre", "acres", "ac"), metric = false, imperial = true),
        Spec(id = "are", category = Category.AREA, symbol = "a", factor = 100.0, offset = 0.0, aliases = listOf("are", "ares"), metric = true, imperial = false),
        Spec(id = "kilogram_per_cubic_meter", category = Category.DENSITY, symbol = "kg/m³", factor = 1.0, offset = 0.0, aliases = listOf("kilogram per cubic meter", "kilograms per cubic meter", "kilogram per cubic metre", "kilograms per cubic metre", "kg/m3", "kg/m^3", "kgm-3", "kg per m3"), metric = true, imperial = false),
        Spec(id = "gram_per_cubic_centimeter", category = Category.DENSITY, symbol = "g/cm³", factor = 1000.0, offset = 0.0, aliases = listOf("gram per cubic centimeter", "grams per cubic centimeter", "gram per cubic centimetre", "grams per cubic centimetre", "g/cm3", "g/cm^3", "g/cc", "gram per cc", "grams per cc", "gcm-3"), metric = true, imperial = false),
        Spec(id = "gram_per_milliliter", category = Category.DENSITY, symbol = "g/mL", factor = 1000.0, offset = 0.0, aliases = listOf("gram per milliliter", "grams per milliliter", "gram per millilitre", "grams per millilitre", "g/ml", "g per ml"), metric = true, imperial = false),
        Spec(id = "gram_per_liter", category = Category.DENSITY, symbol = "g/L", factor = 1.0, offset = 0.0, aliases = listOf("gram per liter", "grams per liter", "gram per litre", "grams per litre", "g/l", "g per l", "gpl"), metric = true, imperial = false),
        Spec(id = "gram_per_deciliter", category = Category.DENSITY, symbol = "g/dL", factor = 10.0, offset = 0.0, aliases = listOf("gram per deciliter", "grams per deciliter", "gram per decilitre", "grams per decilitre", "g/dl", "g per dl"), metric = true, imperial = false),
        Spec(id = "kilogram_per_liter", category = Category.DENSITY, symbol = "kg/L", factor = 1000.0, offset = 0.0, aliases = listOf("kilogram per liter", "kilograms per liter", "kilogram per litre", "kilograms per litre", "kg/l", "kg per l"), metric = true, imperial = false),
        Spec(id = "milligram_per_milliliter", category = Category.DENSITY, symbol = "mg/mL", factor = 1.0, offset = 0.0, aliases = listOf("milligram per milliliter", "milligrams per milliliter", "milligram per millilitre", "milligrams per millilitre", "mg/ml", "mg per ml"), metric = true, imperial = false),
        Spec(id = "milligram_per_cubic_centimeter", category = Category.DENSITY, symbol = "mg/cm³", factor = 1.0, offset = 0.0, aliases = listOf("milligram per cubic centimeter", "milligrams per cubic centimeter", "milligram per cubic centimetre", "milligrams per cubic centimetre", "mg/cm3", "mg/cm^3", "mg/cc"), metric = true, imperial = false),
        Spec(id = "milligram_per_liter", category = Category.DENSITY, symbol = "mg/L", factor = 0.001, offset = 0.0, aliases = listOf("milligram per liter", "milligrams per liter", "milligram per litre", "milligrams per litre", "mg/l", "mg per l"), metric = true, imperial = false),
        Spec(id = "milligram_per_deciliter", category = Category.DENSITY, symbol = "mg/dL", factor = 0.01, offset = 0.0, aliases = listOf("milligram per deciliter", "milligrams per deciliter", "milligram per decilitre", "milligrams per decilitre", "mg/dl", "mg per dl"), metric = true, imperial = false),
        Spec(id = "milligram_per_cubic_meter", category = Category.DENSITY, symbol = "mg/m³", factor = 1E-06, offset = 0.0, aliases = listOf("milligram per cubic meter", "milligrams per cubic meter", "milligram per cubic metre", "milligrams per cubic metre", "mg/m3", "mg/m^3"), metric = true, imperial = false),
        Spec(id = "microgram_per_milliliter", category = Category.DENSITY, symbol = "µg/mL", factor = 0.001, offset = 0.0, aliases = listOf("microgram per milliliter", "micrograms per milliliter", "microgram per millilitre", "micrograms per millilitre", "ug/ml", "mcg/ml", "µg/ml"), metric = true, imperial = false),
        Spec(id = "microgram_per_liter", category = Category.DENSITY, symbol = "µg/L", factor = 1E-06, offset = 0.0, aliases = listOf("microgram per liter", "micrograms per liter", "microgram per litre", "micrograms per litre", "ug/l", "mcg/l", "µg/l"), metric = true, imperial = false),
        Spec(id = "microgram_per_deciliter", category = Category.DENSITY, symbol = "µg/dL", factor = 1E-05, offset = 0.0, aliases = listOf("microgram per deciliter", "micrograms per deciliter", "microgram per decilitre", "micrograms per decilitre", "ug/dl", "mcg/dl", "µg/dl"), metric = true, imperial = false),
        Spec(id = "pound_per_cubic_foot", category = Category.DENSITY, symbol = "lb/ft³", factor = 16.018463373960138, offset = 0.0, aliases = listOf("pound per cubic foot", "pounds per cubic foot", "pounds per cubic feet", "lb/ft3", "lb/ft^3", "lbs/ft3", "pcf", "lb per ft3"), metric = false, imperial = true),
        Spec(id = "pound_per_cubic_inch", category = Category.DENSITY, symbol = "lb/in³", factor = 27679.904710203125, offset = 0.0, aliases = listOf("pound per cubic inch", "pounds per cubic inch", "pounds per cubic inches", "lb/in3", "lb/in^3", "lbs/in3", "lb per in3"), metric = false, imperial = true),
        Spec(id = "cubic_meter", category = Category.VOLUME, symbol = "m³", factor = 1.0, offset = 0.0, aliases = listOf("cubic meter", "cubic meters", "cubic metre", "cubic metres", "m3", "m^3", "cu m", "cubic m"), metric = true, imperial = false),
        Spec(id = "liter", category = Category.VOLUME, symbol = "L", factor = 0.001, offset = 0.0, aliases = listOf("liter", "liters", "litre", "litres", "ltr"), metric = true, imperial = false),
        Spec(id = "deciliter", category = Category.VOLUME, symbol = "dL", factor = 0.0001, offset = 0.0, aliases = listOf("deciliter", "deciliters", "decilitre", "decilitres", "dl"), metric = true, imperial = false),
        Spec(id = "centiliter", category = Category.VOLUME, symbol = "cL", factor = 1E-05, offset = 0.0, aliases = listOf("centiliter", "centiliters", "centilitre", "centilitres", "cl"), metric = true, imperial = false),
        Spec(id = "milliliter", category = Category.VOLUME, symbol = "mL", factor = 1E-06, offset = 0.0, aliases = listOf("milliliter", "milliliters", "millilitre", "millilitres", "ml"), metric = true, imperial = false),
        Spec(id = "microliter", category = Category.VOLUME, symbol = "µL", factor = 1E-09, offset = 0.0, aliases = listOf("microliter", "microliters", "microlitre", "microlitres", "ul", "µl", "mcl"), metric = true, imperial = false),
        Spec(id = "cubic_centimeter", category = Category.VOLUME, symbol = "cm³", factor = 1E-06, offset = 0.0, aliases = listOf("cubic centimeter", "cubic centimeters", "cubic centimetre", "cubic centimetres", "cc", "cm3", "cm^3", "ccm"), metric = true, imperial = false),
        Spec(id = "cubic_millimeter", category = Category.VOLUME, symbol = "mm³", factor = 1E-09, offset = 0.0, aliases = listOf("cubic millimeter", "cubic millimeters", "cubic millimetre", "cubic millimetres", "mm3", "mm^3"), metric = true, imperial = false),
        Spec(id = "cubic_foot", category = Category.VOLUME, symbol = "ft³", factor = 0.028316846592, offset = 0.0, aliases = listOf("cubic foot", "cubic feet", "ft3", "ft^3", "cu ft", "cuft", "cubic ft"), metric = false, imperial = true),
        Spec(id = "cubic_inch", category = Category.VOLUME, symbol = "in³", factor = 1.6387064E-05, offset = 0.0, aliases = listOf("cubic inch", "cubic inches", "in3", "in^3", "cu in", "cuin", "cubic in"), metric = false, imperial = true),
        Spec(id = "us_gallon", category = Category.VOLUME, symbol = "gal", factor = 0.003785411784, offset = 0.0, aliases = listOf("us gallon", "us gallons", "gallon", "gallons", "gal", "us gal", "usgal", "gallon us"), metric = false, imperial = true),
        Spec(id = "imperial_gallon", category = Category.VOLUME, symbol = "gal (imp)", factor = 0.00454609, offset = 0.0, aliases = listOf("imperial gallon", "imperial gallons", "imp gallon", "imp gallons", "uk gallon", "uk gallons", "imp gal", "imperial gal", "gallon imperial"), metric = false, imperial = true),
        Spec(id = "us_quart", category = Category.VOLUME, symbol = "qt", factor = 0.000946352946, offset = 0.0, aliases = listOf("us quart", "us quarts", "quart", "quarts", "qt", "us qt", "usqt"), metric = false, imperial = true),
        Spec(id = "us_pint", category = Category.VOLUME, symbol = "pt", factor = 0.000473176473, offset = 0.0, aliases = listOf("us pint", "us pints", "pint", "pints", "pt", "us pt", "uspt"), metric = false, imperial = true),
        Spec(id = "imperial_pint", category = Category.VOLUME, symbol = "pt (imp)", factor = 0.00056826125, offset = 0.0, aliases = listOf("imperial pint", "imperial pints", "imp pint", "imp pints", "uk pint", "uk pints", "imp pt", "imperial pt"), metric = false, imperial = true),
        Spec(id = "us_fluid_ounce", category = Category.VOLUME, symbol = "fl oz", factor = 2.95735295625E-05, offset = 0.0, aliases = listOf("us fluid ounce", "us fluid ounces", "fluid ounce", "fluid ounces", "fl oz", "us fl oz", "floz", "us floz", "fluid oz"), metric = false, imperial = true),
        Spec(id = "imperial_fluid_ounce", category = Category.VOLUME, symbol = "fl oz (imp)", factor = 2.84130625E-05, offset = 0.0, aliases = listOf("imperial fluid ounce", "imperial fluid ounces", "imp fluid ounce", "imp fluid ounces", "uk fluid ounce", "uk fluid ounces", "imp fl oz", "imperial fl oz", "imp floz"), metric = false, imperial = true),
        Spec(id = "us_gill", category = Category.VOLUME, symbol = "gi", factor = 0.00011829411825, offset = 0.0, aliases = listOf("us gill", "us gills", "gill", "gills", "gi", "us gi"), metric = false, imperial = true),
        Spec(id = "imperial_gill", category = Category.VOLUME, symbol = "gi (imp)", factor = 0.0001420653125, offset = 0.0, aliases = listOf("imperial gill", "imperial gills", "imp gill", "imp gills", "uk gill", "uk gills", "imp gi", "imperial gi"), metric = false, imperial = true),
        Spec(id = "us_tablespoon", category = Category.VOLUME, symbol = "tbsp", factor = 1.478676478125E-05, offset = 0.0, aliases = listOf("us tablespoon", "us tablespoons", "tablespoon", "tablespoons", "tbsp", "tbs", "us tbsp", "tablespoon us"), metric = false, imperial = true),
        Spec(id = "australian_tablespoon", category = Category.VOLUME, symbol = "tbsp (AU)", factor = 2E-05, offset = 0.0, aliases = listOf("australian tablespoon", "australian tablespoons", "aussie tablespoon", "aussie tablespoons", "au tablespoon", "au tbsp", "metric tablespoon", "metric tablespoons"), metric = true, imperial = false),
        Spec(id = "cup", category = Category.VOLUME, symbol = "cup", factor = 0.0002365882365, offset = 0.0, aliases = listOf("cup", "cups", "us cup", "us cups"), metric = false, imperial = true),
        Spec(id = "second", category = Category.TIME, symbol = "s", factor = 1.0, offset = 0.0, aliases = listOf("second", "seconds", "sec", "secs"), metric = true, imperial = true),
        Spec(id = "decisecond", category = Category.TIME, symbol = "ds", factor = 0.1, offset = 0.0, aliases = listOf("decisecond", "deciseconds", "ds"), metric = true, imperial = false),
        Spec(id = "centisecond", category = Category.TIME, symbol = "cs", factor = 0.01, offset = 0.0, aliases = listOf("centisecond", "centiseconds", "cs"), metric = true, imperial = false),
        Spec(id = "millisecond", category = Category.TIME, symbol = "ms", factor = 0.001, offset = 0.0, aliases = listOf("millisecond", "milliseconds", "millisec", "msec", "ms"), metric = true, imperial = false),
        Spec(id = "microsecond", category = Category.TIME, symbol = "µs", factor = 1E-06, offset = 0.0, aliases = listOf("microsecond", "microseconds", "microsec", "µs", "μs"), metric = true, imperial = false),
        Spec(id = "nanosecond", category = Category.TIME, symbol = "ns", factor = 1E-09, offset = 0.0, aliases = listOf("nanosecond", "nanoseconds", "nanosec", "ns"), metric = true, imperial = false),
        Spec(id = "minute", category = Category.TIME, symbol = "min", factor = 60.0, offset = 0.0, aliases = listOf("minute", "minutes", "min", "mins"), metric = true, imperial = true),
        Spec(id = "hour", category = Category.TIME, symbol = "h", factor = 3600.0, offset = 0.0, aliases = listOf("hour", "hours", "hr", "hrs"), metric = true, imperial = true),
        Spec(id = "day", category = Category.TIME, symbol = "d", factor = 86400.0, offset = 0.0, aliases = listOf("day", "days"), metric = true, imperial = true),
        Spec(id = "week", category = Category.TIME, symbol = "wk", factor = 604800.0, offset = 0.0, aliases = listOf("week", "weeks", "wk", "wks"), metric = true, imperial = true),
        Spec(id = "year", category = Category.TIME, symbol = "yr", factor = 31536000.0, offset = 0.0, aliases = listOf("year", "years", "yr", "yrs"), metric = true, imperial = true),
        Spec(id = "lustrum", category = Category.TIME, symbol = "lustrum", factor = 157680000.0, offset = 0.0, aliases = listOf("lustrum", "lustra", "lustrums"), metric = true, imperial = true),
        Spec(id = "decade", category = Category.TIME, symbol = "dec", factor = 315360000.0, offset = 0.0, aliases = listOf("decade", "decades"), metric = true, imperial = true),
        Spec(id = "century", category = Category.TIME, symbol = "cent", factor = 3153600000.0, offset = 0.0, aliases = listOf("century", "centuries"), metric = true, imperial = true),
        Spec(id = "millennium", category = Category.TIME, symbol = "ky", factor = 31536000000.0, offset = 0.0, aliases = listOf("millennium", "millennia", "millenniums", "millenium"), metric = true, imperial = true),
        Spec(id = "celsius", category = Category.TEMPERATURE, symbol = "°C", factor = 1.0, offset = 0.0, aliases = listOf("celsius", "centigrade", "degree celsius", "degrees celsius", "°c", "deg c"), metric = true, imperial = false),
        Spec(id = "fahrenheit", category = Category.TEMPERATURE, symbol = "°F", factor = 0.5555555555555556, offset = -17.77777777777778, aliases = listOf("fahrenheit", "degree fahrenheit", "degrees fahrenheit", "°f", "deg f"), metric = false, imperial = true),
        Spec(id = "kelvin", category = Category.TEMPERATURE, symbol = "K", factor = 1.0, offset = -273.15, aliases = listOf("kelvin", "kelvins", "degree kelvin", "degrees kelvin"), metric = true, imperial = false),
        Spec(id = "reaumur", category = Category.TEMPERATURE, symbol = "°Ré", factor = 1.25, offset = 0.0, aliases = listOf("reaumur", "réaumur", "degree reaumur", "degrees reaumur", "°ré", "°re", "réau"), metric = false, imperial = false),
        Spec(id = "romer", category = Category.TEMPERATURE, symbol = "°Rø", factor = 1.9047619047619047, offset = -14.285714285714286, aliases = listOf("romer", "rømer", "roemer", "degree romer", "degrees romer", "°rø", "°ro"), metric = false, imperial = false),
        Spec(id = "delisle", category = Category.TEMPERATURE, symbol = "°De", factor = -0.6666666666666666, offset = 100.0, aliases = listOf("delisle", "degree delisle", "degrees delisle", "°de"), metric = false, imperial = false),
        Spec(id = "rankine", category = Category.TEMPERATURE, symbol = "°R", factor = 0.5555555555555556, offset = -273.15, aliases = listOf("rankine", "degree rankine", "degrees rankine", "°ra", "°r"), metric = false, imperial = true),
        Spec(id = "meter_per_second", category = Category.SPEED, symbol = "m/s", factor = 1.0, offset = 0.0, aliases = listOf("meter per second", "meters per second", "metre per second", "metres per second", "m/s", "mps", "m s-1"), metric = true, imperial = false),
        Spec(id = "kilometer_per_hour", category = Category.SPEED, symbol = "km/h", factor = 0.2777777777777778, offset = 0.0, aliases = listOf("kilometer per hour", "kilometers per hour", "kilometre per hour", "kilometres per hour", "km/h", "kmh", "kph", "km/hr", "km h-1"), metric = true, imperial = false),
        Spec(id = "mile_per_hour", category = Category.SPEED, symbol = "mph", factor = 0.44704, offset = 0.0, aliases = listOf("mile per hour", "miles per hour", "mph", "mi/h", "mi/hr"), metric = false, imperial = true),
        Spec(id = "knot", category = Category.SPEED, symbol = "kn", factor = 0.5144444444444445, offset = 0.0, aliases = listOf("knot", "knots", "kn", "kt", "kts", "nautical mile per hour", "nautical miles per hour", "nmi/h"), metric = false, imperial = false),
        Spec(id = "foot_per_second", category = Category.SPEED, symbol = "ft/s", factor = 0.3048, offset = 0.0, aliases = listOf("foot per second", "feet per second", "ft/s", "fps", "ft/sec", "ft s-1"), metric = false, imperial = true),
        Spec(id = "kilogram", category = Category.MASS, symbol = "kg", factor = 1.0, offset = 0.0, aliases = listOf("kilogram", "kilograms", "kilo", "kilos", "kg", "kgs"), metric = true, imperial = false),
        Spec(id = "gram", category = Category.MASS, symbol = "g", factor = 0.001, offset = 0.0, aliases = listOf("gram", "grams", "gramme", "grammes", "g", "gm", "gms"), metric = true, imperial = false),
        Spec(id = "milligram", category = Category.MASS, symbol = "mg", factor = 1E-06, offset = 0.0, aliases = listOf("milligram", "milligrams", "milligramme", "milligrammes", "mg"), metric = true, imperial = false),
        Spec(id = "centigram", category = Category.MASS, symbol = "cg", factor = 1E-05, offset = 0.0, aliases = listOf("centigram", "centigrams", "centigramme", "centigrammes", "cg"), metric = true, imperial = false),
        Spec(id = "hectogram", category = Category.MASS, symbol = "hg", factor = 0.1, offset = 0.0, aliases = listOf("hectogram", "hectograms", "hectogramme", "hectogrammes", "ettogram", "ettograms", "ettogrammo", "ettogrammi", "hg"), metric = true, imperial = false),
        Spec(id = "quintal", category = Category.MASS, symbol = "q", factor = 100.0, offset = 0.0, aliases = listOf("quintal", "quintals", "quintale", "quintali", "metric quintal", "q"), metric = true, imperial = false),
        Spec(id = "tonne", category = Category.MASS, symbol = "t", factor = 1000.0, offset = 0.0, aliases = listOf("tonne", "tonnes", "metric ton", "metric tons", "metric tonne", "metric tonnes", "megagram", "megagrams", "t"), metric = true, imperial = false),
        Spec(id = "pound", category = Category.MASS, symbol = "lb", factor = 0.45359237, offset = 0.0, aliases = listOf("pound", "pounds", "lb", "lbs", "lbm", "pound-mass"), metric = false, imperial = true),
        Spec(id = "ounce", category = Category.MASS, symbol = "oz", factor = 0.028349523125, offset = 0.0, aliases = listOf("ounce", "ounces", "oz", "avoirdupois ounce"), metric = false, imperial = true),
        Spec(id = "stone", category = Category.MASS, symbol = "st", factor = 6.35029318, offset = 0.0, aliases = listOf("stone", "stones", "st"), metric = false, imperial = true),
        Spec(id = "carat", category = Category.MASS, symbol = "ct", factor = 0.0002, offset = 0.0, aliases = listOf("carat", "carats", "metric carat", "metric carats", "ct"), metric = false, imperial = false),
        Spec(id = "pennyweight", category = Category.MASS, symbol = "dwt", factor = 0.00155517384, offset = 0.0, aliases = listOf("pennyweight", "pennyweights", "dwt", "pwt"), metric = false, imperial = false),
        Spec(id = "troy_ounce", category = Category.MASS, symbol = "ozt", factor = 0.0311034768, offset = 0.0, aliases = listOf("troy ounce", "troy ounces", "ozt", "oz t", "oz troy", "ounce troy"), metric = false, imperial = false),
        Spec(id = "atomic_mass_unit", category = Category.MASS, symbol = "u", factor = 1.6605390666E-27, offset = 0.0, aliases = listOf("unified atomic mass unit", "atomic mass unit", "atomic mass units", "dalton", "daltons", "amu", "u", "da"), metric = false, imperial = false),
        Spec(id = "pascal", category = Category.PRESSURE, symbol = "Pa", factor = 1.0, offset = 0.0, aliases = listOf("pascal", "pascals", "pa", "n/m2", "n/m^2", "newton per square meter"), metric = true, imperial = false),
        Spec(id = "hectopascal", category = Category.PRESSURE, symbol = "hPa", factor = 100.0, offset = 0.0, aliases = listOf("hectopascal", "hectopascals", "hpa"), metric = true, imperial = false),
        Spec(id = "kilopascal", category = Category.PRESSURE, symbol = "kPa", factor = 1000.0, offset = 0.0, aliases = listOf("kilopascal", "kilopascals", "kpa"), metric = true, imperial = false),
        Spec(id = "megapascal", category = Category.PRESSURE, symbol = "MPa", factor = 1000000.0, offset = 0.0, aliases = listOf("megapascal", "megapascals", "mpa"), metric = true, imperial = false),
        Spec(id = "gigapascal", category = Category.PRESSURE, symbol = "GPa", factor = 1000000000.0, offset = 0.0, aliases = listOf("gigapascal", "gigapascals", "gpa"), metric = true, imperial = false),
        Spec(id = "bar", category = Category.PRESSURE, symbol = "bar", factor = 100000.0, offset = 0.0, aliases = listOf("bar", "bars"), metric = true, imperial = false),
        Spec(id = "millibar", category = Category.PRESSURE, symbol = "mbar", factor = 100.0, offset = 0.0, aliases = listOf("millibar", "millibars", "mbar", "mb"), metric = true, imperial = false),
        Spec(id = "atmosphere", category = Category.PRESSURE, symbol = "atm", factor = 101325.0, offset = 0.0, aliases = listOf("atmosphere", "atmospheres", "atm", "standard atmosphere", "std atm"), metric = false, imperial = false),
        Spec(id = "psi", category = Category.PRESSURE, symbol = "psi", factor = 6894.757293168361, offset = 0.0, aliases = listOf("psi", "pound per square inch", "pounds per square inch", "lbf/in2", "lbf/in^2", "lb/in2", "pound-force per square inch"), metric = false, imperial = true),
        Spec(id = "ksi", category = Category.PRESSURE, symbol = "ksi", factor = 6894757.293168361, offset = 0.0, aliases = listOf("ksi", "kilopound per square inch", "kilopounds per square inch", "kip per square inch", "kips per square inch", "kpsi"), metric = false, imperial = true),
        Spec(id = "torr", category = Category.PRESSURE, symbol = "Torr", factor = 133.32236842105263, offset = 0.0, aliases = listOf("torr", "mmhg", "mm hg", "millimeter of mercury", "millimeters of mercury", "millimetre of mercury", "mm of mercury"), metric = true, imperial = false),
        Spec(id = "inches_of_mercury", category = Category.PRESSURE, symbol = "inHg", factor = 3386.389, offset = 0.0, aliases = listOf("inhg", "in hg", "inch of mercury", "inches of mercury", "inch hg", "inches hg"), metric = false, imperial = true),
        Spec(id = "joule", category = Category.ENERGY, symbol = "J", factor = 1.0, offset = 0.0, aliases = listOf("joule", "joules"), metric = true, imperial = false),
        Spec(id = "kilojoule", category = Category.ENERGY, symbol = "kJ", factor = 1000.0, offset = 0.0, aliases = listOf("kilojoule", "kilojoules", "kj"), metric = true, imperial = false),
        Spec(id = "calorie", category = Category.ENERGY, symbol = "cal", factor = 4.184, offset = 0.0, aliases = listOf("calorie", "calories", "cal", "small calorie", "gram calorie"), metric = true, imperial = false),
        Spec(id = "kilocalorie", category = Category.ENERGY, symbol = "kcal", factor = 4184.0, offset = 0.0, aliases = listOf("kilocalorie", "kilocalories", "kcal", "food calorie", "large calorie", "cal (food)", "dietary calorie"), metric = true, imperial = false),
        Spec(id = "kilowatt_hour", category = Category.ENERGY, symbol = "kWh", factor = 3600000.0, offset = 0.0, aliases = listOf("kilowatt hour", "kilowatt hours", "kilowatt-hour", "kwh", "kw h", "kw-h"), metric = true, imperial = false),
        Spec(id = "electron_volt", category = Category.ENERGY, symbol = "eV", factor = 1.602176634E-19, offset = 0.0, aliases = listOf("electron volt", "electron volts", "electronvolt", "electronvolts", "ev"), metric = false, imperial = false),
        Spec(id = "foot_pound", category = Category.ENERGY, symbol = "ft·lb", factor = 1.3558179483, offset = 0.0, aliases = listOf("foot pound", "foot pounds", "foot-pound", "foot-pounds", "footpound", "ft lb", "ft-lb", "ft·lb", "ftlb", "ft lbf", "foot-pound force"), metric = false, imperial = true),
        Spec(id = "radian", category = Category.ANGLE, symbol = "rad", factor = 1.0, offset = 0.0, aliases = listOf("radian", "radians", "rad", "rads"), metric = false, imperial = false),
        Spec(id = "degree", category = Category.ANGLE, symbol = "°", factor = 0.017453292519943295, offset = 0.0, aliases = listOf("degree", "degrees", "deg", "degs", "°", "arcdegree", "arcdegrees"), metric = false, imperial = false),
        Spec(id = "arcminute", category = Category.ANGLE, symbol = "′", factor = 0.0002908882086657216, offset = 0.0, aliases = listOf("arcminute", "arcminutes", "arcmin", "arcmins", "minute of arc", "minutes of arc", "moa", "′"), metric = false, imperial = false),
        Spec(id = "arcsecond", category = Category.ANGLE, symbol = "″", factor = 4.84813681109536E-06, offset = 0.0, aliases = listOf("arcsecond", "arcseconds", "arcsec", "arcsecs", "second of arc", "seconds of arc", "″"), metric = false, imperial = false),
        Spec(id = "eu", category = Category.SHOE_SIZE, symbol = "EU", factor = 6.666667, offset = -15.0, aliases = listOf("eu", "eu size", "european", "european size", "paris point", "continental"), metric = true, imperial = false, displayStep = 0.5),
        Spec(id = "us_men", category = Category.SHOE_SIZE, symbol = "US (M)", factor = 8.466667, offset = 186.266667, aliases = listOf("us", "usa", "us men", "us mens", "mens us", "men us", "american", "us size", "us m", "us (m)"), metric = false, imperial = true, displayStep = 0.5),
        Spec(id = "us_women", category = Category.SHOE_SIZE, symbol = "US (W)", factor = 8.466667, offset = 173.566667, aliases = listOf("us women", "us womens", "womens us", "women us", "ladies us", "us w", "us (w)"), metric = false, imperial = true, displayStep = 0.5),
        Spec(id = "uk_men", category = Category.SHOE_SIZE, symbol = "UK (M)", factor = 8.466667, offset = 190.5, aliases = listOf("uk", "uk men", "uk mens", "mens uk", "men uk", "british", "uk size", "uk m", "uk (m)"), metric = false, imperial = true, displayStep = 0.5),
        Spec(id = "uk_women", category = Category.SHOE_SIZE, symbol = "UK (W)", factor = 8.466667, offset = 190.5, aliases = listOf("uk women", "uk womens", "womens uk", "women uk", "ladies uk", "uk w", "uk (w)"), metric = false, imperial = true, displayStep = 0.5),
        Spec(id = "japan", category = Category.SHOE_SIZE, symbol = "JP", factor = 10.0, offset = 0.0, aliases = listOf("japan", "japanese", "jp", "japan size", "japanese size", "jis"), metric = true, imperial = false, displayStep = 0.5),
        Spec(id = "korea", category = Category.SHOE_SIZE, symbol = "KR", factor = 1.0, offset = 0.0, aliases = listOf("korea", "korean", "kr", "korea size", "korean size", "mondopoint"), metric = true, imperial = false, displayStep = 5.0),
        Spec(id = "china", category = Category.SHOE_SIZE, symbol = "CN", factor = 1.0, offset = 0.0, aliases = listOf("china", "chinese", "cn", "china size", "chinese size", "prc"), metric = true, imperial = false, displayStep = 5.0),
        Spec(id = "mexico", category = Category.SHOE_SIZE, symbol = "MX", factor = 10.0, offset = 0.0, aliases = listOf("mexico", "mexican", "mx", "mexico size", "mexican size", "mex"), metric = true, imperial = false, displayStep = 0.5),
        Spec(id = "shoe_mm", category = Category.SHOE_SIZE, symbol = "mm", factor = 1.0, offset = 0.0, aliases = listOf("shoe mm", "shoe millimeters", "shoe millimetres"), metric = true, imperial = false, displayStep = 1.0),
        Spec(id = "shoe_cm", category = Category.SHOE_SIZE, symbol = "cm", factor = 10.0, offset = 0.0, aliases = listOf("shoe cm", "shoe centimeters", "shoe centimetres"), metric = true, imperial = false, displayStep = 0.1),
        Spec(id = "shoe_inches", category = Category.SHOE_SIZE, symbol = "in", factor = 25.4, offset = 0.0, aliases = listOf("shoe in", "shoe inch", "shoe inches"), metric = false, imperial = true, displayStep = 0.1),
        Spec(id = "watt", category = Category.POWER, symbol = "W", factor = 1.0, offset = 0.0, aliases = listOf("watt", "watts"), metric = true, imperial = false),
        Spec(id = "milliwatt", category = Category.POWER, symbol = "mW", factor = 0.001, offset = 0.0, aliases = listOf("milliwatt", "milliwatts", "mw"), metric = true, imperial = false),
        Spec(id = "kilowatt", category = Category.POWER, symbol = "kW", factor = 1000.0, offset = 0.0, aliases = listOf("kilowatt", "kilowatts", "kw"), metric = true, imperial = false),
        Spec(id = "megawatt", category = Category.POWER, symbol = "MW", factor = 1000000.0, offset = 0.0, aliases = listOf("megawatt", "megawatts"), metric = true, imperial = false),
        Spec(id = "gigawatt", category = Category.POWER, symbol = "GW", factor = 1000000000.0, offset = 0.0, aliases = listOf("gigawatt", "gigawatts", "gw"), metric = true, imperial = false),
        Spec(id = "horsepower_metric", category = Category.POWER, symbol = "PS", factor = 735.49875, offset = 0.0, aliases = listOf("metric horsepower", "european horsepower", "horsepower metric", "ps", "cv", "pferdestarke"), metric = true, imperial = false),
        Spec(id = "horsepower_mechanical", category = Category.POWER, symbol = "hp", factor = 745.69987158, offset = 0.0, aliases = listOf("horsepower", "mechanical horsepower", "imperial horsepower", "hp", "bhp"), metric = false, imperial = true),
        Spec(id = "newton", category = Category.FORCE, symbol = "N", factor = 1.0, offset = 0.0, aliases = listOf("newton", "newtons"), metric = true, imperial = false),
        Spec(id = "dyne", category = Category.FORCE, symbol = "dyn", factor = 1E-05, offset = 0.0, aliases = listOf("dyne", "dynes", "dyn"), metric = true, imperial = false),
        Spec(id = "pound_force", category = Category.FORCE, symbol = "lbf", factor = 4.4482216152605, offset = 0.0, aliases = listOf("pound-force", "pound force", "pounds-force", "pounds force", "lbf", "lb-f", "pound of force"), metric = false, imperial = true),
        Spec(id = "kilogram_force", category = Category.FORCE, symbol = "kgf", factor = 9.80665, offset = 0.0, aliases = listOf("kilogram-force", "kilogram force", "kilograms-force", "kilograms force", "kgf", "kp", "kilopond", "kiloponds"), metric = true, imperial = false),
        Spec(id = "poundal", category = Category.FORCE, symbol = "pdl", factor = 0.138254954376, offset = 0.0, aliases = listOf("poundal", "poundals", "pdl"), metric = false, imperial = true),
        Spec(id = "newton_meter", category = Category.TORQUE, symbol = "N·m", factor = 1.0, offset = 0.0, aliases = listOf("newton meter", "newton metre", "newton meters", "newton metres", "newton-meter", "newton-metre", "n m", "n.m", "n*m", "n·m"), metric = true, imperial = false),
        Spec(id = "dyne_meter", category = Category.TORQUE, symbol = "dyn·m", factor = 1E-05, offset = 0.0, aliases = listOf("dyne meter", "dyne metre", "dyne meters", "dyne metres", "dyne-meter", "dyne-metre", "dyn m", "dyn.m", "dyn*m", "dyn·m", "dynm"), metric = true, imperial = false),
        Spec(id = "pound_force_foot", category = Category.TORQUE, symbol = "lbf·ft", factor = 1.3558179483314003, offset = 0.0, aliases = listOf("pound-force foot", "pound-force feet", "pound force foot", "pound force feet", "pound-foot", "pound foot", "foot-pound force", "lbf ft", "lbf.ft", "lbf*ft", "lbf·ft", "lbfft", "lb-ft", "lbft", "ft-lbf", "ft lbf", "ft·lbf"), metric = false, imperial = true),
        Spec(id = "kilogram_force_meter", category = Category.TORQUE, symbol = "kgf·m", factor = 9.80665, offset = 0.0, aliases = listOf("kilogram-force meter", "kilogram-force metre", "kilogram force meter", "kilogram force metre", "kilopond meter", "kilopond metre", "kgf m", "kgf.m", "kgf*m", "kgf·m", "kgfm", "kgm", "kp·m", "kp m", "kpm"), metric = true, imperial = false),
        Spec(id = "poundal_meter", category = Category.TORQUE, symbol = "pdl·m", factor = 0.138254954376, offset = 0.0, aliases = listOf("poundal meter", "poundal metre", "poundal meters", "poundal metres", "poundal-meter", "poundal-metre", "pdl m", "pdl.m", "pdl*m", "pdl·m", "pdlm"), metric = false, imperial = true),
        Spec(id = "l_per_100km", category = Category.FUEL, symbol = "L/100km", factor = 1.0, offset = 0.0, aliases = listOf("l/100km", "liters per 100 km", "liter per 100 km", "litres per 100 km", "litre per 100 km", "l per 100km", "l per 100 km", "liters per hundred km", "l/100 km", "lp100k"), metric = true, imperial = false, fuelKind = "vol_per_dist", fuelK = 1.0),
        Spec(id = "km_per_l", category = Category.FUEL, symbol = "km/L", factor = 1.0, offset = 0.0, aliases = listOf("km/l", "kilometers per liter", "kilometer per liter", "kilometres per litre", "kilometre per litre", "km per liter", "km per litre", "kmpl", "kpl", "km/litre", "km/liter"), metric = true, imperial = false, fuelKind = "dist_per_vol", fuelK = 100.0),
        Spec(id = "mpg_us", category = Category.FUEL, symbol = "mpg (US)", factor = 1.0, offset = 0.0, aliases = listOf("mpg us", "mpg (us)", "miles per us gallon", "miles per gallon us", "mile per us gallon", "us mpg", "mpgus", "miles per gallon (us)"), metric = false, imperial = true, fuelKind = "dist_per_vol", fuelK = 235.214583),
        Spec(id = "mpg_imp", category = Category.FUEL, symbol = "mpg (imp)", factor = 1.0, offset = 0.0, aliases = listOf("mpg imp", "mpg (imp)", "miles per imperial gallon", "miles per gallon imperial", "mile per imperial gallon", "imperial mpg", "uk mpg", "mpg uk", "mpgimp", "miles per gallon (imperial)", "mpg imperial"), metric = false, imperial = true, fuelKind = "dist_per_vol", fuelK = 282.480936),
        Spec(id = "decimal", category = Category.NUMERAL, symbol = "dec", factor = 1.0, offset = 0.0, aliases = listOf("decimal", "decimals", "base 10", "base10", "base-10", "dec", "denary"), metric = false, imperial = false, radix = 10),
        Spec(id = "hexadecimal", category = Category.NUMERAL, symbol = "hex", factor = 1.0, offset = 0.0, aliases = listOf("hexadecimal", "hex", "base 16", "base16", "base-16", "0x"), metric = false, imperial = false, radix = 16),
        Spec(id = "octal", category = Category.NUMERAL, symbol = "oct", factor = 1.0, offset = 0.0, aliases = listOf("octal", "oct", "base 8", "base8", "base-8", "0o"), metric = false, imperial = false, radix = 8),
        Spec(id = "binary", category = Category.NUMERAL, symbol = "bin", factor = 1.0, offset = 0.0, aliases = listOf("binary", "bin", "base 2", "base2", "base-2", "0b"), metric = false, imperial = false, radix = 2),
        Spec(id = "bit", category = Category.DATA, symbol = "bit", factor = 1.0, offset = 0.0, aliases = listOf("bit", "bits"), metric = false, imperial = false),
        Spec(id = "nibble", category = Category.DATA, symbol = "nibble", factor = 4.0, offset = 0.0, aliases = listOf("nibble", "nibbles", "nybble", "nybbles", "nyble"), metric = false, imperial = false),
        Spec(id = "kilobit", category = Category.DATA, symbol = "kbit", factor = 1000.0, offset = 0.0, aliases = listOf("kilobit", "kilobits", "kbit", "kbits", "kbps"), metric = true, imperial = false),
        Spec(id = "megabit", category = Category.DATA, symbol = "Mbit", factor = 1000000.0, offset = 0.0, aliases = listOf("megabit", "megabits", "mbit", "mbits", "mbps"), metric = true, imperial = false),
        Spec(id = "gigabit", category = Category.DATA, symbol = "Gbit", factor = 1000000000.0, offset = 0.0, aliases = listOf("gigabit", "gigabits", "gbit", "gbits", "gbps"), metric = true, imperial = false),
        Spec(id = "terabit", category = Category.DATA, symbol = "Tbit", factor = 1000000000000.0, offset = 0.0, aliases = listOf("terabit", "terabits", "tbit", "tbits", "tbps"), metric = true, imperial = false),
        Spec(id = "petabit", category = Category.DATA, symbol = "Pbit", factor = 1000000000000000.0, offset = 0.0, aliases = listOf("petabit", "petabits", "pbit", "pbits"), metric = true, imperial = false),
        Spec(id = "exabit", category = Category.DATA, symbol = "Ebit", factor = 1E18, offset = 0.0, aliases = listOf("exabit", "exabits", "ebit", "ebits"), metric = true, imperial = false),
        Spec(id = "kibibit", category = Category.DATA, symbol = "Kibit", factor = 1024.0, offset = 0.0, aliases = listOf("kibibit", "kibibits", "kibit", "kibits"), metric = false, imperial = false),
        Spec(id = "mebibit", category = Category.DATA, symbol = "Mibit", factor = 1048576.0, offset = 0.0, aliases = listOf("mebibit", "mebibits", "mibit", "mibits"), metric = false, imperial = false),
        Spec(id = "gibibit", category = Category.DATA, symbol = "Gibit", factor = 1073741824.0, offset = 0.0, aliases = listOf("gibibit", "gibibits", "gibit", "gibits"), metric = false, imperial = false),
        Spec(id = "tebibit", category = Category.DATA, symbol = "Tibit", factor = 1099511627776.0, offset = 0.0, aliases = listOf("tebibit", "tebibits", "tibit", "tibits"), metric = false, imperial = false),
        Spec(id = "pebibit", category = Category.DATA, symbol = "Pibit", factor = 1125899906842624.0, offset = 0.0, aliases = listOf("pebibit", "pebibits", "pibit", "pibits"), metric = false, imperial = false),
        Spec(id = "exbibit", category = Category.DATA, symbol = "Eibit", factor = 1.152921504606847E18, offset = 0.0, aliases = listOf("exbibit", "exbibits", "eibit", "eibits"), metric = false, imperial = false),
        Spec(id = "byte", category = Category.DATA, symbol = "B", factor = 8.0, offset = 0.0, aliases = listOf("byte", "bytes", "octet", "octets"), metric = false, imperial = false),
        Spec(id = "kilobyte", category = Category.DATA, symbol = "kB", factor = 8000.0, offset = 0.0, aliases = listOf("kilobyte", "kilobytes", "kbyte", "kbytes", "kb", "kbs"), metric = true, imperial = false),
        Spec(id = "megabyte", category = Category.DATA, symbol = "MB", factor = 8000000.0, offset = 0.0, aliases = listOf("megabyte", "megabytes", "mbyte", "mbytes", "mb", "megs", "meg"), metric = true, imperial = false),
        Spec(id = "gigabyte", category = Category.DATA, symbol = "GB", factor = 8000000000.0, offset = 0.0, aliases = listOf("gigabyte", "gigabytes", "gbyte", "gbytes", "gb", "gigs", "gig"), metric = true, imperial = false),
        Spec(id = "terabyte", category = Category.DATA, symbol = "TB", factor = 8000000000000.0, offset = 0.0, aliases = listOf("terabyte", "terabytes", "tbyte", "tbytes", "tb"), metric = true, imperial = false),
        Spec(id = "petabyte", category = Category.DATA, symbol = "PB", factor = 8000000000000000.0, offset = 0.0, aliases = listOf("petabyte", "petabytes", "pbyte", "pbytes", "pb"), metric = true, imperial = false),
        Spec(id = "exabyte", category = Category.DATA, symbol = "EB", factor = 8E18, offset = 0.0, aliases = listOf("exabyte", "exabytes", "ebyte", "ebytes", "eb"), metric = true, imperial = false),
        Spec(id = "kibibyte", category = Category.DATA, symbol = "KiB", factor = 8192.0, offset = 0.0, aliases = listOf("kibibyte", "kibibytes", "kib", "kibyte", "kibytes"), metric = false, imperial = false),
        Spec(id = "mebibyte", category = Category.DATA, symbol = "MiB", factor = 8388608.0, offset = 0.0, aliases = listOf("mebibyte", "mebibytes", "mib", "mibyte", "mibytes"), metric = false, imperial = false),
        Spec(id = "gibibyte", category = Category.DATA, symbol = "GiB", factor = 8589934592.0, offset = 0.0, aliases = listOf("gibibyte", "gibibytes", "gib", "gibyte", "gibytes"), metric = false, imperial = false),
        Spec(id = "tebibyte", category = Category.DATA, symbol = "TiB", factor = 8796093022208.0, offset = 0.0, aliases = listOf("tebibyte", "tebibytes", "tib", "tibyte", "tibytes"), metric = false, imperial = false),
        Spec(id = "pebibyte", category = Category.DATA, symbol = "PiB", factor = 9007199254740992.0, offset = 0.0, aliases = listOf("pebibyte", "pebibytes", "pib", "pibyte", "pibytes"), metric = false, imperial = false),
        Spec(id = "exbibyte", category = Category.DATA, symbol = "EiB", factor = 9.223372036854776E18, offset = 0.0, aliases = listOf("exbibyte", "exbibytes", "eib", "eibyte", "eibytes"), metric = false, imperial = false),
    )

    val meta: Map<Category, CatMeta> = mapOf(
        Category.LENGTH to CatMeta(
            base = "meter", metricPrimary = "meter",
            imperialPrimary = "foot", secondary = "kilometer",
            special = null,
            popularPairs = mapOf("meter" to "foot", "kilometer" to "mile", "centimeter" to "inch", "millimeter" to "inch", "micrometer" to "millimeter", "nanometer" to "micrometer", "picometer" to "nanometer", "angstrom" to "nanometer", "inch" to "centimeter", "foot" to "meter", "yard" to "meter", "mile" to "kilometer", "mil" to "millimeter", "nautical_mile" to "kilometer", "astronomical_unit" to "kilometer", "light_year" to "kilometer", "parsec" to "light_year")
        ),
        Category.AREA to CatMeta(
            base = "square_meter", metricPrimary = "square_meter",
            imperialPrimary = "square_foot", secondary = "square_kilometer",
            special = null,
            popularPairs = mapOf("square_meter" to "square_foot", "square_centimeter" to "square_inch", "square_millimeter" to "square_inch", "square_kilometer" to "square_mile", "square_inch" to "square_centimeter", "square_foot" to "square_meter", "square_yard" to "square_meter", "square_mile" to "square_kilometer", "hectare" to "acre", "acre" to "hectare", "are" to "square_meter")
        ),
        Category.DENSITY to CatMeta(
            base = "kilogram_per_cubic_meter", metricPrimary = "gram_per_cubic_centimeter",
            imperialPrimary = "pound_per_cubic_foot", secondary = "kilogram_per_cubic_meter",
            special = null,
            popularPairs = mapOf("gram_per_cubic_centimeter" to "kilogram_per_cubic_meter", "gram_per_milliliter" to "kilogram_per_cubic_meter", "gram_per_liter" to "kilogram_per_cubic_meter", "gram_per_deciliter" to "gram_per_liter", "kilogram_per_liter" to "gram_per_cubic_centimeter", "kilogram_per_cubic_meter" to "gram_per_cubic_centimeter", "milligram_per_milliliter" to "gram_per_liter", "milligram_per_cubic_centimeter" to "kilogram_per_cubic_meter", "milligram_per_liter" to "milligram_per_cubic_meter", "milligram_per_deciliter" to "gram_per_liter", "milligram_per_cubic_meter" to "milligram_per_liter", "microgram_per_milliliter" to "milligram_per_liter", "microgram_per_liter" to "microgram_per_deciliter", "microgram_per_deciliter" to "microgram_per_liter", "pound_per_cubic_foot" to "kilogram_per_cubic_meter", "pound_per_cubic_inch" to "gram_per_cubic_centimeter")
        ),
        Category.VOLUME to CatMeta(
            base = "cubic_meter", metricPrimary = "liter",
            imperialPrimary = "us_gallon", secondary = "milliliter",
            special = null,
            popularPairs = mapOf("cubic_meter" to "liter", "liter" to "us_gallon", "deciliter" to "milliliter", "centiliter" to "milliliter", "milliliter" to "us_fluid_ounce", "microliter" to "milliliter", "cubic_centimeter" to "cubic_inch", "cubic_millimeter" to "milliliter", "cubic_foot" to "cubic_meter", "cubic_inch" to "cubic_centimeter", "us_gallon" to "liter", "imperial_gallon" to "liter", "us_quart" to "liter", "us_pint" to "milliliter", "imperial_pint" to "milliliter", "us_fluid_ounce" to "milliliter", "imperial_fluid_ounce" to "milliliter", "us_gill" to "milliliter", "imperial_gill" to "milliliter", "us_tablespoon" to "milliliter", "australian_tablespoon" to "milliliter", "cup" to "milliliter")
        ),
        Category.TIME to CatMeta(
            base = "second", metricPrimary = "second",
            imperialPrimary = "second", secondary = "minute",
            special = null,
            popularPairs = mapOf("second" to "millisecond", "decisecond" to "second", "centisecond" to "second", "millisecond" to "second", "microsecond" to "millisecond", "nanosecond" to "microsecond", "minute" to "second", "hour" to "minute", "day" to "hour", "week" to "day", "year" to "day", "lustrum" to "year", "decade" to "year", "century" to "year", "millennium" to "year")
        ),
        Category.TEMPERATURE to CatMeta(
            base = "celsius", metricPrimary = "celsius",
            imperialPrimary = "fahrenheit", secondary = "kelvin",
            special = null,
            popularPairs = mapOf("celsius" to "fahrenheit", "fahrenheit" to "celsius", "kelvin" to "celsius", "reaumur" to "celsius", "romer" to "celsius", "delisle" to "celsius", "rankine" to "celsius")
        ),
        Category.SPEED to CatMeta(
            base = "meter_per_second", metricPrimary = "kilometer_per_hour",
            imperialPrimary = "mile_per_hour", secondary = "meter_per_second",
            special = null,
            popularPairs = mapOf("meter_per_second" to "kilometer_per_hour", "kilometer_per_hour" to "mile_per_hour", "mile_per_hour" to "kilometer_per_hour", "knot" to "kilometer_per_hour", "foot_per_second" to "meter_per_second")
        ),
        Category.MASS to CatMeta(
            base = "kilogram", metricPrimary = "kilogram",
            imperialPrimary = "pound", secondary = "gram",
            special = null,
            popularPairs = mapOf("kilogram" to "pound", "gram" to "ounce", "milligram" to "gram", "centigram" to "gram", "hectogram" to "gram", "quintal" to "kilogram", "tonne" to "pound", "pound" to "kilogram", "ounce" to "gram", "stone" to "kilogram", "carat" to "gram", "pennyweight" to "gram", "troy_ounce" to "gram", "atomic_mass_unit" to "gram")
        ),
        Category.PRESSURE to CatMeta(
            base = "pascal", metricPrimary = "pascal",
            imperialPrimary = "psi", secondary = "bar",
            special = null,
            popularPairs = mapOf("pascal" to "kilopascal", "hectopascal" to "millibar", "kilopascal" to "psi", "megapascal" to "psi", "gigapascal" to "megapascal", "bar" to "psi", "millibar" to "hectopascal", "atmosphere" to "bar", "psi" to "bar", "ksi" to "megapascal", "torr" to "kilopascal", "inches_of_mercury" to "millibar")
        ),
        Category.ENERGY to CatMeta(
            base = "joule", metricPrimary = "joule",
            imperialPrimary = "foot_pound", secondary = "kilojoule",
            special = null,
            popularPairs = mapOf("joule" to "calorie", "kilojoule" to "kilocalorie", "calorie" to "joule", "kilocalorie" to "kilojoule", "kilowatt_hour" to "kilojoule", "electron_volt" to "joule", "foot_pound" to "joule")
        ),
        Category.ANGLE to CatMeta(
            base = "radian", metricPrimary = "degree",
            imperialPrimary = "degree", secondary = "radian",
            special = null,
            popularPairs = mapOf("degree" to "radian", "radian" to "degree", "arcminute" to "degree", "arcsecond" to "degree")
        ),
        Category.SHOE_SIZE to CatMeta(
            base = "korea", metricPrimary = "eu",
            imperialPrimary = "us_men", secondary = "uk_men",
            special = null,
            popularPairs = mapOf("eu" to "us_men", "us_men" to "eu", "us_women" to "eu", "uk_men" to "eu", "uk_women" to "eu", "japan" to "eu", "korea" to "eu", "china" to "eu", "mexico" to "eu")
        ),
        Category.POWER to CatMeta(
            base = "watt", metricPrimary = "watt",
            imperialPrimary = "horsepower_mechanical", secondary = "kilowatt",
            special = null,
            popularPairs = mapOf("watt" to "horsepower_mechanical", "milliwatt" to "watt", "kilowatt" to "horsepower_mechanical", "megawatt" to "kilowatt", "gigawatt" to "megawatt", "horsepower_metric" to "kilowatt", "horsepower_mechanical" to "kilowatt")
        ),
        Category.FORCE to CatMeta(
            base = "newton", metricPrimary = "newton",
            imperialPrimary = "pound_force", secondary = "kilogram_force",
            special = null,
            popularPairs = mapOf("newton" to "pound_force", "pound_force" to "newton", "dyne" to "newton", "kilogram_force" to "newton", "poundal" to "newton")
        ),
        Category.TORQUE to CatMeta(
            base = "newton_meter", metricPrimary = "newton_meter",
            imperialPrimary = "pound_force_foot", secondary = "kilogram_force_meter",
            special = null,
            popularPairs = mapOf("newton_meter" to "pound_force_foot", "pound_force_foot" to "newton_meter", "kilogram_force_meter" to "newton_meter", "dyne_meter" to "newton_meter", "poundal_meter" to "newton_meter")
        ),
        Category.FUEL to CatMeta(
            base = "l_per_100km", metricPrimary = "l_per_100km",
            imperialPrimary = "mpg_us", secondary = "km_per_l",
            special = "fuel",
            popularPairs = mapOf("l_per_100km" to "mpg_us", "km_per_l" to "l_per_100km", "mpg_us" to "l_per_100km", "mpg_imp" to "l_per_100km")
        ),
        Category.NUMERAL to CatMeta(
            base = "decimal", metricPrimary = "decimal",
            imperialPrimary = "decimal", secondary = "hexadecimal",
            special = "numeral",
            popularPairs = mapOf("decimal" to "hexadecimal", "hexadecimal" to "decimal", "octal" to "decimal", "binary" to "decimal")
        ),
        Category.DATA to CatMeta(
            base = "bit", metricPrimary = "megabyte",
            imperialPrimary = "megabyte", secondary = "gigabyte",
            special = null,
            popularPairs = mapOf("bit" to "byte", "nibble" to "bit", "kilobit" to "kilobyte", "megabit" to "megabyte", "gigabit" to "gigabyte", "terabit" to "terabyte", "petabit" to "petabyte", "exabit" to "exabyte", "kibibit" to "kilobit", "mebibit" to "megabit", "gibibit" to "gigabit", "tebibit" to "terabit", "pebibit" to "petabit", "exbibit" to "exabit", "byte" to "bit", "kilobyte" to "kibibyte", "megabyte" to "mebibyte", "gigabyte" to "gibibyte", "terabyte" to "tebibyte", "petabyte" to "pebibyte", "exabyte" to "exbibyte", "kibibyte" to "kilobyte", "mebibyte" to "megabyte", "gibibyte" to "gigabyte", "tebibyte" to "terabyte", "pebibyte" to "petabyte", "exbibyte" to "exabyte")
        ),
    )

    private val idIndex: Map<String, Spec> = all.associateBy { it.id }
    private val categoryIndex: Map<Category, List<Spec>> = all.groupBy { it.category }

    fun byId(id: String): Spec? = idIndex[id]
    fun unitsIn(category: Category): List<Spec> = categoryIndex[category] ?: emptyList()
    fun label(id: String): String = id.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    // Specs ordered by category priority so common categories win alias collisions.
    val parsePriorityOrder: List<Spec> by lazy {
        val order = listOf(Category.TEMPERATURE, Category.LENGTH, Category.MASS, Category.VOLUME, Category.TIME, Category.SPEED, Category.AREA, Category.DATA, Category.ENERGY, Category.POWER, Category.PRESSURE, Category.FORCE, Category.TORQUE, Category.DENSITY, Category.ANGLE, Category.FUEL, Category.SHOE_SIZE, Category.NUMERAL)
        all.sortedBy { order.indexOf(it.category) }
    }
}
