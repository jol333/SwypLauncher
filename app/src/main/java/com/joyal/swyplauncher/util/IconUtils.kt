package com.joyal.swyplauncher.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import kotlin.math.max

/**
 * True when a bitmap is mostly black/dark gray — it would disappear on the assistant's black
 * sheet and needs a white backing. Averages the luminance of visible (non-transparent) pixels
 * sampled on a coarse grid so it's cheap enough to run per icon.
 */
fun isBitmapDark(bitmap: Bitmap, threshold: Int = 96): Boolean {
    if (bitmap.width == 0 || bitmap.height == 0) return false
    val stepX = max(1, bitmap.width / 24)
    val stepY = max(1, bitmap.height / 24)
    var luminanceSum = 0L
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap[x, y]
            if (Color.alpha(pixel) >= 128) {
                luminanceSum +=
                    (299 * Color.red(pixel) + 587 * Color.green(pixel) + 114 * Color.blue(pixel)) / 1000
                count++
            }
            x += stepX
        }
        y += stepY
    }
    return count > 0 && luminanceSum / count < threshold
}

/**
 * Up to two characteristic colors of a bitmap (ARGB ints), strongest first; empty when it has no
 * colorful pixels at all (a black, white or gray icon). Pixels are sampled on a coarse grid and
 * grouped by hue, weighted towards vivid ones, so a logo's accent beats its large pale areas.
 */
fun dominantColors(bitmap: Bitmap): List<Int> {
    if (bitmap.width == 0 || bitmap.height == 0) return emptyList()
    val buckets = 12
    val weight = FloatArray(buckets)
    val red = FloatArray(buckets)
    val green = FloatArray(buckets)
    val blue = FloatArray(buckets)
    val hsv = FloatArray(3)
    val stepX = max(1, bitmap.width / 24)
    val stepY = max(1, bitmap.height / 24)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap[x, y]
            if (Color.alpha(pixel) >= 128) {
                Color.colorToHSV(pixel, hsv)
                if (hsv[1] >= 0.25f && hsv[2] >= 0.25f) {
                    val bucket = (hsv[0] / 360f * buckets).toInt().coerceIn(0, buckets - 1)
                    val w = hsv[1] * hsv[2]
                    weight[bucket] += w
                    red[bucket] += Color.red(pixel) * w
                    green[bucket] += Color.green(pixel) * w
                    blue[bucket] += Color.blue(pixel) * w
                }
            }
            x += stepX
        }
        y += stepY
    }

    fun colorOf(bucket: Int) = Color.rgb(
        (red[bucket] / weight[bucket]).toInt(),
        (green[bucket] / weight[bucket]).toInt(),
        (blue[bucket] / weight[bucket]).toInt()
    )

    val ranked = (0 until buckets).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
    val first = ranked.firstOrNull() ?: return emptyList()
    // A second color only counts if it is a clearly different hue and not just a few stray pixels
    val second = ranked.drop(1).firstOrNull { candidate ->
        val distance = kotlin.math.abs(candidate - first).let { minOf(it, buckets - it) }
        distance >= 2 && weight[candidate] >= weight[first] * 0.15f
    }
    return listOfNotNull(colorOf(first), second?.let(::colorOf))
}
