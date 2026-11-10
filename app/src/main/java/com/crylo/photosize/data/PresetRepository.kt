package com.crylo.photosize.data

import android.content.Context
import com.crylo.photosize.core.Preset
import com.crylo.photosize.core.PresetJson
import java.io.File

/** Bundled presets from assets plus the user's own, which live in a private JSON file. */
class PresetRepository(private val context: Context) {
    private val customFile get() = File(context.filesDir, "custom_presets.json")

    fun bundled(): List<Preset> =
        context.assets.open("presets.json").bufferedReader().use { PresetJson.parse(it.readText()) }

    fun custom(): List<Preset> = runCatching {
        if (customFile.exists()) PresetJson.parse(customFile.readText(), custom = true) else emptyList()
    }.getOrDefault(emptyList())

    fun saveCustom(presets: List<Preset>) {
        // Write-then-rename so a crash mid-write can't leave a truncated file behind.
        val tmp = File(context.filesDir, "custom_presets.json.tmp")
        tmp.writeText(PresetJson.write(presets))
        tmp.renameTo(customFile)
    }
}
