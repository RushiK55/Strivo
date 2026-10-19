package com.example.strivo.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

data class WorkoutSession(
    val id: Long? = null,
    val planName: String,
    val date: LocalDateTime,
    val totalTime: String,
    val exercises: List<PerformedExercise>,
    /** The exercises of the plan that were left out when the workout was finished early (none done), by name. */
    val skipped: List<String> = emptyList(),
) {
    fun skippedJson(): String = JSONArray(skipped).toString()

    fun exercisesJson(): String =
        JSONArray().also { array -> exercises.forEach { array.put(it.toJson()) } }.toString()

    companion object {
        /** The names saved by [skippedJson]; empty for a workout saved before this was kept, or text that cannot be read. */
        fun skippedFromJson(json: String?): List<String> = runCatching {
            val array = JSONArray(json ?: return emptyList())
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())

        fun exercisesFromJson(json: String): List<PerformedExercise> {
            val array = JSONArray(json)
            return List(array.length()) { PerformedExercise.fromJson(array.getJSONObject(it)) }
        }
    }
}

data class PerformedExercise(
    val name: String,
    val sets: List<ExerciseSet>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("sets", JSONArray().also { array -> sets.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): PerformedExercise {
            val array = json.getJSONArray("sets")
            return PerformedExercise(
                name = json.getString("name"),
                sets = List(array.length()) { ExerciseSet.fromJson(array.getJSONObject(it)) },
            )
        }
    }
}
