package com.crylo.photosize.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegMarkersTest {
    @Test fun `setDpi patches an existing JFIF header without changing size`() {
        val src = fakeJpeg(1000)
        val out = JpegMarkers.setDpi(src, 200)
        assertEquals(1000, out.size)
        assertEquals(200, JpegMarkers.readDpi(out))
        // Image data after the header is untouched.
        assertArrayEquals(src.copyOfRange(20, 1000), out.copyOfRange(20, 1000))
    }

    @Test fun `setDpi inserts a JFIF header when there is none`() {
        val src = fakeJpeg(500, withJfif = false)
        assertNull(JpegMarkers.readDpi(src))
        val out = JpegMarkers.setDpi(src, 300)
        assertEquals(518, out.size)
        assertEquals(300, JpegMarkers.readDpi(out))
        assertEquals(listOf(0xE0), segmentMarkers(out))
    }

    @Test fun `setDpi handles values above 255`() {
        assertEquals(600, JpegMarkers.readDpi(JpegMarkers.setDpi(fakeJpeg(100), 600)))
    }

    @Test fun `readDpi is null when density is an aspect ratio`() {
        assertNull(JpegMarkers.readDpi(fakeJpeg(100)))
    }

    @Test fun `padTo reaches the exact size for awkward shortfalls`() {
        for (missing in listOf(4, 5, 6, 7, 100, 65536, 65537, 65538, 65539, 65540, 200_000)) {
            val out = JpegMarkers.padTo(fakeJpeg(1000), 1000 + missing)
            assertEquals("missing=$missing", 1000 + missing, out.size)
            val markers = segmentMarkers(out)
            assertEquals("JFIF must stay first", 0xE0, markers.first())
            assertTrue(markers.drop(1).all { it == 0xFE })
        }
    }

    @Test fun `padTo keeps the original bytes in order around the padding`() {
        val src = fakeJpeg(300)
        val out = JpegMarkers.padTo(src, 400)
        assertArrayEquals(src.copyOfRange(0, 20), out.copyOfRange(0, 20))
        assertArrayEquals(src.copyOfRange(20, 300), out.copyOfRange(120, 400))
    }

    @Test fun `padTo is a no-op when already big enough`() {
        val src = fakeJpeg(300)
        assertTrue(JpegMarkers.padTo(src, 200) === src)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `padTo rejects a shortfall smaller than one segment`() {
        JpegMarkers.padTo(fakeJpeg(300), 302)
    }
}
