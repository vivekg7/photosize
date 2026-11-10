package com.crylo.photosize.core

/** What the uploaded image shows, which decides how it is processed. */
enum class DocKind {
    /** A face photograph: cropped and compressed, colours untouched. */
    PHOTO,

    /** Ink on paper (signature, thumb impression, declaration): can be cleaned to black on white. */
    INK,
}

/**
 * One upload slot of an application form, e.g. "Photograph" or "Signature".
 *
 * [widthPx] x [heightPx] is the size the image is produced at. When [minWidthPx] is set the
 * portal accepts a range, so the encoder may shrink the image (keeping the aspect ratio) down
 * to that width to get under [maxKb]; when it is null the dimensions are exact and only the
 * JPEG quality may change.
 */
data class DocSpec(
    val id: String,
    val label: String,
    val kind: DocKind,
    val widthPx: Int,
    val heightPx: Int,
    val minKb: Int,
    val maxKb: Int,
    val minWidthPx: Int? = null,
    val dpi: Int? = null,
    /** Whether the form asks for the candidate's name and photo date printed under the photo. */
    val nameDate: Boolean = false,
    val note: String? = null,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "dimensions must be positive" }
        require(minKb in 0..maxKb && maxKb > 0) { "KB range must satisfy 0 <= min <= max, max > 0" }
        require(minWidthPx == null || minWidthPx in 1..widthPx) { "minWidthPx must be in 1..widthPx" }
        require(dpi == null || dpi in 1..65535) { "dpi must fit in a JFIF density field" }
    }

    val aspect: Float get() = widthPx.toFloat() / heightPx

    /** Smallest width the encoder may shrink to; equals [widthPx] for fixed-size slots. */
    val floorWidthPx: Int get() = minWidthPx ?: widthPx

    val limits: ByteLimits get() = ByteLimits.fromKb(minKb, maxKb)
}

/** An exam (or a generic use) and the documents its form asks for. */
data class Preset(
    val id: String,
    val name: String,
    val documents: List<DocSpec>,
    val notes: String? = null,
    val source: String? = null,
    val custom: Boolean = false,
)

/**
 * Byte bounds for a KB range. Portals disagree on whether a KB is 1000 or 1024 bytes, so the
 * bounds are taken from the stricter reading on each side: the minimum uses 1024 (larger) and
 * the maximum uses 1000 (smaller). A file inside these bounds passes either check.
 */
data class ByteLimits(val minBytes: Int, val maxBytes: Int) {
    init {
        require(minBytes in 0..maxBytes) { "minBytes must be in 0..maxBytes" }
    }

    operator fun contains(size: Int): Boolean = size in minBytes..maxBytes

    companion object {
        fun fromKb(minKb: Int, maxKb: Int): ByteLimits {
            val min = minKb * 1024
            val max = maxKb * 1000
            // A very narrow range (e.g. 10-10 KB) would invert; fall back to the 1024 reading.
            return if (min <= max) ByteLimits(min, max) else ByteLimits(minKb * 1000, maxKb * 1024)
        }
    }
}

/** Height of the optional name-and-date strip at the bottom of a photo of [heightPx]. */
fun stripHeightPx(heightPx: Int): Int = (heightPx * 0.2f).toInt().coerceAtLeast(1)

/** Formats a byte count the way portals display sizes: KB with one decimal (1 KB = 1024 B). */
fun formatKb(bytes: Int): String {
    val tenths = (bytes * 10L + 512) / 1024
    return "${tenths / 10}.${tenths % 10} KB"
}
