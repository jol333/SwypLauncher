package com.joyal.swyplauncher.domain.model

/** An installed icon pack app (anything that answers the usual ADW/Nova theme intents). */
data class IconPackInfo(
    val packageName: String,
    val label: String
)

/** One drawable inside an icon pack. */
data class IconPackIcon(
    val packPackage: String,
    val drawableName: String
)

/**
 * The icon a pack assigns to an app. [changesDaily] marks a dynamic calendar icon, which is only
 * right for today's date.
 */
data class IconPackMatch(
    val icon: IconPackIcon,
    val changesDaily: Boolean = false
)

/**
 * An icon picked for one app from its long-press menu. It wins over the icon pack applied in
 * settings; an app without a choice follows that pack.
 */
sealed interface AppIconChoice {
    /** The app's own icon, even while an icon pack is applied. */
    data object Original : AppIconChoice

    data class FromPack(val icon: IconPackIcon) : AppIconChoice
}

/**
 * How a pack dresses the apps it has no icon for: the app's own icon is scaled by [scale], cut
 * with [mask], and placed between one of the [backs] and [upon]. All names are drawables of the
 * pack; any of them may be missing.
 */
data class IconPackFallbackStyle(
    val backs: List<String>,
    val mask: String?,
    val upon: String?,
    val scale: Float
)
