package com.joyal.swyplauncher.ui.util

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.Options
import coil3.size.Dimension
import com.joyal.swyplauncher.domain.model.AppInfo
import com.joyal.swyplauncher.domain.model.IconPackIcon
import com.joyal.swyplauncher.domain.model.IconPackInfo
import com.joyal.swyplauncher.domain.repository.IconPackRepository
import com.joyal.swyplauncher.util.AppIconDiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the icon the launcher shows for an app: the icon picked for it, else the applied icon
 * pack's, else its own (see [AppIconRenderer]).
 */
class AppIconFetcher(
    private val appInfo: AppInfo,
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val renderer: AppIconRenderer,
    private val iconPacks: IconPackRepository
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // What the icon is drawn from right now; part of the cache file name, so an icon
        // rendered for another icon pack is never mistaken for the current one.
        val style = iconPacks.iconStyleToken(appInfo)

        // Fast path: the icon rendered by an earlier fetch (possibly in a previous process).
        val cached = AppIconDiskCache.read(context, appInfo, style)
        if (cached != null) {
            scheduleRevalidation(cached, style)
            return cached.toFetchResult()
        }
        if (style.isNotEmpty()) {
            // A pack's dynamic calendar icon is filed under today's date instead (and needs no
            // revalidation: tomorrow it is a different file).
            AppIconDiskCache.read(context, appInfo, AppIconDiskCache.dailyStyle(style))
                ?.let { return it.toFetchResult() }
        }

        val rendered = renderer.render(appInfo) ?: return null
        val fileStyle = if (rendered.changesDaily) AppIconDiskCache.dailyStyle(style) else style
        AppIconDiskCache.write(context, appInfo, fileStyle, rendered.bitmap)
        return rendered.bitmap.toFetchResult()
    }

    /**
     * A cached file can outlive what it was rendered from (e.g. the system icon theme changed),
     * and the file name only tracks app and icon pack updates. So about once a day per icon,
     * re-render it in the background and replace the cached copies if the result differs. Delayed
     * and serialized so it never competes with the icons the user is waiting for.
     */
    private fun scheduleRevalidation(cached: Bitmap, style: String) {
        val memoryKey = memoryCacheKey(appInfo)
        if (!AppIconDiskCache.isDueForRevalidation(context, appInfo, style)) return
        if (!revalidating.add(memoryKey)) return
        revalidationScope.launch {
            delay(REVALIDATION_DELAY_MS)
            // Skipped if another icon was picked or applied for this app in the meantime
            val fresh = if (iconPacks.iconStyleToken(appInfo) == style) renderer.render(appInfo) else null
            if (fresh != null && !fresh.changesDaily) {
                if (AppIconDiskCache.sameIcon(cached, fresh.bitmap)) {
                    AppIconDiskCache.markRevalidated(context, appInfo, style)
                } else {
                    AppIconDiskCache.write(context, appInfo, style, fresh.bitmap)
                    imageLoader.memoryCache?.set(
                        MemoryCache.Key(memoryKey),
                        MemoryCache.Value(fresh.bitmap.asImage())
                    )
                }
            }
            revalidating.remove(memoryKey)
        }
    }

    class Factory(
        private val context: Context,
        private val renderer: AppIconRenderer,
        private val iconPacks: IconPackRepository
    ) : Fetcher.Factory<AppInfo> {
        override fun create(
            data: AppInfo,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return AppIconFetcher(data, context, imageLoader, renderer, iconPacks)
        }
    }

    companion object {
        private const val REVALIDATION_DELAY_MS = 4_000L

        // Icons currently queued for a re-check, by memory-cache key.
        private val revalidating: MutableSet<String> = ConcurrentHashMap.newKeySet()

        // One at a time, so background re-renders stay a trickle.
        private val revalidationScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

        /**
         * Mirrors [IconPackRepository.iconVersion] as Compose state (kept in sync by
         * `LauncherApplication`). It is part of [memoryCacheKey], so a composable that builds its
         * request from that key reloads its icon when a pack is applied or an icon is picked.
         */
        var iconVersion by mutableIntStateOf(0)

        /** Memory-cache key shared by every screen that shows an app icon. */
        fun memoryCacheKey(app: AppInfo): String = memoryCacheKey(app.getIdentifier())

        /** [memoryCacheKey] for callers that only hold the app's identifier. */
        fun memoryCacheKey(appIdentifier: String): String = "app_icon_${appIdentifier}_v$iconVersion"
    }
}

private fun Bitmap.toFetchResult() = ImageFetchResult(
    image = asImage(),
    isSampled = false,
    dataSource = DataSource.DISK
)

/** Edge in pixels the request asked for, or [fallback] when it didn't name one. */
private fun Options.requestedSize(fallback: Int): Int =
    (size.width as? Dimension.Pixels)?.px ?: fallback

/**
 * Coil model for [app] as it would look with [packPackage] applied (null: its own icon),
 * regardless of what is applied or picked right now. Never written to the disk cache.
 */
data class AppIconPreview(val app: AppInfo, val packPackage: String?)

/** Coil model for an icon pack's own launcher icon. */
fun IconPackInfo.logoModel() = AppIconPreview(
    app = AppInfo(packageName = packageName, label = label, firstLetter = ' '),
    packPackage = null
)

class AppIconPreviewFetcher(
    private val preview: AppIconPreview,
    private val renderer: AppIconRenderer
) : Fetcher {
    override suspend fun fetch(): FetchResult? =
        renderer.renderWithPack(preview.app, preview.packPackage)?.bitmap?.toFetchResult()

    class Factory(private val renderer: AppIconRenderer) : Fetcher.Factory<AppIconPreview> {
        override fun create(data: AppIconPreview, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconPreviewFetcher(data, renderer)
    }
}

/** Loads a single icon out of an icon pack (the model is the [IconPackIcon] itself). */
class IconPackIconFetcher(
    private val icon: IconPackIcon,
    private val options: Options,
    private val renderer: AppIconRenderer
) : Fetcher {
    override suspend fun fetch(): FetchResult? =
        renderer.renderPackIcon(icon, options.requestedSize(DEFAULT_SIZE))?.toFetchResult()

    class Factory(private val renderer: AppIconRenderer) : Fetcher.Factory<IconPackIcon> {
        override fun create(data: IconPackIcon, options: Options, imageLoader: ImageLoader): Fetcher =
            IconPackIconFetcher(data, options, renderer)
    }

    private companion object {
        const val DEFAULT_SIZE = 192
    }
}

// Without a Keyer, Coil keeps custom models out of its memory cache.
val AppIconPreviewKeyer = Keyer<AppIconPreview> { data, _ ->
    "icon_preview_${data.packPackage}_${data.app.getIdentifier()}_${data.app.lastUpdateTime}"
}

val IconPackIconKeyer = Keyer<IconPackIcon> { data, _ ->
    "pack_icon_${data.packPackage}/${data.drawableName}"
}
