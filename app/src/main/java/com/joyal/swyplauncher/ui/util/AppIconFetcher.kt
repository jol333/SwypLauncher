package com.joyal.swyplauncher.ui.util

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
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
        return ImageFetchResult(
            image = icon.toBitmap().asImage(),
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

            if (activityInfo != null) {
                // Use higher density if possible, though getBadgedIcon usually handles it
                activityInfo.getBadgedIcon(appInfo.iconDensity)
            } else {
                // Fallback to PackageManager if LauncherApps fails or activity not found
                packageManager.getApplicationIcon(appInfo.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
