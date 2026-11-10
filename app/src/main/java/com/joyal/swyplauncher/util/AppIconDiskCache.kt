package com.joyal.swyplauncher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.joyal.swyplauncher.domain.model.AppInfo
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Rendered app icons persisted as PNGs under the cache dir. Rendering an icon from scratch means
 * a LauncherApps binder call plus opening that app's APK resources; decoding a small PNG is an
 * order of magnitude cheaper, which is what makes the grid paint quickly in a fresh process.
 *
 * Entries are versioned by the app's last-update time, so an updated app gets a new file and
 * [prune] drops the old one. All calls do disk I/O — keep them off the main thread.
 */
object AppIconDiskCache {

    private const val REVALIDATION_INTERVAL_MS = 24 * 60 * 60 * 1000L // 1 day

    // Distinguishes temp files of concurrent writes
    private val tempCounter = AtomicInteger()

    private fun directory(context: Context) = File(context.cacheDir, "app_icons")

    private fun fileName(app: AppInfo): String {
        val id = app.getIdentifier().replace('/', '@')
        // Package/class names are filesystem-safe; hash anything unusual or overly long.
        val safeId = if (id.length <= 160 && id.all { it.isLetterOrDigit() || it in "._@$-" }) {
            id
        } else {
            MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        return "${safeId}_${app.lastUpdateTime}_${app.iconDensity}.png"
    }

    fun read(context: Context, app: AppInfo): Bitmap? = try {
        val file = File(directory(context), fileName(app))
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    } catch (_: Exception) {
        // Unreadable entry; the caller renders the icon again and overwrites it.
        null
    }

    fun write(context: Context, app: AppInfo, bitmap: Bitmap) {
        try {
            val dir = directory(context).apply { mkdirs() }
            val target = File(dir, fileName(app))
            // Write-then-rename so a reader never sees a half-written PNG.
            val temp = File(dir, "${target.name}.${tempCounter.incrementAndGet()}.tmp")
            FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!temp.renameTo(target)) temp.delete()
        } catch (_: Exception) {
            // Best-effort cache; the icon is simply rendered again next time.
        }
    }

    /** True when the cached icon hasn't been checked against a fresh render for a day. */
    fun isDueForRevalidation(context: Context, app: AppInfo): Boolean {
        val lastChecked = File(directory(context), fileName(app)).lastModified()
        return System.currentTimeMillis() - lastChecked > REVALIDATION_INTERVAL_MS
    }

    /** Records that the cached icon was just confirmed to still match a fresh render. */
    fun markRevalidated(context: Context, app: AppInfo) {
        try {
            File(directory(context), fileName(app)).setLastModified(System.currentTimeMillis())
        } catch (_: Exception) {
            // Only means it gets checked again sooner than necessary.
        }
    }

    /** Deletes entries that no longer belong to any of [installedApps] (uninstalled or updated). */
    fun prune(context: Context, installedApps: List<AppInfo>) {
        try {
            val keep = installedApps.mapTo(HashSet()) { fileName(it) }
            directory(context).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        } catch (_: Exception) {
            // Stale files only cost a little cache space; try again on the next refresh.
        }
    }

    /**
     * Pixel comparison that tolerates the ±1 rounding a premultiplied-alpha PNG round trip can
     * introduce, so an unchanged icon isn't mistaken for a new one.
     */
    fun sameIcon(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val size = a.width * a.height
        val pixelsA = IntArray(size)
        val pixelsB = IntArray(size)
        a.getPixels(pixelsA, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(pixelsB, 0, b.width, 0, 0, b.width, b.height)
        for (i in 0 until size) {
            val pa = pixelsA[i]
            val pb = pixelsB[i]
            if (pa == pb) continue
            for (shift in 0..24 step 8) {
                if (abs(((pa ushr shift) and 0xFF) - ((pb ushr shift) and 0xFF)) > 2) return false
            }
        }
        return true
    }
}
