package com.crylo.photosize.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and writes presets as JSON. The same schema is used for the bundled
 * `assets/presets.json` and for the user's saved custom presets.
 */
object PresetJson {
    fun parse(json: String, custom: Boolean = false): List<Preset> {
        val root = JSONObject(json)
        val array = root.getJSONArray("presets")
        return (0 until array.length()).map { parsePreset(array.getJSONObject(it), custom) }
    }

    fun write(presets: List<Preset>): String {
        val array = JSONArray()
        presets.forEach { array.put(presetToJson(it)) }
        return JSONObject().put("version", 1).put("presets", array).toString(2)
    }

    private fun parsePreset(o: JSONObject, custom: Boolean): Preset {
        val docs = o.getJSONArray("documents")
        return Preset(
            id = o.getString("id"),
            name = o.getString("name"),
            documents = (0 until docs.length()).map { parseDoc(docs.getJSONObject(it)) },
            notes = o.optStringOrNull("notes"),
            source = o.optStringOrNull("source"),
            custom = custom,
        )
    }

    private fun parseDoc(o: JSONObject): DocSpec {
        val format = o.optString("format", "jpeg").lowercase()
        require(format == "jpeg" || format == "jpg") { "unsupported format: $format" }
        return DocSpec(
            id = o.getString("id"),
            label = o.getString("label"),
            kind = when (val k = o.getString("kind")) {
                "photo" -> DocKind.PHOTO
                "ink" -> DocKind.INK
                else -> throw IllegalArgumentException("unknown kind: $k")
            },
            widthPx = o.getInt("width"),
            heightPx = o.getInt("height"),
            minKb = o.optInt("minKb", 0),
            maxKb = o.getInt("maxKb"),
            minWidthPx = if (o.has("minWidth")) o.getInt("minWidth") else null,
            dpi = if (o.has("dpi")) o.getInt("dpi") else null,
            nameDate = o.optBoolean("nameDate", false),
            note = o.optStringOrNull("note"),
        )
    }

    private fun presetToJson(p: Preset): JSONObject {
        val docs = JSONArray()
        p.documents.forEach { d ->
            docs.put(
                JSONObject()
                    .put("id", d.id)
                    .put("label", d.label)
                    .put("kind", if (d.kind == DocKind.PHOTO) "photo" else "ink")
                    .put("width", d.widthPx)
                    .put("height", d.heightPx)
                    .put("minKb", d.minKb)
                    .put("maxKb", d.maxKb)
                    .put("format", "jpeg")
                    .apply {
                        d.minWidthPx?.let { put("minWidth", it) }
                        d.dpi?.let { put("dpi", it) }
                        if (d.nameDate) put("nameDate", true)
                        d.note?.let { put("note", it) }
                    },
            )
        }
        return JSONObject()
            .put("id", p.id)
            .put("name", p.name)
            .put("documents", docs)
            .apply {
                p.notes?.let { put("notes", it) }
                p.source?.let { put("source", it) }
            }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null
}
