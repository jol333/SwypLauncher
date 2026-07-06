package com.joyal.swyplauncher

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
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

    // Process-lifetime scope for fire-and-forget startup work (lives as long as the process).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        downloadHandwritingModelInBackground()
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
                add(com.joyal.swyplauncher.ui.util.AppIconFetcher.Factory(context))
            }
            .build()
    }
}
