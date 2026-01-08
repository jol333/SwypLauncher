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
import coil3.request.Options
import com.joyal.swyplauncher.domain.model.AppInfo

class AppIconFetcher(
    private val appInfo: AppInfo,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val icon = loadIcon(appInfo) ?: return null
        // Convert the drawable to a square bitmap
        val bitmap = drawableToSquareBitmap(icon, appInfo.iconDensity)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
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
        } catch (e: Exception) {
            e.printStackTrace()
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
            return AppIconFetcher(data, context)
        }
    }
}
