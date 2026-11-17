package com.joyal.swyplauncher.util

import android.annotation.SuppressLint
import android.content.res.Resources
import android.util.Xml
import com.joyal.swyplauncher.domain.model.IconPackFallbackStyle
import org.xmlpull.v1.XmlPullParser

/** A pack's `appfilter.xml`, reduced to the lookups the launcher needs. */
class ParsedIconPack(
    /** "package/activityClass" (same format as `AppInfo.getIdentifier()`) -> drawable name. */
    val icons: Map<String, String>,
    /** package -> drawable name where every entry of that package agrees; "" where they differ. */
    val packageIcons: Map<String, String>,
    /** "package/activityClass" -> drawable name prefix of a per-day calendar icon set. */
    val calendars: Map<String, String>,
    val fallbackStyle: IconPackFallbackStyle?
)

/**
 * Reads the two files of the de-facto ADW/Nova icon pack format: `appfilter.xml` (which icon
 * belongs to which app) and `drawable.xml` (every icon, for manual picking). Either may sit in
 * `res/xml` or in `assets`.
 *
 * Packs in the wild are frequently malformed (unclosed tags, duplicate or empty entries, files
 * cut off mid-way), so parsing never throws: it keeps whatever was read before the problem.
 */
object IconPackParser {

    // Far beyond any real pack; only bounds the damage of a hostile or runaway file.
    private const val MAX_TAGS = 200_000

    fun hasAppFilter(resources: Resources, packPackage: String): Boolean =
        withXml(resources, packPackage, "appfilter") { true } ?: false

    fun parseAppFilter(resources: Resources, packPackage: String): ParsedIconPack {
        val icons = HashMap<String, String>()
        val packageIcons = HashMap<String, String>()
        val calendars = HashMap<String, String>()
        val backs = ArrayList<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f

        withXml(resources, packPackage, "appfilter") { parser ->
            parser.forEachStartTag { tag ->
                when (tag) {
                    "item" -> {
                        val component = parser.attribute("component")?.let(::componentKey)
                        val drawable = parser.attribute("drawable")
                        if (component != null && !drawable.isNullOrBlank() && component !in icons) {
                            icons[component] = drawable
                            val pkg = component.substringBefore('/')
                            val known = packageIcons[pkg]
                            packageIcons[pkg] = if (known == null || known == drawable) drawable else ""
                        }
                    }

                    "calendar" -> {
                        val component = parser.attribute("component")?.let(::componentKey)
                        val prefix = parser.attribute("prefix")
                        if (component != null && !prefix.isNullOrBlank()) calendars[component] = prefix
                    }

                    // <iconback img1="a" img2="b" …/>: any number of alternatives
                    "iconback" -> for (i in 0 until parser.attributeCount) {
                        val value = parser.getAttributeValue(i)
                        if (parser.getAttributeName(i).startsWith("img") && !value.isNullOrBlank()) {
                            backs += value
                        }
                    }

                    "iconmask" -> mask = parser.attribute("img1")?.takeIf { it.isNotBlank() } ?: mask
                    "iconupon" -> upon = parser.attribute("img1")?.takeIf { it.isNotBlank() } ?: upon
                    "scale" -> scale = parser.attribute("factor")?.toFloatOrNull()
                        ?.takeIf { it.isFinite() }?.coerceIn(0.3f, 1.5f) ?: scale
                }
            }
        }

        val style = if (backs.isEmpty() && mask == null && upon == null && scale == 1f) {
            null
        } else {
            IconPackFallbackStyle(backs, mask, upon, scale)
        }
        return ParsedIconPack(icons, packageIcons, calendars, style)
    }

    /** Drawable names listed in `drawable.xml`, in file order, without duplicates. */
    fun parseDrawableList(resources: Resources, packPackage: String): List<String> {
        val names = LinkedHashSet<String>()
        withXml(resources, packPackage, "drawable") { parser ->
            parser.forEachStartTag { tag ->
                if (tag == "item") {
                    parser.attribute("drawable")?.takeIf { it.isNotBlank() }?.let { names += it }
                }
            }
        }
        return names.toList()
    }

    /**
     * "ComponentInfo{com.foo/com.foo.Main}" -> "com.foo/com.foo.Main". Expands the ".Main"
     * shorthand; null for anything else (packs also carry launcher-specific pseudo entries).
     */
    private fun componentKey(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val flat = raw.substring(start + 1, end).trim()
        val slash = flat.indexOf('/')
        if (slash <= 0 || slash == flat.length - 1) return null
        return if (flat[slash + 1] == '.') {
            val pkg = flat.substring(0, slash)
            "$pkg/$pkg${flat.substring(slash + 1)}"
        } else {
            flat
        }
    }

    private fun XmlPullParser.attribute(name: String): String? = getAttributeValue(null, name)

    private inline fun XmlPullParser.forEachStartTag(action: (String) -> Unit) {
        try {
            var seen = 0
            var event = eventType
            while (event != XmlPullParser.END_DOCUMENT && seen < MAX_TAGS) {
                if (event == XmlPullParser.START_TAG) {
                    seen++
                    action(name)
                }
                event = next()
            }
        } catch (_: Exception) {
            // Malformed or truncated file: keep what was read so far.
        }
    }

    /** Runs [block] on `res/xml/<name>.xml`, else `assets/<name>.xml`; null when neither opens. */
    @SuppressLint("DiscouragedApi") // the pack's resources can only be looked up by name
    private inline fun <T> withXml(
        resources: Resources,
        packPackage: String,
        name: String,
        block: (XmlPullParser) -> T
    ): T? {
        try {
            val id = resources.getIdentifier(name, "xml", packPackage)
            if (id != 0) {
                val parser = resources.getXml(id)
                try {
                    return block(parser)
                } finally {
                    parser.close()
                }
            }
            return resources.assets.open("$name.xml").use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                block(parser)
            }
        } catch (_: Exception) {
            // No such file, or it can't be opened as XML at all.
            return null
        }
    }
}
