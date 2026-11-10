package com.joyal.swyplauncher.ui.util

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.memory.MemoryCache
import coil3.request.Options
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.util.AppIconDiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppIconFetcher(
    private val appInfo: AppInfo,
    private val context: Context,
    private val imageLoader: ImageLoader
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // Fast path: the icon rendered by an earlier fetch (possibly in a previous process).
        val cached = AppIconDiskCache.read(context, appInfo)
        if (cached != null) {
            scheduleRevalidation(cached)
            return cached.toFetchResult()
        }
        val bitmap = renderIcon() ?: return null
        AppIconDiskCache.write(context, appInfo, bitmap)
        return bitmap.toFetchResult()
    }

    private fun Bitmap.toFetchResult() = ImageFetchResult(
        image = asImage(),
        isSampled = false,
        dataSource = DataSource.DISK
    )

    private fun renderIcon(): Bitmap? {
        val icon = loadIcon(appInfo) ?: return null
        // Convert the drawable to a square bitmap
        return drawableToSquareBitmap(icon, appInfo.iconDensity)
    }

    /**
     * A cached file can outlive what it was rendered from (e.g. the system icon theme changed),
     * and the file name only tracks app updates. So about once a day per icon, re-render it in
     * the background and replace the cached copies if the result differs. Delayed and serialized
     * so it never competes with the icons the user is waiting for.
     */
    private fun scheduleRevalidation(cached: Bitmap) {
        val memoryKey = memoryCacheKey(appInfo)
        if (!AppIconDiskCache.isDueForRevalidation(context, appInfo)) return
        if (!revalidating.add(memoryKey)) return
        revalidationScope.launch {
            delay(REVALIDATION_DELAY_MS)
            val fresh = renderIcon()
            if (fresh != null) {
                if (AppIconDiskCache.sameIcon(cached, fresh)) {
                    AppIconDiskCache.markRevalidated(context, appInfo)
                } else {
                    AppIconDiskCache.write(context, appInfo, fresh)
                    imageLoader.memoryCache?.set(
                        MemoryCache.Key(memoryKey),
                        MemoryCache.Value(fresh.asImage())
                    )
                }
            }
            revalidating.remove(memoryKey)
        }
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
    private fun drawableToSquareBitmap(drawable: Drawable, density: Int): Bitmap {
        // Calculate size based on density (standard icon size is 48dp)
        val baseSize = 48
        val scale = density / 160f // 160 is mdpi baseline
        val size = (baseSize * scale).toInt().coerceIn(96, 512)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            // For adaptive icons, we render the layers manually without the system mask
            // Adaptive icons have an inset of ~18.75% on each side, so we need a larger canvas
            // The safe zone is 66dp in a 108dp container (ratio of ~0.61)
            val canvasSize = (size * 1.5f).toInt() // Larger to account for the full adaptive icon bounds
            
            val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
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
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppInfo> {
        override fun create(
            data: AppInfo,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return AppIconFetcher(data, context, imageLoader)
        }
    }

    companion object {
        private const val REVALIDATION_DELAY_MS = 4_000L

        // Icons currently queued for a re-check, by memory-cache key.
        private val revalidating: MutableSet<String> = ConcurrentHashMap.newKeySet()

        // One at a time, so background re-renders stay a trickle.
        private val revalidationScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

        /** Memory-cache key shared by every screen that shows an app icon. */
        fun memoryCacheKey(app: AppInfo): String = "app_icon_${app.getIdentifier()}"
    }
}
