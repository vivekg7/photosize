package com.crylo.photosize.core

/**
 * Small, byte-level edits to an encoded baseline JPEG that leave the image data untouched.
 */
object JpegMarkers {
    private const val MARKER = 0xFF
    private const val SOI = 0xD8
    private const val APP0 = 0xE0
    private const val COM = 0xFE

    /** Largest payload of one marker segment (its 2-byte length field counts itself). */
    const val MAX_SEGMENT_PAYLOAD = 65533

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes.u(0) == MARKER && bytes.u(1) == SOI

    /**
     * Sets the JFIF density to [dpi] dots per inch. Patches an existing JFIF APP0 segment in
     * place (same file size) or inserts one right after SOI (+18 bytes) when the encoder wrote
     * none. Exam portals that check DPI read exactly these fields.
     */
    fun setDpi(bytes: ByteArray, dpi: Int): ByteArray {
        require(isJpeg(bytes)) { "not a JPEG" }
        require(dpi in 1..65535) { "dpi out of range" }
        if (hasJfifApp0(bytes)) {
            val out = bytes.copyOf()
            // APP0 layout: FFE0, len(2), "JFIF\0"(5), version(2), units(1), Xdensity(2), Ydensity(2)
            out[13] = 1 // units: dots per inch
            out[14] = (dpi shr 8).toByte()
            out[15] = dpi.toByte()
            out[16] = (dpi shr 8).toByte()
            out[17] = dpi.toByte()
            return out
        }
        val app0 = byteArrayOf(
            0xFF.toByte(), APP0.toByte(), 0, 16,
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0,
            1, 1, // version 1.01
            1, // units: dots per inch
            (dpi shr 8).toByte(), dpi.toByte(),
            (dpi shr 8).toByte(), dpi.toByte(),
            0, 0, // no thumbnail
        )
        return bytes.copyOfRange(0, 2) + app0 + bytes.copyOfRange(2, bytes.size)
    }

    /** Reads the JFIF density as DPI, or null when there is no JFIF header or it isn't in DPI. */
    fun readDpi(bytes: ByteArray): Int? {
        if (!isJpeg(bytes) || !hasJfifApp0(bytes) || bytes.u(13) != 1) return null
        return (bytes.u(14) shl 8) or bytes.u(15)
    }

    /**
     * Grows the file to exactly [targetSize] bytes by inserting comment (COM) segments after
     * the header. Decoders skip comments, so the picture is identical; this is how a very clean
     * image (a signature on white) reaches a portal's *minimum* size, which no amount of JPEG
     * quality can do for an image that simply has little detail.
     */
    fun padTo(bytes: ByteArray, targetSize: Int): ByteArray {
        require(isJpeg(bytes)) { "not a JPEG" }
        var missing = targetSize - bytes.size
        if (missing <= 0) return bytes
        require(missing >= 4) { "padding needs at least 4 bytes (one empty segment)" }
        val padding = java.io.ByteArrayOutputStream(missing)
        while (missing > 0) {
            // Each segment costs 4 bytes of overhead; avoid leaving a remainder under 4.
            var payload = minOf(missing - 4, MAX_SEGMENT_PAYLOAD)
            val rest = missing - 4 - payload
            if (rest in 1..3) payload -= 4 - rest
            val len = payload + 2
            padding.write(MARKER)
            padding.write(COM)
            padding.write(len shr 8)
            padding.write(len and 0xFF)
            repeat(payload) { padding.write(' '.code) }
            missing -= payload + 4
        }
        val at = insertionPoint(bytes)
        return bytes.copyOfRange(0, at) + padding.toByteArray() + bytes.copyOfRange(at, bytes.size)
    }

    /** After SOI and, if present, the JFIF APP0 segment, which must stay first. */
    private fun insertionPoint(bytes: ByteArray): Int =
        if (hasJfifApp0(bytes)) 4 + ((bytes.u(4) shl 8) or bytes.u(5)) else 2

    private fun hasJfifApp0(b: ByteArray): Boolean =
        b.size >= 18 && b.u(2) == MARKER && b.u(3) == APP0 &&
            b[6] == 'J'.code.toByte() && b[7] == 'F'.code.toByte() &&
            b[8] == 'I'.code.toByte() && b[9] == 'F'.code.toByte() && b[10] == 0.toByte()

    private fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF
}
