package com.crylo.photosize.core

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Encodes one fixed source picture. Kept as an interface so the size-fitting search can be
 * unit-tested on the JVM with a fake, while the app plugs in Android's JPEG encoder.
 */
fun interface ImageEncoder {
    /** Encodes the source scaled to [width] x [height] at JPEG [quality] (1..100). */
    fun encode(width: Int, height: Int, quality: Int): ByteArray
}

data class FitResult(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val quality: Int,
    val status: Status,
    /** Bytes of comment padding added to reach the minimum size (0 when none). */
    val paddedBytes: Int = 0,
) {
    enum class Status {
        /** Size is inside the limits. */
        OK,

        /** Even the lowest quality at the smallest allowed size is over the maximum. */
        TOO_LARGE,
    }

    val size: Int get() = bytes.size

    override fun equals(other: Any?): Boolean = other is FitResult && bytes.contentEquals(other.bytes) &&
        width == other.width && height == other.height && quality == other.quality && status == other.status
    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * Finds the best-looking JPEG that lands inside a byte range.
 *
 * 1. At the full size, binary-search the highest quality whose file is at most the maximum.
 *    Quality is searched down to [qualityFloor] only, because below that JPEG blocking is
 *    visible and a slightly smaller picture looks better than a blocky one.
 * 2. If even the floor quality is too big and the slot allows a size range, shrink the
 *    picture (keeping its aspect ratio) by the square root of the overshoot and search again,
 *    down to the smallest allowed width.
 * 3. At the smallest allowed size, search all the way down to quality 1 before giving up.
 * 4. If the best file is under the minimum (a clean image with little detail), pad it with
 *    JPEG comment segments, which leaves the picture untouched.
 */
class SizeFitter(
    private val qualityFloor: Int = 40,
    private val maxQuality: Int = 95,
) {
    init {
        require(qualityFloor in 1..maxQuality && maxQuality <= 100)
    }

    fun fit(
        encoder: ImageEncoder,
        width: Int,
        height: Int,
        limits: ByteLimits,
        minWidth: Int = width,
    ): FitResult {
        require(minWidth in 1..width)
        val aspect = height.toDouble() / width
        var w = width
        while (true) {
            val h = (w * aspect).roundToInt().coerceAtLeast(1)
            val atFloor = w <= minWidth
            val lowest = if (atFloor) 1 else qualityFloor
            val cache = HashMap<Int, ByteArray>()
            fun enc(q: Int) = cache.getOrPut(q) { encoder.encode(w, h, q) }

            val smallest = enc(lowest)
            if (smallest.size > limits.maxBytes) {
                if (atFloor) return FitResult(smallest, w, h, lowest, FitResult.Status.TOO_LARGE)
                // Shrink by the overshoot ratio (file size scales roughly with pixel count),
                // with a 5% margin so we don't creep down one pixel at a time.
                val ratio = sqrt(limits.maxBytes.toDouble() / smallest.size) * 0.95
                w = (w * ratio).toInt().coerceIn(minWidth, w - 1)
                continue
            }

            // Highest quality in [lowest, maxQuality] whose size fits under the maximum.
            var lo = lowest
            var hi = maxQuality
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (enc(mid).size <= limits.maxBytes) lo = mid else hi = mid - 1
            }
            val best = enc(lo)
            return pad(best, w, h, lo, limits)
        }
    }

    private fun pad(bytes: ByteArray, w: Int, h: Int, q: Int, limits: ByteLimits): FitResult {
        if (bytes.size >= limits.minBytes) return FitResult(bytes, w, h, q, FitResult.Status.OK)
        // A comment segment is at least 4 bytes, so a shortfall of 1-3 bytes rounds up.
        val target = maxOf(limits.minBytes, bytes.size + 4)
        if (target > limits.maxBytes) return FitResult(bytes, w, h, q, FitResult.Status.OK)
        val padded = JpegMarkers.padTo(bytes, target)
        return FitResult(padded, w, h, q, FitResult.Status.OK, paddedBytes = padded.size - bytes.size)
    }
}
