package com.joyal.swyplauncher.util

import android.graphics.Bitmap
import android.graphics.Color
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
            val pixel = bitmap.getPixel(x, y)
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
