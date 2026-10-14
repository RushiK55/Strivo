package com.example.strivo.domain.body

import org.json.JSONObject

/** A point or direction in the body's space: metres, Y up, the body faces +Z, +X is the body's left. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)
    fun normalised(): Vec3 = length().let { if (it < 1e-9f) this else Vec3(x / it, y / it, z / it) }
}

/** A box around part of the body. */
data class Box(val min: Vec3, val max: Vec3) {
    val center: Vec3 get() = Vec3((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2)
    val size: Vec3 get() = max - min
}

/** Which body to show: the account's. */
enum class BodyKey(val file: String, val key: String) {
    Male("male.glb", "male"),
    Female("female.glb", "female");

    companion object {
        /** The body for an account's gender as the profile stores it ("Female", "Male", or anything else or nothing): female for female, else male. */
        fun forGender(gender: String?): BodyKey = if (gender?.trim().equals("female", ignoreCase = true)) Female else Male
    }
}

/** The side the camera shows when it zooms to a muscle. */
enum class MuscleView { Front, Back, Both }

/** "Deep" muscles lie under the surface ones and can only be seen with those faded. */
enum class MuscleLayer { Surface, Deep }

/** Where a muscle is on one body: the box around all of it, and around each side ("L", "R"; "C" is the middle). */
data class MusclePlace(val box: Box, val sides: Map<String, Box>)

/** One muscle of the catalogue (what the 3D body's pieces are called, and what an exercise names). */
data class MuscleGroup(
    val id: String,
    val label: String,
    val about: String,
    /** The exercise library's broad group (Chest, Back, Legs ...) it belongs to. */
    val libraryGroup: String,
    val view: MuscleView,
    val layer: MuscleLayer,
    val male: MusclePlace?,
    val female: MusclePlace?,
) {
    fun placeOn(body: BodyKey): MusclePlace? = if (body == BodyKey.Female) female else male
}

/** How a body model is sized (to point the camera at it). */
data class BodyInfo(val file: String, val height: Float, val box: Box)

/**
 * The muscles the 3D body is made of (assets/muscles.json, written by tools/anatomy): their names, what they do, which exercise
 * group they belong to and where they are on each body.
 */
class MuscleCatalogue(val bodies: Map<BodyKey, BodyInfo>, val groups: List<MuscleGroup>) {
    private val byId = groups.associateBy { it.id }

    fun group(id: String): MuscleGroup? = byId[id]

    fun body(key: BodyKey): BodyInfo? = bodies[key]

    /** The label of a muscle, or a readable one made from its id ("delt-rear" -> "Delt rear") when it is not known. */
    fun labelOf(id: String): String = byId[id]?.label ?: id.replace('-', ' ').replaceFirstChar { it.uppercase() }

    companion object {
        /** Reads the catalogue; null when the text is not one (nothing is shown rather than something broken). */
        fun fromJson(text: String): MuscleCatalogue? = runCatching {
            val root = JSONObject(text)
            val bodiesJson = root.getJSONObject("bodies")
            val bodies = BodyKey.entries.associateWith { key ->
                val body = bodiesJson.getJSONObject(key.key)
                BodyInfo(body.getString("file"), body.getDouble("height").toFloat(), box(body.getJSONObject("box")))
            }
            val array = root.getJSONArray("groups")
            val groups = List(array.length()) { array.getJSONObject(it) }.map { json ->
                MuscleGroup(
                    id = json.getString("id"),
                    label = json.getString("label"),
                    about = json.optString("about"),
                    libraryGroup = json.optString("libraryGroup"),
                    view = when (json.optString("view")) { "back" -> MuscleView.Back; "both" -> MuscleView.Both; else -> MuscleView.Front },
                    layer = if (json.optString("layer") == "deep") MuscleLayer.Deep else MuscleLayer.Surface,
                    male = json.optJSONObject("male")?.let(::place),
                    female = json.optJSONObject("female")?.let(::place),
                )
            }
            if (groups.isEmpty()) null else MuscleCatalogue(bodies, groups)
        }.getOrNull()

        private fun vec(array: org.json.JSONArray) = Vec3(array.getDouble(0).toFloat(), array.getDouble(1).toFloat(), array.getDouble(2).toFloat())

        private fun box(json: JSONObject) = Box(vec(json.getJSONArray("min")), vec(json.getJSONArray("max")))

        private fun place(json: JSONObject): MusclePlace {
            val sides = json.optJSONObject("sides")
            return MusclePlace(
                box = box(json.getJSONObject("box")),
                sides = sides?.keys()?.asSequence()?.associateWith { box(sides.getJSONObject(it)) }.orEmpty(),
            )
        }
    }
}
