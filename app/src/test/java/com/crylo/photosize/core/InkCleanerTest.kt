package com.crylo.photosize.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkCleanerTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun value(c: Int) = c and 0xFF

    /** Unevenly lit paper (dark on the left, bright on the right) with a horizontal stroke. */
    private fun unevenSignature(w: Int, h: Int, strokeRows: IntRange): IntArray =
        IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val paper = 110 + 120 * x / (w - 1) // 110..230
            gray(if (y in strokeRows && x in w / 5 until 4 * w / 5) paper * 35 / 100 else paper)
        }

    @Test fun `uneven paper becomes white and the stroke black`() {
        val w = 300
        val h = 120
        val strokes = 55..62
        val out = InkCleaner.clean(unevenSignature(w, h, strokes), w, h)
        var paperWhite = 0
        var paperTotal = 0
        for (y in 0 until h) for (x in 0 until w) {
            val v = value(out[y * w + x])
            val inStroke = y in strokes && x in w / 5 until 4 * w / 5
            if (inStroke) {
                assertTrue("stroke at $x,$y is $v", v < 60)
            } else {
                paperTotal++
                if (v == 255) paperWhite++
            }
        }
        assertTrue("only $paperWhite of $paperTotal paper pixels are white", paperWhite > paperTotal * 0.99)
    }

    @Test fun `output is opaque grey-scale`() {
        val w = 50
        val h = 20
        val src = IntArray(w * h) { (0xFF shl 24) or (200 shl 16) or (30 shl 8) or 90 }
        InkCleaner.clean(src, w, h).forEach { c ->
            assertEquals(0xFF, c ushr 24)
            assertEquals(c and 0xFF, (c shr 8) and 0xFF)
            assertEquals(c and 0xFF, (c shr 16) and 0xFF)
        }
    }

    @Test fun `box blur of a constant image is that constant`() {
        val src = IntArray(40 * 30) { 77 }
        assertTrue(InkCleaner.boxBlur(src, 40, 30, 7).all { it == 77 })
    }

    @Test fun `box blur averages a window clamped at edges`() {
        // 3x1 image [0, 30, 60] radius 1: edges average 2 pixels, centre 3.
        assertEquals(listOf(15, 30, 45), InkCleaner.boxBlur(intArrayOf(0, 30, 60), 3, 1, 1).toList())
    }
}
