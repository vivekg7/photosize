package com.crylo.photosize.core

import kotlin.math.roundToInt

/**
 * Turns a phone photo of ink on paper into clean dark ink on white.
 *
 * Phone photos of paper are never evenly lit: one corner is grey, there is a shadow from the
 * phone. A single global threshold either leaves the dark corner grey or eats thin strokes in
 * the bright one. So each pixel is first divided by the local paper brightness (a wide box
 * blur, computed in O(n) with a summed-area table), which makes the paper uniformly white
 * whatever the lighting. Then anything lighter than [threshold] becomes pure white and the
 * remaining ink is stretched to full black, keeping a soft edge so strokes don't look jagged.
 */
object InkCleaner {
    /**
     * @param argb pixels in row-major ARGB order, as from `Bitmap.getPixels`.
     * @param threshold 0..1; higher keeps fainter strokes (and more noise), lower is cleaner.
     * @return a new grey-scale ARGB array of the same size.
     */
    fun clean(argb: IntArray, width: Int, height: Int, threshold: Float = 0.8f): IntArray {
        require(argb.size == width * height) { "pixel count mismatch" }
        val n = argb.size
        val gray = IntArray(n) { luminance(argb[it]) }
        val radius = maxOf(width, height) / 12 + 1
        // First estimate includes the ink, which drags the paper level down where writing is
        // dense (a declaration paragraph). Replace clearly-dark pixels with that estimate and
        // blur again, so the second estimate is (nearly) paper only.
        val rough = boxBlur(gray, width, height, radius)
        val paperOnly = IntArray(n) { i -> if (gray[i] * 100 < rough[i] * 85) rough[i] else gray[i] }
        val background = boxBlur(paperOnly, width, height, radius)

        // Normalised brightness: 255 means "as bright as the paper around it".
        val norm = IntArray(n) { i ->
            val bg = maxOf(background[i], 1)
            minOf(255, gray[i] * 255 / bg)
        }

        // The darkest ink level present (1st percentile), so faint pens still reach black.
        val histogram = IntArray(256)
        norm.forEach { histogram[it]++ }
        var inkLevel = 0
        var count = 0
        while (inkLevel < 255) {
            count += histogram[inkLevel]
            if (count >= n / 100) break
            inkLevel++
        }

        val cut = (threshold.coerceIn(0.05f, 1f) * 255).roundToInt()
        val ink = minOf(inkLevel, cut - 1).coerceAtLeast(0)
        val span = (cut - ink).coerceAtLeast(1)
        return IntArray(n) { i ->
            val v = norm[i]
            val out = when {
                v >= cut -> 255
                v <= ink -> 0
                // Ink stretched to black with a gamma > 1 so stroke cores go solid while the
                // anti-aliased edges keep a little grey.
                else -> {
                    val t = (v - ink).toFloat() / span
                    (255 * t * t).roundToInt()
                }
            }
            (0xFF shl 24) or (out shl 16) or (out shl 8) or out
        }
    }

    internal fun luminance(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Mean over a (2r+1)^2 window, clamped at the edges, via a summed-area table. */
    internal fun boxBlur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val sat = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += src[y * w + x]
                sat[(y + 1) * (w + 1) + (x + 1)] = sat[y * (w + 1) + (x + 1)] + rowSum
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = maxOf(0, y - radius)
            val y1 = minOf(h, y + radius + 1)
            for (x in 0 until w) {
                val x0 = maxOf(0, x - radius)
                val x1 = minOf(w, x + radius + 1)
                val sum = sat[y1 * (w + 1) + x1] - sat[y0 * (w + 1) + x1] -
                    sat[y1 * (w + 1) + x0] + sat[y0 * (w + 1) + x0]
                out[y * w + x] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return out
    }
}
