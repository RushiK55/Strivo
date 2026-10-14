package com.example.strivo.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * An exercise in the library: what an admin publishes and every user can browse and add to a plan.
 * [id] is stable (it is the document id in the cloud), so an exercise keeps its identity when it is edited.
 */
data class LibraryExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val level: String,
    val instructions: String,
    val tips: String,
    val defaultSets: Int,
    val defaultReps: Int,
    val defaultWeightKg: Double,
    val videoUrl: String,
    val active: Boolean,
    /** Epoch milliseconds of the last change in the cloud; 0 for the exercises that ship with the app. */
    val updatedAt: Long = 0,
    /** The muscles it trains mainly and the ones it helps (ids from the muscle catalogue, such as "delt-rear"); empty when not set. */
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
) {
    /** What is added to a plan: the exercise with its suggested sets, reps and weight. */
    fun toPlanExercise(planId: Long) = Exercise(
        planId = planId,
        name = name,
        weight = defaultWeightKg.toString(),
        sets = defaultSets.toString(),
        reps = defaultReps.toString(),
        notes = tips,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("muscleGroup", muscleGroup)
        .put("equipment", equipment)
        .put("level", level)
        .put("instructions", instructions)
        .put("tips", tips)
        .put("defaultSets", defaultSets)
        .put("defaultReps", defaultReps)
        .put("defaultWeightKg", defaultWeightKg)
        .put("videoUrl", videoUrl)
        .put("active", active)
        .put("updatedAt", updatedAt)
        .put("primaryMuscles", JSONArray(primaryMuscles))
        .put("secondaryMuscles", JSONArray(secondaryMuscles))

    companion object {
        /** Reads one exercise; null when it has no id or name, so one bad entry never hides the rest. */
        fun fromJson(json: JSONObject): LibraryExercise? {
            val id = json.optString("id").trim()
            val name = json.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) return null
            return LibraryExercise(
                id = id,
                name = name,
                muscleGroup = json.optString("muscleGroup").trim().ifEmpty { "Other" },
                equipment = json.optString("equipment").trim(),
                level = json.optString("level").trim(),
                instructions = json.optString("instructions").trim(),
                tips = json.optString("tips").trim(),
                defaultSets = json.optInt("defaultSets", 3).coerceIn(1, 20),
                defaultReps = json.optInt("defaultReps", 10).coerceIn(1, 100),
                defaultWeightKg = json.optDouble("defaultWeightKg", 0.0).takeIf { !it.isNaN() }?.coerceIn(0.0, 1000.0) ?: 0.0,
                videoUrl = json.optString("videoUrl").trim(),
                active = json.optBoolean("active", true),
                updatedAt = json.optLong("updatedAt", 0),
                primaryMuscles = cleanMuscleIds(json.optJSONArray("primaryMuscles")?.let { array -> List(array.length()) { array.opt(it) } }),
                secondaryMuscles = cleanMuscleIds(json.optJSONArray("secondaryMuscles")?.let { array -> List(array.length()) { array.opt(it) } }),
            )
        }

        /**
         * Reads a library document from the cloud (its id and fields). Numbers may arrive as Long or Double, so they are
         * read as Number. Null when the document has no name.
         */
        fun fromMap(id: String, data: Map<String, Any?>): LibraryExercise? {
            val name = (data["name"] as? String)?.trim().orEmpty()
            if (id.isBlank() || name.isEmpty()) return null
            fun text(key: String) = (data[key] as? String)?.trim().orEmpty()
            return LibraryExercise(
                id = id,
                name = name,
                muscleGroup = text("muscleGroup").ifEmpty { "Other" },
                equipment = text("equipment"),
                level = text("level"),
                instructions = text("instructions"),
                tips = text("tips"),
                defaultSets = ((data["defaultSets"] as? Number)?.toInt() ?: 3).coerceIn(1, 20),
                defaultReps = ((data["defaultReps"] as? Number)?.toInt() ?: 10).coerceIn(1, 100),
                defaultWeightKg = ((data["defaultWeightKg"] as? Number)?.toDouble() ?: 0.0).coerceIn(0.0, 1000.0),
                videoUrl = text("videoUrl"),
                active = data["active"] as? Boolean ?: true,
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
                primaryMuscles = cleanMuscleIds(data["primaryMuscles"] as? List<*>),
                secondaryMuscles = cleanMuscleIds(data["secondaryMuscles"] as? List<*>),
            )
        }

        /** The most muscles an exercise can name in each list (the cloud rules allow the same). */
        const val MAX_MUSCLES = 8

        private val MuscleId = Regex("^[a-z]+(-[a-z]+)*$")

        /** The distinct, well-formed muscle ids of a list, in order, at most [MAX_MUSCLES]; anything else in it is dropped. */
        fun cleanMuscleIds(values: List<*>?): List<String> =
            values.orEmpty().filterIsInstance<String>().map { it.trim() }.filter { MuscleId.matches(it) }.distinct().take(MAX_MUSCLES)

        /** The muscles as one text for the database: ids joined by commas (an id never has a comma in it). */
        fun musclesToText(ids: List<String>): String = ids.joinToString(",")

        fun musclesFromText(text: String?): List<String> = cleanMuscleIds(text.orEmpty().split(',').filter { it.isNotBlank() })

        /** Reads a list; entries that cannot be read are skipped. */
        fun listFromJson(text: String): List<LibraryExercise> = runCatching {
            val array = JSONArray(text)
            List(array.length()) { fromJson(array.getJSONObject(it)) }.filterNotNull()
        }.getOrDefault(emptyList())
    }
}

/** The muscle groups in the order the library shows them; anything else is listed after these. */
val LibraryMuscleGroups = listOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Legs", "Glutes", "Core", "Cardio", "Full body")
