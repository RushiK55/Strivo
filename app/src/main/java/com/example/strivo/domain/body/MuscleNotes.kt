package com.example.strivo.domain.body

import org.json.JSONArray
import org.json.JSONObject

/**
 * What an admin wrote about a muscle on the dashboard's Body page (the cloud's muscles/{id}): a [subtitle], a friendlier name shown
 * under the muscle's name, and [about], a description that replaces the built-in one. Either may be empty (the built-in text is used).
 */
data class MuscleNote(val subtitle: String, val about: String) {
    val isEmpty: Boolean get() = subtitle.isEmpty() && about.isEmpty()
}

object MuscleNotes {
    /** The longest a subtitle and a description may be (the cloud rules allow the same). */
    const val MAX_SUBTITLE = 60
    const val MAX_ABOUT = 1000

    /** Reads a document from the cloud; null when it says nothing (so there is no note to keep). Too-long text is cut. */
    fun fromMap(data: Map<String, Any?>): MuscleNote? {
        val note = MuscleNote(
            subtitle = (data["subtitle"] as? String)?.trim()?.take(MAX_SUBTITLE).orEmpty(),
            about = (data["about"] as? String)?.trim()?.take(MAX_ABOUT).orEmpty(),
        )
        return note.takeUnless { it.isEmpty }
    }

    /** The notes kept on the phone, as one text; read back with [fromJson]. */
    fun toJson(notes: Map<String, MuscleNote>): String = JSONArray().also { array ->
        notes.forEach { (id, note) -> array.put(JSONObject().put("id", id).put("subtitle", note.subtitle).put("about", note.about)) }
    }.toString()

    /** Reads the stored notes; entries that cannot be read are skipped. */
    fun fromJson(text: String?): Map<String, MuscleNote> = runCatching {
        val array = JSONArray(text ?: return emptyMap())
        List(array.length()) { array.getJSONObject(it) }.mapNotNull { json ->
            val id = json.optString("id")
            val note = fromMap(mapOf("subtitle" to json.optString("subtitle"), "about" to json.optString("about")))
            if (id.isEmpty() || note == null) null else id to note
        }.toMap()
    }.getOrDefault(emptyMap())
}
