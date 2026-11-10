package com.crylo.photosize.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PresetJsonTest {
    // Unit tests run with the module directory as the working directory.
    private val bundled = File("src/main/assets/presets.json").readText()

    @Test fun `bundled presets parse and cover the promised exams`() {
        val presets = PresetJson.parse(bundled)
        val ids = presets.map { it.id }
        for (id in listOf("ssc", "upsc", "ibps", "sbi", "rrb", "neet", "jee-main", "cuet-ug", "gate", "passport", "generic")) {
            assertTrue("missing $id", id in ids)
        }
        assertEquals("preset ids must be unique", ids.size, ids.toSet().size)
        presets.forEach { p ->
            assertTrue(p.documents.isNotEmpty())
            assertEquals("doc ids unique in ${p.id}", p.documents.size, p.documents.map { it.id }.toSet().size)
        }
    }

    @Test fun `bundled byte windows are all reachable`() {
        PresetJson.parse(bundled).flatMap { it.documents }.forEach { d ->
            assertTrue("${d.id} window", d.limits.minBytes < d.limits.maxBytes)
        }
    }

    @Test fun `IBPS photo matches the notification`() {
        val photo = PresetJson.parse(bundled).first { it.id == "ibps" }.documents.first { it.id == "photo" }
        assertEquals(200, photo.widthPx)
        assertEquals(230, photo.heightPx)
        assertEquals(20, photo.minKb)
        assertEquals(50, photo.maxKb)
        assertEquals(200, photo.dpi)
        assertEquals(DocKind.PHOTO, photo.kind)
    }

    @Test fun `custom presets round-trip`() {
        val original = listOf(
            Preset(
                id = "custom-1",
                name = "State PSC",
                custom = true,
                documents = listOf(
                    DocSpec("doc", "Photo", DocKind.PHOTO, 300, 400, 30, 80, minWidthPx = 150, dpi = 300, nameDate = true, note = "n"),
                    DocSpec("sig", "Signature", DocKind.INK, 140, 60, 0, 20),
                ),
            ),
        )
        assertEquals(original, PresetJson.parse(PresetJson.write(original), custom = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported formats are rejected`() {
        PresetJson.parse(
            """{"presets":[{"id":"x","name":"x","documents":[{"id":"d","label":"d","kind":"photo",""" +
                """"width":10,"height":10,"maxKb":5,"format":"png"}]}]}""",
        )
    }
}
