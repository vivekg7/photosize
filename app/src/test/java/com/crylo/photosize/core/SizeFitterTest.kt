package com.crylo.photosize.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SizeFitterTest {
    /** File size grows with pixel count and (non-linearly) with quality, like a real JPEG. */
    private class FakeEncoder(private val bytesPerPixelAtMax: Double = 1.0) : ImageEncoder {
        var calls = 0
        override fun encode(width: Int, height: Int, quality: Int): ByteArray {
            calls++
            val factor = 0.05 + 0.95 * (quality / 100.0).pow(2.5)
            return fakeJpeg(600 + (width * height * bytesPerPixelAtMax * factor).toInt())
        }
    }

    private val fitter = SizeFitter()

    @Test fun `picks the highest quality that fits under the maximum`() {
        val enc = FakeEncoder()
        val limits = ByteLimits.fromKb(20, 50)
        val r = fitter.fit(enc, 200, 230, limits)
        // 200x230 at max quality is ~40 KB here, which already fits.
        assertEquals(FitResult.Status.OK, r.status)
        assertEquals(95, r.quality)
        assertTrue(r.size in limits)
    }

    @Test fun `binary search lands on the boundary quality`() {
        val enc = FakeEncoder(bytesPerPixelAtMax = 3.0)
        val limits = ByteLimits.fromKb(20, 50)
        val r = fitter.fit(enc, 200, 230, limits)
        assertTrue(r.size <= limits.maxBytes)
        assertTrue("next quality up must overshoot", enc.encode(200, 230, r.quality + 1).size > limits.maxBytes)
        assertEquals(200, r.width)
        assertEquals(230, r.height)
    }

    @Test fun `search is logarithmic, not a linear scan`() {
        val enc = FakeEncoder(bytesPerPixelAtMax = 3.0)
        fitter.fit(enc, 200, 230, ByteLimits.fromKb(20, 50))
        assertTrue("used ${enc.calls} encodes", enc.calls <= 8)
    }

    @Test fun `downscales a flexible slot when quality alone cannot fit`() {
        val enc = FakeEncoder(bytesPerPixelAtMax = 4.0)
        val limits = ByteLimits.fromKb(20, 300)
        val r = fitter.fit(enc, 1000, 1000, limits, minWidth = 350)
        assertEquals(FitResult.Status.OK, r.status)
        assertTrue(r.width in 350 until 1000)
        assertEquals(r.width, r.height) // aspect ratio kept
        assertTrue(r.quality >= 40)
        assertTrue(r.size in limits)
    }

    @Test fun `fixed slot drops below the quality floor instead of resizing`() {
        val enc = FakeEncoder(bytesPerPixelAtMax = 8.0)
        val limits = ByteLimits.fromKb(10, 20)
        val r = fitter.fit(enc, 140 * 2, 60 * 2, limits)
        assertEquals(FitResult.Status.OK, r.status)
        assertEquals(280, r.width)
        assertTrue(r.quality < 40)
        assertTrue(r.size in limits)
    }

    @Test fun `reports too large when nothing fits`() {
        val enc = FakeEncoder(bytesPerPixelAtMax = 50.0)
        val r = fitter.fit(enc, 1000, 1000, ByteLimits.fromKb(1, 10), minWidth = 900)
        assertEquals(FitResult.Status.TOO_LARGE, r.status)
        assertEquals(900, r.width)
        assertEquals(1, r.quality)
    }

    @Test fun `pads a too-small file up to the minimum`() {
        val enc = FakeEncoder(bytesPerPixelAtMax = 0.2)
        val limits = ByteLimits.fromKb(10, 20)
        val r = fitter.fit(enc, 140, 60, limits)
        assertEquals(FitResult.Status.OK, r.status)
        assertEquals(95, r.quality)
        assertEquals(limits.minBytes, r.size)
        assertTrue(r.paddedBytes > 0)
        assertTrue(segmentMarkers(r.bytes).drop(1).all { it == 0xFE })
    }

    @Test fun `byte limits use the stricter KB reading on each side`() {
        val l = ByteLimits.fromKb(20, 50)
        assertEquals(20 * 1024, l.minBytes)
        assertEquals(50 * 1000, l.maxBytes)
        // Degenerate range where min*1024 > max*1000 still yields a valid window.
        val narrow = ByteLimits.fromKb(10, 10)
        assertTrue(narrow.minBytes <= narrow.maxBytes)
    }

    @Test fun `formats KB with one decimal`() {
        assertEquals("48.0 KB", formatKb(48 * 1024))
        assertEquals("0.5 KB", formatKb(512))
        assertEquals("19.5 KB", formatKb(20_000))
    }
}
