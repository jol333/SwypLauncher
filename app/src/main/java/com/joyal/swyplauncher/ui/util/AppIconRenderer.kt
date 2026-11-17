package com.joyal.swyplauncher.ui.util

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import com.joyal.swyplauncher.domain.model.AppIconChoice
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackFallbackStyle
import com.joyal.swyplauncher.domain.model.IconPackIcon
import com.joyal.swyplauncher.domain.repository.IconPackRepository

/** A rendered app icon. [changesDaily]: a dynamic calendar icon that is only right today. */
class RenderedIcon(val bitmap: Bitmap, val changesDaily: Boolean = false)

/**
 * Draws app icons into square bitmaps: the app's own icon, an icon from an icon pack, or the
 * app's own icon dressed in a pack's fallback style. Everything that touches a pack is guarded,
 * because a pack can reference drawables it doesn't contain, ship corrupt or huge images, or
 * disappear mid-render; whatever goes wrong, the result degrades to the app's own icon.
 *
 * All calls read APK resources and block: IO threads only.
 */
class AppIconRenderer(
    private val context: Context,
    private val iconPacks: IconPackRepository
) {

    /** [app]'s icon under the current settings: its picked icon, else the applied pack. */
    fun render(app: AppInfo): RenderedIcon? {
        when (val choice = iconPacks.getIconChoices()[app.getIdentifier()]) {
            AppIconChoice.Original -> return renderOriginal(app)?.let(::RenderedIcon)
            is AppIconChoice.FromPack ->
                renderPackIcon(choice.icon, iconSize(app.iconDensity))?.let { return RenderedIcon(it) }
            null -> Unit
        }
        return renderWithPack(app, iconPacks.getActiveIconPack())
    }

    /**
     * [app]'s icon as [packPackage] alone would show it (null: the app's own icon), ignoring any
     * individually picked icon. Also what the icon pack screen previews.
     */
    fun renderWithPack(app: AppInfo, packPackage: String?): RenderedIcon? {
        if (packPackage == null) return renderOriginal(app)?.let(::RenderedIcon)

        val match = runGuarded { iconPacks.findIcon(packPackage, app) }
        if (match != null) {
            renderPackIcon(match.icon, iconSize(app.iconDensity))?.let {
                return RenderedIcon(it, match.changesDaily)
            }
        }

        // The pack has nothing (usable) for this app: its own icon, in the pack's fallback style
        val original = renderOriginal(app) ?: return null
        val style = runGuarded { iconPacks.getFallbackStyle(packPackage) }
            ?: return RenderedIcon(original)
        val styled = runGuarded { applyFallbackStyle(original, packPackage, style, app.packageName) }
        return RenderedIcon(styled ?: original)
    }

    /** One icon of a pack at [sizePx], or null if the pack can't produce it. */
    fun renderPackIcon(icon: IconPackIcon, sizePx: Int): Bitmap? = runGuarded {
        iconPacks.loadIconDrawable(icon, DisplayMetrics.DENSITY_XXXHIGH)
            ?.let { drawableToSquareBitmap(it, sizePx.coerceIn(MIN_ICON_SIZE, MAX_ICON_SIZE)) }
    }

    /** The app's own icon, as its developer shipped it. */
    fun renderOriginal(app: AppInfo): Bitmap? {
        val icon = loadIcon(app) ?: return null
        return runGuarded { drawableToSquareBitmap(icon, iconSize(app.iconDensity)) }
    }

    private fun loadIcon(appInfo: AppInfo): Drawable? {
        return try {
            val packageManager = context.packageManager
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Try to load using LauncherApps first (handles work profiles, etc. better)
            val userHandle = android.os.Process.myUserHandle()
            val activityList = launcherApps.getActivityList(appInfo.packageName, userHandle)

            // Find the specific activity if activityName is provided, otherwise use the first one
            val activityInfo = if (appInfo.activityName != null) {
                activityList.find { it.componentName.className == appInfo.activityName }
            } else {
                activityList.firstOrNull()
            }

            // Get the raw icon - use getIcon() instead of getBadgedIcon() to avoid system masking
            val rawIcon = if (activityInfo != null) {
                // getIcon() returns the unmasked drawable
                activityInfo.getIcon(appInfo.iconDensity)
            } else {
                // Fallback to PackageManager if LauncherApps fails or activity not found
                packageManager.getApplicationIcon(appInfo.packageName)
            }

            rawIcon
        } catch (_: Exception) {
            // Icon unavailable (e.g. app uninstalled or restricted profile); caller handles null.
            null
        }
    }

    // Converts icon Drawable to a square Bitmap.
    private fun drawableToSquareBitmap(drawable: Drawable, size: Int): Bitmap {
        return if (drawable is AdaptiveIconDrawable) {
            // For adaptive icons, we render the layers manually without the system mask
            // Adaptive icons have an inset of ~18.75% on each side, so we need a larger canvas
            // The safe zone is 66dp in a 108dp container (ratio of ~0.61)
            val canvasSize = (size * 1.5f).toInt() // Larger to account for the full adaptive icon bounds

            val bitmap = createBitmap(canvasSize, canvasSize)
            val canvas = Canvas(bitmap)

            // Draw background layer
            drawable.background?.let { bg ->
                bg.setBounds(0, 0, canvasSize, canvasSize)
                bg.draw(canvas)
            }

            // Draw foreground layer
            drawable.foreground?.let { fg ->
                fg.setBounds(0, 0, canvasSize, canvasSize)
                fg.draw(canvas)
            }

            // Crop to the center to get the visible portion
            // The visible area is the center 2/3 of the full canvas
            val offset = (canvasSize - size) / 2
            Bitmap.createBitmap(bitmap, offset.coerceAtLeast(0), offset.coerceAtLeast(0),
                size.coerceAtMost(canvasSize), size.coerceAtMost(canvasSize))
        } else {
            // For non-adaptive icons, just render as-is
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        }
    }

    /**
     * The usual icon pack treatment for an app the pack has no icon for: one of the pack's back
     * plates, then the app's icon scaled down and cut with the pack's mask, then the pack's
     * overlay. A layer the pack names but doesn't actually contain is simply left out.
     */
    private fun applyFallbackStyle(
        original: Bitmap,
        packPackage: String,
        style: IconPackFallbackStyle,
        seed: String
    ): Bitmap {
        val size = original.width
        fun layer(name: String) = renderPackIcon(IconPackIcon(packPackage, name), size)

        // Same app, same plate, every time
        val back = style.backs.takeIf { it.isNotEmpty() }
            ?.let { layer(it[Math.floorMod(seed.hashCode(), it.size)]) }
        val mask = style.mask?.let(::layer)
        val upon = style.upon?.let(::layer)
        // Nothing of the style is usable (or it asks for nothing): leave the icon as it is
        if (back == null && mask == null && upon == null && style.scale == 1f) return original

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val result = createBitmap(size, size)
        val canvas = Canvas(result)
        back?.let { canvas.drawBitmap(it, 0f, 0f, paint) }

        val iconLayer = createBitmap(size, size)
        val iconCanvas = Canvas(iconLayer)
        val scaled = size * style.scale
        val inset = (size - scaled) / 2f
        val bounds = RectF(inset, inset, inset + scaled, inset + scaled)
        // Our renders of adaptive icons are full-bleed squares. With no mask to shape them, the
        // corners would poke out of the pack's plate, so round them off first.
        if (back != null && mask == null && isFullBleed(original)) {
            iconCanvas.drawOval(bounds, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        iconCanvas.drawBitmap(original, null, bounds, paint)
        if (mask != null) {
            // Opaque mask pixels are the parts to cut away
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            iconCanvas.drawBitmap(mask, 0f, 0f, paint)
        }
        paint.xfermode = null
        canvas.drawBitmap(iconLayer, 0f, 0f, paint)

        upon?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
        return result
    }

    private fun isFullBleed(bitmap: Bitmap): Boolean {
        val last = bitmap.width - 1
        return last > 0 && listOf(bitmap[0, 0], bitmap[last, 0], bitmap[0, last], bitmap[last, last])
            .all { android.graphics.Color.alpha(it) == 255 }
    }

    /** Null instead of an exception; a broken pack must never take the icon grid down with it. */
    private inline fun <T> runGuarded(block: () -> T?): T? = try {
        block()
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: StackOverflowError) {
        // Drawable XML that includes itself
        null
    }

    companion object {
        private const val MIN_ICON_SIZE = 48
        private const val MAX_ICON_SIZE = 512

        /** Edge of a rendered app icon in pixels (48dp at the given density). */
        fun iconSize(density: Int): Int {
            val baseSize = 48
            val scale = density / 160f // 160 is mdpi baseline
            return (baseSize * scale).toInt().coerceIn(96, MAX_ICON_SIZE)
        }
    }
}
