package com.crylo.photosize.core

/** A structurally valid JPEG shell (SOI, JFIF APP0, filler, EOI) of exactly [size] bytes. */
fun fakeJpeg(size: Int, withJfif: Boolean = true): ByteArray {
    val head = mutableListOf(0xFF, 0xD8)
    if (withJfif) head += listOf(0xFF, 0xE0, 0, 16, 'J'.code, 'F'.code, 'I'.code, 'F'.code, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0)
    val out = ByteArray(size) { 0x11 }
    head.forEachIndexed { i, b -> out[i] = b.toByte() }
    out[size - 2] = 0xFF.toByte()
    out[size - 1] = 0xD9.toByte()
    return out
}

/** Segment markers from SOI up to the filler, walking the length fields like a decoder does. */
fun segmentMarkers(bytes: ByteArray): List<Int> {
    val markers = mutableListOf<Int>()
    var i = 2
    while (i + 4 <= bytes.size && (bytes[i].toInt() and 0xFF) == 0xFF) {
        val marker = bytes[i + 1].toInt() and 0xFF
        if (marker !in 0xE0..0xFE) break
        markers += marker
        val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
        i += 2 + len
    }
    return markers
}
