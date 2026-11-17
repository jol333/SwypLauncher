package com.joyal.swyplauncher

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.joyal.swyplauncher.domain.model.AppChangeEvent
import com.joyal.swyplauncher.ui.util.AppIconFetcher
import com.joyal.swyplauncher.ui.util.AppIconPreviewFetcher
import com.joyal.swyplauncher.ui.util.AppIconPreviewKeyer
import com.joyal.swyplauncher.ui.util.AppIconRenderer
import com.joyal.swyplauncher.ui.util.IconPackIconFetcher
import com.joyal.swyplauncher.ui.util.IconPackIconKeyer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class LauncherApplication : Application(), SingletonImageLoader.Factory {

    @javax.inject.Inject
    lateinit var downloadHandwritingModelUseCase: com.joyal.swyplauncher.domain.usecase.DownloadHandwritingModelUseCase

    @javax.inject.Inject
    lateinit var mlKitRepository: com.joyal.swyplauncher.domain.repository.MLKitRepository

    @javax.inject.Inject
    lateinit var appRepository: com.joyal.swyplauncher.domain.repository.AppRepository

    @javax.inject.Inject
    lateinit var recordAppUsageUseCase: com.joyal.swyplauncher.domain.usecase.RecordAppUsageUseCase

    @javax.inject.Inject
    lateinit var iconPackRepository: com.joyal.swyplauncher.domain.repository.IconPackRepository

    // Process-lifetime scope for fire-and-forget startup work (lives as long as the process).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        prewarmAppListInBackground()
        downloadHandwritingModelInBackground()
        keepIconPacksInSync()
    }

    // Icon packs are ordinary apps: when one is updated or removed, whatever was cached from it
    // is stale. And whenever icons may look different, bump the Compose-visible version that is
    // part of every app icon's request, so icons on screen reload.
    private fun keepIconPacksInSync() {
        appScope.launch {
            appRepository.observeAppChanges().collect { event ->
                val packageName = when (event) {
                    is AppChangeEvent.AppInstalled -> event.packageName
                    is AppChangeEvent.AppUninstalled -> event.packageName
                }
                iconPackRepository.onPackageChanged(packageName)
            }
        }
        appScope.launch {
            iconPackRepository.iconVersion.collect { AppIconFetcher.iconVersion = it }
        }
    }

    // The assistant overlay is usually the first thing shown by a new process. Get the persisted
    // app list and the usage ranking into memory while the activity is still being created, so
    // its first frame can already contain the apps. Both are cheap and best-effort.
    private fun prewarmAppListInBackground() {
        appScope.launch {
            try {
                appRepository.restoreCachedApps()
            } catch (e: Exception) {
                // The assistant falls back to its normal loading path.
            }
        }
        appScope.launch {
            try {
                recordAppUsageUseCase.prewarm()
            } catch (e: Exception) {
                // Computed on demand instead.
            }
        }
    }

    // Download handwriting model in background on first launch
    private fun downloadHandwritingModelInBackground() {
        appScope.launch {
            try {
                downloadHandwritingModelUseCase()
            } catch (e: Exception) {
                // Silently fail - user will be prompted to download when they use handwriting mode
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Once our UI is hidden (or the process is a background LRU candidate), release the
        // ML Kit recognizers' native model buffers. The model stays on disk, so the next
        // handwriting/voice use re-creates the client locally — no re-download.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            mlKitRepository.cleanup()
            // Parsed icon packs and their open APKs; re-read when an icon next needs rendering
            iconPackRepository.trimMemory()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.30) // Increased to 30% for better icon caching
                    .strongReferencesEnabled(true) // Keep strong references to prevent GC during scrolling
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100 * 1024 * 1024) // Increased to 100MB for persistent cache
                    .build()
            }
            .components {
                val renderer = AppIconRenderer(context, iconPackRepository)
                add(AppIconFetcher.Factory(context, renderer, iconPackRepository))
                // Icon pack previews and the icons inside a pack
                add(AppIconPreviewKeyer)
                add(AppIconPreviewFetcher.Factory(renderer))
                add(IconPackIconKeyer)
                add(IconPackIconFetcher.Factory(renderer))
            }
            .build()
    }
}
